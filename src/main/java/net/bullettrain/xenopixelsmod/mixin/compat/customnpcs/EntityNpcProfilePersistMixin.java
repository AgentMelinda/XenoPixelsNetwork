package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.compat.npc.NpcProfilePersistence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the DMZ combat profile inside CustomNPCs' own save/clone/wand NBT. */
@Pseudo
@Mixin(targets = "noppes.npcs.entity.EntityNPCInterface", remap = false)
public abstract class EntityNpcProfilePersistMixin {
    @Inject(method = {"addAdditionalSaveData", "m_7380_"}, at = @At("RETURN"))
    private void xenopixels$writeProfile(CompoundTag tag, CallbackInfo ci) {
        NpcProfilePersistence.writeToNpcTag((Entity) (Object) this, tag);
    }

    @Inject(method = {"readAdditionalSaveData", "m_7378_"}, at = @At("RETURN"))
    private void xenopixels$readProfile(CompoundTag tag, CallbackInfo ci) {
        NpcProfilePersistence.readFromNpcTag((Entity) (Object) this, tag);
    }

    @Inject(method = "writeSpawnData()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void xenopixels$writeSpawnProfile(CallbackInfoReturnable<CompoundTag> cir) {
        NpcProfilePersistence.writeToNpcTag((Entity) (Object) this, cir.getReturnValue());
    }

    @Inject(method = "readSpawnData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void xenopixels$readSpawnProfile(CompoundTag tag, CallbackInfo ci) {
        NpcProfilePersistence.readFromNpcTag((Entity) (Object) this, tag);
    }
}
