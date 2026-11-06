package com.fogiscoming.client;

import com.fogiscoming.FogIsComing;
import com.fogiscoming.config.FogConfig;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Visual fog. Works through Minecraft's own distance fog (the shader fog every world shader already uses),
 * so it is free in terms of performance, behaves correctly with camera movement and never paints over the HUD
 * or the held item:
 * <ul>
 *     <li>{@link ViewportEvent.RenderFog} — spherical fog with a long linear falloff: nearby blocks are crisp,
 *     objects gradually dissolve with distance, nothing beyond the visibility limit is visible.</li>
 *     <li>{@link ViewportEvent.ComputeFogColor} — cold grey palette, darker at night and in rain, blended with the
 *     vanilla colour so biome/time tints stay subtle.</li>
 *     <li>{@link RenderLevelStageEvent.Stage#AFTER_SKY} — a fog-coloured veil over the sky (before terrain), which
 *     hides the sun, moon and stars as the fog thickens. Terrain is drawn on top of it afterwards.</li>
 * </ul>
 * The real server view distance is never touched.
 */
@Mod.EventBusSubscriber(modid = FogIsComing.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FogRenderer {
    /** Visibility (blocks) at density 0 and density 1. */
    private static final float REFERENCE_DISTANCE = 256.0F;
    private static final float MIN_DISTANCE = 8.0F;

    /** Last fog colour computed this frame (used to tint the fog wisps). */
    private static final float[] LAST_FOG_COLOR = {0.6F, 0.62F, 0.65F};

    private FogRenderer() {
    }

    static float[] lastFogColor() {
        return LAST_FOG_COLOR;
    }

    /**
     * Density used for rendering this frame: the subtle night thickening, plus, in the deadly fog,
     * a slow "breathing" wave so the wall of fog rolls in and recedes instead of standing still.
     */
    public static float renderDensity(float partialTick) {
        float base = ClientFogState.density(partialTick);
        float d = base;
        if (d <= 0.0005F) return 0.0F;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            float night = 1.0F - daylight(level, partialTick);
            d = d + 0.035F * night * Mth.clamp(d * 2.0F, 0.0F, 1.0F);
        }
        float deadly = FogAtmosphere.deadliness(base);
        if (deadly > 0.0F) {
            float intensity = FogConfig.CLIENT.effectsIntensity.get().floatValue();
            d += 0.065F * deadly * intensity * FogAtmosphere.breath(ClientFogState.clientTicks + partialTick);
        }
        return Mth.clamp(d, 0.0F, 0.985F);
    }

    /** Visibility in blocks for a given density. */
    public static float visibility(float density) {
        return MIN_DISTANCE + (REFERENCE_DISTANCE - MIN_DISTANCE) * (1.0F - density);
    }

    private static float daylight(ClientLevel level, float partialTick) {
        // getSkyDarken: ~1.0 at noon, ~0.2 at midnight; already includes rain/thunder darkening.
        return Mth.clamp((level.getSkyDarken(partialTick) - 0.2F) / 0.8F, 0.0F, 1.0F);
    }

    private static boolean vanillaOverrides(Entity camera) {
        return camera instanceof LivingEntity le && (le.hasEffect(MobEffects.BLINDNESS) || le.hasEffect(MobEffects.DARKNESS));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) return; // water, lava, powder snow keep vanilla behaviour
        if (vanillaOverrides(event.getCamera().getEntity())) return;
        float partial = (float) event.getPartialTick();
        float d = renderDensity(partial);
        if (d <= 0.0F) return;

        float vanillaFar = event.getFarPlaneDistance();
        float far = Math.min(vanillaFar, visibility(d));
        float near;
        if (event.getMode() == net.minecraft.client.renderer.FogRenderer.FogMode.FOG_SKY) {
            near = 0.0F;
        } else {
            // Long gradient: thin fog starts some way out, dense fog starts almost at the camera
            // (but never on the hand / held item, which sits within ~1 block).
            near = Math.max(1.0F, far * 0.35F * (1.0F - d));
        }
        // Blend from vanilla values while the fog is still very thin to avoid any visible jump.
        float blend = Mth.clamp(d / 0.15F, 0.0F, 1.0F);
        near = Mth.lerp(blend, event.getNearPlaneDistance(), near);
        far = Mth.lerp(blend, vanillaFar, far);

        if (FogShaders.enabled() && event.getMode() == net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
            // The volumetric shader draws the real fog; vanilla fog only hides the far chunk edge.
            far = Math.min(vanillaFar, visibility(d) * 2.2F);
            near = far * 0.55F;
        }
        event.setNearPlaneDistance(near);
        event.setFarPlaneDistance(far);
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true); // required for Forge to apply the modified values
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() != FogType.NONE) return;
        if (vanillaOverrides(event.getCamera().getEntity())) return;
        float partial = (float) event.getPartialTick();
        float d = renderDensity(partial);
        if (d <= 0.0F) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        float day = daylight(level, partial);
        // Uses the smoothed base density (not the breathing one) so the colour itself never pulses.
        float deadly = FogAtmosphere.deadliness(ClientFogState.density(partial));
        float intensity = FogConfig.CLIENT.effectsIntensity.get().floatValue();
        float gloom = deadly * (0.4F + 0.6F * intensity);

        // Cold light grey by day; in the deadly fog a heavy, dusky slate grey even at noon.
        // Night: near-black blue-grey, darker still in the deadly fog.
        float dayR = Mth.lerp(gloom, 0.66F, 0.185F), dayG = Mth.lerp(gloom, 0.69F, 0.197F), dayB = Mth.lerp(gloom, 0.72F, 0.215F);
        float nightBoost = (1.0F - 0.6F * intensity) * (1.0F - 0.7F * gloom); // lower intensity -> less night darkening
        float nightR = 0.075F + 0.05F * nightBoost, nightG = 0.082F + 0.05F * nightBoost, nightB = 0.095F + 0.05F * nightBoost;
        nightR = Mth.lerp(gloom, nightR, 0.045F);
        nightG = Mth.lerp(gloom, nightG, 0.05F);
        nightB = Mth.lerp(gloom, nightB, 0.058F);
        float r = Mth.lerp(day, nightR, dayR);
        float g = Mth.lerp(day, nightG, dayG);
        float b = Mth.lerp(day, nightB, dayB);

        float mix = smooth(Mth.clamp(d / 0.6F, 0.0F, 1.0F)) * 0.92F;
        event.setRed(Mth.lerp(mix, event.getRed(), r));
        event.setGreen(Mth.lerp(mix, event.getGreen(), g));
        event.setBlue(Mth.lerp(mix, event.getBlue(), b));
        LAST_FOG_COLOR[0] = event.getRed();
        LAST_FOG_COLOR[1] = event.getGreen();
        LAST_FOG_COLOR[2] = event.getBlue();
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE) return;
        if (vanillaOverrides(mc.gameRenderer.getMainCamera().getEntity())) return;
        if (FogShaders.externalShaderPackActive()) return; // the shader pack draws its own sky and fog
        float d = renderDensity(event.getPartialTick());
        float alpha = smooth(Mth.clamp((d - 0.25F) / 0.6F, 0.0F, 1.0F));
        if (alpha <= 0.002F) return;
        float[] fog = RenderSystem.getShaderFogColor();
        drawFullscreenVeil(fog[0], fog[1], fog[2], Math.min(1.0F, alpha));
    }

    /** Draws a screen-filling quad in normalised device coordinates without touching depth. */
    private static void drawFullscreenVeil(float r, float g, float b, float a) {
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(-1.0, -1.0, 0.0).color(r, g, b, a).endVertex();
        buf.vertex(1.0, -1.0, 0.0).color(r, g, b, a).endVertex();
        buf.vertex(1.0, 1.0, 0.0).color(r, g, b, a).endVertex();
        buf.vertex(-1.0, 1.0, 0.0).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buf.end());

        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }
}
