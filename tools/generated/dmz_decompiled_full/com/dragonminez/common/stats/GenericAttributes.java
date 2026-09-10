package com.dragonminez.common.stats;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.EntityAttributes;
import com.dragonminez.common.init.MainAttributes;
import com.dragonminez.mixin.common.RangedAttributeMixin;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.MOD
)
public class GenericAttributes {
   private static final double ENGINE_DERIVED_ATTRIBUTE_CEILING = 2.0E9;
   public static final double MAX_HEALTH_ENGINE_CEILING = 1048576.0;
   private static volatile boolean gameBusHooked;
   private static volatile boolean loggedOnce;

   @SubscribeEvent
   public static void onLoadComplete(FMLLoadCompleteEvent event) {
      ensureAttributeCeilings();
      hookGameBus();
   }

   private static void hookGameBus() {
      if (!gameBusHooked) {
         gameBusHooked = true;
         NeoForge.EVENT_BUS.addListener(GenericAttributes::onServerAboutToStart);
      }
   }

   private static void onServerAboutToStart(ServerAboutToStartEvent event) {
      ensureAttributeCeilings();
   }

   public static void ensureAttributeCeilings() {
      double configuredMax = readConfiguredMaxValue();
      if (configuredMax > 0.0) {
         raiseMaxIfNeeded(MainAttributes.STRENGTH, configuredMax);
         raiseMaxIfNeeded(MainAttributes.STRIKE_POWER, configuredMax);
         raiseMaxIfNeeded(MainAttributes.RESISTANCE, configuredMax);
         raiseMaxIfNeeded(MainAttributes.VITALITY, configuredMax);
         raiseMaxIfNeeded(MainAttributes.KI_POWER, configuredMax);
         raiseMaxIfNeeded(MainAttributes.ENERGY, configuredMax);
      }

      raiseMaxIfNeeded(Attributes.ARMOR, 2.0E9);
      raiseMaxIfNeeded(Attributes.ARMOR_TOUGHNESS, 2.0E9);
      raiseMaxIfNeeded(Attributes.MAX_HEALTH, 1048576.0);
      raiseMaxIfNeeded(Attributes.ATTACK_DAMAGE, 2.0E9);
      raiseMaxIfNeeded(MainAttributes.MAX_ENERGY, 2.0E9);
      raiseMaxIfNeeded(MainAttributes.MAX_STAMINA, 2.0E9);
      raiseMaxIfNeeded(MainAttributes.MAX_POISE, 2.0E9);
      raiseMaxIfNeeded(MainAttributes.MELEE_DAMAGE, 2.0E9);
      raiseMaxIfNeeded(MainAttributes.STRIKE_DAMAGE, 2.0E9);
      raiseMaxIfNeeded(MainAttributes.KI_DAMAGE, 2.0E9);
      raiseMaxIfNeeded(MainAttributes.DEFENSE, 2.0E9);
      raiseMaxIfNeeded(EntityAttributes.KI_BLAST_DAMAGE, 2.0E9);
      raiseMaxIfNeeded(EntityAttributes.FLY_SPEED, 2.0E9);
      raiseMaxIfNeeded(EntityAttributes.KI_BLAST_SPEED, 2.0E9);
      if (!loggedOnce) {
         loggedOnce = true;
         double mainMax = MainAttributes.VITALITY.value() instanceof RangedAttribute ra ? ra.getMaxValue() : -1.0;
         double hpMax = Attributes.MAX_HEALTH.value() instanceof RangedAttribute rax ? rax.getMaxValue() : -1.0;
         LogUtil.info(
            Env.COMMON,
            "Attribute ceilings synced: config maxValue={} mainStatAttrMax={} maxHealthAttrMax={}",
            configuredMax > 0.0 ? configuredMax : "(config not ready)",
            mainMax,
            hpMax
         );
      }
   }

   private static double readConfiguredMaxValue() {
      return ConfigManager.getServerConfig() != null && ConfigManager.getServerConfig().getGameplay() != null
         ? Math.max(1.0, (double)ConfigManager.getServerConfig().getGameplay().getMaxValue().intValue())
         : -1.0;
   }

   private static void raiseMaxIfNeeded(Holder<Attribute> attribute, double maxValue) {
      if (attribute != null && attribute.value() != null && !(maxValue <= 0.0)) {
         if (attribute.value() instanceof RangedAttribute rangedAttribute) {
            if (!(rangedAttribute.getMaxValue() >= maxValue)) {
               ((RangedAttributeMixin)rangedAttribute).setMaxValue(maxValue);
            }
         }
      }
   }
}
