package com.dragonminez.common.datagen;

import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.MainTags;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DMZEntityTypeTagGenerator extends EntityTypeTagsProvider {
   public DMZEntityTypeTagGenerator(PackOutput output, CompletableFuture<Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
      super(output, lookupProvider, "dragonminez", existingFileHelper);
   }

   protected void addTags(@NotNull Provider provider) {
      this.tag(MainTags.EntityTypes.FRIEZA_SOLDIERS)
         .add((EntityType)MainEntities.SAGA_FRIEZA_SOLDIER.get())
         .add((EntityType)MainEntities.SAGA_FRIEZA_SOLDIER2.get())
         .add((EntityType)MainEntities.SAGA_FRIEZA_SOLDIER3.get());
      this.tag(MainTags.EntityTypes.SAIBAMEN)
         .add((EntityType)MainEntities.SAGA_SAIBAMAN.get())
         .add((EntityType)MainEntities.SAGA_SAIBAMAN2.get())
         .add((EntityType)MainEntities.SAGA_SAIBAMAN3.get())
         .add((EntityType)MainEntities.SAGA_SAIBAMAN4.get())
         .add((EntityType)MainEntities.SAGA_SAIBAMAN5.get())
         .add((EntityType)MainEntities.SAGA_SAIBAMAN6.get());
      this.tag(MainTags.EntityTypes.RED_RIBBON_ROBOTS)
         .add((EntityType)MainEntities.RED_RIBBON_ROBOT1.get())
         .add((EntityType)MainEntities.RED_RIBBON_ROBOT2.get())
         .add((EntityType)MainEntities.RED_RIBBON_ROBOT3.get());
   }
}
