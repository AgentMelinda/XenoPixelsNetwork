package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

/** Persistent energy/stamina resources for DMZ-profiled NPCs. */
public final class NpcResources {
    private static final String NBT_KEY = "xenopixels:npc_resources";
    private static final String ENERGY = "Energy";
    private static final String STAMINA = "Stamina";
    private static final String MAX_ENERGY = "MaxEnergy";
    private static final String MAX_STAMINA = "MaxStamina";

    private NpcResources() {}

    public record Snapshot(double energy, double maxEnergy, double stamina, double maxStamina) {}

    public static Snapshot get(LivingEntity npc, NpcCombatProfile profile) {
        if (npc instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity clone)
            return net.bullettrain.xenopixelsmod.combat.clone.CloneCombatBridge.resources(clone);
        double maxEnergy = NpcStatMath.maxEnergy(profile.energy,
                NpcFormLookup.multiplier(profile, "ENE"));
        double maxStamina = NpcStatMath.maxStamina(profile.resistance,
                NpcFormLookup.multiplier(profile, "STM"));
        CompoundTag root = npc.getPersistentData();
        CompoundTag tag = root.getCompound(NBT_KEY);
        double oldMaxEnergy = tag.contains(MAX_ENERGY) ? tag.getDouble(MAX_ENERGY) : maxEnergy;
        double oldMaxStamina = tag.contains(MAX_STAMINA) ? tag.getDouble(MAX_STAMINA) : maxStamina;
        double energy = tag.contains(ENERGY) ? tag.getDouble(ENERGY) : maxEnergy;
        double stamina = tag.contains(STAMINA) ? tag.getDouble(STAMINA) : maxStamina;
        if (Math.abs(oldMaxEnergy - maxEnergy) > 1.0e-6) {
            energy = NpcStatMath.preservePercent(energy, oldMaxEnergy, maxEnergy);
        }
        if (Math.abs(oldMaxStamina - maxStamina) > 1.0e-6) {
            stamina = NpcStatMath.preservePercent(stamina, oldMaxStamina, maxStamina);
        }
        Snapshot snapshot = new Snapshot(clamp(energy, maxEnergy), maxEnergy,
                clamp(stamina, maxStamina), maxStamina);
        // Only persist when the stored values actually differ; a plain read is by far the
        // common case and used to rewrite the tag every time.
        if (!matches(tag, snapshot)) {
            save(npc, snapshot);
        }
        return snapshot;
    }

    public static boolean spendEnergy(LivingEntity npc, NpcCombatProfile profile, double amount) {
        if (npc instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity clone)
            return net.bullettrain.xenopixelsmod.combat.clone.CloneCombatBridge.spend(clone, amount, 0);
        Snapshot now = get(npc, profile);
        double cost = Math.max(0.0, amount);
        if (now.energy() + 1.0e-6 < cost) return false;
        save(npc, new Snapshot(now.energy() - cost, now.maxEnergy(), now.stamina(), now.maxStamina()));
        return true;
    }

    public static boolean spendStamina(LivingEntity npc, NpcCombatProfile profile, double amount) {
        if (npc instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity clone)
            return net.bullettrain.xenopixelsmod.combat.clone.CloneCombatBridge.spend(clone, 0, amount);
        Snapshot now = get(npc, profile);
        double cost = Math.max(0.0, amount);
        if (now.stamina() + 1.0e-6 < cost) return false;
        save(npc, new Snapshot(now.energy(), now.maxEnergy(), now.stamina() - cost, now.maxStamina()));
        return true;
    }

    public static boolean spend(LivingEntity npc, NpcCombatProfile profile,
                                double energyAmount, double staminaAmount) {
        if (npc instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity clone)
            return net.bullettrain.xenopixelsmod.combat.clone.CloneCombatBridge.spend(clone, energyAmount, staminaAmount);
        Snapshot now = get(npc, profile);
        double energyCost = Math.max(0.0, energyAmount);
        double staminaCost = Math.max(0.0, staminaAmount);
        if (now.energy() + 1.0e-6 < energyCost
                || now.stamina() + 1.0e-6 < staminaCost) {
            return false;
        }
        save(npc, new Snapshot(now.energy() - energyCost, now.maxEnergy(),
                now.stamina() - staminaCost, now.maxStamina()));
        return true;
    }

    public static void tick(LivingEntity npc, NpcCombatProfile profile) {
        if (npc instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity) return;
        Snapshot now = get(npc, profile);
        // DMZ's unmodified recovery is (base + governing stat) / 5 per second. ENE governs
        // energy recovery; VIT governs stamina recovery even though RES sets its capacity.
        double energyRecovery = NpcStatMath.resourceRecoveryPerTick(profile.energy,
                NpcFormLookup.multiplier(profile, "ENE"));
        double staminaRecovery = NpcStatMath.resourceRecoveryPerTick(profile.vitality,
                NpcFormLookup.multiplier(profile, "VIT"));
        double energy = Math.min(now.maxEnergy(), now.energy() + energyRecovery);
        double stamina = Math.min(now.maxStamina(), now.stamina() + staminaRecovery);
        // An NPC sitting at full resources -- which is nearly all of them, nearly always --
        // otherwise allocated and stored a fresh CompoundTag every tick to write back the
        // values it already had.
        if (Math.abs(energy - now.energy()) < 1.0e-9 && Math.abs(stamina - now.stamina()) < 1.0e-9) {
            return;
        }
        save(npc, new Snapshot(energy, now.maxEnergy(), stamina, now.maxStamina()));
    }

    public static void setEnergy(LivingEntity npc, NpcCombatProfile profile, double amount) {
        if (npc instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity) return;
        Snapshot now = get(npc, profile);
        save(npc, new Snapshot(clamp(amount, now.maxEnergy()), now.maxEnergy(),
                now.stamina(), now.maxStamina()));
    }

    public static void setStamina(LivingEntity npc, NpcCombatProfile profile, double amount) {
        if (npc instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity) return;
        Snapshot now = get(npc, profile);
        save(npc, new Snapshot(now.energy(), now.maxEnergy(),
                clamp(amount, now.maxStamina()), now.maxStamina()));
    }

    private static void save(LivingEntity npc, Snapshot value) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(ENERGY, value.energy());
        tag.putDouble(MAX_ENERGY, value.maxEnergy());
        tag.putDouble(STAMINA, value.stamina());
        tag.putDouble(MAX_STAMINA, value.maxStamina());
        npc.getPersistentData().put(NBT_KEY, tag);
    }

    private static boolean matches(CompoundTag tag, Snapshot value) {
        return tag.contains(ENERGY) && tag.contains(STAMINA)
                && tag.contains(MAX_ENERGY) && tag.contains(MAX_STAMINA)
                && Math.abs(tag.getDouble(ENERGY) - value.energy()) < 1.0e-9
                && Math.abs(tag.getDouble(STAMINA) - value.stamina()) < 1.0e-9
                && Math.abs(tag.getDouble(MAX_ENERGY) - value.maxEnergy()) < 1.0e-9
                && Math.abs(tag.getDouble(MAX_STAMINA) - value.maxStamina()) < 1.0e-9;
    }

    private static double clamp(double value, double max) {
        if (!Double.isFinite(value)) return max;
        return Math.max(0.0, Math.min(max, value));
    }
}
