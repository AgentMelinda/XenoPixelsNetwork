package com.dragonminez.common.stats.techniques;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.TechniqueConfig;
import com.dragonminez.common.stats.StatsData;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.util.Mth;

public class KiAttackData extends TechniqueData {
   private static final int MAX_IMPORT_NBT_BYTES = 65536;
   private static final int MAX_IMPORT_CODE_LENGTH = 16384;
   public static final int MIN_SECONDARY_INTENSITY = 5;
   public static final int MAX_SECONDARY_INTENSITY = 50;
   public static final int MIN_SECONDARY_DURATION = 1;
   public static final int MAX_SECONDARY_DURATION = 8;
   public static final float SECONDARY_COST_FACTOR = 0.25F;
   private static final String CODE_PREFIX = "DMZK1:";
   private static final String BASE62_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
   private List<String> allowedRaces = new ArrayList<>();
   private KiAttackData.KiType kiType;
   private String animation;
   private KiAttackData.Utility utility;
   private int colorInterior;
   private int colorExterior;
   private int colorOutline;
   private float damageMultiplier;
   private float speed;
   private float size;
   private int armorPenetration;
   private KiAttackData.SecondaryEffectType secondaryEffectType = KiAttackData.SecondaryEffectType.NONE;
   private KiAttackData.AffectedStat affectedStat;
   private float secondaryIntensity;
   private int secondaryDuration;
   private int damageLevel = 0;
   private int castTimeLevel = 0;
   private int cooldownLevel = 0;
   private int speedLevel = 0;
   private int sizeLevel = 0;
   private int armorPenLevel = 0;
   public static final int KI_TIME_MULTIPLIER = 20;
   public static final float HEAL_OUTPUT_FACTOR = 0.4F;
   public static final float COOLDOWN_LEVEL_REDUCTION = 0.025F;
   public static final float COOLDOWN_MAX_REDUCTION = 0.5F;
   public static final int OVERCHARGE_MAX_PERCENT = 175;
   public static final int OVERCHARGE_TIER_PERCENT = 25;
   private static final float MAX_DAMAGE_MULT = 2.5F;
   private static final float LARGE_OVERLOAD_KI_FACTOR = 2.0F;

   @Override
   public TechniqueType getType() {
      return TechniqueType.KI_ATTACK;
   }

   private static float getTypeMultiplier(KiAttackData.KiType type) {
      return switch (type) {
         case SMALL_BALL -> 1.0F;
         case MEDIUM_BALL -> 1.4F;
         case GIANT_BALL -> 2.2F;
         case WAVE -> 1.6F;
         case LASER -> 0.8F;
         case BEAM -> 1.4F;
         case DISK -> 1.2F;
         case EXPLOSION -> 2.6F;
         case SHIELD -> 1.6F;
         case BARRAGE -> 1.4F;
         case AREA -> 1.8F;
      };
   }

   public String getAnimationPrefix() {
      if (this.animation != null && !this.animation.isEmpty()) {
         return this.animation;
      } else {
         if (PredefinedTechniques.isPredefinedTechniqueId(this.id)) {
            KiAttackData predefined = PredefinedTechniques.REGISTRY.get(this.id);
            if (predefined != null && predefined.getAnimation() != null && !predefined.getAnimation().isEmpty()) {
               return predefined.getAnimation();
            }
         }
         return switch (this.kiType != null ? this.kiType : KiAttackData.KiType.SMALL_BALL) {
            case SMALL_BALL, MEDIUM_BALL -> "ki.bigbang";
            case GIANT_BALL -> "ki.large_ball";
            case WAVE -> "ki.kameha";
            case LASER, BEAM -> "ki.makkako";
            case DISK -> "ki.kienzan";
            case EXPLOSION, SHIELD -> "ki.explosion";
            case BARRAGE -> "ki.barrage";
            default -> "ki.kameha";
         };
      }
   }

   private static float getUtilityMultiplier(KiAttackData.Utility util) {
      return switch (util) {
         case DAMAGE -> 1.0F;
         case HEAL -> 1.25F;
      };
   }

   public static boolean allowsHealUtility(KiAttackData.KiType type) {
      return type == KiAttackData.KiType.AREA || type == KiAttackData.KiType.SMALL_BALL || type == KiAttackData.KiType.SHIELD;
   }

   public KiAttackData.Utility getEffectiveUtility() {
      KiAttackData.Utility u = this.utility != null ? this.utility : KiAttackData.Utility.DAMAGE;
      return u == KiAttackData.Utility.HEAL && !allowsHealUtility(this.kiType != null ? this.kiType : KiAttackData.KiType.SMALL_BALL)
         ? KiAttackData.Utility.DAMAGE
         : u;
   }

