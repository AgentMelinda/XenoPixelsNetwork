package com.dragonminez.client.render.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;

public enum WeaponGripProfile {
   SWORD {
      @Override
      public void applyRight(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
         ps.translate(-0.05, 0.135, -0.1);
      }

      @Override
      public void applyLeft(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(-25.0F));
         ps.mulPose(Axis.ZP.rotationDegrees(180.0F));
         ps.translate(-0.06, -0.38, -0.4);
      }
   },
   TOOL {
      @Override
      public void applyRight(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
         ps.translate(-0.05, 0.135, -0.1);
      }

      @Override
      public void applyLeft(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(-25.0F));
         ps.mulPose(Axis.ZP.rotationDegrees(180.0F));
         ps.translate(-0.06, -0.38, -0.4);
      }
   },
   TRIDENT {
      @Override
      public void applyRight(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
         ps.mulPose(Axis.YP.rotationDegrees(180.0F));
         ps.translate(0.05, 0.0, -0.2);
      }

      @Override
      public void applyLeft(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
         ps.mulPose(Axis.YP.rotationDegrees(180.0F));
         ps.translate(-0.05, 0.0, -0.2);
      }
   },
   BOW {
      @Override
      public void applyRight(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
         ps.translate(0.02, 0.135, -0.1);
      }

      @Override
      public void applyLeft(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(-180.0F));
         ps.mulPose(Axis.YP.rotationDegrees(12.0F));
         ps.mulPose(Axis.ZP.rotationDegrees(-12.0F));
         ps.translate(0.1, 0.05, -0.16);
      }
   },
   CROSSBOW {
      @Override
      public void applyRight(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
         ps.translate(-0.05, 0.135, -0.1);
      }

      @Override
      public void applyLeft(PoseStack ps) {
         ps.mulPose(Axis.ZP.rotationDegrees(60.0F));
         ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
         ps.translate(-0.42, 0.135, 0.1);
      }
   },
   SHIELD {
      @Override
      public void applyRight(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
         ps.translate(-0.15, 0.135, -0.05);
      }

      @Override
      public void applyLeft(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
         ps.mulPose(Axis.YP.rotationDegrees(180.0F));
         ps.translate(-0.03, 0.135, -1.39);
      }
   },
   SHIELD_ACTIVE {
      @Override
      public void applyRight(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
         ps.translate(-0.15, 0.135, -0.05);
      }

      @Override
      public void applyLeft(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(45.0F));
         ps.mulPose(Axis.YP.rotationDegrees(125.0F));
         ps.mulPose(Axis.ZP.rotationDegrees(-95.0F));
         ps.translate(-0.8, 0.75, -0.45);
      }
   },
   BLOCK {
      @Override
      public void applyRight(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
         ps.translate(-0.05, 0.135, -0.1);
      }

      @Override
      public void applyLeft(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
         ps.translate(0.0, 0.1, -0.1);
      }
   },
   DEFAULT {
      @Override
      public void applyRight(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
         ps.translate(-0.05, 0.135, -0.1);
      }

      @Override
      public void applyLeft(PoseStack ps) {
         ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
         ps.translate(0.055, 0.13, -0.1);
      }
   };

   public abstract void applyRight(PoseStack var1);

   public abstract void applyLeft(PoseStack var1);

   public void apply(PoseStack ps, boolean isLeft) {
      if (isLeft) {
         this.applyLeft(ps);
      } else {
         this.applyRight(ps);
      }
   }

   public static WeaponGripProfile resolve(Item item, boolean isUsing, String weaponTypeHint) {
      if (weaponTypeHint != null && !weaponTypeHint.isEmpty()) {
         WeaponGripProfile custom = resolveFromWeaponType(weaponTypeHint);
         if (custom != null) {
            return custom;
         }
      }

      if (item instanceof ShieldItem) {
         return isUsing ? SHIELD_ACTIVE : SHIELD;
      } else if (item instanceof TridentItem) {
         return TRIDENT;
      } else if (item instanceof BowItem) {
         return BOW;
      } else if (item instanceof CrossbowItem) {
         return CROSSBOW;
      } else if (item instanceof BlockItem) {
         return BLOCK;
      } else if (item instanceof SwordItem) {
         return SWORD;
      } else {
         return item instanceof TieredItem ? TOOL : DEFAULT;
      }
   }

   private static WeaponGripProfile resolveFromWeaponType(String category) {
      String var1 = category.toLowerCase();

      return switch (var1) {
         case "sword", "katana", "claymore", "greatsword", "scimitar", "dagger", "knife" -> SWORD;
         case "spear", "lance", "polearm", "naginata" -> TRIDENT;
         case "staff", "bo_staff" -> SWORD;
         default -> null;
      };
   }
}
