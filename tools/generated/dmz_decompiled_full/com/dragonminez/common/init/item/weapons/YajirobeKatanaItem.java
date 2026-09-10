package com.dragonminez.common.init.item.weapons;

import com.dragonminez.common.init.item.weapons.render.YajirobeKatanaRenderer;
import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class YajirobeKatanaItem extends WeaponItem implements GeoItem {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

   public YajirobeKatanaItem() {
      super(12, -2.6F, 500, 15, "yajirobe_katana");
   }

   public void registerControllers(ControllerRegistrar controllers) {
   }

   public void initializeClient(Consumer<IClientItemExtensions> consumer) {
      consumer.accept(new IClientItemExtensions() {
         private YajirobeKatanaRenderer renderer;

         public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (this.renderer == null) {
               this.renderer = new YajirobeKatanaRenderer();
            }

            return this.renderer;
         }
      });
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
