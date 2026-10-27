package net.bullettrain.xenopixelsmod.space.pod.transfer;

import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.nbt.CompoundTag;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Everything a pod transfer has to carry, captured from the live source body while its plot is still attached.
 * This is the only moment the true numbers exist: a serialized copy re-derives the body from a tag, and the
 * physics body's motion is scaled on the way back in.
 *
 * <p>All vectors are copies. joml mutates in place, and Sable's loader multiplies the deserialized velocity by
 * {@code VELOCITY_RETAINED_ON_LOAD}, so a shared instance would silently change what is being asserted.
 */
public record PodTransferSnapshot(UUID podId, UUID bodyId, String displayName, Loadout loadout, Pose pose,
                                  Vector3d linearVelocity, Vector3d angularVelocity,
                                  double boundsMinY, double boundsMaxY, double velocityRetentionOnLoad,
                                  PodRiderBinding.Captured rider) {

    public PodTransferSnapshot {
        linearVelocity = new Vector3d(linearVelocity);
        angularVelocity = new Vector3d(angularVelocity);
    }

    // The generated accessors would hand out the stored mutable joml objects, which would let a caller rewrite
    // the capture. joml is copy-on-read here so the recorded numbers stay the numbers that were measured.
    @Override public Vector3d linearVelocity() { return new Vector3d(linearVelocity); }
    @Override public Vector3d angularVelocity() { return new Vector3d(angularVelocity); }

    /** The pod-v1 identity payload persisted in the body's user data. */
    public record Loadout(String hullId, String cockpitId, String thrusterSkirtId, String trimId,
                          int schemaVersion, boolean fixedHull,
                          int primaryArgb, int secondaryArgb, int accentArgb) {}

    /** Body pose at capture time: where it is, how it is turned, what it spins about, how big it is. */
    public record Pose(Vector3d position, Quaterniond orientation, Vector3d rotationPoint, Vector3d scale) {
        public Pose {
            position = new Vector3d(position);
            orientation = new Quaterniond(orientation);
            rotationPoint = new Vector3d(rotationPoint);
            scale = new Vector3d(scale);
        }

        @Override public Vector3d position() { return new Vector3d(position); }
        @Override public Quaterniond orientation() { return new Quaterniond(orientation); }
        @Override public Vector3d rotationPoint() { return new Vector3d(rotationPoint); }
        @Override public Vector3d scale() { return new Vector3d(scale); }
    }

    private static final String DATA_POD_ID = "xenopixelsmod:space_pod_id";
    private static final String DATA_SCHEMA_VERSION = "xenopixelsmod:space_pod_schema";
    private static final String DATA_HULL_ID = "xenopixelsmod:space_pod_hull";
    private static final String DATA_HULL_FIXED = "xenopixelsmod:space_pod_fixed_hull";
    private static final String DATA_COCKPIT_ID = "xenopixelsmod:space_pod_cockpit";
    private static final String DATA_THRUSTER_ID = "xenopixelsmod:space_pod_thruster_skirt";
    private static final String DATA_TRIM_ID = "xenopixelsmod:space_pod_trim";
    private static final String DATA_PRIMARY_ARGB = "xenopixelsmod:space_pod_primary_argb";
    private static final String DATA_SECONDARY_ARGB = "xenopixelsmod:space_pod_secondary_argb";
    private static final String DATA_ACCENT_ARGB = "xenopixelsmod:space_pod_accent_argb";

    /**
     * @param linearVelocity    the body's live linear velocity, read from the physics pipeline
     * @param angularVelocity   the body's live angular velocity, read from the physics pipeline
     * @param rider             the player bound to this body, or null when nobody is riding
     */
    public static PodTransferSnapshot of(ServerSubLevel source, Vector3d linearVelocity,
                                         Vector3d angularVelocity, PodRiderBinding.Captured rider) {
        CompoundTag data = source.getUserDataTag();
        if (data == null || !data.hasUUID(DATA_POD_ID))
            throw new IllegalArgumentException("source body carries no stable pod identity in user data");
        var pose = source.logicalPose();
        var bounds = source.boundingBox();
        return new PodTransferSnapshot(data.getUUID(DATA_POD_ID), source.getUniqueId(), source.getName(),
                new Loadout(data.getString(DATA_HULL_ID), data.getString(DATA_COCKPIT_ID),
                        data.getString(DATA_THRUSTER_ID), data.getString(DATA_TRIM_ID),
                        data.getInt(DATA_SCHEMA_VERSION), data.getBoolean(DATA_HULL_FIXED),
                        data.getInt(DATA_PRIMARY_ARGB), data.getInt(DATA_SECONDARY_ARGB),
                        data.getInt(DATA_ACCENT_ARGB)),
                new Pose(new Vector3d(pose.position()), new Quaterniond(pose.orientation()),
                        new Vector3d(pose.rotationPoint()), new Vector3d(pose.scale())),
                linearVelocity, angularVelocity,
                bounds.minY(), bounds.maxY(), SableConfig.VELOCITY_RETAINED_ON_LOAD.getAsDouble(), rider);
    }

    /**
     * The velocity a pod must gain after a load to end up exactly where it started. Sable only offers an
     * additive velocity call, and the retention factor is server-configurable, so the correction is measured
     * against the post-load reading instead of assuming the configured multiplier was applied.
     */
    public static Vector3d velocityCorrection(Vector3d captured, Vector3d measuredAfterLoad, Vector3d result) {
        return result.set(captured.x - measuredAfterLoad.x, captured.y - measuredAfterLoad.y,
                captured.z - measuredAfterLoad.z);
    }

    /** True when the two readings agree to within {@code tolerance} per axis. */
    public static boolean velocitiesMatch(Vector3d expected, Vector3d actual, double tolerance) {
        return Math.abs(expected.x - actual.x) <= tolerance
                && Math.abs(expected.y - actual.y) <= tolerance
                && Math.abs(expected.z - actual.z) <= tolerance;
    }

    /** The stable pod identity that the destination body must carry back out of the load. */
    public boolean identityMatches(ServerSubLevel candidate) {
        if (candidate == null || candidate.isRemoved()) return false;
        if (!candidate.getUniqueId().equals(bodyId)) return false;
        CompoundTag data = candidate.getUserDataTag();
        return data != null && data.hasUUID(DATA_POD_ID) && data.getUUID(DATA_POD_ID).equals(podId);
    }
}
