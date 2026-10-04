package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Linked NPCs: the consumer that turned a stored list back into a live control.
 *
 * <p>The list has been on the profile for a while, but nothing walked it, so the editor row was a
 * dead control and the key was correctly kept off the server's editable-key whitelist. What is
 * pinned here is the id handling and the two rules that keep propagation safe; the copy itself
 * needs a running server and two entities, so it is checked in game.
 */
class XenoNpcLinkPropagationTest {

    private static String source(String relative) throws IOException {
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative);
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void aRealUuidParsesAndAnythingElseAnswersNull() {
        UUID id = UUID.randomUUID();
        assertEquals(id, XenoNpcLinkPropagation.parse(id.toString()));
        assertEquals(id, XenoNpcLinkPropagation.parse("  " + id + "  "));

        // A hand-edited datapack or an old save can hold anything. One bad id is a dead link, not
        // a failed save, so this answers null rather than throwing.
        assertNull(XenoNpcLinkPropagation.parse(null));
        assertNull(XenoNpcLinkPropagation.parse(""));
        assertNull(XenoNpcLinkPropagation.parse("   "));
        assertNull(XenoNpcLinkPropagation.parse("not-a-uuid"));
        assertNull(XenoNpcLinkPropagation.parse("1234"));
    }

    @Test
    void theEditorAndTheServerAgreeOnWhatCountsAsAnId() throws IOException {
        // Two spellings of "is this a UUID" would let the editor store an id the server then
        // silently skips, which reads in game as a link that does nothing.
        String editor = source("client/npc/XenoNpcEditorScreen.java");
        assertTrue(editor.contains("XenoNpcLinkPropagation.parse("),
                "the editor should validate with the same parser the server resolves with");
    }

    @Test
    void propagationFollowsExactlyOneHop() throws IOException {
        // Two NPCs linked to each other is the normal case, not a mistake. Following links
        // transitively would bounce the same edit between them forever, so the guard is that the
        // walk is one level deep - there is no recursion and no visited set to get wrong.
        String propagation = source("npc/XenoNpcLinkPropagation.java");
        assertTrue(propagation.contains("One hop"), "the one-hop rule should be stated");
        assertEquals(0, countOf(propagation, "propagate("),
                "propagate should never call itself");
    }

    @Test
    void aLockedTargetRefusesALinkedEdit() throws IOException {
        // Otherwise the editing lock would be bypassable by editing anything linked to it.
        String propagation = source("npc/XenoNpcLinkPropagation.java");
        assertTrue(propagation.contains("editingLocked"),
                "a locked NPC must refuse a linked edit too");
    }

    @Test
    void anUnreachableLinkIsSkippedRatherThanPruned() throws IOException {
        // An NPC in an unloaded chunk is not a dead link. Pruning here would quietly break a link
        // the moment someone saved while the other NPC was out of range.
        String propagation = source("npc/XenoNpcLinkPropagation.java");
        assertTrue(propagation.contains("skipped rather than dropped"),
                "the skip-not-prune rule should be stated where it is relied on");
        assertEquals(0, countOf(propagation, "links.remove("),
                "propagation must never edit the link list");
    }

    @Test
    void onlyKeysTheSavePolicyAlreadyAcceptedAreCopied() throws IOException {
        // A linked NPC must never receive a field the editor is not allowed to write directly.
        String packet = source("network/packet/XenoNpcSavePacket.java");
        int validate = packet.indexOf("XenoNpcSavePolicy.validate(");
        int propagate = packet.indexOf("XenoNpcLinkPropagation.propagate(");
        assertTrue(validate >= 0 && propagate > validate,
                "propagation must happen after the policy has accepted the payload");
    }

    @Test
    void theLinkListRoundTripsAndIgnoresBlanks() {
        NpcCombatProfile profile = new NpcCombatProfile();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        profile.linkedNpcs.add(a.toString());
        profile.linkedNpcs.add("");
        profile.linkedNpcs.add(b.toString());

        NpcCombatProfile back = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals(2, back.linkedNpcs.size(), "blank entries should not be stored");
        assertEquals(a.toString(), back.linkedNpcs.get(0));
        assertEquals(b.toString(), back.linkedNpcs.get(1));
        assertNotNull(XenoNpcLinkPropagation.parse(back.linkedNpcs.get(0)));
    }

    private static int countOf(String text, String needle) {
        int n = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            n++;
            at = text.indexOf(needle, at + needle.length());
        }
        // The declaration itself is one occurrence; the question is whether there are others.
        return Math.max(0, n - 1);
    }
}
