package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.script.api.ScriptWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import xenoapi.npcs.api.CustomNPCsException;
import xenoapi.npcs.api.IDimension;
import xenoapi.npcs.api.INbt;
import xenoapi.npcs.api.IPos;
import xenoapi.npcs.api.IScoreboard;
import xenoapi.npcs.api.IWorld;
import xenoapi.npcs.api.block.IBlock;
import xenoapi.npcs.api.entity.IEntity;
import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.entity.data.IData;
import xenoapi.npcs.api.item.IItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A server level as XenoAPI's {@link IWorld}; time, weather, entities, blocks and effects. */
public final class XenoWorldAdapter implements IWorld {
    /** Entity searches and effects are bounded so one call cannot scan or touch unbounded space. */
    static final int MAX_SEARCH_RANGE = 128;
    static final float MAX_EXPLOSION = 16.0f;
    static final int MAX_PARTICLES = 1000;

    final ServerLevel level;

    public XenoWorldAdapter(ServerLevel level) {
        this.level = Objects.requireNonNull(level);
    }

    private void serverThread() {
        XenoApiAdapters.requireServerThread(level);
    }

    private ScriptWorld scriptWorld() {
        return new ScriptWorld(level);
    }

    // ------------------------------------------------------------------ entities

    private List<Entity> nearby(double x, double y, double z, int range, int type) {
        if (range < 1 || range > MAX_SEARCH_RANGE) {
            throw new IllegalArgumentException("IWorld: range must be 1-" + MAX_SEARCH_RANGE);
        }
        AABB box = new AABB(x - range, y - range, z - range, x + range, y + range, z + range);
        double rangeSq = (double) range * range;
        return level.getEntities((Entity) null, box, entity -> entity.isAlive()
                && entity.distanceToSqr(x, y, z) <= rangeSq && XenoApiAdapters.typeOf(entity, type));
    }

    @Override
    public IEntity[] getNearbyEntities(int x, int y, int z, int range, int type) {
        return nearby(x + 0.5, y + 0.5, z + 0.5, range, type).stream()
                .map(XenoApiAdapters::wrap).toArray(IEntity[]::new);
    }

    @Override
    public IEntity[] getNearbyEntities(IPos pos, int range, int type) {
        Objects.requireNonNull(pos, "IWorld.getNearbyEntities: pos");
        return getNearbyEntities(pos.getX(), pos.getY(), pos.getZ(), range, type);
    }

