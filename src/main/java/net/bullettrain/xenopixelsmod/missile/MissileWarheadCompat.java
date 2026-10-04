package net.bullettrain.xenopixelsmod.missile;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Other mods' explosives as missile warheads, each set off by that mod's own code.
 *
 * <p>Reflective on purpose: none of these mods is a compile dependency, and each detonator is only
 * registered when its mod is loaded. Every symbol below was read with {@code javap} from the exact
 * jars on 2026-09-28:
 * <ul>
 *   <li>sable_tournament ({@code create-aeronautics-tournament-0.0.1.jar}):
 *       {@code AbstractExplosiveBlock.explode(ServerLevel, BlockPos)} - an explosion at the block
 *       centre with that block's strength, placed block not needed;</li>
 *   <li>Ballistix ({@code ballistix-1.21.1-1.0.13.jar}): {@code Blast.ITEM_TO_BLAST_MAP},
 *       {@code IBlast.createBlast(Level, BlockPos, Entity, Entity)} then
 *       {@code Blast.performExplosion()} - the same two calls Ballistix's own primed explosive
 *       makes, so nuclear, antimatter, EMP and the rest behave as themselves;</li>
 *   <li>Create Big Cannons ({@code createbigcannons-5.11.7+mc.1.21.1.jar}):
 *       {@code ProjectileBlock.getProjectile(Level, ItemStack)} and
 *       {@code FuzedBigCannonProjectile.setExplosionCountdown(int)} - the shell is placed at the
 *       impact point and its own tick detonates it when the countdown reaches zero.</li>
 * </ul>
 * A symbol that no longer resolves disables that detonator with one log line; the missile then
 * uses the vanilla explosion instead of failing.
 */
public final class MissileWarheadCompat {
    private MissileWarheadCompat() {}

    /** Registers a detonator for every supported mod that is loaded. Call once at common setup. */
    public static void register() {
        if (ModList.get().isLoaded("sable_tournament")) register(new SableTournament());
        if (ModList.get().isLoaded("ballistix")) register(new Ballistix());
        if (ModList.get().isLoaded("createbigcannons")) register(new CreateBigCannons());
    }

    private static void register(Reflective detonator) {
        if (detonator.ready()) {
            MissileWarhead.registerDetonator(detonator);
            XenoPixelsMod.LOGGER.info("Missile warheads: {} explosives supported", detonator.id());
        }
    }

    private static Block blockOf(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi ? bi.getBlock() : null;
    }

    /** Resolves its symbols once; a detonator that cannot resolve is never registered. */
    private abstract static class Reflective implements WarheadDetonator {
        private final boolean ready;

        Reflective() {
            boolean ok;
            try {
                resolve();
                ok = true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                XenoPixelsMod.LOGGER.warn("Missile warheads: {} support off ({}); its explosives use the vanilla blast",
                        id(), e.toString());
                ok = false;
            }
            ready = ok;
        }

        abstract void resolve() throws ReflectiveOperationException;

        boolean ready() {
            return ready;
        }
    }

    /** sable_tournament instant explosives (small, medium, large). */
    static final class SableTournament extends Reflective {
        private Class<?> explosiveBlock;
        private Method explode;

        @Override public String id() { return "sable_tournament"; }

        @Override
        void resolve() throws ReflectiveOperationException {
            explosiveBlock = Class.forName("org.anonymous.sable_tournament.block.explosive.AbstractExplosiveBlock");
            explode = explosiveBlock.getMethod("explode", ServerLevel.class, BlockPos.class);
        }

        @Override
        public boolean accepts(ItemStack stack) {
            Block block = blockOf(stack);
            return block != null && explosiveBlock.isInstance(block);
        }

        @Override
        public boolean detonate(Context c) {
            try {
                explode.invoke(blockOf(c.warhead()), c.level(), BlockPos.containing(c.position()));
                return true;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e.getCause() == null ? e : e.getCause());
            }
        }
    }

    /** Every Ballistix explosive the mod maps to a blast (blocks, and any other mapped item). */
    static final class Ballistix extends Reflective {
        private Map<?, ?> itemToBlast;
        private Method createBlast;
        private Method performExplosion;

        @Override public String id() { return "ballistix"; }

        @Override
        void resolve() throws ReflectiveOperationException {
            Class<?> blast = Class.forName("ballistix.common.blast.util.Blast");
            Class<?> iBlast = Class.forName("ballistix.api.blast.IBlast");
            Field map = blast.getField("ITEM_TO_BLAST_MAP");
            itemToBlast = (Map<?, ?>) map.get(null);
            createBlast = iBlast.getMethod("createBlast", Level.class, BlockPos.class, Entity.class, Entity.class);
            performExplosion = blast.getMethod("performExplosion");
        }

        private Object blastFor(Item item) {
            return itemToBlast.get(item);
        }

        @Override
        public boolean accepts(ItemStack stack) {
            return blastFor(stack.getItem()) != null;
        }

        @Override
        public boolean detonate(Context c) {
            Object type = blastFor(c.warhead().getItem());
            if (type == null) return false;
            try {
                Object blast = createBlast.invoke(type, c.level(), BlockPos.containing(c.position()), c.owner(), c.missile());
                if (blast == null) return false;
                performExplosion.invoke(blast);
                return true;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e.getCause() == null ? e : e.getCause());
            }
        }
    }

    /** Create Big Cannons fuzed shells: HE, shrapnel, fluid, smoke, AP, drop mortar. */
    static final class CreateBigCannons extends Reflective {
        private Class<?> projectileBlock;
        private Class<?> fuzedProjectile;
        private Method getProjectile;
        private Method setExplosionCountdown;

        @Override public String id() { return "createbigcannons"; }

        @Override
        void resolve() throws ReflectiveOperationException {
            projectileBlock = Class.forName("rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock");
            fuzedProjectile = Class.forName("rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBigCannonProjectile");
            getProjectile = projectileBlock.getMethod("getProjectile", Level.class, ItemStack.class);
            setExplosionCountdown = fuzedProjectile.getMethod("setExplosionCountdown", int.class);
        }

        @Override
        public boolean accepts(ItemStack stack) {
            Block block = blockOf(stack);
            return block != null && projectileBlock.isInstance(block);
        }

        @Override
        public boolean detonate(Context c) {
            try {
                Object shell = getProjectile.invoke(blockOf(c.warhead()), c.level(), c.warhead().copyWithCount(1));
                // Solid shot and other unfuzed rounds have no explosion of their own.
                if (!(shell instanceof Entity entity) || !fuzedProjectile.isInstance(shell)) return false;
                Vec3 at = c.position();
                entity.moveTo(at.x, at.y, at.z, 0f, 0f);
                entity.setDeltaMovement(Vec3.ZERO);
                setExplosionCountdown.invoke(shell, 1);
                return c.level().addFreshEntity(entity);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e.getCause() == null ? e : e.getCause());
            }
        }
    }
}
