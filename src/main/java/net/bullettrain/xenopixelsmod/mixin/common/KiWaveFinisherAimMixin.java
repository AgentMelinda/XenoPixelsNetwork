package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import net.bullettrain.xenopixelsmod.combat.v2.UltimateFinisherKamehameha;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ultimate Finisher waves: DMZ's kame tick forces OFFSET_X=0.4 (right hand) after tick 15, which
 * aims the beam past the victim. Zero the cast offset and disable continuous follow for our
 * marked finisher wave so {@code UltimateFinisher.aimWaveAtTarget} stays authoritative.
 */
@Mixin(value = KiWaveEntity.class, remap = false)
public abstract class KiWaveFinisherAimMixin {
    @Inject(method = "tick", at = @At("HEAD"), remap = true)
    private void xenopixels$clearFinisherHandOffset(CallbackInfo ci) {
        KiWaveEntity self = (KiWaveEntity) (Object) this;
        if (!UltimateFinisherKamehameha.owns(self)) return;
        self.setContinuousFollow(false);
        self.setCastOffsets(0f, 0f, 0f);
    }
}
