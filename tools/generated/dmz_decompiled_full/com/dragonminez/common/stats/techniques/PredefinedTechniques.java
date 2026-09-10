package com.dragonminez.common.stats.techniques;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PredefinedTechniques {
   public static final Map<String, KiAttackData> REGISTRY = new HashMap<>();
   public static final Map<String, StrikeAttackData> STRIKE_REGISTRY = new HashMap<>();
   public static final List<String> STRIKE_IDS = List.of(
      "meteor", "dragon_fist", "deadly_dance_vegetto", "deadly_dance", "kaioken_attack", "wolf_fang", "oozaru_fist", "super_god_fist"
   );

   public void init() {
      this.registerKi(
         "spiritbomb", "technique.dragonminez.spiritbomb", "Goku", KiAttackData.KiType.GIANT_BALL, 3.0F, 3211249, 63743, 5.0F, 0.5F, 10, "ki.large_ball"
      );
      this.registerKi(
         "supernova", "technique.dragonminez.supernova", "Frieza", KiAttackData.KiType.GIANT_BALL, 3.0F, 16750848, 16729088, 5.0F, 0.5F, 10, "ki.large_ball"
      );
      this.registerKi(
         "supernova_cooler",
         "technique.dragonminez.supernova_cooler",
         "Cooler",
         KiAttackData.KiType.GIANT_BALL,
         3.5F,
         16755200,
         16755200,
         5.5F,
         0.5F,
         10,
         "ki.large_ball"
      );
      this.registerKi(
         "big_bang", "technique.dragonminez.big_bang", "Vegeta", KiAttackData.KiType.MEDIUM_BALL, 2.0F, 5240831, 5240831, 2.0F, 1.5F, 10, "ki.bigbang"
      );
      this.registerKi(
         "burning_attack",
         "technique.dragonminez.burning_attack",
         "Trunks",
         KiAttackData.KiType.MEDIUM_BALL,
         1.5F,
         16755200,
         16755200,
         1.5F,
         1.5F,
         10,
         "ki.masenko"
      );
      this.registerKi(
         "sokidan", "technique.dragonminez.sokidan", "Yamcha", KiAttackData.KiType.MEDIUM_BALL, 1.25F, 5220863, 5220863, 1.5F, 1.0F, 12, "ki.bigbang"
      );
      this.registerKi(
         "final_flash", "technique.dragonminez.final_flash", "Vegeta", KiAttackData.KiType.WAVE, 2.5F, 16750848, 16750848, 1.5F, 1.2F, 10, "ki.finalflash"
      );
      this.registerKi("kamehameha", "technique.dragonminez.kamehameha", "Goku", KiAttackData.KiType.WAVE, 2.0F, 5240831, 5240831, 1.0F, 1.2F, 10, "ki.kameha");
      this.registerKi(
         "galick_gun", "technique.dragonminez.galick_gun", "Vegeta", KiAttackData.KiType.WAVE, 2.0F, 13504739, 11407587, 1.0F, 1.2F, 10, "ki.galick"
      );
      this.registerKi("masenko", "technique.dragonminez.masenko", "Gohan", KiAttackData.KiType.WAVE, 1.5F, 16771584, 16771584, 1.0F, 1.2F, 10, "ki.masenko");
      this.registerKi("kienzan", "technique.dragonminez.kienzan", "Krilin", KiAttackData.KiType.DISK, 1.5F, 16771584, 16771584, 1.0F, 1.5F, 10, "ki.kienzan");
      this.registerKi(
         "kienzan_doble",
         "technique.dragonminez.double_kienzan",
         "Krilin",
         KiAttackData.KiType.DISK,
         1.75F,
         16711850,
         16711850,
         1.0F,
         1.5F,
         10,
         "ki.kienzandoble"
      );
      this.registerKi(
         "death_beam", "technique.dragonminez.death_beam", "Frieza", KiAttackData.KiType.LASER, 0.75F, 13504739, 13504739, 0.5F, 2.0F, 10, "ki.makkako"
      );
      this.registerKi(
         "emperor_death_beam",
         "technique.dragonminez.emperor_death_beam",
         "Frieza",
         KiAttackData.KiType.LASER,
         1.25F,
         13504739,
         13504739,
         0.6F,
         2.0F,
         10,
         "ki.makkako"
      );
      this.registerKi(
         "makkanko", "technique.dragonminez.makkankosanpo", "Piccolo", KiAttackData.KiType.BEAM, 0.75F, 16770363, 12860415, 0.8F, 2.0F, 10, "ki.makkako"
      );
      this.registerKi(
         "ki_barrage", "technique.dragonminez.barrage", "Vegeta", KiAttackData.KiType.BARRAGE, 1.0F, 16776960, 16776960, 0.4F, 1.5F, 10, "ki.barrage"
      );
      this.registerKi(
         "final_explosion",
         "technique.dragonminez.final_explosion",
         "Vegeta",
         KiAttackData.KiType.EXPLOSION,
         2.25F,
         16776960,
         16776960,
         15.0F,
         0.0F,
         10,
         "ki.explosion"
      );
      this.registerKi(
         "soul_punisher",
         "technique.dragonminez.soul_punisher",
         "Gogeta",
         KiAttackData.KiType.MEDIUM_BALL,
         3.5F,
         16777215,
         16777215,
         5.0F,
         0.5F,
         45,
         "ki.kienzan"
      );
      this.registerKi(
         "fake_moon", "technique.dragonminez.fake_moon", "Vegeta", KiAttackData.KiType.MEDIUM_BALL, 0.0F, 16118736, 16777215, 2.0F, 0.8F, 45, "ki.bigbang"
      );
      this.registerKi(
         "taiyoken", "technique.dragonminez.taiyoken", "Tenshinhan", KiAttackData.KiType.SMALL_BALL, 0.0F, 16777215, 16777215, 1.0F, 0.1F, 45, "ki.bigbang"
      );
      this.registerStrike("skp.meteor", 1.25F, 40);
      this.registerStrike("skp.dragon_fist", 2.5F, 50);
      this.registerStrike("skp.deadly_dance_vegetto", 1.5F, 40);
      this.registerStrike("skp.deadly_dance", 1.25F, 40);
      this.registerStrike("skp.kaioken_attack", 1.75F, 45);
      this.registerStrike("skp.wolf_fang", 1.25F, 35);
      this.registerStrike("skp.oozaru_fist", 2.25F, 35);
      this.registerStrike("skp.super_god_fist", 2.0F, 25);
   }

   public static boolean isPredefinedTechniqueId(String techniqueId) {
      return techniqueId != null && REGISTRY.containsKey(techniqueId);
   }

   public static boolean isPredefinedTechnique(TechniqueData technique) {
      return technique != null && isPredefinedTechniqueId(technique.getId());
   }

   private void registerKi(
      String id,
      String name,
      String author,
      KiAttackData.KiType type,
      float dmgMult,
      int colorIn,
      int colorOut,
      float size,
      float speed,
      int cooldownSeconds,
      String animPrefix
   ) {
      KiAttackData data = new KiAttackData();
      data.setId(id);
      data.setName(name);
      data.setAuthor(author);
      data.setKiType(type);
      data.setUtility(KiAttackData.Utility.DAMAGE);
      data.setDamageMultiplier(dmgMult);
      data.setColorInterior(colorIn);
      data.setColorExterior(colorOut);
      data.setSize(size);
      data.setSpeed(speed);
      data.setArmorPenetration(0);
      data.getAllowedRaces().add("ALL");
      data.setAnimation(animPrefix);
      data.setCastTime(100);
      data.setCooldown(cooldownSeconds);
      data.calculateDerivedValues();
      REGISTRY.put(id, data);
   }

   private void registerStrike(String animationId, float damageMultiplier, int durationTicks) {
      String id = animationId != null && animationId.startsWith("skp.") ? animationId.substring(4) : animationId;
      StrikeAttackData data = new StrikeAttackData();
      data.setId(id);
      data.setName("technique.dragonminez." + id);
      data.setAuthor("System");
      data.setDamageMultiplier(damageMultiplier);
      data.setAnimationId(animationId);
      data.setDurationTicks(durationTicks);
      data.applyConfigDefaults();
      STRIKE_REGISTRY.put(id, data);
   }
}
