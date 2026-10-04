package net.bullettrain.xenopixelsmod.combat.combo;

/** Live per-player combo-route session. Ticked by {@link ComboRouteMachine}. */
public final class ComboRouteState {
    public final ComboRoute route;
    public final int targetEntityId;
    public ComboRoutePhase phase = ComboRoutePhase.APPROACH;
    public int hitsLanded;
    public int reapproachesDone;
    public int nextActionTick;
    public int ticksInPhase;
    public int cooldownUntilTick;

    public ComboRouteState(ComboRoute route, int targetEntityId, int nextActionTick, int cooldownUntilTick) {
        this.route = route;
        this.targetEntityId = targetEntityId;
        this.nextActionTick = nextActionTick;
        this.cooldownUntilTick = cooldownUntilTick;
    }
}
