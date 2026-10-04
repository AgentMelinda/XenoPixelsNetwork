package net.bullettrain.xenopixelsmod.client.aura;

import com.dragonminez.client.gui.radial.AbstractRadialNode;
import com.dragonminez.common.stats.StatsData;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * HD aura variant under Ki Actions in DragonMineZ's X menu: cycles v1 → v2 → v3 → v1, the same
 * as {@code /xenoaura v1|v2|v3}. Lit while v2 or v3 is chosen.
 */
public final class AuraVariantNode extends AbstractRadialNode {

    @Override
    public Component label(StatsData stats) {
        return switch (net.bullettrain.xenopixelsmod.fx.aura.HdAuraPlan.parseVariant(
                XenoClientConfig.auraVariant)) {
            case "v3" -> Component.translatable("gui.xenopixelsmod.radial.aura_v3");
            case "v2" -> Component.translatable("gui.xenopixelsmod.radial.aura_v2");
            default -> Component.translatable("gui.xenopixelsmod.radial.aura_v1");
        };
    }

    @Override
    public ResourceLocation icon(StatsData stats) {
        return icon("aura");
    }

    @Override
    public boolean active(StatsData stats) {
        return HdAuraClient.variant2() || HdAuraClient.variant3();
    }

    @Override
    public int labelColor(StatsData stats) {
        return active(stats) ? GREEN : RED;
    }

    @Override
    public void onSelect(StatsData stats) {
        String next = switch (net.bullettrain.xenopixelsmod.fx.aura.HdAuraPlan.parseVariant(
                XenoClientConfig.auraVariant)) {
            case "v1" -> "v2";
            case "v2" -> "v3";
            default -> "v1";
        };
        XenoClientConfig.auraVariant = next;
        XenoClientConfig.save();
        playToggle(!"v1".equals(next));
    }
}
