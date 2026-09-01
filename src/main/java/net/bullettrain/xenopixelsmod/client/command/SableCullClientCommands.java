package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.compat.sable.SableContraptionCull;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Local FPS clamp for Sable+Create entity scans.
 * <pre>
 * /sablecull          — toggle
 * /sablecull on|off
 * /sablecull status
 * </pre>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class SableCullClientCommands {

    private SableCullClientCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("sablecull")
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("on").executes(ctx -> set(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> set(ctx.getSource(), false)))
                .then(Commands.literal("toggle").executes(ctx -> toggle(ctx.getSource())))
                .then(Commands.literal("debug").executes(ctx -> toggleDebug(ctx.getSource())))
                .executes(ctx -> toggle(ctx.getSource())));
        d.register(Commands.literal("xenosablecull")
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("on").executes(ctx -> set(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> set(ctx.getSource(), false)))
                .executes(ctx -> toggle(ctx.getSource())));
    }

    private static int toggle(CommandSourceStack source) {
        return set(source, !XenoClientConfig.sableContraptionCullClient);
    }

    private static int set(CommandSourceStack source, boolean on) {
        XenoClientConfig.sableContraptionCullClient = on;
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal(
                "Sable ship FPS cull: " + (on ? "ON" : "OFF")
                        + (on ? "" : " (pose-exploded Create scan — FPS will drop)")), false);
        return on ? 1 : 0;
    }

    private static int status(CommandSourceStack source) {
        boolean on = XenoClientConfig.sableContraptionCullClient;
        source.sendSuccess(() -> Component.literal(
                "Sable ship FPS cull: " + (on ? "ON" : "OFF")
                        + " debug=" + SableContraptionCull.debugHud
                        + "  (/sablecull on|off|toggle|debug). Server MSPT: /xenoperf sablecull"), false);
        return on ? 1 : 0;
    }

    private static int toggleDebug(CommandSourceStack source) {
        SableContraptionCull.debugHud = !SableContraptionCull.debugHud;
        source.sendSuccess(() -> Component.literal(
                "sablecull debug: " + (SableContraptionCull.debugHud ? "ON" : "OFF")
                        + (SableContraptionCull.debugHud
                        ? " — actionbar once/sec: createScans fanOutSkip maxSide secDraw secSkip"
                        : "")), false);
        return SableContraptionCull.debugHud ? 1 : 0;
    }
}
