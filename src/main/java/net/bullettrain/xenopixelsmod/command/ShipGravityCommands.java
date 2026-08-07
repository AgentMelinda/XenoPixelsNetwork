package net.bullettrain.xenopixelsmod.command;

import net.neoforged.fml.common.EventBusSubscriber;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.vs.ShipGravityControl;
import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ShipGravityCommands {
    private ShipGravityCommands() { }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(command("shipgravity"));
        dispatcher.register(command("xenoshipgravity"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("get")
                        .executes(context -> get(context.getSource(), -1L))
                        .then(Commands.argument("shipId", LongArgumentType.longArg(0))
                                .executes(context -> get(context.getSource(),
                                        LongArgumentType.getLong(context, "shipId")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("gravity", DoubleArgumentType.doubleArg(-100.0, 100.0))
                                .executes(context -> set(context.getSource(), -1L,
                                        DoubleArgumentType.getDouble(context, "gravity")))
                                .then(Commands.argument("shipId", LongArgumentType.longArg(0))
                                        .executes(context -> set(context.getSource(),
                                                LongArgumentType.getLong(context, "shipId"),
                                                DoubleArgumentType.getDouble(context, "gravity"))))))
                .then(Commands.literal("reset")
                        .executes(context -> set(context.getSource(), -1L, ShipGravityControl.NORMAL_GRAVITY))
                        .then(Commands.argument("shipId", LongArgumentType.longArg(0))
                                .executes(context -> set(context.getSource(),
                                        LongArgumentType.getLong(context, "shipId"),
                                        ShipGravityControl.NORMAL_GRAVITY))))
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /shipgravity <get|set <m/s²>|reset> [shipId] (positive=down, negative=up)"), false);
                    return 1;
                });
    }

    private static int get(CommandSourceStack source, long shipId) {
        ServerSubLevel ship = resolve(source, shipId);
        if (ship == null) return noShip(source);
        ShipGravityControl control = ShipGravityControl.get(ship);
        double gravity = control == null ? ShipGravityControl.NORMAL_GRAVITY : control.getGravitySi();
        source.sendSuccess(() -> Component.literal(String.format(
                "Sable sub-level #%d gravity: %.4f m/s² (%s)", VsShipHelper.getShipId(ship), gravity,
                gravity > 0 ? "down" : gravity < 0 ? "up" : "zero-g")), false);
        return 1;
    }

    private static int set(CommandSourceStack source, long shipId, double gravity) {
        ServerSubLevel ship = resolve(source, shipId);
        if (ship == null) return noShip(source);
        ShipGravityControl control = ShipGravityControl.getOrCreate(ship);
        control.setGravitySi(gravity);
        source.sendSuccess(() -> Component.literal(String.format(
                "Sable sub-level #%d gravity set to %.4f m/s² (%s)", VsShipHelper.getShipId(ship), control.getGravitySi(),
                control.getGravitySi() > 0 ? "down" : control.getGravitySi() < 0 ? "up" : "zero-g")), true);
        return 1;
    }

    private static ServerSubLevel resolve(CommandSourceStack source, long shipId) {
        ServerLevel level = source.getLevel();
        if (shipId >= 0) return VsShipHelper.getLoadedShipById(level, shipId);
        try {
            ServerPlayer player = source.getPlayerOrException();
            HitResult hit = player.pick(128.0, 0.0f, false);
            if (hit.getType() != HitResult.Type.MISS) {
                ServerSubLevel lookedAt = VsShipHelper.getLoadedShipAt(level, BlockPos.containing(hit.getLocation()));
                if (lookedAt != null) return lookedAt;
            }
            ServerSubLevel under = VsShipHelper.getLoadedShipAt(level, player.blockPosition().below());
            if (under != null) return under;
            return VsShipHelper.getClosestLoadedShip(level, new Vec3(player.getX(), player.getY(), player.getZ()), 128.0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int noShip(CommandSourceStack source) {
        source.sendFailure(Component.literal("No loaded Sable ship selected. Stand on/look at one, or provide its ship ID."));
        return 0;
    }
}
