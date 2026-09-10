package com.dragonminez.common.stats.character;

import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairManager;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.stats.extras.FormMasteries;
import com.dragonminez.common.stats.extras.UsedForms;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

public class Character {
   private String race;
   private String gender;
   private String characterClass;
   private String selectedMaster = "";
   private String selectedFormGroup = "";
   private String activeFormGroup = "";
   private String selectedForm = "";
   private String activeForm = "";
   private final FormMasteries formMasteries = new FormMasteries();
   private UsedForms formsUsedBefore = new UsedForms();
   private int activeFormItemDurationTicks = 0;
   private String previousFormGroup = "";
   private String previousForm = "";
   private boolean hasPreviousFormRecord = false;
   private String selectedStackFormGroup = "";
   private String activeStackFormGroup = "";
   private String selectedStackForm = "";
   private String activeStackForm = "";
   private final FormMasteries stackFormMasteries = new FormMasteries();
   private UsedForms stackFormsUsedBefore = new UsedForms();
   private int activeStackFormItemDurationTicks = 0;
   private String previousStackFormGroup = "";
   private String previousStackForm = "";
   private boolean hasPreviousStackFormRecord = false;
   private boolean hasSaiyanTail = false;
   private boolean renderHairBase = true;
   private final Map<String, MasterLocation> interactedMasters = new HashMap<>();
   private final Set<String> knownMinigames = new HashSet<>();
   public static final String GENDER_MALE = "male";
   public static final String GENDER_FEMALE = "female";
   public static final String CLASS_WARRIOR = "warrior";
   private int hairId;
   private CustomHair hairBase = new CustomHair();
   private CustomHair hairSSJ = new CustomHair();
   private CustomHair hairSSJ2 = new CustomHair();
   private CustomHair hairSSJ3 = new CustomHair();
   private String activeHeadBone = "";
   private int bodyType;
   private int eyesType;
   private int noseType;
   private int mouthType;
   private int tattooType;
   private float boobScale = 1.0F;
   private String bodyColor;
   private String bodyColor2;
   private String bodyColor3;
   private String hairColor;
   private String eye1Color;
   private String eye2Color;
   private String auraColor;
   private transient float[] rgbBodyColor;
   private transient float[] rgbBodyColor2;
   private transient float[] rgbBodyColor3;
   private transient float[] rgbHairColor;
   private transient float[] rgbEye1Color;
   private transient float[] rgbEye2Color;
   private transient float[] rgbAuraColor;
   private transient boolean oozaruCached = false;
   private Boolean armored;

   public void clearInteractedMasters() {
      this.interactedMasters.clear();
   }

   public boolean isMinigameKnown(String minigameId) {
      return minigameId != null && this.knownMinigames.contains(minigameId.toLowerCase());
   }

   public void addKnownMinigame(String minigameId) {
      if (minigameId != null) {
         this.knownMinigames.add(minigameId.toLowerCase());
      }
   }

   public void removeKnownMinigame(String minigameId) {
      if (minigameId != null) {
         this.knownMinigames.remove(minigameId.toLowerCase());
      }
   }

   private static String safeString(String value) {
      return value != null ? value : "";
   }

   public Character() {
      this.race = "human";
      this.gender = "male";
      this.characterClass = "warrior";
      this.armored = false;
      RaceCharacterConfig config = ConfigManager.getRaceCharacter("human");
      if (config != null) {
         this.hairId = config.getDefaultHairType();
         this.activeHeadBone = config.getHeadBones().length > 0 ? config.getHeadBones()[0] : "";
         this.bodyType = config.getDefaultBodyType();
         this.eyesType = config.getDefaultEyesType();
         this.noseType = config.getDefaultNoseType();
         this.mouthType = config.getDefaultMouthType();
         this.tattooType = config.getDefaultTattooType();
         this.bodyColor = config.getDefaultBodyColor() != null ? config.getDefaultBodyColor() : "#F5D5A6";
         this.bodyColor2 = config.getDefaultBodyColor2() != null ? config.getDefaultBodyColor2() : "#F5D5A6";
         this.bodyColor3 = config.getDefaultBodyColor3() != null ? config.getDefaultBodyColor3() : "#F5D5A6";
         this.hairColor = config.getDefaultHairColor() != null ? config.getDefaultHairColor() : "#000000";
         this.eye1Color = config.getDefaultEye1Color() != null ? config.getDefaultEye1Color() : "#000000";
         this.eye2Color = config.getDefaultEye2Color() != null ? config.getDefaultEye2Color() : "#000000";
         this.auraColor = config.getDefaultAuraColor() != null ? config.getDefaultAuraColor() : "#FFFFFF";
         this.setHasSaiyanTail(config.getHasSaiyanTail());
      } else {
         this.hairId = 0;
         this.activeHeadBone = "";
         this.bodyType = 0;
         this.eyesType = 0;
         this.noseType = 0;
         this.mouthType = 0;
         this.tattooType = 0;
         this.bodyColor = "#F5D5A6";
         this.bodyColor2 = "#F5D5A6";
         this.bodyColor3 = "#F5D5A6";
         this.hairColor = "#000000";
         this.eye1Color = "#000000";
         this.eye2Color = "#000000";
         this.auraColor = "#FFFFFF";
         this.setHasSaiyanTail(false);
      }
   }

