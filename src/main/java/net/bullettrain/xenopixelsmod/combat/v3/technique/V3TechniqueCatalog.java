package net.bullettrain.xenopixelsmod.combat.v3.technique;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.level.ServerPlayer;

/**
 * The V3 attack catalog: every attack inventoried from the BT3 reference, as owned data.
 *
 * <p>Definitions are added to DragonMineZ's strike registry so they can be equipped to a technique
 * slot like any other strike. Registration only ever adds: an id that is already present, from
 * DragonMineZ or another addon, is left exactly as it is and reported.
 */
public final class V3TechniqueCatalog {
    private static final String RESOURCE = "/data/xenopixelsmod/combat_v3/techniques.json";
    private static final int MAX_TECHNIQUES = 1024;

    private static final Map<String, V3TechniqueDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Set<String> REGISTERED = new HashSet<>();
    private static final Set<String> REGISTERED_ALIASES = new HashSet<>();
    private static final Map<String, String> COMPATIBILITY_ALIASES = new LinkedHashMap<>();
    private static final Map<String, String> LEGACY_IDS = legacyIds();
    private static List<String> conflicts = List.of();

    private V3TechniqueCatalog() {}

    public static synchronized void load() {
        CatalogData catalog = loadBundledData();
        DEFINITIONS.clear();
        COMPATIBILITY_ALIASES.clear();
        for (V3TechniqueDefinition technique : catalog.techniques()) DEFINITIONS.put(technique.id(), technique);
        COMPATIBILITY_ALIASES.putAll(catalog.aliases());
    }

    public static synchronized V3TechniqueDefinition find(String id) {
        return id == null ? null : DEFINITIONS.get(canonicalId(id));
    }

    public static synchronized Collection<V3TechniqueDefinition> all() {
        return List.copyOf(DEFINITIONS.values());
    }

    /** Ids that already existed in the strike registry and were therefore left alone. */
    public static synchronized List<String> conflicts() { return conflicts; }

    /** Whether {@code id} is a V3 attack this mod actually put in the strike registry. */
    public static synchronized boolean owns(String id) {
        if (id == null) return false;
        String normalized = id.toLowerCase(java.util.Locale.ROOT);
        return REGISTERED.contains(normalized) || REGISTERED_ALIASES.contains(normalized);
    }

    static String canonicalId(String id) {
        if (id == null) return null;
        String normalized = id.toLowerCase(java.util.Locale.ROOT);
        normalized = LEGACY_IDS.getOrDefault(normalized, normalized);
        Set<String> visited = new HashSet<>();
        while (visited.add(normalized)) {
            String target = COMPATIBILITY_ALIASES.get(normalized);
            if (target == null) return normalized;
            normalized = target;
        }
        throw new IllegalStateException("Cyclic V3 technique alias " + id);
    }

    public static List<V3TechniqueDefinition> loadBundled() {
        return loadBundledData().techniques();
    }

    private static CatalogData loadBundledData() {
        try (InputStream in = V3TechniqueCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing " + RESOURCE);
            return parseData(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read " + RESOURCE, failure);
        }
    }

    static List<V3TechniqueDefinition> parse(Reader reader) {
        return parseData(reader).techniques();
    }

    private static CatalogData parseData(Reader reader) {
        JsonObject root;
        try {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException("Malformed V3 technique data", malformed);
        }
        if (!root.has("schema") || (root.get("schema").getAsInt() != 1 && root.get("schema").getAsInt() != 2)) {
            throw new IllegalArgumentException("Unsupported V3 technique schema");
        }
        JsonArray array = root.getAsJsonArray("techniques");
        if (array == null || array.size() > MAX_TECHNIQUES) throw new IllegalArgumentException("Bad technique list");
        List<V3TechniqueDefinition> out = new ArrayList<>(array.size());
        Set<String> seen = new HashSet<>();
        for (JsonElement element : array) {
            V3TechniqueDefinition technique = technique(element.getAsJsonObject());
            if (!seen.add(technique.id())) throw new IllegalArgumentException("Duplicate V3 technique " + technique.id());
            out.add(technique);
        }
        Map<String, String> aliases = new LinkedHashMap<>();
        JsonObject aliasJson = root.getAsJsonObject("compatibilityAliases");
        if (aliasJson != null) {
            for (Map.Entry<String, JsonElement> entry : aliasJson.entrySet()) {
                String alias = entry.getKey().toLowerCase(java.util.Locale.ROOT);
                String target = entry.getValue().getAsString().toLowerCase(java.util.Locale.ROOT);
                if (!alias.startsWith(V3TechniqueDefinition.OWNED_PREFIX) || alias.length() > 96
                        || !target.startsWith(V3TechniqueDefinition.OWNED_PREFIX) || !seen.contains(target)
                        || seen.contains(alias) || alias.equals(target) || aliases.putIfAbsent(alias, target) != null) {
                    throw new IllegalArgumentException("Invalid V3 technique alias " + alias);
                }
            }
        }
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            String target = alias.getValue();
            Set<String> visited = new HashSet<>();
            while (aliases.containsKey(target)) {
                if (!visited.add(target)) throw new IllegalArgumentException("Cyclic V3 technique alias " + alias.getKey());
                target = aliases.get(target);
            }
            if (!seen.contains(target)) throw new IllegalArgumentException("Missing V3 technique alias target " + target);
        }
        return new CatalogData(List.copyOf(out),
                Collections.unmodifiableMap(new LinkedHashMap<>(aliases)));
    }

