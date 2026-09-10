package com.dragonminez.server.commands;

import com.dragonminez.server.world.raid.Raid;
import com.dragonminez.server.world.raid.RaidManager;
import com.dragonminez.server.world.raid.RaidTypes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class RaidCommand {
   private static final double LOOKUP_RANGE = 128.0;

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("dmzraid")
                     .requires(source -> source.hasPermission(2)))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("start").executes(ctx -> start(ctx, "saiyan_assault")))
                        .then(
                           Commands.argument("type", StringArgumentType.word())
                              .suggests((c, b) -> SharedSuggestionProvider.suggest(RaidTypes.ids(), b))
                              .executes(ctx -> start(ctx, StringArgumentType.getString(ctx, "type")))
                        )
                  ))
               .then(Commands.literal("stop").executes(RaidCommand::stop)))
            .then(Commands.literal("info").executes(RaidCommand::info))
      );
   }

   private static int start(CommandContext<CommandSourceStack> ctx, String type) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      ServerLevel level = player.serverLevel();
      Raid raid = RaidManager.startRaid(level, player.blockPosition(), player, type);
      if (raid == null) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.translatable("command.dragonminez.raid.start.fail"));
         return 0;
      } else {
         ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.translatable("command.dragonminez.raid.start.success", new Object[]{type}), true);
         return 1;
      }
   }

   private static int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      boolean cancelled = RaidManager.cancelNearbyRaid(player.serverLevel(), player.blockPosition(), 128.0);
      if (!cancelled) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.translatable("command.dragonminez.raid.none"));
         return 0;
      } else {
         ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.translatable("command.dragonminez.raid.stop.success"), true);
         return 1;
      }
   }

   private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      Raid raid = RaidManager.findNearbyRaid(player.serverLevel(), player.blockPosition(), 128.0);
      if (raid == null) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.translatable("command.dragonminez.raid.none"));
         return 0;
      } else {
         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(
               () -> Component.literal(
                     "Raid "
                        + raid.getRaidId()
                        + " | status="
                        + raid.getStatus()
                        + " | wave="
                        + raid.getCurrentWave()
                        + " | participants="
                        + raid.getParticipants().size()
                        + " | center="
                        + raid.getCenter().toShortString()
                  ),
               false
            );
         return 1;
      }
   }
}
