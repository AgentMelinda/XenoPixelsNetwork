package com.dragonminez.server.commands;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.TechniqueData;
import com.dragonminez.common.stats.techniques.Techniques;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CooldownsCommand {
   private static final String TECHNIQUE_COOLDOWN_PREFIX = "TechniqueCooldown_";

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("dmzcooldowns")
               .requires(source -> DMZPermissions.check(source, DMZPermissions.COOLDOWNS_SELF, DMZPermissions.COOLDOWNS_OTHERS)))
            .then(
               Commands.argument("slot", IntegerArgumentType.integer(1, 8))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("cooldown", IntegerArgumentType.integer(0))
                           .executes(
                              ctx -> setCooldown(
                                    (CommandSourceStack)ctx.getSource(),
                                    List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()),
                                    IntegerArgumentType.getInteger(ctx, "slot"),
                                    IntegerArgumentType.getInteger(ctx, "cooldown")
                                 )
                           ))
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                                 .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.COOLDOWNS_OTHERS)))
                              .executes(
                                 ctx -> setCooldown(
                                       (CommandSourceStack)ctx.getSource(),
                                       EntityArgument.getPlayers(ctx, "targets"),
                                       IntegerArgumentType.getInteger(ctx, "slot"),
                                       IntegerArgumentType.getInteger(ctx, "cooldown")
                                    )
                              )
                        )
                  )
            )
      );
   }

   private static int setCooldown(CommandSourceStack source, Collection<ServerPlayer> targets, int slot, int cooldownTicks) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();
      int slotIndex = slot - 1;
      AtomicInteger applied = new AtomicInteger();
      String[] lastPlayer = new String[1];

      for (ServerPlayer player : targets) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            Techniques techniques = data.getTechniques();
            String techId = techniques.getEquippedSlots()[slotIndex];
            if (techId != null && !techId.isEmpty()) {
               TechniqueData tech = techniques.getUnlockedTechniques().get(techId);
               if (tech != null) {
                  data.getCooldowns().setCooldown("TechniqueCooldown_" + techId, cooldownTicks);
                  applied.incrementAndGet();
                  lastPlayer[0] = player.getName().getString();
                  NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
               }
            }
         });
      }

      if (applied.get() == 0) {
         source.sendFailure(Component.translatable("command.dragonminez.cooldowns.empty_slot", new Object[]{slot}));
         return 0;
      } else {
         if (applied.get() == 1) {
            source.sendSuccess(() -> Component.translatable("command.dragonminez.cooldowns.set_success", new Object[]{slot, cooldownTicks, lastPlayer[0]}), log);
         } else {
            source.sendSuccess(
               () -> Component.translatable("command.dragonminez.cooldowns.set_multiple", new Object[]{slot, cooldownTicks, applied.get()}), log
            );
         }

         return applied.get();
      }
   }
}
