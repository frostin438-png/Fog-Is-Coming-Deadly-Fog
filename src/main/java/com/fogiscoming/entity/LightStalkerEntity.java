package com.fogiscoming.entity;

import com.fogiscoming.FogIsComing;
import com.fogiscoming.config.FogConfig;
import com.fogiscoming.protection.ShelterDetector;
import com.fogiscoming.registry.FogSounds;
import com.fogiscoming.registry.FogTags;
import com.fogiscoming.world.FogStageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Light Stalker: a hunched, long-armed thing that comes out of the fog to take the light away.
 * <ul>
 *     <li>Walks to the nearest torch or lantern ({@code fogiscoming:stalker_targets}), plucks it and carries it off.</li>
 *     <li>Walks through open doorways and breaks wooden doors that are in its way (iron doors hold).</li>
 *     <li>Ignores players unless it is hit; then it fights back for a few seconds.</li>
 *     <li>After a few thefts, or when nothing is left to take, it walks back into the fog and is gone, and the
 *     lights with it. Killing it first drops everything it took.</li>
 * </ul>
 * Never saved with the world.
 */
public class LightStalkerEntity extends PathfinderMob {
    public static final int SEARCH_RADIUS = 20;
    private static final int SEARCH_HEIGHT = 8;
    private static final double REACH_SQR = 2.7 * 2.7;
    private static final int MAX_AGE = 2400;
    /** 3 seconds of splintering per door. */
    private static final int DOOR_BREAK_TICKS = 60;

    private final List<ItemStack> loot = new ArrayList<>();
    private final Set<BlockPos> unreachable = new HashSet<>();
    @Nullable
    private BlockPos targetLight;
    private double bestDistSqr;
    private int stuckTicks;
    private int searchCooldown = 20;
    private int emptySearches;
    private int stolen;
    private int age;
    private int angerTicks;
    private boolean leaving;
    private boolean breakingDoor;
    private int leavingTicks;

