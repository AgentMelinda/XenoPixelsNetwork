package com.dragonminez.common.init.entities.redribbon;

import com.dragonminez.common.util.BetaWhitelist;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

public class RedRibbonSoldierEntity extends RedRibbonEntity {
   private static final EntityDataAccessor<String> SKIN_OWNER = SynchedEntityData.defineId(RedRibbonSoldierEntity.class, EntityDataSerializers.STRING);
   private static final String NBT_SKIN_OWNER = "SkinOwner";

   public RedRibbonSoldierEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
   }

   public static Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 25.0)
         .add(Attributes.MOVEMENT_SPEED, 0.2)
         .add(Attributes.ATTACK_DAMAGE, 3.5)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.1);
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(SKIN_OWNER, "");
   }

   public void addAdditionalSaveData(CompoundTag pCompound) {
      super.addAdditionalSaveData(pCompound);
      pCompound.putString("SkinOwner", this.getSkinOwner());
   }

   public void readAdditionalSaveData(CompoundTag pCompound) {
      super.readAdditionalSaveData(pCompound);
      if (pCompound.contains("SkinOwner")) {
         this.setSkinOwner(pCompound.getString("SkinOwner"));
      } else {
         this.rerollSkinOwner();
      }
   }

   public String getSkinOwner() {
      return (String)this.entityData.get(SKIN_OWNER);
   }

   public void setSkinOwner(String skinOwner) {
      this.entityData.set(SKIN_OWNER, skinOwner == null ? "" : skinOwner);
   }

   public void rerollSkinOwner() {
      this.setSkinOwner(BetaWhitelist.getRandomBetatester(this.random));
   }

   public SpawnGroupData finalizeSpawn(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData) {
      if (this.getSkinOwner().isEmpty()) {
         this.rerollSkinOwner();
      }

      return super.finalizeSpawn(pLevel, pDifficulty, pReason, pSpawnData);
   }
}
