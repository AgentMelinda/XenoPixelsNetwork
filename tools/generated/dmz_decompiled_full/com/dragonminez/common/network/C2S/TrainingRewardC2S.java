package com.dragonminez.common.network.C2S;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.TrainingConfig;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.PacketRateLimiter;
import com.dragonminez.common.network.TrainingSessionTracker;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

public class TrainingRewardC2S {
   private static final long MIN_CLAIM_INTERVAL_TICKS = 100L;
   private final String minigameId;
   private final int levelsCleared;

   public TrainingRewardC2S(String minigameId, int levelsCleared) {
      this.minigameId = minigameId;
      this.levelsCleared = levelsCleared;
   }

   public TrainingRewardC2S(FriendlyByteBuf buf) {
      this.minigameId = buf.readUtf(32);
      this.levelsCleared = buf.readInt();
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeUtf(this.minigameId, 32);
      buf.writeInt(this.levelsCleared);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> {
         ServerPlayer player = ctx.get().getSender();
         if (player != null && this.levelsCleared > 0) {
            long now = player.level().getGameTime();
            if (PacketRateLimiter.allow(player.getUUID(), "training_reward", now, 100L)) {
               int plausibleLevels = TrainingSessionTracker.plausibleLevels(player.getUUID(), now);
               if (plausibleLevels > 0) {
                  int clampedLevelsCleared = Math.min(this.levelsCleared, plausibleLevels);
                  StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(statsData -> {
                     TrainingConfig config = ConfigManager.getTrainingConfig();
                     TrainingConfig.MinigameSettings settings = config.getSettings(this.minigameId);
                     int currentTpc = statsData.getSingleStatCost(statsData.getStats().getTotalStats());
                     float tpsPerLevel = config.computeTpsPerLevel(currentTpc, settings);
                     float totalReward = tpsPerLevel * (float)clampedLevelsCleared;
                     float limit = settings.getTpsLimitPerGame();
                     if (limit > 0.0F && totalReward > limit) {
                        totalReward = limit;
                     }

                     if (!(totalReward <= 0.0F)) {
                        statsData.getResources().addTrainingPoints(totalReward);
                        if (!statsData.getCharacter().getKnownMinigames().contains(this.minigameId)) {
                           statsData.getCharacter().addKnownMinigame(this.minigameId);
                        }

                        player.playSound(SoundEvents.PLAYER_LEVELUP, 0.6F, 1.0F);
                        NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
                     }
                  });
               }
            }
         }
      });
      ctx.get().setPacketHandled(true);
   }
}
