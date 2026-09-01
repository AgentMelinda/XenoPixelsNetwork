package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainDamageTypes;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Scales an NPC's direct (melee) attack damage to {@link NpcCombatProfile#meleeDamage()} --
 * DMZ's real {@code StatsData.getMeleeDamage()} formula ({@code 1.0 + strength * release}),
 * driven by {@code strength} the same way a real player's melee hit is. Before this, an NPC
 * with a combat profile still dealt whatever damage CustomNPCs' own vanilla attack-damage
 * attribute produced, entirely independent of the stats shown in the DMZ wand tab.
 *
 * <p>Ki attacks are untouched here -- {@link NpcKiAttackDispatcher} already computes their own
 * damage and bakes it into the projectile directly, and a ki projectile's damage source has a
 * direct entity distinct from its owner, which is how this is told apart from a real melee hit.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcMeleeDamage {
    private NpcMeleeDamage() {
    }

    @SubscribeEvent
    public static void onNpcMelee(LivingDamageEvent.Pre event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && event.getSource().getDirectEntity() == attacker
                && !MainDamageTypes.isStrikeAttackDamage(event.getSource())
                && NpcCombatProfile.hasProfile(attacker)) {
            NpcCombatProfile profile = NpcCombatProfile.read(attacker);
            double staminaCost = Math.max(1.0, Math.ceil(profile.meleeDamage()
                    * ConfigManager.getCombatConfig().getStaminaConsumptionRatio()));
            float damage = NpcResources.spendStamina(attacker, profile, staminaCost)
                    ? profile.meleeDamage() : 1.0f;
            float residual = Math.max(0f, event.getOriginalDamage() - 1.0f);
            event.setNewDamage(damage + residual);
        }

        if (NpcCombatProfile.hasProfile(event.getEntity())) {
            NpcCombatProfile defender = NpcCombatProfile.read(event.getEntity());
            double defense = NpcStatMath.defense(defender.resistance,
                    NpcFormLookup.multiplier(defender, "DEF"), defender.releaseMultiplier());
            var combat = ConfigManager.getCombatConfig();
            double scale = Math.max(12.0, (defender.resistance + 20.0)
                    * combat.getDefenseReductionScale());
            double mitigated = NpcStatMath.mitigate(event.getNewDamage(), defense, scale,
                    combat.getBaseDamageReductionCap());
            event.setNewDamage((float) mitigated);
        }
    }
}