   public void addInteractedMaster(String id, String name, String dimension, BlockPos pos) {
      this.interactedMasters.put(id, new MasterLocation(id, name, dimension, pos));
   }

   public void removeInteractedMaster(String id) {
      this.interactedMasters.remove(id);
   }

   public void setBodyColor(String hex) {
      this.bodyColor = hex;
      this.rgbBodyColor = ColorUtils.hexToRgb(hex);
   }

   public void setBodyColor2(String hex) {
      this.bodyColor2 = hex;
      this.rgbBodyColor2 = ColorUtils.hexToRgb(hex);
   }

   public void setBodyColor3(String hex) {
      this.bodyColor3 = hex;
      this.rgbBodyColor3 = ColorUtils.hexToRgb(hex);
   }

   public void setHairColor(String hex) {
      this.hairColor = hex;
      this.rgbHairColor = ColorUtils.hexToRgb(hex);
   }

   public void setEye1Color(String hex) {
      this.eye1Color = hex;
      this.rgbEye1Color = ColorUtils.hexToRgb(hex);
   }

   public void setEye2Color(String hex) {
      this.eye2Color = hex;
      this.rgbEye2Color = ColorUtils.hexToRgb(hex);
   }

   public void setAuraColor(String hex) {
      this.auraColor = hex;
      this.rgbAuraColor = ColorUtils.hexToRgb(hex);
   }

   public float[] getRgbBodyColor() {
      if (this.rgbBodyColor == null) {
         this.rgbBodyColor = ColorUtils.hexToRgb(this.bodyColor != null ? this.bodyColor : "#FFFFFF");
      }

      return this.rgbBodyColor;
   }

   public float[] getRgbBodyColor2() {
      if (this.rgbBodyColor2 == null) {
         this.rgbBodyColor2 = ColorUtils.hexToRgb(this.bodyColor2 != null ? this.bodyColor2 : "#FFFFFF");
      }

      return this.rgbBodyColor2;
   }

   public float[] getRgbBodyColor3() {
      if (this.rgbBodyColor3 == null) {
         this.rgbBodyColor3 = ColorUtils.hexToRgb(this.bodyColor3 != null ? this.bodyColor3 : "#FFFFFF");
      }

      return this.rgbBodyColor3;
   }

   public float[] getRgbHairColor() {
      if (this.rgbHairColor == null) {
         this.rgbHairColor = ColorUtils.hexToRgb(this.hairColor != null ? this.hairColor : "#FFFFFF");
      }

      return this.rgbHairColor;
   }

   public float[] getRgbEye1Color() {
      if (this.rgbEye1Color == null) {
         this.rgbEye1Color = ColorUtils.hexToRgb(this.eye1Color != null ? this.eye1Color : "#FFFFFF");
      }

      return this.rgbEye1Color;
   }

   public float[] getRgbEye2Color() {
      if (this.rgbEye2Color == null) {
         this.rgbEye2Color = ColorUtils.hexToRgb(this.eye2Color != null ? this.eye2Color : "#FFFFFF");
      }

      return this.rgbEye2Color;
   }

   public float[] getRgbAuraColor() {
      if (this.rgbAuraColor == null) {
         this.rgbAuraColor = ColorUtils.hexToRgb(this.auraColor != null ? this.auraColor : "#FFFFFF");
      }

      return this.rgbAuraColor;
   }

   public void updateOozaruCache() {
      String raceName = this.getRaceName().toLowerCase();
      String currentForm = this.getActiveForm();
      String logicKey = this.getRenderLogicKey();
      this.oozaruCached = logicKey.startsWith("oozaru")
         || raceName.equals("saiyan") && (Objects.equals(currentForm, "oozaru") || Objects.equals(currentForm, "goldenoozaru"));
   }

   public CustomHair emptyHair() {
      return HairManager.getPresetHair(5, this.hairColor);
   }

   public CustomHair getHairBase() {
      return this.hairId > 0 ? HairManager.getPresetHair(this.hairId, this.hairColor) : this.hairBase;
   }

   public CustomHair getHairSSJ() {
      if (this.hairId > 0) {
         return HairManager.getPresetHairSSJ(this.hairId, this.hairColor);
      } else {
         return this.hairSSJ != null && !this.hairSSJ.isEmpty() ? this.hairSSJ : this.hairBase;
      }
   }

   public CustomHair getHairSSJ2() {
      if (this.hairId > 0) {
         return HairManager.getPresetHairSSJ2(this.hairId, this.hairColor);
      } else if (this.hairSSJ2 != null && !this.hairSSJ2.isEmpty()) {
         return this.hairSSJ2;
      } else {
         return this.hairSSJ != null && !this.hairSSJ.isEmpty() ? this.hairSSJ : this.hairBase;
      }
   }

   public CustomHair getHairSSJ3() {
      if (this.hairId > 0) {
         return HairManager.getPresetHairSSJ3(this.hairId, this.hairColor);
      } else if (this.hairSSJ3 != null && !this.hairSSJ3.isEmpty()) {
         return this.hairSSJ3;
      } else {
         return this.hairSSJ != null && !this.hairSSJ.isEmpty() ? this.hairSSJ : this.hairBase;
      }
   }

