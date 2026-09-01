package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bullettrain.xenopixelsmod.combat.technique.KiDuration;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Last-session ki knobs.
 * <pre>
 * /xenoki status
 * /xenoki duration &lt;ticks&gt; | /xenoki duration &lt;type&gt; &lt;ticks&gt;
 * /xenoki charge &lt;true|false&gt;
 * /xenoki cap &lt;percent&gt;
 * /xenoki grief &lt;true|false&gt;
 * /xenoki maxsize|maxspeed|destruction &lt;n&gt;
 * </pre>
 */
@EventBusSubscriber(modid = net.bullettrain.xenopixelsmod.XenoPixelsMod.MOD_ID)
public final class KiDurationCommands {

    private static final SuggestionProvider<CommandSourceStack> TYPE_SUGGEST =
            (ctx, builder) -> SharedSuggestionProvider.suggest(KiDuration.TYPE_KEYS, builder);

    private KiDurationCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenoki")
                .then(Commands.literal("status")
                        .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_STATUS))
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("duration")
                        .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_SET))
                        .then(Commands.argument("ticks",
                                        IntegerArgumentType.integer(0, XenoServerConfig.BARRAGE_TICKS_MAX))
                                .executes(ctx -> setAll(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "ticks"))))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(TYPE_SUGGEST)
                                .then(Commands.argument("ticks",
                                                IntegerArgumentType.integer(0, XenoServerConfig.BARRAGE_TICKS_MAX))
                                        .executes(ctx -> setType(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "type"),
                                                IntegerArgumentType.getInteger(ctx, "ticks"))))))
                .then(Commands.literal("charge")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_SET))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> setBool(ctx.getSource(), "chargeOverchargeEnabled",
                                        BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("cap")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_SET))
                        .then(Commands.argument("percent", FloatArgumentType.floatArg(201.0f, 2000.0f))
                                .executes(ctx -> setKey(ctx.getSource(), "chargeOverchargeMaxPercent",
                                        Float.toString(FloatArgumentType.getFloat(ctx, "percent"))))))
                .then(Commands.literal("grief")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_SET))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> setBool(ctx.getSource(), "chargeOverchargeGriefEnabled",
                                        BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("maxsize")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_SET))
                        .then(Commands.argument("size", FloatArgumentType.floatArg(0.1f))
                                .executes(ctx -> setKey(ctx.getSource(), "kiProjectileMaxSize",
                                        Float.toString(FloatArgumentType.getFloat(ctx, "size"))))))
                .then(Commands.literal("maxspeed")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_SET))
                        .then(Commands.argument("speed", FloatArgumentType.floatArg(0.1f))
                                .executes(ctx -> setKey(ctx.getSource(), "kiProjectileMaxSpeed",
                                        Float.toString(FloatArgumentType.getFloat(ctx, "speed"))))))
                .then(Commands.literal("destruction")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_SET))
                        .then(Commands.argument("radius", FloatArgumentType.floatArg(0.5f))
                                .executes(ctx -> setKey(ctx.getSource(), "kiDestructionMaxRadius",
                                        Float.toString(FloatArgumentType.getFloat(ctx, "radius"))))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenoki duration <ticks> | /xenoki duration <type> <ticks>\n"
                                    + "       /xenoki charge|grief <true|false>\n"
                                    + "       /xenoki cap <201-2000> | maxsize|maxspeed|destruction <n>\n"
                                    + "types: " + String.join(" ", KiDuration.TYPE_KEYS) + "\n"
                                    + "0 duration = stock DMZ (base × charge). 20 ticks = 1 second.\n"
                                    + currentLine()), false);
                    return 1;
                }));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(currentLine()), false);
        return 1;
    }

    private static int setAll(CommandSourceStack source, int ticks) {
        XenoServerConfig.setKiDurationTicks(ticks);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "All ki fire time: " + format(XenoServerConfig.kiDurationTicks)
                        + (XenoServerConfig.kiDurationTicks == 0 ? " (stock per type)" : "")), true);
        return 1;
    }

    private static int setType(CommandSourceStack source, String type, int ticks) {
        if (!KiDuration.isKnownType(type)) {
            source.sendFailure(Component.literal(
                    "Unknown type '" + type + "'. Use: " + String.join(" ", KiDuration.TYPE_KEYS)));
            return 0;
        }
        XenoServerConfig.setKiDurationForType(type, ticks);
        DmzHudCommands.broadcast();
        Integer now = XenoServerConfig.kiDurationByType.get(type.toLowerCase());
        source.sendSuccess(() -> Component.literal(
                type.toLowerCase() + " fire time: " + format(now == null ? 0 : now)
                        + (now == null || now == 0 ? " (stock)" : "")), true);
        return 1;
    }

    private static int setBool(CommandSourceStack source, String key, boolean value) {
        return setKey(source, key, Boolean.toString(value));
    }

    private static int setKey(CommandSourceStack source, String key, String value) {
        var result = net.bullettrain.xenopixelsmod.config.XenoServerConfigKeys.set(key, value);
        if (!result.ok) {
            source.sendFailure(Component.literal(result.message));
            return 0;
        }
        XenoServerConfig.save();
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(result.message), true);
        return 1;
    }

    static String currentLine() {
        StringBuilder sb = new StringBuilder("ki duration all=");
        sb.append(format(XenoServerConfig.kiDurationTicks));
        if (XenoServerConfig.kiDurationTicks == 0) sb.append(" (stock)");
        if (!XenoServerConfig.kiDurationByType.isEmpty()) {
            sb.append(" types=").append(XenoServerConfig.kiDurationByType);
        }
        sb.append(" barrage=").append(format(XenoServerConfig.barrageDurationTicks));
        sb.append(" charge=").append(XenoServerConfig.chargeOverchargeEnabled);
        sb.append(" cap=").append(XenoServerConfig.chargeOverchargeMaxPercent);
        sb.append(" grief=").append(XenoServerConfig.chargeOverchargeGriefEnabled);
        sb.append(" maxSize=").append(XenoServerConfig.kiProjectileMaxSize);
        sb.append(" maxSpeed=").append(XenoServerConfig.kiProjectileMaxSpeed);
        sb.append(" destruction=").append(XenoServerConfig.kiDestructionMaxRadius);
        sb.append(" guideRange=").append(XenoServerConfig.guidanceControlRange <= 0
                ? "default" : XenoServerConfig.guidanceControlRange + " blocks");
        return sb.toString();
    }

    static String format(int ticks) {
        if (ticks <= 0) return "0 ticks";
        float sec = ticks / 20.0f;
        return ticks + " ticks (" + (sec == (int) sec ? String.valueOf((int) sec) : String.format("%.2f", sec)) + "s)";
    }
}
