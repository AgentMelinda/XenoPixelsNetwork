package net.bullettrain.xenopixelsmod.dmz.form;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmzFormSearchTest {
    private static final List<String> GROUPS =
            List.of("superforms", "legendaryforms", "godforms", "xeno_custom");

    @Test
    void aBlankQueryKeepsEveryEntry() {
        assertSame(GROUPS, DmzFormSearch.filter(GROUPS, ""));
        assertSame(GROUPS, DmzFormSearch.filter(GROUPS, "   "));
        assertSame(GROUPS, DmzFormSearch.filter(GROUPS, null));
    }

    @Test
    void matchingIsCaseInsensitiveSubstringAndKeepsSourceOrder() {
        assertEquals(List.of("superforms", "legendaryforms", "godforms"),
                DmzFormSearch.filter(GROUPS, "FORMS"));
        assertEquals(List.of("xeno_custom"), DmzFormSearch.filter(GROUPS, "  Xeno  "));
        assertEquals(List.of("legendaryforms"), DmzFormSearch.filter(GROUPS, "legend"));
    }

    @Test
    void noMatchesYieldsAnEmptyListRatherThanTheWholeSet() {
        assertTrue(DmzFormSearch.filter(GROUPS, "zzz").isEmpty());
        assertTrue(DmzFormSearch.filter(null, "anything").isEmpty());
    }
}
