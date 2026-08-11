/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from CC:LiftLink (github.com/tiktop101/CC-LiftLink), MPL-2.0, which targets
 * Minecraft 1.20.1 / Forge. This is the 1.21.1 / NeoForge port. Per MPL-2.0 section 3.3 these
 * two files stay under the MPL while the rest of XenoPixels Network keeps its own licence; see
 * THIRD_PARTY_NOTICES.md.
 */
package net.bullettrain.xenopixelsmod.compat.create.elevator;

import com.simibubi.create.content.contraptions.elevator.ElevatorColumn;
import com.simibubi.create.content.contraptions.elevator.ElevatorContactBlockEntity;
import com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.GenericPeripheral;
import dan200.computercraft.api.peripheral.PeripheralType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ComputerCraft methods on Create's elevator contact, so a computer can drive a lift.
 *
 * <p>Attach a wired modem to any <b>elevator contact</b> — the redstone contact blocks on the
 * shaft, not the pulley — and the whole column becomes addressable:
 *
 * <pre>
 * local lift = peripheral.find("create_elevator")
 * lift.listFloors()            -- every floor: y, shortName, longName, name, isTarget, isCurrent
 * lift.getState()              -- one table with everything below
 * lift.getRole()               -- "contact"
 * lift.getCabY()               -- live cab height, interpolated between floors
 * lift.getSpeed()              -- rope speed; 0 when parked
 * lift.getOffset()             -- raw rope offset
 * lift.getTargetY()            -- floor being travelled to, or nil
 * lift.isMoving()
 * lift.getNearestFloor()       -- nearest floor to the cab, plus its distance
 * lift.callToY(64)             -- call the cab to a Y
 * lift.callToFloor("Lobby")    -- call it by floor name
 * </pre>
 *
 * <p>The peripheral type is {@code create_elevator} and the method names and returned table keys
 * match CC:LiftLink exactly, so Lua written against that mod runs here unchanged.
 *
 * <p>Every method is {@code mainThread = true}: they read and mutate live block entities and
 * contraption entities, none of which is safe off the server thread.
 *
 * <p>Methods return nil rather than throwing for state that is merely absent — a disassembled
 * elevator has no pulley, so no speed, offset or interpolated cab height. A {@link LuaException}
 * is reserved for a genuinely bad request: no column at all, an unknown floor name, or a Y that
 * is neither a floor nor reachable.
 */
public final class ElevatorMethods implements GenericPeripheral {

