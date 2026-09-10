package com.dragonminez.client.animation;

import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import com.dragonminez.common.combat.logic.weapon.WeaponRegistry;
import com.dragonminez.common.combat.weapon.WeaponAttributes;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Player;

public final class CombatAnimationResolver {
   private static final ResourceLocation BASE_ANIMATION_FILE = ResourceLocation.fromNamespaceAndPath(
      "dragonminez", "animations/entity/races/combat.animation.json"
   );
   private static final Set<String> AVAILABLE_RAW = new HashSet<>();

   private CombatAnimationResolver() {
   }

   public static void reload(ResourceManager resourceManager) {
      AVAILABLE_RAW.clear();

      try {
         Optional<Resource> resourceOptional = resourceManager.getResource(BASE_ANIMATION_FILE);
         if (!resourceOptional.isEmpty()) {
            try (
               InputStream stream = resourceOptional.get().open();
               InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            ) {
               JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
               JsonObject animations = root.getAsJsonObject("animations");
               if (animations != null) {
                  for (String key : animations.keySet()) {
                     AVAILABLE_RAW.add(key);
                  }

                  return;
               }
            }
         }
      } catch (Exception var12) {
      }
   }

   private static void ensureLoaded() {
      if (AVAILABLE_RAW.isEmpty()) {
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft != null) {
            reload(minecraft.getResourceManager());
         }
      }
   }

   public static String sanitizeAnimationId(String animationId) {
      if (animationId == null) {
         return "";
      } else {
         String id = animationId.trim();
         if (id.startsWith("dragonminez:")) {
            id = id.substring("dragonminez:".length());
         }

         if (id.startsWith("animation.base.")) {
            id = id.substring("animation.base.".length());
         }

         if (id.startsWith("combat.")) {
            id = id.substring("combat.".length());
         }

         return id;
      }
   }

   public static String resolveAttack(String animationId, boolean useLeftArm) {
      ensureLoaded();
      String resolved = sanitizeAnimationId(animationId);
      if (useLeftArm && resolved.contains("right")) {
         String mirrored = toPlayableKey(resolved.replace("right", "left"));
         if (!mirrored.isEmpty()) {
            return mirrored;
         }
      }

      return toPlayableKey(resolved);
   }

   public static String resolvePose(String poseId) {
      ensureLoaded();
      String resolved = sanitizeAnimationId(poseId);
      return toPlayableKey(resolved);
   }

   public static String resolvePlayerPose(Player player) {
      WeaponAttributes main = PlayerAttackHelper.isKiWeaponActive(player)
         ? PlayerAttackHelper.getKiWeaponAttributes(player)
         : WeaponRegistry.getAttributes(player.getMainHandItem());
      return main == null ? "" : resolvePose(main.pose());
   }

   private static String toPlayableKey(String normalizedKey) {
      if (normalizedKey == null || normalizedKey.isBlank()) {
         return "";
      } else if (AVAILABLE_RAW.contains(normalizedKey)) {
         return normalizedKey;
      } else {
         String prefixed = "combat." + normalizedKey;
         return AVAILABLE_RAW.contains(prefixed) ? prefixed : "";
      }
   }
}
