package com.fogiscoming.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Global fog state: stage, continuous density and the timing needed for the countdown. Sent once per second. */
public record FogStatePacket(int stage, boolean forced, long elapsed, long stage1Tick, long stage2Tick, long stage3Tick,
                             float density, boolean affected, boolean timeRunning) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(stage);
        buf.writeBoolean(forced);
        buf.writeVarLong(elapsed);
        buf.writeVarLong(stage1Tick);
        buf.writeVarLong(stage2Tick);
        buf.writeVarLong(stage3Tick);
        buf.writeFloat(density);
        buf.writeBoolean(affected);
        buf.writeBoolean(timeRunning);
    }

    public static FogStatePacket decode(FriendlyByteBuf buf) {
        return new FogStatePacket(buf.readByte(), buf.readBoolean(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarLong(), buf.readVarLong(), buf.readFloat(), buf.readBoolean(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        // The client handler is only referenced inside the client-only lambda, so the class is never loaded
        // on a dedicated server.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.fogiscoming.client.ClientPacketHandler.handleState(this));
        ctx.get().setPacketHandled(true);
    }
}
