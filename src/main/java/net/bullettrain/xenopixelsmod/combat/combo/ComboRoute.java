package net.bullettrain.xenopixelsmod.combat.combo;

import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;

/**
 * Immutable definition of one grant-gated combo route.
 *
 * <p>Minecraft-free so the catalog and {@link ComboRouteMachine#decide} can be unit-tested
 * without a server. Hit count at runtime may truncate {@link #firstString()} / {@link #secondString()}.
 */
public record ComboRoute(
        String skillId,
        String strikeId,
        Bt3AnimationIntent[] firstString,
        KnockbackSpec finisherKnockback,
        boolean autoReapproach,
        boolean chaseAfterKnockback,
        Bt3AnimationIntent[] secondString,
        String[] beatStrikeIds) {
}
