package com.dragonminez.server.util;

import com.dragonminez.common.compat.WorldGuardCompat;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.init.entities.AllMastersEntity;
import com.dragonminez.common.init.item.WeightItem;
import com.dragonminez.common.quest.QuestUnlocks;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.util.AttributeMods;
import com.dragonminez.server.world.dimension.HTCDimension;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

public class GravityLogic {
   public static final UUID GRAVITY_SPEED_UUID = UUID.fromString("019c3047-cd2f-7af4-a3cd-5bca51dd3588");
   private static final UUID GRAVITY_ATTACK_SPEED_UUID = UUID.fromString("019c3047-4e91-74e1-ac87-d4ea8e463688");
   private static final Map<UUID, Double> NPC_GRAVITY_CACHE = new HashMap<>();
   private static final Map<UUID, Long> NPC_GRAVITY_TICK = new HashMap<>();
   private static final Map<UUID, String> NPC_GRAVITY_DIM = new HashMap<>();

   private static GeneralServerConfig.GravityConfig cfg() {
      return ConfigManager.getServerConfig().getGravity();
   }

   public static double getGravityMultiplier(Player player) {
      return computeGravity(player, true);
   }

   public static double getTrainingGravityMultiplier(Player player) {
      boolean htc = player.level().dimension().equals(HTCDimension.HTC_KEY);
      return computeGravity(player, !htc);
   }

   private static double computeGravity(Player player, boolean includeDimension) {
      GeneralServerConfig.GravityConfig config = cfg();
      if (!config.isEnabled()) {
         return 1.0;
      } else {
         String dimId = player.level().dimension().location().toString();
         double ambient = includeDimension ? config.getWorldGravity(dimId) : config.getDefaultWorldGravity();
         double wgGravity = WorldGuardCompat.getGravity(player.level(), player.blockPosition(), player);
         if (wgGravity > ambient) {
            ambient = wgGravity;
         }

         double npcGravity = getNpcGravity(player);
         if (npcGravity > ambient) {
            ambient = npcGravity;
         }

         double machineExtra = Math.max(0.0, getMachineGravity(player) - 1.0);
         return Math.max(0.0, ambient + machineExtra);
      }
   }

   private static double avgEffectiveStats(StatsData data) {
      return (
            (double)data.getStats().getStrength() * data.getTotalMultiplier("STR")
               + (double)data.getStats().getStrikePower() * data.getTotalMultiplier("SKP")
               + (double)data.getStats().getResistance() * data.getTotalMultiplier("RES")
               + (double)data.getStats().getVitality() * data.getTotalMultiplier("VIT")
               + (double)data.getStats().getKiPower() * data.getTotalMultiplier("PWR")
               + (double)data.getStats().getEnergy() * data.getTotalMultiplier("ENE")
         )
         / 6.0;
   }

   private static double getResistance(Player player) {
      GeneralServerConfig.GravityConfig config = cfg();
      return StatsProvider.get(StatsCapability.INSTANCE, player).map(data -> {
         int maxStats = ConfigManager.getServerConfig().getGameplay().getMaxValue();
         double div = Math.max(1.0, (double)maxStats * config.getResistanceStatDivisorRatio());
         return avgEffectiveStats(data) / div * config.getResistanceScale();
      }).orElse(0.0);
   }

   public static double getGravityRoomReliefFraction(Player player) {
      if (getMachineGravity(player) <= 1.0) {
         return 0.0;
      } else {
         GeneralServerConfig.GravityConfig config = cfg();
         double base;
         double falloff;
         if (QuestUnlocks.isCompleted(player, "bulma_gravity_mk3")) {
            base = config.getGravityRoomMk3Relief();
            falloff = config.getGravityRoomMk3Falloff();
         } else {
            if (!QuestUnlocks.isCompleted(player, "bulma_gravity_mk2")) {
               return 0.0;
            }

            base = config.getGravityRoomMk2Relief();
            falloff = config.getGravityRoomMk2Falloff();
         }

         if (base <= 0.0) {
            return 0.0;
         } else {
            double powerNorm = StatsProvider.get(StatsCapability.INSTANCE, player).map(data -> {
               double div = Math.max(1.0, (double)ConfigManager.getServerConfig().getGameplay().getMaxValue().intValue());
               return Math.max(0.0, avgEffectiveStats(data) / div);
            }).orElse(0.0);
            double relief = base * Math.exp(-falloff * powerNorm);
            return Math.max(0.0, Math.min(base, relief));
         }
      }
   }

   public static double getNetGravity(Player player) {
      double gravity = getGravityMultiplier(player);
      return gravity <= 1.0 ? 0.0 : Math.max(0.0, gravity - getResistance(player));
   }

