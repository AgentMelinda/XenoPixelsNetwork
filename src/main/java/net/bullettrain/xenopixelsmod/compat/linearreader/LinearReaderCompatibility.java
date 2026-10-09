package net.bullettrain.xenopixelsmod.compat.linearreader;

/** Exact builds whose converter hooks and merged storage fields have been verified. */
public final class LinearReaderCompatibility {
    public static final String SUPPORTED_VERSIONS = "1.3.0 or 1.3.1";

    private LinearReaderCompatibility() {}

    public static boolean supports(String version) {
        return "1.3.0".equals(version) || "1.3.1".equals(version);
    }
}