   public void setRace(String race) {
      if (race != null) {
         this.race = race.toLowerCase();
      } else {
         this.race = "human";
      }

      if (!this.canHaveGender() && !this.gender.equals("male")) {
         this.gender = "male";
      }

      this.updateOozaruCache();
   }

   public void setSelectedFormGroup(String selectedFormGroup) {
      this.selectedFormGroup = safeString(selectedFormGroup);
   }

   public void setSelectedForm(String selectedForm) {
      this.selectedForm = safeString(selectedForm);
   }

   public void setSelectedStackFormGroup(String selectedStackFormGroup) {
      this.selectedStackFormGroup = safeString(selectedStackFormGroup);
   }

   public void setSelectedStackForm(String selectedStackForm) {
      this.selectedStackForm = safeString(selectedStackForm);
   }

   public String getRaceName() {
      return this.race != null && !this.race.isEmpty() ? this.race : "human";
   }

   public boolean canHaveGender() {
      RaceCharacterConfig raceConfig = ConfigManager.getRaceCharacter(this.getRaceName());
      return raceConfig != null ? raceConfig.getHasGender() : true;
   }

   public Float[] getModelScaling() {
      RaceCharacterConfig raceConfig = ConfigManager.getRaceCharacter(this.getRaceName());
      return raceConfig != null ? safeModelScaling(raceConfig.getDefaultModelScaling()) : new Float[]{0.9375F, 0.9375F, 0.9375F};
   }

   private static Float[] safeModelScaling(Float[] scaling) {
      return scaling != null && scaling.length >= 3 && scaling[0] != null && scaling[1] != null && scaling[2] != null
         ? scaling
         : new Float[]{0.9375F, 0.9375F, 0.9375F};
   }

   public Float[] getResolvedModelScaling() {
      if (this.isOozaruCached()) {
         FormConfig.FormData form = this.getActiveFormData();
         if (form != null) {
            return safeModelScaling(form.getModelScaling());
         } else {
            FormConfig.FormData stack = this.getActiveStackFormData();
            return stack != null ? safeModelScaling(stack.getModelScaling()) : safeModelScaling(this.getModelScaling());
         }
      } else {
         FormConfig.FormData form = this.getActiveFormData();
         FormConfig.FormData stack = this.getActiveStackFormData();
         if (form == null && stack == null) {
            return safeModelScaling(this.getModelScaling());
         } else if (form != null && stack == null) {
            return safeModelScaling(form.getModelScaling());
         } else if (form == null && stack != null) {
            return safeModelScaling(stack.getModelScaling());
         } else {
            Float[] formScale = safeModelScaling(form.getModelScaling());
            Float[] stackScale = safeModelScaling(stack.getModelScaling());
            return new Float[]{
               this.resolveAxis(formScale[0], stackScale[0]), this.resolveAxis(formScale[1], stackScale[1]), this.resolveAxis(formScale[2], stackScale[2])
            };
         }
      }
   }

   private float resolveAxis(float formVal, float stackVal) {
      if (formVal == 0.9375F && stackVal == 0.9375F) {
         return 0.9375F;
      } else if (formVal == 0.9375F) {
         return stackVal;
      } else {
         return stackVal == 0.9375F ? formVal : formVal * stackVal;
      }
   }

   public String getResolvedCustomModel() {
      FormConfig.FormData stack = this.getActiveStackFormData();
      if (stack != null && Boolean.TRUE.equals(stack.hasCustomModel())) {
         return stack.getCustomModel().toLowerCase();
      } else {
         FormConfig.FormData form = this.getActiveFormData();
         if (form != null && Boolean.TRUE.equals(form.hasCustomModel())) {
            return form.getCustomModel().toLowerCase();
         } else {
            RaceCharacterConfig raceConfig = ConfigManager.getRaceCharacter(this.getRaceName());
            return raceConfig != null && raceConfig.getCustomModel() != null ? raceConfig.getCustomModel().toLowerCase() : "";
         }
      }
   }

