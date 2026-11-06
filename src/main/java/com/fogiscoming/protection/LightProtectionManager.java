package com.fogiscoming.protection;

import com.fogiscoming.config.FogConfig;
import com.fogiscoming.registry.FogTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides whether artificial light protects a player.
 * <p>
 * Two conditions must both hold:
 * <ol>
 *     <li>The BLOCK light layer (never the sky layer) at the player's feet or head is at least
 *     {@code minBlockLight}. Sunlight is therefore irrelevant, and light that is walled off from the
 *     player does not reach them.</li>
 *     <li>A valid, currently lit light source exists within {@code lightRadius} blocks (sphere).
 *     Valid = in the {@code fogiscoming:fog_safe_lights} tag, or (if enabled) any emitter of at least
 *     {@code minAnyEmitterLevel} that is not in {@code fogiscoming:fog_light_blacklist}.</li>
 * </ol>
 * The scan only happens if condition 1 passes, walks precomputed offsets sorted by distance
 * and stops at the first hit, so a typical check touches a handful of blocks.
 */
public final class LightProtectionManager {
    private static int cachedRadius = -1;
    private static int[] offsets = new int[0]; // packed dx,dy,dz triples sorted by distance

    private LightProtectionManager() {
    }

    public record LightResult(boolean protectedByLight, int blockLight, BlockPos sourcePos, BlockState sourceState,
                              double distance) {
        static LightResult none(int blockLight) {
            return new LightResult(false, blockLight, null, null, -1);
        }
    }

    public static LightResult check(ServerLevel level, BlockPos feet) {
        int minLight = FogConfig.SERVER.minBlockLight.get();
        int light = Math.max(level.getBrightness(LightLayer.BLOCK, feet), level.getBrightness(LightLayer.BLOCK, feet.above()));
        if (light < minLight) {
            return LightResult.none(light);
        }

        int radius = FogConfig.SERVER.lightRadius.get();
        int[] offs = offsets(radius);
        boolean any = FogConfig.SERVER.allowAnyLightEmitter.get();
        int minEmitter = FogConfig.SERVER.minAnyEmitterLevel.get();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < offs.length; i += 3) {
            pos.set(feet.getX() + offs[i], feet.getY() + offs[i + 1], feet.getZ() + offs[i + 2]);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            int emission = state.getLightEmission(level, pos);
            if (emission <= 0) continue; // unlit campfires, lamps that are off, extinguished candles...
            if (isValidSource(state, emission, any, minEmitter)) {
                double dist = Math.sqrt(offs[i] * offs[i] + offs[i + 1] * offs[i + 1] + offs[i + 2] * offs[i + 2]);
                return new LightResult(true, light, pos.immutable(), state, dist);
            }
        }
        return LightResult.none(light);
    }

    private static boolean isValidSource(BlockState state, int emission, boolean any, int minEmitter) {
        if (state.is(FogTags.LIGHT_BLACKLIST)) return false;
        if (state.is(FogTags.SAFE_LIGHTS)) return true;
        return any && emission >= minEmitter;
    }

    private static int[] offsets(int radius) {
        if (radius == cachedRadius) return offsets;
        List<int[]> list = new ArrayList<>();
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= r2) list.add(new int[]{dx, dy, dz});
                }
            }
        }
        list.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1] + o[2] * o[2]));
        int[] packed = new int[list.size() * 3];
        for (int i = 0; i < list.size(); i++) {
            packed[i * 3] = list.get(i)[0];
            packed[i * 3 + 1] = list.get(i)[1];
            packed[i * 3 + 2] = list.get(i)[2];
        }
        offsets = packed;
        cachedRadius = radius;
        return packed;
    }
}
