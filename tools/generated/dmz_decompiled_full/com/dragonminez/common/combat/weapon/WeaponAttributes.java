package com.dragonminez.common.combat.weapon;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

public final class WeaponAttributes {
   private final double attack_range;
   @Nullable
   private final String pose;
   @Nullable
   private final String off_hand_pose;
   private final Boolean two_handed;
   @Nullable
   private final String category;
   private final WeaponAttributes.Attack[] attacks;
   @Nullable
   private final Double crit_chance;
   @Nullable
   private final Double crit_damage;

   public WeaponAttributes(
      double attack_range,
      @Nullable String pose,
      @Nullable String off_hand_pose,
      Boolean isTwoHanded,
      String category,
      WeaponAttributes.Attack[] attacks,
      @Nullable Double crit_chance,
      @Nullable Double crit_damage
   ) {
      this.attack_range = attack_range;
      this.pose = pose;
      this.off_hand_pose = off_hand_pose;
      this.two_handed = isTwoHanded;
      this.category = category;
      this.attacks = attacks;
      this.crit_chance = crit_chance;
      this.crit_damage = crit_damage;
   }

   public WeaponAttributes(
      double attack_range, @Nullable String pose, @Nullable String off_hand_pose, Boolean isTwoHanded, String category, WeaponAttributes.Attack[] attacks
   ) {
      this(attack_range, pose, off_hand_pose, isTwoHanded, category, attacks, null, null);
   }

   public double attackRange() {
      return this.attack_range;
   }

   @Nullable
   public String pose() {
      return this.pose;
   }

   @Nullable
   public String offHandPose() {
      return this.off_hand_pose;
   }

   @Nullable
   public String category() {
      return this.category;
   }

   public boolean isTwoHanded() {
      return this.two_handed != null ? this.two_handed : false;
   }

   public Boolean two_handed() {
      return this.two_handed;
   }

   public WeaponAttributes.Attack[] attacks() {
      return this.attacks;
   }

   @Nullable
   public Double crit_chance() {
      return this.crit_chance;
   }

   @Nullable
   public Double crit_damage() {
      return this.crit_damage;
   }

   public double getSafeCritChance() {
      return this.crit_chance != null ? this.crit_chance : 0.0;
   }

   public double getSafeCritDamage() {
      return this.crit_damage != null ? this.crit_damage : 0.0;
   }

   @Override
   public boolean equals(Object obj) {
      if (obj == this) {
         return true;
      } else if (obj != null && obj.getClass() == this.getClass()) {
         WeaponAttributes that = (WeaponAttributes)obj;
         return Double.doubleToLongBits(this.attack_range) == Double.doubleToLongBits(that.attack_range)
            && Objects.equals(this.pose, that.pose)
            && Objects.equals(this.two_handed, that.two_handed)
            && Objects.equals(this.attacks, that.attacks)
            && Objects.equals(this.crit_chance, that.crit_chance)
            && Objects.equals(this.crit_damage, that.crit_damage);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.attack_range, this.two_handed, this.attacks, this.crit_chance, this.crit_damage);
   }

   @Override
   public String toString() {
      return "WeaponAttributes[attack_range="
         + this.attack_range
         + ", pose="
         + this.pose
         + ", isTwoHanded="
         + this.two_handed
         + ", attacks="
         + this.attacks
         + ", crit_chance="
         + this.crit_chance
         + ", crit_damage="
         + this.crit_damage
         + "]";
   }

   public static final class Attack {
      private String[] conditions;
      private WeaponAttributes.HitBoxShape hitbox;
      private double damage_multiplier = 1.0;
      private double angle = 0.0;
      private double upswing = 0.0;
      private String animation = null;
      private WeaponAttributes.Sound swing_sound = null;
      private WeaponAttributes.Sound impact_sound = null;

      public Attack() {
      }

