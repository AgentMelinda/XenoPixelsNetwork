package net.bullettrain.xenopixelsmod.client.anim;

import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where the studio can open a clip from.
 *
 * <p>Three sources, all of which end up as an editable {@link XenoAnimClip}:
 *
 * <ul>
 *   <li>{@link Source#CONFIG} - clips saved by the studio itself.
 *   <li>{@link Source#SHIPPED} - the {@code combat.xeno_*} clips this mod ships.
 *   <li>{@link Source#DMZ} - DragonMineZ's own combat animations, for reference.
 * </ul>
 *
 * <p>The two file-backed sources are read through the resource manager, not out of the jar, so a
 * resource pack that overrides either file is what the studio opens. That is the reason there is no
 * separate "baked" source: {@code GeckoLibCache} holds compiled keyframes whose values may be molang
 * expressions rather than editable constants, and reading the file gives the same override-aware
 * content in the form it was actually authored in - degrees, constants and the original easing.
 */
public final class XenoClipSources {

    public enum Source {
        CONFIG("Saved"),
        SHIPPED("Shipped BT3"),
        DMZ("DragonMineZ");

        public final String label;

        Source(String label) {
            this.label = label;
        }
    }

    /** The animation file this mod ships its BT3 combat clips in. */
    public static final ResourceLocation SHIPPED_FILE = ResourceLocation.fromNamespaceAndPath(
            XenoPixelsMod.MOD_ID, "animations/entity/bt3_combat.animation.json");

    /**
     * DragonMineZ's own combat animation file - the one {@code CombatAnimationResolver.reload}
     * reads, per the note on {@code DmzCombatAnimationRegistryMixin}.
     */
    public static final ResourceLocation DMZ_FILE = ResourceLocation.fromNamespaceAndPath(
            "dragonminez", "animations/entity/races/combat.animation.json");

    private XenoClipSources() {}

    /** Every clip name the source offers, in file order. */
    public static List<String> list(Source source) {
        if (source == Source.CONFIG) return XenoAnimClip.listSavedNames();
        JsonObject animations = animations(fileOf(source));
        if (animations == null) return List.of();
        List<String> names = new ArrayList<>(animations.keySet());
        names.sort(String::compareTo);
        return names;
    }

    /**
     * Opens one clip for editing.
     *
     * <p>A clip from a file source comes back as a normal draft: editing it and pressing SAVE writes
     * a copy into the config directory, never back into the pack. Writing back into the repository
     * is {@link XenoClipSourceExport}, and only in a development run.
     */
    public static XenoAnimClip load(Source source, String name) throws IOException {
        if (source == Source.CONFIG) return XenoAnimClip.load(name);
        JsonObject animations = animations(fileOf(source));
        if (animations == null || !animations.has(name)) {
            throw new IOException("No animation " + name + " in " + fileOf(source));
        }
        return XenoAnimClip.fromAnimationObject(name, name, animations.getAsJsonObject(name));
    }

    public static ResourceLocation fileOf(Source source) {
        return source == Source.DMZ ? DMZ_FILE : SHIPPED_FILE;
    }

    /** The {@code animations} object of a resource file, or null when it cannot be read. */
    public static JsonObject animations(ResourceLocation file) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) return null;
        Optional<Resource> resource = minecraft.getResourceManager().getResource(file);
        if (resource.isEmpty()) return null;
        try (BufferedReader reader = resource.get().openAsReader()) {
            JsonObject root = XenoAnimClip.GSON.fromJson(reader, JsonObject.class);
            return root == null ? null : root.getAsJsonObject("animations");
        } catch (IOException | RuntimeException e) {
            XenoPixelsMod.LOGGER.warn("Could not read animation file {}: {}", file, e.toString());
            return null;
        }
    }
}
