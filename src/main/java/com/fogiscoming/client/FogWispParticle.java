package com.fogiscoming.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A big, very transparent puff of fog that slowly drifts with the wind, swells and dissolves again.
 * Many of them around the player make the deadly fog look like it is rolling and moving instead of
 * being a flat grey wall. Tinted with the current fog colour every tick.
 */
@OnlyIn(Dist.CLIENT)
public final class FogWispParticle extends TextureSheetParticle {
    private final float maxAlpha;
    private final float baseSize;
    private final float spin;

    FogWispParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.lifetime = 140 + random.nextInt(100);
        this.baseSize = 1.1F + random.nextFloat() * 1.4F;
        this.quadSize = baseSize * 0.6F;
        this.maxAlpha = 0.14F + random.nextFloat() * 0.14F;
        this.alpha = 0.0F;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        this.spin = (random.nextFloat() - 0.5F) * 0.006F;
        tint();
    }

    @Override
    public void tick() {
        // The volumetric shader replaces the wisps (and would show them as dark patches); drop leftovers at once.
        if (FogShaders.enabled() || FogShaders.externalShaderPackActive()) {
            remove();
            return;
        }
        super.tick();
        oRoll = roll;
        roll += spin;
        float life = (float) age / lifetime;
        // Fade in over the first third, hold, fade out over the last third.
        float fade = Math.min(Mth.clamp(life * 3.0F, 0.0F, 1.0F), Mth.clamp((1.0F - life) * 3.0F, 0.0F, 1.0F));
        alpha = maxAlpha * FogRenderer.smooth(fade);
        quadSize = baseSize * (0.6F + 0.4F * life); // slowly swells as it drifts
        tint();
    }

    private void tint() {
        float[] c = FogRenderer.lastFogColor();
        // Slightly lighter than the fog itself so the wisps read as moving layers.
        setColor(Math.min(1.0F, c[0] * 1.18F + 0.02F), Math.min(1.0F, c[1] * 1.18F + 0.02F), Math.min(1.0F, c[2] * 1.18F + 0.02F));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @OnlyIn(Dist.CLIENT)
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            FogWispParticle p = new FogWispParticle(level, x, y, z, dx, dy, dz);
            p.pickSprite(sprites);
            return p;
        }
    }
}
