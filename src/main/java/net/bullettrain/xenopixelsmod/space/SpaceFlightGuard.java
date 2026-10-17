package net.bullettrain.xenopixelsmod.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The reason a survival player who falls out of a floorless space dimension does not get disconnected.
 *
 * <p>Vanilla's anti-fly watchdog increments a counter every tick a client reports itself floating without
 * a right to be, and disconnects at {@code getMaximumFlyingTicks(player)} — which is
 * {@code ceil(80 * 0.08 / gravity)}, so <b>exactly 80 ticks</b> at vanilla gravity. Verified against the
 * pinned 1.21.1 {@code ServerGamePacketListenerImpl} bytecode, and {@code !player.hasEffect(LEVITATION)}
 * is one of the conjunctions that has to hold for the counter to move at all. A dimension with no floor is
 * therefore unplayable for a walking player for more than about four seconds whether or not anything of
 * ours is switched on, and this mod's void rescue drops them back into exactly that: cancelled damage,
 * no ground underneath, an infinite fall that the watchdog counts.
 *
 * <p><b>Why a Levitation flag and not a {@code mayfly} grant.</b> The watchdog's other exempting
 * conjunction, {@code player.mayFly()}, is the obvious lever and the one this repo already uses for
 * temporary flight in {@code network/ChaseFlightSystem.java}. It is the wrong lever here:
 * {@code Abilities} is saved with the player, so a grant that is live at a disconnect — or at a logout the
 * restore cannot get ahead of — persists, and a survival player who logs out holding {@code mayfly} logs
 * back in able to double-tap jump <em>anywhere</em>, in any dimension, forever. That is a privilege the
 * feature does not need. The Levitation flag exempts the same counter, expires on its own, is not written
 * to the player's ability NBT, and at amplifier -1 applies no upward force. It gives a falling player
 * nothing they can steer, which is the point: this is a kick workaround shaped like the minimum thing that
 * stops the kick.
 *
 * <p><b>Why amplifier -1 is force-free, verified against the pinned 1.21.1 {@code LivingEntity}
 * bytecode.</b> The Levitation lift is applied as {@code 0.05d * (getEffect(LEVITATION).getAmplifier() +
 * 1)} added to the entity's vertical motion, and it is gated only by {@code hasEffect(LEVITATION)} —
 * which is true for any live instance regardless of amplifier. At amplifier -1 the multiplier is
 * {@code 0.05 * (-1 + 1) = 0.0}, so the effect is registered (the watchdog's
 * {@code !hasEffect(LEVITATION)} conjunct is satisfied and the counter never starts) while contributing
 * exactly zero upward force. That is the whole trick: a flag the watchdog sees but the physics does not.
 *
 * <p><b>Cost.</b> The tick walk visits only players this class has already flagged, and returns on an
 * empty map, so a server with nobody falling pays one {@code isEmpty()} check per tick. It is not a space
 * scheduler pass for that reason: the watchdog bites with {@code space.enabled} off, since the void rule
 * deliberately ignores that switch, and a pass that is not registered when space is off would leave the
 * rescue broken in exactly the configuration it exists to fix.
 *
 * <p>What remains <b>not verified</b>: whether a milk bucket or a death clears the flag mid-fall and lets
 * the counter resume before the next re-application refreshes it. The runtime pass for this class is a
 * survival player falling in {@code xenopixelsmod:voidsea} for 120 s with {@code voiddamage off} and no
 * disconnect in the log; the 40-tick re-application bounds any such lapse to well under the 80-tick kick.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SpaceFlightGuard {
    /** Ticks of exemption per application; refreshed by the walk below while the player still needs it. */
    private static final int GRACE_TICKS = 40;

    /** Players this class has flagged, so the walk never visits a player it has not taken on. */
    private static final Map<UUID, ServerPlayer> FLAGGED = new ConcurrentHashMap<>();

    private SpaceFlightGuard() {}

    /**
     * Flags one player as needing the exemption, if the switch is on and they are in a state where the
     * watchdog would count them: not creative, not a spectator, not already able to fly, and not standing
     * on anything. Called by the void rescue and by each handoff arrival, because those are the two moments
     * this mod puts a survival player in mid-air over no floor.
     */
    public static void grant(ServerPlayer player) {
        if (!XenoSpaceConfig.flightGrant()) return;
        if (!eligible(player)) return;
        FLAGGED.put(player.getUUID(), player);
        apply(player);
    }

    /** Removes the flag and the effect, restoring nothing else because nothing else was touched. */
    public static void revoke(ServerPlayer player) {
        if (FLAGGED.remove(player.getUUID()) != null) {
            player.removeEffect(MobEffects.LEVITATION);
        }
    }

    /** What "the watchdog would be counting this player right now" means, without any chart lookup. */
    private static boolean eligible(ServerPlayer player) {
        return !player.isCreative() && !player.isSpectator() && !player.mayFly() && !player.onGround();
    }

    private static void apply(ServerPlayer player) {
        // ambient + visible=false: no particles and no HUD icon. Amplifier -1 is the force-free flag.
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, GRACE_TICKS, -1, true, false));
    }

    /**
     * Keeps the exemption alive while a flagged player still needs it, and drops it the moment they do not:
     * ground under them, a dimension that is not space, flight they earned some other way, or creative.
     */
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        // Unbounded Voidsea (switch voidseaUnboundedHeight): with no floor to reach, a survival player can be airborne
        // anywhere in the Voidsea, not only after a handoff arrival or a void rescue (Codex CL-124 retest: a player joining
        // mid-air at y=301 was kicked "for floating too long" after 6.3 s). Flag every eligible Voidsea player; vanilla
        // kicks after 80 airborne ticks, so a 10-tick scan is well inside that. The walk below keeps or drops each flag.
        if (XenoSpaceConfig.voidseaUnboundedHeight() && XenoSpaceConfig.flightGrant() && server.getTickCount() % 10 == 0) {
            ServerLevel voidsea = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST));
            if (voidsea != null)
                for (ServerPlayer player : voidsea.players())
                    if (!FLAGGED.containsKey(player.getUUID()) && eligible(player)) grant(player);
        }
        if (FLAGGED.isEmpty()) return;
        // FLAGGED is a ConcurrentHashMap, so revoke's remove during this values() view is safe.
        for (ServerPlayer player : FLAGGED.values()) {
            if (player.isRemoved() || !needs(player, server)) {
                revoke(player);
                continue;
            }
            // Re-applied rather than trusted to a long duration, so the effect cannot outlive the reason.
            apply(player);
        }
    }

    /** A flagged player in a non-space dimension is nobody's problem; the faller in orbit still is. */
    private static boolean needs(ServerPlayer player, MinecraftServer server) {
        if (!eligible(player)) return false;
        if (!(player.level() instanceof ServerLevel level)) return false;
        return SpaceDimensions.isSpace(SpaceChartService.state(server).runtime.chart,
                level.dimension().location(), XenoSpaceConfig.spaceDimensions());
    }

    /**
     * A player who reconnects mid-air in a space dimension got no grant (only handoff arrival and void rescue granted),
     * so vanilla kicked them for floating (Codex CL-124 retest, rejoin at Voidsea y=301). Grant on login when needed.
     */
    @SubscribeEvent public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && needs(player, player.getServer())) grant(player);
    }

    /** A departed player's entry would otherwise hold their instance until the next stop. */
    @SubscribeEvent public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) revoke(player);
    }

    /**
     * A dimension change ends the exemption: the old level's reason for it is gone, and a player who has
     * left the floorless dimension does not need the flag. The handoff re-flags on arrival if the new
     * side needs it, so the walk never sees a crossed player between the two.
     */
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) FLAGGED.remove(player.getUUID());
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        FLAGGED.clear();
    }

    /** Echoed by {@code /xenospace handoff status} so an operator can see who is currently flagged. */
    public static String status() {
        List<String> names = new ArrayList<>(FLAGGED.size());
        for (ServerPlayer player : FLAGGED.values()) names.add(player.getScoreboardName());
        return "flight guard " + (XenoSpaceConfig.flightGrant() ? "on" : "off")
                + ", flagged=" + (names.isEmpty() ? "none" : String.join(" ", names));
    }
}
