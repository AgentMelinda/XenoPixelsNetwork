package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptTimers;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native player timers, saved in the player's XenoScriptTimers and ticked every server tick.
 * Loaded at login (so saved timers resume) or when IPlayer.getTimers first asks; dropped at logout.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlayerScriptTimers {
    private static final Map<UUID, ScriptTimers> TIMERS = new ConcurrentHashMap<>();

    private PlayerScriptTimers() {}

    public static ScriptTimers of(ServerPlayer player) {
        return of(player.getUUID(), () -> XenoCapabilities.get(player).orElseThrow().scriptTimers());
    }

    /** {@code data} is read only when no timers are bound for this player yet. */
    static ScriptTimers of(UUID player, java.util.function.Supplier<net.minecraft.nbt.CompoundTag> data) {
        return TIMERS.computeIfAbsent(player, ignored -> new ScriptTimers(data.get()));
    }

    public static void forget(UUID player) { TIMERS.remove(player); }

    /**
     * Death and End exits give the player a new capability holding a copy of the timers; the old
     * binding would keep writing to the dead player's data, so it is dropped and rebuilt lazily.
     */
    static void cloned(UUID player) { TIMERS.remove(player); }

    @SubscribeEvent
    public static void onClone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) cloned(player.getUUID());
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ScriptTimers timers = TIMERS.get(player.getUUID());
        if (timers == null) return;
        timers.tick(player.level().getGameTime(), id -> {
            var typed = new xenoapi.npcs.api.event.PlayerEvent.TimerEvent(
                    (xenoapi.npcs.api.entity.IPlayer<?>) net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoApiAdapters.wrap(player), id);
            PlayerScriptHost.fireTyped(player, "timer", typed);
            net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.post(typed);
        });
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) { TIMERS.clear(); }
}
