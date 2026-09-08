package net.bullettrain.xenopixelsmod.client.pad;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.input.GamepadInputs;
import dev.isxander.controlify.bindings.input.Input;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * Direct controller state reader for BT3 mode.
 * Replaces key emulation with raw button/axis queries.
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

    private static float state(ControllerEntity controller, ResourceLocation input, boolean previous) {
        if (controller == null || input == null) return 0f;
        Input binding = GamepadInputs.getBind(input);
        if (binding == null) return 0f;
        return controller.input()
                .map(component -> binding.state(previous ? component.stateThen() : component.stateNow()))
                .orElse(0f);
    }

    public static boolean isButtonPressed(ResourceLocation input) {
        return state(controller(), input, false) > 0.5f;
    }

    public static float getAxis(ResourceLocation input) {
        return state(controller(), input, false);
    }

    public static boolean isButtonJustPressed(ResourceLocation input) {
        ControllerEntity controller = controller();
        return state(controller, input, false) > 0.5f && state(controller, input, true) <= 0.5f;
    }

    public static boolean isButtonJustReleased(ResourceLocation input) {
        ControllerEntity controller = controller();
        return state(controller, input, false) <= 0.5f && state(controller, input, true) > 0.5f;
    }

    // Convenience helpers for specific BT3 actions
    public static boolean meleePressed() { return isButtonPressed(GamepadInputs.WEST_BUTTON); }
    public static boolean meleeJustPressed() { return isButtonJustPressed(GamepadInputs.WEST_BUTTON); }
    public static boolean dashPressed() { return isButtonPressed(GamepadInputs.SOUTH_BUTTON); }
    public static boolean dashJustPressed() { return isButtonJustPressed(GamepadInputs.SOUTH_BUTTON); }
    public static boolean guardPressed() { return isButtonPressed(GamepadInputs.EAST_BUTTON); }
    public static boolean guardJustPressed() { return isButtonJustPressed(GamepadInputs.EAST_BUTTON); }
    public static boolean kiBlastPressed() { return isButtonPressed(GamepadInputs.NORTH_BUTTON); }
    public static boolean kiBlastJustPressed() { return isButtonJustPressed(GamepadInputs.NORTH_BUTTON); }

    public static boolean chargeHeld() { return getAxis(GamepadInputs.LEFT_TRIGGER_AXIS) > 0.5f; }
    public static boolean lockHeld() { return isButtonPressed(GamepadInputs.LEFT_SHOULDER_BUTTON); }

    public static boolean ascendPressed() { return isButtonPressed(GamepadInputs.RIGHT_SHOULDER_BUTTON); }
    public static boolean descendPressed() { return getAxis(GamepadInputs.RIGHT_TRIGGER_AXIS) > 0.5f; }

    public static boolean transformPressed() { return isButtonPressed(GamepadInputs.RIGHT_STICK_BUTTON); }
    public static boolean statsMenuPressed() { return isButtonPressed(GamepadInputs.BACK_BUTTON); }
    public static boolean chasePressed() { return isButtonPressed(GamepadInputs.DPAD_UP_BUTTON); }
    public static boolean backstepPressed() { return isButtonPressed(GamepadInputs.DPAD_DOWN_BUTTON); }
    public static boolean sonicLeftPressed() { return isButtonPressed(GamepadInputs.DPAD_LEFT_BUTTON); }
    public static boolean sonicRightPressed() { return isButtonPressed(GamepadInputs.DPAD_RIGHT_BUTTON) || (lockHeld() && dashPressed()); }

    public static boolean zBurstPressed() { return chargeHeld() && dashPressed(); }
    public static boolean ultimatePressed() { return chargeHeld() && meleePressed(); }
    public static boolean sparkingPressed() { return chargeHeld() && guardPressed(); }
    public static boolean chargedKickPressed() { return chargeHeld() && kiBlastPressed(); }
    public static boolean zanzokenPressed() { return lockHeld() && meleePressed(); }
    public static boolean multiformPressed() { return lockHeld() && kiBlastPressed(); }
    public static boolean hakaiPressed() { return lockHeld() && guardPressed(); }

    public static boolean flightModeToggleJustPressed() { return isButtonJustPressed(GamepadInputs.LEFT_STICK_BUTTON); }

    // Axis readings
    public static float leftStickX() { return getAxis(GamepadInputs.LEFT_STICK_AXIS_RIGHT) - getAxis(GamepadInputs.LEFT_STICK_AXIS_LEFT); }
    public static float leftStickY() { return getAxis(GamepadInputs.LEFT_STICK_AXIS_UP) - getAxis(GamepadInputs.LEFT_STICK_AXIS_DOWN); }

    public static float flightPitch() { return leftStickY(); }
    public static float flightRoll() { return leftStickX(); }

    // Check if any BT3 physical input is currently down
    public static boolean anyBt3InputDown() {
        ControllerEntity controller = controller();
        if (controller == null) return false;
        for (ResourceLocation input : BT3_PHYSICAL_INPUTS) {
            if (state(controller, input, false) > 0.5f) return true;
        }
        return false;
    }

    // This can be used to suppress vanilla actions when BT3 owns a physical input
    public static boolean conflicts(ResourceLocation bindingInput) {
        if (bindingInput == null) return false;
        // Radial menu is exempt
        if (ResourceLocation.fromNamespaceAndPath("controlify", "radial_menu").equals(bindingInput)) {
            return false;
        }
        // Vanilla movement/camera bindings are never suppressed
        if (isControlifyVanillaBinding(bindingInput)) {
            return false;
        }
        return BT3_PHYSICAL_INPUTS.contains(bindingInput);
    }

    private static boolean isControlifyVanillaBinding(ResourceLocation id) {
        if (id == null || !"controlify".equals(id.getNamespace())) return false;
        String path = id.getPath();
        return "walk_forward".equals(path) || "walk_backward".equals(path)
                || "strafe_left".equals(path) || "strafe_right".equals(path)
                || "look_up".equals(path) || "look_down".equals(path)
                || "look_left".equals(path) || "look_right".equals(path)
                || "sprint".equals(path) || "sneak".equals(path)
                || "jump".equals(path) || "attack".equals(path)
                || "use".equals(path) || "pick_block".equals(path)
                || "drop".equals(path) || "inventory".equals(path)
                || "swap_hands".equals(path) || "hotbar_1".equals(path)
                || "hotbar_2".equals(path) || "hotbar_3".equals(path)
                || "hotbar_4".equals(path) || "hotbar_5".equals(path)
                || "hotbar_6".equals(path) || "hotbar_7".equals(path)
                || "hotbar_8".equals(path) || "hotbar_9".equals(path);
    }
}