   public static double getBonusGravity(Player player) {
      double netGravity = getNetGravity(player);
      GeneralServerConfig.GravityConfig config = cfg();
      return netGravity >= config.getHardStopThreshold() ? 0.0 : netGravity;
   }

   public static double getPenalizationGravity(Player player) {
      return getNetGravity(player);
   }

   public static double getTrainingBonusGravity(Player player) {
      double gravity = getTrainingGravityMultiplier(player);
      if (gravity <= 1.0) {
         return 0.0;
      } else {
         double net = Math.max(0.0, gravity - getResistance(player));
         return net >= cfg().getHardStopThreshold() ? 0.0 : net;
      }
   }

   private static double getNpcGravity(Player player) {
      GeneralServerConfig.GravityConfig config = cfg();
      UUID id = player.getUUID();
      long currentTick = player.level().getGameTime();
      String currentDim = player.level().dimension().location().toString();
      boolean dimChanged = !currentDim.equals(NPC_GRAVITY_DIM.get(id));
      boolean expired = currentTick - NPC_GRAVITY_TICK.getOrDefault(id, 0L) > 100L;
      if (!dimChanged && !expired && NPC_GRAVITY_CACHE.containsKey(id)) {
         return NPC_GRAVITY_CACHE.getOrDefault(id, 0.0);
      } else {
         double gravity = 0.0;
         double range = config.getNpcGravityRange();
         AABB searchBox = player.getBoundingBox().inflate(range);
         List<AllMastersEntity.MasterKaiosamaEntity> kais = player.level().getEntitiesOfClass(AllMastersEntity.MasterKaiosamaEntity.class, searchBox);
         if (!kais.isEmpty()) {
            gravity = config.getNpcGravityValue();
         }

         NPC_GRAVITY_CACHE.put(id, gravity);
         NPC_GRAVITY_TICK.put(id, currentTick);
         NPC_GRAVITY_DIM.put(id, currentDim);
         return gravity;
      }
   }

   public static double getMachineGravity(Player player) {
      return !cfg().getMachineGravityEnabled() ? 0.0 : GravityDeviceManager.getGravityFor(player);
   }

