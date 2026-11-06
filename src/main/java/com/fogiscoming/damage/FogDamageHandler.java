package com.fogiscoming.damage;

import com.fogiscoming.config.FogConfig;
import com.fogiscoming.network.FogExposurePacket;
import com.fogiscoming.network.FogNetworking;
import com.fogiscoming.protection.ExposureStatus;
import com.fogiscoming.protection.LightProtectionManager;
import com.fogiscoming.protection.ShelterDetector;
import com.fogiscoming.registry.FogTags;
import com.fogiscoming.world.FogStage;
import com.fogiscoming.world.FogStageManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies the deadly fog to players (Stage III only), entirely on the server.
 * <p>
 * Protection is evaluated every {@link #EVAL_INTERVAL} ticks per player (staggered by entity id so the work is
 * spread across ticks). Light is checked first (cheap); the shelter flood fill only runs when light does not
 * protect, and is cached. Leaving safety starts a grace countdown; after it, damage is dealt every
 * {@code damageIntervalTicks}, growing by {@code damageEscalation} each hit. Returning to safety stops damage
 * on the next evaluation (at most half a second later).
 */
public final class FogDamageHandler {
    public static final int EVAL_INTERVAL = 10;
    private static final Map<UUID, PlayerFogState> STATES = new HashMap<>();

    private FogDamageHandler() {
    }

    public static final class PlayerFogState {
        public ExposureStatus status = ExposureStatus.NONE;
        public int unsafeTicks;
        public int ticksToNextHit;
        public int hits;
        public boolean protectedNow = true;
        public String protectionDetail = "";
        ExposureStatus lastSentStatus = null;
        int lastSentSeconds = -1;
    }

    public static PlayerFogState state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new PlayerFogState());
    }

    public static void forget(UUID id) {
        STATES.remove(id);
        ShelterDetector.forget(id);
    }

    public static void clear() {
        STATES.clear();
        ShelterDetector.clear();
    }

    public static void tick(MinecraftServer server) {
        FogStage stage = FogStageManager.getStage();
        int tickCount = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerFogState st = state(player);
            if (!stage.isDeadly() || !player.isAlive() || player.isCreative() || player.isSpectator()
                    || !FogStageManager.isAffected(player.level())) {
                st.status = ExposureStatus.NONE;
                st.unsafeTicks = 0;
                st.hits = 0;
                st.protectedNow = true;
                send(player, st);
                continue;
            }

            if (st.status == ExposureStatus.NONE || (tickCount + player.getId()) % EVAL_INTERVAL == 0) {
                evaluate(player, st);
            }

            if (st.protectedNow) {
                st.unsafeTicks = 0;
                st.hits = 0;
            } else {
                st.unsafeTicks++;
                int grace = FogConfig.SERVER.graceTicks.get();
                if (st.unsafeTicks <= grace) {
                    st.status = ExposureStatus.GRACE;
                    st.ticksToNextHit = 0; // first hit lands as soon as grace runs out
                } else {
                    st.status = ExposureStatus.EXPOSED;
                    if (--st.ticksToNextHit <= 0) {
                        hurt(player, st);
                        st.ticksToNextHit = FogConfig.SERVER.damageIntervalTicks.get();
                    }
                }
            }
            send(player, st);
        }
    }

    private static void evaluate(ServerPlayer player, PlayerFogState st) {
        ServerLevel level = player.serverLevel();
        var light = LightProtectionManager.check(level, player.blockPosition());
        if (light.protectedByLight()) {
            st.protectedNow = true;
            st.status = ExposureStatus.SAFE_LIGHT;
            st.protectionDetail = "light: " + light.sourceState().getBlock().getName().getString()
                    + String.format(" at %.1f blocks (block light %d)", light.distance(), light.blockLight());
            return;
        }
        var shelter = ShelterDetector.check(player);
        if (shelter.sheltered()) {
            st.protectedNow = true;
            st.status = ExposureStatus.SAFE_SHELTER;
            st.protectionDetail = "shelter: sealed, volume " + shelter.volume() + ", floor " + shelter.footprint();
            return;
        }
        st.protectedNow = false;
        if (st.status.isSafe() || st.status == ExposureStatus.NONE) {
            st.status = ExposureStatus.GRACE;
        }
        st.protectionDetail = "exposed: block light " + light.blockLight() + ", shelter " + shelter.reason().text
                + " (volume " + shelter.volume() + ", leaks " + shelter.leaks() + ")";
    }

    private static void hurt(ServerPlayer player, PlayerFogState st) {
        double base = FogConfig.SERVER.damageAmount.get();
        double amount = Math.min(FogConfig.SERVER.maxDamagePerHit.get(), base + st.hits * FogConfig.SERVER.damageEscalation.get());
        if (amount <= 0) return;
        DamageSource source = new DamageSource(player.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FogTags.FOG_DAMAGE));
        if (player.hurt(source, (float) amount)) {
            st.hits++;
        }
    }

    public static int secondsUntilDamage(PlayerFogState st) {
        if (st.status == ExposureStatus.GRACE) {
            int left = FogConfig.SERVER.graceTicks.get() - st.unsafeTicks;
            return Math.max(0, (left + 19) / 20);
        }
        return 0;
    }

    private static void send(ServerPlayer player, PlayerFogState st) {
        int seconds = secondsUntilDamage(st);
        if (st.status != st.lastSentStatus || seconds != st.lastSentSeconds) {
            st.lastSentStatus = st.status;
            st.lastSentSeconds = seconds;
            FogNetworking.sendTo(player, new FogExposurePacket(st.status.ordinal(), seconds));
        }
    }

    /** Forces a re-send of the exposure state (login, respawn, dimension change). */
    public static void resync(ServerPlayer player) {
        PlayerFogState st = state(player);
        st.lastSentStatus = null;
        st.lastSentSeconds = -1;
        ShelterDetector.forget(player.getUUID());
    }
}
