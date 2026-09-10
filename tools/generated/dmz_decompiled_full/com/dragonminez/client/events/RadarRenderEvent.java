package com.dragonminez.client.events;

import com.dragonminez.common.dragonball.DragonRadarDefinition;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.item.DragonRadarItem;
import com.dragonminez.server.world.dimension.NamekDimension;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre;
import org.joml.Quaternionf;

@EventBusSubscriber(
   modid = "dragonminez",
   value = {Dist.CLIENT},
   bus = Bus.GAME
)
public class RadarRenderEvent {
   private static final ResourceLocation RADAR_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/radar.png");
   private static List<BlockPos> clientEarthPositions = new ArrayList<>();
   private static List<BlockPos> clientNamekPositions = new ArrayList<>();
   private static Map<String, List<BlockPos>> clientPositionsBySet = new HashMap<>();

   @SubscribeEvent
   public static void onClientDisconnect(LoggingOut event) {
      clientEarthPositions = new ArrayList<>();
      clientNamekPositions = new ArrayList<>();
      clientPositionsBySet = new HashMap<>();
   }

   public static void updateRadarData(List<BlockPos> earth, List<BlockPos> namek, Map<String, List<BlockPos>> positionsBySet) {
      clientEarthPositions = earth;
      clientNamekPositions = namek;
      clientPositionsBySet = new HashMap<>();
      if (positionsBySet != null) {
         positionsBySet.forEach((setId, positions) -> clientPositionsBySet.put(setId, new ArrayList<>(positions)));
      }

      clientPositionsBySet.put("earth", earth);
      clientPositionsBySet.put("namek", namek);
   }

   @SubscribeEvent
   public static void onRenderGameOverlay(Pre event) {
      if (event.getName().getPath().equals("hotbar")) {
         Minecraft mc = Minecraft.getInstance();
         if (!mc.isPaused() && mc.player != null) {
            Player player = mc.player;
            Level level = player.level();
            boolean isOverworld = level.dimension().equals(Level.OVERWORLD);
            boolean isNamek = level.dimension().equals(NamekDimension.NAMEK_KEY);
            if (isOverworld || isNamek) {
               ItemStack mainHand = player.getMainHandItem();
               ItemStack offHand = player.getOffhandItem();
               Item earthRadarItem = (Item)MainItems.DBALL_RADAR_ITEM.get();
               Item namekRadarItem = (Item)MainItems.NAMEKDBALL_RADAR_ITEM.get();
               Item fusedRadarItem = (Item)MainItems.FUSED_DBALL_RADAR_ITEM.get();
               List<BlockPos> targets = null;
               int range = 75;
               boolean isMainHand = false;
               if (isOverworld && mainHand.getItem() == earthRadarItem) {
                  targets = clientEarthPositions;
                  range = getRadarRange(mainHand);
                  isMainHand = true;
               } else if (isNamek && mainHand.getItem() == namekRadarItem) {
                  targets = clientNamekPositions;
                  range = getRadarRange(mainHand);
                  isMainHand = true;
               } else if (isOverworld && offHand.getItem() == earthRadarItem) {
                  targets = clientEarthPositions;
                  range = getRadarRange(offHand);
                  isMainHand = false;
               } else if (isNamek && offHand.getItem() == namekRadarItem) {
                  targets = clientNamekPositions;
                  range = getRadarRange(offHand);
                  isMainHand = false;
               }

               if (targets == null && mainHand.getItem() == fusedRadarItem) {
                  targets = isOverworld ? clientEarthPositions : clientNamekPositions;
                  range = getRadarRange(mainHand);
                  isMainHand = true;
               } else if (targets == null && offHand.getItem() == fusedRadarItem) {
                  targets = isOverworld ? clientEarthPositions : clientNamekPositions;
                  range = getRadarRange(offHand);
                  isMainHand = false;
               }

               if (targets == null) {
                  if (mainHand.getItem() instanceof DragonRadarItem genericMainRadar) {
                     DragonRadarDefinition definition = genericMainRadar.getDefinition();
                     if (definition != null && definition.supportsDimension(level.dimension()) && definition.getBallSetId() != null) {
                        targets = clientPositionsBySet.getOrDefault(definition.getBallSetId(), List.of());
                        range = getRadarRange(mainHand);
                        isMainHand = true;
                     }
                  } else if (offHand.getItem() instanceof DragonRadarItem genericOffRadar) {
                     DragonRadarDefinition definition = genericOffRadar.getDefinition();
                     if (definition != null && definition.supportsDimension(level.dimension()) && definition.getBallSetId() != null) {
                        targets = clientPositionsBySet.getOrDefault(definition.getBallSetId(), List.of());
                        range = getRadarRange(offHand);
                        isMainHand = false;
                     }
                  }
               }

               if (targets != null) {
                  int radarSize = 140;
                  int centerY = mc.getWindow().getGuiScaledHeight() - radarSize - 10;
                  int centerX;
                  if (isMainHand) {
                     centerX = mc.getWindow().getGuiScaledWidth() - radarSize - 10;
                  } else {
                     centerX = 10;
                  }

                  boolean showProximity = DragonRadarItem.hasCompletedQuest(player, "bulma_proximity_hud");
                  renderRadar(event.getGuiGraphics(), player, targets, range, centerX, centerY, showProximity);
               }
            }
         }
      }
   }

