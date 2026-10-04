package net.bullettrain.xenopixelsmod.dmz.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TextureCounter custom-layered path: {@code textures/entity/races/<id>/<id>_<gender>_<i>_layer1.png}
 * when customModel is empty, hasGender is true, isLayered is true.
 */
class RaceCatalogPathsTest {

    @Test
    void layeredLayer1MatchesTextureCounterCustomRace() {
        assertEquals(
                "textures/entity/races/xeno_foo/xeno_foo_male_1_layer1.png",
                RaceCatalogPaths.layeredLayer1("xeno_foo", "male", 1));
        assertEquals(
                "textures/entity/races/xeno_foo/xeno_foo_female_2_layer1.png",
                RaceCatalogPaths.layeredLayer1("Xeno_Foo", "FEMALE", 2));
    }

    @Test
    void resourcePackRelativeUsesDragonminezNamespace() {
        assertEquals(
                "assets/dragonminez/textures/entity/races/xeno_foo/xeno_foo_male_1_layer1.png",
                RaceCatalogPaths.resourcePackRelative("xeno_foo", "male", 1));
    }
}
