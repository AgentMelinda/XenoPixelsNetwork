package net.bullettrain.xenopixelsmod.gametest;

import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.wish.DragonWishRegistry;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.dmz.SuperShenronDefinition;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Super Shenron data bridge on a real server after DMZ's reload: the definition is registered, the server wish
 * list is non-empty, and DMZ still picks Shenron/Porunga for their own balls. Run with ./gradlew runGameTestServer.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SuperShenronGameTests {
    private SuperShenronGameTests() {}

    @GameTest(template = "empty")
    public static void superShenronDataIsLoadedAndDmzSelectionIsUnchanged(GameTestHelper helper) {
        var def = DragonBallDefinitions.getDragon(SuperShenronDefinition.ID);
        helper.assertTrue(def != null, "super_shenron definition registered");
        helper.assertTrue("shenron".equals(def.getEntityRegistryName()), "reuses dragonminez:shenron");
        var wishes = DragonWishRegistry.getServerWishes().get(SuperShenronDefinition.ID);
        helper.assertTrue(wishes != null && !wishes.isEmpty(), "server wish list non-empty: " + wishes);
        assertSelected(helper, "earth", Level.OVERWORLD, "shenron");
        assertSelected(helper, "namek",
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("dragonminez", "namek")), "porunga");
        var voidsea = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "voidsea"));
        var earthInVoidsea = DragonBallDefinitions.getDragonForSetAndDimension("earth", voidsea);
        helper.assertTrue(earthInVoidsea == null || !SuperShenronDefinition.ID.equals(earthInVoidsea.getId()),
                "Earth balls never summon super_shenron");
        XenoPixelsMod.LOGGER.info("SuperShenron GameTest PASS: definition registered, {} server wish(es), Earth/Namek selection unchanged",
                wishes.size());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void planetarySummonBridgeCreatesOwnerBoundDmzWishEntity(GameTestHelper helper) {
        var level = helper.getLevel();
        var spawn = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        double x = spawn.getX() + 0.5;
        double y = Math.max(level.getMinBuildHeight() + 1.0, Math.min(spawn.getY() + 1.0, level.getMaxBuildHeight() - 2.0));
        double z = spawn.getZ() + 0.5;
        var result = SuperShenronDefinition.spawnWishEntity(level, "SdbGameTestOwner", x, y, z);
        helper.assertTrue(result.isPresent(), "supported summon creates the existing DMZ Shenron entity");
        var dragon = result.orElseThrow();
        helper.assertTrue(dragon.getDragonDefinition() != null
                        && SuperShenronDefinition.ID.equals(dragon.getDragonDefinition().getId()),
                "spawned DMZ wish entity resolves the custom Super Shenron definition");
        helper.assertTrue("SdbGameTestOwner".equals(dragon.getOwnerName()), "wish entity is bound to the summoning owner");
        helper.assertTrue(!dragon.hasGrantedWish(), "wish selection remains open on spawn");
        helper.assertTrue(SuperShenronDefinition.spawnWishEntity(level, "SdbGameTestOwner", Double.NaN, y, z).isEmpty(),
                "non-finite spawn coordinates are refused");
        helper.assertTrue(SuperShenronDefinition.spawnWishEntity(level, " ", x, y, z).isEmpty(),
                "blank owner is refused");
        XenoPixelsMod.LOGGER.info("SuperShenron summon bridge GameTest PASS: entity={}, definition={}, owner={}",
                dragon.getUUID(), dragon.getDragonDefinitionId(), dragon.getOwnerName());
        helper.succeed();
    }

    private static void assertSelected(GameTestHelper helper, String ballSet, ResourceKey<Level> dimension, String expected) {
        var picked = DragonBallDefinitions.getDragonForSetAndDimension(ballSet, dimension);
        helper.assertTrue(picked != null && expected.equals(picked.getId()),
                ballSet + " in " + dimension.location() + " picks " + expected + ", got " + (picked == null ? null : picked.getId()));
    }
}
