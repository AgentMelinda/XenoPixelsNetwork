package net.bullettrain.xenopixelsmod.combat;

import net.neoforged.fml.common.EventBusSubscriber;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side BT3 phase-1: guard state, STM drain, super-counter windows.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class Bt3CombatEvents {
    // Forge events and handled packets mutate these on the server thread.
    private static final Map<UUID, Long> GUARDING = new HashMap<>();
    private static final Map<UUID, Integer> COUNTER_UNTIL_TICK = new HashMap<>();
    private static final Map<UUID, Integer> GUARD_STUN_UNTIL = new HashMap<>();
    /** Zanzoken press windows and cooldowns, keyed the same way the counter window is. */
    private static final Map<UUID, Integer> ZANZOKEN_UNTIL_TICK = new HashMap<>();
    private static final Map<UUID, Integer> ZANZOKEN_READY_TICK = new HashMap<>();
    /**
     * While this is in the future, this player's afterimages are still on screen.
     *
     * <p>Separate from the dodge window on purpose. The window closes on the first hit that tests
     * it, but the images outlive that -- and it is the images, not the read, that an onlooker or an
     * AI is being fooled by. Tying the confusion to the window would end it the instant something
     * swung, which is the opposite of what the technique is for.
     */
    private static final Map<UUID, Integer> ZANZOKEN_IMAGES_UNTIL = new HashMap<>();
    /** Grace after a chase drops the fighter mid-air, so the landing does not kill them. */
    private static final Map<UUID, Integer> FALL_GRACE_UNTIL = new HashMap<>();

    private Bt3CombatEvents() {}

    /**
     * Global monotonic server tick. All window timestamps and prunes must use this one clock:
     * {@code player.tickCount} is per-entity (small for a player who just joined a long-running
     * server), so mixing it with the server-tick prune silently expired guard/counter/stun
     * windows for newer players on the first prune. Defensive for client-side callers (server
     * null → 0); on the client the maps are empty so these read as inactive either way.
     */
    private static int serverTick(LivingEntity e) {
        if (e == null || e.level() == null) return 0;
        MinecraftServer server = e.level().getServer();
        return server != null ? server.getTickCount() : 0;
    }

    public static void setGuarding(ServerPlayer player, boolean on) {
        if (player == null) return;
        if (on) {
            GUARDING.put(player.getUUID(), (long) serverTick(player));
        } else {
            GUARDING.remove(player.getUUID());
        }
    }

    public static boolean isGuarding(ServerPlayer player) {
        return player != null && GUARDING.containsKey(player.getUUID());
    }

    public static boolean isGuardStunned(ServerPlayer player) {
        if (player == null) return false;
        Integer until = GUARD_STUN_UNTIL.get(player.getUUID());
        return until != null && serverTick(player) < until;
    }

    public static void openCounterWindow(ServerPlayer player) {
        if (player == null || !XenoServerConfig.bt3SuperCounterEnabled) return;
        int until = serverTick(player) + Math.max(4, XenoServerConfig.superCounterWindowTicks);
        COUNTER_UNTIL_TICK.put(player.getUUID(), until);
    }

    public static boolean consumeCounterWindow(ServerPlayer player) {
        if (player == null) return false;
        Integer until = COUNTER_UNTIL_TICK.get(player.getUUID());
        int now = serverTick(player);
        if (until == null || now > until) {
            COUNTER_UNTIL_TICK.remove(player.getUUID());
            return false;
        }
        COUNTER_UNTIL_TICK.remove(player.getUUID());
        return true;
    }

    /** Opens a Zanzoken read. Returns false when the technique is still cooling down. */
    public static boolean openDodgeWindow(ServerPlayer player) {
        if (player == null || !XenoServerConfig.zanzokenEnabled) return false;
        int now = serverTick(player);
        if (!ZanzokenWindow.ready(now, ZANZOKEN_READY_TICK.get(player.getUUID()))) return false;
        ZANZOKEN_UNTIL_TICK.put(player.getUUID(),
                now + Math.max(1, XenoServerConfig.zanzokenWindowTicks));
        ZANZOKEN_READY_TICK.put(player.getUUID(),
                now + Math.max(0, XenoServerConfig.zanzokenCooldownTicks));
        return true;
    }

    /** Marks this player's afterimages as on screen for {@code ticks} from now. */
    public static void markAfterimages(ServerPlayer player, int ticks) {
        if (player == null || ticks <= 0) return;
        ZANZOKEN_IMAGES_UNTIL.put(player.getUUID(), serverTick(player) + ticks);
    }

    /**
     * The images are gone before their time was up.
     *
     * <p>Striking any image drops the whole ring, well short of the lifetime it was marked for.
     * Without this the mark outlived the bodies, and for the remainder of that lifetime nothing
     * could acquire the fighter even though there was plainly nothing left to be fooled by — a
     * fighter who had already been found stayed untargetable. The mark describes what is on screen,
     * so it has to end when that does.
     */
    public static void clearAfterimages(UUID ownerId) {
        if (ownerId == null) return;
        ZANZOKEN_IMAGES_UNTIL.remove(ownerId);
    }

    /** Whether this player currently has afterimages standing in for them. */
    public static boolean afterimagesActive(ServerPlayer player) {
        if (player == null) return false;
        Integer until = ZANZOKEN_IMAGES_UNTIL.get(player.getUUID());
        // Reuses the window's own staleness rule, so a restarted server clock cannot leave a
        // player permanently untargetable.
        if (until == null || !ZanzokenWindow.armed(serverTick(player), until)) {
            ZANZOKEN_IMAGES_UNTIL.remove(player.getUUID());
            return false;
        }
        return true;
    }

    /** True once, for a hit that arrived inside a live window. Closes the window either way. */
    public static boolean consumeDodgeWindow(ServerPlayer player) {
        if (player == null) return false;
        boolean armed = ZanzokenWindow.armed(serverTick(player),
                ZANZOKEN_UNTIL_TICK.get(player.getUUID()));
        ZANZOKEN_UNTIL_TICK.remove(player.getUUID());
        return armed;
    }

    /**
     * Every map in this class is keyed on {@code MinecraftServer.getTickCount()}, which restarts
     * at zero on each world load, while the maps themselves are static and outlive the server in a
     * single-player client. A cooldown stamped late in one session therefore sat far ahead of the
     * next session's clock and refused the technique until the new server caught up — Zanzoken
     * reported "not ready" for as long as the previous session had run. Super-counter windows and
     * guard stuns are stamped from the same clock and had the same latent fault.
     */
    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        GUARDING.clear();
        COUNTER_UNTIL_TICK.clear();
        GUARD_STUN_UNTIL.clear();
        ZANZOKEN_UNTIL_TICK.clear();
        ZANZOKEN_READY_TICK.clear();
        ZANZOKEN_IMAGES_UNTIL.clear();
        FALL_GRACE_UNTIL.clear();
    }

    /** A rejoin inside one server's life starts clean too. */
    @SubscribeEvent
    public static void onLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        GUARDING.remove(id);
        COUNTER_UNTIL_TICK.remove(id);
        GUARD_STUN_UNTIL.remove(id);
        ZANZOKEN_UNTIL_TICK.remove(id);
        ZANZOKEN_READY_TICK.remove(id);
        ZANZOKEN_IMAGES_UNTIL.remove(id);
        FALL_GRACE_UNTIL.remove(id);
    }

    /**
     * Waives fall damage briefly.
     *
     * <p>A chase ends wherever it ends, often high above the ground: gravity comes straight back
     * and the fighter drops from altitude with nothing between them and the floor. Players were
     * dying to the landing rather than to anything in the fight, which is not how any of this is
     * supposed to read — fighters in this genre do not die to the ground.
     */
    public static void grantFallGrace(ServerPlayer player, int ticks) {
        if (player == null || ticks <= 0) return;
        FALL_GRACE_UNTIL.put(player.getUUID(), serverTick(player) + ticks);
    }

    private static boolean hasFallGrace(ServerPlayer player) {
        Integer until = FALL_GRACE_UNTIL.get(player.getUUID());
        if (until == null) return false;
        int now = serverTick(player);
        // Same staleness guard the Zanzoken window uses: the tick clock restarts on world load.
        if (ZanzokenWindow.stale(now, until)) {
            FALL_GRACE_UNTIL.remove(player.getUUID());
            return false;
        }
        return now <= until;
    }

    public static boolean hasCounterWindow(ServerPlayer player) {
        if (player == null) return false;
        Integer until = COUNTER_UNTIL_TICK.get(player.getUUID());
        return until != null && serverTick(player) <= until;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer defender)) return;
        if (!XenoServerConfig.bt3CombatEnabled) return;

        // Super-counter window after any hit (if enabled)
        if (XenoServerConfig.bt3SuperCounterEnabled && event.getNewDamage() > 0.05f) {
            openCounterWindow(defender);
        }

        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL) && hasFallGrace(defender)) {
            FALL_GRACE_UNTIL.remove(defender.getUUID());
            event.setNewDamage(0f);
            return;
        }

        // Zanzoken resolves before guard: an image cannot be blocked through, and a fighter who
        // read the swing correctly should not also be charged stamina for a block.
        if (ZanzokenWindow.dodges(XenoServerConfig.zanzokenEnabled,
                ZanzokenWindow.armed(serverTick(defender), ZANZOKEN_UNTIL_TICK.get(defender.getUUID())),
                event.getSource().getEntity() instanceof LivingEntity,
                event.getNewDamage() > 0.05f)) {
            consumeDodgeWindow(defender);
            event.setNewDamage(0f);
            if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                net.bullettrain.xenopixelsmod.network.Bt3CombatPacket.performZanzoken(defender, attacker);
            }
            return;
        }

        if (!XenoServerConfig.bt3GuardEnabled || !isGuarding(defender)) return;
        if (isGuardStunned(defender)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;

        Resources res = readResources(defender);
        float cost = XenoServerConfig.guardStaminaPerHit;
        if (res != null && res.getCurrentStamina() < cost * 0.5f) {
            // Guard break
            setGuarding(defender, false);
            DmzAnimHelper.broadcastBlockStop(defender);
            int stun = Math.max(5, XenoServerConfig.guardBreakStunTicks);
            GUARD_STUN_UNTIL.put(defender.getUUID(), serverTick(defender) + stun);
            defender.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 2, false, true));
            defender.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stun, 0, false, true));
            defender.displayClientMessage(Component.literal("§cGUARD BREAK"), true);
            return;
        }
        if (res != null) {
            res.removeStamina(Math.min(res.getCurrentStamina(), cost));
        }
        float red = Math.max(0f, Math.min(0.95f, XenoServerConfig.guardDamageReduction
                + net.bullettrain.xenopixelsmod.features.progression.CombatSkills.guardBonus(defender)));
        event.setNewDamage(event.getNewDamage() * (1f - red));

        // Cyan push-back facing the attacker, so a blocked hit is visibly different from one
        // that landed. Without this the only feedback for a successful guard is damage the
        // defender never sees, which is why blocking read as doing nothing.
        if (defender.level() instanceof net.minecraft.server.level.ServerLevel sl
                && event.getSource().getEntity() instanceof LivingEntity attacker) {
            net.bullettrain.xenopixelsmod.combat.fx.CombatFx.impact(sl, defender,
                    defender.position().subtract(attacker.position()),
                    net.bullettrain.xenopixelsmod.combat.fx.CombatFx.Weight.GUARD);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!XenoServerConfig.bt3CombatEnabled || !XenoServerConfig.bt3GuardEnabled) return;
        int serverTick = event.getServer().getTickCount();

        // Entries drain once per second; polling five times per second preserves timing
        // without resolving every guarding UUID on every server tick.
        if (serverTick % 4 == 0 && !GUARDING.isEmpty()) {
            Iterator<Map.Entry<UUID, Long>> it = GUARDING.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> e = it.next();
                ServerPlayer p = event.getServer().getPlayerList().getPlayer(e.getKey());
                if (p == null || !p.isAlive()) {
                    it.remove();
                    continue;
                }
                long now = serverTick(p);
                long last = e.getValue();
                if (now - last < 20) continue;
                e.setValue(now);
                Resources res = readResources(p);
                float drain = XenoServerConfig.guardStaminaPerSec;
                if (res != null && drain > 0f) {
                    if (res.getCurrentStamina() < drain) {
                        it.remove();
                        DmzAnimHelper.broadcastBlockStop(p);
                        p.displayClientMessage(Component.literal("§eGuard dropped — low stamina"), true);
                    } else {
                        res.removeStamina(drain);
                    }
                }
            }
        }

        // These are queried lazily by exact UUID; bulk pruning once per second is enough.
        if (serverTick % 20 == 0) {
            COUNTER_UNTIL_TICK.entrySet().removeIf(en -> en.getValue() < serverTick - 40);
            GUARD_STUN_UNTIL.entrySet().removeIf(en -> en.getValue() < serverTick - 40);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            UUID id = p.getUUID();
            GUARDING.remove(id);
            COUNTER_UNTIL_TICK.remove(id);
            GUARD_STUN_UNTIL.remove(id);
            KiDeflect.forget(id);
        }
    }

    private static Resources readResources(ServerPlayer player) {
        try {
            var opt = StatsProvider.get(StatsCapability.INSTANCE, player);
            StatsData data = opt.orElse(null);
            return data != null ? data.getResources() : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
