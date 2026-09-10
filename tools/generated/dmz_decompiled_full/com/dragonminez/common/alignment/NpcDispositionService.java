package com.dragonminez.common.alignment;

import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.common.init.entities.MastersEntity;
import com.dragonminez.common.init.entities.questnpc.QuestNPCEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public final class NpcDispositionService {
   public static final String NPC_ALIGNMENT_TAG = "DmzNpcAlignment";
   public static final String NPC_RELATION_OVERRIDE_TAG = "DmzNpcRelationOverride";
   private static final Set<String> GOOD_ALIGNED_MASTERS = Set.of("goku", "gohan", "kingkai", "oldkai", "roshi", "krillin");
   private static final Set<String> EVIL_ALIGNED_MASTERS = Set.of("cell", "frieza");
   private static final int GOOD_ALIGNMENT_MIN = 61;
   private static final int EVIL_ALIGNMENT_MAX = 40;

   private NpcDispositionService() {
   }

   public static boolean isInteractiveNpc(Entity entity) {
      if (entity instanceof QuestNPCEntity) {
         return true;
      } else {
         if (entity instanceof MastersEntity master && master.getMasterName() != null && !master.getMasterName().isBlank()) {
            return true;
         }

         return false;
      }
   }

   public static Optional<TargetHelper.Relation> getInteractiveRelation(Player player, Entity target) {
      return !isInteractiveNpc(target) ? Optional.empty() : Optional.of(getRelation(player, target));
   }

   public static TargetHelper.Relation getRelation(Player player, Entity target) {
      if (player != null && target != null) {
         String npcKey = resolveNpcKey(target);
         String hostilityKey = resolveHostilityKey(target);
         StatsData stats = player instanceof ServerPlayer serverPlayer ? StatsProvider.get(StatsCapability.INSTANCE, serverPlayer).orElse(null) : null;
         if (stats != null && stats.getPlayerQuestData().isNpcHostile(hostilityKey)) {
            return TargetHelper.Relation.HOSTILE;
         } else {
            TargetHelper.Relation override = readRelationOverride(target.getPersistentData());
            if (override != null) {
               return override;
            } else {
               int playerAlignment = stats != null ? stats.getResources().getAlignment() : 100;
               NpcAlignmentRule rule = NpcAlignmentRules.get(npcKey);
               if (rule != null && rule.isHostileFor(playerAlignment)) {
                  return TargetHelper.Relation.HOSTILE;
               } else {
                  Integer npcAlignment = readNpcAlignment(target.getPersistentData());
                  if (npcAlignment != null) {
                     return relationFromNpcAlignment(npcAlignment);
                  } else {
                     return rule != null ? rule.defaultRelation() : TargetHelper.Relation.NEUTRAL;
                  }
               }
            }
         }
      } else {
         return TargetHelper.Relation.HOSTILE;
      }
   }

   @Nullable
   public static Component getDialogueBlocker(ServerPlayer player, Entity npc) {
      if (player != null && npc != null) {
         StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
         if (data == null) {
            return Component.translatable("message.dragonminez.npc.unavailable");
         } else {
            if (npc instanceof MastersEntity master && master.getMasterName() != null && !master.getMasterName().isBlank()) {
               return masterAlignmentBlocker(data, master.getMasterName());
            }

            TargetHelper.Relation relation = getRelation(player, npc);
            return (Component)(relation == TargetHelper.Relation.HOSTILE
               ? Component.translatable("message.dragonminez.npc.hostile")
               : getAlignmentBlocker(data, resolveNpcKey(npc)));
         }
      } else {
         return Component.translatable("message.dragonminez.npc.unavailable");
      }
   }

   @Nullable
   public static Component getServiceBlocker(ServerPlayer player, String npcId) {
      if (player != null && npcId != null && !npcId.isBlank()) {
         StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
         return (Component)(data == null ? Component.translatable("message.dragonminez.npc.unavailable") : masterAlignmentBlocker(data, npcId));
      } else {
         return Component.translatable("message.dragonminez.npc.unavailable");
      }
   }

   public static void markHostile(ServerPlayer player, Entity npc) {
      if (player != null && npc != null && isInteractiveNpc(npc)) {
         if (!(npc instanceof MastersEntity)) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> data.getPlayerQuestData().markNpcHostile(resolveHostilityKey(npc)));
         }
      }
   }

   @Nullable
   private static Component masterAlignmentBlocker(StatsData data, String masterName) {
      String key = normalizeNpcKey(masterName);
      int alignment = data.getResources().getAlignment();
      if (GOOD_ALIGNED_MASTERS.contains(key) && alignment < 61) {
         return Component.translatable("message.dragonminez.npc.alignment_too_low", new Object[]{61});
      } else {
         return EVIL_ALIGNED_MASTERS.contains(key) && alignment > 40
            ? Component.translatable("message.dragonminez.npc.alignment_too_high", new Object[]{40})
            : null;
      }
   }

   public static String resolveNpcKey(Entity entity) {
      if (entity instanceof QuestNPCEntity questNPC) {
         return normalizeNpcKey(questNPC.getNpcId());
      } else {
         if (entity instanceof MastersEntity master && master.getMasterName() != null && !master.getMasterName().isBlank()) {
            return normalizeNpcKey(master.getMasterName());
         }

         CompoundTag data = entity.getPersistentData();
         return data.contains("dmz_npc_placement_id", 8) ? normalizeNpcKey(data.getString("dmz_npc_placement_id")) : entity.getStringUUID();
      }
   }

   public static String resolveHostilityKey(Entity entity) {
      CompoundTag data = entity.getPersistentData();
      return data.contains("dmz_npc_placement_id", 8) ? normalizeNpcKey(data.getString("dmz_npc_placement_id")) : resolveNpcKey(entity);
   }

   private static Component getAlignmentBlocker(StatsData data, String npcKey) {
      NpcAlignmentRule rule = NpcAlignmentRules.get(npcKey);
      if (rule == null) {
         return null;
      } else {
         int alignment = data.getResources().getAlignment();
         if (rule.allowsInteraction(alignment)) {
            return null;
         } else {
            Integer min = rule.minInteractionAlignment();
            Integer max = rule.maxInteractionAlignment();
            if (min != null && alignment < min) {
               return Component.translatable("message.dragonminez.npc.alignment_too_low", new Object[]{min});
            } else {
               return max != null && alignment > max
                  ? Component.translatable("message.dragonminez.npc.alignment_too_high", new Object[]{max})
                  : Component.translatable("message.dragonminez.npc.unavailable");
            }
         }
      }
   }

   private static TargetHelper.Relation relationFromNpcAlignment(int npcAlignment) {
      return switch (AlignmentBand.fromValue(npcAlignment)) {
         case GOOD -> TargetHelper.Relation.FRIENDLY;
         case NEUTRAL -> TargetHelper.Relation.NEUTRAL;
         case EVIL -> TargetHelper.Relation.HOSTILE;
      };
   }

   @Nullable
   private static Integer readNpcAlignment(CompoundTag tag) {
      return tag.contains("DmzNpcAlignment", 3) ? Math.max(0, Math.min(100, tag.getInt("DmzNpcAlignment"))) : null;
   }

   @Nullable
   private static TargetHelper.Relation readRelationOverride(CompoundTag tag) {
      if (!tag.contains("DmzNpcRelationOverride", 8)) {
         return null;
      } else {
         try {
            return TargetHelper.Relation.valueOf(tag.getString("DmzNpcRelationOverride").trim().toUpperCase());
         } catch (IllegalArgumentException var2) {
            return null;
         }
      }
   }

   private static String normalizeNpcKey(String npcId) {
      String normalized = npcId == null ? "" : npcId.trim().toLowerCase();
      if (normalized.contains(":")) {
         normalized = normalized.substring(normalized.indexOf(58) + 1);
      }

      return normalized;
   }
}
