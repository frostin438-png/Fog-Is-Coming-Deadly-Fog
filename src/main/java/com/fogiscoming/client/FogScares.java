package com.fogiscoming.client;

import com.fogiscoming.FogIsComing;
import com.fogiscoming.config.FogConfig;
import com.fogiscoming.entity.WatcherEntity;
import com.fogiscoming.network.FogScarePacket;
import com.fogiscoming.protection.ExposureStatus;
import com.fogiscoming.registry.FogSounds;
import com.fogiscoming.world.FogStage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Client-side fear: things that are not really there, and how the body reacts.
 * <ul>
 *     <li>Hallucinations: footsteps that follow you and stop when you stop, whispers right next to your ear,
 *     cave noises in the open, knocking on the door of your shelter at night.</li>
 *     <li>Looking at a Watcher: ringing in the ears, static at the edge of vision, the view narrows.</li>
 *     <li>When a Watcher reaches you: darkness, two pale eyes, a scream, a violent camera shake.</li>
 *     <li>Exposed to the deadly fog: a constant tremor of the camera.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class FogScares {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final ResourceLocation STATIC = FogIsComing.id("textures/misc/static.png");
    private static final int JUMPSCARE_TICKS = 60;

    private static int jumpscareTicks;
    private static float gaze, prevGaze;
    private static GazeSound gazeSound;

    private static int footstepCooldown = 1200;
    private static int whisperCooldown = 1800;
    private static int caveCooldown = 2400;
    private static int knockCooldown = 2400;

    private static int stepsLeft, stepTimer;
    private static double stepDist, stepAngle;
    private static int knocksLeft, knockTimer;
    private static BlockPos knockPos;

    private FogScares() {
    }

    // ------------------------------------------------------------------ events

    public static void onScare(int kind) {
        if (kind != FogScarePacket.JUMPSCARE) return;
        jumpscareTicks = JUMPSCARE_TICKS;
        Minecraft mc = Minecraft.getInstance();
        float vol = Math.min(1.0F, FogConfig.CLIENT.ambientVolume.get().floatValue());
        mc.getSoundManager().play(SimpleSoundInstance.forUI(FogSounds.WATCHER_SCREAM.get(), 1.0F, vol));
    }

    static void reset() {
        jumpscareTicks = 0;
        gaze = prevGaze = 0;
        if (gazeSound != null) gazeSound.stopNow();
        gazeSound = null;
        stepsLeft = knocksLeft = 0;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (jumpscareTicks > 0) jumpscareTicks--;
        tickGaze(mc, player);

        boolean enabled = FogConfig.CLIENT.hallucinations.get() && FogConfig.CLIENT.soundsEnabled.get()
                && ClientFogState.isAffected() && !player.isSpectator();
        FogStage stage = ClientFogState.stage();
        if (!enabled || stage.id < 2) {
            stepsLeft = knocksLeft = 0;
            return;
        }
        boolean night = mc.level.getSkyDarken(1.0F) < 0.5F;
        boolean deadly = stage.isDeadly();
        boolean outdoors = mc.level.canSeeSky(player.blockPosition().above());
        ExposureStatus ex = ClientFogState.exposure();

        tickFootsteps(mc, player);
        tickKnocks(mc);

        if ((deadly || night) && outdoors && stepsLeft == 0 && --footstepCooldown <= 0) {
            startFootsteps(player);
            footstepCooldown = 1800 + RANDOM.nextInt(deadly && night ? 2400 : 4200);
        }
        if ((deadly || night) && --whisperCooldown <= 0) {
            whisper(mc, player);
            whisperCooldown = (ex.isUnsafe() ? 1400 : 2600) + RANDOM.nextInt(3600);
        }
        if (outdoors && --caveCooldown <= 0) {
            playAround(mc, player, SoundEvents.AMBIENT_CAVE.value(), 0.9F, 0.8F + RANDOM.nextFloat() * 0.3F, 8, 16);
            caveCooldown = 3600 + RANDOM.nextInt(6000);
        }
        if (deadly && night && ex == ExposureStatus.SAFE_SHELTER && knocksLeft == 0 && --knockCooldown <= 0) {
            startKnocks(mc, player);
            knockCooldown = 3000 + RANDOM.nextInt(6000);
        }
    }

    // ------------------------------------------------------------------ the Watcher's gaze

    private static void tickGaze(Minecraft mc, LocalPlayer player) {
        prevGaze = gaze;
        float target = 0.0F;
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0F);
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof WatcherEntity w) || !w.isAlive()) continue;
            Vec3 to = new Vec3(w.getX(), w.getY() + w.getBbHeight() * 0.7, w.getZ()).subtract(eye);
            double len = to.length();
            if (len > 90 || len < 1.0E-3) continue;
            double dot = view.dot(to.scale(1.0 / len));
            if (dot < 0.9 || !player.hasLineOfSight(w)) continue;
            float g = Mth.clamp((float) (dot - 0.9) / 0.08F, 0.0F, 1.0F);
            // Closer is worse.
            g *= Mth.clamp(1.3F - (float) len / 60.0F, 0.4F, 1.0F);
            target = Math.max(target, g);
        }
        gaze += (target - gaze) * (target > gaze ? 0.12F : 0.05F);
        if (gaze < 0.002F) gaze = 0.0F;

        boolean wanted = gaze > 0.02F && FogConfig.CLIENT.soundsEnabled.get();
        if (wanted && (gazeSound == null || gazeSound.isStopped())) {
            gazeSound = new GazeSound(FogSounds.WATCHER_GAZE.get());
            mc.getSoundManager().play(gazeSound);
        }
    }

    static float gaze(float partial) {
        return Mth.lerp(partial, prevGaze, gaze);
    }

    // ------------------------------------------------------------------ hallucinations

    /** Footsteps of whatever you are standing on, a few metres behind you, coming closer. */
    private static void startFootsteps(LocalPlayer player) {
        stepsLeft = 4 + RANDOM.nextInt(4);
        stepDist = 7.0 + RANDOM.nextDouble() * 3.0;
        stepAngle = player.getYRot() * Mth.DEG_TO_RAD + Math.PI + (RANDOM.nextDouble() - 0.5) * 0.8;
        stepTimer = 1;
    }

    private static void tickFootsteps(Minecraft mc, LocalPlayer player) {
        if (stepsLeft <= 0 || --stepTimer > 0) return;
        double x = player.getX() - Math.sin(stepAngle) * stepDist;
        double z = player.getZ() + Math.cos(stepAngle) * stepDist;
        BlockState under = mc.level.getBlockState(player.getOnPos());
        SoundType type = under.isAir() ? SoundType.GRASS : under.getSoundType();
        SoundInstance s = new SimpleSoundInstance(type.getStepSound(), SoundSource.HOSTILE,
                type.getVolume() * 0.55F, type.getPitch() * (0.85F + RANDOM.nextFloat() * 0.1F), RANDOM, x, player.getY(), z);
        mc.getSoundManager().play(s);
        stepsLeft--;
        stepDist = Math.max(2.5, stepDist - 0.8);
        stepTimer = 9 + RANDOM.nextInt(4);
    }

    /** A breathy voice right next to one ear. */
    private static void whisper(Minecraft mc, LocalPlayer player) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double side = RANDOM.nextBoolean() ? 1.0 : -1.0;
        // Right vector of the view: (-cos(yaw), -sin(yaw)).
        double x = player.getX() - Math.cos(yaw) * 1.3 * side + Math.sin(yaw) * 0.6;
        double z = player.getZ() - Math.sin(yaw) * 1.3 * side - Math.cos(yaw) * 0.6;
        float vol = FogConfig.CLIENT.ambientVolume.get().floatValue();
        mc.getSoundManager().play(new SimpleSoundInstance(FogSounds.WHISPER.get(), SoundSource.AMBIENT,
                0.7F * vol, 0.9F + RANDOM.nextFloat() * 0.15F, RANDOM, x, player.getEyeY(), z));
    }

    /** Something knocks on the nearest door (or on a wall) of the shelter you hide in. */
    private static void startKnocks(Minecraft mc, LocalPlayer player) {
        BlockPos center = player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (mc.level.getBlockState(p).getBlock() instanceof DoorBlock) {
                        double d = p.distSqr(center);
                        if (d < bestDist) {
                            bestDist = d;
                            best = p.immutable();
                        }
                    }
                }
            }
        }
        if (best == null) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0;
            best = BlockPos.containing(player.getX() + Math.cos(a) * 3.5, player.getEyeY(), player.getZ() + Math.sin(a) * 3.5);
        }
        knockPos = best;
        knocksLeft = 2 + RANDOM.nextInt(3);
        knockTimer = 1;
    }

    private static void tickKnocks(Minecraft mc) {
        if (knocksLeft <= 0 || knockPos == null || --knockTimer > 0) return;
        mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE,
                0.55F, 0.7F + RANDOM.nextFloat() * 0.15F, RANDOM, knockPos.getX() + 0.5, knockPos.getY() + 1.0, knockPos.getZ() + 0.5));
        knocksLeft--;
        knockTimer = 13 + RANDOM.nextInt(6);
    }

    private static void playAround(Minecraft mc, LocalPlayer player, SoundEvent event, float volume, float pitch, int minDist, int maxDist) {
        double a = RANDOM.nextDouble() * Math.PI * 2.0;
        double d = minDist + RANDOM.nextDouble() * (maxDist - minDist);
        mc.getSoundManager().play(new SimpleSoundInstance(event, SoundSource.AMBIENT, volume, pitch, RANDOM,
                player.getX() + Math.cos(a) * d, player.getEyeY() + RANDOM.nextDouble() * 4.0 - 2.0, player.getZ() + Math.sin(a) * d));
    }

    // ------------------------------------------------------------------ camera

    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!FogConfig.CLIENT.cameraShake.get()) return;
        float partial = (float) event.getPartialTick();
        float shake = switch (ClientFogState.exposure()) {
            case EXPOSED -> 0.45F;
            case GRACE -> 0.15F;
            default -> 0.0F;
        };
        float js = jumpscareStrength(partial);
        shake += js * js * 6.0F + gaze(partial) * 0.3F;
        shake *= FogConfig.CLIENT.effectsIntensity.get().floatValue();
        if (shake <= 0.001F) return;
        float t = ClientFogState.clientTicks + partial;
        event.setRoll(event.getRoll() + shake * (Mth.sin(t * 1.7F) * 0.6F + Mth.sin(t * 3.1F + 0.7F) * 0.4F));
        event.setPitch(event.getPitch() + shake * 0.5F * Mth.sin(t * 2.3F + 1.0F));
        event.setYaw(event.getYaw() + shake * 0.5F * Mth.sin(t * 1.9F + 2.0F));
    }

    static void onFov(ViewportEvent.ComputeFov event) {
        float g = gaze((float) event.getPartialTick()) * FogConfig.CLIENT.effectsIntensity.get().floatValue();
        if (g > 0.001F) event.setFOV(event.getFOV() * (1.0 - 0.08 * g));
    }

    private static float jumpscareStrength(float partial) {
        if (jumpscareTicks <= 0) return 0.0F;
        return Mth.clamp((jumpscareTicks - partial) / JUMPSCARE_TICKS, 0.0F, 1.0F);
    }

    // ------------------------------------------------------------------ overlay

    static void renderOverlay(GuiGraphics g, float partial, int width, int height) {
        float intensity = FogConfig.CLIENT.effectsIntensity.get().floatValue();
        float js = jumpscareStrength(partial);
        float staticAlpha = gaze(partial) * 0.16F * intensity;
        if (js > 0.0F) {
            float since = JUMPSCARE_TICKS * (1.0F - js);
            float black = since < 10.0F ? 1.0F : js * js;
            g.fill(0, 0, width, height, ((int) (black * 255.0F) << 24) | 0x050000);
            if (since < 10.0F) {
                // Two pale eyes in the dark, right in front of you.
                int jitterX = RANDOM.nextInt(3) - 1, jitterY = RANDOM.nextInt(3) - 1;
                int cx = width / 2 + jitterX, cy = (int) (height * 0.44F) + jitterY;
                int ew = Math.max(4, height / 22), eh = Math.max(2, height / 64), gap = height / 9;
                int a = (int) (Mth.clamp(1.0F - since / 10.0F, 0.0F, 1.0F) * 230.0F + 20.0F);
                int eye = (a << 24) | 0xDCE4E2;
                g.fill(cx - gap / 2 - ew, cy, cx - gap / 2, cy + eh, eye);
                g.fill(cx + gap / 2, cy, cx + gap / 2 + ew, cy + eh, eye);
            }
            staticAlpha = Math.max(staticAlpha, 0.45F * js);
        }
        if (staticAlpha > 0.01F) renderStatic(g, width, height, staticAlpha);
    }

    private static void renderStatic(GuiGraphics g, int width, int height, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1.0F, 1.0F, 1.0F, Mth.clamp(alpha, 0.0F, 1.0F));
        int tile = 64;
        for (int x = 0; x < width; x += tile) {
            for (int y = 0; y < height; y += tile) {
                int u = RANDOM.nextInt(128), v = RANDOM.nextInt(128);
                g.blit(STATIC, x, y, tile, tile, u, v, 128, 128, 256, 256);
            }
        }
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    /** Ringing in the ears while you look at a Watcher; follows the gaze. */
    private static final class GazeSound extends AbstractTickableSoundInstance {
        GazeSound(SoundEvent event) {
            super(event, SoundSource.AMBIENT, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001F;
        }

        void stopNow() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            float target = gaze * 0.75F * FogConfig.CLIENT.ambientVolume.get().floatValue();
            volume += (target - volume) * 0.15F;
            if (gaze <= 0.0F && volume < 0.01F) stop();
        }
    }
}
