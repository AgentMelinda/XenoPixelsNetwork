package com.dragonminez.server.world.raid;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.init.entities.IBattlePower;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;

public class Raid {
   public static final String RAID_ID_TAG = "dmz_raid_id";
   private final UUID raidId;
   private final String typeId;
   private final ResourceKey<Level> dimension;
   private final BlockPos center;
   private final Set<UUID> participants;
   private Raid.Status status = Raid.Status.ACTIVE;
   private int currentWaveIndex = -1;
   private final Set<UUID> currentWaveMobs = new HashSet<>();
   private int waveDelayTimer = 0;
   private int totalMobsThisWave = 0;
   private ServerBossEvent bossEvent;

   public Raid(UUID raidId, String typeId, ResourceKey<Level> dimension, BlockPos center, Set<UUID> participants) {
      this.raidId = raidId;
      this.typeId = typeId;
      this.dimension = dimension;
      this.center = center;
      this.participants = new HashSet<>(participants);
   }

   public boolean isFinished() {
      return this.status != Raid.Status.ACTIVE;
   }

   public int getCurrentWave() {
      return this.currentWaveIndex + 1;
   }

   public boolean hasParticipant(UUID id) {
      return this.participants.contains(id);
   }

   public void removeParticipant(UUID id) {
      this.participants.remove(id);
   }

   private RaidType type() {
      return RaidTypes.getOrDefault(this.typeId);
   }

   public void tick(ServerLevel level) {
      if (this.status == Raid.Status.ACTIVE) {
         RaidType type = this.type();
         this.ensureBossEvent(type);
         List<ServerPlayer> active = this.resolveActiveParticipants(level, type);
         this.refreshBossPlayers(active);
         if (active.isEmpty()) {
            this.fail(level);
         } else if (this.currentWaveIndex < 0) {
            this.spawnWave(level, 0);
         } else if (this.waveDelayTimer > 0) {
            this.waveDelayTimer--;
            this.updateBossBar(type, this.totalMobsThisWave);
            if (this.waveDelayTimer == 0) {
               this.spawnWave(level, this.currentWaveIndex + 1);
            }
         } else {
            int alive = this.countAliveMobs(level);
            this.updateBossBar(type, alive);
            if (alive <= 0) {
               if (type.isFinalWave(this.currentWaveIndex)) {
                  this.win(level, active);
               } else {
                  this.waveDelayTimer = type.getInterWaveDelayTicks();
               }
            }
         }
      }
   }

   private int countAliveMobs(ServerLevel level) {
      int alive = 0;
      Iterator<UUID> it = this.currentWaveMobs.iterator();

      while (it.hasNext()) {
         Entity entity = level.getEntity(it.next());
         if (entity instanceof LivingEntity living && living.isAlive()) {
            alive++;
            continue;
         }

         if (entity == null) {
            alive++;
         } else {
            it.remove();
         }
      }

      return alive;
   }

   private void spawnWave(ServerLevel level, int waveIndex) {
      RaidType type = this.type();
      if (waveIndex >= type.waveCount()) {
         this.win(level, this.resolveActiveParticipants(level, type));
      } else {
         this.currentWaveIndex = waveIndex;
         this.currentWaveMobs.clear();
         RaidWave wave = type.wave(waveIndex);
         RandomSource random = level.getRandom();
         LivingEntity focus = this.nearestParticipant(level, type);

         for (RaidWave.SpawnEntry entry : wave.getSpawns()) {
            EntityType<?> entityType = (EntityType<?>)entry.type().get();

            for (int i = 0; i < entry.count(); i++) {
               Mob mob = this.spawnOne(level, entityType, wave, random, focus);
               if (mob != null) {
                  this.currentWaveMobs.add(mob.getUUID());
               }
            }
         }

         this.totalMobsThisWave = Math.max(1, this.currentWaveMobs.size());
         this.updateBossBar(type, this.currentWaveMobs.size());
         LogUtil.info(
            Env.SERVER,
            "Raid {} spawned wave {}/{} ({} mobs){}",
            this.raidId,
            waveIndex + 1,
            type.waveCount(),
            this.currentWaveMobs.size(),
            wave.isBossWave() ? " [BOSS]" : ""
         );
      }
   }

