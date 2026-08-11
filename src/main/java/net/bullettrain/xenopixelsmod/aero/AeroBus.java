package net.bullettrain.xenopixelsmod.aero;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import java.util.EnumSet;
import java.util.Set;

/**
 * Avionics bus — the single authoritative state snapshot for one controller.
 *
 * <p>Every module reads from here and only {@link AeroActionDispatcher} writes to it, so the
 * GUI, the physical panel, and any peripheral observe the same values. The bus lives on the
 * server; clients receive a copy through the state sync packet.
 *
 * <p><b>Persistence rule:</b> operator configuration (mode, enabled subsystems) is saved;
 * live flight state (throttle, attitude setpoints, engagement) is not. This matches the
 * existing contract in {@code ShipVlsGuidanceBlockEntity}, which never resumes a flight after
 * a chunk reload, and in {@code ShipThrusterBlockEntity}, whose guidance ownership is
 * deliberately runtime-only. Persisting throttle would make a parked ship thrust on world
 * load.
 */
public final class AeroBus {
    /** Degradation tiers as available power falls. */
    public enum PowerTier {
        /** Full authority, everything the operator enabled is running. */
        NOMINAL,
        /** Non-essential subsystems shed; flight authority intact. */
        REDUCED,
        /** Flight authority limited; only stop/stabilize commands honored. */
        CRITICAL,
        /** No usable power. Emergency stop only. */
        OFFLINE
    }

    private ControllerMode mode = ControllerMode.MISSILE;
    private final Set<AeroSubsystem> enabled = EnumSet.noneOf(AeroSubsystem.class);

    private double throttle;
    private double yawDeg;
    private double pitchDeg;
    private double rollDeg;
    private boolean flightEngaged;
    private AeroAutopilotMode autopilotMode = AeroAutopilotMode.MANUAL;
    private int waypointIndex;
    private int waypointCount;
    private double targetDistance;
    private double actualSpeed;

    private PowerTier powerTier = PowerTier.NOMINAL;
    private int storedEnergy;
    private int drawFePerTick;

    private int linkCount;
    private int healthyLinkCount;
    private String status = "idle";

    public ControllerMode mode() {
        return mode;
    }

    void setMode(ControllerMode mode) {
        this.mode = mode == null ? ControllerMode.MISSILE : mode;
        if (this.mode != ControllerMode.FLIGHT) {
            // Leaving flight mode must not strand a throttle setting on the bus.
            resetFlightState();
        }
    }

    public boolean isEnabled(AeroSubsystem subsystem) {
        return enabled.contains(subsystem);
    }

    void setEnabled(AeroSubsystem subsystem, boolean on) {
        if (subsystem == null) return;
        if (on) enabled.add(subsystem);
        else enabled.remove(subsystem);
    }

    public Set<AeroSubsystem> enabledSubsystems() {
        // copyOf on an EnumSet is safe when empty, unlike the general Collection overload.
        return EnumSet.copyOf(enabled);
    }

    public double throttle() {
        return throttle;
    }

    void setThrottle(double value) {
        this.throttle = Mth.clamp(value, 0.0, 1.0);
    }

    public double yawDeg() {
        return yawDeg;
    }

    public double pitchDeg() {
        return pitchDeg;
    }

    public double rollDeg() {
        return rollDeg;
    }

    void setAttitude(double yaw, double pitch, double roll) {
        this.yawDeg = wrapDegrees(yaw);
        this.pitchDeg = Mth.clamp(pitch, -89.0, 89.0);
        this.rollDeg = wrapDegrees(roll);
    }

    private static double wrapDegrees(double value) {
        if (!Double.isFinite(value)) return 0.0;
        double wrapped = value % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    public boolean isFlightEngaged() {
        return flightEngaged;
    }

    void setFlightEngaged(boolean engaged) {
        this.flightEngaged = engaged;
        if (!engaged) autopilotMode = AeroAutopilotMode.MANUAL;
    }

    public AeroAutopilotMode autopilotMode() { return autopilotMode; }

    void setAutopilotMode(AeroAutopilotMode mode) {
        autopilotMode = mode == null ? AeroAutopilotMode.MANUAL : mode;
        waypointIndex = 0;
    }

    public int waypointIndex() { return waypointIndex; }
    public int waypointCount() { return waypointCount; }
    public double targetDistance() { return targetDistance; }
    public double actualSpeed() { return actualSpeed; }

    /** Server flight-director telemetry; does not alter operator configuration. */
    public void reportAutopilot(int index, int count, double distance, double speed, String message) {
        waypointIndex = Math.max(0, index);
        waypointCount = Math.max(0, count);
        targetDistance = Double.isFinite(distance) ? Math.max(0.0, distance) : 0.0;
        actualSpeed = Double.isFinite(speed) ? Math.max(0.0, speed) : 0.0;
        setStatus(message);
    }

    /** Safety transition used by the authoritative server controller on lost ship/power. */
    public void disengageRuntime(String reason) {
        resetFlightState();
        setStatus(reason);
    }

    public PowerTier powerTier() {
        return powerTier;
    }

    void setPowerTier(PowerTier tier) {
        this.powerTier = tier == null ? PowerTier.NOMINAL : tier;
    }

    public int storedEnergy() {
        return storedEnergy;
    }

    void setStoredEnergy(int stored) {
        this.storedEnergy = Math.max(0, stored);
    }

    public int drawFePerTick() {
        return drawFePerTick;
    }

    void setDrawFePerTick(int draw) {
        this.drawFePerTick = Math.max(0, draw);
    }

    public int linkCount() {
        return linkCount;
    }

    public int healthyLinkCount() {
        return healthyLinkCount;
    }

    void setLinkCounts(int total, int healthy) {
        this.linkCount = Math.max(0, total);
        this.healthyLinkCount = Mth.clamp(healthy, 0, this.linkCount);
    }

    public String status() {
        return status;
    }

    void setStatus(String status) {
        this.status = status == null || status.isBlank() ? "idle" : status;
    }

    /** Zero every live flight command. Does not touch operator configuration. */
    void resetFlightState() {
        throttle = 0.0;
        yawDeg = 0.0;
        pitchDeg = 0.0;
        rollDeg = 0.0;
        flightEngaged = false;
        autopilotMode = AeroAutopilotMode.MANUAL;
        waypointIndex = 0;
        waypointCount = 0;
        targetDistance = 0.0;
        actualSpeed = 0.0;
    }

    /** Saves operator configuration only — see the persistence rule in the class javadoc. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Mode", mode.name());
        CompoundTag subsystems = new CompoundTag();
        for (AeroSubsystem subsystem : AeroSubsystem.values()) {
            subsystems.putBoolean(subsystem.name(), enabled.contains(subsystem));
        }
        tag.put("Subsystems", subsystems);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag == null) return;
        mode = ControllerMode.byName(tag.getString("Mode"));
        enabled.clear();
        CompoundTag subsystems = tag.getCompound("Subsystems");
        for (AeroSubsystem subsystem : AeroSubsystem.values()) {
            if (subsystems.getBoolean(subsystem.name())) enabled.add(subsystem);
        }
        // Live state is never restored.
        resetFlightState();
        powerTier = PowerTier.NOMINAL;
        storedEnergy = 0;
        drawFePerTick = 0;
        linkCount = 0;
        healthyLinkCount = 0;
        status = "idle";
    }
}
