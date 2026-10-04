package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.handler.data.IAvailability;

/**
 * A native dialog's availability. Native conversations have no availability of their own: what a
 * player may take is gated per quest (the quest's availability filters its options when shown), so
 * every dialog is available and reads as having no conditions. Setting a condition is refused,
 * naming the quest availability as the place it belongs.
 */
final class XenoDialogAvailability implements IAvailability {
    static final XenoDialogAvailability INSTANCE = new XenoDialogAvailability();

    private XenoDialogAvailability() {}

    private static UnsupportedOperationException refused(String method) {
        return XenoApiAdapters.unsupported(method + " (native dialogs have no availability; set it on the quest)");
    }

    @Override public boolean isAvailable(IPlayer player) { return true; }
    @Override public int getDaytime() { return 0; }
    @Override public void setDaytime(int type) { throw refused("IAvailability.setDaytime"); }
    @Override public int getMinPlayerLevel() { return 0; }
    @Override public void setMinPlayerLevel(int level) { throw refused("IAvailability.setMinPlayerLevel"); }
    @Override public int getDialog(int i) { return -1; }
    @Override public void setDialog(int i, int id, int type) { throw refused("IAvailability.setDialog"); }
    @Override public void removeDialog(int i) { }
    @Override public int getQuest(int i) { return -1; }
    @Override public void setQuest(int i, int id, int type) { throw refused("IAvailability.setQuest"); }
    @Override public void removeQuest(int i) { }
    @Override public void setFaction(int i, int id, int type, int stance) { throw refused("IAvailability.setFaction"); }
    @Override public void removeFaction(int i) { }
    @Override public void setScoreboard(int i, String objective, int type, int value) { throw refused("IAvailability.setScoreboard"); }
}
