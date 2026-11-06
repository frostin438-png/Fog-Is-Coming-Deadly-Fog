package com.fogiscoming.client;

import com.fogiscoming.network.FogExposurePacket;
import com.fogiscoming.network.FogStatePacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Client-side packet handlers (only reached through DistExecutor on the physical client). */
@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void handleState(FogStatePacket p) {
        ClientFogState.applyState(p.stage(), p.forced(), p.elapsed(), p.stage1Tick(), p.stage2Tick(), p.stage3Tick(),
                p.density(), p.affected(), p.timeRunning());
    }

    public static void handleExposure(FogExposurePacket p) {
        ClientFogState.applyExposure(p.status(), p.secondsUntilDamage());
    }
}
