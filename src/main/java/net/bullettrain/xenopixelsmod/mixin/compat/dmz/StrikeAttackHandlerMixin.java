package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import net.bullettrain.xenopixelsmod.api.event.StrikeInterceptEvent;
import net.bullettrain.xenopixelsmod.combat.technique.XenoComboStrikes;
import net.bullettrain.xenopixelsmod.combat.technique.XenoSlotTechniques;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs a Xeno move when its DragonMineZ technique slot is triggered, instead of a DMZ strike.
 *
 * <p>Hakai, Zanzoken and Shi Shin No Ken are registered in DMZ's strike registry so they can be
 * equipped to a slot, but they are not melee strikes and must not go through DMZ's strike
 * lifecycle. This is the earliest place to divert them: {@code requestStrike} does nothing before
 * resolving the player's stats, so cancelling at HEAD skips targeting, the dash, damage, the ki
 * cost and the cooldown, all of which each Xeno move already owns for itself.
 *
 * <p>An interception rather than a DMZ event because {@code DMZEvent.StrikeAttackCastEvent} and
 * {@code StrikeAttackFireEvent} are plain {@code Event} subclasses with no cancellation, so
 * listening to either would run the Xeno move <em>and</em> DMZ's strike. Addons that want a
 * say here get {@code StrikeInterceptEvent}, which is cancellable and posted just below.
 *
 * <p>{@code require = 0}: if a DMZ version renames this method the slot route quietly stops
 * diverting rather than the mod refusing to load, and the moves stay reachable through their own
 * key or chord (see {@code Bt3DirectBind}).
 */
@Mixin(targets = "com.dragonminez.server.events.players.combat.StrikeAttackHandler", remap = false)
public abstract class StrikeAttackHandlerMixin {

    @Inject(method = "requestStrike", at = @At("HEAD"), cancellable = true, require = 0)
    private static void xenopixels$castXenoSlotTechnique(ServerPlayer player, int slotIndex,
                                                         CallbackInfo ci) {
        if (player == null || player.level().isClientSide) return;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (data == null || data.getTechniques() == null) return;
        if (data.getTechniques().getSelectedTechnique() instanceof StrikeAttackData v3
                && net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueCatalog.owns(v3.getId())) {
            // A catalog-owned id never falls through to a native strike, even when the cast is refused.
            ci.cancel();
            StrikeInterceptEvent intercept = new StrikeInterceptEvent(player, slotIndex, v3.getId());
            if (!NeoForge.EVENT_BUS.post(intercept).isCanceled()) {
                // The int DMZ passes is a preferred entity id; V3 only ever casts at its approved lock.
                net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.cast(player, net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueCatalog.find(v3.getId()), null);
            }
            return;
        }
        if (data.getTechniques().getSelectedTechnique() instanceof StrikeAttackData ultimate
                && net.bullettrain.xenopixelsmod.combat.technique.UltimateFinisherTechnique.ID.equals(ultimate.getId())) {
            StrikeInterceptEvent intercept = new StrikeInterceptEvent(player, slotIndex, ultimate.getId());
            ci.cancel();
            if (!NeoForge.EVENT_BUS.post(intercept).isCanceled()) {
                net.bullettrain.xenopixelsmod.combat.v2.UltimateFinisher.cast(player, ultimate, slotIndex);
            }
            return;
        }

        String[] slots = data.getTechniques().getEquippedSlots();
        if (slots != null && slotIndex >= 0 && slotIndex < slots.length) {
            String id = slots[slotIndex];
            if (XenoSlotTechniques.isSlotTechniqueId(id)) {
                StrikeInterceptEvent intercept = new StrikeInterceptEvent(player, slotIndex, id);
                if (NeoForge.EVENT_BUS.post(intercept).isCanceled()) return;
                if (XenoSlotTechniques.cast(player, id)) {
                    ci.cancel();
                    return;
                }
            }
        }

        if (data.getTechniques().getSelectedTechnique() instanceof StrikeAttackData strike
                && XenoComboStrikes.isComboId(strike.getId())) {
            String comboId = strike.getId();
            StrikeInterceptEvent comboIntercept = new StrikeInterceptEvent(player, slotIndex, comboId);
            if (NeoForge.EVENT_BUS.post(comboIntercept).isCanceled()) return;
            LivingEntity target = XenoComboStrikes.resolvePreferred(player, slotIndex);
            if (XenoComboStrikes.cast(player, comboId, target)) {
                ci.cancel();
            }
        }
    }
}
