package net.bullettrain.xenopixelsmod.capability;

import net.minecraft.nbt.CompoundTag;
import net.bullettrain.xenopixelsmod.features.progression.QuestAvailability;
import net.bullettrain.xenopixelsmod.features.progression.QuestRuntime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewedDialoguesTest {
    @Test
    void beforeAndAfterGatesUseRecordedDialogueId() {
        XenoPlayerData data = new XenoPlayerData();
        QuestAvailability before = new QuestAvailability(List.of(),
                List.of(new QuestAvailability.DialogGate("village/greeting",
                        QuestAvailability.DialogState.BEFORE)), "always",
                List.of(), List.of(), 0);
        QuestAvailability after = new QuestAvailability(List.of(),
                List.of(new QuestAvailability.DialogGate("village/greeting",
                        QuestAvailability.DialogState.AFTER)), "always",
                List.of(), List.of(), 0);
        assertTrue(QuestRuntime.available(null, data, before));
        assertFalse(QuestRuntime.available(null, data, after));
        data.recordViewedDialogue("village/greeting");
        assertFalse(QuestRuntime.available(null, data, before));
        assertTrue(QuestRuntime.available(null, data, after));
    }

    @Test
    void sharedDialogueReadStateSurvivesSaveAndDeathCopy() {
        XenoPlayerData data = new XenoPlayerData();
        data.recordViewedDialogue("  Village/Greeting  ");
        data.recordViewedDialogue("<npc>");
        assertTrue(data.hasViewedDialogue("village/greeting"));
        assertFalse(data.hasViewedDialogue("<npc>"));

        CompoundTag saved = new CompoundTag();
        data.saveNBT(saved);
        XenoPlayerData loaded = new XenoPlayerData();
        loaded.loadNBT(saved);
        assertTrue(loaded.hasViewedDialogue("village/greeting"));

        XenoPlayerData respawned = new XenoPlayerData();
        respawned.copyFrom(loaded);
        assertTrue(respawned.hasViewedDialogue("village/greeting"));
    }
}
