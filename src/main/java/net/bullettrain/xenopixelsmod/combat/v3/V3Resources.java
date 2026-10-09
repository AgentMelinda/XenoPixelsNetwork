package net.bullettrain.xenopixelsmod.combat.v3;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcResources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * V3's view of the DragonMineZ and Xeno NPC stamina and ki pools.
 *
 * <p>A fighter with no attached stats cannot act and cannot be drained: nothing here invents a
 * pool. Victim drain writes the pool directly rather than "spending" it, so being drained is not
 * credited to the victim as training.
 */
public final class V3Resources {
    private V3Resources() {}

    /** A single stamina pool, as the transaction rules see it. */
    public interface Pool {
        float get();
        void set(float value);
    }

    static Resources resources(Player player) {
        try {
            StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            return stats == null ? null : stats.getResources();
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    /** The attacker's stamina, or null when the player has no DragonMineZ pools yet. */
    static Pool stamina(ServerPlayer player) {
        Resources res = resources(player);
        if (res == null) return null;
        return new Pool() {
            @Override public float get() { return res.getCurrentStamina(); }
            // Attacker spend goes through DMZ's own removal so it counts as real exertion.
            @Override public void set(float value) { res.removeStamina(Math.max(0f, res.getCurrentStamina() - value)); }
        };
    }

    /** What a technique's ki price is right now: nothing while Sparking, as in the source game's MAX power. */
    static float kiCost(float cost, boolean sparking) {
        return sparking || !(cost > 0f) ? 0f : cost;
    }

    /** Spends ki and stamina together or not at all. Creative players act for free. */
    public static boolean spend(ServerPlayer player, float ki, float stamina) {
        if (ki <= 0f && stamina <= 0f) return true;
        if (player.isCreative()) return true;
        Resources res = resources(player);
        if (res == null) return false;
        if (ki > 0f && res.getCurrentEnergy() < ki) return false;
        if (stamina > 0f && res.getCurrentStamina() < stamina) return false;
        if (ki > 0f) res.removeEnergy(ki);
        if (stamina > 0f) res.removeStamina(stamina);
        return true;
    }

    /** The victim's drainable stamina, or null for entities that own no verified pool. */
    static Pool victimStamina(LivingEntity victim) {
        if (victim instanceof Player player) {
            Resources res = resources(player);
            if (res == null) return null;
            return new Pool() {
                @Override public float get() { return res.getCurrentStamina(); }
                @Override public void set(float value) { res.setCurrentStamina(Math.max(0f, value)); }
            };
        }
        NpcCombatProfile profile;
        try {
            profile = NpcCombatProfile.read(victim);
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
        if (profile == null) return null;
        return new Pool() {
            @Override public float get() { return (float) NpcResources.get(victim, profile).stamina(); }
            // Clones keep their owner's pool: NpcResources.setStamina deliberately ignores them.
            @Override public void set(float value) { NpcResources.setStamina(victim, profile, Math.max(0f, value)); }
        };
    }

    /** Removes up to {@code amount} stamina from {@code victim}. @return the units actually removed */
    public static float drainStamina(LivingEntity victim, float amount) {
        if (victim == null || !(amount > 0f)) return 0f;
        Pool pool = victimStamina(victim);
        if (pool == null) return 0f;
        float before = pool.get();
        pool.set(before - Math.min(before, amount));
        return Math.max(0f, before - pool.get());
    }
}
