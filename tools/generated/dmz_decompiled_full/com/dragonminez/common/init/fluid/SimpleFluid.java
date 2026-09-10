package com.dragonminez.common.init.fluid;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidType.Properties;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class SimpleFluid extends FluidType {
   private final ResourceLocation stillTexture = ResourceLocation.withDefaultNamespace("block/water_still");
   private final ResourceLocation flowTexture = ResourceLocation.withDefaultNamespace("block/water_flow");
   private final ResourceLocation overlayTexture = ResourceLocation.withDefaultNamespace("block/water_overlay");
   private final int tintColor;
   private final Vector3f fogColor;
   private final int fogStart;
   private final int fogEnd;

   public SimpleFluid(int color, Properties properties) {
      super(properties);
      this.tintColor = this.toAlpha(color);
      this.fogColor = new Vector3f((float)(color >> 16 & 0xFF) / 255.0F, (float)(color >> 8 & 0xFF) / 255.0F, (float)(color & 0xFF) / 255.0F);
      this.fogStart = -8;
      this.fogEnd = 48;
   }

   private int toAlpha(int color) {
      int red = color >> 16 & 0xFF;
      int green = color >> 8 & 0xFF;
      int blue = color & 0xFF;
      return -1593835520 | red << 16 | green << 8 | blue;
   }

   public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
      consumer.accept(
         new IClientFluidTypeExtensions() {
            public ResourceLocation getStillTexture() {
               return SimpleFluid.this.stillTexture;
            }

            public ResourceLocation getFlowingTexture() {
               return SimpleFluid.this.flowTexture;
            }

            public ResourceLocation getOverlayTexture() {
               return SimpleFluid.this.overlayTexture;
            }

            public int getTintColor() {
               return SimpleFluid.this.tintColor;
            }

            public Vector3f modifyFogColor(
               Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor
            ) {
               return SimpleFluid.this.fogColor;
            }

            public void modifyFogRender(
               Camera camera, FogMode mode, float renderDistance, float partialTick, float nearDistance, float farDistance, FogShape shape
            ) {
               RenderSystem.setShaderFogStart((float)SimpleFluid.this.fogStart);
               RenderSystem.setShaderFogEnd((float)SimpleFluid.this.fogEnd);
            }
         }
      );
   }

   public boolean canSwim(Entity entity) {
      return true;
   }

   public boolean canDrownIn(LivingEntity entity) {
      return !(entity instanceof WaterAnimal);
   }

   @Nullable
   public PathType getBlockPathType(FluidState state, BlockGetter level, BlockPos pos, @Nullable Mob mob, boolean canFluidLog) {
      return PathType.WATER;
   }

   @Nullable
   public PathType getAdjacentBlockPathType(FluidState state, BlockGetter level, BlockPos pos, @Nullable Mob mob, PathType originalType) {
      return null;
   }

   public boolean supportsBoating(FluidState state, Boat boat) {
      return true;
   }

   public boolean canExtinguish(FluidState state, BlockGetter level, BlockPos pos) {
      return true;
   }
}
