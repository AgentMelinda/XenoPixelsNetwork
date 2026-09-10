package com.dragonminez.client.gui.utilitymenu;

import lombok.Generated;
import net.minecraft.network.chat.Component;

public class ButtonInfo {
   protected Component line1 = Component.empty();
   protected Component line2 = Component.empty();
   protected int color = 16777215;
   protected boolean isSelected = false;

   public ButtonInfo() {
   }

   public ButtonInfo(Component line1, Component line2) {
      this.line1 = line1;
      this.line2 = line2;
   }

   public ButtonInfo(Component line1, Component line2, boolean isSelected) {
      this.line1 = line1;
      this.line2 = line2;
      this.isSelected = isSelected;
   }

   public boolean hasContent() {
      return !this.line1.getString().isEmpty() || !this.line2.getString().isEmpty();
   }

   @Generated
   public Component getLine1() {
      return this.line1;
   }

   @Generated
   public Component getLine2() {
      return this.line2;
   }

   @Generated
   public int getColor() {
      return this.color;
   }

   @Generated
   public boolean isSelected() {
      return this.isSelected;
   }

   @Generated
   public void setColor(int color) {
      this.color = color;
   }
}
