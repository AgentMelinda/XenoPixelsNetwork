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

    /**
     * Which integration actually registered, decided once in Controlify's pre-init.
     *
     * <p>Not {@code XenoClientConfig.padRewrite} read live: the two packages declare the same
     * binding ids, so only one of them ever registers, and a player flipping the config mid-session
     * would otherwise start asking a package that holds no bindings - which reads as the pad going
     * dead rather than as a setting needing a restart.
     */
    private static boolean rewrite;

    private XenoPadInput() {
    }

    /** Called by {@link XenoControlifyEntrypoint} with the decision it just acted on. */
    static void useRewrite(boolean value) {
        rewrite = value;
    }

    /**
     * Whether the rewritten integration is the one holding the bindings.
     *
     * <p>Public because the Controlify input mixin has to route its conflict check the same way,
     * and it lives in another package. Controlify-free on purpose, so asking the question does not
     * drag Controlify onto the stack.
     */
    public static boolean usingRewrite() {
        return rewrite;
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
        return available() && (rewrite
                ? net.bullettrain.xenopixelsmod.client.pad2.PadBinds.held(mapping)
                : XenoPadBinds.held(mapping));
    }

    /** Advances mode/radial/gesture state once per client tick. */
    public static void tick() {
        if (!available()) return;
        if (rewrite) {
            net.bullettrain.xenopixelsmod.client.pad2.PadBinds.tick();
        } else {
            XenoPadBinds.tick();
        }
    }

    /** One-shot BT3 guard-stick vanish direction: -1 left, +1 right, 0 none. */
    public static int consumeVanishSide() {
        if (!available()) return 0;
        return rewrite
                ? net.bullettrain.xenopixelsmod.client.pad2.PadBinds.consumeVanishSide()
                : XenoPadBinds.consumeVanishSide();
    }

    /** Whether BT3's Y ki-blast chord must withhold vanilla item use/place. */
    public static boolean suppressesUseItem() {
        return available() && (rewrite
                ? net.bullettrain.xenopixelsmod.client.pad2.PadBinds.suppressesUseItem()
                : XenoPadBinds.suppressesUseItem());
    }

    /** Whether BT3's A dash must withhold Controlify's normal jump. */
    public static boolean suppressesJump() {
        return available() && (rewrite
                ? net.bullettrain.xenopixelsmod.client.pad2.PadBinds.suppressesJump()
                : XenoPadBinds.suppressesJump());
    }

    /** Whether BT3's B guard must withhold Controlify's normal sneak. */
    public static boolean suppressesSneak() {
        return available() && (rewrite
                ? net.bullettrain.xenopixelsmod.client.pad2.PadBinds.suppressesSneak()
                : XenoPadBinds.suppressesSneak());
    }

    /** Whether BT3's RT descend must withhold Controlify's normal attack. */
    public static boolean suppressesAttack() {
        return available() && (rewrite
                ? net.bullettrain.xenopixelsmod.client.pad2.PadBinds.suppressesAttack()
                : XenoPadBinds.suppressesAttack());
    }

    /** Whether BT3's L3 flight-mode toggle must withhold Controlify's normal sprint toggle. */
    public static boolean suppressesSprint() {
        return available() && (rewrite
                ? net.bullettrain.xenopixelsmod.client.pad2.PadBinds.suppressesSprint()
                : XenoPadBinds.suppressesSprint());
    }

    /** True when a controller is the active input device, not merely connected. */
    public static boolean controllerActive() {
        return available() && (rewrite
                ? net.bullettrain.xenopixelsmod.client.pad2.PadBinds.controllerActive()
                : XenoPadBinds.controllerActive());
    }

    /** True only while an active controller is using the persisted BT3 gameplay layer. */
    public static boolean bt3ModeActive() {
        return available() && XenoClientConfig.padMode == PadMode.BT3 && (rewrite
                ? net.bullettrain.xenopixelsmod.client.pad2.PadBinds.controllerActive()
                : XenoPadBinds.controllerActive());
    }

    /**
     * Which ki technique bar a held trigger is raising: 0 for slots 1-4, 4 for 5-8, -1 for none.
     *
     * <p>The keyboard raises these by holding Alt or Ctrl, which a gamepad cannot reproduce -
     * DragonMineZ reads that chord straight off GLFW. So the pad raises the bar itself, and the
     * HUD asks here instead of only asking {@code KeyBinds.isBarModifierActive}.
     *
     * <p>Returns -1 whenever the rewrite is not the live integration, because the old package has
     * no ki-bar concept and answering otherwise would show a bar nothing could drive.
     */
    public static int kiBarOffset() {
        return available() && rewrite
                ? net.bullettrain.xenopixelsmod.client.pad2.PadKiMenu.barOffset() : -1;
    }

    /** Left stick pitch for the pilot seat, {@code -1..1}, positive nose-up; 0 with no pad. */
    public static float flightPitch() {
        if (!available()) return 0f;
        return rewrite
                ? net.bullettrain.xenopixelsmod.client.pad2.PadBinds.flightPitch()
                : XenoPadBinds.flightPitch();
    }

    /** Left stick roll for the pilot seat, {@code -1..1}, positive to the right; 0 with no pad. */
    public static float flightRoll() {
        if (!available()) return 0f;
        return rewrite
                ? net.bullettrain.xenopixelsmod.client.pad2.PadBinds.flightRoll()
                : XenoPadBinds.flightRoll();
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
