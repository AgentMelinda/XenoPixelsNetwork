package com.dragonminez.common.combat.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternMatching {
   public static boolean matches(String subject, String nullableRegex) {
      if (subject == null) {
         return false;
      } else if (nullableRegex != null && !nullableRegex.isEmpty()) {
         Pattern pattern = Pattern.compile(nullableRegex, 2);
         Matcher matcher = pattern.matcher(subject);
         return matcher.find();
      } else {
         return false;
      }
   }
}
