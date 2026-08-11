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

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.elevator.ElevatorColumn;
import com.simibubi.create.content.contraptions.elevator.ElevatorColumn.ColumnCoords;
import com.simibubi.create.content.contraptions.elevator.ElevatorContactBlock;
import com.simibubi.create.content.contraptions.elevator.ElevatorContactBlockEntity;
import com.simibubi.create.content.contraptions.elevator.ElevatorContraption;
import com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Column, contact and pulley lookups behind {@link ElevatorMethods}.
 *
 * <p>An elevator's live state is split across three places: the {@link ElevatorColumn} (the set
 * of contacts and the requested floor), the {@link ElevatorPulleyBlockEntity} (rope offset and
 * speed) and the {@link ElevatorContraption} riding on it (cab geometry and reachability). A
 * contact block knows only its own column, so everything else has to be resolved outward from
 * it, and every one of those steps can legitimately come back empty — an unassembled elevator
 * has no contraption, a parked one has no pulley motion. Callers get {@code null} rather than
 * an exception for those, and turn it into a Lua {@code nil}.
 */
public final class ElevatorHelpers {

    /**
     * {@code ControlledContraptionEntity.controllerPos} is protected, and we are not in Create's
     * package. It is the only link from a contraption entity back to the pulley driving it, so
     * reflection is the only route; a Create version that renames it degrades to "no pulley",
     * which every caller already handles.
     */
    private static final Field CONTROLLER_POS_FIELD;

    static {
        Field field = null;
        try {
            field = ControlledContraptionEntity.class.getDeclaredField("controllerPos");
            field.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Left null; getControllerPos falls back to the entity scan.
        }
        CONTROLLER_POS_FIELD = field;
    }

    /**
     * How far above the highest contact a pulley may sit before the fallback scan gives up.
     *
     * <p>The upstream implementation scanned every Y in the world for a 7x7 column of positions.
     * On a standard world that is ~19k block-entity lookups on the server thread per call; on a
     * world using this mod's own {@code xeno_max_overworld} datapack (-2032..2032) it is ~200k,
     * every time a Lua program polls. The pulley is always above the cab, and therefore above
     * the topmost contact, so bounding the scan to the contact range plus this margin covers
     * every real elevator at a fraction of the cost.
     */
    private static final int PULLEY_SCAN_ABOVE = 64;
    /** Horizontal radius of the fallback pulley scan, in blocks. */
    private static final int PULLEY_SCAN_RADIUS = 3;

    private ElevatorHelpers() {
    }

    /** The column a contact or pulley belongs to, or {@code null} if it has no contacts. */
    public static ElevatorColumn resolveColumn(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof ElevatorPulleyBlockEntity pulley) {
            ElevatorColumn fromContraption = resolveColumnFromPulley(level, pulley);
            if (fromContraption != null) return fromContraption;

            // A parked pulley has no contraption to ask, so fall back to matching the loaded
            // columns on x/z.
            Map<ColumnCoords, ElevatorColumn> loaded = ElevatorColumn.LOADED_COLUMNS.get(level);
            if (loaded != null) {
                for (Map.Entry<ColumnCoords, ElevatorColumn> entry : loaded.entrySet()) {
                    ColumnCoords coords = entry.getKey();
                    if (coords.x() != pos.getX() || coords.z() != pos.getZ()) continue;
                    ElevatorColumn column = entry.getValue();
                    column.gatherAll();
                    if (!column.getContacts().isEmpty()) return column;
                }
            }
            return null;
        }

        ColumnCoords coords = null;
        if (be instanceof ElevatorContactBlockEntity contact && contact.columnCoords != null) {
            coords = contact.columnCoords;
        }
        if (coords == null) coords = ElevatorContactBlock.getColumnCoords(level, pos);
        if (coords == null) return null;

        ElevatorColumn column = ElevatorColumn.getOrCreate(level, coords);
        column.gatherAll();
        if (column.getContacts().isEmpty()) return null;

