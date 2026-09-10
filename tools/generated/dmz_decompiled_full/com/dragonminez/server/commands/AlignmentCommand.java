package com.dragonminez.server.commands;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ResourceSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class AlignmentCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("dmzalignment")
                     .requires(source -> DMZPermissions.check(source, DMZPermissions.ALIGNMENT_INFO_SELF, DMZPermissions.ALIGNMENT_INFO_OTHERS)))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("set")
                           .requires(source -> DMZPermissions.check(source, DMZPermissions.ALIGNMENT_SET_SELF, DMZPermissions.ALIGNMENT_SET_OTHERS)))
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("amount", IntegerArgumentType.integer(0, 100))
                                 .executes(
                                    ctx -> setAlignment(
                                          (CommandSourceStack)ctx.getSource(),
                                          List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()),
                                          IntegerArgumentType.getInteger(ctx, "amount")
                                       )
                                 ))
                              .then(
                                 ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                                       .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.ALIGNMENT_SET_OTHERS)))
                                    .executes(
                                       ctx -> setAlignment(
                                             (CommandSourceStack)ctx.getSource(),
                                             EntityArgument.getPlayers(ctx, "targets"),
                                             IntegerArgumentType.getInteger(ctx, "amount")
                                          )
                                    )
                              )
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("add")
                        .requires(source -> DMZPermissions.check(source, DMZPermissions.ALIGNMENT_ADD_SELF, DMZPermissions.ALIGNMENT_ADD_OTHERS)))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("amount", IntegerArgumentType.integer(1))
                              .executes(
                                 ctx -> addAlignment(
                                       (CommandSourceStack)ctx.getSource(),
                                       List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()),
                                       IntegerArgumentType.getInteger(ctx, "amount")
                                    )
                              ))
                           .then(
                              ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                                    .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.ALIGNMENT_ADD_OTHERS)))
                                 .executes(
                                    ctx -> addAlignment(
                                          (CommandSourceStack)ctx.getSource(),
                                          EntityArgument.getPlayers(ctx, "targets"),
                                          IntegerArgumentType.getInteger(ctx, "amount")
                                       )
                                 )
                           )
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("remove")
                     .requires(source -> DMZPermissions.check(source, DMZPermissions.ALIGNMENT_REMOVE_SELF, DMZPermissions.ALIGNMENT_REMOVE_OTHERS)))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("amount", IntegerArgumentType.integer(1))
                           .executes(
                              ctx -> removeAlignment(
                                    (CommandSourceStack)ctx.getSource(),
                                    List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()),
                                    IntegerArgumentType.getInteger(ctx, "amount")
                                 )
                           ))
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                                 .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.ALIGNMENT_REMOVE_OTHERS)))
                              .executes(
                                 ctx -> removeAlignment(
                                       (CommandSourceStack)ctx.getSource(),
                                       EntityArgument.getPlayers(ctx, "targets"),
                                       IntegerArgumentType.getInteger(ctx, "amount")
                                    )
                              )
                        )
                  )
            )
      );
   }

   private static int setAlignment(CommandSourceStack source, Collection<ServerPlayer> targets, int amount) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();

      for (ServerPlayer player : targets) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            data.getResources().setAlignment(amount);
            NetworkHandler.sendToTrackingEntityAndSelf(new ResourceSyncS2C(player), player);
         });
      }

      if (targets.size() == 1) {
         source.sendSuccess(
            () -> Component.translatable("command.dragonminez.alignment.set.success", new Object[]{amount, targets.iterator().next().getName().getString()}),
            log
         );
      } else {
         source.sendSuccess(() -> Component.translatable("command.dragonminez.alignment.set.multiple", new Object[]{amount, targets.size()}), log);
      }

      return targets.size();
   }

   private static int addAlignment(CommandSourceStack source, Collection<ServerPlayer> targets, int amount) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();

      for (ServerPlayer player : targets) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            data.getResources().addAlignment(amount);
            NetworkHandler.sendToTrackingEntityAndSelf(new ResourceSyncS2C(player), player);
         });
      }

      if (targets.size() == 1) {
         source.sendSuccess(
            () -> Component.translatable("command.dragonminez.alignment.add.success", new Object[]{amount, targets.iterator().next().getName().getString()}),
            log
         );
      } else {
         source.sendSuccess(() -> Component.translatable("command.dragonminez.alignment.add.multiple", new Object[]{amount, targets.size()}), log);
      }

      return targets.size();
   }

   private static int removeAlignment(CommandSourceStack source, Collection<ServerPlayer> targets, int amount) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();

      for (ServerPlayer player : targets) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            data.getResources().removeAlignment(amount);
            NetworkHandler.sendToTrackingEntityAndSelf(new ResourceSyncS2C(player), player);
         });
      }

      if (targets.size() == 1) {
         source.sendSuccess(
            () -> Component.translatable("command.dragonminez.alignment.remove.success", new Object[]{amount, targets.iterator().next().getName().getString()}),
            log
         );
      } else {
         source.sendSuccess(() -> Component.translatable("command.dragonminez.alignment.remove.multiple", new Object[]{amount, targets.size()}), log);
      }

      return targets.size();
   }
}
