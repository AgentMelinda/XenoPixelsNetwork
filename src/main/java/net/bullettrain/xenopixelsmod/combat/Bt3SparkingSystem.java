package net.bullettrain.xenopixelsmod.combat;

import net.neoforged.fml.common.EventBusSubscriber;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.dmz.SparkingSkill;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.SparkingChargePacket;
import net.bullettrain.xenopixelsmod.network.packet.SparkingStatePacket;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BT3 Sparking-style meter: build on hits, activate for temporary damage buff + i-frame frames on dash.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class Bt3SparkingSystem {
    private static final Map<UUID, Float> METER = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE_UNTIL = new HashMap<>();
    private static final Map<UUID, Integer> IFRAMES_UNTIL = new HashMap<>();
    /** The player's own release ceiling and release, parked while Sparking lifts them. */
    private static final Map<UUID, int[]> RELEASE_RESTORE = new HashMap<>();
    /** Server tick each player's Sparking cooldown expires on. */
    private static final Map<UUID, Integer> COOLDOWN_UNTIL = new HashMap<>();
    /** Full-ki Max Power charge ticks, present only while a committed charge is running. */
    private static final Map<UUID, Integer> KI_CHARGE_TICKS = new HashMap<>();
    /** Last segment count sent to the owner, so the charge costs at most nine packets. */
    private static final Map<UUID, Integer> KI_CHARGE_SEGMENTS = new HashMap<>();

    private static final ResourceLocation MOVE_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "sparking_move");
    private static final ResourceLocation ATTACK_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "sparking_attack");

    private Bt3SparkingSystem() {}

    /**
     * Global monotonic server tick — the one clock every window is written, read, and pruned
     * against. {@code player.tickCount} is per-entity and is small for a player who just joined a
     * long-running server, so the old per-entity stamps expired on the first server-tick prune
     * (the 100-tick buff vanished instantly for newer players). Defensive for client callers.
     */
    private static int serverTick(ServerPlayer p) {
        if (p == null || p.level() == null) return 0;
        MinecraftServer server = p.level().getServer();
        return server != null ? server.getTickCount() : 0;
    }

    public static float getMeter(UUID id) {
        return METER.getOrDefault(id, 0f);
    }

    public static boolean isSparking(ServerPlayer player) {
        if (player == null) return false;
        Integer until = ACTIVE_UNTIL.get(player.getUUID());
        return until != null && serverTick(player) < until;
    }

    /** Ticks left on active Sparking, or 0 if inactive. */
    public static int remainingSparkingTicks(ServerPlayer player) {
        if (player == null) return 0;
        Integer until = ACTIVE_UNTIL.get(player.getUUID());
        if (until == null) return 0;
        return Math.max(0, until - serverTick(player));
    }

    /**
     * Tells everyone who can see this player whether they are Sparking.
     *
     * <p>Sent because mob effects reach only the player who holds them, never the clients tracking
     * them, so without this the aura would be invisible on everybody else.
     */
    private static void broadcastState(ServerPlayer player, boolean sparking) {
        try {
            ModNetwork.sendToTrackingAndSelf(player, new SparkingStatePacket(player.getId(), sparking));
        } catch (Throwable ignored) {
        }
    }

    private static void syncCharge(ServerPlayer player, boolean charging, int litSegments) {
        try {
            ModNetwork.sendToPlayer(player, new SparkingChargePacket(charging, litSegments));
        } catch (Throwable ignored) {
        }
    }

    private static void clearCharge(ServerPlayer player) {
        if (player == null) return;
        UUID id = player.getUUID();
        boolean visible = KI_CHARGE_TICKS.remove(id) != null;
        visible |= KI_CHARGE_SEGMENTS.remove(id) != null;
        if (visible) syncCharge(player, false, 0);
    }

    /** Ticks left before Sparking can be entered again, or 0. */
    public static int remainingCooldownTicks(ServerPlayer player) {
        if (player == null) return 0;
        Integer until = COOLDOWN_UNTIL.get(player.getUUID());
        if (until == null) return 0;
        return Math.max(0, until - serverTick(player));
    }

    public static boolean hasIFrames(ServerPlayer player) {
        if (player == null) return false;
        Integer until = IFRAMES_UNTIL.get(player.getUUID());
        return until != null && serverTick(player) < until;
    }

    public static void grantIFrames(ServerPlayer player, int ticks) {
        if (player == null) return;
        IFRAMES_UNTIL.put(player.getUUID(), serverTick(player) + Math.max(1, ticks));
    }

    public static void addMeter(ServerPlayer player, float amount) {
        if (player == null || !XenoServerConfig.bt3SparkingEnabled) return;
        if (amount <= 0f) return;
        float cur = getMeter(player.getUUID());
        METER.put(player.getUUID(), Math.min(100f, cur + amount));
    }

    /** @return true if activated */
    public static boolean tryActivate(ServerPlayer player) {
        if (player == null || !XenoServerConfig.bt3SparkingEnabled) return false;
        if (isSparking(player)) return false;
        if (XenoServerConfig.sparkingFromKiCharge) {
            // Direct activation remains available behind /xenobind, but charge-mode Sparking now
            // requires the full Max Power stage rather than merely reaching normal full ki.
            if (!energyFull(player)) {
                player.displayClientMessage(Component.literal(
                        "§7Sparking: charge your ki to full"), true);
                return false;
            }
            player.displayClientMessage(Component.literal(
                    "§7Sparking: keep charging to Max Power"), true);
            return false;
        }
        float m = getMeter(player.getUUID());
        if (m < 100f) {
            player.displayClientMessage(Component.literal(
                    "§7Sparking: " + Math.round(m) + "% — need full meter"), true);
            return false;
        }
        METER.put(player.getUUID(), 0f);
        return activate(player, Math.max(20, XenoServerConfig.sparkingDurationTicks));
    }

    /**
     * How long a ki-charge Sparking is allowed to run before the tick drain has certainly ended it.
     *
     * <p>Not a duration in any meaningful sense — the ki bar decides when Sparking stops. It exists
     * because the active window is stored as an absolute tick, and a real "forever" would have to
     * be {@code Integer.MAX_VALUE}, which overflows the moment it is added to the server clock.
     */
    private static final int KI_MODE_MAX_TICKS = 72000;

    private static boolean activate(ServerPlayer player, int durationTicks) {
        if (!SparkingSkill.has(player)) {
            player.displayClientMessage(Component.literal(
                    "§7Sparking: learn it from a master, or max Potential Unlock"), true);
            return false;
        }
        int cd = remainingCooldownTicks(player);
        if (cd > 0) {
            player.displayClientMessage(Component.literal(
                    "§7Sparking: " + Math.max(1, cd / 20) + "s cooldown"), true);
            return false;
        }
        int dur = Math.min(KI_MODE_MAX_TICKS, Math.max(20, durationTicks));
        liftRelease(player);
        applySpeed(player);
        ACTIVE_UNTIL.put(player.getUUID(), serverTick(player) + dur);
        broadcastState(player, true);
        player.displayClientMessage(Component.literal("§6§lSPARKING!"), true);
        // Inventory (E) + HUD status icon
        try {
            net.bullettrain.xenopixelsmod.effect.XenoStatusEffectSync.ensure(
                    player,
                    net.bullettrain.xenopixelsmod.effect.ModEffects.SPARKING,
                    0,
                    dur);
            net.bullettrain.xenopixelsmod.effect.XenoStatusEffectSync.remove(
                    player,
                    net.bullettrain.xenopixelsmod.effect.ModEffects.SPARKING_READY);
        } catch (Throwable ignored) {
        }
        return true;
    }

    /** Ends Sparking now, whatever put it up. */
    public static void deactivate(ServerPlayer player) {
        if (player == null) return;
        if (ACTIVE_UNTIL.remove(player.getUUID()) == null) return;
        restoreRelease(player);
        clearSpeed(player);
        broadcastState(player, false);
        int cd = Math.max(0, XenoServerConfig.sparkingCooldownTicks);
        if (cd > 0) COOLDOWN_UNTIL.put(player.getUUID(), serverTick(player) + cd);
        try {
            net.bullettrain.xenopixelsmod.effect.XenoStatusEffectSync.remove(
                    player, net.bullettrain.xenopixelsmod.effect.ModEffects.SPARKING);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Raises the player's release ceiling for the duration of Sparking.
     *
     * <p>DragonMineZ caps release at {@code 50 + potentialunlock_level * 5}, so even a maxed player
     * sits at 115. Only the ceiling is raised here: DMZ's own tick handler ramps power release
     * toward whatever the limit is, so the climb still costs the player the time it normally does.
     *
     * <p>The previous values are parked rather than recomputed, because the ceiling is a stored
     * field DMZ only rewrites when the player changes it themselves — there is nothing to derive
     * it back from once it has been overwritten.
     */
    private static void liftRelease(ServerPlayer player) {
        Resources res = resourcesOf(player);
        if (res == null) return;
        int target = Math.max(1, XenoServerConfig.sparkingReleaseLimit);
        RELEASE_RESTORE.putIfAbsent(player.getUUID(),
                new int[]{res.getReleaseLimit(), res.getPowerRelease()});
        if (res.getReleaseLimit() < target) res.setReleaseLimit(target);
    }

    /**
     * Puts the player's own ceiling back.
     *
     * <p>Every exit has to reach this — the drain, a manual end, and logout — or a player keeps the
     * Sparking ceiling for free. Release itself is clamped back down too, since DMZ leaves it where
     * it was and it would otherwise sit above the restored limit.
     */
    private static void restoreRelease(ServerPlayer player) {
        int[] saved = RELEASE_RESTORE.remove(player.getUUID());
        if (saved == null) return;
        Resources res = resourcesOf(player);
        if (res == null) return;
        res.setReleaseLimit(saved[0]);
        if (res.getPowerRelease() > saved[0]) res.setPowerRelease(Math.max(0, saved[0]));
    }

    /**
     * Ki spent per tick so that a full bar lasts exactly {@code durationTicks}.
     *
     * <p>One configured duration therefore sets the drain speed as well, which is what makes the
     * bar the timer: a player who spends ki on techniques mid-Sparking gets a correspondingly
     * shorter Sparking rather than the full window regardless.
     */
    public static float drainPerTick(float maxEnergy, int durationTicks) {
        if (maxEnergy <= 0f) return 0f;
        return maxEnergy / Math.max(1, durationTicks);
    }

    /**
     * Makes the player move and swing faster while Sparking.
     *
     * <p>Attribute modifiers rather than raw speed writes, so the boost stacks correctly with
     * anything else touching the same attributes and disappears cleanly when it is removed —
     * including if the player logs out mid-Sparking, since the modifier is not persisted.
     */
    private static void applySpeed(ServerPlayer player) {
        modifier(player, Attributes.MOVEMENT_SPEED, MOVE_MODIFIER,
                XenoServerConfig.sparkingMoveSpeedMult);
        modifier(player, Attributes.ATTACK_SPEED, ATTACK_MODIFIER,
                XenoServerConfig.sparkingAttackSpeedMult);
    }

    private static void clearSpeed(ServerPlayer player) {
        remove(player, Attributes.MOVEMENT_SPEED, MOVE_MODIFIER);
        remove(player, Attributes.ATTACK_SPEED, ATTACK_MODIFIER);
    }

    private static void modifier(ServerPlayer player,
                                 net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                 ResourceLocation id, float multiplier) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (multiplier <= 1f) return;
        instance.addTransientModifier(new AttributeModifier(id, multiplier - 1f,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void remove(ServerPlayer player,
                               net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                               ResourceLocation id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.removeModifier(id);
    }

    private static Resources resourcesOf(ServerPlayer player) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        return data != null ? data.getResources() : null;
    }

    private static float maxEnergyOf(ServerPlayer player) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        return data != null ? data.getMaxEnergy() : 0f;
    }

    private static boolean energyFull(ServerPlayer player) {
        Resources res = resourcesOf(player);
        float max = maxEnergyOf(player);
        return res != null && max > 0f && res.getCurrentEnergy() >= max - 0.5f;
    }

    /**
     * Builds the committed full-ki charge, then spends the bar while Sparking is up.
     *
     * <p>This is what "lasts until the blue ki drains" means: the ki bar is the timer, so a player
     * who charged to full and immediately spent their ki on techniques gets a short Sparking, and
     * one who holds it gets the whole bar's worth.
     */
    @SubscribeEvent
    public static void onPlayerTickSparking(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!XenoServerConfig.bt3SparkingEnabled || !XenoServerConfig.sparkingFromKiCharge) {
            clearCharge(player);
            return;
        }

        if (!isSparking(player)) {
            tickFullKiCharge(player);
            return;
        }

        clearCharge(player);

        Resources res = resourcesOf(player);
        float max = maxEnergyOf(player);
        if (res == null || max <= 0f) {
            deactivate(player);
            return;
        }
        float drain = drainPerTick(max, XenoServerConfig.sparkingDurationTicks);
        if (drain > 0f) res.removeEnergy(drain);
        if (res.getCurrentEnergy() <= max * XenoServerConfig.sparkingEndEnergyFraction) {
            deactivate(player);
        }
    }

    private static void tickFullKiCharge(ServerPlayer player) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        boolean eligible = data != null
                && data.getStatus().isChargingKi()
                && energyFull(player)
                && SparkingSkill.has(player)
                && remainingCooldownTicks(player) <= 0;
        if (!eligible) {
            clearCharge(player);
            return;
        }

        UUID id = player.getUUID();
        boolean starting = !KI_CHARGE_TICKS.containsKey(id);
        int ticks = Bt3SparkingCharge.advance(KI_CHARGE_TICKS.getOrDefault(id, 0),
                XenoServerConfig.sparkingChargeTicks);
        KI_CHARGE_TICKS.put(id, ticks);

        if (starting) {
            KI_CHARGE_SEGMENTS.put(id, 0);
            syncCharge(player, true, 0);
        }

        int litSegments = Bt3SparkingCharge.litSegments(ticks, XenoServerConfig.sparkingChargeTicks);
        int previous = KI_CHARGE_SEGMENTS.getOrDefault(id, 0);
        if (litSegments != previous) {
            KI_CHARGE_SEGMENTS.put(id, litSegments);
            syncCharge(player, true, litSegments);
        }

        if (Bt3SparkingCharge.complete(ticks, XenoServerConfig.sparkingChargeTicks)) {
            activate(player, KI_MODE_MAX_TICKS);
            clearCharge(player);
        }
    }

    public static float damageMult(ServerPlayer player) {
        return isSparking(player) ? Math.max(1f, XenoServerConfig.sparkingDamageMult) : 1f;
    }

    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Pre event) {
        if (!XenoServerConfig.bt3CombatEnabled || !XenoServerConfig.bt3SparkingEnabled) return;

        // i-frames during sonic sway / sparking dash
        if (event.getEntity() instanceof ServerPlayer def && hasIFrames(def)) {
            event.setNewDamage(0f);
            return;
        }

        if (event.getSource().getEntity() instanceof ServerPlayer atk) {
            addMeter(atk, XenoServerConfig.sparkingBuildPerHit);
            // Apply sparking damage mult
            float mult = damageMult(atk);
            if (mult > 1.001f) {
                event.setNewDamage(event.getNewDamage() * mult);
            }
        }
        if (event.getEntity() instanceof ServerPlayer def && event.getNewDamage() > 0.05f) {
            addMeter(def, XenoServerConfig.sparkingBuildOnHurt);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int t = server.getTickCount();
        if (t % 20 != 0) return;

        // Sparking is granted the moment Potential Unlock is maxed. Checked here, on the second,
        // rather than from a skill-change event: DMZ raises the level from several places (a
        // master, a command, an admin edit) and this catches all of them for the price of one
        // integer compare per player.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (SparkingSkill.grantIfEligible(player)) {
                player.displayClientMessage(Component.literal(
                        "§6§lSparking unlocked!§r §7Fill your ki, then keep charging to Max Power."), false);
            }
        }
        // Expiry goes through deactivate rather than dropping the entry, so the release ceiling
        // Sparking raised is always put back. Dropping it silently left the player at the Sparking
        // limit for good.
        for (UUID id : List.copyOf(ACTIVE_UNTIL.keySet())) {
            Integer until = ACTIVE_UNTIL.get(id);
            if (until == null || until >= t) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                deactivate(player);
            } else {
                ACTIVE_UNTIL.remove(id);
                RELEASE_RESTORE.remove(id);
        COOLDOWN_UNTIL.remove(id);
            }
        }
        IFRAMES_UNTIL.entrySet().removeIf(e -> e.getValue() < t - 5);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player p = event.getEntity();
        if (p == null) return;
        UUID id = p.getUUID();
        if (p instanceof ServerPlayer sp) {
            restoreRelease(sp);
            clearSpeed(sp);
        }
        METER.remove(id);
        ACTIVE_UNTIL.remove(id);
        IFRAMES_UNTIL.remove(id);
        RELEASE_RESTORE.remove(id);
        COOLDOWN_UNTIL.remove(id);
        KI_CHARGE_TICKS.remove(id);
        KI_CHARGE_SEGMENTS.remove(id);
    }
}
