package net.bullettrain.xenopixelsmod.block.custom;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The collision voxels of a managed physical planet. Sable builds a sub-level's collider and mass from its
 * blocks only, so the planet keeps a hollow shell of these; they draw nothing, because the HD sphere is the
 * planet's visible surface and a visible voxel would poke through its facets.
 *
 * <p>Sable counts a block as solid when it is not air and its collision shape is non-empty, which the
 * default full-cube shape satisfies, so an invisible shell still collides and still has mass. Its mass is
 * set by {@code data/sable/physics_block_properties/xenopixelsmod_planet_shell.json}; it must stay above
 * zero, because Sable removes a sub-level whose total mass falls to zero.
 */
public class PlanetShellBlock extends Block {
    public PlanetShellBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
