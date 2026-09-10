package com.dragonminez.common.init.entities.namek;

import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.entities.IBattlePower;
import com.dragonminez.common.init.entities.goals.VillageAlertSystem;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Dynamic;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;

public class NamekVillagerEntity extends Villager implements GeoEntity {
   private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);
   private static final List<NamekVillagerEntity.CustomTrade> TRADES = new ArrayList<>();

   public NamekVillagerEntity(EntityType<? extends Villager> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.setPersistenceRequired();
      this.setVillagerData(this.getVillagerData().setProfession(VillagerProfession.FLETCHER));
      if (this instanceof IBattlePower bp) {
         bp.setBattlePower(20);
      }
   }

   public static Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 100.0)
         .add(Attributes.MOVEMENT_SPEED, 0.2)
         .add(Attributes.ATTACK_DAMAGE, 5.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(1, new FloatGoal(this));
      this.goalSelector.addGoal(2, new PanicGoal(this, 2.0));
      this.goalSelector.addGoal(3, new RandomStrollGoal(this, 1.2));
      this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
      this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
   }

   public boolean isPersistenceRequired() {
      return true;
   }

   public void checkDespawn() {
   }

   public boolean hurt(DamageSource source, float amount) {
      boolean isHurt = super.hurt(source, amount);
      if (isHurt && source.getEntity() instanceof Player) {
         Player player = (Player)source.getEntity();
         VillageAlertSystem.alertAll(player);
      }

      return isHurt;
   }

   public boolean wantsToSpawnGolem(long pGameTime) {
      return false;
   }

   protected Brain<?> makeBrain(Dynamic<?> dynamic) {
      Brain<Villager> brain = super.makeBrain(dynamic);
      this.registerBrainGoals(brain);
      return brain;
   }

   public void refreshBrain(ServerLevel serverLevel) {
      Brain<Villager> brain = this.getBrain();
      brain.stopAll(serverLevel, this);
      this.brain = brain.copyWithoutBehaviors();
      brain.setSchedule(Schedule.VILLAGER_DEFAULT);
      brain.addActivity(Activity.CORE, VillagerGoalPackages.getCorePackage(VillagerProfession.FLETCHER, 0.5F));
      brain.addActivity(Activity.IDLE, VillagerGoalPackages.getIdlePackage(VillagerProfession.FLETCHER, 0.5F));
      brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
      brain.setDefaultActivity(Activity.IDLE);
      brain.setActiveActivityIfPossible(Activity.IDLE);
   }

   protected void registerBrainGoals(Brain<Villager> brain) {
      brain.setSchedule(Schedule.VILLAGER_DEFAULT);
      brain.addActivity(Activity.CORE, VillagerGoalPackages.getCorePackage(VillagerProfession.FLETCHER, 0.5F));
      brain.addActivity(Activity.IDLE, VillagerGoalPackages.getIdlePackage(VillagerProfession.FLETCHER, 0.5F));
      brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
      brain.setDefaultActivity(Activity.IDLE);
      brain.setActiveActivityIfPossible(Activity.IDLE);
   }

   protected void customServerAiStep() {
      this.level().getProfiler().push("villagerBrain");
      this.getBrain().tick((ServerLevel)this.level(), this);
      this.level().getProfiler().pop();
      super.customServerAiStep();
   }

   protected void updateTrades() {
      if (this.offers == null) {
         this.offers = new MerchantOffers();
      }

      if (this.offers.isEmpty()) {
         Random random = new Random();
         List<NamekVillagerEntity.CustomTrade> availableTrades = new ArrayList<>(TRADES);

         for (int i = 0; i < 3 && !availableTrades.isEmpty(); i++) {
            NamekVillagerEntity.CustomTrade randomTrade = availableTrades.remove(random.nextInt(availableTrades.size()));
            this.offers.add(randomTrade.createOffer());
         }
      } else {
         Random random = new Random();
         List<NamekVillagerEntity.CustomTrade> availableTrades = new ArrayList<>(TRADES);

         for (MerchantOffer offer : this.offers) {
            availableTrades.removeIf(trade -> trade.createOffer().equals(offer));
         }

         if (!availableTrades.isEmpty()) {
            NamekVillagerEntity.CustomTrade randomTrade = availableTrades.get(random.nextInt(availableTrades.size()));
            this.offers.add(randomTrade.createOffer());
         }
      }
   }

   public MerchantOffers getOffers() {
      if (this.offers == null) {
         this.offers = new MerchantOffers();
         this.updateTrades();
      }

      return this.offers;
   }

   public void registerControllers(ControllerRegistrar controllers) {
   }

   public boolean canBreed() {
      return false;
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.geoCache;
   }

   static {
      TRADES.add(
         new NamekVillagerEntity.CustomTrade(
            new ItemStack(Items.EMERALD, 1), new ItemStack(Items.CARROT, 3), new ItemStack((ItemLike)MainItems.FROG_LEGS_RAW.get(), 2), 10, 6
         )
      );
      TRADES.add(
         new NamekVillagerEntity.CustomTrade(
            new ItemStack(Items.EMERALD, 3), new ItemStack(Items.BUCKET, 1), new ItemStack((ItemLike)MainItems.HEALING_BUCKET.get(), 1), 3, 5
         )
      );
      TRADES.add(
         new NamekVillagerEntity.CustomTrade(
            new ItemStack(Items.EMERALD, 2), new ItemStack(Items.AIR), new ItemStack((ItemLike)MainBlocks.NAMEK_BLOCK.get(), 8), 64, 3
         )
      );
      TRADES.add(
         new NamekVillagerEntity.CustomTrade(
            new ItemStack(Items.EMERALD, 32), new ItemStack(Items.AIR), new ItemStack((ItemLike)MainItems.T2_RADAR_CHIP.get(), 1), 2, 9
         )
      );
      TRADES.add(
         new NamekVillagerEntity.CustomTrade(
            new ItemStack(Items.EMERALD, 16), new ItemStack(Items.ANCIENT_DEBRIS, 1), new ItemStack(Items.NETHERITE_SCRAP, 2), 16, 6
         )
      );
      TRADES.add(
         new NamekVillagerEntity.CustomTrade(new ItemStack(Items.EMERALD, 28), new ItemStack(Items.DIAMOND, 4), new ItemStack(Items.NETHERITE_SCRAP, 2), 4, 7)
      );
      TRADES.add(new NamekVillagerEntity.CustomTrade(new ItemStack(Items.EMERALD, 2), new ItemStack(Items.AIR), new ItemStack(Items.CARROT, 3), 10, 3));
      TRADES.add(new NamekVillagerEntity.CustomTrade(new ItemStack(Items.CARROT, 8), new ItemStack(Items.AIR), new ItemStack(Items.EMERALD, 2), 10, 5));
      TRADES.add(new NamekVillagerEntity.CustomTrade(new ItemStack(Items.EMERALD, 2), new ItemStack(Items.AIR), new ItemStack(Items.POTATO, 3), 10, 3));
      TRADES.add(new NamekVillagerEntity.CustomTrade(new ItemStack(Items.POTATO, 8), new ItemStack(Items.AIR), new ItemStack(Items.EMERALD, 2), 10, 5));
      TRADES.add(new NamekVillagerEntity.CustomTrade(new ItemStack(Items.EMERALD, 2), new ItemStack(Items.AIR), new ItemStack(Items.BEETROOT, 3), 10, 3));
      TRADES.add(new NamekVillagerEntity.CustomTrade(new ItemStack(Items.BEETROOT, 8), new ItemStack(Items.AIR), new ItemStack(Items.EMERALD, 2), 10, 5));
   }

   private static class CustomTrade {
      private final ItemStack input;
      private final ItemStack secInput;
      private final ItemStack output;
      private final int maxUses;
      private final int xp;

      public CustomTrade(ItemStack input, ItemStack secInput, ItemStack output, int maxUses, int xp) {
         this.input = input;
         this.secInput = secInput;
         this.output = output;
         this.maxUses = maxUses;
         this.xp = xp;
      }

      public MerchantOffer createOffer() {
         return new MerchantOffer(
            new ItemCost(this.input.getItem(), this.input.getCount()),
            Optional.of(new ItemCost(this.secInput.getItem(), this.secInput.getCount())),
            this.output,
            this.maxUses,
            this.xp,
            0.15F
         );
      }
   }
}
