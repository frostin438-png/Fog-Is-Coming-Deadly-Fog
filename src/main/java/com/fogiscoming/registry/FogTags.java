package com.fogiscoming.registry;

import com.fogiscoming.FogIsComing;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.block.Block;

/** Data-driven tags and keys. Packs can extend all of these without touching code. */
public final class FogTags {
    /** Blocks that always count as protective light sources (when actually emitting light). */
    public static final TagKey<Block> SAFE_LIGHTS = BlockTags.create(FogIsComing.id("fog_safe_lights"));
    /** Light emitters that never protect (lava, fire, portals...). */
    public static final TagKey<Block> LIGHT_BLACKLIST = BlockTags.create(FogIsComing.id("fog_light_blacklist"));
    /** Blocks with collision that the fog still passes through (leaves, ladders...). */
    public static final TagKey<Block> FOG_PERMEABLE = BlockTags.create(FogIsComing.id("fog_permeable"));
    /** Light blocks a Light Stalker takes away (torches and lanterns by default). */
    public static final TagKey<Block> STALKER_TARGETS = BlockTags.create(FogIsComing.id("stalker_targets"));

    /** Damage type defined in data/fogiscoming/damage_type/fog.json. */
    public static final ResourceKey<DamageType> FOG_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, FogIsComing.id("fog"));
    /** Damage type of a Watcher reaching the player (data/fogiscoming/damage_type/watcher.json). */
    public static final ResourceKey<DamageType> WATCHER_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, FogIsComing.id("watcher"));

    private FogTags() {
    }
}
