package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * {@code /xenoflight <field> <value>} / {@code /xenoflight status} — live tuning of the
 * client-side, per-player flight-feel constants in {@link XenoClientConfig} that had a field but
 * no runtime command, most notably {@link XenoClientConfig#flightStickRampPerSec} — how sensitive
 * a held rotation key is (how fast it ramps to full deflection and decays back on release).
 * Client-only and per-player, unlike the server-wide {@code /xenoaerotune} — modelled on
 * {@code client/command/WingPanelDebugCommands.java}'s registration shape.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoFlightConfigCommands {
    private XenoFlightConfigCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("xenoflight")
                .then(Commands.literal("status").executes(ctx -> status()))
                .executes(ctx -> status());
        root.then(field("ramp", "press/hold sensitivity (0..1 stick ramp, units/sec)"));
        root.then(field("rollrate", "Q/E commanded roll rate, deg/sec"));
        root.then(field("pitchrate", "A/D commanded pitch rate, deg/sec (keyboard mode)"));
        root.then(field("autolevel", "auto-level roll-back rate, deg/sec"));
        root.then(field("zoomrate", "+/- contraption-camera zoom rate"));
        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> field(String name, String help) {
        return Commands.literal(name)
                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                        .executes(ctx -> set(name, DoubleArgumentType.getDouble(ctx, "value"), help)));
    }

    private static int set(String name, double raw, String help) {
        float applied;
        switch (name) {
            case "ramp" -> applied = XenoClientConfig.flightStickRampPerSec =
                    (float) Math.max(1.0, Math.min(20.0, raw));
            case "rollrate" -> applied = XenoClientConfig.flightRollRateDegPerSec =
                    (float) Math.max(5.0, Math.min(360.0, raw));
            case "pitchrate" -> applied = XenoClientConfig.flightPitchRateDegPerSec =
                    (float) Math.max(5.0, Math.min(360.0, raw));
            case "autolevel" -> applied = XenoClientConfig.flightAutoLevelRateDegPerSec =
                    (float) Math.max(0.0, Math.min(360.0, raw));
            case "zoomrate" -> applied = XenoClientConfig.flightZoomKeyRatePerSec =
                    (float) Math.max(0.1, Math.min(50.0, raw));
            default -> {
                feedback("§cUnknown field: " + name);
                return 0;
            }
        }
        XenoClientConfig.save();
        feedback("§eflight " + name + " §f= §a" + fmt(applied) + " §7(" + help + ")");
        return 1;
    }

    private static int status() {
        feedback("§eFlight input tuning:");
        line("ramp", XenoClientConfig.flightStickRampPerSec);
        line("rollrate", XenoClientConfig.flightRollRateDegPerSec);
        line("pitchrate", XenoClientConfig.flightPitchRateDegPerSec);
        line("autolevel", XenoClientConfig.flightAutoLevelRateDegPerSec);
        line("zoomrate", XenoClientConfig.flightZoomKeyRatePerSec);
        feedback("§7  mouseaim §f= §b" + XenoClientConfig.flightMouseAim
                + " §7(toggle with K while seated, or /xenoflight does not touch this)");
        return 1;
    }

    private static void line(String name, float value) {
        feedback("§7  " + name + " §f= §b" + fmt(value));
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    private static void feedback(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
