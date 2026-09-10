package com.dragonminez.client.render.layer;

import com.dragonminez.client.model.DMZPlayerModel;
import com.dragonminez.client.render.compat.CosmeticArmorCompat;
import com.dragonminez.client.util.ArmorTextureResolver;
import com.dragonminez.client.util.SkinGathererProvider;
import com.dragonminez.common.init.armor.DbzArmorItem;
import com.dragonminez.common.init.armor.DbzArmorTextured;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class DMZCustomArmorLayer<T extends AbstractClientPlayer & GeoAnimatable> extends GeoRenderLayer<T> {
   private static final ResourceLocation MAJIN_ARMOR_MODEL = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/armor/armormajinfat.geo.json");
   private static final ResourceLocation MAJIN_SLIM_ARMOR_MODEL = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/armor/armormajinslim.geo.json");
   private static final ResourceLocation OOZARU_ARMOR_MODEL = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/armor/armoroozaru.geo.json");
   private static final Set<String> SLIM_SUPPORTED_MODELS = Set.of(
      "majin_evil", "majin_kid", "majin_super", "majin_ultra", "majin", "saiyan", "human", "ssj4gt", "ssj4d"
   );
   private static final Set<String> EXCLUDED_BONES = Set.of("tail1", "tail1m", "arm", "arm2", "arm3");

   public DMZCustomArmorLayer(GeoRenderer<T> entityRendererIn) {
      super(entityRendererIn);
   }

   public void renderForBone(
      PoseStack poseStack,
      T animatable,
      GeoBone playerBone,
      RenderType renderType,
      MultiBufferSource bufferSource,
      VertexConsumer buffer,
      float partialTick,
      int packedLight,
      int packedOverlay
   ) {
      if (!animatable.isSpectator() && "body".equals(playerBone.getName())) {
         ItemStack stack = this.resolveChestArmorStack(animatable);
         if (!stack.isEmpty() && stack.getItem() instanceof ArmorItem) {
            StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, animatable).orElse(new StatsData(animatable));
            if (!stats.getCharacter().getArmored()) {
               DMZCustomArmorLayer.ArmorRenderContext ctx = this.resolveArmorContext(stats, stack);
               if (ctx.shouldRender()) {
                  if (ctx.isDbzArmor()) {
                     ResourceLocation texture = this.getDbzArmorTexture((DbzArmorTextured)stack.getItem(), stack);
                     float translateY = this.resolveCustomArmorTranslateY(ctx);
                     float inflation = ctx.isOozaruTarget() ? 1.021F : 1.035F;
                     poseStack.pushPose();
                     poseStack.translate(0.0F, translateY, 0.0F);
                     GeoBone armorBody = this.getChild(playerBone, "armorBody");
                     GeoBone armorLeggingsBody = this.getChild(playerBone, "armorLeggingsBody");
                     GeoBone bodyLayer = this.getChild(playerBone, "body_layer");
                     GeoBone boobasBone = this.getChild(playerBone, "boobas");
                     boolean ob1 = armorBody != null && armorBody.isHidden();
                     boolean ob2 = armorLeggingsBody != null && armorLeggingsBody.isHidden();
                     boolean ob3 = bodyLayer != null && bodyLayer.isHidden();
                     if (armorBody != null) {
                        armorBody.setHidden(true);
                     }

                     if (armorLeggingsBody != null) {
                        armorLeggingsBody.setHidden(true);
                     }

                     if (bodyLayer != null) {
                        bodyLayer.setHidden(true);
                     }

                     float bX = 1.0F;
                     float bY = 1.0F;
                     float bZ = 1.0F;
                     if (boobasBone != null) {
                        bX = boobasBone.getScaleX();
                        bY = boobasBone.getScaleY();
                        bZ = boobasBone.getScaleZ();
                        boobasBone.setScaleX(bX * 1.03F);
                        boobasBone.setScaleY(bY * 1.06F);
                        boobasBone.setScaleZ(bZ * 1.15F);
                     }

                     this.renderRootBoneInflated(playerBone, poseStack, bufferSource, animatable, texture, partialTick, packedLight, inflation);
                     if (armorBody != null) {
                        armorBody.setHidden(ob1);
                     }

                     if (armorLeggingsBody != null) {
                        armorLeggingsBody.setHidden(ob2);
                     }

                     if (bodyLayer != null) {
                        bodyLayer.setHidden(ob3);
                     }

                     if (boobasBone != null) {
                        boobasBone.setScaleX(bX);
                        boobasBone.setScaleY(bY);
                        boobasBone.setScaleZ(bZ);
                     }

                     poseStack.popPose();
                     if (renderType != null) {
                        bufferSource.getBuffer(renderType);
                     }
                  } else {
                     ResourceLocation targetModelLoc = ctx.isOozaruTarget()
                        ? OOZARU_ARMOR_MODEL
                        : (ctx.isSlimTarget() ? MAJIN_SLIM_ARMOR_MODEL : MAJIN_ARMOR_MODEL);
                     BakedGeoModel vanillaArmorModel = this.getGeoModel().getBakedModel(targetModelLoc);
                     if (vanillaArmorModel != null) {
                        GeoBone armorBodyBone = (GeoBone)vanillaArmorModel.getBone("body").orElse(null);
                        if (armorBodyBone == null && !vanillaArmorModel.topLevelBones().isEmpty()) {
                           armorBodyBone = (GeoBone)vanillaArmorModel.topLevelBones().get(0);
                        }

                        if (armorBodyBone != null) {
                           ResourceLocation texturex = this.getVanillaArmorTexture(animatable, stack, EquipmentSlot.CHEST, null);
                           GeoBone armorBoobas = this.findBoneDeep(armorBodyBone, "boobas");
                           float boobFactor = this.resolveBoobScale(stats);
                           float savedBoobX = 1.0F;
                           float savedBoobY = 1.0F;
                           float savedBoobZ = 1.0F;
                           if (armorBoobas != null) {
                              savedBoobX = armorBoobas.getScaleX();
                              savedBoobY = armorBoobas.getScaleY();
                              savedBoobZ = armorBoobas.getScaleZ();
                              float[] axis = DMZPlayerModel.computeBoobAxisScale(boobFactor);
                              armorBoobas.setScaleX(savedBoobX * axis[0] * 1.03F);
                              armorBoobas.setScaleY(savedBoobY * axis[1] * 1.06F);
                              armorBoobas.setScaleZ(savedBoobZ * axis[2] * 1.15F);
                           }

                           float translateYx = this.resolveCustomArmorTranslateY(ctx);
                           poseStack.pushPose();
                           poseStack.translate(0.0F, translateYx, 0.0F);
                           this.renderRootBoneInflated(armorBodyBone, poseStack, bufferSource, animatable, texturex, partialTick, packedLight, 1.05F);
                           poseStack.popPose();
                           if (stack.has(DataComponents.DYED_COLOR) || DyedItemColor.getOrDefault(stack, -1) != -1) {
                              ResourceLocation overlayTex = this.getVanillaArmorTexture(animatable, stack, EquipmentSlot.CHEST, "overlay");
                              poseStack.pushPose();
                              poseStack.translate(0.0F, translateYx, 0.0F);
                              this.renderRootBoneInflated(armorBodyBone, poseStack, bufferSource, animatable, overlayTex, partialTick, packedLight, 1.05F);
                              poseStack.popPose();
                           }

                           if (armorBoobas != null) {
                              armorBoobas.setScaleX(savedBoobX);
                              armorBoobas.setScaleY(savedBoobY);
                              armorBoobas.setScaleZ(savedBoobZ);
                           }

                           if (renderType != null) {
                              bufferSource.getBuffer(renderType);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private float resolveBoobScale(StatsData stats) {
      Character c = stats.getCharacter();
      String gender = c.getGender() != null ? c.getGender().toLowerCase() : "";
      boolean isFemale = gender.equals("female") || c.getBodyType() == 1;
      return isFemale ? Math.max(0.75F, Math.min(1.25F, c.getBoobScale())) : 1.0F;
   }

   private ItemStack resolveChestArmorStack(T animatable) {
      ItemStack stack = animatable.getItemBySlot(EquipmentSlot.CHEST);
      if (CosmeticArmorCompat.isLoaded()) {
         ItemStack cosmeticStack = CosmeticArmorCompat.getCosmeticStack(animatable, EquipmentSlot.CHEST);
         if (cosmeticStack != null) {
            if (cosmeticStack.isEmpty()) {
               return ItemStack.EMPTY;
            }

            stack = cosmeticStack;
         }
      }

      return stack;
   }

   private DMZCustomArmorLayer.ArmorRenderContext resolveArmorContext(StatsData stats, ItemStack stack) {
      Character character = stats.getCharacter();
      String raceName = character.getRaceName().toLowerCase();
      String gender = character.getGender().toLowerCase();
      int bodyType = character.getBodyType();
      String logicKey = character.getRenderLogicKey();
      ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
      boolean isVanilla = itemKey != null && "minecraft".equals(itemKey.getNamespace());
      boolean isDbzArmor = stack.getItem() instanceof DbzArmorTextured;
      boolean isPothala = stack.getDescriptionId().contains("pothala");
      if (!isPothala && (isVanilla || isDbzArmor)) {
         boolean shouldRender = false;
         boolean isSlimTarget = false;
         boolean isOozaruTarget = false;
         boolean isMajinGordoTarget = false;
         if (character.isOozaruCached() || logicKey.equals("oozaru")) {
            shouldRender = true;
            isOozaruTarget = true;
         } else if (SLIM_SUPPORTED_MODELS.contains(logicKey) && gender.equals("female")) {
            shouldRender = true;
            isSlimTarget = true;
         } else if (!logicKey.contains("buffed")
            && !logicKey.contains("frostdemon_fp")
            && !logicKey.contains("majin_ultra")
            && !logicKey.contains("namekian_orange")
            && !logicKey.contains("bioandroid_ultra")
            && !logicKey.contains("ssj4gt")
            && !logicKey.contains("ssj4d")
            && !logicKey.contains("frostdemon_fifth")
            && !logicKey.contains("frostdemon_metalcore")
            && !logicKey.contains("namekian_buffed")
            && !logicKey.contains("4arms")
            && !logicKey.contains("bioandroid_xeno")
            && !logicKey.equals("janemba_super")) {
            if (logicKey.equals("majin") && gender.equals("male") && bodyType != 2) {
               shouldRender = true;
               isMajinGordoTarget = true;
            } else if (logicKey.equals("janemba_fat")) {
               shouldRender = true;
               isMajinGordoTarget = true;
            } else if (SkinGathererProvider.modelFamily(logicKey).equals("custom") && isDbzArmor) {
               shouldRender = true;
            }
         } else if (isDbzArmor) {
            shouldRender = true;
         }

         return new DMZCustomArmorLayer.ArmorRenderContext(shouldRender, isSlimTarget, isOozaruTarget, isMajinGordoTarget, isDbzArmor);
      } else {
         return new DMZCustomArmorLayer.ArmorRenderContext(false, false, false, false, isDbzArmor);
      }
   }

   private float resolveCustomArmorTranslateY(DMZCustomArmorLayer.ArmorRenderContext ctx) {
      return !ctx.isOozaruTarget() && !ctx.isMajinGordoTarget() ? 0.001F : 0.03F;
   }

   private void renderBaseArmorBodyFromPlayerBone(
      GeoBone playerBodyBone, PoseStack poseStack, MultiBufferSource bufferSource, T animatable, ResourceLocation texture, float partialTick, int packedLight
   ) {
      this.renderBaseArmorPiece(playerBodyBone, "armorBody", poseStack, bufferSource, animatable, texture, partialTick, packedLight);
      this.renderBaseArmorPiece(playerBodyBone, "armorBody2", poseStack, bufferSource, animatable, texture, partialTick, packedLight);
      this.renderBaseArmorPiece(playerBodyBone, "armorLeggingsBody", poseStack, bufferSource, animatable, texture, partialTick, packedLight);
   }

   private void renderBaseArmorPiece(
      GeoBone playerBodyBone,
      String boneName,
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      T animatable,
      ResourceLocation texture,
      float partialTick,
      int packedLight
   ) {
      GeoBone armorPiece = this.getChild(playerBodyBone, boneName);
      if (armorPiece != null) {
         boolean wasHidden = armorPiece.isHidden();
         if (wasHidden) {
            armorPiece.setHidden(false);
         }

         DMZCustomArmorLayer.VisibilityState parentVisibility = this.unhideParentChain(armorPiece);
         this.renderChildBoneInflated(armorPiece, poseStack, bufferSource, animatable, texture, partialTick, packedLight, 1.1F);
         this.restoreParentChain(parentVisibility);
         if (wasHidden) {
            armorPiece.setHidden(true);
         }
      }
   }

   private DMZCustomArmorLayer.VisibilityState unhideParentChain(GeoBone bone) {
      List<GeoBone> parents = new ArrayList<>();
      List<Boolean> hiddenStates = new ArrayList<>();

      for (GeoBone parent = bone.getParent(); parent != null; parent = parent.getParent()) {
         parents.add(parent);
         hiddenStates.add(parent.isHidden());
         parent.setHidden(false);
      }

      return new DMZCustomArmorLayer.VisibilityState(parents, hiddenStates);
   }

   private void restoreParentChain(DMZCustomArmorLayer.VisibilityState state) {
      for (int i = 0; i < state.parents().size(); i++) {
         state.parents().get(i).setHidden(state.hiddenStates().get(i));
      }
   }

   private GeoBone getChild(GeoBone parent, String name) {
      for (GeoBone child : parent.getChildBones()) {
         if (child.getName().equals(name)) {
            return child;
         }
      }

      return null;
   }

   private void renderRootBoneInflated(
      GeoBone targetBone,
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      T animatable,
      ResourceLocation texture,
      float partialTick,
      int packedLight,
      float inflation
   ) {
      float rotX = targetBone.getRotX();
      float rotY = targetBone.getRotY();
      float rotZ = targetBone.getRotZ();
      float posX = targetBone.getPosX();
      float posY = targetBone.getPosY();
      float posZ = targetBone.getPosZ();
      float scaleX = targetBone.getScaleX();
      float scaleY = targetBone.getScaleY();
      float scaleZ = targetBone.getScaleZ();
      targetBone.setRotX(0.0F);
      targetBone.setRotY(0.0F);
      targetBone.setRotZ(0.0F);
      targetBone.setPosX(0.0F);
      targetBone.setPosY(0.0F);
      targetBone.setPosZ(0.0F);
      targetBone.setScaleX(inflation);
      targetBone.setScaleY(inflation);
      targetBone.setScaleZ(inflation);
      List<GeoBone> excludedFound = new ArrayList<>();
      List<Boolean> excludedHidden = new ArrayList<>();

      for (String name : EXCLUDED_BONES) {
         GeoBone bone = this.findBoneDeep(targetBone, name);
         if (bone != null) {
            excludedFound.add(bone);
            excludedHidden.add(bone.isHidden());
            bone.setHidden(true);
         }
      }

      RenderType armorRenderType = RenderType.armorCutoutNoCull(texture);
      this.getRenderer()
         .renderRecursively(
            poseStack,
            animatable,
            targetBone,
            armorRenderType,
            bufferSource,
            bufferSource.getBuffer(armorRenderType),
            true,
            partialTick,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            -1
         );

      for (int i = 0; i < excludedFound.size(); i++) {
         excludedFound.get(i).setHidden(excludedHidden.get(i));
      }

      targetBone.setRotX(rotX);
      targetBone.setRotY(rotY);
      targetBone.setRotZ(rotZ);
      targetBone.setPosX(posX);
      targetBone.setPosY(posY);
      targetBone.setPosZ(posZ);
      targetBone.setScaleX(scaleX);
      targetBone.setScaleY(scaleY);
      targetBone.setScaleZ(scaleZ);
   }

   private GeoBone findBoneDeep(GeoBone parent, String name) {
      for (GeoBone child : parent.getChildBones()) {
         if (child.getName().equals(name)) {
            return child;
         }

         GeoBone found = this.findBoneDeep(child, name);
         if (found != null) {
            return found;
         }
      }

      return null;
   }

   private void renderChildBoneInflated(
      GeoBone targetBone,
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      T animatable,
      ResourceLocation texture,
      float partialTick,
      int packedLight,
      float inflation
   ) {
      float scaleX = targetBone.getScaleX();
      float scaleY = targetBone.getScaleY();
      float scaleZ = targetBone.getScaleZ();
      targetBone.setScaleX(scaleX * inflation);
      targetBone.setScaleY(scaleY * inflation);
      targetBone.setScaleZ(scaleZ * inflation);
      RenderType armorRenderType = RenderType.armorCutoutNoCull(texture);
      this.getRenderer()
         .renderRecursively(
            poseStack,
            animatable,
            targetBone,
            armorRenderType,
            bufferSource,
            bufferSource.getBuffer(armorRenderType),
            true,
            partialTick,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            -1
         );
      targetBone.setScaleX(scaleX);
      targetBone.setScaleY(scaleY);
      targetBone.setScaleZ(scaleZ);
   }

   private ResourceLocation getDbzArmorTexture(DbzArmorTextured item, ItemStack stack) {
      String modId = "dragonminez";
      if (stack.getItem() instanceof DbzArmorItem dbzItem) {
         modId = dbzItem.getModId();
      }

      return ArmorTextureResolver.resolve(modId, item.getItemId(), EquipmentSlot.CHEST, stack);
   }

   private ResourceLocation getVanillaArmorTexture(LivingEntity entity, ItemStack stack, EquipmentSlot slot, String type) {
      ArmorItem item = (ArmorItem)stack.getItem();
      String materialName = item.getMaterial().unwrapKey().map(k -> k.location().getPath()).orElse("unknown");
      String domain = "minecraft";
      if (materialName.contains(":")) {
         String[] split = materialName.split(":", 2);
         domain = split[0];
         materialName = split[1];
      } else {
         ResourceLocation itemRegistryName = BuiltInRegistries.ITEM.getKey(item);
         if (itemRegistryName != null) {
            domain = itemRegistryName.getNamespace();
         }
      }

      String typeSuffix = type != null && !type.isEmpty() ? "_" + type : "";
      String textureLocation = String.format("%s:textures/models/armor/%s_layer_1%s.png", domain, materialName, typeSuffix);
      return ResourceLocation.parse(textureLocation);
   }

   private static record ArmorRenderContext(boolean shouldRender, boolean isSlimTarget, boolean isOozaruTarget, boolean isMajinGordoTarget, boolean isDbzArmor) {
   }

   private static record VisibilityState(List<GeoBone> parents, List<Boolean> hiddenStates) {
   }
}
