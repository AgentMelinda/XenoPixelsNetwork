package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.stats.character.Character;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.dmz.DmzAppearance;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A borrowed look, with the way back attached to it.
 *
 * <p>One look at a time per player: borrow an NPC's appearance for a number of ticks, and either the
 * deadline, a death, a relog or an explicit return puts the player's own look back. Nothing here can
 * leave a player permanently wearing someone else's face, which is why the snapshot is stored on the
 * player rather than remembered in memory - a server that crashes mid-steal still knows what it owes.
 *
 * <p>Combat state is deliberately untouched: race, gender and class move because they are part of the
 * look, while masteries, skills, forms and progression stay exactly where they were.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlayerAppearanceSteals {
    /**
     * Players with work pending, so the per-tick check is a set lookup rather than an attachment read
     * for everyone online. The stored tag is the truth; this only says whether to go and look.
     */
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    private PlayerAppearanceSteals() {}

    /** Borrows {@code npc}'s look for {@code ticks} game ticks, remembering what to give back. */
    public static NpcAppearanceMapping.Result start(ServerPlayer player, Entity npc, long ticks) {
        if (npc == null) {
            return NpcAppearanceMapping.Result.NO_SOURCE;
        }
        return start(player, NpcAppearanceSource.from(npc), npc.getUUID().toString(), ticks);
    }

    static NpcAppearanceMapping.Result start(ServerPlayer player, NpcAppearanceSource source,
                                             String sourceId, long ticks) {
        NpcAppearanceMapping.Result ready = NpcAppearanceMapping.validate(player, source);
        if (ready != NpcAppearanceMapping.Result.OK) {
            return ready;
        }
        CompoundTag store = store(player);
        Character target = NpcAppearanceMapping.character(player);
        if (store == null || target == null) {
            return NpcAppearanceMapping.Result.NO_DATA;
        }
        NpcAppearanceMapping.Result begun = Ledger.begin(store, target, source, sourceId,
                player.level().getGameTime(), ticks);
        if (begun == NpcAppearanceMapping.Result.OK) {
            PENDING.add(player.getUUID());
            NpcAppearanceMapping.sync(player);
        }
        return begun;
    }

    /** Gives the borrowed look back, whether or not the deadline has arrived. */
    public static NpcAppearanceMapping.Result stop(ServerPlayer player) {
        if (player == null) {
            return NpcAppearanceMapping.Result.NO_DATA;
        }
        CompoundTag store = store(player);
        if (store == null) {
            return NpcAppearanceMapping.Result.NO_DATA;
        }
        NpcAppearanceMapping.Result ended = Ledger.end(store, NpcAppearanceMapping.character(player));
        if (ended == NpcAppearanceMapping.Result.OK
                || ended == NpcAppearanceMapping.Result.NOT_ACTIVE
                || ended == NpcAppearanceMapping.Result.INTERNAL) {
            PENDING.remove(player.getUUID());
        }
        if (ended == NpcAppearanceMapping.Result.OK) {
            NpcAppearanceMapping.sync(player);
        }
        return ended;
    }

    public static boolean isActive(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        CompoundTag store = store(player);
        return store != null && Ledger.active(store);
    }

    /** Ticks left on the borrow, or 0 when none is active. */
    public static long remainingTicks(ServerPlayer player) {
        if (player == null) {
            return 0L;
        }
        CompoundTag store = store(player);
        return store == null ? 0L : Ledger.remaining(store, player.level().getGameTime());
    }

    /** The NPC this look came from, or an empty string. */
    public static String sourceId(ServerPlayer player) {
        if (player == null) {
            return "";
        }
        CompoundTag store = store(player);
        return store == null ? "" : Ledger.source(store);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !PENDING.contains(player.getUUID())) {
            return;
        }
        CompoundTag store = store(player);
        if (store == null || !Ledger.active(store)) {
            PENDING.remove(player.getUUID());
            return;
        }
        if (Ledger.remaining(store, player.level().getGameTime()) == 0L) {
            stop(player);
        }
    }

    /**
     * Dying copies the attachment to the new player, who is wearing the borrowed look and does not
     * know it. Pick the debt up again rather than wait for the deadline.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resume(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resume(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resume(player);
        }
    }

    /**
     * Going offline keeps the snapshot: the deadline runs on game time rather than on the session, so
     * the next login either continues the borrow or hands the look back.
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    /**
     * A clean shutdown is the one chance to keep a borrowed look out of the player's save file: this
     * event fires when the tick loop exits, while the players are still connected, so the look can go
     * back before anything is written. Their clients are about to disappear too, so nothing is synced.
     *
     * <p>A crash never reaches here. That is why the snapshot lives on the player rather than in
     * memory - {@link #onLogin} picks the debt back up.
     */
    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            CompoundTag store = store(player);
            if (store == null || !Ledger.active(store)) {
                continue;
            }
            if (Ledger.end(store, NpcAppearanceMapping.character(player))
                    == NpcAppearanceMapping.Result.OK) {
                PENDING.remove(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) {
        PENDING.clear();
    }

    /** Re-reads the stored borrow after a player object changed under it, restoring if it expired. */
    private static void resume(ServerPlayer player) {
        CompoundTag store = store(player);
        if (store == null || !Ledger.active(store)) {
            PENDING.remove(player.getUUID());
            return;
        }
        PENDING.add(player.getUUID());
        if (Ledger.remaining(store, player.level().getGameTime()) == 0L) {
            stop(player);
        }
    }

    private static CompoundTag store(ServerPlayer player) {
        return XenoCapabilities.get(player).map(XenoPlayerData::appearanceSteals).orElse(null);
    }

    /**
     * The bookkeeping, kept apart from players and packets so the rules can be checked without a
     * server: one borrow at a time, the snapshot written before the look changes, and an entry that
     * cannot be restored is dropped rather than retried forever.
     */
    static final class Ledger {
        /** One day of ticks, which is also the cap native script timers use. */
        static final long MAX_TICKS = 1_200_000L;
        private static final String ACTIVE = "Active";
        private static final String SNAPSHOT = "Snapshot";
        private static final String ENDS_AT = "EndsAt";
        private static final String SOURCE = "Source";

        private Ledger() {}

        static NpcAppearanceMapping.Result begin(CompoundTag store, Character target,
                                                 NpcAppearanceSource source, String sourceId,
                                                 long gameTime, long ticks) {
            if (store == null || target == null) {
                return NpcAppearanceMapping.Result.NO_DATA;
            }
            NpcAppearanceMapping.Result look = NpcAppearanceMapping.inspect(source);
            if (look != NpcAppearanceMapping.Result.OK) {
                return look;
            }
            if (active(store)) {
                return NpcAppearanceMapping.Result.ALREADY_ACTIVE;
            }
            CompoundTag snapshot = DmzAppearance.snapshot(target);
            if (!DmzAppearance.isSnapshot(snapshot)) {
                return NpcAppearanceMapping.Result.NO_DATA;
            }
            NpcAppearanceMapping.apply(target, source);
            CompoundTag entry = new CompoundTag();
            entry.put(SNAPSHOT, snapshot);
            entry.putLong(ENDS_AT, gameTime + Math.max(1L, Math.min(MAX_TICKS, ticks)));
            entry.putString(SOURCE, sourceId == null ? "" : sourceId);
            store.put(ACTIVE, entry);
            return NpcAppearanceMapping.Result.OK;
        }

        static NpcAppearanceMapping.Result end(CompoundTag store, Character target) {
            if (store == null) {
                return NpcAppearanceMapping.Result.NO_DATA;
            }
            CompoundTag entry = store.getCompound(ACTIVE);
            if (entry.isEmpty()) {
                return NpcAppearanceMapping.Result.NOT_ACTIVE;
            }
            CompoundTag snapshot = entry.getCompound(SNAPSHOT);
            if (!DmzAppearance.isSnapshot(snapshot)) {
                // Unrecoverable, and waiting will not help: the borrow is dropped so nobody keeps
                // checking, and whatever the player is wearing now is what they keep.
                store.remove(ACTIVE);
                return NpcAppearanceMapping.Result.INTERNAL;
            }
            if (target == null || !DmzAppearance.restore(target, snapshot)) {
                // Their DragonMineZ data is not loaded yet, which is temporary. The entry stays,
                // because the alternative is leaving them in the borrowed body.
                return NpcAppearanceMapping.Result.NO_DATA;
            }
            store.remove(ACTIVE);
            return NpcAppearanceMapping.Result.OK;
        }

        static boolean active(CompoundTag store) {
            return store != null && !store.getCompound(ACTIVE).isEmpty();
        }

        static long remaining(CompoundTag store, long gameTime) {
            if (!active(store)) {
                return 0L;
            }
            return Math.max(0L, store.getCompound(ACTIVE).getLong(ENDS_AT) - gameTime);
        }

        static String source(CompoundTag store) {
            return active(store) ? store.getCompound(ACTIVE).getString(SOURCE) : "";
        }
    }
}
