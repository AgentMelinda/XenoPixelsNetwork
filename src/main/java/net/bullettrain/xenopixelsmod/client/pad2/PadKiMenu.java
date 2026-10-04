package net.bullettrain.xenopixelsmod.client.pad2;

import com.dragonminez.common.network.C2S.SelectTechniqueSlotC2S;
import com.dragonminez.common.network.C2S.TechniqueChargeC2S;
import com.dragonminez.common.network.NetworkHandler;
import dev.isxander.controlify.controller.input.GamepadInputs;
import net.bullettrain.xenopixelsmod.client.combat.ClientLockState;
import net.bullettrain.xenopixelsmod.client.pad.Bt3ControllerInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Hold a trigger to raise a ki technique bar; press a d-pad direction to charge one.
 *
 * <p>On the keyboard DragonMineZ raises these bars by holding Alt or Ctrl and fires a slot with a
 * number key. <b>A gamepad cannot reproduce that chord.</b> Both halves of
 * {@code KeyBinds.isChordDown} read raw GLFW — {@code isBarModifierActive} goes through
 * {@code KeyModifier.isActive()} to {@code Screen.hasAltDown()}, and {@code isPhysicallyDown} calls
 * {@code InputConstants.isKeyDown(window, key)} whenever the slot has a key bound. Controlify's key
 * emulation sets {@code isDown} and bumps {@code clickCount}; it never touches the physical
 * keyboard state those two read. So emulating the technique keys does nothing at all, and this
 * class goes around them instead.
 *
 * <p>It uses DragonMineZ's own packets, which is what its GUI uses and therefore the supported
 * route: {@code SelectTechniqueSlotC2S} to choose the slot, {@code TechniqueChargeC2S.start} to
 * begin, {@code setHolding(true)} each tick to keep charging — this is the overcharge — and
 * {@code setHolding(false)} to let go.
 *
 * <p>The bar itself is drawn by {@code XenoTechniqueHotbarOverlay}, which is this mod's own HUD and
 * asks {@link #barOffset()} whether a trigger is raising it.
 */
public final class PadKiMenu {

    /** Slots per bar. DragonMineZ ships eight in two bars of four. */
    public static final int SLOTS_PER_BAR = 4;

    /** The left trigger's bar: technique slots 1-4. */
    public static final int BAR_LEFT = 0;

    /** The right trigger's bar: technique slots 5-8. */
    public static final int BAR_RIGHT = 4;

    /** No trigger held, so no bar. */
    public static final int NO_BAR = -1;

    /** Past halfway counts as held, matching {@code Bt3ControllerInput}'s own trigger reads. */
    private static int barOffset = NO_BAR;

    /** The slot currently charging, 0-based across both bars, or -1. */
    private static int chargingSlot = -1;

    /** So a direction has to be released and pressed again before it charges a second technique. */
    private static boolean dpadLatched;

    private PadKiMenu() {
    }

    /**
     * Which bar a trigger is raising: {@link #BAR_LEFT}, {@link #BAR_RIGHT} or {@link #NO_BAR}.
     *
     * <p>Read by the HUD overlay, which is why this is a plain int rather than anything that would
     * drag Controlify onto the HUD's classpath.
     */
    public static int barOffset() {
        return barOffset;
    }

    /** Whether a technique is charging right now, for the HUD to show its meter. */
    public static boolean charging() {
        return chargingSlot >= 0;
    }

    /**
     * One client tick.
     *
     * <p>Called from {@link PadBinds#tick()}, which has already established that a controller is
     * connected, BT3 mode is on and no screen is open.
     */
    static void tick(LocalPlayer player) {
        boolean left = Bt3ControllerInput.chargeHeld();
        boolean right = Bt3ControllerInput.descendPressed();
        // Left wins when both are held, matching PadChords' own precedence: the left trigger is
        // the one a player holds for seconds at a time, so resolving in its favour makes the
        // ambiguous case predictable rather than order-dependent.
        int wanted = left ? BAR_LEFT : right ? BAR_RIGHT : NO_BAR;

        if (wanted != barOffset) {
            // Letting go of the trigger releases whatever was charging - that is the shot.
            release(player);
            barOffset = wanted;
            dpadLatched = false;
            return;
        }
        if (barOffset == NO_BAR) {
            return;
        }

        int direction = dpadDirection();
        if (direction < 0) {
            dpadLatched = false;
        } else if (!dpadLatched && chargingSlot < 0) {
            dpadLatched = true;
            begin(player, barOffset + direction);
        }

        if (chargingSlot >= 0) {
            // Held down, so DragonMineZ keeps charging it. This is the overcharge: the longer the
            // trigger stays down, the further the technique winds up.
            send(TechniqueChargeC2S.setHolding(true, aim(player)));
        }
    }

    /** Selects a slot and starts charging it. */
    private static void begin(LocalPlayer player, int slot) {
        Entity target = ClientLockState.resolveTarget();
        // -1 is DragonMineZ's own "no target" value; its GUI passes the same when nothing is
        // locked, and the aim vector is what steers the shot in that case.
        int targetId = target == null ? -1 : target.getId();
        send(new SelectTechniqueSlotC2S(slot));
        send(TechniqueChargeC2S.start(slot, targetId, aim(player)));
        chargingSlot = slot;
    }

    /** Lets go, which fires whatever was charged. Safe to call when nothing is charging. */
    private static void release(LocalPlayer player) {
        if (chargingSlot < 0) {
            return;
        }
        send(TechniqueChargeC2S.setHolding(false, aim(player)));
        chargingSlot = -1;
    }

    /**
     * Clears everything without firing.
     *
     * <p>For leaving BT3 mode, opening a screen or losing the controller: the charge is abandoned
     * rather than released, because none of those are a player choosing to shoot.
     */
    static void reset() {
        barOffset = NO_BAR;
        chargingSlot = -1;
        dpadLatched = false;
    }

    /** 0 up, 1 right, 2 down, 3 left, or -1. Ordered so the bars read clockwise. */
    private static int dpadDirection() {
        if (Bt3ControllerInput.isButtonPressed(GamepadInputs.DPAD_UP_BUTTON)) {
            return 0;
        }
        if (Bt3ControllerInput.isButtonPressed(GamepadInputs.DPAD_RIGHT_BUTTON)) {
            return 1;
        }
        if (Bt3ControllerInput.isButtonPressed(GamepadInputs.DPAD_DOWN_BUTTON)) {
            return 2;
        }
        if (Bt3ControllerInput.isButtonPressed(GamepadInputs.DPAD_LEFT_BUTTON)) {
            return 3;
        }
        return -1;
    }

    /**
     * Where the shot is pointed.
     *
     * <p>The look vector. DragonMineZ's own {@code getCameraAim} is private, but the camera is
     * where the player is looking, and a locked target is carried separately as an entity id.
     */
    private static Vec3 aim(LocalPlayer player) {
        return player == null ? Vec3.ZERO : player.getLookAngle();
    }

    private static void send(Object packet) {
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        NetworkHandler.sendToServer(packet);
    }
}
