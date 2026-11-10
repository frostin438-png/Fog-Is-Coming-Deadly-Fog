package com.fogiscoming.world;

import com.fogiscoming.config.FogConfig;
import com.fogiscoming.entity.LightStalkerEntity;
import com.fogiscoming.registry.FogEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sends Light Stalkers after the players' lights. From Stage II on, day and night: one Stalker per player at a
 * time, every 2–4 minutes (1.5–3 in Stage III), and only when there is a torch or lantern near the player to take.
 * It appears on the ground out in the fog, preferably behind the player.
 */
public final class LightStalkerDirector {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();
    private static final Map<UUID, UUID> ACTIVE = new HashMap<>();

    private LightStalkerDirector() {
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
        if (server.getTickCount() % 20 != 10 || !FogConfig.SERVER.stalkersEnabled.get()) return;
        FogStage stage = FogStageManager.getStage();
        if (stage.id < 2) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) continue;
            ServerLevel level = player.serverLevel();
            if (!FogStageManager.isAffected(level)) continue;

            UUID id = player.getUUID();
            UUID existing = ACTIVE.get(id);
            if (existing != null) {
                Entity e = level.getEntity(existing);
                if (e != null && e.isAlive()) continue;
                ACTIVE.remove(id);
            }
            int cd = COOLDOWN.getOrDefault(id, 60) - 1;
            if (cd > 0) {
                COOLDOWN.put(id, cd);
                continue;
            }
            // Nothing to take here: look again in a while.
            if (LightStalkerEntity.findLight(level, player.blockPosition(), 16, null) == null) {
                COOLDOWN.put(id, 20);
                continue;
            }
            LightStalkerEntity stalker = spawnFor(player, -1);
            if (stalker != null) {
                ACTIVE.put(id, stalker.getUUID());
                COOLDOWN.put(id, nextCooldown(stage));
            } else {
                COOLDOWN.put(id, 10);
            }
        }
    }

    private static int nextCooldown(FogStage stage) {
        int base = stage.isDeadly() ? 90 : 120;
        double freq = FogConfig.SERVER.stalkerFrequency.get();
        return Math.max(5, (int) ((base + RANDOM.nextInt(120)) / freq));
    }

    /**
     * Places a Light Stalker for this player. {@code distance <= 0} picks a spot out in the fog, mostly behind
     * the player; a positive distance puts it straight ahead (for testing). Returns null if no spot was found.
     */
    public static LightStalkerEntity spawnFor(ServerPlayer player, double distance) {
        ServerLevel level = player.serverLevel();
        boolean deadly = FogStageManager.getStage().isDeadly();
        double min = distance > 0 ? distance : (deadly ? 14.0 : 18.0);
        double max = distance > 0 ? distance + 1.0 : (deadly ? 20.0 : 28.0);
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        for (int attempt = 0; attempt < 20; attempt++) {
            double offset;
            if (distance > 0) {
                offset = (RANDOM.nextFloat() - 0.5F) * 0.4F;
            } else if (RANDOM.nextFloat() < 0.7F) {
                offset = Math.PI + (RANDOM.nextFloat() - 0.5F) * 2.4F; // behind
            } else {
                offset = RANDOM.nextFloat() * Mth.TWO_PI;
            }
            double angle = yaw + offset;
            double dist = Mth.lerp(RANDOM.nextDouble(), min, max);
            int bx = Mth.floor(player.getX() - Math.sin(angle) * dist);
            int bz = Mth.floor(player.getZ() + Math.cos(angle) * dist);
            if (!level.isLoaded(new BlockPos(bx, player.getBlockY(), bz))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            if (Math.abs(y - player.getY()) > 10) continue;
            BlockPos pos = new BlockPos(bx, y, bz);
            if (!canStandAt(level, pos)) continue;
            LightStalkerEntity stalker = FogEntities.LIGHT_STALKER.get().create(level);
            if (stalker == null) return null;
            stalker.moveTo(bx + 0.5, y, bz + 0.5, RANDOM.nextFloat() * 360.0F, 0.0F);
            if (level.addFreshEntity(stalker)) return stalker;
        }
        return null;
    }

    private static boolean canStandAt(ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.getCollisionShape(level, pos.below()).isEmpty() || !below.getFluidState().isEmpty()) return false;
        for (int i = 0; i < 2; i++) {
            BlockPos p = pos.above(i);
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty() || !s.getFluidState().isEmpty()) return false;
        }
        return true;
    }
}
