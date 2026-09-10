package com.dragonminez.common.network.C2S;

import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ResourceSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.MasterLocation;
import com.dragonminez.common.util.ITTeleportHelper;
import com.dragonminez.compat.network.NetworkEvent;
import com.dragonminez.server.events.players.combat.DashHandler;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

public class InstantTransmissionTravelC2S {
   private final String masterId;

   public InstantTransmissionTravelC2S(String masterId) {
      this.masterId = masterId;
   }

   public InstantTransmissionTravelC2S(FriendlyByteBuf buf) {
      this.masterId = buf.readUtf(256);
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeUtf(this.masterId);
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
                           int skillLevel = data.getSkills().getSkillLevel("instant_transmission");
                           if (skillLevel >= 5) {
                              Map<String, MasterLocation> masters = data.getCharacter().getInteractedMasters();
                              if (!masters.containsKey(this.masterId)) {
                                 fail(player, "not_found");
                              } else {
                                 MasterLocation masterData = masters.get(this.masterId);
                                 String currentDim = player.level().dimension().location().toString();
                                 boolean sameDimension = masterData.getDimension().equals(currentDim);
                                 if (skillLevel < 10 && !sameDimension) {
                                    fail(player, "too_far");
                                 } else {
                                    ResourceLocation targetId = ResourceLocation.tryParse(masterData.getDimension());
                                    if (targetId == null) {
                                       fail(player, "unknown");
                                    } else {
                                       ResourceKey<Level> targetKey = ResourceKey.create(Registries.DIMENSION, targetId);
                                       ServerLevel targetLevel = player.server.getLevel(targetKey);
                                       if (targetLevel == null) {
                                          fail(player, "unknown");
                                       } else {
                                          BlockPos center = masterData.getPosition();
                                          boolean bypassCosts = player.isCreative() || player.isSpectator();
                                          if (!bypassCosts && data.getCooldowns().hasCooldown("TeleportCooldown")) {
                                             fail(player, "cooldown");
                                          } else {
                                             double distance = sameDimension
                                                ? Math.sqrt(
                                                   player.distanceToSqr((double)center.getX() + 0.5, (double)center.getY(), (double)center.getZ() + 0.5)
                                                )
                                                : 0.0;
                                             int kiCost = DashHandler.getFlyDashKiCost() * 5 + ITTeleportHelper.extraKiCostForDistance(distance);
                                             if (!bypassCosts) {
                                                if (data.getResources().getCurrentEnergy() < (float)kiCost) {
                                                   fail(player, "no_ki");
                                                   return;
                                                }

                                                data.getResources().removeEnergy((float)kiCost);
                                                NetworkHandler.sendToTrackingEntityAndSelf(new ResourceSyncS2C(player), player);
                                                ITTeleportHelper.applyTeleportCooldown(player, data);
                                             }

                                             if (player.isVehicle()) {
                                                player.stopRiding();
                                             }

                                             BlockPos safePos = ITTeleportHelper.findSafeTeleportPos(targetLevel, center);
                                             double dX = (double)(center.getX() - safePos.getX());
                                             double dZ = (double)(center.getZ() - safePos.getZ());
                                             float yaw = (float)(Math.atan2(dZ, dX) * (180.0 / Math.PI)) - 90.0F;
                                             player.teleportTo(
                                                targetLevel,
                                                (double)safePos.getX() + 0.5,
                                                (double)safePos.getY(),
                                                (double)safePos.getZ() + 0.5,
                                                yaw,
                                                player.getXRot()
                                             );
                                             player.playNotifySound((SoundEvent)MainSounds.TP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     );
               }
            }
         );
      ctx.get().setPacketHandled(true);
   }

   private static void fail(ServerPlayer player, String reason) {
      player.displayClientMessage(Component.translatable("gui.dragonminez.transmission.fail." + reason), true);
   }
}
