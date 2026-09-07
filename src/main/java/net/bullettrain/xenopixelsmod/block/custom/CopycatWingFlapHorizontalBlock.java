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

/** Texture-inheriting {@link WingFlapHorizontalBlock} — see {@link CopycatWingPanelBlock}. */
public class CopycatWingFlapHorizontalBlock extends WingFlapHorizontalBlock implements CopycatWing {

    public static final MapCodec<CopycatWingFlapHorizontalBlock> CODEC =
            simpleCodec(CopycatWingFlapHorizontalBlock::new);

    public CopycatWingFlapHorizontalBlock(Properties properties) {
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
