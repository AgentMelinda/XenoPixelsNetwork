package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.stats.character.Stats;
import net.bullettrain.xenopixelsmod.compat.npc.NpcStatsAttributeHost;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes DMZ main-stat attribute writes onto the bound NPC instead of a Player.
 */
@Mixin(value = Stats.class, remap = false)
public abstract class StatsNpcHostMixin implements NpcStatsAttributeHost {
    @Unique
    private LivingEntity xenopixels$npcHost;

    @Override
    public void xenopixels$setNpcHost(LivingEntity npc) {
        this.xenopixels$npcHost = npc;
    }

    @Inject(method = "attributesReady", at = @At("HEAD"), cancellable = true, remap = false)
    private void xenopixels$npcAttributesReady(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity host = this.xenopixels$npcHost;
        if (host != null && host.getAttributes() != null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "setAttributeBaseValue", at = @At("HEAD"), cancellable = true, remap = false)
    private void xenopixels$npcSetAttr(Holder<Attribute> attribute, int value, CallbackInfo ci) {
        LivingEntity host = this.xenopixels$npcHost;
        if (host == null) {
            return;
        }
        AttributeInstance instance = host.getAttribute(attribute);
        if (instance != null && Math.abs(instance.getBaseValue() - (double) value) >= 0.5) {
            instance.setBaseValue(value);
        }
        ci.cancel();
    }

    @Inject(method = "readAttributeBase", at = @At("HEAD"), cancellable = true, remap = false)
    private void xenopixels$npcReadAttr(Holder<Attribute> attribute, int fallback,
                                        CallbackInfoReturnable<Integer> cir) {
        LivingEntity host = this.xenopixels$npcHost;
        if (host == null) {
            return;
        }
        AttributeInstance instance = host.getAttribute(attribute);
        cir.setReturnValue(instance == null ? fallback : (int) Math.round(instance.getBaseValue()));
    }
}
