package net.bullettrain.xenopixelsmod.space.dragonball;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Stable per-world slot identity groundwork for promoting the seven legacy balls into planets. */
public record DragonBallIdentity(int slot, UUID planetId, String planetKey, String surfaceCellKey) {
    public static final int VERSION = 1;
    private static final String NAMESPACE = "xenopixelsmod:cosmic_dragon_ball:v1:";

    public DragonBallIdentity {
        if (slot < 1 || slot > CosmicDragonBalls.COUNT) throw new IllegalArgumentException("SDB slot out of range: " + slot);
        if (planetId == null || planetKey == null || planetKey.isBlank()
                || surfaceCellKey == null || surfaceCellKey.isBlank())
            throw new IllegalArgumentException("SDB identity fields must be present");
    }

    public static DragonBallIdentity forSlot(int slot) {
        if (slot < 1 || slot > CosmicDragonBalls.COUNT) throw new IllegalArgumentException("SDB slot out of range: " + slot);
        String suffix = "slot-" + slot;
        String stableKey = NAMESPACE + suffix;
        UUID id = UUID.nameUUIDFromBytes(stableKey.getBytes(StandardCharsets.UTF_8));
        return new DragonBallIdentity(slot, id, "xenopixelsmod:sdb/planet/" + suffix,
                "xenopixelsmod:planet_surfaces/sdb/" + suffix);
    }

    /** Returns the star count encoded by an exact production planet key, or zero for any other body id. */
    public static int slotFromPlanetKey(String planetKey) {
        if (planetKey == null) return 0;
        String prefix = "xenopixelsmod:sdb/planet/slot-";
        if (!planetKey.startsWith(prefix) || planetKey.length() != prefix.length() + 1) return 0;
        char digit = planetKey.charAt(prefix.length());
        if (digit < '1' || digit > '0' + CosmicDragonBalls.COUNT) return 0;
        return digit - '0';
    }

    public static boolean hasUniquePlanetIds(List<DragonBallIdentity> identities) {
        Set<UUID> ids = new HashSet<>();
        Set<Integer> slots = new HashSet<>();
        Set<String> planetKeys = new HashSet<>();
        Set<String> surfaceCells = new HashSet<>();
        for (DragonBallIdentity identity : identities) {
            if (identity == null || !ids.add(identity.planetId()) || !slots.add(identity.slot())
                    || !planetKeys.add(identity.planetKey()) || !surfaceCells.add(identity.surfaceCellKey())) return false;
        }
        return true;
    }
}
