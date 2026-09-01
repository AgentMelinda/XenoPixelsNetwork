package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Runtime control for DMZ Z-lock through walls.
 * <pre>
 * /xenolock through status
 * /xenolock through on|off|toggle
 * </pre>
 * Server flag is synced to every client. On the integrated host this also
 * writes {@code XenoClientConfig.lockOnThroughBlocks}.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class LockOnCommands {

    private LockOnCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenolock")
                .then(Commands.literal("through")
                        .executes(ctx -> status(ctx.getSource()))
                        .then(Commands.literal("status")
                                .requires(XenoPermissions.require(XenoPermissions.XENOLOCK_STATUS))
                                .executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("on")
                                .requires(XenoPermissions.require(XenoPermissions.XENOLOCK_SET))
                                .executes(ctx -> setThrough(ctx.getSource(), true)))
                        .then(Commands.literal("off")
                                .requires(XenoPermissions.require(XenoPermissions.XENOLOCK_SET))
                                .executes(ctx -> setThrough(ctx.getSource(), false)))
                        .then(Commands.literal("toggle")
                                .requires(XenoPermissions.require(XenoPermissions.XENOLOCK_SET))
                                .executes(ctx -> setThrough(ctx.getSource(),
                                        !XenoServerConfig.lockOnThroughBlocks))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenolock through <on|off|toggle|status>\n" + currentLine()), false);
                    return 1;
                }));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(currentLine()), false);
        return 1;
    }

    private static int setThrough(CommandSourceStack source, boolean enabled) {
        XenoServerConfig.setLockOnThroughBlocks(enabled);
        DmzHudCommands.broadcast();
        boolean clientSet = applyLocalClient(enabled);
        source.sendSuccess(() -> Component.literal(
                "Lock through blocks: " + (enabled ? "ON" : "OFF")
                        + " (server" + (clientSet ? " + this client" : "") + ")"), true);
        return 1;
    }

    private static boolean applyLocalClient(boolean enabled) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return false;
        }
        XenoClientConfig.lockOnThroughBlocks = enabled;
        XenoClientConfig.save();
        return true;
    }

    private static String currentLine() {
        return "lock through blocks: server=" + XenoServerConfig.lockOnThroughBlocks
                + (FMLEnvironment.dist == Dist.CLIENT
                ? " client=" + XenoClientConfig.lockOnThroughBlocks
                : "");
    }
}
