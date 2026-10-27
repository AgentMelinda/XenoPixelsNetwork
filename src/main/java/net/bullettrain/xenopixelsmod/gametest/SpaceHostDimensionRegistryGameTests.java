package net.bullettrain.xenopixelsmod.gametest;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Captures the GameTest server's dimension stems and instantiated levels to diagnose host-fixture gaps. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpaceHostDimensionRegistryGameTests {
    private SpaceHostDimensionRegistryGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 60, batch = "xenopixelsmod_space_host_dimension_registry")
    public static void reportsVoidseaStemAndLoadedLevelState(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        List<String> stems = server.registries().compositeAccess()
                .registryOrThrow(Registries.LEVEL_STEM).keySet().stream()
                .map(Object::toString).sorted().toList();
        List<String> levels = server.levelKeys().stream()
                .map(key -> key.location().toString()).sorted().toList();
        ResourceKey<Level> hostKey = ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST);
        boolean hostLevel = server.getLevel(hostKey) != null;
        XenoPixelsMod.LOGGER.info("Space host GameTest registry probe: voidseaStem={} voidseaLevel={} voidseaGetLevel={} stems={} loadedLevels={}",
                stems.contains(PhysicalEarth.HOST.toString()), levels.contains(PhysicalEarth.HOST.toString()),
                hostLevel, stems, levels);
        helper.succeed();
    }
}
