package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmallPlanetVoxelsTest {
    private static SmallPlanetField.Candidate body(SmallPlanetField.Profile profile, long seed, int radius) {
        return new SmallPlanetField.Candidate(
                new SmallPlanetField.ObjectId(new UniverseField.Cell(1, -2), 0, 0, 0),
                seed, 0, 512, 0, radius, profile, 0.8, false);
    }

    @Test void eachProfileBuildsASeedStableSolidSphereWithLayeredInterior() {
        for (var profile : SmallPlanetField.Profile.values()) {
            var candidate = body(profile, 0x51A11L, 12);
            var first = new SmallPlanetVoxels(candidate);
            var second = new SmallPlanetVoxels(candidate);
            assertEquals(12, first.extent());
            assertEquals(Blocks.DEEPSLATE.defaultBlockState(), first.at(0, 0, 0));
            assertNotNull(first.at(0, 0, 12));
            assertNull(first.at(0, 0, 13));
            for (int x = -12; x <= 12; x++) for (int y = -12; y <= 12; y++) for (int z = -12; z <= 12; z++)
                assertEquals(first.at(x, y, z), second.at(x, y, z), "profile " + profile + " at " + x + ',' + y + ',' + z);
        }
    }

    @Test void oreRichnessChangesTheSeededOreAmountWithoutChangingTheSilhouette() {
        var candidate = body(SmallPlanetField.Profile.ROCK, 0xAA5511L, 18);
        var barren = new SmallPlanetVoxels(new SmallPlanetField.Candidate(candidate.id(), candidate.seed(),
                candidate.x(), candidate.y(), candidate.z(), candidate.radius(), candidate.profile(), 0, false));
        var rich = new SmallPlanetVoxels(new SmallPlanetField.Candidate(candidate.id(), candidate.seed(),
                candidate.x(), candidate.y(), candidate.z(), candidate.radius(), candidate.profile(), 1, false));
        int barrenOres = 0, richOres = 0;
        for (int x = -18; x <= 18; x++) for (int y = -18; y <= 18; y++) for (int z = -18; z <= 18; z++) {
            assertEquals(barren.at(x, y, z) == null, rich.at(x, y, z) == null);
            if (isOre(barren.at(x, y, z))) barrenOres++;
            if (isOre(rich.at(x, y, z))) richOres++;
        }
        assertTrue(barrenOres < richOres);
    }

    private static boolean isOre(net.minecraft.world.level.block.state.BlockState state) {
        return state != null && state.getBlock().getDescriptionId().contains("_ore");
    }
}
