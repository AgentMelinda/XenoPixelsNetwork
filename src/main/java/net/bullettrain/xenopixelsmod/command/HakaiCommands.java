package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Runtime control for the Hakai erasure technique.
 * <pre>
 * /xenohakai status
 * /xenohakai toggle &lt;true|false&gt;
 * /xenohakai kicost &lt;0-1000&gt;
 * /xenohakai range &lt;1-64&gt;
 * /xenohakai cooldown &lt;40-2400&gt;
 * </pre>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class HakaiCommands {

    private HakaiCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenohakai")
                .then(Commands.literal("status")
                        .requires(XenoPermissions.require(XenoPermissions.HAKAI_SET))
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("toggle")
                        .requires(XenoPermissions.require(XenoPermissions.HAKAI_SET))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> setToggle(ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("kicost")
                        .requires(XenoPermissions.require(XenoPermissions.HAKAI_SET))
                        .then(Commands.argument("cost", FloatArgumentType.floatArg(0.0f, 1000.0f))
                                .executes(ctx -> setKiCost(ctx.getSource(),
                                        FloatArgumentType.getFloat(ctx, "cost")))))
                .then(Commands.literal("range")
                        .requires(XenoPermissions.require(XenoPermissions.HAKAI_SET))
                        .then(Commands.argument("blocks", FloatArgumentType.floatArg(1.0f, 64.0f))
                                .executes(ctx -> setRange(ctx.getSource(),
                                        FloatArgumentType.getFloat(ctx, "blocks")))))
                .then(Commands.literal("cooldown")
                        .requires(XenoPermissions.require(XenoPermissions.HAKAI_SET))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(40, 2400))
                                .executes(ctx -> setCooldown(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "ticks")))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenohakai toggle <true|false> | kicost <0-1000>"
                                    + " | range <1-64> | cooldown <40-2400>\n"
                                    + currentLine()), false);
                    return 1;
                }));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(currentLine()), false);
        return 1;
    }

    private static int setToggle(CommandSourceStack source, boolean enabled) {
        XenoServerConfig.setHakaiEnabled(enabled);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Hakai: " + (enabled ? "enabled" : "disabled")), true);
        return 1;
    }

    private static int setKiCost(CommandSourceStack source, float cost) {
        XenoServerConfig.setHakaiKiCost(cost);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Hakai ki cost: " + XenoServerConfig.hakaiKiCost), true);
        return 1;
    }

    private static int setRange(CommandSourceStack source, double blocks) {
        XenoServerConfig.setHakaiMaxRange(blocks);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Hakai range: " + XenoServerConfig.hakaiMaxRange + " blocks"), true);
        return 1;
    }

    private static int setCooldown(CommandSourceStack source, int ticks) {
        XenoServerConfig.setHakaiCooldownTicks(ticks);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Hakai cooldown: " + XenoServerConfig.hakaiCooldownTicks + " ticks"), true);
        return 1;
    }

    private static String currentLine() {
        return "hakai enabled=" + XenoServerConfig.hakaiEnabled
                + " kicost=" + XenoServerConfig.hakaiKiCost
                + " range=" + XenoServerConfig.hakaiMaxRange + " blocks"
                + " cooldown=" + XenoServerConfig.hakaiCooldownTicks + " ticks";
    }
}
