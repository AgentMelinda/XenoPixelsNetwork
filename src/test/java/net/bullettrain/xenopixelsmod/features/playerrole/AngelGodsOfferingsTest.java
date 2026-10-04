package net.bullettrain.xenopixelsmod.features.playerrole;

import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadata;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadataRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AngelGodsOfferingsTest {
    @AfterEach
    void clearRegistry() {
        DmzFormMetadataRegistry.applySnapshot("{}");
    }

    @Test
    void angelOfferingsOnlyGodsForms() {
        seedGodsAndOther();

        List<String> formTypes = AngelTrainerGate.offeringsFor(PlayerRoleId.ANGEL);
        assertFalse(formTypes.isEmpty());
        // Real skill formType for group xenopixels_gods_forms is xenopixels_divinity
        // (see xenopixels_gods_forms.json / DmzContentBootstrap) — not xenopixels_gods_forms.*.
        for (String ft : formTypes) {
            assertTrue(AngelTrainerGate.GODS_FORM_TYPE.equalsIgnoreCase(ft)
                            || belongsToGodsGroup(ft),
                    "unexpected angel offering formType: " + ft);
        }
        assertTrue(formTypes.stream()
                .anyMatch(AngelTrainerGate.GODS_FORM_TYPE::equalsIgnoreCase));
        assertFalse(formTypes.stream()
                .anyMatch(ft -> "xenopixels_fan_ss".equalsIgnoreCase(ft)));
        assertTrue(AngelTrainerGate.offeringsFor(PlayerRoleId.NONE).isEmpty());
    }

    @Test
    void angelBuiltinFallbackIsDivinityWhenRegistryEmpty() {
        assertEquals(List.of(AngelTrainerGate.GODS_FORM_TYPE),
                AngelTrainerGate.offeringsFor(PlayerRoleId.ANGEL));
        assertTrue(AngelTrainerGate.allowsFormType(PlayerRoleId.ANGEL,
                AngelTrainerGate.GODS_FORM_TYPE));
        assertFalse(AngelTrainerGate.allowsFormType(PlayerRoleId.ANGEL, "xenopixels_fan_ss"));
        assertFalse(AngelTrainerGate.allowsFormType(PlayerRoleId.NONE,
                AngelTrainerGate.GODS_FORM_TYPE));
    }

    @Test
    void resolveOfferingRejectsNonGodsFormType() {
        seedGodsAndOther();
        assertEquals(AngelTrainerGate.GODS_FORM_TYPE,
                AngelTrainerGate.resolveOffering(AngelTrainerGate.GODS_FORM_TYPE).formType);
        assertEquals(null, AngelTrainerGate.resolveOffering("xenopixels_fan_ss"));
    }

    private static boolean belongsToGodsGroup(String formType) {
        for (var offering : DmzFormMetadataRegistry.all()) {
            if (offering.metadata() == null) continue;
            if (!AngelTrainerGate.isGodsGroup(offering.metadata())) continue;
            if (formType.equalsIgnoreCase(offering.metadata().formType)) return true;
        }
        return false;
    }

    private static void seedGodsAndOther() {
        DmzFormMetadata gods = new DmzFormMetadata();
        gods.race = "saiyan";
        gods.group = AngelTrainerGate.GODS_GROUP;
        gods.formType = AngelTrainerGate.GODS_FORM_TYPE;
        gods.masterLearningEnabled = true;
        gods.buyFromMaster = true;
        gods.skillCosts = new ArrayList<>(List.of(150000));

        DmzFormMetadata other = new DmzFormMetadata();
        other.race = "saiyan";
        other.group = "xenopixels_fan_ss";
        other.formType = "xenopixels_fan_ss";
        other.masterLearningEnabled = true;
        other.buyFromMaster = true;
        other.skillCosts = new ArrayList<>(List.of(120000));

        JsonObject snapshot = new JsonObject();
        snapshot.add("normal:saiyan:xenopixels_gods_forms",
                DmzFormMetadataRegistry.gson().toJsonTree(gods));
        snapshot.add("normal:saiyan:xenopixels_fan_ss",
                DmzFormMetadataRegistry.gson().toJsonTree(other));
        DmzFormMetadataRegistry.applySnapshot(snapshot.toString());
    }
}