   public String getRenderLogicKey() {
      String key = this.getResolvedCustomModel();
      return key.isEmpty() ? this.getRaceName().toLowerCase() : key;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("Race", safeString(this.race));
      tag.putString("Gender", safeString(this.gender));
      tag.putString("Class", safeString(this.characterClass));
      tag.putInt("HairId", this.hairId);
      tag.put("HairBase", this.hairBase.save());
      tag.put("HairSSJ", this.hairSSJ.save());
      tag.put("HairSSJ2", this.hairSSJ2.save());
      tag.put("HairSSJ3", this.hairSSJ3.save());
      tag.putString("ActiveHeadBone", this.activeHeadBone != null ? this.activeHeadBone : "");
      tag.putInt("BodyType", this.bodyType);
      tag.putInt("EyesType", this.eyesType);
      tag.putInt("NoseType", this.noseType);
      tag.putInt("MouthType", this.mouthType);
      tag.putInt("TattooType", this.tattooType);
      tag.putFloat("BoobScale", this.boobScale);
      this.saveAppearance(tag);
      tag.putString("SelectedMaster", safeString(this.selectedMaster));
      tag.putString("SelectedFormGroup", safeString(this.selectedFormGroup));
      tag.putString("CurrentFormGroup", safeString(this.activeFormGroup));
      tag.putString("SelectedForm", safeString(this.selectedForm));
      tag.putString("CurrentForm", safeString(this.activeForm));
      tag.putInt("ActiveFormItemDurationTicks", this.activeFormItemDurationTicks);
      tag.putString("PreviousFormGroup", safeString(this.previousFormGroup));
      tag.putString("PreviousForm", safeString(this.previousForm));
      tag.putBoolean("HasPreviousFormRecord", this.hasPreviousFormRecord);
      tag.put("FormMasteries", this.formMasteries.save());
      tag.putString("SelectedStackFormGroup", safeString(this.selectedStackFormGroup));
      tag.putString("CurrentStackFormGroup", safeString(this.activeStackFormGroup));
      tag.putString("SelectedStackForm", safeString(this.selectedStackForm));
      tag.putString("CurrentStackForm", safeString(this.activeStackForm));
      tag.putInt("ActiveStackFormItemDurationTicks", this.activeStackFormItemDurationTicks);
      tag.putString("PreviousStackFormGroup", safeString(this.previousStackFormGroup));
      tag.putString("PreviousStackForm", safeString(this.previousStackForm));
      tag.putBoolean("HasPreviousStackFormRecord", this.hasPreviousStackFormRecord);
      tag.put("StackFormMasteries", this.stackFormMasteries.save());
      tag.put("FormsUsedBefore", (this.formsUsedBefore != null ? this.formsUsedBefore : new UsedForms()).save());
      tag.put("StackFormsUsedBefore", (this.stackFormsUsedBefore != null ? this.stackFormsUsedBefore : new UsedForms()).save());
      tag.putBoolean("HasSaiyanTail", this.hasSaiyanTail);
      tag.putBoolean("RenderHairBase", this.renderHairBase);
      tag.putBoolean("isArmored", this.armored);
      ListTag mastersList = new ListTag();

      for (MasterLocation master : this.interactedMasters.values()) {
         CompoundTag masterTag = new CompoundTag();
         masterTag.putString("Id", master.getMasterId());
         masterTag.putString("Name", master.getDisplayName());
         masterTag.putString("Dimension", master.getDimension());
         masterTag.putLong("Pos", master.getPosition().asLong());
         mastersList.add(masterTag);
      }

      tag.put("InteractedMasters", mastersList);
      ListTag minigamesList = new ListTag();

      for (String minigame : this.knownMinigames) {
         minigamesList.add(StringTag.valueOf(minigame));
      }

      tag.put("KnownMinigames", minigamesList);
      return tag;
   }

   public void load(CompoundTag tag) {
      if (tag.contains("Race", 8)) {
         this.race = tag.getString("Race");
      } else if (tag.contains("Race", 3)) {
         int oldRaceId = tag.getInt("Race");
         List<String> races = ConfigManager.getLoadedRaces();
         this.race = oldRaceId >= 0 && oldRaceId < races.size() ? races.get(oldRaceId) : "human";
      } else {
         this.race = "human";
      }

      this.gender = tag.getString("Gender");
      this.characterClass = tag.getString("Class");
      this.hairId = tag.getInt("HairId");
      if (tag.contains("HairBase")) {
         this.hairBase.load(tag.getCompound("HairBase"));
      }

      if (tag.contains("HairSSJ")) {
         this.hairSSJ.load(tag.getCompound("HairSSJ"));
      }

      if (tag.contains("HairSSJ2")) {
         this.hairSSJ2.load(tag.getCompound("HairSSJ2"));
      }

      if (tag.contains("HairSSJ3")) {
         this.hairSSJ3.load(tag.getCompound("HairSSJ3"));
      }

      this.activeHeadBone = tag.getString("ActiveHeadBone");
      this.bodyType = tag.getInt("BodyType");
      this.eyesType = tag.getInt("EyesType");
      this.noseType = tag.getInt("NoseType");
      this.mouthType = tag.getInt("MouthType");
      this.tattooType = tag.getInt("TattooType");
      this.boobScale = tag.contains("BoobScale") ? tag.getFloat("BoobScale") : 1.0F;
      this.setBodyColor(tag.getString("BodyColor"));
      this.setBodyColor2(tag.getString("BodyColor2"));
      this.setBodyColor3(tag.getString("BodyColor3"));
      this.setHairColor(tag.getString("HairColor"));
      this.setEye1Color(tag.getString("Eye1Color"));
      this.setEye2Color(tag.getString("Eye2Color"));
      this.setAuraColor(tag.getString("AuraColor"));
      if (tag.contains("SelectedMaster")) {
         this.selectedMaster = tag.getString("SelectedMaster");
      }

      this.selectedFormGroup = tag.getString("SelectedFormGroup");
      this.activeFormGroup = tag.getString("CurrentFormGroup");
      this.selectedForm = tag.getString("SelectedForm");
      this.activeForm = tag.getString("CurrentForm");
      this.activeFormItemDurationTicks = tag.getInt("ActiveFormItemDurationTicks");
      this.previousFormGroup = tag.getString("PreviousFormGroup");
      this.previousForm = tag.getString("PreviousForm");
      this.hasPreviousFormRecord = tag.getBoolean("HasPreviousFormRecord");
      if (tag.contains("FormMasteries")) {
         this.formMasteries.load(tag.getCompound("FormMasteries"));
      }

      if (tag.contains("FormsUsedBefore")) {
         this.formsUsedBefore.load(tag.getCompound("FormsUsedBefore"));
      }

      this.selectedStackFormGroup = tag.getString("SelectedStackFormGroup");
      this.activeStackFormGroup = tag.getString("CurrentStackFormGroup");
      this.selectedStackForm = tag.getString("SelectedStackForm");
      this.activeStackForm = tag.getString("CurrentStackForm");
      this.activeStackFormItemDurationTicks = tag.getInt("ActiveStackFormItemDurationTicks");
      this.previousStackFormGroup = tag.getString("PreviousStackFormGroup");
      this.previousStackForm = tag.getString("PreviousStackForm");
      this.hasPreviousStackFormRecord = tag.getBoolean("HasPreviousStackFormRecord");
      if (tag.contains("StackFormMasteries")) {
         this.stackFormMasteries.load(tag.getCompound("StackFormMasteries"));
      }

      if (tag.contains("StackFormsUsedBefore")) {
         this.stackFormsUsedBefore.load(tag.getCompound("StackFormsUsedBefore"));
      }

      this.hasSaiyanTail = tag.getBoolean("HasSaiyanTail");
      this.renderHairBase = tag.getBoolean("RenderHairBase");
      this.armored = tag.getBoolean("isArmored");
      this.interactedMasters.clear();
      if (tag.contains("InteractedMasters")) {
         ListTag mastersList = tag.getList("InteractedMasters", 10);

         for (int i = 0; i < mastersList.size(); i++) {
            CompoundTag masterTag = mastersList.getCompound(i);
            String id = masterTag.getString("Id");
            String name = masterTag.getString("Name");
            String dim = masterTag.getString("Dimension");
            BlockPos pos = BlockPos.of(masterTag.getLong("Pos"));
            this.interactedMasters.put(id, new MasterLocation(id, name, dim, pos));
         }
      }

      this.knownMinigames.clear();
      if (tag.contains("KnownMinigames")) {
         ListTag minigamesList = tag.getList("KnownMinigames", 8);

         for (int i = 0; i < minigamesList.size(); i++) {
            this.knownMinigames.add(minigamesList.getString(i));
         }
      }

      this.updateOozaruCache();
   }

