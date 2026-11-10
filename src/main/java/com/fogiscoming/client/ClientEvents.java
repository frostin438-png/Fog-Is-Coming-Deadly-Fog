package com.fogiscoming.client;

import com.fogiscoming.FogIsComing;
import com.fogiscoming.config.FogConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import com.fogiscoming.registry.FogEntities;
import com.fogiscoming.registry.FogParticles;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only event wiring. These classes are never loaded on a dedicated server. */
public final class ClientEvents {
    private ClientEvents() {
    }

    @Mod.EventBusSubscriber(modid = FogIsComing.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("fog_hud", FogHudOverlay.INSTANCE);
        }

        @SubscribeEvent
        public static void registerParticles(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(FogParticles.FOG_WISP.get(), FogWispParticle.Provider::new);
        }

        @SubscribeEvent
        public static void registerShaders(RegisterShadersEvent event) {
            FogShaders.register(event);
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(FogEntities.WATCHER.get(), WatcherRenderer::new);
            event.registerEntityRenderer(FogEntities.LIGHT_STALKER.get(), LightStalkerRenderer::new);
        }

        @SubscribeEvent
        public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(WatcherRenderer.LAYER, WatcherRenderer::createLayer);
            event.registerLayerDefinition(LightStalkerRenderer.LAYER, LightStalkerRenderer::createLayer);
        }
    }

    @Mod.EventBusSubscriber(modid = FogIsComing.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.isPaused()) return;
            ClientFogState.tick();
            FogSoundManager.tick();
            FogAtmosphere.tick();
            FogShaders.tick();
            FogScares.tick();
        }

        @SubscribeEvent
        public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
            FogScares.onCameraAngles(event);
        }

        @SubscribeEvent
        public static void onFov(ViewportEvent.ComputeFov event) {
            FogScares.onFov(event);
        }

        @SubscribeEvent
        public static void onRenderStage(RenderLevelStageEvent event) {
            FogShaders.onRenderStage(event);
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientFogState.reset();
            FogSoundManager.reset();
            FogScares.reset();
        }

        /** Thick fog swallows distant sounds: one-shot world sounds far from the listener get quieter. */
        @SubscribeEvent
        public static void onPlaySound(PlaySoundEvent event) {
            SoundInstance sound = event.getSound();
            if (sound == null || sound.getClass() != SimpleSoundInstance.class || sound.isRelative()) return;
            if (!FogConfig.CLIENT.muffleDistantSounds.get() || !ClientFogState.isAffected()) return;
            SoundSource src = sound.getSource();
            if (src == SoundSource.MASTER || src == SoundSource.MUSIC || src == SoundSource.RECORDS || src == SoundSource.VOICE) return;
            if (sound.getLocation().getNamespace().equals(FogIsComing.MOD_ID)) return;

            float density = ClientFogState.density(1.0F);
            if (density < 0.7F) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            double dist = mc.gameRenderer.getMainCamera().getPosition().distanceTo(new Vec3(sound.getX(), sound.getY(), sound.getZ()));
            if (dist < 10.0) return;
            float strength = Mth.clamp((density - 0.7F) / 0.25F, 0.0F, 1.0F) * 0.65F;
            float factor = 1.0F - strength * Mth.clamp((float) (dist - 10.0) / 24.0F, 0.0F, 1.0F);
            if (factor < 0.98F) event.setSound(new MuffledSoundInstance(sound, factor));
        }
    }
}
