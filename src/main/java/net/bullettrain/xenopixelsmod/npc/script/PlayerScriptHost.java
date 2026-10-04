package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcScriptSay;
import net.bullettrain.xenopixelsmod.npc.script.api.NativeXenoScriptApi;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptEntity;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptEvent;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptPlayer;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptWorld;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcScripts;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Global player script tabs, evaluated in one isolated scope per player. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlayerScriptHost {
    private static final Map<UUID, Host> HOSTS = new ConcurrentHashMap<>();
    private PlayerScriptHost() {}

    /** Hooks a player tab may define: the four existing ones plus the typed XenoAPI player events. */
    public static final List<String> PLAYER_HOOKS = List.of("init", "tick", "interact", "attack", "broken",
            "toss", "pickedUp", "containerOpen", "containerClosed", "damagedEntity", "rangedLaunched", "died",
            "kill", "damaged", "timer", "login", "logout", "levelUp", "chat", "trigger");

    private record Tab(String id, NpcScriptEngine.Instance instance, boolean chatOnly) {}
    private record Host(int generation, List<Tab> tabs, boolean hasChat) {}

    private static Host host(ServerPlayer player) {
        Host existing = HOSTS.get(player.getUUID());
        if (existing != null && existing.generation == XenoNpcScripts.generation()) return existing;
        List<Tab> tabs = new ArrayList<>();
        var store = XenoNpcStores.get();
        if (store != null) {
            var entries = new ArrayList<>(store.list(XenoNpcStoreCategory.PLAYER_SCRIPTS));
            entries.sort(Comparator.comparing(e -> e.id()));
            for (var entry : entries) {
                if (tabs.size() >= NpcScriptContainer.MAX_TABS) break;
                var script = XenoNpcScripts.Script.of(entry.id(), entry.tag());
                if (!script.enabled() || script.script().isBlank()) continue;
                NpcScriptScope scope = NpcScriptScope.builder()
                        .put("player", ScriptEntity.of(player))
                        .put("world", new ScriptWorld(player.serverLevel()))
                        .put("XenoPixels", NativeXenoScriptApi.INSTANCE)
                        .put("XenoAPI", xenoapi.npcs.api.NpcAPI.Instance())
                        .put("log", (NpcScriptLog) line -> XenoPixelsMod.LOGGER.info(
                                "Player script {}: {}", entry.id(), line))
                        .putString("script", entry.id())
                        .putString("scriptName", entry.id())
                        .build();
                var instance = NpcScriptEngines.forLanguage(script.language())
                        .instantiate(script.script(), scope);
                if (!instance.ok()) {
                    XenoPixelsMod.LOGGER.error("Player script {} load: {}", entry.id(),
                            instance.loadResult().describe());
                }
                tabs.add(new Tab(entry.id(), instance, entry.tag().getBoolean("ChatOnly")));
            }
        }
        Host built = new Host(XenoNpcScripts.generation(), List.copyOf(tabs),
                tabs.stream().anyMatch(t -> t.instance.hasFunction("chat")));
        HOSTS.put(player.getUUID(), built);
        return built;
    }

    /** Rebuilds online player tabs after a store edit, on the server thread. */
    public static void reload(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            host(player);
            // A saved tab starts running now, not at the next login: init fires for everyone
            // online, as it does on join. login itself still waits for a real login.
            fire(player, "init", null, new xenoapi.npcs.api.event.PlayerEvent.InitEvent(api(player)));
        }
    }

    public static boolean hasHook(ServerPlayer player, String hook) {
        for (Tab tab : host(player).tabs) {
            if (tab.chatOnly && !hook.equals("chat")) continue;
            if (tab.instance.hasFunction(hook)) return true;
        }
        return false;
    }

    /** A new-style hook: the typed XenoAPI event is the argument. */
    public static void fireTyped(ServerPlayer player, String hook, Object typed) {
        for (Tab tab : host(player).tabs) {
            if (tab.chatOnly && !hook.equals("chat")) continue;
            if (!tab.instance.hasFunction(hook)) continue;
            NpcScriptResult result = tab.instance.call(hook, typed);
            if (!result.ok()) XenoPixelsMod.LOGGER.error("Player script {} {}: {}", tab.id, hook, result.describe());
        }
    }

    private static xenoapi.npcs.api.entity.IPlayer<?> api(ServerPlayer player) {
        return (xenoapi.npcs.api.entity.IPlayer<?>) net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoApiAdapters.wrap(player);
    }

    /** An existing hook: today's ScriptEvent carrying the typed event as event.xeno; then Java. */
    private static ScriptEvent fire(ServerPlayer player, String hook, String message,
                                    xenoapi.npcs.api.event.CustomNPCsEvent xeno) {
        Host host = host(player);
        ScriptEvent event = new ScriptEvent(hook, null, ScriptEntity.of(player), null, null, 0, xeno);
        event.message = message;
        for (Tab tab : host.tabs) {
            if (tab.chatOnly && !hook.equals("chat")) continue;
            if (!tab.instance.hasFunction(hook)) continue;
            NpcScriptResult result = tab.instance.call(hook, event);
            if (!result.ok()) XenoPixelsMod.LOGGER.error("Player script {} {}: {}",
                    tab.id, hook, result.describe());
        }
        if (xeno != null) {
            event.pushToXeno();
            net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.post(xeno);
            event.syncFromXeno();
            if (xeno instanceof xenoapi.npcs.api.event.PlayerEvent.ChatEvent chat
                    && chat.message != null && !chat.message.equals(message)) {
                // A Java listener rewrote the line after the scripts ran; it runs last, so it wins.
                event.message = chat.message;
            }
        }
        return event;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var api = api(player);
            PlayerScriptTimers.forget(player.getUUID());   // always bind to this login's player data
            PlayerScriptTimers.of(player);
            fire(player, "init", null, new xenoapi.npcs.api.event.PlayerEvent.InitEvent(api));
            fire(player, "login", null, new xenoapi.npcs.api.event.PlayerEvent.LoginEvent(api));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            fire(player, "logout", null, new xenoapi.npcs.api.event.PlayerEvent.LogoutEvent(api(player)));
            HOSTS.remove(player.getUUID());
            PlayerScriptTimers.forget(player.getUUID());
            ScriptPlayer.forget(player.getUUID());
            net.bullettrain.xenopixelsmod.npc.dialog.ScriptShownDialogues.forget(player.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null) return;
        String raw = NpcScriptSay.typedLine(event.getRawText(),
                event.getMessage() == null ? "" : event.getMessage().getString());
        var server = player.getServer();
        if (server == null) return;
        if (!server.isSameThread()) {
            Host cached = HOSTS.get(player.getUUID());
            if (cached == null || !cached.hasChat) return;
            event.setCanceled(true);
            server.execute(() -> handleChat(player, raw, true));
            return;
        }
        if (!host(player).hasChat) return;
        ScriptEvent scripted = fire(player, "chat", raw, new xenoapi.npcs.api.event.PlayerEvent.ChatEvent(api(player), raw));
        if (scripted.isCanceled()) event.setCanceled(true);
        else if (NpcScriptSay.shouldRewrite(raw, scripted.message)) {
            event.setCanceled(true);
            NpcScriptSay.broadcast(player, scripted.message);
        }
    }

    private static void handleChat(ServerPlayer player, String raw, boolean canceledOriginal) {
        ScriptEvent scripted = fire(player, "chat", raw, new xenoapi.npcs.api.event.PlayerEvent.ChatEvent(api(player), raw));
        if (!scripted.isCanceled() && canceledOriginal) NpcScriptSay.broadcast(player, scripted.message);
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) { HOSTS.clear(); }
}
