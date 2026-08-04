package net.bullettrain.xenopixelsmod.compat.computercraft;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.GenericPeripheral;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.bullettrain.xenopixelsmod.missile.BallisticFlightPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;

/**
 * CC methods for the native Ballistic Guidance Computer (not Ballistix).
 *
 * <pre>
 * peripheral.setTarget(x, y, z)
 * peripheral.getTarget()
 * peripheral.clearTarget()
 * peripheral.launch()              -- launches the VS ship as a ballistic missile
 * peripheral.abort()               -- abort in-flight ship missile
 * peripheral.setTerminal(true)
 * peripheral.setSpeed(level)       -- 1–20 missile power (Heavy hulls: 12–20)
 * peripheral.getSpeed()
 * peripheral.setBoost(accel, ticks)
 * peripheral.setYield(power)
 * peripheral.setShipMissile(true)  -- whole hull is the warhead (default true)
 * peripheral.setAlsoTubes(false)   -- also fire entity tubes
 * peripheral.getSolution()         -- {pitchDeg, etaTicks}
 * peripheral.getStatus()           -- flight phase string
 * peripheral.setFleet(channel, intervalTicks) -- 0 disables; interval 1-200
 * peripheral.broadcastTarget()     -- sync target to loaded channel members
 * peripheral.fleetSalvo()          -- stagger one launch per loaded vessel
 * </pre>
 */
public final class VlsGuidancePeripheral implements GenericPeripheral {
    @Override
    public String id() {
        return new ResourceLocation(XenoPixelsMod.MOD_ID, "ballistic_guidance").toString();
    }

    @LuaFunction(mainThread = true)
    public final void setTarget(ShipVlsGuidanceBlockEntity be, int x, int y, int z) {
        be.setTargetWorld(x, y, z);
        be.recomputeSolution();
    }

    /** Horizontal range to current target (blocks), or -1 if none. */
    @LuaFunction(mainThread = true)
    public final double getRange(ShipVlsGuidanceBlockEntity be) {
        return be.horizontalRangeToTarget();
    }

    /** Configured max range (blocks); huge number if unlimited. */
    @LuaFunction(mainThread = true)
    public final double getMaxRange(ShipVlsGuidanceBlockEntity be) {
        double m = net.bullettrain.xenopixelsmod.config.XenoPerfConfig.maxBallisticRange();
        return m >= Double.MAX_VALUE / 2 ? -1 : m;
    }

    @LuaFunction(mainThread = true)
    public final Object getTarget(ShipVlsGuidanceBlockEntity be) {
        BlockPos t = be.getTarget();
        if (t == null) return null;
        return new Object[]{t.getX(), t.getY(), t.getZ()};
    }

    @LuaFunction(mainThread = true)
    public final void clearTarget(ShipVlsGuidanceBlockEntity be) {
        be.clearTarget();
    }

    @LuaFunction(mainThread = true)
    public final int launch(ShipVlsGuidanceBlockEntity be) {
        if (be.getLevel() instanceof ServerLevel sl) {
            return be.firePulse(sl);
        }
        return 0;
    }

    @LuaFunction(mainThread = true)
    public final boolean abort(ShipVlsGuidanceBlockEntity be) {
        return be.abortShipFlight();
    }

    @LuaFunction(mainThread = true)
    public final void setTerminal(ShipVlsGuidanceBlockEntity be, boolean enabled) {
        be.setTerminalGuidance(enabled);
    }

    /** Missile speed/power 1–20 (higher for heavy VS2 hulls). */
    @LuaFunction(mainThread = true)
    public final void setSpeed(ShipVlsGuidanceBlockEntity be, int level) {
        be.setSpeedLevel(level);
    }

    @LuaFunction(mainThread = true)
    public final int getSpeed(ShipVlsGuidanceBlockEntity be) {
        return be.getSpeedLevel();
    }

    /**
     * Loft peak (world Y): climb here first. Pass 0 for auto.
     */
    @LuaFunction(mainThread = true)
    public final void setApexY(ShipVlsGuidanceBlockEntity be, int y) {
        be.setDesiredApexY(y);
    }

    @LuaFunction(mainThread = true)
    public final int getApexY(ShipVlsGuidanceBlockEntity be) {
        return be.getDesiredApexY();
    }

    /** Alias for loft peak. */
    @LuaFunction(mainThread = true)
    public final void setLoftY(ShipVlsGuidanceBlockEntity be, int y) {
        be.setDesiredApexY(y);
    }

    @LuaFunction(mainThread = true)
    public final int getLoftY(ShipVlsGuidanceBlockEntity be) {
        return be.getDesiredApexY();
    }

    /**
     * Cruise altitude (world Y): after loft, fly level straight to target at this Y.
     * Pass 0 to cruise at loft peak.
     */
    @LuaFunction(mainThread = true)
    public final void setCruiseY(ShipVlsGuidanceBlockEntity be, int y) {
        be.setDesiredCruiseY(y);
    }

