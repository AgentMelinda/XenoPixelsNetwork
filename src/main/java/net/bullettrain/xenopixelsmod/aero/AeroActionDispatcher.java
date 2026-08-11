package net.bullettrain.xenopixelsmod.aero;

import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The one place controller intents are validated and applied.
 *
 * <p>Nothing else may write to {@link AeroBus}. Actions arrive from the planner GUI, the
 * physical panel on the block model, and peripherals; all three are untrusted in the sense
 * that matters here — values are clamped and gated on the server, and the sender's opinion
 * about whether an action is legal is ignored.
 *
 * <p>Ordering note: {@link AeroAction.EmergencyStop} is honored in every state, including
 * with no power and in missile mode. A controller that cannot be told to stop is worse than
 * one that cannot be told to go.
 */
public final class AeroActionDispatcher {

    /**
     * @param accepted whether the action changed state
     * @param message  short operator-facing explanation; shown in the GUI status line
     */
    public record Result(boolean accepted, String message) {
        static Result ok(String message) {
            return new Result(true, message);
        }

        static Result reject(String message) {
            return new Result(false, message);
        }
    }

    private AeroActionDispatcher() {
    }

    public static Result dispatch(@Nullable ShipVlsGuidanceBlockEntity host,
                                  @Nullable AeroAction action,
                                  @Nullable ServerPlayer actor) {
        if (host == null || action == null) return Result.reject("no controller");
        if (host.getLevel() == null || host.getLevel().isClientSide) {
            return Result.reject("client cannot command flight");
        }
        AeroBus bus = host.aeroBus();

        // Always available, in any mode and at any power level.
        if (action instanceof AeroAction.EmergencyStop) {
            return emergencyStop(host, bus);
        }

        if (action instanceof AeroAction.SetMode setMode) {
            return setMode(host, bus, setMode.mode());
        }

        if (action instanceof AeroAction.Link link) {
            return link(host, bus, link.op());
        }

        // Remaining actions are flight commands.
        if (bus.mode() != ControllerMode.FLIGHT) {
            return Result.reject("controller is in missile mode");
        }
        if (bus.powerTier() == AeroBus.PowerTier.OFFLINE) {
            return Result.reject("no power — emergency stop only");
        }

        if (action instanceof AeroAction.ToggleSubsystem toggle) {
            return toggleSubsystem(host, bus, toggle);
        }
        if (action instanceof AeroAction.SetThrottle throttle) {
            return setThrottle(host, bus, throttle.throttle());
        }
        if (action instanceof AeroAction.SetAttitude attitude) {
            return setAttitude(host, bus, attitude);
        }
        if (action instanceof AeroAction.SetAutopilot autopilot) {
            return setAutopilot(host, bus, autopilot.mode());
        }
        return Result.reject("unhandled action");
    }

    private static Result emergencyStop(ShipVlsGuidanceBlockEntity host, AeroBus bus) {
        bus.resetFlightState();
        bus.setStatus("emergency stop");
        AeroLinkManager.releaseAll(host.getLevel(), host.getPairedThrusters());
        host.setChanged();
        host.syncAero();
        return Result.ok("emergency stop — thrust released");
    }

    private static Result setMode(ShipVlsGuidanceBlockEntity host, AeroBus bus, ControllerMode mode) {
        if (mode == null) return Result.reject("unknown mode");
        if (bus.mode() == mode) return Result.reject("already in " + mode.name().toLowerCase() + " mode");
        if (host.isCommandingFlight()) {
            return Result.reject("cannot change mode during an active launch");
        }
        // Leaving flight mode must not leave engines running.
        AeroLinkManager.releaseAll(host.getLevel(), host.getPairedThrusters());
        bus.setMode(mode);
        bus.setStatus(mode == ControllerMode.FLIGHT ? "flight mode armed" : "missile mode");
        host.setChanged();
        host.syncAero();
        return Result.ok("mode set to " + mode.name().toLowerCase());
    }

