package com.dragonminez.common.init.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RockParticle extends TextureSheetParticle {
   protected RockParticle(ClientLevel level, double x, double y, double z, double velX, double velY, double velZ) {
      super(level, x, y, z, velX, velY, velZ);
      this.gravity = 0.0F;
      this.yd = velY + Math.random() * 0.02;
      this.xd = velX;
      this.zd = velZ;
      this.quadSize = 0.1F + this.random.nextFloat() * 0.2F;
      this.lifetime = 40 + this.random.nextInt(40);
      this.roll = this.random.nextFloat() * 3.14F;
      this.oRoll = this.roll;
   }

   public void tick() {
      this.xo = this.x;
      this.yo = this.y;
      this.zo = this.z;
      this.oRoll = this.roll;
      if (this.age++ >= this.lifetime) {
         this.remove();
      } else {
         this.move(this.xd, this.yd, this.zd);
         this.roll += 0.1F;
         this.xd *= 0.9;
         this.zd *= 0.9;
         this.yd += 0.002F;
         if (this.age > this.lifetime - 10) {
            this.alpha = 1.0F - (float)(this.age - (this.lifetime - 10)) / 10.0F;
         }
      }
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
   }

   @OnlyIn(Dist.CLIENT)
   public static class Provider implements ParticleProvider<SimpleParticleType> {
      private final SpriteSet spriteSet;

      public Provider(SpriteSet spriteSet) {
         this.spriteSet = spriteSet;
      }

      public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
         RockParticle particle = new RockParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);

         try {
            particle.pickSprite(this.spriteSet);
            return particle;
         } catch (NullPointerException var17) {
            return null;
         }
      }
   }
}