    @LuaFunction(mainThread = true)
    public final int getCruiseY(ShipVlsGuidanceBlockEntity be) {
        return be.getDesiredCruiseY();
    }

    @LuaFunction(mainThread = true)
    public final void setBoost(ShipVlsGuidanceBlockEntity be, double accel, int ticks) {
        be.setBoostAccel(accel);
        be.setBoostTicks(ticks);
        be.recomputeSolution();
    }

    @LuaFunction(mainThread = true)
    public final void setYield(ShipVlsGuidanceBlockEntity be, double yield) {
        be.setWarheadYield((float) yield);
    }

    @LuaFunction(mainThread = true)
    public final void setRadius(ShipVlsGuidanceBlockEntity be, int radius) {
        be.setSearchRadius(radius);
    }

    @LuaFunction(mainThread = true)
    public final void setShipMissile(ShipVlsGuidanceBlockEntity be, boolean enabled) {
        be.setShipMissileMode(enabled);
    }

    @LuaFunction(mainThread = true)
    public final void setAlsoTubes(ShipVlsGuidanceBlockEntity be, boolean enabled) {
        be.setAlsoFireTubes(enabled);
    }

    /**
     * @return {pitchDeg, etaTicks, range, maxRange, status}
     */
    @LuaFunction(mainThread = true)
    public final Object getSolution(ShipVlsGuidanceBlockEntity be) {
        be.recomputeSolution();
        double range = be.horizontalRangeToTarget();
        double max = net.bullettrain.xenopixelsmod.config.XenoPerfConfig.maxBallisticRange();
        return new Object[]{
                be.getLastPitchDeg(),
                be.getLastEtaTicks(),
                range,
                max >= Double.MAX_VALUE / 2 ? -1 : max,
                be.getLastStatus()
        };
    }

    @LuaFunction(mainThread = true)
    public final String getStatus(ShipVlsGuidanceBlockEntity be) {
        return be.getLastStatus();
    }

    @LuaFunction(mainThread = true)
    public final void setFleet(ShipVlsGuidanceBlockEntity be, int channel, int intervalTicks) {
        be.setFleetChannel(channel);
        be.setSalvoIntervalTicks(intervalTicks);
    }

    @LuaFunction(mainThread = true)
    public final Object getFleet(ShipVlsGuidanceBlockEntity be) {
        return new Object[]{be.getFleetChannel(), be.getSalvoIntervalTicks()};
    }

    @LuaFunction(mainThread = true)
    public final int broadcastTarget(ShipVlsGuidanceBlockEntity be) {
        return be.broadcastFleetTarget();
    }

    @LuaFunction(mainThread = true)
    public final int fleetSalvo(ShipVlsGuidanceBlockEntity be) {
        return be.queueFleetSalvo();
    }

    @LuaFunction(mainThread = true)
    public final void setFlightModel(ShipVlsGuidanceBlockEntity be, double gravitySi, double dragPerMeter) {
        be.setFlightPhysics(gravitySi, dragPerMeter);
    }

    /** {azimuthDeg, elevationDeg, requiredMps, availableMps, etaTicks, apexY, impactMps, miss, reachable}. */
    @LuaFunction(mainThread = true)
    public final Object getBallisticCalculation(ShipVlsGuidanceBlockEntity be) {
        be.recomputeSolution();
        var calc = be.getLastCalculation();
        if (calc == null) return null;
        return new Object[]{
                calc.azimuthDeg(), calc.selectedAngleDeg(),
                calc.requiredSpeed() * 20.0, calc.availableSpeed() * 20.0,
                calc.flightTimeTicks(), calc.apexY(), calc.impactSpeed() * 20.0,
                calc.predictedMiss(), calc.reachable()
        };
    }

    @LuaFunction(mainThread = true)
    public final void setPlannerAuto(ShipVlsGuidanceBlockEntity be, boolean enabled) {
        BallisticFlightPlan.Settings s = be.getPlannerSettings();
        be.setPlannerSettings(new BallisticFlightPlan.Settings(enabled, s.mode(), s.arc(), s.profile(), s.phaseLayerEnabled(),
                s.waypointLayerEnabled(), s.altitudeLayerEnabled(), s.motorCutoffFraction(), s.apexFraction(),
                s.terminalFraction(), s.minimumClearanceY(), s.ceilingY(), s.desiredAngleDeg(),
                s.angleCommandY(), s.angleCommandDeg(), s.waypoints()));
    }

    @LuaFunction(mainThread = true)
    public final void setPlannerMode(ShipVlsGuidanceBlockEntity be, String mode) {
        BallisticFlightPlan.Settings s = be.getPlannerSettings();
        BallisticFlightPlan.FlightMode parsed = "pure".equalsIgnoreCase(mode) || "pure_ballistic".equalsIgnoreCase(mode)
                ? BallisticFlightPlan.FlightMode.PURE_BALLISTIC : BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE;
        be.setPlannerSettings(new BallisticFlightPlan.Settings(s.autoEnabled(), parsed, s.arc(), s.profile(), s.phaseLayerEnabled(),
                s.waypointLayerEnabled(), s.altitudeLayerEnabled(), s.motorCutoffFraction(), s.apexFraction(),
                s.terminalFraction(), s.minimumClearanceY(), s.ceilingY(), s.desiredAngleDeg(),
                s.angleCommandY(), s.angleCommandDeg(), s.waypoints()));
    }

