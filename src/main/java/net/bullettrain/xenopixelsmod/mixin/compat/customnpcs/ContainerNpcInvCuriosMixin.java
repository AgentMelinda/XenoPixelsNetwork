package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCuriosInventory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import noppes.npcs.containers.ContainerNPCInv;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends Curios equip/cosmetic slots to the CustomNPCs inventory menu.
 *
 * <p>{@code addSlot} lives on {@link AbstractContainerMenu}, not on {@code ContainerNPCInv}. A
 * {@code @Shadow(remap = true)} of it failed at apply ({@code No refMap loaded} in this named-dev
 * run), so the mixin extends the vanilla parent and calls the inherited method instead.
 */
@Mixin(value = ContainerNPCInv.class, remap = false)
public abstract class ContainerNpcInvCuriosMixin extends AbstractContainerMenu {

    protected ContainerNpcInvCuriosMixin(MenuType<?> type, int containerId) {
        super(type, containerId);
    }

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
