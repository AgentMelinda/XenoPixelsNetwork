package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.combat.clone.CloneDetectRange;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Runtime control for Shi Shin No Ken copies (server-wide).
 * <pre>
 * /xenomultiform status
 * /xenomultiform ai clone|brain
 * /xenomultiform distance &lt;0.5-16&gt;
 * /xenomultiform detect &lt;1-128&gt;
 * /xenomultiform retaliate on|off
 * /xenomultiform hostile on|off
 * /xenomultiform vanish on|off
 * </pre>
 * Body count stays on {@code /xenoserver set multiformbodies}.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class MultiFormCommands {
    private MultiFormCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(tree("xenomultiform"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        return Commands.literal(name)
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /" + name + " status\n"
                                    + "       /" + name + " ai <clone|brain|multiform>\n"
                                    + "       /" + name + " distance <0.5-16>\n"
                                    + "       /" + name + " detect <1-128>\n"
                                    + "       /" + name + " look|retaliate|hostile|vanish <on|off>\n"
                                    + currentLine()), false);
                    return 1;
                })
                .then(Commands.literal("status")
                        .requires(XenoPermissions.require(XenoPermissions.XENOMULTIFORM_STATUS))
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("ai")
                        .requires(XenoPermissions.require(XenoPermissions.XENOMULTIFORM_SET))
                        .then(Commands.literal("clone")
                                .executes(ctx -> setAi(ctx.getSource(), "clone")))
                        .then(Commands.literal("brain")
                                .executes(ctx -> setAi(ctx.getSource(), "brain")))
                        // The copies' own brain. A third mode rather than a replacement, so the two
                        // that already work stay reachable and the three can be compared in play.
                        .then(Commands.literal("multiform")
                                .executes(ctx -> setAi(ctx.getSource(), "multiform"))))
                .then(Commands.literal("distance")
                        .requires(XenoPermissions.require(XenoPermissions.XENOMULTIFORM_SET))
                        .then(Commands.argument("blocks", FloatArgumentType.floatArg(
                                        XenoServerConfig.MULTI_FORM_RADIUS_MIN,
                                        XenoServerConfig.MULTI_FORM_RADIUS_MAX))
                                .executes(ctx -> setDistance(ctx.getSource(),
                                        FloatArgumentType.getFloat(ctx, "blocks")))))
                .then(Commands.literal("detect")
                        .requires(XenoPermissions.require(XenoPermissions.XENOMULTIFORM_SET))
                        .then(Commands.argument("blocks", FloatArgumentType.floatArg(
                                        (float) CloneDetectRange.MIN,
                                        (float) CloneDetectRange.MAX))
                                .executes(ctx -> setDetect(ctx.getSource(),
                                        FloatArgumentType.getFloat(ctx, "blocks")))))
                .then(flag("look",
                        () -> XenoServerConfig.multiFormLook,
                        XenoServerConfig::setMultiFormLook,
                        "Multi-form look target"))
                .then(flag("retaliate",
                        () -> XenoServerConfig.multiFormRetaliate,
                        XenoServerConfig::setMultiFormRetaliate,
                        "Multi-form retaliate"))
                .then(flag("hostile",
                        () -> XenoServerConfig.multiFormHostile,
                        XenoServerConfig::setMultiFormHostile,
                        "Multi-form hostile"))
                .then(flag("vanish",
                        () -> XenoServerConfig.multiFormVanish,
                        XenoServerConfig::setMultiFormVanish,
                        "Multi-form vanish"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> flag(String name,
            java.util.function.BooleanSupplier get,
            java.util.function.Consumer<Boolean> set, String label) {
        return Commands.literal(name)
                .requires(XenoPermissions.require(XenoPermissions.XENOMULTIFORM_SET))
                .then(Commands.argument("on", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean on = BoolArgumentType.getBool(ctx, "on");
                            set.accept(on);
                            DmzHudCommands.broadcast();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    label + ": " + (on ? "ON" : "OFF")), true);
                            return 1;
                        }))
                .then(Commands.literal("on").executes(ctx -> {
                    set.accept(true);
                    DmzHudCommands.broadcast();
                    ctx.getSource().sendSuccess(() -> Component.literal(label + ": ON"), true);
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    set.accept(false);
                    DmzHudCommands.broadcast();
                    ctx.getSource().sendSuccess(() -> Component.literal(label + ": OFF"), true);
                    return 1;
                }));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(currentLine()), false);
        return 1;
    }

    private static int setAi(CommandSourceStack source, String ai) {
        XenoServerConfig.setMultiFormAi(ai);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Multi-form AI: " + XenoServerConfig.normalizeMultiFormAi(XenoServerConfig.multiFormAi)),
                true);
        return 1;
    }

    private static int setDistance(CommandSourceStack source, float blocks) {
        XenoServerConfig.setMultiFormRadius(blocks);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Multi-form distance: " + XenoServerConfig.clampedMultiFormRadius()), true);
        return 1;
    }

    private static int setDetect(CommandSourceStack source, float blocks) {
        XenoServerConfig.setMultiFormDetectRange(blocks);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Multi-form detect: " + XenoServerConfig.clampedMultiFormDetectRange()), true);
        return 1;
    }

    static String currentLine() {
        return "multiform ai=" + XenoServerConfig.normalizeMultiFormAi(XenoServerConfig.multiFormAi)
                + " distance=" + XenoServerConfig.clampedMultiFormRadius()
                + " detect=" + XenoServerConfig.clampedMultiFormDetectRange()
                + " look=" + (XenoServerConfig.multiFormLook ? "ON" : "OFF")
                + " retaliate=" + (XenoServerConfig.multiFormRetaliate ? "ON" : "OFF")
                + " hostile=" + (XenoServerConfig.multiFormHostile ? "ON" : "OFF")
                + " vanish=" + (XenoServerConfig.multiFormVanish ? "ON" : "OFF")
                + " bodies=" + XenoServerConfig.multiFormBodies
                + " (bodies: /xenoserver set multiformbodies)";
    }
}