    private static Result link(ShipVlsGuidanceBlockEntity host, AeroBus bus, AeroAction.Link.Op op) {
        if (op == null) return Result.reject("unknown link operation");
        switch (op) {
            case PAIR_NEARBY -> {
                int added = host.pairNearbyThrusters();
                host.refreshAeroLinks();
                return Result.ok("paired " + added + " new engine(s), "
                        + bus.healthyLinkCount() + "/" + bus.linkCount() + " healthy");
            }
            case CLEAR_ALL -> {
                int removed = host.clearPairedThrusters();
                host.refreshAeroLinks();
                return Result.ok("cleared " + removed + " link(s)");
            }
            case REFRESH -> {
                host.refreshAeroLinks();
                return Result.ok(bus.healthyLinkCount() + "/" + bus.linkCount() + " links healthy");
            }
            default -> {
                return Result.reject("unknown link operation");
            }
        }
    }

    private static Result toggleSubsystem(ShipVlsGuidanceBlockEntity host, AeroBus bus,
                                          AeroAction.ToggleSubsystem toggle) {
        if (toggle.subsystem() == null) return Result.reject("unknown subsystem");
        if (toggle.enabled() && bus.powerTier() == AeroBus.PowerTier.CRITICAL) {
            return Result.reject("power critical — cannot enable " + toggle.subsystem().name().toLowerCase());
        }
        bus.setEnabled(toggle.subsystem(), toggle.enabled());
        if (toggle.subsystem() == AeroSubsystem.FLIGHT) {
            bus.setFlightEngaged(toggle.enabled());
            if (!toggle.enabled()) {
                bus.setThrottle(0.0);
                AeroLinkManager.releaseAll(host.getLevel(), host.getPairedThrusters());
            }
        }
        bus.setStatus(toggle.subsystem().name().toLowerCase()
                + (toggle.enabled() ? " enabled" : " disabled"));
        host.setChanged();
        host.syncAero();
        return Result.ok(bus.status());
    }

    private static Result setThrottle(ShipVlsGuidanceBlockEntity host, AeroBus bus, double requested) {
        if (!Double.isFinite(requested)) return Result.reject("throttle must be a number");
        if (!bus.isFlightEngaged()) return Result.reject("engage flight before commanding throttle");
        double previous = bus.throttle();
        if (bus.powerTier() == AeroBus.PowerTier.CRITICAL && requested > previous) {
            return Result.reject("power critical — throttle increases refused");
        }
        bus.setThrottle(requested);
        host.syncAero();
        return Result.ok(String.format("throttle %.0f%%", bus.throttle() * 100.0));
    }

    private static Result setAttitude(ShipVlsGuidanceBlockEntity host, AeroBus bus,
                                      AeroAction.SetAttitude attitude) {
        if (!Double.isFinite(attitude.yawDeg())
                || !Double.isFinite(attitude.pitchDeg())
                || !Double.isFinite(attitude.rollDeg())) {
            return Result.reject("attitude must be numbers");
        }
        if (!bus.isFlightEngaged()) return Result.reject("engage flight before commanding attitude");
        bus.setAttitude(attitude.yawDeg(), attitude.pitchDeg(), attitude.rollDeg());
        host.syncAero();
        return Result.ok(String.format("attitude %.0f / %.0f / %.0f",
                bus.yawDeg(), bus.pitchDeg(), bus.rollDeg()));
    }

    private static Result setAutopilot(ShipVlsGuidanceBlockEntity host, AeroBus bus,
                                       AeroAutopilotMode mode) {
        if (mode == null) return Result.reject("unknown autopilot mode");
        if (mode != AeroAutopilotMode.MANUAL && host.getTarget() == null) {
            return Result.reject("set a target before engaging autopilot");
        }
        if (mode == AeroAutopilotMode.ROUTE && host.getPlannerSettings().waypoints().isEmpty()) {
            return Result.reject("route autopilot needs at least one waypoint");
        }
        bus.setAutopilotMode(mode);
        bus.setStatus(mode == AeroAutopilotMode.MANUAL ? "manual flight"
                : mode.name().toLowerCase() + " autopilot ready");
        host.syncAero();
        return Result.ok(bus.status());
    }
}
