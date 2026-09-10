package net.bullettrain.xenopixelsmod.client.pad;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.input.GamepadInputs;
import dev.isxander.controlify.bindings.input.Input;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * Raw controller state, read straight off the hardware rather than through a Controlify binding.
 *
 * <p>Used for the things a binding cannot answer: analogue stick values for the pilot seat, and
 * the "is this physical input spoken for by BT3" question the input filter asks. It is also the
 * body of the optional {@code padRawPolling} path in {@link XenoPadBinds#held}.
 *
 * <p><b>These reads carry no chord gate and no remapping.</b> {@link #meleePressed()} is "is the
 * west face button down", nothing more, so it answers true while the left trigger is held and the
 * button really means Ultimate. Anything that needs a move's real meaning must go through
 * {@link XenoPadBinds#held}, which applies {@link PadChords}.
 */
public final class Bt3ControllerInput {
    private static final Set<ResourceLocation> BT3_PHYSICAL_INPUTS = new HashSet<>();

    static {
        // Face buttons
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.WEST_BUTTON);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.SOUTH_BUTTON);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.EAST_BUTTON);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.NORTH_BUTTON);
        // Shoulders / triggers
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.LEFT_SHOULDER_BUTTON);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.RIGHT_SHOULDER_BUTTON);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.LEFT_TRIGGER_AXIS);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.RIGHT_TRIGGER_AXIS);
        // Sticks
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.LEFT_STICK_BUTTON);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.RIGHT_STICK_BUTTON);
        // D-pad
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.DPAD_UP_BUTTON);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.DPAD_DOWN_BUTTON);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.DPAD_LEFT_BUTTON);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.DPAD_RIGHT_BUTTON);
        // Others
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.BACK_BUTTON);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.START_BUTTON);
        // Axis inputs for sticks
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.LEFT_STICK_AXIS_UP);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.LEFT_STICK_AXIS_DOWN);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.LEFT_STICK_AXIS_LEFT);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.LEFT_STICK_AXIS_RIGHT);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.RIGHT_STICK_AXIS_UP);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.RIGHT_STICK_AXIS_DOWN);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.RIGHT_STICK_AXIS_LEFT);
        BT3_PHYSICAL_INPUTS.add(GamepadInputs.RIGHT_STICK_AXIS_RIGHT);
    }

    private Bt3ControllerInput() {}

    private static ControllerEntity controller() {
        return ControlifyApi.get().getCurrentController().orElse(null);
    }

    private static float state(ControllerEntity controller, ResourceLocation input) {
        if (controller == null || input == null) return 0f;
        Input binding = GamepadInputs.getBind(input);
        if (binding == null) return 0f;
        return controller.input()
                .map(component -> binding.state(component.stateNow()))
                .orElse(0f);
    }

    public static boolean isButtonPressed(ResourceLocation input) {
        return state(controller(), input) > 0.5f;
    }

    public static float getAxis(ResourceLocation input) {
        return state(controller(), input);
    }

    // Convenience helpers for specific BT3 actions. Ungated -- see the class note.
    public static boolean meleePressed() { return isButtonPressed(GamepadInputs.WEST_BUTTON); }
    public static boolean dashPressed() { return isButtonPressed(GamepadInputs.SOUTH_BUTTON); }
    public static boolean guardPressed() { return isButtonPressed(GamepadInputs.EAST_BUTTON); }
    public static boolean kiBlastPressed() { return isButtonPressed(GamepadInputs.NORTH_BUTTON); }

    public static boolean chargeHeld() { return getAxis(GamepadInputs.LEFT_TRIGGER_AXIS) > 0.5f; }
    public static boolean lockHeld() { return isButtonPressed(GamepadInputs.LEFT_SHOULDER_BUTTON); }
    public static boolean descendPressed() { return getAxis(GamepadInputs.RIGHT_TRIGGER_AXIS) > 0.5f; }

    // Axis readings
    public static float leftStickX() { return getAxis(GamepadInputs.LEFT_STICK_AXIS_RIGHT) - getAxis(GamepadInputs.LEFT_STICK_AXIS_LEFT); }
    public static float leftStickY() { return getAxis(GamepadInputs.LEFT_STICK_AXIS_UP) - getAxis(GamepadInputs.LEFT_STICK_AXIS_DOWN); }

    public static float flightPitch() { return leftStickY(); }
    public static float flightRoll() { return leftStickX(); }

    /**
     * Whether a physical input is one the BT3 layout owns, so a built-in Controlify action bound to
     * it can be withheld.
     *
     * <p>Takes a physical input id -- {@code controlify:button/south}, {@code controlify:axis/
     * left_trigger} -- not a binding id. The exemptions for the radial menu and for Controlify's
     * vanilla movement bindings are decided by {@link XenoPadBinds#conflicts}, which is the caller
     * and the only side that holds the binding's own id; testing them here could never match.
     */
    public static boolean conflicts(ResourceLocation physicalInput) {
        return physicalInput != null && BT3_PHYSICAL_INPUTS.contains(physicalInput);
    }
}
