package com.fogiscoming.world;

import com.fogiscoming.FogIsComing;
import com.fogiscoming.config.FogConfig;
import com.fogiscoming.network.FogNetworking;
import com.fogiscoming.network.FogStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-authoritative fog progression.
 * <p>
 * Progress is measured from the overworld day time: every forward step of the clock
 * (normal ticking, sleeping, {@code /time add}) advances the fog, while freezing the
 * daylight cycle or setting the time backwards does not.
 */
public final class FogStageManager {
    public static final long TICKS_PER_DAY = 24000L;
    private static final int SYNC_INTERVAL = 20;

    private static FogStage currentStage = FogStage.CALM;
    private static boolean timeRunning;
    private static Set<String> affectedDims = Set.of("minecraft:overworld");
    private static List<? extends String> affectedDimsSource;

    private FogStageManager() {
    }

    // ------------------------------------------------------------------ ticking

    public static void onServerStarted(MinecraftServer server) {
        FogWorldData data = FogWorldData.get(server);
        currentStage = computeStage(data);
        FogIsComing.LOGGER.info("[FogIsComing] World loaded at {} (elapsed {} ticks, forced {}).",
                currentStage, data.getElapsedTicks(), data.getForcedStage());
    }

    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        FogWorldData data = FogWorldData.get(server);

        long now = overworld.getDayTime();
        long last = data.getLastDayTime();
        timeRunning = false;
        if (last >= 0) {
            long delta = now - last;
            if (delta > 0) {
                data.setElapsedTicks(data.getElapsedTicks() + delta);
                timeRunning = true;
            }
        }
        data.setLastDayTime(now);

        FogStage newStage = computeStage(data);
        if (data.getForcedStage() < 0 && newStage.id > data.getHighestStage()) {
            data.setHighestStage(newStage.id);
        }
        if (newStage != currentStage) {
            FogIsComing.LOGGER.info("[FogIsComing] Fog stage changed: {} -> {}", currentStage, newStage);
            currentStage = newStage;
            syncAll(server);
        } else if (server.getTickCount() % SYNC_INTERVAL == 0) {
            syncAll(server);
        }
    }

    // ------------------------------------------------------------------ queries

    public static FogStage getStage() {
        return currentStage;
    }

    public static FogStage computeStage(FogWorldData data) {
        if (data.getForcedStage() >= 0) {
            return FogStage.byId(data.getForcedStage());
        }
        long e = data.getElapsedTicks();
        int stage;
        if (e >= stageStartTick(3)) stage = 3;
        else if (e >= stageStartTick(2)) stage = 2;
        else if (e >= stageStartTick(1)) stage = 1;
        else stage = 0;
        // The deadly fog never recedes: a world that has reached Stage III stays there.
        if (data.getHighestStage() >= 3) stage = 3;
        return FogStage.byId(stage);
    }

    /** Tick (in elapsed ticks) at which the given stage begins, with stages forced to be monotonic. */
    public static long stageStartTick(int stage) {
        double d1 = FogConfig.SERVER.stage1Day.get();
        double d2 = Math.max(d1, FogConfig.SERVER.stage2Day.get());
        double d3 = Math.max(d2, FogConfig.SERVER.stage3Day.get());
        double day = switch (stage) {
            case 1 -> d1;
            case 2 -> d2;
            case 3 -> d3;
            default -> 0.0;
        };
        return (long) Math.round(day * TICKS_PER_DAY);
    }

    public static float stageDensity(int stage) {
        return switch (stage) {
            case 1 -> FogConfig.SERVER.stage1Density.get().floatValue();
            case 2 -> FogConfig.SERVER.stage2Density.get().floatValue();
            case 3 -> FogConfig.SERVER.stage3Density.get().floatValue();
            default -> 0.0F;
        };
    }

    /** Continuous fog density: ramps smoothly from the previous stage density over {@code transitionDays}. */
    public static float computeDensity(FogWorldData data, FogStage stage) {
        if (stage == FogStage.CALM) return 0.0F;
        float target = stageDensity(stage.id);
        if (data.getForcedStage() >= 0) return target;
        float from = stageDensity(stage.id - 1);
        long start = stageStartTick(stage.id);
        // If the stage was locked in by the "never recedes" rule, the ramp is already over.
        if (data.getElapsedTicks() < start) return target;
        double ramp = FogConfig.SERVER.transitionDays.get() * TICKS_PER_DAY;
        if (ramp <= 0) return target;
        float t = (float) Mth.clamp((data.getElapsedTicks() - start) / ramp, 0.0, 1.0);
        t = t * t * (3.0F - 2.0F * t); // smoothstep
        return Mth.lerp(t, from, target);
    }

    public static boolean isAffected(Level level) {
        List<? extends String> source = FogConfig.SERVER.affectedDimensions.get();
        if (source != affectedDimsSource) {
            affectedDims = new HashSet<>(source);
            affectedDimsSource = source;
        }
        return affectedDims.contains(level.dimension().location().toString());
    }

    public static boolean isTimeRunning() {
        return timeRunning;
    }

    // ------------------------------------------------------------------ mutations (commands)

    public static void forceStage(MinecraftServer server, int stage) {
        FogWorldData data = FogWorldData.get(server);
        data.setForcedStage(stage);
        refresh(server);
    }

    public static void setAutomatic(MinecraftServer server) {
        FogWorldData data = FogWorldData.get(server);
        data.setForcedStage(-1);
        refresh(server);
    }

    public static void reset(MinecraftServer server) {
        FogWorldData data = FogWorldData.get(server);
        data.setElapsedTicks(0L);
        data.setForcedStage(-1);
        data.setHighestStage(0);
        data.setLastDayTime(server.overworld().getDayTime());
        refresh(server);
    }

    public static void addDays(MinecraftServer server, double days) {
        FogWorldData data = FogWorldData.get(server);
        data.setElapsedTicks(data.getElapsedTicks() + Math.round(days * TICKS_PER_DAY));
        refresh(server);
    }

    private static void refresh(MinecraftServer server) {
        FogWorldData data = FogWorldData.get(server);
        currentStage = computeStage(data);
        if (data.getForcedStage() < 0 && currentStage.id > data.getHighestStage()) {
            data.setHighestStage(currentStage.id);
        }
        syncAll(server);
    }

    // ------------------------------------------------------------------ sync

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    public static void sync(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        FogWorldData data = FogWorldData.get(server);
        FogStage stage = computeStage(data);
        FogStatePacket packet = new FogStatePacket(
                stage.id,
                data.getForcedStage() >= 0,
                data.getElapsedTicks(),
                stageStartTick(1),
                stageStartTick(2),
                stageStartTick(3),
                computeDensity(data, stage),
                isAffected(player.level()),
                timeRunning);
        FogNetworking.sendTo(player, packet);
    }
}
