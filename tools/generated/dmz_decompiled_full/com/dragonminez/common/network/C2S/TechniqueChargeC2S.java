package com.dragonminez.common.network.C2S;

import com.dragonminez.common.compat.CameraAimHelper;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.TechniqueData;
import com.dragonminez.common.stats.techniques.TechniqueDispatcher;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

public class TechniqueChargeC2S {
   private final TechniqueChargeC2S.Action action;
   private final int slot;
   private final boolean holding;
   private final int targetId;
   private final float aimX;
   private final float aimY;
   private final float aimZ;

   private TechniqueChargeC2S(TechniqueChargeC2S.Action action, int slot, boolean holding, int targetId, Vec3 aim) {
      this.action = action;
      this.slot = slot;
      this.holding = holding;
      this.targetId = targetId;
      this.aimX = (float)aim.x;
      this.aimY = (float)aim.y;
      this.aimZ = (float)aim.z;
   }

   public static TechniqueChargeC2S start(int slot, int targetId, Vec3 aim) {
      return new TechniqueChargeC2S(TechniqueChargeC2S.Action.START, slot, false, targetId, aim);
   }

   public static TechniqueChargeC2S setHolding(boolean holding, Vec3 aim) {
      return new TechniqueChargeC2S(TechniqueChargeC2S.Action.SET_HOLDING, -1, holding, -1, aim);
   }

   public static TechniqueChargeC2S updateAim(Vec3 aim) {
      return new TechniqueChargeC2S(TechniqueChargeC2S.Action.UPDATE_AIM, -1, false, -1, aim);
   }

   public TechniqueChargeC2S(FriendlyByteBuf buf) {
      this.action = (TechniqueChargeC2S.Action)buf.readEnum(TechniqueChargeC2S.Action.class);
      this.slot = buf.readVarInt();
      this.holding = buf.readBoolean();
      this.targetId = buf.readInt();
      this.aimX = buf.readFloat();
      this.aimY = buf.readFloat();
      this.aimZ = buf.readFloat();
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeEnum(this.action);
      buf.writeVarInt(this.slot);
      buf.writeBoolean(this.holding);
      buf.writeInt(this.targetId);
      buf.writeFloat(this.aimX);
      buf.writeFloat(this.aimY);
      buf.writeFloat(this.aimZ);
   }

   public static void handle(TechniqueChargeC2S msg, Supplier<NetworkEvent.Context> ctx) {
      ctx.get()
         .enqueueWork(
            () -> {
               ServerPlayer player = ctx.get().getSender();
               if (player != null) {
                  CameraAimHelper.store(player, new Vec3((double)msg.aimX, (double)msg.aimY, (double)msg.aimZ));
                  StatsProvider.get(StatsCapability.INSTANCE, player)
                     .ifPresent(
                        data -> {
                           if (data.getStatus().isHasCreatedCharacter() && !data.getStatus().isStunned()) {
                              switch (msg.action) {
                                 case START:
                                    if (!player.isSpectator() && (!data.getStatus().isFused() || data.getStatus().isFusionLeader())) {
                                       if (msg.slot >= 0 && msg.slot < 8) {
                                          String techId = data.getTechniques().getEquippedSlots()[msg.slot];
                                          TechniqueData selected = techId != null && !techId.isEmpty()
                                             ? data.getTechniques().getUnlockedTechniques().get(techId)
                                             : null;
                                          boolean meetsRequirements = data.getSkills().getSkillLevel("kicontrol") > 0
                                             && data.getResources().getPowerRelease() >= 5
                                             && player.getMainHandItem().isEmpty();
                                          if (!(selected instanceof KiAttackData kiAttack)
                                             || !meetsRequirements
                                             || data.getCooldowns().hasCooldown("TechniqueCooldown_" + kiAttack.getId())) {
                                             data.getTechniques().clearTechniqueCharge();
                                             break;
                                          }

                                          if (player.isPassenger() && TechniqueDispatcher.restrictsMovementWhileCharging(kiAttack.getKiType())) {
                                             data.getTechniques().clearTechniqueCharge();
                                          } else {
                                             data.getTechniques().selectSlot(msg.slot);
                                             data.getTechniques().startTechniqueCharge(kiAttack.getId());
                                             data.getTechniques().setHomingTargetId(msg.targetId);
                                             NeoForge.EVENT_BUS.post(new DMZEvent.KiAttackCastEvent(player, data, kiAttack));
                                          }
                                       } else {
                                          data.getTechniques().clearTechniqueCharge();
                                       }
                                    } else {
                                       data.getTechniques().clearTechniqueCharge();
                                    }
                                    break;
                                 case SET_HOLDING:
                                    if (data.getTechniques().isTechniqueChargeActive() || data.getTechniques().isTechniqueCharging()) {
                                       data.getTechniques().setChargeHolding(msg.holding);
                                    }
                              }

                              if (msg.action != TechniqueChargeC2S.Action.UPDATE_AIM) {
                                 NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
                              }
                           } else {
                              data.getTechniques().clearTechniqueCharge();
                              NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
                           }
                        }
                     );
               }
            }
         );
      ctx.get().setPacketHandled(true);
   }

   public static enum Action {
      START,
      SET_HOLDING,
      UPDATE_AIM;
   }
}
