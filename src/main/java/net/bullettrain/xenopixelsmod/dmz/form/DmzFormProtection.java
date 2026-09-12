package net.bullettrain.xenopixelsmod.dmz.form;

import com.dragonminez.common.config.ConfigManager;
import net.bullettrain.xenopixelsmod.config.DmzFormProtectionConfig;

import java.util.Set;

/** Server-authoritative ownership policy for DragonMineZ groups exposed by Form Studio. */
public final class DmzFormProtection {
    private static final Set<String> BUNDLED_NORMAL = Set.of(
            "saiyan:xenopixels_fan_ss",
            "saiyan:xenopixels_gods_forms",
            "saiyan:xenopixels_saga_forms",
            "frostdemon:xenopixels_dark_frieza"
    );

    private DmzFormProtection() {
    }

    public static boolean isBundled(DmzFormKind kind, String race, String group) {
        return kind == DmzFormKind.NORMAL
                && BUNDLED_NORMAL.contains(normal(race) + ":" + normal(group));
    }

    /**
     * Existing groups are editable only when Form Studio owns their versioned metadata. Bundled
     * groups remain protected even if an older build accidentally wrote editor metadata for them.
     */
    public static boolean isProtected(DmzFormKind kind, String race, String group,
                                      boolean groupExists, boolean editorMetadataExists) {
        return isBundled(kind, race, group) || groupExists && !editorMetadataExists;
    }

    public static boolean isProtected(DmzFormKind kind, String race, String group) {
        boolean exists = kind == DmzFormKind.STACK
                ? ConfigManager.getStackFormGroup(group) != null
                : ConfigManager.getFormGroup(race, group) != null;
        return isProtected(kind, race, group, exists,
                DmzFormMetadataRegistry.get(kind, race, group) != null);
    }

    public static String mutationError(DmzFormKind kind, String race, String group) {
        if (!isProtected(kind, race, group) || DmzFormProtectionConfig.allowProtectedEdits()) {
            return null;
        }
        return "Group " + group + " is read-only (native or bundled content); duplicate it to a new group id";
    }

    private static String normal(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
