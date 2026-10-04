package net.bullettrain.xenopixelsmod.combat.combo;

/** What the live ticker should do this step. Minecraft-free. */
public record ComboRouteDecision(
        ComboRoutePhase next,
        int hitIndex,
        boolean spendHit,
        boolean applyKnockback,
        boolean startChase,
        boolean finish) {

    static ComboRouteDecision to(ComboRoutePhase next) {
        return new ComboRouteDecision(next, -1, false, false, false, next == ComboRoutePhase.IDLE);
    }

    static ComboRouteDecision hit(ComboRoutePhase next, int hitIndex) {
        return new ComboRouteDecision(next, hitIndex, true, false, false, false);
    }

    static ComboRouteDecision knockback(ComboRoutePhase next, boolean chase) {
        return new ComboRouteDecision(next, -1, false, true, chase, false);
    }

    static ComboRouteDecision idle() {
        return new ComboRouteDecision(ComboRoutePhase.IDLE, -1, false, false, false, true);
    }
}
