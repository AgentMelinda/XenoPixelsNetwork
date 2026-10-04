package net.bullettrain.xenopixelsmod.client.maker;

import com.dragonminez.common.hair.CustomHair;
import net.bullettrain.xenopixelsmod.hair.HairMakerDocument;
import net.bullettrain.xenopixelsmod.hair.HairStrandModel;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;

/**
 * Strand picking for the Hair Editor Preview well.
 *
 * <p>Projects each DMZ strand base XYZ ({@link CustomHair#getStrandBasePosition}) through the
 * same inventory path the preview uses: hair pixels → blocks ({@code ×0.0625}, cited from
 * {@code HairRenderer}), head offset, body yaw, then {@code scale(s,s,-s)} + {@code rotateZ(PI)}.
 *
 * <p>Hit radius scales with inventory scale (zoom): larger when zoomed out, tighter when zoomed in.
 * Samples along the shaft and across cube width so FRONT fringe is easier to click.
 */
public final class HairStrandPick {
    /** Baseline squared radius at inventory scale 55 (tests / fallback). */
    public static final float HIT_RADIUS_SQ = 22f * 22f;
    /** Hair BASE_POSITIONS are pixel units; HairRenderer multiplies by 1/16. */
    public static final float PIXEL_TO_BLOCK = 0.0625f;
    /** Standing player head centre in entity space (blocks from feet). */
    public static final float HEAD_Y_BLOCKS = 1.5f;

    private HairStrandPick() {
    }

    public record Hit(String face, int index, float distSq, float screenX, float screenY) {
    }

    public record ScreenPoint(float x, float y) {
    }

    /** UI-pixel pick radius for the current inventory scale (zoom-aware). */
    public static float hitRadiusPxForScale(float scale) {
        // At scale 55 → ~20px; zoomed in (110) → ~14px; zoomed out (35) → ~28px.
        float r = 20f * (55f / Math.max(28f, scale));
        return Math.max(12f, Math.min(36f, r));
    }

    public static float hitRadiusSqForScale(float scale) {
        float r = hitRadiusPxForScale(scale);
        return r * r;
    }

    /**
     * @param entityX/entityY inventory feet pivot (same args as {@code renderEntityInInventory})
     * @param scale           inventory scale (pixels per block)
     * @param yawDegrees      preview body yaw (MakerPreviewController)
     * @param currentFaceOnly when true, only test {@code document.face()}; else all faces
     */
    public static Hit pick(
            HairMakerDocument document,
            int uiX,
            int uiY,
            int entityX,
            int entityY,
            float scale,
            float yawDegrees,
            boolean currentFaceOnly) {
        return pick(document, uiX, uiY, entityX, entityY, scale, yawDegrees, currentFaceOnly,
                hitRadiusSqForScale(scale));
    }

    public static Hit pick(
            HairMakerDocument document,
            int uiX,
            int uiY,
            int entityX,
            int entityY,
            float scale,
            float yawDegrees,
            boolean currentFaceOnly,
            float hitRadiusSq) {
        if (document == null || scale <= 0.01f) {
            return null;
        }
        float radiusSq = hitRadiusSq > 0f ? hitRadiusSq : hitRadiusSqForScale(scale);
        if (currentFaceOnly) {
            return pickFace(document, document.face(), uiX, uiY, entityX, entityY, scale,
                    yawDegrees, radiusSq);
        }
        Hit best = null;
        for (String face : facePickOrder(yawDegrees)) {
            Hit hit = pickFace(document, face, uiX, uiY, entityX, entityY, scale, yawDegrees, radiusSq);
            if (hit == null) {
                continue;
            }
            float score = hit.distSq - faceBias(face, yawDegrees);
            if (best == null || score < best.distSq) {
                // Store score in distSq so later compares stay consistent.
                best = new Hit(hit.face(), hit.index(), score, hit.screenX(), hit.screenY());
            }
        }
        return best;
    }

    /** Prefer the face toward the camera for the current yaw. */
    public static String[] facePickOrder(float yawDegrees) {
        float y = ((yawDegrees % 360f) + 360f) % 360f;
        // Preview default yaw 180 → looking at the character's face (FRONT).
        if (y > 135f && y < 225f) {
            return new String[]{"FRONT", "TOP", "LEFT", "RIGHT", "BACK", "BOTTOM"};
        }
        if (y < 45f || y > 315f) {
            return new String[]{"BACK", "TOP", "LEFT", "RIGHT", "FRONT", "BOTTOM"};
        }
        if (y >= 45f && y <= 135f) {
            return new String[]{"LEFT", "TOP", "FRONT", "BACK", "RIGHT", "BOTTOM"};
        }
        return new String[]{"RIGHT", "TOP", "FRONT", "BACK", "LEFT", "BOTTOM"};
    }

    static float faceBias(String faceName, float yawDegrees) {
        String preferred = facePickOrder(yawDegrees)[0];
        if (preferred.equalsIgnoreCase(faceName)) {
            return 90f; // ~9.5px preference toward the camera-facing face
        }
        if ("TOP".equalsIgnoreCase(faceName)) {
            return 25f;
        }
        return 0f;
    }

