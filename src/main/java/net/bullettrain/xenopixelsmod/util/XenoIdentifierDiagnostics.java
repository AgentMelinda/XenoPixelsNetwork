package net.bullettrain.xenopixelsmod.util;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Rate-limited diagnostics for empty or malformed identifiers without mutating them. */
public final class XenoIdentifierDiagnostics {
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private XenoIdentifierDiagnostics() {
    }

    public static void reportIfMalformed(String raw, String context) {
        String value = raw == null ? "<null>" : raw;
        if (raw != null && !raw.isBlank() && !raw.endsWith(":") && ResourceLocation.tryParse(raw) != null) {
            return;
        }
        report(value, context);
    }

    public static void reportEmpty(ResourceLocation location, String context) {
        if (location != null && location.getPath().isBlank()) {
            report(location.toString(), context);
        }
    }

    private static void report(String value, String context) {
        String detail = context + "|" + value;
        if (!REPORTED.add(detail)) {
            return;
        }
        XenoPixelsMod.LOGGER.warn(
                "Malformed identifier '{}' detected in {} on thread '{}'; value was not rewritten.",
                value, context, Thread.currentThread().getName(),
                new IllegalStateException("XenoPixels identifier diagnostic"));
    }
}
