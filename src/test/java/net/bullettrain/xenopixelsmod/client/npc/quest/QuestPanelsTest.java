package net.bullettrain.xenopixelsmod.client.npc.quest;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the two panels show, and what they must not.
 *
 * <p>Rendering needs a running client, so these are source-level. Each names a rule the spec states
 * explicitly and that a plausible implementation would break silently.
 */
class QuestPanelsTest {

    private static String code(String file) throws IOException {
        String raw = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest", file),
                StandardCharsets.UTF_8);
        // Comments stripped: a test asserting "this call exists" must not be satisfied by a
        // comment that merely mentions it.
        return raw.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    // ------------------------------------------------------------ the quest panel

    @Test
    void thePanelReadsActiveQuestsOnly() throws IOException {
        // Spec section 3.1: the log groups ACTIVE quests. Completed ones are not synced at all,
        // so a panel reaching for them would be reaching for something that does not exist.
        String panel = code("QuestLogPanel.java");
        assertTrue(panel.contains("ClientQuests.all()"));
        assertFalse(panel.contains("completed"), "there is no completed list on the client");
    }

    @Test
    void notYetSyncedReadsDifferentlyFromNoQuests() {
        // Two states; one is "you have none", the other is "the server has not told us yet", and
        // showing the first for the second is a lie the player cannot detect.
        ClientQuests.clear();
        assertFalse(ClientQuests.isSynced());
        ClientQuests.accept(List.of());
        assertTrue(ClientQuests.isSynced());
    }

    @Test
    void thePanelDistinguishesThoseTwoStates() throws IOException {
        String panel = code("QuestLogPanel.java");
        assertTrue(panel.contains("ClientQuests.isSynced()"),
                "the panel must branch on whether the sync arrived");
        assertTrue(panel.contains("no active quests"),
                "and say the empty case out loud rather than drawing an empty box");
    }

    @Test
    void completeWithIsShownOnlyForAReadyQuestThatNamesSomebody() throws IOException {
        // An INSTANT quest has no completer. Printing "Complete with" and a blank name would send
        // the player looking for an NPC that does not exist.
        String panel = code("QuestLogPanel.java");
        int marker = panel.indexOf("Complete with");
        assertTrue(marker >= 0, "the NPC-mode hint should be present");
        String before = panel.substring(Math.max(0, marker - 400), marker);
        assertTrue(before.contains("completerNpc().isEmpty()"),
                "guarded on the quest actually naming a completer");
        assertTrue(before.contains("ready()"), "and on it actually being ready");
    }

    @Test
    void theObjectivesLineShowsProgressAgainstTarget() throws IOException {
        String panel = code("QuestLogPanel.java");
        assertTrue(panel.contains("progress()") && panel.contains("target()"),
                "a quest log without a progress count is not a quest log");
        assertTrue(panel.contains("Objectives"), "and the spec names the section");
    }

    @Test
    void pagingAndSortingGoThroughTheTestedHelpers() throws IOException {
        // Rather than an inline subList or a String.compareTo, which are where the dropped-tail
        // and the "Side 10 before Side 2" bugs live.
        String panel = code("QuestLogPanel.java");
        assertTrue(panel.contains("QuestLogLayout.paginate("));
        assertTrue(panel.contains("QuestLogLayout.categories("));
        assertTrue(panel.contains("QuestLogLayout.visibleSlice("),
                "long lists must draw only rows inside their viewport");
        assertTrue(panel.contains("QuestLogLayout.panelLeft("),
                "the log stays left of the inventory and item viewer");
        assertTrue(panel.contains("XenoAtlasSprites.blit(graphics, \"xeno_inventory_panel\""),
                "the journal uses the crisp native-size generated inventory frame");
        assertTrue(panel.contains("wrap(minecraft, selected.title(), detailWidth)"),
                "long quest titles wrap in the detail column");
        assertTrue(panel.contains("tooltip(graphics, minecraft, quest.title()"),
                "the quest list exposes the full title on hover");
    }

