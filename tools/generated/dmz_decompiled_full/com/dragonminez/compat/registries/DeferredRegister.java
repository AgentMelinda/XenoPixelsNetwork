package com.dragonminez.compat.registries;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;

public final class DeferredRegister<T> {
   private final net.neoforged.neoforge.registries.DeferredRegister<T> delegate;
   private final ResourceKey<? extends Registry<T>> registryKey;
   private final String namespace;

   private DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
      this.registryKey = Objects.requireNonNull(registryKey, "registryKey");
      this.namespace = Objects.requireNonNull(namespace, "namespace");
      this.delegate = net.neoforged.neoforge.registries.DeferredRegister.create(registryKey, namespace);
   }

   public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> registry, String namespace) {
      return new DeferredRegister<>(registry, namespace);
   }

   public static <T> DeferredRegister<T> create(Registry<T> registry, String namespace) {
      return new DeferredRegister<>(registry.key(), namespace);
   }

   public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> supplier) {
      this.delegate.register(name, supplier);
      ResourceKey<I> key = ResourceKey.create(this.registryKey, ResourceLocation.fromNamespaceAndPath(this.namespace, name));
      return RegistryObject.of(key);
   }

   public void register(IEventBus bus) {
      this.delegate.register(bus);
   }

   public String getNamespace() {
      return this.namespace;
   }

   public net.neoforged.neoforge.registries.DeferredRegister<T> unwrap() {
      return this.delegate;
   }
}
