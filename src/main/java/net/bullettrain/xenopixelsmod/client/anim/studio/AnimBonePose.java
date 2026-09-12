package net.bullettrain.xenopixelsmod.client.anim.studio;

/**
 * One bone's local transform in the studio.
 *
 * <p>Rotation is degrees, position is model units, scale is a multiplier. The three channels match
 * the {@code rotation} / {@code position} / {@code scale} tracks of a GeckoLib 1.8 animation and the
 * {@code setRot*} / {@code setPos*} / {@code setScale*} setters on {@code GeoBone}.
 */
public final class AnimBonePose {
    public float rotX;
    public float rotY;
    public float rotZ;
    public float posX;
    public float posY;
    public float posZ;
    public float scaleX = 1f;
    public float scaleY = 1f;
    public float scaleZ = 1f;

    /**
     * Whether this pose owns its position / scale channel even while the values sit at rest.
     *
     * <p>Without this a keyframe that deliberately returns a bone to origin or to scale 1 would look
     * identical to a pose that never touched the channel, and the bone would snap back to whatever
     * DragonMineZ was doing instead of finishing the move.
     */
    public boolean usePosition;

    /** @see #usePosition */
    public boolean useScale;

    /**
     * Whether the bone is drawn at all, and whether this pose owns that decision.
     *
     * <p>GeckoLib 1.8 has no visibility track, so this rides in a sidecar the format ignores and is
     * applied through {@code GeoBone.setHidden}. It therefore works on this mod's own playback
     * paths and not through a baked GeckoLib controller - see docs/xeno-anim-studio.md.
     */
    public boolean visible = true;

    /** @see #visible */
    public boolean useVisibility;

    public AnimBonePose() {}

    /** Rotation-only pose, in degrees. */
    public AnimBonePose(float rotX, float rotY, float rotZ) {
        this.rotX = rotX;
        this.rotY = rotY;
        this.rotZ = rotZ;
    }

    /** Rotation-only pose, in degrees. */
    public static AnimBonePose rot(float x, float y, float z) {
        return new AnimBonePose(x, y, z);
    }

    /** Position-only pose, in model units. */
    public static AnimBonePose pos(float x, float y, float z) {
        AnimBonePose pose = new AnimBonePose();
        pose.setPosition(x, y, z);
        return pose;
    }

    /** Scale-only pose. 1 is unscaled. */
    public static AnimBonePose scale(float x, float y, float z) {
        AnimBonePose pose = new AnimBonePose();
        pose.setScale(x, y, z);
        return pose;
    }

    public void setPosition(float x, float y, float z) {
        posX = x;
        posY = y;
        posZ = z;
        usePosition = true;
    }

    public void setScale(float x, float y, float z) {
        scaleX = x;
        scaleY = y;
        scaleZ = z;
        useScale = true;
    }

    public void setVisible(boolean value) {
        visible = value;
        useVisibility = true;
    }

    public AnimBonePose copy() {
        AnimBonePose out = new AnimBonePose(rotX, rotY, rotZ);
        out.posX = posX;
        out.posY = posY;
        out.posZ = posZ;
        out.scaleX = scaleX;
        out.scaleY = scaleY;
        out.scaleZ = scaleZ;
        out.usePosition = usePosition;
        out.useScale = useScale;
        out.visible = visible;
        out.useVisibility = useVisibility;
        return out;
    }

    public boolean hasRotation() {
        return rotX != 0f || rotY != 0f || rotZ != 0f;
    }

    public boolean hasPosition() {
        return usePosition || posX != 0f || posY != 0f || posZ != 0f;
    }

    public boolean hasScale() {
        return useScale || scaleX != 1f || scaleY != 1f || scaleZ != 1f;
    }

    public boolean hasVisibility() {
        return useVisibility || !visible;
    }

    public boolean isIdentity() {
        return !hasRotation() && !hasPosition() && !hasScale() && !hasVisibility();
    }

    public void reset() {
        rotX = rotY = rotZ = 0f;
        posX = posY = posZ = 0f;
        scaleX = scaleY = scaleZ = 1f;
        usePosition = false;
        useScale = false;
        visible = true;
        useVisibility = false;
    }

    /**
     * The same pose on the opposite side of the body: yaw and roll flip, and so does sideways
     * translation. Pitch, vertical and forward translation, and scale are side-neutral.
     *
     * <p>The caller is responsible for also swapping {@code right_*} and {@code left_*} bone names;
     * see {@link AnimPoseClipboard#mirror}.
     */
    public AnimBonePose mirrored() {
        AnimBonePose out = copy();
        out.rotY = -rotY;
        out.rotZ = -rotZ;
        out.posX = -posX;
        return out;
    }

    public static AnimBonePose lerp(AnimBonePose a, AnimBonePose b, float t) {
        if (a == null) return b == null ? new AnimBonePose() : b.copy();
        if (b == null) return a.copy();
        float u = Math.max(0f, Math.min(1f, t));
        AnimBonePose out = new AnimBonePose(
                a.rotX + (b.rotX - a.rotX) * u,
                a.rotY + (b.rotY - a.rotY) * u,
                a.rotZ + (b.rotZ - a.rotZ) * u);
        out.posX = a.posX + (b.posX - a.posX) * u;
        out.posY = a.posY + (b.posY - a.posY) * u;
        out.posZ = a.posZ + (b.posZ - a.posZ) * u;
        out.scaleX = a.scaleX + (b.scaleX - a.scaleX) * u;
        out.scaleY = a.scaleY + (b.scaleY - a.scaleY) * u;
        out.scaleZ = a.scaleZ + (b.scaleZ - a.scaleZ) * u;
        out.usePosition = a.hasPosition() || b.hasPosition();
        out.useScale = a.hasScale() || b.hasScale();
        // Visibility is a switch: it holds the value it is leaving until the next key lands.
        out.visible = u < 1f ? a.visible : b.visible;
        out.useVisibility = a.hasVisibility() || b.hasVisibility();
        return out;
    }
}
