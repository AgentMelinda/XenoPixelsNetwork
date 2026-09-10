package com.dragonminez.mixin.sable;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class SableMixinPlugin implements IMixinConfigPlugin {
   private static final String SABLE_MARKER = "dev.ryanhcode.sable.Sable";
   private boolean sablePresent;

   public void onLoad(String mixinPackage) {
      try {
         Class.forName("dev.ryanhcode.sable.Sable", false, this.getClass().getClassLoader());
         this.sablePresent = true;
      } catch (NoClassDefFoundError | ClassNotFoundException var3) {
         this.sablePresent = false;
      }
   }

   public String getRefMapperConfig() {
      return null;
   }

   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      return this.sablePresent;
   }

   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   public List<String> getMixins() {
      return null;
   }

   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }
}
