package net.bullettrain.xenopixelsmod.combat.controller;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.combo.ComboRouteMachine;
import net.bullettrain.xenopixelsmod.combat.Bt3CinematicRushSystem;
import net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents;
import net.bullettrain.xenopixelsmod.combat.Bt3CombatLimiter;
import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;
import net.bullettrain.xenopixelsmod.combat.HakaiChannelSystem;
import net.bullettrain.xenopixelsmod.combat.overcharge.ChargeOverchargeManager;
import net.bullettrain.xenopixelsmod.command.DmzHudCommands;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.network.ChaseFlightSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Owns the server's combat controller mode at runtime.
 *
 * <p>Two rules. First, the switch is atomic from the players' point of view: the config is
 * updated, every live combat window on every player is swept, and only then is the new mode
 * broadcast, so no client ever runs one controller against a server that is half-way into the
 * other. Second, the sweep happens no matter how the mode changed — {@code /xenocombat mode},
 * {@code /xenoset combatControllerMode}, or a {@code /xenoconfig reload} that read a different
 * value from disk — because a tick watcher compares the effective mode against the last one it
 * applied and performs the same sweep on any difference.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class CombatControllerService {

    /** The mode the server has last swept for; {@code null} until the first observation. */
    private static CombatControllerMode applied;

    private CombatControllerService() {}

    /** The effective mode right now, straight from config (never null). */
    public static CombatControllerMode current() {
        return XenoServerConfig.controllerMode();
    }

    /**
     * Switches the controller. Returns {@code true} when the mode actually changed (and state
     * was swept, config saved, and clients synced); {@code false} when it was already active.
     */
    public static boolean setMode(MinecraftServer server, CombatControllerMode requested) {
        CombatControllerMode next = requested == null ? CombatControllerMode.DEFAULT : requested;
        CombatControllerMode previous = current();
        if (previous == next && applied == next) return false;
        XenoServerConfig.combatControllerMode = next.id();
        sweepAll(server, previous, next);
        XenoServerConfig.save();
        DmzHudCommands.broadcast();
        return previous != next;
    }

    /**
     * Sweeps every player's live combat state and records {@code next} as applied. Used by the
     * explicit switch and by the tick watcher when the mode drifted through another path.
     */
    private static void sweepAll(MinecraftServer server, CombatControllerMode previous,
                                 CombatControllerMode next) {
        applied = next;
        if (server == null) return;
        int swept = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            clearLiveCombatState(player);
            swept++;
        }
        XenoPixelsMod.LOGGER.info("Combat controller mode {} -> {} (swept live combat state on {} player(s))",
                previous == null ? "unknown" : previous.id(), next.id(), swept);
    }

    /**
     * Cancels everything a controller may be mid-way through for one player: the combo string
     * and rush step, cinematic rush, chase flight, charge/overcharge sessions, Hakai channels,
     * guard hold, counter/Zanzoken windows, and the block/charge animations that go with them.
     * Cooldowns are kept on purpose.
     */
    public static void clearLiveCombatState(ServerPlayer player) {
        if (player == null) return;
        net.bullettrain.xenopixelsmod.combat.v3.V3CombatServer.clear(player);
        net.bullettrain.xenopixelsmod.combat.v2.V2CombatServer.clear(player);
        Bt3CinematicRushSystem.interrupt(player);
        ComboRouteMachine.cancel(player);
        ChaseFlightSystem.stopChase(player);
        HakaiChannelSystem.cancel(player, null);
        ChargeOverchargeManager.forget(player.getUUID());
        Bt3CombatLimiter.clearLiveState(player);
        boolean wasGuarding = Bt3CombatEvents.isGuarding(player);
        Bt3CombatEvents.clearLiveState(player);
        if (wasGuarding) DmzAnimHelper.broadcastBlockStop(player);
        DmzAnimHelper.broadcastChargeStop(player);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // First observation: treat the persisted mode as newly applied so the log records it and
        // any stale static state from a previous single-player session is swept.
        sweepAll(event.getServer(), null, current());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (reconcile(event.getServer())) {
            DmzHudCommands.broadcast();
        }
    }

    /** Apply any config drift before broadcasting or admitting another controller intent. */
    public static boolean reconcile(MinecraftServer server) {
        CombatControllerMode now = current();
        if (!CombatControllerMode.requiresCleanup(applied, now)) return false;
        sweepAll(server, applied, now);
        return true;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        applied = null;
    }
}
