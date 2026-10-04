package net.bullettrain.xenopixelsmod.client.maker;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.jupiter.api.Assertions.*;

/** Descriptors inspected in the pinned DragonMineZ dependency, without initializing game classes. */
class MakerMixinTargetTest {
    @Test void newMakerHooksMatchPinnedDragonMineZ() throws Exception {
        ClassNode character = read("com/dragonminez/common/stats/character/Character");
        assertTrue(character.methods.stream().anyMatch(m -> m.name.equals("getActiveFormData")
                && m.desc.equals("()Lcom/dragonminez/common/config/FormConfig$FormData;")));
        ClassNode customization = read("com/dragonminez/client/gui/character/CharacterCustomizationScreen");
        assertTrue(customization.methods.stream().anyMatch(m -> m.name.equals("initHairTab") && m.desc.equals("(I)V")));
        assertTrue(customization.fields.stream().anyMatch(f -> f.name.equals("character")
                && f.desc.equals("Lcom/dragonminez/common/stats/character/Character;")));
        ClassNode skin = read("com/dragonminez/client/util/SkinGathererProvider");
        assertTrue(skin.methods.stream().anyMatch(m -> m.name.equals("gatherTattooLayers")
                && m.desc.equals("(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/dragonminez/common/stats/StatsData;FLjava/util/function/BiConsumer;)V")));
    }

    private static ClassNode read(String name) throws Exception {
        try (var stream = MakerMixinTargetTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
            return node;
        }
    }
}
