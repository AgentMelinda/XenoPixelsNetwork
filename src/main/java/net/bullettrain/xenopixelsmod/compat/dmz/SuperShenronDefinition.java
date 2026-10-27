package net.bullettrain.xenopixelsmod.compat.dmz;

import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonDefinition;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.entities.dragon.DragonWishEntity;
import com.dragonminez.common.wish.Wish;
import com.dragonminez.common.wish.wishes.TPSWish;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Super Shenron as a data-only DMZ dragon: it reuses the registered {@code dragonminez:shenron} entity type.
 * DMZ 2.1.3 ignores dragon JSON shipped inside a mod (both reload listeners discard their resource map and read
 * only the game-dir {@code dragonballs} folders), so the definition goes through the public bootstrap API and the
 * wish list through {@code DmzWishRegistrySuperShenronMixin}.
 * <p>
 * The ball set id matches no DMZ ball set, so {@code getDragonForSetAndDimension} never picks this dragon for real
 * balls and Earth/Namek summons are unchanged; on despawn {@code scatterDragonBalls} finds no set and returns. It is
 * only spawned on purpose (the SDB trigger, not part of this slice).
 */
public final class SuperShenronDefinition {
    /** Definition id, wish-screen id and wish-list key; DMZ grants by the wish-screen id, so all three match. */
    public static final String ID = "super_shenron";
    /** Size and asset are Earth Shenron's; giant scaling waits until the renderer scaling is verified. */
    static final float WIDTH = 3.0f;
    static final float HEIGHT = 17.0f;
    static final int WISH_COUNT = 1;
    static final Set<ResourceLocation> DIMENSIONS = Set.of(
            ResourceLocation.withDefaultNamespace("overworld"),
            ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "voidsea"));

    private SuperShenronDefinition() {}

    public static DragonDefinition definition() {
        return new DragonDefinition(ID, "shenron", WIDTH, HEIGHT, new LinkedHashSet<>(DIMENSIONS), ID, ID, WISH_COUNT, "shenron");
    }

    /** DMZ's own default Shenron TPS wish (as built by WishManager.createDefaultShenronWishes): no item ids. */
    public static List<Wish> wishes() {
        return List.of(new TPSWish("wish.shenron.tps.name", "wish.shenron.tps.desc", 5000));
    }

    /**
     * Registers the definition on this side and republishes DMZ's runtime definitions. {@code getDragon} reads the
     * runtime map, which a client connected to a dedicated server only ever builds from bootstrap, and no packet
     * carries definitions; so both sides call this in common setup.
     */
    public static void register() {
        DragonBallDefinitions.registerBootstrapDragon(definition());
        DragonBallDefinitions.resetRuntimeDefinitions();
    }

    /**
     * The server wish map with our list added. DMZ gives every definition without a pack list an empty one, so only
     * a missing or empty entry is filled; a list from a dragonballs pack wins.
     *
     * @return {@code current} itself when nothing changes, otherwise an immutable copy
     */
    public static Map<String, List<Wish>> withWishes(Map<String, List<Wish>> current) {
        List<Wish> existing = current.get(ID);
        if (existing != null && !existing.isEmpty()) return current;
        Map<String, List<Wish>> merged = new LinkedHashMap<>(current);
        merged.put(ID, wishes());
        return Map.copyOf(merged);
    }

    /**
     * Creates the existing DMZ wish entity for a future server-authoritative planetary summon flow.
     * This only establishes the entity/DMZ definition bridge; it does not implement summon eligibility,
     * a giant Shenron renderer, summon phases, or SDB consumption/reset.
     */
    public static Optional<DragonWishEntity> spawnWishEntity(ServerLevel level, String ownerName,
                                                              double x, double y, double z) {
        if (level == null || ownerName == null || ownerName.isBlank()
                || !supportedCoordinates(level, x, y, z)) return Optional.empty();
        DragonDefinition definition = DragonBallDefinitions.getDragon(ID);
        if (definition == null || !definition.supportsDimension(level.dimension())) return Optional.empty();

        DragonWishEntity dragon = MainEntities.getDragonWishEntityOrThrow("shenron").get().create(level);
        if (dragon == null) return Optional.empty();
        dragon.setDragonDefinitionId(ID);
        dragon.setOwnerName(ownerName);
        // DMZ 2.2 uses an absolute game-tick deadline (its default summon lifetime is 12000 ticks).
        dragon.setSummonExpiresAt(level.getGameTime() + 12000L);
        dragon.setGrantedWish(false);
        dragon.moveTo(x, y, z, 0.0F, 0.0F);
        return level.addFreshEntity(dragon) ? Optional.of(dragon) : Optional.empty();
    }

    private static boolean supportedCoordinates(ServerLevel level, double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) < 30_000_000 && Math.abs(z) < 30_000_000
                && y >= level.getMinBuildHeight() && y < level.getMaxBuildHeight();
    }
}
