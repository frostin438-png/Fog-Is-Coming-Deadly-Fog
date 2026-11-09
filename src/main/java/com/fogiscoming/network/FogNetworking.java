package com.fogiscoming.network;

import com.fogiscoming.FogIsComing;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server → client synchronisation of the fog state. The server is the single source of truth. */
public final class FogNetworking {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            FogIsComing.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static boolean registered;

    private FogNetworking() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(FogStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FogStatePacket::encode)
                .decoder(FogStatePacket::decode)
                .consumerMainThread(FogStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(FogExposurePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FogExposurePacket::encode)
                .decoder(FogExposurePacket::decode)
                .consumerMainThread(FogExposurePacket::handle)
                .add();
        CHANNEL.messageBuilder(FogScarePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FogScarePacket::encode)
                .decoder(FogScarePacket::decode)
                .consumerMainThread(FogScarePacket::handle)
                .add();
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        if (player.connection == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
