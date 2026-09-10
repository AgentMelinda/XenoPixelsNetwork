package com.dragonminez.client.util;

import lombok.Generated;

public class ScrollbarState {
   private int barX;
   private int barWidth;
   private int trackY;
   private int trackHeight;
   private float maxScroll;
   private boolean dragging;

   public void update(int barX, int barWidth, int trackY, int trackHeight, float maxScroll) {
      this.barX = barX;
      this.barWidth = barWidth;
      this.trackY = trackY;
      this.trackHeight = trackHeight;
      this.maxScroll = maxScroll;
   }

   public boolean tryStartDrag(double mouseX, double mouseY) {
      if (this.maxScroll > 0.0F && TextUtil.overScrollBar(mouseX, mouseY, this.barX, this.barWidth, this.trackY, this.trackHeight)) {
         this.dragging = true;
         return true;
      } else {
         return false;
      }
   }

   public void clear() {
      this.update(0, 0, 0, 0, 0.0F);
   }

   public void stopDrag() {
      this.dragging = false;
   }

   public float scrollFor(double mouseY) {
      return TextUtil.scrollFromBar(mouseY, this.trackY, this.trackHeight, this.maxScroll);
   }

   @Generated
   public boolean isDragging() {
      return this.dragging;
   }
}
