package net.bullettrain.tutorialmod.event;

import com.dragonminez.common.events.DMZEvent;
import net.bullettrain.tutorialmod.TutorialMod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TutorialMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzHooks {
    private DmzHooks() {
    }

    @SubscribeEvent
    public static void onTrainingPointGain(DMZEvent.TPGainEvent event) {
        TutorialMod.LOGGER.debug(
                "DragonMineZ TPGainEvent received for {}: {} TP",
                event.getPlayer().getGameProfile().getName(),
                event.getTpGain()
        );
    }
}
