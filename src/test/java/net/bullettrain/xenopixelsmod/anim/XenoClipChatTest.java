package net.bullettrain.xenopixelsmod.anim;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoClipChatTest {

    @Test
    void prefersRawTextAndUnwrapsDecoratedName() {
        assertEquals("!cliplist", XenoClipChat.typedLine("!cliplist", "<Steve> !cliplist"));
        assertEquals("!cliplist", XenoClipChat.typedLine("", "<Steve> !cliplist"));
        assertEquals("!cliplist", XenoClipChat.typedLine(null, "<Steve> !cliplist"));
        assertEquals("!clip my_jab", XenoClipChat.typedLine("  !clip my_jab  ", "ignored"));
    }

    @Test
    void handlesClipFamilyOnly() {
        assertTrue(XenoClipChat.isHandled("!cliplist"));
        assertTrue(XenoClipChat.isHandled("!cliphelp"));
        assertTrue(XenoClipChat.isHandled("!clipstop"));
        assertTrue(XenoClipChat.isHandled("!clip my_jab 1.5"));
        assertFalse(XenoClipChat.isHandled("!ping"));
        assertFalse(XenoClipChat.isHandled("hello"));
        assertFalse(XenoClipChat.isHandled(""));
    }

    @Test
    void commandAndPartsSplitTheTypedLine() {
        assertEquals("cliplist", XenoClipChat.command("!cliplist"));
        assertEquals("clip", XenoClipChat.command("!clip my_jab 1.5 60 hold"));
        String[] parts = XenoClipChat.parts("!clip my_jab 1.5 60 hold");
        assertEquals(5, parts.length);
        assertEquals("clip", parts[0]);
        assertEquals("my_jab", parts[1]);
        assertEquals("hold", parts[4]);
    }

    @Test
    void chunkNamesStaysUnderLineLimit() {
        List<String> names = List.of("a", "b", "c");
        List<String> lines = XenoClipChat.chunkNames("Clips: ", names);
        assertEquals(1, lines.size());
        assertEquals("Clips: a, b, c", lines.getFirst());
        assertTrue(XenoClipChat.chunkNames("Clips: ", List.of()).isEmpty());
    }
}
