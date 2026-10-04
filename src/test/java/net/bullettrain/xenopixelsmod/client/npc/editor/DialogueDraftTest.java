package net.bullettrain.xenopixelsmod.client.npc.editor;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueNbt;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writing a dialogue from the editor.
 *
 * <p>A draft exists because {@link XenoDialogue} will not hold a conversation mid-edit: a node just
 * added has no name yet, and a start pointing at a node about to be renamed resolves to nothing.
 * The record is right to refuse that and an editor is obliged to allow it, so the two are separate
 * and convert at the boundaries.
 */
class DialogueDraftTest {

    @Test
    void editingStoredLinesPreservesTheirPalette() {
        DialogueDraft draft = DialogueDraft.of(null);
        draft.addNode();
        draft.node(0).palette = "GOLD";
        DialogueDraft reopened = DialogueDraft.of(draft.toTag());
        assertEquals("GOLD", reopened.node(0).palette);
        assertEquals("GOLD", XenoDialogueNbt.read(reopened.toTag())
                .nodes().get("root").palette());
    }

    @Test
    void eachAnswerPaletteSurvivesSaveAndReload() {
        DialogueDraft draft = DialogueDraft.of(null);
        draft.addNode();
        DialogueDraft.Option angel = new DialogueDraft.Option();
        angel.text = "Help";
        angel.palette = "GOLD";
        draft.node(0).options.add(angel);
        DialogueDraft.Option demon = new DialogueDraft.Option();
        demon.text = "Fight";
        demon.palette = "RED";
        draft.node(0).options.add(demon);

        DialogueDraft reloaded = DialogueDraft.of(draft.toTag());
        assertEquals("GOLD", reloaded.node(0).options.get(0).palette);
        assertEquals("RED", reloaded.node(0).options.get(1).palette);
    }

    @Test
    void answersWrittenBeforePerAnswerColorsInheritTheNpcSetting() {
        DialogueDraft draft = DialogueDraft.of(null);
        draft.addNode();
        draft.node(0).options.add(new DialogueDraft.Option());

        assertEquals("", DialogueDraft.of(draft.toTag()).node(0).options.get(0).palette);
    }

    @Test
    void anEmptyDraftIsTheNormalStartingPoint() {
        DialogueDraft draft = DialogueDraft.of(null);
        assertTrue(draft.isEmpty());
        assertTrue(draft.problems().isEmpty(), "nothing written yet is not a problem");
        assertNull(XenoDialogueNbt.read(draft.toTag()),
                "and it must not shadow the role's dialogue");
    }

    @Test
    void theFirstLineAddedBecomesTheStart() {
        // Otherwise a new dialogue would be unopenable until the author found the start cycler,
        // which is exactly the kind of invisible prerequisite this screen is written to avoid.
        DialogueDraft draft = DialogueDraft.of(null);
        int index = draft.addNode();
        assertEquals(0, index);
        assertEquals("root", draft.node(0).id);
        assertEquals("root", draft.start());
    }

    @Test
    void laterLinesGetFreeNamesRatherThanClashing() {
        DialogueDraft draft = DialogueDraft.of(null);
        draft.addNode();
        draft.addNode();
        draft.addNode();
        assertEquals(List.of("root", "node_2", "node_3"), draft.nodeIds());
        assertTrue(draft.problems().stream().noneMatch(p -> p.startsWith("Two nodes")));
    }

    @Test
    void renamingALineMovesEveryLinkIntoIt() {
        // The failure this prevents is the quiet one: rename a node and every option pointing at
        // it dead-ends while the dialogue still looks valid.
        DialogueDraft draft = DialogueDraft.of(null);
        draft.addNode();
        draft.addNode();
        draft.node(0).options.add(new DialogueDraft.Option());
        draft.node(0).options.get(0).target = "node_2";

        draft.renameNode(1, "Ending Line!");
        assertEquals("ending_line", draft.node(1).id, "ids are keys, so they are sanitised");
        assertEquals("ending_line", draft.node(0).options.get(0).target);
        assertTrue(draft.problems().isEmpty(), draft.problems().toString());
    }

    @Test
    void renamingTheStartLineMovesTheStart() {
        DialogueDraft draft = DialogueDraft.of(null);
        draft.addNode();
        draft.renameNode(0, "opening");
        assertEquals("opening", draft.start());
        assertTrue(draft.problems().isEmpty(), draft.problems().toString());
    }

