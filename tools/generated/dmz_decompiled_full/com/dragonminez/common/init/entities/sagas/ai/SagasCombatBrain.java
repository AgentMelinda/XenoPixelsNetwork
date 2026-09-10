package com.dragonminez.common.init.entities.sagas.ai;

import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import java.util.List;
import net.minecraft.util.RandomSource;

public final class SagasCombatBrain {
   public static final double MELEE_RANGE = 4.5;
   public static final double MID_RANGE = 12.0;
   public static final double OUT_RANGE = 28.0;
   public static final int COMBO_RECOVERY = 7;
   private static final int[] STUN_COMBOS = new int[]{1, 3, 8};
   private static final int[] PRESSURE_COMBOS = new int[]{0, 8};
   private static final int[] HEAVY_COMBOS = new int[]{3, 1};

   private SagasCombatBrain() {
   }

   public static SagasCombatBrain.Intent decide(CombatContext ctx) {
      DBSagasEntity self = ctx.self;
      RandomSource rnd = self.getRandom();
      boolean advanced = self.getAiTier() == DBSagasEntity.AiTier.ADVANCED;
      double d = ctx.dist3D;
      if (ctx.selfHpPct < 0.25F && ctx.comboReady && !ctx.targetApproaching() && hasCombo(self, 7)) {
         return SagasCombatBrain.Intent.combo(7);
      } else if (advanced && ctx.targetCasting) {
         if (!(d <= 4.5)) {
            return SagasCombatBrain.Intent.approach(DBSagasEntity.LocomotionMode.WALK_SLOW);
         } else {
            return ctx.comboReady ? SagasCombatBrain.Intent.combo(chooseCombo(self, STUN_COMBOS, rnd)) : SagasCombatBrain.Intent.melee();
         }
      } else {
         if (advanced && (ctx.targetHelpless() || ctx.targetTransforming)) {
            if (d <= 4.5 && ctx.comboReady) {
               return SagasCombatBrain.Intent.combo(chooseCombo(self, HEAVY_COMBOS, rnd));
            }

            List<DBSagasEntity.KiSkill> burst = ctx.readyByRole(DBSagasEntity.SkillRole.HITSCAN, DBSagasEntity.SkillRole.GUARD_BREAK);
            if (!burst.isEmpty()) {
               return SagasCombatBrain.Intent.cast(pick(burst, rnd));
            }
         }

         if (ctx.targetBlocking) {
            List<DBSagasEntity.KiSkill> guardBreak = ctx.readyByRole(DBSagasEntity.SkillRole.GUARD_BREAK);
            if (!guardBreak.isEmpty() && d <= 28.0) {
               return SagasCombatBrain.Intent.cast(pick(guardBreak, rnd));
            } else {
               return d <= 4.5 && ctx.comboReady ? SagasCombatBrain.Intent.combo(chooseCombo(self, PRESSURE_COMBOS, rnd)) : SagasCombatBrain.Intent.melee();
            }
         } else if (d > 28.0) {
            return reposition(ctx, rnd);
         } else if (d > 12.0) {
            if (ctx.targetApproaching()) {
               if (!ctx.hasReadyRole(DBSagasEntity.SkillRole.HITSCAN)) {
                  List<DBSagasEntity.KiSkill> ranged = ctx.readyByRole(DBSagasEntity.SkillRole.RANGED_TRAVEL);
                  if (!ranged.isEmpty() && roll(rnd, 0.6F)) {
                     return SagasCombatBrain.Intent.cast(pick(ranged, rnd));
                  }
               }

               return SagasCombatBrain.Intent.approach(DBSagasEntity.LocomotionMode.RUN);
            } else {
               List<DBSagasEntity.KiSkill> ranged = ctx.readyByRole(DBSagasEntity.SkillRole.RANGED_TRAVEL, DBSagasEntity.SkillRole.ZONING);
               return !ranged.isEmpty() ? SagasCombatBrain.Intent.cast(pick(ranged, rnd)) : reposition(ctx, rnd);
            }
         } else if (d > 4.5) {
            List<DBSagasEntity.KiSkill> mid = ctx.readyByRole(DBSagasEntity.SkillRole.HITSCAN, DBSagasEntity.SkillRole.PROJECTILE_FAST);
            float castChance = advanced ? 0.55F : 0.65F;
            return !mid.isEmpty() && roll(rnd, castChance)
               ? SagasCombatBrain.Intent.cast(pick(mid, rnd))
               : SagasCombatBrain.Intent.approach(DBSagasEntity.LocomotionMode.RUN);
         } else {
            return ctx.comboReady && roll(rnd, 0.6F) ? SagasCombatBrain.Intent.combo(chooseCombo(self, PRESSURE_COMBOS, rnd)) : SagasCombatBrain.Intent.melee();
         }
      }
   }

