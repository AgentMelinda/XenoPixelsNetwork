package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import xenoapi.npcs.api.IContainer;
import xenoapi.npcs.api.INbt;
import xenoapi.npcs.api.IPos;
import xenoapi.npcs.api.IWorld;
import xenoapi.npcs.api.block.IBlock;
import xenoapi.npcs.api.entity.IEntityLiving;
import xenoapi.npcs.api.entity.data.IData;

import java.util.Objects;

/** A block position as XenoAPI's IBlock. The state is read live; edits apply to the live level. */
public final class XenoBlockAdapter implements IBlock {
    private final ServerLevel level;
    private final BlockPos pos;

    public XenoBlockAdapter(ServerLevel level, BlockPos pos) {
        this.level = Objects.requireNonNull(level);
        this.pos = Objects.requireNonNull(pos).immutable();
    }

    /** Reads can load chunks, so they too stay on the server thread. */
    private BlockState state() {
        XenoApiAdapters.requireServerThread(level);
        return level.getBlockState(pos);
    }

    private BlockEntity blockEntity() {
        XenoApiAdapters.requireServerThread(level);
        return level.getBlockEntity(pos);
    }

    @Override public int getX() { return pos.getX(); }
    @Override public int getY() { return pos.getY(); }
    @Override public int getZ() { return pos.getZ(); }
    @Override public IPos getPos() { return new XenoPosAdapter(pos); }
    @Override public String getName() { return BuiltInRegistries.BLOCK.getKey(state().getBlock()).toString(); }
    @Override public String getDisplayName() { return state().getBlock().getName().getString(); }
    @Override public boolean isAir() { return state().isAir(); }
    /** True once the block at this position is gone (air). */
    @Override public boolean isRemoved() { return state().isAir(); }
    @Override public IWorld getWorld() { return XenoApiAdapters.wrap(level); }
    @Override public boolean hasTileEntity() { return blockEntity() != null; }
    @Override public boolean isContainer() { return blockEntity() instanceof Container; }

    @Override
    public IContainer getContainer() {
        return blockEntity() instanceof Container container ? XenoContainerAdapter.of(container) : null;
    }

    /** The property's current value as text, or null for a property this block does not have. */
    @Override
    public Object getProperty(String name) {
        BlockState state = state();
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) return String.valueOf(state.getValue(property));
        }
        return null;
    }

    @Override
    public String[] getProperties() {
        return state().getProperties().stream().map(Property::getName).toArray(String[]::new);
    }

    /** A detached snapshot of the block entity's save data; null without a block entity. */
    @Override
    public INbt getBlockEntityNBT() {
        BlockEntity be = blockEntity();
        return be == null ? null : XenoApiAdapters.wrap(be.saveWithFullMetadata(level.registryAccess()));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof XenoBlockAdapter block && block.level == level && block.pos.equals(pos);
    }

    @Override public int hashCode() { return pos.hashCode(); }

    /** The level this block is in. */
    ServerLevel level() { return level; }

    /** Its position. */
    BlockPos blockPos() { return pos; }

    private String dataKey() {
        return level.dimension().location() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // ------------------------------------------------------------------ editing

    /** Sets one block-state property from its text form (as {@link #getProperty} returns it). */
    @Override
    public void setProperty(String name, Object val) {
        BlockState state = state();
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                level.setBlock(pos, with(state, property, String.valueOf(val)), Block.UPDATE_ALL);
                return;
            }
        }
        throw new IllegalArgumentException("IBlock.setProperty: " + getName() + " has no property " + name);
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        T parsed = property.getValue(value).orElseThrow(() -> new IllegalArgumentException(
                "IBlock.setProperty: " + value + " is not a value of " + property.getName()));
        return state.setValue(property, parsed);
    }

    @Override
    public void remove() {
        state();
        level.removeBlock(pos, false);
    }

    /** Places the named block's default state here and returns this position. */
    @Override
    public IBlock setBlock(String name) {
        ResourceLocation id = name == null ? null : ResourceLocation.tryParse(name);
        Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) throw new xenoapi.npcs.api.CustomNPCsException("Unknown block id: %s", name);
        state();
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        return this;
    }

    /** Copies another block's full state (not its block entity) to this position. */
    @Override
    public IBlock setBlock(IBlock block) {
        if (!(block instanceof XenoBlockAdapter source)) {
            throw new IllegalArgumentException("IBlock.setBlock: block must be a native block");
        }
        BlockState copied = source.state();
        state();
        level.setBlock(pos, copied, Block.UPDATE_ALL);
        return this;
    }

    // ------------------------------------------------------------------ script data

    /** Per-position temp data, cleared when the server stops. */
    @Override
    public IData getTempdata() {
        state();
        return XenoDataAdapter.ofView(() -> XenoBoundedData.temp(XenoWorldData.blockTemp(dataKey())));
    }

    /** Per-position stored data, strings and numbers, kept in the world's XenoAPI save data. */
    @Override
    public IData getStoreddata() {
        XenoWorldData data = XenoWorldData.get(level.getServer());
        String key = dataKey();
        return XenoDataAdapter.ofView(() -> XenoBoundedData.stored(() -> data.block(key),
                tag -> data.blockChanged(key, tag)));
    }

    // ------------------------------------------------------------------ block entity

    /** Loads {@code nbt} into this block's block entity and sends the change to clients. */
    @Override
    public void setTileEntityNBT(INbt nbt) {
        CompoundTag tag = XenoApiAdapters.unwrap(nbt).copy();
        BlockEntity be = blockEntity();
        if (be == null) throw new IllegalStateException("IBlock.setTileEntityNBT: " + getName() + " has no block entity");
        be.loadWithComponents(tag, level.registryAccess());
        setChanged();
    }

    /** Marks the block entity changed and re-sends the block to clients. */
    @Override
    public void setChanged() {
        BlockState state = state();
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) be.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
    }

    /** A vanilla block event (a note block's note, a chest lid, a piston), as {@code Level.blockEvent}. */
    @Override
    public void blockEvent(int type, int data) {
        BlockState state = state();
        level.blockEvent(pos, state.getBlock(), type, data);
    }

    /** Right-clicks this block with an empty hand as {@code entity}, who must be a player. */
    @Override
    public void interact(int side, IEntityLiving entity) {
        if (!(XenoApiAdapters.unwrap(entity) instanceof net.minecraft.server.level.ServerPlayer player)) {
            throw new IllegalArgumentException("IBlock.interact: only a player can use a block");
        }
        if (side < 0 || side > 5) throw new IllegalArgumentException("IBlock.interact: side must be 0-5");
        BlockState state = state();
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                net.minecraft.core.Direction.from3DDataValue(side), pos, false);
        state.useWithoutItem(level, player, hit);
    }

    // ------------------------------------------------------------------ unsupported

    @Override public BlockEntity getMCTileEntity() { throw XenoApiAdapters.unsupported("IBlock.getMCTileEntity (raw handles are not exposed)"); }
    @Override public Block getMCBlock() { throw XenoApiAdapters.unsupported("IBlock.getMCBlock (raw handles are not exposed)"); }
    @Override public BlockState getMCBlockState() { throw XenoApiAdapters.unsupported("IBlock.getMCBlockState (raw handles are not exposed)"); }
}
