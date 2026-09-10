package com.dragonminez.common.init.entities.questnpc;

import com.dragonminez.common.alignment.NpcDispositionService;
import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.common.init.entities.MastersEntity;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.OpenQuestNPCDialogueS2C;
import com.dragonminez.common.quest.QuestService;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class QuestNPCEntity extends MastersEntity {
   private static final EntityDataAccessor<String> NPC_ID = SynchedEntityData.defineId(QuestNPCEntity.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<String> NPC_MODEL = SynchedEntityData.defineId(QuestNPCEntity.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<String> NPC_TEXTURE = SynchedEntityData.defineId(QuestNPCEntity.class, EntityDataSerializers.STRING);
   private static final double HOME_DRIFT_THRESHOLD_SQR = 1.0;
   private boolean hasHome = false;
   private double homeX;
   private double homeZ;

   public QuestNPCEntity(EntityType<? extends PathfinderMob> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.setPersistenceRequired();
   }

   public void setHomePosition(double x, double z) {
      this.homeX = x;
      this.homeZ = z;
      this.hasHome = true;
   }

   public boolean hasHome() {
      return this.hasHome;
   }

   public void tick() {
      super.tick();
      if (!this.level().isClientSide && this.hasHome) {
         double dx = this.getX() - this.homeX;
         double dz = this.getZ() - this.homeZ;
         if (dx * dx + dz * dz > 1.0) {
            this.getNavigation().stop();
            this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
            this.hasImpulse = true;
            this.moveTo(this.homeX, this.getY(), this.homeZ, this.getYRot(), this.getXRot());
         }
      }
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (!source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC) && !source.is(DamageTypes.GENERIC_KILL)) {
         if (source.getEntity() instanceof Player player && TargetHelper.getRelation(player, this) == TargetHelper.Relation.HOSTILE) {
            return super.hurt(source, amount);
         }

         return false;
      } else {
         return super.hurt(source, amount);
      }
   }

   @Override
   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(NPC_ID, "generic_npc");
      builder.define(NPC_MODEL, "");
      builder.define(NPC_TEXTURE, "");
   }

   public String getNpcId() {
      return (String)this.entityData.get(NPC_ID);
   }

   public void setNpcId(String npcId) {
      this.entityData.set(NPC_ID, npcId);
   }

   public String getNpcModel() {
      return (String)this.entityData.get(NPC_MODEL);
   }

   public void setNpcModel(String model) {
      this.entityData.set(NPC_MODEL, model != null ? model : "");
   }

   public String getNpcTexture() {
      return (String)this.entityData.get(NPC_TEXTURE);
   }

   public void setNpcTexture(String texture) {
      this.entityData.set(NPC_TEXTURE, texture != null ? texture : "");
   }

   public String getModelKey() {
      String model = this.getNpcModel();
      return model != null && !model.isEmpty() ? model : this.getNpcId();
   }

   public String getTextureKey() {
      String texture = this.getNpcTexture();
      return texture != null && !texture.isEmpty() ? texture : this.getNpcId();
   }

   public Component getName() {
      return Component.translatable("entity.dragonminez.questnpc." + this.getNpcId());
   }

   public Component getDisplayName() {
      return this.getName();
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putString("QuestNpcId", this.getNpcId());
      String model = this.getNpcModel();
      if (model != null && !model.isEmpty()) {
         tag.putString("QuestNpcModel", model);
      }

      String texture = this.getNpcTexture();
      if (texture != null && !texture.isEmpty()) {
         tag.putString("QuestNpcTexture", texture);
      }

      if (this.hasHome) {
         tag.putBoolean("QuestNpcHasHome", true);
         tag.putDouble("QuestNpcHomeX", this.homeX);
         tag.putDouble("QuestNpcHomeZ", this.homeZ);
      }
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      if (tag.contains("QuestNpcId")) {
         this.setNpcId(tag.getString("QuestNpcId"));
      }

      if (tag.contains("QuestNpcModel")) {
         this.setNpcModel(tag.getString("QuestNpcModel"));
      }

      if (tag.contains("QuestNpcTexture")) {
         this.setNpcTexture(tag.getString("QuestNpcTexture"));
      }

      if (tag.getBoolean("QuestNpcHasHome")) {
         this.setHomePosition(tag.getDouble("QuestNpcHomeX"), tag.getDouble("QuestNpcHomeZ"));
      }
   }

   @Override
   protected InteractionResult mobInteract(Player pPlayer, InteractionHand pHand) {
      if (!this.level().isClientSide && pPlayer instanceof ServerPlayer serverPlayer) {
         String npcId = this.getNpcId();
         StatsProvider.get(StatsCapability.INSTANCE, serverPlayer)
            .ifPresent(
               data -> {
                  if (!data.getStatus().isHasCreatedCharacter()) {
                     serverPlayer.displayClientMessage(Component.translatable("gui.dragonminez.lines.generic.createcharacter"), true);
                  } else {
                     Component blocker = NpcDispositionService.getDialogueBlocker(serverPlayer, this);
                     if (blocker != null) {
                        serverPlayer.displayClientMessage(blocker, true);
                     } else {
                        QuestService.NPCQuestOptions options = QuestService.collectNpcQuestOptions(npcId, data);
                        NetworkHandler.sendToPlayer(
                           new OpenQuestNPCDialogueS2C(
                              npcId, options.offerableQuestIds(), options.turnInQuestIds(), options.inProgressQuestIds(), false, this.getId()
                           ),
                           serverPlayer
                        );
                     }
                  }
               }
            );
         return InteractionResult.SUCCESS;
      } else {
         return InteractionResult.SUCCESS;
      }
   }
}