    @Test
    void deletingTheStartLinePromotesTheNextOne() {
        DialogueDraft draft = DialogueDraft.of(null);
        draft.addNode();
        draft.addNode();
        draft.removeNode(0);
        assertEquals("node_2", draft.start());
        assertTrue(draft.problems().isEmpty(), draft.problems().toString());
    }

    @Test
    void deletingTheLastLineLeavesNoDialogueRatherThanABrokenOne() {
        DialogueDraft draft = DialogueDraft.of(null);
        draft.addNode();
        draft.removeNode(0);
        assertTrue(draft.isEmpty());
        assertEquals("", draft.start());
        assertNull(XenoDialogueNbt.read(draft.toTag()));
    }

    @Test
    void problemsNameWhatIsMissing() {
        DialogueDraft draft = DialogueDraft.of(null);
        draft.addNode();
        DialogueDraft.Option dangling = new DialogueDraft.Option();
        dangling.target = "missing";
        DialogueDraft.Option quest = new DialogueDraft.Option();
        quest.type = XenoDialogue.OptionType.QUEST;
        DialogueDraft.Option command = new DialogueDraft.Option();
        command.type = XenoDialogue.OptionType.COMMAND;
        draft.node(0).options.add(dangling);
        draft.node(0).options.add(quest);
        draft.node(0).options.add(command);

        List<String> problems = draft.problems();
        assertTrue(problems.contains("root option 1 points at missing node missing."), problems.toString());
        assertTrue(problems.contains("root option 2 names no quest."), problems.toString());
        assertTrue(problems.contains("root option 3 runs no command."), problems.toString());
    }

    @Test
    void aQuitOptionNeedsNothing() {
        DialogueDraft draft = DialogueDraft.of(null);
        draft.addNode();
        DialogueDraft.Option quit = new DialogueDraft.Option();
        quit.type = XenoDialogue.OptionType.QUIT;
        draft.node(0).options.add(quit);
        assertTrue(draft.problems().isEmpty(), draft.problems().toString());
    }

    @Test
    void anUnfinishedDraftStillSavesAndStillFallsBack() {
        // Saving has to keep a half-written conversation, because half-written is the normal state
        // of one being written. What it must not do is quietly become the NPC's dialogue while
        // broken - the reader answers null, so the NPC keeps using its role's.
        DialogueDraft draft = DialogueDraft.of(null);
        draft.addNode();
        draft.setStart("nowhere");
        assertFalse(draft.problems().isEmpty());
        assertNotNull(draft.toTag(), "the edit is kept");
        assertNull(XenoDialogueNbt.read(draft.toTag()), "but it does not take effect yet");
    }

    @Test
    void aFinishedDraftRoundTripsThroughTheStoredTag() {
        DialogueDraft draft = DialogueDraft.of(null);
        draft.addNode();
        draft.node(0).text = "So you want to train, {player}?";
        draft.addNode();
        draft.node(1).text = "Then begin.";
        DialogueDraft.Option go = new DialogueDraft.Option();
        go.text = "Tell me more";
        go.target = "node_2";
        draft.node(0).options.add(go);

        assertTrue(draft.problems().isEmpty(), draft.problems().toString());

        DialogueDraft reloaded = DialogueDraft.of(draft.toTag());
        assertEquals(List.of("root", "node_2"), reloaded.nodeIds());
        assertEquals("root", reloaded.start());
        assertEquals("So you want to train, {player}?", reloaded.node(0).text);
        assertEquals("Tell me more", reloaded.node(0).options.get(0).text);
        assertEquals("node_2", reloaded.node(0).options.get(0).target);
    }

    @Test
    void theCapsHold() {
        DialogueDraft draft = DialogueDraft.of(null);
        for (int i = 0; i < DialogueDraft.MAX_NODES; i++) {
            assertTrue(draft.addNode() >= 0);
        }
        assertEquals(-1, draft.addNode(), "past the cap it refuses rather than growing");

        for (int i = 0; i < DialogueDraft.MAX_OPTIONS; i++) {
            assertTrue(draft.addOption(0) >= 0);
        }
        assertEquals(-1, draft.addOption(0));
    }

