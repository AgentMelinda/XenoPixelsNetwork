package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.stats.techniques.TechniqueData;
import com.dragonminez.common.stats.techniques.Techniques;
import net.bullettrain.xenopixelsmod.combat.technique.TechniqueSlotBind;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Target: {@code Techniques#equipOrSwapTechnique}
 * Reason: stock bind swaps occupants and treats {@code ""} as an equipped id, so binding onto a
 *         used slot looks like the attack deleted itself.
 * Version: NeoForge 1.21.1 / DMZ 2.1.x
 * Side: common. Body is a containsKey check + array write.
 */
@Mixin(value = Techniques.class, remap = false)
public abstract class TechniquesEquipMixin {

    @Shadow
    public abstract Map<String, TechniqueData> getUnlockedTechniques();

    @Shadow
    public abstract String[] getEquippedSlots();

    @Inject(method = "equipOrSwapTechnique", at = @At("HEAD"), cancellable = true)
    private void xenopixels$placeNotSwap(int slotIndex, String techniqueId, CallbackInfo ci) {
        if (slotIndex < 0 || slotIndex >= Techniques.SLOT_COUNT) {
            ci.cancel();
            return;
        }
        boolean empty = techniqueId == null || techniqueId.isEmpty();
        if (!empty && !this.getUnlockedTechniques().containsKey(techniqueId)) {
            ci.cancel();
            return;
        }
        TechniqueSlotBind.place(this.getEquippedSlots(), slotIndex, techniqueId);
        ci.cancel();
    }
}
