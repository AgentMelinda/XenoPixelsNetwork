package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.controller.CombatControllerMode;
import net.bullettrain.xenopixelsmod.combat.controller.CombatControllerService;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;

/**
 * {@code /xenocombat mode legacy|bt3_manual|v2}, {@code /xenocombat status}, {@code /xenocombat reload}.
 *
 * <p>The operator-facing switch for the combat controller. Unlike {@code /xenoset
 * combatControllerMode}, an unknown mode here is an error rather than a silent fall-back to
 * legacy, and the switch runs through {@link CombatControllerService} so live combat state is
 * swept before clients learn about the new mode.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoCombatCommands {

    private XenoCombatCommands() {}

    private static final String[] MODE_IDS = Arrays.stream(CombatControllerMode.values())
            .map(CombatControllerMode::id).toArray(String[]::new);

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("xenocombat")
                .then(Commands.literal("status")
                        .requires(XenoPermissions.require(XenoPermissions.XENOCOMBAT_STATUS))
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("mode")
                        .requires(XenoPermissions.require(XenoPermissions.XENOCOMBAT_MODE))
                        .executes(ctx -> status(ctx.getSource()))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(MODE_IDS, b))
                                .executes(ctx -> setMode(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "mode")))))
                .then(Commands.literal("reload")
                        .requires(XenoPermissions.require(XenoPermissions.XENOCOMBAT_RELOAD))
                        .executes(ctx -> reload(ctx.getSource())))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenocombat status | mode <" + String.join("|", MODE_IDS)
                                    + "> | reload"), false);
                    return 1;
                }));
    }

    static String statusLine() {
        CombatControllerMode mode = CombatControllerService.current();
        StringBuilder sb = new StringBuilder("Combat controller: ").append(mode.id());
        if (mode == CombatControllerMode.DEFAULT) sb.append(" (default)");
        sb.append(" | bt3Combat=").append(XenoServerConfig.bt3CombatEnabled ? "on" : "off");
        sb.append(" | bt3Combo=").append(XenoServerConfig.bt3ComboEnabled ? "on" : "off");
        sb.append(" | cinematicRush=").append(XenoServerConfig.bt3CinematicRushEnabled ? "on" : "off");
        sb.append(" | grab=").append(grabStatus());
        if (mode == CombatControllerMode.BT3_MANUAL && !XenoServerConfig.bt3CombatEnabled) {
            sb.append(" | NOTE: bt3CombatEnabled is off, manual input is refused");
        }
        return sb.toString();
    }

    /** Which controllers have the grab: it is the one XenoCombat v2 move the others share. */
    private static String grabStatus() {
        net.bullettrain.xenopixelsmod.combat.v2.V2Config.Values cfg =
                net.bullettrain.xenopixelsmod.combat.v2.V2Config.get();
        if (!cfg.grabEnabled) return "off";
        return cfg.grabOutsideV2 ? "every controller" : "v2 only";
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(statusLine()), false);
        return 1;
    }

    private static int setMode(CommandSourceStack source, String raw) {
        CombatControllerMode requested = CombatControllerMode.parseStrict(raw);
        if (requested == null) {
            source.sendFailure(Component.literal("Unknown combat controller mode '" + raw
                    + "'. Use " + String.join(" or ", MODE_IDS)));
            return 0;
        }
        boolean changed = CombatControllerService.setMode(source.getServer(), requested);
        if (!changed) {
            source.sendSuccess(() -> Component.literal(
                    "Combat controller already " + requested.id()), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(
                "Combat controller set to " + requested.id()
                        + " (live combat state swept, saved, synced to all clients)"), true);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        CombatControllerMode before = CombatControllerService.current();
        XenoServerConfig.load();
        net.bullettrain.xenopixelsmod.combat.v2.V2Config.load();
        net.bullettrain.xenopixelsmod.combat.v3.V3Config.load();
        net.bullettrain.xenopixelsmod.combat.v2.combo.ComboGraphs.reload();
        CombatControllerService.reconcile(source.getServer());
        net.bullettrain.xenopixelsmod.combat.v3.V3CombatServer.clearAll(source.getServer());
        CombatControllerMode after = CombatControllerService.current();
        // Reconciliation and V3 session invalidation above precede every new snapshot.
        DmzHudCommands.broadcast();
        if (before == after) {
            // The reload may have turned the grab on or off; that reaches clients as fighter state.
            net.bullettrain.xenopixelsmod.combat.v2.V2CombatServer.resyncAll(source.getServer());
        }
        source.sendSuccess(() -> Component.literal("Reloaded combat config. " + statusLine()), true);
        return 1;
    }
}
