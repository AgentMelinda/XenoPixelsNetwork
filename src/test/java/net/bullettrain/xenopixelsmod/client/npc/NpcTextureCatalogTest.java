package net.bullettrain.xenopixelsmod.client.npc;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NpcTextureCatalogTest {
    @Test void geoPathsBecomeEditorModelIdsAcrossAnyNamespace() {
        var id = NpcTextureCatalog.geoToModelId(
                ResourceLocation.fromNamespaceAndPath("coolmodels", "geo/entity/saiyan/base.geo.json"));
        assertEquals("coolmodels", id.getNamespace());
        assertEquals("entity/saiyan/base", id.getPath());
        assertEquals("Entity", NpcTextureCatalog.category(id));
    }

    @Test void textureEntityPathsStayEntityCategory() {
        assertEquals("Entity", NpcTextureCatalog.category(
                ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/sagas/foo.png")));
    }
}
