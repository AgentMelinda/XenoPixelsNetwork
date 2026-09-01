package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.config.XenoServerConfigKeys;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Server command for ki-barrage fire window, cooldown, and Guidance.
 * <pre>
 * /xenobarrage status|duration|cooldown|range|kipertick|extra|cdreduce
 * /xenoguide status|range|turn|rate|grace|lookmin
 * </pre>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class BarrageCommands {

    private BarrageCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xenoguide")
                .then(Commands.literal("status")
                        .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_STATUS))
                        .executes(ctx -> guideStatus(ctx.getSource())))
                .then(Commands.literal("range")
                        .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_SET))
                        .then(Commands.argument("blocks",
                                        IntegerArgumentType.integer(0, XenoServerConfig.GUIDANCE_RANGE_MAX))
                                .executes(ctx -> setRange(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "blocks")))))
                .then(Commands.literal("turn")
                        .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_SET))
                        .then(Commands.argument("rate", FloatArgumentType.floatArg(0.0f, 1.0f))
                                .executes(ctx -> setKey(ctx.getSource(), "guidanceTurnRate",
                                        Float.toString(FloatArgumentType.getFloat(ctx, "rate"))))))
                .then(Commands.literal("rate")
                        .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_SET))
                        .then(Commands.argument("rate", FloatArgumentType.floatArg(0.0f, 1.0f))
                                .executes(ctx -> setKey(ctx.getSource(), "guidanceCameraRate",
                                        Float.toString(FloatArgumentType.getFloat(ctx, "rate"))))))
                .then(Commands.literal("grace")
                        .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_SET))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 40))
                                .executes(ctx -> setKey(ctx.getSource(), "guidanceHoldGraceTicks",
                                        Integer.toString(IntegerArgumentType.getInteger(ctx, "ticks"))))))
                .then(Commands.literal("lookmin")
                        .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_SET))
                        .then(Commands.argument("blocks", FloatArgumentType.floatArg(0.0f, 64.0f))
                                .executes(ctx -> setKey(ctx.getSource(), "guidanceLookRayMin",
                                        Float.toString(FloatArgumentType.getFloat(ctx, "blocks"))))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenoguide range <blocks>   — 0 = default 192+48×level\n"
                                    + "       /xenoguide turn <0-1> | rate <0-1> | grace <ticks> | lookmin <blocks>\n"
                                    + guideLine()), false);
                    return 1;
                }));
        dispatcher.register(Commands.literal("xenobarrage")
                .then(Commands.literal("status")
                        .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_STATUS))
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("duration")
                        .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_SET))
                        .then(Commands.argument("ticks",
                                        IntegerArgumentType.integer(0, XenoServerConfig.BARRAGE_TICKS_MAX))
                                .executes(ctx -> setDuration(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "ticks")))))
                .then(Commands.literal("time")
                        .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_SET))
                        .then(Commands.argument("ticks",
                                        IntegerArgumentType.integer(0, XenoServerConfig.BARRAGE_TICKS_MAX))
                                .executes(ctx -> setDuration(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "ticks")))))
                .then(Commands.literal("cooldown")
                        .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_SET))
                        .then(Commands.argument("ticks",
                                        IntegerArgumentType.integer(0, XenoServerConfig.BARRAGE_TICKS_MAX))
                                .executes(ctx -> setCooldown(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "ticks")))))
                .then(Commands.literal("range")
                        .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_SET))
                        .then(Commands.argument("blocks",
                                        IntegerArgumentType.integer(0, XenoServerConfig.GUIDANCE_RANGE_MAX))
                                .executes(ctx -> setRange(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "blocks")))))
                .then(Commands.literal("kipertick")
                        .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_SET))
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0f))
                                .executes(ctx -> setKey(ctx.getSource(), "barrageKiPerTick",
                                        Float.toString(FloatArgumentType.getFloat(ctx, "amount"))))))
                .then(Commands.literal("extra")
                        .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_SET))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 3))
                                .then(Commands.argument("ticks",
                                                IntegerArgumentType.integer(0, XenoServerConfig.BARRAGE_TICKS_MAX))
                                        .executes(ctx -> setExtra(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "level"),
                                                IntegerArgumentType.getInteger(ctx, "ticks"))))))
                .then(Commands.literal("cdreduce")
                        .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_SET))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 3))
                                .then(Commands.argument("ticks",
                                                IntegerArgumentType.integer(0, XenoServerConfig.BARRAGE_TICKS_MAX))
                                        .executes(ctx -> setCdReduce(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "level"),
                                                IntegerArgumentType.getInteger(ctx, "ticks"))))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenobarrage <status|duration <ticks>|cooldown <ticks>|range <blocks>>\n"
                                    + "       /xenobarrage kipertick <n> | extra <1-3> <ticks> | cdreduce <1-3> <ticks>\n"
                                    + "duration 0 = stock DMZ (50 × charge). cooldown 0 = technique default.\n"
                                    + "range 0 = default Guidance reach. 20 ticks = 1 second.\n"
                                    + currentLine()), false);
                    return 1;
                }));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(currentLine()), false);
        return 1;
    }

    private static int guideStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(guideLine()), false);
        return 1;
    }

    private static int setDuration(CommandSourceStack source, int ticks) {
        XenoServerConfig.setBarrageDurationTicks(ticks);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Barrage fire time: " + formatTicks(XenoServerConfig.barrageDurationTicks)
                        + (XenoServerConfig.barrageDurationTicks == 0
                        ? " (stock DMZ 50 × charge)" : "")), true);
        return 1;
    }

    private static int setCooldown(CommandSourceStack source, int ticks) {
        XenoServerConfig.setBarrageCooldownTicks(ticks);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Barrage cooldown: " + formatTicks(XenoServerConfig.barrageCooldownTicks)
                        + (XenoServerConfig.barrageCooldownTicks == 0
                        ? " (technique default)" : "")), true);
        return 1;
    }

    private static int setRange(CommandSourceStack source, int blocks) {
        XenoServerConfig.setGuidanceControlRange(blocks);
        DmzHudCommands.broadcast();
        int now = XenoServerConfig.guidanceControlRange;
        source.sendSuccess(() -> Component.literal(
                "Guidance control range: " + (now <= 0
                        ? "default (192 + 48 × skill)"
                        : now + " blocks")), true);
        return 1;
    }

    private static int setExtra(CommandSourceStack source, int level, int ticks) {
        XenoServerConfig.setBarrageExtraTicks(level, ticks);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Barrage extra ticks L" + level + " = " + ticks), true);
        return 1;
    }

    private static int setCdReduce(CommandSourceStack source, int level, int ticks) {
        XenoServerConfig.setBarrageCooldownReduce(level, ticks);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Barrage cooldown reduce L" + level + " = " + ticks), true);
        return 1;
    }

    private static int setKey(CommandSourceStack source, String key, String value) {
        XenoServerConfigKeys.Result result = XenoServerConfigKeys.set(key, value);
        if (!result.ok) {
            source.sendFailure(Component.literal(result.message));
            return 0;
        }
        XenoServerConfig.save();
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(result.message), true);
        return 1;
    }

    private static String currentLine() {
        return "barrage duration=" + formatTicks(XenoServerConfig.barrageDurationTicks)
                + (XenoServerConfig.barrageDurationTicks == 0 ? " (stock)" : "")
                + " cooldown=" + formatTicks(XenoServerConfig.barrageCooldownTicks)
                + (XenoServerConfig.barrageCooldownTicks == 0 ? " (technique)" : "")
                + " kipertick=" + XenoServerConfig.barrageKiPerTick
                + " extra=" + XenoServerConfig.barrageExtraTicks1
                + "/" + XenoServerConfig.barrageExtraTicks2
                + "/" + XenoServerConfig.barrageExtraTicks3
                + " cdreduce=" + XenoServerConfig.barrageCooldownReduce1
                + "/" + XenoServerConfig.barrageCooldownReduce2
                + "/" + XenoServerConfig.barrageCooldownReduce3
                + " " + guideLine();
    }

    private static String guideLine() {
        int range = XenoServerConfig.guidanceControlRange;
        return "guideRange=" + (range <= 0 ? "default" : range + " blocks")
                + " turn=" + XenoServerConfig.guidanceTurnRate
                + " rate=" + XenoServerConfig.guidanceCameraRate
                + " grace=" + XenoServerConfig.guidanceHoldGraceTicks
                + " lookmin=" + XenoServerConfig.guidanceLookRayMin;
    }

    private static String formatTicks(int ticks) {
        if (ticks <= 0) return "0 ticks";
        float sec = ticks / 20.0f;
        return ticks + " ticks (" + (sec == (int) sec ? String.valueOf((int) sec) : String.format("%.2f", sec))
                + "s)";
    }
}