    private static Hit pickFace(
            HairMakerDocument document,
            String faceName,
            int uiX,
            int uiY,
            int entityX,
            int entityY,
            float scale,
            float yawDegrees,
            float hitRadiusSq) {
        CustomHair.HairFace face = parseFace(faceName);
        if (face == null) {
            return null;
        }
        List<HairStrandModel> strands = document.faceStrands(faceName);
        if (strands.isEmpty()) {
            return null;
        }
        Hit best = null;
        for (int i = 0; i < strands.size(); i++) {
            HairStrandModel strand = strands.get(i);
            if (strand == null || !strand.visible()) {
                continue;
            }
            Vector3f basePx = CustomHair.getStrandBasePosition(face, i);
            float distSq = Float.MAX_VALUE;
            ScreenPoint base = project(
                    basePx.x * PIXEL_TO_BLOCK,
                    HEAD_Y_BLOCKS + basePx.y * PIXEL_TO_BLOCK,
                    basePx.z * PIXEL_TO_BLOCK,
                    entityX, entityY, scale, yawDegrees);
            distSq = Math.min(distSq, distSq(uiX, uiY, base.x, base.y));
            // Shaft samples + slight width offsets so FRONT fringe is easier to hit.
            for (int s = 1; s <= 8; s++) {
                float t = s / 8.0f;
                for (float side : new float[]{0f, -0.45f, 0.45f}) {
                    ScreenPoint pt = projectAlongOffset(
                            basePx, strand, t, side, entityX, entityY, scale, yawDegrees);
                    if (pt != null) {
                        distSq = Math.min(distSq, distSq(uiX, uiY, pt.x, pt.y));
                    }
                }
            }
            if (distSq > hitRadiusSq) {
                continue;
            }
            if (best == null || distSq < best.distSq) {
                best = new Hit(faceName, i, distSq, base.x, base.y);
            }
        }
        return best;
    }

    /**
     * Project a point {@code t} along the strand (0 = base, 1 = tip) after local rotation.
     * {@code side} is a fraction of cube width (−1..1) for pick thickness.
     */
    static ScreenPoint projectAlongOffset(
            Vector3f basePx,
            HairStrandModel strand,
            float t,
            float side,
            int entityX,
            int entityY,
            float scale,
            float yawDegrees) {
        if (strand == null || strand.length() <= 0 || t < 0f) {
            return null;
        }
        float tipLenPx = strand.length() * Math.max(0.1f, strand.cubeHeight()) * strand.lengthScale() * t;
        float sidePx = side * Math.max(0.5f, strand.cubeWidth()) * 0.5f;
        Quaternionf local = new Quaternionf()
                .rotateXYZ(
                        (float) Math.toRadians(strand.rotationX()),
                        (float) Math.toRadians(strand.rotationY()),
                        (float) Math.toRadians(strand.rotationZ()));
        Vector3f localTip = new Vector3f(sidePx, tipLenPx, 0f);
        local.transform(localTip);
        float bx = (basePx.x + localTip.x) * PIXEL_TO_BLOCK;
        float by = HEAD_Y_BLOCKS + (basePx.y + localTip.y) * PIXEL_TO_BLOCK;
        float bz = (basePx.z + localTip.z) * PIXEL_TO_BLOCK;
        return project(bx, by, bz, entityX, entityY, scale, yawDegrees);
    }

    static ScreenPoint projectAlong(
            Vector3f basePx,
            HairStrandModel strand,
            float t,
            int entityX,
            int entityY,
            float scale,
            float yawDegrees) {
        return projectAlongOffset(basePx, strand, t, 0f, entityX, entityY, scale, yawDegrees);
    }

    /** Tip sample ({@code t = 1}). */
    static ScreenPoint projectTip(
            Vector3f basePx,
            CustomHair.HairFace face,
            HairStrandModel strand,
            int entityX,
            int entityY,
            float scale,
            float yawDegrees) {
        return projectAlong(basePx, strand, 1f, entityX, entityY, scale, yawDegrees);
    }

    /**
     * Entity-space blocks → preview UI pixels.
     *
     * <p>Matches InventoryScreen path used by EntityPreviewRenderContext:
     * {@code translate(entityX, entityY)} → {@code scale(s,s,-s)} → {@code mulPose(rotateZ(PI))}.
     * Body yaw applied first around Y (player.yBodyRot).
     */
    public static ScreenPoint project(
            float modelX,
            float modelY,
            float modelZ,
            int entityX,
            int entityY,
            float scale,
            float yawDegrees) {
        float yawRad = (float) Math.toRadians(yawDegrees);
        // Minecraft body yaw: rotate around +Y (same as setting player.yBodyRot).
        float cos = (float) Math.cos(-yawRad);
        float sin = (float) Math.sin(-yawRad);
        float rx = modelX * cos + modelZ * sin;
        float ry = modelY;
        float rz = -modelX * sin + modelZ * cos;

        // rotateZ(PI): (x,y,z) -> (-x,-y,z); then scale(s,s,-s).
        float sx = entityX + (-rx) * scale;
        float sy = entityY + (-ry) * scale;
        return new ScreenPoint(sx, sy);
    }

    private static float distSq(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return dx * dx + dy * dy;
    }

    private static CustomHair.HairFace parseFace(String faceName) {
        if (faceName == null || faceName.isBlank()) {
            return null;
        }
        try {
            return CustomHair.HairFace.valueOf(faceName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** rows, cols matching {@code CustomHair.HairFace} (kept for tests / UI hints). */
    public static int[] faceGrid(String faceName) {
        String face = faceName == null ? "" : faceName.trim().toUpperCase(Locale.ROOT);
        return switch (face) {
            case "FRONT" -> new int[]{1, 4};
            default -> new int[]{4, 4};
        };
    }
}