   public static int getTotalWeight(Player player) {
      int[] totalWeight = new int[]{0};
      CuriosApi.getCuriosInventory(player).ifPresent(inv -> {
         ICurioStacksHandler handler = (ICurioStacksHandler)inv.getCurios().get("weights");
         if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
               ItemStack stack = handler.getStacks().getStackInSlot(i);
               if (stack.getItem() instanceof WeightItem) {
                  totalWeight[0] += WeightItem.getWeight(stack);
               } else if (!stack.isEmpty()) {
                  totalWeight[0] += ((CustomData)stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag().getInt("WeightValue");
               }
            }
         }
      });
      return totalWeight[0];
   }

   private static double computeRelativeLevel(StatsData data) {
      int currentBaseLevel = data.getLevel();
      int totalBaseStats = data.getStats().getTotalStats();
      int initialStats = totalBaseStats - (currentBaseLevel - 1) * 6;
      double boostedTotal = (double)data.getStats().getStrength() * data.getTotalMultiplier("STR")
         + (double)data.getStats().getStrikePower() * data.getTotalMultiplier("SKP")
         + (double)data.getStats().getResistance() * data.getTotalMultiplier("RES")
         + (double)data.getStats().getVitality() * data.getTotalMultiplier("VIT")
         + (double)data.getStats().getKiPower() * data.getTotalMultiplier("PWR")
         + (double)data.getStats().getEnergy() * data.getTotalMultiplier("ENE");
      return (boostedTotal - (double)initialStats) / 6.0 + 1.0;
   }

   private static double loadGravityFactor(Player player) {
      double gravity = getGravityMultiplier(player);
      return Math.max(1.0E-4, 1.0 + (gravity - 1.0) * cfg().getGravitySensitivity());
   }

   public static int getEffectiveWeight(Player player) {
      return (int)Math.round((double)getTotalWeight(player) * loadGravityFactor(player));
   }

   public static int getIdealWeight(Player player) {
      GeneralServerConfig.GravityConfig config = cfg();
      return !config.getTpEnabled() ? 0 : StatsProvider.get(StatsCapability.INSTANCE, player).map(data -> {
         double capacity = computeRelativeLevel(data) / config.getTpIdealBaseDivisor();
         return (int)Math.max(0L, Math.round(capacity));
      }).orElse(0);
   }

   public static double getLoadRatio(Player player) {
      int ideal = getIdealWeight(player);
      return ideal <= 0 ? 0.0 : (double)getEffectiveWeight(player) / (double)ideal;
   }

   public static int getTrainingZone(Player player) {
      if (getTotalWeight(player) <= 0) {
         return 0;
      } else {
         int ideal = getIdealWeight(player);
         if (ideal <= 0) {
            return 0;
         } else {
            GeneralServerConfig.GravityConfig config = cfg();
            double r = (double)getEffectiveWeight(player) / (double)ideal;
            if (r < config.getTpIdealRatioLow()) {
               return 1;
            } else if (r <= config.getTpIdealRatioHigh()) {
               return 2;
            } else {
               return r < config.getTpOverloadHardRatio() ? 3 : 4;
            }
         }
      }
   }

   public static double getWeightTpMultiplier(Player player) {
      GeneralServerConfig.GravityConfig config = cfg();
      if (!config.getTpEnabled()) {
         return 1.0;
      } else if (getTotalWeight(player) <= 0) {
         return 1.0;
      } else {
         int ideal = getIdealWeight(player);
         return ideal <= 0 ? 1.0 : weightTpMultiplierForRatio((double)getEffectiveWeight(player) / (double)ideal, config);
      }
   }

   private static double weightTpMultiplierForRatio(double r, GeneralServerConfig.GravityConfig config) {
      double comfortLow = config.getTpComfortRatioLow();
      double idealLow = config.getTpIdealRatioLow();
      double idealHigh = config.getTpIdealRatioHigh();
      double overload = config.getTpOverloadRatio();
      double overloadHard = config.getTpOverloadHardRatio();
      double comfortMult = config.getTpComfortMultiplier();
      double peak = config.getTpPeakMultiplier();
      double heavyMult = config.getTpHeavyMultiplier();
      if (r <= 0.0) {
         return 1.0;
      } else if (comfortLow > 0.0 && r < comfortLow) {
         return lerp(1.0, comfortMult, r / comfortLow);
      } else if (r < idealLow) {
         return lerp(comfortMult, peak, (r - comfortLow) / (idealLow - comfortLow));
      } else if (r <= idealHigh) {
         return peak;
      } else if (r <= overload) {
         return lerp(peak, heavyMult, (r - idealHigh) / (overload - idealHigh));
      } else {
         return r < overloadHard ? lerp(heavyMult, 1.0, (r - overload) / (overloadHard - overload)) : 1.0;
      }
   }

   public static double getWeightPenaltyFactor(Player player) {
      GeneralServerConfig.GravityConfig config = cfg();
      if (!config.getTpEnabled()) {
         return 0.0;
      } else if (getTotalWeight(player) <= 0) {
         return 0.0;
      } else {
         int ideal = getIdealWeight(player);
         if (ideal <= 0) {
            return 0.0;
         } else {
            double r = (double)getEffectiveWeight(player) / (double)ideal;
            double idealHigh = config.getTpIdealRatioHigh();
            double overloadHard = config.getTpOverloadHardRatio();
            if (r <= idealHigh) {
               return 0.0;
            } else {
               double max = config.getMaxWeightPenalty();
               double penalty = r >= overloadHard ? max : max * (r - idealHigh) / (overloadHard - idealHigh);
               double relief = getGravityRoomReliefFraction(player);
               if (relief > 0.0) {
                  penalty *= 1.0 - relief;
               }

               return penalty;
            }
         }
      }
   }

   public static double getStatReduction(Player player) {
      GeneralServerConfig.GravityConfig config = cfg();
      if (!config.getStatReductionEnabled()) {
         return 0.0;
      } else {
         double pGravity = getPenalizationGravity(player);
         double reduction = 0.0;
         if (pGravity > 0.0) {
            reduction = pGravity * config.getStatReductionPerGravity();
            reduction = Math.max(config.getMinStatReduction(), Math.min(config.getMaxStatReduction(), reduction));
         }

         return Math.min(config.getMaxStatReduction(), reduction + getWeightPenaltyFactor(player));
      }
   }

   private static double lerp(double a, double b, double t) {
      if (t <= 0.0) {
         return a;
      } else {
         return t >= 1.0 ? b : a + (b - a) * t;
      }
   }

   public static double getGeneralPenaltyFactor(double pGravity) {
      if (pGravity <= 0.0) {
         return 0.0;
      } else {
         double baseCurve = Math.sqrt(pGravity / 100.0);
         return baseCurve * cfg().getPenaltyCurveFactor();
      }
   }

   public static double getJumpFactor(Player player) {
      GeneralServerConfig.GravityConfig config = cfg();
      if (!config.getPhysicalEnabled()) {
         return 1.0;
      } else {
         double pGravity = getPenalizationGravity(player);
         if (pGravity <= 0.0) {
            return 1.0;
         } else if (pGravity >= config.getHardStopThreshold()) {
            return 1.0 - config.getMaxJumpPenalty();
         } else {
            double penalty = Math.min(config.getMaxJumpPenalty(), getGeneralPenaltyFactor(pGravity));
            return 1.0 - penalty;
         }
      }
   }

   public static double getFlyFactor(Player player) {
      GeneralServerConfig.GravityConfig config = cfg();
      if (!config.getPhysicalEnabled()) {
         return 1.0;
      } else {
         double pGravity = getPenalizationGravity(player);
         if (pGravity <= 0.0) {
            return 1.0;
         } else if (pGravity >= config.getHardStopThreshold()) {
            return 0.0;
         } else {
            double penalty = Math.min(config.getMaxFlyPenalty(), getGeneralPenaltyFactor(pGravity));
            return 1.0 - penalty;
         }
      }
   }

   public static boolean isFlightHardStopped(Player player) {
      GeneralServerConfig.GravityConfig config = cfg();
      return !config.getPhysicalEnabled() ? false : getPenalizationGravity(player) >= config.getHardStopThreshold();
   }

   public static double getFallExtra(Player player) {
      GeneralServerConfig.GravityConfig config = cfg();
      if (!config.getPhysicalEnabled()) {
         return 0.0;
      } else {
         double pGravity = getPenalizationGravity(player);
         return pGravity <= 0.0 ? 0.0 : Math.min(config.getMaxExtraFall(), pGravity * config.getExtraFallPerGravity());
      }
   }

   public static void tick(ServerPlayer player) {
      GeneralServerConfig.GravityConfig config = cfg();
      double pGravity = getPenalizationGravity(player);
      AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
      if (movementSpeed != null && attackSpeed != null) {
         movementSpeed.removeModifier(AttributeMods.id(GRAVITY_SPEED_UUID));
         attackSpeed.removeModifier(AttributeMods.id(GRAVITY_ATTACK_SPEED_UUID));
         double weightPenalty = getWeightPenaltyFactor(player);
         double movePenalty = 0.0;
         double attackPenalty = 0.0;
         if (pGravity > 0.0) {
            if (pGravity >= config.getHardStopThreshold()) {
               movePenalty = config.getMaxMovementPenalty();
               attackPenalty = config.getMaxAttackPenalty();
            } else {
               movePenalty = Math.min(config.getMaxMovementPenalty(), getGeneralPenaltyFactor(pGravity));
               attackPenalty = Math.min(config.getMaxAttackPenalty(), Math.sqrt(pGravity / 100.0));
            }
         }

         movePenalty = Math.min(config.getMaxMovementPenalty(), movePenalty + weightPenalty);
         attackPenalty = Math.min(config.getMaxAttackPenalty(), attackPenalty + weightPenalty);
         if (movePenalty > 0.0) {
            movementSpeed.addTransientModifier(AttributeMods.of(GRAVITY_SPEED_UUID, "Gravity movement penalty", -movePenalty, Operation.ADD_MULTIPLIED_TOTAL));
         }

         if (attackPenalty > 0.0) {
            attackSpeed.addTransientModifier(
               AttributeMods.of(GRAVITY_ATTACK_SPEED_UUID, "Gravity attack speed penalty", -attackPenalty, Operation.ADD_MULTIPLIED_TOTAL)
            );
         }

         applyStatReduction(player, config);
      }
   }

   private static void applyStatReduction(ServerPlayer player, GeneralServerConfig.GravityConfig config) {
      StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
         String[] stats = config.getAffectedStats();
         double reduction = getStatReduction(player);
         if (reduction <= 0.0) {
            for (String stat : stats) {
               data.getBonusStats().removeBonusSplit(stat, "Gravity");
            }
         } else {
            double multiplier = 1.0 - reduction;

            for (String stat : stats) {
               data.getBonusStats().addBonusSplit(stat, "Gravity", "*", multiplier, false);
            }
         }
      });
   }

   public static void clearNpcGravityCache(UUID playerId) {
      NPC_GRAVITY_CACHE.remove(playerId);
      NPC_GRAVITY_TICK.remove(playerId);
      NPC_GRAVITY_DIM.remove(playerId);
   }

   public static double getConsumptionMultiplier(Player player) {
      double pGravity = getPenalizationGravity(player);
      return pGravity <= 0.0 ? 1.0 : 1.0 + pGravity * cfg().getConsumptionPerGravity();
   }
}
