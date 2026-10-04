package net.bullettrain.xenopixelsmod.combat.technique;

import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.ChaseFlightSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Adds Xeno's BT3 impact behavior to DMZ's native strike lifecycle.
 *
 * <p>DMZ remains responsible for cast validation, resource cost, cooldown, animation timing, and
 * base strike damage. This listener only adds the authored rush movement after DMZ resolves a hit.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoRushTechniqueEvents {
    private XenoRushTechniqueEvents() {
    }

    @SubscribeEvent
    public static void onPlayerDataLoad(DMZEvent.PlayerDataLoadEvent event) {
        applyGrantedStrikes(event.getPlayer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyGrantedStrikes(player);
        }
    }

    /** Re-applies kit/combo strikes the player already earned. Never grants exclusive combos. */
    static void applyGrantedStrikes(ServerPlayer player) {
        if (player == null) return;
        boolean auto = net.bullettrain.xenopixelsmod.config.XenoServerConfig.rushAutoUnlock;
        int rushLevel = net.bullettrain.xenopixelsmod.features.progression.CombatSkills
                .level(player, net.bullettrain.xenopixelsmod.features.progression.CombatSkills.RUSH);
        if (XenoRushTechniques.shouldUnlockRushKit(auto, rushLevel)) {
            XenoRushTechniques.unlockRushTechniques(player);
        }
        XenoComboStrikes.unlock(player);
    }

    @SubscribeEvent
    public static void onStrikeFire(DMZEvent.StrikeAttackFireEvent event) {
        StrikeAttackData strike = event.getStrike();
        ServerPlayer player = event.getPlayer();
        LivingEntity target = event.getTarget();
        if (strike == null || player == null || target == null || !target.isAlive()) {
            return;
        }
        if (!net.bullettrain.xenopixelsmod.command.XenoPermissions.hasPermission(
                player, net.bullettrain.xenopixelsmod.command.XenoPermissions.SKILL_RUSH_USE)) {
            return;
        }

        String id = strike.getId();
        if (!XenoRushTechniques.isRushId(id)) return;
        double distance;
        double up;
        boolean chase;
        if (XenoRushTechniques.RUSH_LEFT.equals(id) || XenoRushTechniques.RUSH_RIGHT.equals(id)) {
            distance = net.bullettrain.xenopixelsmod.config.XenoServerConfig.rushKnockbackLeftRight;
            up = net.bullettrain.xenopixelsmod.config.XenoServerConfig.rushKnockbackLeftRightUp;
            chase = false;
        } else if (XenoRushTechniques.RUSH_BREAKER.equals(id)) {
            distance = net.bullettrain.xenopixelsmod.config.XenoServerConfig.rushKnockbackBreaker;
            up = net.bullettrain.xenopixelsmod.config.XenoServerConfig.rushKnockbackBreakerUp;
            chase = true;
        } else if (XenoRushTechniques.RUSH_FINISHER.equals(id)) {
            distance = net.bullettrain.xenopixelsmod.config.XenoServerConfig.rushKnockbackFinisher;
            up = net.bullettrain.xenopixelsmod.config.XenoServerConfig.rushKnockbackFinisherUp;
            chase = true;
        } else {
            return;
        }

        Vec3 away = target.position().subtract(player.position());
        Vec3 look = player.getLookAngle();
        double[] impulse = net.bullettrain.xenopixelsmod.combat.combo.RushKnockbackPath.impulse(
                look.x, look.y, look.z,
                away.x, away.y, away.z,
                distance, up,
                net.bullettrain.xenopixelsmod.config.XenoServerConfig.rushKnockbackDown,
                net.bullettrain.xenopixelsmod.config.XenoServerConfig.rushKnockbackVerticalPitch,
                player.getXRot());
        CombatKnockback.add(target, new Vec3(impulse[0], impulse[1], impulse[2]));
        if (chase) {
            ChaseFlightSystem.startAutomatic(player, target);
        }
    }
}
