package com.dragonminez.common.combat.weapon;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class WeaponAttributesHelper {
   private static final String nbtTag = "dragonminez_weapon_attributes";
   private static final Type attributesContainerFileFormat = (new TypeToken<AttributesContainer>() {
   }).getType();

   public static WeaponAttributes override(WeaponAttributes a, WeaponAttributes b) {
      double attackRange = b.attackRange() > 0.0 ? b.attackRange() : a.attackRange();
      String pose = b.pose() != null ? b.pose() : a.pose();
      String off_hand_pose = b.offHandPose() != null ? b.offHandPose() : a.offHandPose();
      Boolean isTwoHanded = b.two_handed() != null ? b.two_handed() : a.two_handed();
      String category = b.category() != null ? b.category() : a.category();
      Double critChance = b.crit_chance() != null ? b.crit_chance() : a.crit_chance();
      Double critDamage = b.crit_damage() != null ? b.crit_damage() : a.crit_damage();
      WeaponAttributes.Attack[] attacks = a.attacks();
      if (b.attacks() != null && b.attacks().length > 0) {
         ArrayList<WeaponAttributes.Attack> overrideAttacks = new ArrayList<>();

         for (int i = 0; i < b.attacks().length; i++) {
            WeaponAttributes.Attack base = a.attacks() != null && a.attacks().length > i
               ? a.attacks()[i]
               : new WeaponAttributes.Attack(null, null, 0.0, 0.0, 0.0, null, null, null);
            WeaponAttributes.Attack override = b.attacks()[i];
            WeaponAttributes.Attack attack = new WeaponAttributes.Attack(
               override.conditions() != null ? override.conditions() : base.conditions(),
               override.hitbox() != null ? override.hitbox() : base.hitbox(),
               override.damageMultiplier() != 0.0 ? override.damageMultiplier() : base.damageMultiplier(),
               override.angle() != 0.0 ? override.angle() : base.angle(),
               override.upswing() != 0.0 ? override.upswing() : base.upswing(),
               override.animation() != null ? override.animation() : base.animation(),
               override.swingSound() != null ? override.swingSound() : base.swingSound(),
               override.impactSound() != null ? override.impactSound() : base.impactSound()
            );
            overrideAttacks.add(attack);
         }

         attacks = overrideAttacks.toArray(new WeaponAttributes.Attack[0]);
      }

      return new WeaponAttributes(attackRange, pose, off_hand_pose, isTwoHanded, category, attacks, critChance, critDamage);
   }

   public static AttributesContainer getContainerFromNBT(ItemStack itemStack) {
      if (itemStack.isEmpty()) {
         return null;
      } else {
         CustomData data = (CustomData)itemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
         CompoundTag tag = data.copyTag();
         if (tag.contains("dragonminez_weapon_attributes")) {
            String json = tag.getString("dragonminez_weapon_attributes");
            if (!json.isBlank()) {
               Gson gson = new Gson();
               return (AttributesContainer)gson.fromJson(json, attributesContainerFileFormat);
            }
         }

         return null;
      }
   }

   public static AttributesContainer decode(Reader reader) {
      Gson gson = new Gson();
      AttributesContainer container = (AttributesContainer)gson.fromJson(reader, attributesContainerFileFormat);
      return normalizeContainer(container);
   }

   public static AttributesContainer decode(JsonReader json) {
      Gson gson = new Gson();
      AttributesContainer container = (AttributesContainer)gson.fromJson(json, attributesContainerFileFormat);
      return normalizeContainer(container);
   }

   public static String encode(AttributesContainer container) {
      Gson gson = new Gson();
      return gson.toJson(container);
   }

   private static AttributesContainer normalizeContainer(AttributesContainer container) {
      if (container != null && container.attributes() != null) {
         WeaponAttributes attributes = container.attributes();
         String pose = sanitizeAnimationId(attributes.pose());
         String offHandPose = sanitizeAnimationId(attributes.offHandPose());
         WeaponAttributes.Attack[] attacks = null;
         if (attributes.attacks() != null) {
            attacks = new WeaponAttributes.Attack[attributes.attacks().length];

            for (int i = 0; i < attributes.attacks().length; i++) {
               WeaponAttributes.Attack attack = attributes.attacks()[i];
               if (attack == null) {
                  attacks[i] = null;
               } else {
                  attacks[i] = new WeaponAttributes.Attack(
                     attack.conditions(),
                     attack.hitbox(),
                     attack.damageMultiplier(),
                     attack.angle(),
                     attack.upswing(),
                     sanitizeAnimationId(attack.animation()),
                     attack.swingSound(),
                     attack.impactSound()
                  );
               }
            }
         }

         WeaponAttributes normalized = new WeaponAttributes(
            attributes.attackRange(),
            pose.isBlank() ? null : pose,
            offHandPose.isBlank() ? null : offHandPose,
            attributes.two_handed(),
            attributes.category(),
            attacks,
            attributes.crit_chance(),
            attributes.crit_damage()
         );
         return new AttributesContainer(container.parent(), normalized);
      } else {
         return container;
      }
   }

   private static String sanitizeAnimationId(String animationId) {
      if (animationId == null) {
         return "";
      } else {
         String id = animationId.trim();
         if (id.startsWith("dragonminez:")) {
            id = id.substring("dragonminez:".length());
         }

         if (id.startsWith("animation.base.")) {
            id = id.substring("animation.base.".length());
         }

         if (id.startsWith("combat.")) {
            id = id.substring("combat.".length());
         }

         return id;
      }
   }
}
