package com.fogiscoming.client;

import com.fogiscoming.FogIsComing;
import com.fogiscoming.config.FogConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Method;
import java.util.List;

/**
 * The mod's own GLSL post-processing pass ({@code assets/fogiscoming/shaders/core/fog_volume.*}):
 * volumetric height fog with drifting 3D noise, light halos in the fog and a cinematic grade.
 * <p>
 * Drawn at {@link RenderLevelStageEvent.Stage#AFTER_LEVEL}: the world is finished, the hand and the HUD are
 * not drawn yet, so neither is affected. If the shader fails to compile, is disabled in the config, or an
 * Oculus/Iris shader pack is active, nothing is drawn and the plain distance fog of {@link FogRenderer}
 * is used at full strength instead.
 */
@OnlyIn(Dist.CLIENT)
public final class FogShaders {
    private static ShaderInstance shader;
    private static TextureTarget sceneCopy;
    private static int frame;
    private static float indoorSmoothed = 1.0F;

    private static boolean irisChecked;
    private static Object irisApi;
    private static Method irisInUse;

    private FogShaders() {
    }

    static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), FogIsComing.id("fog_volume"),
                    DefaultVertexFormat.POSITION), s -> shader = s);
        } catch (Exception e) {
            shader = null;
            FogIsComing.LOGGER.error("[FogIsComing] Fog shader failed to load, falling back to vanilla fog", e);
        }
    }

    /** True when the shader pass will draw the fog this frame (FogRenderer then softens the vanilla fog). */
    public static boolean enabled() {
        return shader != null && FogConfig.CLIENT.shaderFog.get() && !externalShaderPackActive();
    }

    /** Oculus / Iris replace the whole pipeline; their shader pack is then in charge of the image. */
    static boolean externalShaderPackActive() {
        if (!irisChecked) {
            irisChecked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
            } catch (Throwable ignored) {
                irisApi = null;
            }
        }
        if (irisApi == null) return false;
        try {
            return (Boolean) irisInUse.invoke(irisApi);
        } catch (Throwable t) {
            return false;
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean wantLights = enabled() && FogConfig.CLIENT.lightHalos.get() && ClientFogState.isAffected()
                && ClientFogState.density(1.0F) > 0.02F;
        FogLightTracker.tick(wantLights);
        // Smoothly track whether the camera is indoors (little sky light): the fog then does not fill the room.
        if (mc.level != null) {
            BlockPos pos = BlockPos.containing(mc.gameRenderer.getMainCamera().getPosition());
            float sky = mc.level.getBrightness(LightLayer.SKY, pos) / 15.0F;
            float target = Mth.clamp(sky * 1.6F - 0.2F, 0.0F, 1.0F);
            indoorSmoothed += (target - indoorSmoothed) * 0.08F;
        }
    }

    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        Camera camera = event.getCamera();
        if (mc.level == null || camera.getFluidInCamera() != FogType.NONE) return;
        if (camera.getEntity() instanceof LivingEntity le && (le.hasEffect(MobEffects.BLINDNESS) || le.hasEffect(MobEffects.DARKNESS))) return;
        float partial = event.getPartialTick();
        float density = FogRenderer.renderDensity(partial);
        if (density <= 0.002F) return;

        RenderTarget main = mc.getMainRenderTarget();
        copyScene(main);

        float intensity = FogConfig.CLIENT.effectsIntensity.get().floatValue();
        float deadly = FogAtmosphere.deadliness(ClientFogState.density(partial));
        float visibility = FogRenderer.visibility(density);
        // Fade the effect in with the very first wisps of fog (no visible pop).
        float fadeIn = Mth.clamp(density / 0.15F, 0.0F, 1.0F);
        float extinction = 3.0F / visibility * fadeIn;
        float renderDistance = mc.gameRenderer.getRenderDistance();
        float marchDistance = Math.min(visibility * 1.6F, Math.max(32.0F, renderDistance));
        int steps = switch (FogConfig.CLIENT.shaderQuality.get()) {
            case LOW -> 8;
            case MEDIUM -> 14;
            case HIGH -> 22;
        };
        float time = (ClientFogState.clientTicks + partial) / 20.0F;

        Matrix4f invViewProj = new Matrix4f().set(RenderSystem.getInverseViewRotationMatrix())
                .mul(new Matrix4f(event.getProjectionMatrix()).invert());
        Vec3 cam = camera.getPosition();

        ShaderInstance s = shader;
        s.safeGetUniform("InvViewProj").set(invViewProj);
        s.safeGetUniform("NoiseOrigin").set((float) wrap(cam.x), (float) cam.y, (float) wrap(cam.z));
        float[] fog = FogRenderer.lastFogColor();
        s.safeGetUniform("FogTint").set(fog[0], fog[1], fog[2]);
        float heightFalloff = Mth.lerp(deadly, 22.0F, 70.0F);
        float noiseAmount = Mth.lerp(deadly, 0.75F, 0.95F) * Math.max(0.3F, intensity);
        s.safeGetUniform("FogParams").set(extinction, heightFalloff, noiseAmount, time);
        float grading = FogConfig.CLIENT.cinematicGrading.get()
                ? intensity * FogRenderer.smooth(Mth.clamp((density - 0.2F) / 0.6F, 0.0F, 1.0F)) : 0.0F;
        s.safeGetUniform("FogParams2").set(marchDistance, (float) steps, indoorSmoothed, grading);
        float windAngle = FogAtmosphere.windAngle();
        float windSpeed = 0.8F + 0.6F * deadly;
        s.safeGetUniform("WindParams").set(Mth.cos(windAngle) * windSpeed, 0.0F, Mth.sin(windAngle) * windSpeed, 0.045F);

        List<FogLightTracker.LightSource> lights = FogConfig.CLIENT.lightHalos.get() ? FogLightTracker.lights() : List.of();
        // In daylight a lamp barely glows in fog; at night the same lamp lights up the fog around it.
        float day = FogRenderer.daylight(mc.level, partial);
        float haloStrength = 0.9F * Math.max(0.35F, intensity) * Mth.lerp(day, 1.0F, 0.25F);
        s.safeGetUniform("HaloParams").set(haloStrength, (float) lights.size(), deadly, (float) (frame++ & 1023));
        for (int i = 0; i < FogLightTracker.MAX_LIGHTS; i++) {
            if (i < lights.size()) {
                FogLightTracker.LightSource l = lights.get(i);
                s.safeGetUniform("Light" + i).set((float) (l.x() - cam.x), (float) (l.y() - cam.y), (float) (l.z() - cam.z), l.intensity());
                s.safeGetUniform("LightColor" + i).set(l.r(), l.g(), l.b(), l.flicker());
            } else {
                s.safeGetUniform("Light" + i).set(0.0F, 0.0F, 0.0F, 0.0F);
                s.safeGetUniform("LightColor" + i).set(0.0F, 0.0F, 0.0F, 0.0F);
            }
        }
        s.safeGetUniform("ScreenSize").set((float) main.width, (float) main.height);
        s.setSampler("SceneColor", sceneCopy.getColorTextureId());
        s.setSampler("SceneDepth", sceneCopy.getDepthTextureId());

        main.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> s);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buf.vertex(-1.0, -1.0, 0.0).endVertex();
        buf.vertex(1.0, -1.0, 0.0).endVertex();
        buf.vertex(1.0, 1.0, 0.0).endVertex();
        buf.vertex(-1.0, 1.0, 0.0).endVertex();
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** Copies the finished world (colour + depth) so the shader can read it while writing the main target. */
    private static void copyScene(RenderTarget main) {
        if (sceneCopy == null) {
            sceneCopy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        } else if (sceneCopy.width != main.width || sceneCopy.height != main.height) {
            sceneCopy.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        if (main.isStencilEnabled() && !sceneCopy.isStencilEnabled()) sceneCopy.enableStencil();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneCopy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    /** Keeps noise coordinates small (float precision far from spawn); a seam every 4096 blocks is invisible in fog. */
    private static double wrap(double v) {
        return v - Math.floor(v / 4096.0) * 4096.0;
    }
}
