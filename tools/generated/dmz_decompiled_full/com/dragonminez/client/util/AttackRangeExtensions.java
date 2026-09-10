package com.dragonminez.client.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.world.entity.player.Player;

public class AttackRangeExtensions {
   private static final ArrayList<Function<AttackRangeExtensions.Context, AttackRangeExtensions.Modifier>> sources = new ArrayList<>();

   public static void register(Function<AttackRangeExtensions.Context, AttackRangeExtensions.Modifier> source) {
      sources.add(source);
      sources.sort((a, b) -> {
         AttackRangeExtensions.Modifier modA = a.apply(new AttackRangeExtensions.Context(null, 0.0));
         AttackRangeExtensions.Modifier modB = b.apply(new AttackRangeExtensions.Context(null, 0.0));
         if (modA == null && modB == null) {
            return 0;
         } else if (modA == null) {
            return 1;
         } else {
            return modB == null ? -1 : Integer.compare(modA.operationOrder(), modB.operationOrder());
         }
      });
   }

   public static List<Function<AttackRangeExtensions.Context, AttackRangeExtensions.Modifier>> sources() {
      return sources;
   }

   public static record Context(Player player, double attackRange) {
   }

   public static record Modifier(double value, AttackRangeExtensions.Operation operation) {
      public int operationOrder() {
         return this.operation.order;
      }
   }

   public static enum Operation {
      ADD(0),
      MULTIPLY(1);

      public final int order;

      private Operation(int order) {
         this.order = order;
      }

      public int getOrder() {
         return this.order;
      }
   }
}
