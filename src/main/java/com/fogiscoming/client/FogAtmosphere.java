package com.fogiscoming.client;

import com.fogiscoming.config.FogConfig;
import com.fogiscoming.registry.FogParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Makes the deadly fog feel alive: spawns drifting fog wisps around the camera (only in the open air,
 * never inside houses or caves) and provides the slow "breathing" wave used by {@link FogRenderer}.
 */
@OnlyIn(Dist.CLIENT)
public final class FogAtmosphere {
    private static final RandomSource RANDOM = RandomSource.create();
    /** Slowly wandering wind direction (radians). */
    private static float windAngle = RANDOM.nextFloat() * Mth.TWO_PI;

    private FogAtmosphere() {
    }

    static float windAngle() {
        return windAngle;
    }

    /** 0..1: how much of the deadly-stage look applies at this density. */
    public static float deadliness(float density) {
        return Mth.clamp((density - 0.85F) / 0.1F, 0.0F, 1.0F);
    }

    /**
     * Slow, irregular wave in [-1, 1]: the fog rolls in and recedes over roughly 10-20 seconds.
     * A sum of incommensurate sines, so it never visibly repeats.
     */
    public static float breath(float time) {
        float a = Mth.sin(time * (Mth.TWO_PI / 420.0F));
        float b = Mth.sin(time * (Mth.TWO_PI / 173.0F) + 1.3F);
        float c = Mth.sin(time * (Mth.TWO_PI / 67.0F) + 0.4F);
        return 0.55F * a + 0.30F * b + 0.15F * c;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        windAngle += (RANDOM.nextFloat() - 0.5F) * 0.01F;
        if (!FogConfig.CLIENT.fogParticles.get() || !ClientFogState.isAffected()) return;
        // The volumetric shader (ours or the shader pack's) already draws moving clumps of fog; flat wisps would only fight with it.
        if (FogShaders.enabled() || FogShaders.externalShaderPackActive()) return;
        float deadly = deadliness(ClientFogState.density(1.0F));
        if (deadly <= 0.0F) return;
        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.MINIMAL) return;

        float intensity = FogConfig.CLIENT.effectsIntensity.get().floatValue();
        float rate = 2.2F * deadly * intensity * (status == ParticleStatus.DECREASED ? 0.4F : 1.0F);
        int count = (int) rate + (RANDOM.nextFloat() < rate - (int) rate ? 1 : 0);
        if (count == 0) return;

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float speed = 0.025F + 0.015F * (0.5F + 0.5F * breath(ClientFogState.clientTicks));
        double wx = Mth.cos(windAngle) * speed;
        double wz = Mth.sin(windAngle) * speed;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < count; i++) {
            double r = 3.0 + RANDOM.nextDouble() * 13.0;
            double ang = RANDOM.nextDouble() * Math.PI * 2.0;
            double x = cam.x + Math.cos(ang) * r;
            double z = cam.z + Math.sin(ang) * r;
            double y = cam.y - 2.0 + RANDOM.nextDouble() * 7.0;
            pos.set(x, y, z);
            if (!mc.level.isEmptyBlock(pos) || !mc.level.canSeeSky(pos)) continue;
            mc.particleEngine.createParticle(FogParticles.FOG_WISP.get(), x, y, z,
                    wx + (RANDOM.nextDouble() - 0.5) * 0.01,
                    0.002 + RANDOM.nextDouble() * 0.004,
                    wz + (RANDOM.nextDouble() - 0.5) * 0.01);
        }
    }
}
