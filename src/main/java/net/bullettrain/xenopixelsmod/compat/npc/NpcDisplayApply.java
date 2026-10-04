package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.world.entity.LivingEntity;

/**
 * Pushes stored skin fields onto CustomNPCs {@code DataDisplay}
 * ({@code setSkinPlayer} / {@code setSkinUrl}). Reflection so the class
 * compiles without the CNPC jar on every run.
 */
public final class NpcDisplayApply {
    private NpcDisplayApply() {}

    public static boolean applySkin(LivingEntity npc, String skinPlayer, String skinUrl) {
        if (npc == null) {
            return false;
        }
        try {
            Class<?> npcClass = NpcTypes.npcInterface();
            if (!npcClass.isInstance(npc)) {
                return false;
            }
            Object display = npcClass.getField("display").get(npc);
            if (display == null) {
                return false;
            }
            boolean changed = false;
            if (skinPlayer != null && !skinPlayer.isBlank()) {
                display.getClass().getMethod("setSkinPlayer", String.class).invoke(display, skinPlayer);
                changed = true;
            }
            if (skinUrl != null && !skinUrl.isBlank()) {
                display.getClass().getMethod("setSkinUrl", String.class).invoke(display, skinUrl);
                changed = true;
            }
            if (changed) {
                try {
                    npcClass.getField("updateClient").setBoolean(npc, true);
                } catch (Throwable ignored) {
                }
            }
            return changed;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Switches the CNPC display model type to/from the player model via reflection on
     * the {@code modelType} enum field of {@code DataDisplay}. Returns false when the
     * field or the expected enum constant does not exist in the loaded CNPC version.
     */
    public static boolean setPlayerModel(LivingEntity npc, boolean playerModel) {
        Object display = displayOf(npc);
        if (display == null) {
            return false;
        }
        try {
            java.lang.reflect.Field field = display.getClass().getField("modelType");
            Class<?> enumType = field.getType();
            if (!enumType.isEnum()) {
                return false;
            }
            String wanted = playerModel ? "PLAYER" : "CUSTOM";
            Object target = null;
            for (Object constant : enumType.getEnumConstants()) {
                if (wanted.equals(((Enum<?>) constant).name())) {
                    target = constant;
                    break;
                }
            }
            if (target == null) {
                return false;
            }
            field.set(display, target);
            try {
                NpcTypes.npcInterface()
                        .getField("updateClient").setBoolean(npc, true);
            } catch (Throwable ignored) {
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * DragonMineZ's default display size. Size 5 is 1.0x, which is why every scale here divides by
     * it.
     */
    public static final float DEFAULT_SIZE = 5.0f;

    /**
     * Render scale for an NPC, from its CustomNPCs display size or, failing that, its own profile.
     *
     * <p>{@link #getSize} reads a CustomNPCs Display object by reflection, so a <em>native</em> Xeno
     * NPC - which has no such object - answers 0. Treating that 0 as a real size produced
     * {@code max(0.05, 0/5)}, a twentieth of normal: the model, its aura and its transform particles
     * all rendered tiny. Size 0 means "unset", not "minuscule".
     *
     * <p>{@code NpcAuraClient.cnpcSizeMul} already got this right; this is that rule shared so the
     * remaining call sites cannot each get it wrong separately.
     *
     * @param profile may be null, in which case only the display size is consulted
     */
    public static float sizeScale(LivingEntity npc, NpcCombatProfile profile) {
        int size = getSize(npc);
        if (size <= 0 && profile != null) {
            size = profile.baseSize;
        }
        if (size <= 0) {
            return 1.0f;
        }
        return Math.max(0.05f, size / DEFAULT_SIZE);
    }

    /** As {@link #sizeScale(LivingEntity, NpcCombatProfile)}, reading the profile off the entity. */
    public static float sizeScale(LivingEntity npc) {
        return sizeScale(npc, npc == null ? null : NpcCombatProfile.readCached(npc));
    }

    public static int getSize(LivingEntity npc) {
        Object display = displayOf(npc);
        if (display == null) {
            return 0;
        }
        try {
            Object value = display.getClass().getMethod("getSize").invoke(display);
            return value instanceof Integer i ? i : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static boolean setSize(LivingEntity npc, int size) {
        Object display = displayOf(npc);
        if (display == null) {
            return false;
        }
        try {
            display.getClass().getMethod("setSize", int.class).invoke(display, Math.max(1, size));
            NpcTypes.npcInterface()
                    .getField("updateClient").setBoolean(npc, true);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object displayOf(LivingEntity npc) {
        if (npc == null) {
            return null;
        }
        try {
            Class<?> npcClass = NpcTypes.npcInterface();
            if (!npcClass.isInstance(npc)) {
                return null;
            }
            return npcClass.getField("display").get(npc);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
