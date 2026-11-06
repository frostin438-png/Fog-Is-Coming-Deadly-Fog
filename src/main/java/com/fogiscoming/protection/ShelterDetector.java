package com.fogiscoming.protection;

import com.fogiscoming.config.FogConfig;
import com.fogiscoming.registry.FogTags;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Detects whether a player stands inside a sealed shelter.
 * <p>
 * Algorithm: bounded breadth-first flood fill of the "air" the fog could travel through, starting at the
 * player's feet and head. A cell is fog-permeable if it has no collision shape, is an open door / trapdoor /
 * fence gate, or is in the {@code fogiscoming:fog_permeable} tag (leaves, ladders...). The interior is
 * <b>breached</b> when the fill reaches a cell that is open to the sky (at or above the
 * {@code MOTION_BLOCKING_NO_LEAVES} heightmap, so glass roofs still count as roofs), and
 * <b>not a shelter</b> when it exceeds the volume / radius budget (open land, huge caves) or touches unloaded
 * chunks. Tiny enclosures (a hole with one block on top) are rejected by minimum volume and floor area.
 * <p>
 * Results are cached per player and recomputed when the player moves to another block, every
 * {@link #CACHE_TICKS} ticks, or immediately after a nearby block is broken or placed.
 */
public final class ShelterDetector {
    public static final int CACHE_TICKS = 40;
    private static final Direction[] DIRS = Direction.values();
    private static final Map<UUID, Cached> CACHE = new HashMap<>();

    private ShelterDetector() {
    }

    public enum Reason {
        SEALED("sealed"),
        OPEN_SKY("opening to the sky"),
        TOO_LARGE("interior too large / open area"),
        TOO_FAR("interior extends beyond scan radius"),
        TOO_SMALL("too small to be a shelter"),
        UNLOADED("touches unloaded chunks"),
        DISABLED("shelter protection disabled");

        public final String text;

        Reason(String text) {
            this.text = text;
        }
    }

    public record ShelterResult(boolean sheltered, Reason reason, int volume, int footprint, int leaks) {
    }

    private record Cached(ShelterResult result, long pos, ResourceKey<Level> dim, long tick) {
    }

    public static ShelterResult check(ServerPlayer player) {
        if (!FogConfig.SERVER.shelterEnabled.get()) {
            return new ShelterResult(false, Reason.DISABLED, 0, 0, 0);
        }
        ServerLevel level = player.serverLevel();
        BlockPos feet = player.blockPosition();
        long now = level.getGameTime();
        Cached c = CACHE.get(player.getUUID());
        if (c != null && c.pos == feet.asLong() && c.dim == level.dimension() && now - c.tick < CACHE_TICKS && now >= c.tick) {
            return c.result;
        }
        ShelterResult result = scan(level, feet);
        CACHE.put(player.getUUID(), new Cached(result, feet.asLong(), level.dimension(), now));
        return result;
    }

    /** Drops cached results for players near a changed block, so a broken wall is noticed at once. */
    public static void invalidateNear(Level level, BlockPos changed) {
        if (CACHE.isEmpty() || level.isClientSide()) return;
        int r = FogConfig.SERVER.shelterMaxRadius.get() + 2;
        double r2 = (double) r * r;
        for (var player : level.players()) {
            if (player.blockPosition().distSqr(changed) <= r2) {
                CACHE.remove(player.getUUID());
            }
        }
    }

    public static void forget(UUID id) {
        CACHE.remove(id);
    }

    public static void clear() {
        CACHE.clear();
    }

    public static ShelterResult scan(ServerLevel level, BlockPos feet) {
        final int maxVolume = FogConfig.SERVER.shelterMaxVolume.get();
        final int maxRadius = FogConfig.SERVER.shelterMaxRadius.get();
        final int minVolume = FogConfig.SERVER.shelterMinVolume.get();
        final int minFootprint = FogConfig.SERVER.shelterMinFootprint.get();
        final int tolerance = FogConfig.SERVER.shelterLeakTolerance.get();
        final int minY = level.getMinBuildHeight();
        final int maxY = level.getMaxBuildHeight();

        LongOpenHashSet visited = new LongOpenHashSet(256);
        LongOpenHashSet columns = new LongOpenHashSet(64);
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue(256);
        int leaks = 0;

        BlockPos head = feet.above();
        for (BlockPos start : new BlockPos[]{feet, head}) {
            if (!level.isLoaded(start)) return new ShelterResult(false, Reason.UNLOADED, 0, 0, 0);
            if (isOpenSky(level, start)) return new ShelterResult(false, Reason.OPEN_SKY, 0, 0, 1);
            if (visited.add(start.asLong())) {
                queue.enqueue(start.asLong());
                columns.add(column(start.getX(), start.getZ()));
            }
        }

        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            cur.set(queue.dequeueLong());
            for (Direction dir : DIRS) {
                next.setWithOffset(cur, dir);
                long key = next.asLong();
                if (visited.contains(key)) continue;

                int y = next.getY();
                if (y < minY || y >= maxY) {
                    // Leaving the world vertically: below bedrock is sealed, above the build limit is sky.
                    if (y >= maxY) return new ShelterResult(false, Reason.OPEN_SKY, visited.size(), columns.size(), leaks + 1);
                    continue;
                }
                if (Math.abs(next.getX() - feet.getX()) > maxRadius
                        || Math.abs(y - feet.getY()) > maxRadius
                        || Math.abs(next.getZ() - feet.getZ()) > maxRadius) {
                    return new ShelterResult(false, Reason.TOO_FAR, visited.size(), columns.size(), leaks);
                }
                if (!level.isLoaded(next)) {
                    return new ShelterResult(false, Reason.UNLOADED, visited.size(), columns.size(), leaks);
                }
                BlockState state = level.getBlockState(next);
                if (!isPermeable(level, next, state)) continue;

                visited.add(key);
                if (isOpenSky(level, next)) {
                    leaks++;
                    if (leaks > tolerance) {
                        return new ShelterResult(false, Reason.OPEN_SKY, visited.size(), columns.size(), leaks);
                    }
                    continue; // do not expand outside
                }
                if (visited.size() > maxVolume) {
                    return new ShelterResult(false, Reason.TOO_LARGE, visited.size(), columns.size(), leaks);
                }
                columns.add(column(next.getX(), next.getZ()));
                queue.enqueue(key);
            }
        }

        int volume = visited.size() - leaks;
        if (volume < minVolume || columns.size() < minFootprint) {
            return new ShelterResult(false, Reason.TOO_SMALL, volume, columns.size(), leaks);
        }
        return new ShelterResult(true, Reason.SEALED, volume, columns.size(), leaks);
    }

    private static boolean isOpenSky(ServerLevel level, BlockPos pos) {
        return pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
    }

    /** Can fog flow through this block? */
    public static boolean isPermeable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) return true;
        if (state.is(FogTags.FOG_PERMEABLE)) return true;
        if (state.getBlock() instanceof DoorBlock) return state.getValue(DoorBlock.OPEN);
        if (state.getBlock() instanceof TrapDoorBlock) return state.getValue(TrapDoorBlock.OPEN);
        if (state.getBlock() instanceof FenceGateBlock) return state.getValue(FenceGateBlock.OPEN);
        return state.getCollisionShape(level, pos).isEmpty();
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
