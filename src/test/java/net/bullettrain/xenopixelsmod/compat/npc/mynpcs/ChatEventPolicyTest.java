package net.bullettrain.xenopixelsmod.compat.npc.mynpcs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatEventPolicyTest {
    @Test
    void messageBoundMatchesVanillaChatLimit() {
        assertEquals(256, ChatEventPolicy.MAX_MESSAGE_LENGTH);
    }
}
