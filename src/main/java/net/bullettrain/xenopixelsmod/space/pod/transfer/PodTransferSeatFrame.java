package net.bullettrain.xenopixelsmod.space.pod.transfer;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Coordinate conversion for a rider's local seat when a pod is copied into a remapped Sable plot cell. */
final class PodTransferSeatFrame {
    private PodTransferSeatFrame() {}

    /**
     * PlotCellRemap shifts the copied pose's rotation point by {@code storageShift}; the same translation must be
     * added to the captured local coordinate so the body-space point remains invariant under Sable's transform.
     */
    static Vector3d afterStorageShift(Vector3dc sourcePlotLocal, Vector3dc storageShift, Vector3d result) {
        if (sourcePlotLocal == null || storageShift == null || result == null) return null;
        double x = sourcePlotLocal.x() + storageShift.x();
        double y = sourcePlotLocal.y() + storageShift.y();
        double z = sourcePlotLocal.z() + storageShift.z();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        return result.set(x, y, z);
    }
}
