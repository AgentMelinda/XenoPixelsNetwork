package net.bullettrain.xenopixelsmod.event;

import net.neoforged.fml.common.EventBusSubscriber;

import com.dragonminez.common.events.DMZEvent;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DmzHooks {
    private DmzHooks() {
    }

    @SubscribeEvent
    public static void onTrainingPointGain(DMZEvent.TPGainEvent event) {
        XenoPixelsMod.LOGGER.debug(
                "DragonMineZ TPGainEvent received for {}: {} TP",
                event.getPlayer().getGameProfile().getName(),
                event.getTpGain()
        );
    }

    // DMZ עצמו קורא (post) לאירוע הזה כל פעם שמערכת ה-Stats שלו מחדשת (regen) חיים לשחקן.
    // זה לא קורה "כי אכלו פריט" - הוא לא קשור לאכילה כלל, אלא לטיק הרגיל של DMZ.
    // כאן אפשר רק לצפות בכמות ה-regen ואף לשנות אותה עם setAmount(), לא ליזום אותה.
    @SubscribeEvent
    public static void onFormChange(DMZEvent.FormChangeEvent event) {
        if (event.getNewForm() == null || event.getNewForm().isBlank()) {
            net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.stopClip(event.getPlayer());
            return;
        }
        if (net.bullettrain.xenopixelsmod.anim.CombatStateAnim.hasCustom(event.getPlayer(),
                net.bullettrain.xenopixelsmod.combat.anim.TechniqueAnimSlot.TRANSFORM)) {
            net.bullettrain.xenopixelsmod.anim.CombatStateAnim.play(event.getPlayer(),
                    net.bullettrain.xenopixelsmod.combat.anim.TechniqueAnimSlot.TRANSFORM);
        }
    }

    @SubscribeEvent
    public static void onHealthRegen(DMZEvent.HealthRegenEvent event) {
        XenoPixelsMod.LOGGER.debug(
                "DragonMineZ HealthRegenEvent for {}: regen amount = {}",
                event.getPlayer().getGameProfile().getName(),
                event.getAmount()
        );

        // דוגמה: להגדיל את כמות ה-regen ב-50% לכל שחקן
        // event.setAmount(event.getAmount() * 1.5);
    }
}
