package net.bullettrain.xenopixelsmod.dmz;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

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
 * Installs XenoPixels DMZ custom forms/prices and restricts god-form acquisition
 * to Beerus / Whis masters.
 *
 * @see <a href="https://github.com/DragonMineZ/dragonminez/wiki/Custom-Forms">Custom Forms</a>
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class DmzContentBootstrap {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final String[] BUNDLED_FORMS = {
            "races/saiyan/forms/supersaiyan_legend.json",
            "races/saiyan/forms/xenopixels_godforms.json",
            "races/saiyan/forms/xenopixels_legendary.json"
    };

    private DmzContentBootstrap() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        installBundledContent();
    }

    public static void installBundledContent() {
        Path root = FMLPaths.CONFIGDIR.get().resolve("dragonminez");
        for (String relative : BUNDLED_FORMS) {
            Path target = root.resolve(relative);
            try {
                Files.createDirectories(target.getParent());
                // Always refresh bundled XenoPixels forms so balance updates ship
                if (relative.contains("xenopixels_") || relative.contains("supersaiyan_legend")
                        || !Files.exists(target)) {
                    copyResource("/data/xenopixelsmod/dmz/" + relative, target);
                }
            } catch (IOException e) {
                XenoPixelsMod.LOGGER.error("Failed installing DMZ content {}", relative, e);
            }
        }
        patchSaiyanFormPrices(root.resolve("races/saiyan/character.json"));
        patchSkillsConfig(root.resolve("skills.json"));
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

    private static void patchSaiyanFormPrices(Path characterJson) {
        if (!Files.exists(characterJson)) {
            XenoPixelsMod.LOGGER.warn("Saiyan character.json missing; skip form price patch: {}", characterJson);
            return;
        }

        JsonObject priceBundle = readResourceJson("/data/xenopixelsmod/dmz/races/saiyan/form_skill_prices.json");
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
            character.add("formSkillsCosts", costs);

            try (Writer writer = Files.newBufferedWriter(characterJson, StandardCharsets.UTF_8)) {
                GSON.toJson(character, writer);
            }
            XenoPixelsMod.LOGGER.info("Patched Saiyan form skill TP prices in {}", characterJson);
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.error("Failed patching Saiyan form prices", e);
        }
    }

    /**
     * Ensures {@code xenopixels_godforms} is a form skill and only Beerus/Whis offer it.
     */
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

            // formSkills list
            JsonArray formSkills = skills.has("formSkills") && skills.get("formSkills").isJsonArray()
                    ? skills.getAsJsonArray("formSkills")
                    : new JsonArray();
            if (patch.has("formSkillsAdd") && patch.get("formSkillsAdd").isJsonArray()) {
                for (JsonElement el : patch.getAsJsonArray("formSkillsAdd")) {
                    String id = el.getAsString();
                    if (!jsonArrayContains(formSkills, id)) {
                        formSkills.add(id);
                    }
                }
            }
            skills.add("formSkills", formSkills);

            // skillOfferings — beerus / whis only for our god forms
            JsonObject offerings = skills.has("skillOfferings") && skills.get("skillOfferings").isJsonObject()
                    ? skills.getAsJsonObject("skillOfferings")
                    : new JsonObject();

            if (patch.has("skillOfferings") && patch.get("skillOfferings").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : patch.getAsJsonObject("skillOfferings").entrySet()) {
                    String master = entry.getKey();
                    JsonArray want = entry.getValue().getAsJsonArray();
                    JsonArray existing = offerings.has(master) && offerings.get(master).isJsonArray()
                            ? offerings.getAsJsonArray(master)
                            : new JsonArray();
                    for (JsonElement el : want) {
                        String skill = el.getAsString();
                        if (!jsonArrayContains(existing, skill)) {
                            existing.add(skill);
                        }
                    }
                    offerings.add(master, existing);
                }
            }

            // Restrict exclusive form skills (godforms / legendaryforms) to intended masters
            JsonObject exclusive = patch.has("exclusiveFormSkills") && patch.get("exclusiveFormSkills").isJsonObject()
                    ? patch.getAsJsonObject("exclusiveFormSkills")
                    : new JsonObject();

            for (Map.Entry<String, JsonElement> entry : offerings.entrySet()) {
                String master = entry.getKey().toLowerCase();
                if (!entry.getValue().isJsonArray()) continue;
                JsonArray arr = entry.getValue().getAsJsonArray();
                JsonArray cleaned = new JsonArray();
                for (JsonElement el : arr) {
                    String skill = el.getAsString();
                    if (exclusive.has(skill) && exclusive.get(skill).isJsonArray()) {
                        boolean allowed = false;
                        for (JsonElement m : exclusive.getAsJsonArray(skill)) {
                            if (master.equalsIgnoreCase(m.getAsString())) {
                                allowed = true;
                                break;
                            }
                        }
                        if (!allowed) continue;
                    }
                    // Drop obsolete custom skill ids
                    if ("xenopixels_godforms".equals(skill) || "xenopixels_legendary".equals(skill)) {
                        continue;
                    }
                    if (!jsonArrayContains(cleaned, skill)) {
                        cleaned.add(el);
                    }
                }
                offerings.add(entry.getKey(), cleaned);
            }

            // Ensure exclusive masters still have their skills
            if (exclusive != null) {
                for (Map.Entry<String, JsonElement> ex : exclusive.entrySet()) {
                    String skill = ex.getKey();
                    if (!ex.getValue().isJsonArray()) continue;
                    for (JsonElement mEl : ex.getValue().getAsJsonArray()) {
                        String master = mEl.getAsString();
                        JsonArray existing = offerings.has(master) && offerings.get(master).isJsonArray()
                                ? offerings.getAsJsonArray(master)
                                : new JsonArray();
                        if (!jsonArrayContains(existing, skill)) {
                            existing.add(skill);
                        }
                        offerings.add(master, existing);
                    }
                }
            }

            skills.add("skillOfferings", offerings);

            try (Writer writer = Files.newBufferedWriter(skillsJson, StandardCharsets.UTF_8)) {
                GSON.toJson(skills, writer);
            }
            XenoPixelsMod.LOGGER.info("Patched DMZ skills.json (godforms→Beerus/Whis, legendaryforms→Trunks)");
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.error("Failed patching skills.json", e);
        }
    }

    private static boolean jsonArrayContains(JsonArray arr, String value) {
        for (JsonElement el : arr) {
            if (value.equals(el.getAsString())) return true;
        }
        return false;
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
