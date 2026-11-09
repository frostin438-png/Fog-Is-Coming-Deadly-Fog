package com.fogiscoming.world;

import com.fogiscoming.config.FogConfig;
import com.fogiscoming.entity.WatcherEntity;
import com.fogiscoming.registry.FogEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Decides when and where Watchers appear. At most one Watcher per player at a time, placed near the edge of
 * visibility, either at the side of the player's view or behind them.
 * <ul>
 *     <li>Stage II: only at night, every 2.5–6 minutes.</li>
 *     <li>Stage III: every 1–2.5 minutes at night, 2.5–5 minutes by day.</li>
 * </ul>
 */
public final class WatcherDirector {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();
    private static final Map<UUID, UUID> ACTIVE = new HashMap<>();

    private WatcherDirector() {
    }

    public static void clear() {
        COOLDOWN.clear();
        ACTIVE.clear();
    }

    public static void forget(UUID player) {
        COOLDOWN.remove(player);
        ACTIVE.remove(player);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0 || !FogConfig.SERVER.watchersEnabled.get()) return;
        FogStage stage = FogStageManager.getStage();
        if (stage.id < 2) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) continue;
            ServerLevel level = player.serverLevel();
            if (!FogStageManager.isAffected(level)) continue;
            boolean night = level.isNight();
            if (stage.id == 2 && !night) continue;

            UUID id = player.getUUID();
            UUID existing = ACTIVE.get(id);
            if (existing != null) {
                Entity e = level.getEntity(existing);
                if (e != null && e.isAlive()) continue;
                ACTIVE.remove(id);
            }
            int cd = COOLDOWN.getOrDefault(id, 45) - 1;
            if (cd > 0) {
                COOLDOWN.put(id, cd);
                continue;
            }
            WatcherEntity w = spawnFor(player, -1);
            if (w != null) {
                ACTIVE.put(id, w.getUUID());
                COOLDOWN.put(id, nextCooldown(stage, night));
            } else {
                COOLDOWN.put(id, 10);
            }
        }
    }

    private static int nextCooldown(FogStage stage, boolean night) {
        int base, spread;
        if (!stage.isDeadly()) {
            base = 150;
            spread = 210;
        } else if (night) {
            base = 60;
            spread = 90;
        } else {
            base = 150;
            spread = 150;
        }
        double freq = FogConfig.SERVER.watcherFrequency.get();
        return Math.max(5, (int) ((base + RANDOM.nextInt(spread)) / freq));
    }

    /** Typical visibility in blocks for the current stage (mirrors the client fog distance). */
    private static double visibility() {
        float density = FogStageManager.stageDensity(FogStageManager.getStage().id);
        return 8.0 + 248.0 * (1.0 - density);
    }

    /**
     * Places a Watcher for this player. {@code distance <= 0} picks a spot near the edge of the fog.
     * Returns null if no suitable spot was found.
     */
    public static WatcherEntity spawnFor(ServerPlayer player, double distance) {
        ServerLevel level = player.serverLevel();
        double vis = visibility();
        double min = distance > 0 ? distance : Math.max(8.0, vis * 0.45);
        double max = distance > 0 ? distance + 1.0 : Math.max(11.0, vis * 0.65);
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        for (int attempt = 0; attempt < 16; attempt++) {
            double offset;
            if (distance > 0) {
                offset = (RANDOM.nextFloat() - 0.5F) * 0.5F; // in front of the player, for testing
            } else if (RANDOM.nextFloat() < 0.55F) {
                // At the side of the view: noticed out of the corner of the eye.
                offset = (RANDOM.nextBoolean() ? 1 : -1) * Mth.lerp(RANDOM.nextFloat(), 0.35F, 0.95F);
            } else {
                // Behind: there when the player turns around.
                offset = Math.PI + (RANDOM.nextFloat() - 0.5F) * 2.0F;
            }
            double angle = yaw + offset;
            double dist = Mth.lerp(RANDOM.nextDouble(), min, max);
            double x = player.getX() - Math.sin(angle) * dist;
            double z = player.getZ() + Math.cos(angle) * dist;
            int bx = Mth.floor(x), bz = Mth.floor(z);
            if (!level.isLoaded(new BlockPos(bx, 0, bz))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            if (Math.abs(y - player.getY()) > 10) continue;
            BlockPos pos = new BlockPos(bx, y, bz);
            if (!WatcherEntity.canStandAt(level, pos)) continue;
            WatcherEntity w = FogEntities.WATCHER.get().create(level);
            if (w == null) return null;
            w.moveTo(bx + 0.5, y, bz + 0.5, 0.0F, 0.0F);
            w.setTargetPlayer(player.getUUID());
            if (level.addFreshEntity(w)) return w;
        }
        return null;
    }
}
