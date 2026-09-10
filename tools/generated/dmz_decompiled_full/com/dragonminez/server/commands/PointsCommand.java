package com.dragonminez.server.commands;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ResourceSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class PointsCommand {
   private static final DecimalFormat POINTS_FORMAT = createPointsFormat();

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("dmzpoints")
                     .requires(source -> DMZPermissions.check(source, DMZPermissions.POINTS_INFO_SELF, DMZPermissions.POINTS_INFO_OTHERS)))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("set")
                           .requires(source -> DMZPermissions.check(source, DMZPermissions.POINTS_SET_SELF, DMZPermissions.POINTS_SET_OTHERS)))
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("amount", StringArgumentType.word())
                                 .executes(
                                    ctx -> setPoints(
                                          (CommandSourceStack)ctx.getSource(),
                                          List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()),
                                          StringArgumentType.getString(ctx, "amount")
                                       )
                                 ))
                              .then(
                                 ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                                       .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.POINTS_SET_OTHERS)))
                                    .executes(
                                       ctx -> setPoints(
                                             (CommandSourceStack)ctx.getSource(),
                                             EntityArgument.getPlayers(ctx, "targets"),
                                             StringArgumentType.getString(ctx, "amount")
                                          )
                                    )
                              )
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("add")
                        .requires(source -> DMZPermissions.check(source, DMZPermissions.POINTS_ADD_SELF, DMZPermissions.POINTS_ADD_OTHERS)))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("amount", StringArgumentType.word())
                              .executes(
                                 ctx -> addPoints(
                                       (CommandSourceStack)ctx.getSource(),
                                       List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()),
                                       StringArgumentType.getString(ctx, "amount")
                                    )
                              ))
                           .then(
                              ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                                    .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.POINTS_ADD_OTHERS)))
                                 .executes(
                                    ctx -> addPoints(
                                          (CommandSourceStack)ctx.getSource(),
                                          EntityArgument.getPlayers(ctx, "targets"),
                                          StringArgumentType.getString(ctx, "amount")
                                       )
                                 )
                           )
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("remove")
                     .requires(source -> DMZPermissions.check(source, DMZPermissions.POINTS_REMOVE_SELF, DMZPermissions.POINTS_REMOVE_OTHERS)))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("amount", StringArgumentType.word())
                           .executes(
                              ctx -> removePoints(
                                    (CommandSourceStack)ctx.getSource(),
                                    List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()),
                                    StringArgumentType.getString(ctx, "amount")
                                 )
                           ))
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                                 .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.POINTS_REMOVE_OTHERS)))
                              .executes(
                                 ctx -> removePoints(
                                       (CommandSourceStack)ctx.getSource(),
                                       EntityArgument.getPlayers(ctx, "targets"),
                                       StringArgumentType.getString(ctx, "amount")
                                    )
                              )
                        )
                  )
            )
      );
   }

   private static int setPoints(CommandSourceStack source, Collection<ServerPlayer> targets, String amountStr) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();
      Float normalizedAmount = parseIntegerLikeAmount(source, amountStr);
      if (normalizedAmount == null) {
         return 0;
      } else {
         for (ServerPlayer player : targets) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
               data.getResources().setTrainingPoints(normalizedAmount);
               NetworkHandler.sendToTrackingEntityAndSelf(new ResourceSyncS2C(player), player);
            });
         }

         String formatted = formatPoints(normalizedAmount);
         if (targets.size() == 1) {
            source.sendSuccess(
               () -> Component.translatable("command.dragonminez.points.set.success", new Object[]{formatted, targets.iterator().next().getName().getString()}),
               log
            );
         } else {
            source.sendSuccess(() -> Component.translatable("command.dragonminez.points.set.multiple", new Object[]{formatted, targets.size()}), log);
         }

         return targets.size();
      }
   }

   private static int addPoints(CommandSourceStack source, Collection<ServerPlayer> targets, String amountStr) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();
      Float normalizedAmount = parseIntegerLikeAmount(source, amountStr);
      if (normalizedAmount == null) {
         return 0;
      } else {
         for (ServerPlayer player : targets) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
               float currentPoints = data.getResources().getTrainingPoints();
               float newPoints = Math.min(Float.MAX_VALUE, currentPoints + normalizedAmount);
               data.getResources().setTrainingPoints(newPoints);
               NetworkHandler.sendToTrackingEntityAndSelf(new ResourceSyncS2C(player), player);
            });
         }

         String formatted = formatPoints(normalizedAmount);
         if (targets.size() == 1) {
            source.sendSuccess(
               () -> Component.translatable("command.dragonminez.points.add.success", new Object[]{formatted, targets.iterator().next().getName().getString()}),
               log
            );
         } else {
            source.sendSuccess(() -> Component.translatable("command.dragonminez.points.add.multiple", new Object[]{formatted, targets.size()}), log);
         }

         return targets.size();
      }
   }

   private static int removePoints(CommandSourceStack source, Collection<ServerPlayer> targets, String amountStr) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();
      Float normalizedAmount = parseIntegerLikeAmount(source, amountStr);
      if (normalizedAmount == null) {
         return 0;
      } else {
         for (ServerPlayer player : targets) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
               float currentPoints = data.getResources().getTrainingPoints();
               float newPoints = Math.max(0.0F, currentPoints - normalizedAmount);
               data.getResources().setTrainingPoints(newPoints);
               NetworkHandler.sendToTrackingEntityAndSelf(new ResourceSyncS2C(player), player);
            });
         }

         String formatted = formatPoints(normalizedAmount);
         if (targets.size() == 1) {
            source.sendSuccess(
               () -> Component.translatable(
                     "command.dragonminez.points.remove.success", new Object[]{formatted, targets.iterator().next().getName().getString()}
                  ),
               log
            );
         } else {
            source.sendSuccess(() -> Component.translatable("command.dragonminez.points.remove.multiple", new Object[]{formatted, targets.size()}), log);
         }

         return targets.size();
      }
   }

   private static Float parseIntegerLikeAmount(CommandSourceStack source, String rawAmount) {
      if (!rawAmount.matches("\\d+")) {
         source.sendFailure(Component.translatable("command.dragonminez.points.invalid_number", new Object[]{rawAmount}));
         return null;
      } else {
         BigDecimal parsed = new BigDecimal(rawAmount);
         if (parsed.compareTo(BigDecimal.valueOf(Float.MAX_VALUE)) > 0) {
            source.sendFailure(Component.translatable("command.dragonminez.points.invalid_number", new Object[]{rawAmount}));
            return null;
         } else {
            return parsed.floatValue();
         }
      }
   }

   private static String formatPoints(float value) {
      BigDecimal displayValue = BigDecimal.valueOf((double)value).setScale(0, RoundingMode.DOWN);
      return POINTS_FORMAT.format(displayValue);
   }

   private static DecimalFormat createPointsFormat() {
      DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
      symbols.setGroupingSeparator('.');
      DecimalFormat format = new DecimalFormat("#,###", symbols);
      format.setGroupingUsed(true);
      return format;
   }
}
