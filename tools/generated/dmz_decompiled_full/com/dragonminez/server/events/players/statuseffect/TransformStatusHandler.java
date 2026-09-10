package com.dragonminez.server.events.players.statuseffect;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.extras.ActionMode;
import com.dragonminez.server.events.players.IStatusEffectHandler;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.NeoForge;

public class TransformStatusHandler implements IStatusEffectHandler {
   private static final String LOG_PREFIX = "[DMZ-FORM-EFFECTS] ";
   private static final String TAG_ROOT = "dmzTransformMobEffects";
   private static final String TAG_LAST_FORM = "lastForm";
   private static final String TAG_LAST_FORM_GROUP = "lastFormGroup";
   private static final String TAG_LAST_STACK_FORM = "lastStackForm";
   private static final String TAG_LAST_STACK_GROUP = "lastStackGroup";

   @Override
   public void handleStatusEffects(ServerPlayer player, StatsData data) {
      if (data.getStatus().isActionCharging()) {
         if (data.getStatus().getSelectedAction().equals(ActionMode.FORM)) {
            if (!player.hasEffect(MainEffects.TRANSFORM)) {
               player.addEffect(new MobEffectInstance(MainEffects.TRANSFORM, -1, 0, false, false, true));
            }
         } else if (data.getStatus().getSelectedAction().equals(ActionMode.STACK) && !player.hasEffect(MainEffects.STACK_TRANSFORM)) {
            player.addEffect(new MobEffectInstance(MainEffects.STACK_TRANSFORM, -1, 0, false, false, true));
         }
      } else {
         player.removeEffect(MainEffects.TRANSFORM);
         player.removeEffect(MainEffects.STACK_TRANSFORM);
      }
   }

