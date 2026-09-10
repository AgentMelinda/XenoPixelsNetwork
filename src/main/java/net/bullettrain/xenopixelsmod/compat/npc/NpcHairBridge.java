package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.hair.HairManager;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

/** Server-side access to the fields mixed into CNPC Gecko Addon's verified model-data API. */
public final class NpcHairBridge {
    public enum Result { OK, ADDON_MISSING, CUSTOM_MODEL_REQUIRED, INVALID_CODE, INVALID_COLOR,
        INVALID_STYLE }

    private NpcHairBridge() {}

    public static Result setEnabled(Entity npc, boolean enabled) {
        if (npc == null) {
            return Result.CUSTOM_MODEL_REQUIRED;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        profile.hairEnabled = enabled;
        profile.write(npc);
        return Result.OK;
    }

    public static Result setCode(Entity npc, String code) {
        if (npc == null) {
            return Result.CUSTOM_MODEL_REQUIRED;
        }
        String value = code == null ? "" : code.trim();
        try {
            boolean valid = !value.isEmpty() && (HairManager.isFullSetCode(value)
                    ? HairManager.fromFullSetCode(value) != null
                    : HairManager.fromCode(value) != null);
            if (!valid) {
                return Result.INVALID_CODE;
            }
        } catch (RuntimeException ignored) {
            return Result.INVALID_CODE;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        profile.hairCode = value;
        profile.hairEnabled = true;
        profile.write(npc);
        return Result.OK;
    }

    /**
     * Picks one of DragonMineZ's built-in character-creation hair styles.
     *
     * <p>{@code 0} hands back to the NPC's own hair code. Anything else is DMZ's {@code hairId},
     * which is the same field a player's character creation writes, so an NPC can wear a stock look
     * without a hair code at all.
     */
    public static Result setStyle(Entity npc, int styleId) {
        if (npc == null) {
            return Result.CUSTOM_MODEL_REQUIRED;
        }
        if (styleId < 0 || styleId > presetCount()) {
            return Result.INVALID_STYLE;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        profile.hairStyleId = styleId;
        if (styleId > 0) {
            profile.hairEnabled = true;
        }
        profile.write(npc);
        return Result.OK;
    }

    /**
     * Pushes the style to CustomNPCs' model data when that build exposes it.
     *
     * <p>Reflective and silent: the field is a XenoPixels addition to CNPC's data and older builds
     * do not have it. The appearance packet remains the renderer's source either way, so a client
     * without the setter still shows the right hair.
     */
    private static void applyStyle(NpcHairModelData data, int styleId) {
        try {
            data.getClass()
                    .getMethod("xenopixels$setDmzHairStyle", int.class)
                    .invoke(data, Math.max(0, styleId));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Older CustomNPCs data object; the appearance packet still carries the style.
        }
    }

    /**
     * Steps a hair style selection, wrapping through {@code 0} and {@code 1..count}.
     *
     * <p>{@code 0} is the custom hair code rather than "no selection", so it is part of the cycle
     * and reachable from either end. A stored id past the end of the list — a profile written when
     * more presets were registered — still lands back in range rather than trapping the button.
     *
     * <p>Pure, so the wrap rules can be tested without a GUI.
     */
    public static int cycleStyle(int current, int delta, int count) {
        int span = Math.max(0, count) + 1;
        return Math.floorMod(Math.max(0, current) % span + delta, span);
    }

    /** How many styles DMZ registered; 0 when its hair system is unavailable. */
    public static int presetCount() {
        try {
            return Math.max(0, HairManager.getPresetCount());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static Result setColor(Entity npc, String color) {
        if (npc == null) {
            return Result.CUSTOM_MODEL_REQUIRED;
        }
        String value = color == null ? "" : color.trim();
        String stored = NpcCombatProfile.canonicalizeHairColor(value);
        if (!value.isEmpty() && stored.isEmpty()) {
            return Result.INVALID_COLOR;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        profile.hairColor = stored;
        if (!stored.isEmpty()) {
            profile.hairEnabled = true;
        }
        profile.write(npc);
        return Result.OK;
    }

    /** Mirror profile hair onto gecko {@code CustomModelData} (optional). */
    public static void applyProfile(Entity npc, NpcCombatProfile profile) {
        NpcHairModelData data = data(npc);
        if (data == null || profile == null) {
            return;
        }
        data.xenopixels$setDmzHairEnabled(profile.hairEnabled);
        String code = profile.hairCode == null ? "" : profile.hairCode;
        // CNPC display packets choke on full-set codes; appearance packet is the renderer source.
        data.xenopixels$setDmzHairCode(code.length() <= 8000 ? code : "");
        data.xenopixels$setDmzHairColor(NpcCombatProfile.canonicalizeHairColor(profile.hairColor));
        // Keeps CustomNPCs' own display data agreeing with the appearance packet.
        applyStyle(data, profile.hairStyleId);
        updateClient(npc);
    }

    /** Prefer live gecko hair when the profile copy is empty or truncated. */
    public static void fillProfile(Entity npc, NpcCombatProfile profile) {
        NpcHairModelData data = data(npc);
        if (data == null || profile == null) {
            return;
        }
        String liveCode = data.xenopixels$getDmzHairCode();
        String liveColor = data.xenopixels$getDmzHairColor();
        if (liveCode != null && liveCode.length() > (profile.hairCode == null ? 0 : profile.hairCode.length())) {
            profile.hairCode = liveCode;
        }
        if ((profile.hairColor == null || profile.hairColor.isBlank()) && liveColor != null && !liveColor.isBlank()) {
            profile.hairColor = NpcCombatProfile.canonicalizeHairColor(liveColor);
        }
        if (!profile.hairEnabled) {
            profile.hairEnabled = data.xenopixels$isDmzHairEnabled();
        }
    }

    public static NpcHairModelData data(Entity npc) {
        if (npc == null || !ModList.get().isLoaded("cnpcgeckoaddon")) {
            return null;
        }
        try {
            Class<?> npcType = NpcTypes.npcInterface();
            if (!npcType.isInstance(npc)) {
                return null;
            }
            Object display = npcType.getField("display").get(npc);
            Class<?> addonDisplay = Class.forName("com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay");
            if (!addonDisplay.isInstance(display)
                    || !(Boolean) addonDisplay.getMethod("hasCustomModel").invoke(display)) {
                return null;
            }
            Object modelData = addonDisplay.getMethod("getCustomModelData").invoke(display);
            return modelData instanceof NpcHairModelData hair ? hair : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static void updateClient(Entity npc) {
        if (npc.level() != null && npc.level().isClientSide()) {
            return;
        }
        try {
            NpcTypes.npcInterface()
                    .getMethod("updateClient").invoke(npc);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
