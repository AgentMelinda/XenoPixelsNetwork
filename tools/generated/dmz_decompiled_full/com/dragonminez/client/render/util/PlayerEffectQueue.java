package com.dragonminez.client.render.util;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;

public class PlayerEffectQueue {
   private static final List<PlayerEffectQueue.AuraRenderEntry> AURA_QUEUE = new ArrayList<>();
   private static final List<PlayerEffectQueue.SparkRenderEntry> SPARK_QUEUE = new ArrayList<>();
   private static final List<PlayerEffectQueue.FirstPersonAuraEntry> FIRST_PERSON_AURA_QUEUE = new ArrayList<>();
   private static final List<PlayerEffectQueue.KiRenderTask> KI_ATTACK_QUEUE = new ArrayList<>();
   private static final List<PlayerEffectQueue.DeferredEffectTask> ENTITY_EFFECT_QUEUE = new ArrayList<>();

   public static synchronized void addAura(AbstractClientPlayer player, BakedGeoModel playerModel, PoseStack currentStack, float partialTick, int packedLight) {
      AURA_QUEUE.add(new PlayerEffectQueue.AuraRenderEntry(player, playerModel, new Matrix4f(currentStack.last().pose()), partialTick, packedLight));
   }

   public static synchronized void addSpark(AbstractClientPlayer player, BakedGeoModel playerModel, PoseStack currentStack, float partialTick, int packedLight) {
      SPARK_QUEUE.add(new PlayerEffectQueue.SparkRenderEntry(player, playerModel, new Matrix4f(currentStack.last().pose()), partialTick, packedLight));
   }

   public static synchronized void addFirstPersonAura(AbstractClientPlayer player, PoseStack currentStack, float partialTick, int packedLight) {
      FIRST_PERSON_AURA_QUEUE.add(new PlayerEffectQueue.FirstPersonAuraEntry(player, new Matrix4f(currentStack.last().pose()), partialTick, packedLight));
   }

   public static synchronized void addKiAttack(PlayerEffectQueue.KiRenderTask task) {
      KI_ATTACK_QUEUE.add(task);
   }

   public static synchronized void addEntityEffect(PlayerEffectQueue.DeferredEffectTask task) {
      ENTITY_EFFECT_QUEUE.add(task);
   }

   public static synchronized List<PlayerEffectQueue.AuraRenderEntry> getAndClearAuras() {
      if (AURA_QUEUE.isEmpty()) {
         return new ArrayList<>();
      } else {
         List<PlayerEffectQueue.AuraRenderEntry> copy = new ArrayList<>(AURA_QUEUE);
         AURA_QUEUE.clear();
         return copy;
      }
   }

   public static synchronized List<PlayerEffectQueue.SparkRenderEntry> getAndClearSparks() {
      if (SPARK_QUEUE.isEmpty()) {
         return new ArrayList<>();
      } else {
         List<PlayerEffectQueue.SparkRenderEntry> copy = new ArrayList<>(SPARK_QUEUE);
         SPARK_QUEUE.clear();
         return copy;
      }
   }

   public static synchronized List<PlayerEffectQueue.FirstPersonAuraEntry> getAndClearFirstPersonAuras() {
      if (FIRST_PERSON_AURA_QUEUE.isEmpty()) {
         return new ArrayList<>();
      } else {
         List<PlayerEffectQueue.FirstPersonAuraEntry> copy = new ArrayList<>(FIRST_PERSON_AURA_QUEUE);
         FIRST_PERSON_AURA_QUEUE.clear();
         return copy;
      }
   }

   public static synchronized List<PlayerEffectQueue.KiRenderTask> getAndClearKiAttacks() {
      if (KI_ATTACK_QUEUE.isEmpty()) {
         return new ArrayList<>();
      } else {
         List<PlayerEffectQueue.KiRenderTask> copy = new ArrayList<>(KI_ATTACK_QUEUE);
         KI_ATTACK_QUEUE.clear();
         return copy;
      }
   }

   public static synchronized List<PlayerEffectQueue.DeferredEffectTask> getAndClearEntityEffects() {
      if (ENTITY_EFFECT_QUEUE.isEmpty()) {
         return new ArrayList<>();
      } else {
         List<PlayerEffectQueue.DeferredEffectTask> copy = new ArrayList<>(ENTITY_EFFECT_QUEUE);
         ENTITY_EFFECT_QUEUE.clear();
         return copy;
      }
   }

   public static record AuraRenderEntry(AbstractClientPlayer player, BakedGeoModel playerModel, Matrix4f poseMatrix, float partialTick, int packedLight) {
   }

   public interface DeferredEffectTask {
      void render();
   }

   public static record FirstPersonAuraEntry(AbstractClientPlayer player, Matrix4f poseMatrix, float partialTick, int packedLight) {
   }

   public interface KiRenderTask {
      void render(PoseStack var1, Matrix4f var2);
   }

   public static record SparkRenderEntry(AbstractClientPlayer player, BakedGeoModel playerModel, Matrix4f poseMatrix, float partialTick, int packedLight) {
   }
}
