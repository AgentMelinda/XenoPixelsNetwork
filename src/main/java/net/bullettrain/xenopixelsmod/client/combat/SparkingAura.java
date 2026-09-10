package net.bullettrain.xenopixelsmod.client.combat;

import net.minecraft.world.entity.Entity;

/**
 * The Sparking aura's colour, and which player is currently being drawn.
 *
 * <p>DragonMineZ resolves every aura colour through one static helper that is handed only the two
 * colour names and a blend factor — it has no idea whose aura it is building. The layer that calls
 * it does. So the layer records the player here for the length of its own render call, and the
 * colour override reads it back; without that, one player Sparking would tint everybody's aura.
 *
 * <p>Gold rather than blue: DragonMineZ already has blue auras and the resting ki bar is blue, so
 * gold is what actually reads as "this player is Sparking" at a glance.
 */
public final class SparkingAura {

    /** Gold, matching the HUD's Sparking bar so the two states read as one thing. */
    private static final float[] GOLD = {1.0f, 0.788f, 0.235f};

    private static Entity rendering;

    private SparkingAura() {
    }

    /**
     * Marks whose aura is being drawn. Cleared with {@code null} when the layer is finished, so a
     * colour resolved outside any layer — the GUI aura, for instance — never picks up a tint.
     */
    public static void setRendering(Entity entity) {
        rendering = entity;
    }

    /**
     * Whose aura is being drawn, or null outside any layer.
     *
     * <p>Exposed because the aura's size needs the same answer its colour does: DragonMineZ's sizing
     * method is static and is handed only stats, so anything that has to remember something between
     * frames per player has to be told who the player is.
     */
    public static Entity renderingEntity() {
        return rendering;
    }

    /** Whether the aura currently being drawn belongs to a Sparking player. */
    public static boolean shouldTint() {
        return rendering != null && SparkingClientState.isSparking(rendering);
    }

    /** A fresh copy each call: DMZ writes into the array it is given. */
    public static float[] color() {
        return new float[]{GOLD[0], GOLD[1], GOLD[2]};
    }
}