    @Test
    void anIndexHeldAcrossADeletionAnswersNullRatherThanThrowing() {
        // The screen keeps a node index across a rebuild, so it can outlive the node.
        DialogueDraft draft = DialogueDraft.of(null);
        draft.addNode();
        draft.removeNode(0);
        assertNull(draft.node(0));
        assertEquals(-1, draft.addOption(0));
        draft.removeOption(0, 0);
        draft.renameNode(0, "x");
    }

    @Test
    void theEditorScreensAreWiredToTheDraft() throws IOException {
        // The screen was once twelve disabled "Select Option" rows saying a writable registry was
        // required, and this page replaced them with a per-NPC dialogue tree on the grounds that
        // the dialogue only had to live somewhere writable.
        //
        // Both halves are now true at once. The world store gave us a writable registry, so the
        // twelve slots are real and assign from a shared library - which is My NPCs' model and
        // what an author actually wants, because twenty guards can then share one greeting. The
        // per-NPC tree is still here, one level down at ADVANCED_DIALOG_OWN: NPCs already
        // carrying one must not lose it.
        String editor = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                        "XenoNpcEditorScreen.java"), StandardCharsets.UTF_8);
        assertTrue(editor.contains("case ADVANCED_DIALOGS -> dialogSlotRows()"),
                "the Dialogs page is the twelve-slot grid");
        assertTrue(editor.contains("case ADVANCED_DIALOG_OWN -> dialogueRows()"),
                "and the per-NPC tree is still reachable");
        assertTrue(editor.contains("case ADVANCED_DIALOG_NODE -> dialogueNodeRows()"));
        assertTrue(editor.contains("case ADVANCED_DIALOG_OPTION -> dialogueOptionRows()"));
        assertTrue(editor.contains("Create or edit shared dialogs"));
        assertTrue(editor.contains("returnToAdvancedDialogs = true"));
        assertTrue(editor.contains("Return to Advanced dialogs"));
        int route = editor.indexOf("Create or edit shared dialogs");
        assertTrue(route >= 0);
        assertTrue(editor.substring(route, route + 250).contains("openPage(ScreenId.GLOBAL_DIALOGS)"));
        int returnAction = editor.indexOf("Return to Advanced dialogs");
        assertTrue(editor.substring(returnAction, returnAction + 220)
                .contains("openPage(ScreenId.ADVANCED_DIALOGS)"));
        assertFalse(editor.contains("unavailableSelectorRows(\"Dialog Options\""),
                "the disabled placeholder should be gone");
    }

    @Test
    void answersChooseAColorAndQuestOptionsChooseFromTheGlobalCatalog() throws IOException {
        String editor = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                        "XenoNpcEditorScreen.java"), StandardCharsets.UTF_8);
        assertTrue(editor.contains("new EditorRow.Cycle(\"Bubble colour\""));
        assertTrue(editor.contains("option.palette = selected == 0 ? \"\""));
        assertTrue(editor.contains("QuestCatalogChoices.fromIndex("));
        assertTrue(editor.contains("new EditorRow.Cycle(\"Quest\""));
        assertFalse(editor.contains("fullField(\"Quest\", option.quest"),
                "authors choose an existing global quest rather than mistyping its id");
        assertTrue(editor.contains("ENTITY_TYPE.keySet()"));
        assertTrue(editor.contains("QuestObjective.KILL_NPC"));
        assertTrue(editor.contains("npcNames"));
    }

    @Test
    void everyEditPushesTheDraftOntoTheProfile() throws IOException {
        // The draft is what the sub-screens read back and the profile is what the save packet
        // serialises. Letting them drift is how a field looks edited and saves its old value.
        String editor = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                        "XenoNpcEditorScreen.java"), StandardCharsets.UTF_8);
        int at = editor.indexOf("private void commitDialogue()");
        assertTrue(at >= 0, "there should be one place that commits");
        String body = editor.substring(at, at + 400);
        assertTrue(body.contains("if (editingStoreDialogue)"),
                "shared dialogs are saved to the world store, not the NPC profile");
        assertTrue(body.contains("profile.setDialogue(dialogueDraft.toTag())"));
        assertTrue(body.contains("markDirty(\"Dialogue\")"));
    }
}