   public float getOutputMultiplier() {
      return this.getEffectiveUtility() == KiAttackData.Utility.HEAL ? 0.4F : 1.0F;
   }

   public float getActualDamageMultiplier() {
      return this.damageMultiplier;
   }

   public float getActualSpeed() {
      return this.speed;
   }

   public float getActualSize() {
      return this.size;
   }

   public int getActualArmorPenetration() {
      return Math.min(100, this.armorPenetration);
   }

   public int getActualCastTime() {
      return this.getBaseChargeTicks();
   }

   public float getCooldownLevelMultiplier() {
      return Math.max(0.5F, 1.0F - (float)Math.max(0, this.cooldownLevel) * 0.025F);
   }

   public int getActualCooldown() {
      return Math.round((float)(this.cooldown * 20) * this.getCooldownLevelMultiplier());
   }

   public boolean isInstantCast() {
      return this.kiType == KiAttackData.KiType.SMALL_BALL || this.kiType == KiAttackData.KiType.LASER;
   }

   public int getBaseChargeTicks() {
      if (this.isInstantCast()) {
         return 0;
      } else {
         int configured = ConfigManager.getTechniqueConfig()
            .getKiTypeConfig(this.kiType != null ? this.kiType : KiAttackData.KiType.SMALL_BALL)
            .getCastTimeTicks();
         return Math.max(0, configured);
      }
   }

   public static float costMultiplier(float percent) {
      return percent <= 100.0F ? Math.max(0.0F, percent) / 100.0F : 1.0F + (percent - 100.0F) / 75.0F;
   }

   public void setSecondaryIntensity(float value) {
      this.secondaryIntensity = value <= 0.0F ? 0.0F : Mth.clamp(value, 5.0F, 50.0F);
   }

   public void setSecondaryDuration(int value) {
      this.secondaryDuration = value <= 0 ? 0 : Mth.clamp(value, 1, 8);
   }

   public boolean hasValidSecondaryEffect() {
      if (this.secondaryEffectType == null || this.secondaryEffectType == KiAttackData.SecondaryEffectType.NONE) {
         return false;
      } else if (this.affectedStat == null) {
         return false;
      } else if (!(this.secondaryIntensity <= 0.0F) && this.secondaryDuration > 0) {
         KiAttackData.Utility u = this.utility != null ? this.utility : KiAttackData.Utility.DAMAGE;
         return this.secondaryEffectType == KiAttackData.SecondaryEffectType.BUFF && u == KiAttackData.Utility.HEAL
            || this.secondaryEffectType == KiAttackData.SecondaryEffectType.DEBUFF && u == KiAttackData.Utility.DAMAGE;
      } else {
         return false;
      }
   }

   public float secondaryCostWeight() {
      return secondaryCostWeight(
         this.hasValidSecondaryEffect() ? this.secondaryEffectType : KiAttackData.SecondaryEffectType.NONE, this.secondaryIntensity, this.secondaryDuration
      );
   }

   private static float secondaryCostWeight(KiAttackData.SecondaryEffectType type, float intensity, int duration) {
      if (type != null && type != KiAttackData.SecondaryEffectType.NONE) {
         float intensityNorm = Mth.clamp((intensity - 5.0F) / 45.0F, 0.0F, 1.0F);
         float durationNorm = Mth.clamp((float)(duration - 1) / 7.0F, 0.0F, 1.0F);
         return intensityNorm * 0.6F + durationNorm * 0.4F;
      } else {
         return 0.0F;
      }
   }

   public float secondaryCostMultiplier() {
      return 1.0F + 0.25F * this.secondaryCostWeight();
   }

   @Override
   public double getCalculatedCost(StatsData statsData) {
      double damageDone = statsData.getKiDamageNoForms() * (double)this.getActualDamageMultiplier();
      double complexityFactor = (double)this.getActualSize() * 5.0 + (double)this.getActualSpeed() * 5.0 + (double)this.getActualArmorPenetration() * 0.2;
      float typeMult = getTypeMultiplier(this.kiType != null ? this.kiType : KiAttackData.KiType.SMALL_BALL);
      float utilMult = getUtilityMultiplier(this.utility != null ? this.utility : KiAttackData.Utility.DAMAGE);
      TechniqueConfig.TechniqueTypeConfig cfg = ConfigManager.getTechniqueConfig()
         .getKiTypeConfig(this.kiType != null ? this.kiType : KiAttackData.KiType.SMALL_BALL);
      double configCostMult = Math.max(0.0, cfg.getKiCostMultiplier());
      KiAttackData.KiType resolvedType = this.kiType != null ? this.kiType : KiAttackData.KiType.SMALL_BALL;
      double overload = (double)largeOverloadKiMultiplier(resolvedType, this.damageMultiplier);
      return Math.max(
         5.0,
         (damageDone * 0.5 + complexityFactor) * (double)typeMult * (double)utilMult * configCostMult * (double)this.secondaryCostMultiplier() * overload / 2.0
      );
   }

