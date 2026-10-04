package net.bullettrain.xenopixelsmod.npc.dialog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads NPC dialogues from {@code data/<namespace>/npcs/dialogue/*.json}.
 *
 * <p>Follows {@code XenoNpcRoleDefinitions}, which is the established loader shape in this mod. The
 * directory matches the {@code refs.dialogue} ids already written into the role JSONs, which have
 * been parsed and ignored until now.
 *
 * <p>Failures are loud. A malformed dialogue is reported with its id and the reason, and the reload
 * finishes with a one-line summary of everything that did not load, so a typo in a pack shows up
 * immediately rather than as an NPC that quietly has nothing to say.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class XenoDialogues extends SimpleJsonResourceReloadListener {
    private static final String DIRECTORY = "npcs/dialogue";
    private static final Gson GSON = new GsonBuilder().create();

    public static final XenoDialogues INSTANCE = new XenoDialogues();

    private static volatile Map<ResourceLocation, XenoDialogue> dialogues = Map.of();
    private static volatile List<String> loadErrors = List.of();

    private XenoDialogues() {
        super(GSON, DIRECTORY);
    }

    /** The dialogue with this id, or null when the pack does not define one. */
    public static XenoDialogue get(ResourceLocation id) {
        return id == null ? null : dialogues.get(id);
    }

    /**
     * The dialogue named by a ref string, or null when the ref is blank or unresolvable.
     *
     * <p>Goes through {@link XenoDataRefs}, which reduces a full-path ref such as
     * {@code xenopixelsmod:npcs/dialogue/default} to the key this loader actually registered. Both
     * that spelling and a bare {@code xenopixelsmod:default} resolve to the same dialogue.
     */
    public static XenoDialogue get(String ref) {
        return get(net.bullettrain.xenopixelsmod.npc.XenoDataRefs.resolve(ref, DIRECTORY));
    }

    /** Every loaded id, for the editor's picker. */
    public static List<ResourceLocation> ids() {
        return List.copyOf(dialogues.keySet());
    }

    /** What failed at the last reload, so the editor and commands can surface it. */
    public static List<String> loadErrors() {
        return loadErrors;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources,
                         ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, XenoDialogue> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            try {
                loaded.put(entry.getKey(), XenoDialogue.fromJson(entry.getValue()));
            } catch (RuntimeException e) {
                String message = entry.getKey() + ": " + e.getMessage();
                errors.add(message);
                XenoPixelsMod.LOGGER.error("Failed to load NPC dialogue {}", message);
            }
        }

        // Ordered, so ids() lists them the way the pack does rather than by hash.
        dialogues = java.util.Collections.unmodifiableMap(loaded);
        loadErrors = List.copyOf(errors);

        if (errors.isEmpty()) {
            XenoPixelsMod.LOGGER.info("Loaded {} NPC dialogue(s)", loaded.size());
        } else {
            XenoPixelsMod.LOGGER.warn("Loaded {} NPC dialogue(s), {} failed", loaded.size(),
                    errors.size());
        }
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }
}
