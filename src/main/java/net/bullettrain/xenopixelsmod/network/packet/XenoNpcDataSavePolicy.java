package net.bullettrain.xenopixelsmod.network.packet;

import net.minecraft.nbt.CompoundTag;

import java.util.Set;

/**
 * What an editor save may change on {@link net.bullettrain.xenopixelsmod.npc.XenoNpcData}.
 *
 * <p>A second whitelist rather than a wider one, because there are two destinations.
 * {@code XenoNpcSavePacket} has always carried a single tag merged into {@code NpcCombatProfile};
 * home, leash and respawn live on {@code XenoNpcData} instead. Adding their keys to the profile
 * whitelist would have been inert — they would pass validation and then be silently dropped,
 * because {@code NpcCombatProfile.fromTag} has never heard of them. That failure looks exactly
 * like a save that worked, which is the failure this whole pair of classes exists to prevent.
 *
 * <p>The list is deliberately short. {@code Revision} is the optimistic-lock counter.
 * {@code Role} is editable through a bounded enum and the NPC's live role predicates consult it
 * after a save. {@code Owner}, {@code SourceMod} and {@code SourceUuid} are provenance, written
 * once by whatever created the NPC.
 */
public final class XenoNpcDataSavePolicy {

    /** Seven keys and a little headroom; a save carrying more is not from our editor. */
    public static final int MAX_KEYS = 16;

    private static final Set<String> EDITABLE_KEYS = Set.of(
            // The respawn point. Editable now that the editor can set it; before this it was
            // written once by the wand at placement and could never be moved again.
            "HomeX", "HomeY", "HomeZ",
            // Blocks from home before the NPC is walked back. Zero disables it.
            "LeashRadius",
            // Whether it comes back at all, and how long it takes. Both were hardcoded.
            "RespawnEnabled", "RespawnDelayTicks",
            // The server validates this against XenoNpcRole before applying it.
            "Role"
    );

    private XenoNpcDataSavePolicy() {
    }

    public static Set<String> editableKeys() {
        return EDITABLE_KEYS;
    }

    /**
     * Whether this payload may be applied.
     *
     * <p>Range checking is deliberately <em>not</em> done here. Every one of these keys lands in a
     * {@code XenoNpcData} setter that already clamps — {@code setHome} drops a non-finite
     * coordinate and clamps to the world border, {@code setLeashRadius} to 0–512,
     * {@code setRespawnDelayTicks} to 1–72000. Duplicating those bounds here would give two places
     * to keep in agreement, and the setter is the one that cannot be bypassed.
     */
    public static XenoNpcSavePolicy.Validation validate(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return XenoNpcSavePolicy.Validation.accept();
        }
        if (tag.size() > MAX_KEYS) {
            return XenoNpcSavePolicy.Validation.reject("too many NPC data fields");
        }
        for (String key : tag.getAllKeys()) {
            if (!EDITABLE_KEYS.contains(key)) {
                return XenoNpcSavePolicy.Validation.reject("field is not editor-owned: " + key);
            }
        }
        if (tag.contains("Role") && (!tag.contains("Role", net.minecraft.nbt.Tag.TAG_STRING)
                || !isRoleId(tag.getString("Role")))) {
            return XenoNpcSavePolicy.Validation.reject("invalid NPC role");
        }
        return XenoNpcSavePolicy.Validation.accept();
    }

    private static boolean isRoleId(String id) {
        if (id == null) return false;
        for (var role : net.bullettrain.xenopixelsmod.npc.XenoNpcRole.values()) {
            if (role.id().equals(id)) return true;
        }
        return false;
    }

    /**
     * Lays an accepted payload over the NPC's current data tag.
     *
     * <p>A save is a subset — only what the editor changed — so anything the payload does not
     * mention has to survive untouched.
     */
    public static CompoundTag merge(CompoundTag current, CompoundTag change) {
        CompoundTag merged = current == null ? new CompoundTag() : current.copy();
        if (change == null) {
            return merged;
        }
        for (String key : change.getAllKeys()) {
            if (EDITABLE_KEYS.contains(key)) {
                merged.put(key, change.get(key).copy());
            }
        }
        return merged;
    }
}
