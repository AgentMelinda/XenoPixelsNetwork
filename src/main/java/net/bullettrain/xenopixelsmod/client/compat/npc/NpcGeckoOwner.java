package net.bullettrain.xenopixelsmod.client.compat.npc;

import net.minecraft.world.entity.LivingEntity;

/** Associates CNPC Gecko Addon's render surrogate with its real CustomNPC owner. */
public interface NpcGeckoOwner {
    LivingEntity xenopixels$getNpcOwner();
    void xenopixels$setNpcOwner(LivingEntity owner);
}