   @Override
   public void onPlayerTick(ServerPlayer player, StatsData data) {
      if (data.getCharacter().getActiveForm() == null || data.getCharacter().getActiveForm().isEmpty() || data.getCharacter().getActiveForm().equals("base")) {
         player.removeEffect(MainEffects.TRANSFORM);
      }

      if (data.getCharacter().getActiveStackForm() == null
         || data.getCharacter().getActiveStackForm().isEmpty()
         || data.getCharacter().getActiveStackForm().equals("base")) {
         player.removeEffect(MainEffects.STACK_TRANSFORM);
      }

      CompoundTag effectTag = this.getOrCreateEffectTag(player);
      String activeForm = this.normalizeFormName(data.getCharacter().getActiveForm(), true);
      String activeFormGroup = this.normalizeGroupName(data.getCharacter().getActiveFormGroup());
      String activeStackForm = this.normalizeFormName(data.getCharacter().getActiveStackForm(), false);
      String activeStackGroup = this.normalizeGroupName(data.getCharacter().getActiveStackFormGroup());
      String lastForm = this.decodeNullable(effectTag.getString("lastForm"));
      String lastFormGroup = this.decodeNullable(effectTag.getString("lastFormGroup"));
      String lastStackForm = this.decodeNullable(effectTag.getString("lastStackForm"));
      String lastStackGroup = this.decodeNullable(effectTag.getString("lastStackGroup"));
      boolean formChanged = !Objects.equals(activeForm, lastForm) || !Objects.equals(activeFormGroup, lastFormGroup);
      boolean stackChanged = !Objects.equals(activeStackForm, lastStackForm) || !Objects.equals(activeStackGroup, lastStackGroup);
      if (formChanged || stackChanged) {
         LogUtil.info(
            Env.SERVER,
            "[DMZ-FORM-EFFECTS] State change for {} -> formGroup='{}', form='{}', stackGroup='{}', stackForm='{}'",
            player.getScoreboardName(),
            this.safe(activeFormGroup),
            this.safe(activeForm),
            this.safe(activeStackGroup),
            this.safe(activeStackForm)
         );
      }

      if (formChanged && activeForm != null) {
         NeoForge.EVENT_BUS.post(new DMZEvent.FormChangeEvent(player, lastFormGroup, lastForm, activeFormGroup, activeForm));
      }

      if (stackChanged && activeStackForm != null) {
         NeoForge.EVENT_BUS.post(new DMZEvent.StackFormChangeEvent(player, lastStackGroup, lastStackForm, activeStackGroup, activeStackForm));
      }

      FormConfig.FormData formData = this.resolveRegularFormData(data, activeFormGroup, activeForm, player);
      FormConfig.FormData stackFormData = this.resolveStackFormData(activeStackGroup, activeStackForm, player);
      if (formChanged) {
         this.applyTemporaryEffects(player, formData, "form", activeFormGroup, activeForm);
      }

      if (stackChanged) {
         this.applyTemporaryEffects(player, stackFormData, "stack", activeStackGroup, activeStackForm);
      }

      Map<Holder<MobEffect>, TransformStatusHandler.PersistentEffectAccumulator> persistentEffects = new HashMap<>();
      this.collectPersistentEffects(persistentEffects, formData, "form", activeFormGroup, activeForm, player);
      this.collectPersistentEffects(persistentEffects, stackFormData, "stack", activeStackGroup, activeStackForm, player);
      Set<Holder<MobEffect>> desiredPersistentTypes = persistentEffects.keySet();

      for (Holder<MobEffect> effect : readTrackedPersistentEffects(effectTag)) {
         if (!desiredPersistentTypes.contains(effect) && player.hasEffect(effect)) {
            LogUtil.info(
               Env.SERVER,
               "[DMZ-FORM-EFFECTS] Removing persistent effect '{}' from {} because no active transformation now provides it.",
               getEffectId(effect),
               player.getScoreboardName()
            );
            player.removeEffect(effect);
         }
      }

      for (Entry<Holder<MobEffect>, TransformStatusHandler.PersistentEffectAccumulator> entry : persistentEffects.entrySet()) {
         Holder<MobEffect> effectx = entry.getKey();
         TransformStatusHandler.PersistentEffectAccumulator accumulator = entry.getValue();
         MobEffectInstance instance = new MobEffectInstance(
            effectx, -1, accumulator.getFinalAmplifier(), accumulator.ambient, accumulator.visible, accumulator.showIcon
         );
         boolean added = player.addEffect(instance);
         LogUtil.info(
            Env.SERVER,
            "[DMZ-FORM-EFFECTS] Applied persistent effect '{}' to {} from {} source(s): amplifier={}, ambient={}, visible={}, icon={}, result={}",
            getEffectId(effectx),
            player.getScoreboardName(),
            accumulator.sources,
            accumulator.getFinalAmplifier(),
            accumulator.ambient,
            accumulator.visible,
            accumulator.showIcon,
            added
         );
      }

      this.writeTrackedPersistentEffects(effectTag, desiredPersistentTypes);
      effectTag.putString("lastForm", this.encodeNullable(activeForm));
      effectTag.putString("lastFormGroup", this.encodeNullable(activeFormGroup));
      effectTag.putString("lastStackForm", this.encodeNullable(activeStackForm));
      effectTag.putString("lastStackGroup", this.encodeNullable(activeStackGroup));
   }

   @Override
   public void onPlayerSecond(ServerPlayer serverPlayer, StatsData data) {
   }

   private FormConfig.FormData resolveRegularFormData(StatsData data, String group, String form, ServerPlayer player) {
      if (group != null && form != null) {
         FormConfig config = ConfigManager.getFormGroup(data.getCharacter().getRaceName(), group);
         if (config == null) {
            LogUtil.warn(Env.SERVER, "[DMZ-FORM-EFFECTS] Could not resolve regular form group '{}' for {}.", group, player.getScoreboardName());
            return null;
         } else {
            FormConfig.FormData formData = config.getForm(form);
            if (formData == null) {
               formData = config.getFormByKey(form);
            }

            if (formData == null) {
               LogUtil.warn(Env.SERVER, "[DMZ-FORM-EFFECTS] Could not resolve regular form '{}' in group '{}' for {}.", form, group, player.getScoreboardName());
               return null;
            } else {
               return formData;
            }
         }
      } else {
         return null;
      }
   }

   private FormConfig.FormData resolveStackFormData(String group, String form, ServerPlayer player) {
      if (group != null && form != null) {
         FormConfig config = ConfigManager.getStackFormGroup(group);
         if (config == null) {
            LogUtil.warn(Env.SERVER, "[DMZ-FORM-EFFECTS] Could not resolve stack form group '{}' for {}.", group, player.getScoreboardName());
            return null;
         } else {
            FormConfig.FormData formData = config.getForm(form);
            if (formData == null) {
               formData = config.getFormByKey(form);
            }

            if (formData == null) {
               LogUtil.warn(Env.SERVER, "[DMZ-FORM-EFFECTS] Could not resolve stack form '{}' in group '{}' for {}.", form, group, player.getScoreboardName());
               return null;
            } else {
               return formData;
            }
         }
      } else {
         return null;
      }
   }

