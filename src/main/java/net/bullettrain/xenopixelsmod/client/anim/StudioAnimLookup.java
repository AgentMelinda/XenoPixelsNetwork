package net.bullettrain.xenopixelsmod.client.anim;

/**
 * Studio / library clips win over a shipped {@code combat.xeno_*} of the same name so an
 * in-game edit can play without rewriting {@code bt3_combat.animation.json}.
 */
public final class StudioAnimLookup {
    private StudioAnimLookup() {}

    public static <T> T preferStudio(String name, T studio, T gecko) {
        if (name != null && name.startsWith("combat.xeno_") && studio != null) {
            return studio;
        }
        return gecko;
    }
}
