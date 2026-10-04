package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.aero.GuidanceConfig;
import net.bullettrain.xenopixelsmod.aero.GuidanceVersion;
import net.bullettrain.xenopixelsmod.block.custom.WingPanelForkBlock;
import net.bullettrain.xenopixelsmod.network.GuidanceV2Network;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class GuidanceCommands {
    private GuidanceCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(command());
    }

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("xenoguidance")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("system")
                        .executes(ctx -> status(ctx.getSource()))
                        .then(Commands.literal("v1").executes(ctx -> set(ctx.getSource(), GuidanceVersion.V1)))
                        .then(Commands.literal("v2").executes(ctx -> set(ctx.getSource(), GuidanceVersion.V2)))
                        .then(Commands.literal("v3").executes(ctx -> set(ctx.getSource(), GuidanceVersion.V3))))
                // The guidance computer you are looking at: the same settings as its screen and CC.
                .then(Commands.literal("computer").executes(ctx -> computer(ctx.getSource())))
                .then(Commands.literal("speed")
                        .then(Commands.argument("level",
                                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 20))
                                .executes(ctx -> computerSet(ctx.getSource(), "speed",
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "level")))))
                .then(Commands.literal("gravity")
                        .then(Commands.argument("metersPerSecondSquared",
                                        com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.01, 100.0))
                                .executes(ctx -> computerSet(ctx.getSource(), "gravity",
                                        com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx,
                                                "metersPerSecondSquared")))))
                .then(Commands.literal("drag")
                        .then(Commands.argument("coefficient",
                                        com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 0.01))
                                .executes(ctx -> computerSet(ctx.getSource(), "drag",
                                        com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx,
                                                "coefficient")))))
                .then(Commands.literal("flap")
                        .then(Commands.literal("facing")
                                .then(facingLiteral("north", Direction.NORTH))
                                .then(facingLiteral("south", Direction.SOUTH))
                                .then(facingLiteral("east", Direction.EAST))
                                .then(facingLiteral("west", Direction.WEST))
                                .then(facingLiteral("up", Direction.UP))
                                .then(facingLiteral("down", Direction.DOWN))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(facingLiteral("north", Direction.NORTH))
                                        .then(facingLiteral("south", Direction.SOUTH))
                                        .then(facingLiteral("east", Direction.EAST))
                                        .then(facingLiteral("west", Direction.WEST))
                                        .then(facingLiteral("up", Direction.UP))
                                        .then(facingLiteral("down", Direction.DOWN)))))
                .executes(ctx -> status(ctx.getSource()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> facingLiteral(String name, Direction facing) {
        return Commands.literal(name).executes(ctx -> {
            BlockPos pos;
            try {
                pos = BlockPosArgument.getBlockPos(ctx, "pos");
            } catch (IllegalArgumentException ignored) {
                pos = lookAt(ctx.getSource());
            }
            return setFacing(ctx.getSource(), pos, facing);
        });
    }

    private static net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity lookedAtComputer(
            CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.world.entity.player.Player player)) return null;
        var hit = player.pick(8.0, 0.0f, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)) return null;
        var be = source.getLevel().getBlockEntity(blockHit.getBlockPos());
        return be instanceof net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity computer
                ? computer : null;
    }

    private static int computer(CommandSourceStack source) {
        var computer = lookedAtComputer(source);
        if (computer == null) {
            source.sendFailure(Component.literal("Look at a guidance computer (within 8 blocks)."));
            return 0;
        }
        double g = net.bullettrain.xenopixelsmod.missile.BallisticCalculator.toTickGravity(computer.getGravitySi());
        int speed = computer.getSpeedLevel();
        double engine = net.bullettrain.xenopixelsmod.missile.v3.GuidanceV3.engineAccel(
                net.bullettrain.xenopixelsmod.missile.MissileGuidance.burnPerTick(
                        net.bullettrain.xenopixelsmod.missile.MissileSpeed.accelFor(Math.max(1, speed))), 0, g) * 400.0;
        source.sendSuccess(() -> Component.literal(String.format(
                "§eGuidance computer §7speed §f%d §7gravity §f%.2f m/s² §7drag §f%.5f §7| V3 engine §f%.1f m/s² §7(%.1f g)",
                speed, computer.getGravitySi(), computer.getDragCoefficient(), engine,
                engine / Math.max(0.01, computer.getGravitySi()))), false);
        return 1;
    }

    private static int computerSet(CommandSourceStack source, String what, double value) {
        var computer = lookedAtComputer(source);
        if (computer == null) {
            source.sendFailure(Component.literal("Look at a guidance computer (within 8 blocks)."));
            return 0;
        }
        switch (what) {
            case "speed" -> computer.setSpeedLevel((int) value);
            case "gravity" -> computer.setFlightPhysics(value, computer.getDragCoefficient());
            case "drag" -> computer.setFlightPhysics(computer.getGravitySi(), value);
            default -> {
                return 0;
            }
        }
        return computer(source);
    }

    private static int set(CommandSourceStack source, GuidanceVersion requested) {
        GuidanceVersion applied = GuidanceConfig.set(requested);
        if (source.getServer() != null) {
            GuidanceV2Network.broadcastVersion(source.getServer().getPlayerList().getPlayers());
        }
        source.sendSuccess(() -> Component.literal(
                "§eGuidance system §f= §a" + applied.name().toLowerCase()
                        + (applied == GuidanceVersion.V3
                                ? " §7(V1 flight, HUD and planner; V3 missile guidance)" : "")),
                true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "§eGuidance system §f= §b" + GuidanceConfig.version().name().toLowerCase()
                        + " §7(stock and fork hosts both use this stack)"), false);
        source.sendSuccess(() -> Component.literal(
                "§7Flap facing: §f/xenoguidance flap facing <dir> §7| computer: §f/xenocomp orient <x|y|z> <deg> §7| v2 flaps: §f/xenowing orient <normal|horizontal|vertical> <x|y|z> <deg>"), false);
        return 1;
    }

    private static int setFacing(CommandSourceStack source, BlockPos pos, Direction facing) {
        if (!(source.getLevel() instanceof ServerLevel level) || pos == null) {
            source.sendFailure(Component.literal("Look at a (fork) flap, or pass a block position"));
            return 0;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(WingPanelForkBlock.FACING)) {
            source.sendFailure(Component.literal("That block is not a (fork) flap / panel"));
            return 0;
        }
        level.setBlock(pos, WingPanelForkBlock.withFacing(state, facing), 3);
        source.sendSuccess(() -> Component.literal(
                "§eFlap facing §f= §a" + facing.getSerializedName() + " §7at " + pos.toShortString()), true);
        return 1;
    }

    private static BlockPos lookAt(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return null;
        }
        HitResult hit = player.pick(8.0, 0.0f, false);
        if (hit instanceof BlockHitResult block) {
            return block.getBlockPos();
        }
        return null;
    }
}