    @Test
    void theInventoryJournalConsumesWheelInputAndSuppressesNpcAura() throws IOException {
        String tabs = code("XenoInventoryTabs.java");
        assertTrue(tabs.contains("open == Tab.QUESTS"));
        assertTrue(tabs.contains("QuestLogPanel.scroll("));
        assertTrue(tabs.contains("MouseScrolled.Pre"));
        assertTrue(code("XenoInventoryTabs.java").contains("questLogOpen()"));
        String aura = Files.readString(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/client/compat/npc",
                "NpcAuraClient.java"), StandardCharsets.UTF_8);
        assertTrue(aura.contains("XenoInventoryTabs.questLogOpen()"));
    }

    @Test
    void aSelectionThatNoLongerExistsFallsBackRatherThanBlanking() throws IOException {
        // A quest completed while its own page was open must not leave the panel showing nothing
        // with no way back.
        assertTrue(code("QuestLogPanel.java").contains("categories.contains(selectedCategory)"),
                "a vanished category falls back to the first");
    }

    // ------------------------------------------------------------ the faction panel

    @Test
    void thePanelReadsThePlayersOwnStanding() throws IOException {
        // ClientFactions.Entry.defaultStanding() is the FACTION's default. Reading it as the
        // player's would show everyone on a server identical numbers and would look correct.
        String panel = code("FactionPanel.java");
        assertTrue(panel.contains("ClientStandings.of("),
                "the player's own standing, not the faction's default");
        assertFalse(panel.contains("attitudeAt(faction.defaultStanding())"),
                "classifying the default is the bug this guards");
    }

    @Test
    void itUsesOurOwnAttitudeVocabulary() throws IOException {
        // Theirs is Friendly / Neutral / Unfriendly; ours is FRIENDLY / NEUTRAL / HOSTILE, which
        // is already in XenoFaction.Attitude and already on the wire. A second vocabulary on the
        // screen only would mean two names for one state.
        String panel = code("FactionPanel.java");
        assertTrue(panel.contains("attitudeAt("),
                "standing should be classified by the shared rule, not a local threshold");
        assertFalse(panel.contains("Unfriendly"), "that is their word, not ours");
    }

    @Test
    void theFactionPanelHasNoControls() throws IOException {
        // Nothing on the client can change a standing, so a button here would be a dead control -
        // the standing rule is that a widget goes in only once something consumes its value.
        String panel = code("FactionPanel.java");
        assertFalse(panel.contains("Button.builder"), "read-only means no buttons");
        assertFalse(panel.contains("sendToServer"), "and nothing to send");
    }

    @Test
    void theEmptyFactionCaseSaysSomething() throws IOException {
        // Their copy is "You have no standings with any faction". An empty panel reads as broken.
        String panel = code("FactionPanel.java");
        assertTrue(panel.contains("ClientFactions.isEmpty()"));
        assertTrue(panel.contains("no standings"));
    }

    @Test
    void factionPanelSharesTheJournalSidePosition() throws IOException {
        assertTrue(code("FactionPanel.java").contains("QuestLogLayout.panelLeft("),
                "both E-screen side tabs belong on the left of the inventory");
    }

    @Test
    void theFactionListIsBoundedByThePanelHeight() throws IOException {
        // A server with 200 factions must not draw 200 rows down over the hotbar and the chat.
        assertTrue(code("FactionPanel.java").contains("PANEL_HEIGHT"),
                "the row loop should stop at the panel's own bottom edge");
    }

    // ------------------------------------------------------------ wiring

    @Test
    void questJournalUsesTheInventorySidePanelBesideFactionStandings() throws IOException {
        String tabs = code("XenoInventoryTabs.java");
        assertTrue(tabs.contains("FactionPanel.render("), "and so must the factions tab");
        assertTrue(tabs.contains("QuestLogPanel.render("), "the journal stays beside the inventory");
        assertTrue(code("QuestLogPanel.java").contains("XenoAtlasSprites.blit(graphics, \"xeno_inventory_panel\""),
                "the journal uses the generated faction-panel frame and native dimensions");
    }

    @Test
    void aClickThatHitsNothingIsNotSwallowed() throws IOException {
        // Cancelling unconditionally would stop the player picking up their own items while the
        // Quests tab happened to be open.
        String tabs = code("XenoInventoryTabs.java");
        int clickStart = tabs.indexOf("void onClick(");
        int clickEnd = tabs.indexOf("@SubscribeEvent\n    public static void onScroll(", clickStart);
        String click = tabs.substring(clickStart, clickEnd);
        int cancels = click.split("setCanceled\\(true\\)", -1).length - 1;
        assertEquals(2, cancels,
                "only a tab click is cancelled; all other clicks reach vanilla inventory slots");
    }

    @Test
    void completionPopupOptsOutOfMinecraftBackgroundBlur() throws IOException {
        String popup = Files.readString(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest",
                "QuestCompletionScreen.java"), StandardCharsets.UTF_8);
        assertTrue(popup.contains("extends UnblurredScreen"));
        assertTrue(popup.contains("XenoAtlasSprites.blit(graphics, frame, palette"));
        String background = Files.readString(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/client/screen",
                "UnblurredScreen.java"), StandardCharsets.UTF_8);
        assertTrue(background.contains("graphics.fill("),
                "the completion card dims the sharp world without invoking vanilla blur");
        assertFalse(background.contains("renderTransparentBackground("),
                "renderTransparentBackground enters Minecraft's blurred background path");
        String mixin = Files.readString(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/mixin/client",
                "ScreenDmzMenuBackgroundMixin.java"), StandardCharsets.UTF_8);
        assertTrue(mixin.contains("XenoInventoryTabs.sidePanelOpen()"),
                "the inventory journal/faction side panel also opts out of blur");
        assertFalse(mixin.contains("renderTransparentBackground(graphics)"));
    }

    @Test
    void newQuestToastUsesTheGeneratedAtlas() throws IOException {
        String toast = code("QuestToastOverlay.java");
        assertTrue(toast.contains("XenoAtlasSprites.blit(graphics, \"xeno_quest_toast\""));
        assertFalse(toast.contains("graphics.fill("), "the frame comes from generated atlas art");
    }

    @Test
    void questWritesUseTheKnownRevisionAndKeepTheDraftUntilTheServerAnswers() throws IOException {
        String editor = Files.readString(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                "XenoNpcEditorScreen.java"), StandardCharsets.UTF_8);
        assertTrue(editor.contains("ClientNpcStoreIndex.find("));
        assertTrue(editor.contains("expectedRevision, tag"));
        assertTrue(editor.contains("receiveStoreWriteResult(boolean saved, String reason)"));
        assertTrue(editor.contains("your draft is still here"));
        assertTrue(editor.contains("AtlasTextFit.groupScale(group, 0.55f)"));
        assertTrue(editor.contains("lineTextScales.getOrDefault(p.y()"));
    }
}
