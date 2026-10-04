package net.bullettrain.xenopixelsmod.client.npc.speech;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillboardDrawTest {
    @Test
    void outlineColorOverridesFormattedRunColorsButKeepsFormatting() {
        var sequence = Component.literal("Hello")
                .withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true))
                .getVisualOrderText();
        List<Integer> colors = new ArrayList<>();
        List<Boolean> bold = new ArrayList<>();

        BillboardDraw.forceColor(sequence, 0x000000).accept((index, style, codePoint) -> {
            colors.add(style.getColor().getValue());
            bold.add(style.isBold());
            return true;
        });

        assertEquals(List.of(0, 0, 0, 0, 0), colors);
        assertEquals(List.of(true, true, true, true, true), bold);
    }
}
