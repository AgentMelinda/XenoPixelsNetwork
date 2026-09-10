package com.dragonminez.common.init.particles;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.Entity;

public class KiFlashParticle extends TextureSheetParticle {
   private final int targetEntityId;
   private Entity targetEntity;
   private boolean colorSet = false;
   private final SpriteSet spriteSet;

   protected KiFlashParticle(ClientLevel level, double x, double y, double z, double entityIdAsDouble, double colorData, double sizeData, SpriteSet spriteSet) {
      super(level, x, y, z, 0.0, 0.0, 0.0);
      this.spriteSet = spriteSet;
      this.targetEntityId = (int)entityIdAsDouble;
      this.quadSize = 1.0F;
      this.lifetime = 1000;
      this.gravity = 0.0F;
      this.hasPhysics = false;
      this.alpha = 0.0F;
      this.setSpriteFromAge(spriteSet);
   }

   public void tick() {
      if (this.targetEntity == null) {
         this.targetEntity = this.level.getEntity(this.targetEntityId);
      }

      if (this.targetEntity != null && this.targetEntity.isAlive()) {
         this.xo = this.x;
         this.yo = this.y;
         this.zo = this.z;
         this.setPos(this.targetEntity.getX(), this.targetEntity.getY() + (double)this.targetEntity.getBbHeight() / 2.0, this.targetEntity.getZ());
         if (this.targetEntity instanceof AbstractKiProjectile kiBall) {
            if (!this.colorSet) {
               float[] rgb = kiBall.getRgbColorBorder();
               this.rCol = rgb[0];
               this.gCol = rgb[1];
               this.bCol = rgb[2];
               this.alpha = 0.4F;
               this.colorSet = true;
            }

            this.setSpriteFromAge(this.spriteSet);
            this.quadSize = kiBall.getSize() + 0.3F;
         }
      } else {
         this.remove();
      }
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
   }

   public int getLightColor(float pPartialTick) {
      return 15728880;
   }

   public static class Provider implements ParticleProvider<SimpleParticleType> {
      private final SpriteSet spriteSet;

      public Provider(SpriteSet spriteSet) {
         this.spriteSet = spriteSet;
      }

      public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
         try {
            return new KiFlashParticle(level, x, y, z, dx, dy, dz, this.spriteSet);
         } catch (NullPointerException var16) {
            return null;
         }
      }
   }
}
