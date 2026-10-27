package net.bullettrain.xenopixelsmod.client.space;

/** Chooses which planet representation owns an active Hakai draw when far projections are enabled. */
final class PlanetHakaiRenderPolicy {
    private PlanetHakaiRenderPolicy() {}

    static boolean drawPhysical(boolean projectionVisible, boolean burning, boolean physicalBodyBound) {
        return physicalBodyBound && (!projectionVisible || burning);
    }

    static boolean drawProjection(boolean projectionVisible, boolean burning, boolean physicalBodyBound) {
        return projectionVisible && (!burning || !physicalBodyBound);
    }

    static boolean drawBurningProjection(boolean projectionVisible, boolean burning, boolean physicalBodyBound) {
        return projectionVisible && burning && !physicalBodyBound;
    }
}
