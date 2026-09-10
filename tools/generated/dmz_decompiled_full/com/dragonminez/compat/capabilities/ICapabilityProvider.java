package com.dragonminez.compat.capabilities;

import com.dragonminez.compat.util.LazyOptional;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ICapabilityProvider {
   @NotNull
   <T> LazyOptional<T> getCapability(@NotNull Capability<T> var1, @Nullable Direction var2);

   @NotNull
   default <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap) {
      return this.getCapability(cap, null);
   }
}
