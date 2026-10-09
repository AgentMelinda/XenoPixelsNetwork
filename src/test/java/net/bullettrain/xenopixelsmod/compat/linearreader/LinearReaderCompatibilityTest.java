package net.bullettrain.xenopixelsmod.compat.linearreader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LinearReaderCompatibilityTest {
    @Test void onlyVerifiedBuildsCanActivateStorageRestrictions() {
        assertTrue(LinearReaderCompatibility.supports("1.3.0"));
        assertTrue(LinearReaderCompatibility.supports("1.3.1"));
        for (String unknown : new String[]{null, "", "1.2.1", "1.3.10", "1.3.1-beta", "1.4.0", "2.0.0"}) {
            assertFalse(LinearReaderCompatibility.supports(unknown), "Must refuse unverified storage build: " + unknown);
        }
    }
}
