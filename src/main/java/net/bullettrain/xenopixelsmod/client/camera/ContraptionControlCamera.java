package net.bullettrain.xenopixelsmod.client.camera;

import net.bullettrain.xenopixelsmod.client.flight.XenoFlightControls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;

import java.lang.reflect.Method;

/**
 * True while the local player is in a Create or Sable/Aeronautics control camera, or seated in
 * a pilot seat. DMZ shoulder + first-person offsets must not run in any of these states.
 */
public final class ContraptionControlCamera {

    private static final Method CREATE_GET_CONTRAPTION;

    static {
        Method method = null;
        try {
            method = Class.forName(
                            "com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler")
                    .getMethod("getContraption");
        } catch (Throwable ignored) {
        }
        CREATE_GET_CONTRAPTION = method;
    }

    private ContraptionControlCamera() {
    }

    public static boolean active() {
        return createControls() || sableSubLevelView() || seatedInPilotSeat();
    }

    private static boolean seatedInPilotSeat() {
        return XenoFlightControls.seated();
    }

    private static boolean createControls() {
        if (CREATE_GET_CONTRAPTION == null) return false;
        try {
            return CREATE_GET_CONTRAPTION.invoke(null) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean sableSubLevelView() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return false;
        CameraType type = mc.options.getCameraType();
        if (type == null) return false;
        String name = type.name();
        return "SUB_LEVEL_VIEW".equals(name) || "SUB_LEVEL_VIEW_UNLOCKED".equals(name);
    }
}
