package net.bullettrain.xenopixelsmod.client.combat;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * One alpha wrap for every technique that dims a body. Zanzoken and Hakai multiply rather
 * than stacking two buffer wrappers, so a dodger mid-Hakai does not square the fade.
 */
public final class CombatBodyFade {
    private static final ThreadLocal<Entity> FADING = new ThreadLocal<>();

    private CombatBodyFade() {}

    public static float alpha(Entity entity, float partialTick) {
        float zanzoken = ZanzokenFade.alpha(entity, partialTick);
        float hakai = HakaiFade.alpha(entity, partialTick);
        float floor = XenoServerConfig.hakaiFadeEnabled
                ? Math.min(0.02f, XenoServerConfig.hakaiFadeMinAlpha)
                : 0.02f;
        return Math.max(floor, Math.min(1.0f, zanzoken * hakai));
    }

    public static MultiBufferSource wrap(MultiBufferSource buffers, float alpha) {
        if (buffers == null || isWrapped(buffers) || alpha >= 0.999f) return buffers;
        return new AlphaMultiBufferSource(buffers, alpha);
    }

    /**
     * Height-aware Hakai wrap. Zanzoken stays a single multiplier so a dodger mid-Hakai
     * does not stack two wrappers.
     */
    public static MultiBufferSource wrapHakai(MultiBufferSource buffers, Entity entity) {
        return wrapHakai(buffers, entity, 1.0f);
    }

    public static MultiBufferSource wrapHakai(MultiBufferSource buffers, Entity entity,
                                              float partialTick) {
        if (buffers == null || isWrapped(buffers) || entity == null) return buffers;
        if (HakaiSweepFx.enabled()) {
            // The sweep has its own smooth clock (and keeps the body cut through the final clear packet).
            float sweep = HakaiSweepFx.sweepProgress(entity, partialTick);
            if (sweep <= 0f) return buffers;
            // Sweep style: a clean cut from the top down (HakaiSweepFx draws the rim at the same height).
            // 1.21.1 LevelRenderer puts the camera rotation in the model-view stack and renders entities with only a
            // camera-relative translation, so model vertices are NOT rotated: world Y = camera Y + vertex Y.
            CameraFrame frame = cameraFrame();
            if (net.bullettrain.xenopixelsmod.client.config.XenoClientConfig.hakaiSweepDebug)
                net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info("HAKAI_SWEEP clip id={} class={} sweep={} cutY={} partial={} buffers={}",
                        entity.getId(), entity.getClass().getSimpleName(), sweep, HakaiSweepFx.cutY(entity, sweep, partialTick), partialTick,
                        buffers.getClass().getSimpleName() + " bounds=" + java.util.Arrays.toString(HakaiSweepFx.bounds(entity, partialTick))
                                + " feet=" + entity.getPosition(partialTick).y + " h=" + entity.getBbHeight());
            if (!Float.isNaN(frame.y))
                return new HakaiClipMultiBufferSource(buffers, HakaiSweepFx.cutY(entity, sweep, partialTick),
                        frame.x, frame.y, frame.z, HakaiSweepFx.measure(entity, partialTick));
            return buffers;
        }
        if (!HakaiFade.dissolving(entity)) return buffers;
        float progress = HakaiFade.progress(entity, partialTick);
        float zanzoken = ZanzokenFade.alpha(entity, partialTick);
        float uniform = alpha(entity, partialTick);
        float feetY = (float) entity.getY();
        float bbHeight = Math.max(0.01f, entity.getBbHeight());
        CameraFrame camera = cameraFrame();
        return new AlphaMultiBufferSource(buffers, new AlphaMultiBufferSource.HeightFade(
                progress, feetY, bbHeight,
                camera.x, camera.y, camera.z,
                camera.qx, camera.qy, camera.qz, camera.qw,
                XenoServerConfig.hakaiFadeMinAlpha,
                XenoServerConfig.hakaiFadeCurve,
                XenoServerConfig.hakaiFadeSpeed,
                XenoServerConfig.hakaiFadeBand,
                zanzoken, uniform));
    }

    private record CameraFrame(float x, float y, float z, float qx, float qy, float qz, float qw) {
        static CameraFrame identity(float y) {
            return new CameraFrame(0.0f, y, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        }
    }

    private static CameraFrame cameraFrame() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameRenderer == null) return CameraFrame.identity(Float.NaN);
            Camera camera = mc.gameRenderer.getMainCamera();
            if (camera == null) return CameraFrame.identity(Float.NaN);
            Vec3 pos = camera.getPosition();
            Quaternionf rot = camera.rotation();
            if (pos == null || rot == null) return CameraFrame.identity(Float.NaN);
            return new CameraFrame((float) pos.x, (float) pos.y, (float) pos.z,
                    rot.x, rot.y, rot.z, rot.w);
        } catch (RuntimeException e) {
            return CameraFrame.identity(Float.NaN);
        }
    }

    public static boolean isWrapped(MultiBufferSource buffers) {
        return buffers instanceof AlphaMultiBufferSource || buffers instanceof HakaiClipMultiBufferSource;
    }

    /** True when this source is the vanilla glow outline pass (do not steal its RenderType). */
    public static boolean outlinePass(MultiBufferSource buffers) {
        if (buffers instanceof OutlineBufferSource) return true;
        return buffers instanceof AlphaMultiBufferSource wrapped && wrapped.wrapsOutline();
    }

    /** Marks the entity whose current {@code render} call is fading, for getRenderType mixins. */
    public static void begin(Entity entity) {
        FADING.set(entity);
    }

    public static void end() {
        FADING.remove();
    }

    public static boolean fading(Entity entity) {
        return entity != null && entity == FADING.get();
    }
}
