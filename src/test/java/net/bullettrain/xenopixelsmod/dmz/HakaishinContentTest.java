package net.bullettrain.xenopixelsmod.dmz;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 2026-09-29 owner: a Hakaishin form for every race, after Hakai. */
class HakaishinContentTest {
    static final List<String> RACES = List.of("saiyan", "human", "namekian", "frostdemon", "majin", "bioandroid");

    private static JsonObject read(String path) throws Exception {
        InputStream in = HakaishinContentTest.class.getResourceAsStream(path);
        assertNotNull(in, path);
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    @Test
    void everyRaceHasTheHakaishinForm() throws Exception {
        for (String race : RACES) {
            JsonObject group = read("/data/xenopixelsmod/dmz/races/" + race + "/forms/xenopixels_hakaishin.json");
            assertEquals("xenopixels_hakaishin", group.get("groupName").getAsString(), race);
            String type = group.get("formType").getAsString();
            assertEquals("xenopixels_destroyer", type, race);
            assertFalse(type.toLowerCase(Locale.ROOT).contains("god"),
                    "a formType with 'god' is remapped to DMZ's godforms skill");
            JsonObject form = group.getAsJsonObject("forms").getAsJsonObject("hakaishin");
            assertEquals("hakaishin", form.get("name").getAsString(), race);

            JsonObject prices = read("/data/xenopixelsmod/dmz/races/" + race + "/form_skill_prices.json");
            assertTrue(prices.has("xenopixels_destroyer"), race + " sells the form skill");
        }
    }

    @Test
    void theBootstrapInstallsItForEveryRace() {
        for (String race : RACES) {
            assertTrue(DmzContentBootstrap.bundledForms()
                    .contains("races/" + race + "/forms/xenopixels_hakaishin.json"), race);
            assertTrue(DmzContentBootstrap.pricedRaces().contains(race), race);
        }
        assertTrue(DmzContentBootstrap.xenoFormSkills().contains("xenopixels_destroyer"));
    }

    /**
     * 2026-09-29 owner: "learn it only from no one" - "it cant be enabled to all only by setting the
     * form group level using /dmzform". It stays a DMZ form skill (so /dmzform can set it) but no
     * master offers it and it is not for sale.
     */
    @Test
    void noOneTeachesIt() throws Exception {
        JsonObject patch = read("/data/xenopixelsmod/dmz/skills_patch.json");
        assertTrue(patch.getAsJsonArray("formSkillsAdd").toString().contains("xenopixels_destroyer"),
                "still a form skill /dmzform can set");
        for (var master : patch.getAsJsonObject("skillOfferings").entrySet()) {
            assertFalse(master.getValue().toString().contains("xenopixels_destroyer"), master.getKey());
        }
        for (String race : RACES) {
            JsonObject prices = read("/data/xenopixelsmod/dmz/races/" + race + "/form_skill_prices.json");
            assertFalse(prices.getAsJsonObject("xenopixels_destroyer").get("buyFromMaster").getAsBoolean(), race);
        }
        assertTrue(DmzContentBootstrap.notForSale().contains("xenopixels_destroyer"),
                "the bootstrap must not force it back on sale");
    }
}
