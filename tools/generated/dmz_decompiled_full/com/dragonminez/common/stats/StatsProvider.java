package com.dragonminez.common.stats;

import com.dragonminez.compat.capabilities.Capability;
import com.dragonminez.compat.capabilities.ICapabilityProvider;
import com.dragonminez.compat.util.LazyOptional;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StatsProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
   public static final ResourceLocation ID = ResourceLocation.parse("dragonminez");
   private final StatsData data;
   private final LazyOptional<StatsData> optional;

   public StatsProvider(Player player) {
      this.data = new StatsData(player);
      this.optional = LazyOptional.of(() -> this.data);
   }

   public static StatsProvider getOrCreate(Player player) {
      return (StatsProvider)player.getData((AttachmentType)StatsCapability.PLAYER_STATS.get());
   }

   public static void remove(Player player) {
   }

   @NotNull
   @Override
   public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
      return cap == StatsCapability.INSTANCE ? this.optional.cast() : LazyOptional.empty();
   }

   @NotNull
   public static <T> LazyOptional<T> get(Capability<T> cap, Entity entity) {
      if (entity instanceof Player player) {
         return cap != StatsCapability.INSTANCE ? LazyOptional.empty() : getOrCreate(player).getCapability(cap, null);
      } else {
         return LazyOptional.empty();
      }
   }

   void invalidate() {
      this.optional.invalidate();
   }

   public CompoundTag serializeNBT(Provider provider) {
      return this.data.save();
   }

   public void deserializeNBT(Provider provider, CompoundTag nbt) {
      try {
         this.data.load(nbt);
      } catch (ClassNotFoundException var4) {
         throw new RuntimeException(var4);
      }
   }
}
