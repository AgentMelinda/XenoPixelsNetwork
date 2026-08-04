package net.bullettrain.xenopixelsmod.block.entity;

import net.bullettrain.xenopixelsmod.block.custom.MissileTubeBlock;
import net.bullettrain.xenopixelsmod.compat.ballistix.MissileChunkLoadManager;
import net.bullettrain.xenopixelsmod.missile.BallisticMissileEntity;
import net.bullettrain.xenopixelsmod.missile.BallisticTrajectory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Launch tube — no continuous server tick when idle (cooldown uses scheduled block ticks).
 */
public class MissileTubeBlockEntity extends BlockEntity {
    private int cooldown;
    private boolean armed = true;

    public MissileTubeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MISSILE_TUBE.get(), pos, state);
    }

    public boolean isArmed() {
        return armed;
    }

    public void setArmed(boolean armed) {
        this.armed = armed;
        setChanged();
    }

    public void toggleArmed() {
        setArmed(!armed);
    }

    public int getCooldown() {
        return cooldown;
    }

    public boolean isOnCooldown() {
        return cooldown > 0;
    }

    /**
     * Called from scheduled block tick / rare BE ticker only while reloading.
     * Idle tubes do not tick every game tick.
     */
    public void tickCooldown() {
        if (cooldown <= 0) return;
        cooldown--;
        if (cooldown == 0) {
            setChanged();
        } else if (level instanceof ServerLevel sl) {
            // Re-schedule next tick only while cooling down
            sl.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    /**
     * @return true if a missile was spawned
     */
    public boolean tryLaunch(BlockPos target, double boostAccel, int boostTicks,
                             boolean terminal, float yield) {
        return tryLaunch(target, boostAccel, boostTicks, terminal, yield,
                net.bullettrain.xenopixelsmod.missile.BallisticCalculator.EARTH_GRAVITY,
                net.bullettrain.xenopixelsmod.missile.BallisticCalculator.DEFAULT_DRAG);
    }

    public boolean tryLaunch(BlockPos target, double boostAccel, int boostTicks,
                             boolean terminal, float yield, double gravitySi, double dragCoefficient) {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!armed || cooldown > 0 || target == null) return false;

        Direction face = getBlockState().getValue(MissileTubeBlock.FACING);
        Vec3 localSpawn = Vec3.atCenterOf(worldPosition)
                .add(face.getStepX() * 0.8, face.getStepY() * 0.8, face.getStepZ() * 0.8);

        Vec3 spawn = localSpawn;
        try {
            Vec3 world = net.bullettrain.xenopixelsmod.compat.ballistix.BallistixVs2Compat
                    .shipyardToWorld(sl, localSpawn);
            if (world != null) spawn = world;
        } catch (Throwable ignored) {
        }

        Vec3 tgt = Vec3.atCenterOf(target);
        Vec3 loft = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        if (loft.lengthSqr() < 1.0e-6) loft = new Vec3(0, 1, 0);

        var sol = BallisticTrajectory.solve(spawn, tgt, boostAccel, boostTicks);
        Vec3 blended = loft.normalize().scale(0.65).add(sol.direction().scale(0.35)).normalize();

        BallisticMissileEntity missile = BallisticMissileEntity.create(
                sl, spawn, tgt, blended, boostAccel, boostTicks, terminal, yield, null,
                gravitySi, dragCoefficient);
        if (missile == null) return false;

        sl.addFreshEntity(missile);
        cooldown = 20 * 8;
        armed = true;
        setChanged();
        // Cooldown without permanent BE ticker
        sl.scheduleTick(worldPosition, getBlockState().getBlock(), 1);

        sl.playSound(null, worldPosition, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 1.2f, 0.7f);
        // Chunk tickets: target (and pad only if forceChunksTargetOnly=false)
        MissileChunkLoadManager.forceNear(sl, spawn, 1, 20 * 20, MissileChunkLoadManager.Role.PAD);
        MissileChunkLoadManager.forceNear(sl, target, 1, 20 * 40, MissileChunkLoadManager.Role.TARGET);

        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Cooldown", cooldown);
        tag.putBoolean("Armed", armed);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        cooldown = tag.getInt("Cooldown");
        armed = !tag.contains("Armed") || tag.getBoolean("Armed");
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Resume cooldown after chunk load without permanent ticker
        if (cooldown > 0 && level instanceof ServerLevel sl && !level.isClientSide) {
            sl.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
