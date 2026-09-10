package com.dragonminez.common.util;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.util.RandomSource;

public class BetaWhitelist {
   public static final String DEV_USERNAME = "Dev";
   public static final String WHITELIST_URL = "https://raw.githubusercontent.com/DragonMineZ/.github/refs/heads/main/allowed_betatesters.txt";
   private static final List<String> FALLBACK_LIST = Arrays.asList("Dev", "ImYuseix", "ezShokkoh");
   private static List<String> activeList = new ArrayList<>(FALLBACK_LIST);

   public static void reload() {
      LogUtil.info(Env.SERVER, "Fetching remote whitelist from GitHub...");
      CompletableFuture.runAsync(() -> {
         try {
            URL url = new URL("https://raw.githubusercontent.com/DragonMineZ/.github/refs/heads/main/allowed_betatesters.txt");
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            List<String> downloadedList = new ArrayList<>();

            String line;
            while ((line = in.readLine()) != null) {
               String name = line.trim();
               if (!name.isEmpty()) {
                  downloadedList.add(name.toLowerCase());
               }
            }

            in.close();
            if (!downloadedList.isEmpty()) {
               activeList = downloadedList;
               LogUtil.info(Env.SERVER, "Whitelist updated! Loaded {} users.", activeList.size());
            }
         } catch (Exception var5) {
            LogUtil.error(Env.SERVER, "Failed to fetch remote whitelist. Using fallback list. Error: " + var5.getMessage());
         }
      });
   }

   public static boolean isAllowed(String username) {
      return activeList.contains(username.toLowerCase());
   }

   public static List<String> getSkinCandidates() {
      List<String> candidates = new ArrayList<>(activeList.size());

      for (String name : activeList) {
         if (!name.equalsIgnoreCase("Dev")) {
            candidates.add(name);
         }
      }

      return candidates;
   }

   public static String getRandomBetatester(RandomSource random) {
      List<String> candidates = getSkinCandidates();
      return candidates.isEmpty() ? "" : candidates.get(random.nextInt(candidates.size()));
   }
}
