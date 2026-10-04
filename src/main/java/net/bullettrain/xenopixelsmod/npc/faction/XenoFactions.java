package net.bullettrain.xenopixelsmod.npc.faction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.npc.XenoDataRefs;
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
import java.util.Locale;
import java.util.Map;

/**
 * Loads factions from {@code data/<namespace>/npcs/factions/*.json}.
 *
 * <p>Same shape as {@code XenoDialogues} and {@code XenoNpcRoleDefinitions}, which is the
 * established loader in this mod: ids come from the file names, failures are reported per file with
 * a reason, and a summary line says how many loaded.
 *
 * <p>NPCs name their faction as a plain string rather than a resource location, because that is
 * what the editor field has always held. Lookups are therefore by bare id, and
 * {@link XenoDataRefs} handles the full-path spelling for anything that writes one.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class XenoFactions extends SimpleJsonResourceReloadListener {

    private static final String DIRECTORY = "npcs/factions";
    private static final Gson GSON = new GsonBuilder().create();

    public static final XenoFactions INSTANCE = new XenoFactions();

    private static volatile Map<String, XenoFaction> factions = Map.of();
    private static volatile List<String> loadErrors = List.of();

    private XenoFactions() {
        super(GSON, DIRECTORY);
    }

    /** The faction with this bare id, or null when no pack defines one. */
    public static XenoFaction get(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return factions.get(id.trim().toLowerCase(Locale.ROOT));
    }

    /** As {@link #get(String)}, but accepting a full-path ref as well as a bare id. */
    public static XenoFaction byRef(String ref) {
        ResourceLocation resolved = XenoDataRefs.resolve(ref, DIRECTORY);
        return resolved == null ? get(ref) : get(resolved.getPath());
    }

    /** Whether any pack defines factions at all. */
    public static boolean isEmpty() {
        return factions.isEmpty();
    }

    /** Every loaded id, for the editor's picker and command completion. */
    public static List<String> ids() {
        return List.copyOf(factions.keySet());
    }

    /** What failed at the last reload, so commands can surface it. */
    public static List<String> loadErrors() {
        return loadErrors;
    }

    /**
     * Whether {@code a} attacks {@code b} on sight.
     *
     * <p>Answers false when either side names a faction no pack defines. An NPC labelled with a
     * faction that was deleted should stand there, not become hostile to everything.
     */
    public static boolean hostile(String a, String b) {
        XenoFaction first = get(a);
        return first != null && get(b) != null && first.isHostileTo(b);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources,
                         ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, XenoFaction> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            String id = entry.getKey().getPath().toLowerCase(Locale.ROOT);
            try {
                loaded.put(id, XenoFaction.fromJson(id, entry.getValue()));
            } catch (RuntimeException e) {
                String message = entry.getKey() + ": " + e.getMessage();
                errors.add(message);
                XenoPixelsMod.LOGGER.error("Failed to load faction {}", message);
            }
        }

        factions = Map.copyOf(loaded);
        loadErrors = List.copyOf(errors);

        if (errors.isEmpty()) {
            XenoPixelsMod.LOGGER.info("Loaded {} faction(s)", loaded.size());
        } else {
            XenoPixelsMod.LOGGER.warn("Loaded {} faction(s), {} failed", loaded.size(),
                    errors.size());
        }
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }
}
