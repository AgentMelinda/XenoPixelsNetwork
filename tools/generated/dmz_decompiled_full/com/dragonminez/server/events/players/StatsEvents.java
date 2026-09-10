package com.dragonminez.server.events.players;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import com.dragonminez.common.config.CombatConfig;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.init.MainDamageTypes;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainFluids;
import com.dragonminez.common.init.entities.ShadowDummyEntity;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.namek.NamekTraderEntity;
import com.dragonminez.common.init.entities.namek.NamekWarriorEntity;
import com.dragonminez.common.init.entities.redribbon.BanditEntity;
import com.dragonminez.common.init.entities.redribbon.RedRibbonSoldierEntity;
import com.dragonminez.common.init.entities.redribbon.RobotEntity;
import com.dragonminez.common.init.entities.sagas.SagaFriezaSoldier01Entity;
import com.dragonminez.common.init.entities.sagas.SagaFriezaSoldier02Entity;
import com.dragonminez.common.init.item.WeightItem;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.SummonPlayerShadowDummyC2S;
import com.dragonminez.common.network.S2C.AppearanceSyncS2C;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.passives.PassiveEventHandler;
import com.dragonminez.common.stats.GenericAttributes;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.TechniqueData;
import com.dragonminez.common.util.AttributeMods;
import com.dragonminez.common.util.IHealthFixable;
import com.dragonminez.server.events.DragonBallsHandler;
import com.dragonminez.server.util.GravityLogic;
import com.dragonminez.server.util.MutantManager;
import com.dragonminez.server.util.PotionEffectHelper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.EntityEvent.Size;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start;
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME
)
public class StatsEvents {
   public static final UUID DMZ_HEALTH_MODIFIER_UUID = UUID.fromString("b065b873-f4c8-4a0f-aa8c-6e778cd410e0");
   public static final UUID FORM_SPEED_UUID = UUID.fromString("c8c07577-3365-4b1c-9917-26b237da6e08");
   public static final UUID TURBO_SPEED_UUID = UUID.fromString("b3f4a1d2-6c8e-4b0a-9f21-7d5e3c9a1b64");
   private static final double TURBO_SPEED_BONUS = 0.3;
   public static final UUID FORM_REACH_UUID = UUID.fromString("d8d18684-4476-5c2d-ba28-37c348eb521f");
   public static final UUID FORM_ATTACK_SPEED_UUID = UUID.fromString("f2e0aaf0-a4ab-4921-a5b0-f34cf1c3533b");
   public static final UUID KI_WEAPON_ATTACK_SPEED_UUID = UUID.fromString("a3b1c5d7-9e2f-4a6b-8c1d-5f7e9a0b2c4d");
   public static final UUID WEIGHT_MOVEMENT_SPEED_MOD_UUID = UUID.fromString("6f663f73-199f-431a-8c35-132d75a31742");
   private static final UUID WEIGHT_ATTACK_SPEED_MOD_UUID = UUID.fromString("a1b2c3d4-e5f6-431a-8c35-132d75a31742");
   private static final Map<UUID, List<StatsEvents.FoodRegenTask>> FOOD_REGEN_QUEUE = new ConcurrentHashMap<>();
   private static final double HEAL_PERCENTAGE = 0.08;
   private static final int HEAL_TICKS = 60;
   private static final Map<Player, Long> lastHealingTime = new WeakHashMap<>();

   private static double getWeightStatMultiplier(int level, int weight) {
      if (weight <= 0) {
         return 1.0;
      } else {
         double x = (double)level;
         double w = (double)weight;
         double penalty;
         if (x <= 2.0 * w) {
            penalty = 10.5 * (1.0 - Math.sqrt(x / (4.0 * w)));
         } else {
            double f2w = 10.5 * (1.0 - Math.sqrt(2.0 * w / (4.0 * w)));
            double innerDiv = -w / (w + 10.0) + 1.0;
            double top = f2w / innerDiv;
            double slope = -(top / (2.0 * (w + 10.0)));
            double result = slope * x + top;
            penalty = Math.max(0.0, result);
         }

         double multiplier = 1.0 - penalty / 10.5;
         return Math.max(0.001, multiplier);
      }
   }

