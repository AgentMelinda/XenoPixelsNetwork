package com.dragonminez.common.util;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.ActionMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TransformationsHelper {
   public static List<TransformationsHelper.OrderedFormEntry> getOrderedFormsForRace(String raceName, List<String> formTypeOrder) {
      List<TransformationsHelper.OrderedFormEntry> result = new ArrayList<>();
      Map<String, FormConfig> allGroups = ConfigManager.getAllFormsForRace(raceName);
      if (allGroups != null && !allGroups.isEmpty()) {
         Map<String, Integer> typeOrderIndex = new HashMap<>();
         if (formTypeOrder != null) {
            for (int i = 0; i < formTypeOrder.size(); i++) {
               typeOrderIndex.put(formTypeOrder.get(i).toLowerCase(Locale.ROOT), i);
            }
         }

         for (Entry<String, FormConfig> entry : allGroups.entrySet()) {
            String groupName = entry.getKey();
            FormConfig formConfig = entry.getValue();
            if (formConfig != null) {
               String formType = formConfig.getFormType() != null ? formConfig.getFormType().toLowerCase(Locale.ROOT) : "";
               if (!formType.equalsIgnoreCase("android")) {
                  for (FormConfig.FormData formData : formConfig.getForms().values()) {
                     if (formData != null) {
                        result.add(new TransformationsHelper.OrderedFormEntry(groupName, formType, formData));
                     }
                  }
               }
            }
         }

         result.sort(
            Comparator.<TransformationsHelper.OrderedFormEntry>comparingInt(item -> typeOrderIndex.getOrDefault(item.getFormType(), Integer.MAX_VALUE))
               .thenComparingInt(item -> item.getFormData().getUnlockOnSkillLevel() != null ? item.getFormData().getUnlockOnSkillLevel() : 0)
               .thenComparing(item -> item.getFormData().getName(), String.CASE_INSENSITIVE_ORDER)
         );
         return result;
      } else {
         return result;
      }
   }

   public static List<FormConfig.FormData> getUnlockedForms(StatsData statsData, String raceName, String groupName) {
      List<FormConfig.FormData> unlockedForms = new ArrayList<>();
      FormConfig formConfig = ConfigManager.getFormGroup(raceName, groupName);
      if (formConfig == null) {
         return unlockedForms;
      } else {
         boolean isAndroidGroup = "androidforms".equalsIgnoreCase(groupName);
         boolean isGodGroup = formConfig.getFormType().equalsIgnoreCase("god");
         boolean isAndroidUpgraded = statsData.getStatus().isAndroidUpgraded();
         boolean isOozaruGroup = "oozaru".equalsIgnoreCase(formConfig.getGroupName());
         boolean hasTail = statsData.getCharacter().isHasSaiyanTail();
         if (isAndroidGroup && !isAndroidUpgraded) {
            return unlockedForms;
         } else if (isAndroidUpgraded && !isAndroidGroup && !isGodGroup) {
            return unlockedForms;
         } else {
            String formType = formConfig.getFormType();

            for (FormConfig.FormData formData : formConfig.getForms().values()) {
               if ((!isOozaruGroup || hasTail || !isTailOnlyOozaruForm(formData.getName()))
                  && hasFormSkillAccess(statsData, groupName, formType, formData.getUnlockOnSkillLevel())
                  && meetsMasteryRequisite(statsData, formData)) {
                  unlockedForms.add(formData);
               }
            }

            return unlockedForms;
         }
      }
   }

   public static List<FormConfig.FormData> getUnlockedStackForms(StatsData statsData, String groupName) {
      List<FormConfig.FormData> unlockedForms = new ArrayList<>();
      FormConfig formConfig = ConfigManager.getStackFormGroup(groupName);
      if (formConfig == null) {
         return unlockedForms;
      } else {
         String formType = formConfig.getFormType();

         for (FormConfig.FormData formData : formConfig.getForms().values()) {
            if (isStackFormUnlocked(statsData, formType, formData.getUnlockOnSkillLevel()) && meetsMasteryRequisite(statsData, formData)) {
               unlockedForms.add(formData);
            }
         }

         return unlockedForms;
      }
   }

   public static String getSkillNameForType(String formType) {
      String lower = formType.toLowerCase();
      if (lower.contains("superform")) {
         return "superforms";
      } else if (lower.contains("legendaryform")) {
         return "legendaryforms";
      } else if (lower.contains("godform")) {
         return "godforms";
      } else {
         return lower.contains("androidform") ? "androidforms" : formType;
      }
   }

   private static boolean isFormUnlocked(StatsData statsData, String formType, int requiredLevel) {
      return statsData.getSkills().isUnlockedAtLevel(getSkillNameForType(formType), requiredLevel);
   }

   public static boolean hasMutantLegendaryAccess(StatsData statsData, String groupName) {
      if (statsData != null && groupName != null) {
         if (!statsData.getEffects().hasEffect("mutant")) {
            return false;
         } else {
            String legendaryGroup = "legendaryforms";
            if (ConfigManager.getServerConfig() != null && ConfigManager.getServerConfig().getMutant() != null) {
               legendaryGroup = ConfigManager.getServerConfig().getMutant().getLegendaryGroupName();
            }

            return groupName.equalsIgnoreCase(legendaryGroup);
         }
      } else {
         return false;
      }
   }

   private static boolean hasFormSkillAccess(StatsData statsData, String groupName, String formType, int requiredLevel) {
      int effectiveRequiredLevel = requiredLevel;
      if (hasMutantLegendaryAccess(statsData, groupName)) {
         effectiveRequiredLevel = Math.max(0, requiredLevel - 1);
      }

      return isFormUnlocked(statsData, formType, effectiveRequiredLevel);
   }

   private static boolean isStackFormUnlocked(StatsData statsData, String formType, int requiredLevel) {
      return formType != null && !formType.isEmpty() ? statsData.getSkills().isUnlockedAtLevel(formType.toLowerCase(Locale.ROOT), requiredLevel) : false;
   }

   private static boolean meetsMasteryRequisite(StatsData statsData, FormConfig.FormData formData) {
      if (formData == null) {
         return false;
      } else if (statsData.getPlayer() != null && statsData.getPlayer().isCreative()) {
         return true;
      } else {
         String req = formData.getFormRequisite();
         double need = formData.getUnlockOnMastery();
         if (req != null && !req.isEmpty() && !(need <= 0.0)) {
            boolean any = "any".equalsIgnoreCase(formData.getFormRequisiteType());
            boolean sawValid = false;
            boolean allMet = true;

            for (String token : req.split(",")) {
               String entry = token.trim();
               if (!entry.isEmpty()) {
                  int dot = entry.indexOf(46);
                  if (dot > 0 && dot < entry.length() - 1) {
                     String reqGroup = entry.substring(0, dot);
                     String reqForm = entry.substring(dot + 1);
                     double have = Math.max(
                        statsData.getCharacter().getFormMasteries().getMastery(reqGroup, reqForm),
                        statsData.getCharacter().getStackFormMasteries().getMastery(reqGroup, reqForm)
                     );
                     boolean met = have >= need;
                     sawValid = true;
                     if (any) {
                        if (met) {
                           return true;
                        }
                     } else if (!met) {
                        allMet = false;
                     }
                  }
               }
            }

            return !sawValid ? true : !any && allMet;
         } else {
            return true;
         }
      }
   }

   public static boolean areFormsCompatible(FormConfig.FormData baseForm, String baseGroup, FormConfig.FormData stackForm, String stackGroup) {
      if (baseForm == null || stackForm == null) {
         return true;
      } else {
         return baseForm.isIncompatibleWith(stackGroup, stackForm.getName()) ? false : !stackForm.isIncompatibleWith(baseGroup, baseForm.getName());
      }
   }

   public static List<String> getSelectableFormNames(StatsData statsData, String race, String groupName) {
      if (groupName != null && !groupName.isEmpty()) {
         List<FormConfig.FormData> unlockedForms = getUnlockedForms(statsData, race, groupName);
         return unlockedForms.stream().filter(formData -> isFormSelectable(statsData, groupName, formData, false)).map(FormConfig.FormData::getName).toList();
      } else {
         return Collections.emptyList();
      }
   }

   private static boolean meetsFreeTransformMasteryFor(StatsData statsData, String groupName, FormConfig.FormData formData, boolean stack) {
      if (formData == null) {
         return false;
      } else if (statsData.getPlayer() != null && statsData.getPlayer().isCreative()) {
         return true;
      } else if (isFirstUnlockedForm(statsData, groupName, formData, stack)) {
         return true;
      } else {
         double have = stack
            ? statsData.getCharacter().getStackFormMasteries().getMastery(groupName, formData.getName())
            : statsData.getCharacter().getFormMasteries().getMastery(groupName, formData.getName());
         return have >= formData.getAllowFreeTransformOnMastery();
      }
   }

   private static boolean isFirstUnlockedForm(StatsData statsData, String groupName, FormConfig.FormData formData, boolean stack) {
      if (formData == null) {
         return false;
      } else {
         List<FormConfig.FormData> unlocked = stack
            ? getUnlockedStackForms(statsData, groupName)
            : getUnlockedForms(statsData, statsData.getCharacter().getRaceName(), groupName);
         return !unlocked.isEmpty() && unlocked.get(0).getName().equalsIgnoreCase(formData.getName());
      }
   }

   public static List<String> getSelectableStackFormNames(StatsData statsData, String groupName) {
      if (groupName != null && !groupName.isEmpty()) {
         List<FormConfig.FormData> unlockedForms = getUnlockedStackForms(statsData, groupName);
         return unlockedForms.stream().filter(formData -> isFormSelectable(statsData, groupName, formData, true)).map(FormConfig.FormData::getName).toList();
      } else {
         return Collections.emptyList();
      }
   }

   private static boolean isFormSelectable(StatsData statsData, String groupName, FormConfig.FormData formData, boolean stack) {
      return meetsFreeTransformMasteryFor(statsData, groupName, formData, stack);
   }

   public static String getGroupWithFirstAvailableForm(StatsData statsData) {
      String race = statsData.getCharacter().getRaceName();
      Map<String, FormConfig> allGroups = ConfigManager.getAllFormsForRace(race);
      if (allGroups != null && !allGroups.isEmpty()) {
         List<String> preferredTypes = new ArrayList<>();
         List<String> allTypes = new ArrayList<>();

         for (FormConfig config : allGroups.values()) {
            if (config != null) {
               String formType = config.getFormType();
               if (formType != null && !formType.isEmpty()) {
                  String lowerType = formType.toLowerCase(Locale.ROOT);
                  if (!allTypes.contains(lowerType)) {
                     allTypes.add(lowerType);
                  }

                  boolean hasSkill = statsData.getSkills().getSkillLevel(getSkillNameForType(formType)) > 0;
                  boolean mutantLegendary = lowerType.contains("legendary") && statsData.getEffects().hasEffect("mutant");
                  if ((hasSkill || mutantLegendary) && !preferredTypes.contains(lowerType)) {
                     preferredTypes.add(lowerType);
                  }
               }
            }
         }

         if (preferredTypes.isEmpty()) {
            preferredTypes.addAll(allTypes);
         }

         for (String formType : preferredTypes) {
            String group = findBestGroupByType(statsData, race, allGroups, formType);
            if (group != null) {
               return group;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static String getFirstAvailableForm(StatsData statsData) {
      String group = getGroupWithFirstAvailableForm(statsData);
      if (group == null) {
         return null;
      } else {
         FormConfig config = ConfigManager.getFormGroup(statsData.getCharacter().getRaceName(), group);
         if (config == null) {
            return null;
         } else {
            boolean isOozaruGroup1 = config.getGroupName().contains("oozaru");
            boolean hasTail1 = statsData.getCharacter().isHasSaiyanTail();
            Optional<FormConfig.FormData> firstForm = config.getForms()
               .values()
               .stream()
               .filter(f -> !isOozaruGroup1 || hasTail1 || !isTailOnlyOozaruForm(f.getName()))
               .filter(f -> hasFormSkillAccess(statsData, group, config.getFormType(), f.getUnlockOnSkillLevel()) && meetsMasteryRequisite(statsData, f))
               .min(Comparator.comparingInt(FormConfig.FormData::getUnlockOnSkillLevel));
            return firstForm.map(FormConfig.FormData::getName).orElse(null);
         }
      }
   }

   public static int getFirstAvailableFormLevel(StatsData statsData) {
      String group = getGroupWithFirstAvailableForm(statsData);
      if (group == null) {
         return -1;
      } else {
         FormConfig config = ConfigManager.getFormGroup(statsData.getCharacter().getRaceName(), group);
         if (config == null) {
            return -1;
         } else {
            boolean isOozaruGroup2 = config.getGroupName().contains("oozaru");
            boolean hasTail2 = statsData.getCharacter().isHasSaiyanTail();
            Optional<FormConfig.FormData> firstForm = config.getForms()
               .values()
               .stream()
               .filter(f -> !isOozaruGroup2 || hasTail2 || !isTailOnlyOozaruForm(f.getName()))
               .filter(f -> hasFormSkillAccess(statsData, group, config.getFormType(), f.getUnlockOnSkillLevel()) && meetsMasteryRequisite(statsData, f))
               .min(Comparator.comparingInt(FormConfig.FormData::getUnlockOnSkillLevel));
            return firstForm.map(FormConfig.FormData::getUnlockOnSkillLevel).orElse(-1);
         }
      }
   }

   public static String getGroupWithFirstAvailableStackForm(StatsData statsData) {
      Map<String, FormConfig> allGroups = ConfigManager.getAllStackForms();
      if (allGroups != null && !allGroups.isEmpty()) {
         List<String> preferredTypes = new ArrayList<>();

         for (FormConfig config : allGroups.values()) {
            String formType = config.getFormType();
            if (formType != null
               && !formType.isEmpty()
               && statsData.getSkills().getSkillLevel(formType) > 0
               && !preferredTypes.contains(formType.toLowerCase())) {
               preferredTypes.add(formType.toLowerCase());
            }
         }

         if (preferredTypes.isEmpty()) {
            for (FormConfig configx : allGroups.values()) {
               String formType = configx.getFormType();
               if (formType != null && !formType.isEmpty() && !preferredTypes.contains(formType.toLowerCase())) {
                  preferredTypes.add(formType.toLowerCase());
               }
            }
         }

         for (String formType : preferredTypes) {
            String group = findBestStackGroupByType(statsData, allGroups, formType);
            if (group != null) {
               return group;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static String getFirstAvailableStackForm(StatsData statsData) {
      String group = getGroupWithFirstAvailableStackForm(statsData);
      if (group == null) {
         return null;
      } else {
         FormConfig config = ConfigManager.getStackFormGroup(group);
         if (config == null) {
            return null;
         } else {
            Optional<FormConfig.FormData> firstForm = config.getForms()
               .values()
               .stream()
               .filter(f -> isStackFormUnlocked(statsData, config.getFormType(), f.getUnlockOnSkillLevel()) && meetsMasteryRequisite(statsData, f))
               .min(Comparator.comparingInt(FormConfig.FormData::getUnlockOnSkillLevel));
            return firstForm.map(FormConfig.FormData::getName).orElse(null);
         }
      }
   }

   public static int getFirstAvailableStackFormLevel(StatsData statsData) {
      String group = getGroupWithFirstAvailableStackForm(statsData);
      if (group == null) {
         return -1;
      } else {
         FormConfig config = ConfigManager.getStackFormGroup(group);
         if (config == null) {
            return -1;
         } else {
            Optional<FormConfig.FormData> firstForm = config.getForms()
               .values()
               .stream()
               .filter(f -> isStackFormUnlocked(statsData, config.getFormType(), f.getUnlockOnSkillLevel()) && meetsMasteryRequisite(statsData, f))
               .min(Comparator.comparingInt(FormConfig.FormData::getUnlockOnSkillLevel));
            return firstForm.map(FormConfig.FormData::getUnlockOnSkillLevel).orElse(-1);
         }
      }
   }

   private static String findBestGroupByType(StatsData statsData, String race, Map<String, FormConfig> allGroups, String formType) {
      int lowestReqLevel = Integer.MAX_VALUE;
      String selectedGroup = null;

      for (Entry<String, FormConfig> entry : allGroups.entrySet()) {
         String groupKey = entry.getKey();
         FormConfig config = ConfigManager.getFormGroup(race, groupKey);
         if (config != null && config.getFormType().toLowerCase().contains(formType)) {
            boolean isOozaruGroupBest = config.getGroupName().contains("oozaru");
            boolean hasTailBest = statsData.getCharacter().isHasSaiyanTail();
            int[] reqLevels = config.getForms()
               .values()
               .stream()
               .filter(f -> !isOozaruGroupBest || hasTailBest || !isTailOnlyOozaruForm(f.getName()))
               .filter(f -> meetsMasteryRequisite(statsData, f))
               .mapToInt(FormConfig.FormData::getUnlockOnSkillLevel)
               .filter(req -> hasFormSkillAccess(statsData, groupKey, config.getFormType(), req))
               .sorted()
               .toArray();
            if (reqLevels.length > 0 && reqLevels[0] < lowestReqLevel) {
               lowestReqLevel = reqLevels[0];
               selectedGroup = groupKey;
            }
         }
      }

      return selectedGroup;
   }

   private static String findBestStackGroupByType(StatsData statsData, Map<String, FormConfig> allGroups, String formType) {
      int lowestReqLevel = Integer.MAX_VALUE;
      String selectedGroup = null;

      for (Entry<String, FormConfig> entry : allGroups.entrySet()) {
         String groupKey = entry.getKey();
         FormConfig config = entry.getValue();
         if (config != null && config.getFormType().toLowerCase().contains(formType)) {
            int[] reqLevels = config.getForms()
               .values()
               .stream()
               .filter(f -> meetsMasteryRequisite(statsData, f))
               .mapToInt(FormConfig.FormData::getUnlockOnSkillLevel)
               .filter(req -> isStackFormUnlocked(statsData, config.getFormType(), req))
               .sorted()
               .toArray();
            if (reqLevels.length > 0 && reqLevels[0] < lowestReqLevel) {
               lowestReqLevel = reqLevels[0];
               selectedGroup = groupKey;
            }
         }
      }

      return selectedGroup;
   }

   public static String getTransformTargetGroup(StatsData statsData) {
      Character character = statsData.getCharacter();
      String selectedGroup = character.getSelectedFormGroup();
      if (character.hasActiveForm()) {
         return selectedGroup != null && !selectedGroup.isEmpty() && !selectedGroup.equalsIgnoreCase(character.getActiveFormGroup())
            ? selectedGroup
            : character.getActiveFormGroup();
      } else {
         return selectedGroup;
      }
   }

   public static boolean isCrossGroupTransform(StatsData statsData) {
      Character character = statsData.getCharacter();
      if (!character.hasActiveForm()) {
         return false;
      } else {
         String selectedGroup = character.getSelectedFormGroup();
         return selectedGroup != null && !selectedGroup.isEmpty() && !selectedGroup.equalsIgnoreCase(character.getActiveFormGroup());
      }
   }

   public static boolean needsFreeTransformMastery(StatsData statsData) {
      Character character = statsData.getCharacter();
      String group = getTransformTargetGroup(statsData);
      if (group == null || group.isEmpty()) {
         return false;
      } else {
         return character.hasActiveForm() && group.equalsIgnoreCase(character.getActiveFormGroup()) ? false : getNextFormCandidate(statsData) != null;
      }
   }

   public static boolean meetsFreeTransformMastery(StatsData statsData) {
      FormConfig.FormData candidate = getNextFormCandidate(statsData);
      return candidate == null ? false : meetsFreeTransformMasteryFor(statsData, getTransformTargetGroup(statsData), candidate, false);
   }

   public static void revertToBaseForm(ServerPlayer player, StatsData statsData) {
      if (statsData.getStatus().isAndroidUpgraded()) {
         statsData.getCharacter().setActiveForm("androidforms", "androidbase");
      } else {
         statsData.getCharacter().clearActiveForm(player);
      }
   }

   public static FormConfig.FormData getNextAvailableForm(StatsData statsData) {
      FormConfig.FormData nextFormConfig = getNextFormCandidate(statsData);
      if (nextFormConfig == null) {
         return null;
      } else {
         String race = statsData.getCharacter().getRaceName();
         String group = getTransformTargetGroup(statsData);
         FormConfig config = ConfigManager.getFormGroup(race, group);
         if (config == null) {
            return null;
         } else {
            return hasFormSkillAccess(statsData, group, config.getFormType(), nextFormConfig.getUnlockOnSkillLevel())
                  && meetsMasteryRequisite(statsData, nextFormConfig)
               ? nextFormConfig
               : null;
         }
      }
   }

   public static FormConfig.FormData getNextFormCandidate(StatsData statsData) {
      String race = statsData.getCharacter().getRaceName();
      String group = getTransformTargetGroup(statsData);
      if (group != null && !group.isEmpty()) {
         FormConfig config = ConfigManager.getFormGroup(race, group);
         if (config == null) {
            return null;
         } else {
            boolean isAndroidUpgraded = statsData.getStatus().isAndroidUpgraded();
            boolean isAndroidGroup = "androidforms".equalsIgnoreCase(group);
            boolean isGodGroup = config.getFormType().toLowerCase().contains("god");
            boolean isOozaruGroupNext = "oozaru".equalsIgnoreCase(config.getGroupName());
            boolean hasTailNext = statsData.getCharacter().isHasSaiyanTail();
            if (!isAndroidUpgraded && isAndroidGroup) {
               return null;
            } else if (isAndroidUpgraded && !isAndroidGroup && !isGodGroup) {
               return null;
            } else {
               boolean crossGroup = isCrossGroupTransform(statsData);
               String currentFormName = statsData.getCharacter().getActiveForm();
               FormConfig.FormData nextFormConfig = null;
               if (!crossGroup && currentFormName != null && !currentFormName.isEmpty()) {
                  boolean foundCurrent = false;

                  for (Entry<String, FormConfig.FormData> entry : config.getForms().entrySet()) {
                     if (foundCurrent) {
                        if (!isOozaruGroupNext || hasTailNext || !isTailOnlyOozaruForm(entry.getValue().getName())) {
                           nextFormConfig = entry.getValue();
                           break;
                        }
                     } else if (entry.getKey().equalsIgnoreCase(currentFormName)) {
                        foundCurrent = true;
                     }
                  }
               } else {
                  FormConfig.FormData selected = config.getForm(statsData.getCharacter().getSelectedForm());
                  if (selected != null && isOozaruGroupNext && !hasTailNext && isTailOnlyOozaruForm(selected.getName())) {
                     for (FormConfig.FormData f : config.getForms().values()) {
                        if (!isTailOnlyOozaruForm(f.getName())) {
                           nextFormConfig = f;
                           break;
                        }
                     }
                  } else {
                     nextFormConfig = selected;
                  }
               }

               return nextFormConfig;
            }
         }
      } else {
         return null;
      }
   }

   public static boolean isNextFormMasteryBlocked(StatsData statsData) {
      FormConfig.FormData candidate = getNextFormCandidate(statsData);
      if (candidate == null) {
         return false;
      } else {
         String race = statsData.getCharacter().getRaceName();
         String group = getTransformTargetGroup(statsData);
         FormConfig config = ConfigManager.getFormGroup(race, group);
         return config == null
            ? false
            : hasFormSkillAccess(statsData, group, config.getFormType(), candidate.getUnlockOnSkillLevel()) && !meetsMasteryRequisite(statsData, candidate);
      }
   }

   public static boolean isOozaruForm(FormConfig.FormData formData) {
      return formData != null && "oozaru".equalsIgnoreCase(formData.getName());
   }

   private static boolean isTailOnlyOozaruForm(String formName) {
      return "oozaru".equals(formName) || "goldenoozaru".equals(formName);
   }

   public static boolean shouldAutoChargeOozaru(Player player, StatsData statsData) {
      if (player != null && statsData != null) {
         if (statsData.getStatus().getSelectedAction() != ActionMode.FORM) {
            return false;
         } else if (!statsData.getCharacter().hasActiveForm() && !statsData.getCharacter().hasActiveStackForm()) {
            FormConfig.FormData nextForm = getNextAvailableForm(statsData);
            if (!isOozaruForm(nextForm)) {
               return false;
            } else {
               Level playerLevel = player.level();
               boolean realMoon = playerLevel.isNight() && isFullMoon(playerLevel) && isLookingAtMoon(player);
               return realMoon || isLookingAtFakeMoon(player);
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean isFullMoon(Level level) {
      long day = Math.floorDiv(level.getDayTime(), 24000L);
      return day % 8L == 0L;
   }

   private static boolean isLookingAtMoon(Player player) {
      return player.level().canSeeSky(player.blockPosition()) && player.getLookAngle().y > 0.95;
   }

   private static boolean isLookingAtFakeMoon(Player player) {
      Level level = player.level();
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle().normalize();
      double range = 200.0;
      Vec3 end = eye.add(look.scale(range));
      AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(3.0);

      for (KiBlastEntity ki : level.getEntitiesOfClass(KiBlastEntity.class, searchBox)) {
         if (ki.getKiRenderType() == 11 && ki.isParked()) {
            AABB hitbox = ki.getBoundingBox().inflate(2.0);
            if (hitbox.clip(eye, end).isPresent()) {
               return true;
            }
         }
      }

      return false;
   }

   public static FormConfig.FormData getNextAvailableStackForm(StatsData statsData) {
      String group = statsData.getCharacter().hasActiveStackForm()
         ? statsData.getCharacter().getActiveStackFormGroup()
         : statsData.getCharacter().getSelectedStackFormGroup();
      if (group != null && !group.isEmpty()) {
         FormConfig config = ConfigManager.getStackFormGroup(group);
         if (config == null) {
            return null;
         } else {
            String currentFormName = statsData.getCharacter().getActiveStackForm();
            FormConfig.FormData nextFormConfig = null;
            if (currentFormName != null && !currentFormName.isEmpty()) {
               boolean foundCurrent = false;

               for (Entry<String, FormConfig.FormData> entry : config.getForms().entrySet()) {
                  if (foundCurrent) {
                     nextFormConfig = entry.getValue();
                     break;
                  }

                  if (entry.getKey().equalsIgnoreCase(currentFormName)) {
                     foundCurrent = true;
                  }
               }
            } else {
               String nextFormName = statsData.getCharacter().getSelectedStackForm();
               nextFormConfig = config.getForm(nextFormName);
            }

            if (nextFormConfig == null) {
               return nextFormConfig;
            } else {
               return isStackFormUnlocked(statsData, config.getFormType(), nextFormConfig.getUnlockOnSkillLevel())
                     && meetsMasteryRequisite(statsData, nextFormConfig)
                  ? nextFormConfig
                  : null;
            }
         }
      } else {
         return null;
      }
   }

   public static boolean canDescend(StatsData statsData) {
      if (!statsData.getCharacter().hasActiveForm()) {
         return false;
      } else {
         String race = statsData.getCharacter().getRaceName();
         String group = statsData.getCharacter().getActiveFormGroup();
         String currentForm = statsData.getCharacter().getActiveForm();
         if ("androidforms".equalsIgnoreCase(group) && "androidbase".equalsIgnoreCase(currentForm)) {
            return false;
         } else {
            return !isDefaultGroup(race, group) ? true : !"frostdemon".equals(race) && !"majin".equals(race) && !"bioandroid".equals(race);
         }
      }
   }

   public static boolean canStackDescend(StatsData statsData) {
      if (!statsData.getCharacter().hasActiveStackForm()) {
         return false;
      } else {
         String group = statsData.getCharacter().getActiveStackFormGroup();
         String currentForm = statsData.getCharacter().getActiveStackForm();
         return !group.equalsIgnoreCase("") && !currentForm.equalsIgnoreCase("");
      }
   }

   public static boolean isSelectableForm(StatsData statsData, String groupName, String formName) {
      if (statsData != null && groupName != null && formName != null) {
         String race = statsData.getCharacter().getRaceName();

         for (String name : getSelectableFormNames(statsData, race, groupName)) {
            if (name.equalsIgnoreCase(formName)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static boolean isSelectableStackForm(StatsData statsData, String groupName, String formName) {
      if (statsData != null && groupName != null && formName != null) {
         for (String name : getSelectableStackFormNames(statsData, groupName)) {
            if (name.equalsIgnoreCase(formName)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static boolean ensureSelectedFormDefault(StatsData statsData) {
      String form = statsData.getCharacter().getSelectedForm();
      if (form != null && !form.isEmpty()) {
         return false;
      } else {
         String group = getGroupWithFirstAvailableForm(statsData);
         String firstForm = getFirstAvailableForm(statsData);
         if (group != null && !group.isEmpty() && firstForm != null && !firstForm.isEmpty()) {
            statsData.getCharacter().setSelectedFormGroup(group);
            statsData.getCharacter().setSelectedForm(firstForm);
            return true;
         } else {
            return false;
         }
      }
   }

   public static boolean ensureSelectedStackFormDefault(StatsData statsData) {
      String form = statsData.getCharacter().getSelectedStackForm();
      if (form != null && !form.isEmpty()) {
         return false;
      } else {
         String group = getGroupWithFirstAvailableStackForm(statsData);
         String firstForm = getFirstAvailableStackForm(statsData);
         if (group != null && !group.isEmpty() && firstForm != null && !firstForm.isEmpty()) {
            statsData.getCharacter().setSelectedStackFormGroup(group);
            statsData.getCharacter().setSelectedStackForm(firstForm);
            return true;
         } else {
            return false;
         }
      }
   }

   private static boolean isDefaultGroup(String race, String group) {
      return switch (race) {
         case "frostdemon" -> "evolutionforms".equals(group);
         case "majin" -> "pureforms".equals(group);
         case "bioandroid" -> "bioevolution".equals(group);
         default -> false;
      };
   }

   public static FormConfig.FormData getPreviousForm(StatsData statsData) {
      if (!statsData.getCharacter().hasActiveForm()) {
         return null;
      } else {
         String race = statsData.getCharacter().getRaceName();
         String group = statsData.getCharacter().getActiveFormGroup();
         String current = statsData.getCharacter().getActiveForm();
         FormConfig config = ConfigManager.getFormGroup(race, group);
         if (config == null) {
            return null;
         } else {
            FormConfig.FormData prev = null;

            for (FormConfig.FormData f : config.getForms().values()) {
               if (f.getName().equalsIgnoreCase(current)) {
                  return prev;
               }

               prev = f;
            }

            return null;
         }
      }
   }

   public static FormConfig.FormData getPreviousStackForm(StatsData statsData) {
      if (!statsData.getCharacter().hasActiveStackForm()) {
         return null;
      } else {
         String group = statsData.getCharacter().getActiveStackFormGroup();
         String current = statsData.getCharacter().getActiveStackForm();
         FormConfig config = ConfigManager.getStackFormGroup(group);
         if (config == null) {
            return null;
         } else {
            FormConfig.FormData prev = null;

            for (FormConfig.FormData f : config.getForms().values()) {
               if (f.getName().equalsIgnoreCase(current)) {
                  return prev;
               }

               prev = f;
            }

            return null;
         }
      }
   }

   public static int getKaiokenPhase(StatsData stats) {
      if ("kaioken".equalsIgnoreCase(stats.getCharacter().getActiveStackFormGroup())) {
         String var1 = stats.getCharacter().getActiveStackForm();

         return switch (var1) {
            case "x2" -> 1;
            case "x3" -> 2;
            case "x4" -> 3;
            case "x10" -> 4;
            case "x20" -> 5;
            default -> 6;
         };
      } else {
         return 0;
      }
   }

   public static boolean hasGodFormActive(StatsData statsData) {
      Character character = statsData.getCharacter();
      if (!character.hasActiveForm()) {
         return false;
      } else {
         FormConfig config = ConfigManager.getFormGroup(character.getRaceName(), character.getActiveFormGroup());
         return config != null && config.getFormType() != null && config.getFormType().toLowerCase(Locale.ROOT).contains("god");
      }
   }

   public static boolean isInstantTransmissionBlocked(StatsData requester, StatsData target) {
      return target.getStatus().isAndroidUpgraded() ? true : hasGodFormActive(target) && requester.getSkills().getSkillLevel("godforms") < 1;
   }

   public static boolean hasAntiKiCloak(Player target) {
      return CuriosUtil.getFirstStack(target, "head_tech").getItem() == MainItems.ANTI_KI_CLOAK.get();
   }

   public static class OrderedFormEntry {
      private final String groupName;
      private final String formType;
      private final FormConfig.FormData formData;

      public OrderedFormEntry(String groupName, String formType, FormConfig.FormData formData) {
         this.groupName = groupName;
         this.formType = formType;
         this.formData = formData;
      }

      public String getGroupName() {
         return this.groupName;
      }

      public String getFormType() {
         return this.formType;
      }

      public FormConfig.FormData getFormData() {
         return this.formData;
      }
   }
}
