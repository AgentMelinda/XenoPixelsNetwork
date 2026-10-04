package net.bullettrain.xenopixelsmod.missile;

import net.bullettrain.xenopixelsmod.item.ModItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Resolves a tube warhead item into vanilla explosion power. Other mods replace this by
 * cancelling {@link net.bullettrain.xenopixelsmod.api.event.MissileWarheadEvent}.
 */
public final class MissileWarhead {
    public static final float MAX_YIELD = 24.0f;

    private MissileWarhead() {
    }

    /** Other mods' explosives, set off as themselves. See {@link MissileWarheadCompat}. */
    private static final List<WarheadDetonator> DETONATORS = new CopyOnWriteArrayList<>();

    /** Adds a detonator; later ones are asked after earlier ones. */
    public static void registerDetonator(WarheadDetonator detonator) {
        if (detonator != null) DETONATORS.add(detonator);
    }

    static void clearDetonatorsForTest() {
        DETONATORS.clear();
    }

    public static boolean isWarhead(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.TNT) || stack.is(Items.TNT_MINECART)) return true;
        return stack.is(ModItemTags.MISSILE_WARHEADS) || detonatorFor(stack) != null;
    }

    private static WarheadDetonator detonatorFor(ItemStack stack) {
        for (WarheadDetonator d : DETONATORS) {
            try {
                if (d.accepts(stack)) return d;
            } catch (RuntimeException | LinkageError ignored) {
                // A detonator that cannot even look at the item simply does not claim it.
            }
        }
        return null;
    }

    /**
     * Lets another mod's explosive go off as itself. Every detonator that accepts the item is
     * tried in order; false means none did, and the caller uses the vanilla explosion.
     */
    public static boolean detonateNative(WarheadDetonator.Context context) {
        if (context == null || context.warhead() == null || context.warhead().isEmpty()) return false;
        for (WarheadDetonator d : DETONATORS) {
            try {
                if (d.accepts(context.warhead()) && d.detonate(context)) return true;
            } catch (RuntimeException | LinkageError e) {
                net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.warn(
                        "Missile warhead {} could not detonate {}: {} (vanilla blast instead)",
                        d.id(), context.warhead(), e.toString());
            }
        }
        return false;
    }

    public static float explosionPower(ItemStack stack, MissileSize size) {
        if (!isWarhead(stack)) return 0.0f;
        MissileSize tier = size == null ? MissileSize.MEDIUM : size;
        float base = stack.is(Items.TNT_MINECART) ? 4.0f : 4.0f;
        return Mth.clamp(base * tier.yieldScale(), 0.0f, MAX_YIELD);
    }
}
