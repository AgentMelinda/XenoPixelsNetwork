package com.dragonminez.common.network.C2S;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.TechniqueData;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

public class CreateTechniqueC2S {
   private static final int MAX_NAME_LENGTH = 64;
   private final String name;
   private final String type;
   private final String utility;
   private final float damage;
   private final float speed;
   private final float size;
   private final int armorPen;
   private final int cast;
   private final int cooldown;
   private final int colorInterior;
   private final int colorExterior;
   private final int colorOutline;
   private final String secondaryEffectType;
   private final String affectedStat;
   private final float secondaryIntensity;
   private final int secondaryDuration;

   public CreateTechniqueC2S(
      String name,
      String type,
      String utility,
      float damage,
      float speed,
      float size,
      int armorPen,
      int cast,
      int cooldown,
      int colorInterior,
      int colorExterior,
      int colorOutline,
      String secondaryEffectType,
      String affectedStat,
      float secondaryIntensity,
      int secondaryDuration
   ) {
      this.name = name;
      this.type = type;
      this.utility = utility;
      this.damage = damage;
      this.speed = speed;
      this.size = size;
      this.armorPen = armorPen;
      this.cast = cast;
      this.cooldown = cooldown;
      this.colorInterior = colorInterior;
      this.colorExterior = colorExterior;
      this.colorOutline = colorOutline;
      this.secondaryEffectType = secondaryEffectType;
      this.affectedStat = affectedStat;
      this.secondaryIntensity = secondaryIntensity;
      this.secondaryDuration = secondaryDuration;
   }

   public CreateTechniqueC2S(FriendlyByteBuf buf) {
      this.name = buf.readUtf();
      this.type = buf.readUtf();
      this.utility = buf.readUtf();
      this.damage = buf.readFloat();
      this.speed = buf.readFloat();
      this.size = buf.readFloat();
      this.armorPen = buf.readInt();
      this.cast = buf.readInt();
      this.cooldown = buf.readInt();
      this.colorInterior = buf.readInt();
      this.colorExterior = buf.readInt();
      this.colorOutline = buf.readInt();
      this.secondaryEffectType = buf.readUtf();
      this.affectedStat = buf.readUtf();
      this.secondaryIntensity = buf.readFloat();
      this.secondaryDuration = buf.readInt();
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeUtf(this.name);
      buf.writeUtf(this.type);
      buf.writeUtf(this.utility);
      buf.writeFloat(this.damage);
      buf.writeFloat(this.speed);
      buf.writeFloat(this.size);
      buf.writeInt(this.armorPen);
      buf.writeInt(this.cast);
      buf.writeInt(this.cooldown);
      buf.writeInt(this.colorInterior);
      buf.writeInt(this.colorExterior);
      buf.writeInt(this.colorOutline);
      buf.writeUtf(this.secondaryEffectType != null ? this.secondaryEffectType : "NONE");
      buf.writeUtf(this.affectedStat != null ? this.affectedStat : "");
      buf.writeFloat(this.secondaryIntensity);
      buf.writeInt(this.secondaryDuration);
   }

