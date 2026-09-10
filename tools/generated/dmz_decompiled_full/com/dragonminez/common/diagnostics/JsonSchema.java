package com.dragonminez.common.diagnostics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class JsonSchema {
   private static final String CONFIG_PACKAGE = "com.dragonminez.common.config";

   private JsonSchema() {
   }

   public static void check(String source, String file, String path, JsonObject json, Class<?> type) {
      if (json != null && type != null && type.getName().startsWith("com.dragonminez")) {
         Map<String, Field> fields = fieldsOf(type);

         for (Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (!isComment(key)) {
               Field field = fields.get(key);
               if (field == null) {
                  JsonLoadReport.error(source, file, "unknown field '" + key + "'" + at(path) + " (ignored)");
               } else {
                  recurse(source, file, path.isEmpty() ? key : path + "." + key, entry.getValue(), field.getGenericType());
               }
            }
         }
      }
   }

   private static void recurse(String source, String file, String path, JsonElement value, Type type) {
      if (value != null && !value.isJsonNull()) {
         Class<?> raw = rawClass(type);
         if (value.isJsonObject()) {
            if (isConfigType(raw)) {
               check(source, file, path, value.getAsJsonObject(), raw);
            } else if (raw != null && Map.class.isAssignableFrom(raw)) {
               Class<?> valueType = rawClass(typeArgument(type, 1));
               if (isConfigType(valueType)) {
                  for (Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                     if (entry.getValue().isJsonObject()) {
                        check(source, file, path + "." + entry.getKey(), entry.getValue().getAsJsonObject(), valueType);
                     }
                  }
               }
            }
         } else if (value.isJsonArray()) {
            Class<?> elementType = raw != null && Collection.class.isAssignableFrom(raw) ? rawClass(typeArgument(type, 0)) : null;
            if (isConfigType(elementType)) {
               JsonArray array = value.getAsJsonArray();

               for (int i = 0; i < array.size(); i++) {
                  if (array.get(i).isJsonObject()) {
                     check(source, file, path + "[" + i + "]", array.get(i).getAsJsonObject(), elementType);
                  }
               }
            }
         }
      }
   }

   private static boolean isConfigType(Class<?> type) {
      return type != null && type.getName().startsWith("com.dragonminez.common.config") && !hasCustomAdapter(type);
   }

   private static boolean hasCustomAdapter(Class<?> type) {
      for (Class<?> nested : type.getDeclaredClasses()) {
         if (nested.getSimpleName().equals("Adapter")) {
            return true;
         }
      }

      return false;
   }

   private static Map<String, Field> fieldsOf(Class<?> type) {
      Map<String, Field> fields = new HashMap<>();

      for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
         for (Field field : current.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (!Modifier.isStatic(mods) && !Modifier.isTransient(mods) && !field.isSynthetic()) {
               SerializedName serialized = field.getAnnotation(SerializedName.class);
               if (serialized != null) {
                  fields.putIfAbsent(serialized.value(), field);

                  for (String alternate : serialized.alternate()) {
                     fields.putIfAbsent(alternate, field);
                  }
               } else {
                  fields.putIfAbsent(field.getName(), field);
               }
            }
         }
      }

      return fields;
   }

   private static Class<?> rawClass(Type type) {
      if (type instanceof Class) {
         return (Class<?>)type;
      } else {
         if (type instanceof ParameterizedType p) {
            Type var3 = p.getRawType();
            if (var3 instanceof Class) {
               return (Class<?>)var3;
            }
         }

         return null;
      }
   }

   private static Type typeArgument(Type type, int index) {
      if (type instanceof ParameterizedType p) {
         Type[] args = p.getActualTypeArguments();
         if (index < args.length) {
            return args[index];
         }
      }

      return null;
   }

   private static boolean isComment(String key) {
      return key.isEmpty() || key.charAt(0) == '_' || key.charAt(0) == '$' || key.startsWith("//");
   }

   private static String at(String path) {
      return path != null && !path.isEmpty() ? " in " + path : "";
   }
}
