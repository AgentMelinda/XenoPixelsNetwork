package net.bullettrain.xenopixelsmod.combat.v3.ki;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.bullettrain.xenopixelsmod.fx.ki.KiLook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XenoKiProfileTest {
    @TempDir Path temp;

    @Test void mergeAndJsonRoundTrip() {
        XenoKiProfile base = XenoKiProfile.empty("xenopixelsmod:bt3_test", "kamehameha")
                .merge(new XenoKiProfile("xenopixelsmod:bt3_test", "kamehameha",
                        5240831, 5240831, 16777215, 1.15f, 1.2f, 1.0f, 0,
                        0f, 0.2f, 0.5f, 1, true, "trail", "impact", "charge"));
        JsonObject json = base.toJson();
        XenoKiProfile again = XenoKiProfile.fromJson("xenopixelsmod:bt3_test", json);
        assertEquals(5240831, again.colorInterior());
        assertEquals(1.15f, again.size(), 1e-4);
        assertTrue(again.centerMuzzle());
        assertEquals("trail", again.fxTrail());
        XenoKiProfile over = XenoKiProfile.fromJson("xenopixelsmod:bt3_test",
                JsonParser.parseString("{\"size\":2.0,\"centerMuzzle\":false}").getAsJsonObject());
        XenoKiProfile merged = base.merge(over);
        assertEquals(2.0f, merged.size(), 1e-4);
        assertFalse(merged.centerMuzzle(), "explicit overlay centerMuzzle wins");
    }

    @Test void ownedKiUsesBundledProfileEffectsThroughChargeAndFlight() {
        V3KiStyle style = new V3KiStyle(KiLook.Kind.WAVE, 0x4FFFBF, 0x4FFFBF,
                1f, 1.6, 1, 5, 3f, 1, "ki_wave_body_0000ff",
                "ki_impact_0000ff", "ki_charge_0000ff");
        assertEquals("ki_charge_0000ff", style.asset(KiLook.Part.CHARGE));
        assertEquals("ki_wave_body_0000ff", style.charged(2f).asset(KiLook.Part.WAVE_BODY));
        assertEquals("ki_impact_0000ff", style.asset(KiLook.Part.IMPACT));
        V3KiStyle invalid = new V3KiStyle(KiLook.Kind.WAVE, 0x4FFFBF, 0x4FFFBF,
                1f, 1.6, 1, 5, 3f, 1, "../invalid", null, null);
        assertEquals(KiLook.asset(KiLook.Part.WAVE_BODY, 0x4FFFBF),
                invalid.asset(KiLook.Part.WAVE_BODY));
    }

    @Test void centerMuzzleAndArmorPenRoundTrip() {
        XenoKiProfile profile = new XenoKiProfile(
                "xenopixelsmod:bt3_test_kame", "kamehameha",
                0x00AAFF, 0x0044AA, null, 1.2f, 1.1f, 1.0f, 25,
                0.4f, 0.2f, 0.5f, null, true, null, null, null);
        assertTrue(profile.centerMuzzle());
        assertEquals(25, profile.armorPenetration());
        assertEquals(0f, profile.centerMuzzle() ? 0f : profile.castOffsetX(), 1e-4);
        XenoKiProfile again = XenoKiProfile.fromJson(profile.techniqueId(), profile.toJson());
        assertTrue(again.centerMuzzle());
        assertEquals(25, again.armorPenetration());
        assertEquals(0.4f, again.castOffsetX(), 1e-4);
    }

    @Test void catalogLoadsAndSavesSeedFile() throws Exception {
        Path file = temp.resolve("ki_profiles.json");
        XenoKiProfileCatalog.load(file);
        assertFalse(XenoKiProfileCatalog.archetypesView().isEmpty());
        assertTrue(Files.isRegularFile(file));
        XenoKiProfile kame = XenoKiProfileCatalog.resolve(
                "xenopixelsmod:bt3_early_kid_goku_kamehameha_15", "beam", "kamehameha");
        assertNotNull(kame.colorInterior());
        assertEquals(5240831, kame.colorInterior());
        assertTrue(kame.centerMuzzle());
        XenoKiProfileCatalog.put(new XenoKiProfile(
                "xenopixelsmod:bt3_early_kid_goku_kamehameha_15", "kamehameha",
                0x112233, null, null, 1.5f, null, null, null, null, null, null, null, true, null, null, null));
        XenoKiProfileCatalog.save(file);
        XenoKiProfileCatalog.load(file);
        XenoKiProfile again = XenoKiProfileCatalog.resolve(
                "xenopixelsmod:bt3_early_kid_goku_kamehameha_15", "beam", "kamehameha");
        assertEquals(0x112233, again.colorInterior());
        assertEquals(1.5f, again.size(), 1e-4);
    }
}
