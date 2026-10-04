package net.bullettrain.xenopixelsmod.features.progression;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

/** Optional-mod-safe identification and visible-name matching for NPC kill objectives. */
public final class QuestNpcTarget {
    public enum Family { XENO, MYNPCS, CUSTOMNPCS }

    private QuestNpcTarget() {}

    /** Identifies supported NPC implementations without linking optional mod classes. */
    public static Family family(String className, String typeId) {
        String classKey = normalize(className);
        String typeKey = normalize(typeId);
        if (classKey.contains("xenopixelsmod.npc.xenonpcentity")
                || typeKey.startsWith("xenopixelsmod:xeno_npc_")) return Family.XENO;
        if (classKey.contains("espi.mynpcs.entity.entitycustomnpc")
                || classKey.contains("espi.mynpcs.entity.entitynpc64x32")
                || classKey.contains("espi.mynpcs.entity.entitynpcalex")
                || classKey.contains("espi.mynpcs.entity.entitynpcclassicplayer")
                || isNpcType(typeKey, "mynpcs")) return Family.MYNPCS;
        if (classKey.contains("noppes.npcs.entity.entitynpcinterface")
                || isNpcType(typeKey, "customnpcs")) return Family.CUSTOMNPCS;
        return null;
    }

    private static boolean isNpcType(String typeKey, String namespace) {
        return typeKey.equals(namespace + ":customnpc")
                || typeKey.equals(namespace + ":customnpc64x32")
                || typeKey.equals(namespace + ":customnpcalex")
                || typeKey.equals(namespace + ":customnpcclassic");
    }

    /** True when this entity is a supported NPC whose complete visible name matches the target. */
    public static boolean matches(Entity entity, String targetName) {
        if (entity == null || targetName == null || targetName.isBlank()) return false;
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (family(entity.getClass().getName(), typeId) == null) return false;
        String visibleName = entity.getName().getString();
        if (entity instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity npc) {
            visibleName = npc.npcData().displayName();
        }
        return normalize(visibleName).equals(normalize(targetName));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
