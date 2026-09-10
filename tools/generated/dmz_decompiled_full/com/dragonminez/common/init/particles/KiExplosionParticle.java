package com.dragonminez.common.init.particles;

import com.dragonminez.client.render.util.ModParticleRenderTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public class KiExplosionParticle extends TextureSheetParticle {
   private final SpriteSet spriteSet;
   private final float baseSize;
   private final float targetSize;
   private final int animationFrames = 8;

   protected KiExplosionParticle(ClientLevel level, double x, double y, double z, double r, double g, double b, SpriteSet spriteSet, float size) {
      super(level, x, y, z, 0.0, 0.0, 0.0);
      this.spriteSet = spriteSet;
      this.baseSize = size;
      this.targetSize = size * 1.3F;
      this.quadSize = this.baseSize;
      this.lifetime = 20;
      this.gravity = 0.0F;
      this.hasPhysics = false;
      this.rCol = 1.0F;
      this.gCol = 1.0F;
      this.bCol = 1.0F;
      this.alpha = 1.0F;
      this.setSprite(this.spriteSet.get(0, 8));
   }

   public void tick() {
      this.xo = this.x;
      this.yo = this.y;
      this.zo = this.z;
      if (this.age++ >= this.lifetime) {
         this.remove();
      } else {
         if (this.age < 8) {
            this.setSprite(this.spriteSet.get(this.age, 8));
         } else {
            this.setSprite(this.spriteSet.get(8 - 1, 8));
         }

         if (this.age <= 8) {
            float expandProgress = (float)this.age / 8.0F;
            this.quadSize = Mth.lerp(expandProgress, this.baseSize, this.targetSize);
         } else {
            this.quadSize = this.targetSize;
         }

         if (this.age > 8) {
            float fadeTicks = (float)(this.age - 8);
            float totalFadeDuration = (float)(this.lifetime - 8);
            float lifeRatio = 1.0F - fadeTicks / totalFadeDuration;
            this.alpha = lifeRatio * lifeRatio;
         }
      }
   }

   public ParticleRenderType getRenderType() {
      return ModParticleRenderTypes.ADDITIVE_LIT;
   }

   public int getLightColor(float pPartialTick) {
      return 15728880;
   }

   public static class Provider implements ParticleProvider<SimpleParticleType> {
      private final SpriteSet spriteSet;

      public Provider(SpriteSet spriteSet) {
         this.spriteSet = spriteSet;
      }

      public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double r, double g, double b) {
         float size = (float)r;
         if (size <= 0.0F) {
            size = 1.0F;
         }

         try {
            return new KiExplosionParticle(level, x, y, z, r, g, b, this.spriteSet, size);
         } catch (NullPointerException var17) {
            return null;
         }
      }
   }
}
