package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.render.effects.AuraRenderer;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * DragonMineZ's own aura layers for a player (form, stack form, fusion, the blend while
 * transforming): the HD aura takes its colours from exactly what DMZ would draw, rather than
 * re-deriving them from form data. {@code getAuraLayers} is private static in DragonMineZ 2.1.3.
 */
@Mixin(value = AuraRenderer.class, remap = false)
public interface DmzAuraLayersInvoker {
    /** The player's resolved model scale; {@code getModelScale} is private static in 2.1.3. */
    @Invoker("getModelScale")
    static float[] xenopixels$modelScale(StatsData stats) {
        throw new AssertionError("mixin invoker");
    }

    /**
     * The size DragonMineZ draws this player's aura at, per axis: the model scale times its 1.05
     * base, and through {@code DmzAuraStatScaleMixin} the battle-power and ki-charge stretch of
     * {@code /xenohud aura}. {@code getAuraScale(StatsData, float[])} is private static in 2.1.3.
     */
    @Invoker("getAuraScale")
    static float[] xenopixels$auraScale(StatsData stats, float[] modelScale) {
        throw new AssertionError("mixin invoker");
    }

    @Invoker("getAuraLayers")
    static List<AuraRenderer.AuraLayer> xenopixels$auraLayers(Player player, StatsData stats, float partialTick) {
        throw new AssertionError("mixin invoker");
    }
}
