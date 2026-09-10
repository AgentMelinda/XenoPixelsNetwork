package com.dragonminez.common.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import lombok.Generated;

public class RaceCharacterConfig {
   public static final String CURRENT_VERSION = "2.1.3";
   private String configVersion;
   private String raceName;
   private Boolean hasGender = true;
   private Boolean useVanillaSkin = false;
   private String customModel = "";
   private Boolean isLayered = false;
   private String[] headBones = new String[0];
   private String racialSkill = "human";
   private Boolean hasSaiyanTail = false;
   private String auraType = "kakarot";
   private Float[] defaultModelScaling = new Float[]{0.9375F, 0.9375F, 0.9375F};
   private Integer defaultBodyType = 0;
   private Integer defaultHairType = 0;
   private Integer defaultEyesType = 0;
   private Integer defaultNoseType = 0;
   private Integer defaultMouthType = 0;
   private Integer defaultTattooType = 0;
   private String defaultBodyColor = null;
   private String defaultBodyColor2 = null;
   private String defaultBodyColor3 = null;
   private String defaultHairColor = null;
   private String defaultEye1Color = null;
   private String defaultEye2Color = null;
   private String defaultAuraColor = null;
   private Map<String, RaceCharacterConfig.FormSkillCost> formSkillsCosts = new HashMap<>();

   private RaceCharacterConfig.FormSkillCost getFormSkillEntry(String form) {
      if (form == null) {
         return null;
      } else {
         RaceCharacterConfig.FormSkillCost entry = this.formSkillsCosts.get(form);
         return entry != null ? entry : this.formSkillsCosts.get(form.toLowerCase());
      }
   }

   public Integer[] getFormSkillTpCosts(String form) {
      RaceCharacterConfig.FormSkillCost entry = this.getFormSkillEntry(form);
      List<Integer> list = (List<Integer>)(entry != null && entry.getPrices() != null ? entry.getPrices() : new ArrayList<>());
      return list.toArray(new Integer[0]);
   }

   public boolean isFormSkillBuyFromMaster(String form) {
      RaceCharacterConfig.FormSkillCost entry = this.getFormSkillEntry(form);
      return entry != null && entry.isBuyFromMaster();
   }

   public boolean hasFormSkill(String form) {
      return this.getFormSkillEntry(form) != null;
   }

   public Collection<String> getFormSkills() {
      return this.formSkillsCosts.keySet();
   }

   public void setFormSkillTpCosts(String form, Integer[] costs) {
      this.formSkillsCosts.put(form, new RaceCharacterConfig.FormSkillCost(new ArrayList<>(Arrays.asList(costs))));
   }

   public boolean normalizeFormSkillKeys(Collection<String> canonicalFormSkills) {
      if (this.formSkillsCosts != null && !this.formSkillsCosts.isEmpty() && canonicalFormSkills != null && !canonicalFormSkills.isEmpty()) {
         Set<String> canonical = new HashSet<>();

         for (String name : canonicalFormSkills) {
            if (name != null) {
               canonical.add(name.toLowerCase());
            }
         }

         Map<String, RaceCharacterConfig.FormSkillCost> normalized = new LinkedHashMap<>();
         boolean changed = false;

         for (Entry<String, RaceCharacterConfig.FormSkillCost> entry : this.formSkillsCosts.entrySet()) {
            String key = entry.getKey();
            if (key != null) {
               String lower = key.toLowerCase();
               if (canonical.contains(lower)) {
                  if (!normalized.containsKey(lower)) {
                     normalized.put(lower, entry.getValue());
                  }

                  if (!key.equals(lower)) {
                     changed = true;
                  }
               }
            }
         }

         for (Entry<String, RaceCharacterConfig.FormSkillCost> entryx : this.formSkillsCosts.entrySet()) {
            String key = entryx.getKey();
            if (key != null) {
               String lower = key.toLowerCase();
               if (!canonical.contains(lower)) {
                  String target = canonical.contains(lower + "s") ? lower + "s" : lower;
                  if (!target.equals(key)) {
                     changed = true;
                  }

                  RaceCharacterConfig.FormSkillCost existing = normalized.get(target);
                  if (existing == null || existing.getPrices() == null || existing.getPrices().isEmpty()) {
                     normalized.put(target, entryx.getValue());
                  }
               }
            }
         }

         if (changed) {
            this.formSkillsCosts.clear();
            this.formSkillsCosts.putAll(normalized);
         }

         return changed;
      } else {
         return false;
      }
   }

