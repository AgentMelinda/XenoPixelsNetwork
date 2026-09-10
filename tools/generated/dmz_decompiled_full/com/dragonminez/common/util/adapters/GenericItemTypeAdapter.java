package com.dragonminez.common.util.adapters;

import com.dragonminez.common.util.types.items.EnchantedBookDTO;
import com.dragonminez.common.util.types.items.EnchantedItemDTO;
import com.dragonminez.common.util.types.items.GenericItemDTO;
import com.dragonminez.common.util.types.items.LingeringPotionDTO;
import com.dragonminez.common.util.types.items.PotionDTO;
import com.dragonminez.common.util.types.items.SplashPotionDTO;
import com.dragonminez.common.util.types.items.TippedArrowDTO;
import com.dragonminez.common.util.types.items.TrimmedArmorDTO;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class GenericItemTypeAdapter implements JsonSerializer<GenericItemDTO>, JsonDeserializer<GenericItemDTO> {
   private static final String TYPE = "itemType";

   public JsonElement serialize(GenericItemDTO src, Type typeOfSrc, JsonSerializationContext context) {
      return new GsonBuilder().setPrettyPrinting().create().toJsonTree(src, src.getClass());
   }

   public GenericItemDTO deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      JsonObject jsonObject = json.getAsJsonObject();
      String type = jsonObject.get("itemType").getAsString();
      Class<? extends GenericItemDTO> target = classForType(type);
      if (target == null) {
         throw new JsonParseException("Unknown item type: " + type);
      } else {
         return (GenericItemDTO)new GsonBuilder().create().fromJson(json, target);
      }
   }

   public static Class<? extends GenericItemDTO> classForType(String type) {
      if (type == null) {
         return null;
      } else {
         return switch (type) {
            case "enchanted_book" -> EnchantedBookDTO.class;
            case "enchanted_item" -> EnchantedItemDTO.class;
            case "generic_item" -> GenericItemDTO.class;
            case "lingering_potion" -> LingeringPotionDTO.class;
            case "potion" -> PotionDTO.class;
            case "splash_potion" -> SplashPotionDTO.class;
            case "tipped_arrow" -> TippedArrowDTO.class;
            case "trimmed_armor" -> TrimmedArmorDTO.class;
            default -> null;
         };
      }
   }
}
