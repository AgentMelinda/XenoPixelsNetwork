package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcCnpcQuestsTest {
    @Test
    void listLineUsesNpcPrefix() {
        assertEquals("npc:12 — Hunt the saibamen",
                NpcCnpcQuests.formatListLine(new NpcCnpcQuests.Entry(12, "Hunt the saibamen")));
    }
}
