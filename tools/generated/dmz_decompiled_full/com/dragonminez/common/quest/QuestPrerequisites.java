package com.dragonminez.common.quest;

import java.util.ArrayList;
import java.util.List;
import lombok.Generated;

public record QuestPrerequisites(QuestPrerequisites.Operator operator, List<QuestPrerequisites.Condition> conditions) {
   public QuestPrerequisites(QuestPrerequisites.Operator operator, List<QuestPrerequisites.Condition> conditions) {
      this.operator = operator != null ? operator : QuestPrerequisites.Operator.AND;
      this.conditions = (List<QuestPrerequisites.Condition>)(conditions != null ? conditions : new ArrayList<>());
   }

   public static class Condition {
      private final QuestPrerequisites.ConditionType type;
      private final String sagaId;
      private final Integer questId;
      private final String requiredQuestId;
      private final String stat;
      private final Integer minValue;
      private final Integer minLevel;
      private final String biomeId;
      private final String structureId;
      private final QuestPrerequisites.StructureHint structureHint;
      private final String dimensionId;
      private final QuestPrerequisites.TimeMode timeMode;
      private final Long duration;
      private final Integer minAlignment;
      private final Integer maxAlignment;
      private final String skill;
      private final Integer skillLevel;
      private final String race;
      private final String characterClass;
      private final QuestPrerequisites nested;

      private Condition(
         QuestPrerequisites.ConditionType type,
         String sagaId,
         Integer questId,
         String requiredQuestId,
         String stat,
         Integer minValue,
         Integer minLevel,
         String biomeId,
         String structureId,
         QuestPrerequisites.StructureHint structureHint,
         String dimensionId,
         QuestPrerequisites.TimeMode timeMode,
         Long duration,
         Integer minAlignment,
         Integer maxAlignment,
         String skill,
         Integer skillLevel,
         String race,
         String characterClass,
         QuestPrerequisites nested
      ) {
         this.type = type;
         this.sagaId = sagaId;
         this.questId = questId;
         this.requiredQuestId = requiredQuestId;
         this.stat = stat;
         this.minValue = minValue;
         this.minLevel = minLevel;
         this.biomeId = biomeId;
         this.structureId = structureId;
         this.structureHint = structureHint;
         this.dimensionId = dimensionId;
         this.timeMode = timeMode;
         this.duration = duration;
         this.minAlignment = minAlignment;
         this.maxAlignment = maxAlignment;
         this.skill = skill;
         this.skillLevel = skillLevel;
         this.race = race;
         this.characterClass = characterClass;
         this.nested = nested;
      }

      public boolean isNestedGroup() {
         return this.type == null && this.nested != null;
      }

      public static QuestPrerequisites.Condition sagaQuest(String sagaId, int questId) {
         return new QuestPrerequisites.Condition(
            QuestPrerequisites.ConditionType.SAGA_QUEST,
            sagaId,
            questId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
         );
      }

      public static QuestPrerequisites.Condition quest(String requiredQuestId) {
         return new QuestPrerequisites.Condition(
            QuestPrerequisites.ConditionType.QUEST,
            null,
            null,
            requiredQuestId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
         );
      }

      public static QuestPrerequisites.Condition stat(String stat, int minValue) {
         return new QuestPrerequisites.Condition(
            QuestPrerequisites.ConditionType.STAT,
            null,
            null,
            null,
            stat,
            minValue,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
         );
      }

      public static QuestPrerequisites.Condition level(int minLevel) {
         return new QuestPrerequisites.Condition(
            QuestPrerequisites.ConditionType.LEVEL,
            null,
            null,
            null,
            null,
            null,
            minLevel,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
         );
      }

      public static QuestPrerequisites.Condition biome(String biomeId) {
         return new QuestPrerequisites.Condition(
            QuestPrerequisites.ConditionType.BIOME,
            null,
            null,
            null,
            null,
            null,
            null,
            biomeId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
         );
      }

      public static QuestPrerequisites.Condition structure(String structureId, QuestPrerequisites.StructureHint structureHint) {
         return new QuestPrerequisites.Condition(
            QuestPrerequisites.ConditionType.STRUCTURE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            structureId,
            structureHint,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
         );
      }

