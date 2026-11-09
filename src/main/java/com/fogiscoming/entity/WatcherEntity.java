package com.fogiscoming.entity;

import com.fogiscoming.config.FogConfig;
import com.fogiscoming.damage.FogDamageHandler;
import com.fogiscoming.network.FogNetworking;
import com.fogiscoming.network.FogScarePacket;
import com.fogiscoming.registry.FogSounds;
import com.fogiscoming.registry.FogTags;
import com.fogiscoming.world.FogStage;
import com.fogiscoming.world.FogStageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A Watcher: a tall dark figure at the edge of the fog. It has no AI goals; everything is driven from
 * {@link #tick()} on the server.
 * <ul>
 *     <li>Always faces the player it came for.</li>
 *     <li>Vanishes when stared at for a moment, when hit, when it touches light, or after a while.</li>
 *     <li>Stage II: it only watches, and vanishes if the player comes close.</li>
 *     <li>Stage III: while the player is exposed and looks away, it glides closer. If it reaches them it strikes.</li>
 * </ul>
 * Watchers are never saved with the world.
 */
public class WatcherEntity extends Mob {
    private static final double HUNT_SPEED = 0.32;

    private UUID targetId;
    private int age;
    private int stareTicks;

    public WatcherEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setSilent(true);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public void setTargetPlayer(UUID player) {
        this.targetId = player;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel level = (ServerLevel) level();
        ServerPlayer player = targetId == null ? null : (ServerPlayer) level.getPlayerByUUID(targetId);
        FogStage stage = FogStageManager.getStage();
        if (player == null || !player.isAlive() || player.isSpectator() || stage.id < 2 || !FogStageManager.isAffected(level)) {
            discard();
            return;
        }
        age++;
        boolean deadly = stage.isDeadly();
        double dist = distanceTo(player);
        faceTowards(player);

        if (age > (deadly ? 1800 : 1200) || dist > 96) {
            discard();
            return;
        }
        // Light burns it away.
        if (level.getBrightness(LightLayer.BLOCK, blockPosition().above()) >= FogConfig.SERVER.minBlockLight.get()) {
            vanish(player, true);
            return;
        }

        double dot = lookDot(player);
        boolean seen = dot > 0.55 && player.hasLineOfSight(this);
        boolean stared = seen && dot > (dist < 12 ? 0.94 : 0.975);
        stareTicks = stared ? stareTicks + 1 : Math.max(0, stareTicks - 1);
        if (stareTicks > (deadly ? 30 : 14)) {
            vanish(player, true);
            return;
        }
        if (!deadly) {
            if (dist < 12) vanish(player, false);
            return;
        }

        // Stage III: it comes for exposed players whenever they are not looking.
        boolean exposed = FogDamageHandler.state(player).status.isUnsafe();
        if (!exposed || seen || age < 30 || !FogConfig.SERVER.watcherAttacks.get()) return;
        if (dist < 1.6) {
            strike(player);
            return;
        }
        Vec3 to = player.position().subtract(position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        double len = flat.length();
        if (len < 1.0E-3) return;
        Vec3 step = flat.scale(Math.min(HUNT_SPEED, Math.max(0.05, len - 1.0)) / len);
        double nx = getX() + step.x;
        double nz = getZ() + step.z;
        // It glides through trunks and fences; it only follows the ground up and down.
        double ny = groundNear(level, nx, nz, getY());
        if (Math.abs(player.getY() - ny) < 1.5) ny = player.getY();
        // Steps up at once (never sinks into a slope), drifts down smoothly.
        moveTo(nx, ny > getY() ? ny : Mth.lerp(0.5, getY(), ny), nz, getYRot(), getXRot());
    }

    /** Standing height near {@code y} at this column, or {@code y} itself if it is inside something solid. */
    private static double groundNear(ServerLevel level, double x, double z, double y) {
        int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(y);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = 2; dy >= -4; dy--) {
            p.set(bx, by + dy, bz);
            BlockState here = level.getBlockState(p);
            if (!here.getCollisionShape(level, p).isEmpty()) continue;
            p.move(0, -1, 0);
            BlockState below = level.getBlockState(p);
            if (!below.getCollisionShape(level, p).isEmpty()) return by + dy;
        }
        return y;
    }

    /** How directly the player looks at this Watcher: 1 = straight at it. */
    private double lookDot(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 to = new Vec3(getX(), getY() + getBbHeight() * 0.7, getZ()).subtract(eye);
        double len = to.length();
        if (len < 1.0E-3) return 1.0;
        return player.getViewVector(1.0F).dot(to.scale(1.0 / len));
    }

    private void faceTowards(Entity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double dy = target.getEyeY() - getEyeY();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        setXRot(Mth.clamp(pitch, -40.0F, 40.0F));
    }

    private void strike(ServerPlayer player) {
        com.fogiscoming.FogIsComing.LOGGER.debug("A Watcher reached {}", player.getGameProfile().getName());
        double amount = FogConfig.SERVER.watcherDamage.get();
        if (amount > 0) {
            DamageSource source = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(FogTags.WATCHER_DAMAGE), this);
            player.hurt(source, (float) amount);
        }
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false));
        FogNetworking.sendTo(player, new FogScarePacket(FogScarePacket.JUMPSCARE));
        discard();
    }

    /** Disappears in a puff of dark smoke; {@code audible} plays a soft rushing sound the player can hear from afar. */
    public void vanish(ServerPlayer player, boolean audible) {
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.1, getZ(), 24, 0.2, 0.7, 0.2, 0.01);
        if (audible && player != null) {
            float volume = (float) Math.max(1.0, distanceTo(player) / 16.0 + 0.4);
            level.playSound(null, getX(), getY() + 1.5, getZ(), FogSounds.WATCHER_VANISH.get(), SoundSource.HOSTILE,
                    volume, 0.85F + random.nextFloat() * 0.3F);
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        // Walls, falls, fire and the like mean nothing to it; only being struck makes it go away.
        if (source.getEntity() == null && source.getDirectEntity() == null) return false;
        if (!level().isClientSide && isAlive()) {
            vanish(targetId == null ? null : (ServerPlayer) level().getPlayerByUUID(targetId), true);
        }
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    /** Spawn helper: is this an open, dark spot on the ground where a Watcher can stand? */
    public static boolean canStandAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState below = level.getBlockState(pos.below());
        if (below.getCollisionShape(level, pos.below()).isEmpty() || !below.getFluidState().isEmpty()) return false;
        for (int i = 0; i < 3; i++) {
            BlockPos p = pos.above(i);
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty() || !s.getFluidState().isEmpty()) return false;
        }
        return level.getBrightness(LightLayer.BLOCK, pos.above()) < FogConfig.SERVER.minBlockLight.get();
    }
}
