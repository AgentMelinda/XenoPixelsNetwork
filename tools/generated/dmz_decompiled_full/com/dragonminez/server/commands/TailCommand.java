package com.dragonminez.server.commands;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.AppearanceSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class TailCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("dmztail")
                  .requires(source -> DMZPermissions.check(source, DMZPermissions.TAIL_SELF, DMZPermissions.TAIL_OTHERS)))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("cut")
                        .executes(
                           ctx -> setTail((CommandSourceStack)ctx.getSource(), List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()), false)
                        ))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                              .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.TAIL_OTHERS)))
                           .executes(ctx -> setTail((CommandSourceStack)ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), false))
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("grow")
                     .executes(ctx -> setTail((CommandSourceStack)ctx.getSource(), List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()), true)))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                           .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.TAIL_OTHERS)))
                        .executes(ctx -> setTail((CommandSourceStack)ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), true))
                  )
            )
      );
   }

   private static boolean canHaveTail(StatsData data) {
      String race = data.getCharacter().getRaceName();
      if ("saiyan".equalsIgnoreCase(race)) {
         return true;
      } else {
         RaceCharacterConfig config = ConfigManager.getRaceCharacter(race);
         return config != null && Boolean.TRUE.equals(config.getHasSaiyanTail());
      }
   }

   private static int setTail(CommandSourceStack source, Collection<ServerPlayer> targets, boolean grow) {
      String action = grow ? "grow" : "cut";
      if (targets.size() == 1) {
         ServerPlayer player = targets.iterator().next();
         StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
         if (data == null) {
            return 0;
         } else if (!canHaveTail(data)) {
            source.sendFailure(Component.translatable("command.dragonminez.tail.no_race", new Object[]{player.getName().getString()}));
            return 0;
         } else {
            Character character = data.getCharacter();
            if (character.isHasSaiyanTail() == grow) {
               source.sendFailure(Component.translatable("command.dragonminez.tail.already_" + action, new Object[]{player.getName().getString()}));
               return 0;
            } else {
               applyTail(player, character, grow);
               if (player == source.getEntity()) {
                  source.sendSuccess(() -> Component.translatable("command.dragonminez.tail." + action + ".self"), false);
               } else {
                  source.sendSuccess(
                     () -> Component.translatable("command.dragonminez.tail." + action + ".other", new Object[]{player.getName().getString()}), true
                  );
               }

               return 1;
            }
         }
      } else {
         int success = 0;

         for (ServerPlayer player : targets) {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            if (data != null && canHaveTail(data)) {
               Character character = data.getCharacter();
               if (character.isHasSaiyanTail() != grow) {
                  applyTail(player, character, grow);
                  success++;
               }
            }
         }

         int finalSuccess = success;
         source.sendSuccess(() -> Component.translatable("command.dragonminez.tail." + action + ".multiple", new Object[]{finalSuccess}), true);
         return success;
      }
   }

   private static void applyTail(ServerPlayer player, Character character, boolean grow) {
      character.setHasSaiyanTail(grow);
      NetworkHandler.sendToTrackingEntityAndSelf(new AppearanceSyncS2C(player), player);
      if (!grow) {
         player.level()
            .playSound(null, player.getX(), player.getY(), player.getZ(), (SoundEvent)MainSounds.KATANA_SLASH.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
      }
   }
}
