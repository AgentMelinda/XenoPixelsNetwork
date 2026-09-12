package net.bullettrain.xenopixelsmod.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The studio's 3D view: an explicit orbit camera over a living entity.
 *
 * <h2>Why not {@code renderEntityInInventoryFollowsMouse}</h2>
 *
 * That helper derives the pose from the cursor:
 * {@code f2 = atan((centreX - mouseX) / 40)}, then {@code yBodyRot = 180 + f2*20} and
 * {@code yRot = 180 + f2*40}. Over an inventory slot a few dozen pixels wide that is a gentle
 * follow. Over a viewport several hundred pixels wide the {@code atan} saturates near
 * {@code PI/2}, so yaw reaches about ±63° with the body at half the head's angle - the model
 * arrives twisted, which is exactly what the studio looked like.
 *
 * <p>Worse, it writes {@code yBodyRot}, {@code yRot}, {@code xRot}, {@code yHeadRot} and
 * {@code yHeadRotO} on the entity and restores only those five. {@code yBodyRotO}, {@code yRotO}
 * and {@code xRotO} are left modified. The studio renders the <em>real local player</em>, so those
 * leftovers are read back by the world renderer as the previous tick's rotation and show up as the
 * player's own view lurching - the intermittent fault the user reported.
 *
 * <p>So this class drives its own yaw, pitch, zoom and pan, puts the pitch in the camera quaternion
 * rather than in the entity, keeps body and head yaw equal so nothing twists, and saves and
 * restores all eight rotation fields in a {@code finally} - the discipline
 * {@code NpcFullDmzRenderer.renderPreview} already applies for the NPC visualizer.
 */
public final class StudioViewport {
    /** Matches the vanilla inventory preview, which lifts the entity by a sixteenth of a block. */
    private static final float Y_OFFSET = 0.0625f;

    private static final float MIN_ZOOM = 0.4f;
    private static final float MAX_ZOOM = 4.0f;
    private static final float MIN_PITCH = -80f;
    private static final float MAX_PITCH = 80f;
    private static final float DEFAULT_YAW = 180f;

    private float yaw = DEFAULT_YAW;
    private float pitch;
    private float zoom = 1.0f;
    private float panX;
    private float panY;
    private boolean orbiting;
    private boolean panning;

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float zoom() {
        return zoom;
    }

    public boolean interacting() {
        return orbiting || panning;
    }

    /** Back to a straight-on, centred, unzoomed view. */
    public void reset() {
        yaw = DEFAULT_YAW;
        pitch = 0f;
        zoom = 1.0f;
        panX = 0f;
        panY = 0f;
    }

    /** Keeps the rotation but re-centres and re-fits, for the F key. */
    public void frame() {
        zoom = 1.0f;
        panX = 0f;
        panY = 0f;
    }

    public void beginOrbit(boolean pan) {
        orbiting = !pan;
        panning = pan;
    }

    public void endDrag() {
        orbiting = false;
        panning = false;
    }

    /** @return true when the drag was consumed */
    public boolean drag(double dragX, double dragY) {
        if (orbiting) {
            yaw = Mth.wrapDegrees(yaw + (float) dragX * 1.5f);
            pitch = Mth.clamp(pitch - (float) dragY, MIN_PITCH, MAX_PITCH);
            return true;
        }
        if (panning) {
            panX += (float) dragX;
            panY += (float) dragY;
            return true;
        }
        return false;
    }

    public void scroll(double amount) {
        zoom = Mth.clamp(zoom + (float) amount * 0.12f, MIN_ZOOM, MAX_ZOOM);
    }

    public String readout() {
        return String.format(java.util.Locale.ROOT, "yaw %+.0f  pitch %+.0f  zoom %.2fx",
                Mth.wrapDegrees(yaw - DEFAULT_YAW), pitch, zoom);
    }

    /**
     * Draws {@code entity} inside the given rectangle.
     *
     * @param baseScale the unzoomed pixel height of one block, before {@link #zoom}
     */
    public void render(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                       LivingEntity entity, int baseScale) {
        render(graphics, x1, y1, x2, y2, entity, baseScale, 0f, 0f);
    }

    /** As {@link #render}, nudged sideways - what the onion-skin ghosts use. */
    public void render(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                       LivingEntity entity, int baseScale, float offsetX, float offsetY) {
        if (entity == null || x2 - x1 < 8 || y2 - y1 < 8) return;
        float centreX = (x1 + x2) / 2f + panX + offsetX;
        float centreY = (y1 + y2) / 2f + panY + offsetY;

        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(pitch * Mth.DEG_TO_RAD);
        pose.mul(camera);

        float oldBodyRot = entity.yBodyRot;
        float oldBodyRotO = entity.yBodyRotO;
        float oldYRot = entity.getYRot();
        float oldYRotO = entity.yRotO;
        float oldXRot = entity.getXRot();
        float oldXRotO = entity.xRotO;
        float oldHeadRot = entity.yHeadRot;
        float oldHeadRotO = entity.yHeadRotO;

        graphics.enableScissor(x1, y1, x2, y2);
        try {
            // Body and head share one yaw so the model never twists against itself, and the pitch
            // lives in the camera instead of the entity so the head is not forced to look down.
            entity.yBodyRot = yaw;
            entity.yBodyRotO = yaw;
            entity.setYRot(yaw);
            entity.yRotO = yaw;
            entity.setXRot(0f);
            entity.xRotO = 0f;
            entity.yHeadRot = yaw;
            entity.yHeadRotO = yaw;

            float entityScale = entity.getScale();
            Vector3f translation =
                    new Vector3f(0f, entity.getBbHeight() / 2f + Y_OFFSET * entityScale, 0f);
            float size = Math.max(1f, baseScale * zoom) / entityScale;
            InventoryScreen.renderEntityInInventory(
                    graphics, centreX, centreY, size, translation, pose, camera, entity);
        } finally {
            // All eight, not the five the vanilla helper puts back.
            entity.yBodyRot = oldBodyRot;
            entity.yBodyRotO = oldBodyRotO;
            entity.setYRot(oldYRot);
            entity.yRotO = oldYRotO;
            entity.setXRot(oldXRot);
            entity.xRotO = oldXRotO;
            entity.yHeadRot = oldHeadRot;
            entity.yHeadRotO = oldHeadRotO;
            graphics.disableScissor();
        }
    }

    /** A ground plane and axis marks, so the model is not floating in a void. */
    public void drawGround(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                           int gridColor, int axisColor) {
        int centreX = (int) ((x1 + x2) / 2f + panX);
        int floorY = (int) ((y1 + y2) / 2f + panY);
        int halfWidth = Math.min((x2 - x1) / 2 - 6, 140);
        if (halfWidth <= 8) return;

        // Rows tighten towards the horizon, which reads as a plane rather than a ladder.
        for (int row = 1; row <= 7; row++) {
            int offset = (int) (row * row * 1.6f * zoom);
            int y = floorY + offset;
            if (y >= y2 - 2 || y <= y1 + 1) continue;
            int width = halfWidth - row * 8;
            if (width <= 4) break;
            int alpha = Math.max(0x10, 0x40 - row * 7);
            graphics.fill(centreX - width, y, centreX + width, y + 1,
                    (alpha << 24) | (gridColor & 0xFFFFFF));
        }
        graphics.fill(centreX - halfWidth / 2, floorY, centreX + halfWidth / 2, floorY + 1,
                axisColor);
        graphics.fill(centreX - 1, floorY - 3, centreX + 1, floorY + 4, axisColor);
    }
}
