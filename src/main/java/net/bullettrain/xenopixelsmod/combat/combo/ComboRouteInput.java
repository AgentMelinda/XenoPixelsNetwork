package net.bullettrain.xenopixelsmod.combat.combo;

/**
 * Snapshot the pure machine needs to pick the next phase.
 *
 * @param targetAlive        false cancels the route without a second cost
 * @param inRange            attacker has closed to melee
 * @param hitCount           3 or 4, truncates the authored string
 * @param maxReapproach      0 or 1
 * @param autoReapproach     config switch; also requires the route to allow it
 * @param hitsLanded         hits already spent in the <em>current</em> string
 * @param reapproachesDone   auto re-rushes already used this cast
 */
public record ComboRouteInput(
        boolean targetAlive,
        boolean inRange,
        int hitCount,
        int maxReapproach,
        boolean autoReapproach,
        int hitsLanded,
        int reapproachesDone) {

    public static ComboRouteInput start(int hitCount, int maxReapproach, boolean autoReapproach) {
        return new ComboRouteInput(true, false, hitCount, maxReapproach, autoReapproach, 0, 0);
    }

    public static ComboRouteInput inRange(int hitCount, int maxReapproach, boolean autoReapproach,
                                          int hitsLanded, int reapproachesDone) {
        return new ComboRouteInput(true, true, hitCount, maxReapproach, autoReapproach,
                hitsLanded, reapproachesDone);
    }

    public static ComboRouteInput lost(int hitCount, int maxReapproach, boolean autoReapproach) {
        return new ComboRouteInput(false, false, hitCount, maxReapproach, autoReapproach, 0, 0);
    }
}
