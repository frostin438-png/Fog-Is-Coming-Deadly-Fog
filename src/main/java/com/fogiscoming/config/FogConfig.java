package com.fogiscoming.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Forge configuration.
 * <ul>
 *     <li>{@link Server} — everything that affects gameplay. Stored per world in
 *     {@code <world>/serverconfig/fogiscoming-server.toml}, read only on the logical server.</li>
 *     <li>{@link Client} — purely cosmetic preferences (HUD, sounds, effect intensity).</li>
 * </ul>
 */
public final class FogConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ForgeConfigSpec.Builder sb = new ForgeConfigSpec.Builder();
        SERVER = new Server(sb);
        SERVER_SPEC = sb.build();

        ForgeConfigSpec.Builder cb = new ForgeConfigSpec.Builder();
        CLIENT = new Client(cb);
        CLIENT_SPEC = cb.build();
    }

    private FogConfig() {
    }

    public static final class Server {
        // --- stages ---
        public final ForgeConfigSpec.DoubleValue stage1Day;
        public final ForgeConfigSpec.DoubleValue stage2Day;
        public final ForgeConfigSpec.DoubleValue stage3Day;
        public final ForgeConfigSpec.DoubleValue transitionDays;
        public final ForgeConfigSpec.DoubleValue stage1Density;
        public final ForgeConfigSpec.DoubleValue stage2Density;
        public final ForgeConfigSpec.DoubleValue stage3Density;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> affectedDimensions;

        // --- damage ---
        public final ForgeConfigSpec.DoubleValue damageAmount;
        public final ForgeConfigSpec.DoubleValue damageEscalation;
        public final ForgeConfigSpec.DoubleValue maxDamagePerHit;
        public final ForgeConfigSpec.IntValue damageIntervalTicks;
        public final ForgeConfigSpec.IntValue graceTicks;

        // --- light protection ---
        public final ForgeConfigSpec.IntValue lightRadius;
        public final ForgeConfigSpec.IntValue minBlockLight;
        public final ForgeConfigSpec.BooleanValue allowAnyLightEmitter;
        public final ForgeConfigSpec.IntValue minAnyEmitterLevel;

        // --- shelter ---
        public final ForgeConfigSpec.BooleanValue shelterEnabled;
        public final ForgeConfigSpec.IntValue shelterMaxVolume;
        public final ForgeConfigSpec.IntValue shelterMaxRadius;
        public final ForgeConfigSpec.IntValue shelterMinVolume;
        public final ForgeConfigSpec.IntValue shelterMinFootprint;
        public final ForgeConfigSpec.IntValue shelterLeakTolerance;

        // --- horror ---
        public final ForgeConfigSpec.BooleanValue watchersEnabled;
        public final ForgeConfigSpec.BooleanValue watcherAttacks;
        public final ForgeConfigSpec.DoubleValue watcherDamage;
        public final ForgeConfigSpec.DoubleValue watcherFrequency;
        public final ForgeConfigSpec.BooleanValue fogEatsLight;

        Server(ForgeConfigSpec.Builder b) {
            b.comment("Fog progression. One Minecraft day = 24000 ticks. Values are in elapsed in-game days",
                            "counted from world creation (or from the last /fog reset). Sleeping and /time add count, /time set backwards does not.")
                    .push("stages");
            stage1Day = b.comment("Elapsed days before the fog arrives (Stage I — The Arrival).")
                    .defineInRange("stage1Day", 1.0, 0.0, 10000.0);
            stage2Day = b.comment("Elapsed days before Stage II — The Thickening.")
                    .defineInRange("stage2Day", 6.0, 0.0, 10000.0);
            stage3Day = b.comment("Elapsed days before Stage III — The Deadly Fog. The deadly fog never leaves once it arrives.")
                    .defineInRange("stage3Day", 8.0, 0.0, 10000.0);
            transitionDays = b.comment("How long (in days) the density takes to ramp up from the previous stage to the new one.")
                    .defineInRange("transitionDays", 1.0, 0.0, 100.0);
            stage1Density = b.comment("Fog density in Stage I (0 = none, 1 = maximum). Roughly: 0.70 ≈ 82 blocks of visibility.")
                    .defineInRange("stage1Density", 0.70, 0.0, 1.0);
            stage2Density = b.comment("Fog density in Stage II. 0.85 ≈ 45 blocks of visibility.")
                    .defineInRange("stage2Density", 0.85, 0.0, 1.0);
            stage3Density = b.comment("Fog density in Stage III. 0.95 ≈ 20 blocks of visibility.")
                    .defineInRange("stage3Density", 0.95, 0.0, 1.0);
            affectedDimensions = b.comment("Dimensions covered by the fog (visuals and damage).")
                    .defineListAllowEmpty(List.of("affectedDimensions"), () -> List.of("minecraft:overworld"), o -> o instanceof String);
            b.pop();

            b.comment("Deadly fog damage (Stage III only). Creative and spectator players are never damaged.").push("damage");
            damageAmount = b.comment("Damage of the first hit (1.0 = half a heart).")
                    .defineInRange("damageAmount", 2.0, 0.0, 1000.0);
            damageEscalation = b.comment("Extra damage added to every consecutive hit while the player stays exposed.")
                    .defineInRange("damageEscalation", 0.5, 0.0, 100.0);
            maxDamagePerHit = b.comment("Upper limit of a single hit after escalation.")
                    .defineInRange("maxDamagePerHit", 6.0, 0.0, 1000.0);
            damageIntervalTicks = b.comment("Ticks between hits (20 ticks = 1 second).")
                    .defineInRange("damageIntervalTicks", 40, 10, 12000);
            graceTicks = b.comment("Ticks a player may stay exposed before the fog starts to hurt (countdown after leaving safety).")
                    .defineInRange("graceTicks", 100, 0, 12000);
            b.pop();

            b.comment("Light protection. A player is protected when artificial (block) light reaches them AND a valid",
                    "light source is within the radius. Sunlight / sky light never protects.").push("light");
            lightRadius = b.comment("Maximum distance (blocks, spherical) between the player and a protecting light source.")
                    .defineInRange("lightRadius", 7, 1, 16);
            minBlockLight = b.comment("Minimum BLOCK light level (sky light ignored) at the player's feet or head.")
                    .defineInRange("minBlockLight", 4, 1, 15);
            allowAnyLightEmitter = b.comment("If true, any lit light-emitting block counts (except the fogiscoming:fog_light_blacklist tag).",
                            "If false, only blocks in the fogiscoming:fog_safe_lights tag protect.")
                    .define("allowAnyLightEmitter", true);
            minAnyEmitterLevel = b.comment("Minimum emitted light level for blocks outside the fog_safe_lights tag.")
                    .defineInRange("minAnyEmitterLevel", 7, 1, 15);
            b.pop();

            b.comment("Shelter protection: an enclosed interior (roof + walls, no openings to the open sky).").push("shelter");
            shelterEnabled = b.comment("Enable protection inside enclosed shelters.")
                    .define("shelterEnabled", true);
            shelterMaxVolume = b.comment("Maximum interior volume (air blocks) scanned. Bigger spaces are treated as open (caves, huge halls).")
                    .defineInRange("shelterMaxVolume", 2048, 64, 32768);
            shelterMaxRadius = b.comment("Maximum distance from the player the interior may extend on any axis.")
                    .defineInRange("shelterMaxRadius", 20, 4, 64);
            shelterMinVolume = b.comment("Minimum interior volume. Prevents 'a hole with a block on top' from counting as a house.")
                    .defineInRange("shelterMinVolume", 12, 1, 4096);
            shelterMinFootprint = b.comment("Minimum floor area (distinct X/Z columns) of the interior.")
                    .defineInRange("shelterMinFootprint", 4, 1, 4096);
            shelterLeakTolerance = b.comment("How many interior cells may touch the open sky before the shelter is considered breached.",
                            "0 = any hole to the outside breaks protection.")
                    .defineInRange("shelterLeakTolerance", 0, 0, 16);
            b.pop();

            b.comment("Things in the fog.").push("horror");
            watchersEnabled = b.comment("Watchers: tall dark figures standing at the edge of the fog (Stage II at night, Stage III always).",
                            "They vanish when stared at or approached and never enter the light.")
                    .define("watchersEnabled", true);
            watcherAttacks = b.comment("In Stage III a Watcher closes in on an exposed player whenever they look away.")
                    .define("watcherAttacks", true);
            watcherDamage = b.comment("Damage dealt when a Watcher reaches a player (ignores armor).")
                    .defineInRange("watcherDamage", 8.0, 0.0, 1000.0);
            watcherFrequency = b.comment("How often Watchers appear (1.0 = default, 2.0 = twice as often, 0.5 = half).")
                    .defineInRange("watcherFrequency", 1.0, 0.05, 10.0);
            fogEatsLight = b.comment("At night in Stage III the fog now and then snuffs out torches and campfires under the open sky",
                            "near a player. Lanterns and other light blocks are never touched.")
                    .define("fogEatsLight", true);
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue hudEnabled;
        public final ForgeConfigSpec.BooleanValue soundsEnabled;
        public final ForgeConfigSpec.BooleanValue muffleDistantSounds;
        public final ForgeConfigSpec.DoubleValue effectsIntensity;
        public final ForgeConfigSpec.DoubleValue ambientVolume;
        public final ForgeConfigSpec.BooleanValue clockTicking;
        public final ForgeConfigSpec.DoubleValue clockVolume;
        public final ForgeConfigSpec.BooleanValue fogParticles;
        public final ForgeConfigSpec.BooleanValue shaderFog;
        public final ForgeConfigSpec.EnumValue<ShaderQuality> shaderQuality;
        public final ForgeConfigSpec.BooleanValue lightHalos;
        public final ForgeConfigSpec.BooleanValue cinematicGrading;
        public final ForgeConfigSpec.BooleanValue hallucinations;
        public final ForgeConfigSpec.BooleanValue cameraShake;

        Client(ForgeConfigSpec.Builder b) {
            b.push("client");
            hudEnabled = b.comment("Show the FOG IS COMING HUD (countdown, stage, exposure warnings).")
                    .define("hudEnabled", true);
            soundsEnabled = b.comment("Play atmospheric fog sounds.")
                    .define("soundsEnabled", true);
            muffleDistantSounds = b.comment("Muffle distant world sounds while the fog is thick.")
                    .define("muffleDistantSounds", true);
            effectsIntensity = b.comment("Intensity of screen effects (vignette, sky veil, night darkening). Fog distance itself is server-controlled.")
                    .defineInRange("effectsIntensity", 1.0, 0.0, 1.0);
            ambientVolume = b.comment("Volume multiplier for fog ambience.")
                    .defineInRange("ambientVolume", 1.0, 0.0, 2.0);
            clockTicking = b.comment("Mechanical clock ticking during the countdown before the fog (gets louder near the end).")
                    .define("clockTicking", true);
            clockVolume = b.comment("Volume multiplier for the countdown clock.")
                    .defineInRange("clockVolume", 1.0, 0.0, 2.0);
            fogParticles = b.comment("Drifting fog wisps in the deadly fog (respects the vanilla Particles setting).")
                    .define("fogParticles", true);
            shaderFog = b.comment("Volumetric fog shader (GLSL post-processing): height fog with drifting 3D clumps.",
                            "Turned off automatically while an Oculus/Iris shader pack is active. Off = plain distance fog.")
                    .define("shaderFog", true);
            shaderQuality = b.comment("Ray-march quality of the fog shader: LOW (8 steps), MEDIUM (14), HIGH (22).")
                    .defineEnum("shaderQuality", ShaderQuality.MEDIUM);
            lightHalos = b.comment("Soft glow of torches, lanterns, campfires and other lights in the fog (needs shaderFog).")
                    .define("lightHalos", true);
            cinematicGrading = b.comment("Cold, slightly desaturated film look with grain and vignette in thick fog (needs shaderFog).")
                    .define("cinematicGrading", true);
            hallucinations = b.comment("Sounds that are not really there: footsteps behind you, whispers, knocking on the door at night.")
                    .define("hallucinations", true);
            cameraShake = b.comment("Camera shake while exposed to the deadly fog and when something reaches you.")
                    .define("cameraShake", true);
            b.pop();
        }
    }

    public enum ShaderQuality {
        LOW, MEDIUM, HIGH
    }
}
