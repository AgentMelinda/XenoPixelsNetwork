package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Runtime control for chase-dash's fly-to-target mode.
 * <pre>
 * /xenochase status
 * /xenochase toggle &lt;true|false&gt;
 * /xenochase range &lt;blocks&gt;
 * </pre>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ChaseFlightCommands {

    private ChaseFlightCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenochase")
                .then(Commands.literal("status")
                        .requires(XenoPermissions.require(XenoPermissions.XENOCHASE_STATUS))
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("toggle")
                        .requires(XenoPermissions.require(XenoPermissions.XENOCHASE_SET))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> setToggle(ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("range")
                        .requires(XenoPermissions.require(XenoPermissions.XENOCHASE_SET))
                        .then(Commands.argument("blocks", FloatArgumentType.floatArg(0.0f, 512.0f))
                                .executes(ctx -> setRange(ctx.getSource(),
                                        FloatArgumentType.getFloat(ctx, "blocks")))))
                .then(Commands.literal("speed")
                        .requires(XenoPermissions.require(XenoPermissions.XENOCHASE_SET))
                        .then(Commands.argument("blocksPerTick", FloatArgumentType.floatArg(0.1f, 20.0f))
                                .executes(ctx -> setSpeed(ctx.getSource(),
                                        FloatArgumentType.getFloat(ctx, "blocksPerTick")))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenochase toggle <true|false> | /xenochase range <0-512> (0=unlimited)"
                                    + " | /xenochase speed <0.1-20>\n"
                                    + currentLine()), false);
                    return 1;
                }));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(currentLine()), false);
        return 1;
    }

    private static int setToggle(CommandSourceStack source, boolean enabled) {
        XenoServerConfig.setChaseFlightEnabled(enabled);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Chase flight: " + (enabled ? "enabled" : "disabled")), true);
        return 1;
    }

    private static int setRange(CommandSourceStack source, double blocks) {
        XenoServerConfig.setChaseMaxRange(blocks);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Chase range: " + (XenoServerConfig.chaseRangeUnlimited()
                        ? "unlimited" : XenoServerConfig.chaseMaxRange + " blocks")), true);
        return 1;
    }

    private static int setSpeed(CommandSourceStack source, double blocksPerTick) {
        XenoServerConfig.setChaseFlightSpeed(blocksPerTick);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Chase speed: " + XenoServerConfig.chaseFlightSpeed + " blocks/tick"), true);
        return 1;
    }

    private static String currentLine() {
        return "chase flight=" + XenoServerConfig.chaseFlightEnabled
                + " range=" + (XenoServerConfig.chaseRangeUnlimited()
                ? "unlimited" : XenoServerConfig.chaseMaxRange + " blocks")
                + " speed=" + XenoServerConfig.chaseFlightSpeed + " blocks/tick";
    }
}
