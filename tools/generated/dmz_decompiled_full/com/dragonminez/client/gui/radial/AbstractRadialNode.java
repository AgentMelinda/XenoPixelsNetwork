package com.dragonminez.client.gui.radial;

import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.stats.StatsData;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public abstract class AbstractRadialNode implements RadialNode {
   public static final ResourceLocation PLACEHOLDER = icon("placeholder");
   public static final int GREEN = 2883328;
   public static final int RED = 16718592;
   public float animScale = 0.0F;
   public float animExpand = 0.0F;
   public float animHighlight = 0.0F;
   private List<RadialNode> cachedChildren;

   @Override
   public List<RadialNode> children(StatsData stats) {
      if (this.cachedChildren == null) {
         this.cachedChildren = this.buildChildren(stats);
      }

      return this.cachedChildren;
   }

   protected List<RadialNode> buildChildren(StatsData stats) {
      return List.of();
   }

   public void invalidate() {
      this.cachedChildren = null;
   }

   protected static ResourceLocation icon(String name) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/radial/" + name + ".png");
   }

   protected static ResourceLocation iconForFormType(String type) {
      if (type == null) {
         return PLACEHOLDER;
      } else {
         String t = type.toLowerCase(Locale.ROOT);
         if (t.contains("legendary")) {
            return icon("legendaryforms");
         } else if (t.contains("super")) {
            return icon("superforms");
         } else if (t.contains("god")) {
            return icon("godforms");
         } else if (t.contains("android")) {
            return icon("androidforms");
         } else if (t.contains("kaioken")) {
            return icon("kaioken");
         } else if (t.contains("ultimate")) {
            return icon("ultimate");
         } else {
            return Minecraft.getInstance().getResourceManager().getResource(icon(t)).isPresent() ? icon(t) : PLACEHOLDER;
         }
      }
   }

   protected static int tintOf(FormConfig.FormData formData) {
      if (formData == null) {
         return -1;
      } else {
         float[] rgb = formData.getRgbAuraColor();
         if (rgb != null && rgb.length >= 3) {
            int r = Math.round(rgb[0] * 255.0F);
            int g = Math.round(rgb[1] * 255.0F);
            int b = Math.round(rgb[2] * 255.0F);
            return r << 16 | g << 8 | b;
         } else {
            return -1;
         }
      }
   }

   protected void playToggle(boolean turnedOn) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.playSound(turnedOn ? (SoundEvent)MainSounds.SWITCH_ON.get() : (SoundEvent)MainSounds.SWITCH_OFF.get(), 1.0F, 1.0F);
      }
   }

   protected void playClick() {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
   }
}
