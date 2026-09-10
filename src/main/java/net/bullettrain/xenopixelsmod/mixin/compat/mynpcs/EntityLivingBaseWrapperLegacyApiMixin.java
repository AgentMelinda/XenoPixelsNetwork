package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import net.minecraft.world.entity.LivingEntity;
import espi.mynpcs.api.item.IItemStack;
import espi.mynpcs.api.wrapper.EntityLivingBaseWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Adds legacy main-hand method names to current CustomNPCs living-entity wrappers. */
@Mixin(value = EntityLivingBaseWrapper.class, remap = false)
public abstract class EntityLivingBaseWrapperLegacyApiMixin {
    @Shadow public abstract LivingEntity getMCEntity();
    @Shadow public abstract void swingMainhand();
    @Shadow public abstract IItemStack getMainhandItem();
    @Shadow public abstract void setMainhandItem(IItemStack item);

    public LivingEntity getMinecraftEntity() {
        return getMCEntity();
    }

    public void swingHand() {
        swingMainhand();
    }

    public IItemStack getHeldItem() {
        return getMainhandItem();
    }

    public void setHeldItem(IItemStack item) {
        setMainhandItem(item);
    }
}
