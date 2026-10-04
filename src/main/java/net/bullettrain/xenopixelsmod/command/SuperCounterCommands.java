package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Runtime control for the BT3 super-counter parry.
 * <pre>
 * /xenosupercounter status
 * /xenosupercounter on|off|toggle
 * /xenosupercounter window &lt;ticks&gt;
 * /xenosupercounter kicost &lt;0-1000&gt;
 * /xenosupercounter scale &lt;n&gt;
 * </pre>
 * Same fields as {@code /xenoserver set supercounter|counterwindow|counterkicost|counterscale}.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SuperCounterCommands {
    private SuperCounterCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(tree("xenosupercounter"));
        dispatcher.register(tree("xenocounter"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        return Commands.literal(name)
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /" + name + " <on|off|toggle|status>\n"
                                    + "       /" + name + " window <4-40> | kicost <0-1000> | scale <n>\n"
                                    + currentLine()), false);
                    return 1;
                })
                .then(Commands.literal("status")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSUPERCOUNTER_STATUS))
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("on")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSUPERCOUNTER_SET))
                        .executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(Commands.literal("off")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSUPERCOUNTER_SET))
                        .executes(ctx -> setEnabled(ctx.getSource(), false)))
                .then(Commands.literal("toggle")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSUPERCOUNTER_SET))
                        .executes(ctx -> setEnabled(ctx.getSource(),
                                !XenoServerConfig.bt3SuperCounterEnabled)))
                .then(Commands.literal("window")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSUPERCOUNTER_SET))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(4, 40))
                                .executes(ctx -> setWindow(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "ticks")))))
                .then(Commands.literal("kicost")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSUPERCOUNTER_SET))
                        .then(Commands.argument("cost", FloatArgumentType.floatArg(0.0f, 1000.0f))
                                .executes(ctx -> setKiCost(ctx.getSource(),
                                        FloatArgumentType.getFloat(ctx, "cost")))))
                .then(Commands.literal("scale")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSUPERCOUNTER_SET))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.01f, 100.0f))
                                .executes(ctx -> setScale(ctx.getSource(),
                                        FloatArgumentType.getFloat(ctx, "scale")))));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(currentLine()), false);
        return 1;
    }

    private static int setEnabled(CommandSourceStack source, boolean enabled) {
        XenoServerConfig.setBt3SuperCounterEnabled(enabled);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Super counter: " + (enabled ? "ON" : "OFF")), true);
        return 1;
    }

    private static int setWindow(CommandSourceStack source, int ticks) {
        XenoServerConfig.setSuperCounterWindowTicks(ticks);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Super-counter window: " + XenoServerConfig.superCounterWindowTicks + " ticks"), true);
        return 1;
    }

    private static int setKiCost(CommandSourceStack source, float cost) {
        XenoServerConfig.setSuperCounterKiCost(cost);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Super-counter ki cost: " + XenoServerConfig.superCounterKiCost), true);
        return 1;
    }

    private static int setScale(CommandSourceStack source, float scale) {
        XenoServerConfig.setSuperCounterDamageScale(scale);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Super-counter damage scale: " + XenoServerConfig.superCounterDamageScale), true);
        return 1;
    }

    static String currentLine() {
        return "super counter=" + (XenoServerConfig.bt3SuperCounterEnabled ? "ON" : "OFF")
                + " window=" + XenoServerConfig.superCounterWindowTicks + " ticks"
                + " kicost=" + XenoServerConfig.superCounterKiCost
                + " scale=" + XenoServerConfig.superCounterDamageScale;
    }
}
