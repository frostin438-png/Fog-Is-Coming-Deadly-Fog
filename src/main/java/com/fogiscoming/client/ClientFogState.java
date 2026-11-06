package com.fogiscoming.client;

import com.fogiscoming.protection.ExposureStatus;
import com.fogiscoming.world.FogStage;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client mirror of the server fog state plus purely visual smoothing.
 * Everything here is cosmetic — gameplay decisions are made on the server.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientFogState {
    /** Per-tick exponential approach factor: ~95% of a change after ~7.5 seconds. */
    private static final float SMOOTHING = 0.02F;

    static boolean initialized;
    static FogStage stage = FogStage.CALM;
    static boolean forced;
    static long elapsed;
    static long stage1Tick, stage2Tick, stage3Tick;
    static float targetDensity;
    static boolean affected;
    static boolean timeRunning;
    static long packetClientTick;

    static ExposureStatus exposure = ExposureStatus.NONE;
    static int secondsUntilDamage;
    static long exposureChangedTick;

    /** Client tick counter (does not advance while paused). */
    static long clientTicks;
    static long stageTitleTick = Long.MIN_VALUE;

    private static float prevDensity;
    private static float density;
    private static float prevExposureBlend;
    private static float exposureBlend;

    private ClientFogState() {
    }

    // ---------------------------------------------------------------- updates

    static void applyState(int stageId, boolean forcedStage, long elapsedTicks, long s1, long s2, long s3,
                           float dens, boolean affectedDim, boolean running) {
        FogStage newStage = FogStage.byId(stageId);
        boolean firstPacket = !initialized;
        boolean dimChanged = affected != affectedDim;
        if (firstPacket) {
            if (newStage != FogStage.CALM) stageTitleTick = clientTicks;
        } else if (newStage.id > stage.id) {
            stageTitleTick = clientTicks;
            FogSoundManager.onStageAdvanced(newStage);
        }
        stage = newStage;
        forced = forcedStage;
        elapsed = elapsedTicks;
        stage1Tick = s1;
        stage2Tick = s2;
        stage3Tick = s3;
        targetDensity = affectedDim ? Mth.clamp(dens, 0.0F, 1.0F) : 0.0F;
        affected = affectedDim;
        timeRunning = running;
        packetClientTick = clientTicks;
        if (firstPacket || dimChanged) {
            // Do not fade in from nothing when joining a foggy world or entering/leaving the overworld.
            density = prevDensity = targetDensity;
        }
        initialized = true;
    }

    static void applyExposure(int status, int seconds) {
        ExposureStatus s = ExposureStatus.byId(status);
        if (s.isSafe() != exposure.isSafe() || s.isUnsafe() != exposure.isUnsafe()) {
            exposureChangedTick = clientTicks;
        }
        exposure = s;
        secondsUntilDamage = seconds;
    }

    static void tick() {
        clientTicks++;
        prevDensity = density;
        density += (targetDensity - density) * SMOOTHING;
        if (Math.abs(targetDensity - density) < 0.0005F) density = targetDensity;

        prevExposureBlend = exposureBlend;
        float exposureTarget = switch (exposure) {
            case EXPOSED -> 1.0F;
            case GRACE -> 0.55F;
            default -> 0.0F;
        };
        exposureBlend += (exposureTarget - exposureBlend) * 0.05F;
    }

    static void reset() {
        initialized = false;
        stage = FogStage.CALM;
        forced = false;
        elapsed = 0;
        targetDensity = density = prevDensity = 0;
        affected = false;
        exposure = ExposureStatus.NONE;
        exposureBlend = prevExposureBlend = 0;
        stageTitleTick = Long.MIN_VALUE;
    }

    // ---------------------------------------------------------------- queries

    public static boolean isInitialized() {
        return initialized;
    }

    public static FogStage stage() {
        return stage;
    }

    public static boolean isAffected() {
        return affected;
    }

    /** Smoothed, frame-interpolated fog density (0..1). */
    public static float density(float partialTick) {
        return Mth.lerp(partialTick, prevDensity, density);
    }

    public static float exposureBlend(float partialTick) {
        return Mth.lerp(partialTick, prevExposureBlend, exposureBlend);
    }

    public static ExposureStatus exposure() {
        return exposure;
    }

    /** Elapsed fog ticks, extrapolated between server packets for a smooth countdown. */
    public static long estimatedElapsed() {
        long since = timeRunning ? Math.min(40, Math.max(0, clientTicks - packetClientTick)) : 0;
        return elapsed + since;
    }

    public static long ticksUntilFog() {
        return Math.max(0, stage1Tick - estimatedElapsed());
    }

    /**
     * 0..1: how close the fog is. Stays 0 for most of the countdown and rises over its last third
     * (at least 2 in-game hours, at most one in-game day). Drives the red HUD and the clock volume.
     */
    public static float countdownDanger() {
        if (forced) return 0.0F;
        long window = Math.min(24000L, Math.max(2400L, stage1Tick / 3L));
        float t = 1.0F - Mth.clamp(ticksUntilFog() / (float) window, 0.0F, 1.0F);
        return FogRenderer.smooth(t);
    }
}
