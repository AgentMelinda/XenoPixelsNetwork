package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptEntity;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptEvent;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptNpc;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptTimers;
import net.bullettrain.xenopixelsmod.npc.script.api.NativeXenoScriptApi;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcScripts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs an NPC's script tabs on its events, the way CustomNPCs does: each tab is evaluated once
 * into its own globals ({@link NpcScriptEngine.Instance}), then a hook such as {@code interact} is
 * called on every tab that defines a function of that name, with a {@link ScriptEvent}.
 *
 * <p>Instances are rebuilt when the NPC's container changes or any stored script is written
 * ({@link XenoNpcScripts#generation}). {@code init} fires once per build. Everything here runs on
 * the server thread; the client never evaluates a script.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcScriptHost {
    /** The hooks this runtime fires, each from a real call site on {@link XenoNpcEntity}. */
    public static final List<String> HOOKS = List.of(
            "init", "tick", "interact", "damaged", "died", "kill", "target", "targetLost",
            "collide", "meleeAttack", "rangedLaunched", "timer", "dialog", "dialogOption", "rangedAttack",
            "trigger");

    /** Bindings every tab gets, for the screen's Functions panel. */
    public static final List<String> BINDINGS = List.of(
            "npc", "world", "log", "script", "scriptName", "XenoPixels", "XenoAPI",
            "event (hook argument)");

    /** tick fires every this many server ticks, as CustomNPCs' default. */
    public static final int TICK_INTERVAL = 10;
    private static final int CONSOLE_LINES = 64;
    private static final int ERROR_CHAT_INTERVAL_TICKS = 100;
    private static final double ERROR_CHAT_RANGE_SQ = 64.0 * 64.0;

    private static final Map<UUID, Host> HOSTS = new ConcurrentHashMap<>();
    /** State handed out before this NPC's first host exists; the first build adopts it. */
    private static final Map<UUID, SharedState> PENDING = new ConcurrentHashMap<>();
    /** Contract hook names that differ from a native one; the typed event goes to both. */
    private static final Map<String, String> TYPED_ALIASES = Map.of("rangedLaunched", "rangedAttack");
    /** NPCs whose InitEvent reached Java without a script host; cleared with forget/clearAll. */
    private static final java.util.Set<UUID> JAVA_INITED = ConcurrentHashMap.newKeySet();

    static String typedAlias(String hook) {
        return TYPED_ALIASES.get(hook);
    }

    private static boolean defines(Host host, String hook) {
        if (host == null) return false;
        String alias = typedAlias(hook);
        for (NpcScriptEngine.Instance tab : host.tabs) {
            if (tab.hasFunction(hook) || (alias != null && tab.hasFunction(alias))) return true;
        }
        return false;
    }

    private NpcScriptHost() {}

    private static final class Host {
        final String fingerprint;
        final List<NpcScriptEngine.Instance> tabs;
        final Map<String, Object> temp;
        final ScriptTimers timers;
        final Deque<String> console;
        long nextErrorChatTick;

        Host(String fingerprint, List<NpcScriptEngine.Instance> tabs, Deque<String> console,
             Map<String, Object> temp, ScriptTimers timers) {
            this.fingerprint = fingerprint;
            this.tabs = tabs;
            this.console = console;
            this.temp = temp;
            this.timers = timers;
        }
    }

    /** The temporary data and timers every wrapper of one NPC must share. */
    public record SharedState(Map<String, Object> temp, ScriptTimers timers) {}

    /**
     * This NPC's current temporary data and timers, for callers outside a hook (XenoAPI adapters).
     * With no host yet, the state is held until the first build adopts it, so there is never a
     * second owner. A rebuild starts fresh temporary data, as it always has; ask again after one.
     */
    public static SharedState sharedState(XenoNpcEntity npc) {
        if (npc == null || npc.level().isClientSide()) {
            throw new IllegalStateException("NPC script state exists only on the logical server");
        }
        return sharedState(npc.getUUID(), () -> new ScriptTimers(npc.getPersistentData()));
    }

    /** {@link #sharedState(XenoNpcEntity)} by id; {@code timers} loads the saved timers once. */
    static SharedState sharedState(UUID npc, java.util.function.Supplier<ScriptTimers> timers) {
        Host host = HOSTS.get(npc);
        if (host != null) return new SharedState(host.temp, host.timers);
        return PENDING.computeIfAbsent(npc, ignored -> new SharedState(new HashMap<>(), timers.get()));
    }

    /**
     * An NPC left its level (chunk unload, dimension change, removal). Pending state is bound to that
     * entity instance's persistent data, so it is dropped; the reloaded entity builds its own.
     */
    static void leftLevel(UUID npc) {
        if (npc == null) return;
        PENDING.remove(npc);
        JAVA_INITED.remove(npc);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onLeaveLevel(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof XenoNpcEntity npc && !event.getLevel().isClientSide()) {
            leftLevel(npc.getUUID());
        }
    }

    /** Hands pending state to the first host build, removing it so it has exactly one owner. */
    static SharedState takePending(UUID npc) {
        return PENDING.remove(npc);
    }

    /**
     * Fires {@code hook} on every tab of this NPC that defines it.
     *
     * @return true when a script canceled the event ({@code event.setCanceled(true)})
     */
    public static boolean fire(XenoNpcEntity npc, String hook, LivingEntity player,
                               LivingEntity source, LivingEntity entity, float damage) {
        return fire(npc, hook, player, source, entity, damage, null);
    }

    /** As fire, also delivering the typed XenoAPI event that {@code typed} builds. */
    public static boolean fire(XenoNpcEntity npc, String hook, LivingEntity player, LivingEntity source,
                               LivingEntity entity, float damage,
                               java.util.function.Function<xenoapi.npcs.api.entity.ICustomNpc<?>,
                                       ? extends xenoapi.npcs.api.event.CustomNPCsEvent> typed) {
        ScriptEvent event = fireEvent(npc, hook, player, source, entity, damage, typed);
        return event != null && event.isCanceled();
    }

    /** Returns the event so combat callers can use MyNPCs' mutable melee damage. */
    public static ScriptEvent fireEvent(XenoNpcEntity npc, String hook, LivingEntity player,
                                        LivingEntity source, LivingEntity entity, float damage) {
        return fireEvent(npc, hook, player, source, entity, damage, null);
    }

    /**
     * Scripts, then Java listeners, then read-back. Returns null only when neither a script nor a
     * listener wanted this occurrence (the native path then proceeds unchanged).
     */
    public static ScriptEvent fireEvent(XenoNpcEntity npc, String hook, LivingEntity player,
                                        LivingEntity source, LivingEntity entity, float damage,
                                        java.util.function.Function<xenoapi.npcs.api.entity.ICustomNpc<?>,
                                       ? extends xenoapi.npcs.api.event.CustomNPCsEvent> typed) {
        if (npc == null || npc.level().isClientSide()) return null;
        NpcScriptContainer container = NpcCombatProfile.readCached(npc).scripts;
        Host host = null;
        if (container == null || container.isEmpty() || !container.enabled()) {
            if (!HOSTS.isEmpty()) HOSTS.remove(npc.getUUID());
        } else {
            host = hostFor(npc, container);
        }
        boolean java = typed != null && net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.javaListening();
        if (host == null && !java) return null;
        if (!net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.enter(npc.getUUID(), hook)) return null;
        try {
            if (host == null && JAVA_INITED.add(npc.getUUID())) {
                net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.post(new xenoapi.npcs.api.event.NpcEvent.InitEvent(new net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoNpcAdapter(npc)));
            }
            // Build the typed event only when a tab defines this hook (or its alias) or Java listens.
            xenoapi.npcs.api.event.CustomNPCsEvent xeno = typed == null || !(java || defines(host, hook)) ? null
                    : typed.apply(new net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoNpcAdapter(npc));
            SharedState state = host != null ? new SharedState(host.temp, host.timers) : sharedState(npc);
            ScriptEvent event = new ScriptEvent(hook, new ScriptNpc(npc, state.temp(), state.timers()),
                    ScriptEntity.of(player), ScriptEntity.of(source), ScriptEntity.of(entity), damage, xeno);
            if (host != null) {
                dispatch(npc, host, hook, event);
                String alias = typedAlias(hook);
                if (alias != null && xeno != null) dispatchTyped(npc, host, alias, xeno);
            }
            if (xeno != null) {
                event.pushToXeno();
                net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.post(xeno);
                event.syncFromXeno();
            }
            return event;
        } finally {
            net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.exit(npc.getUUID(), hook);
        }
    }

    private static void dispatchTyped(XenoNpcEntity npc, Host host, String hook, Object typed) {
        for (NpcScriptEngine.Instance tab : host.tabs) {
            if (!tab.hasFunction(hook)) continue;
            NpcScriptResult result = tab.call(hook, typed);
            if (!result.ok()) reportError(npc, host, hook + ": " + result.describe());
        }
    }

    private static void dispatch(XenoNpcEntity npc, Host host, String hook, ScriptEvent event) {
        for (NpcScriptEngine.Instance tab : host.tabs) {
            if (!tab.hasFunction(hook)) {
                continue;
            }
            NpcScriptResult result = tab.call(hook, event);
            if (!result.ok()) {
                reportError(npc, host, hook + ": " + result.describe());
            }
        }
    }

    /** Convenience for hooks with no other party. */
    public static boolean fire(XenoNpcEntity npc, String hook) {
        return fire(npc, hook, null, null, null, 0.0f);
    }

    /**
     * Fires {@code trigger} on this NPC's tabs (XenoAPI {@code trigger(id, args)}). The typed
     * {@code ScriptTriggerEvent} rides along as {@code event.xeno}; {@code event.id} and
     * {@code event.arguments} carry the caller's values.
     */
    public static void fireTrigger(XenoNpcEntity npc, int id, Object[] arguments,
                                   xenoapi.npcs.api.event.WorldEvent.ScriptTriggerEvent xeno) {
        if (npc == null || npc.level().isClientSide()) return;
        NpcScriptContainer container = NpcCombatProfile.readCached(npc).scripts;
        if (container == null || container.isEmpty() || !container.enabled()) return;
        Host host = hostFor(npc, container);
        ScriptEvent event = new ScriptEvent("trigger", new ScriptNpc(npc, host.temp, host.timers),
                null, null, null, 0, xeno);
        event.id = id;
        event.arguments = arguments;
        dispatch(npc, host, "trigger", event);
    }

    /** Advances native timers every server tick; callbacks may schedule another timer safely. */
    public static void tickTimers(XenoNpcEntity npc) {
        Host host = HOSTS.get(npc.getUUID());
        SharedState pending = host == null ? PENDING.get(npc.getUUID()) : null;
        ScriptTimers timers = host != null ? host.timers : pending != null ? pending.timers() : null;
        if (timers == null) return;
        timers.tick(npc.level().getGameTime(), id -> {
            var xeno = new xenoapi.npcs.api.event.NpcEvent.TimerEvent(new net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoNpcAdapter(npc), id);
            if (host != null) {
                ScriptEvent event = new ScriptEvent("timer", new ScriptNpc(npc, host.temp, host.timers),
                        null, null, null, 0, xeno);
                event.id = id;
                dispatch(npc, host, "timer", event);
            }
            net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.post(xeno);
        });
    }

    private static Host hostFor(XenoNpcEntity npc, NpcScriptContainer container) {
        String fingerprint = fingerprint(container);
        Host host = HOSTS.get(npc.getUUID());
        if (host != null && host.fingerprint.equals(fingerprint)) {
            return host;
        }
        Deque<String> console = host == null ? new ArrayDeque<>() : host.console;
        NpcScriptEngine engine = NpcScriptEngines.forLanguage(container.language());
        List<NpcScriptEngine.Instance> tabs = new ArrayList<>();
        SharedState pending = host == null ? takePending(npc.getUUID()) : null;
        Host built = new Host(fingerprint, tabs, console,
                pending == null ? new HashMap<>() : pending.temp(),
                host != null ? host.timers
                        : pending != null ? pending.timers() : new ScriptTimers(npc.getPersistentData()));
        HOSTS.put(npc.getUUID(), built);
        int index = 0;
        for (NpcScriptContainer.Tab tab : container.tabs()) {
            index++;
            String source = sourceOf(tab);
            if (source.isBlank()) {
                continue;
            }
            NpcScriptLog log = line -> report(npc, built, line);
            NpcScriptScope scope = NpcScriptScope.builder()
                    .put("npc", new ScriptNpc(npc, built.temp, built.timers))
                    .put("world", new ScriptNpc(npc, built.temp, built.timers).getWorld())
                    .put("XenoPixels", NativeXenoScriptApi.INSTANCE)
                    .put("XenoAPI", xenoapi.npcs.api.NpcAPI.Instance())
                    .put("log", log)
                    .putString("script", tab.scriptId())
                    .putString("scriptName", "tab " + index)
                    .build();
            NpcScriptEngine.Instance instance = engine.instantiate(source, scope);
            if (!instance.ok()) {
                reportError(npc, built, "tab " + index + " failed to load: "
                        + instance.loadResult().describe());
            }
            tabs.add(instance);
        }
        fireInit(npc, built);
        return built;
    }

    private static void fireInit(XenoNpcEntity npc, Host host) {
        var xeno = new xenoapi.npcs.api.event.NpcEvent.InitEvent(new net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoNpcAdapter(npc));
        ScriptEvent event = new ScriptEvent("init", new ScriptNpc(npc, host.temp, host.timers), null, null,
                null, 0.0f, xeno);
        for (NpcScriptEngine.Instance tab : host.tabs) {
            if (tab.hasFunction("init")) {
                NpcScriptResult result = tab.call("init", event);
                if (!result.ok()) reportError(npc, host, "init: " + result.describe());
            }
        }
        JAVA_INITED.add(npc.getUUID());
        net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.post(xeno);
    }

    /** Loaded library scripts first, then the tab's own text, as CustomNPCs concatenates them. */
    static String sourceOf(NpcScriptContainer.Tab tab) {
        StringBuilder out = new StringBuilder();
        for (String id : tab.loaded()) {
            String text = XenoNpcScripts.scriptText(id);
            if (text != null) out.append(text).append('\n');
        }
        String own = XenoNpcScripts.scriptText(tab.scriptId());
        if (own != null) out.append(own);
        return out.toString();
    }

    static String fingerprint(NpcScriptContainer container) {
        return XenoNpcScripts.generation() + "|" + container.language() + "|"
                + String.join(",", container.referencedIds()) + "|" + container.tabs().size();
    }

    private static final SimpleDateFormat STAMP = new SimpleDateFormat("HH:mm:ss");

    private static void report(XenoNpcEntity npc, Host host, String line) {
        String stamped;
        synchronized (STAMP) {
            stamped = STAMP.format(new Date()) + " " + line;
        }
        synchronized (host.console) {
            host.console.addFirst(stamped);
            while (host.console.size() > CONSOLE_LINES) host.console.removeLast();
        }
        XenoPixelsMod.LOGGER.debug("NPC script {}: {}", npc.getUUID(), line);
    }

    /** Keep automatic script failures visible without sending every tick's repeat to chat. */
    private static void reportError(XenoNpcEntity npc, Host host, String line) {
        report(npc, host, line);
        if (!(npc.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (now < host.nextErrorChatTick) return;
        host.nextErrorChatTick = now + ERROR_CHAT_INTERVAL_TICKS;
        Component notice = Component.literal("[Xeno NPC script] " + npc.getName().getString()
                + " — " + line).withStyle(ChatFormatting.RED);
        for (ServerPlayer viewer : level.players()) {
            // Anyone who may edit NPCs (level 2) is told, not only the scripter bar (4): an op
            // standing at a broken NPC needs to see why it is not doing anything.
            if (viewer.hasPermissions(2)
                    && viewer.distanceToSqr(npc) <= ERROR_CHAT_RANGE_SQ) {
                viewer.sendSystemMessage(notice);
            }
        }
    }

    /** Newest-first console lines for this NPC (prints and errors), for the script screen. */
    public static List<String> console(UUID npc) {
        Host host = npc == null ? null : HOSTS.get(npc);
        if (host == null) return List.of();
        synchronized (host.console) {
            return List.copyOf(host.console);
        }
    }

    /** Clears this NPC's console (Settings tab's Clear). */
    public static void clearConsole(UUID npc) {
        Host host = npc == null ? null : HOSTS.get(npc);
        if (host != null) {
            synchronized (host.console) {
                host.console.clear();
            }
        }
    }

    /** Drops the NPC's instances, so the next hook rebuilds them (container rebound, removal). */
    public static void forget(UUID npc) {
        if (npc != null) {
            HOSTS.remove(npc);
            PENDING.remove(npc);
            JAVA_INITED.remove(npc);
        }
    }

    /**
     * Resolves the engine once at startup and says which one answered, so a server log shows
     * whether scripts can run before anyone opens the editor.
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        XenoPixelsMod.LOGGER.info("NPC scripts: {}", NpcScriptEngines.describe());
    }

    /** Server stop: nothing may outlive the world it was built for. */
    public static void clearAll() {
        HOSTS.clear();
        PENDING.clear();
        JAVA_INITED.clear();
    }
}