   public Boolean hasCustomModel() {
      return this.customModel != null && !this.customModel.isEmpty();
   }

   @Generated
   public void setConfigVersion(String configVersion) {
      this.configVersion = configVersion;
   }

   @Generated
   public void setRaceName(String raceName) {
      this.raceName = raceName;
   }

   @Generated
   public void setHasGender(Boolean hasGender) {
      this.hasGender = hasGender;
   }

   @Generated
   public void setUseVanillaSkin(Boolean useVanillaSkin) {
      this.useVanillaSkin = useVanillaSkin;
   }

   @Generated
   public void setCustomModel(String customModel) {
      this.customModel = customModel;
   }

   @Generated
   public void setIsLayered(Boolean isLayered) {
      this.isLayered = isLayered;
   }

   @Generated
   public void setHeadBones(String[] headBones) {
      this.headBones = headBones;
   }

   @Generated
   public void setRacialSkill(String racialSkill) {
      this.racialSkill = racialSkill;
   }

   @Generated
   public void setHasSaiyanTail(Boolean hasSaiyanTail) {
      this.hasSaiyanTail = hasSaiyanTail;
   }

   @Generated
   public void setAuraType(String auraType) {
      this.auraType = auraType;
   }

   @Generated
   public void setDefaultModelScaling(Float[] defaultModelScaling) {
      this.defaultModelScaling = defaultModelScaling;
   }

   @Generated
   public void setDefaultBodyType(Integer defaultBodyType) {
      this.defaultBodyType = defaultBodyType;
   }

   @Generated
   public void setDefaultHairType(Integer defaultHairType) {
      this.defaultHairType = defaultHairType;
   }

   @Generated
   public void setDefaultEyesType(Integer defaultEyesType) {
      this.defaultEyesType = defaultEyesType;
   }

   @Generated
   public void setDefaultNoseType(Integer defaultNoseType) {
      this.defaultNoseType = defaultNoseType;
   }

   @Generated
   public void setDefaultMouthType(Integer defaultMouthType) {
      this.defaultMouthType = defaultMouthType;
   }

   @Generated
   public void setDefaultTattooType(Integer defaultTattooType) {
      this.defaultTattooType = defaultTattooType;
   }

   @Generated
   public void setDefaultBodyColor(String defaultBodyColor) {
      this.defaultBodyColor = defaultBodyColor;
   }

   @Generated
   public void setDefaultBodyColor2(String defaultBodyColor2) {
      this.defaultBodyColor2 = defaultBodyColor2;
   }

   @Generated
   public void setDefaultBodyColor3(String defaultBodyColor3) {
      this.defaultBodyColor3 = defaultBodyColor3;
   }

   @Generated
   public void setDefaultHairColor(String defaultHairColor) {
      this.defaultHairColor = defaultHairColor;
   }

   @Generated
   public void setDefaultEye1Color(String defaultEye1Color) {
      this.defaultEye1Color = defaultEye1Color;
   }

   @Generated
   public void setDefaultEye2Color(String defaultEye2Color) {
      this.defaultEye2Color = defaultEye2Color;
   }

   @Generated
   public void setDefaultAuraColor(String defaultAuraColor) {
      this.defaultAuraColor = defaultAuraColor;
   }

   @Generated
   public void setFormSkillsCosts(Map<String, RaceCharacterConfig.FormSkillCost> formSkillsCosts) {
      this.formSkillsCosts = formSkillsCosts;
   }

   @Generated
   public String getConfigVersion() {
      return this.configVersion;
   }

   @Generated
   public String getRaceName() {
      return this.raceName;
   }

   @Generated
   public Boolean getHasGender() {
      return this.hasGender;
   }

   @Generated
   public Boolean getUseVanillaSkin() {
      return this.useVanillaSkin;
   }

   @Generated
   public String getCustomModel() {
      return this.customModel;
   }

   @Generated
   public Boolean getIsLayered() {
      return this.isLayered;
   }

   @Generated
   public String[] getHeadBones() {
      return this.headBones;
   }

   @Generated
   public String getRacialSkill() {
      return this.racialSkill;
   }

   @Generated
   public Boolean getHasSaiyanTail() {
      return this.hasSaiyanTail;
   }

   @Generated
   public String getAuraType() {
      return this.auraType;
   }

   @Generated
   public Float[] getDefaultModelScaling() {
      return this.defaultModelScaling;
   }

