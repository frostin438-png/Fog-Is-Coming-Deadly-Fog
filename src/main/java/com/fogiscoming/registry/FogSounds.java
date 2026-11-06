package com.fogiscoming.registry;

import com.fogiscoming.FogIsComing;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Original sound assets of the mod (see assets/fogiscoming/sounds.json). */
public final class FogSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, FogIsComing.MOD_ID);

    public static final RegistryObject<SoundEvent> WIND_GUST = register("ambient.wind_gust");
    public static final RegistryObject<SoundEvent> DISTANT_RUMBLE = register("ambient.distant_rumble");
    public static final RegistryObject<SoundEvent> DISTANT_GROAN = register("ambient.distant_groan");
    public static final RegistryObject<SoundEvent> EXPOSED_DRONE = register("ambient.exposed_drone");
    public static final RegistryObject<SoundEvent> HEARTBEAT = register("ambient.heartbeat");
    public static final RegistryObject<SoundEvent> FOG_ARRIVAL = register("event.fog_arrival");
    public static final RegistryObject<SoundEvent> CLOCK_TICK = register("clock.tick");
    public static final RegistryObject<SoundEvent> CLOCK_TOCK = register("clock.tock");

    private FogSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(FogIsComing.id(name)));
    }
}
