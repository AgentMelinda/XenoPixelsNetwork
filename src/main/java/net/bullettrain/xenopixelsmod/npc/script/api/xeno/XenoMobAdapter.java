package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import xenoapi.npcs.api.IPos;
import xenoapi.npcs.api.entity.IAnimal;
import xenoapi.npcs.api.entity.IMob;
import xenoapi.npcs.api.entity.IMonster;
import xenoapi.npcs.api.entity.IVillager;

/** Navigation over ordinary mobs, using the same bounds as native NPC navigation. */
public class XenoMobAdapter<T extends Mob> extends XenoLivingAdapter<T> implements IMob<T> {
    public XenoMobAdapter(T entity) { super(entity); }

    @Override public boolean isNavigating() { return entity.getNavigation().isInProgress(); }

    @Override public void clearNavigation() {
        serverThread();
        entity.getNavigation().stop();
    }

    @Override public void navigateTo(double x, double y, double z, double speed) {
        validateNavigation(x, y, z, speed, entity.distanceToSqr(x, y, z));
        serverThread();
        entity.getNavigation().moveTo(x, y, z, speed);
    }

    static void validateNavigation(double x, double y, double z, double speed, double distanceSquared) {
        XenoApiAdapters.requireFinite("IMob.navigateTo", x, y, z, speed, distanceSquared);
        if (speed <= 0 || speed > XenoNpcAdapter.MAX_NAVIGATION_SPEED) {
            throw new IllegalArgumentException("IMob.navigateTo: speed must be in (0, 3]");
        }
        if (distanceSquared < 0 || distanceSquared > XenoNpcAdapter.MAX_NAVIGATION_DISTANCE
                * XenoNpcAdapter.MAX_NAVIGATION_DISTANCE) {
            throw new IllegalArgumentException("IMob.navigateTo: target must be within 256 blocks");
        }
    }

    @Override public IPos getNavigationPath() {
        var path = entity.getNavigation().getPath();
        if (path == null || path.isDone()) return null;
        var end = path.getTarget();
        return end == null ? null : new XenoPosAdapter(end);
    }

    @Override public void jump() {
        serverThread();
        entity.getJumpControl().jump();
    }

    static final class AnimalView extends XenoMobAdapter<Animal> implements IAnimal<Animal> {
        AnimalView(Animal entity) { super(entity); }
    }

    static final class MonsterView extends XenoMobAdapter<Mob> implements IMonster<Mob> {
        MonsterView(Mob entity) { super(entity); }
    }

    static final class VillagerView extends XenoMobAdapter<AbstractVillager> implements IVillager<AbstractVillager> {
        VillagerView(AbstractVillager entity) { super(entity); }
    }
}
