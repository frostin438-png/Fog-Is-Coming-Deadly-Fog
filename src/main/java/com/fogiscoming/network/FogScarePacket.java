package com.fogiscoming.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** One-shot scare for a single player (a Watcher reached them). */
public record FogScarePacket(int kind) {
    public static final int JUMPSCARE = 0;

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(kind);
    }

    public static FogScarePacket decode(FriendlyByteBuf buf) {
        return new FogScarePacket(buf.readByte());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.fogiscoming.client.ClientPacketHandler.handleScare(this));
        ctx.get().setPacketHandled(true);
    }
}
