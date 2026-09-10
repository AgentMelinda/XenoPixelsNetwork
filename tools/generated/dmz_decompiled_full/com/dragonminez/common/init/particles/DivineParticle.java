package com.dragonminez.common.init.particles;

import com.dragonminez.client.render.util.ModParticleRenderTypes;
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
public class DivineParticle extends TextureSheetParticle {
   private float baseScale;

   protected DivineParticle(ClientLevel level, double x, double y, double z, double r, double g, double b) {
      super(level, x, y, z);
      this.rCol = (float)r;
      this.gCol = (float)g;
      this.bCol = (float)b;
      this.xd = 0.0;
      this.yd = 0.0;
      this.zd = 0.0;
      this.quadSize *= 1.2F;
      this.baseScale = this.quadSize;
      this.lifetime = 20 + this.random.nextInt(10);
      this.hasPhysics = false;
   }

   public void resize(float multiplier) {
      this.quadSize = this.baseScale * (0.5F + multiplier * 0.5F);
   }

   public void tick() {
      this.xo = this.x;
      this.yo = this.y;
      this.zo = this.z;
      if (this.age++ >= this.lifetime) {
         this.remove();
      } else {
         this.move(this.xd, this.yd, this.zd);
         this.yd += 0.001;
         this.setAlpha(1.0F - (float)this.age / (float)this.lifetime);
      }
   }

   public ParticleRenderType getRenderType() {
      return ModParticleRenderTypes.ADDITIVE_LIT;
   }

   @OnlyIn(Dist.CLIENT)
   public static class Provider implements ParticleProvider<SimpleParticleType> {
      private final SpriteSet spriteSet;

      public Provider(SpriteSet spriteSet) {
         this.spriteSet = spriteSet;
      }

      public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double r, double g, double b) {
         DivineParticle particle = new DivineParticle(level, x, y, z, r, g, b);

         try {
            particle.pickSprite(this.spriteSet);
            return particle;
         } catch (NullPointerException var17) {
            return null;
         }
      }
   }
}
