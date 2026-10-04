package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which way the form-stacking check fails.
 *
 * <p>Found by the project skill's audit in {@code --strict-evidence} mode, which asks of every
 * broad catch around an external call whether it fails closed for permissions, packets, saves and
 * gameplay authority. Four such catches were checked by hand; three already failed closed. This one
 * did not.
 *
 * <p>{@code compatible()} answered <b>true</b> on a throw - "these forms may stack". So a
 * DragonMineZ change that broke {@code isIncompatibleWith} would let an NPC stack two forms a pack
 * had declared incompatible, and say nothing, because the exception was swallowed. Refusing a stack
 * is visible and recoverable; granting one a pack forbade is neither.
 *
 * <p>Asserted against the source rather than by calling it: the method takes DragonMineZ
 * {@code FormConfig.FormData}, which cannot be constructed in a unit test without the mod.
 */
class NpcFormCompatibilityTest {

    private static String lookup() throws IOException {
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/compat/npc",
                "NpcFormLookup.java");
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** The body of {@code compatible}, which is the only method this test is about. */
    private static String compatibleBody() throws IOException {
        String source = lookup();
        int at = source.indexOf("public static boolean compatible(");
        assertTrue(at >= 0, "the method should exist");
        int end = source.indexOf("\n    }", at);
        assertTrue(end > at, "its body should be findable");
        return source.substring(at, end);
    }

    @Test
    void aFailedCheckRefusesTheStack() throws IOException {
        String body = compatibleBody();
        int katch = body.indexOf("catch (");
        assertTrue(katch >= 0, "there should be a catch around the dependency call");

        String after = body.substring(katch);
        assertTrue(after.contains("return false;"),
                "a check that could not run must refuse, not permit");
        assertFalse(after.contains("return true;"),
                "returning true here is the fail-open this replaced");
    }

    @Test
    void theRefusalIsLoggedRatherThanSwallowed() throws IOException {
        // A silent refusal is as hard to diagnose as a silent permit, and this only fires when a
        // dependency has moved under us - exactly when somebody needs to be told.
        String body = compatibleBody();
        assertFalse(body.contains("catch (Throwable ignored)"),
                "the exception should not be discarded unnamed");
        assertTrue(body.contains("LOGGER.warn"), "and the reason should reach the log");
    }

    @Test
    void anAbsentFormIsStillCompatible() throws IOException {
        // Distinct from a failed check: a null form is "nothing to be incompatible with", which is
        // a legitimate yes and must not be swept into the refusal.
        String body = compatibleBody();
        assertTrue(body.contains("if (normal == null || stack == null) return true;"),
                "the null case should keep answering yes");
    }

    @Test
    void theCallersTreatFalseAsARefusal() throws IOException {
        // The fix only means something if false actually stops the stack.
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/compat/npc",
                "NpcTransformSystem.java");
        String transform = Files.readString(path, StandardCharsets.UTF_8);
        for (String guard : List.of("if (!NpcFormLookup.compatible(")) {
            assertTrue(transform.contains(guard),
                    "the callers should gate on the negation, so false refuses");
        }
    }
}
