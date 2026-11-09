package com.fogiscoming.event;

import com.fogiscoming.FogIsComing;
import com.fogiscoming.command.FogCommands;
import com.fogiscoming.command.FogSelfTest;
import com.fogiscoming.damage.FogDamageHandler;
import com.fogiscoming.protection.ShelterDetector;
import com.fogiscoming.world.FogStageManager;
import com.fogiscoming.world.LightEater;
import com.fogiscoming.world.WatcherDirector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-side (logical server) event wiring. Safe on both physical sides. */
@Mod.EventBusSubscriber(modid = FogIsComing.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommonEvents {
    private CommonEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        FogDamageHandler.clear();
        WatcherDirector.clear();
        LightEater.clear();
        FogStageManager.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FogDamageHandler.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        FogStageManager.tick(event.getServer());
        FogDamageHandler.tick(event.getServer());
        WatcherDirector.tick(event.getServer());
        LightEater.tick(event.getServer());
        FogSelfTest.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        FogCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FogDamageHandler.resync(player);
            FogStageManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FogDamageHandler.forget(event.getEntity().getUUID());
        WatcherDirector.forget(event.getEntity().getUUID());
        LightEater.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FogDamageHandler.resync(player);
            FogStageManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FogDamageHandler.resync(player);
            FogStageManager.sync(player);
        }
    }

    // A broken wall must be noticed immediately: drop cached shelter results nearby.
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level) ShelterDetector.invalidateNear(level, event.getPos());
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level) ShelterDetector.invalidateNear(level, event.getPos());
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!event.getAffectedBlocks().isEmpty()) {
            ShelterDetector.invalidateNear(event.getLevel(), event.getAffectedBlocks().get(0));
        }
    }
}
