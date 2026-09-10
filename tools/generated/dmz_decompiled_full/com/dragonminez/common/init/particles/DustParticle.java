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
public class DustParticle extends TextureSheetParticle {
   protected DustParticle(ClientLevel level, double x, double y, double z, double velX, double velY, double velZ) {
      super(level, x, y, z, velX, velY, velZ);
      this.xd = velX;
      this.yd = 0.0;
      this.zd = velZ;
      this.quadSize *= 1.5F;
      this.lifetime = 40 + this.random.nextInt(20);
      this.gravity = 0.0F;
      this.rCol = 1.0F;
      this.gCol = 1.0F;
      this.bCol = 1.0F;
   }

   public void tick() {
      super.tick();
      this.setAlpha(1.0F - (float)this.age / (float)this.lifetime);
      this.xd *= 0.95;
      this.zd *= 0.95;
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
   }

   @OnlyIn(Dist.CLIENT)
   public static class Provider implements ParticleProvider<SimpleParticleType> {
      private final SpriteSet spriteSet;

      public Provider(SpriteSet spriteSet) {
         this.spriteSet = spriteSet;
      }

      public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
         DustParticle particle = new DustParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);

         try {
            particle.pickSprite(this.spriteSet);
            return particle;
         } catch (NullPointerException var17) {
            return null;
         }
      }
   }
}
