package net.bullettrain.xenopixelsmod.npc.dialog;

/** Admission to a server-authored native quest binding; never grants progress or rewards. */
public final class DmzNpcQuestPolicy {
    private DmzNpcQuestPolicy() {}

    public static boolean validNpcId(String id) {
        return id != null && !id.isBlank() && id.length() <= 128
                && id.matches("[a-zA-Z0-9_.:/-]+");
    }

    public static boolean mayOpen(boolean playerAlive, boolean hostAlive, boolean sameLevel,
                                  boolean loadPending, double distanceSquared, String npcId) {
        return playerAlive && hostAlive && sameLevel && !loadPending
                && Double.isFinite(distanceSquared) && distanceSquared >= 0
                && distanceSquared <= 64 && validNpcId(npcId);
    }
}
