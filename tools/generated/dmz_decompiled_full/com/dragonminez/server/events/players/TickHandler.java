package com.dragonminez.server.events.players;

import com.dragonminez.Env;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainEnchants;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.entities.ShadowDummyEntity;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiAreaEntity;
import com.dragonminez.common.init.entities.ki.KiBarrierEntity;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.init.entities.ki.KiDiskEntity;
import com.dragonminez.common.init.entities.ki.KiExplosionEntity;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.dragonminez.common.init.item.PothalaPairItem;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.SummonPlayerShadowDummyC2S;
import com.dragonminez.common.network.S2C.AppearanceSyncS2C;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.network.S2C.TechniqueChargeSyncS2C;
import com.dragonminez.common.network.S2C.TriggerAnimationS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.extras.ActionMode;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.TechniqueData;
import com.dragonminez.common.stats.techniques.TechniqueDispatcher;
import com.dragonminez.common.stats.techniques.Techniques;
import com.dragonminez.common.util.CuriosUtil;
import com.dragonminez.common.util.TransformationItemCostHelper;
import com.dragonminez.common.util.TransformationsHelper;
import com.dragonminez.server.events.players.actionmode.FormModeHandler;
import com.dragonminez.server.events.players.actionmode.FusionModeHandler;
import com.dragonminez.server.events.players.actionmode.RacialModeHandler;
import com.dragonminez.server.events.players.actionmode.StackFormModeHandler;
import com.dragonminez.server.events.players.statuseffect.BioDrainHandler;
import com.dragonminez.server.events.players.statuseffect.CooldownEffectHandler;
import com.dragonminez.server.events.players.statuseffect.FlyStatusHandler;
import com.dragonminez.server.events.players.statuseffect.FusionStatusHandler;
import com.dragonminez.server.events.players.statuseffect.KiChargeStatusHandler;
import com.dragonminez.server.events.players.statuseffect.MajinStatusHandler;
import com.dragonminez.server.events.players.statuseffect.MightFruitStatusHandler;
import com.dragonminez.server.events.players.statuseffect.MutantStatusHandler;
import com.dragonminez.server.events.players.statuseffect.SaiyanPassiveHandler;
import com.dragonminez.server.events.players.statuseffect.TransformStatusHandler;
import com.dragonminez.server.util.FusionLogic;
import com.dragonminez.server.util.GravityLogic;
import com.dragonminez.server.util.GravityStateSync;
import com.dragonminez.server.util.PotionEffectHelper;
import com.dragonminez.server.world.dimension.OtherworldDimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME
)
public class TickHandler {
   private static final Env LOG_ENV = Env.SERVER;
   private static final Map<String, IActionModeHandler> ACTION_MODE_HANDLERS = new HashMap<>();
   private static final List<IStatusEffectHandler> STATUS_EFFECT_HANDLERS = new ArrayList<>();
   private static final Map<UUID, AbstractKiProjectile> CHARGING_CACHE = new HashMap<>();
   private static final Map<UUID, Float> CHARGE_COST_ACCUM = new HashMap<>();
   private static final int REGEN_INTERVAL = 20;
   private static final int SYNC_INTERVAL = 10;
   private static final int FORCED_KILL_GRACE_TICKS = 40;
   private static final int OTHERWORLD_TP_GRACE_TICKS = 10;
   private static final int AURA_LIGHT_INTERVAL = 2;
   private static final int AURA_LIGHT_LEVEL = 12;
   private static final int AURA_LIGHT_STEP = 1;
   public static final double MEDITATION_BONUS_PER_LEVEL = 0.05;
   private static final Map<UUID, Integer> masterySecondsByPlayer = new HashMap<>();
   private static final Map<UUID, Integer> chargeTicksByPlayer = new HashMap<>();
   private static final Map<UUID, Integer> playerTickCounters = new HashMap<>();
   private static final Map<UUID, BlockPos> auraLightPositions = new HashMap<>();
   private static final Map<UUID, Integer> auraLightLevels = new HashMap<>();
   private static final Map<UUID, Integer> forceKillGraceByPlayer = new HashMap<>();
   private static final Map<UUID, Integer> otherworldTpGraceByPlayer = new HashMap<>();
   private static final double SHADOW_DUMMY_TETHER_SQR = 10000.0;