   private void applyTemporaryEffects(ServerPlayer player, FormConfig.FormData formData, String sourceType, String group, String form) {
      if (formData != null) {
         for (FormConfig.FormData.MobEffectConfig effectConfig : formData.getMobEffects()) {
            if (effectConfig != null && !effectConfig.isPersistent()) {
               Holder<MobEffect> effect = resolveMobEffect(effectConfig.getEffectId());
               if (effect == null) {
                  LogUtil.warn(
                     Env.SERVER,
                     "[DMZ-FORM-EFFECTS] Failed to resolve temporary effect '{}' for {} source '{}'/'{}'.",
                     effectConfig != null ? effectConfig.getEffectId() : "<null>",
                     sourceType,
                     this.safe(group),
                     this.safe(form)
                  );
               } else {
                  MobEffectInstance instance = new MobEffectInstance(
                     effect,
                     effectConfig.getDurationTicks(),
                     effectConfig.getAmplifier(),
                     effectConfig.isAmbient(),
                     effectConfig.isVisible(),
                     effectConfig.isShowIcon()
                  );
                  boolean added = player.addEffect(instance);
                  LogUtil.info(
                     Env.SERVER,
                     "[DMZ-FORM-EFFECTS] Applied temporary effect '{}' to {} from {} '{}'/'{}': duration={}, amplifier={}, ambient={}, visible={}, icon={}, result={}",
                     getEffectId(effect),
                     player.getScoreboardName(),
                     sourceType,
                     this.safe(group),
                     this.safe(form),
                     effectConfig.getDurationTicks(),
                     effectConfig.getAmplifier(),
                     effectConfig.isAmbient(),
                     effectConfig.isVisible(),
                     effectConfig.isShowIcon(),
                     added
                  );
               }
            }
         }
      }
   }

   private void collectPersistentEffects(
      Map<Holder<MobEffect>, TransformStatusHandler.PersistentEffectAccumulator> persistentEffects,
      FormConfig.FormData formData,
      String sourceType,
      String group,
      String form,
      ServerPlayer player
   ) {
      if (formData != null) {
         for (FormConfig.FormData.MobEffectConfig effectConfig : formData.getMobEffects()) {
            if (effectConfig != null && effectConfig.isPersistent()) {
               Holder<MobEffect> effect = resolveMobEffect(effectConfig.getEffectId());
               if (effect == null) {
                  LogUtil.warn(
                     Env.SERVER,
                     "[DMZ-FORM-EFFECTS] Failed to resolve persistent effect '{}' for {} source '{}'/'{}'.",
                     effectConfig != null ? effectConfig.getEffectId() : "<null>",
                     sourceType,
                     this.safe(group),
                     this.safe(form)
                  );
               } else {
                  TransformStatusHandler.PersistentEffectAccumulator accumulator = persistentEffects.computeIfAbsent(
                     effect, ignored -> new TransformStatusHandler.PersistentEffectAccumulator()
                  );
                  accumulator.totalLevels = accumulator.totalLevels + effectConfig.getAmplifier() + 1;
                  accumulator.ambient = accumulator.ambient || effectConfig.isAmbient();
                  accumulator.visible = accumulator.visible || effectConfig.isVisible();
                  accumulator.showIcon = accumulator.showIcon || effectConfig.isShowIcon();
                  accumulator.sources++;
                  LogUtil.info(
                     Env.SERVER,
                     "[DMZ-FORM-EFFECTS] Queued persistent effect '{}' for {} from {} '{}'/'{}': rawAmplifier={}, cumulativeLevel={}, cumulativeAmplifier={}",
                     getEffectId(effect),
                     player.getScoreboardName(),
                     sourceType,
                     this.safe(group),
                     this.safe(form),
                     effectConfig.getAmplifier(),
                     accumulator.totalLevels,
                     Math.max(0, accumulator.totalLevels - 1)
                  );
               }
            }
         }
      }
   }

