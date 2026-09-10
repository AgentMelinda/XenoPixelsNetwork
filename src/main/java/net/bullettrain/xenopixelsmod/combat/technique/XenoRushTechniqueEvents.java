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
        XenoRushTechniques.unlockRushTechniques(event.getPlayer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            XenoRushTechniques.unlockRushTechniques(player);
        }
    }

    @SubscribeEvent
    public static void onStrikeFire(DMZEvent.StrikeAttackFireEvent event) {
        StrikeAttackData strike = event.getStrike();
        ServerPlayer player = event.getPlayer();
        LivingEntity target = event.getTarget();
        if (strike == null || player == null || target == null || !target.isAlive()) {
            return;
        }

        String id = strike.getId();
        double horizontal;
        double upward;
        boolean chase;
        if (XenoRushTechniques.RUSH_LEFT.equals(id) || XenoRushTechniques.RUSH_RIGHT.equals(id)) {
            horizontal = 0.35;
            upward = 0.12;
            chase = false;
        } else if (XenoRushTechniques.RUSH_BREAKER.equals(id)) {
            horizontal = 0.75;
            upward = 0.85;
            chase = true;
        } else if (XenoRushTechniques.RUSH_FINISHER.equals(id)) {
            horizontal = 1.55;
            upward = 0.55;
            chase = true;
        } else {
            return;
        }

        Vec3 away = target.position().subtract(player.position());
        Vec3 flat = new Vec3(away.x, 0.0, away.z);
        if (flat.lengthSqr() < 1.0e-4) {
            flat = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
        }
        if (flat.lengthSqr() < 1.0e-4) {
            return;
        }
        CombatKnockback.add(target,
                flat.normalize().scale(horizontal).add(0.0, upward, 0.0));
        if (chase) {
            ChaseFlightSystem.startAutomatic(player, target);
        }
    }
}
