package net.bullettrain.xenopixelsmod.item.custom;

import com.dragonminez.common.stats.StatsData;
import net.bullettrain.xenopixelsmod.api.dmz.DmzAccess;
import net.bullettrain.xenopixelsmod.api.dmz.DmzSync;
import net.minecraft.server.level.ServerPlayer;

/**
 * The strawberry senzu's full restore: health, and DragonMineZ ki and stamina (2026-09-29 owner).
 * Ki and stamina go through DMZ's own {@code Resources.setCurrentEnergy} / {@code setCurrentStamina}
 * (verified against libs/dragonminez-2.1.3.jar) and are then synced to the client HUD.
 */
public final class SenzuRestore {
    private SenzuRestore() {
    }

    /** What the restore touches, so the rule is testable without a game. */
    interface Pools {
        float maxHealth();
        void setHealth(float value);
        boolean hasDmzStats();
        float maxKi();
        void setKi(float value);
        float maxStamina();
        void setStamina(float value);
    }

    /** Fills every pool; returns whether DMZ stats were changed (and so need a sync). */
    static boolean fill(Pools pools) {
        pools.setHealth(pools.maxHealth());
        if (!pools.hasDmzStats()) return false;
        pools.setKi(pools.maxKi());
        pools.setStamina(pools.maxStamina());
        return true;
    }

    public static void restore(ServerPlayer player) {
        StatsData stats = DmzAccess.stats(player).orElse(null);
        boolean changed = fill(new Pools() {
            @Override public float maxHealth() { return player.getMaxHealth(); }
            @Override public void setHealth(float value) { player.setHealth(value); }
            @Override public boolean hasDmzStats() { return stats != null && stats.getResources() != null; }
            @Override public float maxKi() { return stats.getMaxEnergy(); }
            @Override public void setKi(float value) { stats.getResources().setCurrentEnergy(value); }
            @Override public float maxStamina() { return stats.getMaxStamina(); }
            @Override public void setStamina(float value) { stats.getResources().setCurrentStamina(value); }
        });
        if (changed) DmzSync.syncStats(player);
    }
}
