package net.bullettrain.xenopixelsmod.dmz.form;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The data one DragonMineZ skill-master interaction puts on screen.
 *
 * <p>Deliberately free of Minecraft and client types: the server builds it, the form-editor
 * channel carries it, and the screen renders it. Because the packet class is loaded on dedicated
 * servers too, the payload cannot be a client screen type — this record is the neutral carrier.
 *
 * <p>Locale resolution is left to the client, which is the only side that knows the player's
 * language. The server therefore ships the raw per-locale maps rather than a single resolved
 * string, and the client applies the usual chain: requested locale, then {@code en_us}, then the
 * internal id.
 */
public record DmzTrainerMenu(String title, Map<String, String> groupNames,
                             Map<String, String> body, List<Entry> entries) {

    /** Offered forms one menu may carry. Matches the row cap the screen renders. */
    public static final int MAX_ENTRIES = 32;
    /** Locale entries one menu map may carry; mirrors {@link DmzSkillMaster#MAX_MENU_LOCALES}. */
    public static final int MAX_LOCALES = DmzSkillMaster.MAX_MENU_LOCALES;
    /** Characters one menu heading may carry. */
    public static final int MAX_TITLE_LENGTH = DmzSkillMaster.MAX_MENU_TITLE_LENGTH;
    /** Characters one body or group-name entry may carry. */
    public static final int MAX_BODY_LENGTH = DmzSkillMaster.MAX_MENU_BODY_LENGTH;
    /** Characters a locale key may carry, matching the longest shipped key plus headroom. */
    public static final int MAX_LOCALE_LENGTH = 16;
    /** Characters a trainer name may carry. */
    public static final int MAX_NAME_LENGTH = 128;
    /** Characters any one entry identifier may carry. */
    public static final int MAX_ID_LENGTH = 64;
    /** Characters the {@code kind} discriminator may carry. */
    public static final int MAX_KIND_LENGTH = 16;

    public static final DmzTrainerMenu EMPTY =
            new DmzTrainerMenu("", Map.of(), Map.of(), List.of());

    /** One offered form: its identity plus the server's {@code en_us} label as a last resort. */
    public record Entry(String kind, String race, String group, String formType,
                        String formId, String label) {
        public Entry {
            kind = clamp(kind, MAX_KIND_LENGTH);
            race = clamp(race, MAX_ID_LENGTH);
            group = clamp(group, MAX_ID_LENGTH);
            formType = clamp(formType, MAX_ID_LENGTH);
            formId = clamp(formId, MAX_ID_LENGTH);
            label = clamp(label, MAX_ID_LENGTH);
        }
    }

    public DmzTrainerMenu {
        title = clamp(title, MAX_TITLE_LENGTH);
        groupNames = sanitize(groupNames);
        body = sanitize(body);
        if (entries == null) {
            entries = List.of();
        } else if (entries.size() > MAX_ENTRIES) {
            entries = List.copyOf(entries.subList(0, MAX_ENTRIES));
        } else {
            entries = List.copyOf(entries);
        }
    }

    /**
     * The player-facing heading: the configured per-NPC title, then the group name in the
     * requested locale, then {@code en_us}, then the caller's fallback (the entity name).
     */
    public String resolveTitle(String locale, String fallback) {
        if (title != null && !title.isBlank()) return title;
        String localized = localized(groupNames, locale);
        if (localized != null) return localized;
        return fallback == null ? "" : fallback;
    }

    /** The configured body text in the requested locale, or an empty string when none is set. */
    public String resolveBody(String locale) {
        String localized = localized(body, locale);
        return localized == null ? "" : localized;
    }

    private static String localized(Map<String, String> values, String locale) {
        if (values == null || values.isEmpty()) return null;
        String value = values.get(normalize(locale));
        if (value == null || value.isBlank()) value = values.get("en_us");
        return value == null || value.isBlank() ? null : value;
    }

    private static Map<String, String> sanitize(Map<String, String> values) {
        if (values == null || values.isEmpty()) return Map.of();
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        int kept = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (kept >= MAX_LOCALES) break;
            String key = normalize(entry.getKey());
            String value = entry.getValue();
            if (key.isEmpty() || value == null || value.isBlank()) continue;
            result.put(key, clamp(value, MAX_BODY_LENGTH));
            kept++;
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private static String normalize(String locale) {
        return locale == null ? "" : locale.toLowerCase(Locale.ROOT).replace('-', '_').trim();
    }

    private static String clamp(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

}