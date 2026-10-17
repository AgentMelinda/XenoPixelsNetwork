package net.bullettrain.xenopixelsmod.space.dragonball;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WishCatalogTest {
    private static WishCatalog.Wish parse(String json) {
        return WishCatalog.parse("t:wish", JsonParser.parseString(json).getAsJsonObject());
    }

    @Test void parsesAllRewardKindsAndClamps() {
        var wish = parse("{\"name\":\"W\",\"rewards\":[{\"type\":\"item\",\"id\":\"minecraft:diamond\",\"count\":99999},"
                + "{\"type\":\"effect\",\"id\":\"minecraft:speed\",\"seconds\":10,\"amplifier\":50},{\"type\":\"xp\",\"levels\":5},"
                + "{\"type\":\"command\",\"command\":\"say hi @wisher\"}]}");
        assertEquals(4, wish.rewards().size());
        assertEquals(4096, wish.rewards().get(0).amount());
        assertEquals(9, wish.rewards().get(1).amplifier());
        assertEquals(5, wish.rewards().get(2).amount());
        assertEquals("say hi @wisher", wish.rewards().get(3).command());
        assertEquals("", wish.description());
    }

    @Test void rejectsMalformedWishes() {
        assertThrows(RuntimeException.class, () -> parse("{\"rewards\":[{\"type\":\"xp\"}]}"));
        assertThrows(RuntimeException.class, () -> parse("{\"name\":\"W\",\"rewards\":[]}"));
        assertThrows(RuntimeException.class, () -> parse("{\"name\":\"W\",\"rewards\":[{\"type\":\"teleport\"}]}"));
        assertThrows(RuntimeException.class, () -> parse("{\"name\":\"W\",\"rewards\":[{\"type\":\"item\",\"id\":\"Not Valid!\"}]}"));
        assertThrows(RuntimeException.class, () -> parse("{\"name\":\"" + "x".repeat(65) + "\",\"rewards\":[{\"type\":\"xp\"}]}"));
    }

    @Test void bundledWishesParse() throws Exception {
        Path dir = net.bullettrain.xenopixelsmod.RepoRoot.of("src/main/resources/data/xenopixelsmod/xenospace/wish");
        int count = 0;
        try (var files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                var wish = WishCatalog.parse("xenopixelsmod:" + file.getFileName(), JsonParser.parseString(Files.readString(file)).getAsJsonObject());
                assertFalse(wish.rewards().isEmpty());
                count++;
            }
        }
        assertTrue(count >= 5 && count <= WishCatalog.MAX_WISHES);
    }
}