   private static int getRadarRange(ItemStack stack) {
      CustomData custom = (CustomData)stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
      int r = custom.copyTag().getInt("RadarRange");
      return r == 0 ? 150 : r;
   }

   private static void renderRadar(GuiGraphics gui, Player player, List<BlockPos> targets, int range, int centerX, int centerY, boolean showProximity) {
      int textureW = 121;
      int textureH = 146;
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.setShaderTexture(0, RADAR_TEXTURE);
      gui.blit(RADAR_TEXTURE, centerX, centerY, 0, 0, textureW, textureH);
      int radarCenterX = centerX + 61;
      int radarCenterY = centerY + 87;
      double nearestDist = Double.MAX_VALUE;

      for (BlockPos pos : targets) {
         double dx = (double)pos.getX() - player.getX();
         double dz = (double)pos.getZ() - player.getZ();
         double dist = Math.sqrt(dx * dx + dz * dz);
         if (dist < nearestDist) {
            nearestDist = dist;
         }

         double angleToTarget = Math.atan2(dz, dx);
         double playerYaw = Math.toRadians((double)player.getYRot()) + (Math.PI / 2);
         double finalAngle = angleToTarget - playerYaw;
         double renderAngle = finalAngle - (Math.PI / 2);
         if (dist <= (double)range) {
            double scaledDist = dist / (double)range * 50.0;
            int dotX = (int)((double)radarCenterX + scaledDist * Math.cos(renderAngle));
            int dotY = (int)((double)radarCenterY + scaledDist * Math.sin(renderAngle));
            gui.blit(RADAR_TEXTURE, dotX - 3, dotY - 3, 130, 0, 6, 6);
         } else {
            int arrowRadius = 50;
            int arrowX = (int)((double)radarCenterX + (double)arrowRadius * Math.cos(renderAngle));
            int arrowY = (int)((double)radarCenterY + (double)arrowRadius * Math.sin(renderAngle));
            float rotation = (float)renderAngle + (float) (Math.PI / 2);
            gui.pose().pushPose();
            gui.pose().translate((float)arrowX, (float)arrowY, 0.0F);
            gui.pose().mulPose(new Quaternionf().rotationZ(rotation));
            gui.pose().translate(-3.5F, -3.0F, 0.0F);
            gui.blit(RADAR_TEXTURE, 0, 0, 130, 8, 7, 6);
            gui.pose().popPose();
         }
      }

      if (showProximity && nearestDist != Double.MAX_VALUE) {
         Font font = Minecraft.getInstance().font;
         Component text = Component.translatable("gui.dmzradar.nearest", new Object[]{(int)Math.round(nearestDist)});
         int textX = radarCenterX - font.width(text) / 2;
         int textY = centerY + textureH - 18;
         gui.drawString(font, text, textX, textY, -2513855, true);
      }

      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }
}
