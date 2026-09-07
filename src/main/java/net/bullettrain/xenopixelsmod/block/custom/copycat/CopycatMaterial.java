package net.bullettrain.xenopixelsmod.block.custom.copycat;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A block entity that stores a "copied" {@link BlockState} to render in place of its own shell —
 * the shared contract behind {@code CopycatGlowstoneBlockEntity} and the copycat wing panels.
 * {@code CopycatMaterialSupport} drives the right-click-to-apply / wrench-to-unbind interaction
 * against this interface.
 */
public interface CopycatMaterial {

    /** The state currently being mimicked. Equal to the block's own shell state when unbound. */
    BlockState getMaterial();

    /** True once a real block has been copied onto this one. */
    boolean hasCustomMaterial();

    /** Copy {@code newMaterial}. Implementations reject null/air and a second apply while bound. */
    boolean applyMaterial(BlockState newMaterial);

    /** Drop the copied material and go back to the plain shell. */
    boolean resetMaterial();
}
