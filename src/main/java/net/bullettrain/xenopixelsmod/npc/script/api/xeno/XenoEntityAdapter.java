package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import xenoapi.npcs.api.INbt;
import xenoapi.npcs.api.IPos;
import xenoapi.npcs.api.IRayTrace;
import xenoapi.npcs.api.IWorld;
import xenoapi.npcs.api.constants.EntitiesType;
import xenoapi.npcs.api.entity.IEntity;
import xenoapi.npcs.api.entity.IEntityItem;
import xenoapi.npcs.api.entity.data.IData;
import xenoapi.npcs.api.item.IItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Any server-side entity as XenoAPI's {@link IEntity}. Every call reads or writes the live entity;
 * nothing is cached. Operations with no native owner throw through
 * {@link XenoApiAdapters#unsupported} instead of pretending to work.
 */
public class XenoEntityAdapter<T extends Entity> implements IEntity<T> {
    /** Upper bound for knockback power and motion components a caller may apply in one call. */
    static final double MAX_MOTION = 10.0;
    /** Per-entity temp data; weak so an unloaded entity takes its data with it. Server thread only. */
    private static final java.util.Map<Entity, java.util.Map<String, Object>> TEMP = new java.util.WeakHashMap<>();
    private static final String STORED_DATA = "XenoScriptData";

    protected final T entity;

    public XenoEntityAdapter(T entity) {
        this.entity = Objects.requireNonNull(entity);
    }

    /** Mutations run on the logical server's own thread, as every native write does. */
    protected final void serverThread() {
        XenoApiAdapters.requireServerThread(entity.level());
    }

    // ------------------------------------------------------------------ position / body

    @Override public double getX() { return entity.getX(); }
    @Override public double getY() { return entity.getY(); }
    @Override public double getZ() { return entity.getZ(); }
    @Override public void setX(double x) { setPosition(x, entity.getY(), entity.getZ()); }
    @Override public void setY(double y) { setPosition(entity.getX(), y, entity.getZ()); }
    @Override public void setZ(double z) { setPosition(entity.getX(), entity.getY(), z); }
    @Override public int getBlockX() { return entity.getBlockX(); }
    @Override public int getBlockY() { return entity.getBlockY(); }
    @Override public int getBlockZ() { return entity.getBlockZ(); }
    @Override public IPos getPos() { return new XenoPosAdapter(entity.blockPosition()); }

    @Override
    public void setPos(IPos pos) {
        if (pos == null) throw new IllegalArgumentException("IEntity.setPos: pos cannot be null");
        setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    @Override
    public void setPosition(double x, double y, double z) {
        XenoApiAdapters.requireFinite("IEntity.setPosition", x, y, z);
        serverThread();
        entity.teleportTo(x, y, z);
    }

    @Override public float getRotation() { return entity.getYRot(); }

    @Override
    public void setRotation(float rotation) {
        XenoApiAdapters.requireFinite("IEntity.setRotation", rotation);
        serverThread();
        entity.setYRot(rotation);
        entity.setYHeadRot(rotation);
        entity.setYBodyRot(rotation);
    }

    @Override public float getPitch() { return entity.getXRot(); }

    @Override
    public void setPitch(float pitch) {
        XenoApiAdapters.requireFinite("IEntity.setPitch", pitch);
        serverThread();
        entity.setXRot(Math.max(-90.0f, Math.min(90.0f, pitch)));
    }

    @Override public float getHeight() { return entity.getBbHeight(); }
    @Override public float getEyeHeight() { return entity.getEyeHeight(); }
    @Override public float getWidth() { return entity.getBbWidth(); }

    // ------------------------------------------------------------------ riding

    @Override public IEntity getMount() { return XenoApiAdapters.wrap(entity.getVehicle()); }

    @Override
    public void setMount(IEntity mount) {
        Entity vehicle = XenoApiAdapters.unwrap(mount);
        serverThread();
        if (vehicle == null) entity.stopRiding();
        else entity.startRiding(vehicle, true);
    }

    @Override
    public IEntity[] getRiders() {
        return entity.getPassengers().stream().map(XenoApiAdapters::wrap).toArray(IEntity[]::new);
    }

    @Override
    public IEntity[] getAllRiders() {
        return java.util.stream.StreamSupport.stream(entity.getIndirectPassengers().spliterator(), false).map(XenoApiAdapters::wrap)
                .toArray(IEntity[]::new);
    }

    @Override
    public void addRider(IEntity rider) {
        Entity passenger = XenoApiAdapters.unwrap(rider);
        if (passenger == null) throw new IllegalArgumentException("IEntity.addRider: rider cannot be null");
        serverThread();
        passenger.startRiding(entity, true);
    }

    @Override
    public void clearRiders() {
        serverThread();
        entity.ejectPassengers();
    }

    // ------------------------------------------------------------------ movement

    /** {@code direction} is a yaw in degrees; the push follows that heading, as CustomNPCs does. */
    @Override
    public void knockback(int power, float direction) {
        XenoApiAdapters.requireFinite("IEntity.knockback", direction);
        double strength = Math.max(0.0, Math.min(MAX_MOTION, power * 0.5));
        serverThread();
        double yaw = Math.toRadians(direction);
        entity.push(-Math.sin(yaw) * strength, 0.1, Math.cos(yaw) * strength);
        entity.hurtMarked = true;
    }

    @Override public boolean isSneaking() { return entity.isShiftKeyDown(); }
    @Override public boolean isSprinting() { return entity.isSprinting(); }
    @Override public double getMotionX() { return entity.getDeltaMovement().x; }
    @Override public double getMotionY() { return entity.getDeltaMovement().y; }
    @Override public double getMotionZ() { return entity.getDeltaMovement().z; }

    @Override public void setMotionX(double motion) { setMotion(motion, getMotionY(), getMotionZ()); }
    @Override public void setMotionY(double motion) { setMotion(getMotionX(), motion, getMotionZ()); }
    @Override public void setMotionZ(double motion) { setMotion(getMotionX(), getMotionY(), motion); }

    private void setMotion(double x, double y, double z) {
        XenoApiAdapters.requireFinite("IEntity.setMotion", x, y, z);
        serverThread();
        entity.setDeltaMovement(new Vec3(clampMotion(x), clampMotion(y), clampMotion(z)));
        entity.hurtMarked = true;
    }

    private static double clampMotion(double value) {
        return Math.max(-MAX_MOTION, Math.min(MAX_MOTION, value));
    }

    // ------------------------------------------------------------------ state

    @Override public boolean inWater() { return entity.isInWater(); }
    @Override public boolean inLava() { return entity.isInLava(); }

    @Override
    public boolean inFire() {
        return entity.level().getBlockStatesIfLoaded(entity.getBoundingBox().deflate(0.001))
                .anyMatch(state -> state.is(net.minecraft.tags.BlockTags.FIRE));
    }

    @Override public boolean isAlive() { return entity.isAlive(); }
    @Override public long getAge() { return entity.tickCount; }
    @Override public boolean isBurning() { return entity.isOnFire(); }

    @Override
    public void setBurning(int seconds) {
        serverThread();
        if (seconds <= 0) entity.clearFire();
        else entity.igniteForSeconds(Math.min(seconds, 3600));
    }

    @Override
    public void extinguish() {
        serverThread();
        entity.clearFire();
    }

    @Override
    public void despawn() {
        serverThread();
        if (entity instanceof ServerPlayer) {
            throw new IllegalArgumentException("IEntity.despawn cannot remove a player");
        }
        entity.discard();
    }

    /** Adds an entity created by {@code IWorld.createEntity} or {@code NpcAPI.createNPC}. */
    @Override
    public void spawn() {
        if (!(entity.level() instanceof ServerLevel level)) {
            throw new IllegalStateException("IEntity.spawn needs a server level");
        }
        serverThread();
        if (entity.isAddedToLevel()) {
            throw new IllegalStateException("IEntity.spawn: entity is already in the world");
        }
        level.addFreshEntity(entity);
    }

    @Override
    public void kill() {
        serverThread();
        entity.kill();
    }

    @Override
    public void damage(float amount) {
        XenoApiAdapters.requireFinite("IEntity.damage", amount);
        if (amount <= 0) return;
        serverThread();
        entity.hurt(entity.damageSources().generic(), amount);
    }

    // ------------------------------------------------------------------ identity / type

    @Override
    public IWorld getWorld() {
        return entity.level() instanceof ServerLevel level ? XenoApiAdapters.wrap(level) : null;
    }

    @Override
    public String getTypeName() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    @Override
    public int getType() {
        if (entity instanceof ServerPlayer) return EntitiesType.PLAYER;
        if (entity instanceof XenoNpcEntity) return EntitiesType.NPC;
        if (entity instanceof AbstractVillager) return EntitiesType.VILLAGER;
        if (entity instanceof Enemy && entity instanceof LivingEntity) return EntitiesType.MONSTER;
        if (entity instanceof Animal) return EntitiesType.ANIMAL;
        if (entity instanceof LivingEntity) return EntitiesType.LIVING;
        if (entity instanceof ItemEntity) return EntitiesType.ITEM;
        if (entity instanceof AbstractArrow) return EntitiesType.ARROW;
        if (entity instanceof ThrowableProjectile) return EntitiesType.THROWABLE;
        if (entity instanceof Projectile) return EntitiesType.PROJECTILE;
        return EntitiesType.UNKNOWN;
    }

    /** True for the entity's own type and its broader families (any living entity is LIVING). */
    @Override
    public boolean typeOf(int type) {
        return XenoApiAdapters.typeOf(entity, type);
    }

    @Override public String getUUID() { return entity.getUUID().toString(); }

    @Override
    public String getName() {
        return entity.getName().getString();
    }

    @Override
    public void setName(String name) {
        String next = XenoApiAdapters.boundedText("IEntity.setName", name, 64);
        serverThread();
        entity.setCustomName(next.isEmpty() ? null : Component.literal(next));
    }

    @Override public boolean hasCustomName() { return entity.hasCustomName(); }
    @Override public String getEntityName() { return entity.getType().getDescription().getString(); }

    // ------------------------------------------------------------------ tags

    @Override public String[] getTags() { return entity.getTags().toArray(String[]::new); }

    @Override
    public void addTag(String tag) {
        String next = XenoApiAdapters.boundedText("IEntity.addTag", tag, 64);
        if (next.isEmpty()) throw new IllegalArgumentException("IEntity.addTag: tag cannot be blank");
        serverThread();
        entity.addTag(next);
    }

    @Override public boolean hasTag(String tag) { return tag != null && entity.getTags().contains(tag); }

    @Override
    public void removeTag(String tag) {
        if (tag == null) return;
        serverThread();
        entity.removeTag(tag);
    }

    // ------------------------------------------------------------------ data / NBT

    /** Temp data: any value, gone when the entity unloads (reference: "only until it's reloaded"). */
    @Override
    public IData getTempdata() {
        serverThread();
        return XenoDataAdapter.ofView(() -> XenoBoundedData.temp(
                TEMP.computeIfAbsent(entity, ignored -> new java.util.HashMap<>())));
    }

    /** Stored data: strings and numbers in the entity's persistent data, under the native NPC key. */
    @Override
    public IData getStoreddata() {
        return XenoDataAdapter.ofView(() -> XenoBoundedData.stored(
                () -> entity.getPersistentData().getCompound(STORED_DATA),
                tag -> entity.getPersistentData().put(STORED_DATA, tag)));
    }

    /** A detached snapshot of the entity's save data; writing to it changes nothing. */
    @Override
    public INbt getEntityNbt() {
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        return XenoApiAdapters.wrap(tag);
    }

    /**
     * The entity's extra persistent data, live, as CustomNPCs returns it: what a script writes here
     * is saved with the entity. {@link #getStoreddata()} remains the bounded, typed alternative.
     */
    @Override
    public INbt getNbt() {
        serverThread();
        return XenoApiAdapters.wrap(entity.getPersistentData());
    }

    /**
     * Loads a whole saved entity over this one, as {@code getEntityNbt} returns it. Refused for
     * players, whose save data also carries their inventory, position and game mode.
     */
    @Override
    public void setEntityNbt(INbt nbt) {
        if (entity instanceof ServerPlayer) throw new IllegalArgumentException("IEntity.setEntityNbt: not for players");
        CompoundTag tag = XenoApiAdapters.unwrap(nbt).copy();
        serverThread();
        entity.load(tag);
        if (entity instanceof XenoNpcEntity npc) {
            // The profile rides in persistent data; re-apply it so gear, stats and looks follow the load.
            var profile = net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.read(npc);
            net.bullettrain.xenopixelsmod.compat.npc.NpcCounterpartSync.apply(npc, profile, true);
            net.bullettrain.xenopixelsmod.compat.npc.NpcAppearanceFx.sync(npc);
        }
    }

    /** Raw handles would let a sandboxed script reach every Minecraft method. */
    @Override
    public T getMCEntity() {
        throw XenoApiAdapters.unsupported("IEntity.getMCEntity (raw handles are not exposed)");
    }

    // ------------------------------------------------------------------ items / identity / clones

    /** Drops a copy of the stack at this entity, as its own drop would. */
    @Override
    public IEntityItem<?> dropItem(IItemStack item) {
        ItemStack stack = XenoApiAdapters.unwrap(item).copy();
        if (stack.isEmpty()) throw new IllegalArgumentException("IEntity.dropItem: item cannot be empty");
        serverThread();
        ItemEntity dropped = entity.spawnAtLocation(stack);
        return dropped == null ? null : new XenoEntityItemAdapter(dropped);
    }

    /** Gives the entity a fresh random UUID and returns it. Refused for players, whose UUID is their account. */
    @Override
    public String generateNewUUID() {
        if (entity instanceof ServerPlayer) throw new IllegalArgumentException("IEntity.generateNewUUID: not for players");
        serverThread();
        UUID next = UUID.randomUUID();
        entity.setUUID(next);
        return next.toString();
    }

    /** Saves this NPC to the clone library; see {@code NpcAPI.getClones()}. */
    @Override
    public void storeAsClone(int tab, String name) {
        new XenoCloneHandler().set(tab, name, this);
    }

    // ------------------------------------------------------------------ ray traces

    static final double MAX_RAY = 256.0;

    private Vec3 rayEnd(String method, double distance) {
        if (!Double.isFinite(distance) || distance <= 0 || distance > MAX_RAY) {
            throw new IllegalArgumentException(method + ": distance must be in (0, " + (int) MAX_RAY + "]");
        }
        return entity.getEyePosition().add(entity.getLookAngle().scale(distance));
    }

    private BlockHitResult clip(Vec3 end, boolean stopOnLiquid, boolean ignoreBlockWithoutBoundingBox) {
        return entity.level().clip(new ClipContext(entity.getEyePosition(), end,
                ignoreBlockWithoutBoundingBox ? ClipContext.Block.COLLIDER : ClipContext.Block.OUTLINE,
                stopOnLiquid ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, entity));
    }

    /** The first block along the look direction, or null when nothing is hit within {@code distance}. */
    @Override
    public IRayTrace rayTraceBlock(double distance, boolean stopOnLiquid, boolean ignoreBlockWithoutBoundingBox) {
        Vec3 end = rayEnd("IEntity.rayTraceBlock", distance);
        serverThread();
        BlockHitResult hit = clip(end, stopOnLiquid, ignoreBlockWithoutBoundingBox);
        if (hit.getType() == HitResult.Type.MISS || !(entity.level() instanceof ServerLevel level)) return null;
        BlockPos pos = hit.getBlockPos();
        int side = hit.getDirection().get3DDataValue();
        XenoBlockAdapter block = new XenoBlockAdapter(level, pos);
        return new IRayTrace() {
            @Override public IPos getPos() { return new XenoPosAdapter(pos); }
            @Override public xenoapi.npcs.api.block.IBlock getBlock() { return block; }
            @Override public int getSideHit() { return side; }
        };
    }

    /** Entities the look ray passes through before the first block, nearest first. */
    @Override
    public IEntity[] rayTraceEntities(double distance, boolean stopOnLiquid, boolean ignoreBlockWithoutBoundingBox) {
        Vec3 end = rayEnd("IEntity.rayTraceEntities", distance);
        serverThread();
        Vec3 start = entity.getEyePosition();
        BlockHitResult block = clip(end, stopOnLiquid, ignoreBlockWithoutBoundingBox);
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        final Vec3 stop = end;
        List<Entity> hits = new ArrayList<>();
        java.util.Map<Entity, Double> along = new java.util.HashMap<>();
        for (Entity other : entity.level().getEntities(entity, new AABB(start, stop).inflate(1.0))) {
            if (!other.isPickable() && !(other instanceof LivingEntity)) continue;
            var point = other.getBoundingBox().inflate(other.getPickRadius()).clip(start, stop);
            if (point.isEmpty()) continue;
            hits.add(other);
            along.put(other, start.distanceToSqr(point.get()));
        }
        hits.sort(java.util.Comparator.comparingDouble(along::get));
        return hits.stream().map(XenoApiAdapters::wrap).toArray(IEntity[]::new);
    }

    /** Vanilla client animations (reference): 0 swing main hand, 2 wake up (players only), 3 swing off hand. */
    @Override
    public void playAnimation(int type) {
        if (type != 0 && type != 2 && type != 3) throw new IllegalArgumentException("IEntity.playAnimation: type must be 0, 2 or 3");
        if (type == 2 && !(entity instanceof ServerPlayer)) throw new IllegalArgumentException("IEntity.playAnimation: type 2 is for players only");
        if (!(entity instanceof LivingEntity)) throw new IllegalArgumentException("IEntity.playAnimation needs a living entity");
        if (!(entity.level() instanceof ServerLevel level)) throw new IllegalStateException("IEntity.playAnimation needs a server level");
        serverThread();
        level.getChunkSource().broadcastAndSend(entity,
                new net.minecraft.network.protocol.game.ClientboundAnimatePacket(entity, type));
    }

    /** Two adapters are equal when they wrap the same live entity. */
    @Override
    public boolean equals(Object other) {
        return other instanceof XenoEntityAdapter<?> adapter && adapter.entity == entity;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(entity);
    }

    @Override
    public String toString() {
        return getName();
    }
}
