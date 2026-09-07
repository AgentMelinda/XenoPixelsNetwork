package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.aero.AeroConfig;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /xenoaerotune <field> <value>} / {@code /xenoaerotune status} — live tuning of the
 * flight-feel constants without a restart. Each setter clamps, assigns the {@link AeroConfig}
 * field, and persists via {@link AeroConfig#save()}. Modelled on {@code AeroPowerCommands}.
 *
 * <p>All of these take effect on the next physics tick; they are not per-ship, they are the
 * server-wide defaults.
 *
 * <p>{@code maxangular} and {@code maxspeed} are the exception to that: they live in
 * {@link XenoServerConfig} rather than {@link AeroConfig}, because they are the hard physics
 * walls rather than feel constants, and they persist through that config's own save.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class AeroTuneCommands {
    private AeroTuneCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(command());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("xenoaerotune")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .executes(ctx -> status(ctx.getSource()));
        root.then(field("damping", "arcade angular damping (higher = heavier instructor)"));
        root.then(field("kp", "attitude-hold P gain (mouse-aim only)"));
        root.then(field("maxaccel", "max commanded angular accel, rad/s^2"));
        root.then(field("torque", "control-surface torque scale"));
        root.then(field("authspeed", "speed where control authority stops scaling"));
        root.then(field("deadzone", "keyboard stick deadzone (0..0.5)"));
        root.then(field("expo", "keyboard stick expo (0 linear .. 1 cubic)"));
        root.then(field("maxangular", "hard angular-velocity wall, rad/s (server config)"));
        root.then(field("maxspeed", "hard flight speed wall, blocks/s (server config)"));
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> field(String name, String help) {
        return Commands.literal(name)
                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                        .executes(ctx -> set(ctx.getSource(), name, DoubleArgumentType.getDouble(ctx, "value"), help)));
    }

    private static int set(CommandSourceStack source, String name, double raw, String help) {
        double applied;
        switch (name) {
            case "damping" -> applied = AeroConfig.arcadeAngularDamping = Math.max(0.0, raw);
            case "kp" -> applied = AeroConfig.stabilizerKp = Math.max(0.0, raw);
            case "maxaccel" -> applied = AeroConfig.stabilizerMaxAngularAccel = Math.max(0.1, raw);
            case "torque" -> applied = AeroConfig.controlSurfaceTorqueScale = Math.max(0.0, raw);
            case "authspeed" -> applied = AeroConfig.controlAuthoritySpeed = Math.max(1.0, raw);
            case "deadzone" -> applied = AeroConfig.stickDeadzone = Math.max(0.0, Math.min(0.5, raw));
            case "expo" -> applied = AeroConfig.stickExpo = Math.max(0.0, Math.min(1.0, raw));
            case "maxangular" -> {
                applied = XenoServerConfig.maxFlightAngularVelocity = Math.max(0.1, raw);
                XenoServerConfig.save();
                source.sendSuccess(() -> Component.literal(
                        "§eaero " + name + " §f= §a" + fmt(XenoServerConfig.maxFlightAngularVelocity)
                        + " §7(" + help + ")"), true);
                return 1;
            }
            // Floored at 1 rather than 0: the clamp treats a non-positive ceiling as "no clamp at
            // all", and losing the wall by typing 0 is not what anyone means by a maximum speed.
            case "maxspeed" -> {
                applied = XenoServerConfig.maxFlightSpeed = Math.max(1.0, raw);
                XenoServerConfig.save();
                source.sendSuccess(() -> Component.literal(
                        "§eaero " + name + " §f= §a" + fmt(XenoServerConfig.maxFlightSpeed)
                        + " §7(" + help + ")"), true);
                return 1;
            }
            default -> {
                source.sendFailure(Component.literal("Unknown field: " + name));
                return 0;
            }
        }
        AeroConfig.save();
        double shown = applied;
        source.sendSuccess(() -> Component.literal(
                "§eaero " + name + " §f= §a" + fmt(shown) + " §7(" + help + ")"), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§eAero flight tuning:"), false);
        line(source, "damping", AeroConfig.arcadeAngularDamping);
        line(source, "kp", AeroConfig.stabilizerKp);
        line(source, "maxaccel", AeroConfig.stabilizerMaxAngularAccel);
        line(source, "torque", AeroConfig.controlSurfaceTorqueScale);
        line(source, "authspeed", AeroConfig.controlAuthoritySpeed);
        line(source, "deadzone", AeroConfig.stickDeadzone);
        line(source, "expo", AeroConfig.stickExpo);
        line(source, "maxangular", XenoServerConfig.maxFlightAngularVelocity);
        line(source, "maxspeed", XenoServerConfig.maxFlightSpeed);
        return 1;
    }

    private static void line(CommandSourceStack source, String name, double value) {
        source.sendSuccess(() -> Component.literal("§7  " + name + " §f= §b" + fmt(value)), false);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }
}