   @Generated
   public Integer getDefaultBodyType() {
      return this.defaultBodyType;
   }

   @Generated
   public Integer getDefaultHairType() {
      return this.defaultHairType;
   }

   @Generated
   public Integer getDefaultEyesType() {
      return this.defaultEyesType;
   }

   @Generated
   public Integer getDefaultNoseType() {
      return this.defaultNoseType;
   }

   @Generated
   public Integer getDefaultMouthType() {
      return this.defaultMouthType;
   }

   @Generated
   public Integer getDefaultTattooType() {
      return this.defaultTattooType;
   }

   @Generated
   public String getDefaultBodyColor() {
      return this.defaultBodyColor;
   }

   @Generated
   public String getDefaultBodyColor2() {
      return this.defaultBodyColor2;
   }

   @Generated
   public String getDefaultBodyColor3() {
      return this.defaultBodyColor3;
   }

   @Generated
   public String getDefaultHairColor() {
      return this.defaultHairColor;
   }

   @Generated
   public String getDefaultEye1Color() {
      return this.defaultEye1Color;
   }

   @Generated
   public String getDefaultEye2Color() {
      return this.defaultEye2Color;
   }

   @Generated
   public String getDefaultAuraColor() {
      return this.defaultAuraColor;
   }

   @Generated
   public Map<String, RaceCharacterConfig.FormSkillCost> getFormSkillsCosts() {
      return this.formSkillsCosts;
   }

   public static class FormSkillCost {
      private boolean buyFromMaster = false;
      private List<Integer> prices = new ArrayList<>();

      public FormSkillCost(List<Integer> prices) {
         this.prices = (List<Integer>)(prices != null ? prices : new ArrayList<>());
      }

      public FormSkillCost(boolean buyFromMaster, List<Integer> prices) {
         this.buyFromMaster = buyFromMaster;
         this.prices = (List<Integer>)(prices != null ? prices : new ArrayList<>());
      }

      @Generated
      public boolean isBuyFromMaster() {
         return this.buyFromMaster;
      }

      @Generated
      public List<Integer> getPrices() {
         return this.prices;
      }

      @Generated
      public void setBuyFromMaster(boolean buyFromMaster) {
         this.buyFromMaster = buyFromMaster;
      }

      @Generated
      public void setPrices(List<Integer> prices) {
         this.prices = prices;
      }

      @Generated
      public FormSkillCost() {
      }

      public static class Adapter implements JsonDeserializer<RaceCharacterConfig.FormSkillCost>, JsonSerializer<RaceCharacterConfig.FormSkillCost> {
         public RaceCharacterConfig.FormSkillCost deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
               return new RaceCharacterConfig.FormSkillCost();
            } else if (json.isJsonArray()) {
               return new RaceCharacterConfig.FormSkillCost(false, parsePrices(json.getAsJsonArray()));
            } else if (!json.isJsonObject()) {
               return new RaceCharacterConfig.FormSkillCost();
            } else {
               JsonObject obj = json.getAsJsonObject();
               boolean buyFromMaster = obj.has("buyFromMaster") && !obj.get("buyFromMaster").isJsonNull() && obj.get("buyFromMaster").getAsBoolean();
               List<Integer> prices = (List<Integer>)(obj.has("prices") && obj.get("prices").isJsonArray()
                  ? parsePrices(obj.getAsJsonArray("prices"))
                  : new ArrayList<>());
               return new RaceCharacterConfig.FormSkillCost(buyFromMaster, prices);
            }
         }

         public JsonElement serialize(RaceCharacterConfig.FormSkillCost src, Type typeOfSrc, JsonSerializationContext ctx) {
            JsonObject obj = new JsonObject();
            obj.addProperty("buyFromMaster", src != null && src.isBuyFromMaster());
            JsonArray prices = new JsonArray();
            if (src != null && src.getPrices() != null) {
               for (Integer p : src.getPrices()) {
                  prices.add(p);
               }
            }

            obj.add("prices", prices);
            return obj;
         }

         private static List<Integer> parsePrices(JsonArray arr) {
            List<Integer> prices = new ArrayList<>();

            for (JsonElement el : arr) {
               if (el != null && !el.isJsonNull()) {
                  try {
                     prices.add(el.getAsInt());
                  } catch (UnsupportedOperationException | NumberFormatException var5) {
                  }
               }
            }

            return prices;
         }
      }
   }
}
