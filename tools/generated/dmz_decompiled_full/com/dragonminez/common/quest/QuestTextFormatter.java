package com.dragonminez.common.quest;

import com.dragonminez.common.quest.objectives.BiomeObjective;
import com.dragonminez.common.quest.objectives.CoordsObjective;
import com.dragonminez.common.quest.objectives.DimensionObjective;
import com.dragonminez.common.quest.objectives.DragonSummonObjective;
import com.dragonminez.common.quest.objectives.InteractObjective;
import com.dragonminez.common.quest.objectives.ItemObjective;
import com.dragonminez.common.quest.objectives.KillObjective;
import com.dragonminez.common.quest.objectives.SkillObjective;
import com.dragonminez.common.quest.objectives.StructureObjective;
import com.dragonminez.common.quest.objectives.TalkToObjective;
import com.dragonminez.common.stats.StatsData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class QuestTextFormatter {
   private static final String OBJECTIVE_DEFEAT = "dmz.quest.defeat.obj";
   private static final String OBJECTIVE_OBTAIN = "dmz.quest.obtain.obj";
   private static final String OBJECTIVE_TALK_TO = "dmz.quest.talk_to.obj";
   private static final String OBJECTIVE_GO_TO = "dmz.quest.go_to.obj";
   private static final String OBJECTIVE_INTERACT = "dmz.quest.interact.obj";
   private static final String OBJECTIVE_SUMMON_DRAGON = "dmz.quest.summon_dragon.obj";
   private static final String OBJECTIVE_SKILL = "dmz.quest.skill.obj";
   private static final Pattern NPC_VARIANT_SUFFIX = Pattern.compile("^(.+?)_\\d+$");
   private static final Map<String, String> STRUCTURE_NAMES = Map.ofEntries(
      Map.entry("dragonminez:goku_house", "Goku's House"),
      Map.entry("dragonminez:roshi_house", "Kame House"),
      Map.entry("dragonminez:elder_guru", "Guru's House"),
      Map.entry("dragonminez:timechamber", "Hyperbolic Time Chamber"),
      Map.entry("dragonminez:kamilookout", "Kami's Lookout"),
      Map.entry("dragonminez:gero_lab", "Dr. Gero's Laboratory"),
      Map.entry("dragonminez:village_ajissa", "Ajissa Village"),
      Map.entry("dragonminez:village_sacred", "Sacred Village")
   );
   private static final Map<String, String> DIMENSION_NAMES = Map.ofEntries(
      Map.entry("minecraft:overworld", "Earth"),
      Map.entry("minecraft:the_nether", "Nether"),
      Map.entry("minecraft:the_end", "The End"),
      Map.entry("dragonminez:namek", "Namek"),
      Map.entry("dragonminez:time_chamber", "Hyperbolic Time Chamber")
   );

   private QuestTextFormatter() {
   }

   public static Component describeObjective(QuestObjective objective) {
      if (objective == null) {
         return Component.empty();
      } else if (objective instanceof KillObjective kill) {
         return Component.translatable("dmz.quest.defeat.obj", new Object[]{resolveEntityName(kill.getEntityId())});
      } else if (objective instanceof ItemObjective item) {
         return Component.translatable("dmz.quest.obtain.obj", new Object[]{resolveItemName(item.getItemId())});
      } else if (objective instanceof TalkToObjective talkTo) {
         return Component.translatable("dmz.quest.talk_to.obj", new Object[]{resolveNpcName(talkTo.getNpcId())});
      } else if (objective instanceof InteractObjective interact) {
         return Component.translatable("dmz.quest.interact.obj", new Object[]{resolveInteractTarget(interact)});
      } else if (objective instanceof DragonSummonObjective dragonSummon) {
         return Component.translatable("dmz.quest.summon_dragon.obj", new Object[]{resolveDragonName(dragonSummon.getDragonId())});
      } else if (objective instanceof SkillObjective skill) {
         return Component.translatable("dmz.quest.skill.obj", new Object[]{resolveSkillName(skill.getSkill()), skill.getLevel()});
      } else if (objective instanceof StructureObjective structure) {
         return Component.translatable("dmz.quest.go_to.obj", new Object[]{resolveStructureName(structure.getStructureId())});
      } else if (objective instanceof BiomeObjective biome) {
         return Component.translatable("dmz.quest.go_to.obj", new Object[]{resolveBiomeName(biome.getBiomeId())});
      } else if (objective instanceof DimensionObjective dimension) {
         return Component.translatable("dmz.quest.go_to.obj", new Object[]{resolveDimensionName(dimension.getDimensionId())});
      } else {
         return objective instanceof CoordsObjective coords
            ? Component.translatable(
               "dmz.quest.go_to.obj",
               new Object[]{Component.literal(coords.getTargetPos().getX() + ", " + coords.getTargetPos().getY() + ", " + coords.getTargetPos().getZ())}
            )
            : Component.literal(objective.getType() != null ? humanizeIdentifier(objective.getType().name()) : "?");
      }
   }

   public static Component describeRequirement(QuestPrerequisites.Condition condition, QuestTextFormatter.RequirementContext context) {
      if (condition != null && condition.getType() != null) {
         return (Component)(switch (condition.getType()) {
            case SAGA_QUEST -> Component.translatable(
            "gui.dragonminez.quests.requirement.complete_saga", new Object[]{resolveSagaQuestName(condition.getSagaId(), condition.getQuestId())}
         );
            case QUEST -> Component.translatable(
            "gui.dragonminez.quests.requirement.complete_quest", new Object[]{resolveQuestName(condition.getRequiredQuestId())}
         );
            case STAT -> Component.translatable(
            "gui.dragonminez.quests.requirement.stat", new Object[]{condition.getMinValue(), humanizeIdentifier(condition.getStat())}
         );
            case LEVEL -> Component.translatable("gui.dragonminez.quests.requirement.level", new Object[]{condition.getMinLevel()});
            case BIOME -> Component.translatable("gui.dragonminez.quests.requirement.biome", new Object[]{resolveBiomeName(condition.getBiomeId())});
            case STRUCTURE -> buildStructureRequirement(condition);
            case DIMENSION -> Component.translatable(
            "gui.dragonminez.quests.requirement.dimension", new Object[]{resolveDimensionName(condition.getDimensionId())}
         );
            case TIME -> buildTimeRequirement(condition, context);
            case ALIGNMENT -> buildAlignmentRequirement(condition);
            case SKILL -> Component.translatable(
            "gui.dragonminez.quests.requirement.skill", new Object[]{resolveSkillName(condition.getSkill()), condition.getSkillLevel()}
         );
            case RACE -> Component.translatable(
            "gui.dragonminez.quests.requirement.race", new Object[]{Component.literal(humanizeIdentifier(condition.getRace()))}
         );
            case CLASS -> Component.translatable(
            "gui.dragonminez.quests.requirement.class", new Object[]{Component.literal(humanizeIdentifier(condition.getCharacterClass()))}
         );
         });
      } else {
         return Component.translatable("message.dragonminez.quest.start.unavailable");
      }
   }

   public static Component displayText(String raw) {
      if (raw == null || raw.isBlank()) {
         return Component.literal("?");
      } else {
         return !raw.contains(" ") && raw.contains(".") ? Component.translatable(raw) : Component.literal(raw);
      }
   }

   public static String humanizeResourceIdentifier(String raw) {
      if (raw != null && !raw.isBlank()) {
         String value = raw.startsWith("#") ? raw.substring(1) : raw;
         int colon = value.indexOf(58);
         String token = colon >= 0 ? value.substring(colon + 1) : value;
         if (token.startsWith("is_")) {
            token = token.substring(3);
         }

         return humanizeIdentifier(token);
      } else {
         return "?";
      }
   }

   public static String humanizeIdentifier(String raw) {
      if (raw != null && !raw.isBlank()) {
         String normalized = raw.replace('_', ' ').replace('-', ' ');
         String[] parts = normalized.split("\\s+");
         StringBuilder builder = new StringBuilder();

         for (String part : parts) {
            if (!part.isBlank()) {
               if (!builder.isEmpty()) {
                  builder.append(' ');
               }

               builder.append(Character.toUpperCase(part.charAt(0)));
               if (part.length() > 1) {
                  builder.append(part.substring(1).toLowerCase());
               }
            }
         }

         return builder.toString();
      } else {
         return "?";
      }
   }

   public static String formatGameTimeDuration(long ticks) {
      if (ticks <= 0L) {
         return "0s";
      } else {
         long totalSeconds = Math.max(1L, ticks / 20L);
         return formatSeconds(totalSeconds);
      }
   }

   public static String formatRealTimeDuration(long millis) {
      if (millis <= 0L) {
         return "0s";
      } else {
         long totalSeconds = Math.max(1L, millis / 1000L);
         return formatSeconds(totalSeconds);
      }
   }

   private static Component resolveInteractTarget(InteractObjective interact) {
      if (interact.getEntityName() != null && !interact.getEntityName().isBlank()) {
         return Component.literal(interact.getEntityName());
      } else {
         return (Component)(interact.getEntityTypeId() != null && !interact.getEntityTypeId().isBlank()
            ? resolveEntityName(interact.getEntityTypeId())
            : Component.literal("?"));
      }
   }

   private static Component resolveDragonName(String dragonId) {
      if (dragonId != null && !dragonId.isBlank()) {
         String entityId = dragonId.contains(":") ? dragonId : "dragonminez:" + dragonId;
         return resolveEntityName(entityId);
      } else {
         return Component.literal("the dragon");
      }
   }

   private static Component resolveEntityName(String entityId) {
      if (entityId != null && entityId.startsWith("#")) {
         String path = entityId.substring(1);
         int colon = path.indexOf(58);
         String key = "entity.dragonminez.tag." + (colon >= 0 ? path.substring(colon + 1) : path);
         Component translated = Component.translatable(key);
         return (Component)(translated.getString().equals(key) ? Component.literal(humanizeResourceIdentifier(entityId)) : translated);
      } else {
         ResourceLocation id = safeParse(entityId);
         if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            EntityType<?> type = (EntityType<?>)BuiltInRegistries.ENTITY_TYPE.get(id);
            return (Component)(type != null ? type.getDescription() : Component.literal(humanizeResourceIdentifier(entityId)));
         } else {
            return Component.literal(humanizeResourceIdentifier(entityId));
         }
      }
   }

   private static Component resolveItemName(String itemId) {
      ResourceLocation id = safeParse(itemId);
      if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
         Item item = (Item)BuiltInRegistries.ITEM.get(id);
         return (Component)(item != null && item != Items.AIR ? new ItemStack(item).getHoverName() : Component.literal(humanizeResourceIdentifier(itemId)));
      } else {
         return Component.literal(humanizeResourceIdentifier(itemId));
      }
   }

   private static Component resolveNpcName(String npcId) {
      if (npcId != null && !npcId.isBlank()) {
         String normalized = npcId.contains(":") ? npcId.substring(npcId.indexOf(58) + 1) : npcId;
         Component exact = resolveNpcTranslation(normalized);
         if (exact != null) {
            return exact;
         } else {
            Matcher variantMatcher = NPC_VARIANT_SUFFIX.matcher(normalized);
            if (variantMatcher.matches()) {
               Component variant = resolveNpcTranslation(variantMatcher.group(1));
               if (variant != null) {
                  return variant;
               }
            }

            return Component.literal(humanizeIdentifier(normalized));
         }
      } else {
         return Component.literal("?");
      }
   }

   private static Component resolveNpcTranslation(String npcId) {
      String questNpcKey = "entity.dragonminez.questnpc." + npcId;
      String masterKey = "gui.dragonminez.lines." + npcId + ".name";
      if (!npcId.contains(" ")) {
         return Component.translatable(questNpcKey).getString().equals(questNpcKey)
            ? (!Component.translatable(masterKey).getString().equals(masterKey) ? Component.translatable(masterKey) : null)
            : Component.translatable(questNpcKey);
      } else {
         return null;
      }
   }

   private static Component resolveSkillName(String skill) {
      if (skill != null && !skill.isBlank()) {
         String normalized = skill.contains(":") ? skill.substring(skill.indexOf(58) + 1) : skill;
         String key = normalized.contains(".") ? normalized : "skill.dragonminez." + normalized.toLowerCase();
         Component translated = Component.translatable(key);
         return (Component)(translated.getString().equals(key) ? Component.literal(humanizeIdentifier(normalized)) : translated);
      } else {
         return Component.literal("?");
      }
   }

   private static Component resolveStructureName(String structureId) {
      String display = STRUCTURE_NAMES.get(structureId);
      return display != null ? Component.literal(display) : Component.literal(humanizeResourceIdentifier(structureId));
   }

   public static Component resolveBiomeName(String biomeId) {
      ResourceLocation id = safeParse(biomeId != null && biomeId.startsWith("#") ? biomeId.substring(1) : biomeId);
      if (biomeId != null && biomeId.startsWith("#")) {
         return Component.literal(humanizeResourceIdentifier(biomeId));
      } else {
         return id != null && "minecraft".equals(id.getNamespace())
            ? Component.translatable("biome.minecraft." + id.getPath())
            : Component.literal(humanizeResourceIdentifier(biomeId));
      }
   }

   public static Component resolveDimensionName(String dimensionId) {
      String display = DIMENSION_NAMES.get(dimensionId);
      return display != null ? Component.literal(display) : Component.literal(humanizeResourceIdentifier(dimensionId));
   }

   private static Component buildStructureRequirement(QuestPrerequisites.Condition condition) {
      MutableComponent base = Component.translatable(
         "gui.dragonminez.quests.requirement.structure", new Object[]{resolveStructureName(condition.getStructureId())}
      );
      QuestPrerequisites.StructureHint hint = condition.getStructureHint();
      if (hint != null && (hint.hasCoordinates() || hint.dimensionId() != null)) {
         if (hint.dimensionId() != null) {
            base.append(Component.literal(" ("))
               .append(Component.translatable("gui.dragonminez.quests.requirement.dimension", new Object[]{resolveDimensionName(hint.dimensionId())}))
               .append(Component.literal(")"));
         }

         if (hint.hasCoordinates()) {
            base.append(Component.literal(" - "))
               .append(Component.translatable("gui.dragonminez.quests.requirement.coords", new Object[]{hint.x(), hint.y(), hint.z()}));
         }

         return base;
      } else {
         return base;
      }
   }

   private static Component buildTimeRequirement(QuestPrerequisites.Condition condition, QuestTextFormatter.RequirementContext context) {
      long duration = condition.getDuration() != null ? condition.getDuration() : 0L;
      MutableComponent base = condition.getTimeMode() == QuestPrerequisites.TimeMode.REAL_TIME
         ? Component.translatable("gui.dragonminez.quests.requirement.time_real", new Object[]{formatRealTimeDuration(duration)})
         : Component.translatable("gui.dragonminez.quests.requirement.time_game", new Object[]{formatGameTimeDuration(duration)});
      if (context != null && context.questKey() != null && !context.questKey().isBlank() && duration > 0L && context.data() != null) {
         PlayerQuestData.QuestStartRequirementTiming timing = context.data().getPlayerQuestData().getStartRequirementTiming(context.questKey());
         if (timing == null) {
            return base;
         } else if (condition.getTimeMode() == QuestPrerequisites.TimeMode.REAL_TIME) {
            long elapsed = Math.max(0L, context.realTimeMs() - timing.getRealTimeStartedMs());
            long remaining = Math.max(0L, duration - elapsed);
            return remaining > 0L
               ? base.append(Component.literal(" ("))
                  .append(Component.translatable("gui.dragonminez.quests.requirement.remaining", new Object[]{formatRealTimeDuration(remaining)}))
                  .append(Component.literal(")"))
               : base.append(Component.literal(" (")).append(Component.translatable("gui.dragonminez.quests.requirement.ready")).append(Component.literal(")"));
         } else {
            long elapsed = Math.max(0L, context.gameTime() - timing.getGameTimeStarted());
            long remaining = Math.max(0L, duration - elapsed);
            return remaining > 0L
               ? base.append(Component.literal(" ("))
                  .append(Component.translatable("gui.dragonminez.quests.requirement.remaining", new Object[]{formatGameTimeDuration(remaining)}))
                  .append(Component.literal(")"))
               : base.append(Component.literal(" (")).append(Component.translatable("gui.dragonminez.quests.requirement.ready")).append(Component.literal(")"));
         }
      } else {
         return base;
      }
   }

   private static Component buildAlignmentRequirement(QuestPrerequisites.Condition condition) {
      Integer min = condition.getMinAlignment();
      Integer max = condition.getMaxAlignment();
      if (min != null && max != null) {
         return Component.translatable("gui.dragonminez.quests.requirement.alignment_range", new Object[]{min, max});
      } else if (min != null) {
         return Component.translatable("gui.dragonminez.quests.requirement.alignment_min", new Object[]{min});
      } else {
         return max != null
            ? Component.translatable("gui.dragonminez.quests.requirement.alignment_max", new Object[]{max})
            : Component.translatable("gui.dragonminez.quests.requirement.alignment");
      }
   }

   private static Component resolveSagaQuestName(String sagaId, Integer questId) {
      if (sagaId != null && questId != null) {
         Saga saga = QuestRegistry.getSaga(sagaId);
         if (saga == null) {
            saga = QuestRegistry.getClientSaga(sagaId);
         }

         if (saga != null) {
            Quest quest = saga.getQuestById(questId);
            if (quest != null) {
               return displayText(quest.getTitle());
            }
         }

         return Component.literal(humanizeIdentifier(sagaId) + " " + questId);
      } else {
         return Component.literal("?");
      }
   }

   private static Component resolveQuestName(String questId) {
      if (questId != null && !questId.isBlank()) {
         Quest quest = QuestRegistry.getQuest(questId);
         if (quest == null) {
            quest = QuestRegistry.getClientQuest(questId);
         }

         return (Component)(quest != null ? displayText(quest.getTitle()) : Component.literal(humanizeResourceIdentifier(questId)));
      } else {
         return Component.literal("?");
      }
   }

   private static String formatSeconds(long totalSeconds) {
      long hours = totalSeconds / 3600L;
      long minutes = totalSeconds % 3600L / 60L;
      long seconds = totalSeconds % 60L;
      if (hours > 0L) {
         return minutes > 0L ? hours + "h " + minutes + "m" : hours + "h";
      } else if (minutes > 0L) {
         return seconds > 0L ? minutes + "m " + seconds + "s" : minutes + "m";
      } else {
         return seconds + "s";
      }
   }

   private static ResourceLocation safeParse(String id) {
      if (id != null && !id.isBlank() && id.contains(":")) {
         try {
            return ResourceLocation.parse(id);
         } catch (Exception var2) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static List<QuestTextFormatter.RewardGroup> groupRewardsByDifficulty(List<QuestReward> rewards, boolean excludeCommands) {
      LinkedHashMap<Set<Difficulty>, List<QuestReward>> grouped = new LinkedHashMap<>();
      if (rewards != null) {
         for (QuestReward reward : rewards) {
            if (!excludeCommands || reward.getType() != QuestReward.RewardType.COMMAND) {
               grouped.computeIfAbsent(reward.getDifficulties(), key -> new ArrayList<>()).add(reward);
            }
         }
      }

      List<QuestTextFormatter.RewardGroup> result = new ArrayList<>();

      for (Entry<Set<Difficulty>, List<QuestReward>> entry : grouped.entrySet()) {
         result.add(new QuestTextFormatter.RewardGroup(entry.getKey(), entry.getValue()));
      }

      result.sort(Comparator.comparingInt(group -> groupRank(group.difficulties())));
      return result;
   }

   private static int groupRank(Set<Difficulty> difficulties) {
      if (isUniversalDifficulty(difficulties)) {
         return -1;
      } else {
         int min = Integer.MAX_VALUE;

         for (Difficulty difficulty : difficulties) {
            min = Math.min(min, difficulty.ordinal());
         }

         return min;
      }
   }

   public static boolean isUniversalDifficulty(Set<Difficulty> difficulties) {
      return difficulties == null || difficulties.size() >= Difficulty.values().length;
   }

   public static boolean hasRewardTiers(List<QuestReward> rewards) {
      if (rewards == null) {
         return false;
      } else {
         for (QuestReward reward : rewards) {
            if (reward.getType() != QuestReward.RewardType.COMMAND && !isUniversalDifficulty(reward.getDifficulties())) {
               return true;
            }
         }

         return false;
      }
   }

   public static Component describeRewardDifficulties(Set<Difficulty> difficulties) {
      if (isUniversalDifficulty(difficulties)) {
         return Component.translatable("gui.dragonminez.quests.rewards.tier.all");
      } else {
         MutableComponent joined = Component.empty();
         boolean first = true;

         for (Difficulty difficulty : Difficulty.values()) {
            if (difficulties.contains(difficulty)) {
               if (!first) {
                  joined.append(", ");
               }

               joined.append(Component.translatable("gui.dragonminez.quest_tree.difficulty." + difficulty.name().toLowerCase()));
               first = false;
            }
         }

         return Component.translatable("gui.dragonminez.quests.rewards.tier.only", new Object[]{joined});
      }
   }

   public static int rewardDifficultyColor(Set<Difficulty> difficulties, boolean locked) {
      if (locked) {
         return -10066330;
      } else if (isUniversalDifficulty(difficulties)) {
         return -5592406;
      } else {
         return difficulties.contains(Difficulty.HARD) ? -43691 : -10496;
      }
   }

   public static ChatFormatting rewardDifficultyStyle(Set<Difficulty> difficulties) {
      if (isUniversalDifficulty(difficulties)) {
         return ChatFormatting.GRAY;
      } else {
         return difficulties.contains(Difficulty.HARD) ? ChatFormatting.RED : ChatFormatting.YELLOW;
      }
   }

   public static record RequirementContext(StatsData data, Player player, String questKey) {
      public Level level() {
         return this.player != null ? this.player.level() : null;
      }

      public long gameTime() {
         return this.level() != null ? this.level().getGameTime() : 0L;
      }

      public long realTimeMs() {
         return System.currentTimeMillis();
      }
   }

   public static record RewardGroup(Set<Difficulty> difficulties, List<QuestReward> rewards) {
   }
}
