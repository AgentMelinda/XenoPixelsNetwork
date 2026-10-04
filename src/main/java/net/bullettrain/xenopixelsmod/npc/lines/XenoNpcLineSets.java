package net.bullettrain.xenopixelsmod.npc.lines;

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
 * Loads NPC line sets from {@code data/<namespace>/npcs/lines/*.json}.
 *
 * <p>Same loader shape as {@code XenoDialogues} and {@code XenoNpcRoleDefinitions}, and the same
 * loud-failure policy: a malformed set is reported with its id and reason and the reload ends with
 * a count, so a typo shows up straight away rather than as an NPC that mysteriously stays quiet.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class XenoNpcLineSets extends SimpleJsonResourceReloadListener {
    private static final String DIRECTORY = "npcs/lines";
    private static final Gson GSON = new GsonBuilder().create();

    public static final XenoNpcLineSets INSTANCE = new XenoNpcLineSets();

    private static volatile Map<ResourceLocation, XenoNpcLines> sets = Map.of();
    private static volatile List<String> loadErrors = List.of();

    private XenoNpcLineSets() {
        super(GSON, DIRECTORY);
    }

    /** The set with this id, or {@link XenoNpcLines#EMPTY} so callers never null-check. */
    public static XenoNpcLines get(ResourceLocation id) {
        if (id == null) {
            return XenoNpcLines.EMPTY;
        }
        return sets.getOrDefault(id, XenoNpcLines.EMPTY);
    }

    /**
     * The set named by a ref string, empty when the ref is blank or unresolvable.
     *
     * <p>Goes through {@link XenoDataRefs}, so the full-path spelling in the role JSONs
     * ({@code xenopixelsmod:npcs/lines/default}) and a bare id both resolve to the same set.
     */
    public static XenoNpcLines get(String ref) {
        return get(net.bullettrain.xenopixelsmod.npc.XenoDataRefs.resolve(ref, DIRECTORY));
    }

    /** Every loaded id, for the editor's picker. */
    public static List<ResourceLocation> ids() {
        return List.copyOf(sets.keySet());
    }

    public static List<String> loadErrors() {
        return loadErrors;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources,
                         ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, XenoNpcLines> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            try {
                loaded.put(entry.getKey(), XenoNpcLines.fromJson(entry.getValue()));
            } catch (RuntimeException e) {
                String message = entry.getKey() + ": " + e.getMessage();
                errors.add(message);
                XenoPixelsMod.LOGGER.error("Failed to load NPC lines {}", message);
            }
        }

        sets = Map.copyOf(loaded);
        loadErrors = List.copyOf(errors);

        if (errors.isEmpty()) {
            XenoPixelsMod.LOGGER.info("Loaded {} NPC line set(s)", loaded.size());
        } else {
            XenoPixelsMod.LOGGER.warn("Loaded {} NPC line set(s), {} failed", loaded.size(),
                    errors.size());
        }
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }
}
