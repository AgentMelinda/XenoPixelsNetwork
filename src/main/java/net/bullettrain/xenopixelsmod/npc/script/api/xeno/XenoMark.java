package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import xenoapi.npcs.api.constants.MarkType;
import xenoapi.npcs.api.entity.data.IMark;
import xenoapi.npcs.api.handler.data.IAvailability;

import java.util.List;

/**
 * The floating mark over a native NPC's head as XenoAPI's {@link IMark}. It is the editor's Mark
 * setting: each change is written to the NPC's profile at once, which syncs it to clients.
 */
final class XenoMark implements IMark {
    /** CustomNPCs' MarkType order, as the native icon names. */
    static final List<String> ICONS = List.of("", "question", "exclamation", "pointer", "skull", "cross", "star");

    private final XenoNpcEntity npc;

    XenoMark(XenoNpcEntity npc) {
        this.npc = npc;
    }

    /** Marks show to everyone natively. */
    @Override public IAvailability getAvailability() { return XenoDialogAvailability.INSTANCE; }
    @Override public int getColor() { return NpcCombatProfile.readCached(npc).markColor; }

    @Override
    public void setColor(int color) {
        XenoApiAdapters.requireServerThread(npc.level());
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        profile.markColor = color & 0xFFFFFF;
        profile.write(npc);
    }

    @Override
    public int getType() {
        int index = ICONS.indexOf(NpcCombatProfile.readCached(npc).markIcon);
        return index < 0 ? MarkType.NONE : index;
    }

    @Override
    public void setType(int type) {
        if (type < 0 || type >= ICONS.size()) throw new IllegalArgumentException("IMark.setType: type must be 0-" + (ICONS.size() - 1));
        XenoApiAdapters.requireServerThread(npc.level());
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        profile.markIcon = NpcCombatProfile.canonicalMarkIcon(ICONS.get(type));
        profile.write(npc);
    }

    /** Changes are written as they are made; this re-sends the NPC's profile. */
    @Override
    public void update() {
        XenoApiAdapters.requireServerThread(npc.level());
        NpcCombatProfile.read(npc).write(npc);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof XenoMark mark && mark.npc == npc;
    }

    @Override public int hashCode() { return System.identityHashCode(npc); }
}
