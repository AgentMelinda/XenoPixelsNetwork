package net.bullettrain.xenopixelsmod.npc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-09-28 owner request: colours in NPC names and titles ("&6Goku", "&#FF8800Elite"). */
class XenoNpcNameFormatTest {
    @Test
    void plainTextDropsColourCodes() {
        assertEquals("Goku", XenoNpcNameFormat.plain("&6Goku"));
        assertEquals("Super Saiyan", XenoNpcNameFormat.plain("&e&lSuper &r§bSaiyan"));
        assertEquals("Elite", XenoNpcNameFormat.plain("&#FF8800Elite"));
        assertEquals("R&D 100%", XenoNpcNameFormat.plain("R&D 100%"));
        assertEquals("", XenoNpcNameFormat.plain(null));
    }

    @Test
    void componentsCarryTheColours() {
        Component goku = XenoNpcNameFormat.component("&6Goku");
        assertEquals("Goku", goku.getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), goku.getSiblings().get(0).getStyle().getColor());

        Component hex = XenoNpcNameFormat.component("&#FF8800Elite &lGuard");
        assertEquals("Elite Guard", hex.getString());
        assertEquals(0xFF8800, hex.getSiblings().get(0).getStyle().getColor().getValue());
        assertTrue(hex.getSiblings().get(1).getStyle().isBold());
        assertEquals(0xFF8800, hex.getSiblings().get(1).getStyle().getColor().getValue(), "bold keeps the colour");
    }

    @Test
    void aColourResetsFormattingAndResetClearsEverything() {
        Component c = XenoNpcNameFormat.component("&lBold&cRed&rPlain");
        assertTrue(c.getSiblings().get(0).getStyle().isBold());
        assertFalse(c.getSiblings().get(1).getStyle().isBold());
        assertNull(c.getSiblings().get(2).getStyle().getColor());
        assertEquals("BoldRedPlain", c.getString());
    }
}
