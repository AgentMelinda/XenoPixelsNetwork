package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.features.party.PartyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * When an NPC quest/dialog command uses {@code @dp}, run it for every online party member —
 * not only the player who turned the quest in.
 */
public final class NpcPartyReward {
    private static final long DEDUPE_MS = 2_000L;
    private static final Map<String, Long> RECENT = new ConcurrentHashMap<>();

    private NpcPartyReward() {}

    /**
     * @param shareQuests when false, only {@code trigger} is paid — anti-farm default
     */
    public static List<ServerPlayer> select(ServerPlayer trigger, boolean shareQuests,
                                            List<ServerPlayer> members) {
        if (trigger == null) return List.of();
        if (!shareQuests || members == null || members.size() <= 1) return List.of(trigger);
        return List.copyOf(members);
    }

    public static List<ServerPlayer> recipients(ServerPlayer trigger) {
        if (trigger == null) return List.of();
        if (!PartyManager.shareQuests(trigger)) return List.of(trigger);
        List<UUID> ids = PartyManager.membersOf(trigger);
        if (ids.size() <= 1) return List.of(trigger);
        List<ServerPlayer> out = new ArrayList<>();
        var list = trigger.getServer().getPlayerList();
        for (UUID id : ids) {
            ServerPlayer member = list.getPlayer(id);
            if (member != null && member.isAlive()) out.add(member);
        }
        return out.isEmpty() ? List.of(trigger) : List.copyOf(out);
    }

    public static boolean isDialogReward(String command) {
        if (command == null) return false;
        String lower = command.toLowerCase();
        return lower.contains("@dp") || lower.contains("{refplayer}") || lower.contains("@p2");
    }

    public static String runForParty(Player trigger, String command, Function<Player, String> run) {
        if (!isDialogReward(command) || !(trigger instanceof ServerPlayer serverTrigger)) {
            return run.apply(trigger);
        }
        List<ServerPlayer> members = recipients(serverTrigger);
        String last = "";
        for (ServerPlayer member : members) {
            if (recentlyPaid(member.getUUID(), command)) continue;
            last = run.apply(member);
        }
        return last;
    }

    private static boolean recentlyPaid(UUID playerId, String command) {
        long now = System.currentTimeMillis();
        String key = playerId + "|" + (command == null ? "" : command.trim());
        Long previous = RECENT.get(key);
        if (previous != null && now - previous < DEDUPE_MS) return true;
        RECENT.put(key, now);
        return false;
    }
}
