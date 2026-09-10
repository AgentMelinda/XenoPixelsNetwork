package com.dragonminez.server;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.server.commands.AlignmentCommand;
import com.dragonminez.server.commands.BonusCommand;
import com.dragonminez.server.commands.ClassCommand;
import com.dragonminez.server.commands.ConfigCommand;
import com.dragonminez.server.commands.CooldownsCommand;
import com.dragonminez.server.commands.DebugCommand;
import com.dragonminez.server.commands.EffectsCommand;
import com.dragonminez.server.commands.FormsCommand;
import com.dragonminez.server.commands.HairCommand;
import com.dragonminez.server.commands.HaloCommand;
import com.dragonminez.server.commands.LocateCommand;
import com.dragonminez.server.commands.MasteryCommand;
import com.dragonminez.server.commands.PartyCommand;
import com.dragonminez.server.commands.PointsCommand;
import com.dragonminez.server.commands.RacialSkillCommand;
import com.dragonminez.server.commands.RaidCommand;
import com.dragonminez.server.commands.ReloadCommand;
import com.dragonminez.server.commands.RestoreCommand;
import com.dragonminez.server.commands.ReviveCommand;
import com.dragonminez.server.commands.SkillsCommand;
import com.dragonminez.server.commands.StatsCommand;
import com.dragonminez.server.commands.StoryCommand;
import com.dragonminez.server.commands.TailCommand;
import com.dragonminez.server.commands.TechCommand;
import com.dragonminez.server.commands.WeightCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

public class DMZServer {
   public static void init() {
      LogUtil.info(Env.SERVER, "Initializing DragonMineZ Server...");
   }

   public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
      StatsCommand.register(dispatcher);
      RacialSkillCommand.register(dispatcher);
      BonusCommand.register(dispatcher);
      EffectsCommand.register(dispatcher);
      SkillsCommand.register(dispatcher);
      TechCommand.register(dispatcher);
      CooldownsCommand.register(dispatcher);
      FormsCommand.register(dispatcher);
      PointsCommand.register(dispatcher);
      DebugCommand.register(dispatcher);
      MasteryCommand.register(dispatcher);
      LocateCommand.register(dispatcher);
      PartyCommand.register(dispatcher);
      StoryCommand.register(dispatcher);
      ReviveCommand.register(dispatcher);
      ReloadCommand.register(dispatcher);
      ConfigCommand.register(dispatcher);
      WeightCommand.register(dispatcher);
      RaidCommand.register(dispatcher);
      AlignmentCommand.register(dispatcher);
      TailCommand.register(dispatcher);
      HaloCommand.register(dispatcher);
      RestoreCommand.register(dispatcher);
      HairCommand.register(dispatcher);
      ClassCommand.register(dispatcher);
      LogUtil.info(Env.SERVER, "DragonMineZ Commands Registered");
   }
}
