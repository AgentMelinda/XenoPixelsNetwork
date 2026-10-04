package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Night texture, and the rule that re-enabled it.
 *
 * <p>The field existed before any of this, but nothing read it - both renderers went straight to
 * {@code modelTexture} - so the editor row was a dead control and was correctly excluded from the
 * server's editable-key whitelist. These tests pin the two halves of putting it back: a real
 * consumer, and only then the key.
 *
 * <p>The day/night branch itself needs a {@code Level}, which needs a running game, so what is
 * checked here is the null case and the wiring. The swap is verified in game.
 */
class NpcNightTextureTest {

    private static String source(String relative) throws IOException {
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative);
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void withNoLevelTheDayTextureIsUsed() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.modelTexture = "xenopixelsmod:textures/entity/day.png";
        profile.nightTexture = "xenopixelsmod:textures/entity/night.png";

        // A null level is "we do not know what time it is", and the day skin is the safe answer.
        assertEquals(profile.modelTexture, profile.textureFor(null));
    }

    @Test
    void anUnsetNightTextureNeverReplacesTheDayOne() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.modelTexture = "xenopixelsmod:textures/entity/day.png";
        assertEquals("", profile.nightTexture, "the shipped default is unset");
        assertEquals(profile.modelTexture, profile.textureFor(null));
    }

    @Test
    void bothRenderersGoThroughTheSharedResolver() throws IOException {
        // Two renderers resolve textures independently. If one reads modelTexture directly it will
        // disagree with the other after dusk, which is the reason this lives on the profile.
        for (String file : new String[]{"client/npc/XenoNpcRenderer.java",
                "client/npc/XenoNpcGeoModel.java"}) {
            String text = source(file);
            assertTrue(text.contains("textureFor("),
                    file + " must resolve its texture through NpcCombatProfile.textureFor");
        }
    }

    @Test
    void theKeyIsEditableOnlyBecauseTheConsumerExists() throws IOException {
        String policy = source("../../../../../main/java/net/bullettrain/xenopixelsmod"
                .replace("../../../../../main/java/net/bullettrain/xenopixelsmod", "")
                + "network/packet/XenoNpcSavePolicy.java");
        assertTrue(policy.contains("\"NightTexture\""),
                "NightTexture should be back on the editable-key whitelist");

        String profile = source("compat/npc/NpcCombatProfile.java");
        assertTrue(profile.contains("public String textureFor("),
                "and the consumer that earned it should still be there");
    }
}
