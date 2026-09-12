package net.bullettrain.xenopixelsmod.dmz.form;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One in-progress edit of a DragonMineZ form group plus the XenoPixels metadata that travels
 * with it.
 *
 * <p>The DragonMineZ side is held as the raw Gson tree of a {@link FormConfig} rather than as a
 * typed object, so every field that dragonminez-2.1.3 actually serialises is editable without
 * this class having to mirror the schema. {@code FormConfig.FormData} initialises every persisted
 * field to a non-null default, so a round trip through {@code toJsonTree} lists them all.
 */
public final class DmzFormDocument {
    /** Widget class the editor should use for one field. */
    public enum Kind {
        /** Free text. */
        TEXT,
        /** Hexadecimal colour with a picker. */
        COLOR,
        /** {@code true}/{@code false}. */
        BOOL,
        /** Any finite number. */
        NUMBER,
        /** Raw JSON array or object. */
        JSON,
        /** One {@code modelScaling} axis, or the uniform control over all three. */
        SCALE
    }

    /** Synthetic field key for the uniform body-size control. */
    public static final String SCALE_UNIFORM = "$scaleUniform";
    private static final String[] SCALE_AXES = {"$scaleX", "$scaleY", "$scaleZ"};
    private static final String[] SCALE_LABELS = {"Body Size X", "Body Size Y", "Body Size Z"};
    private static final float DEFAULT_SCALE = 0.9375F;

    private final DmzFormKind kind;
    private String race;
    private String group;
    private String form;
    private JsonObject root;
    private DmzFormMetadata metadata;
    private boolean created;

    private DmzFormDocument(DmzFormKind kind, String race, String group, String form,
                            JsonObject root, DmzFormMetadata metadata, boolean created) {
        this.kind = kind;
        this.race = safe(race);
        this.group = safe(group);
        this.form = safe(form);
        this.root = root;
        this.metadata = metadata;
        this.created = created;
    }

    public static DmzFormDocument load(DmzFormKind kind, String race, String group, String form) {
        FormConfig config = kind == DmzFormKind.STACK
                ? ConfigManager.getStackFormGroup(group) : ConfigManager.getFormGroup(race, group);
        if (config == null || !config.getForms().containsKey(form)) return create(kind, race);
        JsonObject root = DmzFormMetadataRegistry.gson().toJsonTree(config).getAsJsonObject();
        DmzFormMetadata metadata = DmzFormMetadataRegistry.get(kind, race, group);
        if (metadata == null) metadata = defaults(kind, race, group, config.getFormType(), form);
        else metadata = DmzFormMetadataRegistry.gson().fromJson(
                DmzFormMetadataRegistry.gson().toJson(metadata), DmzFormMetadata.class);
        metadata.groupNames.putIfAbsent("en_us", title(group));
        metadata.form(form).names.putIfAbsent("en_us", title(form));
        return new DmzFormDocument(kind, race, group, form, root, metadata, true);
    }

    public static DmzFormDocument create(DmzFormKind kind, String race) {
        String group = "custom_group";
        String form = "custom_form";
        FormConfig config = new FormConfig();
        config.setConfigVersion(FormConfig.CURRENT_VERSION);
        config.setGroupName(group);
        config.setFormType(group);
        FormConfig.FormData data = new FormConfig.FormData();
        data.setName(form);
        Map<String, FormConfig.FormData> forms = new LinkedHashMap<>();
        forms.put(form, data);
        config.setForms(forms);
        return new DmzFormDocument(kind, kind == DmzFormKind.STACK ? "" : safe(race),
                group, form, DmzFormMetadataRegistry.gson().toJsonTree(config).getAsJsonObject(),
                defaults(kind, race, group, group, form), false);
    }

    private static DmzFormMetadata defaults(DmzFormKind kind, String race, String group,
                                            String formType, String form) {
        DmzFormMetadata metadata = new DmzFormMetadata();
        metadata.race = kind == DmzFormKind.STACK ? "" : safe(race);
        metadata.group = group;
        metadata.formType = formType;
        metadata.formTypeIcon = "xeno_form_01";
        metadata.groupNames.put("en_us", title(group));
        metadata.form(form).icon = "xeno_form_01";
        metadata.form(form).names.put("en_us", title(form));
        return metadata;
    }

