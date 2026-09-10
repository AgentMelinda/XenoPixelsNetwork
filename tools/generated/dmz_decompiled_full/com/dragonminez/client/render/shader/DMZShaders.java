package com.dragonminez.client.render.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(
   modid = "dragonminez",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public class DMZShaders {
   public static ShaderInstance auraShader;
   public static ShaderInstance lightningShader;
   public static ShaderInstance outlineShader;
   public static ShaderInstance outlineMaskTexShader;
   public static ShaderInstance ki3dShader;

   @SubscribeEvent
   public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
      event.registerShader(
         new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("dragonminez", "aura"), DefaultVertexFormat.POSITION_TEX),
         shaderInstance -> auraShader = shaderInstance
      );
      event.registerShader(
         new ShaderInstance(
            event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("dragonminez", "lightning"), DefaultVertexFormat.POSITION_COLOR_NORMAL
         ),
         shaderInstance -> lightningShader = shaderInstance
      );
      event.registerShader(
         new ShaderInstance(
            event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("dragonminez", "transformation_mask"), DefaultVertexFormat.NEW_ENTITY
         ),
         shaderInstance -> outlineShader = shaderInstance
      );
      event.registerShader(
         new ShaderInstance(
            event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("dragonminez", "transformation_mask_tex"), DefaultVertexFormat.NEW_ENTITY
         ),
         shaderInstance -> outlineMaskTexShader = shaderInstance
      );
      event.registerShader(
         new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("dragonminez", "kiattack"), DefaultVertexFormat.NEW_ENTITY),
         shaderInstance -> ki3dShader = shaderInstance
      );
   }
}
