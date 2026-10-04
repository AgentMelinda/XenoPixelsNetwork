package net.bullettrain.xenopixelsmod.client.npc;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcSoundCatalogTest {
    @Test
    void sortsAndDeduplicatesRegistryIds() {
        assertEquals(List.of("minecraft:ambient.cave", "minecraft:entity.zombie.hurt",
                        "xenopixelsmod:npc.power_up"),
                NpcSoundCatalog.sortedIds(List.of(
                        ResourceLocation.parse("xenopixelsmod:npc.power_up"),
                        ResourceLocation.parse("minecraft:entity.zombie.hurt"),
                        ResourceLocation.parse("minecraft:ambient.cave"),
                        ResourceLocation.parse("minecraft:entity.zombie.hurt"))));
    }
}
