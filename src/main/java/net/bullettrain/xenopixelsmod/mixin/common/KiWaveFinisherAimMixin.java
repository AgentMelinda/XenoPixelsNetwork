package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import net.bullettrain.xenopixelsmod.combat.v2.UltimateFinisherKamehameha;
import net.bullettrain.xenopixelsmod.combat.v2.UltimateFinisherWaveTarget;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ultimate Finisher waves: DMZ's kame tick forces OFFSET_X=0.4 (right hand) after tick 15, which
 * aims the beam past the victim. Zero the cast offset and disable continuous follow for our
 * marked finisher wave. The target ID is synced because persistent NBT alone is
 * not client metadata. HEAD aligns before collision detection, the native
 * three-argument position update is corrected at TAIL, and tick TAIL clears
 * DMZ's later hand-offset writes before rendering. Stock waves are unaffected.
 */
@Mixin(value = KiWaveEntity.class, remap = false)
public abstract class KiWaveFinisherAimMixin implements UltimateFinisherWaveTarget {
    @Unique private static final EntityDataAccessor<Integer> XENO_FINISHER_TARGET =
            SynchedEntityData.defineId(KiWaveEntity.class, EntityDataSerializers.INT);

    @Inject(method = "defineSynchedData", at = @At("TAIL"), remap = true)
    private void xenopixels$defineFinisherTarget(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(XENO_FINISHER_TARGET, -1);
    }
    @Override public int xenopixels$finisherTarget() {
        return ((KiWaveEntity) (Object) this).getEntityData().get(XENO_FINISHER_TARGET);
    }
    @Override public void xenopixels$finisherTarget(int targetId) {
        ((KiWaveEntity) (Object) this).getEntityData().set(XENO_FINISHER_TARGET, targetId);
    }

    @Inject(method = "tick", at = @At("HEAD"), remap = true)
    private void xenopixels$clearFinisherHandOffset(CallbackInfo ci) {
        KiWaveEntity self = (KiWaveEntity) (Object) this;
        if (!UltimateFinisherKamehameha.owns(self)) return;
        self.setContinuousFollow(false);
        self.setCastOffsets(0f, 0f, 0f);
        UltimateFinisherKamehameha.align(self);
    }

    @Inject(method = "updatePositionRelativeToOwner(Lnet/minecraft/world/entity/LivingEntity;ZLnet/minecraft/world/phys/Vec3;)V",
            at = @At("TAIL"), remap = false)
    private void xenopixels$alignBeforeHitDetection(LivingEntity owner, boolean casting, Vec3 look, CallbackInfo ci) {
        UltimateFinisherKamehameha.align((KiWaveEntity) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = true)
    private void xenopixels$preserveFinisherAimForRender(CallbackInfo ci) {
        UltimateFinisherKamehameha.align((KiWaveEntity) (Object) this);
    }
}
