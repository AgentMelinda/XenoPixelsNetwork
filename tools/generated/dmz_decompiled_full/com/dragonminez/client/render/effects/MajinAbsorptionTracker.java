package com.dragonminez.client.render.effects;

import com.dragonminez.client.render.util.ModRenderTypes;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.extras.ActionMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.joml.Matrix4f;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME,
   value = {Dist.CLIENT}
)
public final class MajinAbsorptionTracker {
   private static final double ABSORB_RANGE = 8.0;
   private static final float FADE_IN = 0.06F;
   private static final float FADE_OUT = 0.1F;
   private static final float INFLATE = 1.12F;
   private static final float MIN_GROW = 0.25F;
   private static final float BREATH_AMP = 0.18F;
   private static final float WOBBLE_AMP = 0.22F;
   private static final float SPIKE_AMP = 0.55F;
   private static final float ANIM_SPEED = 0.45F;
   private static final int SOUND_INTERVAL = 24;
   private static final Map<UUID, Integer> SOUND_COOLDOWN = new HashMap<>();
   private static final Set<UUID> absorbingThisTick = new HashSet<>();
   private static final float SHADE_AMBIENT = 0.55F;
   private static final float[] LIGHT_DIR = normalize(0.3F, 1.0F, 0.45F);
   private static final int STACKS = 18;
   private static final int SECTORS = 28;
   private static final Map<Integer, MajinAbsorptionTracker.AbsorbState> ACTIVE = new HashMap<>();

   private MajinAbsorptionTracker() {
   }

