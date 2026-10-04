package net.bullettrain.xenopixelsmod.data;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.RepositorySource;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModDatapacksTest {

    @Test
    void heightOverridePacksAreAvailableButNotAutoEnabled() {
        List<RepositorySource> repositorySources = new ArrayList<>();
        ModDatapacks.addPackFinders(new AddPackFindersEvent(
                PackType.SERVER_DATA,
                repositorySources::add,
                false));

        Map<String, Pack> packsById = new HashMap<>();
        repositorySources.forEach(source -> source.loadPacks(pack -> packsById.put(pack.getId(), pack)));

        Pack standardHeight = findPack(packsById, "xeno_standard_overworld");
        Pack maximumHeight = findPack(packsById, "xeno_max_overworld");
        Pack shipMasses = findPack(packsById, "xeno_ship_masses");

        assertFalse(standardHeight.getPackSource().shouldAddAutomatically());
        assertFalse(maximumHeight.getPackSource().shouldAddAutomatically());
        assertTrue(shipMasses.getPackSource().shouldAddAutomatically());
    }

    private static Pack findPack(Map<String, Pack> packsById, String path) {
        return packsById.values().stream()
                .filter(pack -> pack.getId().contains(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Built-in datapack was not discovered: " + path));
    }
}