    @LuaFunction(mainThread = true)
    public final boolean setTrajectoryProfile(ShipVlsGuidanceBlockEntity be, String profile) {
        BallisticFlightPlan.TrajectoryProfile parsed;
        try {
            parsed = BallisticFlightPlan.TrajectoryProfile.valueOf(profile.trim().toUpperCase().replace(' ', '_'));
        } catch (RuntimeException ex) {
            return false;
        }
        BallisticFlightPlan.Settings s = be.getPlannerSettings();
        be.setPlannerSettings(new BallisticFlightPlan.Settings(s.autoEnabled(), s.mode(), s.arc(), parsed,
                s.phaseLayerEnabled(), s.waypointLayerEnabled(), s.altitudeLayerEnabled(),
                s.motorCutoffFraction(), s.apexFraction(), s.terminalFraction(),
                s.minimumClearanceY(), s.ceilingY(), s.desiredAngleDeg(),
                s.angleCommandY(), s.angleCommandDeg(), s.waypoints()));
        return true;
    }

    @LuaFunction(mainThread = true)
    public final boolean addPlanWaypoint(ShipVlsGuidanceBlockEntity be, double x, double y, double z, boolean locked) {
        BallisticFlightPlan.Settings s = be.getPlannerSettings();
        if (s.waypoints().size() >= BallisticFlightPlan.MAX_WAYPOINTS) return false;
        ArrayList<BallisticFlightPlan.Waypoint> points = new ArrayList<>(s.waypoints());
        points.add(new BallisticFlightPlan.Waypoint(x, y, z, locked));
        be.setPlannerSettings(new BallisticFlightPlan.Settings(s.autoEnabled(), s.mode(), s.arc(), s.profile(), s.phaseLayerEnabled(),
                true, s.altitudeLayerEnabled(), s.motorCutoffFraction(), s.apexFraction(), s.terminalFraction(),
                s.minimumClearanceY(), s.ceilingY(), s.desiredAngleDeg(),
                s.angleCommandY(), s.angleCommandDeg(), points));
        return true;
    }

    @LuaFunction(mainThread = true)
    public final void clearPlanWaypoints(ShipVlsGuidanceBlockEntity be) {
        BallisticFlightPlan.Settings s = be.getPlannerSettings();
        be.setPlannerSettings(new BallisticFlightPlan.Settings(s.autoEnabled(), s.mode(), s.arc(), s.profile(), s.phaseLayerEnabled(),
                s.waypointLayerEnabled(), s.altitudeLayerEnabled(), s.motorCutoffFraction(), s.apexFraction(),
                s.terminalFraction(), s.minimumClearanceY(), s.ceilingY(), s.desiredAngleDeg(),
                s.angleCommandY(), s.angleCommandDeg(), java.util.List.of()));
    }

    /** Set world-Y-triggered pitch; pass zero for either value to disable it. */
    @LuaFunction(mainThread = true)
    public final void setAltitudeAngleCommand(ShipVlsGuidanceBlockEntity be,
                                              double worldY, double pitchDegrees) {
        BallisticFlightPlan.Settings s = be.getPlannerSettings();
        double y = worldY > 0.0 ? worldY : 0.0;
        double pitch = y > 0.0 ? Math.max(-89.0, Math.min(89.0, pitchDegrees)) : 0.0;
        if (Math.abs(pitch) < 0.001) y = 0.0;
        BallisticFlightPlan.FlightMode mode = y > 0.0
                ? BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE : s.mode();
        be.setPlannerSettings(new BallisticFlightPlan.Settings(s.autoEnabled(), mode, s.arc(), s.profile(),
                s.phaseLayerEnabled(), y > 0.0 || s.waypointLayerEnabled(), s.altitudeLayerEnabled(),
                s.motorCutoffFraction(), s.apexFraction(), s.terminalFraction(),
                s.minimumClearanceY(), s.ceilingY(), s.desiredAngleDeg(), y, pitch, s.waypoints()));
    }

    @LuaFunction(mainThread = true)
    public final Object getPlanner(ShipVlsGuidanceBlockEntity be) {
        BallisticFlightPlan.Settings s = be.getPlannerSettings();
        return new Object[]{s.autoEnabled(), s.mode().name(), s.arc().name(), s.profile().name(), s.phaseLayerEnabled(),
                s.waypointLayerEnabled(), s.altitudeLayerEnabled(), s.motorCutoffFraction(), s.apexFraction(),
                s.terminalFraction(), s.minimumClearanceY(), s.ceilingY(), s.desiredAngleDeg(),
                s.angleCommandY(), s.angleCommandDeg(), s.waypoints().size(), be.getTargetShipId()};
    }
}
