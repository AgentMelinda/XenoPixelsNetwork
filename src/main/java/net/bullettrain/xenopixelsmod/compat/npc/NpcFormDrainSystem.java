package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import net.minecraft.world.entity.LivingEntity;

/** DMZ-equivalent once-per-second normal+stack form resource drains for NPC profiles. */
public final class NpcFormDrainSystem {
    private NpcFormDrainSystem() {}

    public static void tick(LivingEntity npc, NpcCombatProfile profile, int serverTick) {
        if (npc == null || profile == null || serverTick % 20 != Math.floorMod(npc.getId(), 20)) return;
        FormConfig.FormData normal = NpcFormLookup.activeForm(profile);
        FormConfig.FormData stack = NpcFormLookup.activeStackForm(profile);
        if (normal == null && stack == null) return;

        double energy = drain(profile, normal, stack, Drain.ENERGY);
        double stamina = drain(profile, normal, stack, Drain.STAMINA);
        double health = drain(profile, normal, stack, Drain.HEALTH);
        int energyWhole = (int) Math.round(energy);
        int staminaWhole = (int) Math.round(stamina);
        double healthWhole = Math.round(health);
        NpcResources.Snapshot resources = NpcResources.get(npc, profile);
        boolean enough = (energyWhole <= 0 || resources.energy() >= energyWhole)
                && (staminaWhole <= 0 || resources.stamina() >= staminaWhole)
                && (healthWhole <= 0.0 || npc.getHealth() > healthWhole);
        if (!enough) {
            NpcTransformSystem.descend(npc);
            return;
        }

        NpcResources.setEnergy(npc, profile, resources.energy() - energyWhole);
        NpcResources.setStamina(npc, profile, resources.stamina() - staminaWhole);
        if (healthWhole > 0.0) npc.setHealth((float) (npc.getHealth() - healthWhole));
        else if (healthWhole < 0.0) npc.setHealth((float) Math.min(npc.getMaxHealth(), npc.getHealth() - healthWhole));
    }

    static double drain(NpcCombatProfile profile, FormConfig.FormData normal,
                        FormConfig.FormData stack, Drain type) {
        double normalDrain = adjusted(profile, normal, false, type);
        double stackDrain = adjusted(profile, stack, true, type);
        if (normal != null && stack != null) {
            double combined = normal.getStackDrainMultiplier() * stack.getStackDrainMultiplier();
            normalDrain *= combined;
            stackDrain *= combined;
        }
        double raw = normalDrain + stackDrain;
        if (raw == 0.0) return 0.0;
        double baseline = 1.0;
        try { baseline = ConfigManager.getCombatConfig().getBaselineFormDrain(); }
        catch (Throwable ignored) {}
        double scaled = raw * baseline * profile.releaseMultiplier();
        return raw < 0.0 ? Math.min(-1.0, scaled) : Math.max(1.0, scaled);
    }

    private static double adjusted(NpcCombatProfile profile, FormConfig.FormData form,
                                   boolean stack, Drain type) {
        if (form == null) return 0.0;
        double raw = switch (type) {
            case ENERGY -> form.getEnergyDrain();
            case STAMINA -> form.getStaminaDrain();
            case HEALTH -> form.getHealthDrain();
        };
        double mastery = stack
                ? profile.stackMasteries.getMastery(profile.stackGroup, profile.stackId)
                : profile.masteries.getMastery(profile.formGroup, profile.formId);
        double max = NpcFormLookup.maxMastery(form);
        double ratio = max <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, mastery / max));
        double cost = 1.0 + ratio * (form.getMaxCostMultiplier() - 1.0);
        return raw < 0.0 ? raw / cost : raw * cost;
    }

    enum Drain { ENERGY, STAMINA, HEALTH }
}
