package com.dragonminez.common.util.adapters;

import com.dragonminez.common.quest.QuestObjective;
import com.dragonminez.common.quest.objectives.BiomeObjective;
import com.dragonminez.common.quest.objectives.CoordsObjective;
import com.dragonminez.common.quest.objectives.InteractObjective;
import com.dragonminez.common.quest.objectives.ItemObjective;
import com.dragonminez.common.quest.objectives.KillObjective;
import com.dragonminez.common.quest.objectives.SkillObjective;
import com.dragonminez.common.quest.objectives.StructureObjective;
import com.dragonminez.common.quest.objectives.TalkToObjective;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class QuestObjectiveTypeAdapter implements JsonSerializer<QuestObjective>, JsonDeserializer<QuestObjective> {
   public QuestObjective deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      JsonObject jsonObject = json.getAsJsonObject();
      String type = jsonObject.get("type").getAsString();
      String var6 = type.toUpperCase();
      switch (var6) {
         case "ITEM":
            return (QuestObjective)context.deserialize(jsonObject, ItemObjective.class);
         case "KILL":
            return (QuestObjective)context.deserialize(jsonObject, KillObjective.class);
         case "INTERACT":
            return (QuestObjective)context.deserialize(jsonObject, InteractObjective.class);
         case "STRUCTURE":
            return (QuestObjective)context.deserialize(jsonObject, StructureObjective.class);
         case "BIOME":
            return (QuestObjective)context.deserialize(jsonObject, BiomeObjective.class);
         case "COORDS":
            return (QuestObjective)context.deserialize(jsonObject, CoordsObjective.class);
         case "TALK_TO":
            return (QuestObjective)context.deserialize(jsonObject, TalkToObjective.class);
         case "SKILL":
            return (QuestObjective)context.deserialize(jsonObject, SkillObjective.class);
         default:
            throw new JsonParseException("Unknown objective type: " + type);
      }
   }

   public JsonElement serialize(QuestObjective src, Type typeOfSrc, JsonSerializationContext context) {
      return context.serialize(src, src.getClass());
   }
}
