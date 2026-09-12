package net.bullettrain.xenopixelsmod.dmz.form;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DmzFormMetadataRegistry {
    /**
     * DragonMineZ resolves every form and group label through this translation-key prefix; see
     * {@code com.dragonminez.client.gui.radial.nodes.FormSelectNode#label} in dragonminez-2.1.3.
     */
    public static final String TRANSLATION_PREFIX = "race.dragonminez.";
    /**
     * DragonMineZ labels a form's skill entry with this prefix; see
     * {@code SkillsMenuScreen} in dragonminez-2.1.3, which renders
     * {@code skill.dragonminez.<formType>} in the skills list and the forms-tree tooltip. A form
     * type created by the Form Studio has no entry in DragonMineZ's own language table, so without
     * this the UI would show the raw key (for example {@code skill.dragonminez.customforms}).
     */
    public static final String SKILL_PREFIX = "skill.dragonminez.";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, DmzFormMetadata> ENTRIES = new LinkedHashMap<>();
    /** Translation key to locale-to-name map, rebuilt whenever {@link #ENTRIES} changes. */
    private static final Map<String, Map<String, String>> TRANSLATIONS = new LinkedHashMap<>();

    private DmzFormMetadataRegistry() {
    }

    public static synchronized void loadFromDisk() {
        ENTRIES.clear();
        Path root = metadataRoot();
        if (!Files.isDirectory(root)) return;
        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> loadOne(root, path));
        } catch (IOException exception) {
            XenoPixelsMod.LOGGER.error("Failed scanning DMZ form metadata", exception);
        }
        rebuildTranslations();
    }

    private static void loadOne(Path root, Path path) {
        try {
            DmzFormMetadata metadata = GSON.fromJson(Files.readString(path), DmzFormMetadata.class);
            if (metadata == null || !validId(metadata.group)) return;
            DmzFormKind kind = path.startsWith(root.resolve("stack"))
                    ? DmzFormKind.STACK : DmzFormKind.NORMAL;
            ENTRIES.put(key(kind, metadata.race, metadata.group), metadata);
        } catch (Exception exception) {
            XenoPixelsMod.LOGGER.error("Failed loading DMZ form metadata {}", path, exception);
        }
    }

    public static synchronized String snapshotJson() {
        return GSON.toJson(ENTRIES);
    }

    public static synchronized void applySnapshot(String json) {
        ENTRIES.clear();
        try {
            if (json != null && !json.isBlank()) {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                for (var entry : root.entrySet()) {
                    DmzFormMetadata metadata = GSON.fromJson(entry.getValue(), DmzFormMetadata.class);
                    if (metadata != null) ENTRIES.put(entry.getKey(), metadata);
                }
            }
        } finally {
            rebuildTranslations();
        }
    }

    public static synchronized DmzFormMetadata get(DmzFormKind kind, String race, String group) {
        return ENTRIES.get(key(kind, race, group));
    }

    /** Every stored group, with the kind encoded in its registry key. */
    public static synchronized List<TrainerOffering> all() {
        List<TrainerOffering> result = new ArrayList<>();
        for (Map.Entry<String, DmzFormMetadata> entry : ENTRIES.entrySet()) {
            if (entry.getValue() == null) continue;
            DmzFormKind kind = entry.getKey() != null && entry.getKey().startsWith("stack:")
                    ? DmzFormKind.STACK : DmzFormKind.NORMAL;
            result.add(new TrainerOffering(kind, entry.getValue()));
        }
        return result;
    }

    public static synchronized void put(DmzFormKind kind, DmzFormMetadata metadata) {
        ENTRIES.put(key(kind, metadata.race, metadata.group), metadata);
        rebuildTranslations();
    }

    /**
     * Resolves a DragonMineZ form or group translation key against the configured names.
     *
     * <p>Returns {@code null} for every key this mod does not own, so callers can fall straight
     * back to the vanilla language table. Resolution order is the requested locale, then
     * {@code en_us}, and the caller is expected to fall back to the internal id after that.
     */
    public static synchronized String translate(String key, String locale) {
        if (key == null
                || (!key.startsWith(TRANSLATION_PREFIX) && !key.startsWith(SKILL_PREFIX))) {
            return null;
        }
        Map<String, String> names = TRANSLATIONS.get(key);
        if (names == null || names.isEmpty()) return null;
        String localized = names.get(normalizeLocale(locale));
        if (localized == null || localized.isBlank()) localized = names.get("en_us");
        return localized == null || localized.isBlank() ? null : localized;
    }

    /** Translation key DragonMineZ uses for one form label. */
    public static String formKey(DmzFormKind kind, String race, String group, String form) {
        return kind == DmzFormKind.STACK
                ? TRANSLATION_PREFIX + "stack.form." + safe(group) + "." + safe(form)
                : TRANSLATION_PREFIX + safe(race) + ".form." + safe(group) + "." + safe(form);
    }

    /**
     * Translation key DragonMineZ uses for the skill entry that gates one form type.
     *
     * <p>Kept lowercase because {@code SkillsMenuScreen} lowercases the form type before building
     * its keys, while the metadata may carry the id in a different case.
     */
    public static String skillKey(String formType) {
        return SKILL_PREFIX + safe(formType).toLowerCase(Locale.ROOT);
    }

    /** Translation key DragonMineZ uses for one form-group label. */
    public static String groupKey(DmzFormKind kind, String race, String group) {
        return kind == DmzFormKind.STACK
                ? TRANSLATION_PREFIX + "stack.group." + safe(group)
                : TRANSLATION_PREFIX + safe(race) + ".group." + safe(group);
    }

    private static void rebuildTranslations() {
        TRANSLATIONS.clear();
        for (Map.Entry<String, DmzFormMetadata> entry : ENTRIES.entrySet()) {
            DmzFormMetadata metadata = entry.getValue();
            if (metadata == null || metadata.group == null || metadata.group.isBlank()) continue;
            DmzFormKind kind = entry.getKey().startsWith("stack:") ? DmzFormKind.STACK : DmzFormKind.NORMAL;
            if (metadata.groupNames != null && !metadata.groupNames.isEmpty()) {
                TRANSLATIONS.put(groupKey(kind, metadata.race, metadata.group),
                        new LinkedHashMap<>(metadata.groupNames));
                if (metadata.formType != null && !metadata.formType.isBlank()) {
                    TRANSLATIONS.put(skillKey(metadata.formType),
                            new LinkedHashMap<>(metadata.groupNames));
                }
            }
            if (metadata.forms == null) continue;
            for (Map.Entry<String, DmzFormMetadata.FormEntry> form : metadata.forms.entrySet()) {
                DmzFormMetadata.FormEntry value = form.getValue();
                if (value == null || value.names == null || value.names.isEmpty()) continue;
                TRANSLATIONS.put(formKey(kind, metadata.race, metadata.group, form.getKey()),
                        new LinkedHashMap<>(value.names));
            }
        }
    }

    /**
     * One group that lists a trainer, paired with the kind it was stored under.
     *
     * <p>The kind is not a field of {@link DmzFormMetadata} — it lives in the registry key — so a
     * caller that needs to build normal-versus-stack paths (the menu payload, for example) has to
     * receive it alongside the metadata rather than guess it.
     */
    public record TrainerOffering(DmzFormKind kind, DmzFormMetadata metadata) {
    }

    /** Every group listing {@code trainerId}, with the kind each group belongs to. */
    public static synchronized List<TrainerOffering> trainerOfferingEntries(UUID trainerId) {
        List<TrainerOffering> result = new ArrayList<>();
        if (trainerId == null) return result;
        String expected = trainerId.toString();
        for (Map.Entry<String, DmzFormMetadata> entry : ENTRIES.entrySet()) {
            DmzFormMetadata metadata = entry.getValue();
            if (metadata == null || metadata.customNpcTrainers == null) {
                continue;
            }
            for (DmzFormMetadata.TrainerRef trainer : metadata.customNpcTrainers) {
                if (trainer != null && expected.equalsIgnoreCase(trainer.uuid)
                        && (trainer.skillMaster || metadata.masterLearningEnabled)) {
                    DmzFormKind kind = entry.getKey().startsWith("stack:")
                            ? DmzFormKind.STACK : DmzFormKind.NORMAL;
                    result.add(new TrainerOffering(kind, metadata));
                    break;
                }
            }
        }
        return result;
    }

    public static synchronized List<DmzFormMetadata> trainerOfferings(UUID trainerId) {
        List<DmzFormMetadata> result = new ArrayList<>();
        for (TrainerOffering offering : trainerOfferingEntries(trainerId)) {
            result.add(offering.metadata());
        }
        return result;
    }

    public static synchronized String displayName(DmzFormKind kind, String race, String group,
                                                   String form, String locale) {
        DmzFormMetadata metadata = get(kind, race, group);
        if (metadata == null) return "";
        DmzFormMetadata.FormEntry entry = metadata.forms.get(form);
        if (entry == null || entry.names == null) return "";
        String localized = entry.names.get(normalizeLocale(locale));
        if (localized == null || localized.isBlank()) localized = entry.names.get("en_us");
        return localized == null ? "" : localized;
    }

    public static synchronized ResourceLocation formIcon(DmzFormKind kind, String race, String group,
                                                          String form) {
        DmzFormMetadata metadata = get(kind, race, group);
        if (metadata == null) return null;
        DmzFormMetadata.FormEntry entry = metadata.forms.get(form);
        return icon(entry == null ? "" : entry.icon, true);
    }

    public static synchronized ResourceLocation formTypeIcon(String formType) {
        return formTypeIcon(formType, true);
    }

    public static synchronized ResourceLocation formTypeSkillIcon(String formType) {
        return formTypeIcon(formType, false);
    }

    private static ResourceLocation formTypeIcon(String formType, boolean radial) {
        if (formType == null || formType.isBlank()) return null;
        for (DmzFormMetadata metadata : ENTRIES.values()) {
            if (formType.equalsIgnoreCase(metadata.formType)) {
                ResourceLocation icon = icon(metadata.formTypeIcon, radial);
                if (icon != null) return icon;
            }
        }
        return null;
    }

    public static Path metadataPath(DmzFormKind kind, String race, String group) {
        return metadataPath(metadataRoot(), kind, race, group);
    }

    /**
     * Versioned metadata location under {@code metadataRoot}, refusing any race or group id that
     * would escape it. DragonMineZ never reads this tree; it holds only the XenoPixels-owned
     * names, icons, revision and trainer records.
     */
    static Path metadataPath(Path metadataRoot, DmzFormKind kind, String race, String group) {
        if (!validId(group)) throw new IllegalArgumentException("Invalid form group id: " + group);
        Path base = metadataRoot.toAbsolutePath().normalize()
                .resolve(kind == DmzFormKind.STACK ? "stack" : "normal");
        Path path;
        if (kind == DmzFormKind.STACK) {
            path = base.resolve(group + ".json");
        } else {
            if (!validId(race)) throw new IllegalArgumentException("Invalid race id: " + race);
            path = base.resolve(race).resolve(group + ".json");
        }
        path = path.toAbsolutePath().normalize();
        if (!path.startsWith(base)) throw new IllegalArgumentException("Metadata path escaped its root");
        return path;
    }

    public static Gson gson() {
        return GSON;
    }

    private static Path metadataRoot() {
        return FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod").resolve("dmz-form-editor");
    }

    private static String key(DmzFormKind kind, String race, String group) {
        return kind.name().toLowerCase(Locale.ROOT) + ":"
                + (kind == DmzFormKind.STACK ? "" : safe(race).toLowerCase(Locale.ROOT)) + ":"
                + safe(group).toLowerCase(Locale.ROOT);
    }

    private static ResourceLocation icon(String icon, boolean radial) {
        if (icon == null || icon.isBlank()) return null;
        boolean explicitNamespace = icon.indexOf(':') >= 0;
        ResourceLocation parsed = ResourceLocation.tryParse(icon);
        if (parsed != null && parsed.getPath().startsWith("textures/")) {
            return explicitNamespace ? parsed : ResourceLocation.tryBuild(XenoPixelsMod.MOD_ID, parsed.getPath());
        }
        String logical = parsed == null ? icon : parsed.getPath();
        String namespace = explicitNamespace && parsed != null ? parsed.getNamespace() : XenoPixelsMod.MOD_ID;
        return ResourceLocation.tryBuild(namespace,
                "textures/gui/" + (radial ? "radial/" : "icons/") + logical + ".png");
    }

    /** Same id shape {@code DmzFormEditorService} enforces, so a path can never be traversed. */
    private static boolean validId(String value) {
        return value != null && value.matches("[a-z0-9][a-z0-9_.-]*") && !value.contains("..");
    }

    private static String normalizeLocale(String locale) {
        return safe(locale).toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
