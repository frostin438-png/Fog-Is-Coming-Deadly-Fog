package com.fogiscoming.registry;

import com.fogiscoming.FogIsComing;
import com.fogiscoming.entity.LightStalkerEntity;
import com.fogiscoming.entity.WatcherEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FogEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FogIsComing.MOD_ID);

    public static final RegistryObject<EntityType<WatcherEntity>> WATCHER = ENTITIES.register("watcher",
            () -> EntityType.Builder.<WatcherEntity>of(WatcherEntity::new, MobCategory.MISC)
                    .sized(0.6F, 2.25F)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(FogIsComing.id("watcher").toString()));

    public static final RegistryObject<EntityType<LightStalkerEntity>> LIGHT_STALKER = ENTITIES.register("light_stalker",
            () -> EntityType.Builder.<LightStalkerEntity>of(LightStalkerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build(FogIsComing.id("light_stalker").toString()));

    private FogEntities() {
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(WATCHER.get(), WatcherEntity.createAttributes().build());
        event.put(LIGHT_STALKER.get(), LightStalkerEntity.createAttributes().build());
    }
}
