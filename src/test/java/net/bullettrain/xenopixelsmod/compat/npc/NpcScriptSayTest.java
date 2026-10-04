package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcScriptSayTest {
    @Test
    void rejectsNullEmptyAndOverlong() {
        assertNull(NpcScriptSay.sanitize(null));
        assertNull(NpcScriptSay.sanitize(""));
        assertNull(NpcScriptSay.sanitize("x".repeat(NpcScriptSay.MAX_MESSAGE_LENGTH + 1)));
    }

    @Test
    void acceptsBoundAndKeepsText() {
        String text = "Welcome back.";
        assertEquals(text, NpcScriptSay.sanitize(text));
        String max = "x".repeat(NpcScriptSay.MAX_MESSAGE_LENGTH);
        assertEquals(max, NpcScriptSay.sanitize(max));
    }

    @Test
    void rewriteOnlyWhenScriptChangedTheTypedLine() {
        assertFalse(NpcScriptSay.shouldRewrite("hello", "hello"));
        assertFalse(NpcScriptSay.shouldRewrite("hello", null));
        assertFalse(NpcScriptSay.shouldRewrite(null, ""));
        assertTrue(NpcScriptSay.shouldRewrite("hello", "[NPC] hello"));
        assertTrue(NpcScriptSay.shouldRewrite("", "hi"));
    }

    @Test
    void decoratedUsesVanillaChatTypeKey() {
        Component line = NpcScriptSay.decorated(Component.literal("Steve"), "hello");
        assertInstanceOf(TranslatableContents.class, line.getContents());
        TranslatableContents contents = (TranslatableContents) line.getContents();
        assertEquals(NpcScriptSay.CHAT_TYPE_KEY, contents.getKey());
        assertEquals(2, contents.getArgs().length);
    }

    @Test
    void emptyKeyWritebackMatchesMyNpcsSetMessage() {
        Component writeback = Component.translatable("").append("[Xeno] hello");
        assertTrue(NpcScriptSay.isEmptyKeyWriteback(writeback));
        assertEquals("[Xeno] hello", writeback.getString());
    }

    @Test
    void emptyKeyWritebackRejectsLiteralAndVanillaChatType() {
        assertFalse(NpcScriptSay.isEmptyKeyWriteback(null));
        assertFalse(NpcScriptSay.isEmptyKeyWriteback(Component.literal("#hello")));
        assertFalse(NpcScriptSay.isEmptyKeyWriteback(
                NpcScriptSay.decorated(Component.literal("Steve"), "[Xeno] hello")));
    }

    @Test
    void chatCancelFlagIsConsumedOnce() {
        assertFalse(NpcScriptSay.consumeChatCancelled());
        NpcScriptSay.markChatCancelled();
        assertTrue(NpcScriptSay.consumeChatCancelled());
        assertFalse(NpcScriptSay.consumeChatCancelled());
    }

    @Test
    void cancelScriptChatMarksThreadLocalEvenWithoutSetCanceled() {
        assertFalse(NpcScriptSay.cancelScriptChat(null));
        assertFalse(NpcScriptSay.consumeChatCancelled());
        assertTrue(NpcScriptSay.cancelScriptChat(new Object()));
        assertTrue(NpcScriptSay.consumeChatCancelled());
        assertFalse(NpcScriptSay.isScriptChatCanceled(new Object()));
    }

    @Test
    void typedLinePrefersRawAndUnwrapsDecoratedName() {
        assertEquals("#hello", NpcScriptSay.typedLine("#hello", "<Steve> #hello"));
        assertEquals("#hello", NpcScriptSay.typedLine("", "<Steve> #hello"));
        assertEquals("#hello", NpcScriptSay.typedLine(null, "<Steve> #hello"));
        assertEquals("!cliplist", NpcScriptSay.typedLine("", "<Steve> !cliplist"));
        assertEquals("!ping", NpcScriptSay.typedLine(null, "<Alex> !ping"));
    }

    @Test
    void resolveChatMessageUsesTypedLineUntilScriptRewrites() {
        NpcScriptSay.clearTypedLine();
        try {
            NpcScriptSay.markTypedLine("#hello");
            assertEquals("#hello", NpcScriptSay.resolveChatMessage(""));
            assertEquals("#hello", NpcScriptSay.resolveChatMessage("#hello"));
            assertEquals("#hello", NpcScriptSay.resolveChatMessage("<Steve> #hello"));
            assertEquals("[Xeno] hello", NpcScriptSay.resolveChatMessage("[Xeno] hello"));
        } finally {
            NpcScriptSay.clearTypedLine();
        }
    }

    @Test
    void resolveChatMessageUnwrapsWhenTypedLineMissing() {
        NpcScriptSay.clearTypedLine();
        assertEquals("#hello", NpcScriptSay.resolveChatMessage("<Steve> #hello"));
        assertEquals("plain", NpcScriptSay.resolveChatMessage("plain"));
    }

    @Test
    void auraPulsePhaseIsContinuousAndDoesNotResetOnAGap() {
        float a = NpcAuraAnim.pulsePhase(10, 0.0f);
        float b = NpcAuraAnim.pulsePhase(13, 0.0f);
        assertTrue(a >= 0.0f && a < 1.0f);
        assertTrue(b >= 0.0f && b < 1.0f);
        assertEquals(0.03f, b - a, 0.0001f);
        assertEquals(NpcAuraAnim.pulsePhase(110, 0.0f), NpcAuraAnim.pulsePhase(10, 0.0f), 0.0001f);
    }
}
