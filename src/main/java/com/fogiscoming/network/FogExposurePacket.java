package com.fogiscoming.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Per-player exposure state. Only sent when it changes. */
public record FogExposurePacket(int status, int secondsUntilDamage) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(status);
        buf.writeVarInt(secondsUntilDamage);
    }

    public static FogExposurePacket decode(FriendlyByteBuf buf) {
        return new FogExposurePacket(buf.readByte(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.fogiscoming.client.ClientPacketHandler.handleExposure(this));
        ctx.get().setPacketHandled(true);
    }
}