   @SubscribeEvent
   public static void onClientTick(Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && !mc.isPaused()) {
         absorbingThisTick.clear();

         for (MajinAbsorptionTracker.AbsorbState state : ACTIVE.values()) {
            state.seenThisTick = false;
         }

         for (Player player : mc.level.players()) {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            if (data != null && data.getStatus().isActionCharging() && data.getStatus().getSelectedAction() == ActionMode.RACIAL) {
               RaceCharacterConfig race = ConfigManager.getRaceCharacter(data.getCharacter().getRaceName());
               if (race != null && "majin".equals(race.getRacialSkill())) {
                  LivingEntity target = findTarget(player);
                  if (target != null && target != player) {
                     float[] body = data.getCharacter().getRgbBodyColor();
                     MajinAbsorptionTracker.AbsorbState state = ACTIVE.computeIfAbsent(target.getId(), id -> new MajinAbsorptionTracker.AbsorbState());
                     state.seenThisTick = true;
                     if (body != null && body.length >= 3) {
                        state.r = body[0];
                        state.g = body[1];
                        state.b = body[2];
                     }

                     playAbsorbSound(mc, player);
                  }
               }
            }
         }

         SOUND_COOLDOWN.keySet().retainAll(absorbingThisTick);
         ACTIVE.entrySet().removeIf(entry -> {
            MajinAbsorptionTracker.AbsorbState statex = entry.getValue();
            if (statex.seenThisTick) {
               statex.intensity = Math.min(1.0F, statex.intensity + 0.06F);
               return false;
            } else {
               statex.intensity -= 0.1F;
               return statex.intensity <= 0.0F;
            }
         });
      } else {
         if (!ACTIVE.isEmpty()) {
            ACTIVE.clear();
         }

         if (!SOUND_COOLDOWN.isEmpty()) {
            SOUND_COOLDOWN.clear();
         }
      }
   }

   private static void playAbsorbSound(Minecraft mc, Player player) {
      UUID id = player.getUUID();
      absorbingThisTick.add(id);
      int cooldown = SOUND_COOLDOWN.getOrDefault(id, 0);
      if (cooldown > 0) {
         SOUND_COOLDOWN.put(id, cooldown - 1);
      } else {
         mc.level
            .playLocalSound(player.getX(), player.getY(), player.getZ(), (SoundEvent)MainSounds.MAJIN_ABSORB.get(), SoundSource.PLAYERS, 0.8F, 1.0F, false);
         SOUND_COOLDOWN.put(id, 24);
      }
   }

   private static LivingEntity findTarget(Player player) {
      Vec3 start = player.getEyePosition();
      Vec3 look = player.getViewVector(1.0F);
      Vec3 end = start.add(look.scale(8.0));
      AABB searchBox = player.getBoundingBox().expandTowards(look.scale(8.0)).inflate(1.0);

      for (Entity entity : player.level().getEntities(player, searchBox, e -> e instanceof LivingEntity && !e.isSpectator() && e.isPickable())) {
         AABB box = entity.getBoundingBox().inflate((double)entity.getPickRadius());
         if (box.contains(start) || box.clip(start, end).isPresent()) {
            return (LivingEntity)entity;
         }
      }

      return null;
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_ENTITIES) {
         if (!ACTIVE.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
               float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
               Camera camera = event.getCamera();
               Vec3 cam = camera.getPosition();
               PoseStack pose = event.getPoseStack();
               BufferSource buffers = mc.renderBuffers().bufferSource();
               VertexConsumer consumer = buffers.getBuffer(ModRenderTypes.gooBlob());

               for (Entry<Integer, MajinAbsorptionTracker.AbsorbState> entry : ACTIVE.entrySet()) {
                  Entity entity = mc.level.getEntity(entry.getKey());
                  if (entity != null) {
                     MajinAbsorptionTracker.AbsorbState state = entry.getValue();
                     if (!(state.intensity <= 0.0F)) {
                        Vec3 feet = entity.getPosition(partialTick);
                        float time = (float)entity.tickCount + partialTick;
                        pose.pushPose();
                        pose.translate(feet.x - cam.x, feet.y - cam.y, feet.z - cam.z);
                        renderBlob(pose, consumer, time, state, entity.getBbWidth(), entity.getBbHeight());
                        pose.popPose();
                     }
                  }
               }

               buffers.endBatch(ModRenderTypes.gooBlob());
            }
         }
      }
   }

   private static void renderBlob(PoseStack pose, VertexConsumer consumer, float time, MajinAbsorptionTracker.AbsorbState state, float width, float height) {
      float intensity = state.intensity;
      if (!(intensity <= 0.0F)) {
         float cy = height * 0.5F;
         float t = time * 0.45F;
         float grow = 0.25F + 0.75F * intensity;
         float breath = 0.18F * ((float)Math.sin((double)t * 0.9) * 0.6F + (float)Math.sin((double)t * 2.3) * 0.4F);
         float ry = height * 0.5F * 1.12F * grow * (1.0F + breath);
         float rxz = width * 0.5F * 1.12F * grow * (1.0F - breath * 0.5F);
         float wob = 0.22F;
         Matrix4f matrix = pose.last().pose();
         float[][][] grid = new float[19][29][3];
         int[][] colors = new int[19][29];

         for (int i = 0; i <= 18; i++) {
            double lat = Math.PI * (double)i / 18.0;
            double sinLat = Math.sin(lat);
            double cosLat = Math.cos(lat);

            for (int j = 0; j <= 28; j++) {
               double lon = (Math.PI * 2) * (double)j / 28.0;
               double dx = sinLat * Math.cos(lon);
               double dz = sinLat * Math.sin(lon);
               double lumps = Math.sin(dx * 5.0 + (double)t * 0.9) * Math.sin(cosLat * 4.0 - (double)t * 0.7) * Math.sin(dz * 6.0 + (double)t * 1.1);
               double s = Math.sin(dx * 3.0 + cosLat * 2.5 + dz * 3.5 + (double)t * 2.6);
               double spike = Math.pow(Math.max(0.0, s), 6.0);
               float disp = 1.0F + wob * (float)lumps + 0.55F * intensity * (float)spike;
               grid[i][j][0] = (float)(dx * (double)rxz * (double)disp);
               grid[i][j][1] = (float)((double)cy + cosLat * (double)ry * (double)disp);
               grid[i][j][2] = (float)(dz * (double)rxz * (double)disp);
               float ndl = (float)(dx * (double)LIGHT_DIR[0] + cosLat * (double)LIGHT_DIR[1] + dz * (double)LIGHT_DIR[2]);
               float shade = 0.55F + 0.45F * Math.max(0.0F, ndl);
               colors[i][j] = packColor(state.r * shade, state.g * shade, state.b * shade);
            }
         }

         for (int i = 0; i < 18; i++) {
            for (int j = 0; j < 28; j++) {
               vertex(consumer, matrix, grid[i][j], colors[i][j]);
               vertex(consumer, matrix, grid[i + 1][j], colors[i + 1][j]);
               vertex(consumer, matrix, grid[i + 1][j + 1], colors[i + 1][j + 1]);
               vertex(consumer, matrix, grid[i][j], colors[i][j]);
               vertex(consumer, matrix, grid[i + 1][j + 1], colors[i + 1][j + 1]);
               vertex(consumer, matrix, grid[i][j + 1], colors[i][j + 1]);
            }
         }
      }
   }

   private static void vertex(VertexConsumer consumer, Matrix4f matrix, float[] p, int argb) {
      consumer.addVertex(matrix, p[0], p[1], p[2]).setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, 255);
   }

   private static int packColor(float r, float g, float b) {
      return clampByte(r) << 16 | clampByte(g) << 8 | clampByte(b);
   }

   private static int clampByte(float channel) {
      return Math.max(0, Math.min(255, Math.round(channel * 255.0F)));
   }

   private static float[] normalize(float x, float y, float z) {
      float len = (float)Math.sqrt((double)(x * x + y * y + z * z));
      return len == 0.0F ? new float[]{0.0F, 1.0F, 0.0F} : new float[]{x / len, y / len, z / len};
   }

   private static final class AbsorbState {
      float intensity;
      float r = 1.0F;
      float g = 0.4F;
      float b = 0.7F;
      boolean seenThisTick;
   }
}