   private CompoundTag getOrCreateEffectTag(ServerPlayer player) {
      CompoundTag persistentData = player.getPersistentData();
      if (!persistentData.contains("dmzTransformMobEffects")) {
         persistentData.put("dmzTransformMobEffects", new CompoundTag());
      }

      return persistentData.getCompound("dmzTransformMobEffects");
   }

   public static void clearAllPersistentFormEffects(ServerPlayer player) {
      CompoundTag persistentData = player.getPersistentData();
      if (persistentData.contains("dmzTransformMobEffects")) {
         CompoundTag effectTag = persistentData.getCompound("dmzTransformMobEffects");

         for (Holder<MobEffect> effect : readTrackedPersistentEffects(effectTag)) {
            if (player.hasEffect(effect)) {
               player.removeEffect(effect);
               LogUtil.info(
                  Env.SERVER,
                  "[DMZ-FORM-EFFECTS] Removing persistent effect '{}' from {} due to character reset.",
                  getEffectId(effect),
                  player.getScoreboardName()
               );
            }
         }

         persistentData.remove("dmzTransformMobEffects");
      }
   }

   private static Set<Holder<MobEffect>> readTrackedPersistentEffects(CompoundTag effectTag) {
      Set<Holder<MobEffect>> tracked = new HashSet<>();
      if (!effectTag.contains("trackedPersistentEffects")) {
         return tracked;
      } else {
         String raw = effectTag.getString("trackedPersistentEffects");
         if (raw != null && !raw.isEmpty()) {
            for (String part : raw.split(";")) {
               if (part != null && !part.isBlank()) {
                  Holder<MobEffect> effect = resolveMobEffect(part.trim());
                  if (effect != null) {
                     tracked.add(effect);
                  }
               }
            }

            return tracked;
         } else {
            return tracked;
         }
      }
   }

   private void writeTrackedPersistentEffects(CompoundTag effectTag, Set<Holder<MobEffect>> effects) {
      StringBuilder builder = new StringBuilder();

      for (Holder<MobEffect> effect : effects) {
         if (builder.length() > 0) {
            builder.append(';');
         }

         builder.append(getEffectId(effect));
      }

      effectTag.putString("trackedPersistentEffects", builder.toString());
   }

   private static Holder<MobEffect> resolveMobEffect(String rawEffectId) {
      if (rawEffectId != null && !rawEffectId.isBlank()) {
         String normalized = normalizeEffectId(rawEffectId.trim().toLowerCase());
         ResourceLocation location = ResourceLocation.tryParse(normalized);
         return location == null ? null : (Holder)BuiltInRegistries.MOB_EFFECT.getHolder(location).orElse(null);
      } else {
         return null;
      }
   }

   private static String normalizeEffectId(String effectId) {
      return switch (effectId) {
         case "minecraft:movement_speed" -> "minecraft:speed";
         case "minecraft:damage_boost" -> "minecraft:strength";
         case "minecraft:jump" -> "minecraft:jump_boost";
         case "minecraft:dig_speed" -> "minecraft:haste";
         case "minecraft:dig_slowdown" -> "minecraft:mining_fatigue";
         case "minecraft:heal" -> "minecraft:instant_health";
         case "minecraft:harm" -> "minecraft:instant_damage";
         default -> effectId;
      };
   }

   private static String getEffectId(Holder<MobEffect> effect) {
      ResourceLocation key = effect.unwrapKey().map(k -> k.location()).orElse(null);
      return key != null ? key.toString() : "unknown";
   }

   private String normalizeFormName(String form, boolean regular) {
      if (form == null || form.isBlank()) {
         return null;
      } else {
         return regular && "base".equalsIgnoreCase(form) ? null : form;
      }
   }

   private String normalizeGroupName(String group) {
      return group != null && !group.isBlank() ? group : null;
   }

   private String safe(String value) {
      return value != null ? value : "<none>";
   }

   private String encodeNullable(String value) {
      return value != null ? value : "";
   }

   private String decodeNullable(String value) {
      return value != null && !value.isEmpty() ? value : null;
   }

   private static class PersistentEffectAccumulator {
      private int totalLevels;
      private boolean ambient;
      private boolean visible;
      private boolean showIcon;
      private int sources;

      private int getFinalAmplifier() {
         return Math.max(0, this.totalLevels - 1);
      }
   }
}
