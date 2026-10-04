package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.world.entity.LivingEntity;

/**
 * Mixin surface on {@code StatsData} so an NPC can host a real DragonMineZ blob without a
 * world {@code Player} / FakePlayer.
 */
public interface NpcStatsDataAccess {
    void xenopixels$setNpcHost(LivingEntity npc);

    LivingEntity xenopixels$getNpcHost();

    void xenopixels$markLoaded();
}
