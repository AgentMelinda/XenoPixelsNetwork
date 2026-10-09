package net.bullettrain.xenopixelsmod.combat.technique;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import net.minecraft.server.level.ServerPlayer;

/** Native DMZ list/slot entry; UltimateFinisher owns the intercepted cast lifecycle. */
public final class UltimateFinisherTechnique {
    public static final String ID = "xenopixelsmod:ultimate_finisher";
    public static final int COOLDOWN_TICKS = 400;
    private UltimateFinisherTechnique() {}

    public static void register() {
        if (PredefinedTechniques.STRIKE_REGISTRY.containsKey(ID)) return;
        StrikeAttackData strike = new StrikeAttackData();
        strike.setId(ID);
        strike.setName("UltimateFinisher");
        strike.setAuthor("XenoPixels");
        strike.setAnimationId("combat.xeno_heavy_finish_v4");
        strike.setDamageMultiplier(4.0f);
        strike.setDurationTicks(144);
        strike.setBaseCost(40.0);
        strike.applyConfigDefaults();
        strike.setCooldown(COOLDOWN_TICKS);
        PredefinedTechniques.STRIKE_REGISTRY.put(ID, strike);
    }

    /** Follows the rush kit's existing auto-unlock / earned Rush skill decision. */
    public static void unlock(ServerPlayer player) {
        StrikeAttackData strike = PredefinedTechniques.STRIKE_REGISTRY.get(ID);
        if (strike == null) return;
        StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
            if (stats.getTechniques() != null) stats.getTechniques().unlockTechnique(strike);
        });
    }
}
