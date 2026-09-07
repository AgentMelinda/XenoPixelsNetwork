package net.bullettrain.xenopixelsmod.client.combat.anim;

import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Client end of {@code Bt3AnimIntentPacket}: plays a server-confirmed combat beat on whichever
 * player performed it.
 *
 * <p>The attacking client predicts its own beat immediately so a swing has no perceptible delay,
 * and the server broadcasts the beat it actually accepted. Those normally agree — both run the
 * same pure {@code Bt3ComboChoreography} function — but they can diverge when the server rejects
 * or throttles a swing and its combo step moves differently from the client's. So the confirmation
 * is applied to the local player only when it disagrees with what was just predicted; a matching
 * confirmation is dropped rather than replaying the same animation twice.
 */
public final class Bt3AnimIntentClient {

    /** How long a local prediction suppresses a matching confirmation, in client ticks. */
    private static final int PREDICTION_WINDOW_TICKS = 10;
    /**
     * A confirmation this soon after the last locally played beat is dropped even when it
     * disagrees. Correcting mid-swing restarts the clip from its wind-up, which reads as a stutter
     * — worse than one beat showing the pose the client guessed. Outside this window the
     * correction still runs, which is the case that matters: the client predicted nothing, or the
     * string genuinely diverged and every following beat would be wrong too.
     */
    private static final int MIN_RESTART_GAP_TICKS = 3;

    private static Bt3AnimationIntent predictedIntent;
    private static int predictedAtTick = Integer.MIN_VALUE;

    private Bt3AnimIntentClient() {
    }

    /**
     * Plays a beat on the local player right away and remembers it, so the server's confirmation
     * of the same beat does not play it a second time.
     */
    public static void predictLocal(Player player, Bt3AnimationIntent intent) {
        if (player == null || intent == null) {
            return;
        }
        predictedIntent = intent;
        predictedAtTick = player.tickCount;
        Bt3PalAnimator.play(player, intent);
    }

    /** Applies a server-confirmed beat to the player with the given entity id. */
    public static void apply(int entityId, Bt3AnimationIntent intent) {
        if (intent == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof Player player)) {
            return;
        }
        if (player == mc.player
                && (isFreshMatchingPrediction(player, intent) || isMidSwing(player))) {
            return;
        }
        Bt3PalAnimator.play(player, intent);
    }

    /** Clears the prediction, so a new string is never reconciled against a stale beat. */
    public static void clearPrediction() {
        predictedIntent = null;
        predictedAtTick = Integer.MIN_VALUE;
    }

    private static boolean isFreshMatchingPrediction(Player player, Bt3AnimationIntent intent) {
        return predictedIntent == intent
                && player.tickCount - predictedAtTick <= PREDICTION_WINDOW_TICKS;
    }

    /** True while the beat the client just played is still too young to interrupt. */
    private static boolean isMidSwing(Player player) {
        return predictedIntent != null
                && player.tickCount - predictedAtTick < MIN_RESTART_GAP_TICKS;
    }
}
