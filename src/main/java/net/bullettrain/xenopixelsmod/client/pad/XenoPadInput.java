package net.bullettrain.xenopixelsmod.client.pad;

import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.KeyMapping;
import net.neoforged.fml.ModList;

/**
 * The gamepad, as the rest of the mod sees it.
 *
 * <p>Nothing outside this package may name a Controlify type, because Controlify is optional and a
 * class that mentions a missing type fails to verify the moment it is touched. Every signature
 * here is vanilla, and every method answers safely — {@code false}, {@code 0} — when Controlify is
 * not installed, no controller is connected, or the player has switched pad support off. Callers
 * do not have to ask which.
 *
 * <p>The indirection is load-bearing: the {@link XenoPadBinds} calls below sit behind a runtime
 * branch, so the JVM only ever resolves that class, and through it Controlify's, when the branch
 * is actually taken.
 */
public final class XenoPadInput {

    private static Boolean controlifyPresent;

    private XenoPadInput() {
    }

    /**
     * Whether pad input should be consulted at all.
     *
     * <p>Checked on every call rather than cached, because the config flag can be turned off mid
     * session and the answer has to follow it immediately.
     */
    private static boolean available() {
        if (!XenoClientConfig.padEnabled) return false;
        if (controlifyPresent == null) {
            // ModList is not populated during early class loading, so this is resolved on first
            // use rather than in a static initialiser.
            controlifyPresent = ModList.get() != null && ModList.get().isLoaded("controlify");
        }
        return controlifyPresent;
    }

    /**
     * Whether the pad is pressing {@code mapping} right now.
     *
     * <p>Combat input in this mod is read from the hardware through GLFW rather than through
     * {@link KeyMapping#isDown()} — see the reasoning above {@code Bt3CombatClient.heldNow}. A pad
     * moves no physical key, so those readers OR this in; without it a controller would work for
     * every binding that goes through the vanilla key path and silently do nothing for guard, the
     * fist combo, charge, chase, Hakai, beam surge and ki guidance.
     *
     * <p>This <em>adds</em> a source. It never suppresses the keyboard, so both work at once and a
     * player can keep a hand on each.
     */
    public static boolean held(KeyMapping mapping) {
        return available() && XenoPadBinds.held(mapping);
    }

    /** Advances mode/radial/gesture state once per client tick. */
    public static void tick() {
        if (available()) XenoPadBinds.tick();
    }

    /** One-shot BT3 guard-stick vanish direction: -1 left, +1 right, 0 none. */
    public static int consumeVanishSide() {
        return available() ? XenoPadBinds.consumeVanishSide() : 0;
    }

    /** Whether BT3's Y ki-blast chord must withhold vanilla item use/place. */
    public static boolean suppressesUseItem() {
        return available() && XenoPadBinds.suppressesUseItem();
    }

    /** Whether BT3's A dash must withhold Controlify's normal jump. */
    public static boolean suppressesJump() {
        return available() && XenoPadBinds.suppressesJump();
    }

    /** Whether BT3's B guard must withhold Controlify's normal sneak. */
    public static boolean suppressesSneak() {
        return available() && XenoPadBinds.suppressesSneak();
    }

    /** Whether BT3's RT descend must withhold Controlify's normal attack. */
    public static boolean suppressesAttack() {
        return available() && XenoPadBinds.suppressesAttack();
    }

    /** Whether BT3's L3 flight-mode toggle must withhold Controlify's normal sprint toggle. */
    public static boolean suppressesSprint() {
        return available() && XenoPadBinds.suppressesSprint();
    }

    /** True when a controller is the active input device, not merely connected. */
    public static boolean controllerActive() {
        return available() && XenoPadBinds.controllerActive();
    }

    /** True only while an active controller is using the persisted BT3 gameplay layer. */
    public static boolean bt3ModeActive() {
        return available() && XenoClientConfig.padMode == PadMode.BT3
                && XenoPadBinds.controllerActive();
    }

    /** Left stick pitch for the pilot seat, {@code -1..1}, positive nose-up; 0 with no pad. */
    public static float flightPitch() {
        return available() ? XenoPadBinds.flightPitch() : 0f;
    }

    /** Left stick roll for the pilot seat, {@code -1..1}, positive to the right; 0 with no pad. */
    public static float flightRoll() {
        return available() ? XenoPadBinds.flightRoll() : 0f;
    }

    /**
     * Buzz the controller for a combat impact. Does nothing without a pad.
     *
     * @param strength 0..1, already faded for distance by the caller
     * @param ticks    how long the buzz lasts
     */
    public static void rumbleImpact(float strength, int ticks) {
        if (!available()) return;
        XenoPadRumble.impact(strength, ticks);
    }
}
