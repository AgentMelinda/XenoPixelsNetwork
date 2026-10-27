package net.bullettrain.xenopixelsmod.space.physics;

/** Pure policy for skipping Sable's per-body parent-world chunk-ticket sweep. */
public final class PlanetChunkTicketPolicy {
    private PlanetChunkTicketPolicy() {}

    /**
     * Asteroids already bypass this sweep unconditionally. Planet and in-flight build markers obey the shared
     * planetSkipChunkTickets switch, so admins can restore ordinary world-chunk collision around them if needed.
     */
    public static boolean shouldSkip(boolean enabled, boolean asteroidManaged, boolean markedPlanetOrBuild) {
        return asteroidManaged || enabled && markedPlanetOrBuild;
    }

    public static boolean isTaggedDragonBall(net.minecraft.nbt.CompoundTag userData) {
        return userData != null && (userData.contains(
                net.bullettrain.xenopixelsmod.space.dragonball.DragonBallBodyScan.TAG)
                || userData.contains(net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanets.TAG));
    }
}
