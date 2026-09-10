package com.dragonminez.common.spacepod;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public interface SpacePodUnlockExpression {
   boolean test(Player var1);

   static SpacePodUnlockExpression fromJson(JsonElement element) {
      if (element == null || element.isJsonNull()) {
         throw new IllegalArgumentException("unlock_rules is required");
      } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
         return new SpacePodUnlockExpression.Primitive(element.getAsString());
      } else if (!element.isJsonObject()) {
         throw new IllegalArgumentException("unlock_rules must be a string or an object");
      } else {
         JsonObject object = element.getAsJsonObject();
         if (object.has("and")) {
            return new SpacePodUnlockExpression.And(parseArray(object.get("and"), "and"));
         } else if (object.has("or")) {
            return new SpacePodUnlockExpression.Or(parseArray(object.get("or"), "or"));
         } else if (object.has("not")) {
            return new SpacePodUnlockExpression.Not(fromJson(object.get("not")));
         } else if (object.has("quest")) {
            return new SpacePodUnlockExpression.QuestCompleted(getRequiredString(object, "quest"));
         } else if (object.has("level")) {
            return new SpacePodUnlockExpression.LevelAtLeast(object.get("level").getAsInt());
         } else if (object.has("race")) {
            return new SpacePodUnlockExpression.RaceMatches(getRequiredString(object, "race"));
         } else if (object.has("tag")) {
            return new SpacePodUnlockExpression.PlayerTag(getRequiredString(object, "tag"));
         } else if (object.has("visited_dimension")) {
            String dimension = getRequiredString(object, "visited_dimension");
            if (ResourceLocation.tryParse(dimension) == null) {
               throw new IllegalArgumentException("unlock_rules.visited_dimension must be a valid resource location");
            } else {
               return new SpacePodUnlockExpression.VisitedDimension(dimension);
            }
         } else if (object.has("stat")) {
            JsonElement statElement = object.get("stat");
            if (statElement != null && statElement.isJsonObject()) {
               JsonObject statObject = statElement.getAsJsonObject();
               return new SpacePodUnlockExpression.StatAtLeast(getRequiredString(statObject, "name"), statObject.get("min").getAsInt());
            } else {
               throw new IllegalArgumentException("unlock_rules.stat must be an object");
            }
         } else {
            throw new IllegalArgumentException("unlock_rules object must contain one of: and, or, not, quest, level, race, tag, visited_dimension, stat");
         }
      }
   }

   private static List<SpacePodUnlockExpression> parseArray(JsonElement element, String key) {
      if (element != null && element.isJsonArray()) {
         JsonArray array = element.getAsJsonArray();
         if (array.isEmpty()) {
            throw new IllegalArgumentException("unlock_rules." + key + " must not be empty");
         } else {
            List<SpacePodUnlockExpression> expressions = new ArrayList<>();

            for (JsonElement child : array) {
               expressions.add(fromJson(child));
            }

            return expressions;
         }
      } else {
         throw new IllegalArgumentException("unlock_rules." + key + " must be an array");
      }
   }

   private static String getRequiredString(JsonObject object, String key) {
      if (object.has(key) && !object.get(key).isJsonNull()) {
         return object.get(key).getAsString();
      } else {
         throw new IllegalArgumentException("Missing required unlock_rules field '" + key + "'");
      }
   }

   private static StatsData getStatsData(Player player) {
      return StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
   }

   public static final class And implements SpacePodUnlockExpression {
      private final List<SpacePodUnlockExpression> children;

      private And(List<SpacePodUnlockExpression> children) {
         this.children = children;
      }

      public List<SpacePodUnlockExpression> children() {
         return this.children;
      }

      @Override
      public boolean test(Player player) {
         for (SpacePodUnlockExpression child : this.children) {
            if (!child.test(player)) {
               return false;
            }
         }

         return true;
      }
   }

   public static final class LevelAtLeast implements SpacePodUnlockExpression {
      private final int level;

      public LevelAtLeast(int level) {
         this.level = level;
      }

      public int level() {
         return this.level;
      }

      @Override
      public boolean test(Player player) {
         StatsData data = SpacePodUnlockExpression.getStatsData(player);
         return data != null && data.getLevel() >= this.level;
      }
   }

   public static final class Not implements SpacePodUnlockExpression {
      private final SpacePodUnlockExpression child;

      private Not(SpacePodUnlockExpression child) {
         this.child = child;
      }

      public SpacePodUnlockExpression child() {
         return this.child;
      }

      @Override
      public boolean test(Player player) {
         return !this.child.test(player);
      }
   }

   public static final class Or implements SpacePodUnlockExpression {
      private final List<SpacePodUnlockExpression> children;

      private Or(List<SpacePodUnlockExpression> children) {
         this.children = children;
      }

      public List<SpacePodUnlockExpression> children() {
         return this.children;
      }

      @Override
      public boolean test(Player player) {
         for (SpacePodUnlockExpression child : this.children) {
            if (child.test(player)) {
               return true;
            }
         }

         return false;
      }
   }

   public static final class PlayerTag implements SpacePodUnlockExpression {
      private final String tag;

      public PlayerTag(String tag) {
         this.tag = tag;
      }

      public String tag() {
         return this.tag;
      }

      @Override
      public boolean test(Player player) {
         return player.getTags().contains(this.tag);
      }
   }

   public static final class Primitive implements SpacePodUnlockExpression {
      private final SpacePodUnlockExpression.Rule rule;

      private Primitive(String rawRule) {
         this.rule = SpacePodUnlockExpression.Rule.valueOf(rawRule.toUpperCase(Locale.ROOT));
      }

      public SpacePodUnlockExpression.Rule rule() {
         return this.rule;
      }

      @Override
      public boolean test(Player player) {
         return switch (this.rule) {
            case ALWAYS -> true;
            case NEVER -> false;
            case KAIO_UNLOCKED -> {
               boolean[] unlocked = new boolean[]{false};
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(cap -> unlocked[0] = cap.getStatus().isInKaioPlanet());
               yield unlocked[0];
            }
            case OTHERWORLD_ENABLED -> ConfigManager.getServerConfig().getWorldGen().getOtherworldActive();
         };
      }
   }

   public static final class QuestCompleted implements SpacePodUnlockExpression {
      private final String questId;

      public QuestCompleted(String questId) {
         this.questId = questId;
      }

      public String questId() {
         return this.questId;
      }

      @Override
      public boolean test(Player player) {
         StatsData data = SpacePodUnlockExpression.getStatsData(player);
         return data != null && data.getPlayerQuestData().isQuestCompleted(this.questId);
      }
   }

   public static final class RaceMatches implements SpacePodUnlockExpression {
      private final String race;

      public RaceMatches(String race) {
         this.race = race.toLowerCase(Locale.ROOT);
      }

      public String race() {
         return this.race;
      }

      @Override
      public boolean test(Player player) {
         StatsData data = SpacePodUnlockExpression.getStatsData(player);
         return data != null && data.getCharacter().getRaceName().equalsIgnoreCase(this.race);
      }
   }

   public static enum Rule {
      ALWAYS,
      NEVER,
      KAIO_UNLOCKED,
      OTHERWORLD_ENABLED;
   }

   public static final class StatAtLeast implements SpacePodUnlockExpression {
      private final String statName;
      private final int min;

      public StatAtLeast(String statName, int min) {
         this.statName = statName.toUpperCase(Locale.ROOT);
         this.min = min;
      }

      public String statName() {
         return this.statName;
      }

      public int min() {
         return this.min;
      }

      @Override
      public boolean test(Player player) {
         StatsData data = SpacePodUnlockExpression.getStatsData(player);
         return data != null && data.getCurrentStatValue(this.statName) >= this.min;
      }
   }

   public static final class VisitedDimension implements SpacePodUnlockExpression {
      private final String dimensionId;

      public VisitedDimension(String dimensionId) {
         this.dimensionId = dimensionId;
      }

      public String dimensionId() {
         return this.dimensionId;
      }

      @Override
      public boolean test(Player player) {
         StatsData data = SpacePodUnlockExpression.getStatsData(player);
         return data != null && data.getStatus().hasVisitedDimension(this.dimensionId);
      }
   }
}
