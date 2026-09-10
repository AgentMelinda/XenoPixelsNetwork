package com.dragonminez.client.gui.tooltip.dynamic;

import com.dragonminez.common.init.MainAttributes;
import com.dragonminez.common.init.MainEnchants;
import com.dragonminez.common.util.AttributeMods;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.Nullable;

public class AttributeTooltipHandler {
   private static final DecimalFormat FORMAT = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT));
   public static final ChatFormatting BASE_COLOR = ChatFormatting.DARK_GREEN;
   public static final int MERGE_BASE_MODIFIER_COLOR = 16758784;
   public static final int MODIFIER_BLUE = 5592575;
   public static final int MODIFIER_RED = 16733525;
   public static final Set<Holder<Attribute>> PERCENT_ATTRIBUTES = Set.of(Attributes.MOVEMENT_SPEED, Attributes.KNOCKBACK_RESISTANCE);
   public static final Comparator<AttributeModifier> ATTRIBUTE_MODIFIER_COMPARATOR = Comparator.comparing(AttributeModifier::operation)
      .thenComparing(a -> -Math.abs(a.amount()))
      .thenComparing(AttributeModifier::id);

   public static boolean isPercentAttribute(Holder<Attribute> attribute) {
      return PERCENT_ATTRIBUTES.contains(attribute) || attribute.equals(MainAttributes.CRIT_CHANCE) || attribute.equals(MainAttributes.CRIT_DAMAGE);
   }

   public static boolean isPercentAttribute(Attribute attribute) {
      if (attribute == null) {
         return false;
      } else {
         return !attribute.equals(Attributes.MOVEMENT_SPEED.value()) && !attribute.equals(Attributes.KNOCKBACK_RESISTANCE.value())
            ? attribute.equals(MainAttributes.CRIT_CHANCE.get()) || attribute.equals(MainAttributes.CRIT_DAMAGE.get())
            : true;
      }
   }

   public static boolean processVanillaAttributes(ItemStack stack, Consumer<Component> tooltip, @Nullable Player player) {
      boolean needsShiftPrompt = false;
      ItemAttributeModifiers actualModifiers = stack.getAttributeModifiers();
      ItemAttributeModifiers defaultModifiers = (ItemAttributeModifiers)stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
      if (defaultModifiers.modifiers().isEmpty()) {
         defaultModifiers = stack.getItem().getDefaultAttributeModifiers(stack);
      }

      EquipmentSlot[] slots = new EquipmentSlot[]{
         EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND
      };

      for (EquipmentSlot slot : slots) {
         Multimap<Holder<Attribute>, AttributeModifier> actual = collectForSlot(actualModifiers, slot);
         Multimap<Holder<Attribute>, AttributeModifier> defaults = collectForSlot(defaultModifiers, slot);
         float enchantDamage = 0.0F;
         double critChanceBonus = 0.0;
         double critDamageBonus = 0.0;
         if (slot == EquipmentSlot.MAINHAND) {
            enchantDamage = 0.0F;
            int chanceLevel = MainEnchants.level(stack, Minecraft.getInstance().level, MainEnchants.CRIT_CHANCE);
            if (chanceLevel > 0) {
               critChanceBonus = (double)chanceLevel * 0.05;
            }

            int damageLevel = MainEnchants.level(stack, Minecraft.getInstance().level, MainEnchants.CRIT_DAMAGE);
            if (damageLevel > 0) {
               critDamageBonus = (double)damageLevel * 0.05;
            }
         }

         if (!actual.isEmpty() || !(enchantDamage <= 0.0F) || !(critChanceBonus <= 0.0) || !(critDamageBonus <= 0.0)) {
            tooltip.accept(Component.translatable("item.modifiers." + slot.getName()).withStyle(ChatFormatting.GRAY));
            Set<Holder<Attribute>> allAttributes = new LinkedHashSet<>(actual.keySet());
            if (enchantDamage > 0.0F) {
               allAttributes.add(Attributes.ATTACK_DAMAGE);
            }

            if (critChanceBonus > 0.0) {
               allAttributes.add(MainAttributes.CRIT_CHANCE);
            }

            if (critDamageBonus > 0.0) {
               allAttributes.add(MainAttributes.CRIT_DAMAGE);
            }

            for (Holder<Attribute> attr : allAttributes) {
               if (!attr.equals(Attributes.BLOCK_INTERACTION_RANGE) && !attr.equals(Attributes.ENTITY_INTERACTION_RANGE)) {
                  List<AttributeModifier> baseMods = new ArrayList<>();
                  List<AttributeModifier> extraMods = new ArrayList<>();

                  for (AttributeModifier mod : actual.get(attr)) {
                     if (defaults.containsEntry(attr, mod)) {
                        baseMods.add(mod);
                     } else {
                        extraMods.add(mod);
                     }
                  }

                  if (attr.equals(Attributes.ATTACK_DAMAGE) && enchantDamage > 0.0F) {
                     extraMods.add(AttributeMods.of(UUID.randomUUID(), "Enchantment Damage", (double)enchantDamage, Operation.ADD_VALUE));
                  }

                  if (attr.equals(MainAttributes.CRIT_CHANCE) && critChanceBonus > 0.0) {
                     extraMods.add(AttributeMods.of(UUID.randomUUID(), "Enchantment Crit Chance", critChanceBonus, Operation.ADD_VALUE));
                  }

                  if (attr.equals(MainAttributes.CRIT_DAMAGE) && critDamageBonus > 0.0) {
                     extraMods.add(AttributeMods.of(UUID.randomUUID(), "Enchantment Crit Damage", critDamageBonus, Operation.ADD_VALUE));
                  }

                  double playerBase = player != null && player.getAttributes().hasAttribute(attr) ? player.getAttributeBaseValue(attr) : 0.0;
                  if (attr.equals(Attributes.ATTACK_DAMAGE)) {
                     playerBase = 1.0;
                  }

                  if (attr.equals(Attributes.ATTACK_SPEED)) {
                     playerBase = 4.0;
                  }

                  if (attr.equals(MainAttributes.CRIT_CHANCE)) {
                     playerBase = 0.05;
                  }

                  if (attr.equals(MainAttributes.CRIT_DAMAGE)) {
                     playerBase = 1.5;
                  }

                  double trueBase = playerBase;

                  for (AttributeModifier modx : baseMods) {
                     if (modx.operation() == Operation.ADD_VALUE) {
                        trueBase += modx.amount();
                     } else if (modx.operation() == Operation.ADD_MULTIPLIED_BASE) {
                        trueBase += playerBase * modx.amount();
                     } else if (modx.operation() == Operation.ADD_MULTIPLIED_TOTAL) {
                        trueBase *= 1.0 + modx.amount();
                     }
                  }

                  double finalValue = trueBase;
                  extraMods.sort(ATTRIBUTE_MODIFIER_COMPARATOR);

                  for (AttributeModifier modxx : extraMods) {
                     if (modxx.operation() == Operation.ADD_VALUE) {
                        finalValue += modxx.amount();
                     } else if (modxx.operation() == Operation.ADD_MULTIPLIED_BASE) {
                        finalValue += trueBase * modxx.amount();
                     } else if (modxx.operation() == Operation.ADD_MULTIPLIED_TOTAL) {
                        finalValue *= 1.0 + modxx.amount();
                     }
                  }

                  boolean hasExtras = !extraMods.isEmpty();
                  if (hasExtras) {
                     needsShiftPrompt = true;
                  }

                  if (Screen.hasShiftDown() && hasExtras) {
                     tooltip.accept(createTotalComponent(attr, finalValue).withStyle(style -> style.withColor(16758784)));
                     tooltip.accept(listHeader().append(createTotalComponent(attr, trueBase).withStyle(BASE_COLOR)));

                     for (AttributeModifier modxxx : extraMods) {
                        tooltip.accept(listHeader().append(createModifierComponent(attr, modxxx)));
                     }
                  } else {
                     ChatFormatting color = hasExtras ? null : BASE_COLOR;
                     Integer intColor = hasExtras ? 16758784 : null;
                     tooltip.accept(createTotalComponent(attr, finalValue).withStyle(style -> {
                        if (intColor != null) {
                           return style.withColor(intColor);
                        } else {
                           return color != null ? style.applyFormat(color) : style;
                        }
                     }));
                  }
               }
            }
         }
      }

      return needsShiftPrompt;
   }

   private static Multimap<Holder<Attribute>, AttributeModifier> collectForSlot(ItemAttributeModifiers modifiers, EquipmentSlot slot) {
      Multimap<Holder<Attribute>, AttributeModifier> map = HashMultimap.create();
      if (modifiers == null) {
         return map;
      } else {
         modifiers.forEach(slot, map::put);
         return map;
      }
   }

   public static MutableComponent createTotalComponent(Holder<Attribute> attribute, double value) {
      boolean percent = isPercentAttribute(attribute);
      String suffix = percent ? "%" : "";
      double displayValue = percent ? value * 100.0 : value;
      Component rawAttrDesc = Component.translatable(((Attribute)attribute.value()).getDescriptionId());
      Component attrDescNoIcon = IconUtil.getAttributeNameWithoutIcon(rawAttrDesc);
      Component coloredStat = Component.translatable("attribute.modifier.equals.0", new Object[]{FORMAT.format(displayValue) + suffix, attrDescNoIcon});
      Component finalStat = IconUtil.processIcon(rawAttrDesc, coloredStat);
      return Component.empty().append(finalStat);
   }

   public static MutableComponent createTotalComponent(Attribute attribute, double value) {
      boolean percent = isPercentAttribute(attribute);
      String suffix = percent ? "%" : "";
      double displayValue = percent ? value * 100.0 : value;
      Component rawAttrDesc = Component.translatable(attribute.getDescriptionId());
      Component attrDescNoIcon = IconUtil.getAttributeNameWithoutIcon(rawAttrDesc);
      Component coloredStat = Component.translatable("attribute.modifier.equals.0", new Object[]{FORMAT.format(displayValue) + suffix, attrDescNoIcon});
      Component finalStat = IconUtil.processIcon(rawAttrDesc, coloredStat);
      return Component.empty().append(finalStat);
   }

   public static MutableComponent createModifierComponent(Holder<Attribute> attribute, AttributeModifier modifier) {
      double value = modifier.amount();
      boolean isPositive = value > 0.0;
      boolean percent = isPercentAttribute(attribute);
      String suffix = percent ? "%" : "";
      double displayValue = !percent && modifier.operation() == Operation.ADD_VALUE ? value : value * 100.0;
      String key = isPositive ? "attribute.modifier.plus." + modifier.operation().id() : "attribute.modifier.take." + modifier.operation().id();
      String formattedValue = FORMAT.format(Math.abs(displayValue)) + suffix;
      ChatFormatting color = isPositive ? ChatFormatting.BLUE : ChatFormatting.RED;
      Component rawAttrDesc = Component.translatable(((Attribute)attribute.value()).getDescriptionId());
      Component attrDescNoIcon = IconUtil.getAttributeNameWithoutIcon(rawAttrDesc);
      Component coloredStat = Component.translatable(key, new Object[]{formattedValue, attrDescNoIcon}).withStyle(color);
      Component finalStat = IconUtil.processIcon(rawAttrDesc, coloredStat);
      return Component.empty().append(finalStat);
   }

   public static MutableComponent createModifierComponent(Attribute attribute, AttributeModifier modifier) {
      double value = modifier.amount();
      boolean isPositive = value > 0.0;
      boolean percent = isPercentAttribute(attribute);
      String suffix = percent ? "%" : "";
      double displayValue = !percent && modifier.operation() == Operation.ADD_VALUE ? value : value * 100.0;
      String key = isPositive ? "attribute.modifier.plus." + modifier.operation().id() : "attribute.modifier.take." + modifier.operation().id();
      String formattedValue = FORMAT.format(Math.abs(displayValue)) + suffix;
      ChatFormatting color = isPositive ? ChatFormatting.BLUE : ChatFormatting.RED;
      Component rawAttrDesc = Component.translatable(attribute.getDescriptionId());
      Component attrDescNoIcon = IconUtil.getAttributeNameWithoutIcon(rawAttrDesc);
      Component coloredStat = Component.translatable(key, new Object[]{formattedValue, attrDescNoIcon}).withStyle(color);
      Component finalStat = IconUtil.processIcon(rawAttrDesc, coloredStat);
      return Component.empty().append(finalStat);
   }

   public static MutableComponent listHeader() {
      return Component.literal(" ┇ ").withStyle(ChatFormatting.GRAY);
   }
}