    private static V3TechniqueDefinition technique(JsonObject json) {
        try {
            List<V3Beat> beats = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("beats")) {
                JsonObject beat = element.getAsJsonObject();
                V3Beat.Kind kind;
                try {
                    kind = V3Beat.Kind.valueOf(beat.get("kind").getAsString());
                } catch (IllegalArgumentException unknown) {
                    throw new IllegalArgumentException("Unknown V3 beat kind " + beat.get("kind"));
                }
                beats.add(new V3Beat(kind, beat.get("tick").getAsInt(), beat.get("duration").getAsInt(),
                        beat.get("payload").getAsString(), beat.get("value").getAsFloat()));
            }
            List<Long> starts = new ArrayList<>();
            for (JsonElement start : json.getAsJsonArray("sourceStartsMs")) starts.add(start.getAsLong());
            JsonElement ki = json.get("kiTechnique");
            String name = json.get("name").getAsString();
            JsonElement label = json.get("sourceLabel");
            List<V3CameraBeat> camera = new ArrayList<>();
            JsonArray shots = json.getAsJsonArray("camera");
            if (shots != null) {
                if (shots.size() > V3CameraBeat.MAX_BEATS) throw new IllegalArgumentException("Too many V3 camera beats");
                for (JsonElement element : shots) {
                    JsonObject shot = element.getAsJsonObject();
                    camera.add(new V3CameraBeat(shot.get("tick").getAsInt(), shot.get("duration").getAsInt(),
                            cameraVector(shot.getAsJsonArray("position")), cameraVector(shot.getAsJsonArray("look")),
                            shot.get("focus").getAsFloat(), V3CameraBeat.Easing.valueOf(shot.get("easing").getAsString())));
                }
            }
            Double range = null;
            if (json.has("range") && !json.get("range").isJsonNull()) {
                range = json.get("range").getAsDouble();
            }
            return new V3TechniqueDefinition(json.get("id").getAsString(), name,
                    label == null || label.isJsonNull() ? name : label.getAsString(),
                    json.get("type").getAsString(), json.get("durationTicks").getAsInt(),
                    json.get("kiCost").getAsDouble(), json.get("cooldownTicks").getAsInt(),
                    ki == null || ki.isJsonNull() ? null : ki.getAsString(),
                    json.get("animationStatus").getAsString(), starts, beats, camera, range);
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException("Malformed V3 technique entry", malformed);
        }
    }

    private static net.minecraft.world.phys.Vec3 cameraVector(JsonArray vector) {
        if (vector == null || vector.size() != 3) throw new IllegalArgumentException("Invalid V3 camera offset");
        return new net.minecraft.world.phys.Vec3(vector.get(0).getAsDouble(), vector.get(1).getAsDouble(), vector.get(2).getAsDouble());
    }

    /**
     * Adds every definition whose id is free. Existing entries are never replaced.
     *
     * @param alreadyOurs ids a previous call of ours registered, which are not collisions
     * @return ids held by somebody else, in catalog order
     */
    static <T> List<String> registerInto(Collection<V3TechniqueDefinition> techniques, Map<String, T> registry,
                                         Function<V3TechniqueDefinition, T> factory, Set<String> alreadyOurs) {
        List<String> taken = new ArrayList<>();
        for (V3TechniqueDefinition technique : techniques) {
            if (registry.containsKey(technique.id())) {
                if (!alreadyOurs.contains(technique.id())) taken.add(technique.id());
                continue;
            }
            registry.put(technique.id(), factory.apply(technique));
        }
        return List.copyOf(taken);
    }

    /** Loads the bundled data and adds it to DragonMineZ's strike registry. Safe to call again. */
    public static synchronized void register() {
        try {
            load();
        } catch (RuntimeException invalid) {
            // Bad owned data must not take the mod down; V3 techniques are simply absent.
            XenoPixelsMod.LOGGER.error("Combat V3 technique data rejected; no V3 techniques registered", invalid);
            DEFINITIONS.clear();
            return;
        }
        Set<String> before = new HashSet<>(PredefinedTechniques.STRIKE_REGISTRY.keySet());
        List<String> foundConflicts = new ArrayList<>(registerInto(DEFINITIONS.values(), PredefinedTechniques.STRIKE_REGISTRY,
                V3TechniqueCatalog::strike, Set.copyOf(REGISTERED)));
        for (V3TechniqueDefinition technique : DEFINITIONS.values()) {
            if (!before.contains(technique.id()) && PredefinedTechniques.STRIKE_REGISTRY.containsKey(technique.id())) {
                REGISTERED.add(technique.id());
            }
        }
        Map<String, String> aliases = new LinkedHashMap<>(COMPATIBILITY_ALIASES);
        for (Map.Entry<String, String> legacy : LEGACY_IDS.entrySet()) {
            aliases.put(legacy.getKey(), canonicalId(legacy.getValue()));
        }
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            V3TechniqueDefinition technique = DEFINITIONS.get(canonicalId(alias.getValue()));
            if (technique == null || !REGISTERED.contains(technique.id())) continue;
            if (PredefinedTechniques.STRIKE_REGISTRY.containsKey(alias.getKey())) {
                if (!REGISTERED.contains(alias.getKey()) && !REGISTERED_ALIASES.contains(alias.getKey())) {
                    foundConflicts.add(alias.getKey());
                }
                continue;
            }
            PredefinedTechniques.STRIKE_REGISTRY.put(alias.getKey(), strike(technique, alias.getKey()));
            REGISTERED_ALIASES.add(alias.getKey());
        }
        conflicts = List.copyOf(foundConflicts);
        for (String id : conflicts) {
            XenoPixelsMod.LOGGER.warn("Combat V3 technique {} not registered: the id is already defined elsewhere", id);
        }
        long unfinished = DEFINITIONS.values().stream().filter(t -> !t.choreographyComplete()).count();
        XenoPixelsMod.LOGGER.info("Combat V3 techniques: {} registered, {} collisions, {} awaiting in-game choreography comparison",
                REGISTERED.size(), conflicts.size(), unfinished);
    }

    private static StrikeAttackData strike(V3TechniqueDefinition technique) {
        return strike(technique, technique.id());
    }

    private static StrikeAttackData strike(V3TechniqueDefinition technique, String id) {
        StrikeAttackData data = new StrikeAttackData();
        data.setId(id);
        data.setName(technique.name());
        data.setAuthor("XenoPixels");
        // The cast is intercepted before DragonMineZ's own strike runs, so these never apply;
        // V3 charges its own cost and deals its own damage at the timeline's contact beats.
        data.setDamageMultiplier(0.0f);
        data.setDurationTicks(1);
        data.setBaseCost(0.0);
        data.applyConfigDefaults();
        return data;
    }

    private record CatalogData(List<V3TechniqueDefinition> techniques, Map<String, String> aliases) {}

    private static Map<String, String> legacyIds() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("xenopixelsmod:bt3_raff_energ_max_potenza", "xenopixelsmod:bt3_raff_ener_max_potenza");
        aliases.put("xenopixelsmod:bt3_super_onda_esplosiva", "xenopixelsmod:bt3_onda_super_esplosiva");
        return Collections.unmodifiableMap(aliases);
    }

    /** Makes every registered V3 attack equippable for {@code player}. Idempotent. */
    public static void unlockAll(ServerPlayer player) {
        if (player == null) return;
        StatsData data;
        try {
            data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        } catch (RuntimeException | LinkageError unavailable) {
            return;
        }
        if (data == null || data.getTechniques() == null) return;
        List<String> ids;
        synchronized (V3TechniqueCatalog.class) {
            ids = REGISTERED.stream().filter(DEFINITIONS::containsKey).toList();
        }
        for (String id : ids) {
            StrikeAttackData strike = PredefinedTechniques.STRIKE_REGISTRY.get(id);
            if (strike != null) data.getTechniques().unlockTechnique(strike);
        }
    }
}
