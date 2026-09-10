package com.dragonminez.common.network.C2S;

import com.dragonminez.common.compat.CameraAimHelper;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SokidanControlC2S {
   private final SokidanControlC2S.Action action;
   private final float aimX;
   private final float aimY;
   private final float aimZ;

   private SokidanControlC2S(SokidanControlC2S.Action action, Vec3 aim) {
      this.action = action;
      this.aimX = (float)aim.x;
      this.aimY = (float)aim.y;
      this.aimZ = (float)aim.z;
   }

   public SokidanControlC2S(FriendlyByteBuf buf) {
      this.action = (SokidanControlC2S.Action)buf.readEnum(SokidanControlC2S.Action.class);
      this.aimX = buf.readFloat();
      this.aimY = buf.readFloat();
      this.aimZ = buf.readFloat();
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeEnum(this.action);
      buf.writeFloat(this.aimX);
      buf.writeFloat(this.aimY);
      buf.writeFloat(this.aimZ);
   }

   public static SokidanControlC2S toggle(Vec3 aim) {
      return new SokidanControlC2S(SokidanControlC2S.Action.TOGGLE, aim);
   }

   public static SokidanControlC2S aim(Vec3 aim) {
      return new SokidanControlC2S(SokidanControlC2S.Action.AIM, aim);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> {
         ServerPlayer player = ctx.get().getSender();
         if (player != null && player.level() != null) {
            CameraAimHelper.store(player, new Vec3((double)this.aimX, (double)this.aimY, (double)this.aimZ));
            if (this.action == SokidanControlC2S.Action.AIM) {
               return;
            }

            double searchRadius = 64.0;
            AABB searchBox = player.getBoundingBox().inflate(searchRadius);

            for (KiBlastEntity blast : player.level().getEntitiesOfClass(KiBlastEntity.class, searchBox)) {
               if (blast.isControllable() && blast.getOwner() != null && blast.getOwner().getUUID().equals(player.getUUID())) {
                  blast.toggleSokidanControl();
               }
            }
         }
      });
      ctx.get().setPacketHandled(true);
   }

   private static enum Action {
      TOGGLE,
      AIM;
   }
}
