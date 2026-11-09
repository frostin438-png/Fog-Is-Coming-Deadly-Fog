package com.fogiscoming.world;

import com.fogiscoming.config.FogConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The deadly fog eats light: at night in Stage III, every 1.5–4 minutes per player, one torch or lit campfire
 * under the open sky within 14 blocks goes out with a hiss. Lanterns, glowstone and other light blocks are never
 * touched, and nothing inside a roofed building is affected.
 */
public final class LightEater {
    private static final int RADIUS = 14;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();

    private LightEater() {
    }

    public static void clear() {
        COOLDOWN.clear();
    }

    public static void forget(UUID player) {
        COOLDOWN.remove(player);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0 || !FogConfig.SERVER.fogEatsLight.get()) return;
        if (!FogStageManager.getStage().isDeadly()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            ServerLevel level = player.serverLevel();
            if (!FogStageManager.isAffected(level) || !level.isNight()) continue;
            UUID id = player.getUUID();
            int cd = COOLDOWN.getOrDefault(id, 60 + RANDOM.nextInt(60)) - 1;
            if (cd > 0) {
                COOLDOWN.put(id, cd);
                continue;
            }
            COOLDOWN.put(id, eatOne(level, player.blockPosition()) ? 90 + RANDOM.nextInt(150) : 30);
        }
    }

    /** Snuffs out one random outdoor torch or campfire near {@code center}. Returns true if one went out. */
    public static boolean eatOne(ServerLevel level, BlockPos center) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -6; dy <= 6; dy++) {
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.isLoaded(p)) continue;
                    BlockState s = level.getBlockState(p);
                    if (isEdible(s) && level.canSeeSky(p)) found.add(p.immutable());
                }
            }
        }
        if (found.isEmpty()) return false;
        BlockPos pos = found.get(RANDOM.nextInt(found.size()));
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CampfireBlock) {
            level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), 3);
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.55F + RANDOM.nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 10, 0.15, 0.2, 0.15, 0.02);
        return true;
    }

    private static boolean isEdible(BlockState s) {
        if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH)) return true;
        return s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT);
    }
}