   @SubscribeEvent
   public static void onPlayerTick(Post event) {
      if (!event.getEntity().level().isClientSide) {
         if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            UUID var5 = serverPlayer.getUUID();
            int graceTicks = forceKillGraceByPlayer.getOrDefault(var5, 0);
            if (graceTicks > 0) {
               forceKillGraceByPlayer.put(var5, graceTicks - 1);
            }

            int tickCounter = playerTickCounters.getOrDefault(var5, 0) + 1;
            if (tickCounter >= 20) {
               playerTickCounters.put(var5, 0);
            } else {
               playerTickCounters.put(var5, tickCounter);
            }

            if (shouldForceKillForInvalidHealth(serverPlayer, var5)) {
               serverPlayer.kill();
               forceKillGraceByPlayer.put(var5, 40);
            } else {
               StatsProvider.get(StatsCapability.INSTANCE, serverPlayer)
                  .ifPresent(
                     data -> {
                        if (data.getStatus().isHasCreatedCharacter()) {
                           if (serverPlayer.tickCount % 2 == 0) {
                              updateAuraLight(serverPlayer, data);
                           }

                           boolean isStunned = serverPlayer.hasEffect(MainEffects.STUN) || data.getStatus().isStrikeLocked();
                           if (isStunned) {
                              data.getStatus().setChargingKi(false);
                              data.getStatus().setActionCharging(false);
                              data.getTechniques().clearTechniqueCharge();
                              data.getResources().setActionCharge(0);
                              if (!data.getStatus().isStunEffect()) {
                                 data.getStatus().setStunEffect(true);
                              }
                           } else if (data.getStatus().isStunEffect()) {
                              data.getStatus().setStunEffect(false);
                           }

                           data.getCooldowns().tick();
                           data.getEffects().tick();
                           data.getSecondaryStatEffects().tick();
                           clearExpiredKnockdown(data);

                           for (IStatusEffectHandler handler : STATUS_EFFECT_HANDLERS) {
                              handler.onPlayerTick(serverPlayer, data);
                              handler.handleStatusEffects(serverPlayer, data);
                           }

                           if (tickCounter % 20 == 0) {
                              for (IStatusEffectHandler handler : STATUS_EFFECT_HANDLERS) {
                                 handler.onPlayerSecond(serverPlayer, data);
                              }
                           }

                           boolean flyingSkillActive = data.getSkills().isSkillActive("fly");
                           if (flyingSkillActive
                              && serverPlayer.getDeltaMovement().y < 0.0
                              && !TechniqueDispatcher.isMovementRestrictedKiAttack(serverPlayer, data)) {
                              serverPlayer.setDeltaMovement(serverPlayer.getDeltaMovement().x, 0.0, serverPlayer.getDeltaMovement().z);
                              serverPlayer.resetFallDistance();
                              serverPlayer.hasImpulse = true;
                           }

                           if (!isStunned) {
                              handleTechniqueCharge(serverPlayer, data);
                           }

                           boolean shouldRegen = tickCounter >= 20 && !serverPlayer.isDeadOrDying();
                           boolean shouldSync = tickCounter % 10 == 0;
                           boolean isChargingKi = data.getStatus().isChargingKi();
                           boolean isDescending = data.getStatus().isDescending();
                           int meditationLevel = data.getSkills().getSkillLevel("meditation");
                           double currentRegenMod = 1.0;
                           boolean isGuardBroken = data.getStatus().isStunEffect() || data.getResources().getCurrentPoise() <= 0.0F;
                           boolean isFastFly = data.getSkills().isSkillActive("fly") && serverPlayer.isSprinting();
                           boolean isBlocking = data.getStatus().isBlocking();
                           boolean isAttacking = serverPlayer.swingTime > 0;
                           boolean isStill = serverPlayer.getDeltaMovement().lengthSqr() < 0.001 && serverPlayer.onGround();
                           boolean isWalk = !serverPlayer.isSprinting() && serverPlayer.onGround() && serverPlayer.getDeltaMovement().lengthSqr() >= 0.001;
                           if (isFastFly) {
                              currentRegenMod = 0.0;
                           } else if (isGuardBroken) {
                              currentRegenMod = 4.0;
                           } else if (isBlocking || isAttacking) {
                              currentRegenMod = 0.5;
                           } else if (isStill) {
                              currentRegenMod = 2.0;
                           } else if (isWalk) {
                              currentRegenMod = 1.5;
                           }

                           double savedMod = serverPlayer.getPersistentData().getDouble("dmz_stamina_regen_mod");
                           long modTimestamp = serverPlayer.getPersistentData().getLong("dmz_stamina_mod_time");
                           long now = System.currentTimeMillis();
                           if (currentRegenMod != savedMod) {
                              if (currentRegenMod < savedMod || now - modTimestamp >= 1000L) {
                                 serverPlayer.getPersistentData().putDouble("dmz_stamina_regen_mod", currentRegenMod);
                                 serverPlayer.getPersistentData().putLong("dmz_stamina_mod_time", now);
                              }
                           } else {
                              serverPlayer.getPersistentData().putLong("dmz_stamina_mod_time", now);
                           }

                           if (shouldRegen) {
                              double meditationBonus = meditationLevel > 0 ? 1.0 + (double)meditationLevel * 0.05 : 1.0;
                              boolean activeCharging = isChargingKi && !isDescending;
                              double foodRegenMod = getFoodRegenMultiplier(serverPlayer);
                              regenerateHealth(serverPlayer, data, foodRegenMod);
                              regenerateEnergy(serverPlayer, data, activeCharging, foodRegenMod);
                              regenerateStamina(serverPlayer, data, foodRegenMod);
                              regeneratePoise(data, meditationBonus);
                              playerTickCounters.put(var5, 0);
                           } else {
                              playerTickCounters.put(var5, tickCounter);
                           }

                           boolean isMovementRestricted = TechniqueDispatcher.isMovementRestrictedKiAttack(serverPlayer, data);
                           boolean isFiring = TechniqueDispatcher.isFiringKiAttack(serverPlayer);
                           boolean wasExecuting = serverPlayer.getPersistentData().getBoolean("dmz_was_executing_ki");
                           if (isMovementRestricted) {
                              boolean flying = data.getSkills().isSkillActive("fly");
                              double restrictedY = flying ? 0.0 : Math.min(serverPlayer.getDeltaMovement().y, 0.0);
                              serverPlayer.setDeltaMovement(0.0, restrictedY, 0.0);
                              if (flying) {
                                 serverPlayer.resetFallDistance();
                              }

                              serverPlayer.hasImpulse = true;
                              serverPlayer.setJumping(false);
                              serverPlayer.setSprinting(false);
                              if (serverPlayer.getPose() != Pose.STANDING) {
                                 serverPlayer.setPose(Pose.STANDING);
                              }

                              if (isFiring) {
                                 serverPlayer.setYRot(serverPlayer.yRotO);
                                 serverPlayer.setXRot(serverPlayer.xRotO);
                                 serverPlayer.yHeadRot = serverPlayer.yHeadRotO;
                                 serverPlayer.yBodyRot = serverPlayer.yBodyRotO;
                              }

                              serverPlayer.getPersistentData().putBoolean("dmz_was_executing_ki", true);
                           } else if (wasExecuting) {
                              serverPlayer.getPersistentData().putBoolean("dmz_was_executing_ki", false);
                           }

                           if (data.getStatus().isActionCharging() && data.getStatus().getSelectedAction() == ActionMode.FUSION) {
                              serverPlayer.setDeltaMovement(0.0, Math.min(serverPlayer.getDeltaMovement().y, 0.0), 0.0);
                              serverPlayer.hasImpulse = true;
                              serverPlayer.setJumping(false);
                              serverPlayer.setSprinting(false);
                              serverPlayer.setYRot(serverPlayer.yRotO);
                              serverPlayer.setXRot(serverPlayer.xRotO);
                              serverPlayer.yHeadRot = serverPlayer.yHeadRotO;
                              serverPlayer.yBodyRot = serverPlayer.yBodyRotO;
                           }

                           if (serverPlayer.tickCount % 10 == 0
                              && data.getStatus().getPotaraPoseTimer() == 0
                              && !data.getStatus().isFused()
                              && data.getStatus().getFusionPartnerUUID() == null) {
                              ItemStack head = CuriosUtil.getFirstStack(serverPlayer, "head_tech");
                              Item counterpart = pothalaLeftCounterpart(head.getItem());
                              int pairId = counterpart != null ? PothalaPairItem.getPairId(head) : 0;
                              if (pairId != 0) {
                                 for (ServerPlayer other : serverPlayer.level()
                                    .getEntitiesOfClass(ServerPlayer.class, serverPlayer.getBoundingBox().inflate(20.0), p -> p != serverPlayer)) {
                                    StatsData otherData = StatsProvider.get(StatsCapability.INSTANCE, other).orElse(null);
                                    if (otherData != null
                                       && otherData.getStatus().getPotaraPoseTimer() <= 0
                                       && !otherData.getStatus().isFused()
                                       && otherData.getStatus().getFusionPartnerUUID() == null
                                       && !(serverPlayer.distanceTo(other) > 20.0F)) {
                                       ItemStack otherHead = CuriosUtil.getFirstStack(other, "head_tech");
                                       if (otherHead.getItem() == counterpart && PothalaPairItem.getPairId(otherHead) == pairId) {
                                          startPotaraPose(serverPlayer, data, other, otherData);
                                          break;
                                       }
                                    }
                                 }
                              }
                           }

                           if (data.getStatus().getPotaraPoseTimer() > 0) {
                              UUID potaraPartnerUUID = data.getStatus().getPotaraPartnerUUID();
                              ServerPlayer potaraPartner = potaraPartnerUUID != null
                                 ? serverPlayer.getServer().getPlayerList().getPlayer(potaraPartnerUUID)
                                 : null;
                              if (potaraPartner != null && potaraPartner.isAlive() && !(serverPlayer.distanceTo(potaraPartner) > 24.0F)) {
                                 int elapsed = data.getStatus().getPotaraPoseTimer();
                                 double dx = potaraPartner.getX() - serverPlayer.getX();
                                 double dy = potaraPartner.getY() - serverPlayer.getY();
                                 double dz = potaraPartner.getZ() - serverPlayer.getZ();
                                 double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                                 double speed = Math.min(2.5, 0.02 + (double)(elapsed * elapsed) * 0.0015);
                                 if (dist > 0.05) {
                                    double step = Math.min(speed, dist) / dist;
                                    serverPlayer.setDeltaMovement(dx * step, dy * step, dz * step);
                                    serverPlayer.hurtMarked = true;
                                 } else {
                                    serverPlayer.setDeltaMovement(0.0, 0.0, 0.0);
                                 }

                                 serverPlayer.setJumping(false);
                                 serverPlayer.setSprinting(false);
                                 serverPlayer.fallDistance = 0.0F;
                                 data.getStatus().setPotaraPoseTimer(elapsed + 1);
                                 if (data.getStatus().isPotaraLeader() && (dist < 1.4 || elapsed > 200)) {
                                    clearPotaraPose(data);
                                    StatsData partnerData = StatsProvider.get(StatsCapability.INSTANCE, potaraPartner).orElse(null);
                                    if (partnerData != null) {
                                       clearPotaraPose(partnerData);
                                       FusionLogic.executePothala(serverPlayer, potaraPartner, data, partnerData);
                                    } else {
                                       NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(serverPlayer), serverPlayer);
                                    }
                                 }
                              } else {
                                 clearPotaraPose(data);
                                 NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(serverPlayer), serverPlayer);
                              }
                           }

                           boolean kiAnimShouldBeActive = playerOwnsKiProjectile(serverPlayer)
                              || data.getTechniques().isTechniqueCharging()
                              || data.getTechniques().isTechniqueChargeActive();
                           boolean kiAnimWasActive = serverPlayer.getPersistentData().getBoolean("dmz_ki_anim_active");
                           if (kiAnimShouldBeActive) {
                              serverPlayer.getPersistentData().putBoolean("dmz_ki_anim_active", true);
                           } else if (kiAnimWasActive) {
                              serverPlayer.getPersistentData().putBoolean("dmz_ki_anim_active", false);
                              NetworkHandler.sendToTrackingEntityAndSelf(
                                 new TriggerAnimationS2C(serverPlayer.getUUID(), TriggerAnimationS2C.AnimationType.KI_ANIMATION_STOP, 0, -1, ""), serverPlayer
                              );
                           }

                           if (data.getStatus().isActionCharging() && !canChargeSelectedAction(serverPlayer, data)) {
                              data.getStatus().setActionCharging(false);
                              if (data.getResources().getActionCharge() > 0) {
                                 data.getResources().setActionCharge(0);
                              }
                           }

                           boolean isReleaseCharging = isChargingKi || data.getStatus().isActionCharging();
                           if (isReleaseCharging) {
                              int chargeTicks = chargeTicksByPlayer.getOrDefault(var5, 0) + 1;
                              chargeTicksByPlayer.put(var5, chargeTicks);
                              if (chargeTicks % 2 == 0) {
                                 chargePowerRelease(data, chargeTicks, isChargingKi && isDescending);
                              }
                           } else if (chargeTicksByPlayer.containsKey(var5)) {
                              chargeTicksByPlayer.remove(var5);
                           }

                           boolean auraFromActions = isChargingKi
                              || data.getStatus().isActionCharging()
                                 && (data.getStatus().getSelectedAction() == ActionMode.FORM || data.getStatus().getSelectedAction() == ActionMode.STACK);
                           boolean auraFromFlySprint = data.getSkills().isSkillActive("fly")
                              && serverPlayer.isSprinting()
                              && serverPlayer.getDeltaMovement().length() > 0.65F;
                           data.getStatus().setAuraActive(auraFromActions || auraFromFlySprint);
                           if (tickCounter % 5 == 0) {
                              boolean hasYajirobe = serverPlayer.getInventory().hasAnyOf(Set.of((Item)MainItems.KATANA_YAJIROBE.get()));
                              boolean holdingYajirobe = serverPlayer.getMainHandItem().getItem() == MainItems.KATANA_YAJIROBE.get()
                                 || serverPlayer.getOffhandItem().getItem() == MainItems.KATANA_YAJIROBE.get();
                              boolean renderKatanaTarget = hasYajirobe && !holdingYajirobe;
                              boolean playedSound = false;
                              if (data.getStatus().isRenderKatana() != renderKatanaTarget) {
                                 if (renderKatanaTarget) {
                                    serverPlayer.level()
                                       .playSound(null, serverPlayer.blockPosition(), (SoundEvent)MainSounds.SWORD_IN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                                    playedSound = true;
                                 } else if (holdingYajirobe) {
                                    serverPlayer.level()
                                       .playSound(null, serverPlayer.blockPosition(), (SoundEvent)MainSounds.SWORD_OUT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                                    playedSound = true;
                                 }

                                 data.getStatus().setRenderKatana(renderKatanaTarget);
                              }

                              ItemStack heldWeapon = ItemStack.EMPTY;
                              ItemStack stowedWeapon = ItemStack.EMPTY;

                              for (int i = 0; i < serverPlayer.getInventory().getContainerSize(); i++) {
                                 ItemStack stack = serverPlayer.getInventory().getItem(i);
                                 if (!stack.isEmpty()) {
                                    Item item = stack.getItem();
                                    if (item == MainItems.Z_SWORD.get() || item == MainItems.BRAVE_SWORD.get() || item == MainItems.POWER_POLE.get()) {
                                       boolean isHeld = serverPlayer.getMainHandItem().getItem() == item || serverPlayer.getOffhandItem().getItem() == item;
                                       if (isHeld) {
                                          if (heldWeapon == ItemStack.EMPTY) {
                                             heldWeapon = item.getDefaultInstance();
                                          }
                                       } else if (stowedWeapon == ItemStack.EMPTY) {
                                          stowedWeapon = item.getDefaultInstance();
                                       }
                                    }
                                 }
                              }

                              boolean heldHasSheath = heldWeapon != ItemStack.EMPTY && heldWeapon.getItem() != MainItems.Z_SWORD.get();
                              ItemStack backItem;
                              if (heldHasSheath) {
                                 backItem = heldWeapon;
                              } else if (stowedWeapon != ItemStack.EMPTY) {
                                 backItem = stowedWeapon;
                              } else {
                                 backItem = heldWeapon;
                              }

                              boolean weaponDrawn = heldWeapon != ItemStack.EMPTY;
                              String newBackWeapon = backItem != ItemStack.EMPTY ? backItem.getDescriptionId() : "";
                              String currentBackWeapon = data.getStatus().getBackWeapon();
                              boolean wasDrawn = serverPlayer.getPersistentData().getBoolean("dmz_weapon_drawn");
                              if (!playedSound) {
                                 if (weaponDrawn && !wasDrawn) {
                                    serverPlayer.level()
                                       .playSound(null, serverPlayer.blockPosition(), (SoundEvent)MainSounds.SWORD_OUT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                                 } else if (!weaponDrawn && wasDrawn && !newBackWeapon.isEmpty()) {
                                    serverPlayer.level()
                                       .playSound(null, serverPlayer.blockPosition(), (SoundEvent)MainSounds.SWORD_IN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                                 } else if (currentBackWeapon.isEmpty() && !newBackWeapon.isEmpty() && !weaponDrawn) {
                                    serverPlayer.level()
                                       .playSound(null, serverPlayer.blockPosition(), (SoundEvent)MainSounds.SWORD_IN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                                 }
                              }

                              if (weaponDrawn != wasDrawn) {
                                 serverPlayer.getPersistentData().putBoolean("dmz_weapon_drawn", weaponDrawn);
                              }

                              if (!currentBackWeapon.equals(newBackWeapon)) {
                                 data.getStatus().setBackWeapon(newBackWeapon);
                              }

                              ItemStack headTechStack = CuriosUtil.getFirstStack(serverPlayer, "head_tech");
                              String itemId = headTechStack.getDescriptionId();
                              boolean hasScouter = itemId.contains("scouter");
                              if (hasScouter) {
                                 if (!data.getStatus().getScouterItem().equals(itemId)) {
                                    data.getStatus().setScouterItem(itemId);
                                 }
                              } else if (!data.getStatus().getScouterItem().isEmpty()) {
                                 data.getStatus().setScouterItem("");
                              }

                              boolean hasPothala = itemId.contains("pothala");
                              if (hasPothala) {
                                 boolean isGreenPothala = itemId.contains("green");
                                 data.getStatus().setPothalaColor(isGreenPothala ? "green" : "yellow");
                              } else if (!data.getStatus().getPothalaColor().isEmpty()) {
                                 data.getStatus().setPothalaColor("");
                              }
                           }

                           handleOtherworldTransfer(serverPlayer, data, var5);
                           if (tickCounter % 20 == 0) {
                              handleActionCharge(serverPlayer, data);
                              handleActiveFormDrains(serverPlayer, data);
                              enforceShadowDummyTether(serverPlayer, data);
                              GravityLogic.tick(serverPlayer);
                              GravityStateSync.sync(serverPlayer);
                              if (data.getStatus().isAndroidUpgraded()
                                 && (data.getCharacter().getActiveForm().isEmpty() || data.getCharacter().getActiveForm() == null)) {
                                 data.getCharacter().setActiveForm("androidforms", "androidbase");
                                 serverPlayer.refreshDimensions();
                              }
                           }

                           if (shouldSync) {
                              NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(serverPlayer), serverPlayer);
                           }
                        }
                     }
                  );
            }
         }
      }
   }

   private static void handleOtherworldTransfer(ServerPlayer serverPlayer, StatsData data, UUID playerId) {
      boolean shouldTransfer = ConfigManager.getServerConfig().getWorldGen().getOtherworldActive()
         && !data.getStatus().isAlive()
         && !serverPlayer.isSpectator()
         && !serverPlayer.isCreative()
         && !serverPlayer.serverLevel().dimension().equals(OtherworldDimension.OTHERWORLD_KEY);
      if (!shouldTransfer) {
         otherworldTpGraceByPlayer.remove(playerId);
      } else {
         int grace = otherworldTpGraceByPlayer.getOrDefault(playerId, 0) + 1;
         if (grace < 10) {
            otherworldTpGraceByPlayer.put(playerId, grace);
         } else {
            otherworldTpGraceByPlayer.remove(playerId);
            ServerLevel otherworld = serverPlayer.getServer().getLevel(OtherworldDimension.OTHERWORLD_KEY);
            if (otherworld != null) {
               serverPlayer.teleportTo(otherworld, 0.0, 41.0, 10.0, 0.0F, 0.0F);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer serverPlayer) {
         serverPlayer.getPersistentData().putBoolean("dmz_was_executing_ki", false);
         CHARGING_CACHE.remove(serverPlayer.getUUID());
         otherworldTpGraceByPlayer.remove(serverPlayer.getUUID());
         StatsProvider.get(StatsCapability.INSTANCE, serverPlayer).ifPresent(data -> {
            data.getStatus().setChargingKi(false);
            data.getStatus().setActionCharging(false);
            data.getResources().setActionCharge(0);
            SummonPlayerShadowDummyC2S.clearPlayerShadowDummy(serverPlayer, data);
            data.getTechniques().clearTechniqueCharge();
            data.getTechniques().setTechniqueChargePercent(0.0F);
            NetworkHandler.sendToTrackingEntityAndSelf(new TechniqueChargeSyncS2C(serverPlayer.getId(), 0.0F, false), serverPlayer);
         });
         NetworkHandler.sendToTrackingEntityAndSelf(
            new TriggerAnimationS2C(serverPlayer.getUUID(), TriggerAnimationS2C.AnimationType.KI_ANIMATION_STOP, 0, -1, ""), serverPlayer
         );
      }
   }

   @SubscribeEvent
   public static void onPlayerLogout(PlayerLoggedOutEvent event) {
      UUID playerId = event.getEntity().getUUID();
      if (event.getEntity() instanceof ServerPlayer serverPlayer) {
         serverPlayer.getPersistentData().putBoolean("dmz_was_executing_ki", false);
         NetworkHandler.sendToTrackingEntityAndSelf(
            new TriggerAnimationS2C(serverPlayer.getUUID(), TriggerAnimationS2C.AnimationType.KI_ANIMATION_STOP, 0, -1, ""), serverPlayer
         );
      }

      CHARGING_CACHE.remove(playerId);
      CHARGE_COST_ACCUM.remove(playerId);
      chargeTicksByPlayer.remove(playerId);
      masterySecondsByPlayer.remove(playerId);
      playerTickCounters.remove(playerId);
      forceKillGraceByPlayer.remove(playerId);
      auraLightLevels.remove(playerId);
      GravityLogic.clearNpcGravityCache(playerId);
      GravityStateSync.clear(playerId);
      if (event.getEntity() instanceof ServerPlayer serverPlayer) {
         removeAuraLight(serverPlayer.serverLevel(), playerId);
         clearHumanKiAccumulators(serverPlayer);
      }
   }

   @SubscribeEvent
   public static void onPlayerRespawn(PlayerRespawnEvent event) {
      UUID playerId = event.getEntity().getUUID();
      forceKillGraceByPlayer.put(playerId, 40);
      playerTickCounters.remove(playerId);
   }

   private static void enforceShadowDummyTether(ServerPlayer player, StatsData data) {
      if (data.getStatus().hasActiveShadowDummy()) {
         if (player.serverLevel().getEntity(data.getStatus().getActiveShadowDummyUUID()) instanceof ShadowDummyEntity shadow) {
            if (player.distanceToSqr(shadow) > 10000.0) {
               SummonPlayerShadowDummyC2S.dismissByDummy(shadow);
            }
         } else {
            SummonPlayerShadowDummyC2S.clearPlayerShadowDummy(player, data);
         }
      }
   }

   private static void clearExpiredKnockdown(StatsData data) {
      if (data.getStatus().isKnockedDown() && !data.getCooldowns().hasCooldown("KnockdownDuration")) {
         data.getStatus().setKnockedDown(false);
      }
   }

   private static boolean shouldForceKillForInvalidHealth(ServerPlayer serverPlayer, UUID playerId) {
      if (serverPlayer.isAlive() && !serverPlayer.isDeadOrDying() && serverPlayer.deathTime <= 0) {
         float health = serverPlayer.getHealth();
         if (!Float.isNaN(health) && !Float.isInfinite(health)) {
            return forceKillGraceByPlayer.getOrDefault(playerId, 0) > 0 ? false : health <= 0.0F;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public static void registerForceKillGrace(UUID playerId) {
      forceKillGraceByPlayer.put(playerId, 40);
   }

   private static void updateAuraLight(ServerPlayer player, StatsData data) {
      boolean auraActive = data.getStatus().isAuraActive() || data.getStatus().isPermanentAura();
      ServerLevel level = player.serverLevel();
      UUID playerId = player.getUUID();
      int currentLevel = auraLightLevels.getOrDefault(playerId, 0);
      int targetLevel = auraActive ? 12 : 0;
      int nextLevel = approach(currentLevel, targetLevel, 1);
      if (nextLevel <= 0) {
         auraLightLevels.remove(playerId);
         removeAuraLight(level, playerId);
      } else {
         auraLightLevels.put(playerId, nextLevel);
         BlockPos targetPos = player.blockPosition().above();
         if (!canHostAuraLight(level, targetPos)) {
            targetPos = player.blockPosition();
            if (!canHostAuraLight(level, targetPos)) {
               removeAuraLight(level, playerId);
               return;
            }
         }

         BlockPos previousPos = auraLightPositions.get(playerId);
         if (previousPos != null && !previousPos.equals(targetPos)) {
            clearAuraLightIfOwned(level, previousPos);
         }

         BlockState currentState = level.getBlockState(targetPos);
         if (!isAuraLight(currentState) || (Integer)currentState.getValue(LightBlock.LEVEL) != nextLevel) {
            BlockState lightState = (BlockState)Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, nextLevel);
            level.setBlock(targetPos, lightState, 3);
         }

         auraLightPositions.put(playerId, targetPos.immutable());
      }
   }

   private static boolean canHostAuraLight(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.isAir() || isAuraLight(state);
   }

   private static boolean isAuraLight(BlockState state) {
      return state.is(Blocks.LIGHT);
   }

   private static void removeAuraLight(ServerLevel level, UUID playerId) {
      BlockPos previousPos = auraLightPositions.remove(playerId);
      if (previousPos != null) {
         clearAuraLightIfOwned(level, previousPos);
      }
   }

   private static void clearAuraLightIfOwned(ServerLevel level, BlockPos pos) {
      BlockState currentState = level.getBlockState(pos);
      if (isAuraLight(currentState) && (Integer)currentState.getValue(LightBlock.LEVEL) <= 12) {
         level.removeBlock(pos, false);
      }
   }

   private static int approach(int current, int target, int step) {
      if (current < target) {
         return Math.min(target, current + step);
      } else {
         return current > target ? Math.max(target, current - step) : current;
      }
   }

   private static double getFoodRegenMultiplier(ServerPlayer player) {
      int drumsticks = player.getFoodData().getFoodLevel() / 2;
      if (drumsticks >= 9) {
         return 1.0;
      } else {
         return drumsticks <= 3 ? 0.0 : 1.0 - (double)(9 - drumsticks) * 0.1;
      }
   }

   private static void regenerateHealth(ServerPlayer player, StatsData data, double foodRegenMod) {
      float currentHealth = player.getHealth();
      if (!Float.isFinite(currentHealth)) {
         player.setHealth(1.0F);
      } else if (!(foodRegenMod <= 0.0)) {
         float maxHealth = player.getMaxHealth();
         if (!(currentHealth >= maxHealth)) {
            DMZEvent.HealthRegenEvent event = new DMZEvent.HealthRegenEvent(player, data, data.getHealthRegenPerSecond());
            if (!((DMZEvent.HealthRegenEvent)NeoForge.EVENT_BUS.post(event)).isCanceled()) {
               double finalRegen = Math.max(0.0, event.getAmount()) * foodRegenMod;
               if (Double.isFinite(finalRegen) && !(finalRegen <= 0.0)) {
                  player.setHealth((float)Math.min((double)maxHealth, (double)currentHealth + finalRegen));
               }
            }
         }
      }
   }

   private static void regenerateEnergy(ServerPlayer player, StatsData data, boolean activeCharging, double foodRegenMod) {
      float currentEnergy = data.getResources().getCurrentEnergy();
      float maxEnergy = data.getMaxEnergy();
      boolean hasActiveForm = data.getCharacter().hasActiveForm();
      FormConfig.FormData activeForm = hasActiveForm ? data.getCharacter().getActiveFormData() : null;
      boolean hasActiveStackForm = data.getCharacter().hasActiveStackForm();
      FormConfig.FormData activeStackForm = hasActiveStackForm ? data.getCharacter().getActiveStackFormData() : null;
      double energyChange = data.getEnergyRegenPerSecond(activeCharging);
      if (activeCharging) {
         DMZEvent.KiChargeEvent kiEvent = new DMZEvent.KiChargeEvent(player, currentEnergy, maxEnergy);
         if (((DMZEvent.KiChargeEvent)NeoForge.EVENT_BUS.post(kiEvent)).isCanceled()) {
            energyChange = 0.0;
         }
      }

      UUID masteryPlayerId = player.getUUID();
      int masterySeconds = masterySecondsByPlayer.getOrDefault(masteryPlayerId, 0);
      if (masterySeconds < 5) {
         masterySecondsByPlayer.put(masteryPlayerId, masterySeconds + 1);
      } else {
         masterySecondsByPlayer.put(masteryPlayerId, 0);
         if (hasActiveForm && activeForm != null) {
            String activeFormName = activeForm.getName().toLowerCase();
            String activeFormGroup = data.getCharacter().getActiveFormGroup();
            double maxMastery = 100.0;
            FormConfig.FormData formData = ConfigManager.getForm(data.getCharacter().getRaceName(), activeFormGroup, activeFormName);
            if (formData != null) {
               maxMastery = formData.getMaxMastery();
            }

            if (!data.getCharacter().getFormMasteries().hasMaxMastery(activeFormGroup, activeFormName, maxMastery)) {
               double masteryGain = formData != null ? formData.getPassiveMasteryEveryFiveSeconds() : 0.001;
               masteryGain = PotionEffectHelper.applyMasteryGainMultiplier(player, masteryGain);
               data.getCharacter().gainMastery(activeFormGroup, activeFormName, masteryGain);
               NetworkHandler.sendToTrackingEntityAndSelf(new AppearanceSyncS2C(player), player);
            }
         }

         if (hasActiveStackForm && activeStackForm != null) {
            String activeFormNamex = activeStackForm.getName().toLowerCase();
            String activeFormGroupx = data.getCharacter().getActiveStackFormGroup();
            double maxMasteryx = 100.0;
            FormConfig.FormData formDatax = ConfigManager.getStackForm(activeFormGroupx, activeFormNamex);
            if (formDatax != null) {
               maxMasteryx = formDatax.getMaxMastery();
            }

            if (!data.getCharacter().getStackFormMasteries().hasMaxMastery(activeFormGroupx, activeFormNamex, maxMasteryx)) {
               double masteryGain = formDatax != null ? formDatax.getPassiveMasteryEveryFiveSeconds() : 0.001;
               masteryGain = PotionEffectHelper.applyMasteryGainMultiplier(player, masteryGain);
               data.getCharacter().gainMastery(activeFormGroupx, activeFormNamex, masteryGain);
               NetworkHandler.sendToTrackingEntityAndSelf(new AppearanceSyncS2C(player), player);
            }
         }
      }

      if (energyChange > 0.0) {
         energyChange *= foodRegenMod;
      }

      if (energyChange != 0.0) {
         DMZEvent.EnergyRegenEvent regenEvent = new DMZEvent.EnergyRegenEvent(player, data, energyChange);
         energyChange = ((DMZEvent.EnergyRegenEvent)NeoForge.EVENT_BUS.post(regenEvent)).isCanceled() ? 0.0 : regenEvent.getAmount();
      }

      if (energyChange != 0.0) {
         float effectiveMaxEnergy = maxEnergy;
         if (data.getStatus().hasActiveShadowDummy()) {
            int pct = data.getStatus().getShadowDummyPercent();
            effectiveMaxEnergy = maxEnergy * (1.0F - (float)pct / 100.0F);
         }

         double newEnergy = Math.max(0.0, Math.min((double)effectiveMaxEnergy, (double)currentEnergy + Math.ceil(energyChange)));
         data.getResources().setCurrentEnergy((float)newEnergy);
         if (newEnergy <= (double)maxEnergy * 0.05 && !data.getStatus().isAndroidUpgraded() && (hasActiveForm || hasActiveStackForm)) {
            data.getCharacter().clearActiveForm(player, false);
            data.getCharacter().clearActiveStackForm(player, false);
            data.getResources().setPowerRelease(0);
            data.getResources().setActionCharge(0);
            player.refreshDimensions();
            player.level()
               .playSound(null, player.getX(), player.getY(), player.getZ(), (SoundEvent)MainSounds.NO_KI_FORM.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            player.sendSystemMessage(Component.translatable("message.dragonminez.form.drained_ki"), true);
         }
      }
   }

   private static void regenerateStamina(ServerPlayer player, StatsData data, double foodRegenMod) {
      if (!(foodRegenMod <= 0.0)) {
         if (!data.getCooldowns().hasCooldown("StaminaPause")) {
            float currentStamina = data.getResources().getCurrentStamina();
            float maxStamina = data.getMaxStamina();
            if (!(currentStamina >= maxStamina)) {
               double regenPerSecond = data.getStaminaRegenPerSecond();
               if (!(regenPerSecond <= 0.0)) {
                  DMZEvent.StaminaRegenEvent event = new DMZEvent.StaminaRegenEvent(player, data, regenPerSecond);
                  if (!((DMZEvent.StaminaRegenEvent)NeoForge.EVENT_BUS.post(event)).isCanceled()) {
                     regenPerSecond = Math.max(0.0, event.getAmount()) * foodRegenMod;
                     if (!(regenPerSecond <= 0.0)) {
                        float effectiveMaxStamina = maxStamina;
                        if (data.getStatus().hasActiveShadowDummy()) {
                           int pct = data.getStatus().getShadowDummyPercent();
                           effectiveMaxStamina = maxStamina * (1.0F - (float)pct / 100.0F);
                        }

                        double newStamina = Math.min((double)effectiveMaxStamina, (double)currentStamina + Math.ceil(regenPerSecond));
                        data.getResources().setCurrentStamina((float)newStamina);
                     }
                  }
               }
            }
         }
      }
   }

   private static void regeneratePoise(StatsData data, double meditationBonus) {
      if (!data.getCooldowns().hasCooldown("PoiseCooldown") && !data.getStatus().isBlocking() && !data.getStatus().isStunEffect()) {
         float currentPoise = data.getResources().getCurrentPoise();
         float maxPoise = data.getMaxPoise();
         if (currentPoise < maxPoise) {
            double baseRegen = 0.1;
            int totalEnchLvl = getTotalArmorEnchantmentLevel(MainEnchants.RESISTANCE_RECOVERY, data.getPlayer());
            double enchMult = getRecoveryMultiplier(totalEnchLvl);
            double regenAmount = (double)maxPoise * baseRegen * meditationBonus * enchMult;
            if (regenAmount < 1.0) {
               regenAmount = 1.0;
            }

            data.getResources().addPoise((float)regenAmount);
         }
      }
   }

   public static int getTotalArmorEnchantmentLevel(ResourceKey<Enchantment> enchantment, LivingEntity entity) {
      int totalLevel = 0;

      for (ItemStack stack : entity.getArmorSlots()) {
         totalLevel += MainEnchants.level(stack, entity.level(), enchantment);
      }

      return totalLevel;
   }

   public static double getRecoveryMultiplier(int totalLevel) {
      double bonus = 0.0;
      if (totalLevel > 0) {
         bonus += (double)Math.min(totalLevel, 4) * 0.0625;
      }

      if (totalLevel > 4) {
         bonus += (double)Math.min(totalLevel - 4, 4) * 0.03125;
      }

      if (totalLevel > 8) {
         bonus += (double)Math.min(totalLevel - 8, 4) * 0.015625;
      }

      if (totalLevel > 12) {
         bonus += (double)Math.min(totalLevel - 12, 4) * 0.0078125;
      }

      return 1.0 + bonus;
   }

   private static void handleActionCharge(ServerPlayer player, StatsData data) {
      if (!data.getStatus().isActionCharging()) {
         if (data.getResources().getActionCharge() > 0) {
            data.getResources().setActionCharge(0);
         }
      } else {
         ActionMode mode = data.getStatus().getSelectedAction();
         int currentRelease = data.getResources().getActionCharge();
         int increment = 0;
         boolean execute = false;
         IActionModeHandler handler = ACTION_MODE_HANDLERS.get(mode.name());
         if (handler != null) {
            increment += handler.handleActionCharge(player, data);
         }

         if (increment > 0) {
            if (mode != ActionMode.FUSION || currentRelease < 100) {
               currentRelease += increment;
            }

            if (currentRelease >= 100) {
               currentRelease = 100;
               execute = true;
            }

            data.getResources().setActionCharge(currentRelease);
         }

         if (execute) {
            boolean success = performAction(player, data, mode);
            if (success) {
               data.getResources().setActionCharge(0);
            }
         }
      }
   }

   private static void chargePowerRelease(StatsData data, int chargeTicks, boolean descending) {
      int currentRelease = data.getResources().getPowerRelease();
      int potentialUnlockLevel = data.getSkills().getSkillLevel("potentialunlock");
      int maxRelease = 50 + potentialUnlockLevel * 5;
      int releaseLimit = data.getResources().getReleaseLimit();
      if (releaseLimit > 0) {
         maxRelease = Math.min(maxRelease, releaseLimit);
      }

      int effectiveLevel = Math.min(10, potentialUnlockLevel);
      float temporalMultiplier = (float)Math.pow((double)((float)chargeTicks / 50.0F), 2.0);
      float levelMultiplier = 1.0F + (float)effectiveLevel * 0.1F;
      int step = (int)Math.min(10.0F, Math.max(1.0F, temporalMultiplier * levelMultiplier));
      if (!descending && currentRelease < maxRelease) {
         data.getResources().setPowerRelease(Math.min(maxRelease, currentRelease + step));
      } else if (descending && currentRelease > 0) {
         data.getResources().setPowerRelease(Math.max(0, currentRelease - step));
      }
   }

   private static void handleTechniqueCharge(ServerPlayer player, StatsData data) {
      Techniques techniques = data.getTechniques();
      String chargingTechniqueId = techniques.getChargingTechniqueId();
      if (chargingTechniqueId != null && !chargingTechniqueId.isEmpty()) {
         TechniqueData techniqueData = techniques.getUnlockedTechniques().get(chargingTechniqueId);
         if (!(techniqueData instanceof KiAttackData kiAttack)) {
            techniques.clearTechniqueCharge();
         } else {
            String cooldownKey = getTechniqueCooldownKey(chargingTechniqueId);
            if (data.getCooldowns().hasCooldown(cooldownKey)) {
               techniques.clearTechniqueCharge();
            } else if (techniques.isTechniqueCharging()) {
               if (kiAttack.getKiType() == KiAttackData.KiType.SHIELD
                  && kiAttack.getEffectiveUtility() == KiAttackData.Utility.HEAL
                  && techniques.getHomingTargetId() >= 0) {
                  boolean var10000;
                  label119: {
                     if (player.serverLevel().getEntity(techniques.getHomingTargetId()) instanceof LivingEntity living
                        && living.isAlive()
                        && !((double)player.distanceTo(living) > 30.0)) {
                        var10000 = false;
                        break label119;
                     }

                     var10000 = true;
                  }

                  boolean targetGone = var10000;
                  if (targetGone) {
                     AbstractKiProjectile leftover = findChargingEntity(player);
                     if (leftover != null) {
                        leftover.discard();
                     }

                     CHARGING_CACHE.remove(player.getUUID());
                     CHARGE_COST_ACCUM.remove(player.getUUID());
                     techniques.clearTechniqueCharge();
                     return;
                  }
               }

               AbstractKiProjectile activeKi = findChargingEntity(player);
               if (activeKi != null && !chargingTechniqueId.equals(activeKi.getTechniqueId())) {
                  activeKi.discard();
                  CHARGING_CACHE.remove(player.getUUID());
                  activeKi = null;
               }

               if (activeKi == null && techniques.getTechniqueChargePercent() == 0.0F) {
                  TechniqueDispatcher.executeKiAttack(player, player.level(), kiAttack, data, 0.01F);
               }

               float OVER = 175.0F;
               boolean instant = kiAttack.isInstantCast();
               int baseTicks = Math.max(1, kiAttack.getBaseChargeTicks());
               float percent = techniques.getTechniqueChargePercent();
               boolean holding = techniques.isChargeHolding();
               float base = (float)kiAttack.getCalculatedCost(data);
               boolean creative = player.isCreative();
               float ceiling = holding && !instant ? 175.0F : 100.0F;
               boolean outOfKi = false;
               if (percent < ceiling - 0.01F) {
                  float rate = percent < 100.0F ? 100.0F / (float)baseTicks : 25.0F / (float)baseTicks;
                  float newP = Math.min(ceiling, percent + rate);
                  if (creative) {
                     percent = newP;
                  } else {
                     double chargeCost = 0.5 * (double)base * (double)(KiAttackData.costMultiplier(newP) - KiAttackData.costMultiplier(percent));
                     float accum = CHARGE_COST_ACCUM.getOrDefault(player.getUUID(), 0.0F) + (float)chargeCost;
                     float energy = data.getResources().getCurrentEnergy();
                     int whole = (int)accum;
                     int effectiveWhole = (int)Math.round((double)whole * data.getKiAttackCostModifier());
                     if (energy >= (float)effectiveWhole) {
                        if (effectiveWhole > 0) {
                           data.getResources().removeEnergy((float)effectiveWhole);
                           applyHumanKiPassiveDuringCharge(player, data, whole);
                        }

                        accum -= (float)whole;
                        CHARGE_COST_ACCUM.put(player.getUUID(), accum);
                        percent = newP;
                     } else {
                        float affordFrac = effectiveWhole > 0 ? Math.max(0.0F, Math.min(1.0F, energy / (float)effectiveWhole)) : 1.0F;
                        percent += (newP - percent) * affordFrac;
                        data.getResources().setCurrentEnergy(0.0F);
                        CHARGE_COST_ACCUM.put(player.getUUID(), 0.0F);
                        applyHumanKiPassiveDuringCharge(player, data, Math.round((float)whole * affordFrac));
                        outOfKi = true;
                     }
                  }

                  techniques.setTechniqueChargePercent(percent);
               }

               boolean reachedCeiling = percent >= ceiling - 0.01F;
               if (holding && !instant || !reachedCeiling && !outOfKi) {
                  NetworkHandler.sendToTrackingEntityAndSelf(new TechniqueChargeSyncS2C(player.getId(), techniques.getTechniqueChargePercent(), true), player);
               } else {
                  resolveKiAttackOnRelease(player, data, techniques);
               }
            }
         }
      } else {
         boolean hadCharge = techniques.getTechniqueChargePercent() > 0.0F || techniques.isTechniqueCharging();
         if (hadCharge || CHARGING_CACHE.containsKey(player.getUUID())) {
            AbstractKiProjectile leftover = findChargingEntity(player);
            if (leftover != null) {
               leftover.discard();
            }

            CHARGING_CACHE.remove(player.getUUID());
            CHARGE_COST_ACCUM.remove(player.getUUID());
            clearHumanKiAccumulators(player);
         }

         if (hadCharge) {
            techniques.clearTechniqueCharge();
         }
      }
   }

   private static void resolveKiAttackOnRelease(ServerPlayer player, StatsData data, Techniques techniques) {
      float effectiveCharge = Math.min(techniques.getTechniqueChargePercent(), 175.0F);
      AbstractKiProjectile activeKi = findChargingEntity(player);
      if (activeKi != null) {
         if (effectiveCharge < 50.0F) {
            activeKi.discard();
         } else {
            float chargeMultiplier = effectiveCharge / 100.0F;
            TechniqueData techniqueData = techniques.getUnlockedTechniques().get(techniques.getChargingTechniqueId());
            if (techniqueData instanceof KiAttackData kiAttack) {
               boolean fired = TechniqueDispatcher.executeKiAttack(player, player.level(), kiAttack, data, chargeMultiplier);
               if (fired) {
                  boolean creative = player.isCreative();
                  int baseCooldown = creative ? 60 : Math.max(1, (int)Math.ceil((double)((float)kiAttack.getActualCooldown() * chargeMultiplier)));
                  DMZEvent.KiAttackFireEvent fireEvent = new DMZEvent.KiAttackFireEvent(player, data, kiAttack, chargeMultiplier, baseCooldown);
                  NeoForge.EVENT_BUS.post(fireEvent);
                  int cooldownTicks = Math.max(1, fireEvent.getCooldownTicks());
                  data.getCooldowns().setCooldown(getTechniqueCooldownKey(kiAttack.getId()), cooldownTicks);
                  if (!creative) {
                     double base = kiAttack.getCalculatedCost(data);
                     float costMult = KiAttackData.costMultiplier(effectiveCharge);
                     boolean drainsOverLife = activeKi.isMovementRestrictedType();
                     double fireFraction = drainsOverLife ? 0.25 : 0.5;
                     int originalFireCost = (int)Math.round(fireFraction * base * (double)costMult);
                     int modifiedFireCost = (int)Math.round((double)originalFireCost * data.getKiAttackCostModifier());
                     if (modifiedFireCost > 0) {
                        data.getResources().removeEnergy((float)modifiedFireCost);
                     }

                     applyHumanKiPassiveDuringCharge(player, data, originalFireCost);
                     player.getPersistentData().putFloat("dmz_human_hp_drain_accum", 0.0F);
                     if (drainsOverLife) {
                        double lifeCost = 0.25 * base * (double)costMult;
                        int maxLife = Math.max(1, activeKi.getMaxLife());
                        activeKi.setKiLifetimeDrainPerTick((float)(lifeCost / (double)maxLife));
                     }
                  }
               } else {
                  activeKi.discard();
               }
            } else {
               activeKi.discard();
            }
         }
      } else {
         TechniqueData techniqueData = techniques.getUnlockedTechniques().get(techniques.getChargingTechniqueId());
         if (techniqueData instanceof KiAttackData kiAttackx && kiAttackx.isInstantCast() && effectiveCharge >= 50.0F) {
            boolean creative = player.isCreative();
            int baseCooldown = creative ? 60 : Math.max(1, kiAttackx.getActualCooldown());
            DMZEvent.KiAttackFireEvent fireEvent = new DMZEvent.KiAttackFireEvent(player, data, kiAttackx, 1.0F, baseCooldown);
            NeoForge.EVENT_BUS.post(fireEvent);
            int cooldownTicks = Math.max(1, fireEvent.getCooldownTicks());
            data.getCooldowns().setCooldown(getTechniqueCooldownKey(kiAttackx.getId()), cooldownTicks);
         }
      }

      CHARGE_COST_ACCUM.remove(player.getUUID());
      CHARGING_CACHE.remove(player.getUUID());
      techniques.clearTechniqueCharge();
   }

   private static AbstractKiProjectile findChargingEntity(ServerPlayer player) {
      UUID playerId = player.getUUID();
      AbstractKiProjectile cached = CHARGING_CACHE.get(playerId);
      if (cached != null && !cached.isRemoved() && !isEntityFiring(cached)) {
         return cached;
      } else {
         for (AbstractKiProjectile ki : player.level().getEntitiesOfClass(AbstractKiProjectile.class, player.getBoundingBox().inflate(30.0))) {
            if (ki.getOwner() != null && ki.getOwner().getUUID().equals(playerId) && !isEntityFiring(ki)) {
               CHARGING_CACHE.put(playerId, ki);
               return ki;
            }
         }

         CHARGING_CACHE.remove(playerId);
         return null;
      }
   }

   private static void clearPotaraPose(StatsData data) {
      data.getStatus().setPotaraPoseTimer(0);
      data.getStatus().setPotaraPartnerUUID(null);
      data.getStatus().setPotaraLeader(false);
   }

   private static void startPotaraPose(ServerPlayer leader, StatsData leaderData, ServerPlayer partner, StatsData partnerData) {
      leaderData.getStatus().setPotaraPoseTimer(1);
      leaderData.getStatus().setPotaraPartnerUUID(partner.getUUID());
      leaderData.getStatus().setPotaraLeader(true);
      partnerData.getStatus().setPotaraPoseTimer(1);
      partnerData.getStatus().setPotaraPartnerUUID(leader.getUUID());
      partnerData.getStatus().setPotaraLeader(false);
      NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(leader), leader);
      NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(partner), partner);
   }

   private static Item pothalaLeftCounterpart(Item rightItem) {
      if (rightItem == MainItems.POTHALA_RIGHT.get()) {
         return (Item)MainItems.POTHALA_LEFT.get();
      } else {
         return rightItem == MainItems.GREEN_POTHALA_RIGHT.get() ? (Item)MainItems.GREEN_POTHALA_LEFT.get() : null;
      }
   }

   private static boolean playerOwnsKiProjectile(ServerPlayer player) {
      for (AbstractKiProjectile ki : player.level().getEntitiesOfClass(AbstractKiProjectile.class, player.getBoundingBox().inflate(48.0))) {
         if (ki.getOwner() != null && ki.getOwner().getUUID().equals(player.getUUID())) {
            return true;
         }
      }

      return false;
   }

   private static boolean isEntityFiring(AbstractKiProjectile ki) {
      if (ki instanceof KiWaveEntity wave) {
         return wave.isFiring();
      } else if (ki instanceof KiBlastEntity blast) {
         return blast.isFiring();
      } else if (ki instanceof KiLaserEntity laser) {
         return laser.isFiring();
      } else if (ki instanceof KiDiskEntity disk) {
         return disk.isFiring();
      } else if (ki instanceof KiExplosionEntity explosion) {
         return explosion.isFiring();
      } else if (ki instanceof KiBarrierEntity barrier) {
         return barrier.isFiring();
      } else {
         return ki instanceof KiAreaEntity area ? area.isFiring() : false;
      }
   }

   private static String getTechniqueCooldownKey(String techniqueId) {
      return "TechniqueCooldown_" + techniqueId;
   }

   private static boolean performAction(ServerPlayer player, StatsData data, ActionMode mode) {
      IActionModeHandler handler = ACTION_MODE_HANDLERS.get(mode.name());
      return handler != null ? handler.performAction(player, data) : false;
   }

   public static boolean canChargeSelectedAction(ServerPlayer player, StatsData data) {
      IActionModeHandler handler = ACTION_MODE_HANDLERS.get(data.getStatus().getSelectedAction().name());
      return handler == null || handler.canCharge(player, data);
   }

   private static void handleActiveFormDrains(ServerPlayer player, StatsData data) {
      boolean hasActiveForm = data.getCharacter().getActiveForm() != null && !data.getCharacter().getActiveForm().isEmpty();
      boolean hasActiveStackForm = data.getCharacter().getActiveStackForm() != null && !data.getCharacter().getActiveStackForm().isEmpty();
      if (hasActiveForm
         && data.getCharacter().getSelectedFormGroup().contains("oozaru")
         && !data.getCharacter().isHasSaiyanTail()
         && !"supersaiyan4".equals(data.getCharacter().getActiveForm())) {
         TransformationsHelper.revertToBaseForm(player, data);
         TransformationItemCostHelper.clearFormDurationSecondsRemaining(player);
         player.removeEffect(MainEffects.TRANSFORMED);
         player.refreshDimensions();
      }

      if (!data.getStatus().isAlive() && player.level().dimension().equals(OtherworldDimension.OTHERWORLD_KEY)) {
         if (player.getFoodData().getFoodLevel() <= 20) {
            player.getFoodData().setFoodLevel(20);
         }
      } else {
         if (hasActiveForm || hasActiveStackForm) {
            handleDurationItemCosts(player, data, hasActiveForm, hasActiveStackForm);
            hasActiveForm = data.getCharacter().getActiveForm() != null && !data.getCharacter().getActiveForm().isEmpty();
            hasActiveStackForm = data.getCharacter().getActiveStackForm() != null && !data.getCharacter().getActiveStackForm().isEmpty();
         }

         if ((hasActiveForm || hasActiveStackForm) && !player.isCreative() && !player.isSpectator()) {
            int energyDrain = (int)Math.round(data.getEffectiveEnergyDrain());
            int staminaDrain = (int)Math.round(data.getEffectiveStaminaDrain());
            double healthDrain = (double)Math.round(data.getEffectiveHealthDrain());
            boolean hasEnoughEnergy = energyDrain <= 0 || data.getResources().getCurrentEnergy() >= (float)energyDrain;
            boolean hasEnoughStamina = staminaDrain <= 0 || data.getResources().getCurrentStamina() >= (float)staminaDrain;
            boolean hasEnoughHealth = healthDrain <= 0.0 || (double)player.getHealth() > healthDrain;
            if (hasEnoughEnergy && hasEnoughStamina && hasEnoughHealth) {
               if (energyDrain > 0) {
                  data.getResources().removeEnergy((float)energyDrain);
               } else if (energyDrain < 0) {
                  data.getResources().addEnergy((float)(-energyDrain));
               }

               if (staminaDrain > 0) {
                  data.getResources().removeStamina((float)staminaDrain);
               } else if (staminaDrain < 0) {
                  data.getResources().addStamina((float)(-staminaDrain));
               }

               if (healthDrain > 0.0) {
                  player.setHealth((float)((double)player.getHealth() - healthDrain));
               } else if (healthDrain < 0.0) {
                  player.setHealth((float)Math.min((double)player.getMaxHealth(), (double)player.getHealth() - healthDrain));
               }
            } else {
               data.getCharacter().clearActiveStackForm(player);
               TransformationItemCostHelper.clearStackFormDurationSecondsRemaining(player);
               player.removeEffect(MainEffects.STACK_TRANSFORMED);
               TransformationsHelper.revertToBaseForm(player, data);
               TransformationItemCostHelper.clearFormDurationSecondsRemaining(player);
               player.removeEffect(MainEffects.TRANSFORMED);
               player.refreshDimensions();
               String drainMessage = !hasEnoughEnergy
                  ? "message.dragonminez.form.drained_ki"
                  : (!hasEnoughStamina ? "message.dragonminez.form.drained_stamina" : "message.dragonminez.form.drained_health");
               player.sendSystemMessage(Component.translatable(drainMessage), true);
            }
         }
      }
   }

   private static void handleDurationItemCosts(ServerPlayer player, StatsData data, boolean hasActiveForm, boolean hasActiveStackForm) {
      if (!player.isCreative() && !player.isSpectator()) {
         if (!hasActiveForm) {
            TransformationItemCostHelper.clearFormDurationSecondsRemaining(player);
         } else {
            handleSingleDurationCost(player, data, true);
         }

         if (!hasActiveStackForm) {
            TransformationItemCostHelper.clearStackFormDurationSecondsRemaining(player);
         } else {
            handleSingleDurationCost(player, data, false);
         }
      } else {
         if (hasActiveForm) {
            TransformationItemCostHelper.clearFormDurationSecondsRemaining(player);
         }

         if (hasActiveStackForm) {
            TransformationItemCostHelper.clearStackFormDurationSecondsRemaining(player);
         }
      }
   }

   private static void handleSingleDurationCost(ServerPlayer player, StatsData data, boolean baseForm) {
      FormConfig.FormData activeData = baseForm ? data.getCharacter().getActiveFormData() : data.getCharacter().getActiveStackFormData();
      if (activeData != null && activeData.hasDurationItemCosts()) {
         int remaining = baseForm
            ? TransformationItemCostHelper.getFormDurationSecondsRemaining(player)
            : TransformationItemCostHelper.getStackFormDurationSecondsRemaining(player);
         if (remaining <= 0) {
            int addedSeconds = TransformationItemCostHelper.consumeDurationItem(player, activeData);
            if (addedSeconds <= 0) {
               clearTransformationForMissingDurationItem(player, data, baseForm);
               return;
            }

            remaining += addedSeconds;
         }

         remaining = Math.max(0, remaining - 1);
         if (baseForm) {
            TransformationItemCostHelper.setFormDurationSecondsRemaining(player, remaining);
         } else {
            TransformationItemCostHelper.setStackFormDurationSecondsRemaining(player, remaining);
         }
      } else {
         if (baseForm) {
            TransformationItemCostHelper.clearFormDurationSecondsRemaining(player);
         } else {
            TransformationItemCostHelper.clearStackFormDurationSecondsRemaining(player);
         }
      }
   }

   private static void clearTransformationForMissingDurationItem(ServerPlayer player, StatsData data, boolean baseForm) {
      if (baseForm) {
         TransformationsHelper.revertToBaseForm(player, data);
         TransformationItemCostHelper.clearFormDurationSecondsRemaining(player);
         player.removeEffect(MainEffects.TRANSFORMED);
      } else {
         data.getCharacter().clearActiveStackForm(player);
         TransformationItemCostHelper.clearStackFormDurationSecondsRemaining(player);
         player.removeEffect(MainEffects.STACK_TRANSFORMED);
      }

      player.sendSystemMessage(Component.translatable("message.dragonminez.form.no_duration_item"), true);
      player.refreshDimensions();
   }

   private static void applyHumanKiPassiveDuringCharge(ServerPlayer player, StatsData data, int originalKiCost) {
      if (data.isHumanRacialActive() && !data.isAndroidRacialActive() && originalKiCost > 0) {
         float hpAccum = player.getPersistentData().getFloat("dmz_human_hp_drain_accum") + (float)originalKiCost * 0.125F;
         int hpWhole = (int)hpAccum;
         if (hpWhole > 0 && player.getHealth() > (float)hpWhole + 2.0F) {
            player.setHealth(player.getHealth() - (float)hpWhole);
            double bonusDmg = player.getPersistentData().getDouble("dmz_human_ki_bonus_dmg") + (double)hpWhole;
            player.getPersistentData().putDouble("dmz_human_ki_bonus_dmg", bonusDmg);
            hpAccum -= (float)hpWhole;
         }

         player.getPersistentData().putFloat("dmz_human_hp_drain_accum", hpAccum);
      }
   }

   private static void clearHumanKiAccumulators(ServerPlayer player) {
      player.getPersistentData().putFloat("dmz_human_hp_drain_accum", 0.0F);
      player.getPersistentData().putDouble("dmz_human_ki_bonus_dmg", 0.0);
   }

   public static void registerActionModeHandlers() {
      ACTION_MODE_HANDLERS.put(ActionMode.FORM.name(), new FormModeHandler());
      ACTION_MODE_HANDLERS.put(ActionMode.FUSION.name(), new FusionModeHandler());
      ACTION_MODE_HANDLERS.put(ActionMode.STACK.name(), new StackFormModeHandler());
      ACTION_MODE_HANDLERS.put(ActionMode.RACIAL.name(), new RacialModeHandler());
   }

   public static void registerStatusEffectHandlers() {
      STATUS_EFFECT_HANDLERS.add(new TransformStatusHandler());
      STATUS_EFFECT_HANDLERS.add(new BioDrainHandler());
      STATUS_EFFECT_HANDLERS.add(new CooldownEffectHandler());
      STATUS_EFFECT_HANDLERS.add(new FlyStatusHandler());
      STATUS_EFFECT_HANDLERS.add(new FusionStatusHandler());
      STATUS_EFFECT_HANDLERS.add(new KiChargeStatusHandler());
      STATUS_EFFECT_HANDLERS.add(new MajinStatusHandler());
      STATUS_EFFECT_HANDLERS.add(new MightFruitStatusHandler());
      STATUS_EFFECT_HANDLERS.add(new MutantStatusHandler());
      STATUS_EFFECT_HANDLERS.add(new SaiyanPassiveHandler());
   }

   public static void registerActionModeHandler(String actionMode, IActionModeHandler actionModeHandler) {
      ACTION_MODE_HANDLERS.putIfAbsent(actionMode, actionModeHandler);
   }

   public static void registerStatusEffectHandler(IStatusEffectHandler statusEffectHandler) {
      STATUS_EFFECT_HANDLERS.add(statusEffectHandler);
   }

   static {
      registerActionModeHandlers();
      registerStatusEffectHandlers();
   }
}
