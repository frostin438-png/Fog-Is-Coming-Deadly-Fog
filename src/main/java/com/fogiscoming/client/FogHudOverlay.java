package com.fogiscoming.client;

import com.fogiscoming.config.FogConfig;
import com.fogiscoming.protection.ExposureStatus;
import com.fogiscoming.world.FogStage;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Minimal HUD: countdown before the fog, the current stage afterwards, and soft exposure warnings in the
 * deadly stage. Everything fades in and out; nothing flashes.
 */
@OnlyIn(Dist.CLIENT)
public final class FogHudOverlay implements IGuiOverlay {
    public static final FogHudOverlay INSTANCE = new FogHudOverlay();
    private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");

    private static final int PALE = 0xD8DCE0;
    private static final int DIM = 0x9AA0A6;
    private static final int RED = 0xC0281E;
    private static final int TITLE_TICKS = 140;
    private static final int SAFE_MESSAGE_TICKS = 70;

    private FogHudOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !ClientFogState.isInitialized()) return;

        renderExposureVignette(g, partialTick, width, height);
        if (!FogConfig.CLIENT.hudEnabled.get()) return;

        Font font = mc.font;
        FogStage stage = ClientFogState.stage();
        float time = ClientFogState.clientTicks + partialTick;

        if (stage == FogStage.CALM) {
            renderCountdown(g, font, width, time);
        } else {
            renderStage(g, font, width, height, stage, time);
            renderExposure(g, font, width, time);
        }
    }

    // ------------------------------------------------------------------ before the fog

    private void renderCountdown(GuiGraphics g, Font font, int width, float time) {
        String title = "F O G   I S   C O M I N G";
        long remaining = ClientFogState.ticksUntilFog();

        // Pale grey for most of the countdown, then slowly bleeding into red.
        float danger = ClientFogState.countdownDanger();
        int color = lerpColor(PALE, RED, danger);
        float alpha = 0.85F;
        if (danger > 0.9F) {
            // Final in-game hours: a slow "breathing", never a blink.
            alpha = 0.7F + 0.25F * (0.5F + 0.5F * Mth.sin(time * 0.06F));
        }

        float scale = 1.5F;
        g.pose().pushPose();
        g.pose().translate(width / 2.0F, 6.0F, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        drawCentered(g, font, title, 0, 0, color, alpha);
        g.pose().popPose();

        String line;
        if (ClientFogState.forced) {
            line = "THE FOG IS HELD BACK";
        } else {
            long days = remaining / 24000L;
            long rest = remaining % 24000L;
            long hours = rest / 1000L;
            long minutes = (rest % 1000L) * 60L / 1000L;
            line = String.format("TIME REMAINING: %02d DAYS %02d:%02d", days, hours, minutes);
        }
        drawCentered(g, font, line, width / 2, 22, lerpColor(DIM, RED, danger * 0.8F), 0.8F);
    }

    // ------------------------------------------------------------------ after the fog

    private void renderStage(GuiGraphics g, Font font, int width, int height, FogStage stage, float time) {
        int color = stage.isDeadly() ? lerpColor(DIM, RED, 0.55F) : DIM;
        drawCentered(g, font, stage.title, width / 2, 6, color, 0.75F);

        // Big centred title when a stage begins (or when joining a foggy world).
        float t = time - ClientFogState.stageTitleTick;
        if (t >= 0 && t < TITLE_TICKS) {
            float a = fade(t, TITLE_TICKS, 25, 40);
            g.pose().pushPose();
            g.pose().translate(width / 2.0F, height / 3.0F, 0.0F);
            g.pose().scale(2.0F, 2.0F, 1.0F);
            drawCentered(g, font, stage.title, 0, 0, stage.isDeadly() ? RED : PALE, a * 0.9F);
            g.pose().popPose();
            String sub = switch (stage) {
                case ARRIVAL -> "Something is drifting in from the horizon.";
                case THICKENING -> "The world grows quiet. The distance is gone.";
                case DEADLY -> "Stay near the light. Do not go out.";
                default -> "";
            };
            drawCentered(g, font, sub, width / 2, (int) (height / 3.0F) + 22, DIM, a * 0.8F);
        }
    }

    private void renderExposure(GuiGraphics g, Font font, int width, float time) {
        ExposureStatus status = ClientFogState.exposure();
        float sinceChange = time - ClientFogState.exposureChangedTick;
        if (status.isUnsafe()) {
            float fadeIn = Mth.clamp(sinceChange / 15.0F, 0.0F, 1.0F);
            float breathe = 0.75F + 0.25F * Mth.sin(time * 0.08F);
            drawCentered(g, font, "YOU ARE EXPOSED", width / 2, 20, RED, fadeIn * breathe);
            String hint = status == ExposureStatus.GRACE && ClientFogState.secondsUntilDamage > 0
                    ? "The fog will find you in " + ClientFogState.secondsUntilDamage + "s — find light or shelter"
                    : "Find light or shelter";
            drawCentered(g, font, hint, width / 2, 31, DIM, fadeIn * 0.7F);
        } else if (status.isSafe()) {
            if (sinceChange < SAFE_MESSAGE_TICKS) {
                float a = fade(sinceChange, SAFE_MESSAGE_TICKS, 10, 25);
                drawCentered(g, font, "YOU ARE SAFE", width / 2, 20, PALE, a * 0.9F);
            }
            String small = status == ExposureStatus.SAFE_LIGHT ? "· sheltered by light ·" : "· sheltered indoors ·";
            float a = Mth.clamp((sinceChange - SAFE_MESSAGE_TICKS + 20) / 30.0F, 0.0F, 1.0F) * 0.45F;
            drawCentered(g, font, small, width / 2, 20, DIM, a);
        }
    }

    /** Grey vignette that closes in while exposed; drawn even when the text HUD is disabled. */
    private void renderExposureVignette(GuiGraphics g, float partialTick, int width, int height) {
        float e = ClientFogState.exposureBlend(partialTick) * FogConfig.CLIENT.effectsIntensity.get().floatValue();
        if (e <= 0.01F) return;
        float v = Mth.clamp(e * 0.85F, 0.0F, 1.0F);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        g.setColor(v, v, v, 1.0F);
        g.blit(VIGNETTE, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ helpers

    private static float fade(float t, float total, float in, float out) {
        if (t < in) return t / in;
        if (t > total - out) return Math.max(0.0F, (total - t) / out);
        return 1.0F;
    }

    private static void drawCentered(GuiGraphics g, Font font, String text, int x, int y, int rgb, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        if (a < 8) return; // the font renderer treats alpha < 4 as opaque
        g.drawString(font, text, x - font.width(text) / 2, y, (a << 24) | (rgb & 0xFFFFFF), true);
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int gr = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return (r << 16) | (gr << 8) | b;
    }
}
