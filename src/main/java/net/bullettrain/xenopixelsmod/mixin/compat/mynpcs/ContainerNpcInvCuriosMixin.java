package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import espi.mynpcs.containers.ContainerNPCInv;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCuriosInventory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ContainerNPCInv.class, remap = false)
public abstract class ContainerNpcInvCuriosMixin {

    @Shadow(remap = true)
    protected abstract Slot addSlot(Slot slot);

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void xenopixels$addCuriosSlots(int containerId, Inventory inventory, int entityId,
                                           CallbackInfo ci) {
        if (inventory == null || inventory.player == null || inventory.player.level() == null) {
            NpcCuriosInventory.addSlots(null, this::addSlot);
            return;
        }
        Entity entity = inventory.player.level().getEntity(entityId);
        LivingEntity living = entity instanceof LivingEntity npc ? npc : null;
        NpcCuriosInventory.addSlots(living, this::addSlot);
    }
}
