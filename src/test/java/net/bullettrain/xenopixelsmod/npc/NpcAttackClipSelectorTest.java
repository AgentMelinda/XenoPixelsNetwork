package net.bullettrain.xenopixelsmod.npc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NpcAttackClipSelectorTest {
    @Test
    void honorsAuthoredClipOnCustomRig() {
        assertEquals("my_rig.slash", NpcAttackClipSelector.select(
                List.of("idle", "attack", "my_rig.slash"), "my_rig.slash"));
    }

    @Test
    void choosesOnlyClipsPresentOnThatRig() {
        assertEquals("attack1_1", NpcAttackClipSelector.select(
                List.of("idle", "attack1_1"), "missing"));
        assertEquals("model.heavy_attack", NpcAttackClipSelector.select(
                List.of("idle", "model.heavy_attack"), ""));
        assertNull(NpcAttackClipSelector.select(List.of("idle", "walk"), "attack"));
    }
}
