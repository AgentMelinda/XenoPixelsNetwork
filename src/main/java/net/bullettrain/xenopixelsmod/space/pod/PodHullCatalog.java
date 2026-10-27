package net.bullettrain.xenopixelsmod.space.pod;

import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Server-side P16 loader for {@code data/<namespace>/xenospace/pod/hull_*.json}. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class PodHullCatalog extends SimplePreparableReloadListener<PodHullCatalog.Snapshot> {
    public static final int MAX_HULLS = 64;
    public static final int MAX_RESOURCE_CANDIDATES = 128;
    public static final int MAX_FILE_BYTES = 32 * 1024;
    private static final String FOLDER = "xenospace/pod";
    private static final String HULL_PREFIX = FOLDER + "/hull_";
    private static volatile Snapshot current = Snapshot.empty();

    /** Immutable catalog published only after the resource manager has prepared a full candidate. */
    public record Snapshot(Map<ResourceLocation, PodHullDefinition> hulls, List<String> errors) {
        public Snapshot {
            hulls = Collections.unmodifiableMap(new TreeMap<>(hulls));
            errors = List.copyOf(errors);
        }

        public static Snapshot empty() { return new Snapshot(Map.of(), List.of()); }
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new PodHullCatalog());
    }

    public static Snapshot snapshot() { return current; }

    @Override
    protected Snapshot prepare(ResourceManager manager, ProfilerFiller profiler) {
        var resources = new TreeMap<>(manager.listResources(FOLDER,
                id -> id.getPath().startsWith(HULL_PREFIX) && id.getPath().endsWith(".json")));
        List<String> errors = new ArrayList<>();
        Map<ResourceLocation, PodHullDefinition> hulls = new TreeMap<>();
        int examined = 0;
        for (var entry : resources.entrySet()) {
            if (examined++ >= MAX_RESOURCE_CANDIDATES) {
                errors.add(FOLDER + ": resource scan capped at " + MAX_RESOURCE_CANDIDATES + " candidates");
                break;
            }
            ResourceLocation hullId;
            try {
                hullId = hullIdFromResource(entry.getKey());
            } catch (IllegalArgumentException invalidPath) {
                errors.add(entry.getKey() + ": " + invalidPath.getMessage());
                continue;
            }
            if (hulls.size() >= MAX_HULLS) {
                errors.add(FOLDER + ": hull catalog capped at " + MAX_HULLS + " entries");
                break;
            }
            try (var stream = entry.getValue().open()) {
                byte[] bytes = stream.readNBytes(MAX_FILE_BYTES + 1);
                if (bytes.length > MAX_FILE_BYTES) throw new IllegalArgumentException("hull file exceeds " + MAX_FILE_BYTES + " bytes");
                var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                PodHullDefinition definition = PodHullDefinition.parse(hullId, json);
                if (hulls.putIfAbsent(hullId, definition) != null)
                    throw new IllegalArgumentException("duplicate hull id " + hullId);
            } catch (Exception invalid) {
                errors.add(entry.getKey() + ": " + invalid.getMessage());
            }
        }
        return new Snapshot(hulls, errors);
    }

    @Override
    protected void apply(Snapshot prepared, ResourceManager manager, ProfilerFiller profiler) {
        current = prepared;
        if (prepared.errors().isEmpty()) {
            XenoPixelsMod.LOGGER.info("Pod hull catalog loaded: {} hull(s)", prepared.hulls().size());
        } else {
            XenoPixelsMod.LOGGER.warn("Pod hull catalog loaded: {} hull(s), {} invalid resource(s)",
                    prepared.hulls().size(), prepared.errors().size());
            prepared.errors().forEach(error -> XenoPixelsMod.LOGGER.warn("Pod hull catalog: {}", error));
        }
    }

    /** Map the documented filename convention to the stable loadout ID namespace. */
    static ResourceLocation hullIdFromResource(ResourceLocation file) {
        String path = file.getPath();
        if (!path.startsWith(HULL_PREFIX) || !path.endsWith(".json"))
            throw new IllegalArgumentException("expected " + HULL_PREFIX + "<name>.json");
        String name = path.substring(HULL_PREFIX.length(), path.length() - ".json".length());
        if (name.isBlank() || name.indexOf('/') >= 0 || !name.matches("[a-z0-9_.-]{1,64}"))
            throw new IllegalArgumentException("hull filename must be a single lowercase id segment");
        return ResourceLocation.fromNamespaceAndPath(file.getNamespace(), "pod/hull/" + name);
    }
}
