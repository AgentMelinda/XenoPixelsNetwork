package net.bullettrain.xenopixelsmod.npc;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Turns a datapack reference written in a role definition into the key its loader actually uses.
 *
 * <p>{@code SimpleJsonResourceReloadListener} strips its own directory prefix when building keys -
 * {@code FileToIdConverter.fileToId} does
 * {@code path.substring(prefix.length() + 1, path.length() - extension.length())}. So a loader over
 * {@code npcs/lines} registers {@code data/ns/npcs/lines/default.json} as {@code ns:default}, not as
 * {@code ns:npcs/lines/default}.
 *
 * <p>The role JSONs were written with the full path - {@code "dialogue":
 * "xenopixelsmod:npcs/dialogue/default"} was in {@code quest.json} from the start - so every lookup
 * missed and no NPC ever had a line or a conversation. Rather than rewrite the datapack, both
 * spellings are accepted here: the full path is reduced to what the loader registered, and a bare id
 * is passed through untouched.
 */
public final class XenoDataRefs {

    private XenoDataRefs() {
    }

    /**
     * Resolves {@code ref} against a loader rooted at {@code directory}.
     *
     * @param ref       a reference from a role definition, either {@code ns:npcs/lines/default} or
     *                  the bare {@code ns:default}
     * @param directory the loader's directory, e.g. {@code npcs/lines}
     * @return the loader's key, or null when the ref is blank or unparseable
     */
    public static ResourceLocation resolve(String ref, String directory) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(ref.trim().toLowerCase(Locale.ROOT));
        if (id == null) {
            return null;
        }
        String prefix = directory == null ? "" : directory.toLowerCase(Locale.ROOT) + "/";
        String path = id.getPath();
        if (!prefix.isEmpty() && path.startsWith(prefix)) {
            path = path.substring(prefix.length());
        }
        // A trailing ".json" is an easy thing to write by hand and is never part of the key.
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        if (path.isEmpty()) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path);
    }
}
