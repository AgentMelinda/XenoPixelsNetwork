package net.bullettrain.xenopixelsmod.client.camera;

import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;

/**
 * Cancels DMZ {@code EffectsEvents#onCameraSetup} in every camera mode unless
 * {@code /xenoclient set dmzshake true}.
 */
public final class DmzCameraShakeControl {

    private DmzCameraShakeControl() {
    }

    public static boolean suppress() {
        return !XenoClientConfig.dmzCameraShake;
    }
}
