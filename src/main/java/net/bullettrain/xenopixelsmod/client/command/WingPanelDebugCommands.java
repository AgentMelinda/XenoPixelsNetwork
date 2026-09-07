package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.custom.WingPanelDebugRotation;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Live wing-panel deflection tuning — {@code /xenowing} — so the flap's swing axis/sign can be
 * tried in-game without a recompile+relaunch each time. See
 * {@link WingPanelDebugRotation} for what these actually change.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class WingPanelDebugCommands {

    private WingPanelDebugCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xenowing")
                .then(Commands.literal("deflectaxis")
                        .then(Commands.literal("x").executes(ctx -> setAxis(Direction.Axis.X)))
                        .then(Commands.literal("y").executes(ctx -> setAxis(Direction.Axis.Y)))
                        .then(Commands.literal("z").executes(ctx -> setAxis(Direction.Axis.Z))))
                .then(Commands.literal("deflectsign")
                        .then(Commands.literal("+").executes(ctx -> setSign(false)))
                        .then(Commands.literal("-").executes(ctx -> setSign(true))))
                .then(Commands.literal("statictwist")
                        .then(Commands.literal("x")
                                .then(Commands.literal("+90").executes(ctx -> nudgeTwistX(90)))
                                .then(Commands.literal("-90").executes(ctx -> nudgeTwistX(-90))))
                        .then(Commands.literal("y")
                                .then(Commands.literal("+90").executes(ctx -> nudgeTwistY(90)))
                                .then(Commands.literal("-90").executes(ctx -> nudgeTwistY(-90))))
                        .then(Commands.literal("z")
                                .then(Commands.literal("+90").executes(ctx -> nudgeTwistZ(90)))
                                .then(Commands.literal("-90").executes(ctx -> nudgeTwistZ(-90))))
                        .then(Commands.literal("reset").executes(ctx -> {
                            WingPanelDebugRotation.staticTwistXDegrees = 0;
                            WingPanelDebugRotation.staticTwistYDegrees = 0;
                            WingPanelDebugRotation.staticTwistZDegrees = 0;
                            feedback("§bStatic twist reset to 0/0/0");
                            return 1;
                        })))
                .then(Commands.literal("testdeflect")
                        .then(Commands.literal("off").executes(ctx -> setTestDeflect(Double.NaN)))
                        .then(Commands.argument("degrees", DoubleArgumentType.doubleArg(-90.0, 90.0))
                                .executes(ctx -> setTestDeflect(DoubleArgumentType.getDouble(ctx, "degrees")))))
                .then(Commands.literal("orient")
                        .then(orientNode("normal"))
                        .then(orientNode("horizontal"))
                        .then(orientNode("vertical")))
                .then(Commands.literal("status").executes(ctx -> {
                    double test = WingPanelDebugRotation.testDeflectDeg;
                    feedback("§bdeflectaxis=§f" + WingPanelDebugRotation.deflectAxis.getSerializedName()
                            + " §bdeflectsign=§f" + (WingPanelDebugRotation.deflectNegated ? "-" : "+")
                            + " §bstatictwist=§f" + triple(WingPanelDebugRotation.staticTwistXDegrees,
                                    WingPanelDebugRotation.staticTwistYDegrees, WingPanelDebugRotation.staticTwistZDegrees)
                            + " §btestdeflect=§f" + (Double.isNaN(test) ? "off" : test + "°"));
                    feedback("§borient §fnormal=" + triple(WingPanelDebugRotation.normalOrient)
                            + " §fhorizontal=" + triple(WingPanelDebugRotation.horizontalOrient)
                            + " §fvertical=" + triple(WingPanelDebugRotation.verticalOrient));
                    return 1;
                })));
    }

    private static int setAxis(Direction.Axis axis) {
        WingPanelDebugRotation.deflectAxis = axis;
        feedback("§bWing flap deflection axis: §f" + axis.getSerializedName());
        return 1;
    }

    private static int setSign(boolean negated) {
        WingPanelDebugRotation.deflectNegated = negated;
        feedback("§bWing flap deflection sign: §f" + (negated ? "-" : "+"));
        return 1;
    }

    private static int setTestDeflect(double degrees) {
        WingPanelDebugRotation.testDeflectDeg = degrees;
        feedback(Double.isNaN(degrees)
                ? "§bWing flap test angle: §foff §7(live animation)"
                : "§bWing flap test angle: §f" + degrees + "° §7(held on every panel)");
        return 1;
    }

    private static int nudgeTwistX(int delta) {
        int next = Math.floorMod(WingPanelDebugRotation.staticTwistXDegrees + delta, 360);
        WingPanelDebugRotation.staticTwistXDegrees = next;
        feedback("§bStatic twist X: §f" + next + "°");
        return 1;
    }

    private static int nudgeTwistY(int delta) {
        int next = Math.floorMod(WingPanelDebugRotation.staticTwistYDegrees + delta, 360);
        WingPanelDebugRotation.staticTwistYDegrees = next;
        feedback("§bStatic twist Y: §f" + next + "°");
        return 1;
    }

    private static int nudgeTwistZ(int delta) {
        int next = Math.floorMod(WingPanelDebugRotation.staticTwistZDegrees + delta, 360);
        WingPanelDebugRotation.staticTwistZDegrees = next;
        feedback("§bStatic twist Z: §f" + next + "°");
        return 1;
    }

    /** {@code orient <type> <x|y|z> <degrees>} — sets one component of a block type's mount triple. */
    private static LiteralArgumentBuilder<CommandSourceStack> orientNode(String type) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(type);
        for (int i = 0; i < 3; i++) {
            int idx = i;
            node.then(Commands.literal(String.valueOf("xyz".charAt(i)))
                    .then(Commands.argument("degrees", IntegerArgumentType.integer(-180, 180))
                            .executes(ctx -> setOrient(type, idx, IntegerArgumentType.getInteger(ctx, "degrees")))));
        }
        return node;
    }

    private static int setOrient(String type, int idx, int degrees) {
        int[] target = switch (type) {
            case "horizontal" -> WingPanelDebugRotation.horizontalOrient;
            case "vertical" -> WingPanelDebugRotation.verticalOrient;
            default -> WingPanelDebugRotation.normalOrient;
        };
        target[idx] = degrees;
        feedback("§bOrient " + type + ": §f" + triple(target));
        return 1;
    }

    private static String triple(int[] xyz) {
        return triple(xyz[0], xyz[1], xyz[2]);
    }

    private static String triple(int x, int y, int z) {
        return "x" + x + ",y" + y + ",z" + z;
    }

    private static void feedback(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
