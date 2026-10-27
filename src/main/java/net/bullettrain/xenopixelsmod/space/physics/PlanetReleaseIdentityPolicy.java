package net.bullettrain.xenopixelsmod.space.physics;

/** Identity guard for Sable's plot-coordinate-based sub-level removal overload. */
final class PlanetReleaseIdentityPolicy {
    private PlanetReleaseIdentityPolicy() {}

    static boolean isCurrent(Object requested, Object loadedAtRequestedId) {
        return requested != null && requested == loadedAtRequestedId;
    }
}
