package com.dragonminez.compat.util;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class LazyOptional<T> {
   private final Optional<T> value;
   private boolean valid = true;

   private LazyOptional(Optional<T> value) {
      this.value = value;
   }

   public static <T> LazyOptional<T> of(Supplier<T> supplier) {
      return new LazyOptional<>(Optional.ofNullable(supplier.get()));
   }

   public static <T> LazyOptional<T> empty() {
      return new LazyOptional<>(Optional.empty());
   }

   public void ifPresent(Consumer<? super T> consumer) {
      if (this.valid) {
         this.value.ifPresent(consumer);
      }
   }

   public <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
      return this.valid ? this.value.map(mapper) : Optional.empty();
   }

   public Optional<T> resolve() {
      return this.valid ? this.value : Optional.empty();
   }

   public boolean isPresent() {
      return this.valid && this.value.isPresent();
   }

   public T orElse(T other) {
      return this.valid ? this.value.orElse(other) : other;
   }

   public T orElseThrow(Supplier<? extends RuntimeException> exceptionSupplier) {
      if (this.valid && !this.value.isEmpty()) {
         return this.value.get();
      } else {
         throw (RuntimeException)exceptionSupplier.get();
      }
   }

   public <X> LazyOptional<X> cast() {
      return (LazyOptional<X>)this;
   }

   public void invalidate() {
      this.valid = false;
   }
}
