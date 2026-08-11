package net.bullettrain.xenopixelsmod.aero;

import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Maps a clicked panel control to a controller intent.
 *
 * <p>Every physical control routes through {@link AeroActionDispatcher}, exactly as the GUI
 * does. That is deliberate: the panel and the screen share one authoritative path, so a
 * control cannot do something the GUI would have refused, and validation lives in one place.
 *
 * <p>Action ids are agreed with {@code tools/gen_flight_controller_model.py}, which writes
 * them into the hitbox data alongside the regions.
 */
public final class AeroPanelActions {

    /** Throttle step per lever pull; wraps back to zero past full. */
    private static final double THROTTLE_STEP = 0.25;

    private AeroPanelActions() {
    }

    public static void activate(ShipVlsGuidanceBlockEntity host, AeroHitRegions.Region region,
                                ServerPlayer player) {
        if (host == null || region == null || player == null) return;
        AeroBus bus = host.aeroBus();
        AeroAction action = switch (region.action()) {
            case "cycle_mode" -> new AeroAction.SetMode(
                    bus.mode() == ControllerMode.FLIGHT ? ControllerMode.MISSILE : ControllerMode.FLIGHT);
            case "toggle_flight" -> new AeroAction.ToggleSubsystem(
                    AeroSubsystem.FLIGHT, !bus.isEnabled(AeroSubsystem.FLIGHT));
            case "toggle_map" -> new AeroAction.ToggleSubsystem(
                    AeroSubsystem.TERRAIN_MAP, !bus.isEnabled(AeroSubsystem.TERRAIN_MAP));
            case "step_throttle" -> new AeroAction.SetThrottle(nextThrottle(bus.throttle()));
            case "reset_attitude" -> new AeroAction.SetAttitude(0.0, 0.0, 0.0);
            default -> null;
        };

        if (action == null) {
            player.displayClientMessage(Component.literal(
                    "§cUnknown panel control: " + region.action()), true);
            return;
        }

        AeroActionDispatcher.Result result = AeroActionDispatcher.dispatch(host, action, player);
        player.displayClientMessage(Component.literal(
                (result.accepted() ? "§b" : "§c") + region.name() + " §7— " + result.message()), true);
    }

    private static double nextThrottle(double current) {
        double next = current + THROTTLE_STEP;
        return next > 1.0 + 1.0e-6 ? 0.0 : Math.min(1.0, next);
    }
}
