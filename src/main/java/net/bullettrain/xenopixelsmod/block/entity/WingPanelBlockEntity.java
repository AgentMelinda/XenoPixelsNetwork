package net.bullettrain.xenopixelsmod.block.entity;

import net.bullettrain.xenopixelsmod.block.custom.copycat.CopycatMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Holds a wing panel's deflection as a continuous angle, server-authoritative, with a
 * client-side exponential chase toward it for smooth rendering — the same shape as Control
 * Craft's {@code CompactFlapBlockEntity} (their {@code angle}/{@code tilt} fields plus a
 * client-side {@code LerpedFloat} chase), adapted without a Create dependency: this project only
 * has Create as an <i>optional</i> dependency, so referencing {@code LerpedFloat} directly here
 * would crash any install without Create. The exponential-chase math itself is a few lines and
 * needs nothing from Create — see {@link #tickClientAnimation()}.
 *
 * <p>This is what actually fixes the "flaps just snap" problem: the old design stored deflection
 * as a 3-state {@code NONE/UP/DOWN} blockstate property swapped via a fixed &plusmn;22.5&deg;
 * baked model rotation. A blockstate model swap is instant by construction — vanilla has no way
 * to render a frame <i>between</i> two block models. Storing a continuous angle here and
 * animating it in {@link net.bullettrain.xenopixelsmod.client.render.WingPanelBlockEntityRenderer}
 * is what makes in-between frames possible at all.
 */
public class WingPanelBlockEntity extends BlockEntity implements CopycatMaterial {

    /** Model-data key carrying the copied material to {@code CopycatWingModel}. Only the copycat
     * wing variants ever set it; a plain wing panel leaves it absent. */
    public static final ModelProperty<BlockState> MATERIAL_MODEL_PROPERTY = new ModelProperty<>();
    private static final String MATERIAL_TAG = "Material";

    /** The copied block state. Defaults to this block's own state == "unbound". */
    private BlockState material;

    /** How much of the remaining gap to the target closes per client tick. Matches the general
     * feel of Create's {@code LerpedFloat.Chaser.EXP} default rather than any exact constant. */
    private static final double CHASE_RATE = 0.35;
    /** Below this, snap the rest of the way rather than chase forever toward an unreachable limit. */
    private static final double SNAP_EPSILON_DEG = 0.02;
    /**
     * Smallest change in the commanded angle worth a client update. Roughly a twentieth of full
     * travel, which the exponential chase closes in a couple of ticks — below anything the eye
     * reads as a step, and well above the per-tick jitter of an attitude error signal.
     */
    private static final double SYNC_EPSILON_DEG = 1.5;

    /** Server-authoritative target, in degrees. Positive deflects one way, negative the other. */
    private double targetDeflectDeg;
    /** Last angle actually pushed to clients, so the sync threshold measures real drift. */
    private transient double lastSyncedDeflectDeg;

    /** Client-only animation state; never touched on the server. */
    private transient double clientDeflectDeg;
    private transient double clientPrevDeflectDeg;
    /** Last game-time the chase advanced. Shared by the block ticker and the BER so a Sable
     * hull (which often never runs client BE tickers) still animates, without double-stepping
     * when both paths fire in the same tick. */
    private transient long lastChaseGameTime = Long.MIN_VALUE;
    private transient boolean clientSeeded;

    public WingPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WING_PANEL.get(), pos, state);
        material = state;
    }

    public double getTargetDeflectDeg() {
        return targetDeflectDeg;
    }

    // ---- Copycat material (used only by the copycat wing variants) ----

    @Override
    public BlockState getMaterial() {
        return material;
    }

    @Override
    public boolean hasCustomMaterial() {
        return !material.is(getBlockState().getBlock());
    }

    @Override
    public boolean applyMaterial(BlockState newMaterial) {
        if (newMaterial == null || newMaterial.isAir() || hasCustomMaterial()) return false;
        if (level != null) {
            // Match Create / CopycatGlowstone: adopt an adjacent copycat's full state for the same
            // block so directional / connected-texture state stays consistent.
            for (Direction direction : Direction.values()) {
                if (level.getBlockEntity(worldPosition.relative(direction)) instanceof CopycatMaterial nb
                        && nb.getMaterial().is(newMaterial.getBlock())
                        && nb.hasCustomMaterial()) {
                    newMaterial = nb.getMaterial();
                    break;
                }
            }
        }
        return setMaterial(newMaterial);
    }

    @Override
    public boolean resetMaterial() {
        return setMaterial(getBlockState());
    }

    private boolean setMaterial(BlockState newMaterial) {
        if (newMaterial == null || newMaterial.equals(material)) return false;
        material = newMaterial;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(worldPosition);
        } else if (level != null && level.isClientSide) {
            redrawClient();
        }
        return true;
    }

    private void redrawClient() {
        requestModelDataUpdate();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_KNOWN_SHAPE);
    }

    @Override
    public ModelData getModelData() {
        return hasCustomMaterial() ? ModelData.of(MATERIAL_MODEL_PROPERTY, material) : ModelData.EMPTY;
    }

    /**
     * Called from the server flight tick.
     *
     * <p>The angle is stored unconditionally, but a client update only goes out once the target
     * has moved a visible amount. {@code sendBlockUpdated} is not free: it queues the position on
     * the chunk holder, which ships the block-entity payload <i>and</i> makes the client re-render
     * that section. A hull is allowed hundreds of panels and the flight tick revisits them several
     * times a second, so syncing every sub-degree twitch of a stabilizer error signal was a
     * re-render storm across the whole craft for motion nobody could see. The client-side chase in
     * {@link #tickClientAnimation()} smooths whatever it is told, so coarser updates cost nothing
     * visually — but a return to exactly centred is always sent, because that is the one value a
     * pilot notices being wrong when the controls are released.
     */
    public void setTargetDeflectDeg(double degrees) {
        if (Math.abs(degrees - targetDeflectDeg) < 1.0e-4) return;
        double previouslySynced = lastSyncedDeflectDeg;
        targetDeflectDeg = degrees;
        setChanged();
        if (level == null || level.isClientSide) return;
        boolean settled = degrees == 0.0 && previouslySynced != 0.0;
        if (!settled && Math.abs(degrees - previouslySynced) < SYNC_EPSILON_DEG) return;
        lastSyncedDeflectDeg = degrees;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /**
     * One client-tick step of the chase toward {@link #targetDeflectDeg}. Safe to call from both
     * the block ticker and the BER — only one step is taken per game tick.
     */
    public void tickClientAnimation() {
        chaseIfNewTick();
    }

    /** Smoothly interpolated angle for rendering, blending the last two ticks by partial ticks. */
    public double getAnimatedDeflectDeg(float partialTicks) {
        chaseIfNewTick();
        return Mth.lerp(partialTicks, clientPrevDeflectDeg, clientDeflectDeg);
    }

    private void chaseIfNewTick() {
        long gameTime = level != null ? level.getGameTime() : Long.MIN_VALUE;
        if (gameTime != Long.MIN_VALUE && gameTime == lastChaseGameTime) return;
        lastChaseGameTime = gameTime;
        if (!clientSeeded) {
            clientDeflectDeg = targetDeflectDeg;
            clientPrevDeflectDeg = targetDeflectDeg;
            clientSeeded = true;
            return;
        }
        clientPrevDeflectDeg = clientDeflectDeg;
        double remaining = targetDeflectDeg - clientDeflectDeg;
        if (Math.abs(remaining) < SNAP_EPSILON_DEG) {
            clientDeflectDeg = targetDeflectDeg;
        } else {
            clientDeflectDeg += remaining * CHASE_RATE;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("Deflect", targetDeflectDeg);
        if (hasCustomMaterial()) tag.put(MATERIAL_TAG, NbtUtils.writeBlockState(material));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Deliberately does not touch clientDeflectDeg/clientPrevDeflectDeg here: this method
        // also runs on every routine sync packet (handleUpdateTag delegates to it), not only the
        // very first load. Resetting the animation state on every sync would snap on every
        // update instead of chasing smoothly — exactly the bug this whole class exists to fix.
        targetDeflectDeg = tag.contains("Deflect") ? tag.getDouble("Deflect") : 0.0;
        BlockState previousMaterial = material;
        material = tag.contains(MATERIAL_TAG, CompoundTag.TAG_COMPOUND)
                ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound(MATERIAL_TAG))
                : getBlockState();
        if (material == null || material.isAir()) material = getBlockState();
        if (level != null && level.isClientSide && !material.equals(previousMaterial)) redrawClient();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) requestModelDataUpdate();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
