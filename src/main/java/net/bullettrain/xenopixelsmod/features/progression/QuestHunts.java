package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.bullettrain.xenopixelsmod.compat.npc.NpcTargetKeeper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Kill target hunts the player": for a quest with {@code target_hunts}, the NPC its KILL_NPC step
 * names (a native Xeno NPC, a MyNPCs NPC or a CustomNPCs NPC, matched by full visible name exactly
 * as the objective matches its kill) targets and attacks the player who started the quest, from
 * quest start until the quest ends. Re-applied every two seconds, because NPC mods reset targets;
 * released when the quest is completed, abandoned or ready to hand in, or the player leaves.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class QuestHunts {
    /** How far from the claimant a hunter is looked for, in blocks. */
    static final double RANGE = 96.0;
    private static final int INTERVAL = 40;

    /** Claimant to the quest ids hunting them, and to the hunters currently provoked. */
    private static final Map<UUID, Set<String>> QUESTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> HUNTERS = new ConcurrentHashMap<>();

    private QuestHunts() {}

    /** Called when a quest starts. A quest without the option does nothing. */
    public static void onQuestStarted(ServerPlayer player, ParallelQuests.QuestDef def) {
        if (player == null || def == null || def.huntedNpcNames().isEmpty()) {
            return;
        }
        QUESTS.computeIfAbsent(player.getUUID(), id -> ConcurrentHashMap.newKeySet()).add(def.id());
        apply(player);
    }

    /** Ends the hunt at once when a quest completes, instead of waiting for the next sweep. */
    public static void onQuestEnded(ServerPlayer player, String questId) {
        Set<String> quests = player == null ? null : QUESTS.get(player.getUUID());
        if (quests == null || !quests.remove(questId)) {
            return;
        }
        if (quests.isEmpty()) {
            Set<UUID> hunters = HUNTERS.remove(player.getUUID());
            if (hunters != null) calm(player, hunters);
            QUESTS.remove(player.getUUID());
        } else {
            apply(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (QUESTS.isEmpty() || server.getTickCount() % INTERVAL != 0) {
            return;
        }
        for (UUID id : Set.copyOf(QUESTS.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                release(server, id);
            } else {
                apply(player);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        QUESTS.clear();
        HUNTERS.clear();
    }

    /** Provokes every live hunter for this player's still-running hunt quests. */
    static void apply(ServerPlayer player) {
        Set<String> quests = QUESTS.get(player.getUUID());
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        if (quests == null || data == null) {
            return;
        }
        Set<String> names = new HashSet<>();
        Set<UUID> givers = new HashSet<>();
        for (String questId : Set.copyOf(quests)) {
            ActiveQuest active = data.quests().active(questId);
            ParallelQuests.QuestDef def = ParallelQuests.definition(questId);
            if (active == null || active.ready() || def == null) {
                quests.remove(questId);
                continue;
            }
            names.addAll(def.huntedNpcNames());
            if (active.giverId() != null) givers.add(active.giverId());
        }
        Set<UUID> hunters = HUNTERS.computeIfAbsent(player.getUUID(), id -> ConcurrentHashMap.newKeySet());
        if (names.isEmpty()) {
            calm(player, hunters);
            QUESTS.remove(player.getUUID());
            HUNTERS.remove(player.getUUID());
            return;
        }
        ServerLevel level = player.serverLevel();
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(player.blockPosition()).inflate(RANGE),
                entity -> entity != player && entity.isAlive()
                        && isHunter(entity.getUUID(), matchesAny(entity, names), givers));
        for (LivingEntity hunter : found) {
            NpcTargetKeeper.provoke(hunter, player);
            hunters.add(hunter.getUUID());
        }
    }

    /**
     * Whether an entity is turned on the claimant: it carries a kill-target name and is not the
     * NPC that gave one of the hunting quests. NPCs commonly share a name ("Humanoid"), and the
     * giver used to match its own kill target and attack the player it had just given the quest to
     * (2026-09-30 owner: "Kill Target Hunts Plater makes the quest giver npc attack me").
     */
    static boolean isHunter(UUID entity, boolean nameMatches, Set<UUID> givers) {
        return nameMatches && !givers.contains(entity);
    }

    private static boolean matchesAny(LivingEntity entity, Set<String> names) {
        for (String name : names) {
            if (QuestNpcTarget.matches(entity, name)) return true;
        }
        return false;
    }

    private static void calm(ServerPlayer player, Set<UUID> hunters) {
        for (UUID id : hunters) {
            if (player.serverLevel().getEntity(id) instanceof LivingEntity hunter) {
                NpcTargetKeeper.calmIfTargeting(hunter, player);
            }
        }
        hunters.clear();
    }

    private static void release(MinecraftServer server, UUID player) {
        QUESTS.remove(player);
        Set<UUID> hunters = HUNTERS.remove(player);
        if (hunters == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (UUID id : hunters) {
                if (level.getEntity(id) instanceof net.minecraft.world.entity.Mob mob
                        && mob.getTarget() != null && mob.getTarget().getUUID().equals(player)) {
                    mob.setTarget(null);
                }
            }
        }
    }
}
