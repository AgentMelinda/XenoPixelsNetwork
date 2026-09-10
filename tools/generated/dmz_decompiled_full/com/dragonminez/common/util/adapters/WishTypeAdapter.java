package com.dragonminez.common.util.adapters;

import com.dragonminez.common.wish.Wish;
import com.dragonminez.common.wish.wishes.ChangeDifficultyWish;
import com.dragonminez.common.wish.wishes.CommandWish;
import com.dragonminez.common.wish.wishes.ItemListWish;
import com.dragonminez.common.wish.wishes.ItemWish;
import com.dragonminez.common.wish.wishes.MultiItemWish;
import com.dragonminez.common.wish.wishes.PassiveResetWish;
import com.dragonminez.common.wish.wishes.ReCustomizeWish;
import com.dragonminez.common.wish.wishes.RelocateStatsWish;
import com.dragonminez.common.wish.wishes.ResetStoryWish;
import com.dragonminez.common.wish.wishes.SkillWish;
import com.dragonminez.common.wish.wishes.TPSWish;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class WishTypeAdapter implements JsonSerializer<Wish>, JsonDeserializer<Wish> {
   private static final String TYPE = "type";

   public JsonElement serialize(Wish src, Type typeOfSrc, JsonSerializationContext context) {
      return new GsonBuilder().setPrettyPrinting().create().toJsonTree(src, src.getClass());
   }

   public Wish deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      JsonObject jsonObject = json.getAsJsonObject();
      String type = jsonObject.get("type").getAsString();
      Class<? extends Wish> target = classForType(type);
      if (target == null) {
         throw new JsonParseException("Unknown wish type: " + type);
      } else {
         return (Wish)new GsonBuilder().create().fromJson(json, target);
      }
   }

   public static Class<? extends Wish> classForType(String type) {
      if (type == null) {
         return null;
      } else {
         return switch (type) {
            case "item" -> ItemWish.class;
            case "command" -> CommandWish.class;
            case "tps" -> TPSWish.class;
            case "multi_wish" -> MultiItemWish.class;
            case "skill" -> SkillWish.class;
            case "passivereset" -> PassiveResetWish.class;
            case "recustomize" -> ReCustomizeWish.class;
            case "relocatestats" -> RelocateStatsWish.class;
            case "changedifficulty" -> ChangeDifficultyWish.class;
            case "resetstory" -> ResetStoryWish.class;
            case "item_list_wish" -> ItemListWish.class;
            default -> null;
         };
      }
   }
}