   public boolean hasActiveForm() {
      return !this.activeFormGroup.isEmpty() && !this.activeForm.isEmpty();
   }

   public void setActiveForm(String groupName, String formName) {
      this.activeFormGroup = groupName != null ? groupName : "";
      this.activeForm = formName != null ? formName : "";
      this.activeFormItemDurationTicks = 0;
      this.updateOozaruCache();
   }

   public void recordPreviousForm() {
      this.previousFormGroup = this.activeFormGroup;
      this.previousForm = this.activeForm;
      this.hasPreviousFormRecord = true;
   }

   public void clearPreviousFormRecord() {
      this.previousFormGroup = "";
      this.previousForm = "";
      this.hasPreviousFormRecord = false;
   }

   public void recordPreviousStackForm() {
      this.previousStackFormGroup = this.activeStackFormGroup;
      this.previousStackForm = this.activeStackForm;
      this.hasPreviousStackFormRecord = true;
   }

   public void clearPreviousStackFormRecord() {
      this.previousStackFormGroup = "";
      this.previousStackForm = "";
      this.hasPreviousStackFormRecord = false;
   }

   public void clearActiveForm() {
      this.activeFormGroup = "";
      this.activeForm = "";
      this.activeFormItemDurationTicks = 0;
      this.updateOozaruCache();
   }

   public void clearActiveForm(LivingEntity entity) {
      this.clearActiveForm(entity, true);
   }

