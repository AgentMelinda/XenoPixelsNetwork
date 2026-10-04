package net.bullettrain.xenopixelsmod.npc.store;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every store category has a reader or a documented reservation, and the contract is written down.
 *
 * <p>Eight of ten categories had no reader and no stated purpose. They are kept rather than
 * deleted — a stable directory layout is worth the cost, because formats nobody pinned down is
 * exactly what left the importer unable to import six of them — but "kept" and "unexplained" are
 * different things, and this is what keeps them apart.
 */
class StoreContractTest {

    private static String categorySource() throws IOException {
        return Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/npc/store",
                        "XenoNpcStoreCategory.java"), StandardCharsets.UTF_8);
    }

    /** Whether anything outside the enum itself names this category. */
    private static boolean hasReader(XenoNpcStoreCategory category) throws IOException {
        try (Stream<Path> paths = Files.walk(RepoRoot.of("src/main/java"))) {
            return paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("XenoNpcStoreCategory.java"))
                    .anyMatch(p -> {
                        try {
                            return Files.readString(p, StandardCharsets.UTF_8)
                                    .contains("XenoNpcStoreCategory." + category.name());
                        } catch (IOException e) {
                            return false;
                        }
                    });
        }
    }

    @Test
    void everyCategoryHasEitherAReaderOrAStatedReservation() throws IOException {
        String source = categorySource();
        for (XenoNpcStoreCategory category : XenoNpcStoreCategory.values()) {
            if (hasReader(category)) {
                continue;
            }
            // No reader, so it must say what it is for and what would unblock it. A category with
            // neither is dead weight that nobody can tell from an oversight.
            int declaration = source.indexOf(category.name() + "(\"");
            assertTrue(declaration > 0, category.name() + " should be declared");
            String javadoc = source.substring(Math.max(0, declaration - 1200), declaration);
            assertTrue(javadoc.contains("Reserved"),
                    category.name() + " has no reader and no reservation");
        }
    }

    @Test
    void transportIsLiveNow() throws IOException {
        // It was one of the eight. TransportNetworks is its reader.
        assertTrue(hasReader(XenoNpcStoreCategory.TRANSPORT),
                "transport destinations moved into the store on 2026-09-23");
    }

    @Test
    void playerdataSaysItIsPermanentlyEmpty() throws IOException {
        // Unlike every other reservation, nothing unblocks this one - XenoPlayerData owns
        // per-player state, and a second writer with no rule for which wins is worse than one.
        String source = categorySource();
        int declaration = source.indexOf("PLAYERDATA(\"");
        String javadoc = source.substring(Math.max(0, declaration - 1400), declaration);
        assertTrue(javadoc.contains("permanently empty"));
        assertTrue(javadoc.contains("XenoPlayerData"), "and names which store wins");
    }

    @Test
    void theOwnershipRuleIsWrittenDownWithItsTest() throws IOException {
        // The question that got answered wrong for transports.
        String doc = Files.readString(RepoRoot.of("docs", "xeno-npc-schema.md"),
                StandardCharsets.UTF_8);
        assertTrue(doc.contains("Would two NPCs ever want to share this"),
                "the decidable test must be in the doc, not only in a spec");
        assertTrue(doc.contains("ambient lines stay per-NPC"),
                "and the counter-example, so it reads as a decision rather than an oversight");
    }

    @Test
    void theSyncContractIsWrittenDownWithItsWorkedExample() throws IOException {
        String doc = Files.readString(RepoRoot.of("docs", "xeno-npc-schema.md"),
                StandardCharsets.UTF_8);
        assertTrue(doc.contains("An id, never a value"),
                "what the client may send back");
        assertTrue(doc.contains("No factions yet"),
                "and the bug that proves why a mutation push is needed");
    }
}
