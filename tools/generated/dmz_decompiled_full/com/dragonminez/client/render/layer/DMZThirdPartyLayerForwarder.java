package com.dragonminez.client.render.layer;

import com.dragonminez.client.render.VanillaModelSync;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.mixin.client.LivingEntityRendererAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.BeeStingerLayer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.Deadmau5EarsLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.SpinAttackEffectLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class DMZThirdPartyLayerForwarder<T extends AbstractClientPlayer & GeoAnimatable> extends GeoRenderLayer<T> {
   private static final Set<Class<?>> VANILLA_LAYER_CLASSES = Set.of(
      HumanoidArmorLayer.class,
      ItemInHandLayer.class,
      PlayerItemInHandLayer.class,
      ArrowLayer.class,
      Deadmau5EarsLayer.class,
      CapeLayer.class,
      CustomHeadLayer.class,
      ElytraLayer.class,
      ParrotOnShoulderLayer.class,
      SpinAttackEffectLayer.class,
      BeeStingerLayer.class
   );

   public DMZThirdPartyLayerForwarder(GeoRenderer<T> renderer) {
      super(renderer);
   }

   public void renderForBone(
      PoseStack poseStack,
      T animatable,
      GeoBone bone,
      RenderType renderType,
      MultiBufferSource bufferSource,
      VertexConsumer buffer,
      float partialTick,
      int packedLight,
      int packedOverlay
   ) {
      if ("root".equals(bone.getName())) {
         StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, animatable).orElse(new StatsData(animatable));
         if (!stats.getCharacter().isOozaruCached()) {
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            EntityRenderer<? extends Player> vanillaRenderer = (EntityRenderer<? extends Player>)dispatcher.getSkinMap().get(animatable.getSkin().model().id());
            if (vanillaRenderer instanceof PlayerRenderer playerRenderer) {
               List<RenderLayer<?, ?>> layers;
               try {
                  layers = ((LivingEntityRendererAccessor)playerRenderer).dragonminez$getLayers();
               } catch (Exception var30) {
                  return;
               }

               if (layers != null && !layers.isEmpty()) {
                  BakedGeoModel geoModel = this.getRenderer().getGeoModel().getBakedModel(this.getRenderer().getGeoModel().getModelResource(animatable));
                  PlayerModel<AbstractClientPlayer> vanillaModel = (PlayerModel<AbstractClientPlayer>)playerRenderer.getModel();
                  if (geoModel != null) {
                     VanillaModelSync.sync(geoModel, vanillaModel, animatable);
                     vanillaModel.attackTime = animatable.getAttackAnim(partialTick);
                     vanillaModel.riding = animatable.isPassenger();
                     vanillaModel.young = false;
                     float bodyYaw = Mth.rotLerp(partialTick, animatable.yBodyRotO, animatable.yBodyRot);
                     float headYaw = Mth.rotLerp(partialTick, animatable.yHeadRotO, animatable.yHeadRot);
                     float netHeadYaw = headYaw - bodyYaw;
                     float headPitch = Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot());
                     float limbSwing = animatable.walkAnimation.position(partialTick);
                     float limbSwingAmount = animatable.walkAnimation.speed(partialTick);
                     float ageInTicks = (float)animatable.tickCount + partialTick;
                     poseStack.pushPose();
                     poseStack.scale(-1.0F, -1.0F, 1.0F);
                     poseStack.translate(0.0F, -1.501F, 0.0F);

                     for (RenderLayer layer : layers) {
                        Class<?> layerClass = layer.getClass();
                        if (!VANILLA_LAYER_CLASSES.contains(layerClass)) {
                           String className = layerClass.getName().toLowerCase();
                           if (!className.contains("cosmeticarmor") && !className.contains("cosarmor")) {
                              poseStack.pushPose();

                              try {
                                 layer.render(
                                    poseStack,
                                    bufferSource,
                                    packedLight,
                                    animatable,
                                    limbSwing,
                                    limbSwingAmount,
                                    partialTick,
                                    ageInTicks,
                                    netHeadYaw,
                                    headPitch
                                 );
                              } catch (Exception var29) {
                              }

                              poseStack.popPose();
                           }
                        }
                     }

                     if (renderType != null) {
                        bufferSource.getBuffer(renderType);
                     }

                     poseStack.popPose();
                  }
               }
            }
         }
      }
   }
}