      public Attack(
         String[] conditions,
         WeaponAttributes.HitBoxShape hitbox,
         double damage_multiplier,
         double angle,
         double upswing,
         String animation,
         WeaponAttributes.Sound swing_sound,
         WeaponAttributes.Sound impact_sound
      ) {
         this.conditions = conditions;
         this.hitbox = hitbox;
         this.damage_multiplier = damage_multiplier;
         this.angle = angle;
         this.upswing = upswing;
         this.animation = animation;
         this.swing_sound = swing_sound;
         this.impact_sound = impact_sound;
      }

      @Nullable
      public String[] conditions() {
         return this.conditions;
      }

      public WeaponAttributes.HitBoxShape hitbox() {
         return this.hitbox;
      }

      public double damageMultiplier() {
         return this.damage_multiplier;
      }

      public double angle() {
         return this.angle;
      }

      public double upswing() {
         return this.upswing;
      }

      public String animation() {
         return this.animation;
      }

      public WeaponAttributes.Sound swingSound() {
         return this.swing_sound;
      }

      public WeaponAttributes.Sound impactSound() {
         return this.impact_sound;
      }

      @Override
      public boolean equals(Object obj) {
         if (obj == this) {
            return true;
         } else if (obj != null && obj.getClass() == this.getClass()) {
            WeaponAttributes.Attack that = (WeaponAttributes.Attack)obj;
            return Objects.equals(this.hitbox, that.hitbox)
               && Double.doubleToLongBits(this.damage_multiplier) == Double.doubleToLongBits(that.damage_multiplier)
               && Double.doubleToLongBits(this.angle) == Double.doubleToLongBits(that.angle)
               && Double.doubleToLongBits(this.upswing) == Double.doubleToLongBits(that.upswing)
               && Objects.equals(this.animation, that.animation)
               && Objects.equals(this.swing_sound, that.swing_sound)
               && Objects.equals(this.impact_sound, that.impact_sound);
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.hitbox, this.damage_multiplier, this.angle, this.upswing, this.animation, this.swing_sound, this.impact_sound);
      }

      @Override
      public String toString() {
         return "Attack[hitbox="
            + this.hitbox
            + ", damage_multiplier="
            + this.damage_multiplier
            + ", angle="
            + this.angle
            + ", upswing="
            + this.upswing
            + ", animation="
            + this.animation
            + ", swing_sound="
            + this.swing_sound
            + ", impact_sound="
            + this.impact_sound
            + "]";
      }
   }

   public static enum Condition {
      NOT_DUAL_WIELDING,
      DUAL_WIELDING_ANY,
      DUAL_WIELDING_SAME,
      DUAL_WIELDING_SAME_CATEGORY,
      NO_OFFHAND_ITEM,
      OFF_HAND_SHIELD,
      MAIN_HAND_ONLY,
      OFF_HAND_ONLY,
      MOUNTED,
      NOT_MOUNTED,
      SNEAKING,
      NOT_SNEAKING;
   }

   public static enum HitBoxShape {
      FORWARD_BOX,
      VERTICAL_PLANE,
      HORIZONTAL_PLANE;
   }

   public static final class Sound {
      private String id = null;
      private float volume = 1.0F;
      private float pitch = 1.0F;
      private float randomness = 0.1F;

      public Sound() {
      }

      public Sound(String id) {
         this.id = id;
      }

      public String id() {
         return this.id;
      }

      public float volume() {
         return this.volume;
      }

      public float pitch() {
         return this.pitch;
      }

      public float randomness() {
         return this.randomness;
      }

      @Override
      public boolean equals(Object obj) {
         if (obj == this) {
            return true;
         } else if (obj != null && obj.getClass() == this.getClass()) {
            WeaponAttributes.Sound that = (WeaponAttributes.Sound)obj;
            return Objects.equals(this.id, that.id)
               && Float.floatToIntBits(this.volume) == Float.floatToIntBits(that.volume)
               && Float.floatToIntBits(this.pitch) == Float.floatToIntBits(that.pitch)
               && Float.floatToIntBits(this.randomness) == Float.floatToIntBits(that.randomness);
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.id, this.volume, this.pitch, this.randomness);
      }

      @Override
      public String toString() {
         return "SoundV2[id=" + this.id + ", volume=" + this.volume + ", pitch=" + this.pitch + ", randomness=" + this.randomness + "]";
      }
   }
}
