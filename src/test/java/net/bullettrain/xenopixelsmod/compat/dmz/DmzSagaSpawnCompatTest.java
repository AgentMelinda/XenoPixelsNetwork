package net.bullettrain.xenopixelsmod.compat.dmz;

import com.dragonminez.common.quest.objectives.KillObjective;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmzSagaSpawnCompatTest {
    private static final String QUEST = "dragonminez:saiyan_saga/raditz";
    private static final String OWNER = "398c2204-5945-4b0f-9061-c215c1056b27";

    @Test
    void missingCountSubtractsProgressAndLivingQuestEnemies() {
        assertEquals(3, DmzSagaSpawnCompat.missingCount(5, 1, 1));
        assertEquals(0, DmzSagaSpawnCompat.missingCount(1, 1, 0));
        assertEquals(0, DmzSagaSpawnCompat.missingCount(1, 0, 2));
        assertEquals(0, DmzSagaSpawnCompat.missingCount(-1, 0, 0));
    }

    @Test
    void retriesAreBoundedAndOnlyRunAfterSpawnOrFailure() {
        assertTrue(DmzSagaSpawnCompat.shouldRetry(1, 1, 0));
        assertTrue(DmzSagaSpawnCompat.shouldRetry(2, 0, 1));
        assertFalse(DmzSagaSpawnCompat.shouldRetry(1, 0, 0));
        assertFalse(DmzSagaSpawnCompat.shouldRetry(3, 1, 0));
    }

    @Test
    void matchingEntityRequiresAllDmzKillCreditMetadata() {
        KillObjective objective = zombieObjective();
        CompoundTag tag = matchingTag();

        assertEquals(DmzSagaSpawnCompat.EntityMatch.MATCHING,
                DmzSagaSpawnCompat.classifyQuestEntity(
                        tag, EntityType.ZOMBIE, Set.of(OWNER), QUEST, 2, objective));

        tag.remove("dmz_quest_owner");
        assertEquals(DmzSagaSpawnCompat.EntityMatch.MALFORMED,
                DmzSagaSpawnCompat.classifyQuestEntity(
                        tag, EntityType.ZOMBIE, Set.of(OWNER), QUEST, 2, objective));
    }

    @Test
    void unrelatedOwnerObjectiveOrQuestDoesNotSuppressRecovery() {
        KillObjective objective = zombieObjective();
        CompoundTag tag = matchingTag();

        assertEquals(DmzSagaSpawnCompat.EntityMatch.UNRELATED,
                DmzSagaSpawnCompat.classifyQuestEntity(
                        tag, EntityType.ZOMBIE, Set.of("another-owner"), QUEST, 2, objective));
        assertEquals(DmzSagaSpawnCompat.EntityMatch.UNRELATED,
                DmzSagaSpawnCompat.classifyQuestEntity(
                        tag, EntityType.ZOMBIE, Set.of(OWNER), QUEST, 3, objective));
        assertEquals(DmzSagaSpawnCompat.EntityMatch.UNRELATED,
                DmzSagaSpawnCompat.classifyQuestEntity(
                        tag, EntityType.ZOMBIE, Set.of(OWNER), "dragonminez:other", 2, objective));
    }

    @Test
    void wrongEntityTypeDoesNotSuppressRecovery() {
        assertEquals(DmzSagaSpawnCompat.EntityMatch.MISMATCHED,
                DmzSagaSpawnCompat.classifyQuestEntity(
                        matchingTag(), EntityType.SKELETON, Set.of(OWNER), QUEST, 2, zombieObjective()));
    }

    @Test
    void untaggedEntitiesAreIgnored() {
        assertEquals(DmzSagaSpawnCompat.EntityMatch.UNRELATED,
                DmzSagaSpawnCompat.classifyQuestEntity(
                        new CompoundTag(), EntityType.ZOMBIE, Set.of(OWNER), QUEST, 2, zombieObjective()));
    }

    private static CompoundTag matchingTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dmz_quest_key", QUEST);
        tag.putInt("dmz_quest_objective_index", 2);
        tag.putString("dmz_quest_owner", OWNER);
        return tag;
    }

    private static KillObjective zombieObjective() {
        return new KillObjective("minecraft:zombie", 1, 20.0, 2.0, 0.0,
                KillObjective.SpawnMode.QUEST, KillObjective.CountMode.QUEST_SPAWNED_ONLY,
                -1, 0, true, null, null, null, null, null, null, null);
    }
}