   public boolean handle(Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(
         () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
               LogUtil.info(Env.SERVER, "Received custom technique creation request '{}' from {}", this.name, player.getName().getString());
               StatsProvider.get(StatsCapability.INSTANCE, player)
                  .ifPresent(
                     data -> {
                        KiAttackData technique = new KiAttackData();
                        String safeName = this.name != null && !this.name.trim().isEmpty() ? this.name.trim() : "New Skill";
                        if (safeName.length() > 64) {
                           safeName = safeName.substring(0, 64);
                        }

                        technique.setName(safeName);
                        technique.setAuthor(player.getName().getString());
                        technique.setId(TechniqueData.generateId(technique.getAuthor(), technique.getName()));
                        if (data.getTechniques().getUnlockedTechniques().containsKey(technique.getId())) {
                           LogUtil.warn(Env.SERVER, "Rejected custom technique '{}': duplicate id {}", safeName, technique.getId());
                           player.displayClientMessage(Component.literal("A technique named \"" + safeName + "\" already exists."), true);
                           NetworkHandler.sendToPlayer(new ProgressionSyncS2C(player), player);
                        } else {
                           KiAttackData.KiType parsedType;
                           try {
                              parsedType = KiAttackData.KiType.valueOf(this.type);
                           } catch (Exception var13) {
                              parsedType = KiAttackData.KiType.SMALL_BALL;
                           }

                           technique.setKiType(parsedType);

                           KiAttackData.Utility parsedUtility;
                           try {
                              parsedUtility = KiAttackData.Utility.valueOf(this.utility);
                           } catch (Exception var12) {
                              parsedUtility = KiAttackData.Utility.DAMAGE;
                           }

                           if (!allowsUtilityForType(parsedType)) {
                              parsedUtility = KiAttackData.Utility.DAMAGE;
                           }

                           technique.setUtility(parsedUtility);
                           float[] normalized = KiAttackData.normalizeStatsForType(parsedType, this.damage, this.size, this.speed, this.armorPen);
                           technique.setDamageMultiplier(normalized[0]);
                           technique.setSize(normalized[1]);
                           technique.setSpeed(normalized[2]);
                           technique.setArmorPenetration(Math.round(normalized[3]));
                           technique.setCastTime(Mth.clamp(this.cast, 0, 200));
                           technique.setCooldown(Mth.clamp(this.cooldown, 0, 400));
                           technique.setColorInterior(this.colorInterior & 16777215);
                           technique.setColorExterior(this.colorExterior & 16777215);
                           technique.setColorOutline(this.colorOutline & 16777215);
                           technique.setAnimation("");

                           KiAttackData.SecondaryEffectType parsedSecondary;
                           try {
                              parsedSecondary = KiAttackData.SecondaryEffectType.valueOf(this.secondaryEffectType);
                           } catch (Exception var11) {
                              parsedSecondary = KiAttackData.SecondaryEffectType.NONE;
                           }

                           technique.setSecondaryEffectType(parsedSecondary);
                           if (this.affectedStat != null && !this.affectedStat.isEmpty()) {
                              try {
                                 technique.setAffectedStat(KiAttackData.AffectedStat.valueOf(this.affectedStat));
                              } catch (Exception var10) {
                                 technique.setAffectedStat(null);
                              }
                           } else {
                              technique.setAffectedStat(null);
                           }

                           technique.setSecondaryIntensity(this.secondaryIntensity);
                           technique.setSecondaryDuration(this.secondaryDuration);
                           technique.calculateDerivedValues();
                           int tpCost = Math.max(0, Math.round(technique.getTpCost()));
                           if (data.getResources().getTrainingPoints() < (float)tpCost) {
                              LogUtil.warn(
                                 Env.SERVER,
                                 "Rejected custom technique '{}': needs {} TP but player has {}",
                                 safeName,
                                 tpCost,
                                 data.getResources().getTrainingPoints()
                              );
                              player.displayClientMessage(Component.literal("Not enough TP: this technique costs " + tpCost + " TP."), true);
                              NetworkHandler.sendToPlayer(new ProgressionSyncS2C(player), player);
                           } else if (data.getTechniques().getUnlockedTechniques().size() >= 256) {
                              LogUtil.warn(Env.SERVER, "Rejected custom technique '{}': technique limit reached", safeName);
                              player.displayClientMessage(Component.literal("Technique limit reached."), true);
                           } else {
                              if (tpCost > 0) {
                                 data.getResources().removeTrainingPoints((float)tpCost);
                              }

                              data.getTechniques().unlockTechnique(technique);
                              LogUtil.info(
                                 Env.SERVER, "Registered custom technique '{}' as {} for {}", safeName, technique.getId(), player.getName().getString()
                              );
                              player.displayClientMessage(Component.literal("Created technique: " + safeName), true);
                              NetworkHandler.sendToPlayer(new ProgressionSyncS2C(player), player);
                           }
                        }
                     }
                  );
            }
         }
      );
      context.setPacketHandled(true);
      return true;
   }

   private static boolean allowsUtilityForType(KiAttackData.KiType type) {
      return KiAttackData.allowsHealUtility(type);
   }
}
