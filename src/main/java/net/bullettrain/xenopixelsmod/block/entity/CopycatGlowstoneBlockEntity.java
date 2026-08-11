package net.bullettrain.xenopixelsmod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.bullettrain.xenopixelsmod.block.custom.CopycatGlowstoneBlock;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

/** Stores and synchronizes the complete state used as the copycat material. */
public final class CopycatGlowstoneBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
    public static final ModelProperty<BlockState> MATERIAL_MODEL_PROPERTY = new ModelProperty<>();
    private static final String MATERIAL_TAG = "Material";
    private static final String ENERGY_TAG = "Energy";
    private BlockState material;
    private boolean energyDirty;
    private final CopycatEnergyStorage energy = new CopycatEnergyStorage(() -> energyDirty = true);

    public CopycatGlowstoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COPYCAT_GLOWSTONE.get(), pos, state);
        material = state;
    }

    public BlockState getMaterial() {
        return material;
    }

    public boolean hasCustomMaterial() {
        return !material.is(getBlockState().getBlock());
    }

    public CopycatEnergyStorage energy() {
        return energy;
    }

    /** Draws configured FE and mirrors active lighting into block state for vanilla light updates. */
    public void serverTick(Level level, BlockPos pos, BlockState state) {
        boolean shouldLight = !XenoServerConfig.copycatForgeEnergyEnabled
                || energy.consume(XenoServerConfig.copycatEnergyUseFePerTick);
        if (state.getValue(CopycatGlowstoneBlock.POWERED) != shouldLight) {
            level.setBlock(pos, state.setValue(CopycatGlowstoneBlock.POWERED, shouldLight),
                    Block.UPDATE_CLIENTS);
        }
        if (energyDirty && level.getGameTime() % 20L == 0L) {
            energyDirty = false;
            setChanged();
        }
    }

    /** Applies a new block type, or cycles its orientation when the same type is clicked again. */
    public boolean applyMaterial(BlockState newMaterial) {
        if (newMaterial == null || newMaterial.isAir()) return false;
        if (hasCustomMaterial()) return false;

        // Match Create: inherit the complete state of an adjacent copycat using the same
        // material. This keeps directional and connected-texture state consistent cheaply.
        if (level != null) {
            for (Direction direction : Direction.values()) {
                if (level.getBlockEntity(worldPosition.relative(direction))
                        instanceof CopycatGlowstoneBlockEntity neighbour
                        && neighbour.material.is(newMaterial.getBlock())) {
                    newMaterial = neighbour.material;
                    break;
                }
            }
        }
        return setMaterial(newMaterial);
    }

    public boolean setMaterial(BlockState newMaterial) {
        if (newMaterial == null || newMaterial.equals(material)) return false;
        material = newMaterial;
        markMaterialChanged();
        return true;
    }

    public boolean resetMaterial() {
        return setMaterial(getBlockState());
    }

    private void markMaterialChanged() {
        setChanged();
        if (level instanceof ServerLevel serverLevel)
            serverLevel.getChunkSource().blockChanged(worldPosition);
        else if (level != null && level.isClientSide)
            redrawClient();
    }

    /** One local chunk redraw after authoritative BE data arrives; no neighbour packet storm. */
    private void redrawClient() {
        requestModelDataUpdate();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_KNOWN_SHAPE);
    }

    @Override
    public ModelData getModelData() {
        return ModelData.of(MATERIAL_MODEL_PROPERTY, material);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(MATERIAL_TAG, NbtUtils.writeBlockState(material));
        tag.putInt(ENERGY_TAG, energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        BlockState previous = material;
        material = tag.contains(MATERIAL_TAG, CompoundTag.TAG_COMPOUND)
                ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound(MATERIAL_TAG))
                : getBlockState();
        if (material.isAir()) {
            material = getBlockState();
        }
        energy.setStored(tag.getInt(ENERGY_TAG));
        energyDirty = false;
        if (level != null && level.isClientSide && !material.equals(previous)) redrawClient();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) requestModelDataUpdate();
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
