package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.combat.Bt3DirectBind;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.pad2.PadRadialSlots;
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

        // The radial's extra slots. Controlify's own configuration screen still owns the first
        // eight; these are the ones past them, which that screen cannot reach.
        root = root.then(Commands.literal("radial")
                .executes(ctx -> radialList())
                .then(Commands.literal("list").executes(ctx -> radialList()))
                .then(Commands.literal("reset").executes(ctx -> radialReset()))
                .then(Commands.literal("add")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> radialAdd(
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> radialRemove(
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("move")
                        .then(Commands.argument("from", IntegerArgumentType.integer(1))
                                .then(Commands.argument("to", IntegerArgumentType.integer(1))
                                        .executes(ctx -> radialMove(
                                                IntegerArgumentType.getInteger(ctx, "from"),
                                                IntegerArgumentType.getInteger(ctx, "to")))))));

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

    // ---- the radial's extra slots ----------------------------------------------------------

    /**
     * The extras as they stand.
     *
     * <p>Says whether the list is the default or the player's own, because "nothing configured"
     * and "configured to exactly the defaults" behave identically but mean different things the
     * next time the defaults change.
     */
    private static int radialList() {
        java.util.List<String> configured = XenoClientConfig.padRadialExtras;
        boolean custom = configured != null && !configured.isEmpty();
        java.util.List<String> shown = custom ? configured : PadRadialSlots.DEFAULT_EXTRAS;

        feedback("§bRadial extras §7(slots " + (PadRadialSlots.CONTROLIFY_SLOTS + 1) + "+, "
                + shown.size() + "/" + (PadRadialSlots.MAX_SLOTS - PadRadialSlots.CONTROLIFY_SLOTS)
                + (custom ? ", custom)" : ", default)"));
        feedback("§7Controlify's own screen still owns the first "
                + PadRadialSlots.CONTROLIFY_SLOTS + ".");
        for (int i = 0; i < shown.size(); i++) {
            feedback("  §7" + (i + 1) + ". §f" + shown.get(i));
        }
        return shown.size();
    }

    private static int radialAdd(String id) {
        java.util.List<String> next = PadRadialSlots.add(currentExtras(), id);
        if (next.size() == currentExtras().size()) {
            feedback("§7Not added: already listed, or the list is full at "
                    + PadRadialSlots.MAX_SLOTS + ".");
            return 0;
        }
        return saveExtras(next, "§aAdded §f" + PadRadialSlots.clean(id));
    }

    private static int radialRemove(String id) {
        java.util.List<String> before = currentExtras();
        java.util.List<String> next = PadRadialSlots.remove(before, id);
        if (next.size() == before.size()) {
            feedback("§7Not listed: " + PadRadialSlots.clean(id));
            return 0;
        }
        return saveExtras(next, "§fRemoved §7" + PadRadialSlots.clean(id));
    }

    /** One-based in the command, because that is how the list above prints. */
    private static int radialMove(int from, int to) {
        java.util.List<String> next = PadRadialSlots.move(currentExtras(), from - 1, to - 1);
        return saveExtras(next, "§fMoved slot " + from + " to " + to);
    }

    private static int radialReset() {
        XenoClientConfig.padRadialExtras = java.util.List.of();
        XenoClientConfig.save();
        feedback("§fRadial extras reset to the defaults.");
        return radialList();
    }

    /**
     * The list being edited.
     *
     * <p>An empty config means "never configured", so the first edit starts from the defaults
     * rather than from nothing - otherwise adding one entry would silently delete the other
     * eleven a player was already using.
     */
    private static java.util.List<String> currentExtras() {
        java.util.List<String> configured = XenoClientConfig.padRadialExtras;
        return configured == null || configured.isEmpty()
                ? PadRadialSlots.DEFAULT_EXTRAS : configured;
    }

    private static int saveExtras(java.util.List<String> next, String message) {
        XenoClientConfig.padRadialExtras = next;
        XenoClientConfig.save();
        feedback(message + " §7(" + next.size() + " extras; reopen the radial to see it)");
        return next.size();
    }

    private static void feedback(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
