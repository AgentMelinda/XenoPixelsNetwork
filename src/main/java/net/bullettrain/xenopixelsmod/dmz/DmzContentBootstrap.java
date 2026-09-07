package net.bullettrain.xenopixelsmod.dmz;

import net.neoforged.fml.common.EventBusSubscriber;

import com.dragonminez.common.config.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Installs XenoPixels DMZ form groups the same way DMZ itself structures them.
 *
 * <h2>DMZ form group rules (from FormConfig / RadialForms / TransformationsHelper)</h2>
 * <ul>
 *   <li>{@code groupName} — unique id for the group file / lang / form requisites
 *       ({@code groupName.formId}).</li>
 *   <li>{@code formType} — skill id. Vanilla skills: {@code superforms}, {@code godforms},
 *       {@code legendaryforms}, {@code androidforms}. Multiple groups may share one type
 *       (e.g. ssgrades + supersaiyan + oozaru all use {@code superforms}).</li>
 *   <li>{@code /dmzform set} and X-menu unlocks check the skill named by
 *       {@code TransformationsHelper.getSkillNameForType(formType)}, which remaps any
 *       formType containing super/legendary/god/android to those vanilla skills.</li>
 *   <li>Radial: types containing super|legendary|android → Super Forms; else → Extra Forms.</li>
 *   <li>{@code formSkillsCosts} keys are formType skill ids, not group names.</li>
 * </ul>
 *
 * <h2>Our groups (all learnable only from Beerus / Whis)</h2>
 * <p>DMZ {@code MastersSkillsScreen} only lists form skills that appear in
 * {@code skills.json → skillOfferings.&lt;masterName&gt;} <b>and</b>
 * {@code formSkills}, with race {@code formSkillsCosts} present.
 * Vanilla {@code skillOfferings} has no beerus/whis entries, so we create them.
 * </p>
 * <ul>
 *   <li>{@code xenopixels_fan_ss} → formType {@code xenopixels_fan_ss} (SSJ5–10)</li>
 *   <li>{@code xenopixels_gods_forms} → formType {@code xenopixels_divinity}
 *       (no {@code god} substring — DMZ would remap to vanilla godforms)</li>
 *   <li>{@code xenopixels_saga_forms} → formType {@code xenopixels_saga_forms}</li>
 *   <li>{@code xenopixels_dark_frieza} → formType {@code xenopixels_dark_frieza}</li>
 * </ul>
 * Masters: <b>Beerus</b> and <b>Whis</b> only for every Xeno form skill.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class DmzContentBootstrap {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Masters allowed to sell every XenoPixels form skill. */
    private static final String[] XENO_FORM_MASTERS = {"beerus", "whis"};

    /**
     * Custom formTypes (skill ids). Must not contain super/legendary/god/android
     * or {@code TransformationsHelper.getSkillNameForType} remaps them to vanilla skills.
     */
    private static final String[] XENO_FORM_SKILLS = {
            "xenopixels_fan_ss",
            "xenopixels_divinity",
            "xenopixels_saga_forms",
            "xenopixels_dark_frieza"
    };

    /** Broken / obsolete formType skill ids from earlier patches (never remove vanilla formTypes). */
    private static final String[] LEGACY_FORM_SKILLS = {
            "ssj_legend",
            "xeno_divine",
            "xeno_ikari",
            "supersaiyan_legend",
            "xenopixels_godforms",
            "xenopixels_legendary",
            "xenopixels_gods_forms"
    };

    private static final String[] BUNDLED_FORMS = {
            // supersaiyan_legend (superforms 9–14) removed — extended vanilla superforms, not Beerus-gated
            "races/saiyan/forms/xenopixels_fan_ss.json",
            "races/saiyan/forms/xenopixels_gods_forms.json",
            "races/saiyan/forms/xenopixels_saga_forms.json",
            "races/frostdemon/forms/xenopixels_dark_frieza.json"
    };

    /** Old form JSON filenames to delete from config so DMZ does not keep loading them. */
    private static final String[] OBSOLETE_FORM_FILES = {
            "races/saiyan/forms/xenopixels_godforms.json",
            "races/saiyan/forms/xenopixels_legendary.json",
            "races/saiyan/forms/supersaiyan_legend.json"
    };

    private static final int[] VANILLA_SAIYAN_SUPERFORM_PRICES = {
            13000, 21000, 31000, 42000, 52000, 65000, 78000, 104000
    };
    private static final int[] LEGACY_XENO_SUPERFORM_PRICES = {
            120000, 145000, 175000, 210000, 250000, 300000
    };

    private DmzContentBootstrap() {}

    public static void installBundledContent() {
        installBundledContent(false);
    }

    public static void installBundledContent(boolean reloadDmz) {
        Path root = FMLPaths.CONFIGDIR.get().resolve("dragonminez");
        for (String relative : BUNDLED_FORMS) {
            Path target = root.resolve(relative);
            try {
                Files.createDirectories(target.getParent());
                if (relative.contains("xenopixels_") || relative.contains("supersaiyan_legend")
                        || !Files.exists(target)) {
                    copyResource("/data/xenopixelsmod/dmz/" + relative, target);
                }
            } catch (IOException e) {
                XenoPixelsMod.LOGGER.error("Failed installing DMZ content {}", relative, e);
            }
        }
        // Remove renamed groups so DMZ does not load both old + new files
        for (String obsolete : OBSOLETE_FORM_FILES) {
            try {
                Path old = root.resolve(obsolete);
                if (Files.deleteIfExists(old)) {
                    XenoPixelsMod.LOGGER.info("Removed obsolete DMZ form file: {}", old);
                }
            } catch (IOException e) {
                XenoPixelsMod.LOGGER.warn("Could not remove obsolete form file {}", obsolete, e);
            }
        }
        patchRaceFormPrices(root.resolve("races/saiyan/character.json"),
                "/data/xenopixelsmod/dmz/races/saiyan/form_skill_prices.json");
        patchRaceFormPrices(root.resolve("races/frostdemon/character.json"),
                "/data/xenopixelsmod/dmz/races/frostdemon/form_skill_prices.json");
        patchSkillsConfig(root.resolve("skills.json"));

        if (reloadDmz) {
            reloadDmzConfigs();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onServerStarting(ServerStartingEvent event) {
        installBundledContent(true);
    }

    private static void reloadDmzConfigs() {
        try {
            ConfigManager.reload();
            XenoPixelsMod.LOGGER.info(
                    "Reloaded DMZ configs after XenoPixels form install (fan skill={}, others use vanilla formTypes)",
                    String.join(", ", XENO_FORM_SKILLS));
        } catch (Throwable t) {
            XenoPixelsMod.LOGGER.error(
                    "Failed to reload DMZ configs — run /dmzreload config so form skills apply", t);
        }
    }

    private static void copyResource(String resource, Path target) throws IOException {
        try (InputStream in = DmzContentBootstrap.class.getResourceAsStream(resource)) {
            if (in == null) {
                XenoPixelsMod.LOGGER.warn("Missing bundled DMZ content: {}", resource);
                return;
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            XenoPixelsMod.LOGGER.info("Installed DMZ content: {}", target);
        }
    }

    /**
     * Merges our form skill TP prices into a race {@code character.json}.
     * Only keys present in the bundled price file are written (vanilla keys left alone).
     */
    private static void patchRaceFormPrices(Path characterJson, String priceResource) {
        if (!Files.exists(characterJson)) {
            XenoPixelsMod.LOGGER.warn("Race character.json missing; skip form price patch: {}", characterJson);
            return;
        }

        JsonObject priceBundle = readResourceJson(priceResource);
        if (priceBundle == null) return;

        try (Reader reader = Files.newBufferedReader(characterJson, StandardCharsets.UTF_8)) {
            JsonObject character = GSON.fromJson(reader, JsonObject.class);
            if (character == null) return;

            JsonObject costs = character.has("formSkillsCosts") && character.get("formSkillsCosts").isJsonObject()
                    ? character.getAsJsonObject("formSkillsCosts")
                    : new JsonObject();

            for (Map.Entry<String, JsonElement> entry : priceBundle.entrySet()) {
                costs.add(entry.getKey(), entry.getValue());
            }
            for (String legacy : LEGACY_FORM_SKILLS) {
                costs.remove(legacy);
            }
            // Saiyan-only repair: older bad patch left Ikari on legendaryforms
            if (characterJson.toString().replace('\\', '/').contains("/saiyan/")) {
                repairLegendaryFormsPrices(costs);
                if (repairLegacySuperforms(costs)) {
                    XenoPixelsMod.LOGGER.info(
                            "Restored vanilla Saiyan superforms registration and 8-level price ladder");
                }
            }
            // Never leave buyFromMaster false for our skills
            for (String skill : XENO_FORM_SKILLS) {
                if (costs.has(skill) && costs.get(skill).isJsonObject()) {
                    costs.getAsJsonObject(skill).addProperty("buyFromMaster", true);
                }
            }
            character.add("formSkillsCosts", costs);

            try (Writer writer = Files.newBufferedWriter(characterJson, StandardCharsets.UTF_8)) {
                GSON.toJson(character, writer);
            }
            XenoPixelsMod.LOGGER.info("Patched form skill TP prices in {}", characterJson);
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.error("Failed patching form prices for {}", characterJson, e);
        }
    }

    private static void patchSkillsConfig(Path skillsJson) {
        if (!Files.exists(skillsJson)) {
            XenoPixelsMod.LOGGER.warn("skills.json missing; skip master offerings patch: {}", skillsJson);
            return;
        }

        JsonObject patch = readResourceJson("/data/xenopixelsmod/dmz/skills_patch.json");
        if (patch == null) return;

        try (Reader reader = Files.newBufferedReader(skillsJson, StandardCharsets.UTF_8)) {
            JsonObject skills = GSON.fromJson(reader, JsonObject.class);
            if (skills == null) return;

            JsonArray formSkills = skills.has("formSkills") && skills.get("formSkills").isJsonArray()
                    ? skills.getAsJsonArray("formSkills")
                    : new JsonArray();

            if (patch.has("formSkillsRemove") && patch.get("formSkillsRemove").isJsonArray()) {
                for (JsonElement el : patch.getAsJsonArray("formSkillsRemove")) {
                    removeFromJsonArray(formSkills, el.getAsString());
                }
            }
            for (String legacy : LEGACY_FORM_SKILLS) {
                removeFromJsonArray(formSkills, legacy);
            }

            // Ensure vanilla form skill types exist
            for (String vanilla : new String[]{"superforms", "godforms", "legendaryforms", "androidforms"}) {
                if (!jsonArrayContains(formSkills, vanilla)) {
                    formSkills.add(vanilla);
                }
            }

            if (patch.has("formSkillsAdd") && patch.get("formSkillsAdd").isJsonArray()) {
                for (JsonElement el : patch.getAsJsonArray("formSkillsAdd")) {
                    String id = el.getAsString();
                    if (!jsonArrayContains(formSkills, id)) {
                        formSkills.add(id);
                    }
                }
            }
            for (String id : XENO_FORM_SKILLS) {
                if (!jsonArrayContains(formSkills, id)) {
                    formSkills.add(id);
                }
            }
            skills.add("formSkills", formSkills);

            // DMZ Skills.calculateMaxLevel() reads skills.json → skills.<id>.costs.size().
            // Without this, /dmzform set clamps level to maxLevel=0 and forms never unlock.
            ensureFormSkillCostEntries(skills, patch);

            JsonObject offerings = skills.has("skillOfferings") && skills.get("skillOfferings").isJsonObject()
                    ? skills.getAsJsonObject("skillOfferings")
                    : new JsonObject();

            // 1) Strip ALL Xeno form skills + legacy ids from every master first
            for (Map.Entry<String, JsonElement> entry : offerings.entrySet()) {
                if (!entry.getValue().isJsonArray()) continue;
                JsonArray arr = entry.getValue().getAsJsonArray();
                JsonArray cleaned = new JsonArray();
                for (JsonElement el : arr) {
                    String skill = el.getAsString();
                    if (isXenoFormSkill(skill) || isLegacyFormSkill(skill)) {
                        continue;
                    }
                    if (!jsonArrayContains(cleaned, skill)) {
                        cleaned.add(el);
                    }
                }
                offerings.add(entry.getKey(), cleaned);
            }

            // 2) Re-add only on Beerus / Whis (from patch + hard-coded masters)
            JsonObject exclusive = patch.has("exclusiveFormSkills") && patch.get("exclusiveFormSkills").isJsonObject()
                    ? patch.getAsJsonObject("exclusiveFormSkills")
                    : new JsonObject();

            // Ensure every xeno skill has exclusive masters = beerus/whis
            for (String skill : XENO_FORM_SKILLS) {
                if (!exclusive.has(skill) || !exclusive.get(skill).isJsonArray()) {
                    JsonArray masters = new JsonArray();
                    for (String m : XENO_FORM_MASTERS) {
                        masters.add(m);
                    }
                    exclusive.add(skill, masters);
                }
            }

            if (patch.has("skillOfferings") && patch.get("skillOfferings").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : patch.getAsJsonObject("skillOfferings").entrySet()) {
                    String master = entry.getKey().toLowerCase();
                    if (!isXenoFormMaster(master)) {
                        // Never add our form skills to non-Beerus/Whis from a stale patch
                        continue;
                    }
                    JsonArray want = entry.getValue().getAsJsonArray();
                    JsonArray existing = offerings.has(master) && offerings.get(master).isJsonArray()
                            ? offerings.getAsJsonArray(master)
                            : new JsonArray();
                    for (JsonElement el : want) {
                        String skill = el.getAsString();
                        if (isXenoFormSkill(skill) && !jsonArrayContains(existing, skill)) {
                            existing.add(skill);
                        }
                    }
                    offerings.add(master, existing);
                }
            }

            // 3) Force every exclusive master list entry onto beerus/whis only
            for (Map.Entry<String, JsonElement> ex : exclusive.entrySet()) {
                String skill = ex.getKey();
                if (!isXenoFormSkill(skill) || !ex.getValue().isJsonArray()) continue;
                for (JsonElement mEl : ex.getValue().getAsJsonArray()) {
                    String master = mEl.getAsString().toLowerCase();
                    if (!isXenoFormMaster(master)) continue;
                    JsonArray existing = offerings.has(master) && offerings.get(master).isJsonArray()
                            ? offerings.getAsJsonArray(master)
                            : new JsonArray();
                    if (!jsonArrayContains(existing, skill)) {
                        existing.add(skill);
                    }
                    offerings.add(master, existing);
                }
            }

            // 4) Final pass: any master that is not beerus/whis must not retain xeno form skills
            for (Map.Entry<String, JsonElement> entry : offerings.entrySet()) {
                String master = entry.getKey().toLowerCase();
                if (!entry.getValue().isJsonArray()) continue;
                if (isXenoFormMaster(master)) {
                    // Ensure all xeno skills present on beerus/whis
                    JsonArray arr = entry.getValue().getAsJsonArray();
                    for (String skill : XENO_FORM_SKILLS) {
                        if (!jsonArrayContains(arr, skill)) {
                            arr.add(skill);
                        }
                    }
                    offerings.add(entry.getKey(), arr);
                    continue;
                }
                JsonArray arr = entry.getValue().getAsJsonArray();
                JsonArray cleaned = new JsonArray();
                for (JsonElement el : arr) {
                    String skill = el.getAsString();
                    if (isXenoFormSkill(skill) || isLegacyFormSkill(skill)) continue;
                    if (!jsonArrayContains(cleaned, skill)) cleaned.add(el);
                }
                offerings.add(entry.getKey(), cleaned);
            }

            skills.add("skillOfferings", offerings);

            try (Writer writer = Files.newBufferedWriter(skillsJson, StandardCharsets.UTF_8)) {
                GSON.toJson(skills, writer);
            }
            XenoPixelsMod.LOGGER.info(
                    "Patched DMZ skills.json: formSkills={} learnable only from {}",
                    java.util.Arrays.toString(XENO_FORM_SKILLS),
                    java.util.Arrays.toString(XENO_FORM_MASTERS));
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.error("Failed patching skills.json", e);
        }
    }

    /**
     * If an earlier XenoPixels patch left legendaryforms as {@code [-1,-1,-1,95000]},
     * restore the stock 3-level DMZ shape (Ikari now lives on xenopixels_saga_forms).
     */
    private static void repairLegendaryFormsPrices(JsonObject costs) {
        if (!costs.has("legendaryforms") || !costs.get("legendaryforms").isJsonObject()) return;
        JsonObject leg = costs.getAsJsonObject("legendaryforms");
        if (!leg.has("prices") || !leg.get("prices").isJsonArray()) return;
        JsonArray prices = leg.getAsJsonArray("prices");
        if (prices.size() == 4
                && prices.get(0).getAsInt() == -1
                && prices.get(1).getAsInt() == -1
                && prices.get(2).getAsInt() == -1
                && prices.get(3).getAsInt() == 95000) {
            JsonArray fixed = new JsonArray();
            fixed.add(-1);
            fixed.add(-1);
            fixed.add(-1);
            leg.add("prices", fixed);
            leg.addProperty("buyFromMaster", false);
            XenoPixelsMod.LOGGER.info("Restored vanilla legendaryforms price ladder (removed stale Ikari slot)");
        }
    }

    /** Repairs only the exact Saiyan superforms shapes written by older XenoPixels releases. */
    static boolean repairLegacySuperforms(JsonObject costs) {
        if (!costs.has("superforms") || !costs.get("superforms").isJsonObject()) return false;
        JsonObject superforms = costs.getAsJsonObject("superforms");
        if (!superforms.has("buyFromMaster")
                || !superforms.get("buyFromMaster").isJsonPrimitive()
                || !superforms.getAsJsonPrimitive("buyFromMaster").isBoolean()
                || !superforms.get("buyFromMaster").getAsBoolean()
                || !superforms.has("prices")
                || !superforms.get("prices").isJsonArray()) {
            return false;
        }

        JsonArray prices = superforms.getAsJsonArray("prices");
        boolean trimmedLegacy = prices.size() == VANILLA_SAIYAN_SUPERFORM_PRICES.length;
        boolean extendedLegacy = prices.size() == VANILLA_SAIYAN_SUPERFORM_PRICES.length
                + LEGACY_XENO_SUPERFORM_PRICES.length;
        if ((!trimmedLegacy && !extendedLegacy)
                || !matchesPrices(prices, 0, VANILLA_SAIYAN_SUPERFORM_PRICES)
                || (extendedLegacy && !matchesPrices(prices,
                        VANILLA_SAIYAN_SUPERFORM_PRICES.length,
                        LEGACY_XENO_SUPERFORM_PRICES))) {
            return false;
        }

        if (extendedLegacy) {
            JsonArray trimmed = new JsonArray();
            for (int price : VANILLA_SAIYAN_SUPERFORM_PRICES) {
                trimmed.add(price);
            }
            superforms.add("prices", trimmed);
        }
        superforms.addProperty("buyFromMaster", false);
        return true;
    }

    private static boolean matchesPrices(JsonArray prices, int offset, int[] expected) {
        for (int i = 0; i < expected.length; i++) {
            JsonElement value = prices.get(offset + i);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                    || value.getAsInt() != expected[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ensures each custom form skill has a {@code skills.&lt;id&gt;.costs} array so
     * {@code Skills.setSkillLevel} can create the skill with a non-zero maxLevel.
     * Real TP prices still live in race character {@code formSkillsCosts}.
     */
    private static void ensureFormSkillCostEntries(JsonObject skillsRoot, JsonObject patch) {
        JsonObject skillsMap = skillsRoot.has("skills") && skillsRoot.get("skills").isJsonObject()
                ? skillsRoot.getAsJsonObject("skills")
                : new JsonObject();

        if (patch.has("skillCosts") && patch.get("skillCosts").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : patch.getAsJsonObject("skillCosts").entrySet()) {
                skillsMap.add(e.getKey(), e.getValue());
            }
        }

        // Fallback lengths if patch section missing
        ensureSkillCostsLength(skillsMap, "xenopixels_divinity", 8);
        ensureSkillCostsLength(skillsMap, "xenopixels_fan_ss", 6);
        ensureSkillCostsLength(skillsMap, "xenopixels_saga_forms", 1);
        ensureSkillCostsLength(skillsMap, "xenopixels_dark_frieza", 1);

        skillsRoot.add("skills", skillsMap);
    }

    private static void ensureSkillCostsLength(JsonObject skillsMap, String skillId, int levels) {
        if (skillsMap.has(skillId) && skillsMap.get(skillId).isJsonObject()) {
            JsonObject entry = skillsMap.getAsJsonObject(skillId);
            if (entry.has("costs") && entry.get("costs").isJsonArray()
                    && entry.getAsJsonArray("costs").size() >= levels) {
                return;
            }
        }
        JsonObject entry = new JsonObject();
        JsonArray costs = new JsonArray();
        for (int i = 0; i < levels; i++) {
            costs.add(1);
        }
        entry.add("costs", costs);
        entry.add("allowedRaces", new JsonArray());
        skillsMap.add(skillId, entry);
    }

    private static boolean isXenoFormSkill(String skill) {
        if (skill == null) return false;
        for (String x : XENO_FORM_SKILLS) {
            if (x.equals(skill)) return true;
        }
        return false;
    }

    private static boolean isLegacyFormSkill(String skill) {
        if (skill == null) return false;
        for (String leg : LEGACY_FORM_SKILLS) {
            if (leg.equals(skill)) return true;
        }
        return false;
    }

    private static boolean isXenoFormMaster(String master) {
        if (master == null) return false;
        for (String m : XENO_FORM_MASTERS) {
            if (m.equalsIgnoreCase(master)) return true;
        }
        return false;
    }

    private static boolean jsonArrayContains(JsonArray arr, String value) {
        for (JsonElement el : arr) {
            if (value.equals(el.getAsString())) return true;
        }
        return false;
    }

    private static void removeFromJsonArray(JsonArray arr, String value) {
        for (int i = arr.size() - 1; i >= 0; i--) {
            if (value.equals(arr.get(i).getAsString())) {
                arr.remove(i);
            }
        }
    }

    private static JsonObject readResourceJson(String resource) {
        try (InputStream in = DmzContentBootstrap.class.getResourceAsStream(resource)) {
            if (in == null) {
                XenoPixelsMod.LOGGER.warn("Missing resource {}", resource);
                return null;
            }
            return GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.error("Failed reading {}", resource, e);
            return null;
        }
    }
}
