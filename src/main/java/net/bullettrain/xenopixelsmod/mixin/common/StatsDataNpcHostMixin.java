package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.stats.StatsData;
import net.bullettrain.xenopixelsmod.compat.npc.NpcStatsDataAccess;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a CustomNPC carry a real {@code StatsData} without constructing a world FakePlayer.
 *
 * <p>DMZ's constructor still needs a {@code Player} reference; NPCs pass {@code null} and bind
 * the living host here so attribute reads and {@code isDataLoaded} work.
 */
@Mixin(value = StatsData.class, remap = false)
public abstract class StatsDataNpcHostMixin implements NpcStatsDataAccess {
    @Shadow private boolean isDataLoaded;

    @Unique
    private LivingEntity xenopixels$npcHost;

    @Override
    public void xenopixels$setNpcHost(LivingEntity npc) {
        this.xenopixels$npcHost = npc;
    }

    @Override
    public LivingEntity xenopixels$getNpcHost() {
        return this.xenopixels$npcHost;
    }

    @Override
    public void xenopixels$markLoaded() {
        this.isDataLoaded = true;
    }

    @Inject(method = "getSecondaryAttributeValue", at = @At("HEAD"), cancellable = true, remap = false)
    private void xenopixels$hostAttrValue(Holder<Attribute> attribute, double fallback,
                                          CallbackInfoReturnable<Double> cir) {
        LivingEntity host = this.xenopixels$npcHost;
        if (host == null) {
            return;
        }
        AttributeInstance instance = host.getAttribute(attribute);
        cir.setReturnValue(instance != null ? instance.getValue() : fallback);
    }

    @Inject(method = "getSecondaryAttributeBaseValue", at = @At("HEAD"), cancellable = true, remap = false)
    private void xenopixels$hostAttrBase(Holder<Attribute> attribute, double fallback,
                                         CallbackInfoReturnable<Double> cir) {
        LivingEntity host = this.xenopixels$npcHost;
        if (host == null) {
            return;
        }
        AttributeInstance instance = host.getAttribute(attribute);
        cir.setReturnValue(instance != null ? instance.getBaseValue() : fallback);
    }

    @Inject(method = "getArmorToughnessValue", at = @At("HEAD"), cancellable = true, remap = false)
    private void xenopixels$hostToughness(CallbackInfoReturnable<Double> cir) {
        LivingEntity host = this.xenopixels$npcHost;
        if (host == null) {
            return;
        }
        AttributeInstance toughness = host.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
        cir.setReturnValue(toughness != null ? toughness.getValue() : 0.0);
    }

    @Redirect(method = {"getMaxDefense", "getDefense"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getArmorValue()I"),
            remap = false, require = 0)
    private int xenopixels$hostArmor(Player player) {
        LivingEntity host = this.xenopixels$npcHost != null ? this.xenopixels$npcHost : player;
        return host == null ? 0 : host.getArmorValue();
    }
}
