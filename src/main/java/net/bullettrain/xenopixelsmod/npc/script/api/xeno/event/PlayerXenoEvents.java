package net.bullettrain.xenopixelsmod.npc.script.api.xeno.event;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost;
import net.bullettrain.xenopixelsmod.npc.script.PlayerScriptHost;
import net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoApiAdapters;
import net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoBlockAdapter;
import net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoContainerAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.event.CustomNPCsEvent;
import xenoapi.npcs.api.event.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Posts typed XenoAPI PlayerEvents from NeoForge events: new-style script hooks first, then Java
 * listeners, then the result is read back into the NeoForge event. The existing player hooks
 * (init, login, logout, chat) stay in PlayerScriptHost.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlayerXenoEvents {
    /** Scripted break experience, applied by the matching BlockDropsEvent of the same tick. */
    private static final Map<String, Integer> BREAK_EXP = new ConcurrentHashMap<>();
    private static final int BREAK_EXP_SWEEP_TICKS = 200;

    private PlayerXenoEvents() {}

    static boolean mainHand(InteractionHand hand) { return hand == InteractionHand.MAIN_HAND; }

    static String breakKey(String dimension, BlockPos pos, UUID player, long gameTime) {
        return dimension + "|" + pos.asLong() + "|" + player + "|" + gameTime;
    }

    static void rememberBreakExp(String key, int exp) { BREAK_EXP.put(key, exp); }
    static Integer takeBreakExp(String key) { return BREAK_EXP.remove(key); }

    /** Last tick a player's block or entity interact was posted, so the follow-up item use is not. */
    private static final Map<UUID, Long> LAST_INTERACT = new ConcurrentHashMap<>();

    /**
     * Puts a cancelled toss back as far as it fits. {@code add} inserts and returns what did not
     * fit. The snapshot is taken before scripts run and is never mutated. Returns the remainder
     * that still has to drop (empty when everything went back).
     */
    static ItemStack returnTossed(java.util.function.UnaryOperator<ItemStack> add, ItemStack snapshot) {
        ItemStack remainder = add.apply(snapshot.copy());
        return remainder == null ? ItemStack.EMPTY : remainder;
    }

    /**
     * True for the first interact a player makes in a tick. A right-click on a block or entity
     * that passes makes the client also send an item use in the same tick; that one is skipped.
     */
    static boolean firstInteract(UUID player, long tick) {
        Long last = LAST_INTERACT.put(player, tick);
        return last == null || last != tick;
    }

    private static IPlayer<?> api(ServerPlayer player) {
        return (IPlayer<?>) XenoApiAdapters.wrap(player);
    }

    /** Scripts (new hook), then Java. Returns the event for read-back, or null when unwanted. */
    private static <E extends CustomNPCsEvent> E deliver(ServerPlayer player, String hook, Supplier<E> build) {
        boolean scripted = PlayerScriptHost.hasHook(player, hook);
        if (!XenoEventDispatch.wanted(scripted)) return null;
        if (!XenoEventDispatch.enter(player.getUUID(), hook)) return null;
        try {
            E event = build.get();
            if (scripted) PlayerScriptHost.fireTyped(player, hook, event);
            return XenoEventDispatch.post(event);
        } finally {
            XenoEventDispatch.exit(player.getUUID(), hook);
        }
    }

    private static float finiteDamage(float scripted, float fallback) {
        return Float.isFinite(scripted) ? Math.max(0.0f, scripted) : fallback;
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % NpcScriptHost.TICK_INTERVAL == 0) {
            deliver(player, "tick", () -> new PlayerEvent.UpdateEvent(api(player)));
        }
    }

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !mainHand(event.getHand())) return;
        firstInteract(player.getUUID(), player.level().getGameTime());
        var typed = deliver(player, "interact",
                () -> new PlayerEvent.InteractEvent(api(player), 1, XenoApiAdapters.wrap(event.getTarget())));
        if (typed != null && typed.isCanceled()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !mainHand(event.getHand())) return;
        firstInteract(player.getUUID(), player.level().getGameTime());
        var typed = deliver(player, "interact", () -> new PlayerEvent.InteractEvent(api(player), 2,
                new XenoBlockAdapter(player.serverLevel(), event.getPos())));
        if (typed != null && typed.isCanceled()) event.setCanceled(true);
    }

    /** Right-click air with an item: NeoForge does not fire this when a block or entity is targeted. */
    @SubscribeEvent
    public static void onInteractItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !mainHand(event.getHand())) return;
        if (!firstInteract(player.getUUID(), player.level().getGameTime())) return;
        var typed = deliver(player, "interact", () -> new PlayerEvent.InteractEvent(api(player), 0, null));
        if (typed != null && typed.isCanceled()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var typed = deliver(player, "attack",
                () -> new PlayerEvent.AttackEvent(api(player), 1, XenoApiAdapters.wrap(event.getTarget())));
        if (typed != null && typed.isCanceled()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttackBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        var typed = deliver(player, "attack", () -> new PlayerEvent.AttackEvent(api(player), 2,
                new XenoBlockAdapter(player.serverLevel(), event.getPos())));
        if (typed != null && typed.isCanceled()) event.setCanceled(true);
    }

    /** Attacker's damagedEntity first, then the victim's damaged; the second sees the first's damage. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            float amount = event.getAmount();
            var typed = deliver(attacker, "damagedEntity", () -> new PlayerEvent.DamagedEntityEvent(api(attacker),
                    event.getEntity(), amount, event.getSource()));
            if (typed != null) {
                if (typed.isCanceled()) {
                    event.setCanceled(true);
                    return;
                }
                event.setAmount(finiteDamage(typed.damage, amount));
            }
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            float amount = event.getAmount();
            var typed = deliver(victim, "damaged", () -> new PlayerEvent.DamagedEvent(api(victim),
                    event.getSource().getEntity(), amount, event.getSource()));
            if (typed != null) {
                if (typed.isCanceled()) {
                    event.setCanceled(true);
                    return;
                }
                event.setAmount(finiteDamage(typed.damage, amount));
                if (typed.clearTarget && event.getSource().getEntity() instanceof Mob mob && mob.getTarget() == victim) {
                    mob.setTarget(null);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof ServerPlayer victim) {
            var typed = deliver(victim, "died",
                    () -> new PlayerEvent.DiedEvent(api(victim), event.getSource(), event.getSource().getEntity()));
            if (typed != null && typed.isCanceled()) {
                event.setCanceled(true);
                victim.setHealth(1.0f);   // a cancelled death must not repeat next tick
                return;
            }
        }
        if (event.getSource().getEntity() instanceof ServerPlayer killer && killer != event.getEntity()) {
            LivingEntity victim = event.getEntity();
            deliver(killer, "kill", () -> new PlayerEvent.KilledEntityEvent(api(killer), victim));
        }
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getEntity().getItem();
        ItemStack snapshot = stack.copy();   // what was really thrown, before any script edits the stack
        var typed = deliver(player, "toss", () -> new PlayerEvent.TossEvent(api(player), XenoApiAdapters.wrap(stack)));
        if (typed == null || !typed.isCanceled()) return;
        // CommonHooks.onPlayerTossEvent already removed the stack; a plain cancel would delete it.
        ItemStack remainder = returnTossed(s -> {
            player.getInventory().add(s);   // add() shrinks s to what did not fit
            return s;
        }, snapshot);
        player.inventoryMenu.broadcastChanges();
        if (remainder.isEmpty()) {
            event.setCanceled(true);
        } else {
            event.getEntity().setItem(remainder);   // only what did not fit drops
            XenoPixelsMod.LOGGER.info("XenoAPI toss cancel partly applied for {}: {} did not fit and dropped",
                    player.getScoreboardName(), remainder.getCount());
        }
    }

    @SubscribeEvent
    public static void onPickUp(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemEntity().getItem();
        var typed = deliver(player, "pickedUp", () -> new PlayerEvent.PickUpEvent(api(player), XenoApiAdapters.wrap(stack)));
        if (typed != null && typed.isCanceled()) event.setCanPickup(TriState.FALSE);
    }

    @SubscribeEvent
    public static void onRangedLaunch(ArrowLooseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var typed = deliver(player, "rangedLaunched", () -> new PlayerEvent.RangedLaunchedEvent(api(player)));
        if (typed != null && typed.isCanceled()) event.setCanceled(true);
    }

    /** XenoAPI's LevelUpEvent is not cancellable (verified in PlayerEvent.java); notify only. */
    @SubscribeEvent
    public static void onLevelChange(PlayerXpEvent.LevelChange event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int change = event.getLevels();
        deliver(player, "levelUp", () -> new PlayerEvent.LevelUpEvent(api(player), change));
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        int exp = event.getState().getExpDrop(level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
        var typed = deliver(player, "broken",
                () -> new PlayerEvent.BreakEvent(api(player), new XenoBlockAdapter(level, pos), exp));
        if (typed == null) return;
        if (typed.isCanceled()) {
            event.setCanceled(true);
            return;
        }
        if (typed.exp != exp) {
            rememberBreakExp(breakKey(level.dimension().location().toString(), pos, player.getUUID(), level.getGameTime()),
                    Math.max(0, typed.exp));
        }
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;
        Integer exp = takeBreakExp(breakKey(event.getLevel().dimension().location().toString(), event.getPos(),
                player.getUUID(), event.getLevel().getGameTime()));
        if (exp != null) event.setDroppedExperience(exp);
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            deliver(player, "containerOpen",
                    () -> new PlayerEvent.ContainerOpen(api(player), XenoContainerAdapter.of(event.getContainer())));
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            deliver(player, "containerClosed",
                    () -> new PlayerEvent.ContainerClosed(api(player), XenoContainerAdapter.of(event.getContainer())));
        }
    }

    /** Break-exp entries whose break dropped nothing never get taken; sweep them regularly. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % BREAK_EXP_SWEEP_TICKS == 0) BREAK_EXP.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BREAK_EXP.clear();
        LAST_INTERACT.clear();
    }
}
