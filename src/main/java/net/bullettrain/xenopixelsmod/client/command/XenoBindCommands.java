package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.combat.Bt3DirectBind;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * {@code /xenobind} — switches a move's own key or gamepad chord back on after it has moved to
 * Controlify's radial menu or a DragonMineZ technique slot.
 *
 * <p>Client-side and local: these are input preferences for the player sitting at this game, so
 * there is no permission node and nothing is sent to the server. Changes take effect on the next
 * press and are saved immediately, so no restart is needed to compare the two routes.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoBindCommands {

    private XenoBindCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("xenobind")
                .executes(ctx -> list())
                .then(Commands.literal("list").executes(ctx -> list()))
                .then(Commands.literal("all")
                        .then(Commands.argument("on", BoolArgumentType.bool())
                                .executes(ctx -> setAll(BoolArgumentType.getBool(ctx, "on")))));

        for (Bt3DirectBind route : Bt3DirectBind.values()) {
            root = root.then(Commands.literal(route.id())
                    .executes(ctx -> set(route, !route.enabled()))
                    .then(Commands.argument("on", BoolArgumentType.bool())
                            .executes(ctx -> set(route, BoolArgumentType.getBool(ctx, "on")))));
        }
        dispatcher.register(root);
    }

    private static int list() {
        feedback("§bDirect key/chord routes §7(/xenobind <name> [true|false])");
        for (Bt3DirectBind route : Bt3DirectBind.values()) {
            feedback("  §f" + route.id() + " §7- " + route.label() + " "
                    + (route.enabled() ? "§aon" : "§7off"));
        }
        return Bt3DirectBind.values().length;
    }

    private static int set(Bt3DirectBind route, boolean on) {
        route.set(on);
        feedback("§f" + route.label() + " direct input " + (on ? "§aon" : "§7off"));
        return 1;
    }

    private static int setAll(boolean on) {
        for (Bt3DirectBind route : Bt3DirectBind.values()) {
            route.set(on);
        }
        feedback("§fAll direct inputs " + (on ? "§aon" : "§7off"));
        return Bt3DirectBind.values().length;
    }

    private static void feedback(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
