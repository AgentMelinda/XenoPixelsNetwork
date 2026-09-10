package com.dragonminez.client.util;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.client.gui.quest.StoryToast;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.VersionChecker;
import net.neoforged.fml.VersionChecker.CheckResult;
import net.neoforged.fml.VersionChecker.Status;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.ScreenEvent.Init.Post;
import net.neoforged.neoforgespi.language.IModInfo;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME,
   value = {Dist.CLIENT}
)
public class VersionCheckerUtil {
   private static final int MAX_PENDING_POLLS = 40;
   private static final long PENDING_POLL_DELAY_MS = 250L;
   private static volatile boolean checkStarted = false;
   private static volatile boolean updateToastShown = false;

   @SubscribeEvent
   public static void onTitleScreenInit(Post event) {
      if (!checkStarted && !updateToastShown) {
         if (event.getScreen() instanceof TitleScreen) {
            startCheck();
         }
      }
   }

   private static synchronized void startCheck() {
      if (!checkStarted) {
         checkStarted = true;
         Optional<IModInfo> modInfoOpt = ModList.get().getModContainerById("dragonminez").map(ModContainer::getModInfo);
         if (modInfoOpt.isEmpty()) {
            LogUtil.warn(Env.CLIENT, "[DMZ-VERSION] Could not locate mod metadata for update check.");
         } else {
            IModInfo modInfo = modInfoOpt.get();
            String currentVersion = modInfo.getVersion().toString();
            if (isPreReleaseBuild(currentVersion)) {
               LogUtil.info(Env.CLIENT, "[DMZ-VERSION] Skipping update notification for pre-release build: {}", currentVersion);
            } else {
               CompletableFuture.<CheckResult>supplyAsync(() -> awaitForgeResult(modInfo))
                  .thenAccept(result -> Minecraft.getInstance().execute(() -> showOutdatedToastIfNeeded(currentVersion, result)))
                  .exceptionally(exception -> {
                     LogUtil.warn(Env.CLIENT, "[DMZ-VERSION] Update check failed: {}", exception.getMessage());
                     return null;
                  });
            }
         }
      }
   }

   private static CheckResult awaitForgeResult(IModInfo modInfo) {
      CheckResult result = VersionChecker.getResult(modInfo);

      for (int attempt = 0; attempt < 40 && result.status() == Status.PENDING; attempt++) {
         try {
            Thread.sleep(250L);
         } catch (InterruptedException var4) {
            Thread.currentThread().interrupt();
            break;
         }

         result = VersionChecker.getResult(modInfo);
      }

      return result;
   }

   private static void showOutdatedToastIfNeeded(String currentVersion, CheckResult result) {
      if (!updateToastShown) {
         if (result != null) {
            Status status = result.status();
            if (status == Status.PENDING) {
               LogUtil.warn(Env.CLIENT, "[DMZ-VERSION] Update check is still pending after timeout.");
            } else if (status == Status.FAILED) {
               LogUtil.warn(Env.CLIENT, "[DMZ-VERSION] Forge update check failed to retrieve metadata.");
            } else if (status == Status.OUTDATED) {
               String targetVersion = result.target() == null ? "unknown" : result.target().toString();
               LogUtil.info(Env.CLIENT, "[DMZ-VERSION] Update available: {} -> {}", currentVersion, targetVersion);
               Minecraft.getInstance()
                  .getToasts()
                  .addToast(
                     new StoryToast(
                        Component.translatable("toast.dragonminez.update.title"),
                        Component.translatable("toast.dragonminez.update.desc", new Object[]{currentVersion, targetVersion}),
                        StoryToast.Tone.INFO
                     )
                  );
               updateToastShown = true;
            }
         }
      }
   }

   private static boolean isPreReleaseBuild(String currentVersion) {
      String normalized = currentVersion.toLowerCase(Locale.ROOT);
      return normalized.contains("alpha") || normalized.contains("beta");
   }
}