   public int getUpgradeXpCost(String statName) {
      TechniqueConfig.TechniqueTypeConfig cfg = ConfigManager.getTechniqueConfig()
         .getKiTypeConfig(this.kiType != null ? this.kiType : KiAttackData.KiType.SMALL_BALL);
      int baseMin = Math.max(0, cfg.getMinXPCost());
      double multiplier = Math.max(0.0, cfg.getXpCostMultiplier());
      float initialDamage = Math.max(0.0F, this.damageMultiplier - (float)this.damageLevel * 0.05F);
      float initialSize = Math.max(0.0F, this.size - (float)this.sizeLevel * 0.1F);
      float initialSpeed = Math.max(0.0F, this.speed - (float)this.speedLevel * 0.05F);
      int initialArmorPen = Math.max(0, this.armorPenetration - this.armorPenLevel);
      KiAttackData.KiType resolvedType = this.kiType != null ? this.kiType : KiAttackData.KiType.SMALL_BALL;
      float initialComplexity = getWeightedComplexity(initialDamage, sizeComplexityRatio(resolvedType, initialSize), initialSpeed, initialArmorPen);
      int totalUpgrades = this.getTotalUpgradeCount();
      int complexityBase = baseMin + (int)Math.round((double)initialComplexity * 10.0);
      int scaledBase = (int)Math.round((double)complexityBase * multiplier);
      int upgradeExtra = (int)Math.round((double)totalUpgrades * Math.max(0.0, (double)complexityBase * (multiplier - 1.0)));
      int computed = Math.max(0, scaledBase + upgradeExtra);
      int max = cfg.getMaxXPCost();
      if (max >= 0) {
         computed = Math.min(computed, max);
      }

      return Math.max(0, computed);
   }

   public int getXpGainPerHit() {
      TechniqueConfig.TechniqueTypeConfig cfg = ConfigManager.getTechniqueConfig()
         .getKiTypeConfig(this.kiType != null ? this.kiType : KiAttackData.KiType.SMALL_BALL);
      double gain = Math.max(0.0, (double)cfg.getXpGainPerHit() * cfg.getXpGainMultiplier());
      return Math.max(0, (int)Math.round(gain));
   }

   public int getXpGainPerKill() {
      TechniqueConfig.TechniqueTypeConfig cfg = ConfigManager.getTechniqueConfig()
         .getKiTypeConfig(this.kiType != null ? this.kiType : KiAttackData.KiType.SMALL_BALL);
      double gain = Math.max(0.0, (double)cfg.getXpGainPerKill() * cfg.getXpGainMultiplier());
      return Math.max(0, (int)Math.round(gain));
   }

   public float getConfiguredDamageMultiplier() {
      TechniqueConfig.TechniqueTypeConfig cfg = ConfigManager.getTechniqueConfig()
         .getKiTypeConfig(this.kiType != null ? this.kiType : KiAttackData.KiType.SMALL_BALL);
      return (float)Math.max(0.0, cfg.getDamageMultiplier());
   }

   public boolean canUpgradeStat(String statName) {
      KiAttackData.KiType type = this.kiType != null ? this.kiType : KiAttackData.KiType.SMALL_BALL;
      if (!"damage".equals(statName) && !"cooldown".equals(statName)) {
         return switch (type) {
            case SMALL_BALL -> "speed".equals(statName);
            case MEDIUM_BALL -> "speed".equals(statName) || "armor_pen".equals(statName);
            case GIANT_BALL -> "size".equals(statName) || "armor_pen".equals(statName);
            case WAVE, LASER, BEAM, DISK -> "speed".equals(statName) || "armor_pen".equals(statName);
            case EXPLOSION -> "armor_pen".equals(statName);
            case SHIELD, AREA -> false;
            case BARRAGE -> "speed".equals(statName);
         };
      } else {
         return true;
      }
   }

   private int getTotalUpgradeCount() {
      return Math.max(0, this.damageLevel)
         + Math.max(0, this.sizeLevel)
         + Math.max(0, this.speedLevel)
         + Math.max(0, this.armorPenLevel)
         + Math.max(0, this.castTimeLevel)
         + Math.max(0, this.cooldownLevel);
   }

