package com.dragonminez.common.util;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

public final class AttributeMods {
   private AttributeMods() {
   }

   public static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", path);
   }

   public static ResourceLocation id(UUID uuid) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "uuid_" + uuid.toString().replace("-", ""));
   }

   public static AttributeModifier of(String path, double amount, Operation op) {
      return new AttributeModifier(id(path), amount, op);
   }

   public static AttributeModifier of(UUID uuid, String legacyName, double amount, Operation op) {
      return new AttributeModifier(id(uuid), amount, op);
   }
}
