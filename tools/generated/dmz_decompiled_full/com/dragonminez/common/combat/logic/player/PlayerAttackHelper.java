package com.dragonminez.common.combat.logic.player;

import com.dragonminez.common.combat.logic.weapon.WeaponRegistry;
import com.dragonminez.common.combat.player.AttackHand;
import com.dragonminez.common.combat.player.ComboState;
import com.dragonminez.common.combat.weapon.WeaponAttributes;
import com.dragonminez.common.config.CombatConfig;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.skills.Skills;
import com.dragonminez.common.stats.techniques.Techniques;
import com.dragonminez.compat.util.LazyOptional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import org.jetbrains.annotations.Nullable;

public class PlayerAttackHelper {
   public static final double MIN_ATTACK_SPEED = 0.25;
   private static final Object attributesLock = new Object();

   public static float getDualWieldingAttackDamageMultiplier(Player player, AttackHand hand) {
      return isDualWielding(player) ? (hand.isOffHand() ? 0.9F : 1.0F) : 1.0F;
   }

   public static boolean shouldAttackWithOffHand(Player player, int comboCount) {
      return isDualWielding(player) && comboCount % 2 == 1;
   }

   public static boolean isDualWielding(Player player) {
      if (isKiWeaponActive(player)) {
         WeaponAttributes mainAttributes = getKiWeaponAttributes(player);
         if (mainAttributes != null && !mainAttributes.isTwoHanded()) {
            ItemStack offStack = player.getOffhandItem();
            if (!offStack.isEmpty() && !(offStack.getItem() instanceof ShieldItem)) {
               WeaponAttributes offAttributes = WeaponRegistry.getAttributes(offStack);
               if (offAttributes != null && !offAttributes.isTwoHanded()) {
                  String mainCategory = mainAttributes.category();
                  String offCategory = offAttributes.category();
                  return mainCategory != null && !mainCategory.isEmpty() && mainCategory.equals(offCategory);
               } else {
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         boolean mainEmpty = player.getMainHandItem().isEmpty();
         boolean offEmpty = player.getOffhandItem().isEmpty();
         if (mainEmpty || offEmpty) {
            return false;
         } else if (player.getOffhandItem().getItem() instanceof ShieldItem) {
            return false;
         } else {
            WeaponAttributes mainAttributes = WeaponRegistry.getAttributes(player.getMainHandItem());
            WeaponAttributes offAttributes = WeaponRegistry.getAttributes(player.getOffhandItem());
            return mainAttributes != null && !mainAttributes.isTwoHanded() && offAttributes != null && !offAttributes.isTwoHanded();
         }
      }
   }

   public static boolean isTwoHandedWielding(Player player) {
      if (!isKiWeaponActive(player)) {
         WeaponAttributes mainAttributes = WeaponRegistry.getAttributes(player.getMainHandItem());
         return mainAttributes != null ? mainAttributes.isTwoHanded() : false;
      } else {
         WeaponAttributes kiAttributes = getKiWeaponAttributes(player);
         return kiAttributes != null && kiAttributes.isTwoHanded();
      }
   }

   public static float getAttackCooldownTicksCapped(Player player) {
      float intervalCap = (float)ConfigManager.getCombatConfig().getAttackIntervalCap().intValue();
      double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
      if (!Double.isFinite(attackSpeed) || attackSpeed < 0.25) {
         attackSpeed = 0.25;
      }

      float rawDelay = (float)(1.0 / attackSpeed * 20.0);
      float capped = Math.max(rawDelay, intervalCap);
      return Math.max(2.0F, Mth.clamp(capped, intervalCap, 200.0F));
   }

   public static AttackHand getCurrentAttack(Player player, int comboCount) {
      if (isDualWielding(player)) {
         boolean isOffHand = shouldAttackWithOffHand(player, comboCount);
         ItemStack itemStack = isOffHand ? player.getOffhandItem() : player.getMainHandItem();
         WeaponAttributes attributes;
         if (itemStack.isEmpty()) {
            attributes = resolveEmptyHandAttributes(player);
         } else {
            attributes = WeaponRegistry.getAttributes(itemStack);
         }

         if (attributes != null && attributes.attacks() != null) {
            int handSpecificComboCount = (isOffHand && comboCount > 0 ? comboCount - 1 : comboCount) / 2;
            PlayerAttackHelper.AttackSelection attackSelection = selectAttack(handSpecificComboCount, attributes, player, isOffHand);
            if (attackSelection == null) {
               return null;
            }

            WeaponAttributes.Attack attack = attackSelection.attack;
            ComboState combo = attackSelection.comboState;
            return new AttackHand(attack, combo, isOffHand, attributes, itemStack);
         }
      } else {
         ItemStack itemStackx = player.getMainHandItem();
         WeaponAttributes attributesx;
         if (itemStackx.isEmpty()) {
            attributesx = resolveEmptyHandAttributes(player);
         } else {
            attributesx = WeaponRegistry.getAttributes(itemStackx);
         }

         if (attributesx != null && attributesx.attacks() != null) {
            PlayerAttackHelper.AttackSelection attackSelection = selectAttack(comboCount, attributesx, player, false);
            if (attackSelection == null) {
               return null;
            }

            WeaponAttributes.Attack attack = attackSelection.attack;
            ComboState combo = attackSelection.comboState;
            return new AttackHand(attack, combo, false, attributesx, itemStackx);
         }
      }

      return null;
   }

   @Nullable
   private static PlayerAttackHelper.AttackSelection selectAttack(int comboCount, WeaponAttributes attributes, Player player, boolean isOffHandAttack) {
      WeaponAttributes.Attack[] attacks = attributes.attacks();
      attacks = Arrays.stream(attacks)
         .filter(attack -> attack.conditions() == null || attack.conditions().length == 0 || evaluateConditions(attack.conditions(), player, isOffHandAttack))
         .toArray(WeaponAttributes.Attack[]::new);
      if (comboCount < 0) {
         comboCount = 0;
      }

      if (attacks.length == 0) {
         return null;
      } else {
         int index = comboCount % attacks.length;
         return new PlayerAttackHelper.AttackSelection(attacks[index], new ComboState(index + 1, attacks.length));
      }
   }

   private static boolean evaluateConditions(String[] conditions, Player player, boolean isOffHandAttack) {
      return Arrays.stream(conditions).allMatch(condition -> evaluateCondition(condition, player, isOffHandAttack));
   }

   private static boolean evaluateCondition(String raw, Player player, boolean isOffHandAttack) {
      if (raw != null && !raw.isBlank()) {
         int colon = raw.indexOf(58);
         String type;
         String[] args;
         if (colon >= 0) {
            type = raw.substring(0, colon).trim().toUpperCase();
            args = Arrays.stream(raw.substring(colon + 1).split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
         } else {
            type = raw.trim().toUpperCase();
            args = new String[0];
         }

         switch (type) {
            case "SKILL_ACTIVE": {
               if (args.length < 1) {
                  return true;
               }

               Skills skills = getSkills(player);
               return skills != null && skills.isSkillActive(args[0]);
            }
            case "SKILL_LEVEL": {
               if (args.length < 2) {
                  return true;
               } else {
                  Skills skills = getSkills(player);
                  if (skills == null) {
                     return false;
                  } else {
                     try {
                        return skills.getSkillLevel(args[0]) >= Integer.parseInt(args[1]);
                     } catch (NumberFormatException var10) {
                        return false;
                     }
                  }
               }
            }
            default:
               WeaponAttributes.Condition condition;
               try {
                  condition = WeaponAttributes.Condition.valueOf(type);
               } catch (IllegalArgumentException var11) {
                  return true;
               }

               switch (condition) {
                  case NOT_DUAL_WIELDING:
                     return !isDualWielding(player);
                  case DUAL_WIELDING_ANY:
                     return isDualWielding(player);
                  case DUAL_WIELDING_SAME:
                     return isDualWielding(player) && player.getMainHandItem().getItem() == player.getOffhandItem().getItem();
                  case DUAL_WIELDING_SAME_CATEGORY:
                     if (!isDualWielding(player)) {
                        return false;
                     } else {
                        WeaponAttributes mainHandAttributes = isKiWeaponActive(player)
                           ? getKiWeaponAttributes(player)
                           : WeaponRegistry.getAttributes(player.getMainHandItem());
                        WeaponAttributes offHandAttributes = WeaponRegistry.getAttributes(player.getOffhandItem());
                        if (mainHandAttributes != null
                           && offHandAttributes != null
                           && mainHandAttributes.category() != null
                           && !mainHandAttributes.category().isEmpty()
                           && offHandAttributes.category() != null
                           && !offHandAttributes.category().isEmpty()) {
                           return mainHandAttributes.category().equals(offHandAttributes.category());
                        }

                        return false;
                     }
                  case NO_OFFHAND_ITEM: {
                     ItemStack offhandStack = player.getOffhandItem();
                     return offhandStack.isEmpty();
                  }
                  case OFF_HAND_SHIELD: {
                     ItemStack offhandStack = player.getOffhandItem();
                     return !offhandStack.isEmpty() && offhandStack.getItem() instanceof ShieldItem;
                  }
                  case MAIN_HAND_ONLY:
                     return !isOffHandAttack;
                  case OFF_HAND_ONLY:
                     return isOffHandAttack;
                  case MOUNTED:
                     return player.getVehicle() != null;
                  case NOT_MOUNTED:
                     return player.getVehicle() == null;
                  case SNEAKING:
                     return player.isCrouching();
                  case NOT_SNEAKING:
                     return !player.isCrouching();
                  default:
                     return true;
               }
         }
      } else {
         return true;
      }
   }

   public static boolean canAttack(Player player) {
      return true;
   }

   public static boolean isChargingTechnique(Player player) {
      StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).resolve().orElse(null);
      if (stats == null) {
         return false;
      } else {
         Techniques techniques = stats.getTechniques();
         return techniques.isTechniqueCharging() || techniques.isTechniqueChargeActive();
      }
   }

   public static double getEffectiveAttackRange(Player player, double weaponAttackRange) {
      AttributeInstance entityReachAttr = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
      if (entityReachAttr == null) {
         return Math.max(0.0, weaponAttackRange);
      } else {
         double defaultEntityReach = ((Attribute)Attributes.ENTITY_INTERACTION_RANGE.value()).getDefaultValue();
         double currentEntityReach = entityReachAttr.getValue();
         double effectiveRange = weaponAttackRange + (currentEntityReach - defaultEntityReach);
         return Math.max(0.0, effectiveRange);
      }
   }

   @Nullable
   private static WeaponAttributes getActiveFormComboAttributes(Player player) {
      LazyOptional<StatsData> statsOpt = StatsProvider.get(StatsCapability.INSTANCE, player);
      if (!statsOpt.isPresent()) {
         return null;
      } else {
         StatsData stats = statsOpt.resolve().orElse(null);
         if (stats == null) {
            return null;
         } else {
            Character character = stats.getCharacter();
            if (character == null) {
               return null;
            } else {
               FormConfig.FormData formData = character.getActiveFormData();
               if (formData == null) {
                  formData = character.getActiveStackFormData();
               }

               if (formData == null) {
                  return null;
               } else {
                  String formCombo = formData.getFormCombo();
                  if (formCombo != null && !formCombo.isBlank()) {
                     String comboRaw = formCombo.trim().toLowerCase();
                     Set<ResourceLocation> candidates = new LinkedHashSet<>();
                     ResourceLocation parsed = ResourceLocation.tryParse(comboRaw);
                     if (parsed != null) {
                        candidates.add(parsed);
                     }

                     if (!comboRaw.contains(":")) {
                        candidates.add(ResourceLocation.fromNamespaceAndPath("dragonminez", comboRaw));
                        candidates.add(ResourceLocation.fromNamespaceAndPath("minecraft", comboRaw));
                     }

                     for (ResourceLocation candidate : candidates) {
                        WeaponAttributes attributes = WeaponRegistry.getAttributes(candidate);
                        if (attributes != null && attributes.attacks() != null) {
                           return attributes;
                        }
                     }

                     return null;
                  } else {
                     return null;
                  }
               }
            }
         }
      }
   }

   @Nullable
   private static Skills getSkills(Player player) {
      LazyOptional<StatsData> statsOpt = StatsProvider.get(StatsCapability.INSTANCE, player);
      if (!statsOpt.isPresent()) {
         return null;
      } else {
         StatsData stats = statsOpt.resolve().orElse(null);
         return stats != null ? stats.getSkills() : null;
      }
   }

   public static boolean isKiWeaponActive(Player player) {
      if (!player.getMainHandItem().isEmpty()) {
         return false;
      } else {
         LazyOptional<StatsData> statsOpt = StatsProvider.get(StatsCapability.INSTANCE, player);
         if (!statsOpt.isPresent()) {
            return false;
         } else {
            StatsData stats = statsOpt.resolve().orElse(null);
            if (stats != null && stats.getSkills().isSkillActive("kimanipulation")) {
               String type = stats.getStatus().getKiWeaponType();
               return type != null && !type.equalsIgnoreCase("none") ? ConfigManager.getCombatConfig().getKiWeaponConfig(type) != null : false;
            } else {
               return false;
            }
         }
      }
   }

   @Nullable
   public static WeaponAttributes getKiWeaponAttributes(Player player) {
      LazyOptional<StatsData> statsOpt = StatsProvider.get(StatsCapability.INSTANCE, player);
      if (!statsOpt.isPresent()) {
         return null;
      } else {
         StatsData stats = statsOpt.resolve().orElse(null);
         if (stats == null) {
            return null;
         } else {
            String type = stats.getStatus().getKiWeaponType();
            if (type == null) {
               return null;
            } else {
               CombatConfig.KiWeaponConfig cfg = ConfigManager.getCombatConfig().getKiWeaponConfig(type);
               String combo = cfg != null ? cfg.getWeaponCombo() : null;
               if (combo != null && !combo.isBlank()) {
                  ResourceLocation comboId = ResourceLocation.tryParse(combo.trim().toLowerCase());
                  if (comboId != null) {
                     WeaponAttributes attributes = WeaponRegistry.getAttributes(comboId);
                     if (attributes != null && attributes.attacks() != null) {
                        return attributes;
                     }
                  }
               }

               return WeaponRegistry.getAttributes(ResourceLocation.fromNamespaceAndPath("dragonminez", "fist"));
            }
         }
      }
   }

   @Nullable
   private static WeaponAttributes resolveEmptyHandAttributes(Player player) {
      if (isKiWeaponActive(player)) {
         WeaponAttributes kiAttributes = getKiWeaponAttributes(player);
         if (kiAttributes != null && kiAttributes.attacks() != null) {
            return kiAttributes;
         }
      }

      WeaponAttributes attributes = getActiveFormComboAttributes(player);
      if (attributes == null || attributes.attacks() == null) {
         attributes = WeaponRegistry.getAttributes(ResourceLocation.fromNamespaceAndPath("dragonminez", "fist"));
      }

      return attributes;
   }

   public static void offhandAttributes(Player player, Runnable runnable) {
      synchronized (attributesLock) {
         setAttributesForOffHandAttack(player, true);
         runnable.run();
         setAttributesForOffHandAttack(player, false);
      }
   }

   public static void setAttributesForOffHandAttack(Player player, boolean useOffHand) {
      ItemStack mainHandStack = player.getMainHandItem();
      ItemStack offHandStack = player.getOffhandItem();
      ItemStack add;
      ItemStack remove;
      if (useOffHand) {
         remove = mainHandStack;
         add = offHandStack;
      } else {
         remove = offHandStack;
         add = mainHandStack;
      }

      Multimap<Holder<Attribute>, AttributeModifier> removeMods = HashMultimap.create();
      Multimap<Holder<Attribute>, AttributeModifier> addMods = HashMultimap.create();
      remove.forEachModifier(EquipmentSlot.MAINHAND, removeMods::put);
      add.forEachModifier(EquipmentSlot.MAINHAND, addMods::put);
      player.getAttributes().removeAttributeModifiers(removeMods);
      player.getAttributes().addTransientAttributeModifiers(addMods);
   }

   private static record AttackSelection(WeaponAttributes.Attack attack, ComboState comboState) {
   }
}
