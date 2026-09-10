package com.dragonminez.common.quest;

import com.dragonminez.common.stats.StatsData;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class QuestAvailabilityChecker {
   public static boolean isAvailable(Quest quest, StatsData statsData) {
      if (quest == null || statsData == null) {
         return false;
      } else {
         return !quest.hasPrerequisites()
            ? true
            : evaluate(
               quest.getPrerequisites(),
               new QuestAvailabilityChecker.EvaluationContext(statsData, statsData.getPlayer(), null),
               QuestAvailabilityChecker.EvalOptions.STRICT
            );
      }
   }

   public static boolean areStartRequirementsMet(Quest quest, String questKey, Player player, StatsData statsData) {
      if (quest == null || statsData == null) {
         return false;
      } else if (!quest.hasStartRequirements()) {
         return true;
      } else if (player == null) {
         return false;
      } else {
         if (!player.level().isClientSide) {
            primeStartRequirementTiming(quest, questKey, player, statsData);
         }

         return evaluate(
            quest.getStartRequirements(),
            new QuestAvailabilityChecker.EvaluationContext(statsData, player, questKey),
            QuestAvailabilityChecker.EvalOptions.STRICT
         );
      }
   }

   public static boolean primeStartRequirementTiming(Quest quest, String questKey, Player player, StatsData statsData) {
      if (quest == null || statsData == null || player == null) {
         return false;
      } else if (player.level().isClientSide) {
         return false;
      } else if (!quest.hasStartRequirements() || questKey == null || questKey.isBlank()) {
         return false;
      } else if (!containsTimeCondition(quest.getStartRequirements())) {
         return false;
      } else {
         QuestAvailabilityChecker.EvaluationContext context = new QuestAvailabilityChecker.EvaluationContext(statsData, player, questKey);
         return !evaluate(quest.getStartRequirements(), context, QuestAvailabilityChecker.EvalOptions.PRIME_TIMING)
            ? false
            : statsData.getPlayerQuestData().ensureStartRequirementTiming(questKey, context.gameTime(), context.realTimeMs());
      }
   }

   public static Component describeAvailabilityFailure(Quest quest, StatsData statsData) {
      return describeAvailabilityFailure(quest, statsData, QuestAvailabilityChecker.EvalOptions.STRICT);
   }

   public static Component describeStartRequirementFailure(Quest quest, String questKey, Player player, StatsData statsData) {
      return describeStartRequirementFailure(quest, questKey, player, statsData, QuestAvailabilityChecker.EvalOptions.STRICT, true);
   }

   public static Component describeQuestStartBlocker(Quest quest, String questKey, Player player, StatsData statsData) {
      Component availabilityFailure = describeAvailabilityFailure(quest, statsData);
      return availabilityFailure != null ? availabilityFailure : describeStartRequirementFailure(quest, questKey, player, statsData);
   }

   public static Component describeNonPositionalStartBlocker(Quest quest, String questKey, Player player, StatsData statsData) {
      Component availabilityFailure = describeAvailabilityFailure(quest, statsData, QuestAvailabilityChecker.EvalOptions.NON_POSITIONAL);
      return availabilityFailure != null
         ? availabilityFailure
         : describeStartRequirementFailure(quest, questKey, player, statsData, QuestAvailabilityChecker.EvalOptions.NON_POSITIONAL, false);
   }

   public static Component describeNonPositionalStartRequirementFailure(Quest quest, String questKey, Player player, StatsData statsData) {
      return describeStartRequirementFailure(quest, questKey, player, statsData, QuestAvailabilityChecker.EvalOptions.NON_POSITIONAL, false);
   }

   private static Component describeAvailabilityFailure(Quest quest, StatsData statsData, QuestAvailabilityChecker.EvalOptions options) {
      return quest != null && statsData != null && quest.hasPrerequisites()
         ? describeFailure(quest.getPrerequisites(), new QuestAvailabilityChecker.EvaluationContext(statsData, statsData.getPlayer(), null), options)
         : null;
   }

   private static Component describeStartRequirementFailure(
      Quest quest, String questKey, Player player, StatsData statsData, QuestAvailabilityChecker.EvalOptions options, boolean primeTiming
   ) {
      if (quest != null && statsData != null && quest.hasStartRequirements() && player != null) {
         if (primeTiming && !player.level().isClientSide) {
            primeStartRequirementTiming(quest, questKey, player, statsData);
         }

         return describeFailure(quest.getStartRequirements(), new QuestAvailabilityChecker.EvaluationContext(statsData, player, questKey), options);
      } else {
         return null;
      }
   }

   static boolean matchesAlignmentCondition(QuestPrerequisites.Condition condition, int alignment) {
      if (condition != null && condition.getType() == QuestPrerequisites.ConditionType.ALIGNMENT) {
         Integer min = condition.getMinAlignment();
         Integer max = condition.getMaxAlignment();
         return min != null && alignment < min ? false : max == null || alignment <= max;
      } else {
         return false;
      }
   }

   public static boolean isSagaQuestAvailable(Quest quest, Saga saga, int questIndex, StatsData statsData) {
      if (quest == null || saga == null || statsData == null) {
         return false;
      } else {
         return !isSequentiallyReachable(saga, questIndex, statsData.getPlayerQuestData()) ? false : !quest.hasPrerequisites() || isAvailable(quest, statsData);
      }
   }

   private static boolean isSequentiallyReachable(Saga saga, int questIndex, PlayerQuestData pqd) {
      if (questIndex <= 0) {
         return true;
      } else {
         List<Quest> sagaQuests = saga.getQuests();
         if (questIndex >= sagaQuests.size()) {
            return false;
         } else {
            Quest previous = sagaQuests.get(questIndex - 1);
            String previousKey = PlayerQuestData.sagaQuestKey(saga.getId(), previous.getId());
            return pqd.isQuestCompleted(previousKey);
         }
      }
   }

   private static boolean containsTimeCondition(QuestPrerequisites prereqs) {
      if (prereqs != null && !prereqs.conditions().isEmpty()) {
         for (QuestPrerequisites.Condition condition : prereqs.conditions()) {
            if (condition != null) {
               if (condition.isNestedGroup()) {
                  if (containsTimeCondition(condition.getNested())) {
                     return true;
                  }
               } else if (condition.getType() == QuestPrerequisites.ConditionType.TIME) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean evaluate(QuestPrerequisites prereqs, QuestAvailabilityChecker.EvaluationContext context, QuestAvailabilityChecker.EvalOptions options) {
      if (prereqs != null && !prereqs.conditions().isEmpty()) {
         if (prereqs.operator() == QuestPrerequisites.Operator.AND) {
            for (QuestPrerequisites.Condition condition : prereqs.conditions()) {
               if (!evaluateCondition(condition, context, options)) {
                  return false;
               }
            }

            return true;
         } else {
            for (QuestPrerequisites.Condition conditionx : prereqs.conditions()) {
               if (evaluateCondition(conditionx, context, options)) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return true;
      }
   }

   private static Component describeFailure(
      QuestPrerequisites prereqs, QuestAvailabilityChecker.EvaluationContext context, QuestAvailabilityChecker.EvalOptions options
   ) {
      if (prereqs != null && !prereqs.conditions().isEmpty()) {
         if (prereqs.operator() == QuestPrerequisites.Operator.AND) {
            for (QuestPrerequisites.Condition condition : prereqs.conditions()) {
               Component failure = describeConditionFailure(condition, context, options);
               if (failure != null) {
                  return failure;
               }
            }

            return null;
         } else {
            Component firstFailure = null;

            for (QuestPrerequisites.Condition conditionx : prereqs.conditions()) {
               Component failure = describeConditionFailure(conditionx, context, options);
               if (failure == null) {
                  return null;
               }

               if (firstFailure == null) {
                  firstFailure = failure;
               }
            }

            return firstFailure;
         }
      } else {
         return null;
      }
   }

   private static Component describeConditionFailure(
      QuestPrerequisites.Condition condition, QuestAvailabilityChecker.EvaluationContext context, QuestAvailabilityChecker.EvalOptions options
   ) {
      if (condition == null) {
         return Component.translatable("message.dragonminez.quest.start.unavailable");
      } else if (condition.isNestedGroup()) {
         return describeFailure(condition.getNested(), context, options);
      } else if (condition.getType() == null) {
         return Component.translatable("message.dragonminez.quest.start.unavailable");
      } else if (evaluateCondition(condition, context, options)) {
         return null;
      } else {
         Component base = QuestTextFormatter.describeRequirement(condition, context.toRequirementContext());
         Component current = describeCurrentLocation(condition.getType(), context);
         return (Component)(current == null
            ? base
            : Component.empty().append(base).append(Component.translatable("message.dragonminez.quest.start.current_location", new Object[]{current})));
      }
   }

   private static Component describeCurrentLocation(QuestPrerequisites.ConditionType type, QuestAvailabilityChecker.EvaluationContext context) {
      if (type == null) {
         return null;
      } else {
         Level level = context.level();
         if (level == null) {
            return null;
         } else {
            return switch (type) {
               case BIOME -> {
                  BlockPos pos = context.pos();
                  yield pos == null
                     ? null
                     : level.getBiome(pos).unwrapKey().map(key -> QuestTextFormatter.resolveBiomeName(key.location().toString())).orElse(null);
               }
               case DIMENSION -> QuestTextFormatter.resolveDimensionName(level.dimension().location().toString());
               default -> null;
            };
         }
      }
   }

   private static boolean evaluateCondition(
      QuestPrerequisites.Condition condition, QuestAvailabilityChecker.EvaluationContext context, QuestAvailabilityChecker.EvalOptions options
   ) {
      if (condition == null) {
         return false;
      } else if (condition.isNestedGroup()) {
         return evaluate(condition.getNested(), context, options);
      } else if (condition.getType() == null) {
         return false;
      } else if (options.isAutoSatisfied(condition.getType())) {
         return true;
      } else {
         StatsData data = context.data();
         PlayerQuestData pqd = data.getPlayerQuestData();

         return switch (condition.getType()) {
            case BIOME -> {
               Level level = context.level();
               BlockPos pos = context.pos();
               String biomeId = condition.getBiomeId();
               yield level != null && pos != null && biomeId != null ? QuestLocationHelper.matchesBiome(level, pos, biomeId) : false;
            }
            case DIMENSION -> {
               Level level = context.level();
               String dimensionId = condition.getDimensionId();
               yield level != null && dimensionId != null ? QuestLocationHelper.isInDimension(level, dimensionId) : false;
            }
            case SAGA_QUEST -> {
               String sagaId = condition.getSagaId();
               Integer questId = condition.getQuestId();
               yield sagaId != null && questId != null ? pqd.isQuestCompleted(PlayerQuestData.sagaQuestKey(sagaId, questId)) : false;
            }
            case QUEST -> {
               String requiredQuestId = condition.getRequiredQuestId();
               yield requiredQuestId == null ? false : pqd.isQuestCompleted(requiredQuestId);
            }
            case STAT -> {
               String stat = condition.getStat();
               Integer minValue = condition.getMinValue();
               yield stat != null && minValue != null ? data.getCurrentStatValue(stat) >= minValue : false;
            }
            case LEVEL -> {
               Integer minLevel = condition.getMinLevel();
               yield minLevel == null ? false : data.getLevel() >= minLevel;
            }
            case STRUCTURE -> {
               Level level = context.level();
               BlockPos pos = context.pos();
               String structureId = condition.getStructureId();
               yield level == null || pos == null || structureId == null
                  ? false
                  : (level instanceof ServerLevel serverLevel ? QuestLocationHelper.isInStructure(serverLevel, pos, structureId) : true);
            }
            case TIME -> {
               if (context.questKey() != null && !context.questKey().isBlank()) {
                  QuestPrerequisites.TimeMode timeMode = condition.getTimeMode();
                  Long duration = condition.getDuration();
                  if (timeMode != null && duration != null && duration > 0L) {
                     PlayerQuestData.QuestStartRequirementTiming timing = pqd.getStartRequirementTiming(context.questKey());
                     if (timing == null) {
                        yield false;
                     } else {
                        switch (timeMode) {
                           case GAME_TIME:
                              yield context.gameTime() - timing.getGameTimeStarted() >= duration;
                           case REAL_TIME:
                              yield context.realTimeMs() - timing.getRealTimeStartedMs() >= duration;
                           default:
                              throw new MatchException(null, null);
                        }
                     }
                  } else {
                     yield true;
                  }
               } else {
                  yield false;
               }
            }
            case ALIGNMENT -> matchesAlignmentCondition(condition, data.getResources().getAlignment());
            case SKILL -> {
               String skill = condition.getSkill();
               Integer skillLevel = condition.getSkillLevel();
               yield skill != null && skillLevel != null ? data.getSkills().getSkillLevel(skill) >= skillLevel : false;
            }
            case RACE -> {
               String requiredRace = condition.getRace();
               yield requiredRace == null ? false : requiredRace.equalsIgnoreCase(data.getCharacter().getRaceName());
            }
            case CLASS -> {
               String requiredClass = condition.getCharacterClass();
               if (requiredClass == null) {
                  yield false;
               } else {
                  String playerClass = data.getCharacter().getCharacterClass();
                  yield playerClass != null && playerClass.equalsIgnoreCase(requiredClass);
               }
            }
         };
      }
   }

   private static record EvalOptions(boolean ignoreTime, boolean ignorePositional) {
      static final QuestAvailabilityChecker.EvalOptions STRICT = new QuestAvailabilityChecker.EvalOptions(false, false);
      static final QuestAvailabilityChecker.EvalOptions PRIME_TIMING = new QuestAvailabilityChecker.EvalOptions(true, false);
      static final QuestAvailabilityChecker.EvalOptions NON_POSITIONAL = new QuestAvailabilityChecker.EvalOptions(false, true);

      boolean isAutoSatisfied(QuestPrerequisites.ConditionType type) {
         if (type == null) {
            return false;
         } else {
            if (this.ignorePositional) {
               switch (type) {
                  case BIOME:
                  case DIMENSION:
                  case STRUCTURE:
                  case TIME:
                     return true;
                  case SAGA_QUEST:
                  case QUEST:
                  case STAT:
                  case LEVEL:
               }
            }

            return this.ignoreTime && type == QuestPrerequisites.ConditionType.TIME;
         }
      }
   }

   private static record EvaluationContext(StatsData data, Player player, String questKey) {
      Level level() {
         return this.player != null ? this.player.level() : null;
      }

      BlockPos pos() {
         return this.player != null ? this.player.blockPosition() : null;
      }

      long gameTime() {
         return this.level() != null ? this.level().getGameTime() : 0L;
      }

      long realTimeMs() {
         return System.currentTimeMillis();
      }

      QuestTextFormatter.RequirementContext toRequirementContext() {
         return new QuestTextFormatter.RequirementContext(this.data, this.player, this.questKey);
      }
   }
}
