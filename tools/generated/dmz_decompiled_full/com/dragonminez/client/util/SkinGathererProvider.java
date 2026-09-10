package com.dragonminez.client.util;

import com.dragonminez.client.render.layer.DMZSkinLayer;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.ActionMode;
import com.dragonminez.common.util.TransformationsHelper;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class SkinGathererProvider {
   public static SkinGathererProvider INSTANCE = new SkinGathererProvider();
   private static final Set<String> BUILTIN_RACES = Set.of("human", "saiyan", "namekian", "majin", "frostdemon", "bioandroid");
   private static final Map<String, ResourceLocation> TEXTURE_CACHE = new ConcurrentHashMap<>();
   private static final float[] WHITE_COLOR = new float[]{1.0F, 1.0F, 1.0F};
   private static final float[] DEFAULT_TAIL_COLOR = ColorUtils.hexToRgb("#572117");
   private static final float[] DEFAULT_ORANGE_COLOR = ColorUtils.hexToRgb("#e67d40");
   private static final float[] DEFAULT_STINGER_COLOR = ColorUtils.hexToRgb("#D9B28D");

   public static boolean isBuiltInRace(String race) {
      return race != null && BUILTIN_RACES.contains(race.toLowerCase());
   }

   public static String modelFamily(String key) {
      if (key != null && !key.isEmpty()) {
         String k = key.toLowerCase();
         if (k.startsWith("oozaru")) {
            return "oozaru";
         } else if (k.startsWith("namekian")) {
            return "namekian";
         } else if (k.startsWith("frostdemon")) {
            return "frostdemon";
         } else if (k.startsWith("bioandroid")) {
            return "bioandroid";
         } else if (k.startsWith("majin") || k.startsWith("janemba")) {
            return "majin";
         } else {
            return !k.startsWith("human")
                  && !k.startsWith("saiyan")
                  && !k.contains("ssj4d")
                  && !k.contains("ssj4gt")
                  && !k.startsWith("buffed")
                  && !k.equals("4arms")
               ? "custom"
               : "human";
         }
      } else {
         return "human";
      }
   }

   public static ResourceLocation getCachedTexture(String path) {
      return TEXTURE_CACHE.computeIfAbsent(path, p -> ResourceLocation.fromNamespaceAndPath("dragonminez", p));
   }

   private void emitFadingLayer(BiConsumer<ResourceLocation, float[]> consumer, String layerId, ResourceLocation texture, float[] color) {
      if (consumer instanceof SkinGathererProvider.BodyLayerSink sink) {
         sink.fading(layerId, texture, color);
      } else {
         consumer.accept(texture, color);
      }
   }

   private void emitExtraFormLayer(BiConsumer<ResourceLocation, float[]> consumer, String layerId, FormConfig.FormData form) {
      ResourceLocation extraTex = this.resolveConfiguredTexture(form.getExtraFormLayer());
      if (extraTex != null) {
         float[] extraColor = form.getRgbExtraFormColor() != null ? form.getRgbExtraFormColor() : WHITE_COLOR;
         this.emitFadingLayer(consumer, layerId, DMZSkinLayer.getSafeTexture(extraTex), extraColor);
      }
   }

   private ResourceLocation resolveConfiguredTexture(String path) {
      return path.contains(":") ? ResourceLocation.tryParse(path) : getCachedTexture(path);
   }

   public void gatherBodyLayers(AbstractClientPlayer player, StatsData stats, float partialTick, BiConsumer<ResourceLocation, float[]> consumer) {
      Character character = stats.getCharacter();
      String raceName = character.getRaceName().toLowerCase();
      int bodyType = character.getBodyType();
      String currentForm = character.getActiveForm();
      RaceCharacterConfig raceConfig = ConfigManager.getRaceCharacter(raceName);
      if (raceConfig != null) {
         String raceCustomModel = raceConfig.getCustomModel() != null ? raceConfig.getCustomModel().toLowerCase() : "";
         String formCustomModel = "";
         boolean hasStackForm = character.hasActiveStackForm() && character.getActiveStackFormData() != null;
         boolean hasForm = character.hasActiveForm() && character.getActiveFormData() != null;
         if (hasStackForm && character.getActiveStackFormData().hasCustomModel()) {
            formCustomModel = character.getActiveStackFormData().getCustomModel().toLowerCase();
         } else if (hasForm && character.getActiveFormData().hasCustomModel()) {
            formCustomModel = character.getActiveFormData().getCustomModel().toLowerCase();
         }

         String key = formCustomModel.isEmpty() ? raceCustomModel : formCustomModel;
         if (key.isEmpty()) {
            key = isBuiltInRace(raceName) ? raceName : "human";
         }

         String logicKey = key;
         if (key.equals("human_slim") || key.equals("majin_slim") || key.equals("base_slim")) {
            logicKey = raceName;
         }

         float[] b1 = character.getRgbBodyColor();
         float[] b2 = character.getRgbBodyColor2();
         float[] b3 = character.getRgbBodyColor3();
         float[] hair = character.getRgbHairColor();
         if (hasForm) {
            FormConfig.FormData f = character.getActiveFormData();
            if (f.getRgbBodyColor1() != null) {
               b1 = f.getRgbBodyColor1();
            }

            if (f.getRgbBodyColor2() != null) {
               b2 = f.getRgbBodyColor2();
            }

            if (f.getRgbBodyColor3() != null) {
               b3 = f.getRgbBodyColor3();
            }

            if (f.getRgbHairColor() != null) {
               hair = f.getRgbHairColor();
            }
         }

         if (hasStackForm) {
            FormConfig.FormData sf = character.getActiveStackFormData();
            if (sf.getRgbBodyColor1() != null) {
               b1 = sf.getRgbBodyColor1();
            }

            if (sf.getRgbBodyColor2() != null) {
               b2 = sf.getRgbBodyColor2();
            }

            if (sf.getRgbBodyColor3() != null) {
               b3 = sf.getRgbBodyColor3();
            }

            if (sf.getRgbHairColor() != null) {
               hair = sf.getRgbHairColor();
            }
         }

         if (stats.getStatus().isActionCharging()) {
            FormConfig.FormData nextForm = null;
            if (stats.getStatus().getSelectedAction() == ActionMode.FORM) {
               nextForm = TransformationsHelper.getNextAvailableForm(stats);
            } else if (stats.getStatus().getSelectedAction() == ActionMode.STACK) {
               nextForm = TransformationsHelper.getNextAvailableStackForm(stats);
            }

            if (nextForm != null) {
               float factor = Mth.clamp((float)stats.getResources().getActionCharge() / 100.0F, 0.0F, 1.0F);
               if (nextForm.getRgbBodyColor1() != null) {
                  b1 = DMZSkinLayer.lerpColor(factor, b1, nextForm.getRgbBodyColor1());
               }

               if (nextForm.getRgbBodyColor2() != null) {
                  b2 = DMZSkinLayer.lerpColor(factor, b2, nextForm.getRgbBodyColor2());
               }

               if (nextForm.getRgbBodyColor3() != null) {
                  b3 = DMZSkinLayer.lerpColor(factor, b3, nextForm.getRgbBodyColor3());
               }

               if (nextForm.getRgbHairColor() != null) {
                  hair = DMZSkinLayer.lerpColor(factor, hair, nextForm.getRgbHairColor());
               }
            }
         }

         if (hasForm && character.getActiveFormData().hasExtraFormLayer()) {
            this.emitExtraFormLayer(consumer, "extraform_form", character.getActiveFormData());
         }

         if (hasStackForm && character.getActiveStackFormData().hasExtraFormLayer()) {
            this.emitExtraFormLayer(consumer, "extraform_stack", character.getActiveStackFormData());
         }

         boolean isOozaruForm = raceName.equals("saiyan") && (Objects.equals(currentForm, "oozaru") || Objects.equals(currentForm, "goldenoozaru"));
         if (!logicKey.equals("oozaru") && !isOozaruForm) {
            boolean isSaiyanLogic = logicKey.equals("saiyan") || logicKey.contains("ssj4gt") || logicKey.contains("ssj4d") || raceName.equals("saiyan");
            boolean hasSaiyanTail = raceConfig.getHasSaiyanTail() != null && raceConfig.getHasSaiyanTail();
            boolean isSSJ4Active = currentForm != null && (currentForm.contains("supersaiyan4") || currentForm.contains("ssj4"));
            boolean renderSaiyanTail = (isSaiyanLogic || hasSaiyanTail) && (isSSJ4Active || stats.getStatus().isTailVisible() && character.isHasSaiyanTail());
            boolean isHumanoid = logicKey.equals("human")
               || logicKey.equals("saiyan")
               || logicKey.contains("ssj4d")
               || logicKey.contains("ssj4gt")
               || logicKey.equals("buffed")
               || logicKey.equals("4arms");
            if (isHumanoid && bodyType == 0) {
               consumer.accept(player.getSkin().texture(), WHITE_COLOR);
            } else if (isHumanoid) {
               this.resolveBodyHumanSaiyan(character, logicKey, b1, b2, b3, consumer);
            } else {
               switch (logicKey) {
                  case "namekian":
                  case "namekian_orange":
                  case "namekian_buffed":
                     this.resolveBodyNamekian(character, b1, b2, b3, consumer);
                     break;
                  case "majin":
                  case "majin_super":
                  case "majin_ultra":
                  case "majin_evil":
                  case "majin_kid":
                  case "janemba_fat":
                  case "janemba_super":
                     this.resolveBodyMajin(character, logicKey, b1, b2, b3, consumer);
                     break;
                  case "frostdemon":
                  case "frostdemon_second":
                  case "frostdemon_final":
                  case "frostdemon_fifth":
                  case "frostdemon_third":
                  case "frostdemon_fp":
                  case "frostdemon_mecha":
                  case "frostdemon_metalcore":
                     this.resolveBodyFrostDemon(character, logicKey, b1, b2, b3, hair, consumer);
                     break;
                  case "bioandroid":
                  case "bioandroid_semi":
                  case "bioandroid_perfect":
                  case "bioandroid_base":
                  case "bioandroid_ultra":
                  case "bioandroid_xeno":
                     this.resolveBodyBioAndroid(character, logicKey, b1, b2, b3, hair, consumer);
                     break;
                  default:
                     boolean hasGender = Boolean.TRUE.equals(raceConfig.getHasGender());
                     String genSuffix = hasGender ? (character.getGender().equals("female") ? "_female" : "_male") : "";
                     if (Boolean.TRUE.equals(raceConfig.getUseVanillaSkin()) && bodyType == 0) {
                        consumer.accept(player.getSkin().texture(), WHITE_COLOR);
                     } else if (Boolean.TRUE.equals(raceConfig.getIsLayered())) {
                        String prefix = "textures/entity/races/" + raceName + "/" + logicKey + genSuffix + "_" + bodyType + "_";
                        String fallbackPrefix = "textures/entity/races/" + raceName + "/" + logicKey + genSuffix + "_0_";
                        consumer.accept(
                           DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer1.png"), getCachedTexture(fallbackPrefix + "layer1.png")), b1
                        );
                        consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer2.png")), b2);
                        consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer3.png")), b3);
                     } else {
                        ResourceLocation customTex = getCachedTexture("textures/entity/races/" + raceName + "/" + logicKey + genSuffix + ".png");
                        consumer.accept(DMZSkinLayer.getSafeTexture(customTex), b1);
                     }
               }
            }

            if (renderSaiyanTail) {
               float[] tailColor = b2 != null ? b2 : DEFAULT_TAIL_COLOR;
               consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture("textures/entity/races/tail1.png")), tailColor);
            }
         } else {
            this.resolveBodyOozaru(b1, b2, consumer);
         }
      }
   }

   public void gatherAndroidLayers(AbstractClientPlayer player, StatsData stats, float partialTick, BiConsumer<ResourceLocation, float[]> consumer) {
      Character character = stats.getCharacter();
      String raceName = character.getRace().toLowerCase();
      boolean canBeUpgraded = ConfigManager.getRaceCharacter(raceName) != null
         && ConfigManager.getRaceCharacter(raceName).getFormSkillTpCosts("androidforms").length > 0;
      if (canBeUpgraded && stats.getStatus().isAndroidUpgraded()) {
         String androidPath = character.getGender().equals("female") ? "textures/entity/races/female_android.png" : "textures/entity/races/male_android.png";
         this.emitFadingLayer(consumer, "android", DMZSkinLayer.getSafeTexture(getCachedTexture(androidPath)), WHITE_COLOR);
      }
   }

   public void gatherTattooLayers(AbstractClientPlayer player, StatsData stats, float partialTick, BiConsumer<ResourceLocation, float[]> consumer) {
      int tattooType = stats.getCharacter().getTattooType();
      if (tattooType != 0) {
         consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture("textures/entity/races/tattoos/tattoo_" + tattooType + ".png")), WHITE_COLOR);
      }
   }

   public void gatherEffectLayers(AbstractClientPlayer player, StatsData stats, float partialTick, BiConsumer<ResourceLocation, float[]> consumer) {
      if (stats.getEffects() != null && stats.getEffects().hasEffect("majin")) {
         consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture("textures/entity/races/majinm.png")), WHITE_COLOR);
      }
   }

   protected void resolveBodyHumanSaiyan(
      Character character, String key, float[] bodyColor, float[] bodyColor2, float[] bodyColor3, BiConsumer<ResourceLocation, float[]> consumer
   ) {
      int bodyType = character.getBodyType();
      String gender = character.getGender().toLowerCase().trim();
      String genderPart = gender.equals("female") ? "_female" : "_male";
      String path = "textures/entity/races/humansaiyan/bodytype" + genderPart + "_" + bodyType + ".png";
      String fallbackPath = "textures/entity/races/humansaiyan/bodytype" + genderPart + "_0.png";
      consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(path), getCachedTexture(fallbackPath)), bodyColor);
   }

   protected void resolveBodyOozaru(float[] bodyColor, float[] bodyColor2, BiConsumer<ResourceLocation, float[]> consumer) {
      String basePath = "textures/entity/races/humansaiyan/oozaru_";
      consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(basePath + "layer1.png"), getCachedTexture(basePath + "layer1.png")), bodyColor2);
      consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(basePath + "layer2.png"), getCachedTexture(basePath + "layer2.png")), bodyColor);
      consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(basePath + "layer3.png"), getCachedTexture(basePath + "layer3.png")), WHITE_COLOR);
   }

   protected void resolveBodyNamekian(Character character, float[] c1, float[] c2, float[] c3, BiConsumer<ResourceLocation, float[]> consumer) {
      int bodyType = character.getBodyType();
      float[] hairColor = character.getRgbHairColor();
      String basePath = "textures/entity/races/namekian/bodytype_" + bodyType + "_";
      String fallbackPath = "textures/entity/races/namekian/bodytype_0_";
      consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(basePath + "layer1.png"), getCachedTexture(fallbackPath + "layer1.png")), c1);
      this.tryLoadOptionalLayerWithFallback(basePath + "layer2.png", fallbackPath + "layer2.png", c2, consumer);
      this.tryLoadOptionalLayerWithFallback(basePath + "layer3.png", fallbackPath + "layer3.png", c3, consumer);
      this.tryLoadOptionalLayerWithFallback(basePath + "layer4.png", fallbackPath + "layer4.png", hairColor, consumer);
   }

   protected void resolveBodyFrostDemon(
      Character character, String key, float[] b1, float[] b2, float[] b3, float[] hair, BiConsumer<ResourceLocation, float[]> consumer
   ) {
      String currentForm = character.getActiveForm();
      int bodyType = character.getBodyType();
      String folder = "textures/entity/races/frostdemon/";
      boolean isSecondForm = Objects.equals(currentForm, "second");
      boolean isBase = currentForm == null || currentForm.isEmpty() || currentForm.equalsIgnoreCase("base");
      boolean isBulky = key.equals("frostdemon") && (isBase || isSecondForm) || key.equals("frostdemon_second") || key.equals("frostdemon_third");
      if (isBulky) {
         String prefix = key.equals("frostdemon_third") ? folder + "thirdform_bodytype_" + bodyType + "_" : folder + "bodytype_" + bodyType + "_";
         String fallbackPrefix = key.equals("frostdemon_third") ? folder + "thirdform_bodytype_0_" : folder + "bodytype_0_";
         consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer1.png"), getCachedTexture(fallbackPrefix + "layer1.png")), b1);
         consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer2.png"), getCachedTexture(fallbackPrefix + "layer2.png")), b2);
         consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer3.png"), getCachedTexture(fallbackPrefix + "layer3.png")), b3);
         consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer4.png"), getCachedTexture(fallbackPrefix + "layer4.png")), hair);
         if (bodyType == 0) {
            consumer.accept(
               DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer5.png"), getCachedTexture(fallbackPrefix + "layer5.png")), DEFAULT_ORANGE_COLOR
            );
         }
      } else {
         String prefix = key.equals("frostdemon_fifth") ? folder + "fifth_bodytype_" + bodyType + "_" : folder + "finalform_bodytype_" + bodyType + "_";
         String fallbackPrefix = key.equals("frostdemon_fifth") ? folder + "fifth_bodytype_0_" : folder + "finalform_bodytype_0_";
         consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer1.png"), getCachedTexture(fallbackPrefix + "layer1.png")), b1);
         consumer.accept(
            DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer2.png"), getCachedTexture(fallbackPrefix + "layer2.png")),
            bodyType != 0 && bodyType != 2 ? b2 : hair
         );
         if (bodyType == 1) {
            consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer3.png"), getCachedTexture(fallbackPrefix + "layer3.png")), b3);
            consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer4.png"), getCachedTexture(fallbackPrefix + "layer4.png")), hair);
         } else if (bodyType == 2) {
            consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer3.png"), getCachedTexture(fallbackPrefix + "layer3.png")), hair);
            consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer2.png"), getCachedTexture(fallbackPrefix + "layer2.png")), b2);
         }
      }

      if (key.equals("frostdemon_mecha")) {
         String var17 = folder + "mechaform_";
         String var20 = folder + "mechaform_";
         consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(var17 + "layer1.png"), getCachedTexture(var20 + "layer1.png")), b1);
      }

      if (key.equals("frostdemon_metalcore")) {
         String var18 = folder + "metalcore_";
         String var21 = folder + "metalcore_";
         this.emitFadingLayer(
            consumer,
            "metalcore_1",
            DMZSkinLayer.getSafeTexture(getCachedTexture(var18 + "layer1.png"), getCachedTexture(var21 + "layer1.png")),
            ColorUtils.hexToRgb("#9BA377")
         );
         this.emitFadingLayer(
            consumer,
            "metalcore_2",
            DMZSkinLayer.getSafeTexture(getCachedTexture(var18 + "layer2.png"), getCachedTexture(var21 + "layer2.png")),
            ColorUtils.hexToRgb("#20211A")
         );
      }
   }

   protected void resolveBodyBioAndroid(
      Character character, String key, float[] b1, float[] b2, float[] b3, float[] hair, BiConsumer<ResourceLocation, float[]> consumer
   ) {
      String phase = switch (key) {
         case "bioandroid_semi" -> "semiperfect";
         case "bioandroid_perfect", "bioandroid_ultra", "bioandroid_xeno" -> "perfect";
         case "bioandroid_base" -> "base";
         case "bioandroid" -> character.hasActiveForm() ? "perfect" : "base";
         default -> "perfect";
      };
      int bodyType = character.getBodyType();
      String currentForm = character.getActiveForm() != null ? character.getActiveForm() : "";
      String formGroup = character.getActiveFormGroup() != null ? character.getActiveFormGroup() : "";
      boolean legendaryGroup = formGroup.equals("legendaryforms");
      String prefix = "textures/entity/races/bioandroid/" + phase + "_" + bodyType + "_";
      String fallbackPrefix = "textures/entity/races/bioandroid/" + phase + "_0_";
      float[] finalBodyColor = b1;
      if (legendaryGroup) {
         finalBodyColor = ColorUtils.darkenColor(b1, 0.4F);
      }

      consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer1.png"), getCachedTexture(fallbackPrefix + "layer1.png")), finalBodyColor);
      consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer2.png"), getCachedTexture(fallbackPrefix + "layer2.png")), b2);
      consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer3.png"), getCachedTexture(fallbackPrefix + "layer3.png")), b3);
      consumer.accept(DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer4.png"), getCachedTexture(fallbackPrefix + "layer4.png")), hair);
      if (!currentForm.equals("xenomax") && !currentForm.equals("xenofp")) {
         consumer.accept(
            DMZSkinLayer.getSafeTexture(getCachedTexture(prefix + "layer5.png"), getCachedTexture(fallbackPrefix + "layer5.png")), DEFAULT_STINGER_COLOR
         );
      }

      if (legendaryGroup) {
         consumer.accept(
            DMZSkinLayer.getSafeTexture(
               getCachedTexture("textures/entity/races/bioandroid/xenoform_layer1.png"),
               getCachedTexture("textures/entity/races/bioandroid/xenoform_layer1.png")
            ),
            ColorUtils.hexToRgb("#FFFFFF")
         );
         if (currentForm.equals("xenomax")) {
            consumer.accept(
               DMZSkinLayer.getSafeTexture(
                  getCachedTexture("textures/entity/races/bioandroid/xenoform_layer2.png"),
                  getCachedTexture("textures/entity/races/bioandroid/xenoform_layer2.png")
               ),
               ColorUtils.hexToRgb("#FFFFFF")
            );
         }
      }
   }

   protected void resolveBodyMajin(Character character, String key, float[] b1, float[] b2, float[] b3, BiConsumer<ResourceLocation, float[]> consumer) {
      if ("janemba_super".equals(key)) {
         String path = "textures/entity/races/majin/janembasuper_0_male_";
         consumer.accept(getCachedTexture(path + "layer1.png"), b1);
         consumer.accept(getCachedTexture(path + "layer2.png"), b2);
         consumer.accept(getCachedTexture(path + "layer3.png"), b3);
      } else if ("janemba_fat".equals(key)) {
         String path = "textures/entity/races/majin/janemba_0_male_";
         consumer.accept(getCachedTexture(path + "layer1.png"), b1);
      } else {
         String currentForm = character.getActiveForm();
         String gender = character.getGender().toLowerCase().trim();
         String genderSuffix = gender.equals("female") ? "female" : "male";
         String phase;
         if (Objects.equals(currentForm, "kid") || key.equals("majin_kid")) {
            phase = "kid";
         } else if (Objects.equals(currentForm, "evil") || key.equals("majin_evil")) {
            phase = "evil";
         } else if (Objects.equals(currentForm, "super") || key.equals("majin_super")) {
            phase = "super";
         } else if (Objects.equals(currentForm, "ultra") || key.equals("majin_ultra")) {
            phase = "ultra";
         } else if (character.hasActiveForm()) {
            phase = "super";
         } else {
            phase = "base";
         }

         int bodyType = character.getBodyType();
         String basePath = "textures/entity/races/majin/bodytype_" + genderSuffix + "_" + bodyType + "_";
         String fallbackPath = "textures/entity/races/majin/bodytype_" + genderSuffix + "_0_";
         ResourceLocation l1 = DMZSkinLayer.getSafeTexture(getCachedTexture(basePath + "layer1.png"), getCachedTexture(fallbackPath + "layer1.png"));
         if (l1 != null) {
            consumer.accept(l1, b1);
         }

         this.tryLoadOptionalLayer(basePath + "layer2.png", b2, consumer);
         this.tryLoadOptionalLayer(basePath + "layer3.png", b3, consumer);
         if (genderSuffix.equals("female") && (phase.equals("super") || phase.equals("ultra"))) {
            ResourceLocation tailLoc = getCachedTexture("textures/entity/races/tail1.png");
            consumer.accept(DMZSkinLayer.getSafeTexture(tailLoc, tailLoc), b1);
         }
      }
   }

   private void tryLoadOptionalLayer(String path, float[] color, BiConsumer<ResourceLocation, float[]> consumer) {
      ResourceLocation loc = getCachedTexture(path);
      if (Minecraft.getInstance().getResourceManager().getResource(loc).isPresent()) {
         consumer.accept(loc, color);
      }
   }

   private void tryLoadOptionalLayerWithFallback(String path, String fallbackPath, float[] color, BiConsumer<ResourceLocation, float[]> consumer) {
      ResourceLocation loc = getCachedTexture(path);
      ResourceLocation fallbackLoc = getCachedTexture(fallbackPath);
      if (Minecraft.getInstance().getResourceManager().getResource(loc).isPresent()) {
         consumer.accept(loc, color);
      } else if (Minecraft.getInstance().getResourceManager().getResource(fallbackLoc).isPresent()) {
         consumer.accept(fallbackLoc, color);
      }
   }

   public interface BodyLayerSink extends BiConsumer<ResourceLocation, float[]> {
      default void accept(ResourceLocation texture, float[] color) {
         this.base(texture, color);
      }

      void base(ResourceLocation var1, float[] var2);

      void fading(String var1, ResourceLocation var2, float[] var3, float var4);

      default void fading(String layerId, ResourceLocation texture, float[] color) {
         this.fading(layerId, texture, color, 1.0F);
      }
   }
}
