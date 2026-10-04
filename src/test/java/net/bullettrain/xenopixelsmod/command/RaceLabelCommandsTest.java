package net.bullettrain.xenopixelsmod.command;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Operator-facing race label + grant command. Registering Brigadier needs a server,
 * so these pin the source contract.
 */
class RaceLabelCommandsTest {
    private static String code() throws IOException {
        return Files.readString(RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/command",
                        "RaceLabelCommands.java"), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    void commandIsXenoraceWithNameDescGiveAndList() throws IOException {
        String src = code();
        assertTrue(src.contains("Commands.literal(\"xenorace\")"));
        assertTrue(src.contains("Commands.literal(\"name\")"));
        assertTrue(src.contains("Commands.literal(\"desc\")"));
        assertTrue(src.contains("Commands.literal(\"give\")"));
        assertTrue(src.contains("Commands.literal(\"list\")"));
        assertTrue(src.contains("StringArgumentType.greedyString()"),
                "display name and description are greedy literals, not translation keys");
    }

    @Test
    void literalsStayOutOfEnUs() throws IOException {
        String src = code();
        assertFalse(src.contains("en_us.json"));
        assertTrue(src.contains("xeno_labels.json"));
        assertTrue(src.contains("RACE_EDIT"));
        assertTrue(src.contains("RACE_GIVE"));
    }
}
