package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.api.item.IItemStack;
import noppes.npcs.api.wrapper.EntityLivingBaseWrapper;
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
