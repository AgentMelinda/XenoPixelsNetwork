package com.dragonminez.compat.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class RegistryObject<T> extends DeferredHolder<T, T> {
   private RegistryObject(ResourceKey<T> key) {
      super(key);
   }

   public static <T> RegistryObject<T> of(ResourceKey<? extends Registry<T>> registry, ResourceLocation id) {
      return new RegistryObject<>(ResourceKey.create(registry, id));
   }

   public static <T> RegistryObject<T> of(ResourceKey<T> key) {
      return new RegistryObject<>(key);
   }
}
