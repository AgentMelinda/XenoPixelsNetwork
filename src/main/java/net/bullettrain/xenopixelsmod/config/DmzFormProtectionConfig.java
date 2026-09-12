package net.bullettrain.xenopixelsmod.config;

/** Developer-only escape hatch for diagnosing protected DMZ form edits. */
public final class DmzFormProtectionConfig {
    private DmzFormProtectionConfig() {
    }

    public static boolean allowProtectedEdits() {
        return XenoServerConfig.dmzFormProtectedEditOverride;
    }

    public static void setAllowProtectedEdits(boolean value) {
        XenoServerConfig.dmzFormProtectedEditOverride = value;
    }
}