      public static QuestPrerequisites.Condition dimension(String dimensionId) {
         return new QuestPrerequisites.Condition(
            QuestPrerequisites.ConditionType.DIMENSION,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            dimensionId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
         );
      }

      public static QuestPrerequisites.Condition time(QuestPrerequisites.TimeMode timeMode, long duration) {
         return new QuestPrerequisites.Condition(
            QuestPrerequisites.ConditionType.TIME,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            timeMode,
            duration,
            null,
            null,
            null,
            null,
            null,
            null,
            null
         );
      }

      public static QuestPrerequisites.Condition alignment(Integer minAlignment, Integer maxAlignment) {
         return new QuestPrerequisites.Condition(
            QuestPrerequisites.ConditionType.ALIGNMENT,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            minAlignment,
            maxAlignment,
            null,
            null,
            null,
            null,
            null
         );
      }

      public static QuestPrerequisites.Condition skill(String skill, int skillLevel) {
         return new QuestPrerequisites.Condition(
            QuestPrerequisites.ConditionType.SKILL,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            skill,
            Math.max(1, skillLevel),
            null,
            null,
            null
         );
      }

      public static QuestPrerequisites.Condition race(String raceName) {
         return new QuestPrerequisites.Condition(
            QuestPrerequisites.ConditionType.RACE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            raceName,
            null,
            null
         );
      }

      public static QuestPrerequisites.Condition characterClass(String className) {
         return new QuestPrerequisites.Condition(
            QuestPrerequisites.ConditionType.CLASS,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            className,
            null
         );
      }

      public static QuestPrerequisites.Condition nestedGroup(QuestPrerequisites nested) {
         return new QuestPrerequisites.Condition(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, nested
         );
      }

      @Generated
      public QuestPrerequisites.ConditionType getType() {
         return this.type;
      }

      @Generated
      public String getSagaId() {
         return this.sagaId;
      }

      @Generated
      public Integer getQuestId() {
         return this.questId;
      }

      @Generated
      public String getRequiredQuestId() {
         return this.requiredQuestId;
      }

      @Generated
      public String getStat() {
         return this.stat;
      }

      @Generated
      public Integer getMinValue() {
         return this.minValue;
      }

      @Generated
      public Integer getMinLevel() {
         return this.minLevel;
      }

      @Generated
      public String getBiomeId() {
         return this.biomeId;
      }

      @Generated
      public String getStructureId() {
         return this.structureId;
      }

      @Generated
      public QuestPrerequisites.StructureHint getStructureHint() {
         return this.structureHint;
      }

      @Generated
      public String getDimensionId() {
         return this.dimensionId;
      }

      @Generated
      public QuestPrerequisites.TimeMode getTimeMode() {
         return this.timeMode;
      }

      @Generated
      public Long getDuration() {
         return this.duration;
      }

      @Generated
      public Integer getMinAlignment() {
         return this.minAlignment;
      }

      @Generated
      public Integer getMaxAlignment() {
         return this.maxAlignment;
      }

      @Generated
      public String getSkill() {
         return this.skill;
      }

      @Generated
      public Integer getSkillLevel() {
         return this.skillLevel;
      }

      @Generated
      public String getRace() {
         return this.race;
      }

      @Generated
      public String getCharacterClass() {
         return this.characterClass;
      }

      @Generated
      public QuestPrerequisites getNested() {
         return this.nested;
      }
   }

   public static enum ConditionType {
      SAGA_QUEST,
      QUEST,
      STAT,
      LEVEL,
      BIOME,
      STRUCTURE,
      DIMENSION,
      TIME,
      ALIGNMENT,
      SKILL,
      RACE,
      CLASS;
   }

   public static enum Operator {
      AND,
      OR;
   }

   public static record StructureHint(String dimensionId, Integer x, Integer y, Integer z) {
      public boolean hasCoordinates() {
         return this.x != null && this.y != null && this.z != null;
      }
   }

   public static enum TimeMode {
      GAME_TIME,
      REAL_TIME;
   }
}
