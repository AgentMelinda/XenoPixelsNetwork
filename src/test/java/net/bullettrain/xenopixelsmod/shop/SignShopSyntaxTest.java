package net.bullettrain.xenopixelsmod.shop;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignShopSyntaxTest {

    @Test
    void parsesFourLineForm() {
        SignShopData data = SignShopSyntax.parse(new String[]{
                "[XPSHOP]", "minecraft:diamond", "64x", "100"
        });
        assertNotNull(data);
        assertEquals(ResourceLocation.parse("minecraft:diamond"), data.targetId());
        assertEquals(64, data.quantity());
        assertEquals(100.0, data.price());
    }

    @Test
    void parsesCompactFormOnLineOne() {
        SignShopData data = SignShopSyntax.parse(new String[]{
                "[XPSHOP] minecraft:stone 1x 12.50", "", "", ""
        });
        assertNotNull(data);
        assertEquals(ResourceLocation.parse("minecraft:stone"), data.targetId());
        assertEquals(1, data.quantity());
        assertEquals(12.5, data.price());
    }

    @Test
    void markerMatchIsCaseInsensitiveAndTrimmed() {
        assertNotNull(SignShopSyntax.parse(new String[]{
                "  [xpshop]  ", "minecraft:diamond", "1x", "5"
        }));
    }

    @Test
    void acceptsOptionalCurrencyTagAfterPrice() {
        SignShopData data = SignShopSyntax.parse(new String[]{
                "[XPSHOP]", "minecraft:diamond", "1x", "100 zp"
        });
        assertNotNull(data);
        assertEquals(100.0, data.price());
    }

    @Test
    void rejectsNonShopSigns() {
        assertNull(SignShopSyntax.parse(null));
        assertNull(SignShopSyntax.parse(new String[]{"hello", "world", "1x", "5"}));
        assertNull(SignShopSyntax.parse(new String[]{"[XPSHOP]"}));
    }

    @Test
    void rejectsBadTargetQuantityAndPrice() {
        assertNull(SignShopSyntax.parse(new String[]{"[XPSHOP]", "not a valid id!", "1x", "5"}));
        assertNull(SignShopSyntax.parse(new String[]{"[XPSHOP]", "minecraft:diamond", "0x", "5"}));
        assertNull(SignShopSyntax.parse(new String[]{"[XPSHOP]", "minecraft:diamond", "10000x", "5"}));
        assertNull(SignShopSyntax.parse(new String[]{"[XPSHOP]", "minecraft:diamond", "1", "5"}));
        assertNull(SignShopSyntax.parse(new String[]{"[XPSHOP]", "minecraft:diamond", "1x", "-5"}));
        assertNull(SignShopSyntax.parse(new String[]{"[XPSHOP]", "minecraft:diamond", "1x", "abc"}));
    }

    @Test
    void roundTripsThroughSerialize() {
        SignShopData original = new SignShopData(
                ResourceLocation.parse("xenopixelsmod:wing_panel"), 32, 250.0);
        String[] lines = SignShopSyntax.serialize(original);
        assertEquals("[XPSHOP]", lines[0]);
        assertEquals("xenopixelsmod:wing_panel", lines[1]);
        assertEquals("32x", lines[2]);
        assertEquals("250", lines[3]);
        assertEquals(original, SignShopSyntax.parse(lines));
    }

    @Test
    void isShopSignTracksMarkerPresence() {
        assertTrue(SignShopSyntax.isShopSign(new String[]{"[XPSHOP]", "x", "y", "z"}));
        assertTrue(SignShopSyntax.isShopSign(new String[]{"[XPSHOP] garbage", "", "", ""}));
        assertFalse(SignShopSyntax.isShopSign(new String[]{"plain", "", "", ""}));
        assertFalse(SignShopSyntax.isShopSign(null));
    }
}