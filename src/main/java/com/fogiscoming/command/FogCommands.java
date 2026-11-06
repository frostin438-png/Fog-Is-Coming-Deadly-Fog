package com.fogiscoming.command;

import com.fogiscoming.damage.FogDamageHandler;
import com.fogiscoming.protection.LightProtectionManager;
import com.fogiscoming.protection.ShelterDetector;
import com.fogiscoming.world.FogStage;
import com.fogiscoming.world.FogStageManager;
import com.fogiscoming.world.FogWorldData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /fog status | setstage <0-3> | auto | reset | skipdays <days> | debug | selftest}.
 * Everything except {@code status} requires permission level 2 (operators / cheats enabled).
 */
public final class FogCommands {
    private FogCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fog")
                .then(Commands.literal("status").executes(FogCommands::status))
                .then(Commands.literal("setstage").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("stage", IntegerArgumentType.integer(0, 3))
                                .executes(ctx -> setStage(ctx, IntegerArgumentType.getInteger(ctx, "stage")))))
                .then(Commands.literal("auto").requires(s -> s.hasPermission(2)).executes(FogCommands::auto))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2)).executes(FogCommands::reset))
                .then(Commands.literal("skipdays").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("days", DoubleArgumentType.doubleArg(0.0, 1000.0))
                                .executes(ctx -> skipDays(ctx, DoubleArgumentType.getDouble(ctx, "days")))))
                .then(Commands.literal("debug").requires(s -> s.hasPermission(2)).executes(FogCommands::debug))
                .then(Commands.literal("selftest").requires(s -> s.hasPermission(2)).executes(ctx -> FogSelfTest.start(ctx.getSource()))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        FogWorldData data = FogWorldData.get(server);
        FogStage stage = FogStageManager.computeStage(data);
        float density = FogStageManager.computeDensity(data, stage);
        long elapsed = data.getElapsedTicks();

        ctx.getSource().sendSuccess(() -> Component.literal("— FOG IS COMING —").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Stage: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(stage.id + " (" + stage.title + ")").withStyle(stage.isDeadly() ? ChatFormatting.RED : ChatFormatting.WHITE)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("Mode: %s | Elapsed: %.2f days (%d ticks) | Density: %.2f",
                data.getForcedStage() >= 0 ? "FORCED" : "AUTOMATIC", elapsed / (double) FogStageManager.TICKS_PER_DAY, elapsed, density))
                .withStyle(ChatFormatting.GRAY), false);
        if (data.getForcedStage() < 0 && stage.id < 3) {
            long next = FogStageManager.stageStartTick(stage.id + 1) - elapsed;
            ctx.getSource().sendSuccess(() -> Component.literal("Next stage in: " + formatTicks(Math.max(0, next)))
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return stage.id;
    }

    private static int setStage(CommandContext<CommandSourceStack> ctx, int stage) {
        FogStageManager.forceStage(ctx.getSource().getServer(), stage);
        ctx.getSource().sendSuccess(() -> Component.literal("Fog stage forced to " + stage + " (" + FogStage.byId(stage).title
                + "). It is saved with the world. Use /fog auto to resume natural progression.").withStyle(ChatFormatting.YELLOW), true);
        return stage;
    }

    private static int auto(CommandContext<CommandSourceStack> ctx) {
        FogStageManager.setAutomatic(ctx.getSource().getServer());
        FogStage stage = FogStageManager.getStage();
        ctx.getSource().sendSuccess(() -> Component.literal("Fog progression is automatic again. Current stage: " + stage.title)
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        FogStageManager.reset(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("Fog progression reset. The countdown starts again.")
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int skipDays(CommandContext<CommandSourceStack> ctx, double days) {
        FogStageManager.addDays(ctx.getSource().getServer(), days);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("Fog progression advanced by %.2f days. Stage: %s",
                days, FogStageManager.getStage().title)).withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int debug(CommandContext<CommandSourceStack> ctx) {
        status(ctx);
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("(run as a player for protection diagnostics)").withStyle(ChatFormatting.DARK_GRAY), false);
            return 1;
        }
        var light = LightProtectionManager.check(player.serverLevel(), player.blockPosition());
        var shelter = ShelterDetector.scan(player.serverLevel(), player.blockPosition());
        var st = FogDamageHandler.state(player);
        boolean affected = FogStageManager.isAffected(player.level());

        line(ctx, "Dimension affected: " + affected);
        line(ctx, "Block light (feet/head max): " + light.blockLight() + " | Light protection: "
                + (light.protectedByLight()
                ? "YES — " + light.sourceState().getBlock().getName().getString() + " at " + light.sourcePos().toShortString()
                + String.format(" (%.1f blocks)", light.distance())
                : "NO"));
        line(ctx, "Shelter: " + (shelter.sheltered() ? "YES" : "NO") + " — " + shelter.reason().text
                + " | volume " + shelter.volume() + ", floor " + shelter.footprint() + ", sky leaks " + shelter.leaks());
        line(ctx, "Exposure status: " + st.status + " | unsafe ticks " + st.unsafeTicks + " | hits " + st.hits
                + " | next hit in " + Math.max(0, st.ticksToNextHit) + " ticks");
        line(ctx, "Last evaluation: " + st.protectionDetail);
        return 1;
    }

    private static void line(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GRAY), false);
    }

    private static String formatTicks(long ticks) {
        long days = ticks / FogStageManager.TICKS_PER_DAY;
        long rest = ticks % FogStageManager.TICKS_PER_DAY;
        long hours = rest / 1000;
        long minutes = (rest % 1000) * 60 / 1000;
        return String.format("%02d days %02d:%02d (%d ticks)", days, hours, minutes, ticks);
    }
}
