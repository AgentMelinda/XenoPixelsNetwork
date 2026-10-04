package net.bullettrain.xenopixelsmod.client.npc.speech;

import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BubbleTextTest {
    @Test
    void legacyCodesBecomeStyledSpansInsteadOfVisibleGlyphs() {
        var text = BubbleText.styled("§aHello§r world");
        assertEquals("Hello world", text.getString());
        assertEquals(ChatFormatting.GREEN.getColor(),
                text.getSiblings().get(0).getStyle().getColor().getValue());
    }
}
