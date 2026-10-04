package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Sets off another mod's explosive as a missile warhead, the way that mod sets it off itself, so a
 * Ballistix nuke is a nuke and a Create Big Cannons HE shell is an HE shell.
 *
 * <p>{@link #detonate} returns false - or throws - when it could not; the missile then falls back to
 * a vanilla explosion, so a changed or missing mod never leaves a dud.
 */
public interface WarheadDetonator {
    /** Short name for logs. */
    String id();

    /** Whether this detonator knows the item. Cheap; called when a tube is loaded. */
    boolean accepts(ItemStack stack);

    /** Sets the explosive off at {@code context.position()}; true when it did. */
    boolean detonate(Context context);

    /** Where and what. {@code owner} is who launched the missile, when known. */
    record Context(ServerLevel level, Vec3 position, ItemStack warhead, Entity missile, Entity owner) {}
}
