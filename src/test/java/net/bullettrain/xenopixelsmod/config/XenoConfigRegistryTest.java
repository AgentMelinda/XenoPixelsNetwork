package net.bullettrain.xenopixelsmod.config;

import net.bullettrain.xenopixelsmod.combat.targeting.LockOnConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoConfigRegistryTest {
    private final int savedMax = XenoPartyConfig.maxMembers;
    private final double savedPing = XenoPartyConfig.pingRange;
    private final boolean savedFf = XenoPartyConfig.friendlyFireDefault;
    private final boolean savedPartyFf = LockOnConfig.respectPartyFriendlyFire;
    private final boolean savedCombat = XenoServerConfig.bt3CombatEnabled;
    private final net.bullettrain.xenopixelsmod.combat.v3.V3Config.Values savedV3 =
            net.bullettrain.xenopixelsmod.combat.v3.V3Config.get();

    @AfterEach
    void restore() {
        XenoPartyConfig.maxMembers = savedMax;
        XenoPartyConfig.pingRange = savedPing;
        XenoPartyConfig.friendlyFireDefault = savedFf;
        LockOnConfig.respectPartyFriendlyFire = savedPartyFf;
        XenoServerConfig.bt3CombatEnabled = savedCombat;
        net.bullettrain.xenopixelsmod.combat.v3.V3Config.apply(savedV3);
    }

    @Test void v3AliasUsesSeparateStoreWithoutChangingLegacyRange() {
        double legacyRange = XenoServerConfig.dragonDashRange;
        assertEquals("v3.dragonDashRange", XenoConfigRegistry.resolve("dragonDashRange").id);
        assertEquals(XenoConfigRegistry.Store.V3, XenoConfigRegistry.set("dragonDashRange", "999").store);
        assertTrue(XenoConfigRegistry.suggest("v3.").contains("v3.dragonDashRange"));
        assertTrue(XenoConfigRegistry.get("v3.heavyAttackerStaminaCost").ok);
        assertEquals(legacyRange, XenoServerConfig.dragonDashRange);
    }

    @Test
    void partyMaxMembersClampsAndUsesAlias() {
        XenoConfigRegistry.Result result = XenoConfigRegistry.set("party.max", "8");
        assertTrue(result.ok, result.message);
        assertEquals(XenoConfigRegistry.Store.PARTY, result.store);
        assertEquals(8, XenoPartyConfig.maxMembers);

        XenoConfigRegistry.Result over = XenoConfigRegistry.set("party.maxMembers", "99");
        assertTrue(over.ok, over.message);
        assertEquals(16, XenoPartyConfig.maxMembers);

        XenoConfigRegistry.Result under = XenoConfigRegistry.set("party.max", "1");
        assertTrue(under.ok, under.message);
        assertEquals(2, XenoPartyConfig.maxMembers);
    }

    @Test
    void partyPingRangeClamps() {
        XenoConfigRegistry.Result result = XenoConfigRegistry.set("party.ping", "4");
        assertTrue(result.ok, result.message);
        assertEquals(8.0, XenoPartyConfig.pingRange, 1.0e-5);
        result = XenoConfigRegistry.set("party.pingRange", "200");
        assertTrue(result.ok, result.message);
        assertEquals(200.0, XenoPartyConfig.pingRange, 1.0e-5);
    }

    @Test
    void lockonPartyFriendlyFireAlias() {
        XenoConfigRegistry.Result off = XenoConfigRegistry.set("lockon.partyff", "off");
        assertTrue(off.ok, off.message);
        assertEquals(XenoConfigRegistry.Store.LOCKON, off.store);
        assertFalse(LockOnConfig.respectPartyFriendlyFire);
        XenoConfigRegistry.Result on = XenoConfigRegistry.set("lockon.respectPartyFriendlyFire", "true");
        assertTrue(on.ok, on.message);
        assertTrue(LockOnConfig.respectPartyFriendlyFire);
    }

    @Test
    void combatKeyStillRoutesAndDoesNotTouchParty() {
        int before = XenoPartyConfig.maxMembers;
        XenoConfigRegistry.Result result = XenoConfigRegistry.set("combat", "false");
        assertTrue(result.ok, result.message);
        assertEquals(XenoConfigRegistry.Store.COMBAT, result.store);
        assertFalse(XenoServerConfig.bt3CombatEnabled);
        assertEquals(before, XenoPartyConfig.maxMembers);
    }

    @Test
    void hudPrefixIsOutOfScope() {
        assertTrue(XenoConfigRegistry.isHudPrefix("hud"));
        assertTrue(XenoConfigRegistry.isHudPrefix("partyhud"));
        assertFalse(XenoConfigRegistry.isHudPrefix("party.maxMembers"));
        assertTrue(XenoConfigRegistry.hudOutOfScope().contains("/xenohud"));
    }

    @Test
    void getReportsPartyHelp() {
        XenoConfigRegistry.Result result = XenoConfigRegistry.get("party.max");
        assertTrue(result.ok, result.message);
        assertTrue(result.message.contains("party.maxMembers"));
        assertEquals(XenoConfigRegistry.Store.PARTY, result.store);
    }
}