    /**
     * Duplicates this document — including an edit the operator has not saved yet — into a fresh
     * draft. Every DragonMineZ setting and every XenoPixels preset field is carried over
     * verbatim; only the identity changes, so the copy lands on a group that is neither bundled
     * nor already owned and therefore passes {@link DmzFormProtection} without an override.
     */
    public DmzFormDocument copyAsNewDraft() {
        String groupBase = isId(group) ? group : "custom_group";
        if (groupBase.length() > 48) groupBase = groupBase.substring(0, 48);
        String newGroup = groupBase + "_copy" + token();
        for (int attempt = 0; attempt < 32 && !idAvailable(kind, race, newGroup); attempt++) {
            newGroup = groupBase + "_copy" + token();
        }
        if (!idAvailable(kind, race, newGroup)) {
            newGroup = groupBase + "_copy" + Long.toUnsignedString(System.nanoTime());
        }
        String formBase = isId(form) ? form : "custom_form";
        if (formBase.length() > 48) formBase = formBase.substring(0, 48);
        return duplicate(this, newGroup, formBase + "_copy" + token());
    }

    /** Identity-rewriting half of {@link #copyAsNewDraft()}. */
    private static DmzFormDocument duplicate(DmzFormDocument source, String newGroup,
                                             String newForm) {
        JsonObject root = source.root.deepCopy();
        JsonObject data = source.formData().deepCopy();
        data.addProperty("name", newForm);
        JsonObject forms = new JsonObject();
        forms.add(newForm, data);
        root.add("forms", forms);
        root.addProperty("groupName", newGroup);
        // DragonMineZ derives the skill type from the group, so the copy needs its own formType.
        root.addProperty("formType", newGroup);

        DmzFormMetadata metadata = DmzFormMetadataRegistry.gson().fromJson(
                DmzFormMetadataRegistry.gson().toJson(source.metadata), DmzFormMetadata.class);
        metadata.revision = 0L;
        metadata.race = source.kind == DmzFormKind.STACK ? "" : source.race;
        metadata.group = newGroup;
        metadata.formType = newGroup;
        // Trainer associations belong to the source group, not to the duplicate.
        metadata.customNpcTrainers = new ArrayList<>();
        DmzFormMetadata.FormEntry sourceEntry = metadata.forms.get(source.form);
        DmzFormMetadata.FormEntry entry = sourceEntry == null
                ? new DmzFormMetadata.FormEntry() : sourceEntry;
        entry.names = new LinkedHashMap<>();
        entry.names.put("en_us", copyName(namesOf(source.metadata, source.form), source.form));
        metadata.forms = new LinkedHashMap<>();
        metadata.forms.put(newForm, entry);
        metadata.groupNames = new LinkedHashMap<>();
        metadata.groupNames.put("en_us", copyName(source.metadata.groupNames, source.group));

        return new DmzFormDocument(source.kind, metadata.race, newGroup, newForm,
                root, metadata, false);
    }

    /**
     * Reports why this draft cannot be created yet, or {@code null} when the identity fields are
     * complete. New forms stay local until this passes, so a half-typed id is never written.
     */
    public String identityError() {
        if (kind == DmzFormKind.NORMAL && !isId(race)) return "Race id must start a-z0-9, then a-z0-9_.-";
        if (!isId(group)) return "Group id must start a-z0-9, then a-z0-9_.-";
        if (!isId(form)) return "Form id must start a-z0-9, then a-z0-9_.-";
        if (!isId(string(root, "formType"))) return "Form type must start a-z0-9, then a-z0-9_.-";
        if (!created && DmzFormMetadataRegistry.get(kind, race, group) != null) {
            return "Group " + group + " is already owned by the form studio";
        }
        return null;
    }