   public void clearActiveForm(LivingEntity entity, boolean playSound) {
      String oldGroup = this.activeFormGroup;
      String oldForm = this.activeForm;
      boolean hadForm = this.hasActiveForm();
      if (entity != null && hadForm) {
         if (playSound) {
            entity.level()
               .playSound(null, entity.getX(), entity.getY(), entity.getZ(), (SoundEvent)MainSounds.TRANSFORM_OFF.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
         }

         entity.refreshDimensions();
      }

      this.clearActiveForm();
      if (hadForm && entity instanceof ServerPlayer serverPlayer && !serverPlayer.level().isClientSide) {
         NeoForge.EVENT_BUS.post(new DMZEvent.FormChangeEvent(serverPlayer, oldGroup, oldForm, "", ""));
      }
   }

   public FormConfig.FormData getActiveFormData() {
      return !this.hasActiveForm() ? null : ConfigManager.getForm(this.getRaceName(), this.activeFormGroup, this.activeForm);
   }

   public void gainMastery(String group, String form, double amount) {
      this.addMasteryResolved(group, form, amount);
      FormConfig.FormData formData = this.resolveFormData(group, form);
      if (formData != null) {
         double shared = amount * formData.getShareMasteryMultiplier();
         if (shared != 0.0) {
            for (String entry : formData.getShareMasteryWith()) {
               if (entry != null) {
                  int dot = entry.indexOf(46);
                  if (dot > 0 && dot < entry.length() - 1) {
                     this.addMasteryResolved(entry.substring(0, dot), entry.substring(dot + 1), shared);
                  }
               }
            }
         }
      }
   }

   private void addMasteryResolved(String group, String form, double amount) {
      boolean isStack = ConfigManager.getStackFormGroup(group) != null;
      FormConfig.FormData formData = isStack ? ConfigManager.getStackForm(group, form) : ConfigManager.getForm(this.getRaceName(), group, form);
      double maxMastery = formData != null ? formData.getMaxMastery() : 100.0;
      (isStack ? this.stackFormMasteries : this.formMasteries).addMastery(group, form, amount, maxMastery);
   }

   private FormConfig.FormData resolveFormData(String group, String form) {
      boolean isStack = ConfigManager.getStackFormGroup(group) != null;
      return isStack ? ConfigManager.getStackForm(group, form) : ConfigManager.getForm(this.getRaceName(), group, form);
   }

   public boolean hasActiveStackForm() {
      return !this.activeStackFormGroup.isEmpty() && !this.activeStackForm.isEmpty();
   }

   public void setActiveStackForm(String groupName, String formName) {
      this.activeStackFormGroup = groupName != null ? groupName : "";
      this.activeStackForm = formName != null ? formName : "";
      this.activeStackFormItemDurationTicks = 0;
      this.updateOozaruCache();
   }

   public void clearActiveStackForm() {
      this.activeStackFormGroup = "";
      this.activeStackForm = "";
      this.activeStackFormItemDurationTicks = 0;
      this.updateOozaruCache();
   }

   public void clearActiveStackForm(LivingEntity entity) {
      this.clearActiveStackForm(entity, true);
   }

   public void clearActiveStackForm(LivingEntity entity, boolean playSound) {
      String oldGroup = this.activeStackFormGroup;
      String oldForm = this.activeStackForm;
      boolean hadForm = this.hasActiveStackForm();
      if (entity != null && hadForm) {
         if (playSound) {
            entity.level()
               .playSound(null, entity.getX(), entity.getY(), entity.getZ(), (SoundEvent)MainSounds.TRANSFORM_OFF.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
         }

         entity.refreshDimensions();
      }

      this.clearActiveStackForm();
      if (hadForm && entity instanceof ServerPlayer serverPlayer && !serverPlayer.level().isClientSide) {
         NeoForge.EVENT_BUS.post(new DMZEvent.StackFormChangeEvent(serverPlayer, oldGroup, oldForm, "", ""));
      }
   }

   public FormConfig.FormData getActiveStackFormData() {
      return !this.hasActiveStackForm() ? null : ConfigManager.getStackForm(this.activeStackFormGroup, this.activeStackForm);
   }

   public boolean areExtraHeadBonesEnabled() {
      FormConfig.FormData activeStackFormData = this.getActiveStackFormData();
      if (activeStackFormData != null && !activeStackFormData.isKeepBaseFormHeadBones()) {
         return false;
      } else {
         FormConfig.FormData activeFormData = this.getActiveFormData();
         return activeFormData == null || activeFormData.isKeepBaseFormHeadBones();
      }
   }

   public String getRenderableHeadBone() {
      String bone = safeString(this.activeHeadBone);
      if (!bone.isEmpty() && !bone.equals("hair")) {
         return this.areExtraHeadBonesEnabled() ? bone : "";
      } else {
         return bone;
      }
   }

   public void saveAppearance(CompoundTag tag) {
      tag.putString("BodyColor", safeString(this.bodyColor));
      tag.putString("BodyColor2", safeString(this.bodyColor2));
      tag.putString("BodyColor3", safeString(this.bodyColor3));
      tag.putString("HairColor", safeString(this.hairColor));
      tag.putString("Eye1Color", safeString(this.eye1Color));
      tag.putString("Eye2Color", safeString(this.eye2Color));
      tag.putString("AuraColor", safeString(this.auraColor));
   }

   public void loadAppearance(CompoundTag tag) {
      if (tag.contains("BodyColor")) {
         this.setBodyColor(tag.getString("BodyColor"));
      }

      if (tag.contains("BodyColor2")) {
         this.setBodyColor2(tag.getString("BodyColor2"));
      }

      if (tag.contains("BodyColor3")) {
         this.setBodyColor3(tag.getString("BodyColor3"));
      }

      if (tag.contains("HairColor")) {
         this.setHairColor(tag.getString("HairColor"));
      }

      if (tag.contains("Eye1Color")) {
         this.setEye1Color(tag.getString("Eye1Color"));
      }

      if (tag.contains("Eye2Color")) {
         this.setEye2Color(tag.getString("Eye2Color"));
      }

      if (tag.contains("AuraColor")) {
         this.setAuraColor(tag.getString("AuraColor"));
      }
   }

   public void copyFrom(Character other) {
      this.race = other.race;
      this.gender = other.gender;
      this.characterClass = other.characterClass;
      this.hairId = other.hairId;
      this.hairBase = other.hairBase.copy();
      this.hairSSJ = other.hairSSJ.copy();
      this.hairSSJ2 = other.hairSSJ2.copy();
      this.hairSSJ3 = other.hairSSJ3.copy();
      this.activeHeadBone = other.activeHeadBone;
      this.bodyType = other.bodyType;
      this.eyesType = other.eyesType;
      this.noseType = other.noseType;
      this.mouthType = other.mouthType;
      this.tattooType = other.tattooType;
      this.boobScale = other.boobScale;
      this.setBodyColor(other.bodyColor);
      this.setBodyColor2(other.bodyColor2);
      this.setBodyColor3(other.bodyColor3);
      this.setHairColor(other.hairColor);
      this.setEye1Color(other.eye1Color);
      this.setEye2Color(other.eye2Color);
      this.setAuraColor(other.auraColor);
      this.selectedMaster = safeString(other.selectedMaster);
      this.selectedFormGroup = safeString(other.selectedFormGroup);
      this.activeFormGroup = safeString(other.activeFormGroup);
      this.selectedForm = safeString(other.selectedForm);
      this.activeForm = safeString(other.activeForm);
      this.activeFormItemDurationTicks = other.activeFormItemDurationTicks;
      this.previousFormGroup = safeString(other.previousFormGroup);
      this.previousForm = safeString(other.previousForm);
      this.hasPreviousFormRecord = other.hasPreviousFormRecord;
      this.formMasteries.copyFrom(other.formMasteries);
      this.selectedStackFormGroup = safeString(other.selectedStackFormGroup);
      this.activeStackFormGroup = safeString(other.activeStackFormGroup);
      this.selectedStackForm = safeString(other.selectedStackForm);
      this.activeStackForm = safeString(other.activeStackForm);
      this.activeStackFormItemDurationTicks = other.activeStackFormItemDurationTicks;
      this.previousStackFormGroup = safeString(other.previousStackFormGroup);
      this.previousStackForm = safeString(other.previousStackForm);
      this.hasPreviousStackFormRecord = other.hasPreviousStackFormRecord;
      this.stackFormMasteries.copyFrom(other.stackFormMasteries);
      this.hasSaiyanTail = other.hasSaiyanTail;
      this.renderHairBase = other.renderHairBase;
      this.armored = other.armored;
      this.interactedMasters.clear();
      this.interactedMasters.putAll(other.interactedMasters);
      this.knownMinigames.clear();
      this.knownMinigames.addAll(other.knownMinigames);
      this.updateOozaruCache();
   }

   public void setGender(String gender) {
      this.gender = gender;
   }

   public void setCharacterClass(String characterClass) {
      this.characterClass = characterClass;
   }

   public void setSelectedMaster(String selectedMaster) {
      this.selectedMaster = selectedMaster;
   }

   public void setActiveFormGroup(String activeFormGroup) {
      this.activeFormGroup = activeFormGroup;
   }

   public void setActiveForm(String activeForm) {
      this.activeForm = activeForm;
   }

   public void setFormsUsedBefore(UsedForms formsUsedBefore) {
      this.formsUsedBefore = formsUsedBefore;
   }

   public void setActiveFormItemDurationTicks(int activeFormItemDurationTicks) {
      this.activeFormItemDurationTicks = activeFormItemDurationTicks;
   }

   public void setPreviousFormGroup(String previousFormGroup) {
      this.previousFormGroup = previousFormGroup;
   }

   public void setPreviousForm(String previousForm) {
      this.previousForm = previousForm;
   }

   public void setHasPreviousFormRecord(boolean hasPreviousFormRecord) {
      this.hasPreviousFormRecord = hasPreviousFormRecord;
   }

   public void setActiveStackFormGroup(String activeStackFormGroup) {
      this.activeStackFormGroup = activeStackFormGroup;
   }

   public void setActiveStackForm(String activeStackForm) {
      this.activeStackForm = activeStackForm;
   }

   public void setStackFormsUsedBefore(UsedForms stackFormsUsedBefore) {
      this.stackFormsUsedBefore = stackFormsUsedBefore;
   }

   public void setActiveStackFormItemDurationTicks(int activeStackFormItemDurationTicks) {
      this.activeStackFormItemDurationTicks = activeStackFormItemDurationTicks;
   }

   public void setPreviousStackFormGroup(String previousStackFormGroup) {
      this.previousStackFormGroup = previousStackFormGroup;
   }

   public void setPreviousStackForm(String previousStackForm) {
      this.previousStackForm = previousStackForm;
   }

   public void setHasPreviousStackFormRecord(boolean hasPreviousStackFormRecord) {
      this.hasPreviousStackFormRecord = hasPreviousStackFormRecord;
   }

   public void setHasSaiyanTail(boolean hasSaiyanTail) {
      this.hasSaiyanTail = hasSaiyanTail;
   }

   public void setRenderHairBase(boolean renderHairBase) {
      this.renderHairBase = renderHairBase;
   }

   public void setHairId(int hairId) {
      this.hairId = hairId;
   }

   public void setHairBase(CustomHair hairBase) {
      this.hairBase = hairBase;
   }

   public void setHairSSJ(CustomHair hairSSJ) {
      this.hairSSJ = hairSSJ;
   }

   public void setHairSSJ2(CustomHair hairSSJ2) {
      this.hairSSJ2 = hairSSJ2;
   }

   public void setHairSSJ3(CustomHair hairSSJ3) {
      this.hairSSJ3 = hairSSJ3;
   }

   public void setActiveHeadBone(String activeHeadBone) {
      this.activeHeadBone = activeHeadBone;
   }

   public void setBodyType(int bodyType) {
      this.bodyType = bodyType;
   }

   public void setEyesType(int eyesType) {
      this.eyesType = eyesType;
   }

   public void setNoseType(int noseType) {
      this.noseType = noseType;
   }

   public void setMouthType(int mouthType) {
      this.mouthType = mouthType;
   }

   public void setTattooType(int tattooType) {
      this.tattooType = tattooType;
   }

   public void setBoobScale(float boobScale) {
      this.boobScale = boobScale;
   }

   public void setRgbBodyColor(float[] rgbBodyColor) {
      this.rgbBodyColor = rgbBodyColor;
   }

   public void setRgbBodyColor2(float[] rgbBodyColor2) {
      this.rgbBodyColor2 = rgbBodyColor2;
   }

   public void setRgbBodyColor3(float[] rgbBodyColor3) {
      this.rgbBodyColor3 = rgbBodyColor3;
   }

   public void setRgbHairColor(float[] rgbHairColor) {
      this.rgbHairColor = rgbHairColor;
   }

   public void setRgbEye1Color(float[] rgbEye1Color) {
      this.rgbEye1Color = rgbEye1Color;
   }

   public void setRgbEye2Color(float[] rgbEye2Color) {
      this.rgbEye2Color = rgbEye2Color;
   }

   public void setRgbAuraColor(float[] rgbAuraColor) {
      this.rgbAuraColor = rgbAuraColor;
   }

   public void setOozaruCached(boolean oozaruCached) {
      this.oozaruCached = oozaruCached;
   }

   public void setArmored(Boolean armored) {
      this.armored = armored;
   }

   public String getRace() {
      return this.race;
   }

   public String getGender() {
      return this.gender;
   }

   public String getCharacterClass() {
      return this.characterClass;
   }

   public String getSelectedMaster() {
      return this.selectedMaster;
   }

   public String getSelectedFormGroup() {
      return this.selectedFormGroup;
   }

   public String getActiveFormGroup() {
      return this.activeFormGroup;
   }

   public String getSelectedForm() {
      return this.selectedForm;
   }

   public String getActiveForm() {
      return this.activeForm;
   }

   public FormMasteries getFormMasteries() {
      return this.formMasteries;
   }

   public UsedForms getFormsUsedBefore() {
      return this.formsUsedBefore;
   }

   public int getActiveFormItemDurationTicks() {
      return this.activeFormItemDurationTicks;
   }

   public String getPreviousFormGroup() {
      return this.previousFormGroup;
   }

   public String getPreviousForm() {
      return this.previousForm;
   }

   public boolean isHasPreviousFormRecord() {
      return this.hasPreviousFormRecord;
   }

   public String getSelectedStackFormGroup() {
      return this.selectedStackFormGroup;
   }

   public String getActiveStackFormGroup() {
      return this.activeStackFormGroup;
   }

   public String getSelectedStackForm() {
      return this.selectedStackForm;
   }

   public String getActiveStackForm() {
      return this.activeStackForm;
   }

   public FormMasteries getStackFormMasteries() {
      return this.stackFormMasteries;
   }

   public UsedForms getStackFormsUsedBefore() {
      return this.stackFormsUsedBefore;
   }

   public int getActiveStackFormItemDurationTicks() {
      return this.activeStackFormItemDurationTicks;
   }

   public String getPreviousStackFormGroup() {
      return this.previousStackFormGroup;
   }

   public String getPreviousStackForm() {
      return this.previousStackForm;
   }

   public boolean isHasPreviousStackFormRecord() {
      return this.hasPreviousStackFormRecord;
   }

   public boolean isHasSaiyanTail() {
      return this.hasSaiyanTail;
   }

   public boolean isRenderHairBase() {
      return this.renderHairBase;
   }

   public Map<String, MasterLocation> getInteractedMasters() {
      return this.interactedMasters;
   }

   public Set<String> getKnownMinigames() {
      return this.knownMinigames;
   }

   public int getHairId() {
      return this.hairId;
   }

   public String getActiveHeadBone() {
      return this.activeHeadBone;
   }

   public int getBodyType() {
      return this.bodyType;
   }

   public int getEyesType() {
      return this.eyesType;
   }

   public int getNoseType() {
      return this.noseType;
   }

   public int getMouthType() {
      return this.mouthType;
   }

   public int getTattooType() {
      return this.tattooType;
   }

   public float getBoobScale() {
      return this.boobScale;
   }

   public String getBodyColor() {
      return this.bodyColor;
   }

   public String getBodyColor2() {
      return this.bodyColor2;
   }

   public String getBodyColor3() {
      return this.bodyColor3;
   }

   public String getHairColor() {
      return this.hairColor;
   }

   public String getEye1Color() {
      return this.eye1Color;
   }

   public String getEye2Color() {
      return this.eye2Color;
   }

   public String getAuraColor() {
      return this.auraColor;
   }

   public boolean isOozaruCached() {
      return this.oozaruCached;
   }

   public Boolean getArmored() {
      return this.armored;
   }
}