    @Override
    public String id() {
        // Ours, not CC:LiftLink's: the generic-source id has to be unique per mod.
        return ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "elevator").toString();
    }

    @Override
    public PeripheralType getType() {
        // Deliberately the upstream type, so peripheral.find("create_elevator") keeps working.
        return PeripheralType.ofType("create_elevator");
    }

    /** Always {@code "contact"}; present so a program can tell what it attached to. */
    @LuaFunction(mainThread = true)
    public final String getRole(ElevatorContactBlockEntity contact) {
        return "contact";
    }

    /** Every floor on this column, bottom first. */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> listFloors(ElevatorContactBlockEntity contact) throws LuaException {
        Level level = contact.getLevel();
        ElevatorColumn column = requireColumn(level, contact);
        Integer targetY = columnTargetY(column);
        ElevatorContactBlockEntity current = ElevatorHelpers.findCurrentFloorContact(level, column);

        List<Map<String, Object>> out = new ArrayList<>();
        for (ElevatorContactBlockEntity floor : ElevatorHelpers.getSortedContacts(level, column)) {
            int y = floor.getBlockPos().getY();
            Map<String, Object> row = new HashMap<>();
            row.put("y", y);
            // Raw, not nonBlank: upstream returns these verbatim, so an unnamed floor gives Lua
            // an empty string here and nil only in "name". Narrowing that to nil would change
            // what an existing CC:LiftLink program sees.
            row.put("shortName", floor.shortName);
            row.put("longName", floor.longName);
            row.put("name", ElevatorHelpers.bestFloorName(floor));
            row.put("currentFloorName", ElevatorHelpers.nonBlank(floor.lastReportedCurrentFloor));
            row.put("isTarget", targetY != null && targetY == y);
            row.put("isCurrent", current != null && current.getBlockPos().equals(floor.getBlockPos()));
            out.add(row);
        }
        return out;
    }

    /** Everything the other getters return, in one round trip. */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getState(ElevatorContactBlockEntity contact) throws LuaException {
        Level level = contact.getLevel();
        ElevatorColumn column = requireColumn(level, contact);
        ElevatorPulleyBlockEntity pulley = ElevatorHelpers.resolvePulley(level, contact.getBlockPos());
        ElevatorContactBlockEntity currentFloor = ElevatorHelpers.findCurrentFloorContact(level, column);

        double speed = pulley == null ? 0.0 : pulley.getMovementSpeed();
        Integer currentTargetY = ElevatorHelpers.getCurrentTargetY(level, pulley);
        Integer targetY = currentTargetY != null ? currentTargetY : columnTargetY(column);
        Double cabY = ElevatorHelpers.getCurrentCabY(level, pulley);
        // Parked with no contraption: the floor it last reported is the best answer available.
        if (cabY == null && currentFloor != null) cabY = (double) currentFloor.getBlockPos().getY();

        Map<String, Object> out = new HashMap<>();
        out.put("modName", XenoPixelsMod.MOD_ID);
        out.put("role", "contact");
        out.put("active", column.isActive());
        out.put("targetAvailable", column.isTargetAvailable());
        out.put("targetY", targetY);
        out.put("currentTargetY", currentTargetY);
        out.put("speed", speed);
        out.put("offset", ElevatorHelpers.getCurrentOffset(pulley));
        out.put("cabY", cabY);
        out.put("moving", isMoving(pulley, speed, targetY, currentFloor));
        out.put("floorCount", ElevatorHelpers.getSortedContacts(level, column).size());
        out.put("blockX", contact.getBlockPos().getX());
        out.put("blockY", contact.getBlockPos().getY());
        out.put("blockZ", contact.getBlockPos().getZ());
        out.put("hasPulley", pulley != null);
        out.put("trackingMode", pulley != null ? "live_pulley" : "single_contact");
        out.put("currentFloorName", currentFloor == null ? null : ElevatorHelpers.bestFloorName(currentFloor));
        out.put("currentFloorY", currentFloor == null ? null : currentFloor.getBlockPos().getY());
        return out;
    }

    /**
     * Live cab height, falling back to the reported floor's Y when the elevator is parked and
     * has no contraption to interpolate from.
     */
    @LuaFunction(mainThread = true)
    public final Double getCabY(ElevatorContactBlockEntity contact) throws LuaException {
        Level level = contact.getLevel();
        ElevatorColumn column = requireColumn(level, contact);
        ElevatorPulleyBlockEntity pulley = ElevatorHelpers.resolvePulley(level, contact.getBlockPos());
        Double liveCabY = ElevatorHelpers.getCurrentCabY(level, pulley);
        if (liveCabY != null) return liveCabY;
        ElevatorContactBlockEntity current = ElevatorHelpers.findCurrentFloorContact(level, column);
        return current == null ? null : (double) current.getBlockPos().getY();
    }

    /** Rope speed. Zero when parked or when there is no pulley. */
    @LuaFunction(mainThread = true)
    public final double getSpeed(ElevatorContactBlockEntity contact) {
        ElevatorPulleyBlockEntity pulley =
                ElevatorHelpers.resolvePulley(contact.getLevel(), contact.getBlockPos());
        return pulley == null ? 0.0 : pulley.getMovementSpeed();
    }

    /** Raw rope offset, or nil with no pulley. */
    @LuaFunction(mainThread = true)
    public final Double getOffset(ElevatorContactBlockEntity contact) {
        return ElevatorHelpers.getCurrentOffset(
                ElevatorHelpers.resolvePulley(contact.getLevel(), contact.getBlockPos()));
    }

    /** The Y being travelled to, or nil when the cab is not going anywhere. */
    @LuaFunction(mainThread = true)
    public final Integer getTargetY(ElevatorContactBlockEntity contact) throws LuaException {
        Level level = contact.getLevel();
        ElevatorColumn column = requireColumn(level, contact);
        ElevatorPulleyBlockEntity pulley = ElevatorHelpers.resolvePulley(level, contact.getBlockPos());
        Integer live = ElevatorHelpers.getCurrentTargetY(level, pulley);
        return live != null ? live : columnTargetY(column);
    }

    /**
     * Whether the cab is in motion.
     *
     * <p>Rope speed when there is a pulley; otherwise inferred from the target differing from
     * the reported floor, which is the only signal a disassembled elevator gives.
     */
    @LuaFunction(mainThread = true)
    public final boolean isMoving(ElevatorContactBlockEntity contact) throws LuaException {
        Level level = contact.getLevel();
        ElevatorPulleyBlockEntity pulley = ElevatorHelpers.resolvePulley(level, contact.getBlockPos());
        if (pulley != null) return Math.abs(pulley.getMovementSpeed()) > SPEED_EPSILON;
        ElevatorColumn column = requireColumn(level, contact);
        return isMoving(null, 0.0, columnTargetY(column),
                ElevatorHelpers.findCurrentFloorContact(level, column));
    }

    /**
     * Call the cab to a Y.
     *
     * <p>Prefers the contact on that floor, so the call behaves exactly like pressing its
     * button. A Y with no contact works too while the elevator is assembled and the Y is inside
     * its travel range; otherwise this throws rather than silently doing nothing.
     */
    @LuaFunction(mainThread = true)
    public final boolean callToY(ElevatorContactBlockEntity contact, int y) throws LuaException {
        Level level = contact.getLevel();
        ElevatorColumn column = requireColumn(level, contact);
        ElevatorContactBlockEntity target = ElevatorHelpers.findContactByY(level, column, y);
        if (target != null) return ElevatorHelpers.callTo(level, column, target);
        if (ElevatorHelpers.callToArbitraryY(level, column, contact.getBlockPos(), y)) return true;
        throw new LuaException("Y=" + y + " is not a real floor and is outside the elevator travel range");
    }

    /** Call the cab to a named floor; matches either name, case-insensitively. */
    @LuaFunction(mainThread = true)
    public final boolean callToFloor(ElevatorContactBlockEntity contact, String floorName) throws LuaException {
        Level level = contact.getLevel();
        ElevatorColumn column = requireColumn(level, contact);
        ElevatorContactBlockEntity target = ElevatorHelpers.findContactByName(level, column, floorName);
        if (target == null) throw new LuaException("No floor named '" + floorName + "'");
        return ElevatorHelpers.callTo(level, column, target);
    }

    /** The floor the cab is closest to right now, with its distance in blocks. */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getNearestFloor(ElevatorContactBlockEntity contact) throws LuaException {
        Level level = contact.getLevel();
        ElevatorColumn column = requireColumn(level, contact);
        ElevatorPulleyBlockEntity pulley = ElevatorHelpers.resolvePulley(level, contact.getBlockPos());
        Double cabY = ElevatorHelpers.getCurrentCabY(level, pulley);

        ElevatorContactBlockEntity best = ElevatorHelpers.findBestContactForCabY(level, column, cabY);
        if (best == null) best = ElevatorHelpers.findCurrentFloorContact(level, column);
        if (best == null) return null;

        Map<String, Object> out = new HashMap<>();
        out.put("y", best.getBlockPos().getY());
        out.put("shortName", best.shortName);
        out.put("longName", best.longName);
        out.put("name", ElevatorHelpers.bestFloorName(best));
        out.put("distance", cabY == null ? 0.0 : Math.abs(best.getBlockPos().getY() - cabY));
        return out;
    }

    /** Rope speed below this reads as parked. */
    private static final double SPEED_EPSILON = 1.0e-4;

    private static ElevatorColumn requireColumn(Level level, ElevatorContactBlockEntity contact)
            throws LuaException {
        if (level == null) throw new LuaException("Elevator contact is not in a loaded world");
        ElevatorColumn column = ElevatorHelpers.resolveColumn(level, contact.getBlockPos());
        if (column == null) throw new LuaException("No elevator column found for this contact");
        return column;
    }

    /** Column target, but only when Create says one is actually set. */
    private static Integer columnTargetY(ElevatorColumn column) {
        return column.isTargetAvailable() ? column.getTargetedYLevel() : null;
    }

    private static boolean isMoving(ElevatorPulleyBlockEntity pulley, double speed,
                                    Integer targetY, ElevatorContactBlockEntity currentFloor) {
        if (pulley != null) return Math.abs(speed) > SPEED_EPSILON;
        return targetY != null && currentFloor != null && targetY != currentFloor.getBlockPos().getY();
    }
}
