package net.bullettrain.xenopixelsmod.aero;

import net.bullettrain.xenopixelsmod.aero.power.AeroEnergyStorage;

/**
 * Per-tick power accounting for a flight controller.
 *
 * <p>Lives in this package rather than {@code aero.power} on purpose: {@link AeroBus}'s
 * mutators are package-private so that only the dispatcher and this budget can write
 * controller state. Putting the budget in a sub-package would have meant widening those
 * setters to public and losing that guarantee.
 *
 * <p>Draw figures come from the design notes: a 1,200 FE/t baseline sized around ten Mekanism
 * Advanced Solar Generators, plus 2,400 for engaged flight, 600 for the terrain map and 1,200
 * for advanced cooling. All are overridable in {@link AeroConfig}.
 */
public final class AeroPowerBudget {

    private AeroPowerBudget() {
    }

    /**
     * Charge this tick's power and update the bus.
     *
     * <p>Only meaningful in {@link ControllerMode#FLIGHT}; a controller in missile mode draws
     * nothing at all, which keeps the original guidance behavior byte-identical for worlds
     * that never opt into flight mode.
     *
     * <p>When the buffer cannot meet demand, subsystems are shed in {@link AeroSubsystem}
     * declaration order — least flight-critical first. Shedding genuinely disables the
     * subsystem on the bus rather than silently skipping it, so the operator can see what was
     * turned off and must re-enable it once power is restored.
     */
    public static void tick(AeroBus bus, AeroEnergyStorage energy) {
        if (bus == null) return;

        if (!AeroConfig.requirePower || bus.mode() != ControllerMode.FLIGHT) {
            bus.setDrawFePerTick(0);
            bus.setPowerTier(AeroBus.PowerTier.NOMINAL);
            if (energy != null) bus.setStoredEnergy(energy.getEnergyStored());
            return;
        }
        if (energy == null) return;

        int baseline = Math.max(0, AeroConfig.baselineDrawFePerTick);

        // Cannot even keep the lights on: everything off, emergency stop only.
        if (!energy.canAfford(baseline)) {
            for (AeroSubsystem subsystem : AeroSubsystem.values()) {
                bus.setEnabled(subsystem, false);
            }
            bus.setFlightEngaged(false);
            bus.setThrottle(0.0);
            // Deliberately does NOT drain the buffer. Burning the partial charge every tick
            // meant any supply slower than the baseline could never accumulate enough to come
            // back online — the controller sat at OFFLINE forever, silently eating its input.
            // An offline controller runs nothing, so it costs nothing.
            bus.setDrawFePerTick(0);
            bus.setStoredEnergy(energy.getEnergyStored());
            bus.setPowerTier(AeroBus.PowerTier.OFFLINE);
            bus.setStatus("power offline");
            return;
        }

        int demand = baseline + enabledDraw(bus);
        boolean shed = false;
        // Shed in declaration order until the buffer can carry the load.
        for (AeroSubsystem subsystem : AeroSubsystem.values()) {
            if (energy.canAfford(demand)) break;
            if (!bus.isEnabled(subsystem)) continue;
            bus.setEnabled(subsystem, false);
            if (subsystem == AeroSubsystem.FLIGHT) {
                bus.setFlightEngaged(false);
                bus.setThrottle(0.0);
            }
            demand = baseline + enabledDraw(bus);
            shed = true;
        }

        int consumed = energy.consume(demand);
        bus.setDrawFePerTick(demand);
        bus.setStoredEnergy(energy.getEnergyStored());

        AeroBus.PowerTier tier;
        if (consumed < demand) {
            // Should not happen after shedding, but never report healthier than reality.
            tier = AeroBus.PowerTier.CRITICAL;
        } else if (shed) {
            tier = bus.isEnabled(AeroSubsystem.FLIGHT)
                    ? AeroBus.PowerTier.REDUCED : AeroBus.PowerTier.CRITICAL;
        } else {
            tier = AeroBus.PowerTier.NOMINAL;
        }
        bus.setPowerTier(tier);
        if (shed) {
            bus.setStatus("power " + tier.name().toLowerCase() + " — subsystems shed");
        }
    }

    /** Total configured draw of everything currently enabled, excluding the baseline. */
    private static int enabledDraw(AeroBus bus) {
        int total = 0;
        for (AeroSubsystem subsystem : AeroSubsystem.values()) {
            if (bus.isEnabled(subsystem)) total += AeroConfig.drawFor(subsystem);
        }
        return total;
    }
}
