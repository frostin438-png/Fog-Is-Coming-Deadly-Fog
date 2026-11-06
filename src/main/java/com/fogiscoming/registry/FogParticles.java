package com.fogiscoming.registry;

import com.fogiscoming.FogIsComing;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Particle types of the mod. The type itself is common; its renderer is registered client-side only. */
public final class FogParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, FogIsComing.MOD_ID);

    /** Large, soft, slowly drifting wisp of the deadly fog (purely cosmetic, spawned by the client). */
    public static final RegistryObject<SimpleParticleType> FOG_WISP =
            PARTICLES.register("fog_wisp", () -> new SimpleParticleType(false));

    private FogParticles() {
    }
}
