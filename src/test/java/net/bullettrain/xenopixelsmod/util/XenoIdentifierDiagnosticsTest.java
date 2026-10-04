package net.bullettrain.xenopixelsmod.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoIdentifierDiagnosticsTest {
    @Test
    void blankPathIsDetectedAndMalformedReportDoesNotThrow() {
        assertTrue(XenoIdentifierDiagnostics.isBlankPath(
                net.minecraft.resources.ResourceLocation.tryParse("minecraft:")));
        assertFalse(XenoIdentifierDiagnostics.isBlankPath(
                net.minecraft.resources.ResourceLocation.tryParse("minecraft:stone")));
        assertFalse(XenoIdentifierDiagnostics.isBlankPath(null));
        assertDoesNotThrow(() -> XenoIdentifierDiagnostics.reportIfMalformed(
                "minecraft:", "unit-test-blank"));
        assertDoesNotThrow(() -> XenoIdentifierDiagnostics.reportIfMalformed(
                "minecraft:stone", "unit-test-ok"));
        assertDoesNotThrow(() -> XenoIdentifierDiagnostics.reportEmpty(
                net.minecraft.resources.ResourceLocation.tryParse("minecraft:"),
                "unit-test-empty"));
    }
}
