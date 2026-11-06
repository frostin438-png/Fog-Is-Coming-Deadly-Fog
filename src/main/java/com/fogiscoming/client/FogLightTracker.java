package com.fogiscoming.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the brightest light sources around the camera for the fog halo shader.
 * Runs on the client twice a second over a small cube of already-loaded blocks; keeps the nearest eight.
 */
@OnlyIn(Dist.CLIENT)
public final class FogLightTracker {
    public static final int MAX_LIGHTS = 8;
    private static final int RADIUS = 14;
    private static final int MIN_EMISSION = 10;

    /** x, y, z (block centre, world space), intensity, r, g, b, flicker (0/1). */
    public record LightSource(double x, double y, double z, float intensity, float r, float g, float b, float flicker) {
    }

    private static List<LightSource> lights = List.of();
    private static int cooldown;

    private FogLightTracker() {
    }

    public static List<LightSource> lights() {
        return lights;
    }

    static void tick(boolean wanted) {
        if (!wanted) {
            lights = List.of();
            return;
        }
        if (--cooldown > 0) return;
        cooldown = 10;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        BlockPos center = BlockPos.containing(cam);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<LightSource> found = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS / 2; dy <= RADIUS / 2; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir()) continue;
                    int emission = state.getLightEmission(mc.level, pos);
                    if (emission < MIN_EMISSION) continue;
                    found.add(describe(state, pos, emission));
                }
            }
        }
        found.sort(Comparator.comparingDouble(l -> distSq(l, cam)));
        lights = found.size() > MAX_LIGHTS ? List.copyOf(found.subList(0, MAX_LIGHTS)) : List.copyOf(found);
    }

    private static double distSq(LightSource l, Vec3 cam) {
        double x = l.x - cam.x, y = l.y - cam.y, z = l.z - cam.z;
        return x * x + y * y + z * z;
    }

    private static LightSource describe(BlockState state, BlockPos pos, int emission) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        String name = id == null ? "" : id.getPath();
        float intensity = emission / 15.0F;
        double yOffset = 0.5;
        float r = 1.0F, g = 0.62F, b = 0.32F, flicker = 0.0F; // warm flame by default
        if (name.contains("soul")) {
            r = 0.35F; g = 0.78F; b = 1.0F; flicker = 1.0F;
        } else if (name.contains("sea_lantern") || name.contains("beacon") || name.contains("end_rod") || name.contains("conduit")) {
            r = 0.72F; g = 0.9F; b = 1.0F;
        } else if (name.contains("glowstone") || name.contains("froglight") || name.contains("shroomlight")) {
            r = 1.0F; g = 0.82F; b = 0.5F;
        } else if (name.contains("redstone_lamp")) {
            r = 1.0F; g = 0.7F; b = 0.42F;
        } else if (name.contains("lava") || name.contains("magma")) {
            r = 1.0F; g = 0.42F; b = 0.12F; flicker = 1.0F; intensity *= 0.7F; yOffset = 0.9;
        } else if (name.contains("torch") || name.contains("fire") || name.contains("candle")) {
            flicker = 1.0F; yOffset = 0.65;
        }
        return new LightSource(pos.getX() + 0.5, pos.getY() + yOffset, pos.getZ() + 0.5, intensity, r, g, b, flicker);
    }
}
