package com.fogiscoming;

import com.fogiscoming.config.FogConfig;
import com.fogiscoming.network.FogNetworking;
import com.fogiscoming.registry.FogEntities;
import com.fogiscoming.registry.FogParticles;
import com.fogiscoming.registry.FogSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * FOG IS COMING — main mod entry point.
 * <p>
 * Server-side systems (stages, persistence, protection, damage, commands) live in common packages
 * and are wired through Forge event subscribers. Client-only systems live in
 * {@code com.fogiscoming.client} and are only ever touched behind {@code Dist.CLIENT} guards,
 * so a dedicated server never loads a client class.
 */
@Mod(FogIsComing.MOD_ID)
public final class FogIsComing {
    public static final String MOD_ID = "fogiscoming";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FogIsComing() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        FogSounds.SOUNDS.register(modBus);
        FogParticles.PARTICLES.register(modBus);
        FogEntities.ENTITIES.register(modBus);
        modBus.addListener(FogEntities::registerAttributes);

        // Gameplay settings: per-world, server-authoritative (serverconfig/fogiscoming-server.toml).
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, FogConfig.SERVER_SPEC, "fogiscoming-server.toml");
        // Visual / audio / HUD preferences: per-player (config/fogiscoming-client.toml).
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FogConfig.CLIENT_SPEC, "fogiscoming-client.toml");

        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(FogNetworking::register);
        LOGGER.info("Fog Is Coming initialised. Stay near the light.");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
