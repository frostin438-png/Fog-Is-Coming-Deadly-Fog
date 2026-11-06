package com.fogiscoming.command;

import com.fogiscoming.FogIsComing;
import com.fogiscoming.protection.LightProtectionManager;
import com.fogiscoming.protection.ShelterDetector;
import com.fogiscoming.registry.FogTags;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code /fog selftest} — in-world QA of the protection systems.
 * <p>
 * Builds a row of small test scenes high in the sky above the command position (open sky, so real
 * sunlight/sky light is present), waits for the light engine, evaluates light and shelter protection exactly as
 * the damage handler does, breaks a wall of the test house and re-checks it, prints PASS/FAIL and removes every
 * block it placed.
 */
public final class FogSelfTest {
    private static final int SPACING = 24;
    private static final int SETTLE_TICKS = 60;
    private static Run active;

    private FogSelfTest() {
    }

    private record Scene(String name, BlockPos feet, Boolean expectLight, Boolean expectShelter) {
    }

    private static final class Run {
        final CommandSourceStack source;
        final ServerLevel level;
        final Set<BlockPos> placed = new LinkedHashSet<>();
        final Set<Long> forcedChunks = new LinkedHashSet<>();
        final List<Scene> scenes = new ArrayList<>();
        BlockPos brokenWall;
        Scene house;
        int ticks;
        int phase;
        int passed, failed;

        Run(CommandSourceStack source, ServerLevel level) {
            this.source = source;
            this.level = level;
        }

        void set(BlockPos pos, BlockState state) {
            placed.add(pos.immutable());
            int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
            long key = ChunkPos.asLong(cx, cz);
            // Keep test chunks loaded and ticking (lighting) until the test ends.
            if (!forcedChunks.contains(key) && !level.getForcedChunks().contains(key)) {
                level.setChunkForced(cx, cz, true);
                forcedChunks.add(key);
            }
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }

        void set(BlockPos pos, Block block) {
            set(pos, block.defaultBlockState());
        }

        void box(BlockPos min, BlockPos max, BlockState state, boolean hollow) {
            for (BlockPos p : BlockPos.betweenClosed(min, max)) {
                boolean edge = p.getX() == min.getX() || p.getX() == max.getX() || p.getY() == min.getY()
                        || p.getY() == max.getY() || p.getZ() == min.getZ() || p.getZ() == max.getZ();
                if (!hollow || edge) set(p, state);
            }
        }

        void say(String text, ChatFormatting color) {
            source.sendSuccess(() -> Component.literal(text).withStyle(color), false);
            FogIsComing.LOGGER.info("[FogIsComing selftest] {}", text);
        }
    }

