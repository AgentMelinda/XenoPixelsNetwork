package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.world.entity.LivingEntity;

/** Mixin surface on DMZ {@code Stats} so attribute writes go to the NPC living entity. */
public interface NpcStatsAttributeHost {
    void xenopixels$setNpcHost(LivingEntity npc);
}
