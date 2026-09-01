package net.bullettrain.xenopixelsmod.mixin.compat.cnpcgecko;

import net.bullettrain.xenopixelsmod.client.compat.npc.NpcGeckoOwner;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel", remap = false)
public abstract class EntityCustomModelOwnerMixin implements NpcGeckoOwner {
    @Unique private LivingEntity xenopixels$npcOwner;

    @Override public LivingEntity xenopixels$getNpcOwner() { return xenopixels$npcOwner; }
    @Override public void xenopixels$setNpcOwner(LivingEntity owner) { xenopixels$npcOwner = owner; }
}
