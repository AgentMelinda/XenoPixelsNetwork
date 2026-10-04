package net.bullettrain.xenopixelsmod.client.npc.dialog;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.client.npc.store.ClientNpcStoreIndex;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestCatalogChoicesTest {
    @Test
    void choicesShowTitlesAndPersistTheCanonicalQuestId() {
        List<QuestCatalogChoices.Choice> choices = QuestCatalogChoices.fromIndex(List.of(
                new ClientNpcStoreIndex.Entry(XenoNpcStoreCategory.QUESTS, "Main", "wolves",
                        "Hunt the Northern Wolves", 1),
                new ClientNpcStoreIndex.Entry(XenoNpcStoreCategory.DIALOGS, "", "talk",
                        "Not a quest", 1)), "wolves");

        assertEquals(1, choices.size());
        assertEquals("Hunt the Northern Wolves (wolves)", choices.get(0).label());
        assertEquals(0, QuestCatalogChoices.selectedIndex(choices, "wolves"));
    }

    @Test
    void aQuestThatDisappearsRemainsVisibleUntilReplaced() {
        List<QuestCatalogChoices.Choice> choices =
                QuestCatalogChoices.fromIndex(List.of(), "removed_quest");
        assertEquals("removed_quest", choices.get(0).id());
        assertEquals("Unavailable: removed_quest", choices.get(0).label());
    }

    @Test
    void theExistingIndexPacketIncludesServerQuestDefinitionsWithoutChangingItsWireShape()
            throws IOException {
        String packet = Files.readString(RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod",
                "network/packet/SyncNpcStoreIndexPacket.java"), StandardCharsets.UTF_8);
        assertTrue(packet.contains("ParallelQuests.ids()"));
        assertTrue(packet.contains("ParallelQuests.definition(id)"));
        assertTrue(packet.contains("new ClientNpcStoreIndex.Entry(XenoNpcStoreCategory.QUESTS"));
        assertTrue(packet.contains("MAX_ENTRIES = MAX_STORE_ENTRIES"),
                "keep the established packet count bound and packet ID contract");
    }
}
