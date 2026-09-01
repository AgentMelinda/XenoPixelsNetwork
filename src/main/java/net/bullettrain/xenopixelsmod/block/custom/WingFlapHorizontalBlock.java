package net.bullettrain.xenopixelsmod.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link WingPanelBlock} that always mounts flat ({@code AXIS=Y}), matching Warium's own
 * separate horizontal control-surface block rather than relying on {@code AXIS} placement or the
 * Panel Configurator's axis auto-correction. Placing this always tilts up/down — this is the
 * block to reach for when building a flap, elevator or aileron.
 *
 * <p>Everything else — the deflection animation, the two-part base/flap render split, lift
 * generation, the Panel Configurator's role cycling — is inherited unchanged from
 * {@link WingPanelBlock}; only where it is allowed to point differs.
 */
public class WingFlapHorizontalBlock extends WingPanelBlock {

    public static final MapCodec<WingFlapHorizontalBlock> CODEC = simpleCodec(WingFlapHorizontalBlock::new);

    public WingFlapHorizontalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // defaultBlockState() is already AXIS=Y (WingPanelBlock's own default) — this just refuses
        // to let the look-direction placement WingPanelBlock normally uses override it.
        return defaultBlockState();
    }
}
