package net.bullettrain.xenopixelsmod.command;

import net.bullettrain.xenopixelsmod.combat.v3.V3Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.JsonParser;
import static org.junit.jupiter.api.Assertions.*;

class V3ConfigCommandTest {
    @TempDir Path directory;
    private final V3Config.Values previous = V3Config.get();
    @AfterEach void restore() { V3Config.apply(previous); }

    @Test void protectedInvalidConfigReturnsFailureAndRestoresEffectiveSettingsWithoutBroadcast() throws Exception {
        Path path = directory.resolve("combat.json");
        for (String original : new String[]{"{broken json", "{\"version\":1.5,\"addon\":true}"}) {
            Files.writeString(path, original);
            V3Config.apply(previous);
            var successes = new ArrayList<String>();
            var failures = new ArrayList<String>();
            var broadcasts = new AtomicInteger();
            int result = XenoConfigCommands.setV3("v3.dragonDashRange", "999",
                    () -> V3Config.persist(path), broadcasts::incrementAndGet, successes::add, failures::add);
            assertEquals(0, result);
            assertEquals(previous, V3Config.get());
            assertEquals(original, Files.readString(path));
            assertEquals(0, broadcasts.get());
            assertTrue(successes.isEmpty());
            assertEquals(1, failures.size());
            assertTrue(failures.getFirst().contains("not saved"));
            assertTrue(failures.getFirst().contains("restored"));
            assertTrue(failures.getFirst().contains("xenopixelsmod-combat-v3.json"));
        }
    }

    @Test void filesystemRefusalReturnsFailureWithoutActivatingTheSetting() throws Exception {
        Path blocker = directory.resolve("not-a-directory");
        Files.writeString(blocker, "preserve me");
        var successes = new ArrayList<String>();
        var failures = new ArrayList<String>();
        var broadcasts = new AtomicInteger();
        int result = XenoConfigCommands.setV3("dragonDashRange", "999",
                () -> V3Config.persist(blocker.resolve("combat.json")), broadcasts::incrementAndGet,
                successes::add, failures::add);
        assertEquals(0, result);
        assertEquals(previous, V3Config.get());
        assertEquals("preserve me", Files.readString(blocker));
        assertEquals(0, broadcasts.get());
        assertTrue(successes.isEmpty());
        assertEquals(1, failures.size());
    }

    @Test void successfulCommandPersistsEffectiveClampBeforeFeedbackAndSync() throws Exception {
        Path path = directory.resolve("combat.json");
        var successes = new ArrayList<String>();
        var failures = new ArrayList<String>();
        var broadcasts = new AtomicInteger();
        int result = XenoConfigCommands.setV3("dragonDashRange", "1001", () -> V3Config.persist(path), () -> {
            assertTrue(Files.exists(path), "sync only follows a completed config write");
            broadcasts.incrementAndGet();
        }, successes::add, failures::add);
        assertEquals(1, result);
        assertEquals(999, V3Config.get().dragonDashRange());
        assertEquals(999, JsonParser.parseString(Files.readString(path)).getAsJsonObject().get("dragonDashRange").getAsDouble());
        assertEquals(1, broadcasts.get());
        assertTrue(failures.isEmpty());
        assertEquals(1, successes.size());
        assertTrue(successes.getFirst().endsWith("= 999"));
    }
}
