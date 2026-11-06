package com.fogiscoming.client;

import com.fogiscoming.config.FogConfig;
import com.fogiscoming.protection.ExposureStatus;
import com.fogiscoming.registry.FogSounds;
import com.fogiscoming.world.FogStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Atmosphere: rare gusts of wind, distant low rumbles, very rare unidentifiable groans in the deadly fog,
 * and a low drone + slow heartbeat while exposed. Each category has its own long randomised cooldown and
 * never overlaps with itself, so the soundscape stays sparse and unsettling instead of noisy.
 */
@OnlyIn(Dist.CLIENT)
public final class FogSoundManager {
    private static final RandomSource RANDOM = RandomSource.create();

    private static int windCooldown = 200;
    private static int rumbleCooldown = 600;
    private static int groanCooldown = 1800;
    private static int heartbeatCooldown;
    private static SoundInstance lastWind, lastRumble, lastGroan;
    private static DroneSound drone;
    private static boolean clockTock;

    private FogSoundManager() {
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) return;
        boolean enabled = FogConfig.CLIENT.soundsEnabled.get() && ClientFogState.isAffected();
        FogStage stage = ClientFogState.stage();
        float density = ClientFogState.density(1.0F);

        tickClock(mc);
        tickDrone(mc, enabled && stage.isDeadly());
        if (!enabled || stage == FogStage.CALM || density < 0.05F) return;

        boolean night = mc.level.getSkyDarken(1.0F) < 0.5F;
        float vol = FogConfig.CLIENT.ambientVolume.get().floatValue();
        var sm = mc.getSoundManager();

        // Wind gusts: from Stage I. Every ~35-80 s, more often as the fog thickens.
        if (--windCooldown <= 0 && (lastWind == null || !sm.isActive(lastWind))) {
            lastWind = playDistant(FogSounds.WIND_GUST.get(), 0.35F + 0.35F * density, 0.85F + RANDOM.nextFloat() * 0.3F, vol, 6, 12);
            windCooldown = cooldown(stage == FogStage.ARRIVAL ? 1200 : 700, 1600);
        }
        // Distant low rumbles: from Stage II, rarer by day.
        if (stage.id >= 2 && --rumbleCooldown <= 0 && (lastRumble == null || !sm.isActive(lastRumble))) {
            lastRumble = playDistant(FogSounds.DISTANT_RUMBLE.get(), 0.55F, 0.8F + RANDOM.nextFloat() * 0.25F, vol, 24, 40);
            rumbleCooldown = cooldown(night ? 1400 : 2200, 3000);
        }
        // Something far away in the fog: deadly stage (or Stage II at night), very rare.
        if ((stage.isDeadly() || (stage.id == 2 && night)) && --groanCooldown <= 0
                && (lastGroan == null || !sm.isActive(lastGroan))) {
            lastGroan = playDistant(FogSounds.DISTANT_GROAN.get(), 0.45F, 0.75F + RANDOM.nextFloat() * 0.3F, vol, 28, 48);
            groanCooldown = cooldown(night ? 2400 : 4000, 4800);
        }
        // Heartbeat while the grace countdown runs.
        ExposureStatus ex = ClientFogState.exposure();
        if (ex.isUnsafe() && --heartbeatCooldown <= 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(FogSounds.HEARTBEAT.get(), 1.0F, 0.5F * vol));
            heartbeatCooldown = ex == ExposureStatus.EXPOSED ? 22 : 30;
        }
    }

    /**
     * Mechanical clock during the countdown: tick, tock, once per real second. Quiet in the background
     * for most of the day, steadily louder as the fog approaches. Silent while the fog is held back.
     */
    private static void tickClock(Minecraft mc) {
        if (!FogConfig.CLIENT.soundsEnabled.get() || !FogConfig.CLIENT.clockTicking.get()) return;
        if (!ClientFogState.isInitialized() || !ClientFogState.isAffected() || ClientFogState.stage() != FogStage.CALM) return;
        if (ClientFogState.forced || !ClientFogState.timeRunning || ClientFogState.ticksUntilFog() <= 0) return;
        if (ClientFogState.clientTicks % 20 != 0) return;
        float danger = ClientFogState.countdownDanger();
        float volume = (0.14F + 0.46F * danger) * FogConfig.CLIENT.clockVolume.get().floatValue();
        if (volume <= 0.001F) return;
        clockTock = !clockTock;
        SoundEvent sound = clockTock ? FogSounds.CLOCK_TOCK.get() : FogSounds.CLOCK_TICK.get();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, volume));
    }

    static void onStageAdvanced(FogStage stage) {
        if (!FogConfig.CLIENT.soundsEnabled.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float vol = FogConfig.CLIENT.ambientVolume.get().floatValue();
        float pitch = stage.isDeadly() ? 0.7F : 0.85F;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(FogSounds.FOG_ARRIVAL.get(), pitch, 0.8F * vol));
    }

    static void reset() {
        if (drone != null) drone.finish();
        drone = null;
        lastWind = lastRumble = lastGroan = null;
        windCooldown = 200;
        rumbleCooldown = 600;
        groanCooldown = 1800;
    }

    private static void tickDrone(Minecraft mc, boolean deadly) {
        boolean wanted = deadly && ClientFogState.exposure() != ExposureStatus.NONE;
        if (wanted && (drone == null || drone.isStopped())) {
            drone = new DroneSound(FogSounds.EXPOSED_DRONE.get());
            mc.getSoundManager().play(drone);
        } else if (wanted) {
            drone.finishing = false;
        } else if (!wanted && drone != null) {
            drone.finish();
            if (drone.isStopped()) drone = null;
        }
    }

    /** Plays a positional sound some distance away from the player, in a random direction. */
    private static SoundInstance playDistant(SoundEvent event, float volume, float pitch, float volumeScale, int minDist, int maxDist) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null) return null;
        double angle = RANDOM.nextDouble() * Math.PI * 2.0;
        double dist = minDist + RANDOM.nextDouble() * (maxDist - minDist);
        double x = p.getX() + Math.cos(angle) * dist;
        double z = p.getZ() + Math.sin(angle) * dist;
        double y = p.getEyeY() + RANDOM.nextDouble() * 6.0 - 2.0;
        // Volume > 1 extends the audible range of linear attenuation (16 blocks * volume).
        float rangeVolume = Math.max(volume, (float) (dist / 16.0) + 0.5F) * volumeScale;
        SoundInstance sound = new SimpleSoundInstance(event, SoundSource.AMBIENT, rangeVolume, pitch, RANDOM, x, y, z);
        mc.getSoundManager().play(sound);
        return sound;
    }

    private static int cooldown(int base, int spread) {
        return base + RANDOM.nextInt(Math.max(1, spread));
    }

    /** Looping low drone that fades in/out with exposure. */
    private static final class DroneSound extends AbstractTickableSoundInstance {
        private boolean finishing;

        DroneSound(SoundEvent event) {
            super(event, SoundSource.AMBIENT, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = 0;
            this.y = 0;
            this.z = 0;
            this.volume = 0.001F;
        }

        void finish() {
            finishing = true;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            float scale = FogConfig.CLIENT.ambientVolume.get().floatValue();
            float target = finishing ? 0.0F : switch (ClientFogState.exposure()) {
                case EXPOSED -> 0.85F;
                case GRACE -> 0.55F;
                default -> 0.12F; // safe: barely there, the fog is still outside
            };
            target *= scale;
            volume += (target - volume) * 0.04F;
            volume = Mth.clamp(volume, 0.0F, 2.0F);
            if (finishing && volume < 0.01F) stop();
        }
    }
}
