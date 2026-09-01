package net.bullettrain.xenopixelsmod.client.render;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * A per-frame snapshot of the world's own projection/model-view matrices and camera position,
 * captured once the 3D scene has finished rendering, so 2D HUD code (the crosshair's lock
 * brackets, off-screen arrow and lead marker) can project a world point to a screen position
 * without touching the 3D render pipeline itself.
 *
 * <p>The 2D GUI pass sets up its own orthographic projection, so reading
 * {@code RenderSystem.getProjectionMatrix()} <i>during</i> HUD rendering would return the wrong
 * matrix entirely. Capturing it earlier, at {@link RenderLevelStageEvent.Stage#AFTER_LEVEL} — the
 * same event NeoForge already fires with the exact matrices and camera used for that frame's 3D
 * pass — is the standard way mods solve this, and it means the HUD layer never needs to touch
 * render state directly.
 *
 * <p>World positions are expected pre-translated relative to the camera (world minus camera
 * position) before this class's matrices are applied, matching how the 3D pass itself is built:
 * Minecraft renders everything camera-relative to keep vertex math in float precision even at
 * large world coordinates, so the model-view matrix here carries rotation only.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class WorldToScreenCache {

    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static Vec3 cameraPos = Vec3.ZERO;
    private static boolean valid;

    private WorldToScreenCache() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        PROJECTION.set(event.getProjectionMatrix());
        MODEL_VIEW.set(event.getModelViewMatrix());
        cameraPos = event.getCamera().getPosition();
        valid = true;
    }

    /** A projected point: screen-space pixel coordinates plus whether it actually lands on screen. */
    public record ScreenPoint(float x, float y, boolean onScreen, boolean behindCamera) {
    }

    /**
     * Project a world position to screen pixels for the current frame's camera.
     *
     * @return null only when no frame has rendered yet (e.g. the very first tick); otherwise a
     * result whose {@code onScreen}/{@code behindCamera} flags the caller must check before
     * drawing anything at the returned coordinates.
     */
    public static @Nullable ScreenPoint project(Vec3 worldPos, int guiWidth, int guiHeight) {
        if (!valid) return null;

        Vector4f clip = new Vector4f(
                (float) (worldPos.x - cameraPos.x),
                (float) (worldPos.y - cameraPos.y),
                (float) (worldPos.z - cameraPos.z),
                1.0f);
        clip.mul(MODEL_VIEW);
        clip.mul(PROJECTION);

        // A point behind the camera has w <= 0. Dividing by it flips both axes, which is why a
        // naive projection sends a target that is directly behind you to the exact opposite
        // corner of the screen. Divide by |w| and negate instead: the result is meaningless as a
        // pixel position (it is nowhere near the viewport, and behindCamera says so), but it
        // still points the right way, which is the whole job of the off-screen arrow. Returning
        // (0, 0) here — as this used to — gave that arrow no direction at all, so it pinned
        // itself to the same screen corner regardless of where the target actually was.
        boolean behind = clip.w() <= 1.0e-5f;
        float w = behind ? Math.max(1.0e-5f, Math.abs(clip.w())) : clip.w();
        float sign = behind ? -1.0f : 1.0f;
        float ndcX = sign * clip.x() / w;
        float ndcY = sign * clip.y() / w;
        float screenX = (ndcX * 0.5f + 0.5f) * guiWidth;
        float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * guiHeight;
        boolean onScreen = !behind && ndcX >= -1.0f && ndcX <= 1.0f && ndcY >= -1.0f && ndcY <= 1.0f;
        return new ScreenPoint(screenX, screenY, onScreen, behind);
    }
}
