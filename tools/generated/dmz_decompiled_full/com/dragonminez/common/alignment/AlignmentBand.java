package com.dragonminez.common.alignment;

public enum AlignmentBand {
   EVIL,
   NEUTRAL,
   GOOD;

   public static AlignmentBand fromValue(int alignment) {
      if (alignment > 60) {
         return GOOD;
      } else {
         return alignment > 40 ? NEUTRAL : EVIL;
      }
   }
}
