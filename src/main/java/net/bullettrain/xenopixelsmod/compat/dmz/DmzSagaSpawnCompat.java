package net.bullettrain.xenopixelsmod.compat.dmz;

import com.dragonminez.common.quest.Difficulty;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.QuestObjective;
import com.dragonminez.common.quest.QuestService;
import com.dragonminez.common.quest.objectives.KillObjective;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Repairs missing DragonMineZ quest-spawned combat enemies without duplicating valid spawns. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class DmzSagaSpawnCompat {
    private static final String QUEST_KEY_TAG = "dmz_quest_key";
    private static final String OBJECTIVE_INDEX_TAG = "dmz_quest_objective_index";
    private static final String QUEST_OWNER_TAG = "dmz_quest_owner";
    private static final String SAGA_ID_TAG = "dmz_saga_id";
    private static final String QUEST_TEAM_TAG = "dmz_quest_team";
    private static final int MAX_AUDITS = 3;

    private static final Map<AuditKey, PendingAudit> PENDING = new HashMap<>();
    private static final Map<UUID, String> LAST_FAILURE = new HashMap<>();

    private DmzSagaSpawnCompat() {
    }

    public static void schedule(ServerPlayer player, QuestService.ResolvedQuest resolvedQuest,
                                int partySize, Difficulty difficulty) {
        if (!XenoServerConfig.dmzSagaSpawnCompat || player == null || resolvedQuest == null) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        AuditKey key = new AuditKey(player.getUUID(), resolvedQuest.questKey());
        PENDING.put(key, new PendingAudit(
                player.getUUID(), resolvedQuest.questKey(), Math.max(1, partySize),
                difficulty == null ? Difficulty.NORMAL : difficulty,
                server.getTickCount() + 1, 0));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        List<AuditKey> due = new ArrayList<>();
        for (Map.Entry<AuditKey, PendingAudit> entry : PENDING.entrySet()) {
            if (entry.getValue().dueTick <= now) due.add(entry.getKey());
        }
        for (AuditKey key : due) {
            PendingAudit pending = PENDING.remove(key);
            if (pending == null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId);
            if (player == null) continue;

            RepairResult result = repair(player, pending.questKey, pending.partySize, pending.difficulty);
            if (!result.activeQuest) continue;
            int nextAudit = pending.auditNumber + 1;
            if (nextAudit < MAX_AUDITS && (result.spawned > 0 || result.missing > 0)) {
                int delay = result.spawned > 0 ? 1 : 20;
                PENDING.put(key, new PendingAudit(
                        pending.playerId, pending.questKey, pending.partySize, pending.difficulty,
                        now + delay, nextAudit));
            } else if (result.missing > 0) {
                rememberFailure(player, "Quest enemy still missing after " + MAX_AUDITS
                        + " audits: " + pending.questKey);
            }
        }
    }

    public static RepairResult repairActiveQuest(ServerPlayer player) {
        ActiveQuest active = activeQuest(player);
        if (active == null) return RepairResult.inactive("No accepted DMZ quest is tracked");
        int partySize = Math.max(1, com.dragonminez.common.quest.PartyManager.getAllPartyMembers(player).size());
        return repair(player, active.questKey, partySize, active.difficulty);
    }

    public static List<String> diagnose(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        lines.add("Saga spawn compat=" + XenoServerConfig.dmzSagaSpawnCompat
                + ", dimension=" + player.level().dimension().location()
                + ", difficulty=" + player.level().getDifficulty().getKey()
                + ", doMobSpawning=" + player.level().getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING));

        ActiveQuest active = activeQuest(player);
        if (active == null) {
            lines.add("No accepted DMZ quest is tracked");
        } else {
            QuestService.ResolvedQuest resolved = QuestService.resolveQuest(active.questKey);
            lines.add("Quest=" + active.questKey + ", status=ACCEPTED, difficulty=" + active.difficulty.name());
            if (resolved == null) {
                lines.add("Quest registry lookup failed");
            } else {
                PlayerQuestData data = questData(player);
                Quest quest = resolved.quest();
                for (int index = 0; index < quest.getObjectives().size(); index++) {
                    QuestObjective objective = quest.getObjectives().get(index);
                    if (!(objective instanceof KillObjective kill)
                            || kill.getSpawnMode() != KillObjective.SpawnMode.QUEST) continue;
                    int progress = data == null ? 0 : data.getObjectiveProgress(active.questKey, index);
                    int required = data == null ? kill.getCount()
                            : quest.getObjectiveRequired(data, active.questKey, index);
                    int existing = countQuestEntities(player.serverLevel(), player, active.questKey, index);
                    EntityType<?> type = kill.resolveEntityType();
                    ResourceLocation typeId = type == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(type);
                    lines.add("Objective " + index + ": entity=" + kill.getEntityId()
                            + ", resolved=" + (typeId == null ? "null" : typeId)
                            + ", progress=" + progress + "/" + required
                            + ", loaded=" + existing);
                }
            }
        }
        String failure = LAST_FAILURE.get(player.getUUID());
        if (failure != null && !failure.isBlank()) lines.add("Last failure=" + failure);
        return lines;
    }

    static int missingCount(int required, int progress, int existing) {
        return Math.max(0, required - progress - existing);
    }

    private static RepairResult repair(ServerPlayer player, String questKey, int partySize, Difficulty difficulty) {
        if (!XenoServerConfig.dmzSagaSpawnCompat) return RepairResult.inactive("Compatibility disabled");
        PlayerQuestData questData = questData(player);
        if (questData == null) return fail(player, "DragonMineZ player quest data is unavailable");
        if (questData.getQuestStatus(questKey) != PlayerQuestData.QuestStatus.ACCEPTED) {
            return RepairResult.inactive("Quest is no longer accepted");
        }

        QuestService.ResolvedQuest resolved = QuestService.resolveQuest(questKey);
        if (resolved == null) return fail(player, "DragonMineZ quest registry cannot resolve " + questKey);

        Quest quest = resolved.quest();
        int spawned = 0;
        int missing = 0;
        int totalNeeded = totalMissing(player.serverLevel(), player, resolved, questData);
        String teamId = totalNeeded > 1
                ? questKey + ":" + player.getStringUUID() + ":xeno:" + System.nanoTime()
                : null;

        for (int index = 0; index < quest.getObjectives().size(); index++) {
            QuestObjective objective = quest.getObjectives().get(index);
            if (!(objective instanceof KillObjective kill)
                    || kill.getSpawnMode() != KillObjective.SpawnMode.QUEST) continue;

            int progress = questData.getObjectiveProgress(questKey, index);
            int required = quest.getObjectiveRequired(questData, questKey, index);
            int existing = countQuestEntities(player.serverLevel(), player, questKey, index);
            int needed = missingCount(required, progress, existing);
            if (needed <= 0) continue;

            for (int count = 0; count < needed; count++) {
                SpawnResult result = spawnOne(player, resolved, kill, index,
                        Math.max(1, partySize), difficulty, teamId);
                if (result.success) {
                    spawned++;
                } else {
                    missing++;
                    rememberFailure(player, result.message);
                    XenoPixelsMod.LOGGER.warn("DMZ saga spawn fallback failed for {} objective {}: {}",
                            questKey, index, result.message);
                }
            }
        }

        if (missing == 0) LAST_FAILURE.remove(player.getUUID());
        return new RepairResult(true, spawned, missing,
                missing == 0 ? "Spawn audit complete" : "One or more enemies could not be spawned");
    }

    private static int totalMissing(ServerLevel level, ServerPlayer player,
                                    QuestService.ResolvedQuest resolved, PlayerQuestData data) {
        int total = 0;
        Quest quest = resolved.quest();
        for (int index = 0; index < quest.getObjectives().size(); index++) {
            QuestObjective objective = quest.getObjectives().get(index);
            if (!(objective instanceof KillObjective kill)
                    || kill.getSpawnMode() != KillObjective.SpawnMode.QUEST) continue;
            int required = quest.getObjectiveRequired(data, resolved.questKey(), index);
            int progress = data.getObjectiveProgress(resolved.questKey(), index);
            int existing = countQuestEntities(level, player, resolved.questKey(), index);
            total += missingCount(required, progress, existing);
        }
        return total;
    }

    private static SpawnResult spawnOne(ServerPlayer player, QuestService.ResolvedQuest resolved,
                                        KillObjective kill, int objectiveIndex, int partySize,
                                        Difficulty difficulty, String teamId) {
        EntityType<?> type;
        try {
            type = kill.resolveEntityType();
        } catch (Throwable throwable) {
            return SpawnResult.failure("Entity lookup threw for " + kill.getEntityId() + ": "
                    + throwable.getClass().getSimpleName());
        }
        if (type == null) return SpawnResult.failure("Entity type did not resolve: " + kill.getEntityId());

        ServerLevel level = player.serverLevel();
        Entity entity;
        try {
            entity = type.create(level);
        } catch (Throwable throwable) {
            return SpawnResult.failure("Entity creation threw for " + kill.getEntityId() + ": "
                    + throwable.getClass().getSimpleName());
        }
        if (entity == null) return SpawnResult.failure("Entity creation returned null: " + kill.getEntityId());
        if (!positionSafely(player, entity)) {
            return SpawnResult.failure("No collision-safe spawn position near the player");
        }

        applyQuestMetadata(entity, player, resolved, kill, objectiveIndex, partySize, difficulty, teamId);
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.setTarget(player);
        }

        boolean added;
        try {
            added = level.addFreshEntity(entity);
        } catch (Throwable throwable) {
            return SpawnResult.failure("addFreshEntity threw: " + throwable.getClass().getSimpleName());
        }
        if (!added) return SpawnResult.failure("ServerLevel.addFreshEntity returned false");

        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        XenoPixelsMod.LOGGER.info("Recovered missing DMZ saga enemy {} for {} objective {} at {}",
                typeId, resolved.questKey(), objectiveIndex, entity.blockPosition());
        return SpawnResult.added();
    }

    private static void applyQuestMetadata(Entity entity, ServerPlayer player,
                                           QuestService.ResolvedQuest resolved, KillObjective kill,
                                           int objectiveIndex, int partySize, Difficulty difficulty,
                                           String teamId) {
        CompoundTag tag = entity.getPersistentData();
        Difficulty safeDifficulty = difficulty == null ? Difficulty.NORMAL : difficulty;
        if (safeDifficulty != Difficulty.NORMAL) tag.putString("dmz_difficulty", safeDifficulty.name());
        if (resolved.saga() != null) tag.putString(SAGA_ID_TAG, resolved.saga().getId());
        tag.putString(QUEST_KEY_TAG, resolved.questKey());
        tag.putInt(OBJECTIVE_INDEX_TAG, objectiveIndex);
        tag.putString(QUEST_OWNER_TAG, player.getStringUUID());
        if (teamId != null) tag.putString(QUEST_TEAM_TAG, teamId);

        Quest quest = resolved.quest();
        tag.putDouble("dmz_quest_hp", quest.getScaledKillHealth(kill, partySize));
        tag.putDouble("dmz_quest_melee", quest.getScaledKillMeleeDamage(kill, partySize));
        tag.putDouble("dmz_quest_ki", quest.getScaledKillKiDamage(kill, partySize));
        if (kill.getTextureVariant() >= 0) tag.putInt("dmz_quest_texture_variant", kill.getTextureVariant());
        int aiTier = kill.getAiTier() > 0 ? kill.getAiTier() : safeDifficulty.aiTierId();
        tag.putInt("dmz_quest_ai_tier", aiTier);
        if (!kill.isCanTransform()) tag.putBoolean("dmz_quest_no_transform", true);
        putNullable(tag, "dmz_quest_tf_hp_abs", quest.getScaledTransformHealth(kill, partySize));
        putNullable(tag, "dmz_quest_tf_melee_abs", quest.getScaledTransformMeleeDamage(kill, partySize));
        putNullable(tag, "dmz_quest_tf_ki_abs", quest.getScaledTransformKiDamage(kill, partySize));
        putNullable(tag, "dmz_quest_tf_hp_mult", kill.getTransformHealthMultiplier());
        putNullable(tag, "dmz_quest_tf_melee_mult", kill.getTransformMeleeMultiplier());
        putNullable(tag, "dmz_quest_tf_ki_mult", kill.getTransformKiMultiplier());
        putNullable(tag, "dmz_quest_tf_trigger", kill.getTransformTriggerPercent());
    }

    private static void putNullable(CompoundTag tag, String key, Double value) {
        if (value != null) tag.putDouble(key, value);
    }

    private static boolean positionSafely(ServerPlayer player, Entity entity) {
        ServerLevel level = player.serverLevel();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
            double radius = 8.0 + player.getRandom().nextDouble() * 4.0;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            Double y = nearbySafeY(level, entity, x, z, player.getY());
            if (y == null) {
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(x, player.getY(), z));
                y = (double) surface.getY();
            }
            entity.setPos(x, y, z);
            if (level.noCollision(entity, entity.getBoundingBox())) return true;
        }
        return false;
    }

    private static Double nearbySafeY(ServerLevel level, Entity entity, double x, double z, double playerY) {
        int start = Math.min(level.getMaxBuildHeight() - 2, (int) Math.floor(playerY) + 4);
        int end = Math.max(level.getMinBuildHeight() + 1, (int) Math.floor(playerY) - 12);
        for (int y = start; y >= end; y--) {
            entity.setPos(x, y, z);
            if (!level.noCollision(entity, entity.getBoundingBox())) continue;
            BlockPos below = BlockPos.containing(x, y, z).below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return (double) y;
        }
        return null;
    }

    private static int countQuestEntities(ServerLevel level, ServerPlayer player,
                                          String questKey, int objectiveIndex) {
        int count = 0;
        String owner = player.getStringUUID();
        for (Entity entity : level.getEntities().getAll()) {
            if (!entity.isAlive()) continue;
            CompoundTag tag = entity.getPersistentData();
            if (!questKey.equals(tag.getString(QUEST_KEY_TAG))) continue;
            if (tag.getInt(OBJECTIVE_INDEX_TAG) != objectiveIndex) continue;
            String taggedOwner = tag.getString(QUEST_OWNER_TAG);
            if (!taggedOwner.isEmpty() && !owner.equals(taggedOwner)) continue;
            count++;
        }
        return count;
    }

    private static ActiveQuest activeQuest(ServerPlayer player) {
        PlayerQuestData data = questData(player);
        if (data == null) return null;
        String tracked = data.getTrackedQuestId();
        if (tracked != null && !tracked.isBlank()
                && data.getQuestStatus(tracked) == PlayerQuestData.QuestStatus.ACCEPTED) {
            return new ActiveQuest(tracked, difficultyFor(data, tracked));
        }
        for (String accepted : data.getAcceptedQuestIds()) {
            if (accepted != null && !accepted.isBlank()) {
                return new ActiveQuest(accepted, difficultyFor(data, accepted));
            }
        }
        return null;
    }

    private static Difficulty difficultyFor(PlayerQuestData data, String questKey) {
        Difficulty difficulty = data.getQuestDifficulty(questKey);
        if (difficulty == null) difficulty = data.getDifficulty();
        return difficulty == null ? Difficulty.NORMAL : difficulty;
    }

    private static PlayerQuestData questData(ServerPlayer player) {
        StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        return stats == null ? null : stats.getPlayerQuestData();
    }

    private static RepairResult fail(ServerPlayer player, String message) {
        rememberFailure(player, message);
        return new RepairResult(true, 0, 1, message);
    }

    private static void rememberFailure(ServerPlayer player, String message) {
        LAST_FAILURE.put(player.getUUID(), message == null ? "Unknown failure" : message);
    }

    public record RepairResult(boolean activeQuest, int spawned, int missing, String message) {
        static RepairResult inactive(String message) {
            return new RepairResult(false, 0, 0, message);
        }
    }

    private record AuditKey(UUID playerId, String questKey) {
    }

    private record PendingAudit(UUID playerId, String questKey, int partySize,
                                Difficulty difficulty, int dueTick, int auditNumber) {
    }

    private record ActiveQuest(String questKey, Difficulty difficulty) {
    }

    private record SpawnResult(boolean success, String message) {
        static SpawnResult added() {
            return new SpawnResult(true, "ok");
        }

        static SpawnResult failure(String message) {
            return new SpawnResult(false, message == null ? "unknown" : message);
        }
    }
}