   private Mob spawnOne(ServerLevel level, EntityType<?> entityType, RaidWave wave, RandomSource random, LivingEntity focus) {
      Entity created = entityType.create(level);
      if (created instanceof Mob mob) {
         BlockPos pos = this.findSpawnPos(level, random);
         mob.moveTo((double)pos.getX() + 0.5, (double)pos.getY(), (double)pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
         mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
         this.applyScaling(mob, wave);
         mob.getPersistentData().putString("dmz_raid_id", this.raidId.toString());
         mob.setPersistenceRequired();
         if (focus != null) {
            mob.setTarget(focus);
         }

         level.addFreshEntity(mob);
         return mob;
      } else {
         if (created != null) {
            created.discard();
         }

         return null;
      }
   }

   private void applyScaling(Mob mob, RaidWave wave) {
      AttributeInstance maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
      if (maxHealth != null && wave.getHealthMultiplier() != 1.0) {
         maxHealth.setBaseValue(maxHealth.getBaseValue() * wave.getHealthMultiplier());
      }

      AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
      if (attack != null && wave.getDamageMultiplier() != 1.0) {
         attack.setBaseValue(attack.getBaseValue() * wave.getDamageMultiplier());
      }

      mob.setHealth(mob.getMaxHealth());
      if (mob instanceof IBattlePower battlePower && wave.getHealthMultiplier() != 1.0) {
         battlePower.setBattlePower((int)Math.round((double)battlePower.getBattlePower() * wave.getHealthMultiplier()));
      }
   }

   private BlockPos findSpawnPos(ServerLevel level, RandomSource random) {
      for (int attempt = 0; attempt < 12; attempt++) {
         double angle = random.nextDouble() * Math.PI * 2.0;
         double dist = 6.0 + random.nextDouble() * 10.0;
         int x = this.center.getX() + (int)Math.round(Math.cos(angle) * dist);
         int z = this.center.getZ() + (int)Math.round(Math.sin(angle) * dist);
         int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
         BlockPos pos = new BlockPos(x, y, z);
         if (level.noCollision(new AABB(pos))) {
            return pos;
         }
      }

      return this.center.above();
   }

   private List<ServerPlayer> resolveActiveParticipants(ServerLevel level, RaidType type) {
      List<ServerPlayer> active = new ArrayList<>();
      double leashSqr = type.getLeashDistance() * type.getLeashDistance();
      double cx = (double)this.center.getX() + 0.5;
      double cy = (double)this.center.getY() + 0.5;
      double cz = (double)this.center.getZ() + 0.5;
      MinecraftServer server = level.getServer();

      for (UUID id : this.participants) {
         ServerPlayer player = server.getPlayerList().getPlayer(id);
         if (player != null
            && player.level() == level
            && !player.isSpectator()
            && player.isAlive()
            && !player.isDeadOrDying()
            && !(player.distanceToSqr(cx, cy, cz) > leashSqr)) {
            active.add(player);
         }
      }

      return active;
   }

   private LivingEntity nearestParticipant(ServerLevel level, RaidType type) {
      ServerPlayer nearest = null;
      double best = Double.MAX_VALUE;

      for (ServerPlayer player : this.resolveActiveParticipants(level, type)) {
         double d = player.distanceToSqr((double)this.center.getX(), (double)this.center.getY(), (double)this.center.getZ());
         if (d < best) {
            best = d;
            nearest = player;
         }
      }

      return nearest;
   }

   private void ensureBossEvent(RaidType type) {
      if (this.bossEvent == null) {
         this.bossEvent = new ServerBossEvent(type.getDisplayName(), BossBarColor.RED, BossBarOverlay.NOTCHED_10);
         this.bossEvent.setProgress(1.0F);
      }
   }

   private void refreshBossPlayers(List<ServerPlayer> active) {
      if (this.bossEvent != null) {
         Set<UUID> activeIds = new HashSet<>();

         for (ServerPlayer player : active) {
            activeIds.add(player.getUUID());
         }

         for (ServerPlayer shown : new ArrayList(this.bossEvent.getPlayers())) {
            if (!activeIds.contains(shown.getUUID())) {
               this.bossEvent.removePlayer(shown);
            }
         }

         for (ServerPlayer player : active) {
            this.bossEvent.addPlayer(player);
         }
      }
   }

   private void updateBossBar(RaidType type, int aliveMobs) {
      if (this.bossEvent != null) {
         int waveNumber = Math.max(1, this.currentWaveIndex + 1);
         boolean boss = this.currentWaveIndex >= 0 && type.wave(this.currentWaveIndex).isBossWave();
         this.bossEvent.setName(Component.translatable("raid.dragonminez.bossbar", new Object[]{type.getDisplayName(), waveNumber, type.waveCount()}));
         this.bossEvent.setColor(boss ? BossBarColor.PURPLE : BossBarColor.RED);
         if (this.waveDelayTimer > 0) {
            this.bossEvent.setProgress(1.0F);
         } else if (this.totalMobsThisWave > 0) {
            this.bossEvent.setProgress(Math.max(0.0F, Math.min(1.0F, (float)aliveMobs / (float)this.totalMobsThisWave)));
         }
      }
   }

   private void win(ServerLevel level, List<ServerPlayer> winners) {
      this.status = Raid.Status.VICTORY;
      this.clearBossEvent();
      this.discardRemainingMobs(level);
      this.type().getReward().grant(level, winners, this.center);

      for (ServerPlayer player : winners) {
         player.sendSystemMessage(Component.translatable("raid.dragonminez.victory"));
      }

      LogUtil.info(Env.SERVER, "Raid {} completed (victory)", this.raidId);
   }

   private void fail(ServerLevel level) {
      this.status = Raid.Status.DEFEAT;
      this.clearBossEvent();
      this.discardRemainingMobs(level);
      LogUtil.info(Env.SERVER, "Raid {} ended (all participants died or left)", this.raidId);
   }

   public void cancel(ServerLevel level) {
      if (this.status == Raid.Status.ACTIVE) {
         this.status = Raid.Status.DEFEAT;
         this.clearBossEvent();
         this.discardRemainingMobs(level);
         LogUtil.info(Env.SERVER, "Raid {} cancelled", this.raidId);
      }
   }

   private void discardRemainingMobs(ServerLevel level) {
      for (UUID id : this.currentWaveMobs) {
         Entity entity = level.getEntity(id);
         if (entity != null) {
            entity.discard();
         }
      }

      this.currentWaveMobs.clear();
   }

   private void clearBossEvent() {
      if (this.bossEvent != null) {
         this.bossEvent.removeAllPlayers();
         this.bossEvent.setVisible(false);
         this.bossEvent = null;
      }
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("RaidId", this.raidId);
      tag.putString("Type", this.typeId);
      tag.putString("Dimension", this.dimension.location().toString());
      tag.putLong("Center", this.center.asLong());
      tag.putString("Status", this.status.name());
      tag.putInt("WaveIndex", this.currentWaveIndex);
      tag.putInt("WaveDelay", this.waveDelayTimer);
      tag.putInt("TotalMobs", this.totalMobsThisWave);
      ListTag participantsTag = new ListTag();

      for (UUID id : this.participants) {
         CompoundTag entry = new CompoundTag();
         entry.putUUID("Id", id);
         participantsTag.add(entry);
      }

      tag.put("Participants", participantsTag);
      ListTag mobsTag = new ListTag();

      for (UUID id : this.currentWaveMobs) {
         CompoundTag entry = new CompoundTag();
         entry.putUUID("Id", id);
         mobsTag.add(entry);
      }

      tag.put("Mobs", mobsTag);
      return tag;
   }

   public static Raid load(CompoundTag tag) {
      UUID raidId = tag.getUUID("RaidId");
      String typeId = tag.getString("Type");
      ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension")));
      BlockPos center = BlockPos.of(tag.getLong("Center"));
      Set<UUID> participants = new HashSet<>();
      ListTag participantsTag = tag.getList("Participants", 10);

      for (int i = 0; i < participantsTag.size(); i++) {
         participants.add(participantsTag.getCompound(i).getUUID("Id"));
      }

      Raid raid = new Raid(raidId, typeId, dimension, center, participants);
      raid.status = Raid.Status.valueOf(tag.getString("Status"));
      raid.currentWaveIndex = tag.getInt("WaveIndex");
      raid.waveDelayTimer = tag.getInt("WaveDelay");
      raid.totalMobsThisWave = tag.getInt("TotalMobs");
      ListTag mobsTag = tag.getList("Mobs", 10);

      for (int i = 0; i < mobsTag.size(); i++) {
         raid.currentWaveMobs.add(mobsTag.getCompound(i).getUUID("Id"));
      }

      return raid;
   }

   public UUID getRaidId() {
      return this.raidId;
   }

   public ResourceKey<Level> getDimension() {
      return this.dimension;
   }

   public BlockPos getCenter() {
      return this.center;
   }

   public Set<UUID> getParticipants() {
      return this.participants;
   }

   public Raid.Status getStatus() {
      return this.status;
   }

   public static enum Status {
      ACTIVE,
      VICTORY,
      DEFEAT;
   }
}
