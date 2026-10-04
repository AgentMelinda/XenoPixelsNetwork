package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * One of their factions, as one of ours.
 *
 * <p>The only category whose shape is verified against the 1.5.0 jar and a real save; the rest are
 * recorded as provisional in {@code docs/xeno-npc-schema.md} and are deliberately not imported yet.
 * Writing an importer for a format nobody has confirmed would produce files that look like a
 * migration and are not one.
 *
 * <p>Four of their keys are dropped on purpose. {@code FriendlyPoints} and {@code NeutralPoints}
 * are constants here ({@link XenoFaction#FRIENDLY_AT}, {@link XenoFaction#HOSTILE_BELOW}),
 * {@code HideFaction} is read by nothing, and {@code Slot} is the filename. Each is reported rather
 * than silently discarded, because an operator who set them deserves to know they did not survive.
 */
public final class FactionImport {

    /** My NPCs' own defaults, used when a faction predates the field. */
    private static final int DEFAULT_NEUTRAL_POINTS = 500;
    private static final int DEFAULT_FRIENDLY_POINTS = 1500;

    private static final String[] DROPPED = {"HideFaction", "Slot"};

    private FactionImport() {
    }

    /**
     * Converts one faction.
     *
     * @param slot   their int key, used to look up the id this index already assigned
     * @param tag    their faction tag
     * @param index  slot to id, built by assigning every faction in the file <em>before</em> any is
     *               converted - a faction can be hostile to one that appears later in the file
     * @param report where drops and unresolved hostilities are recorded
     */
    public static XenoFaction convert(int slot, CompoundTag tag, SlotIndex index,
                                      NpcImportReport report) {
        String id = index.idFor(slot);
        if (id == null) {
            // Only reachable when a caller converts before assigning, which is a programming
            // error rather than bad data - so it is loud.
            throw new IllegalStateException("slot " + slot + " has no id; assign it first");
        }
        String name = tag.contains("Name") ? tag.getString("Name") : id;
        int color = tag.contains("Color") ? tag.getInt("Color") & 0xFFFFFF : 0xFFFFFF;
        int neutralPoints = tag.contains("NeutralPoints")
                ? tag.getInt("NeutralPoints") : DEFAULT_NEUTRAL_POINTS;
        int friendlyPoints = tag.contains("FriendlyPoints")
                ? tag.getInt("FriendlyPoints") : DEFAULT_FRIENDLY_POINTS;
        int standing = convertStanding(
                tag.contains("DefaultPoints") ? tag.getInt("DefaultPoints") : 0,
                neutralPoints, friendlyPoints);
        boolean attacked = tag.getBoolean("GetsAttacked");

        List<String> hostile = new ArrayList<>();
        for (int foreignSlot : hostileSlots(tag)) {
            String other = index.idFor(foreignSlot);
            if (other == null) {
                // It names a faction that was not in the file. Inventing an id would produce a
                // hostility toward something that does not exist.
                report.note("faction " + id + ": dropped hostility toward unknown slot "
                        + foreignSlot);
                continue;
            }
            if (!other.equals(id) && !hostile.contains(other)) {
                hostile.add(other);
            }
        }

        for (String dropped : DROPPED) {
            if (tag.contains(dropped)) {
                // Grouped: these four repeat for every faction in the file, and the second copy
                // onward tells an operator nothing the first did not.
                report.noteGrouped("dropped " + dropped
                        + " (no equivalent field; see docs/xeno-npc-schema.md)", id);
            }
        }

        return new XenoFaction(id, name, color, hostile, standing, attacked);
    }

    /**
     * Their {@code DefaultPoints} on our standing axis, preserving the attitude exactly.
     *
     * <p>The two scales are unrelated, and clamping their number onto ours -- which is what this
     * did -- silently changed what every faction thought of the player. Their points run from zero
     * upward with two per-faction thresholds; ours run -1000..1000 with fixed ones. Clamping turned
     * a 1000-point <em>neutral</em> faction into a friendly one and a 0-point <em>aggressive</em>
     * faction into a neutral one, which is three of the five factions in a default install.
     *
     * <p>{@code Faction.playerStatus} in {@code mynpcs-neoforge-1.5.0.jar} is exactly:
     * {@code points >= friendlyPoints} is friendly, {@code points < neutralPoints} is hostile, and
     * anything between is neutral. Ours is {@code standing >= FRIENDLY_AT} and
     * {@code standing <= HOSTILE_BELOW}. So each of their three bands is mapped onto the matching
     * band of ours and the position within the band is kept proportional.
     *
     * <p>Note the boundary: their hostile test is strict and ours is not, so their
     * {@code neutralPoints} -- their lowest <em>neutral</em> value -- maps to
     * {@code HOSTILE_BELOW + 1} rather than to {@code HOSTILE_BELOW}.
     */
    static int convertStanding(int points, int neutralPoints, int friendlyPoints) {
        int lowestNeutral = XenoFaction.HOSTILE_BELOW + 1;
        int highestNeutral = XenoFaction.FRIENDLY_AT - 1;
        if (friendlyPoints <= neutralPoints) {
            // Misconfigured, or a faction with no neutral band at all. There is no middle to
            // place anything in, so only the two outer bands remain.
            return XenoFaction.clampStanding(points >= friendlyPoints
                    ? XenoFaction.FRIENDLY_AT : XenoFaction.HOSTILE_BELOW);
        }
        if (points >= friendlyPoints) {
            // Their friendly band has no ceiling, so their own threshold is the reference span:
            // twice the threshold reaches our maximum, and beyond that clamps.
            double over = (double) (points - friendlyPoints) / Math.max(1, friendlyPoints);
            return XenoFaction.clampStanding((int) Math.round(XenoFaction.FRIENDLY_AT
                    + over * (XenoFaction.MAX_STANDING - XenoFaction.FRIENDLY_AT)));
        }
        if (points < neutralPoints) {
            // Their hostile band is bounded below by zero, so it maps onto ours whole.
            double under = (double) (neutralPoints - points) / Math.max(1, neutralPoints);
            return XenoFaction.clampStanding((int) Math.round(XenoFaction.HOSTILE_BELOW
                    - under * (XenoFaction.HOSTILE_BELOW - XenoFaction.MIN_STANDING)));
        }
        double within = (double) (points - neutralPoints) / (friendlyPoints - neutralPoints);
        return XenoFaction.clampStanding((int) Math.round(
                lowestNeutral + within * (highestNeutral - lowestNeutral)));
    }

    /**
     * Their hostility list, which appears as three shapes.
     *
     * <p>The one 1.5.0 actually writes is a <b>list of compounds</b>, each holding the slot under
     * the key {@code Integer} -- {@code NBTTags.getIntegerSet} in the jar reads it with
     * {@code getCompound(i).getInt("Integer")}. That shape was the whole bug: {@code ListTag}
     * answers zero for {@code getInt} on a compound element, so every hostility in the file
     * resolved to slot 0 and each faction came out hostile to whatever happened to sit there.
     *
     * <p>The int-list and int-array readings are kept because older writes used them and reading
     * only one shape is what caused this in the first place.
     */
    private static int[] hostileSlots(CompoundTag tag) {
        if (!tag.contains("AttackFactions")) {
            return new int[0];
        }
        Tag raw = tag.get("AttackFactions");
        if (raw instanceof IntArrayTag array) {
            return array.getAsIntArray();
        }
        if (raw instanceof ListTag list) {
            int[] out = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Tag element = list.get(i);
                out[i] = element instanceof CompoundTag entry
                        ? entry.getInt("Integer")
                        : list.getInt(i);
            }
            return out;
        }
        return new int[0];
    }
}
