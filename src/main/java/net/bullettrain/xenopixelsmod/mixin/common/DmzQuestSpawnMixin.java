package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.quest.Difficulty;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.QuestService;
import net.bullettrain.xenopixelsmod.compat.dmz.DmzSagaSpawnCompat;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Verifies DMZ quest enemies after both quest start and resummon. */
@Mixin(value = QuestService.class, remap = false)
public abstract class DmzQuestSpawnMixin {
    @Inject(method = "spawnKillObjectives", at = @At("RETURN"))
    private static void xenopixels$verifyQuestEnemies(ServerPlayer player,
                                                       QuestService.ResolvedQuest resolvedQuest,
                                                       PlayerQuestData questData,
                                                       int partySize,
                                                       Difficulty difficulty,
                                                       CallbackInfo callbackInfo) {
        DmzSagaSpawnCompat.schedule(player, resolvedQuest, partySize, difficulty);
    }
}
