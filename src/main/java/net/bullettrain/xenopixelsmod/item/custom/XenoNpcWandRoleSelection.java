package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.npc.XenoNpcRole;
import net.minecraft.nbt.CompoundTag;

/** One-spawn role selection stored on the Xeno NPC wand. */
final class XenoNpcWandRoleSelection {
    static final String ROLE_KEY = "XenoNpcRole";
    private static final String SELECTED_KEY = "XenoNpcRoleSelected";

    private XenoNpcWandRoleSelection() {
    }

    static XenoNpcRole current(CompoundTag tag) {
        if (tag == null || !tag.getBoolean(SELECTED_KEY)) {
            return XenoNpcRole.HUMANOID;
        }
        return XenoNpcRole.byId(tag.getString(ROLE_KEY));
    }

    static void select(CompoundTag tag, XenoNpcRole role) {
        if (tag == null) return;
        XenoNpcRole next = role == null ? XenoNpcRole.HUMANOID : role;
        tag.putString(ROLE_KEY, next.id());
        tag.putBoolean(SELECTED_KEY, true);
    }

    static XenoNpcRole consume(CompoundTag tag) {
        XenoNpcRole selected = current(tag);
        clear(tag);
        return selected;
    }

    static void clear(CompoundTag tag) {
        if (tag == null) return;
        tag.remove(ROLE_KEY);
        tag.remove(SELECTED_KEY);
    }
}
