package com.dragonminez.common.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Generated;

public class SkillsConfig {
   public static final String CURRENT_VERSION = "2.1.3";
   private String configVersion;
   @ConfigNonPreservable
   private final List<String> kiSkills = new ArrayList<>();
   @ConfigNonPreservable
   private final List<String> formSkills = new ArrayList<>();
   @ConfigNonPreservable
   private final List<String> stackSkills = new ArrayList<>();
   @ConfigNonPreservable
   private final List<String> androidBlacklistedForms = new ArrayList<>();
   private final Map<String, SkillsConfig.SkillCosts> skills = new HashMap<>();
   private final Map<String, List<String>> skillOfferings = new HashMap<>();
   @ConfigNonPreservable
   private final List<String> strikeSkills = new ArrayList<>();

   public SkillsConfig() {
      this.createDefaults();
   }

   private void createDefaults() {
      this.formSkills.add("superforms");
      this.formSkills.add("legendaryforms");
      this.formSkills.add("godforms");
      this.formSkills.add("androidforms");
      this.stackSkills.add("kaioken");
      this.stackSkills.add("ultimate");
      this.androidBlacklistedForms.add("superforms");
      this.androidBlacklistedForms.add("legendaryforms");
      this.kiSkills.add("spiritbomb");
      this.kiSkills.add("supernova");
      this.kiSkills.add("supernova_cooler");
      this.kiSkills.add("big_bang");
      this.kiSkills.add("burning_attack");
      this.kiSkills.add("sokidan");
      this.kiSkills.add("final_flash");
      this.kiSkills.add("kamehameha");
      this.kiSkills.add("galick_gun");
      this.kiSkills.add("masenko");
      this.kiSkills.add("kienzan");
      this.kiSkills.add("kienzan_doble");
      this.kiSkills.add("death_beam");
      this.kiSkills.add("makkanko");
      this.kiSkills.add("emperor_death_beam");
      this.kiSkills.add("ki_barrage");
      this.kiSkills.add("final_explosion");
      this.kiSkills.add("soul_punisher");
      this.kiSkills.add("fake_moon");
      this.kiSkills.add("taiyoken");
      this.skills.put("ki_barrage", new SkillsConfig.SkillCosts(List.of(1000)));
      this.skills.put("masenko", new SkillsConfig.SkillCosts(List.of(1500)));
      this.skills.put("kamehameha", new SkillsConfig.SkillCosts(List.of(2000)));
      this.skills.put("galick_gun", new SkillsConfig.SkillCosts(List.of(2000)));
      this.skills.put("taiyoken", new SkillsConfig.SkillCosts(List.of(2000)));
      this.skills.put("death_beam", new SkillsConfig.SkillCosts(List.of(2500)));
      this.skills.put("fake_moon", new SkillsConfig.SkillCosts(List.of(3000)));
      this.skills.put("kienzan", new SkillsConfig.SkillCosts(List.of(3000)));
      this.skills.put("makkanko", new SkillsConfig.SkillCosts(List.of(3500)));
      this.skills.put("burning_attack", new SkillsConfig.SkillCosts(List.of(3500)));
      this.skills.put("big_bang", new SkillsConfig.SkillCosts(List.of(4000)));
      this.skills.put("sokidan", new SkillsConfig.SkillCosts(List.of(4000)));
      this.skills.put("kienzan_doble", new SkillsConfig.SkillCosts(List.of(4000)));
      this.skills.put("emperor_death_beam", new SkillsConfig.SkillCosts(List.of(5000)));
      this.skills.put("final_flash", new SkillsConfig.SkillCosts(List.of(5000)));
      this.skills.put("spiritbomb", new SkillsConfig.SkillCosts(List.of(10000)));
      this.skills.put("supernova", new SkillsConfig.SkillCosts(List.of(12000)));
      this.skills.put("supernova_cooler", new SkillsConfig.SkillCosts(List.of(15000)));
      this.skills.put("final_explosion", new SkillsConfig.SkillCosts(List.of(20000)));
      this.skills.put("soul_punisher", new SkillsConfig.SkillCosts(List.of(25000)));
      this.strikeSkills.add("meteor");
      this.strikeSkills.add("dragon_fist");
      this.strikeSkills.add("deadly_dance_vegetto");
      this.strikeSkills.add("deadly_dance");
      this.strikeSkills.add("kaioken_attack");
      this.strikeSkills.add("wolf_fang");
      this.strikeSkills.add("oozaru_fist");
      this.strikeSkills.add("super_god_fist");
      this.skills.put("meteor", new SkillsConfig.SkillCosts(List.of(3000)));
      this.skills.put("wolf_fang", new SkillsConfig.SkillCosts(List.of(3500)));
      this.skills.put("kaioken_attack", new SkillsConfig.SkillCosts(List.of(5000)));
      this.skills.put("dragon_fist", new SkillsConfig.SkillCosts(List.of(6000)));
      this.skills.put("deadly_dance", new SkillsConfig.SkillCosts(List.of(8000)));
      this.skills.put("super_god_fist", new SkillsConfig.SkillCosts(List.of(10000)));
      this.skills.put("deadly_dance_vegetto", new SkillsConfig.SkillCosts(List.of(12000)));
      this.skills.put("oozaru_fist", new SkillsConfig.SkillCosts(List.of(15000)));
      List<Integer> jumpCosts = new ArrayList<>();
      jumpCosts.add(300);
      jumpCosts.add(600);
      jumpCosts.add(900);
      jumpCosts.add(1200);
      jumpCosts.add(1500);
      jumpCosts.add(1800);
      jumpCosts.add(2100);
      jumpCosts.add(2400);
      jumpCosts.add(2700);
      jumpCosts.add(3000);
      this.skills.put("jump", new SkillsConfig.SkillCosts(jumpCosts));
      List<Integer> sprintCosts = new ArrayList<>();
      sprintCosts.add(300);
      sprintCosts.add(600);
      sprintCosts.add(900);
      sprintCosts.add(1200);
      sprintCosts.add(1500);
      sprintCosts.add(1800);
      sprintCosts.add(2100);
      sprintCosts.add(2400);
      sprintCosts.add(2700);
      sprintCosts.add(3000);
      this.skills.put("sprint", new SkillsConfig.SkillCosts(sprintCosts));
      List<Integer> flyCosts = new ArrayList<>();
      flyCosts.add(1500);
      flyCosts.add(600);
      flyCosts.add(900);
      flyCosts.add(1200);
      flyCosts.add(1500);
      flyCosts.add(1800);
      flyCosts.add(2100);
      flyCosts.add(2400);
      flyCosts.add(2700);
      flyCosts.add(3000);
      this.skills.put("fly", new SkillsConfig.SkillCosts(flyCosts));
      List<Integer> meditationCosts = new ArrayList<>();
      meditationCosts.add(300);
      meditationCosts.add(600);
      meditationCosts.add(900);
      meditationCosts.add(1200);
      meditationCosts.add(1500);
      meditationCosts.add(1800);
      meditationCosts.add(2100);
      meditationCosts.add(2400);
      meditationCosts.add(2700);
      meditationCosts.add(3000);
      this.skills.put("meditation", new SkillsConfig.SkillCosts(meditationCosts));
      List<Integer> kiSenseCosts = new ArrayList<>();
      kiSenseCosts.add(300);
      kiSenseCosts.add(600);
      kiSenseCosts.add(900);
      kiSenseCosts.add(1200);
      kiSenseCosts.add(1500);
      kiSenseCosts.add(1800);
      kiSenseCosts.add(2100);
      kiSenseCosts.add(2400);
      kiSenseCosts.add(2700);
      kiSenseCosts.add(3000);
      this.skills.put("kisense", new SkillsConfig.SkillCosts(kiSenseCosts));
      List<Integer> potentialUnlockCosts = new ArrayList<>();
      potentialUnlockCosts.add(600);
      potentialUnlockCosts.add(1600);
      potentialUnlockCosts.add(2400);
      potentialUnlockCosts.add(3200);
      potentialUnlockCosts.add(4000);
      potentialUnlockCosts.add(4800);
      potentialUnlockCosts.add(5600);
      potentialUnlockCosts.add(6400);
      potentialUnlockCosts.add(7200);
      potentialUnlockCosts.add(8000);
      potentialUnlockCosts.add(-1);
      potentialUnlockCosts.add(8800);
      potentialUnlockCosts.add(9600);
      this.skills.put("potentialunlock", new SkillsConfig.SkillCosts(potentialUnlockCosts));
      this.skills.put("kicontrol", new SkillsConfig.SkillCosts(List.of(3000)));
      List<Integer> kiBoostCosts = new ArrayList<>();
      kiBoostCosts.add(2000);
      kiBoostCosts.add(4000);
      kiBoostCosts.add(6000);
      kiBoostCosts.add(8000);
      this.skills.put("kiboost", new SkillsConfig.SkillCosts(kiBoostCosts));
      List<Integer> kiManipulationCosts = new ArrayList<>();
      kiManipulationCosts.add(600);
      kiManipulationCosts.add(1200);
      kiManipulationCosts.add(1800);
      kiManipulationCosts.add(2200);
      kiManipulationCosts.add(2600);
      kiManipulationCosts.add(2800);
      kiManipulationCosts.add(3000);
      kiManipulationCosts.add(3200);
      kiManipulationCosts.add(3600);
      kiManipulationCosts.add(4000);
      this.skills.put("kimanipulation", new SkillsConfig.SkillCosts(kiManipulationCosts));
      List<Integer> instantTransmission = new ArrayList<>();
      instantTransmission.add(600);
      instantTransmission.add(1200);
      instantTransmission.add(1800);
      instantTransmission.add(2200);
      instantTransmission.add(2600);
      instantTransmission.add(2800);
      instantTransmission.add(3000);
      instantTransmission.add(3200);
      instantTransmission.add(3600);
      instantTransmission.add(4000);
      this.skills.put("instant_transmission", new SkillsConfig.SkillCosts(instantTransmission));
      List<Integer> defensePenetration = new ArrayList<>();
      defensePenetration.add(600);
      defensePenetration.add(1200);
      defensePenetration.add(1800);
      defensePenetration.add(2200);
      defensePenetration.add(2600);
      defensePenetration.add(2800);
      defensePenetration.add(3000);
      defensePenetration.add(3200);
      defensePenetration.add(3600);
      defensePenetration.add(4000);
      this.skills.put("defense_penetration", new SkillsConfig.SkillCosts(defensePenetration));
      List<Integer> healingReduction = new ArrayList<>();
      healingReduction.add(600);
      healingReduction.add(1200);
      healingReduction.add(1800);
      healingReduction.add(2200);
      healingReduction.add(2600);
      healingReduction.add(2800);
      healingReduction.add(3000);
      healingReduction.add(3200);
      healingReduction.add(3600);
      healingReduction.add(4000);
      this.skills.put("healing_reduction", new SkillsConfig.SkillCosts(healingReduction));
      List<Integer> kiInfusion = new ArrayList<>();
      kiInfusion.add(600);
      kiInfusion.add(1200);
      kiInfusion.add(1800);
      kiInfusion.add(2200);
      kiInfusion.add(2600);
      kiInfusion.add(2800);
      kiInfusion.add(3000);
      kiInfusion.add(3200);
      kiInfusion.add(3600);
      kiInfusion.add(4000);
      this.skills.put("ki_infusion", new SkillsConfig.SkillCosts(kiInfusion));
      List<Integer> kiProtection = new ArrayList<>();
      kiProtection.add(600);
      kiProtection.add(1200);
      kiProtection.add(1800);
      kiProtection.add(2200);
      kiProtection.add(2600);
      kiProtection.add(2800);
      kiProtection.add(3000);
      kiProtection.add(3200);
      kiProtection.add(3600);
      kiProtection.add(4000);
      this.skills.put("kiprotection", new SkillsConfig.SkillCosts(kiProtection));
      List<Integer> kaiokenCosts = new ArrayList<>();
      kaiokenCosts.add(1000);
      kaiokenCosts.add(1500);
      kaiokenCosts.add(2500);
      kaiokenCosts.add(4000);
      kaiokenCosts.add(7500);
      this.skills.put("kaioken", new SkillsConfig.SkillCosts(kaiokenCosts));
      this.skills.put("ultimate", new SkillsConfig.SkillCosts(List.of(-1)));
      List<Integer> fusionCosts = new ArrayList<>();
      fusionCosts.add(25000);
      fusionCosts.add(5000);
      fusionCosts.add(10000);
      fusionCosts.add(15000);
      fusionCosts.add(20000);
      this.skills.put("fusion", new SkillsConfig.SkillCosts(fusionCosts));
      List<String> roshiSkills = new ArrayList<>();
      roshiSkills.add("jump");
      roshiSkills.add("meditation");
      roshiSkills.add("kicontrol");
      roshiSkills.add("kamehameha");
      this.skillOfferings.put("roshi", roshiSkills);
      List<String> gokuSkills = new ArrayList<>();
      gokuSkills.add("fly");
      gokuSkills.add("instant_transmission");
      gokuSkills.add("fusion");
      gokuSkills.add("kamehameha");
      gokuSkills.add("spiritbomb");
      gokuSkills.add("dragon_fist");
      gokuSkills.add("super_god_fist");
      gokuSkills.add("oozaru_fist");
      this.skillOfferings.put("goku", gokuSkills);
      List<String> kingKaiSkills = new ArrayList<>();
      kingKaiSkills.add("kaioken");
      kingKaiSkills.add("potentialunlock");
      kingKaiSkills.add("kimanipulation");
      kingKaiSkills.add("kaioken_attack");
      kingKaiSkills.add("spiritbomb");
      this.skillOfferings.put("kingkai", kingKaiSkills);
      List<String> vegetaSkills = new ArrayList<>();
      vegetaSkills.add("defense_penetration");
      vegetaSkills.add("potentialunlock");
      vegetaSkills.add("galick_gun");
      vegetaSkills.add("big_bang");
      vegetaSkills.add("final_flash");
      vegetaSkills.add("final_explosion");
      vegetaSkills.add("fake_moon");
      vegetaSkills.add("deadly_dance_vegetto");
      this.skillOfferings.put("vegeta", vegetaSkills);
      List<String> oldKaiSkills = new ArrayList<>();
      oldKaiSkills.add("ki_infusion");
      oldKaiSkills.add("healing_reduction");
      oldKaiSkills.add("soul_punisher");
      this.skillOfferings.put("oldkai", oldKaiSkills);
      List<String> gohanSkills = new ArrayList<>();
      gohanSkills.add("kiboost");
      gohanSkills.add("kiprotection");
      gohanSkills.add("kisense");
      gohanSkills.add("masenko");
      gohanSkills.add("makkanko");
      gohanSkills.add("kamehameha");
      gohanSkills.add("ki_barrage");
      this.skillOfferings.put("gohan", gohanSkills);
      List<String> piccoloSkills = new ArrayList<>();
      piccoloSkills.add("potentialunlock");
      piccoloSkills.add("kicontrol");
      piccoloSkills.add("masenko");
      piccoloSkills.add("makkanko");
      piccoloSkills.add("ki_barrage");
      piccoloSkills.add("kienzan");
      this.skillOfferings.put("piccolo", piccoloSkills);
      List<String> krillinSkills = new ArrayList<>();
      krillinSkills.add("sprint");
      krillinSkills.add("kisense");
      krillinSkills.add("kienzan");
      krillinSkills.add("kienzan_doble");
      krillinSkills.add("ki_barrage");
      krillinSkills.add("taiyoken");
      krillinSkills.add("deadly_dance");
      this.skillOfferings.put("krillin", krillinSkills);
      List<String> friezaSkills = new ArrayList<>();
      friezaSkills.add("fly");
      friezaSkills.add("jump");
      friezaSkills.add("sprint");
      friezaSkills.add("kisense");
      friezaSkills.add("meditation");
      friezaSkills.add("potentialunlock");
      friezaSkills.add("instant_transmission");
      friezaSkills.add("death_beam");
      friezaSkills.add("emperor_death_beam");
      friezaSkills.add("supernova");
      friezaSkills.add("kienzan_doble");
      friezaSkills.add("deadly_dance");
      friezaSkills.add("meteor");
      this.skillOfferings.put("frieza", friezaSkills);
      List<String> trunksSkills = new ArrayList<>();
      trunksSkills.add("kiboost");
      trunksSkills.add("kiprotection");
      trunksSkills.add("burning_attack");
      trunksSkills.add("galick_gun");
      trunksSkills.add("ki_barrage");
      trunksSkills.add("meteor");
      this.skillOfferings.put("trunks", trunksSkills);
      List<String> cellSkills = new ArrayList<>();
      cellSkills.add("kicontrol");
      cellSkills.add("kimanipulation");
      cellSkills.add("ki_infusion");
      cellSkills.add("kiprotection");
      cellSkills.add("defense_penetration");
      cellSkills.add("healing_reduction");
      cellSkills.add("kiboost");
      cellSkills.add("kamehameha");
      cellSkills.add("kienzan");
      cellSkills.add("galick_gun");
      cellSkills.add("masenko");
      cellSkills.add("death_beam");
      cellSkills.add("ki_barrage");
      cellSkills.add("deadly_dance");
      cellSkills.add("meteor");
      this.skillOfferings.put("cell", cellSkills);
      List<String> yamchaSkills = new ArrayList<>();
      yamchaSkills.add("kicontrol");
      yamchaSkills.add("ki_infusion");
      yamchaSkills.add("wolf_fang");
      yamchaSkills.add("sokidan");
      yamchaSkills.add("kamehameha");
      yamchaSkills.add("ki_barrage");
      this.skillOfferings.put("yamcha", yamchaSkills);
      List<String> defaultSkills = new ArrayList<>();
      defaultSkills.add("jump");
      this.skillOfferings.put("default", defaultSkills);
   }

