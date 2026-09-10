package com.dragonminez.server.commands;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class RacialSkillCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("dmzracial")
               .requires(source -> DMZPermissions.check(source, DMZPermissions.RACIAL_RESET_SELF, DMZPermissions.RACIAL_RESET_OTHERS)))
            .then(
               ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("reset")
                        .requires(source -> DMZPermissions.check(source, DMZPermissions.RACIAL_RESET_SELF, DMZPermissions.RACIAL_RESET_OTHERS)))
                     .executes(
                        context -> resetRacialSkills(
                              (CommandSourceStack)context.getSource(), List.of(((CommandSourceStack)context.getSource()).getPlayerOrException())
                           )
                     ))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                           .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.RACIAL_RESET_OTHERS)))
                        .executes(context -> resetRacialSkills((CommandSourceStack)context.getSource(), EntityArgument.getPlayers(context, "targets")))
                  )
            )
      );
   }

   private static int resetRacialSkills(CommandSourceStack source, Collection<ServerPlayer> targets) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();

      for (ServerPlayer player : targets) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            String[] statBoosts = new String[0];
            String var3 = data.getCharacter().getRace();

            statBoosts = switch (var3) {
               case "namekian" -> ConfigManager.getServerConfig().getRacialSkills().getNamekianAssimilationBoosts();
               case "majin" -> ConfigManager.getServerConfig().getRacialSkills().getMajinAbsorptionBoosts();
               case "saiyan" -> ConfigManager.getServerConfig().getRacialSkills().getSaiyanZenkaiBoosts();
               default -> statBoosts;
            };

            for (String stat : statBoosts) {
               for (int i = data.getResources().getRacialSkillCount(); i >= 0; i--) {
                  data.getBonusStats().clearBonusSplit(stat, "Absorption_");
                  data.getBonusStats().clearBonusSplit(stat, "Assimilation_");
                  data.getBonusStats().clearBonusSplit(stat, "Zenkai_");
               }
            }

            data.getCooldowns().removeCooldown("Zenkai");
            data.getResources().setRacialSkillCount(0);
            NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
         });
      }

      if (targets.size() == 1) {
         source.sendSuccess(
            () -> Component.translatable("command.dragonminez.racial.reset.success", new Object[]{targets.iterator().next().getName().getString()}), log
         );
      } else {
         source.sendSuccess(() -> Component.translatable("command.dragonminez.racial.reset.multiple", new Object[]{targets.size()}), log);
      }

      return targets.size();
   }
}
