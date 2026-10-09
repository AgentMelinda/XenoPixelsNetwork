package net.bullettrain.xenopixelsmod.combat.v3.technique;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Removes repeated owned attack names from the picker without mutating saved techniques. */
public final class V3TechniqueSelection {
    private V3TechniqueSelection() {}

    public static List<String> visible(List<String> ids) {
        return visible(ids, id -> V3TechniqueCatalog.owns(id) ? V3TechniqueCatalog.find(id) : null);
    }

    static List<String> visible(List<String> ids, Function<String, V3TechniqueDefinition> resolver) {
        Map<String, String> chosen = new LinkedHashMap<>();
        Map<String, V3TechniqueDefinition> definitions = new LinkedHashMap<>();
        for (String id : ids) {
            V3TechniqueDefinition definition = resolver.apply(id);
            if (definition == null) continue;
            definitions.put(id, definition);
            String key = definition.name().strip().toLowerCase(Locale.ROOT);
            String prior = chosen.get(key);
            if (prior == null || preferred(id, definition, prior, definitions.get(prior))) chosen.put(key, id);
        }
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            V3TechniqueDefinition definition = definitions.get(id);
            if (definition == null) {
                out.add(id);
            } else {
                String key = definition.name().strip().toLowerCase(Locale.ROOT);
                if (id.equals(chosen.get(key))) {
                    out.add(id);
                    chosen.remove(key);
                }
            }
        }
        return out;
    }

    private static boolean preferred(String id, V3TechniqueDefinition definition,
                                     String priorId, V3TechniqueDefinition prior) {
        int rank = rank(definition), priorRank = rank(prior);
        if (rank != priorRank) return rank > priorRank;
        boolean canonical = id.equals(definition.id()), priorCanonical = priorId.equals(prior.id());
        if (canonical != priorCanonical) return canonical;
        long start = definition.sourceStartsMs().stream().mapToLong(Long::longValue).min().orElse(Long.MAX_VALUE);
        long priorStart = prior.sourceStartsMs().stream().mapToLong(Long::longValue).min().orElse(Long.MAX_VALUE);
        return start != priorStart ? start < priorStart : id.compareTo(priorId) < 0;
    }

    private static int rank(V3TechniqueDefinition definition) {
        if (definition.choreographyComplete()) return 2;
        return "reference_timed_unverified".equals(definition.animationStatus())
                || "authored_gameplay_unverified".equals(definition.animationStatus()) ? 1 : 0;
    }
}
