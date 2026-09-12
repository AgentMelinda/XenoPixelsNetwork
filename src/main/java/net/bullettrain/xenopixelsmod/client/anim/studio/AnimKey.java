package net.bullettrain.xenopixelsmod.client.anim.studio;

/**
 * One keyframe on one channel: three components and the easing of the segment arriving at it.
 *
 * <p>What the three components mean depends on the channel that holds it - degrees for rotation,
 * model units for position, a multiplier for scale, and {@code x} alone for visibility.
 */
public final class AnimKey {
    public float x;
    public float y;
    public float z;
    public String easing = AnimEasing.LINEAR;

    public AnimKey() {}

    public AnimKey(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public AnimKey(float x, float y, float z, String easing) {
        this(x, y, z);
        this.easing = AnimEasing.sanitize(easing);
    }

    public AnimKey copy() {
        return new AnimKey(x, y, z, easing);
    }

    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public float component(int axis) {
        return axis == 0 ? x : axis == 1 ? y : z;
    }

    public void component(int axis, float value) {
        if (axis == 0) x = value;
        else if (axis == 1) y = value;
        else z = value;
    }

    @Override
    public String toString() {
        return "AnimKey[" + x + ", " + y + ", " + z + ", " + easing + "]";
    }
}
