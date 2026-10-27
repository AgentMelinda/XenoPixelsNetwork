package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.handler.data.IAvailability;

/**
 * The availability of a mark, which natively has none.
 *
 * <p>Marks hang over the NPC for everyone; CustomNPCs gates them per player. A mark cannot take that
 * condition from a script either, because nothing renders it filters by it - so reading it as
 * "available to all" and refusing the setters is the honest answer. A caller who needs a condition on
 * a mark needs it on the dialog that offers the quest, where
 * {@link XenoNodeAvailability} does store one.
 */
final class XenoMarkAvailability implements IAvailability {
    static final XenoMarkAvailability INSTANCE = new XenoMarkAvailability();

    private XenoMarkAvailability() {}

    private static UnsupportedOperationException refused(String method) {
        return XenoApiAdapters.unsupported(method + " (a native mark is shown to everyone; gate the dialog that offers it)");
    }

    @Override public boolean isAvailable(IPlayer player) { return true; }
    @Override public int getDaytime() { return 0; }
    @Override public void setDaytime(int type) { throw refused("IAvailability.setDaytime"); }
    @Override public int getMinPlayerLevel() { return 0; }
    @Override public void setMinPlayerLevel(int level) { throw refused("IAvailability.setMinPlayerLevel"); }
    @Override public int getDialog(int i) { return -1; }
    @Override public void setDialog(int i, int id, int type) { throw refused("IAvailability.setDialog"); }
    @Override public void removeDialog(int i) { throw refused("IAvailability.removeDialog"); }
    @Override public int getQuest(int i) { return -1; }
    @Override public void setQuest(int i, int id, int type) { throw refused("IAvailability.setQuest"); }
    @Override public void removeQuest(int i) { throw refused("IAvailability.removeQuest"); }
    @Override public void setFaction(int i, int id, int type, int stance) { throw refused("IAvailability.setFaction"); }
    @Override public void removeFaction(int i) { throw refused("IAvailability.removeFaction"); }
    @Override public void setScoreboard(int i, String objective, int type, int value) { throw refused("IAvailability.setScoreboard"); }
}
