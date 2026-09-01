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
            Class<?> npcClass = Class.forName("noppes.npcs.entity.EntityNPCInterface");
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
            Class.forName("noppes.npcs.entity.EntityNPCInterface")
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
            Class<?> npcClass = Class.forName("noppes.npcs.entity.EntityNPCInterface");
            if (!npcClass.isInstance(npc)) {
                return null;
            }
            return npcClass.getField("display").get(npc);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
