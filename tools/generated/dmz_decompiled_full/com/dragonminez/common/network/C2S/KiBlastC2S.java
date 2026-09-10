package com.dragonminez.common.network.C2S;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.compat.SableCompat;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.network.S2C.TriggerAnimationS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;

public class KiBlastC2S {
   private boolean isShooting;
   private final int colorMain;
   private final int colorBorder;
   private final float aimX;
   private final float aimY;
   private final float aimZ;

   public KiBlastC2S(boolean isShooting, int colorMain, int colorBorder) {
      this(isShooting, colorMain, colorBorder, Vec3.ZERO);
   }

   public KiBlastC2S(boolean isShooting, int colorMain, int colorBorder, Vec3 aim) {
      this.isShooting = isShooting;
      this.colorMain = colorMain;
      this.colorBorder = colorBorder;
      this.aimX = (float)aim.x;
      this.aimY = (float)aim.y;
      this.aimZ = (float)aim.z;
   }

   public KiBlastC2S(FriendlyByteBuf buffer) {
      this.isShooting = buffer.readBoolean();
      this.colorMain = buffer.readInt();
      this.colorBorder = buffer.readInt();
      this.aimX = buffer.readFloat();
      this.aimY = buffer.readFloat();
      this.aimZ = buffer.readFloat();
   }

   public void encode(FriendlyByteBuf buffer) {
      buffer.writeBoolean(this.isShooting);
      buffer.writeInt(this.colorMain);
      buffer.writeInt(this.colorBorder);
      buffer.writeFloat(this.aimX);
      buffer.writeFloat(this.aimY);
      buffer.writeFloat(this.aimZ);
   }

   public static void handle(KiBlastC2S msg, Supplier<NetworkEvent.Context> ctx) {
      ctx.get()
         .enqueueWork(
            () -> {
               ServerPlayer player = ctx.get().getSender();
               if (player != null) {
                  if (msg.isShooting) {
                     StatsProvider.get(StatsCapability.INSTANCE, player)
                        .ifPresent(
                           data -> {
                              if (!data.getCooldowns().hasCooldown("KiBlastCooldown")) {
                                 if (!data.getStatus().isStunned()) {
                                    if (data.getSkills().hasSkill("kicontrol")) {
                                       int baseCost = ConfigManager.getCombatConfig().getBaselineFormDrain();
                                       int cost = (int)((double)data.getMaxEnergy() * 0.02 + 0.1 * (double)baseCost);
                                       if (player.isCreative()) {
                                          cost = 0;
                                       }

                                       if (!(data.getResources().getCurrentEnergy() < (float)cost)) {
                                          data.getResources().removeEnergy((float)cost);
                                          data.getCooldowns().setCooldown("KiBlastCooldown", 32);
                                          player.addEffect(new MobEffectInstance(MainEffects.KI_BLAST_CD, 32, 0, false, false, true));
                                          float damage = (float)(
                                             data.getKiDamage() * (double)(0.5F + 0.05F * (float)data.getSkills().getSkillLevel("kimanipulation"))
                                          );
                                          Vec3 aim = new Vec3((double)msg.aimX, (double)msg.aimY, (double)msg.aimZ);
                                          if (Double.isFinite(aim.x) && Double.isFinite(aim.y) && Double.isFinite(aim.z) && !(aim.lengthSqr() < 1.0E-6)) {
                                             aim = aim.normalize();
                                          } else {
                                             aim = player.getLookAngle();
                                          }

                                          Vec3 localAim = SableCompat.worldDirectionToEntitySpace(player, aim).normalize();
                                          Vec3 localSpawnOffset = SableCompat.worldDirectionToEntitySpace(player, aim.scale(0.5).add(0.0, -0.4, 0.0));
                                          KiBlastEntity kiBlast = new KiBlastEntity(player.level(), player);
                                          kiBlast.setup(player, damage, 0.5F, 1.5F, msg.colorMain, msg.colorBorder);
                                          Vec3 spawn = player.getEyePosition().add(localSpawnOffset);
                                          kiBlast.setPos(spawn.x, spawn.y, spawn.z);
                                          kiBlast.shoot(localAim.x, localAim.y, localAim.z, kiBlast.getKiSpeed(), 0.5F);
                                          player.level().addFreshEntity(kiBlast);
                                          LogUtil.info(
                                             Env.SERVER,
                                             "Basic ki blast spawn: sableSubLevel={}, player={}, worldAim={}, localAim={}, spawn={}",
                                             SableCompat.isEntityInSubLevel(player),
                                             player.position(),
                                             aim,
                                             localAim,
                                             spawn
                                          );
                                          NetworkHandler.sendToTrackingEntityAndSelf(
                                             new TriggerAnimationS2C(player.getUUID(), TriggerAnimationS2C.AnimationType.KI_BLAST_SHOT, 0, player.getId()),
                                             player
                                          );
                                          NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
                                       }
                                    }
                                 }
                              }
                           }
                        );
                  } else {
                     NetworkHandler.sendToTrackingEntityAndSelf(
                        new TriggerAnimationS2C(player.getUUID(), TriggerAnimationS2C.AnimationType.KI_BLAST_SHOT, 1, player.getId()), player
                     );
                  }
               }
            }
         );
      ctx.get().setPacketHandled(true);
   }
}