    public List<Field> fields() {
        List<Field> fields = new ArrayList<>();
        add(fields, "$race", "Race", race, Kind.TEXT);
        add(fields, "$group", "Group ID", group, Kind.TEXT);
        add(fields, "$form", "Form ID", form, Kind.TEXT);
        add(fields, "$formType", "Form Type", string(root, "formType"), Kind.TEXT);
        DmzFormMetadata.FormEntry extras = metadata.form(form);
        add(fields, "$displayName", "Name (en_us)", extras.names.getOrDefault("en_us", form), Kind.TEXT);
        add(fields, "$locales", "Name Locales JSON", json(extras.names), Kind.JSON);
        add(fields, "$groupName", "Group Name (en_us)",
                metadata.groupNames.getOrDefault("en_us", group), Kind.TEXT);
        add(fields, "$groupLocales", "Group Locales JSON", json(metadata.groupNames), Kind.JSON);
        add(fields, "$formIcon", "Form Icon", extras.icon, Kind.TEXT);
        add(fields, "$formTypeIcon", "Type Icon", metadata.formTypeIcon, Kind.TEXT);
        add(fields, "$skillCosts", "TP Costs JSON", json(metadata.skillCosts), Kind.JSON);
        add(fields, "$nativeMasters", "Native Masters JSON", json(metadata.nativeMasters), Kind.JSON);
        add(fields, "$customTrainers", "Custom Trainers JSON", json(metadata.customNpcTrainers), Kind.JSON);
        add(fields, SCALE_UNIFORM, "Body Size (all)", format(uniformScale()), Kind.SCALE);
        JsonObject formData = formData();
        for (Map.Entry<String, JsonElement> entry : formData.entrySet()) {
            String key = entry.getKey();
            if ("name".equals(key)) continue;
            if ("modelScaling".equals(key)) {
                for (int axis = 0; axis < SCALE_AXES.length; axis++) {
                    add(fields, SCALE_AXES[axis], SCALE_LABELS[axis], format(scale(axis)), Kind.SCALE);
                }
                continue;
            }
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonObject()) {
                for (Map.Entry<String, JsonElement> nested : value.getAsJsonObject().entrySet()) {
                    String nestedKey = key + "." + nested.getKey();
                    add(fields, nestedKey, label(nestedKey), compact(nested.getValue()),
                            kindOf(nested.getKey(), nested.getValue()));
                }
                continue;
            }
            add(fields, key, label(key), compact(value), kindOf(key, value));
        }
        return fields;
    }

    public void update(String key, String raw) {
        String value = raw == null ? "" : raw.trim();
        switch (key) {
            case "$race" -> {
                if (kind == DmzFormKind.NORMAL && !created) race = value.toLowerCase(Locale.ROOT);
            }
            case "$group" -> {
                if (!created) renameGroup(value.toLowerCase(Locale.ROOT));
            }
            case "$form" -> {
                if (!created) renameForm(value.toLowerCase(Locale.ROOT));
            }
            case "$formType" -> {
                root.addProperty("formType", value.toLowerCase(Locale.ROOT));
                metadata.formType = value.toLowerCase(Locale.ROOT);
            }
            case "$displayName" -> metadata.form(form).names.put("en_us", value);
            case "$locales" -> metadata.form(form).names = parseStringMap(value);
            case "$groupName" -> metadata.groupNames.put("en_us", value);
            case "$groupLocales" -> metadata.groupNames = parseStringMap(value);
            case "$formIcon" -> metadata.form(form).icon = value;
            case "$formTypeIcon" -> metadata.formTypeIcon = value;
            case "$skillCosts" -> metadata.skillCosts = parseList(value, Integer[].class, Integer.class);
            case "$nativeMasters" -> metadata.nativeMasters = parseList(value, String[].class, String.class);
            case "$customTrainers" -> metadata.customNpcTrainers = parseList(
                    value, DmzFormMetadata.TrainerRef[].class, DmzFormMetadata.TrainerRef.class);
            case SCALE_UNIFORM -> {
                float scale = parseScale(value);
                for (int axis = 0; axis < SCALE_AXES.length; axis++) setScale(axis, scale);
            }
            default -> {
                int axis = axisOf(key);
                if (axis >= 0) setScale(axis, parseScale(value));
                else updateFormValue(key, value);
            }
        }
        metadata.race = race;
        metadata.group = group;
        metadata.formType = string(root, "formType");
    }

    private void updateFormValue(String key, String value) {
        JsonObject data = formData();
        int dot = key.indexOf('.');
        if (dot > 0) {
            String parent = key.substring(0, dot);
            if (data.has(parent) && data.get(parent).isJsonObject()) {
                writeValue(data.getAsJsonObject(parent), key.substring(dot + 1), value);
                return;
            }
        }
        writeValue(data, key, value);
    }

    private static void writeValue(JsonObject owner, String key, String value) {
        JsonElement old = owner.get(key);
        if (old == null || old.isJsonNull()) owner.addProperty(key, value);
        else if (old.isJsonArray() || old.isJsonObject()) owner.add(key, JsonParser.parseString(value));
        else if (old.getAsJsonPrimitive().isBoolean()) owner.addProperty(key, Boolean.parseBoolean(value));
        else if (old.getAsJsonPrimitive().isNumber()) owner.addProperty(key, new BigDecimal(value));
        else owner.addProperty(key, value);
    }

    private void renameGroup(String next) {
        String previous = group;
        group = next;
        root.addProperty("groupName", next);
        metadata.group = next;
        if (metadata.groupNames.isEmpty()
                || title(previous).equals(metadata.groupNames.get("en_us"))) {
            metadata.groupNames.put("en_us", title(next));
        }
    }

    private void renameForm(String next) {
        JsonObject forms = root.getAsJsonObject("forms");
        JsonElement data = forms.remove(form);
        if (data == null) data = new JsonObject();
        data.getAsJsonObject().addProperty("name", next);
        DmzFormMetadata.FormEntry extras = metadata.forms.remove(form);
        String previous = form;
        form = next;
        forms.add(form, data);
        metadata.forms.put(form, extras == null ? new DmzFormMetadata.FormEntry() : extras);
        DmzFormMetadata.FormEntry entry = metadata.form(form);
        if (entry.names.isEmpty() || title(previous).equals(entry.names.get("en_us"))) {
            entry.names.put("en_us", title(next));
        }
    }

    // -- modelScaling -------------------------------------------------------

    /** One {@code modelScaling} axis, falling back to DragonMineZ's own default. */
    public float scale(int axis) {
        JsonElement element = formData().get("modelScaling");
        if (element == null || !element.isJsonArray()) return DEFAULT_SCALE;
        var array = element.getAsJsonArray();
        if (axis < 0 || axis >= array.size() || array.get(axis).isJsonNull()) return DEFAULT_SCALE;
        try {
            return array.get(axis).getAsFloat();
        } catch (RuntimeException ignored) {
            return DEFAULT_SCALE;
        }
    }

    /** Mean of the three axes, which is what the uniform control shows and writes. */
    public float uniformScale() {
        return (scale(0) + scale(1) + scale(2)) / 3.0F;
    }

    public void setScale(int axis, float value) {
        JsonObject data = formData();
        JsonElement element = data.get("modelScaling");
        var array = element != null && element.isJsonArray()
                ? element.getAsJsonArray() : new com.google.gson.JsonArray();
        while (array.size() < 3) array.add(DEFAULT_SCALE);
        if (axis >= 0 && axis < 3) array.set(axis, new com.google.gson.JsonPrimitive(value));
        data.add("modelScaling", array);
    }

    private static int axisOf(String key) {
        for (int axis = 0; axis < SCALE_AXES.length; axis++) {
            if (SCALE_AXES[axis].equals(key)) return axis;
        }
        return -1;
    }

    private static float parseScale(String value) {
        float scale = Float.parseFloat(value.trim());
        if (!Float.isFinite(scale)) throw new IllegalArgumentException("Body size must be finite");
        return Math.max(0.05F, Math.min(10.0F, scale));
    }

    // -- preview and serialisation -----------------------------------------

    public FormConfig.FormData previewData() {
        return DmzFormMetadataRegistry.gson().fromJson(formData(), FormConfig.FormData.class);
    }

    public String formJson() {
        return DmzFormMetadataRegistry.gson().toJson(root);
    }

    public String metadataJson() {
        return DmzFormMetadataRegistry.gson().toJson(metadata);
    }

    public DmzFormKind kind() { return kind; }
    public String race() { return race; }
    public String group() { return group; }
    public String form() { return form; }
    public long revision() { return metadata.revision; }
    public void revision(long revision) { metadata.revision = revision; }
    public boolean created() { return created; }
    public void markCreated() { created = true; }
    public DmzFormMetadata metadata() { return metadata; }

    private JsonObject formData() {
        return root.getAsJsonObject("forms").getAsJsonObject(form);
    }

    // -- helpers ------------------------------------------------------------

    private static void add(List<Field> fields, String key, String label, String value, Kind kind) {
        fields.add(new Field(key, label, value, kind));
    }

    /**
     * Classifies a field by its serialised value, with the colour case keyed off DragonMineZ's
     * own naming: every colour in {@code FormConfig.FormData} and {@code OutlineShaderConfig}
     * ends in {@code Color} or {@code Color<n>}.
     */
    static Kind kindOf(String key, JsonElement value) {
        if (value != null && (value.isJsonArray() || value.isJsonObject())) return Kind.JSON;
        if (value != null && value.isJsonPrimitive()) {
            var primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) return Kind.BOOL;
            if (primitive.isNumber()) return Kind.NUMBER;
        }
        return key != null && key.toLowerCase(Locale.ROOT).matches(".*color\\d*") ? Kind.COLOR : Kind.TEXT;
    }

    private static String json(Object value) {
        return DmzFormMetadataRegistry.gson().toJson(value);
    }

    private static String format(float value) {
        return new BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString();
    }

    private static boolean isId(String value) {
        return value != null && value.matches("[a-z0-9][a-z0-9_.-]*") && !value.contains("..");
    }

    /** Four base-36 characters, enough entropy that a duplicate never collides in practice. */
    private static String token() {
        return Long.toString(ThreadLocalRandom.current().nextInt(1 << 20), 36);
    }

    private static boolean idAvailable(DmzFormKind kind, String race, String group) {
        if (DmzFormMetadataRegistry.get(kind, race, group) != null) return false;
        try {
            return kind == DmzFormKind.STACK
                    ? ConfigManager.getStackFormGroup(group) == null
                    : ConfigManager.getFormGroup(race, group) == null;
        } catch (Throwable ignored) {
            // A registry that cannot be read cannot be proven free, so prefer a longer suffix.
            return false;
        }
    }

    /** Locale names of one form id, without creating an entry on the source metadata. */
    private static Map<String, String> namesOf(DmzFormMetadata metadata, String formId) {
        if (metadata == null || metadata.forms == null) return Map.of();
        DmzFormMetadata.FormEntry entry = metadata.forms.get(formId);
        return entry == null || entry.names == null ? Map.of() : entry.names;
    }

    /** Player-facing name for a duplicate, derived from the source's own {@code en_us} entry. */
    private static String copyName(Map<String, String> names, String fallback) {
        String base = names == null ? null : names.get("en_us");
        if (base == null || base.isBlank()) base = title(fallback);
        return base + " Copy";
    }

    private static String compact(JsonElement value) {
        return value == null || value.isJsonNull() ? "" : value.isJsonPrimitive()
                ? value.getAsString() : value.toString();
    }

    private static String string(JsonObject root, String key) {
        return root.has(key) && !root.get(key).isJsonNull() ? root.get(key).getAsString() : "";
    }

    private static Map<String, String> parseStringMap(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        for (var entry : root.entrySet()) values.put(entry.getKey(), entry.getValue().getAsString());
        return values;
    }

    private static <T> List<T> parseList(String json, Class<T[]> arrayType, Class<T> itemType) {
        T[] values = DmzFormMetadataRegistry.gson().fromJson(json, arrayType);
        List<T> list = new ArrayList<>();
        if (values != null) {
            for (T value : values) if (itemType.isInstance(value)) list.add(value);
        }
        return list;
    }

    private static String label(String camel) {
        return camel.replace('.', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    private static String title(String value) {
        String[] parts = safe(value).split("[_ .-]+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return result.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record Field(String key, String label, String value, Kind kind) {
    }
}
