package com.dragonminez.client.util;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public class KeyBinds {
   private static final String DMZ_CATEGORY = "key.categories.dragonminez";
   private static final String MINIGAMES_CATEGORY = "key.categories.minigames.dragonminez";
   public static final KeyMapping STATS_MENU = registerKey("stats_menu", 86);
   public static final KeyMapping STATS_TAB_PARTY = registerKeyUnbound("stats_tab_party");
   public static final KeyMapping STATS_TAB_SKILLS = registerKeyUnbound("stats_tab_skills");
   public static final KeyMapping STATS_TAB_QUESTS = registerKeyUnbound("stats_tab_quests");
   public static final KeyMapping STATS_TAB_MINIGAMES = registerKeyUnbound("stats_tab_minigames");
   public static final KeyMapping STATS_TAB_CONFIG = registerKeyUnbound("stats_tab_config");
   public static final KeyMapping KI_CHARGE = registerKey("ki_charge", 67);
   public static final KeyMapping SECOND_FUNCTION_KEY = registerKey("second_function_key", 342);
   public static final KeyMapping ACTION_KEY = registerKey("action_key", 71);
   public static final KeyMapping DESCEND = registerKeyAlt("descend", 71);
   public static final KeyMapping INSTANT_TRANSFORM = registerKeyUnbound("instant_transform");
   public static final KeyMapping LOWER_RELEASE = registerKeyAlt("lower_release", 67);
   public static final KeyMapping INSTANT_TRANSMISSION = registerKey("instant_transmission", 72);
   public static final KeyMapping SPACEPOD_MENU = registerKey("spacepod_menu", 72);
   public static final KeyMapping UTILITY_MENU = registerKey("utility_menu", 88);
   public static final KeyMapping LOCK_ON = registerKey("lock_on", 90);
   public static final KeyMapping KI_SENSE = registerKey("ki_sense", 293);
   public static final KeyMapping FLY_KEY = registerKey("fly_key", 70);
   public static final KeyMapping DASH_KEY = registerKey("dash_key", 82);
   public static final KeyMapping BLOCK_KEY = registerMouse("block_key", 1);
   public static final KeyMapping TECHNIQUE_SLOT_1 = registerKeyAlt("technique_slot_1", 49);
   public static final KeyMapping TECHNIQUE_SLOT_2 = registerKeyAlt("technique_slot_2", 50);
   public static final KeyMapping TECHNIQUE_SLOT_3 = registerKeyAlt("technique_slot_3", 51);
   public static final KeyMapping TECHNIQUE_SLOT_4 = registerKeyAlt("technique_slot_4", 52);
   public static final KeyMapping TECHNIQUE_SLOT_5 = registerKeyCtrl("technique_slot_5", 49);
   public static final KeyMapping TECHNIQUE_SLOT_6 = registerKeyCtrl("technique_slot_6", 50);
   public static final KeyMapping TECHNIQUE_SLOT_7 = registerKeyCtrl("technique_slot_7", 51);
   public static final KeyMapping TECHNIQUE_SLOT_8 = registerKeyCtrl("technique_slot_8", 52);
   public static final KeyMapping[] TECHNIQUE_SLOTS = new KeyMapping[]{
      TECHNIQUE_SLOT_1, TECHNIQUE_SLOT_2, TECHNIQUE_SLOT_3, TECHNIQUE_SLOT_4, TECHNIQUE_SLOT_5, TECHNIQUE_SLOT_6, TECHNIQUE_SLOT_7, TECHNIQUE_SLOT_8
   };
   public static final KeyMapping RHYTHM_LEFT = registerKey("rhythm_left", 263, true);
   public static final KeyMapping RHYTHM_DOWN = registerKey("rhythm_down", 264, true);
   public static final KeyMapping RHYTHM_UP = registerKey("rhythm_up", 265, true);
   public static final KeyMapping RHYTHM_RIGHT = registerKey("rhythm_right", 262, true);

   private static KeyMapping registerKey(String name, int keyCode) {
      return registerKey(name, keyCode, false);
   }

   private static KeyMapping registerKey(String name, int keyCode, boolean minigame) {
      return new KeyMapping(
         "key.dragonminez." + name,
         KeyConflictContext.IN_GAME,
         Type.KEYSYM,
         keyCode,
         minigame ? "key.categories.minigames.dragonminez" : "key.categories.dragonminez"
      );
   }

   private static KeyMapping registerKeyUnbound(String name) {
      return new KeyMapping("key.dragonminez." + name, KeyConflictContext.IN_GAME, Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.dragonminez");
   }

   private static KeyMapping registerKeyAlt(String name, int keyCode) {
      return new KeyMapping(
         "key.dragonminez." + name, KeyConflictContext.IN_GAME, KeyModifier.ALT, Type.KEYSYM.getOrCreate(keyCode), "key.categories.dragonminez"
      );
   }

   private static KeyMapping registerKeyCtrl(String name, int keyCode) {
      return new KeyMapping(
         "key.dragonminez." + name, KeyConflictContext.IN_GAME, KeyModifier.CONTROL, Type.KEYSYM.getOrCreate(keyCode), "key.categories.dragonminez"
      );
   }

   private static KeyMapping registerMouse(String name, int keyCode) {
      return registerMouse(name, keyCode, false);
   }

   private static KeyMapping registerMouse(String name, int keyCode, boolean minigame) {
      return new KeyMapping(
         "key.dragonminez." + name,
         KeyConflictContext.IN_GAME,
         Type.MOUSE,
         keyCode,
         minigame ? "key.categories.minigames.dragonminez" : "key.categories.dragonminez"
      );
   }

   public static boolean isSecondFunctionDown() {
      Key key = SECOND_FUNCTION_KEY.getKey();
      if (KeyModifier.ALT.matches(key)) {
         return Screen.hasAltDown();
      } else if (KeyModifier.CONTROL.matches(key)) {
         return Screen.hasControlDown();
      } else {
         return KeyModifier.SHIFT.matches(key) ? Screen.hasShiftDown() : isPhysicallyDown(SECOND_FUNCTION_KEY);
      }
   }

   public static boolean isBarModifierActive(KeyModifier modifier) {
      if (modifier == KeyModifier.NONE) {
         return false;
      } else {
         return modifier == KeyModifier.CONTROL && isAltGrDown() ? false : modifier.isActive(KeyConflictContext.IN_GAME);
      }
   }

   private static boolean isAltGrDown() {
      if (Minecraft.ON_OSX) {
         return false;
      } else {
         long window = Minecraft.getInstance().getWindow().getWindow();
         return InputConstants.isKeyDown(window, 346);
      }
   }

   public static boolean isChordDown(KeyMapping mapping) {
      KeyModifier modifier = mapping.getKeyModifier();
      return modifier != KeyModifier.NONE && !isBarModifierActive(modifier) ? false : isPhysicallyDown(mapping);
   }

   public static boolean isPhysicallyDown(KeyMapping mapping) {
      Key key = mapping.getKey();
      if (key.getValue() == InputConstants.UNKNOWN.getValue()) {
         return mapping.isDown();
      } else {
         long window = Minecraft.getInstance().getWindow().getWindow();
         if (key.getType() == Type.KEYSYM) {
            return InputConstants.isKeyDown(window, key.getValue());
         } else {
            return key.getType() == Type.MOUSE ? GLFW.glfwGetMouseButton(window, key.getValue()) == 1 : mapping.isDown();
         }
      }
   }

   public static void registerAll(RegisterKeyMappingsEvent event) {
      try {
         for (Field field : KeyBinds.class.getDeclaredFields()) {
            if (field.getType() == KeyMapping.class && Modifier.isStatic(field.getModifiers())) {
               field.setAccessible(true);
               KeyMapping keyMapping = (KeyMapping)field.get(null);
               if (keyMapping != null) {
                  event.register(keyMapping);
               }
            }
         }
      } catch (IllegalAccessException var6) {
         throw new RuntimeException("Failed to register key bindings", var6);
      }
   }
}