    public LightStalkerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 5;
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        if (getNavigation() instanceof GroundPathNavigation nav) {
            // Lets paths lead through closed wooden doors; the door goal then breaks them.
            nav.setCanOpenDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new StalkerBreakDoorGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    public int stolenCount() {
        return stolen;
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        age++;
        if (FogStageManager.getStage().id < 2 || !FogStageManager.isAffected(level())) {
            vanish();
            return;
        }
        LivingEntity enemy = getTarget();
        if (enemy != null) {
            boolean harmless = enemy instanceof Player p && (p.isCreative() || p.isSpectator());
            if (++angerTicks > 160 || !enemy.isAlive() || harmless) {
                setTarget(null);
                angerTicks = 0;
            } else {
                return; // busy fighting back
            }
        }
        if (leaving) {
            tickLeaving();
            return;
        }
        if (age > MAX_AGE || stolen >= FogConfig.SERVER.stalkerMaxLights.get()) {
            startLeaving();
            return;
        }
        if (targetLight != null && !isStealable(level().getBlockState(targetLight))) {
            targetLight = null; // someone else took it
            searchCooldown = 10;
        }
        if (targetLight == null) {
            if (--searchCooldown > 0) return;
            searchCooldown = 30;
            targetLight = findLight((ServerLevel) level(), blockPosition(), SEARCH_RADIUS, unreachable);
            if (targetLight == null) {
                if (++emptySearches >= 3) startLeaving();
                return;
            }
            emptySearches = 0;
            stuckTicks = 0;
            bestDistSqr = Double.MAX_VALUE;
        }

        double distSqr = targetLight.distToCenterSqr(getX(), getY() + 1.0, getZ());
        if (distSqr <= REACH_SQR && hasClearReach(targetLight)) {
            steal(targetLight);
            return;
        }
        if (distSqr < bestDistSqr - 0.25) {
            bestDistSqr = distSqr;
            stuckTicks = 0;
        } else if (!breakingDoor && ++stuckTicks > 140) {
            // Walled in or out of reach: forget this one.
            unreachable.add(targetLight);
            targetLight = null;
            searchCooldown = 5;
            return;
        }
        if (getNavigation().isDone() || age % 20 == 0) {
            getNavigation().moveTo(targetLight.getX() + 0.5, targetLight.getY(), targetLight.getZ() + 0.5, 1.0);
        }
    }

    /** No reaching through walls: nothing solid between its eyes and the light. */
    private boolean hasClearReach(BlockPos pos) {
        BlockHitResult hit = level().clip(new ClipContext(getEyePosition(), Vec3.atCenterOf(pos),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    private void steal(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        level().levelEvent(2001, pos, Block.getId(state));
        level().removeBlock(pos, false);
        playSound(FogSounds.STALKER_STEAL.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        swing(InteractionHand.MAIN_HAND);
        if (!stack.isEmpty()) {
            loot.add(stack);
            setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        }
        stolen++;
        targetLight = null;
        searchCooldown = 25;
        getNavigation().stop();
        FogIsComing.LOGGER.debug("Light Stalker took {} at {}", state.getBlock(), pos);
    }

    private void startLeaving() {
        leaving = true;
        leavingTicks = 0;
        targetLight = null;
    }

    /** Walks away from the nearest player and dissolves once it is far enough (or after a while). */
    private void tickLeaving() {
        leavingTicks++;
        Player near = level().getNearestPlayer(this, 64.0);
        if (near == null || distanceToSqr(near) > 26.0 * 26.0 || leavingTicks > 300) {
            vanish();
            return;
        }
        if (getNavigation().isDone() || leavingTicks % 40 == 1) {
            Vec3 away = position().subtract(near.position());
            Vec3 flat = new Vec3(away.x, 0.0, away.z);
            if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(1.0, 0.0, 0.0);
            Vec3 goal = position().add(flat.normalize().scale(16.0));
            getNavigation().moveTo(goal.x, goal.y, goal.z, 1.2);
        }
    }

    /** Gone, together with everything it took. */
    public void vanish() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.9, getZ(), 18, 0.2, 0.5, 0.2, 0.01);
        }
        discard();
    }

    public static boolean isStealable(BlockState state) {
        return state.is(FogTags.STALKER_TARGETS);
    }

    /** Nearest torch or lantern around {@code center}, skipping {@code exclude}. */
    @Nullable
    public static BlockPos findLight(ServerLevel level, BlockPos center, int radius, @Nullable Set<BlockPos> exclude) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int height = Math.min(SEARCH_HEIGHT, radius);
        for (int dy = -height; dy <= height; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double d = dx * dx + dz * dz + dy * dy * 4.0; // prefer lights on the same floor
                    if (d >= bestDist) continue;
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.isLoaded(p) || !isStealable(level.getBlockState(p))) continue;
                    if (exclude != null && exclude.contains(p)) continue;
                    bestDist = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ mob plumbing

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        for (ItemStack stack : loot) spawnAtLocation(stack);
        loot.clear();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return FogSounds.STALKER_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 140;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FogSounds.STALKER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FogSounds.STALKER_DEATH.get();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    /** Breaks wooden doors on any difficulty, and tells the shelter check that a house was just opened. */
    private static final class StalkerBreakDoorGoal extends BreakDoorGoal {
        private final LightStalkerEntity stalker;

        StalkerBreakDoorGoal(LightStalkerEntity stalker) {
            super(stalker, DOOR_BREAK_TICKS, difficulty -> true);
            this.stalker = stalker;
        }

        /** Vanilla never goes below 12 seconds; this thing is quicker. */
        @Override
        protected int getDoorBreakTime() {
            return DOOR_BREAK_TICKS;
        }

        @Override
        public void start() {
            super.start();
            stalker.breakingDoor = true;
        }

        @Override
        public boolean canUse() {
            return FogConfig.SERVER.stalkerBreaksDoors.get() && super.canUse();
        }

        @Override
        public void stop() {
            super.stop();
            stalker.breakingDoor = false;
            stalker.stuckTicks = 0;
            if (doorPos != null) ShelterDetector.invalidateNear(mob.level(), doorPos);
        }
    }
}
