package net.bullettrain.xenopixelsmod.hair;

import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairManager;
import com.dragonminez.common.hair.HairStrand;
import com.dragonminez.common.hair.HairStyleSlot;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Load a DMZ built-in hair preset into {@link HairMakerDocument} so the studio can use
 * presets as an editable base. Uses only public {@link HairManager} getters (2.2).
 */
public final class HairPresetImport {
    private HairPresetImport() {
    }

    /** Parse {@code hair:3} / {@code 3} → preset id, or {@code -1}. */
    public static int parsePresetId(String partIdOrNumber) {
        if (partIdOrNumber == null || partIdOrNumber.isBlank()) {
            return -1;
        }
        String raw = partIdOrNumber.trim().toLowerCase(Locale.ROOT);
        if (raw.startsWith("hair:")) {
            raw = raw.substring("hair:".length());
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Fetch the preset CustomHair for the given style slot.
     *
     * @param style Base / SSJ / SSJ2 / SSJ3
     */
    public static CustomHair fetchPreset(int presetId, String style) {
        if (presetId < 0) {
            return null;
        }
        String slot = style == null ? "Base" : style.trim();
        HairStyleSlot styleSlot = HairStyleSlot.byIndex(Math.max(0, HairMakerDocument.STYLE_NAMES.indexOf(slot)));
        CustomHair hair = HairManager.getPresetStyle(presetId, styleSlot);
        if ((hair == null || hair.isEmpty()) && styleSlot != HairStyleSlot.BASE) {
            hair = HairManager.getPresetStyle(presetId, HairStyleSlot.BASE);
        }
        if (hair == null || hair.isEmpty()) {
            return null;
        }
        return hair;
    }

    /**
     * Replace document geometry with the preset for {@code style}. Returns false if the
     * preset could not be loaded (count 0 / missing id / empty geometry).
     */
    public static boolean loadIntoDocument(HairMakerDocument document, int presetId, String style) {
        Objects.requireNonNull(document, "document");
        CustomHair hair = fetchPreset(presetId, style);
        if (hair == null) {
            return false;
        }
        document.loadFromCustomHair(hair, style);
        return true;
    }

    public static int presetCountSafe() {
        try {
            return Math.max(0, HairManager.getPresetCount());
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Copy one DMZ strand into the maker model (public getters only). */
    static void copyFromDmz(HairStrand src, HairStrandModel dst) {
        if (src == null || dst == null) {
            return;
        }
        HairStrandCompatibility.importNative(src, dst);
    }

    static void copyFace(CustomHair hair, CustomHair.HairFace face, List<HairStrandModel> dest) {
        if (hair == null || face == null || dest == null) {
            return;
        }
        for (int i = 0; i < dest.size(); i++) {
            HairStrand src = hair.getStrand(face, i);
            HairStrandModel dst = dest.get(i);
            if (src == null) {
                dst.length(0);
                continue;
            }
            copyFromDmz(src, dst);
        }
    }
}
