package com.dragonminez.common.compat;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public class WorldGuardCompat {
   private static boolean worldGuardAvailable = false;
   private static WorldGuardCompat.IWorldGuardHandler handler = null;

   public static void init() {
      LogUtil.info(Env.SERVER, "Checking for WorldGuard compatibility...");

      try {
         Class<?> wgClass = null;

         try {
            wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
         } catch (ClassNotFoundException var11) {
            try {
               Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
               Method getServerMethod = bukkitClass.getMethod("getServer");
               Object server = getServerMethod.invoke(null);
               Method getPluginManagerMethod = server.getClass().getMethod("getPluginManager");
               Object pluginManager = getPluginManagerMethod.invoke(server);
               Method getPluginMethod = pluginManager.getClass().getMethod("getPlugin", String.class);
               Object plugin = getPluginMethod.invoke(pluginManager, "WorldGuard");
               if (plugin != null) {
                  ClassLoader pluginLoader = plugin.getClass().getClassLoader();
                  wgClass = pluginLoader.loadClass("com.sk89q.worldguard.WorldGuard");
                  LogUtil.info(Env.SERVER, "WorldGuard found via Bukkit PluginManager!");
               }
            } catch (Exception var10) {
               LogUtil.info(Env.SERVER, "WorldGuard not found via Bukkit PluginManager.");
            }
         }

         if (wgClass == null) {
            throw new ClassNotFoundException("WorldGuard class not found");
         }

         handler = new WorldGuardCompat.WorldGuardHandler(wgClass.getClassLoader());
         handler.registerFlags();
         worldGuardAvailable = true;
         LogUtil.info(Env.SERVER, "WorldGuard detected! Ki-griefing flag has been registered.");
      } catch (ClassNotFoundException var12) {
         LogUtil.info(Env.SERVER, "WorldGuard not found. Using only gamerules for ki-griefing control.");
         worldGuardAvailable = false;
      } catch (Exception var13) {
         LogUtil.error(Env.SERVER, "Failed to initialize WorldGuard compatibility", var13);
         worldGuardAvailable = false;
      }
   }

   public static boolean canGrief(Level level, BlockPos pos, Entity source) {
      if (worldGuardAvailable && handler != null) {
         try {
            return handler.canGrief(level, pos, source);
         } catch (Exception var4) {
            LogUtil.error(Env.SERVER, "Error checking WorldGuard flag at " + pos, var4);
            return true;
         }
      } else {
         return true;
      }
   }

   public static double getGravity(Level level, BlockPos pos, Entity source) {
      return worldGuardAvailable && handler != null ? handler.getGravityValue(level, pos, source) : 0.0;
   }

   private interface IWorldGuardHandler {
      void registerFlags();

      boolean canGrief(Level var1, BlockPos var2, Entity var3);

      double getGravityValue(Level var1, BlockPos var2, Entity var3);
   }

   private static class WorldGuardHandler implements WorldGuardCompat.IWorldGuardHandler {
      private final ClassLoader classLoader;
      private Object kiGriefingFlag;
      private Object gravityFlag;

      public WorldGuardHandler(ClassLoader classLoader) {
         this.classLoader = classLoader != null ? classLoader : this.getClass().getClassLoader();
      }

      private Class<?> getClass(String name) throws ClassNotFoundException {
         return Class.forName(name, true, this.classLoader);
      }

      @Override
      public void registerFlags() {
         try {
            Class<?> registryClass = this.getClass("com.sk89q.worldguard.protection.flags.registry.FlagRegistry");
            Class<?> worldGuardClass = this.getClass("com.sk89q.worldguard.WorldGuard");
            Object worldGuardInstance = worldGuardClass.getMethod("getInstance").invoke(null);
            Object flagRegistry = worldGuardClass.getMethod("getFlagRegistry").invoke(worldGuardInstance);
            Class<?> stateFlagClass = this.getClass("com.sk89q.worldguard.protection.flags.StateFlag");
            Object kiFlagInstance = stateFlagClass.getConstructor(String.class, boolean.class).newInstance("ki-griefing", true);
            Class<?> doubleFlagClass = this.getClass("com.sk89q.worldguard.protection.flags.DoubleFlag");
            Object gravityFlagInstance = doubleFlagClass.getConstructor(String.class).newInstance("dmz-gravity");

            try {
               registryClass.getMethod("register", this.getClass("com.sk89q.worldguard.protection.flags.Flag")).invoke(flagRegistry, kiFlagInstance);
               this.kiGriefingFlag = kiFlagInstance;
            } catch (Exception var14) {
               try {
                  this.kiGriefingFlag = registryClass.getMethod("get", String.class).invoke(flagRegistry, "ki-griefing");
               } catch (Exception var13) {
                  LogUtil.error(Env.SERVER, "Failed to retrieve existing ki-griefing flag", var13);
               }
            }

            try {
               registryClass.getMethod("register", this.getClass("com.sk89q.worldguard.protection.flags.Flag")).invoke(flagRegistry, gravityFlagInstance);
               this.gravityFlag = gravityFlagInstance;
            } catch (Exception var12) {
               try {
                  this.gravityFlag = registryClass.getMethod("get", String.class).invoke(flagRegistry, "dmz-gravity");
               } catch (Exception var11) {
                  LogUtil.error(Env.SERVER, "Failed to retrieve existing dmz-gravity flag", var11);
               }
            }
         } catch (Exception var15) {
            LogUtil.error(Env.SERVER, "Error registering WorldGuard flags", var15);
         }
      }

      @Override
      public boolean canGrief(Level level, BlockPos pos, Entity source) {
         if (this.kiGriefingFlag == null) {
            return true;
         } else {
            try {
               Method getWorldMethod = level.getClass().getMethod("getWorld");
               Object bukkitWorld = getWorldMethod.invoke(level);
               Class<?> locationClass = Class.forName("org.bukkit.Location");
               Class<?> worldClass = Class.forName("org.bukkit.World");
               Object location = locationClass.getConstructor(worldClass, double.class, double.class, double.class)
                  .newInstance(bukkitWorld, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ());
               Class<?> bukkitAdapterClass = this.getClass("com.sk89q.worldguard.bukkit.BukkitAdapter");
               Object wgLocation = bukkitAdapterClass.getMethod("adapt", locationClass).invoke(null, location);
               Class<?> worldGuardClass = this.getClass("com.sk89q.worldguard.WorldGuard");
               Object worldGuardInstance = worldGuardClass.getMethod("getInstance").invoke(null);
               Object platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuardInstance);
               Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
               Object regionQuery = regionContainer.getClass().getMethod("createQuery").invoke(regionContainer);
               Class<?> stateFlagClass = this.getClass("com.sk89q.worldguard.protection.flags.StateFlag");
               Method queryStateMethod = regionQuery.getClass()
                  .getMethod(
                     "queryState",
                     this.getClass("com.sk89q.worldedit.util.Location"),
                     this.getClass("com.sk89q.worldguard.LocalPlayer"),
                     this.getClass("com.sk89q.worldguard.protection.flags.Flag[]")
                  );
               Object flagsArray = Array.newInstance(this.getClass("com.sk89q.worldguard.protection.flags.Flag"), 1);
               Array.set(flagsArray, 0, this.kiGriefingFlag);
               Object state = queryStateMethod.invoke(regionQuery, wgLocation, null, flagsArray);
               if (state == null) {
                  return true;
               } else {
                  Class<?> stateFlagStateClass = this.getClass("com.sk89q.worldguard.protection.flags.StateFlag$State");
                  Object denyState = stateFlagStateClass.getField("DENY").get(null);
                  return !state.equals(denyState);
               }
            } catch (NoSuchMethodException var22) {
               return true;
            } catch (Exception var23) {
               LogUtil.error(Env.SERVER, "Error querying WorldGuard flag state", var23);
               return true;
            }
         }
      }

      @Override
      public double getGravityValue(Level level, BlockPos pos, Entity source) {
         if (this.gravityFlag == null) {
            return 0.0;
         } else {
            try {
               Method getWorldMethod = level.getClass().getMethod("getWorld");
               Object bukkitWorld = getWorldMethod.invoke(level);
               Class<?> locationClass = Class.forName("org.bukkit.Location");
               Class<?> worldClass = Class.forName("org.bukkit.World");
               Object location = locationClass.getConstructor(worldClass, double.class, double.class, double.class)
                  .newInstance(bukkitWorld, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ());
               Class<?> bukkitAdapterClass = this.getClass("com.sk89q.worldguard.bukkit.BukkitAdapter");
               Object wgLocation = bukkitAdapterClass.getMethod("adapt", locationClass).invoke(null, location);
               Class<?> worldGuardClass = this.getClass("com.sk89q.worldguard.WorldGuard");
               Object worldGuardInstance = worldGuardClass.getMethod("getInstance").invoke(null);
               Object platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuardInstance);
               Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
               Object regionQuery = regionContainer.getClass().getMethod("createQuery").invoke(regionContainer);
               Method queryValueMethod = regionQuery.getClass()
                  .getMethod(
                     "queryValue",
                     this.getClass("com.sk89q.worldedit.util.Location"),
                     this.getClass("com.sk89q.worldguard.LocalPlayer"),
                     this.getClass("com.sk89q.worldguard.protection.flags.Flag")
                  );
               Object result = queryValueMethod.invoke(regionQuery, wgLocation, null, this.gravityFlag);
               return result != null && result instanceof Double ? (Double)result : 0.0;
            } catch (NoSuchMethodException var18) {
               return 0.0;
            } catch (Exception var19) {
               LogUtil.error(Env.SERVER, "Error querying Gravity flag", var19);
               return 0.0;
            }
         }
      }
   }
}
