package com.dragonminez.client.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

public final class LocalizationUtil {
   private static final Pattern OBJECTIVE_SUFFIX = Pattern.compile(".*\\.obj(\\d+)$");
   private static final Pattern SAGA_QUEST_ID = Pattern.compile("([a-z_]+?)(\\d+)$");

   private LocalizationUtil() {
   }

   public static Component localizedOrReadable(String raw) {
      if (raw != null && !raw.isBlank()) {
         return Language.getInstance().has(raw) ? Component.translatable(raw) : Component.literal(humanize(raw));
      } else {
         return Component.empty();
      }
   }

   public static String localizedOrReadableText(String raw) {
      return localizedOrReadable(raw).getString();
   }

   private static String humanize(String key) {
      Matcher objectiveMatcher = OBJECTIVE_SUFFIX.matcher(key);
      if (objectiveMatcher.matches()) {
         return "Objective " + objectiveMatcher.group(1);
      } else if (!key.endsWith(".name") && !key.endsWith(".desc")) {
         return key;
      } else {
         String base = key.substring(0, key.lastIndexOf(46));
         String token = base.substring(base.lastIndexOf(46) + 1);
         if (key.contains("dmz.quest.")) {
            Matcher sagaMatcher = SAGA_QUEST_ID.matcher(token);
            if (sagaMatcher.matches()) {
               String saga = titleCase(sagaMatcher.group(1).replace('_', ' '));
               String questNo = sagaMatcher.group(2);
               return key.endsWith(".name") ? saga + " Quest " + questNo : "Complete " + saga + " quest " + questNo + ".";
            }
         }

         String pretty = titleCase(token.replace('_', ' '));
         return key.endsWith(".name") ? pretty : "Complete this quest step.";
      }
   }

   private static String titleCase(String raw) {
      if (raw.isBlank()) {
         return raw;
      } else {
         String[] words = raw.toLowerCase(Locale.ROOT).split("\\s+");
         StringBuilder sb = new StringBuilder();

         for (String word : words) {
            if (!word.isBlank()) {
               if (!sb.isEmpty()) {
                  sb.append(' ');
               }

               sb.append(Character.toUpperCase(word.charAt(0)));
               if (word.length() > 1) {
                  sb.append(word.substring(1));
               }
            }
         }

         return sb.toString();
      }
   }
}