   public SkillsConfig.SkillCosts getSkillCosts(String skillName) {
      return this.skills.getOrDefault(skillName.toLowerCase(), new SkillsConfig.SkillCosts(new ArrayList<>(), new ArrayList<>()));
   }

   public boolean isSkillAllowedForRace(String skillName, String raceName) {
      if (skillName != null && !skillName.isEmpty()) {
         SkillsConfig.SkillCosts skillCosts = this.getSkillCosts(skillName);
         if (skillCosts == null || skillCosts.getAllowedRaces() == null || skillCosts.getAllowedRaces().isEmpty()) {
            return true;
         } else if (raceName != null && !raceName.isEmpty()) {
            String normalizedRace = raceName.toLowerCase();

            for (String allowedRace : skillCosts.getAllowedRaces()) {
               if (allowedRace != null && !allowedRace.isEmpty()) {
                  String normalizedAllowed = allowedRace.toLowerCase();
                  if (normalizedAllowed.equals("all") || normalizedAllowed.equals(normalizedRace)) {
                     return true;
                  }
               }
            }

            return false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @Generated
   public String getConfigVersion() {
      return this.configVersion;
   }

   @Generated
   public List<String> getKiSkills() {
      return this.kiSkills;
   }

   @Generated
   public List<String> getFormSkills() {
      return this.formSkills;
   }

   @Generated
   public List<String> getStackSkills() {
      return this.stackSkills;
   }

   @Generated
   public List<String> getAndroidBlacklistedForms() {
      return this.androidBlacklistedForms;
   }

   @Generated
   public Map<String, SkillsConfig.SkillCosts> getSkills() {
      return this.skills;
   }

   @Generated
   public Map<String, List<String>> getSkillOfferings() {
      return this.skillOfferings;
   }

   @Generated
   public List<String> getStrikeSkills() {
      return this.strikeSkills;
   }

   @Generated
   public void setConfigVersion(String configVersion) {
      this.configVersion = configVersion;
   }

   public static class SkillCosts {
      private List<Integer> costs = new ArrayList<>();
      private List<String> allowedRaces = new ArrayList<>();

      public SkillCosts(List<Integer> costs) {
         this.costs = costs;
         this.allowedRaces = new ArrayList<>();
      }

      @Generated
      public List<Integer> getCosts() {
         return this.costs;
      }

      @Generated
      public List<String> getAllowedRaces() {
         return this.allowedRaces;
      }

      @Generated
      public void setCosts(List<Integer> costs) {
         this.costs = costs;
      }

      @Generated
      public void setAllowedRaces(List<String> allowedRaces) {
         this.allowedRaces = allowedRaces;
      }

      @Generated
      public SkillCosts() {
      }

      @Generated
      public SkillCosts(List<Integer> costs, List<String> allowedRaces) {
         this.costs = costs;
         this.allowedRaces = allowedRaces;
      }
   }
}
