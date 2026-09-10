package com.dragonminez.client.gui.radial.nodes;

import com.dragonminez.client.gui.radial.AbstractRadialNode;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.SelectKiWeaponC2S;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class KiWeaponNode extends AbstractRadialNode {
   private final String type;
   private final int tint;

   public KiWeaponNode(String type) {
      this.type = type;
      StatsData stats = null;
      if (Minecraft.getInstance().player != null) {
         stats = StatsProvider.get(StatsCapability.INSTANCE, Minecraft.getInstance().player).orElse(null);
      }

      if (stats != null) {
         if (stats.getCharacter().hasActiveStackForm() && stats.getCharacter().getActiveStackFormData() != null) {
            this.tint = tintOf(stats.getCharacter().getActiveStackFormData().getRgbAuraColor());
         } else if (stats.getCharacter().hasActiveForm() && stats.getCharacter().getActiveFormData() != null) {
            this.tint = tintOf(stats.getCharacter().getActiveFormData().getRgbAuraColor());
         } else {
            this.tint = tintOf(stats.getCharacter().getRgbAuraColor());
         }
      } else {
         this.tint = -1;
      }
   }

   @Override
   public Component label(StatsData stats) {
      return Component.translatable("skill.dragonminez.kiweapon." + this.type);
   }

   @Override
   public ResourceLocation icon(StatsData stats) {
      return icon("kiweapon");
   }

   @Override
   public int iconTint(StatsData stats) {
      return this.tint;
   }

   protected static int tintOf(float[] rgb) {
      if (rgb != null && rgb.length >= 3) {
         int r = Math.round(rgb[0] * 255.0F);
         int g = Math.round(rgb[1] * 255.0F);
         int b = Math.round(rgb[2] * 255.0F);
         return r << 16 | g << 8 | b;
      } else {
         return -1;
      }
   }

   @Override
   public boolean active(StatsData stats) {
      return stats.getSkills().isSkillActive("kimanipulation") && this.type.equalsIgnoreCase(stats.getStatus().getKiWeaponType());
   }

   @Override
   public int labelColor(StatsData stats) {
      return this.active(stats) ? 2883328 : 16718592;
   }

   @Override
   public void onSelect(StatsData stats) {
      boolean wasActive = this.active(stats);
      NetworkHandler.sendToServer(new SelectKiWeaponC2S(this.type));
      this.playToggle(!wasActive);
   }
}