   private static float getWeightedComplexity(float damage, float sizeRatio01, float speed, int armorPen) {
      float maxStat = 20.0F;
      int maxArmorPen = 100;
      float damageWeight = 10.0F;
      float sizeWeight = 4.0F;
      float speedWeight = 3.0F;
      float armorPenWeight = 2.0F;
      float damageRatio = Mth.clamp(damage / 2.5F, 0.0F, 1.0F);
      float sizeRatio = Mth.clamp(sizeRatio01, 0.0F, 1.0F);
      float speedRatio = speed / maxStat;
      float armorPenRatio = (float)armorPen / (float)maxArmorPen;
      return damageRatio * damageWeight + sizeRatio * sizeWeight + speedRatio * speedWeight + armorPenRatio * armorPenWeight;
   }

   public void calculateDerivedValues() {
      KiAttackData.KiType resolvedType = this.kiType != null ? this.kiType : KiAttackData.KiType.SMALL_BALL;
      KiAttackData.Utility resolvedUtil = this.utility != null ? this.utility : KiAttackData.Utility.DAMAGE;
      float typeMult = getTypeMultiplier(resolvedType);
      float utilMult = getUtilityMultiplier(resolvedUtil);
      float[] normalized = normalizeStatsForType(resolvedType, this.damageMultiplier, this.size, this.speed, this.armorPenetration);
      float complexity = getWeightedComplexity(normalized[0], sizeComplexityRatio(resolvedType, normalized[1]), normalized[2], Math.round(normalized[3]));
      float tpBase = (80.0F + complexity * 100.0F) * typeMult * utilMult * this.secondaryCostMultiplier();
      this.tpCost = (float)Math.max(10, Math.round(tpBase));
      if (!PredefinedTechniques.isPredefinedTechniqueId(this.id)) {
         float initialDamage = Math.max(0.0F, normalized[0] - (float)this.damageLevel * 0.05F);
         float initialSize = Math.max(0.0F, normalized[1] - (float)this.sizeLevel * 0.1F);
         float initialSpeed = Math.max(0.0F, normalized[2] - (float)this.speedLevel * 0.05F);
         int initialArmorPen = Math.max(0, Math.round(normalized[3]) - this.armorPenLevel);
         float initialComplexity = getWeightedComplexity(initialDamage, sizeComplexityRatio(resolvedType, initialSize), initialSpeed, initialArmorPen);
         this.castTime = 0;
         int rawCooldown = computeDerivedCooldown(resolvedType, resolvedUtil, initialComplexity);
         this.cooldown = Math.max(10, Math.min(600, Math.round((float)rawCooldown * this.secondaryCostMultiplier())));
      }
   }

   private static byte[] compressOptimized(byte[] data) throws Exception {
      ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
      Deflater deflater = new Deflater(9, true);

      try (DeflaterOutputStream defOut = new DeflaterOutputStream(byteOut, deflater)) {
         defOut.write(data);
      }

      deflater.end();
      return byteOut.toByteArray();
   }

   private static byte[] decompressOptimized(byte[] data) throws Exception {
      ByteArrayInputStream byteIn = new ByteArrayInputStream(data);
      Inflater inflater = new Inflater(true);
      ByteArrayOutputStream byteOut = new ByteArrayOutputStream();

      try (InflaterInputStream infIn = new InflaterInputStream(byteIn, inflater)) {
         byte[] buffer = new byte[1024];
         int total = 0;

         int len;
         while ((len = infIn.read(buffer)) != -1) {
            total += len;
            if (total > 65536) {
               throw new IOException("Decompressed technique data exceeds limit");
            }

            byteOut.write(buffer, 0, len);
         }
      }

      inflater.end();
      return byteOut.toByteArray();
   }

   private static String encodeBigInt(byte[] bytes, String alphabet) {
      if (bytes != null && bytes.length != 0) {
         BigInteger value = new BigInteger(1, bytes);
         if (value.equals(BigInteger.ZERO)) {
            return String.valueOf(alphabet.charAt(0));
         } else {
            StringBuilder result = new StringBuilder();
            BigInteger base = BigInteger.valueOf((long)alphabet.length());

            while (value.compareTo(BigInteger.ZERO) > 0) {
               BigInteger[] divmod = value.divideAndRemainder(base);
               result.append(alphabet.charAt(divmod[1].intValue()));
               value = divmod[0];
            }

            return result.reverse().toString();
         }
      } else {
         return "";
      }
   }

