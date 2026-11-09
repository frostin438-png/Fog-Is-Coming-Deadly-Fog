package com.fogiscoming.mixin;

import com.fogiscoming.client.FogShaders;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hook right after the world is rendered and before the HUD. With an Oculus/Iris shader pack this is the first
 * moment the pack's final image is on screen, so the fog can be drawn over it.
 * <p>
 * Named without a refmap: "renderLevel" in the dev environment, "m_109089_" (SRG) in a normal game.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = {"renderLevel", "m_109089_"}, at = @At("RETURN"), remap = false, require = 0)
    private void fogiscoming$afterRenderLevel(float partialTick, long nanoTime, PoseStack poseStack, CallbackInfo ci) {
        FogShaders.afterGameRenderLevel(partialTick);
    }
}
