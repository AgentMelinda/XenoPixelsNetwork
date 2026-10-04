package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.npc.script.api.ForgeScriptEvent;
import net.bullettrain.xenopixelsmod.npc.script.api.NativeXenoScriptApi;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptEntity;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptWorld;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcScripts;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Forge (world event) script tabs: the "Forge" page of the scripter. MyNPCs ships this page marked
 * BROKEN; ours runs a curated set of server events into one shared scope per tab.
 *
 * <p>A tab defines functions named after {@link #HOOKS}, e.g. {@code function blockBreak(event)}.
 * The argument is a {@link ForgeScriptEvent}: wrappers only, never the NeoForge event, so the
 * sandbox stays closed. Everything runs on the server thread; client-side copies of the same events
 * are ignored. A hook that calls back into itself (a {@code livingHurt} that hurts) stops at
 * {@link #MAX_DEPTH}, and a tab that keeps failing is parked until the next script save.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ForgeScriptHost {
    public static final List<String> HOOKS = List.of("init", "serverTick", "playerLogin", "playerLogout",
            "playerRespawn", "playerChangedDimension", "livingDeath", "livingHurt", "entityJoin", "blockBreak",
            "blockPlace", "serverChat", "rightClickBlock", "rightClickItem", "leftClickBlock", "entityInteract",
            "explosion", "trigger");

    static final int MAX_DEPTH = 3;

    /** Consecutive-failure counter: a tab erroring every tick must not flood the log forever. */
    static final class Breaker {
        static final int LIMIT = 20;
        private int failures;
        boolean fail() { return ++failures == LIMIT; }
        void ok() { failures = 0; }
        boolean parked() { return failures >= LIMIT; }
    }

    private record Tab(String id, NpcScriptEngine.Instance instance, Breaker breaker) {}

    private static List<Tab> tabs = List.of();
    private static int generation = Integer.MIN_VALUE;
    private static MinecraftServer builtFor;
    private static int depth;

    private ForgeScriptHost() {}

    private static List<Tab> tabs(MinecraftServer server) {
        if (generation == XenoNpcScripts.generation() && builtFor == server) return tabs;
        generation = XenoNpcScripts.generation();
        builtFor = server;
        List<Tab> built = new ArrayList<>();
        var store = XenoNpcStores.get();
        if (store != null && server != null) {
            var entries = new ArrayList<>(store.list(XenoNpcStoreCategory.FORGE_SCRIPTS));
            entries.sort(Comparator.comparing(e -> e.id()));
            for (var entry : entries) {
                if (built.size() >= NpcScriptContainer.MAX_TABS) break;
                var script = XenoNpcScripts.Script.of(entry.id(), entry.tag());
                if (!script.enabled() || script.script().isBlank()) continue;
                NpcScriptScope scope = NpcScriptScope.builder()
                        .put("world", new ScriptWorld(server.overworld()))
                        .put("XenoPixels", NativeXenoScriptApi.INSTANCE)
                        .put("XenoAPI", xenoapi.npcs.api.NpcAPI.Instance())
                        .put("log", (NpcScriptLog) line -> XenoPixelsMod.LOGGER.info(
                                "Forge script {}: {}", entry.id(), line))
                        .putString("script", entry.id())
                        .putString("scriptName", entry.id())
                        .build();
                var instance = NpcScriptEngines.forLanguage(script.language()).instantiate(script.script(), scope);
                if (!instance.ok()) {
                    XenoPixelsMod.LOGGER.error("Forge script {} load: {}", entry.id(), instance.loadResult().describe());
                    continue;
                }
                Tab tab = new Tab(entry.id(), instance, new Breaker());
                built.add(tab);
                if (instance.hasFunction("init")) {
                    call(tab, "init", new ForgeScriptEvent("init", false));
                }
            }
        }
        tabs = List.copyOf(built);
        return tabs;
    }

    /** Rebuilds after a store edit; called on the server thread. */
    public static void reload(MinecraftServer server) {
        generation = Integer.MIN_VALUE;
        tabs(server);
    }

    private static boolean wants(MinecraftServer server, String hook) {
        if (server == null || !server.isSameThread() || depth >= MAX_DEPTH) return false;
        for (Tab tab : tabs(server)) if (!tab.breaker.parked() && tab.instance.hasFunction(hook)) return true;
        return false;
    }

    private static void call(Tab tab, String hook, ForgeScriptEvent event) {
        NpcScriptResult result = tab.instance.call(hook, event);
        if (result.ok()) {
            tab.breaker.ok();
        } else if (tab.breaker.fail()) {
            XenoPixelsMod.LOGGER.error("Forge script {} {}: {} (tab paused after {} failures in a row; save it to retry)",
                    tab.id, hook, result.describe(), Breaker.LIMIT);
        } else if (!tab.breaker.parked()) {
            XenoPixelsMod.LOGGER.error("Forge script {} {}: {}", tab.id, hook, result.describe());
        }
    }

    private static ForgeScriptEvent fire(MinecraftServer server, ForgeScriptEvent event) {
        depth++;
        try {
            for (Tab tab : tabs(server)) {
                if (tab.breaker.parked() || !tab.instance.hasFunction(event.hook)) continue;
                call(tab, event.hook, event);
            }
        } finally {
            depth--;
        }
        return event;
    }

    private static ForgeScriptEvent event(String hook, boolean cancelable, Entity at) {
        ForgeScriptEvent e = new ForgeScriptEvent(hook, cancelable);
        if (at != null) {
            e.x = at.getX(); e.y = at.getY(); e.z = at.getZ();
            if (at.level() instanceof ServerLevel level) e.world = new ScriptWorld(level);
            if (at instanceof LivingEntity living) e.entity = ScriptEntity.of(living);
            if (at instanceof ServerPlayer player) e.player = ScriptEntity.of(player);
        }
        return e;
    }

    private static void atBlock(ForgeScriptEvent e, LevelAccessor level, BlockPos pos) {
        e.x = pos.getX(); e.y = pos.getY(); e.z = pos.getZ();
        if (level instanceof ServerLevel server) {
            e.world = new ScriptWorld(server);
            e.block = BuiltInRegistries.BLOCK.getKey(server.getBlockState(pos).getBlock()).toString();
        }
    }

    /** Fires {@code trigger} on the forge tabs (XenoAPI {@code IWorld.trigger} and friends). */
    public static void fireTrigger(MinecraftServer server, int id, Object[] arguments, Entity at) {
        if (!wants(server, "trigger")) return;
        ForgeScriptEvent event = event("trigger", false, at);
        event.id = id;
        event.arguments = arguments;
        fire(server, event);
    }

    // ---- events -------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!wants(server, "serverTick")) return;
        ForgeScriptEvent e = new ForgeScriptEvent("serverTick", false);
        e.world = new ScriptWorld(server.overworld());
        fire(server, e);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) { simple("playerLogin", event.getEntity()); }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { simple("playerLogout", event.getEntity()); }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) { simple("playerRespawn", event.getEntity()); }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !wants(player.getServer(), "playerChangedDimension")) return;
        ForgeScriptEvent e = event("playerChangedDimension", false, player);
        e.message = event.getTo().location().toString();
        fire(player.getServer(), e);
    }

    private static void simple(String hook, Entity entity) {
        if (!(entity instanceof ServerPlayer player) || !wants(player.getServer(), hook)) return;
        fire(player.getServer(), event(hook, false, player));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || !wants(dead.getServer(), "livingDeath")) return;
        ForgeScriptEvent e = event("livingDeath", true, dead);
        if (event.getSource().getEntity() instanceof LivingEntity killer) e.source = ScriptEntity.of(killer);
        e.message = event.getSource().getMsgId();
        if (fire(dead.getServer(), e).isCanceled()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingIncomingDamageEvent event) {
        LivingEntity hurt = event.getEntity();
        if (hurt.level().isClientSide() || !wants(hurt.getServer(), "livingHurt")) return;
        ForgeScriptEvent e = event("livingHurt", true, hurt);
        if (event.getSource().getEntity() instanceof LivingEntity attacker) e.source = ScriptEntity.of(attacker);
        e.message = event.getSource().getMsgId();
        e.damage = event.getAmount();
        fire(hurt.getServer(), e);
        if (e.isCanceled()) event.setCanceled(true);
        else if (e.damage != event.getAmount()) event.setAmount(e.damage);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onJoin(EntityJoinLevelEvent event) {
        // Chunk loads re-add saved entities; only brand-new arrivals count as joining.
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) return;
        Entity joined = event.getEntity();
        if (!wants(joined.getServer(), "entityJoin")) return;
        ForgeScriptEvent e = event("entityJoin", true, joined);
        e.message = BuiltInRegistries.ENTITY_TYPE.getKey(joined.getType()).toString();
        if (fire(joined.getServer(), e).isCanceled()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide() || !(event.getPlayer() instanceof ServerPlayer player)
                || !wants(player.getServer(), "blockBreak")) return;
        ForgeScriptEvent e = event("blockBreak", true, player);
        atBlock(e, event.getLevel(), event.getPos());
        if (fire(player.getServer(), e).isCanceled()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)
                || !wants(player.getServer(), "blockPlace")) return;
        ForgeScriptEvent e = event("blockPlace", true, player);
        atBlock(e, event.getLevel(), event.getPos());
        e.block = BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).toString();
        if (fire(player.getServer(), e).isCanceled()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        MinecraftServer server = player == null ? null : player.getServer();
        if (server == null) return;
        String text = event.getRawText();
        if (!server.isSameThread()) {
            // Chat can arrive off-thread; scripts then see it one task later and cannot cancel it.
            server.execute(() -> {
                if (wants(server, "serverChat")) {
                    ForgeScriptEvent e = event("serverChat", false, player);
                    e.message = text;
                    fire(server, e);
                }
            });
            return;
        }
        if (!wants(server, "serverChat")) return;
        ForgeScriptEvent e = event("serverChat", true, player);
        e.message = text;
        if (fire(server, e).isCanceled()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        clicked("rightClickBlock", event, event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        clicked("leftClickBlock", event, event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        clicked("rightClickItem", event, null);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !wants(player.getServer(), "entityInteract")) return;
        ForgeScriptEvent e = event("entityInteract", true, player);
        if (event.getTarget() instanceof LivingEntity target) e.entity = ScriptEntity.of(target);
        e.message = BuiltInRegistries.ENTITY_TYPE.getKey(event.getTarget().getType()).toString();
        e.item = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString();
        if (fire(player.getServer(), e).isCanceled()) event.setCanceled(true);
    }

    private static void clicked(String hook, PlayerInteractEvent event, BlockPos pos) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !wants(player.getServer(), hook)) return;
        ForgeScriptEvent e = event(hook, event instanceof net.neoforged.bus.api.ICancellableEvent, player);
        if (pos != null) atBlock(e, event.getLevel(), pos);
        e.item = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString();
        if (fire(player.getServer(), e).isCanceled()
                && event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onExplosion(ExplosionEvent.Start event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !wants(level.getServer(), "explosion")) return;
        ForgeScriptEvent e = new ForgeScriptEvent("explosion", true);
        e.world = new ScriptWorld(level);
        var center = event.getExplosion().center();
        e.x = center.x; e.y = center.y; e.z = center.z;
        if (event.getExplosion().getIndirectSourceEntity() instanceof LivingEntity source) {
            e.source = ScriptEntity.of(source);
        }
        if (fire(level.getServer(), e).isCanceled()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) {
        tabs = List.of();
        builtFor = null;
        generation = Integer.MIN_VALUE;
        depth = 0;
    }
}
