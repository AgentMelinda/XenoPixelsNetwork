package net.bullettrain.xenopixelsmod.npc.store;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.npc.dialog.NpcDialogSlots;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueNbt;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedDialogueAuthoringTest {
    @Test
    void aSharedDialogueCanBeCreatedLoadedAndAssignedUsingTheNativeStoreSchema() throws IOException {
        var root = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod");
        var editor = Files.readString(root.resolve("client/npc/XenoNpcEditorScreen.java"),
                StandardCharsets.UTF_8);
        var packet = Files.readString(root.resolve("network/packet/XenoNpcStoreWritePacket.java"),
                StandardCharsets.UTF_8);
        var source = Files.readString(root.resolve("npc/store/XenoNpcDataSource.java"),
                StandardCharsets.UTF_8);

        assertTrue(editor.contains("Create or edit shared dialogs"));
        assertTrue(editor.contains("XenoNpcStoreCategory.DIALOGS"));
        assertTrue(packet.contains("resolved == XenoNpcStoreCategory.DIALOGS"));
        assertTrue(source.contains("XenoDialogueNbt.read(store.get(XenoNpcStoreCategory.DIALOGS"));

        XenoDialogue greeting = new XenoDialogue("root", Map.of("root",
                new XenoDialogue.Node("A quest awaits.", List.of(
                        new XenoDialogue.Option("I'll help", XenoDialogue.OptionType.QUEST,
                                "", "example:quest", "")))));
        var stored = XenoDialogueNbt.write(greeting);
        XenoNpcWorldStore worldStore = new XenoNpcWorldStore(
                java.nio.file.Files.createTempDirectory("advanced-dialog-authoring"));
        assertNull(worldStore.put(XenoNpcStoreCategory.DIALOGS, "quest_givers", "greeting", stored));
        assertEquals("A quest awaits.", XenoNpcDataSource.assignedDialogue(worldStore,
                "quest_givers", "greeting").startNode().text());

        NpcDialogSlots slots = new NpcDialogSlots();
        slots.set(0, "quest_givers", "greeting");
        assertEquals("greeting", slots.primary().id());
    }
}
