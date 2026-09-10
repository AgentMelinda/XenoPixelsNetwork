package com.dragonminez.server.commands;

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

public class ReviveCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("dmzrevive")
                  .requires(source -> DMZPermissions.check(source, DMZPermissions.REVIVE_SELF, DMZPermissions.REVIVE_OTHERS)))
               .executes(ctx -> revivePlayers((CommandSourceStack)ctx.getSource(), List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()))))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                     .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.REVIVE_OTHERS)))
                  .executes(ctx -> revivePlayers((CommandSourceStack)ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))
            )
      );
   }

   private static int revivePlayers(CommandSourceStack source, Collection<ServerPlayer> targets) {
      int successCount = 0;

      for (ServerPlayer player : targets) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            data.getCooldowns().removeCooldown("Revive");
            if (!data.getStatus().isAlive()) {
               data.getStatus().setAlive(true);
               player.setHealth(player.getMaxHealth());
               player.sendSystemMessage(Component.translatable("command.dragonminez.revive.target"));
            }

            NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
         });
         successCount++;
      }

      if (targets.size() == 1) {
         ServerPlayer target = targets.iterator().next();
         if (target == source.getPlayer()) {
            source.sendSuccess(() -> Component.translatable("command.dragonminez.revive.success.self"), true);
         } else {
            source.sendSuccess(() -> Component.translatable("command.dragonminez.revive.success.single", new Object[]{target.getName()}), true);
         }
      } else {
         int finalSuccessCount = successCount;
         source.sendSuccess(() -> Component.translatable("command.dragonminez.revive.success.multiple", new Object[]{finalSuccessCount}), true);
      }

      return successCount;
   }
}
