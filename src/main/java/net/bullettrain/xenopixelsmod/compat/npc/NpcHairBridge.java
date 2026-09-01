package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.hair.HairManager;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

/** Server-side access to the fields mixed into CNPC Gecko Addon's verified model-data API. */
public final class NpcHairBridge {
    public enum Result { OK, ADDON_MISSING, CUSTOM_MODEL_REQUIRED, INVALID_CODE, INVALID_COLOR }

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
            Class<?> npcType = Class.forName("noppes.npcs.entity.EntityNPCInterface");
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
            Class.forName("noppes.npcs.entity.EntityNPCInterface")
                    .getMethod("updateClient").invoke(npc);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
