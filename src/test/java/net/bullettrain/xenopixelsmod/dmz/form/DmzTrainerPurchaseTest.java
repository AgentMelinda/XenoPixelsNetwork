package net.bullettrain.xenopixelsmod.dmz.form;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmzTrainerPurchaseTest {
    @AfterEach
    void clearRegistry() {
        DmzFormMetadataRegistry.applySnapshot("{}");
    }

    @Test
    void thePriceComesFromTheCurrentLevelAndTheLevelAdvancesByOne() {
        DmzFormMetadata metadata = offer(true, List.of(500, 900, 1400));
        DmzTrainerPurchase first = DmzTrainerPurchase.evaluate(metadata, "human", 0, 1000);
        assertTrue(first.allowed());
        assertEquals(500, first.cost());
        assertEquals(1, first.nextLevel());

        DmzTrainerPurchase second = DmzTrainerPurchase.evaluate(metadata, "human", 1, 1000);
        assertTrue(second.allowed());
        assertEquals(900, second.cost());
        assertEquals(2, second.nextLevel());
    }

    @Test
    void insufficientTrainingPointsBlockThePurchase() {
        DmzFormMetadata metadata = offer(true, List.of(500));
        DmzTrainerPurchase result = DmzTrainerPurchase.evaluate(metadata, "human", 0, 499.5F);
        assertFalse(result.allowed());
        assertTrue(result.message().contains("500"));
        assertTrue(DmzTrainerPurchase.evaluate(metadata, "human", 0, 500F).allowed());
    }

    @Test
    void aFormLockedToAnotherRaceIsRefused() {
        DmzFormMetadata metadata = offer(true, List.of(500));
        assertFalse(DmzTrainerPurchase.evaluate(metadata, "saiyan", 0, 9999).allowed());
        assertFalse(DmzTrainerPurchase.evaluate(metadata, null, 0, 9999).allowed());
        assertTrue(DmzTrainerPurchase.evaluate(metadata, "HUMAN", 0, 9999).allowed());
    }

    @Test
    void stackFormsCarryNoRaceSoAnyRaceMayBuyThem() {
        DmzFormMetadata metadata = offer(true, List.of(500));
        metadata.race = "";
        assertTrue(DmzTrainerPurchase.evaluate(metadata, "saiyan", 0, 9999).allowed());
    }

    @Test
    void theFirstLevelNeedsDragonMineZsBuyFromMasterFlag() {
        DmzFormMetadata metadata = offer(false, List.of(500, 900));
        DmzTrainerPurchase locked = DmzTrainerPurchase.evaluate(metadata, "human", 0, 9999);
        assertFalse(locked.allowed());
        assertTrue(locked.message().contains("cannot be purchased"));
        // Later levels are still trainable once the form has been unlocked elsewhere.
        assertTrue(DmzTrainerPurchase.evaluate(metadata, "human", 1, 9999).allowed());
    }

    @Test
    void aFormAtItsConfiguredMaximumLevelCannotBeUpgraded() {
        DmzFormMetadata metadata = offer(true, List.of(500, 900));
        DmzTrainerPurchase result = DmzTrainerPurchase.evaluate(metadata, "human", 2, 9999);
        assertFalse(result.allowed());
        assertTrue(result.message().contains("maximum level"));
    }

    @Test
    void aFormWithMasterLearningDisabledIsNeverTeachable() {
        DmzFormMetadata metadata = offer(true, List.of(500));
        metadata.masterLearningEnabled = false;
        assertFalse(DmzTrainerPurchase.evaluate(metadata, "human", 0, 9999).allowed());
        assertFalse(DmzTrainerPurchase.evaluate(null, "human", 0, 9999).allowed());
    }

    @Test
    void onlyTheConfiguredCustomNpcUuidsAreOfferedTrainerDialogue() {
        UUID trainer = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        DmzFormMetadata metadata = offer(true, List.of(500));
        DmzFormMetadata.TrainerRef ref = new DmzFormMetadata.TrainerRef();
        ref.uuid = trainer.toString().toUpperCase(java.util.Locale.ROOT);
        ref.name = "Master Xeno";
        ref.dimension = "minecraft:overworld";
        metadata.customNpcTrainers = new ArrayList<>(List.of(ref));

        JsonObject snapshot = new JsonObject();
        snapshot.add("normal:human:xeno_custom", DmzFormMetadataRegistry.gson().toJsonTree(metadata));
        DmzFormMetadataRegistry.applySnapshot(snapshot.toString());

        assertEquals(1, DmzFormMetadataRegistry.trainerOfferings(trainer).size());
        assertEquals("radiant_skill",
                DmzFormMetadataRegistry.trainerOfferings(trainer).get(0).formType);
        assertTrue(DmzFormMetadataRegistry.trainerOfferings(stranger).isEmpty());
        assertTrue(DmzFormMetadataRegistry.trainerOfferings(null).isEmpty());
    }

    @Test
    void aTrainerOnAFormWithMasterLearningOffIsNotOffered() {
        DmzFormMetadata metadata = offer(true, List.of(500));
        metadata.masterLearningEnabled = false;
        UUID trainer = UUID.randomUUID();
        DmzFormMetadata.TrainerRef ref = new DmzFormMetadata.TrainerRef();
        ref.uuid = trainer.toString();
        metadata.customNpcTrainers = new ArrayList<>(List.of(ref));

        JsonObject snapshot = new JsonObject();
        snapshot.add("normal:human:xeno_custom", DmzFormMetadataRegistry.gson().toJsonTree(metadata));
        DmzFormMetadataRegistry.applySnapshot(snapshot.toString());

        assertTrue(DmzFormMetadataRegistry.trainerOfferings(trainer).isEmpty());
    }

    private static DmzFormMetadata offer(boolean buyFromMaster, List<Integer> costs) {
        DmzFormMetadata metadata = new DmzFormMetadata();
        metadata.race = "human";
        metadata.group = "xeno_custom";
        metadata.formType = "radiant_skill";
        metadata.masterLearningEnabled = true;
        metadata.buyFromMaster = buyFromMaster;
        metadata.skillCosts = new ArrayList<>(costs);
        return metadata;
    }
}
