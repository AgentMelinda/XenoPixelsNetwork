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

/** Server-side bounded loader for {@code data/<namespace>/xenospace/pod/cockpit_*.json}. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class PodCockpitCatalog extends SimplePreparableReloadListener<PodCockpitCatalog.Snapshot> {
    public static final int MAX_COCKPITS = 64;
    public static final int MAX_RESOURCE_CANDIDATES = 128;
    public static final int MAX_FILE_BYTES = 16 * 1024;
    private static final String FOLDER = "xenospace/pod";
    private static final String COCKPIT_PREFIX = FOLDER + "/cockpit_";
    private static volatile Snapshot current = Snapshot.empty();

    public record Snapshot(Map<ResourceLocation, PodCockpitDefinition> cockpits, List<String> errors) {
        public Snapshot {
            cockpits = Collections.unmodifiableMap(new TreeMap<>(cockpits));
            errors = List.copyOf(errors);
        }

        public static Snapshot empty() { return new Snapshot(Map.of(), List.of()); }
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new PodCockpitCatalog());
    }

    public static Snapshot snapshot() { return current; }

    @Override
    protected Snapshot prepare(ResourceManager manager, ProfilerFiller profiler) {
        var resources = new TreeMap<>(manager.listResources(FOLDER,
                id -> id.getPath().startsWith(COCKPIT_PREFIX) && id.getPath().endsWith(".json")));
        List<String> errors = new ArrayList<>();
        Map<ResourceLocation, PodCockpitDefinition> cockpits = new TreeMap<>();
        int examined = 0;
        for (var entry : resources.entrySet()) {
            if (examined++ >= MAX_RESOURCE_CANDIDATES) {
                errors.add(FOLDER + ": cockpit scan capped at " + MAX_RESOURCE_CANDIDATES + " candidates");
                break;
            }
            ResourceLocation cockpitId;
            try {
                cockpitId = cockpitIdFromResource(entry.getKey());
            } catch (IllegalArgumentException invalidPath) {
                errors.add(entry.getKey() + ": " + invalidPath.getMessage());
                continue;
            }
            if (cockpits.size() >= MAX_COCKPITS) {
                errors.add(FOLDER + ": cockpit catalog capped at " + MAX_COCKPITS + " entries");
                break;
            }
            try (var stream = entry.getValue().open()) {
                byte[] bytes = stream.readNBytes(MAX_FILE_BYTES + 1);
                if (bytes.length > MAX_FILE_BYTES)
                    throw new IllegalArgumentException("cockpit file exceeds " + MAX_FILE_BYTES + " bytes");
                var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                PodCockpitDefinition definition = PodCockpitDefinition.parse(cockpitId, json);
                if (cockpits.putIfAbsent(cockpitId, definition) != null)
                    throw new IllegalArgumentException("duplicate cockpit id " + cockpitId);
            } catch (Exception invalid) {
                errors.add(entry.getKey() + ": " + invalid.getMessage());
            }
        }
        return new Snapshot(cockpits, errors);
    }

    @Override
    protected void apply(Snapshot prepared, ResourceManager manager, ProfilerFiller profiler) {
        current = prepared;
        if (prepared.errors().isEmpty()) {
            XenoPixelsMod.LOGGER.info("Pod cockpit catalog loaded: {} cockpit(s)", prepared.cockpits().size());
        } else {
            XenoPixelsMod.LOGGER.warn("Pod cockpit catalog loaded: {} cockpit(s), {} invalid resource(s)",
                    prepared.cockpits().size(), prepared.errors().size());
            prepared.errors().forEach(error -> XenoPixelsMod.LOGGER.warn("Pod cockpit catalog: {}", error));
        }
    }

    static ResourceLocation cockpitIdFromResource(ResourceLocation file) {
        String path = file.getPath();
        if (!path.startsWith(COCKPIT_PREFIX) || !path.endsWith(".json"))
            throw new IllegalArgumentException("expected " + COCKPIT_PREFIX + "<name>.json");
        String name = path.substring(COCKPIT_PREFIX.length(), path.length() - ".json".length());
        if (name.isBlank() || name.indexOf('/') >= 0 || !name.matches("[a-z0-9_.-]{1,64}"))
            throw new IllegalArgumentException("cockpit filename must be a single lowercase id segment");
        return ResourceLocation.fromNamespaceAndPath(file.getNamespace(), "pod/cockpit/" + name);
    }
}
