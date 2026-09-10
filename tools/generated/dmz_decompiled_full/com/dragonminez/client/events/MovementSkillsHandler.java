package com.dragonminez.client.events;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.skills.Skill;
import com.dragonminez.common.util.AttributeMods;
import com.dragonminez.server.util.GravityLogic;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME,
   value = {Dist.CLIENT}
)
public class MovementSkillsHandler {
   public static final UUID SPRINT_SPEED_UUID = UUID.fromString("c4c4e8b0-5f21-4f16-9a2d-123456789abc");
   private static int airTicks = 0;
   private static boolean wasJumping = false;
   private static boolean hasAppliedBaseBoost = false;

   @SubscribeEvent
   public static void onClientTick(Post event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null) {
         int[] jumpLevel = new int[]{0};
         int[] sprintLevel = new int[]{0};
         boolean[] isStunned = new boolean[]{false};
         boolean[] isFlying = new boolean[]{false};
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            if (data.getStatus().isHasCreatedCharacter()) {
               isStunned[0] = data.getStatus().isStunned();
               Skill flySkill = data.getSkills().getSkill("fly");
               isFlying[0] = flySkill != null && flySkill.isActive();
               if (data.getResources().getPowerRelease() >= 5) {
                  if (data.getSkills().hasSkill("jump") && data.getSkills().isSkillActive("jump")) {
                     jumpLevel[0] = data.getSkills().getSkillLevel("jump");
                  }

                  if (data.getSkills().hasSkill("sprint") && data.getSkills().isSkillActive("sprint")) {
                     sprintLevel[0] = data.getSkills().getSkillLevel("sprint");
                  }
               }
            }
         });
         AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
         if (speedAttr != null) {
            AttributeModifier existingSprint = speedAttr.getModifier(AttributeMods.id(SPRINT_SPEED_UUID));
            if (sprintLevel[0] > 0 && !isStunned[0]) {
               double boost = (double)sprintLevel[0] * 0.1;
               if (existingSprint == null || existingSprint.amount() != boost) {
                  speedAttr.removeModifier(AttributeMods.id(SPRINT_SPEED_UUID));
                  speedAttr.addTransientModifier(AttributeMods.of(SPRINT_SPEED_UUID, "Sprint Skill Boost", boost, Operation.ADD_MULTIPLIED_TOTAL));
               }
            } else if (existingSprint != null) {
               speedAttr.removeModifier(AttributeMods.id(SPRINT_SPEED_UUID));
            }
         }

         if (isStunned[0]) {
            airTicks = 0;
            wasJumping = false;
            hasAppliedBaseBoost = false;
         } else {
            boolean isSpacePressed = mc.options.keyJump.isDown();
            boolean isOnGround = player.onGround();
            boolean isJumping = !isOnGround && player.getDeltaMovement().y > 0.0;
            boolean isMoving = player.input.up || player.input.down || player.input.left || player.input.right;
            if (!isOnGround && isMoving && speedAttr != null) {
               double currentSpeed = speedAttr.getValue();
               double baseSpeed = 0.1;
               if (currentSpeed > baseSpeed) {
                  double speedRatio = currentSpeed / baseSpeed;
                  double dragCompensation = 1.0 + Math.min(0.085, (speedRatio - 1.0) * 0.015);
                  Vec3 delta = player.getDeltaMovement();
                  player.setDeltaMovement(delta.x * dragCompensation, delta.y, delta.z * dragCompensation);
               }
            }

            if (jumpLevel[0] > 0) {
               if (isJumping && !wasJumping && !hasAppliedBaseBoost) {
                  float targetBlocks = 1.0F + (float)jumpLevel[0] * 0.1F;
                  float blocksToAdd = targetBlocks - 1.25F;
                  float baseBoost = blocksToAdd * 0.18F;
                  baseBoost *= (float)GravityLogic.getJumpFactor(player);
                  player.setDeltaMovement(player.getDeltaMovement().add(0.0, (double)baseBoost, 0.0));
                  hasAppliedBaseBoost = true;
               }

               if (isJumping && isSpacePressed) {
                  airTicks++;
                  int maxAirTicks = jumpLevel[0];
                  if (airTicks <= maxAirTicks) {
                     float incrementalBoost = 0.11F;
                     player.setDeltaMovement(player.getDeltaMovement().add(0.0, (double)incrementalBoost, 0.0));
                  }
               }

               if (isOnGround) {
                  if (airTicks > 0) {
                     airTicks = 0;
                  }

                  hasAppliedBaseBoost = false;
               }

               wasJumping = isJumping;
            } else {
               airTicks = 0;
               wasJumping = false;
               hasAppliedBaseBoost = false;
            }

            if (!isOnGround && !isFlying[0] && !player.getAbilities().flying && !player.isInWater() && !player.onClimbable()) {
               double fallExtra = GravityLogic.getFallExtra(player);
               if (fallExtra > 0.0) {
                  player.setDeltaMovement(player.getDeltaMovement().add(0.0, -fallExtra, 0.0));
               }
            }
         }
      }
   }
}
