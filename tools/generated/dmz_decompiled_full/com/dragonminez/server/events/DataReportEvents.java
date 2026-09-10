package com.dragonminez.server.events;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.diagnostics.JsonLoadReport;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;

@EventBusSubscriber(
   modid = "dragonminez"
)
public class DataReportEvents {
   private static final int MAX_LINES = 12;

   @SubscribeEvent
   public static void onPlayerLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         if (!JsonLoadReport.isEmpty()) {
            if (ConfigManager.getServerConfig().getDeveloper().isReportJsonProblemsInChat()) {
               List<JsonLoadReport.Entry> entries = JsonLoadReport.entries();
               long errors = JsonLoadReport.count(JsonLoadReport.Kind.ERROR);
               long updates = JsonLoadReport.count(JsonLoadReport.Kind.UPDATE);
               player.sendSystemMessage(
                  Component.literal("§6§l[DragonMineZ]§r §eJSON load report — §c" + errors + " problem(s)§e, " + updates + " auto-update(s):")
               );
               int shown = 0;
               shown = appendLines(player, entries, JsonLoadReport.Kind.ERROR, "§c [!] §f", shown);
               shown = appendLines(player, entries, JsonLoadReport.Kind.UPDATE, "§e [~] §f", shown);
               if (entries.size() > shown) {
                  player.sendSystemMessage(Component.literal("§7 …and " + (entries.size() - shown) + " more — see the server log."));
               }

               player.sendSystemMessage(
                  Component.literal("§7Fix the file(s) and run §f/dmzreload§7. Silence this with §fdeveloper.reportJsonProblemsInChat = false§7.")
               );
            }
         }
      }
   }

   private static int appendLines(ServerPlayer player, List<JsonLoadReport.Entry> entries, JsonLoadReport.Kind kind, String prefix, int shown) {
      for (JsonLoadReport.Entry entry : entries) {
         if (entry.kind() == kind) {
            if (shown >= 12) {
               break;
            }

            player.sendSystemMessage(Component.literal(prefix + entry.file() + " §7— " + entry.message()));
            shown++;
         }
      }

      return shown;
   }
}
