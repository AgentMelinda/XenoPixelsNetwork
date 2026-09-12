package net.bullettrain.xenopixelsmod.dmz.form;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DmzHairTypesTest {

    @Test
    void cyclesTheFourVerifiedFormHairTypes() {
        assertEquals("ssj", DmzHairTypes.cycle("base", 1));
        assertEquals("ssj2", DmzHairTypes.cycle("ssj", 1));
        assertEquals("ssj3", DmzHairTypes.cycle("ssj2", 1));
        assertEquals("base", DmzHairTypes.cycle("ssj3", 1));
    }

    @Test
    void wrapsBackwardsAndTreatsUnknownAsBase() {
        assertEquals("ssj3", DmzHairTypes.cycle("base", -1));
        assertEquals("base", DmzHairTypes.cycle("not-a-type", 0));
        assertEquals("ssj", DmzHairTypes.cycle(null, 1));
    }
}
