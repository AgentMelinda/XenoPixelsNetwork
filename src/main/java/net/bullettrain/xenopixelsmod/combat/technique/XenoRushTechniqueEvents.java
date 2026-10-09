package net.bullettrain.xenopixelsmod.combat.technique;

import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.ChaseFlightSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Adds Xeno's BT3 impact behavior to DMZ's native strike lifecycle.
 *
 * <p>DMZ remains responsible for cast validation, resource cost, cooldown, animation timing, and
 * base strike damage. This listener only adds the authored rush movement after DMZ resolves a hit.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoRushTechniqueEvents {

    /** DragonMineZ runs no strike shorter than this, whatever the strike's own duration says. */
    private static final int MIN_STRIKE_TICKS = 20;
    /** Longest a strike is waited on before a pending chase is started regardless. */
    private static final int MAX_STRIKE_TICKS = 120;

    /** A breaker's or finisher's automatic chase, waiting for DragonMineZ to let the fighters go. */
    private record PendingChase(int targetId, int startTick) {}

    /** Legacy controller only; v2 keeps its own. Server thread only. */
    private static final Map<UUID, PendingChase> PENDING_CHASE = new HashMap<>();

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
        String id = strike.getId();
        if (!XenoRushTechniques.isRushId(id)) return;

        // The view is the attacker's own for the length of the strike, whichever controller is
        // running and whatever they are permitted below: this is how the move is shown, not what
        // it does. DragonMineZ faces the attacker straight after this event; XenoRushStrikeView
        // takes that over for the strike noted here.
        XenoRushStrikeView.begin(player, target, Math.max(MIN_STRIKE_TICKS, strike.getDurationTicks()));

        if (!net.bullettrain.xenopixelsmod.command.XenoPermissions.hasPermission(
                player, net.bullettrain.xenopixelsmod.command.XenoPermissions.SKILL_RUSH_USE)) {
            return;
        }

        // Under the v2 controller the strike is only noted here. What it does to the target,
        // and the chase after a breaker or finisher, are applied by v2 once DragonMineZ has let
        // both fighters go; the legacy knockback and chase below do not run.
        if (net.bullettrain.xenopixelsmod.combat.v2.V2CombatServer.onRushStrike(player, target, id)) return;
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
            // Not now. DragonMineZ holds both fighters still for the whole strike, so a chase
            // started here spent the strike dragging the attacker against that hold, and had
            // usually given up by the time the target was actually sent flying. It starts when
            // the strike lets go, which is when there is something to chase.
            MinecraftServer server = player.getServer();
            if (server != null) {
                PENDING_CHASE.put(player.getUUID(), new PendingChase(target.getId(), server.getTickCount()));
            }
        }
    }

    /** Starts each waiting chase on the tick DragonMineZ releases its strike. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_CHASE.isEmpty()) return;
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, PendingChase>> it = PENDING_CHASE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingChase> entry = it.next();
            PendingChase pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            // The lock is set in the same call that fired the strike; never judge it that tick.
            if (now <= pending.startTick()) continue;
            if (strikeLocked(player) && now - pending.startTick() < MAX_STRIKE_TICKS) continue;
            it.remove();
            if (player.level().getEntity(pending.targetId()) instanceof LivingEntity target && target.isAlive()) {
                ChaseFlightSystem.startAutomatic(player, target);
            }
        }
    }

    private static boolean strikeLocked(ServerPlayer player) {
        try {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            return data != null && data.getStatus() != null && data.getStatus().isStrikeLocked();
        } catch (Throwable t) {
            return false;
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_CHASE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_CHASE.clear();
    }
}
