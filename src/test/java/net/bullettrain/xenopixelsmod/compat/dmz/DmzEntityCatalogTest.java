package net.bullettrain.xenopixelsmod.compat.dmz;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalog behind {@code /xenostructure summon master|saga|mob|quest}.
 *
 * <p>The grouping itself reads {@code BuiltInRegistries.ENTITY_TYPE}, which needs a running game, so
 * what is pinned here is everything that does not: the quest-giver identities, and the exclusion
 * list of projectiles checked against DragonMineZ's own registrations in the decompiled source.
 *
 * <p>That last check is the one that matters over time. DragonMineZ adding an entity should widen
 * the command on its own, which is the point of reading the registry - but adding a new
 * <em>projectile</em> would quietly make it summonable, and nothing else would notice.
 */
class DmzEntityCatalogTest {

    private static final Path MAIN_ENTITIES = RepoRoot.of(
            "tools/generated/dmz_decompiled_full/com/dragonminez/common/init/MainEntities.java");

    private static final Path CATALOG = RepoRoot.of(
            "src/main/java/net/bullettrain/xenopixelsmod/compat/dmz/DmzEntityCatalog.java");

    /** A double quote, built without an escape so the source stays easy to edit by script. */
    private static final char QUOTE = '"';

    /**
     * Every id DragonMineZ registers, read from its own decompiled registry class.
     *
     * <p>The decompiler wraps these calls, so the id sits on the line after {@code register(}
     * rather than beside it. Hence "find the call, then the next quoted string" instead of one
     * literal match.
     */
    private static Set<String> registeredIds() throws IOException {
        String source = Files.readString(MAIN_ENTITIES, StandardCharsets.UTF_8);
        Set<String> ids = new TreeSet<>();
        String mark = "register(";
        int at = source.indexOf(mark);
        while (at >= 0) {
            int open = source.indexOf(QUOTE, at + mark.length());
            int close = open < 0 ? -1 : source.indexOf(QUOTE, open + 1);
            if (open >= 0 && close > open) {
                ids.add(source.substring(open + 1, close));
            }
            at = source.indexOf(mark, at + mark.length());
        }
        return ids;
    }

    /**
     * The quoted strings in {@code block}.
     *
     * <p>Every second piece of a split on the quote character is what was inside a pair of them;
     * the pieces in between are the separators, which is why this steps by two rather than taking
     * everything.
     */
    private static Set<String> quotedIn(String block) {
        Set<String> ids = new TreeSet<>();
        String[] parts = block.split(String.valueOf(QUOTE), -1);
        for (int i = 1; i < parts.length; i += 2) {
            if (!parts[i].isBlank()) {
                ids.add(parts[i]);
            }
        }
        return ids;
    }

    @Test
    void theDecompiledSourceIsWhereItIsExpectedToBe() {
        assertTrue(Files.exists(MAIN_ENTITIES),
                "the decompiled DragonMineZ source this check reads is missing: " + MAIN_ENTITIES);
    }

    @Test
    void dragonMineZRegistersTheNumberOfEntitiesThisWasBuiltAgainst() throws IOException {
        // A sanity anchor. If this moves, DragonMineZ changed its entity set and the groups below
        // are worth re-reading rather than trusted.
        assertEquals(204, registeredIds().size(),
                "DragonMineZ's entity count changed; re-check the catalog's grouping");
    }

    @Test
    void everyExcludedIdIsRealAndEveryProjectileIsExcluded() throws IOException {
        Set<String> registered = registeredIds();

        // Read the exclusion list out of our own source, so the two cannot drift apart.
        String catalog = Files.readString(CATALOG, StandardCharsets.UTF_8);
        int start = catalog.indexOf("NOT_AN_NPC = Set.of(");
        assertTrue(start >= 0, "the exclusion list should still be there");
        String block = catalog.substring(start, catalog.indexOf(");", start));
        Set<String> excluded = quotedIn(block);

        assertEquals(14, excluded.size(), "expected the fourteen projectiles and effects");
        for (String id : excluded) {
            assertTrue(registered.contains(id),
                    id + " is excluded but DragonMineZ does not register it");
        }

        // And nothing that looks like a projectile escaped the list.
        for (String id : registered) {
            if (id.startsWith("ki_") || id.startsWith("sp_")) {
                assertTrue(excluded.contains(id),
                        id + " looks like a projectile but is still summonable");
            }
        }
    }

    @Test
    void theGroupPrefixesMatchDragonMineZsOwnNaming() throws IOException {
        Set<String> registered = registeredIds();
        assertEquals("master_", DmzEntityCatalog.Group.MASTER.prefix());
        assertEquals("saga_", DmzEntityCatalog.Group.SAGA.prefix());
        assertEquals("", DmzEntityCatalog.Group.MOB.prefix(), "MOB is the catch-all");

        assertEquals(23, registered.stream().filter(id -> id.startsWith("master_")).count());
        assertEquals(144, registered.stream().filter(id -> id.startsWith("saga_")).count());
    }

    @Test
    void questNpcIsARealEntityAndTheIdentitiesAreNot() throws IOException {
        Set<String> registered = registeredIds();
        assertTrue(registered.contains("quest_npc"),
                "the quest giver path spawns dragonminez:quest_npc");

        // The distinction the command rests on: "bulma" is an identity carried by quest_npc, not an
        // entity type. saga_bulma is a different thing - a story fighter with no dialogue.
        for (String identity : DmzEntityCatalog.QUEST_IDENTITIES) {
            assertFalse(registered.contains(identity),
                    identity + " should be a quest identity, not an entity type");
        }
        assertTrue(registered.contains("saga_bulma"),
                "saga_bulma exists separately and is summoned with 'summon saga bulma'");
    }

    @Test
    void theThirteenShippedQuestGiversAreAllThere() {
        List<String> expected = List.of("bulma", "dende", "gohan", "goku", "guru", "kingkai",
                "krillin", "piccolo", "popo", "roshi", "trunks", "vegeta", "yamcha");
        assertEquals(expected, DmzEntityCatalog.QUEST_IDENTITIES,
                "read from the quest_giver fields of DragonMineZ's own sidequest JSONs");
    }

    @Test
    void anIdentityIsRecognisedRegardlessOfCaseOrPadding() {
        assertTrue(DmzEntityCatalog.isQuestIdentity("bulma"));
        assertTrue(DmzEntityCatalog.isQuestIdentity("  BULMA  "));
        assertFalse(DmzEntityCatalog.isQuestIdentity("saga_bulma"));
        assertFalse(DmzEntityCatalog.isQuestIdentity("chi_chi"));
        assertFalse(DmzEntityCatalog.isQuestIdentity(null));
        assertFalse(DmzEntityCatalog.isQuestIdentity(""));
    }

    @Test
    void theQuestNpcIdIsNamespacedToDragonMineZ() {
        assertEquals("dragonminez", DmzEntityCatalog.QUEST_NPC.getNamespace());
        assertEquals("quest_npc", DmzEntityCatalog.QUEST_NPC.getPath());
        assertEquals("dragonminez", DmzEntityCatalog.NAMESPACE);
    }
}
