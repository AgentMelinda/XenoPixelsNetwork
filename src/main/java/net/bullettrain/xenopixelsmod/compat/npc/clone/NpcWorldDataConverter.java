package net.bullettrain.xenopixelsmod.compat.npc.clone;

import net.bullettrain.xenopixelsmod.compat.npc.CustomNpcQuestCommandCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Locale;

/** Conservative string conversion shared by quest, dialog, and script world data. */
public final class NpcWorldDataConverter {
    private static final int MAX_DEPTH = 64;

    private NpcWorldDataConverter() {}

    public static boolean isStructured(String path) {
        String normalized = normalize(path);
        return (normalized.startsWith("quests/") || normalized.startsWith("dialogs/"))
                && normalized.endsWith(".json");
    }

    public static boolean isScript(String path) {
        return normalize(path).startsWith("scripts/");
    }

    public static String convertScript(String source) {
        if (source == null || source.isEmpty()) return source;
        return rewrite(source);
    }

    public static CompoundTag convertStructured(CompoundTag source) {
        CompoundTag copy = source == null ? new CompoundTag() : source.copy();
        rewriteTag(copy, 0);
        return copy;
    }

    static String rewrite(String value) {
        String rewritten = value.replace("customnpcs:", "mynpcs:")
                .replace("noppes.npcs.", "espi.mynpcs.");
        return CustomNpcQuestCommandCompat.normalizeEmbedded(rewritten);
    }

    private static void rewriteTag(Tag tag, int depth) {
        if (tag == null || depth > MAX_DEPTH) return;
        if (tag instanceof CompoundTag compound) {
            for (String key : new ArrayList<>(compound.getAllKeys())) {
                Tag child = compound.get(key);
                if (child instanceof StringTag string) {
                    compound.putString(key, rewrite(string.getAsString()));
                } else {
                    rewriteTag(child, depth + 1);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (int index = 0; index < list.size(); index++) {
                Tag child = list.get(index);
                if (child instanceof StringTag string) {
                    list.set(index, StringTag.valueOf(rewrite(string.getAsString())));
                } else {
                    rewriteTag(child, depth + 1);
                }
            }
        }
    }

    private static String normalize(String path) {
        return path == null ? "" : path.replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
