package com.dragonminez.compat.platform;

import net.minecraft.SharedConstants;

public final class McVersion {
   public static final String NAME = resolveName();
   private static final int[] PARTS = parse(NAME);
   public static final int MAJOR = PARTS[0];
   public static final int MINOR = PARTS[1];
   public static final int PATCH = PARTS[2];
   public static final boolean IS_1_21_1 = is(1, 21, 1);

   private McVersion() {
   }

   private static String resolveName() {
      try {
         return SharedConstants.getCurrentVersion().getName();
      } catch (Throwable var1) {
         return "";
      }
   }

   private static int[] parse(String name) {
      int[] out = new int[]{0, 0, 0};
      if (name != null && !name.isEmpty()) {
         String[] split = name.split("\\.");

         for (int i = 0; i < 3 && i < split.length; i++) {
            try {
               out[i] = Integer.parseInt(split[i].trim());
            } catch (NumberFormatException var5) {
               break;
            }
         }

         return out;
      } else {
         return out;
      }
   }

   public static boolean is(int major, int minor, int patch) {
      return MAJOR == major && MINOR == minor && PATCH == patch;
   }

   public static boolean atLeast(int major, int minor, int patch) {
      if (MAJOR != major) {
         return MAJOR > major;
      } else {
         return MINOR != minor ? MINOR > minor : PATCH >= patch;
      }
   }

   @Override
   public String toString() {
      return NAME;
   }
}
