package net.bullettrain.xenopixelsmod.features.progression;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcDialoguePacket;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestDialogueFilterTest {
    @Test
    void visibleQuestAfterUnavailableQuestKeepsItsServerIdentity() {
        XenoDialogue source = XenoDialogue.fromJson(JsonParser.parseString("""
                {
                  "start": "root",
                  "nodes": {
                    "root": {
                      "text": "Choose",
                      "palette": "GOLD",
                      "options": [
                        {"text": "Locked", "type": "quest", "quest": "example:locked"},
                        {"text": "Tell me more", "type": "text", "target": "more"},
                        {"text": "Available", "type": "quest", "quest": "example:available",
                         "palette": "RED"}
                      ]
                    },
                    "more": {"text": "More", "options": []}
                  }
                }
                """));

        XenoDialogue visible = QuestDialogueFilter.filterUnavailable(source,
                option -> !"example:locked".equals(option.quest()));
        XenoDialogue.Option selected = visible.startNode().options().get(1);
        int serverIndex = selected.sourceIndex();
        XenoDialogue.Option serverSelected = source.startNode().options().get(serverIndex);

        assertEquals(2, serverIndex);
        assertEquals(XenoDialogue.OptionType.QUEST, serverSelected.type());
        assertEquals("example:available", serverSelected.quest());
        assertEquals("RED", selected.palette(), "filtering must preserve the answer palette");
        assertEquals("GOLD", visible.startNode().palette(),
                "filtering must preserve the speaker palette too");

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        new OpenXenoNpcDialoguePacket(1, "id", "npc", visible).encode(buffer);
        XenoDialogue clientDialogue = new OpenXenoNpcDialoguePacket(buffer).dialogue();
        assertEquals("RED", clientDialogue.startNode().options().get(1).palette(),
                "the visible red answer must survive filtering and the client packet together");
        assertTrue(!"example:locked".equals(serverSelected.quest()),
                "the hidden earlier quest must not be selected by the visible quest click");
    }

    @Test
    void aClientSuppliedIndexMustMatchAnOptionTheServerStillOffers() {
        String packet = source("network/packet/XenoNpcDialoguePacket.java");

        assertTrue(packet.contains("node.options().get(optionIndex)"));
        assertTrue(packet.contains("QuestDialogueFilter.canOffer("));
        assertTrue(packet.contains("ParallelQuests.start(player, questId, giver)"));
    }

    private static String source(String relative) {
        try {
            return java.nio.file.Files.readString(net.bullettrain.xenopixelsmod.RepoRoot
                    .of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }
}
