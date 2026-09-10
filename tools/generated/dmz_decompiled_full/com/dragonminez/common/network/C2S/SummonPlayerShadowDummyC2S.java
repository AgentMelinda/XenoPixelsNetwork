package com.dragonminez.common.network.C2S;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.entities.ShadowDummyEntity;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.BonusStats;
import com.dragonminez.common.util.AttributeMods;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

public class SummonPlayerShadowDummyC2S {
   public static final String TAG_PLAYER_SHADOW = "dmz_player_shadow";
   public static final String BONUS_KEY = "dmz_player_shadow_dummy";
   public static final UUID SHADOW_HP_MODIFIER_UUID = UUID.fromString("4a9e6f8b-2c1d-4e3f-a5b6-c7d8e9f01234");
   private final int percent;

   public SummonPlayerShadowDummyC2S(int percent) {
      this.percent = percent;
   }

   public SummonPlayerShadowDummyC2S(FriendlyByteBuf buf) {
      this.percent = buf.readInt();
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeInt(this.percent);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get()
         .enqueueWork(
            () -> {
               ServerPlayer player = ctx.get().getSender();
               if (player != null) {
                  StatsProvider.get(StatsCapability.INSTANCE, player)
                     .ifPresent(
                        data -> {
                           String playerName = player.getGameProfile().getName();
                           boolean hasKill = data.getStatus().getShadowDummyKillCount() > 0;
                           boolean hasKiControl = data.getSkills().hasSkill("kicontrol");
                           boolean hasKiManip = data.getSkills().getSkillLevel("kimanipulation") >= 5;
                           if (hasKill && hasKiControl && hasKiManip) {
                              int pct = Mth.clamp(this.percent, 25, 75);
                              clearPlayerShadowDummy(player, data);
                              ServerLevel level = player.serverLevel();
                              EntityType<?> entityType = (EntityType<?>)MainEntities.SHADOW_DUMMY.get();
                              if (entityType.create(level) instanceof ShadowDummyEntity dummy) {
                                 dummy.setPos(player.getX(), player.getY(), player.getZ());
                                 dummy.copyStatsFromPlayerWithPercent(player, pct);
                                 dummy.getPersistentData().putString("dmz_quest_owner", player.getStringUUID());
                                 dummy.getPersistentData().putBoolean("dmz_player_shadow", true);
                                 dummy.getPersistentData().putInt("dmz_shadow_percent", pct);
                                 if (!level.addFreshEntity(dummy)) {
                                    dummy.discard();
                                    LogUtil.warn(
                                       Env.SERVER,
                                       "Shadow clone (minigame) FAILED to spawn (addFreshEntity rejected, e.g. protected/spawn-blocked area) for player {}",
                                       playerName
                                    );
                                 } else {
                                    LogUtil.info(
                                       Env.SERVER,
                                       "Shadow clone (minigame) spawned for player {} at {}% ({}, {}, {})",
                                       playerName,
                                       pct,
                                       (int)player.getX(),
                                       (int)player.getY(),
                                       (int)player.getZ()
                                    );
                                    data.getStatus().setActiveShadowDummyUUID(dummy.getUUID());
                                    data.getStatus().setShadowDummyPercent(pct);
                                    applyPenalties(player, data, pct);
                                    NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
                                 }
                              } else {
                                 LogUtil.warn(Env.SERVER, "Shadow clone (minigame) FAILED to spawn (entity creation returned null) for player {}", playerName);
                              }
                           } else {
                              LogUtil.warn(
                                 Env.SERVER,
                                 "Shadow clone (minigame) FAILED to spawn (requirements not met: kill={}, kicontrol={}, kimanip>=5={}) for player {}",
                                 hasKill,
                                 hasKiControl,
                                 hasKiManip,
                                 playerName
                              );
                              player.sendSystemMessage(Component.translatable("gui.dragonminez.shadow_dummy.requirements_not_met"));
                           }
                        }
                     );
               }
            }
         );
      ctx.get().setPacketHandled(true);
   }

   public static void clearPlayerShadowDummy(ServerPlayer player, StatsData data) {
      if (data.getStatus().hasActiveShadowDummy()) {
         discardDummy(player.getServer(), data.getStatus().getActiveShadowDummyUUID());
         restoreOwner(player, data);
      }
   }

   private static void discardDummy(MinecraftServer server, UUID dummyUUID) {
      if (server != null && dummyUUID != null) {
         for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(dummyUUID);
            if (e != null) {
               e.discard();
               return;
            }
         }
      }
   }

   private static void restoreOwner(ServerPlayer player, StatsData data) {
      removePenalties(player, data);
      data.getStatus().setActiveShadowDummyUUID(null);
      data.getStatus().setShadowDummyPercent(0);
      NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
   }

   public static void dismissByDummy(ShadowDummyEntity dummy) {
      if (!dummy.getPersistentData().getBoolean("dmz_player_shadow")) {
         dummy.discard();
      } else {
         MinecraftServer server = dummy.getServer();
         ServerPlayer owner = null;
         if (server != null) {
            try {
               owner = server.getPlayerList().getPlayer(UUID.fromString(dummy.getPersistentData().getString("dmz_quest_owner")));
            } catch (Exception var4) {
            }
         }

         if (owner != null) {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, owner).orElse(null);
            if (data != null && data.getStatus().hasActiveShadowDummy() && dummy.getUUID().equals(data.getStatus().getActiveShadowDummyUUID())) {
               restoreOwner(owner, data);
            }
         }

         dummy.discard();
      }
   }

   public static void applyPenalties(ServerPlayer player, StatsData data, int pct) {
      double factor = 1.0 - (double)pct / 100.0;
      BonusStats bonus = data.getBonusStats();
      bonus.addBonus("STR", "dmz_player_shadow_dummy", "*", factor, true);
      bonus.addBonus("SKP", "dmz_player_shadow_dummy", "*", factor, true);
      bonus.addBonus("PWR", "dmz_player_shadow_dummy", "*", factor, true);
      bonus.addBonus("DEF", "dmz_player_shadow_dummy", "*", factor, true);
      bonus.addBonus("STM", "dmz_player_shadow_dummy", "*", factor, true);
      AttributeInstance maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
      if (maxHealthAttr != null) {
         maxHealthAttr.removeModifier(AttributeMods.id(SHADOW_HP_MODIFIER_UUID));
         maxHealthAttr.addPermanentModifier(
            AttributeMods.of(SHADOW_HP_MODIFIER_UUID, "Shadow Dummy HP Penalty", -((double)pct / 100.0), Operation.ADD_MULTIPLIED_TOTAL)
         );
         float newMax = (float)maxHealthAttr.getValue();
         if (player.getHealth() > newMax) {
            player.setHealth(newMax);
         }
      }
   }

   public static void removePenalties(ServerPlayer player, StatsData data) {
      data.getBonusStats().removeAllBonuses("dmz_player_shadow_dummy");
      AttributeInstance maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
      if (maxHealthAttr != null) {
         maxHealthAttr.removeModifier(AttributeMods.id(SHADOW_HP_MODIFIER_UUID));
      }
   }
}
