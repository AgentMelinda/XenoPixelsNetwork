package com.dragonminez.server.commands;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.server.util.FusionLogic;
import com.dragonminez.server.util.MutantManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class EffectsCommand {
   private static final SuggestionProvider<CommandSourceStack> EFFECT_SUGGESTIONS = (ctx, builder) -> SharedSuggestionProvider.suggest(
         List.of("mightfruit", "majin", "mutant"), builder
      );

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("dmzeffect")
                     .requires(source -> DMZPermissions.check(source, DMZPermissions.EFFECTS_LIST_SELF, DMZPermissions.EFFECTS_LIST_OTHERS)))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("give")
                           .requires(source -> DMZPermissions.check(source, DMZPermissions.EFFECTS_GIVE_SELF, DMZPermissions.EFFECTS_GIVE_OTHERS)))
                        .then(
                           Commands.argument("effect", StringArgumentType.string())
                              .suggests(EFFECT_SUGGESTIONS)
                              .then(
                                 ((RequiredArgumentBuilder)Commands.argument("duration", IntegerArgumentType.integer(-1))
                                       .executes(
                                          ctx -> giveEffect(
                                                (CommandSourceStack)ctx.getSource(),
                                                List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()),
                                                StringArgumentType.getString(ctx, "effect"),
                                                IntegerArgumentType.getInteger(ctx, "duration")
                                             )
                                       ))
                                    .then(
                                       ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                                             .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.EFFECTS_GIVE_OTHERS)))
                                          .executes(
                                             ctx -> giveEffect(
                                                   (CommandSourceStack)ctx.getSource(),
                                                   EntityArgument.getPlayers(ctx, "targets"),
                                                   StringArgumentType.getString(ctx, "effect"),
                                                   IntegerArgumentType.getInteger(ctx, "duration")
                                                )
                                          )
                                    )
                              )
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("remove")
                        .requires(source -> DMZPermissions.check(source, DMZPermissions.EFFECTS_REMOVE_SELF, DMZPermissions.EFFECTS_REMOVE_OTHERS)))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("effect", StringArgumentType.string())
                              .suggests(EFFECT_SUGGESTIONS)
                              .executes(
                                 ctx -> removeEffect(
                                       (CommandSourceStack)ctx.getSource(),
                                       List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()),
                                       StringArgumentType.getString(ctx, "effect")
                                    )
                              ))
                           .then(
                              ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                                    .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.EFFECTS_REMOVE_OTHERS)))
                                 .executes(
                                    ctx -> removeEffect(
                                          (CommandSourceStack)ctx.getSource(),
                                          EntityArgument.getPlayers(ctx, "targets"),
                                          StringArgumentType.getString(ctx, "effect")
                                       )
                                 )
                           )
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("clear")
                        .requires(source -> DMZPermissions.check(source, DMZPermissions.EFFECTS_CLEAR_SELF, DMZPermissions.EFFECTS_CLEAR_OTHERS)))
                     .executes(ctx -> clearEffects((CommandSourceStack)ctx.getSource(), List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()))))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                           .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.EFFECTS_CLEAR_OTHERS)))
                        .executes(ctx -> clearEffects((CommandSourceStack)ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))
                  )
            )
      );
   }

   private static int giveEffect(CommandSourceStack source, Collection<ServerPlayer> targets, String effectName, int duration) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();
      double power = getEffectPower(effectName);
      if (power == 0.0) {
         source.sendFailure(Component.translatable("command.dragonminez.effects.unknown_effect", new Object[]{effectName}));
         return 0;
      } else {
         int durationInTicks = duration == -1 ? -1 : duration * 20;
         boolean isMutant = effectName.equalsIgnoreCase("mutant");

         for (ServerPlayer player : targets) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
               if (isMutant) {
                  MutantManager.grant(player, data);
               } else {
                  data.getEffects().addEffect(effectName, power, durationInTicks);
                  NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
               }
            });
         }

         String durationText = duration == -1 ? "permanent" : duration + " seconds";
         if (targets.size() == 1) {
            source.sendSuccess(
               () -> Component.translatable(
                     "command.dragonminez.effects.give_success", new Object[]{effectName, power, targets.iterator().next().getName().getString(), durationText}
                  ),
               log
            );
         } else {
            source.sendSuccess(
               () -> Component.translatable("command.dragonminez.effects.give_multiple", new Object[]{effectName, power, targets.size(), durationText}), log
            );
         }

         return targets.size();
      }
   }

   private static int removeEffect(CommandSourceStack source, Collection<ServerPlayer> targets, String effectName) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();
      boolean isMutant = effectName.equalsIgnoreCase("mutant");

      for (ServerPlayer player : targets) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            if (data.getEffects().hasEffect(effectName)) {
               if (isMutant) {
                  MutantManager.revoke(player, data);
               } else {
                  data.getEffects().removeEffect(effectName);
                  NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
               }
            }
         });
      }

      if (targets.size() == 1) {
         source.sendSuccess(
            () -> Component.translatable(
                  "command.dragonminez.effects.remove_success", new Object[]{effectName, targets.iterator().next().getName().getString()}
               ),
            log
         );
      } else {
         source.sendSuccess(() -> Component.translatable("command.dragonminez.effects.remove_multiple", new Object[]{effectName, targets.size()}), log);
      }

      return targets.size();
   }

   private static int clearEffects(CommandSourceStack source, Collection<ServerPlayer> targets) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();

      for (ServerPlayer player : targets) {
         StatsProvider.get(StatsCapability.INSTANCE, player)
            .ifPresent(
               data -> {
                  if (data.getEffects().hasEffect("mutant")) {
                     MutantManager.revoke(player, data);
                  }

                  data.getEffects().clear();
                  UUID partnerUUID = data.getStatus().getFusionPartnerUUID() != null
                     ? data.getStatus().getFusionPartnerUUID()
                     : data.getStatus().getPotaraPartnerUUID();
                  if (data.getStatus().isFused() || data.getStatus().getFusionPartnerUUID() != null) {
                     FusionLogic.endFusion(player, data, false);
                  }

                  clearPotaraPose(data);
                  FusionLogic.breakPothala(player);
                  clearFusionCooldown(player, data);
                  if (partnerUUID != null) {
                     ServerPlayer partner = player.getServer().getPlayerList().getPlayer(partnerUUID);
                     if (partner != null) {
                        StatsProvider.get(StatsCapability.INSTANCE, partner).ifPresent(partnerData -> {
                           clearPotaraPose(partnerData);
                           FusionLogic.breakPothala(partner);
                           clearFusionCooldown(partner, partnerData);
                           NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(partner), partner);
                        });
                     }
                  }

                  NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
               }
            );
      }

      if (targets.size() == 1) {
         source.sendSuccess(
            () -> Component.translatable("command.dragonminez.effects.clear_success", new Object[]{targets.iterator().next().getName().getString()}), log
         );
      } else {
         source.sendSuccess(() -> Component.translatable("command.dragonminez.effects.clear_multiple", new Object[]{targets.size()}), log);
      }

      return targets.size();
   }

   private static void clearFusionCooldown(ServerPlayer player, StatsData data) {
      data.getCooldowns().removeCooldown("FusionCooldown");
      if (player.hasEffect(MainEffects.FUSION_CD)) {
         player.removeEffect(MainEffects.FUSION_CD);
      }
   }

   private static void clearPotaraPose(StatsData data) {
      data.getStatus().setPotaraPoseTimer(0);
      data.getStatus().setPotaraPartnerUUID(null);
      data.getStatus().setPotaraLeader(false);
   }

   private static double getEffectPower(String effectName) {
      GeneralServerConfig serverConfig = ConfigManager.getServerConfig();
      if (serverConfig == null) {
         return 0.0;
      } else {
         String var2 = effectName.toLowerCase();

         return switch (var2) {
            case "mightfruit" -> serverConfig.getGameplay().getMightFruitPower();
            case "majin" -> serverConfig.getGameplay().getMajinPower();
            case "mutant" -> 1.0;
            default -> 0.0;
         };
      }
   }
}