    @Override
    public IEntity getClosestEntity(int x, int y, int z, int range, int type) {
        double cx = x + 0.5, cy = y + 0.5, cz = z + 0.5;
        return nearby(cx, cy, cz, range, type).stream()
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(cx, cy, cz)))
                .map(XenoApiAdapters::wrap).orElse(null);
    }

    @Override
    public IEntity getClosestEntity(IPos pos, int range, int type) {
        Objects.requireNonNull(pos, "IWorld.getClosestEntity: pos");
        return getClosestEntity(pos.getX(), pos.getY(), pos.getZ(), range, type);
    }

    @Override
    public IEntity[] getAllEntities(int type) {
        List<IEntity> out = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity != null && entity.isAlive() && XenoApiAdapters.typeOf(entity, type)) {
                out.add(XenoApiAdapters.wrap(entity));
            }
        }
        return out.toArray(IEntity[]::new);
    }

    @Override
    public IPlayer getPlayer(String name) {
        if (name == null) return null;
        for (ServerPlayer player : level.players()) {
            if (player.getGameProfile().getName().equalsIgnoreCase(name)) return (IPlayer) XenoApiAdapters.wrap(player);
        }
        return null;
    }

    @Override
    public IPlayer[] getAllPlayers() {
        return level.players().stream().map(p -> (IPlayer) XenoApiAdapters.wrap(p)).toArray(IPlayer[]::new);
    }

    @Override
    public IEntity getEntity(String uuid) {
        UUID id;
        try {
            id = UUID.fromString(Objects.requireNonNull(uuid, "uuid"));
        } catch (IllegalArgumentException e) {
            throw new CustomNPCsException("Invalid UUID: %s", uuid);
        }
        return XenoApiAdapters.wrap(level.getEntity(id));
    }

    /** Creates, but does not add, an entity; call {@code spawn()} or {@link #spawnEntity}. */
    @Override
    public IEntity createEntity(String id) {
        ResourceLocation key = id == null ? null : ResourceLocation.tryParse(id);
        EntityType<?> type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
        if (type == null) throw new CustomNPCsException("Unknown entity id: %s", id);
        if (type == EntityType.PLAYER) throw new CustomNPCsException("Players cannot be created");
        Entity entity = type.create(level);
        if (entity == null) throw new CustomNPCsException("Entity %s cannot be created", id);
        return XenoApiAdapters.wrap(entity);
    }

    @Override
    public IEntity createEntityFromNBT(INbt nbt) {
        CompoundTag tag = XenoApiAdapters.unwrap(nbt).copy();
        tag.remove("UUID");
        Entity entity = EntityType.create(tag, level).orElse(null);
        if (entity == null || entity instanceof ServerPlayer) {
            throw new CustomNPCsException("NBT does not describe a creatable entity");
        }
        return XenoApiAdapters.wrap(entity);
    }

    @Override
    public void spawnEntity(IEntity entity) {
        Entity created = XenoApiAdapters.unwrap(entity);
        if (created == null) throw new IllegalArgumentException("IWorld.spawnEntity: entity cannot be null");
        if (created.level() != level) throw new IllegalArgumentException("IWorld.spawnEntity: entity belongs to another level");
        if (created.isAddedToLevel()) throw new IllegalStateException("IWorld.spawnEntity: entity is already in the world");
        serverThread();
        level.addFreshEntity(created);
    }

    // ------------------------------------------------------------------ time / weather

    @Override public long getTime() { return scriptWorld().getTime(); }
    @Override public long getTotalTime() { return scriptWorld().getTotalTime(); }

    @Override
    public void setTime(long time) {
        if (time < 0) throw new IllegalArgumentException("IWorld.setTime: time cannot be negative");
        serverThread();
        level.setDayTime(time);
    }

    @Override public boolean isDay() { return scriptWorld().isDay(); }
    @Override public boolean isRaining() { return scriptWorld().isRaining(); }

    @Override
    public void setRaining(boolean raining) {
        serverThread();
        if (raining) level.setWeatherParameters(0, 6000, true, false);
        else level.setWeatherParameters(6000, 0, false, false);
    }

    @Override
    public void thunderStrike(double x, double y, double z) {
        XenoApiAdapters.requireFinite("IWorld.thunderStrike", x, y, z);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        serverThread();
        bolt.moveTo(x, y, z);
        level.addFreshEntity(bolt);
    }

    // ------------------------------------------------------------------ blocks

    private static Block block(String name) {
        ResourceLocation id = name == null ? null : ResourceLocation.tryParse(name);
        Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) throw new CustomNPCsException("Unknown block id: %s", name);
        return block;
    }

    private BlockPos loaded(int x, int y, int z, String method) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
            throw new IllegalArgumentException(method + ": position is not loaded or is outside the build height");
        }
        return pos;
    }

    /** Places the block's default state; {@code meta} has no 1.21 meaning and is ignored. */
    @Override
    public void setBlock(int x, int y, int z, String name, int meta) {
        Block block = block(name);
        BlockPos pos = loaded(x, y, z, "IWorld.setBlock");
        serverThread();
        level.setBlockAndUpdate(pos, block.defaultBlockState());
    }

    @Override
    public void removeBlock(int x, int y, int z) {
        BlockPos pos = loaded(x, y, z, "IWorld.removeBlock");
        serverThread();
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public void removeBlock(IPos pos) {
        Objects.requireNonNull(pos, "IWorld.removeBlock: pos");
        removeBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Light at the block as a value between 0 and 1, as the reference documents. */
    @Override
    public float getLightValue(int x, int y, int z) {
        return level.getMaxLocalRawBrightness(new BlockPos(x, y, z)) / 15.0f;
    }

    @Override
    public int getRedstonePower(int x, int y, int z) {
        return level.getBestNeighborSignal(new BlockPos(x, y, z));
    }

    @Override
    public void triggerBlockUpdate(IPos pos) {
        Objects.requireNonNull(pos, "IWorld.triggerBlockUpdate: pos");
        BlockPos at = loaded(pos.getX(), pos.getY(), pos.getZ(), "IWorld.triggerBlockUpdate");
        serverThread();
        level.updateNeighborsAt(at, level.getBlockState(at).getBlock());
    }

    @Override
    public String getBiomeName(int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return level.getBiome(new BlockPos(x, y, z)).unwrapKey()
                .map(key -> key.location().toString()).orElse("");
    }

    @Override
    public BlockPos getMCBlockPos(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    // ------------------------------------------------------------------ effects

    @Override
    public void playSoundAt(IPos pos, String sound, float volume, float pitch) {
        Objects.requireNonNull(pos, "IWorld.playSoundAt: pos");
        var event = XenoApiAdapters.sound(sound);
        XenoApiAdapters.requireFinite("IWorld.playSoundAt", volume, pitch);
        serverThread();
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, event, SoundSource.NEUTRAL,
                Math.max(0.0f, Math.min(4.0f, volume)), Math.max(0.5f, Math.min(2.0f, pitch)));
    }

    /** Only parameterless particle types (such as {@code minecraft:flame}) can be named. */
    @Override
    public void spawnParticle(String particle, double x, double y, double z, double dx, double dy, double dz,
                              double speed, int count) {
        ResourceLocation id = particle == null ? null : ResourceLocation.tryParse(particle);
        var type = id == null ? null : BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
        if (!(type instanceof SimpleParticleType simple)) {
            throw new CustomNPCsException("Unknown or parameterised particle: %s", particle);
        }
        XenoApiAdapters.requireFinite("IWorld.spawnParticle", x, y, z, dx, dy, dz, speed);
        if (count < 0 || count > MAX_PARTICLES) throw new IllegalArgumentException("IWorld.spawnParticle: count must be 0-" + MAX_PARTICLES);
        serverThread();
        level.sendParticles((ParticleOptions) simple, x, y, z, count, dx, dy, dz, speed);
    }

    @Override
    public void broadcast(String message) {
        if (net.bullettrain.xenopixelsmod.compat.npc.NpcScriptSay.sanitize(message) == null) {
            throw new IllegalArgumentException("IWorld.broadcast: message must be non-empty and within the chat limit");
        }
        serverThread();
        scriptWorld().broadcast(message);
    }

    @Override
    public void explode(double x, double y, double z, float range, boolean fire, boolean grief) {
        XenoApiAdapters.requireFinite("IWorld.explode", x, y, z, range);
        if (range <= 0 || range > MAX_EXPLOSION) throw new IllegalArgumentException("IWorld.explode: range must be in (0, " + MAX_EXPLOSION + "]");
        serverThread();
        level.explode(null, x, y, z, range, fire, grief ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
    }

    // ------------------------------------------------------------------ items

    @Override
    public IItemStack createItem(String name, int size) {
        var item = XenoApiAdapters.item(name);
        if (size < 1 || size > item.getDefaultMaxStackSize()) {
            throw new CustomNPCsException("Stack size must be 1-%d", item.getDefaultMaxStackSize());
        }
        return XenoApiAdapters.wrap(new ItemStack(item, size));
    }

    @Override
    public IItemStack createItemFromNbt(INbt nbt) {
        CompoundTag tag = XenoApiAdapters.unwrap(nbt);
        ItemStack stack = ItemStack.parseOptional(level.registryAccess(), tag);
        if (stack.isEmpty()) throw new CustomNPCsException("NBT does not describe an item");
        return XenoApiAdapters.wrap(stack);
    }

    // ------------------------------------------------------------------ identity

    @Override public String getName() { return level.dimension().location().toString(); }

    @Override public IBlock getBlock(int x, int y, int z) { return new XenoBlockAdapter(level, new BlockPos(x, y, z)); }

    @Override
    public IBlock getBlock(IPos pos) {
        Objects.requireNonNull(pos, "IWorld.getBlock: pos");
        return getBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public ServerLevel getMCLevel() {
        throw XenoApiAdapters.unsupported("IWorld.getMCLevel (raw handles are not exposed)");
    }

    /** Two adapters are equal when they wrap the same level. */
    @Override
    public boolean equals(Object other) {
        return other instanceof XenoWorldAdapter world && world.level == level;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(level);
    }

    // ------------------------------------------------------------------ blocks / dimension / scoreboard

    @Override
    public IBlock setBlock(IPos pos, String name) {
        Objects.requireNonNull(pos, "IWorld.setBlock: pos");
        return getBlock(pos).setBlock(name);
    }

    /** The dimension's id, such as {@code minecraft:overworld}. */
    @Override
    public IDimension getDimension() {
        String id = level.dimension().location().toString();
        return () -> id;
    }

    /** The server scoreboard, which vanilla shares across every dimension. */
    @Override public IScoreboard getScoreboard() { return new XenoScoreboardAdapter(level.getScoreboard()); }

    /** Server-wide temp data, the same in every dimension (reference). */
    @Override
    public IData getTempdata() {
        serverThread();
        return XenoDataAdapter.ofView(() -> XenoBoundedData.temp(XenoWorldData.temp()));
    }

    /** Stored data persisted in one overworld SavedData. */
    @Override
    public IData getStoreddata() {
        XenoWorldData data = XenoWorldData.get(level.getServer());
        return XenoDataAdapter.ofView(() -> XenoBoundedData.stored(data::stored, data::storedChanged));
    }

    // ------------------------------------------------------------------ clones / spawn / trigger

    @Override
    public IEntity spawnClone(double x, double y, double z, int tab, String name) {
        return new XenoCloneHandler().spawn(x, y, z, tab, name, this);
    }

    @Override
    public IEntity getClone(int tab, String name) {
        return new XenoCloneHandler().get(tab, name, this);
    }

    /** The world spawn of this level. */
    @Override public IBlock getSpawnPoint() { return new XenoBlockAdapter(level, level.getSharedSpawnPos()); }

    @Override
    public void setSpawnPoint(IBlock block) {
        if (!(block instanceof XenoBlockAdapter target) || target.level() != level) {
            throw new IllegalArgumentException("IWorld.setSpawnPoint: block must be a native block of this world");
        }
        serverThread();
        level.setDefaultSpawnPos(target.blockPos(), level.getSharedSpawnAngle());
    }

    /** Fires {@code trigger} on the forge scripts (and Java listeners), with no entity or position. */
    @Override
    public void trigger(int id, Object... arguments) {
        XenoScriptTriggers.fire(level, null, null, id, arguments);
    }

    /** Block-break particles of the named block, as {@code spawnParticle} otherwise. */
    @Override
    public void spawnParticleBlock(String name, double x, double y, double z, double dx, double dy, double dz,
                                   double speed, int count) {
        ResourceLocation id = name == null ? null : ResourceLocation.tryParse(name);
        Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null || block == Blocks.AIR) throw new CustomNPCsException("Unknown block id: %s", name);
        XenoApiAdapters.requireFinite("IWorld.spawnParticleBlock", x, y, z, dx, dy, dz, speed);
        if (count < 0 || count > MAX_PARTICLES) throw new IllegalArgumentException("IWorld.spawnParticleBlock: count must be 0-" + MAX_PARTICLES);
        serverThread();
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                net.minecraft.core.particles.ParticleTypes.BLOCK, block.defaultBlockState()),
                x, y, z, count, dx, dy, dz, speed);
    }
}
