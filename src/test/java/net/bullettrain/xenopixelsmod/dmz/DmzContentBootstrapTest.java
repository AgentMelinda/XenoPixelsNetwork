package net.bullettrain.xenopixelsmod.dmz;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmzContentBootstrapTest {
    private static final int[] VANILLA_PRICES = {
            13000, 21000, 31000, 42000, 52000, 65000, 78000, 104000
    };
    private static final int[] LEGACY_PRICES = {
            120000, 145000, 175000, 210000, 250000, 300000
    };

    @Test
    void repairsExtendedLegacySuperforms() {
        JsonObject costs = costs(true, concat(VANILLA_PRICES, LEGACY_PRICES));

        assertTrue(DmzContentBootstrap.repairLegacySuperforms(costs));

        JsonObject superforms = costs.getAsJsonObject("superforms");
        assertFalse(superforms.get("buyFromMaster").getAsBoolean());
        assertPrices(superforms.getAsJsonArray("prices"), VANILLA_PRICES);
    }

    @Test
    void repairsAlreadyTrimmedLegacySuperforms() {
        JsonObject costs = costs(true, VANILLA_PRICES);

        assertTrue(DmzContentBootstrap.repairLegacySuperforms(costs));

        JsonObject superforms = costs.getAsJsonObject("superforms");
        assertFalse(superforms.get("buyFromMaster").getAsBoolean());
        assertPrices(superforms.getAsJsonArray("prices"), VANILLA_PRICES);
    }

    @Test
    void leavesCorrectVanillaSuperformsUnchanged() {
        JsonObject costs = costs(false, VANILLA_PRICES);

        assertFalse(DmzContentBootstrap.repairLegacySuperforms(costs));
        assertFalse(costs.getAsJsonObject("superforms").get("buyFromMaster").getAsBoolean());
        assertPrices(costs.getAsJsonObject("superforms").getAsJsonArray("prices"), VANILLA_PRICES);
    }

    @Test
    void leavesCustomSuperformsConfigurationUnchanged() {
        int[] customPrices = VANILLA_PRICES.clone();
        customPrices[0] = 999;
        JsonObject costs = costs(true, customPrices);

        assertFalse(DmzContentBootstrap.repairLegacySuperforms(costs));
        assertTrue(costs.getAsJsonObject("superforms").get("buyFromMaster").getAsBoolean());
        assertPrices(costs.getAsJsonObject("superforms").getAsJsonArray("prices"), customPrices);
    }

    @Test
    void leavesXenoFormSkillsMasterGated() {
        JsonObject costs = costs(true, VANILLA_PRICES);
        JsonObject xeno = new JsonObject();
        xeno.addProperty("buyFromMaster", true);
        JsonArray prices = new JsonArray();
        prices.add(120000);
        xeno.add("prices", prices);
        costs.add("xenopixels_fan_ss", xeno);

        assertTrue(DmzContentBootstrap.repairLegacySuperforms(costs));
        assertTrue(costs.getAsJsonObject("xenopixels_fan_ss")
                .get("buyFromMaster").getAsBoolean());
    }

    private static JsonObject costs(boolean buyFromMaster, int[] prices) {
        JsonArray priceArray = new JsonArray();
        for (int price : prices) {
            priceArray.add(price);
        }
        JsonObject superforms = new JsonObject();
        superforms.addProperty("buyFromMaster", buyFromMaster);
        superforms.add("prices", priceArray);
        JsonObject costs = new JsonObject();
        costs.add("superforms", superforms);
        return costs;
    }

    private static int[] concat(int[] first, int[] second) {
        int[] result = new int[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static void assertPrices(JsonArray actual, int[] expected) {
        assertEquals(expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual.get(i).getAsInt());
        }
    }
}
