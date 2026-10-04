package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.aero.FlightControllerDebugRotation;
import net.bullettrain.xenopixelsmod.client.guidance.GuidanceV2Client;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Live computer-rig mount tuning — {@code /xenocomp} — the v2 counterpart of {@code /xenowing}.
 * Nudges the GeckoLib console after facing rotation. Client-only, not persisted.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class FlightControllerDebugCommands {

    private FlightControllerDebugCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xenocomp")
                .then(Commands.literal("orient")
                        .then(axisNode("x", 0))
                        .then(axisNode("y", 1))
                        .then(axisNode("z", 2)))
                .then(Commands.literal("reset").executes(ctx -> {
                    FlightControllerDebugRotation.reset();
                    feedback("§bComputer orient reset to 0/0/0");
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    feedback("§bComputer extra §f" + triple()
                            + (GuidanceV2Client.isV2()
                            ? " §7(applied — guidance v2)"
                            : " §7(stored only — switch to v2 with /xenoguidance system v2)"));
                    return 1;
                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> axisNode(String name, int idx) {
        return Commands.literal(name)
                .then(Commands.argument("degrees", IntegerArgumentType.integer(-180, 180))
                        .executes(ctx -> setAxis(idx, IntegerArgumentType.getInteger(ctx, "degrees"))));
    }

    private static int setAxis(int idx, int degrees) {
        switch (idx) {
            case 0 -> FlightControllerDebugRotation.extraX = degrees;
            case 1 -> FlightControllerDebugRotation.extraY = degrees;
            default -> FlightControllerDebugRotation.extraZ = degrees;
        }
        feedback("§bComputer orient: §f" + triple());
        return 1;
    }

    private static String triple() {
        return "x" + FlightControllerDebugRotation.extraX
                + ",y" + FlightControllerDebugRotation.extraY
                + ",z" + FlightControllerDebugRotation.extraZ;
    }

    private static void feedback(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
