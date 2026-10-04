package net.bullettrain.xenopixelsmod.compat.npc;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 2026-09-30 owner: "he still cant hit me when punching me wtf is that bug im complaining about alot".
 *
 * <p>An NPC hit lands as a 1.0 placeholder and {@code onNpcMelee} swaps in the real damage. DragonMineZ's
 * {@code CombatEvent.onLivingHurt} (priority HIGH) records the incoming amount on a player victim as
 * {@code dmz_raw_damage}, and {@code overrideVanillaArmorReduction} (LOWEST) then replaces the damage
 * with that raw value less the player's defense (javap, DMZ 2.1.3 for 1.21.1 and 1.20.1). Running at
 * NORMAL, between the two, our amount was always overwritten by "1.0 less defense" - no damage, whatever
 * the NPC's strength. It has to run before DMZ records the raw value.
 */
class NpcMeleeDamageOrderTest {
    @Test
    void npcDamageIsSetBeforeDragonMineZRecordsTheRawHit() throws NoSuchMethodException {
        SubscribeEvent subscribe = NpcMeleeDamage.class
                .getMethod("onNpcMelee", LivingDamageEvent.Pre.class)
                .getAnnotation(SubscribeEvent.class);
        assertEquals(EventPriority.HIGHEST, subscribe.priority());
    }
}
