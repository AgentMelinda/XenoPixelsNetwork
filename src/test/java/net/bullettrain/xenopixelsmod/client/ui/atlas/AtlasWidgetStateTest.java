package net.bullettrain.xenopixelsmod.client.ui.atlas;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtlasWidgetStateTest {
    @Test
    void cycleWrapsInBothDirections() {
        AtlasCycleState state = new AtlasCycleState(List.of("a", "b", "c"), 0);
        state = state.cycle(-1);
        assertEquals("c", state.value());
        state = state.cycle(1);
        assertEquals("a", state.value());
    }

    @Test
    void tabIndexClamps() {
        assertEquals(0, AtlasTabState.clamp(-3, 4));
        assertEquals(3, AtlasTabState.clamp(9, 4));
    }

    private record AtlasCycleState(List<String> values, int index) {
        AtlasCycleState { index = Math.floorMod(index, values.size()); }
        String value() { return values.get(index); }
        AtlasCycleState cycle(int delta) { return new AtlasCycleState(values, index + delta); }
    }

    private record AtlasTabState() {
        static int clamp(int index, int count) { return Math.max(0, Math.min(count - 1, index)); }
    }
}