    public static int start(CommandSourceStack source) {
        if (active != null) {
            source.sendFailure(Component.literal("A self-test is already running."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());
        int y = level.getMaxBuildHeight() - 24;
        Run run = new Run(source, level);
        BlockPos base = new BlockPos(origin.getX(), y, origin.getZ());
        build(run, base);
        active = run;
        run.say("Self-test: built " + run.scenes.size() + " scenes at y=" + y + ", waiting " + SETTLE_TICKS
                + " ticks for lighting...", ChatFormatting.GRAY);
        return 1;
    }

    private static void build(Run r, BlockPos base) {
        int[] index = {0};
        // Every scene has a 1-block stone "floor" under the feet so the position is realistic.
        java.util.function.Function<String, BlockPos> next = name -> {
            BlockPos feet = base.offset(index[0]++ * SPACING, 0, 0);
            r.set(feet.below(), Blocks.STONE);
            return feet;
        };

        BlockPos p;
        p = next.apply("open");
        r.scenes.add(new Scene("Open sky, no light (sunlight must NOT protect)", p, false, false));

        p = next.apply("torch");
        r.set(p.offset(3, -1, 0), Blocks.STONE);
        r.set(p.offset(3, 0, 0), Blocks.TORCH);
        r.scenes.add(new Scene("Torch 3 blocks away", p, true, false));

        p = next.apply("soul_torch");
        r.set(p.offset(2, -1, 1), Blocks.STONE);
        r.set(p.offset(2, 0, 1), Blocks.SOUL_TORCH);
        r.scenes.add(new Scene("Soul torch 2 blocks away", p, true, null));

        p = next.apply("lantern");
        r.set(p.offset(0, -1, 4), Blocks.STONE);
        r.set(p.offset(0, 0, 4), Blocks.LANTERN);
        r.scenes.add(new Scene("Lantern 4 blocks away", p, true, null));

        p = next.apply("soul_lantern");
        r.set(p.offset(-3, -1, 0), Blocks.STONE);
        r.set(p.offset(-3, 0, 0), Blocks.SOUL_LANTERN);
        r.scenes.add(new Scene("Soul lantern 3 blocks away", p, true, null));

        p = next.apply("campfire");
        r.set(p.offset(3, -1, 0), Blocks.STONE);
        r.set(p.offset(3, 0, 0), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        r.scenes.add(new Scene("Lit campfire 3 blocks away", p, true, null));

        p = next.apply("campfire_off");
        r.set(p.offset(3, -1, 0), Blocks.STONE);
        r.set(p.offset(3, 0, 0), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        r.scenes.add(new Scene("Extinguished campfire (must NOT protect)", p, false, null));

        p = next.apply("glowstone");
        r.set(p.offset(0, 2, 3), Blocks.GLOWSTONE);
        r.scenes.add(new Scene("Glowstone 3.6 blocks away", p, true, null));

        p = next.apply("sea_lantern");
        r.set(p.offset(4, 0, 0), Blocks.SEA_LANTERN);
        r.scenes.add(new Scene("Sea lantern 4 blocks away", p, true, null));

        p = next.apply("lamp_on");
        r.set(p.offset(3, -1, 0), Blocks.REDSTONE_BLOCK);
        r.set(p.offset(3, 0, 0), Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
        r.scenes.add(new Scene("Powered redstone lamp", p, true, null));

        p = next.apply("lamp_off");
        r.set(p.offset(3, 0, 0), Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, false));
        r.scenes.add(new Scene("Unpowered redstone lamp (must NOT protect)", p, false, null));

        p = next.apply("torch_far");
        r.set(p.offset(10, -1, 0), Blocks.STONE);
        r.set(p.offset(10, 0, 0), Blocks.TORCH);
        r.scenes.add(new Scene("Torch 10 blocks away (outside radius)", p, false, null));

        p = next.apply("torch_walled");
        BlockPos t = p.offset(3, 0, 0);
        r.box(t.offset(-1, -1, -1), t.offset(1, 1, 1), Blocks.STONE.defaultBlockState(), false);
        r.set(t, Blocks.TORCH);
        r.scenes.add(new Scene("Torch sealed behind stone (light does not reach)", p, false, null));

        p = next.apply("lava");
        BlockPos l = p.offset(3, 0, 0);
        r.box(l.offset(-1, -1, -1), l.offset(1, -1, 1), Blocks.STONE.defaultBlockState(), false);
        r.set(l.offset(-1, 0, 0), Blocks.STONE);
        r.set(l.offset(1, 0, 0), Blocks.STONE);
        r.set(l.offset(0, 0, -1), Blocks.STONE);
        r.set(l.offset(0, 0, 1), Blocks.STONE);
        r.set(l, Blocks.LAVA);
        r.scenes.add(new Scene("Lava pool 3 blocks away (blacklisted)", p, false, null));

        // House: stone shell, 5x5 interior, 3 high, no openings.
        p = next.apply("house");
        r.box(p.offset(-3, -1, -3), p.offset(3, 3, 3), Blocks.STONE_BRICKS.defaultBlockState(), true);
        Scene house = new Scene("Sealed stone house", p, false, true);
        r.scenes.add(house);
        r.house = house;
        r.brokenWall = p.offset(3, 0, 0);

        p = next.apply("glass_house");
        r.box(p.offset(-3, -1, -3), p.offset(3, 3, 3), Blocks.OAK_PLANKS.defaultBlockState(), true);
        r.box(p.offset(-2, 3, -2), p.offset(2, 3, 2), Blocks.GLASS.defaultBlockState(), false);
        r.scenes.add(new Scene("Wooden house with glass roof", p, false, true));

        p = next.apply("door_house");
        r.box(p.offset(-3, -1, -3), p.offset(3, 3, 3), Blocks.COBBLESTONE.defaultBlockState(), true);
        r.set(p.offset(3, 0, 0), Blocks.AIR.defaultBlockState());
        r.set(p.offset(3, 1, 0), Blocks.AIR.defaultBlockState());
        r.scenes.add(new Scene("House with a doorway left open (must NOT protect)", p, false, false));

        p = next.apply("pit");
        r.box(p.offset(-1, -3, -1), p.offset(1, 2, 1), Blocks.DIRT.defaultBlockState(), false);
        r.set(p, Blocks.AIR.defaultBlockState());
        r.set(p.above(), Blocks.AIR.defaultBlockState());
        r.scenes.add(new Scene("1x1 pit with a block on top (not a house)", p, false, false));

        p = next.apply("lit_house");
        r.box(p.offset(-3, -1, -3), p.offset(3, 3, 3), Blocks.STONE.defaultBlockState(), true);
        r.set(p.offset(2, 0, 2), Blocks.TORCH);
        r.scenes.add(new Scene("Sealed house with a torch inside", p, true, true));
    }

    public static void tick(MinecraftServer server) {
        Run r = active;
        if (r == null) return;
        r.ticks++;
        try {
            if (r.phase == 0 && r.ticks >= SETTLE_TICKS) {
                evaluateAll(r);
                r.set(r.brokenWall, Blocks.AIR.defaultBlockState());
                ShelterDetector.invalidateNear(r.level, r.brokenWall);
                r.phase = 1;
                r.ticks = 0;
            } else if (r.phase == 1 && r.ticks >= 10) {
                var s = ShelterDetector.scan(r.level, r.house.feet());
                check(r, "Stone house after breaking one wall block", null, null, !s.sheltered() ? "shelter lost (" + s.reason().text + ")" : "STILL SHELTERED", !s.sheltered());
                boolean damageType = r.level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(FogTags.FOG_DAMAGE).isPresent();
                check(r, "Damage type fogiscoming:fog registered", null, null, damageType ? "present" : "missing", damageType);
                finish(r);
            }
        } catch (RuntimeException e) {
            r.say("Self-test crashed: " + e, ChatFormatting.RED);
            FogIsComing.LOGGER.error("Self-test failed", e);
            finish(r);
        }
    }

    private static void evaluateAll(Run r) {
        for (Scene s : r.scenes) {
            var light = LightProtectionManager.check(r.level, s.feet());
            var shelter = ShelterDetector.scan(r.level, s.feet());
            int sky = r.level.getBrightness(LightLayer.SKY, s.feet());
            boolean ok = (s.expectLight() == null || s.expectLight() == light.protectedByLight())
                    && (s.expectShelter() == null || s.expectShelter() == shelter.sheltered());
            String detail = "light=" + (light.protectedByLight() ? "YES(" + light.sourceState().getBlock().getName().getString() + ")" : "no")
                    + " blockLight=" + light.blockLight() + " skyLight=" + sky
                    + " shelter=" + (shelter.sheltered() ? "YES" : "no") + "(" + shelter.reason().text + ", vol " + shelter.volume() + ")";
            check(r, s.name(), s.expectLight(), s.expectShelter(), detail, ok);
        }
    }

    private static void check(Run r, String name, Boolean el, Boolean es, String detail, boolean ok) {
        if (ok) r.passed++;
        else r.failed++;
        r.say((ok ? "PASS " : "FAIL ") + name + " — " + detail, ok ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static void finish(Run r) {
        // Remove fluids/attached blocks first, then everything else.
        List<BlockPos> list = new ArrayList<>(r.placed);
        for (int i = list.size() - 1; i >= 0; i--) {
            r.level.setBlock(list.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (long key : r.forcedChunks) {
            r.level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false);
        }
        r.say("Self-test finished: " + r.passed + " passed, " + r.failed + " failed. Test blocks removed.",
                r.failed == 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
        active = null;
    }
}
