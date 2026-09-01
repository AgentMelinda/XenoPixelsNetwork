package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.render.WingPanelDebugRotation;
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
                        .then(Commands.literal("reset").executes(ctx -> {
                            WingPanelDebugRotation.staticTwistXDegrees = 0;
                            WingPanelDebugRotation.staticTwistYDegrees = 0;
                            feedback("§bStatic twist reset to 0/0");
                            return 1;
                        })))
                .then(Commands.literal("status").executes(ctx -> {
                    feedback("§bdeflectaxis=§f" + WingPanelDebugRotation.deflectAxis.getSerializedName()
                            + " §bdeflectsign=§f" + (WingPanelDebugRotation.deflectNegated ? "-" : "+")
                            + " §bstatictwist=§fx" + WingPanelDebugRotation.staticTwistXDegrees
                            + ",y" + WingPanelDebugRotation.staticTwistYDegrees);
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

    private static void feedback(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