        // Cache the coords back onto the contact so the next call skips the block scan.
        if (be instanceof ElevatorContactBlockEntity contact) contact.columnCoords = coords;
        return column;
    }

    private static ElevatorColumn resolveColumnFromPulley(Level level, ElevatorPulleyBlockEntity pulley) {
        if (!(pulley.getAttachedContraption() instanceof ControlledContraptionEntity controlled)) return null;
        Contraption contraption = controlled.getContraption();
        if (!(contraption instanceof ElevatorContraption elevator)) return null;
        ColumnCoords coords = elevator.getGlobalColumn();
        if (coords == null) return null;
        ElevatorColumn column = ElevatorColumn.getOrCreate(level, coords);
        column.gatherAll();
        return column.getContacts().isEmpty() ? null : column;
    }

    private static ColumnCoords resolveCoords(Level level, BlockPos pos, ElevatorColumn column) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ElevatorContactBlockEntity contact && contact.columnCoords != null) {
            return contact.columnCoords;
        }
        ColumnCoords coords = ElevatorContactBlock.getColumnCoords(level, pos);
        if (coords != null) return coords;
        if (column != null && !column.getContacts().isEmpty()) {
            BlockPos first = column.getContacts().iterator().next();
            BlockEntity firstBe = level.getBlockEntity(first);
            if (firstBe instanceof ElevatorContactBlockEntity contact && contact.columnCoords != null) {
                return contact.columnCoords;
            }
            return ElevatorContactBlock.getColumnCoords(level, first);
        }
        return null;
    }

    private static BlockPos getControllerPos(ControlledContraptionEntity controlled) {
        if (CONTROLLER_POS_FIELD == null) return null;
        try {
            return CONTROLLER_POS_FIELD.get(controlled) instanceof BlockPos pos ? pos : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Find the pulley by looking for an assembled elevator contraption over this column. */
    private static ElevatorPulleyBlockEntity resolvePulleyFromEntities(Level level, ColumnCoords coords) {
        AABB box = new AABB(
                coords.x() - 4, level.getMinBuildHeight(), coords.z() - 4,
                coords.x() + 5, level.getMaxBuildHeight(), coords.z() + 5);
        for (ControlledContraptionEntity controlled
                : level.getEntitiesOfClass(ControlledContraptionEntity.class, box)) {
            if (!(controlled.getContraption() instanceof ElevatorContraption elevator)) continue;
            ColumnCoords global = elevator.getGlobalColumn();
            if (global == null || global.x() != coords.x() || global.z() != coords.z()) continue;
            BlockPos controllerPos = getControllerPos(controlled);
            if (controllerPos == null) continue;
            if (level.getBlockEntity(controllerPos) instanceof ElevatorPulleyBlockEntity pulley) return pulley;
        }
        return null;
    }

    /**
     * Block scan for a pulley, used when the elevator is disassembled so there is no contraption
     * entity to ask. Bounded to {@code minY..maxY}; see {@link #PULLEY_SCAN_ABOVE}.
     */
    private static ElevatorPulleyBlockEntity scanForPulley(Level level, int centerX, int centerZ,
                                                          int minY, int maxY) {
        int lo = Math.max(level.getMinBuildHeight(), minY);
        int hi = Math.min(level.getMaxBuildHeight() - 1, maxY);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -PULLEY_SCAN_RADIUS; dx <= PULLEY_SCAN_RADIUS; dx++) {
            for (int dz = -PULLEY_SCAN_RADIUS; dz <= PULLEY_SCAN_RADIUS; dz++) {
                // Downward: the pulley is above the cab, so the top of the range hits first.
                for (int y = hi; y >= lo; y--) {
                    cursor.set(centerX + dx, y, centerZ + dz);
                    if (level.getBlockEntity(cursor) instanceof ElevatorPulleyBlockEntity pulley) return pulley;
                }
            }
        }
        return null;
    }

    /** The pulley driving this column, or {@code null} when there is none in range. */
    public static ElevatorPulleyBlockEntity resolvePulley(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ElevatorPulleyBlockEntity self) return self;
        ElevatorColumn column = resolveColumn(level, pos);
        if (column == null) return null;

        ColumnCoords coords = resolveCoords(level, pos, column);
        if (coords != null) {
            ElevatorPulleyBlockEntity pulley = resolvePulleyFromEntities(level, coords);
            if (pulley != null) return pulley;
        }

        // Contacts bound the scan: the pulley hangs above the topmost one.
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BlockPos contactPos : column.getContacts()) {
            minY = Math.min(minY, contactPos.getY());
            maxY = Math.max(maxY, contactPos.getY());
        }
        if (minY > maxY) return null;

        if (coords != null) {
            ElevatorPulleyBlockEntity pulley =
                    scanForPulley(level, coords.x(), coords.z(), minY, maxY + PULLEY_SCAN_ABOVE);
            if (pulley != null) return pulley;
        }
        for (BlockPos contactPos : column.getContacts()) {
            ElevatorPulleyBlockEntity pulley = scanForPulley(level,
                    contactPos.getX(), contactPos.getZ(), minY, maxY + PULLEY_SCAN_ABOVE);
            if (pulley != null) return pulley;
        }
        return null;
    }

    /** Every loaded contact on the column, bottom floor first. */
    public static List<ElevatorContactBlockEntity> getSortedContacts(Level level, ElevatorColumn column) {
        List<ElevatorContactBlockEntity> result = new ArrayList<>();
        for (BlockPos contactPos : column.getContacts()) {
            if (level.getBlockEntity(contactPos) instanceof ElevatorContactBlockEntity contact) {
                result.add(contact);
            }
        }
        result.sort(Comparator.comparingInt(c -> c.getBlockPos().getY()));
        return result;
    }

    public static ElevatorContactBlockEntity findContactByY(Level level, ElevatorColumn column, int y) {
        for (ElevatorContactBlockEntity contact : getSortedContacts(level, column)) {
            if (contact.getBlockPos().getY() == y) return contact;
        }
        return null;
    }

    /** Match on either floor name, case-insensitively, the way the Create UI presents them. */
    public static ElevatorContactBlockEntity findContactByName(Level level, ElevatorColumn column, String name) {
        String wanted = name.trim();
        for (ElevatorContactBlockEntity contact : getSortedContacts(level, column)) {
            if (wanted.equalsIgnoreCase(contact.shortName) || wanted.equalsIgnoreCase(contact.longName)) {
                return contact;
            }
        }
        return null;
    }

    /**
     * The floor the cab is reported to be at.
     *
     * <p>Create broadcasts the current floor's name to every contact, so the contact whose own
     * name matches what it is displaying is the one the cab is parked at.
     */
    public static ElevatorContactBlockEntity findCurrentFloorContact(Level level, ElevatorColumn column) {
        for (ElevatorContactBlockEntity contact : getSortedContacts(level, column)) {
            String currentName = nonBlank(contact.lastReportedCurrentFloor);
            if (currentName == null) continue;
            if (currentName.equalsIgnoreCase(nonBlank(contact.shortName))
                    || currentName.equalsIgnoreCase(nonBlank(contact.longName))) {
                return contact;
            }
        }
        return null;
    }

    /** Nearest contact to a live cab height, or {@code null} when the cab height is unknown. */
    public static ElevatorContactBlockEntity findBestContactForCabY(Level level, ElevatorColumn column, Double cabY) {
        if (cabY == null) return null;
        ElevatorContactBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ElevatorContactBlockEntity contact : getSortedContacts(level, column)) {
            double distance = Math.abs(contact.getBlockPos().getY() - cabY);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = contact;
            }
        }
        return best;
    }

    /**
     * Call the cab to a contact, exactly as pressing that contact's button does — Create's own
     * call path, so redstone output, floor display and the target lock all update with it.
     */
    public static boolean callTo(Level level, ElevatorColumn column, ElevatorContactBlockEntity target) {
        if (target == null) return false;
        BlockState state = target.getBlockState();
        if (!(state.getBlock() instanceof ElevatorContactBlock block)) return false;
        block.callToContactAndUpdate(column, state, level, target.getBlockPos(), false);
        return true;
    }

    /**
     * Target a Y with no contact on it.
     *
     * <p>Only possible while assembled: the contraption is what knows the travel range, and
     * without it a bad target would strand the cab. {@code isTargetUnreachable} is Create's own
     * bounds check.
     */
    public static boolean callToArbitraryY(Level level, ElevatorColumn column, BlockPos originPos, int y) {
        ElevatorPulleyBlockEntity pulley = resolvePulley(level, originPos);
        if (pulley == null) return false;
        if (!(pulley.getAttachedContraption() instanceof ControlledContraptionEntity controlled)) return false;
        if (!(controlled.getContraption() instanceof ElevatorContraption contraption)) return false;
        if (contraption.isTargetUnreachable(y)) return false;
        column.target(y);
        column.markDirty();
        return true;
    }

    /** The contraption's live target Y, which leads the column's while it is moving. */
    public static Integer getCurrentTargetY(Level level, ElevatorPulleyBlockEntity pulley) {
        ElevatorContraption contraption = contraptionOf(pulley);
        return contraption == null ? null : contraption.getCurrentTargetY(level);
    }

    /** Rope offset in blocks, or {@code null} with no pulley. */
    public static Double getCurrentOffset(ElevatorPulleyBlockEntity pulley) {
        return pulley == null ? null : (double) pulley.offset;
    }

    /**
     * Live cab height in world Y, interpolated from the rope rather than snapped to a floor.
     *
     * <p>The pulley is the fixed end of the rope, so the cab hangs {@code offset} below it; the
     * contact offset re-bases that from the contraption's origin onto the contact plane, which
     * is what {@code y} means everywhere else in this API.
     */
    public static Double getCurrentCabY(Level level, ElevatorPulleyBlockEntity pulley) {
        ElevatorContraption contraption = contraptionOf(pulley);
        if (contraption == null) return null;
        return pulley.getBlockPos().getY() + contraption.getContactYOffset() - 1.0 - pulley.offset;
    }

    private static ElevatorContraption contraptionOf(ElevatorPulleyBlockEntity pulley) {
        if (pulley == null) return null;
        AbstractContraptionEntity attached = pulley.getAttachedContraption();
        if (!(attached instanceof ControlledContraptionEntity controlled)) return null;
        return controlled.getContraption() instanceof ElevatorContraption elevator ? elevator : null;
    }

    /** {@code null} for null/blank, so Lua sees {@code nil} instead of an empty string. */
    public static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Short name preferred, long name as fallback; {@code null} if the floor is unnamed.
     *
     * <p>Deliberately not {@code Objects.requireNonNullElse}, which upstream uses here: that
     * throws when <em>both</em> arguments are null, so an entirely unnamed floor — the state a
     * freshly placed contact is in — would blow up the Lua call instead of returning nil.
     */
    public static String bestFloorName(ElevatorContactBlockEntity contact) {
        String shortName = nonBlank(contact.shortName);
        return shortName != null ? shortName : nonBlank(contact.longName);
    }
}