   private static byte[] decodeBigInt(String encoded, String alphabet) {
      if (encoded != null && !encoded.isEmpty()) {
         BigInteger value = BigInteger.ZERO;
         BigInteger base = BigInteger.valueOf((long)alphabet.length());

         for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            int digit = alphabet.indexOf(c);
            if (digit < 0) {
               throw new IllegalArgumentException("Invalid char: " + c);
            }

            value = value.multiply(base).add(BigInteger.valueOf((long)digit));
         }

         byte[] bytes = value.toByteArray();
         if (bytes.length > 1 && bytes[0] == 0) {
            byte[] result = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, result, 0, result.length);
            return result;
         } else {
            return bytes;
         }
      } else {
         return new byte[0];
      }
   }

   public String generateExportCode() {
      try {
         CompoundTag tag = this.save();
         ByteArrayOutputStream nbtOut = new ByteArrayOutputStream();
         DataOutputStream dataOut = new DataOutputStream(nbtOut);
         NbtIo.write(tag, dataOut);
         dataOut.close();
         byte[] compressed = compressOptimized(nbtOut.toByteArray());
         return "DMZK1:" + encodeBigInt(compressed, "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
      } catch (Exception var5) {
         var5.printStackTrace();
         return "";
      }
   }

   public static KiAttackData importFromCode(String code) {
      if (code != null && !code.isEmpty()) {
         if (code.length() > 16384) {
            return null;
         } else if (!code.startsWith("DMZK1:")) {
            try {
               byte[] data = Base64.getUrlDecoder().decode(code);
               if (data.length > 65536) {
                  return null;
               } else {
                  ByteArrayInputStream bais = new ByteArrayInputStream(data);
                  GZIPInputStream gzip = new GZIPInputStream(bais);
                  CompoundTag tag = NbtIo.read(new DataInputStream(gzip), NbtAccounter.create(65536L));
                  gzip.close();
                  KiAttackData attack = new KiAttackData();
                  attack.load(tag);
                  attack.setId(UUID.randomUUID().toString());
                  attack.setExperience(0);
                  attack.sanitizeImportedStats();
                  return attack;
               }
            } catch (Exception var7) {
               return null;
            }
         } else {
            try {
               byte[] bytes = decodeBigInt(code.substring("DMZK1:".length()), "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
               byte[] decompressed = decompressOptimized(bytes);
               ByteArrayInputStream byteIn = new ByteArrayInputStream(decompressed);
               DataInputStream dataIn = new DataInputStream(byteIn);
               CompoundTag tag = NbtIo.read(dataIn, NbtAccounter.create(65536L));
               KiAttackData attack = new KiAttackData();
               attack.load(tag);
               attack.setId(UUID.randomUUID().toString());
               attack.setExperience(0);
               attack.sanitizeImportedStats();
               return attack;
            } catch (Exception var8) {
               var8.printStackTrace();
               return null;
            }
         }
      } else {
         return null;
      }
   }

   public void sanitizeImportedStats() {
      this.damageLevel = 0;
      this.sizeLevel = 0;
      this.speedLevel = 0;
      this.armorPenLevel = 0;
      this.castTimeLevel = 0;
      this.cooldownLevel = 0;
      KiAttackData.KiType resolvedType = this.kiType != null ? this.kiType : KiAttackData.KiType.SMALL_BALL;
      float[] normalized = normalizeStatsForType(resolvedType, this.damageMultiplier, this.size, this.speed, this.armorPenetration);
      this.damageMultiplier = normalized[0];
      this.size = normalized[1];
      this.speed = normalized[2];
      this.armorPenetration = Math.round(normalized[3]);
   }

   @Override
   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("Id", this.id);
      tag.putString("Name", this.name);
      tag.putString("Author", this.author);
      tag.putInt("Experience", this.experience);
      tag.putDouble("BaseCost", this.baseCost);
      tag.putFloat("TpCost", this.tpCost);
      tag.putInt("CastTime", this.castTime);
      tag.putInt("Cooldown", this.cooldown);
      ListTag racesTag = new ListTag();

      for (String race : this.allowedRaces) {
         racesTag.add(StringTag.valueOf(race));
      }

      tag.put("AllowedRaces", racesTag);
      tag.putString("KiType", this.kiType != null ? this.kiType.name() : "");
      tag.putString("Animation", this.animation != null ? this.animation : "");
      tag.putString("Utility", this.utility != null ? this.utility.name() : "");
      tag.putInt("ColorInterior", this.colorInterior);
      tag.putInt("ColorExterior", this.colorExterior);
      tag.putInt("ColorOutline", this.colorOutline);
      tag.putFloat("DamageMultiplier", this.damageMultiplier);
      tag.putFloat("Speed", this.speed);
      tag.putFloat("Size", this.size);
      tag.putInt("ArmorPenetration", this.armorPenetration);
      tag.putInt("ArmorPenLevel", this.armorPenLevel);
      tag.putInt("DamageLevel", this.damageLevel);
      tag.putInt("CastTimeLevel", this.castTimeLevel);
      tag.putInt("CooldownLevel", this.cooldownLevel);
      tag.putInt("SpeedLevel", this.speedLevel);
      tag.putInt("SizeLevel", this.sizeLevel);
      tag.putString("SecondaryEffectType", this.secondaryEffectType != null ? this.secondaryEffectType.name() : KiAttackData.SecondaryEffectType.NONE.name());
      tag.putString("AffectedStat", this.affectedStat != null ? this.affectedStat.name() : "");
      tag.putFloat("SecondaryIntensity", this.secondaryIntensity);
      tag.putInt("SecondaryDuration", this.secondaryDuration);
      return tag;
   }

   @Override
   public void load(CompoundTag tag) {
      this.id = tag.getString("Id");
      this.name = tag.getString("Name");
      this.author = tag.getString("Author");
      this.experience = tag.getInt("Experience");
      this.baseCost = tag.getDouble("BaseCost");
      this.tpCost = tag.contains("TpCost") ? tag.getFloat("TpCost") : 0.0F;
      this.castTime = tag.getInt("CastTime");
      this.cooldown = tag.getInt("Cooldown");
      this.allowedRaces.clear();
      ListTag racesTag = tag.getList("AllowedRaces", 8);

      for (int i = 0; i < racesTag.size(); i++) {
         this.allowedRaces.add(racesTag.getString(i));
      }

      try {
         this.kiType = KiAttackData.KiType.valueOf(tag.getString("KiType"));
      } catch (Exception var8) {
         this.kiType = KiAttackData.KiType.SMALL_BALL;
      }

      this.animation = tag.getString("Animation");

      try {
         this.utility = KiAttackData.Utility.valueOf(tag.getString("Utility"));
      } catch (Exception var7) {
         this.utility = KiAttackData.Utility.DAMAGE;
      }

      if (this.utility == KiAttackData.Utility.HEAL && !allowsHealUtility(this.kiType)) {
         this.utility = KiAttackData.Utility.DAMAGE;
      }

      if ((this.animation == null || this.animation.isEmpty()) && PredefinedTechniques.isPredefinedTechniqueId(this.id)) {
         KiAttackData predefined = PredefinedTechniques.REGISTRY.get(this.id);
         if (predefined != null) {
            this.animation = predefined.getAnimation();
         }
      }

      this.colorInterior = tag.getInt("ColorInterior");
      this.colorExterior = tag.getInt("ColorExterior");
      this.colorOutline = tag.getInt("ColorOutline");
      this.damageMultiplier = tag.getFloat("DamageMultiplier");
      this.speed = tag.getFloat("Speed");
      this.size = tag.getFloat("Size");
      this.armorPenetration = tag.getInt("ArmorPenetration");
      this.armorPenLevel = tag.getInt("ArmorPenLevel");
      this.damageLevel = tag.getInt("DamageLevel");
      this.castTimeLevel = tag.getInt("CastTimeLevel");
      this.cooldownLevel = tag.getInt("CooldownLevel");
      this.speedLevel = tag.getInt("SpeedLevel");
      this.sizeLevel = tag.getInt("SizeLevel");

      try {
         this.secondaryEffectType = KiAttackData.SecondaryEffectType.valueOf(tag.getString("SecondaryEffectType"));
      } catch (Exception var6) {
         this.secondaryEffectType = KiAttackData.SecondaryEffectType.NONE;
      }

      String affected = tag.getString("AffectedStat");
      if (affected != null && !affected.isEmpty()) {
         try {
            this.affectedStat = KiAttackData.AffectedStat.valueOf(affected);
         } catch (Exception var5) {
            this.affectedStat = null;
         }
      } else {
         this.affectedStat = null;
      }

      this.setSecondaryIntensity(tag.getFloat("SecondaryIntensity"));
      this.setSecondaryDuration(tag.getInt("SecondaryDuration"));
   }

   public static float[] normalizeStatsForType(KiAttackData.KiType type, float damage, float size, float speed, int armorPen) {
      KiAttackData.KiType resolvedType = type != null ? type : KiAttackData.KiType.SMALL_BALL;
      float normalizedDamage = Mth.clamp(damage, getMinDamageForType(resolvedType), getMaxDamageForType(resolvedType));
      float normalizedSize = usesCustomSize(resolvedType)
         ? Mth.clamp(size, getMinSizeForType(resolvedType), getMaxSizeForType(resolvedType))
         : getDefaultSizeForType(resolvedType);
      float normalizedSpeed = usesCustomSpeed(resolvedType)
         ? Mth.clamp(speed, getMinSpeedForType(resolvedType), getMaxSpeedForType(resolvedType))
         : getDefaultSpeedForType(resolvedType);
      float normalizedArmorPen = usesCustomArmorPen(resolvedType)
         ? (float)Mth.clamp(armorPen, 0, getMaxArmorPenForType(resolvedType))
         : (float)getDefaultArmorPenForType(resolvedType);
      return new float[]{normalizedDamage, normalizedSize, normalizedSpeed, normalizedArmorPen};
   }

   public static boolean usesCustomSize(KiAttackData.KiType type) {
      return switch (type) {
         case SMALL_BALL, MEDIUM_BALL, GIANT_BALL -> true;
         default -> false;
      };
   }

   public static float getMinSizeForType(KiAttackData.KiType type) {
      return switch (type) {
         case SMALL_BALL -> 1.0F;
         case MEDIUM_BALL -> 7.5F;
         case GIANT_BALL -> 15.0F;
         default -> 0.1F;
      };
   }

   public static float getMaxSizeForType(KiAttackData.KiType type) {
      return switch (type) {
         case SMALL_BALL -> 5.0F;
         case MEDIUM_BALL -> 12.5F;
         default -> 20.0F;
      };
   }

   public static boolean usesCustomSpeed(KiAttackData.KiType type) {
      return switch (type) {
         case SMALL_BALL, MEDIUM_BALL, GIANT_BALL, WAVE, LASER, BEAM, DISK, BARRAGE -> true;
         default -> false;
      };
   }

   public static boolean usesCustomArmorPen(KiAttackData.KiType type) {
      return switch (type) {
         case MEDIUM_BALL, GIANT_BALL, WAVE, LASER, BEAM, DISK, EXPLOSION -> true;
         default -> false;
      };
   }

   public static boolean isLargeDamageTier(KiAttackData.KiType type) {
      return type == KiAttackData.KiType.GIANT_BALL || type == KiAttackData.KiType.EXPLOSION;
   }

   private static boolean isMediumDamageTier(KiAttackData.KiType type) {
      return switch (type) {
         case MEDIUM_BALL, WAVE, BEAM, SHIELD, AREA -> true;
         default -> false;
      };
   }

   public static float getMinDamageForType(KiAttackData.KiType type) {
      if (isLargeDamageTier(type)) {
         return 1.0F;
      } else {
         return isMediumDamageTier(type) ? 0.5F : 0.2F;
      }
   }

   public static float getMaxDamageForType(KiAttackData.KiType type) {
      if (isLargeDamageTier(type)) {
         return 2.0F;
      } else {
         return isMediumDamageTier(type) ? 1.0F : 0.4F;
      }
   }

   public static float getDefaultDamageForType(KiAttackData.KiType type) {
      if (isLargeDamageTier(type)) {
         return 1.5F;
      } else {
         return isMediumDamageTier(type) ? 0.75F : 0.3F;
      }
   }

   public static float getMinSpeedForType(KiAttackData.KiType type) {
      return 0.1F;
   }

   public static float getMaxSpeedForType(KiAttackData.KiType type) {
      return switch (type) {
         case SMALL_BALL, LASER -> 2.0F;
         case MEDIUM_BALL, BEAM, BARRAGE -> 1.5F;
         case GIANT_BALL -> 0.75F;
         case WAVE -> 1.25F;
         case DISK -> 1.75F;
         default -> 1.0F;
      };
   }

   public static int getMaxArmorPenForType(KiAttackData.KiType type) {
      return switch (type) {
         case GIANT_BALL, BEAM, DISK -> 25;
         default -> 15;
      };
   }

   private static float largeOverloadKiMultiplier(KiAttackData.KiType type, float damage) {
      return isLargeDamageTier(type) && !(damage <= 2.0F) ? 1.0F + (damage - 2.0F) * 2.0F : 1.0F;
   }

   public static float getDefaultSizeForType(KiAttackData.KiType type) {
      return switch (type) {
         case SMALL_BALL -> 3.0F;
         case MEDIUM_BALL -> 10.0F;
         case GIANT_BALL -> 17.5F;
         default -> 1.0F;
         case EXPLOSION -> 8.0F;
      };
   }

   private static float sizeComplexityRatio(KiAttackData.KiType type, float size) {
      KiAttackData.KiType resolved = type != null ? type : KiAttackData.KiType.SMALL_BALL;
      if (usesCustomSize(resolved)) {
         float min = getMinSizeForType(resolved);
         float max = getMaxSizeForType(resolved);
         return max <= min ? 0.0F : Mth.clamp((size - min) / (max - min), 0.0F, 1.0F);
      } else {
         return Mth.clamp(getDefaultSizeForType(resolved) / 20.0F, 0.0F, 1.0F);
      }
   }

   public static float getDefaultSpeedForType(KiAttackData.KiType type) {
      return Math.min(1.0F, getMaxSpeedForType(type));
   }

   public static int getDefaultArmorPenForType(KiAttackData.KiType type) {
      return 0;
   }

   private static int computeDerivedCooldown(KiAttackData.KiType type, KiAttackData.Utility util, float initialComplexity) {
      KiAttackData.KiType resolved = type != null ? type : KiAttackData.KiType.SMALL_BALL;
      float typeMult = getTypeMultiplier(resolved);
      float utilMult = getUtilityMultiplier(util != null ? util : KiAttackData.Utility.DAMAGE);

      float cdTypeMult = switch (resolved) {
         case SMALL_BALL, LASER, DISK -> 0.5F;
         default -> 1.0F;
      };
      float base = (20.0F + initialComplexity * 4.0F) * typeMult * utilMult * cdTypeMult;
      return Math.max(10, Math.min(600, Math.round(base)));
   }

   public List<String> getAllowedRaces() {
      return this.allowedRaces;
   }

   public KiAttackData.KiType getKiType() {
      return this.kiType;
   }

   public String getAnimation() {
      return this.animation;
   }

   public KiAttackData.Utility getUtility() {
      return this.utility;
   }

   public int getColorInterior() {
      return this.colorInterior;
   }

   public int getColorExterior() {
      return this.colorExterior;
   }

   public int getColorOutline() {
      return this.colorOutline;
   }

   public float getDamageMultiplier() {
      return this.damageMultiplier;
   }

   public float getSpeed() {
      return this.speed;
   }

   public float getSize() {
      return this.size;
   }

   public int getArmorPenetration() {
      return this.armorPenetration;
   }

   public KiAttackData.SecondaryEffectType getSecondaryEffectType() {
      return this.secondaryEffectType;
   }

   public KiAttackData.AffectedStat getAffectedStat() {
      return this.affectedStat;
   }

   public float getSecondaryIntensity() {
      return this.secondaryIntensity;
   }

   public int getSecondaryDuration() {
      return this.secondaryDuration;
   }

   public int getDamageLevel() {
      return this.damageLevel;
   }

   public int getCastTimeLevel() {
      return this.castTimeLevel;
   }

   public int getCooldownLevel() {
      return this.cooldownLevel;
   }

   public int getSpeedLevel() {
      return this.speedLevel;
   }

   public int getSizeLevel() {
      return this.sizeLevel;
   }

   public int getArmorPenLevel() {
      return this.armorPenLevel;
   }

   public void setAllowedRaces(List<String> allowedRaces) {
      this.allowedRaces = allowedRaces;
   }

   public void setKiType(KiAttackData.KiType kiType) {
      this.kiType = kiType;
   }

   public void setAnimation(String animation) {
      this.animation = animation;
   }

   public void setUtility(KiAttackData.Utility utility) {
      this.utility = utility;
   }

   public void setColorInterior(int colorInterior) {
      this.colorInterior = colorInterior;
   }

   public void setColorExterior(int colorExterior) {
      this.colorExterior = colorExterior;
   }

   public void setColorOutline(int colorOutline) {
      this.colorOutline = colorOutline;
   }

   public void setDamageMultiplier(float damageMultiplier) {
      this.damageMultiplier = damageMultiplier;
   }

   public void setSpeed(float speed) {
      this.speed = speed;
   }

   public void setSize(float size) {
      this.size = size;
   }

   public void setArmorPenetration(int armorPenetration) {
      this.armorPenetration = armorPenetration;
   }

   public void setSecondaryEffectType(KiAttackData.SecondaryEffectType secondaryEffectType) {
      this.secondaryEffectType = secondaryEffectType;
   }

   public void setAffectedStat(KiAttackData.AffectedStat affectedStat) {
      this.affectedStat = affectedStat;
   }

   public void setDamageLevel(int damageLevel) {
      this.damageLevel = damageLevel;
   }

   public void setCastTimeLevel(int castTimeLevel) {
      this.castTimeLevel = castTimeLevel;
   }

   public void setCooldownLevel(int cooldownLevel) {
      this.cooldownLevel = cooldownLevel;
   }

   public void setSpeedLevel(int speedLevel) {
      this.speedLevel = speedLevel;
   }

   public void setSizeLevel(int sizeLevel) {
      this.sizeLevel = sizeLevel;
   }

   public void setArmorPenLevel(int armorPenLevel) {
      this.armorPenLevel = armorPenLevel;
   }

   public static enum AffectedStat {
      STR,
      SKP,
      DEF,
      STM_REGEN,
      HP_REGEN,
      ENE_REGEN,
      PWR;
   }

   public static enum KiType {
      SMALL_BALL,
      MEDIUM_BALL,
      GIANT_BALL,
      WAVE,
      LASER,
      BEAM,
      DISK,
      EXPLOSION,
      SHIELD,
      BARRAGE,
      AREA;
   }

   public static enum SecondaryEffectType {
      NONE,
      BUFF,
      DEBUFF;
   }

   public static enum Utility {
      DAMAGE,
      HEAL;
   }
}