   private static void applyWeightAttributeModifier(Player player, Holder<Attribute> attribute, UUID uuid, String name, double amount) {
      AttributeInstance inst = player.getAttribute(attribute);
      if (inst != null) {
         AttributeModifier existing = inst.getModifier(AttributeMods.id(uuid));
         if (amount == 0.0) {
            if (existing != null) {
               inst.removeModifier(AttributeMods.id(uuid));
            }
         } else if (existing == null || Math.abs(existing.amount() - amount) > 0.001) {
            if (existing != null) {
               inst.removeModifier(AttributeMods.id(uuid));
            }

            inst.addTransientModifier(AttributeMods.of(uuid, name, amount, Operation.ADD_MULTIPLIED_BASE));
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerTick(Post event) {
      if (!event.getEntity().level().isClientSide) {
         if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            StatsProvider.get(StatsCapability.INSTANCE, serverPlayer)
               .ifPresent(
                  data -> {
                     if (data.getStatus().isHasCreatedCharacter()) {
                        int[] totalWeight = new int[]{0};
                        CuriosApi.getCuriosInventory(serverPlayer)
                           .ifPresent(
                              inv -> {
                                 ICurioStacksHandler handler = (ICurioStacksHandler)inv.getCurios().get("weights");
                                 if (handler != null) {
                                    for (int i = 0; i < handler.getSlots(); i++) {
                                       ItemStack stack = handler.getStacks().getStackInSlot(i);
                                       if (stack.getItem() instanceof WeightItem) {
                                          totalWeight[0] += WeightItem.getWeight(stack);
                                       } else if (!stack.isEmpty()) {
                                          totalWeight[0] += ((CustomData)stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY))
                                             .copyTag()
                                             .getInt("WeightValue");
                                       }
                                    }
                                 }
                              }
                           );
                        if (totalWeight[0] > 0) {
                           double gravityMultiplier = GravityLogic.getGravityMultiplier(serverPlayer);
                           int effectiveWeight = (int)((double)totalWeight[0] * gravityMultiplier);
                           int currentBaseLevel = data.getLevel();
                           int totalBaseStats = data.getStats().getTotalStats();
                           int initialStats = totalBaseStats - (currentBaseLevel - 1) * 6;
                           double boostedTotal = 0.0;
                           boostedTotal += (double)data.getStats().getStrength() * data.getTotalMultiplier("STR");
                           boostedTotal += (double)data.getStats().getStrikePower() * data.getTotalMultiplier("SKP");
                           boostedTotal += (double)data.getStats().getResistance() * data.getTotalMultiplier("RES");
                           boostedTotal += (double)data.getStats().getVitality() * data.getTotalMultiplier("VIT");
                           boostedTotal += (double)data.getStats().getKiPower() * data.getTotalMultiplier("PWR");
                           boostedTotal += (double)data.getStats().getEnergy() * data.getTotalMultiplier("ENE");
                           double relativeLevel = (boostedTotal - (double)initialStats) / 6.0 + 1.0;
                           double multiplier = getWeightStatMultiplier((int)relativeLevel, effectiveWeight);
                           if (multiplier < 1.0) {
                              data.getBonusStats().addBonus("STR", "Weights", "*", multiplier);
                              data.getBonusStats().addBonus("SKP", "Weights", "*", multiplier);
                              data.getBonusStats().addBonus("PWR", "Weights", "*", multiplier);
                              data.getBonusStats().addBonus("DEF", "Weights", "*", multiplier);
                              data.getBonusStats().addBonus("STM", "Weights", "*", multiplier);
                           } else {
                              data.getBonusStats().removeBonus("STR", "Weights");
                              data.getBonusStats().removeBonus("SKP", "Weights");
                              data.getBonusStats().removeBonus("PWR", "Weights");
                              data.getBonusStats().removeBonus("DEF", "Weights");
                              data.getBonusStats().removeBonus("STM", "Weights");
                           }

                           double reductionModifier = multiplier - 1.0;
                           applyWeightAttributeModifier(
                              serverPlayer, Attributes.MOVEMENT_SPEED, WEIGHT_MOVEMENT_SPEED_MOD_UUID, "Weight Speed Penalty", reductionModifier
                           );
                           applyWeightAttributeModifier(
                              serverPlayer, Attributes.ATTACK_SPEED, WEIGHT_ATTACK_SPEED_MOD_UUID, "Weight Attack Speed Penalty", reductionModifier
                           );
                        } else {
                           data.getBonusStats().removeBonus("STR", "Weights");
                           data.getBonusStats().removeBonus("SKP", "Weights");
                           data.getBonusStats().removeBonus("PWR", "Weights");
                           data.getBonusStats().removeBonus("DEF", "Weights");
                           data.getBonusStats().removeBonus("STM", "Weights");
                           applyWeightAttributeModifier(serverPlayer, Attributes.MOVEMENT_SPEED, WEIGHT_MOVEMENT_SPEED_MOD_UUID, "Weight Speed Penalty", 0.0);
                           applyWeightAttributeModifier(serverPlayer, Attributes.ATTACK_SPEED, WEIGHT_ATTACK_SPEED_MOD_UUID, "Weight Attack Speed Penalty", 0.0);
                        }

                        applyHealthBonus(serverPlayer);
                        UUID playerId = serverPlayer.getUUID();
                        List<StatsEvents.FoodRegenTask> tasks = FOOD_REGEN_QUEUE.get(playerId);
                        if (tasks != null && !tasks.isEmpty()) {
                           float totalHpPulse = 0.0F;
                           float totalKiPulse = 0.0F;
                           float totalStamPulse = 0.0F;
                           Iterator<StatsEvents.FoodRegenTask> iterator = tasks.iterator();

                           while (iterator.hasNext()) {
                              StatsEvents.FoodRegenTask task = iterator.next();
                              task.ticksPassed++;
                              if (task.ticksPassed % 20 == 0) {
                                 totalHpPulse += task.hpPerSecond;
                                 totalKiPulse += task.kiPerSecond;
                                 totalStamPulse += task.staminaPerSecond;
                              }

                              if (task.ticksPassed >= task.totalSeconds * 20) {
                                 iterator.remove();
                              }
                           }

                           if (totalHpPulse > 0.0F) {
                              totalHpPulse *= (float)Math.min(1.0, data.getSecondaryStatEffects().getMultiplier("HP_REGEN"));
                              if (totalHpPulse > 0.0F) {
                                 PassiveEventHandler.suppressHealingBonus = true;
                                 serverPlayer.heal(totalHpPulse);
                                 PassiveEventHandler.suppressHealingBonus = false;
                              }
                           }

                           if (totalKiPulse > 0.0F || totalStamPulse > 0.0F) {
                              float maxEnergy = data.getMaxEnergy();
                              float maxStamina = data.getMaxStamina();
                              float currentEnergy = data.getResources().getCurrentEnergy();
                              float currentStamina = data.getResources().getCurrentStamina();
                              data.getResources().setCurrentEnergy(Math.min(maxEnergy, currentEnergy + totalKiPulse));
                              data.getResources().setCurrentStamina(Math.min(maxStamina, currentStamina + totalStamPulse));
                           }
                        }

                        if (data.getResources().getCurrentEnergy() > data.getMaxEnergy()) {
                           data.getResources().setCurrentEnergy(data.getMaxEnergy());
                        }

                        if (data.getResources().getCurrentStamina() > data.getMaxStamina()) {
                           data.getResources().setCurrentStamina(data.getMaxStamina());
                        }
                     }
                  }
               );
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
      FOOD_REGEN_QUEUE.remove(event.getEntity().getUUID());
      StatsProvider.get(StatsCapability.INSTANCE, event.getEntity()).ifPresent(data -> data.getSkills().setSkillActive("kisense", false));
   }

   public static void applyHealthBonus(ServerPlayer serverPlayer) {
      StatsProvider.get(StatsCapability.INSTANCE, serverPlayer)
         .ifPresent(
            data -> {
               AttributeInstance maxHealthAttr = serverPlayer.getAttribute(Attributes.MAX_HEALTH);
               if (maxHealthAttr != null) {
                  float dmzHealthBonus = data.getHealthBonus();
                  if (!Float.isFinite(dmzHealthBonus) || dmzHealthBonus < 0.0F) {
                     dmzHealthBonus = 0.0F;
                  }

                  float healthBefore = serverPlayer.getHealth();
                  double maxBefore = maxHealthAttr.getValue();
                  AttributeModifier existingModifier = maxHealthAttr.getModifier(AttributeMods.id(DMZ_HEALTH_MODIFIER_UUID));
                  boolean needsUpdate = existingModifier == null
                     || Math.abs(existingModifier.amount() - (double)dmzHealthBonus) > Math.max(1.0, (double)Math.abs(dmzHealthBonus) * 1.0E-5);
                  if (needsUpdate) {
                     GenericAttributes.ensureAttributeCeilings();
                     maxHealthAttr.removeModifier(AttributeMods.id(DMZ_HEALTH_MODIFIER_UUID));
                     if (dmzHealthBonus > 0.0F) {
                        AttributeModifier healthModifier = AttributeMods.of(
                           DMZ_HEALTH_MODIFIER_UUID, "DMZ Health Bonus", (double)dmzHealthBonus, Operation.ADD_VALUE
                        );
                        maxHealthAttr.addPermanentModifier(healthModifier);
                     }
                  }

                  double maxAfter = maxHealthAttr.getValue();
                  if (Double.isFinite(maxAfter) && !(maxAfter <= 0.0)) {
                     if (maxAfter > maxBefore + 0.01) {
                        float gain = (float)(maxAfter - maxBefore);
                        serverPlayer.setHealth(Math.min((float)maxAfter, healthBefore + gain));
                     } else if ((double)serverPlayer.getHealth() > maxAfter) {
                        serverPlayer.setHealth((float)maxAfter);
                     }

                     if (!data.hasInitializedHealth()) {
                        serverPlayer.setHealth((float)maxAfter);
                        data.setInitializedHealth(true);
                     }
                  }
               }
            }
         );
   }

   public static void restoreStatsPoolsOnJoin(ServerPlayer serverPlayer) {
      StatsProvider.get(StatsCapability.INSTANCE, serverPlayer).ifPresent(data -> {
         try {
            GenericAttributes.ensureAttributeCeilings();
            data.reapplyStatAttributes();
            AttributeInstance maxHealthAttr = serverPlayer.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
               maxHealthAttr.removeModifier(AttributeMods.id(DMZ_HEALTH_MODIFIER_UUID));
            }

            data.setInitializedHealth(false);
            applyHealthBonus(serverPlayer);
            if (serverPlayer instanceof IHealthFixable fixable) {
               fixable.dragonminez$applyDeferredHealthRestore();
            }

            data.getResources().reclampToCurrentMax();
         } catch (Exception var4) {
            LogUtil.error(Env.SERVER, "restoreStatsPoolsOnJoin failed for " + serverPlayer.getGameProfile().getName(), var4);
         }
      });
   }

   @SubscribeEvent
   public static void onPlayerRespawn(PlayerRespawnEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         DragonBallsHandler.syncRadar(player.serverLevel());
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            applyHealthBonus(player);
            player.setHealth(player.getMaxHealth());
            data.getResources().setCurrentEnergy(data.getMaxEnergy());
            data.getResources().setCurrentStamina(data.getMaxStamina());
            data.getSkills().setSkillActive("kisense", false);
         });
      }
   }

   private static boolean addsAlignment(Entity entity) {
      return entity instanceof RedRibbonSoldierEntity
         || entity instanceof SagaFriezaSoldier01Entity
         || entity instanceof SagaFriezaSoldier02Entity
         || entity instanceof RobotEntity
         || entity instanceof BanditEntity;
   }

   private static boolean removesAlignment(Entity entity) {
      return entity instanceof NamekWarriorEntity || entity instanceof Villager || entity instanceof NamekTraderEntity;
   }

   @SubscribeEvent
   public static void cookDropsOnKiKill(LivingDeathEvent event) {
      LivingEntity entity = event.getEntity();
      if (!entity.level().isClientSide) {
         if (!entity.fireImmune()) {
            DamageSource source = event.getSource();
            boolean isKiKill = MainDamageTypes.isKiblastDamage(source) || MainDamageTypes.isStrikeAttackDamage(source);
            if (isKiKill) {
               if (entity.getRemainingFireTicks() <= 0) {
                  entity.setRemainingFireTicks(1);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onEntityDeath(LivingDeathEvent event) {
      if (!event.getEntity().level().isClientSide) {
         boolean[] addAlignment = new boolean[]{false};
         boolean[] removeAlignment = new boolean[]{false};
         if (event.getEntity() instanceof Player victim) {
            StatsProvider.get(StatsCapability.INSTANCE, victim).ifPresent(victimData -> {
               if (victimData.getResources().getAlignment() >= 50 && victimData.getStatus().isHasCreatedCharacter()) {
                  removeAlignment[0] = true;
               } else {
                  addAlignment[0] = true;
               }

               if (victimData.getStatus().isHasCreatedCharacter()) {
                  boolean wasMutant = victimData.getEffects().hasEffect("mutant");
                  boolean keepMutant = ConfigManager.getServerConfig().getMutant().getKeepMutantOnDeath();
                  if (wasMutant && !keepMutant && victim instanceof ServerPlayer mutantVictim) {
                     MutantManager.revoke(mutantVictim, victimData);
                  }

                  victimData.getEffects().removeAllEffects();
                  if (wasMutant && keepMutant) {
                     victimData.getEffects().addEffect("mutant", 1.0, -1);
                     if (victim instanceof ServerPlayer mutantVictim) {
                        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(mutantVictim), mutantVictim);
                     }
                  }

                  victimData.getSecondaryStatEffects().clear();
                  victimData.getStatus().setChargingKi(false);
                  victimData.getStatus().setActionCharging(false);
                  victimData.getCharacter().setActiveForm(null, null);
                  victimData.getCharacter().setActiveStackForm(null, null);
                  victimData.getSkills().setSkillActive("kisense", false);
                  FOOD_REGEN_QUEUE.remove(victim.getUUID());
               }
            });
         }

         Player attacker = resolveAttackerPlayer(event.getSource().getEntity(), event.getSource().getDirectEntity());
         if (attacker != null) {
            if (removesAlignment(event.getEntity())) {
               removeAlignment[0] = true;
            }

            if (addsAlignment(event.getEntity())) {
               addAlignment[0] = true;
            }

            if (event.getEntity() instanceof ShadowDummyEntity dummyEntity && attacker instanceof ServerPlayer killer) {
               StatsProvider.get(StatsCapability.INSTANCE, killer)
                  .ifPresent(killerData -> killerData.getStatus().setShadowDummyKillCount(killerData.getStatus().getShadowDummyKillCount() + 1));
            }

            if (event.getEntity() instanceof ShadowDummyEntity dummyEntity && dummyEntity.getPersistentData().getBoolean("dmz_player_shadow")) {
               SummonPlayerShadowDummyC2S.dismissByDummy(dummyEntity);
            }

            StatsProvider.get(StatsCapability.INSTANCE, attacker).ifPresent(data -> {
               if (data.getStatus().isHasCreatedCharacter()) {
                  if (removeAlignment[0]) {
                     data.getResources().removeAlignment(5);
                     removeAlignment[0] = false;
                  }

                  if (addAlignment[0]) {
                     data.getResources().addAlignment(2);
                     addAlignment[0] = false;
                  }

                  grantTechniqueKillXp(data, event.getSource().getDirectEntity());
               }
            });
         }
      }
   }

   private static Player resolveAttackerPlayer(Entity sourceEntity, Entity directEntity) {
      if (sourceEntity instanceof Player) {
         return (Player)sourceEntity;
      } else {
         if (directEntity instanceof AbstractKiProjectile projectile) {
            Entity var4 = projectile.getOwner();
            if (var4 instanceof Player) {
               return (Player)var4;
            }
         }

         return null;
      }
   }

   private static void grantTechniqueKillXp(StatsData data, Entity directEntity) {
      if (directEntity instanceof AbstractKiProjectile projectile) {
         String techniqueId = projectile.getTechniqueId();
         if (techniqueId != null && !techniqueId.isEmpty()) {
            TechniqueData techniqueData = data.getTechniques().getUnlockedTechniques().get(techniqueId);
            if (techniqueData instanceof KiAttackData kiAttackData) {
               int xpGain = kiAttackData.getXpGainPerKill();
               if (xpGain > 0) {
                  data.getTechniques().addExperienceToTechnique(techniqueId, xpGain);
               }
            }
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOW
   )
   public static void onEntityHit(Pre event) {
      if (!event.getEntity().level().isClientSide) {
         if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            StatsProvider.get(StatsCapability.INSTANCE, attacker)
               .ifPresent(
                  attackerData -> {
                     if (attackerData.getStatus().isHasCreatedCharacter() && event.getNewDamage() >= 1.0F && !isMasteryBlacklisted(event.getEntity())) {
                        double damageScale = masteryDamageScale(event.getEntity(), (double)event.getNewDamage());
                        if (attackerData.getCharacter().hasActiveForm()) {
                           FormConfig.FormData activeForm = attackerData.getCharacter().getActiveFormData();
                           if (activeForm != null && attackerData.getResources().getPowerRelease() >= 50) {
                              String formGroup = attackerData.getCharacter().getActiveFormGroup();
                              String formName = attackerData.getCharacter().getActiveForm();
                              double bonus = 1.0
                                 + GravityLogic.getBonusGravity(attacker) * ConfigManager.getServerConfig().getGravity().getMasteryBonusPerGravity();
                              attackerData.getCharacter()
                                 .gainMastery(
                                    formGroup,
                                    formName,
                                    PotionEffectHelper.applyMasteryGainMultiplier(attacker, activeForm.getMasteryPerHitDealt() * bonus * damageScale)
                                 );
                           }
                        }

                        if (attackerData.getCharacter().hasActiveStackForm()) {
                           FormConfig.FormData activeStackForm = attackerData.getCharacter().getActiveStackFormData();
                           if (activeStackForm != null && attackerData.getResources().getPowerRelease() >= 50) {
                              String stackFormGroup = attackerData.getCharacter().getActiveStackFormGroup();
                              String stackForm = attackerData.getCharacter().getActiveStackForm();
                              double bonus = 1.0
                                 + GravityLogic.getBonusGravity(attacker) * ConfigManager.getServerConfig().getGravity().getMasteryBonusPerGravity();
                              attackerData.getCharacter()
                                 .gainMastery(
                                    stackFormGroup,
                                    stackForm,
                                    PotionEffectHelper.applyMasteryGainMultiplier(attacker, activeStackForm.getMasteryPerHitDealt() * bonus * damageScale)
                                 );
                           }
                        }
                     }

                     NetworkHandler.sendToTrackingEntityAndSelf(new AppearanceSyncS2C(attacker), attacker);
                  }
               );
         }

         if (event.getEntity() instanceof ServerPlayer victim) {
            boolean fromEntity = event.getSource().getEntity() instanceof LivingEntity;
            StatsProvider.get(StatsCapability.INSTANCE, victim)
               .ifPresent(
                  victimData -> {
                     if (fromEntity
                        && victimData.getStatus().isHasCreatedCharacter()
                        && event.getNewDamage() >= 1.0F
                        && !isMasteryBlacklisted(event.getSource().getEntity())) {
                        double damageScale = masteryDamageScale(victim, (double)event.getNewDamage());
                        if (victimData.getCharacter().hasActiveForm()) {
                           FormConfig.FormData activeForm = victimData.getCharacter().getActiveFormData();
                           if (activeForm != null && victimData.getResources().getPowerRelease() >= 50) {
                              String formGroup = victimData.getCharacter().getActiveFormGroup();
                              String formName = victimData.getCharacter().getActiveForm();
                              double bonus = 1.0
                                 + GravityLogic.getBonusGravity(victim) * ConfigManager.getServerConfig().getGravity().getMasteryBonusPerGravity();
                              victimData.getCharacter()
                                 .gainMastery(
                                    formGroup,
                                    formName,
                                    PotionEffectHelper.applyMasteryGainMultiplier(victim, activeForm.getMasteryPerHitReceived() * bonus * damageScale)
                                 );
                           }
                        }

                        if (victimData.getCharacter().hasActiveStackForm()) {
                           FormConfig.FormData activeStackForm = victimData.getCharacter().getActiveStackFormData();
                           if (activeStackForm != null && victimData.getResources().getPowerRelease() >= 50) {
                              String stackFormGroup = victimData.getCharacter().getActiveStackFormGroup();
                              String stackForm = victimData.getCharacter().getActiveStackForm();
                              double bonus = 1.0
                                 + GravityLogic.getBonusGravity(victim) * ConfigManager.getServerConfig().getGravity().getMasteryBonusPerGravity();
                              victimData.getCharacter()
                                 .gainMastery(
                                    stackFormGroup,
                                    stackForm,
                                    PotionEffectHelper.applyMasteryGainMultiplier(victim, activeStackForm.getMasteryPerHitReceived() * bonus * damageScale)
                                 );
                           }
                        }
                     }

                     NetworkHandler.sendToTrackingEntityAndSelf(new AppearanceSyncS2C(victim), victim);
                  }
               );
         }
      }
   }

   private static boolean isMasteryBlacklisted(Entity entity) {
      if (entity == null) {
         return false;
      } else {
         ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
         return key == null ? false : ConfigManager.getCombatConfig().getMasteryBlacklistEntities().contains(key.toString());
      }
   }

   private static double masteryDamageScale(LivingEntity hitEntity, double damage) {
      double maxHp = (double)hitEntity.getMaxHealth();
      if (!(maxHp <= 0.0) && !(damage <= 0.0)) {
         double pct = damage / maxHp;
         double t = Mth.clamp((pct - 0.2) / 0.6, 0.0, 1.0);
         return 1.0 + t * 3.0;
      } else {
         return 1.0;
      }
   }

   @SubscribeEvent
   public static void onLivingTick(Post event) {
      Player player = event.getEntity();
      if (!player.level().isClientSide) {
         FluidState fluidState = player.level().getFluidState(player.blockPosition());
         if (!fluidState.isEmpty()) {
            if (fluidState.is((Fluid)MainFluids.SOURCE_HEALING.get()) || fluidState.is((Fluid)MainFluids.FLOWING_HEALING.get())) {
               long currentTime = player.level().getGameTime();
               long lastHealTime = lastHealingTime.getOrDefault(player, 0L);
               if (currentTime - lastHealTime >= 60L) {
                  funcHealingLiquid(player);
                  lastHealingTime.put(player, currentTime);
               }
            } else if (fluidState.is((Fluid)MainFluids.SOURCE_NAMEK.get()) || fluidState.is((Fluid)MainFluids.FLOWING_NAMEK.get())) {
               funcNamekWater(player);
            }
         }
      }
   }

   private static void funcHealingLiquid(Player player) {
      if (player instanceof ServerPlayer serverPlayer) {
         StatsProvider.get(StatsCapability.INSTANCE, serverPlayer).ifPresent(data -> {
            float maxHp = player.getMaxHealth();
            float healHp = (float)((double)maxHp * 0.08);
            float maxKi = data.getMaxEnergy();
            float healKi = (float)((double)maxKi * 0.08);
            float maxStamina = data.getMaxStamina();
            float healStamina = (float)((double)maxStamina * 0.08);
            boolean hasCreatedChar = data.getStatus().isHasCreatedCharacter();
            if (healHp > maxHp) {
               healHp = maxHp;
            }

            if (healKi > maxKi) {
               healKi = maxKi;
            }

            if (healStamina > maxStamina) {
               healStamina = maxStamina;
            }

            if (hasCreatedChar) {
               serverPlayer.setHealth(player.getHealth() + healHp);
               data.getResources().addEnergy(healKi);
               data.getResources().addStamina(healStamina);
            }
         });
      }

      if (player.isOnFire()) {
         player.clearFire();
      }
   }

   private static void funcNamekWater(Player player) {
      if (player.isOnFire()) {
         player.clearFire();
      }
   }

   @SubscribeEvent
   public static void onItemRightClick(RightClickItem event) {
      if (!event.getLevel().isClientSide) {
         Player player = event.getEntity();
         ItemStack stack = event.getItemStack();
         ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
         if (itemKey != null) {
            String itemId = itemKey.toString();
            String namespace = itemKey.getNamespace();
            GeneralServerConfig.FoodConfig foodConfig = ConfigManager.getServerConfig().getGameplay().getFood();
            boolean isModBlacklisted = !foodConfig.getBlacklistedNamespaces().isEmpty() && foodConfig.getBlacklistedNamespaces().contains(namespace);
            boolean isItemBlacklisted = !foodConfig.getBlacklistedItems().isEmpty() && foodConfig.getBlacklistedItems().contains(itemId);
            if (!isModBlacklisted && !isItemBlacklisted) {
               player.startUsingItem(event.getHand());
               event.setCancellationResult(InteractionResult.CONSUME);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onItemUseFinish(Finish event) {
      if (!event.getEntity().level().isClientSide && event.getEntity() instanceof ServerPlayer player) {
         ItemStack var9 = event.getItem();
         ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(var9.getItem());
         if (itemKey != null) {
            String itemId = itemKey.toString();
            String namespace = itemKey.getNamespace();
            GeneralServerConfig.FoodConfig foodConfig = ConfigManager.getServerConfig().getGameplay().getFood();
            boolean isModBlacklisted = !foodConfig.getBlacklistedNamespaces().isEmpty() && foodConfig.getBlacklistedNamespaces().contains(namespace);
            boolean isItemBlacklisted = !foodConfig.getBlacklistedItems().isEmpty() && foodConfig.getBlacklistedItems().contains(itemId);
            if (!isModBlacklisted && !isItemBlacklisted) {
               StatsProvider.get(StatsCapability.INSTANCE, player)
                  .ifPresent(
                     data -> {
                        boolean isSenzu = itemId.equals("dragonminez:senzu_bean");
                        boolean isHeartMedicine = itemId.equals("dragonminez:heart_medicine");
                        if (!isSenzu && !isHeartMedicine || !player.getCooldowns().isOnCooldown(var9.getItem())) {
                           FoodProperties foodProperties = var9.getFoodProperties(player);
                           if (foodProperties != null) {
                              int foodGain = foodProperties.nutrition();
                              float saturationGain = foodProperties.saturation();
                              float healthFoodRecoveryPercentage = foodGain >= foodConfig.getMinHungerPoints()
                                 ? (float)Math.min(foodGain, foodConfig.getMaxHungerPoints()) * foodConfig.getHealthPercentageRecoveredPerHungerPoint()
                                 : 0.0F;
                              float kiFoodRecoveryPercentage = foodGain >= foodConfig.getMinHungerPoints()
                                 ? (float)Math.min(foodGain, foodConfig.getMaxHungerPoints()) * foodConfig.getKiPercentageRecoveredPerHungerPoint()
                                 : 0.0F;
                              float staminaFoodRecoveryPercentage = foodGain >= foodConfig.getMinHungerPoints()
                                 ? (float)Math.min(foodGain, foodConfig.getMaxHungerPoints()) * foodConfig.getStaminaPercentageRecoveredPerHungerPoint()
                                 : 0.0F;
                              float healthSaturationRecoveryPercentage = saturationGain >= foodConfig.getMinSaturationPoints()
                                 ? Math.min(saturationGain, foodConfig.getMaxSaturationPoints()) * foodConfig.getHealthPercentageRecoveredPerSaturationPoint()
                                 : 0.0F;
                              float kiSaturationRecoveryPercentage = saturationGain >= foodConfig.getMinSaturationPoints()
                                 ? Math.min(saturationGain, foodConfig.getMaxSaturationPoints()) * foodConfig.getKiPercentageRecoveredPerSaturationPoint()
                                 : 0.0F;
                              float staminaSaturationRecoveryPercentage = saturationGain >= foodConfig.getMinSaturationPoints()
                                 ? Math.min(saturationGain, foodConfig.getMaxSaturationPoints()) * foodConfig.getStaminaPercentageRecoveredPerSaturationPoint()
                                 : 0.0F;
                              float healthTotalRecoveryPercentage = healthFoodRecoveryPercentage + healthSaturationRecoveryPercentage;
                              float kiTotalRecoveryPercentage = kiFoodRecoveryPercentage + kiSaturationRecoveryPercentage;
                              float staminaTotalRecoveryPercentage = staminaFoodRecoveryPercentage + staminaSaturationRecoveryPercentage;
                              float maxHealth = player.getMaxHealth();
                              float maxEnergy = data.getMaxEnergy();
                              float maxStamina = data.getMaxStamina();
                              float healAmount = maxHealth * healthTotalRecoveryPercentage;
                              float energyAmount = maxEnergy * kiTotalRecoveryPercentage;
                              float staminaAmount = maxStamina * staminaTotalRecoveryPercentage;
                              if (!isSenzu && !isHeartMedicine) {
                                 int durationSeconds = 6;
                                 FOOD_REGEN_QUEUE.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
                                    .add(new StatsEvents.FoodRegenTask(durationSeconds, healAmount, energyAmount, staminaAmount));
                              } else {
                                 PassiveEventHandler.suppressHealingBonus = true;
                                 player.heal(maxHealth - player.getHealth());
                                 PassiveEventHandler.suppressHealingBonus = false;
                                 data.getResources().setCurrentEnergy(maxEnergy);
                                 data.getResources().setCurrentStamina(maxStamina);
                                 int cooldownTicks = ConfigManager.getServerConfig().getGameplay().getSenzuCooldownTicks();
                                 player.getCooldowns().addCooldown(var9.getItem(), cooldownTicks);
                              }
                           }
                        }
                     }
                  );
            }
         }
      }
   }

   @SubscribeEvent
   public static void onItemUseStart(Start event) {
      if (!event.getEntity().level().isClientSide && event.getEntity() instanceof ServerPlayer player) {
         ItemStack var5 = event.getItem();
         ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(var5.getItem());
         if (itemKey != null) {
            String itemId = itemKey.toString();
            if (itemId.equals("dragonminez:senzu_bean") || itemId.equals("dragonminez:heart_medicine")) {
               if (!player.getCooldowns().isOnCooldown(var5.getItem()) && !player.hasEffect(MainEffects.STUN)) {
                  event.setDuration(1);
               } else {
                  event.setCanceled(true);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerAttack(AttackEntityEvent event) {
      if (!event.getEntity().level().isClientSide) {
         if (event.getEntity().hasEffect(MainEffects.STUN)) {
            event.setCanceled(true);
         }
      }
   }

   @SubscribeEvent
   public static void onLivingAttack(LivingIncomingDamageEvent event) {
      if (!event.getEntity().level().isClientSide) {
         if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker.hasEffect(MainEffects.STUN)) {
            event.setCanceled(true);
         }
      }
   }

   @SubscribeEvent
   public static void onRightClickBlock(RightClickBlock event) {
      handlePlayerInteract(event);
   }

   @SubscribeEvent
   public static void onRightClickItem(RightClickItem event) {
      handlePlayerInteract(event);
   }

   @SubscribeEvent
   public static void onEntityInteract(EntityInteract event) {
      handlePlayerInteract(event);
   }

   @SubscribeEvent
   public static void onEntityInteractSpecific(EntityInteractSpecific event) {
      handlePlayerInteract(event);
   }

   @SubscribeEvent
   public static void onLeftClickBlock(LeftClickBlock event) {
      handlePlayerInteract(event);
   }

   private static void handlePlayerInteract(PlayerInteractEvent event) {
      if (!event.getLevel().isClientSide) {
         if (event.getEntity() != null) {
            if (event.getEntity().hasEffect(MainEffects.STUN) && event instanceof ICancellableEvent cancellable) {
               if (event instanceof RightClickBlock rightClickBlock) {
                  rightClickBlock.setCancellationResult(InteractionResult.FAIL);
               } else if (event instanceof RightClickItem rightClickItem) {
                  rightClickItem.setCancellationResult(InteractionResult.FAIL);
               } else if (event instanceof EntityInteract entityInteract) {
                  entityInteract.setCancellationResult(InteractionResult.FAIL);
               } else if (event instanceof EntityInteractSpecific entityInteractSpecific) {
                  entityInteractSpecific.setCancellationResult(InteractionResult.FAIL);
               }

               cancellable.setCanceled(true);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onLivingJump(LivingJumpEvent event) {
      if (!event.getEntity().level().isClientSide) {
         if (event.getEntity().hasEffect(MainEffects.STUN)) {
            event.getEntity().setDeltaMovement(event.getEntity().getDeltaMovement().multiply(1.0, 0.0, 1.0));
         }
      }
   }

   @SubscribeEvent
   public static void onLivingUpdate(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
      if (event.getEntity() instanceof LivingEntity entity) {
         if (!entity.level().isClientSide) {
            if (entity.hasEffect(MainEffects.STUN)) {
               entity.setDeltaMovement(0.0, entity.getDeltaMovement().y, 0.0);
               entity.setJumping(false);
               entity.setSprinting(false);
               if (entity instanceof ServerPlayer serverPlayer) {
                  if (serverPlayer.getAbilities().flying) {
                     serverPlayer.getAbilities().flying = false;
                     serverPlayer.onUpdateAbilities();
                  }

                  if (serverPlayer.isFallFlying()) {
                     serverPlayer.stopFallFlying();
                  }
               }

               if (entity.getPose() != Pose.CROUCHING) {
                  entity.setPose(Pose.CROUCHING);
               }

               if (entity instanceof Mob mob) {
                  mob.getNavigation().stop();
                  mob.setTarget(null);
                  mob.setAggressive(false);
               }
            }

            if (entity instanceof ServerPlayer serverPlayer) {
               StatsProvider.get(StatsCapability.INSTANCE, serverPlayer)
                  .ifPresent(
                     data -> {
                        if (data.getStatus().isHasCreatedCharacter()) {
                           AttributeInstance speedAttr = serverPlayer.getAttribute(Attributes.MOVEMENT_SPEED);
                           AttributeInstance attackSpeedAttr = serverPlayer.getAttribute(Attributes.ATTACK_SPEED);
                           if (speedAttr != null) {
                              double expectedBonus = 0.0;
                              if (data.getCharacter().hasActiveForm()) {
                                 FormConfig.FormData activeForm = data.getCharacter().getActiveFormData();
                                 if (activeForm != null) {
                                    double multiplier = activeForm.getSpeedMultiplier();
                                    if (multiplier != 1.0) {
                                       expectedBonus = multiplier - 1.0;
                                    }
                                 }
                              }

                              AttributeModifier existingSpeed = speedAttr.getModifier(AttributeMods.id(FORM_SPEED_UUID));
                              double currentBonus = existingSpeed != null ? existingSpeed.amount() : 0.0;
                              if (expectedBonus != currentBonus) {
                                 speedAttr.removeModifier(AttributeMods.id(FORM_SPEED_UUID));
                                 if (expectedBonus > 0.0) {
                                    speedAttr.addTransientModifier(
                                       AttributeMods.of(FORM_SPEED_UUID, "Form Speed Bonus", expectedBonus, Operation.ADD_MULTIPLIED_TOTAL)
                                    );
                                 }
                              }

                              boolean turboActive = data.getStatus().isAuraActive() || data.getStatus().isPermanentAura();
                              double expectedTurboBonus = turboActive ? 0.3 : 0.0;
                              AttributeModifier existingTurbo = speedAttr.getModifier(AttributeMods.id(TURBO_SPEED_UUID));
                              double currentTurboBonus = existingTurbo != null ? existingTurbo.amount() : 0.0;
                              if (expectedTurboBonus != currentTurboBonus) {
                                 speedAttr.removeModifier(AttributeMods.id(TURBO_SPEED_UUID));
                                 if (expectedTurboBonus > 0.0) {
                                    speedAttr.addTransientModifier(
                                       AttributeMods.of(TURBO_SPEED_UUID, "Turbo Speed Bonus", expectedTurboBonus, Operation.ADD_MULTIPLIED_TOTAL)
                                    );
                                 }
                              }
                           }

                           if (attackSpeedAttr != null) {
                              double expectedKi = 0.0;
                              if (PlayerAttackHelper.isKiWeaponActive(serverPlayer)) {
                                 CombatConfig.KiWeaponConfig kiCfg = ConfigManager.getCombatConfig().getKiWeaponConfig(data.getStatus().getKiWeaponType());
                                 if (kiCfg != null) {
                                    expectedKi = kiCfg.getAttackSpeed();
                                 }
                              }

                              AttributeModifier existingKi = attackSpeedAttr.getModifier(AttributeMods.id(KI_WEAPON_ATTACK_SPEED_UUID));
                              double currentKi = existingKi != null ? existingKi.amount() : 0.0;
                              if (Math.abs(expectedKi - currentKi) > 1.0E-9) {
                                 attackSpeedAttr.removeModifier(AttributeMods.id(KI_WEAPON_ATTACK_SPEED_UUID));
                                 if (expectedKi != 0.0) {
                                    attackSpeedAttr.addTransientModifier(
                                       AttributeMods.of(KI_WEAPON_ATTACK_SPEED_UUID, "Ki Weapon Attack Speed", expectedKi, Operation.ADD_VALUE)
                                    );
                                 }
                              }
                           }

                           if (attackSpeedAttr != null) {
                              double expectedMultiplier = 1.0;
                              if (data.getCharacter().hasActiveForm()) {
                                 FormConfig.FormData activeForm = data.getCharacter().getActiveFormData();
                                 if (activeForm != null) {
                                    expectedMultiplier *= activeForm.getAttackSpeed();
                                 }
                              }

                              if (data.getCharacter().hasActiveStackForm()) {
                                 FormConfig.FormData activeStackForm = data.getCharacter().getActiveStackFormData();
                                 if (activeStackForm != null) {
                                    expectedMultiplier *= activeStackForm.getAttackSpeed();
                                 }
                              }

                              double base = attackSpeedAttr.getBaseValue();
                              double afterAdditions = base
                                 + attackSpeedAttr.getModifiers()
                                    .stream()
                                    .filter(mx -> mx.operation() == Operation.ADD_VALUE)
                                    .toList()
                                    .stream()
                                    .mapToDouble(AttributeModifier::amount)
                                    .sum();
                              double intermediateSpeed = afterAdditions;

                              for (AttributeModifier m : attackSpeedAttr.getModifiers()
                                 .stream()
                                 .filter(mx -> mx.operation() == Operation.ADD_MULTIPLIED_BASE)
                                 .toList()) {
                                 intermediateSpeed += afterAdditions * m.amount();
                              }

                              double expectedBonusx;
                              if (intermediateSpeed > 0.0) {
                                 double targetSpeed = Math.max(0.25, intermediateSpeed * expectedMultiplier);
                                 expectedBonusx = targetSpeed / intermediateSpeed - 1.0;
                              } else {
                                 expectedBonusx = 0.0;
                              }

                              AttributeModifier existingAttackSpeed = attackSpeedAttr.getModifier(AttributeMods.id(FORM_ATTACK_SPEED_UUID));
                              double currentBonusx = existingAttackSpeed != null ? existingAttackSpeed.amount() : 0.0;
                              if (Math.abs(expectedBonusx - currentBonusx) > 1.0E-9) {
                                 attackSpeedAttr.removeModifier(AttributeMods.id(FORM_ATTACK_SPEED_UUID));
                                 if (expectedBonusx != 0.0) {
                                    attackSpeedAttr.addTransientModifier(
                                       AttributeMods.of(FORM_ATTACK_SPEED_UUID, "Form Attack Speed Bonus", expectedBonusx, Operation.ADD_MULTIPLIED_TOTAL)
                                    );
                                 }
                              }
                           }

                           AttributeInstance reachAttr = serverPlayer.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
                           AttributeInstance entityReachAttr = serverPlayer.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
                           Float[] scaling = data.getCharacter().getResolvedModelScaling();
                           float currentScaleY = scaling[1];
                           float BASE_SCALE = 0.9375F;
                           float BASE_HEIGHT = 1.8F;
                           float BASE_REACH = 4.5F;
                           float ratioY = currentScaleY / 0.9375F;
                           double expectedReach = 0.0;
                           if (ratioY > 1.01F) {
                              float currentHeight = 1.8F * ratioY;
                              expectedReach = (double)((currentHeight - 1.8F) * 2.5F);
                           }

                           if (reachAttr != null) {
                              AttributeModifier existingReach = reachAttr.getModifier(AttributeMods.id(FORM_REACH_UUID));
                              if ((existingReach != null ? existingReach.amount() : 0.0) != expectedReach) {
                                 reachAttr.removeModifier(AttributeMods.id(FORM_REACH_UUID));
                                 if (expectedReach > 0.0) {
                                    reachAttr.addTransientModifier(AttributeMods.of(FORM_REACH_UUID, "Form Reach Bonus", expectedReach, Operation.ADD_VALUE));
                                 }
                              }
                           }

                           if (entityReachAttr != null) {
                              AttributeModifier existingEntityReach = entityReachAttr.getModifier(AttributeMods.id(FORM_REACH_UUID));
                              if ((existingEntityReach != null ? existingEntityReach.amount() : 0.0) != expectedReach) {
                                 entityReachAttr.removeModifier(AttributeMods.id(FORM_REACH_UUID));
                                 if (expectedReach > 0.0) {
                                    entityReachAttr.addTransientModifier(
                                       AttributeMods.of(FORM_REACH_UUID, "Form Reach Bonus", expectedReach, Operation.ADD_VALUE)
                                    );
                                 }
                              }
                           }
                        }
                     }
                  );
            }
         }
      }
   }

   @SubscribeEvent
   public static void onFall(LivingFallEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         int[] var7 = new int[]{0};
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            if (data.getStatus().isHasCreatedCharacter()) {
               if (data.getSkills().hasSkill("jump") && data.getSkills().isSkillActive("jump")) {
                  var7[0] = data.getSkills().getSkillLevel("jump");
               }
            }
         });
         if (var7[0] > 0) {
            float maxHeight = 1.25F + (float)var7[0] * 1.0F;
            float safeHeight = maxHeight + 1.0F;
            float fallDistance = event.getDistance();
            if (fallDistance <= safeHeight) {
               player.resetFallDistance();
               event.setCanceled(true);
               event.setDamageMultiplier(0.0F);
            } else {
               float reducedDistance = fallDistance - safeHeight;
               event.setDistance(reducedDistance);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onFallDamageKiNegation(Pre event) {
      if (event.getSource().is(DamageTypes.FALL)) {
         if (event.getEntity() instanceof ServerPlayer player) {
            float damage = event.getNewDamage();
            if (!(damage <= 0.0F)) {
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
                  if (data.getStatus().isHasCreatedCharacter()) {
                     float currentKi = data.getResources().getCurrentEnergy();
                     if (!(currentKi <= 0.0F)) {
                        float kiPerDamage = 3.0F;
                        float fullCost = damage * kiPerDamage;
                        if (currentKi >= fullCost) {
                           data.getResources().removeEnergy(fullCost);
                           event.setNewDamage(0.0F);
                        } else {
                           float negatableDamage = currentKi / kiPerDamage;
                           data.getResources().removeEnergy(currentKi);
                           event.setNewDamage(damage - negatableDamage);
                        }

                        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
                     }
                  }
               });
            }
         }
      }
   }

   @SubscribeEvent
   public static void onEntitySize(Size event) {
      Entity entity = event.getEntity();
      if (entity instanceof Player player) {
         if (player.getAttributes() != null) {
            StatsProvider.get(StatsCapability.INSTANCE, entity)
               .ifPresent(
                  data -> {
                     Character character = data.getCharacter();
                     String currentForm = character.getActiveForm();
                     String race = character.getRaceName().toLowerCase();
                     String logicKey = character.getRenderLogicKey();
                     Float[] resolved = character.getResolvedModelScaling();
                     float configScaleX = resolved[0];
                     float configScaleY = resolved[1];
                     boolean isOozaru = logicKey.startsWith("oozaru")
                        || race.equals("saiyan") && (Objects.equals(currentForm, "oozaru") || Objects.equals(currentForm, "goldenoozaru"));
                     float scalingX;
                     float scalingY;
                     if (isOozaru) {
                        float baseOozaruSize = 3.8F;
                        float visualScaleX = Math.max(0.1F, configScaleX - 2.8F);
                        float visualScaleY = Math.max(0.1F, configScaleY - 2.8F);
                        scalingX = visualScaleX * baseOozaruSize;
                        scalingY = visualScaleY * baseOozaruSize;
                     } else {
                        scalingX = configScaleX;
                        scalingY = configScaleY;
                     }

                     Pose pose = event.getPose();
                     if (pose != Pose.DYING && pose != Pose.SLEEPING) {
                        float rawWidth = 0.6F * scalingX;
                        float rawHeight = 1.9F * scalingY;
                        float finalWidth = (float)Math.round(rawWidth * 10.0F) / 10.0F;
                        float finalHeight = (float)Math.round(rawHeight * 10.0F) / 10.0F;
                        float poseHeightMultiplier = 1.0F;
                        float eyeHeightMultiplier = 1.0F;
                        if (pose == Pose.CROUCHING) {
                           poseHeightMultiplier = 0.8333334F;
                           eyeHeightMultiplier = 0.7839506F;
                        } else if (pose == Pose.SWIMMING || pose == Pose.FALL_FLYING || pose == Pose.SPIN_ATTACK) {
                           poseHeightMultiplier = 0.33333334F;
                           eyeHeightMultiplier = 0.24691358F;
                        }

                        float heightConPose = finalHeight * poseHeightMultiplier;
                        float alturaSegura = (float)Math.round(heightConPose * 10.0F) / 10.0F;
                        EntityDimensions newDims = EntityDimensions.fixed(finalWidth, alturaSegura);
                        event.setNewSize(newDims);
                        float rawEyeHeight = 1.7F * scalingY * eyeHeightMultiplier;
                        float finalEyeHeight = (float)Math.round(rawEyeHeight * 10.0F) / 10.0F;
                        event.setNewSize(event.getNewSize().withEyeHeight(finalEyeHeight));
                     } else {
                        event.setNewSize(EntityDimensions.fixed(0.2F, 0.2F));
                        event.setNewSize(event.getNewSize().withEyeHeight(0.2F));
                     }
                  }
               );
         }
      }
   }

   public static class FoodRegenTask {
      public int ticksPassed = 0;
      public final int totalSeconds;
      public final float hpPerSecond;
      public final float kiPerSecond;
      public final float staminaPerSecond;

      public FoodRegenTask(int durationSeconds, float totalHp, float totalKi, float totalStam) {
         this.totalSeconds = durationSeconds;
         this.hpPerSecond = totalHp / (float)durationSeconds;
         this.kiPerSecond = totalKi / (float)durationSeconds;
         this.staminaPerSecond = totalStam / (float)durationSeconds;
      }
   }
}
