package com.dragonminez;

import com.dragonminez.client.DMZClient;
import com.dragonminez.common.DMZCommon;
import com.dragonminez.server.DMZServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforgespi.language.IModFileInfo;

@Mod("dragonminez")
public class DragonMineZ {
   public DragonMineZ(IEventBus modEventBus) {
      this.checkIncompatibility("legendarytooltips", "Legendary Tooltips");
      this.checkIncompatibility("epicfight", "Epic Fight");
      this.checkIncompatibility("bettercombat", "Better Combat");
      LogUtil.info(Env.COMMON, "Initializing DragonMineZ...");
      DMZCommon.init(modEventBus);
      if (FMLEnvironment.dist == Dist.CLIENT) {
         DMZClient.init();
      } else if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
         DMZServer.init();
      }

      LogUtil.info(Env.COMMON, "DragonMineZ initialized successfully");
   }

   private void checkIncompatibility(String modId, String modName) {
      if (ModList.get().isLoaded(modId)) {
         String jarName = modId + ".jar";
         IModFileInfo fileInfo = ModList.get().getModFileById(modId);
         if (fileInfo != null && fileInfo.getFile() != null) {
            jarName = fileInfo.getFile().getFileName();
         }

         throw new IllegalStateException(
            "§cIncompatibility Error: §eDragonMineZ §ccannot be loaded alongside §e"
               + modName
               + "§c. Please remove the §6"
               + jarName
               + " §cfile from your mods folder to continue."
         );
      }
   }
}
