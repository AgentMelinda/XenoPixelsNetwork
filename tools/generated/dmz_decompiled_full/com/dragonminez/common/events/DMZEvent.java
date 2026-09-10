package com.dragonminez.common.events;

import com.dragonminez.common.dragonball.DragonBallSetDefinition;
import com.dragonminez.common.dragonball.DragonDefinition;
import com.dragonminez.common.quest.Difficulty;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.Saga;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import java.util.List;
import lombok.Generated;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class DMZEvent extends Event {
   private static String buildQuestKey(Saga saga, Quest quest) {
      if (quest == null) {
         return "";
      } else if (saga == null) {
         return quest.getStringId() != null ? quest.getStringId() : String.valueOf(quest.getId());
      } else {
         return saga.getId() + ":" + quest.getId();
      }
   }

   public static class CritChanceEvent extends Event {
      private final Player player;
      private double chance;

      public CritChanceEvent(Player player, double chance) {
         this.player = player;
         this.chance = chance;
      }

      @Generated
      public Player getPlayer() {
         return this.player;
      }

      @Generated
      public double getChance() {
         return this.chance;
      }

      @Generated
      public void setChance(double chance) {
         this.chance = chance;
      }
   }

   public static class DamageDealtEvent extends Event {
      private final Player attacker;
      private final LivingEntity victim;
      private final double amount;
      private final boolean blocked;
      private final boolean parried;
      private final DMZEvent.DamageSourceType sourceType;

      public DamageDealtEvent(Player attacker, LivingEntity victim, double amount, boolean blocked, boolean parried, DMZEvent.DamageSourceType sourceType) {
         this.attacker = attacker;
         this.victim = victim;
         this.amount = amount;
         this.blocked = blocked;
         this.parried = parried;
         this.sourceType = sourceType;
      }

      @Generated
      public Player getAttacker() {
         return this.attacker;
      }

      @Generated
      public LivingEntity getVictim() {
         return this.victim;
      }

      @Generated
      public double getAmount() {
         return this.amount;
      }

      @Generated
      public boolean isBlocked() {
         return this.blocked;
      }

      @Generated
      public boolean isParried() {
         return this.parried;
      }

      @Generated
      public DMZEvent.DamageSourceType getSourceType() {
         return this.sourceType;
      }
   }

   public static class DamageModifyEvent extends Event implements ICancellableEvent {
      private final Player attacker;
      private final LivingEntity victim;
      private double amount;
      private double defensePenetration;
      private final DMZEvent.DamageSourceType sourceType;

      public DamageModifyEvent(Player attacker, LivingEntity victim, double amount, double defensePenetration, DMZEvent.DamageSourceType sourceType) {
         this.attacker = attacker;
         this.victim = victim;
         this.amount = amount;
         this.defensePenetration = defensePenetration;
         this.sourceType = sourceType;
      }

      @Generated
      public Player getAttacker() {
         return this.attacker;
      }

      @Generated
      public LivingEntity getVictim() {
         return this.victim;
      }

      @Generated
      public double getAmount() {
         return this.amount;
      }

      @Generated
      public double getDefensePenetration() {
         return this.defensePenetration;
      }

      @Generated
      public DMZEvent.DamageSourceType getSourceType() {
         return this.sourceType;
      }

      @Generated
      public void setAmount(double amount) {
         this.amount = amount;
      }

      @Generated
      public void setDefensePenetration(double defensePenetration) {
         this.defensePenetration = defensePenetration;
      }
   }

   public static enum DamageSourceType {
      MELEE,
      KI,
      STRIKE;
   }

   public static class DragonSummonedEvent extends Event {
      private final Player player;
      private final ServerLevel level;
      private final BlockPos position;
      private final DragonDefinition dragonDefinition;
      private final DragonBallSetDefinition ballSetDefinition;
      private final List<BlockPos> consumedPositions;

      public DragonSummonedEvent(
         Player player,
         ServerLevel level,
         BlockPos position,
         DragonDefinition dragonDefinition,
         DragonBallSetDefinition ballSetDefinition,
         List<BlockPos> consumedPositions
      ) {
         this.player = player;
         this.level = level;
         this.position = position != null ? position.immutable() : BlockPos.ZERO;
         this.dragonDefinition = dragonDefinition;
         this.ballSetDefinition = ballSetDefinition;
         this.consumedPositions = consumedPositions == null ? List.of() : consumedPositions.stream().<BlockPos>map(BlockPos::immutable).toList();
      }

      public String getDragonId() {
         return this.dragonDefinition != null ? this.dragonDefinition.getId() : "";
      }

      public String getBallSetId() {
         return this.ballSetDefinition != null ? this.ballSetDefinition.getId() : "";
      }

      @Generated
      public Player getPlayer() {
         return this.player;
      }

      @Generated
      public ServerLevel getLevel() {
         return this.level;
      }

      @Generated
      public BlockPos getPosition() {
         return this.position;
      }

      @Generated
      public DragonDefinition getDragonDefinition() {
         return this.dragonDefinition;
      }

      @Generated
      public DragonBallSetDefinition getBallSetDefinition() {
         return this.ballSetDefinition;
      }

      @Generated
      public List<BlockPos> getConsumedPositions() {
         return this.consumedPositions;
      }
   }

   public static class EnergyRegenEvent extends DMZEvent.ResourceRegenEvent {
      public EnergyRegenEvent(Player player, StatsData statsData, double amount) {
         super(player, statsData, amount);
      }
   }

   public static class FormChangeEvent extends Event {
      private final ServerPlayer player;
      private final String oldGroup;
      private final String oldForm;
      private final String newGroup;
      private final String newForm;

      public FormChangeEvent(ServerPlayer player, String oldGroup, String oldForm, String newGroup, String newForm) {
         this.player = player;
         this.oldGroup = oldGroup == null ? "" : oldGroup;
         this.oldForm = oldForm == null ? "" : oldForm;
         this.newGroup = newGroup == null ? "" : newGroup;
         this.newForm = newForm == null ? "" : newForm;
      }

      public boolean isTransform() {
         return this.oldForm.isEmpty() && !this.newForm.isEmpty();
      }

      public boolean isUntransform() {
         return !this.oldForm.isEmpty() && this.newForm.isEmpty();
      }

      @Generated
      public ServerPlayer getPlayer() {
         return this.player;
      }

      @Generated
      public String getOldGroup() {
         return this.oldGroup;
      }

      @Generated
      public String getOldForm() {
         return this.oldForm;
      }

      @Generated
      public String getNewGroup() {
         return this.newGroup;
      }

      @Generated
      public String getNewForm() {
         return this.newForm;
      }
   }

   public static class FusionEvent extends Event implements ICancellableEvent {
      private final ServerPlayer initiator;
      private final LivingEntity target;
      private final DMZEvent.FusionEvent.FusionType type;

      public FusionEvent(ServerPlayer initiator, LivingEntity target, DMZEvent.FusionEvent.FusionType type) {
         this.initiator = initiator;
         this.target = target;
         this.type = type;
      }

      @Generated
      public ServerPlayer getInitiator() {
         return this.initiator;
      }

      @Generated
      public LivingEntity getTarget() {
         return this.target;
      }

      @Generated
      public DMZEvent.FusionEvent.FusionType getType() {
         return this.type;
      }

      public static enum FusionType {
         METAMORU,
         POTHALA,
         ABSORPTION,
         ASSIMILATION;
      }
   }

   public static class HealthRegenEvent extends DMZEvent.ResourceRegenEvent {
      public HealthRegenEvent(Player player, StatsData statsData, double amount) {
         super(player, statsData, amount);
      }
   }

   public static class KiAttackCastEvent extends Event {
      private final Player player;
      private final StatsData statsData;
      private final KiAttackData kiAttack;

      public KiAttackCastEvent(Player player, StatsData statsData, KiAttackData kiAttack) {
         this.player = player;
         this.statsData = statsData;
         this.kiAttack = kiAttack;
      }

      @Generated
      public Player getPlayer() {
         return this.player;
      }

      @Generated
      public StatsData getStatsData() {
         return this.statsData;
      }

      @Generated
      public KiAttackData getKiAttack() {
         return this.kiAttack;
      }
   }

   public static class KiAttackFireEvent extends Event {
      private final Player player;
      private final StatsData statsData;
      private final KiAttackData kiAttack;
      private final float chargeMultiplier;
      private int cooldownTicks;

      public KiAttackFireEvent(Player player, StatsData statsData, KiAttackData kiAttack, float chargeMultiplier, int cooldownTicks) {
         this.player = player;
         this.statsData = statsData;
         this.kiAttack = kiAttack;
         this.chargeMultiplier = chargeMultiplier;
         this.cooldownTicks = cooldownTicks;
      }

      @Generated
      public Player getPlayer() {
         return this.player;
      }

      @Generated
      public StatsData getStatsData() {
         return this.statsData;
      }

      @Generated
      public KiAttackData getKiAttack() {
         return this.kiAttack;
      }

      @Generated
      public float getChargeMultiplier() {
         return this.chargeMultiplier;
      }

      @Generated
      public int getCooldownTicks() {
         return this.cooldownTicks;
      }

      @Generated
      public void setCooldownTicks(int cooldownTicks) {
         this.cooldownTicks = cooldownTicks;
      }
   }

   public static class KiChargeEvent extends Event implements ICancellableEvent {
      private final Player player;
      private final float currentEnergy;
      private final float maxEnergy;

      public KiChargeEvent(Player player, float currentEnergy, float maxEnergy) {
         this.player = player;
         this.currentEnergy = currentEnergy;
         this.maxEnergy = maxEnergy;
      }

      public boolean isEnergyFull() {
         return this.currentEnergy >= this.maxEnergy;
      }

      @Generated
      public Player getPlayer() {
         return this.player;
      }

      @Generated
      public float getCurrentEnergy() {
         return this.currentEnergy;
      }

      @Generated
      public float getMaxEnergy() {
         return this.maxEnergy;
      }
   }

   public static class PlayerBlockEvent extends Event implements ICancellableEvent {
      private final ServerPlayer victim;
      private final LivingEntity attacker;
      private final float originalDamage;
      private float finalDamage;
      private boolean isParry;
      private float poiseDamage;

      public PlayerBlockEvent(ServerPlayer victim, LivingEntity attacker, float originalDamage, float finalDamage, boolean isParry, float poiseDamage) {
         this.victim = victim;
         this.attacker = attacker;
         this.originalDamage = originalDamage;
         this.finalDamage = finalDamage;
         this.isParry = isParry;
         this.poiseDamage = poiseDamage;
      }

      @Generated
      public ServerPlayer getVictim() {
         return this.victim;
      }

      @Generated
      public LivingEntity getAttacker() {
         return this.attacker;
      }

      @Generated
      public float getOriginalDamage() {
         return this.originalDamage;
      }

      @Generated
      public float getFinalDamage() {
         return this.finalDamage;
      }

      @Generated
      public boolean isParry() {
         return this.isParry;
      }

      @Generated
      public float getPoiseDamage() {
         return this.poiseDamage;
      }

      @Generated
      public void setFinalDamage(float finalDamage) {
         this.finalDamage = finalDamage;
      }

      @Generated
      public void setParry(boolean isParry) {
         this.isParry = isParry;
      }

      @Generated
      public void setPoiseDamage(float poiseDamage) {
         this.poiseDamage = poiseDamage;
      }
   }

   public static class PlayerDashEvent extends Event implements ICancellableEvent {
      private final ServerPlayer player;
      private final DMZEvent.PlayerDashEvent.DashType dashType;
      private double distance;
      private int kiCost;

      public PlayerDashEvent(ServerPlayer player, DMZEvent.PlayerDashEvent.DashType dashType, double distance, int kiCost) {
         this.player = player;
         this.dashType = dashType;
         this.distance = distance;
         this.kiCost = kiCost;
      }

      @Generated
      public ServerPlayer getPlayer() {
         return this.player;
      }

      @Generated
      public DMZEvent.PlayerDashEvent.DashType getDashType() {
         return this.dashType;
      }

      @Generated
      public double getDistance() {
         return this.distance;
      }

      @Generated
      public int getKiCost() {
         return this.kiCost;
      }

      @Generated
      public void setDistance(double distance) {
         this.distance = distance;
      }

      @Generated
      public void setKiCost(int kiCost) {
         this.kiCost = kiCost;
      }

      public static enum DashType {
         NORMAL,
         DOUBLE;
      }
   }

   public static class PlayerDataLoadEvent extends Event {
      private final ServerPlayer player;
      private final CompoundTag data;

      public PlayerDataLoadEvent(ServerPlayer player, CompoundTag data) {
         this.player = player;
         this.data = data;
      }

      @Generated
      public ServerPlayer getPlayer() {
         return this.player;
      }

      @Generated
      public CompoundTag getData() {
         return this.data;
      }
   }

   public static class PlayerDataSaveEvent extends Event {
      private final ServerPlayer player;
      private final CompoundTag data;

      public PlayerDataSaveEvent(ServerPlayer player, CompoundTag data) {
         this.player = player;
         this.data = data;
      }

      @Generated
      public ServerPlayer getPlayer() {
         return this.player;
      }

      @Generated
      public CompoundTag getData() {
         return this.data;
      }
   }

   public static class PlayerEvasionEvent extends Event implements ICancellableEvent {
      private final ServerPlayer player;
      private final LivingEntity attacker;
      private final float originalDamage;
      private int kiCost;

      public PlayerEvasionEvent(ServerPlayer player, LivingEntity attacker, float originalDamage, int kiCost) {
         this.player = player;
         this.attacker = attacker;
         this.originalDamage = originalDamage;
         this.kiCost = kiCost;
      }

      @Generated
      public ServerPlayer getPlayer() {
         return this.player;
      }

      @Generated
      public LivingEntity getAttacker() {
         return this.attacker;
      }

      @Generated
      public float getOriginalDamage() {
         return this.originalDamage;
      }

      @Generated
      public int getKiCost() {
         return this.kiCost;
      }

      @Generated
      public void setKiCost(int kiCost) {
         this.kiCost = kiCost;
      }
   }

   public static class QuestCompletedEvent extends DMZEvent.QuestLifecycleEvent {
      public QuestCompletedEvent(ServerPlayer player, Saga saga, Quest quest, List<ServerPlayer> partyMembers) {
         super(player, DMZEvent.buildQuestKey(saga, quest), saga, quest, partyMembers);
      }

      public QuestCompletedEvent(ServerPlayer player, String questKey, Saga saga, Quest quest, List<ServerPlayer> partyMembers) {
         super(player, questKey, saga, quest, partyMembers);
      }
   }

   public static class QuestFailEvent extends DMZEvent.QuestLifecycleEvent implements ICancellableEvent {
      private final DMZEvent.QuestFailEvent.FailureReason reason;

      public QuestFailEvent(
         ServerPlayer player, String questKey, Saga saga, Quest quest, List<ServerPlayer> partyMembers, DMZEvent.QuestFailEvent.FailureReason reason
      ) {
         super(player, questKey, saga, quest, partyMembers);
         this.reason = reason;
      }

      @Generated
      public DMZEvent.QuestFailEvent.FailureReason getReason() {
         return this.reason;
      }

      public static enum FailureReason {
         PLAYER_DEATH,
         FORCED_RESET,
         SCRIPT;
      }
   }

   public abstract static class QuestLifecycleEvent extends Event implements ICancellableEvent {
      private final ServerPlayer player;
      private final String questKey;
      private final Saga saga;
      private final Quest quest;
      private final List<ServerPlayer> partyMembers;

      public QuestLifecycleEvent(ServerPlayer player, String questKey, Saga saga, Quest quest, List<ServerPlayer> partyMembers) {
         this.player = player;
         this.questKey = questKey == null ? "" : questKey;
         this.saga = saga;
         this.quest = quest;
         this.partyMembers = partyMembers == null ? List.of() : List.copyOf(partyMembers);
      }

      @Generated
      public ServerPlayer getPlayer() {
         return this.player;
      }

      @Generated
      public String getQuestKey() {
         return this.questKey;
      }

      @Generated
      public Saga getSaga() {
         return this.saga;
      }

      @Generated
      public Quest getQuest() {
         return this.quest;
      }

      @Generated
      public List<ServerPlayer> getPartyMembers() {
         return this.partyMembers;
      }
   }

   public static class QuestObjectiveProgressEvent extends DMZEvent.QuestLifecycleEvent implements ICancellableEvent {
      private final int objectiveIndex;
      private final int oldProgress;
      private int newProgress;
      private final int objectiveRequired;

      public QuestObjectiveProgressEvent(
         ServerPlayer player,
         String questKey,
         Saga saga,
         Quest quest,
         List<ServerPlayer> partyMembers,
         int objectiveIndex,
         int oldProgress,
         int newProgress,
         int objectiveRequired
      ) {
         super(player, questKey, saga, quest, partyMembers);
         this.objectiveIndex = objectiveIndex;
         this.oldProgress = oldProgress;
         this.newProgress = newProgress;
         this.objectiveRequired = objectiveRequired;
      }

      @Generated
      public int getObjectiveIndex() {
         return this.objectiveIndex;
      }

      @Generated
      public int getOldProgress() {
         return this.oldProgress;
      }

      @Generated
      public int getNewProgress() {
         return this.newProgress;
      }

      @Generated
      public int getObjectiveRequired() {
         return this.objectiveRequired;
      }

      @Generated
      public void setNewProgress(int newProgress) {
         this.newProgress = newProgress;
      }
   }

   public static class QuestRewardClaimEvent extends DMZEvent.QuestLifecycleEvent implements ICancellableEvent {
      private final int rewardIndex;

      public QuestRewardClaimEvent(ServerPlayer player, String questKey, Saga saga, Quest quest, List<ServerPlayer> partyMembers, int rewardIndex) {
         super(player, questKey, saga, quest, partyMembers);
         this.rewardIndex = rewardIndex;
      }

      @Generated
      public int getRewardIndex() {
         return this.rewardIndex;
      }
   }

   public static class QuestStartEvent extends DMZEvent.QuestLifecycleEvent implements ICancellableEvent {
      private Difficulty difficulty;

      public QuestStartEvent(ServerPlayer player, String questKey, Saga saga, Quest quest, List<ServerPlayer> partyMembers, Difficulty difficulty) {
         super(player, questKey, saga, quest, partyMembers);
         this.difficulty = difficulty;
      }

      @Generated
      public void setDifficulty(Difficulty difficulty) {
         this.difficulty = difficulty;
      }

      @Generated
      public Difficulty getDifficulty() {
         return this.difficulty;
      }
   }

   public static class QuestTurnInEvent extends DMZEvent.QuestLifecycleEvent implements ICancellableEvent {
      private final String npcId;

      public QuestTurnInEvent(ServerPlayer player, String questKey, Saga saga, Quest quest, List<ServerPlayer> partyMembers, String npcId) {
         super(player, questKey, saga, quest, partyMembers);
         this.npcId = npcId == null ? "" : npcId;
      }

      @Generated
      public String getNpcId() {
         return this.npcId;
      }
   }

   public abstract static class ResourceRegenEvent extends Event implements ICancellableEvent {
      private final Player player;
      private final StatsData statsData;
      private double amount;

      protected ResourceRegenEvent(Player player, StatsData statsData, double amount) {
         this.player = player;
         this.statsData = statsData;
         this.amount = amount;
      }

      @Generated
      public Player getPlayer() {
         return this.player;
      }

      @Generated
      public StatsData getStatsData() {
         return this.statsData;
      }

      @Generated
      public double getAmount() {
         return this.amount;
      }

      @Generated
      public void setAmount(double amount) {
         this.amount = amount;
      }
   }

   public static class StackFormChangeEvent extends Event {
      private final ServerPlayer player;
      private final String oldGroup;
      private final String oldForm;
      private final String newGroup;
      private final String newForm;

      public StackFormChangeEvent(ServerPlayer player, String oldGroup, String oldForm, String newGroup, String newForm) {
         this.player = player;
         this.oldGroup = oldGroup == null ? "" : oldGroup;
         this.oldForm = oldForm == null ? "" : oldForm;
         this.newGroup = newGroup == null ? "" : newGroup;
         this.newForm = newForm == null ? "" : newForm;
      }

      public boolean isTransform() {
         return this.oldForm.isEmpty() && !this.newForm.isEmpty();
      }

      public boolean isUntransform() {
         return !this.oldForm.isEmpty() && this.newForm.isEmpty();
      }

      @Generated
      public ServerPlayer getPlayer() {
         return this.player;
      }

      @Generated
      public String getOldGroup() {
         return this.oldGroup;
      }

      @Generated
      public String getOldForm() {
         return this.oldForm;
      }

      @Generated
      public String getNewGroup() {
         return this.newGroup;
      }

      @Generated
      public String getNewForm() {
         return this.newForm;
      }
   }

   public static class StaminaRegenEvent extends DMZEvent.ResourceRegenEvent {
      public StaminaRegenEvent(Player player, StatsData statsData, double amount) {
         super(player, statsData, amount);
      }
   }

   public static class StatChangeEvent extends Event implements ICancellableEvent {
      private final Player player;
      private final DMZEvent.StatChangeEvent.StatType stat;
      private final int oldValue;
      private final int newValue;

      public StatChangeEvent(Player player, DMZEvent.StatChangeEvent.StatType stat, int oldValue, int newValue) {
         this.player = player;
         this.stat = stat;
         this.oldValue = oldValue;
         this.newValue = newValue;
      }

      @Generated
      public Player getPlayer() {
         return this.player;
      }

      @Generated
      public DMZEvent.StatChangeEvent.StatType getStat() {
         return this.stat;
      }

      @Generated
      public int getOldValue() {
         return this.oldValue;
      }

      @Generated
      public int getNewValue() {
         return this.newValue;
      }

      public static enum StatType {
         STRENGTH,
         STRIKE_POWER,
         RESISTANCE,
         VITALITY,
         KI_POWER,
         ENERGY;
      }
   }

   public static class StrikeAttackCastEvent extends Event {
      private final ServerPlayer player;
      private final StatsData statsData;
      private final StrikeAttackData strike;

      public StrikeAttackCastEvent(ServerPlayer player, StatsData statsData, StrikeAttackData strike) {
         this.player = player;
         this.statsData = statsData;
         this.strike = strike;
      }

      @Generated
      public ServerPlayer getPlayer() {
         return this.player;
      }

      @Generated
      public StatsData getStatsData() {
         return this.statsData;
      }

      @Generated
      public StrikeAttackData getStrike() {
         return this.strike;
      }
   }

   public static class StrikeAttackFireEvent extends Event {
      private final ServerPlayer player;
      private final StatsData statsData;
      private final StrikeAttackData strike;
      private final LivingEntity target;

      public StrikeAttackFireEvent(ServerPlayer player, StatsData statsData, StrikeAttackData strike, LivingEntity target) {
         this.player = player;
         this.statsData = statsData;
         this.strike = strike;
         this.target = target;
      }

      @Generated
      public ServerPlayer getPlayer() {
         return this.player;
      }

      @Generated
      public StatsData getStatsData() {
         return this.statsData;
      }

      @Generated
      public StrikeAttackData getStrike() {
         return this.strike;
      }

      @Generated
      public LivingEntity getTarget() {
         return this.target;
      }
   }

   public static class TPGainEvent extends Event implements ICancellableEvent {
      private final Player player;
      private final int oldValue;
      private boolean shareWithParty;
      private int tpGain;

      public TPGainEvent(Player player, int oldValue, int tpGain, boolean shareWithParty) {
         this.player = player;
         this.oldValue = oldValue;
         this.tpGain = tpGain;
         this.shareWithParty = shareWithParty;
      }

      public int getNewTpsValue() {
         return this.oldValue + this.tpGain;
      }

      public boolean getShareWithParty() {
         return this.shareWithParty;
      }

      @Generated
      public Player getPlayer() {
         return this.player;
      }

      @Generated
      public int getOldValue() {
         return this.oldValue;
      }

      @Generated
      public int getTpGain() {
         return this.tpGain;
      }

      @Generated
      public void setShareWithParty(boolean shareWithParty) {
         this.shareWithParty = shareWithParty;
      }

      @Generated
      public void setTpGain(int tpGain) {
         this.tpGain = tpGain;
      }
   }
}
