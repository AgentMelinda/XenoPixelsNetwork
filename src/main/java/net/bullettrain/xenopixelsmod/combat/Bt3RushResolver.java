package net.bullettrain.xenopixelsmod.combat;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Resolves cinematic rush choreography with form, race, then universal fallback precedence. */
public final class Bt3RushResolver {

    private static final Bt3RushDefinition UNIVERSAL = profile("universal");
    private static final Map<String, Bt3RushDefinition> RACES = new LinkedHashMap<>();
    private static final Map<String, Bt3RushDefinition> FORMS = new LinkedHashMap<>();

    static {
        race("saiyan");
        race("human");
        race("namekian");
        race("majin");
        race("arcosian");

        form("super_saiyan_blue", "blue", "ssb", "super saiyan blue");
        form("super_saiyan_god", "god", "ssg", "super saiyan god");
        form("super_saiyan_3", "ssj3", "super saiyan 3");
        form("super_saiyan_2", "ssj2", "super saiyan 2");
        form("super_saiyan", "ssj", "super saiyan");
        form("golden_arcosian", "golden", "gold form");
        form("potential_unleashed", "potential unleashed", "ultimate form");
        form("orange_namekian", "orange");
        form("giant_namekian", "giant", "great namek");
        form("pure_majin", "pure majin", "kid majin");
    }

    private Bt3RushResolver() {
    }

    public static Bt3RushDefinition resolve(String race, String activeForm) {
        String form = normalize(activeForm);
        for (Map.Entry<String, Bt3RushDefinition> entry : FORMS.entrySet()) {
            if (!form.isEmpty() && form.contains(entry.getKey())) return entry.getValue();
        }
        String raceKey = normalize(race);
        if (raceKey.contains("saiyan")) return RACES.get("saiyan");
        if (raceKey.contains("human") || raceKey.contains("earthling")) return RACES.get("human");
        if (raceKey.contains("namek")) return RACES.get("namekian");
        if (raceKey.contains("majin")) return RACES.get("majin");
        if (raceKey.contains("arcos") || raceKey.contains("frieza")) return RACES.get("arcosian");
        return UNIVERSAL;
    }

    public static Bt3RushDefinition byId(String id) {
        if (id == null || id.isBlank() || UNIVERSAL.id().equals(id)) return UNIVERSAL;
        for (Bt3RushDefinition definition : FORMS.values()) {
            if (definition.id().equals(id)) return definition;
        }
        return RACES.getOrDefault(id, UNIVERSAL);
    }

    public static Collection<Bt3RushDefinition> all() {
        Map<String, Bt3RushDefinition> all = new LinkedHashMap<>();
        all.put(UNIVERSAL.id(), UNIVERSAL);
        RACES.values().forEach(definition -> all.put(definition.id(), definition));
        FORMS.values().forEach(definition -> all.put(definition.id(), definition));
        return all.values();
    }

    private static void race(String id) {
        RACES.put(id, profile(id));
    }

    private static void form(String id, String... aliases) {
        Bt3RushDefinition definition = profile(id);
        FORMS.put(normalize(id), definition);
        for (String alias : aliases) FORMS.put(normalize(alias), definition);
    }

    private static Bt3RushDefinition profile(String id) {
        return Bt3RushDefinition.standard(id, id);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').trim()
                .replaceAll("\\s+", " ");
    }
}
