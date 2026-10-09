package net.bullettrain.xenopixelsmod.client.combat.v3;

import java.util.List;
import net.bullettrain.xenopixelsmod.client.combat.v2.PromptPlate;
import net.bullettrain.xenopixelsmod.combat.v2.V2ChargeRules;
import net.bullettrain.xenopixelsmod.combat.v3.V3State;
import net.bullettrain.xenopixelsmod.combat.v3.V3Window;

/**
 * Which battle indicators a V3 fighter sees. Minecraft-free.
 *
 * <p>Everything here is read from the server's state packet: the lock, the charge and the one
 * timed window. Nothing is guessed from the local buttons, so a prompt never offers a move the
 * server has not opened.
 */
public final class V3Prompts {
    private V3Prompts() {}

    /** The input a prompt is bound to; the overlay turns it into the player's actual key name. */
    public enum Slot { NONE, LIGHT, HEAVY, DASH, GRAB, CHASE, VANISH }

    public record Prompt(PromptPlate plate, Slot slot, String label) {}

    /** Prompts left to right, and 0..1 for the shared timer bar (0 draws none). */
    public record Row(List<Prompt> prompts, float fraction) {
        static final Row EMPTY = new Row(List.of(), 0f);
    }

    private static final Prompt ATTACK = new Prompt(PromptPlate.PUNCH, Slot.LIGHT, "Attack");
    private static final Prompt HEAVY = new Prompt(PromptPlate.FINISH, Slot.HEAVY, "Heavy");
    private static final Prompt DASH = new Prompt(PromptPlate.TRAVEL, Slot.DASH, "Dash");
    /** Follow presses only reposition behind the target (owner design 2026-10-08); the label says so. */
    private static final Prompt CROSS = new Prompt(PromptPlate.TRAVEL, Slot.DASH, "Behind");
    private static final Prompt GRAB = new Prompt(PromptPlate.GRAB, Slot.GRAB, "Grab");

    public static Row row(boolean locked, V3State state, int chargeTicks, int windowTicksLeft, int windowTicksTotal,
                          V3Window window) {
        if (state == null) return Row.EMPTY;
        float timer = windowTicksLeft > 0 && windowTicksTotal > 0
                ? Math.clamp(windowTicksLeft / (float) windowTicksTotal, 0f, 1f) : 0f;
        V3Window open = timer > 0f && window != null ? window : V3Window.NONE;
        // A grab holds both fighters whether or not either still has a lock.
        if (state == V3State.GRABBED) {
            return open == V3Window.GRAB
                    ? new Row(List.of(new Prompt(PromptPlate.ALERT, Slot.LIGHT, "Break free")), timer)
                    : new Row(List.of(new Prompt(PromptPlate.ALERT, Slot.NONE, "Grabbed")), 0f);
        }
        if (state == V3State.GRAB_HOLD) {
            return new Row(List.of(new Prompt(PromptPlate.GRAB, Slot.NONE, "Throw")), open == V3Window.GRAB ? timer : 0f);
        }
        if (!locked) return Row.EMPTY;
        return switch (state) {
            case CHARGING_PUNCH -> new Row(List.of(new Prompt(PromptPlate.FINISH, Slot.LIGHT, "Release punch")),
                    chargeFraction(chargeTicks));
            case CHARGING_KICK -> new Row(List.of(new Prompt(PromptPlate.FINISH, Slot.HEAVY, "Release kick")),
                    chargeFraction(chargeTicks));
            case TRAVEL -> new Row(List.of(CROSS), 0f);
            case IDLE -> switch (open) {
                case COUNTER -> new Row(List.of(new Prompt(PromptPlate.ALERT, Slot.VANISH, "Counter")), timer);
                case CHASE -> new Row(List.of(new Prompt(PromptPlate.TRAVEL, Slot.CHASE, "Chase")), timer);
                case DASH_CROSS -> new Row(List.of(CROSS, ATTACK, HEAVY), timer);
                default -> new Row(List.of(ATTACK, HEAVY, DASH, GRAB), 0f);
            };
            default -> Row.EMPTY;
        };
    }

    private static float chargeFraction(int chargeTicks) {
        return Math.max(0.02f, V2ChargeRules.progress(chargeTicks));
    }
}
