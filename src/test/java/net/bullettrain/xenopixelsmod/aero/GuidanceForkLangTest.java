package net.bullettrain.xenopixelsmod.aero;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidanceForkLangTest {

    private static Path resolveLang() {
        Path relative = Path.of("src/main/resources/assets/xenopixelsmod/lang/en_us.json");
        Path cursor = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && cursor != null; i++) {
            Path candidate = cursor.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("en_us.json not found from " + Path.of("").toAbsolutePath());
    }

    private static final Map<String, String> FORK_KEYS = Map.ofEntries(
            Map.entry("block.xenopixelsmod.ship_vls_guidance_fork", "Ballistic Guidance Computer (fork)"),
            Map.entry("block.xenopixelsmod.pilot_seat_fork", "Seated Control Chair (fork)"),
            Map.entry("block.xenopixelsmod.ship_thruster_fork", "Ship Thruster (fork)"),
            Map.entry("block.xenopixelsmod.wing_panel_fork", "Wing Panel (fork)"),
            Map.entry("block.xenopixelsmod.wing_flap_horizontal_fork", "Wing Flap (Horizontal) (fork)"),
            Map.entry("block.xenopixelsmod.wing_flap_vertical_fork", "Wing Flap (Vertical) (fork)"),
            Map.entry("block.xenopixelsmod.copycat_wing_panel_fork", "Copycat Wing Panel (fork)"),
            Map.entry("item.xenopixelsmod.target_tool_fork", "Ship Target Tool (fork)"),
            Map.entry("item.xenopixelsmod.panel_configurator_fork", "Panel Configurator (fork)")
    );

    @Test
    void forkDisplayNamesKeepStockNamePlusFork() throws Exception {
        Path lang = resolveLang();
        Map<String, String> entries = new Gson().fromJson(
                Files.readString(lang), new TypeToken<Map<String, String>>() {}.getType());
        for (var entry : FORK_KEYS.entrySet()) {
            assertEquals(entry.getValue(), entries.get(entry.getKey()), entry.getKey());
            assertTrue(entry.getValue().endsWith(" (fork)"));
        }
    }
}
