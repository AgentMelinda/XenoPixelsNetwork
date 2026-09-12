package net.bullettrain.xenopixelsmod.dmz.form;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.network.S2C.SyncServerConfigS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DmzFormEditorService {
    public static final int MAX_JSON_CHARS = 262_144;
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final Set<String> SESSION_BACKUPS = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> SESSION_BACKUP_STAMPS = new ConcurrentHashMap<>();

    private DmzFormEditorService() {
    }

    public static synchronized Result save(ServerPlayer editor, DmzFormKind kind, String race,
                                           String group, String formJson, String metadataJson,
                                           long expectedRevision, String sessionId) {
        if (editor == null || !editor.hasPermissions(2)) return Result.failure("Operator permission required");
        try {
            String safeRace = kind == DmzFormKind.STACK ? "" : id(race, "race");
            String safeGroup = id(group, "group");
            String protectionError = DmzFormProtection.mutationError(kind, safeRace, safeGroup);
            if (protectionError != null) return Result.failure(protectionError);
            if (formJson == null || metadataJson == null
                    || formJson.length() > MAX_JSON_CHARS || metadataJson.length() > MAX_JSON_CHARS) {
                return Result.failure("Form payload is too large");
            }

            JsonObject formRoot = JsonParser.parseString(formJson).getAsJsonObject();
            validateJson(formRoot, 0);
            FormConfig config = DmzFormMetadataRegistry.gson().fromJson(formRoot, FormConfig.class);
            if (config == null || !safeGroup.equals(config.getGroupName())) {
                return Result.failure("groupName must match the selected group id");
            }
            String formType = id(config.getFormType(), "formType");
            if (config.getForms().isEmpty() || config.getForms().size() > 256) {
                return Result.failure("A form group must contain 1-256 forms");
            }
            int maxLevel = validateForms(config);

            DmzFormMetadata metadata = DmzFormMetadataRegistry.gson()
                    .fromJson(metadataJson, DmzFormMetadata.class);
            if (metadata == null) return Result.failure("Missing form metadata");
            DmzFormMetadata existing = DmzFormMetadataRegistry.get(kind, safeRace, safeGroup);
            long currentRevision = existing == null ? 0L : existing.revision;
            if (expectedRevision != currentRevision) {
                return Result.failure("Form changed on the server; reopen it before editing");
            }
            metadata.schemaVersion = 1;
            metadata.revision = currentRevision + 1L;
            metadata.race = safeRace;
            metadata.group = safeGroup;
            metadata.formType = formType;
            normalizeMetadata(metadata, config, maxLevel);
            autobindNewForms(metadata, existing);

            Map<String, String> configs = new LinkedHashMap<>();
            String formPath = kind == DmzFormKind.STACK
                    ? "forms/" + safeGroup
                    : "races/" + safeRace + "/forms/" + safeGroup;
            configs.put(formPath, DmzFormMetadataRegistry.gson().toJson(config));
            configs.put("skills", patchSkills(metadata, existing, kind, maxLevel));
            if (kind == DmzFormKind.NORMAL) {
                configs.put("races/" + safeRace + "/character",
                        patchRaceCharacter(safeRace, metadata, maxLevel));
            }

            Path metadataPath = DmzFormMetadataRegistry.metadataPath(kind, safeRace, safeGroup);
            Map<Path, byte[]> oldBytes = new LinkedHashMap<>();
            List<Path> written = new ArrayList<>();
            try {
                for (Map.Entry<String, String> entry : configs.entrySet()) {
                    Path path = configPath(entry.getKey());
                    remember(path, oldBytes);
                    backupOnce(path, sessionId);
                    atomicWrite(path, entry.getValue());
                    written.add(path);
                }
                remember(metadataPath, oldBytes);
                backupOnce(metadataPath, sessionId);
                atomicWrite(metadataPath, DmzFormMetadataRegistry.gson().toJson(metadata));
                written.add(metadataPath);

                for (String path : configs.keySet()) ConfigManager.reloadSpecificConfig(path);
                DmzFormMetadataRegistry.put(kind, metadata);
                sync(editor.getServer(), configs.keySet());
                return Result.success(metadata.revision);
            } catch (Exception failure) {
                rollback(oldBytes, written);
                reloadQuietly(configs.keySet());
                XenoPixelsMod.LOGGER.error("DMZ form editor transaction rolled back", failure);
                return Result.failure("Save failed and was rolled back: " + failure.getMessage());
            }
        } catch (Exception exception) {
            return Result.failure(exception.getMessage() == null ? "Invalid form data" : exception.getMessage());
        }
    }

    static int validateForms(FormConfig config) {
        int maxLevel = 1;
        for (Map.Entry<String, FormConfig.FormData> entry : config.getForms().entrySet()) {
            String formId = id(entry.getKey(), "form id");
            FormConfig.FormData data = entry.getValue();
            if (data == null) throw new IllegalArgumentException("Form " + formId + " is null");
            if (data.getName() == null || data.getName().isBlank()) data.setName(formId);
            if (!formId.equals(data.getName())) {
                throw new IllegalArgumentException("Form name must match its form id: " + formId);
            }
            validateRegistryIds(formId, data);
            Integer level = data.getUnlockOnSkillLevel();
            maxLevel = Math.max(maxLevel, level == null ? 1 : Math.max(1, level));
        }
        return maxLevel;
    }

    /**
     * Rejects malformed item, tag and effect ids before they reach a DragonMineZ config file.
     * DragonMineZ resolves these through the vanilla registries at use time, so a bad value is
     * otherwise only discovered when a player transforms.
     */
    private static void validateRegistryIds(String formId, FormConfig.FormData data) {
        for (FormConfig.FormData.TriggerItemCost cost : data.getTriggerItemCosts()) {
            if (cost == null) continue;
            requireRegistryId(formId, "triggerItemCosts.itemId", cost.getItemId());
            requireRegistryId(formId, "triggerItemCosts.itemTag", cost.getItemTag());
        }
        for (FormConfig.FormData.DurationItemCost cost : data.getDurationItemCosts()) {
            if (cost == null) continue;
            requireRegistryId(formId, "durationItemCosts.itemId", cost.getItemId());
            requireRegistryId(formId, "durationItemCosts.itemTag", cost.getItemTag());
        }
        for (FormConfig.FormData.MobEffectConfig effect : data.getMobEffects()) {
            if (effect == null) continue;
            requireRegistryId(formId, "mobEffects.effectId", effect.getEffectId());
        }
    }

    private static void requireRegistryId(String formId, String field, String value) {
        if (value == null || value.isBlank()) return;
        if (ResourceLocation.tryParse(value) == null) {
            throw new IllegalArgumentException(
                    "Form " + formId + " has an invalid " + field + ": " + value);
        }
    }

    static void normalizeMetadata(DmzFormMetadata metadata, FormConfig config, int maxLevel) {
        if (metadata.formTypeIcon == null) metadata.formTypeIcon = "";
        metadata.nativeMasters = cleanIds(metadata.nativeMasters);
        if (metadata.skillCosts == null) metadata.skillCosts = new ArrayList<>();
        while (metadata.skillCosts.size() < maxLevel) metadata.skillCosts.add(0);
        if (metadata.skillCosts.size() > maxLevel) {
            metadata.skillCosts = new ArrayList<>(metadata.skillCosts.subList(0, maxLevel));
        }
        for (int i = 0; i < metadata.skillCosts.size(); i++) {
            metadata.skillCosts.set(i, Math.max(0, metadata.skillCosts.get(i) == null ? 0 : metadata.skillCosts.get(i)));
        }
        metadata.groupNames = normalizeNames(metadata.groupNames, metadata.group);
        metadata.forms.keySet().retainAll(config.getForms().keySet());
        for (String form : config.getForms().keySet()) {
            DmzFormMetadata.FormEntry entry = metadata.form(form);
            if (entry.icon == null) entry.icon = "";
            entry.names = normalizeNames(entry.names, form);
        }
        if (!metadata.masterLearningEnabled) {
            metadata.anyNativeMaster = false;
            metadata.nativeMasters.clear();
            metadata.customNpcTrainers.clear();
        }
    }

    /**
     * Keeps only well-formed {@code xx_yy} locales with a usable value, and guarantees the
     * {@code en_us} fallback the label resolution chain relies on.
     */
    static Map<String, String> normalizeNames(Map<String, String> source, String fallback) {
        Map<String, String> names = new LinkedHashMap<>();
        if (source != null) {
            for (Map.Entry<String, String> name : source.entrySet()) {
                String locale = name.getKey() == null
                        ? "" : name.getKey().toLowerCase(Locale.ROOT).replace('-', '_');
                String value = name.getValue() == null ? "" : name.getValue().trim();
                if (locale.matches("[a-z]{2}_[a-z]{2}") && !value.isBlank() && value.length() <= 128
                        && names.size() < 64) {
                    names.put(locale, value);
                }
            }
        }
        names.putIfAbsent("en_us", fallback);
        return names;
    }

    /**
     * Adds every form that did not exist before this save to each designated skill master's
     * offering list.
     *
     * <p>Runs after {@link #normalizeMetadata} and inside the same try/rollback block as the file
     * writes, so the offering list and the form file land together or not at all. Trainers that
     * are not marked as skill masters, and trainers whose group offers nothing, are left alone.
     */
    static void autobindNewForms(DmzFormMetadata metadata, DmzFormMetadata previous) {
        if (metadata.customNpcTrainers == null || metadata.customNpcTrainers.isEmpty()) return;
        List<String> added = DmzFormAutobind.addedForms(previous, metadata);
        if (added.isEmpty()) return;
        for (DmzFormMetadata.TrainerRef ref : metadata.customNpcTrainers) {
            if (ref == null || !ref.skillMaster) continue;
            UUID trainerId = parseUuid(ref.uuid);
            if (trainerId == null) continue;
            DmzFormAutobind.bindNewForms(metadata, trainerId, added);
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String patchSkills(DmzFormMetadata metadata, DmzFormMetadata oldMetadata,
                                      DmzFormKind kind, int maxLevel) throws IOException {
        return DmzFormMetadataRegistry.gson().toJson(
                patchSkillsJson(readObject(configPath("skills")), metadata, oldMetadata, kind, maxLevel));
    }

    /**
     * Adds the form type to DragonMineZ's skill registry in place.
     *
     * <p>{@code formSkills} / {@code stackSkills} list the types that exist, {@code skills} holds
     * one TP cost per level, and {@code skillOfferings} maps a master id to the types it teaches
     * ({@code default} being DragonMineZ's any-master bucket). Verified against
     * {@code com.dragonminez.common.config.SkillsConfig} in dragonminez-2.1.3.
     */
    static JsonObject patchSkillsJson(JsonObject root, DmzFormMetadata metadata,
                                      DmzFormMetadata oldMetadata, DmzFormKind kind, int maxLevel) {
        String listName = kind == DmzFormKind.STACK ? "stackSkills" : "formSkills";
        JsonArray formSkills = array(root, listName);
        addUnique(formSkills, metadata.formType);
        root.add(listName, formSkills);

        JsonObject skills = object(root, "skills");
        JsonObject skill = skills.has(metadata.formType) && skills.get(metadata.formType).isJsonObject()
                ? skills.getAsJsonObject(metadata.formType) : new JsonObject();
        JsonArray costs = new JsonArray();
        for (int i = 0; i < maxLevel; i++) costs.add(metadata.skillCosts.get(i));
        skill.add("costs", costs);
        if (!skill.has("allowedRaces")) skill.add("allowedRaces", new JsonArray());
        skills.add(metadata.formType, skill);
        root.add("skills", skills);

        JsonObject offerings = object(root, "skillOfferings");
        removeManagedOfferings(offerings, oldMetadata);
        if (metadata.masterLearningEnabled) {
            if (metadata.anyNativeMaster) addOffering(offerings, "default", metadata.formType);
            for (String master : metadata.nativeMasters) addOffering(offerings, master, metadata.formType);
        }
        root.add("skillOfferings", offerings);
        return root;
    }

    private static String patchRaceCharacter(String race, DmzFormMetadata metadata, int maxLevel)
            throws IOException {
        return DmzFormMetadataRegistry.gson().toJson(patchRaceCharacterJson(
                readObject(configPath("races/" + race + "/character")), metadata, maxLevel));
    }

    /**
     * Writes the per-race TP prices for the form type. The {@code buyFromMaster} / {@code prices}
     * shape is what {@code RaceCharacterConfig.FormSkillCost.Adapter} reads in dragonminez-2.1.3.
     */
    static JsonObject patchRaceCharacterJson(JsonObject root, DmzFormMetadata metadata, int maxLevel) {
        JsonObject costs = object(root, "formSkillsCosts");
        JsonObject entry = new JsonObject();
        entry.addProperty("buyFromMaster", metadata.buyFromMaster);
        JsonArray prices = new JsonArray();
        for (int i = 0; i < maxLevel; i++) prices.add(metadata.skillCosts.get(i));
        entry.add("prices", prices);
        costs.add(metadata.formType, entry);
        root.add("formSkillsCosts", costs);
        return root;
    }

    private static void removeManagedOfferings(JsonObject offerings, DmzFormMetadata oldMetadata) {
        if (oldMetadata == null || oldMetadata.formType == null) return;
        Set<String> keys = new LinkedHashSet<>(oldMetadata.nativeMasters == null
                ? List.of() : oldMetadata.nativeMasters);
        if (oldMetadata.anyNativeMaster) keys.add("default");
        for (String key : keys) {
            if (!offerings.has(key) || !offerings.get(key).isJsonArray()) continue;
            JsonArray values = offerings.getAsJsonArray(key);
            for (int i = values.size() - 1; i >= 0; i--) {
                if (oldMetadata.formType.equalsIgnoreCase(values.get(i).getAsString())) values.remove(i);
            }
        }
    }

    private static void addOffering(JsonObject offerings, String master, String formType) {
        JsonArray values = offerings.has(master) && offerings.get(master).isJsonArray()
                ? offerings.getAsJsonArray(master) : new JsonArray();
        addUnique(values, formType);
        offerings.add(master, values);
    }

    private static void sync(MinecraftServer server, Set<String> changedConfigs) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean reset = false;
            for (String config : changedConfigs) {
                String json = ConfigManager.getSpecificConfigJson(config);
                if (json != null) NetworkHandler.sendToPlayer(new SyncServerConfigS2C(config, json, reset), player);
            }
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
                String race = data.getCharacter().getRaceName();
                if (race != null && !race.isBlank()) data.updateTransformationSkillLimits(race);
                NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
            });
        }
    }

    static void validateJson(JsonElement element, int depth) {
        if (depth > 16) throw new IllegalArgumentException("Form JSON nesting is too deep");
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            double value = element.getAsDouble();
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Numeric values must be finite");
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                && element.getAsString().length() > 4096) {
            throw new IllegalArgumentException("A form string exceeds 4096 characters");
        } else if (element.isJsonArray()) {
            if (element.getAsJsonArray().size() > 512) throw new IllegalArgumentException("A form list exceeds 512 entries");
            for (JsonElement child : element.getAsJsonArray()) validateJson(child, depth + 1);
        } else if (element.isJsonObject()) {
            if (element.getAsJsonObject().size() > 512) throw new IllegalArgumentException("A form object is too large");
            for (var entry : element.getAsJsonObject().entrySet()) validateJson(entry.getValue(), depth + 1);
        }
    }

    /**
     * Normalises one lowercase resource-style id. The leading character must be alphanumeric and
     * {@code ..} is refused outright, so an id can never be a relative path segment even though
     * dots are otherwise legal inside DragonMineZ ids.
     */
    static String id(String value, String name) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9_.-]*") || normalized.contains("..")) {
            throw new IllegalArgumentException("Invalid " + name + " id");
        }
        return normalized;
    }

    private static List<String> cleanIds(List<String> values) {
        if (values == null) return new ArrayList<>();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (normalized.matches("[a-z0-9][a-z0-9_.-]*") && !normalized.contains("..")) {
                result.add(normalized);
            }
        }
        return new ArrayList<>(result);
    }

    private static Path configPath(String relative) {
        return configPath(FMLPaths.CONFIGDIR.get().resolve("dragonminez"), relative);
    }

    /** Resolves one DragonMineZ config file, refusing anything that escapes {@code dmzRoot}. */
    static Path configPath(Path dmzRoot, String relative) {
        Path root = dmzRoot.toAbsolutePath().normalize();
        Path path = root.resolve(relative + ".json").toAbsolutePath().normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Config path escaped DragonMineZ root");
        return path;
    }

    private static JsonObject readObject(Path path) throws IOException {
        if (!Files.exists(path)) return new JsonObject();
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static JsonArray array(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonArray() ? root.getAsJsonArray(key) : new JsonArray();
    }

    private static JsonObject object(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
    }

    private static void addUnique(JsonArray array, String value) {
        for (JsonElement element : array) if (value.equalsIgnoreCase(element.getAsString())) return;
        array.add(value);
    }

    static void remember(Path path, Map<Path, byte[]> oldBytes) throws IOException {
        oldBytes.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);
    }

    private static void backupOnce(Path path, String sessionId) throws IOException {
        backupOnce(path, FMLPaths.CONFIGDIR.get(), sessionId);
    }

    /**
     * Copies one file into the session backup tree the first time that session touches it, so an
     * editing session leaves exactly one pre-edit snapshot per affected file.
     */
    static void backupOnce(Path path, Path configDir, String sessionId) throws IOException {
        if (!Files.exists(path)) return;
        String session = sessionId == null || sessionId.isBlank() ? "default" : sessionId;
        Path source = path.toAbsolutePath().normalize();
        String key = session + "\u0000" + source;
        if (!SESSION_BACKUPS.add(key)) return;
        Path configRoot = configDir.toAbsolutePath().normalize();
        if (!source.startsWith(configRoot)) throw new IOException("Backup source escaped config root");
        Path backupRoot = configRoot.resolve("xenopixelsmod").resolve("dmz-form-editor")
                .resolve("backups").toAbsolutePath().normalize();
        // The session id is part of the folder name: two sessions starting inside the same
        // millisecond would otherwise share a folder and the second copy would fail.
        String stamp = SESSION_BACKUP_STAMPS.computeIfAbsent(session,
                id -> BACKUP_STAMP.format(LocalDateTime.now()) + "-"
                        + Integer.toHexString(id.hashCode()));
        Path backup = backupRoot.resolve(stamp).resolve(configRoot.relativize(source))
                .toAbsolutePath().normalize();
        if (!backup.startsWith(backupRoot)) throw new IOException("Backup path escaped backup root");
        Files.createDirectories(backup.getParent());
        Files.copy(path, backup, StandardCopyOption.COPY_ATTRIBUTES);
    }

    static void atomicWrite(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void rollback(Map<Path, byte[]> oldBytes, List<Path> written) {
        for (int i = written.size() - 1; i >= 0; i--) {
            Path path = written.get(i);
            try {
                byte[] bytes = oldBytes.get(path);
                if (bytes == null) Files.deleteIfExists(path);
                else {
                    Files.createDirectories(path.getParent());
                    Files.write(path, bytes);
                }
            } catch (IOException exception) {
                XenoPixelsMod.LOGGER.error("Failed rolling back {}", path, exception);
            }
        }
    }

    private static void reloadQuietly(Set<String> configs) {
        for (String config : configs) {
            try {
                ConfigManager.reloadSpecificConfig(config);
            } catch (Exception ignored) {
            }
        }
    }

    public record Result(boolean success, long revision, String message) {
        public static Result success(long revision) {
            return new Result(true, revision, "Saved");
        }

        public static Result failure(String message) {
            return new Result(false, -1L, message == null ? "Save failed" : message);
        }
    }
}
