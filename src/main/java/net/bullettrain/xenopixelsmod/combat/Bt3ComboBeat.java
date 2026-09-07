package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;

/**
 * Server-owned meaning of a combo beat. The client supplies only input style; this descriptor is
 * derived from the authoritative step and the selected style.
 */
public record Bt3ComboBeat(
        Bt3AnimationIntent intent,
        boolean finisher,
        boolean launcher,
        boolean chase,
        boolean guardBreak) {

    public static Bt3ComboBeat resolve(Bt3AnimationIntent intent, int step, int mashStyle,
                                       int verticalBias) {
        boolean launcher = verticalBias > 0 || (mashStyle == Bt3ComboChoreography.MASH_STYLE_ROUTE
                && step == 3 && intent.isKick());
        boolean directionalFinisher = mashStyle != Bt3ComboChoreography.MASH_STYLE_ROUTE
                && step > 0 && step % 4 == 0;
        boolean finisher = step == 4 || directionalFinisher;
        boolean chase = launcher || finisher;
        boolean guardBreak = finisher;
        return new Bt3ComboBeat(intent, finisher, launcher, chase, guardBreak);
    }
}
