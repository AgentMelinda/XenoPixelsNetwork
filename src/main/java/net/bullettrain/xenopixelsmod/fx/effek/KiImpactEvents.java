package net.bullettrain.xenopixelsmod.fx.effek;

import com.dragonminez.common.init.entities.ki.KiExplosionVisualEntity;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * A ki attack exploding plays the punch impact instead of DragonMineZ's explosion visual
 * (2026-09-29 owner). DMZ spawns {@code KiExplosionVisualEntity} for the look only - ki blasts, ki
 * waves and strike attacks, after {@code setupExplosion} has set its size - separately from the
 * damaging explosion, so cancelling its join removes the visual and nothing else. When the effect
 * cannot play (effekseerKiImpacts off, library failure) DMZ's visual spawns as before.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class KiImpactEvents {
    private KiImpactEvents() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof KiExplosionVisualEntity visual)) return;
        if (!(event.getLevel() instanceof ServerLevel level) || event.loadedFromDisk()) return;
        if (XenoEffects.play(level, EffectSlot.KI_IMPACT, visual.position(), null,
                KiImpactRules.size(visual.getMaxSize()), -1)) {
            event.setCanceled(true);
        }
    }
}
