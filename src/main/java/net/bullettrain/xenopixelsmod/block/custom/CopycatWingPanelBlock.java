package net.bullettrain.xenopixelsmod.block.custom;

import com.mojang.serialization.MapCodec;
import net.bullettrain.xenopixelsmod.block.custom.copycat.CopycatMaterial;
import net.bullettrain.xenopixelsmod.block.custom.copycat.CopycatMaterialSupport;
import net.bullettrain.xenopixelsmod.block.custom.copycat.CopycatWing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link WingPanelBlock} whose skin is inherited from a block you right-click it with — the
 * copycat wing panel. Everything a wing does (deflection, lift, the Panel Configurator, the wrench,
 * {@code /wingmount}, {@code AeroFlightCore}) is inherited unchanged; only the texture changes.
 * See {@code CopycatWingModel} for the render side and {@code CopycatMaterialSupport} for the
 * apply / unbind interaction.
 */
public class CopycatWingPanelBlock extends WingPanelBlock implements CopycatWing {

    public static final MapCodec<CopycatWingPanelBlock> CODEC = simpleCodec(CopycatWingPanelBlock::new);

    public CopycatWingPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof CopycatMaterial holder) {
            return CopycatMaterialSupport.useItemOn(stack, level, pos, player, hand, hit, holder, this);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos,
                                    Direction side, @Nullable BlockState queryState,
                                    @Nullable BlockPos queryPos) {
        return CopycatMaterialSupport.appearance(state, level, pos);
    }
}
