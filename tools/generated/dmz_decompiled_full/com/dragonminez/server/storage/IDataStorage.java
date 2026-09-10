package com.dragonminez.server.storage;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public interface IDataStorage {
   void init();

   void shutdown();

   CompoundTag loadData(UUID var1);

   boolean saveData(UUID var1, String var2, CompoundTag var3);

   String getName();
}
