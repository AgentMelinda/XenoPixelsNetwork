package com.dragonminez.common.spacepod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public record SpacePodDestinationDefinition(
   String id,
   String name,
   boolean translate,
   String dimension,
   @Nullable Integer iconIndex,
   @Nullable String iconTexture,
   @Nullable Double x,
   @Nullable Double y,
   @Nullable Double z,
   boolean showWhenLocked,
   SpacePodUnlockExpression unlockRules
) {
   public static SpacePodDestinationDefinition fromJson(JsonObject object) {
      String id = getRequiredString(object, "id");
      String name = getRequiredString(object, "name");
      boolean translate = !object.has("translate") || object.get("translate").getAsBoolean();
      String dimension = getRequiredString(object, "dimension");
      Integer iconIndex = object.has("icon_index") && !object.get("icon_index").isJsonNull() ? object.get("icon_index").getAsInt() : null;
      String iconTexture = object.has("icon_texture") && !object.get("icon_texture").isJsonNull() ? object.get("icon_texture").getAsString() : null;
      if (iconIndex != null || iconTexture != null && !iconTexture.isBlank()) {
         Double x = getOptionalDouble(object, "x");
         Double y = getOptionalDouble(object, "y");
         Double z = getOptionalDouble(object, "z");
         validateCoordinates(id, x, y, z);
         boolean showWhenLocked = !object.has("show_when_locked") || object.get("show_when_locked").getAsBoolean();
         JsonElement unlockElement = object.get("unlock_rules");
         SpacePodUnlockExpression unlockRules = SpacePodUnlockExpression.fromJson(unlockElement);
         return new SpacePodDestinationDefinition(id, name, translate, dimension, iconIndex, iconTexture, x, y, z, showWhenLocked, unlockRules);
      } else {
         throw new IllegalArgumentException("Destination '" + id + "' must define icon_index or icon_texture");
      }
   }

   public Vec3 resolvePosition(Vec3 playerPosition) {
      if (this.y != null && this.x == null && this.z == null) {
         return new Vec3(playerPosition.x, this.y, playerPosition.z);
      } else {
         return this.x != null && this.y != null && this.z != null ? new Vec3(this.x, this.y, this.z) : playerPosition;
      }
   }

   private static void validateCoordinates(String id, @Nullable Double x, @Nullable Double y, @Nullable Double z) {
      boolean hasX = x != null;
      boolean hasY = y != null;
      boolean hasZ = z != null;
      if ((hasX || hasZ) && (!hasX || !hasY || !hasZ)) {
         throw new IllegalArgumentException("Destination '" + id + "' must define x, y and z together when x or z is provided");
      }
   }

   @Nullable
   private static Double getOptionalDouble(JsonObject object, String key) {
      return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsDouble() : null;
   }

   private static String getRequiredString(JsonObject object, String key) {
      if (object.has(key) && !object.get(key).isJsonNull()) {
         return object.get(key).getAsString();
      } else {
         throw new IllegalArgumentException("Missing required field '" + key + "'");
      }
   }
}