   private static SagasCombatBrain.Intent reposition(CombatContext ctx, RandomSource rnd) {
      if (ctx.wildSenseReady && roll(rnd, 0.4F)) {
         return SagasCombatBrain.Intent.teleport();
      } else {
         return ctx.dashReady && roll(rnd, 0.5F)
            ? SagasCombatBrain.Intent.approach(DBSagasEntity.LocomotionMode.DASH)
            : SagasCombatBrain.Intent.approach(DBSagasEntity.LocomotionMode.RUN);
      }
   }

   private static boolean hasCombo(DBSagasEntity self, int comboId) {
      int[] allowed = self.getAllowedCombos();
      if (allowed == null) {
         return false;
      } else {
         for (int a : allowed) {
            if (a == comboId) {
               return true;
            }
         }

         return false;
      }
   }

   private static int chooseCombo(DBSagasEntity self, int[] preferred, RandomSource rnd) {
      int[] allowed = self.getAllowedCombos();
      if (allowed != null && allowed.length > 0) {
         for (int pref : preferred) {
            for (int a : allowed) {
               if (a == pref) {
                  return pref;
               }
            }
         }

         return allowed[rnd.nextInt(allowed.length)];
      } else {
         return -1;
      }
   }

   private static DBSagasEntity.KiSkill pick(List<DBSagasEntity.KiSkill> options, RandomSource rnd) {
      return options.get(rnd.nextInt(options.size()));
   }

   private static boolean roll(RandomSource rnd, float chance) {
      return rnd.nextFloat() < chance;
   }

   public static final class Intent {
      public final SagasCombatBrain.Type type;
      public final DBSagasEntity.KiSkill skill;
      public final int comboId;
      public final DBSagasEntity.LocomotionMode locomotion;

      private Intent(SagasCombatBrain.Type type, DBSagasEntity.KiSkill skill, int comboId, DBSagasEntity.LocomotionMode locomotion) {
         this.type = type;
         this.skill = skill;
         this.comboId = comboId;
         this.locomotion = locomotion;
      }

      public static SagasCombatBrain.Intent melee() {
         return new SagasCombatBrain.Intent(SagasCombatBrain.Type.MELEE, null, -1, DBSagasEntity.LocomotionMode.WALK);
      }

      public static SagasCombatBrain.Intent approach(DBSagasEntity.LocomotionMode mode) {
         return new SagasCombatBrain.Intent(SagasCombatBrain.Type.APPROACH, null, -1, mode);
      }

      public static SagasCombatBrain.Intent teleport() {
         return new SagasCombatBrain.Intent(SagasCombatBrain.Type.TELEPORT, null, -1, DBSagasEntity.LocomotionMode.RUN);
      }

      public static SagasCombatBrain.Intent hold() {
         return new SagasCombatBrain.Intent(SagasCombatBrain.Type.HOLD, null, -1, DBSagasEntity.LocomotionMode.IDLE);
      }

      public static SagasCombatBrain.Intent cast(DBSagasEntity.KiSkill skill) {
         return new SagasCombatBrain.Intent(SagasCombatBrain.Type.CAST, skill, -1, DBSagasEntity.LocomotionMode.IDLE);
      }

      public static SagasCombatBrain.Intent combo(int comboId) {
         return new SagasCombatBrain.Intent(SagasCombatBrain.Type.COMBO, null, comboId, DBSagasEntity.LocomotionMode.WALK);
      }
   }

   public static enum Type {
      MELEE,
      APPROACH,
      TELEPORT,
      CAST,
      COMBO,
      HOLD;
   }
}
