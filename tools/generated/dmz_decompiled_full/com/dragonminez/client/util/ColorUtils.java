package com.dragonminez.client.util;

public class ColorUtils {
   public static float[] hexToRgb(String hex) {
      if (hex != null && !hex.isEmpty()) {
         try {
            if (hex.startsWith("#")) {
               hex = hex.substring(1);
            }

            long color = Long.parseLong(hex, 16);
            float r = (float)(color >> 16 & 255L) / 255.0F;
            float g = (float)(color >> 8 & 255L) / 255.0F;
            float b = (float)(color & 255L) / 255.0F;
            return new float[]{r, g, b};
         } catch (Exception var6) {
            return new float[]{1.0F, 1.0F, 1.0F};
         }
      } else {
         return new float[]{1.0F, 1.0F, 1.0F};
      }
   }

   public static float[] rgbIntToFloat(int color) {
      float r = (float)(color >> 16 & 0xFF) / 255.0F;
      float g = (float)(color >> 8 & 0xFF) / 255.0F;
      float b = (float)(color & 0xFF) / 255.0F;
      return new float[]{r, g, b};
   }

   public static String rgbToHex(int r, int g, int b) {
      return String.format("#%02X%02X%02X", r, g, b);
   }

   public static int rgbToInt(int r, int g, int b) {
      return r << 16 | g << 8 | b;
   }

   public static int rgbToInt(float r, float g, float b) {
      return rgbToInt((int)(r * 255.0F), (int)(g * 255.0F), (int)(b * 255.0F));
   }

   public static int[] intToRgb(int color) {
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      return new int[]{r, g, b};
   }

   public static float[] rgbToHsv(int r, int g, int b) {
      float rf = (float)r / 255.0F;
      float gf = (float)g / 255.0F;
      float bf = (float)b / 255.0F;
      float max = Math.max(rf, Math.max(gf, bf));
      float min = Math.min(rf, Math.min(gf, bf));
      float delta = max - min;
      float h = 0.0F;
      if (delta != 0.0F) {
         if (max == rf) {
            h = 60.0F * ((gf - bf) / delta % 6.0F);
         } else if (max == gf) {
            h = 60.0F * ((bf - rf) / delta + 2.0F);
         } else {
            h = 60.0F * ((rf - gf) / delta + 4.0F);
         }
      }

      if (h < 0.0F) {
         h += 360.0F;
      }

      float s = max == 0.0F ? 0.0F : delta / max * 100.0F;
      float v = max * 100.0F;
      return new float[]{h, s, v};
   }

   public static int[] hsvToRgb(float h, float s, float v) {
      s /= 100.0F;
      v /= 100.0F;
      float c = v * s;
      float x = c * (1.0F - Math.abs(h / 60.0F % 2.0F - 1.0F));
      float m = v - c;
      float rf;
      float gf;
      float bf;
      if (h >= 0.0F && h < 60.0F) {
         rf = c;
         gf = x;
         bf = 0.0F;
      } else if (h >= 60.0F && h < 120.0F) {
         rf = x;
         gf = c;
         bf = 0.0F;
      } else if (h >= 120.0F && h < 180.0F) {
         rf = 0.0F;
         gf = c;
         bf = x;
      } else if (h >= 180.0F && h < 240.0F) {
         rf = 0.0F;
         gf = x;
         bf = c;
      } else if (h >= 240.0F && h < 300.0F) {
         rf = x;
         gf = 0.0F;
         bf = c;
      } else {
         rf = c;
         gf = 0.0F;
         bf = x;
      }

      int r = Math.round((rf + m) * 255.0F);
      int g = Math.round((gf + m) * 255.0F);
      int b = Math.round((bf + m) * 255.0F);
      return new int[]{Math.max(0, Math.min(255, r)), Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, b))};
   }

   public static float[] hexToHsv(String hex) {
      float[] rgb = hexToRgb(hex);
      return rgbToHsv((int)(rgb[0] * 255.0F), (int)(rgb[1] * 255.0F), (int)(rgb[2] * 255.0F));
   }

   public static String hsvToHex(float h, float s, float v) {
      int[] rgb = hsvToRgb(h, s, v);
      return rgbToHex(rgb[0], rgb[1], rgb[2]);
   }

   public static int hexToInt(String hex) {
      if (hex != null && !hex.isEmpty()) {
         try {
            if (hex.startsWith("#")) {
               hex = hex.substring(1);
            }

            return (int)Long.parseLong(hex, 16);
         } catch (NumberFormatException var2) {
            return 16777215;
         }
      } else {
         return 16777215;
      }
   }

   public static int darkenColor(int color, float factor) {
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      r = (int)((float)r * factor);
      g = (int)((float)g * factor);
      b = (int)((float)b * factor);
      return r << 16 | g << 8 | b;
   }

   public static float[] lightenColor(float[] color, float whiteness) {
      return new float[]{color[0] + (1.0F - color[0]) * whiteness, color[1] + (1.0F - color[1]) * whiteness, color[2] + (1.0F - color[2]) * whiteness};
   }

   public static float[] darkenColor(float[] color, float darkness) {
      return new float[]{color[0] * (1.0F - darkness), color[1] * (1.0F - darkness), color[2] * (1.0F - darkness)};
   }
}
