package net.bullettrain.xenopixelsmod.block.entity;

import net.bullettrain.xenopixelsmod.block.custom.ShipThrusterBlock;
import net.bullettrain.xenopixelsmod.config.XenoPerfConfig;
import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import net.bullettrain.xenopixelsmod.vs.XenoThrusterControl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/**
 * Ship thruster BE — hot-path optimized for fleets of idle thrusters on VS hulls.
 * <ul>
 *   <li>Idle: early-out, no world queries (redstone from neighborChanged)</li>
 *   <li>Active: ship resolve by id, phys force every N ticks, client-only plumes</li>
 *   <li>Guidance-owned: visual only (ballistic uses CoM thrust)</li>
 * </ul>
 */
public class ShipThrusterBlockEntity extends BlockEntity {
    private static final double LEGACY_DEFAULT_MAX_FORCE = 80_000.0;
    public static final double DEFAULT_MAX_FORCE = 1_200_000.0;

    private double power;
    private double ccPower = -1; // -1 = redstone; else 0..1
    private double maxForce = DEFAULT_MAX_FORCE;
    private boolean soundPlayed;
    private @Nullable BlockPos pairedGuidance;
    private boolean guidanceOwned;
    private boolean forceCleared = true;
    private String cachedKey;
    private long cachedShipId = Long.MIN_VALUE;
    /** Ship whose transient attachment currently owns this thruster's force entry. */
    private long registeredForceShipId = -1L;
    private int shipLookupCooldown;
    private double lastPushedPower = -1;
    private int lastPushedFx, lastPushedFy, lastPushedFz;
    /** From neighborChanged — never hasNeighborSignal on idle path. */
    private boolean cachedRedstone;
    private static final int PHYS_PUSH_INTERVAL = 3;

    public ShipThrusterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIP_THRUSTER.get(), pos, state);
    }

    public @Nullable BlockPos getPairedGuidance() {
        return pairedGuidance;
    }

    public boolean isPaired() {
        return pairedGuidance != null;
    }

    public boolean isGuidanceOwned() {
        return guidanceOwned;
    }

    public void setPairedGuidance(@Nullable BlockPos guidancePos) {
        this.pairedGuidance = guidancePos == null ? null : guidancePos.immutable();
        if (pairedGuidance == null) guidanceOwned = false;
        setChanged();
        sync();
    }

    public void setGuidanceOwned(boolean owned) {
        this.guidanceOwned = owned;
        if (!owned) {
            ccPower = -1;
            power = 0;
            soundPlayed = false;
            clearPhysicsForce();
            forcePoweredState(false);
        }
        setChanged();
        sync();
    }

    /** Visual throttle during ballistic — no setChanged / no phys force. */
    public void setGuidanceThrottle(double throttle) {
        if (!guidanceOwned) return;
        double previous = ccPower;
        ccPower = Mth.clamp(throttle, 0.0, 1.0);
        if (ccPower < 0.01) {
            power = 0;
            clearPhysicsForce();
            forcePoweredState(false);
        } else {
            power = ccPower;
            forcePoweredState(true);
        }
        if (level != null && !level.isClientSide && Math.abs(previous - ccPower) > 0.001) {
            // Flight phase changes are sparse; explicitly sync the visual throttle.
            sync();
        }
    }

    public void forceShutdown() {
        guidanceOwned = false;
        ccPower = -1;
        power = 0;
        soundPlayed = false;
        forceCleared = false;
        clearPhysicsForce();
        forcePoweredState(false);
        setChanged();
        sync();
    }

    private void clearPhysicsForce() {
        if (forceCleared) return;
        if (registeredForceShipId < 0 && lastPushedPower < 0) {
            // No force was ever accepted by a ship attachment.
            forceCleared = true;
            return;
        }
        if (!(level instanceof ServerLevel sl)) {
            forceCleared = true;
            lastPushedPower = -1;
            return;
        }
        try {
            ServerSubLevel loaded = registeredForceShipId >= 0
                    ? VsShipHelper.getLoadedShipById(sl, registeredForceShipId)
                    : resolveShipCached(sl);
            if (loaded == null) {
                // The attachment may still contain the entry. Keep retrying instead of
                // claiming success and leaving permanent ghost thrust.
                return;
            }
            XenoThrusterControl c = XenoThrusterControl.get(loaded);
            if (c != null) c.removeThruster(thrusterKey());
            registeredForceShipId = -1L;
            forceCleared = true;
            lastPushedPower = -1;
        } catch (Throwable ignored) {
            // Retry on the next BE tick.
        }
    }

    private @Nullable ServerSubLevel resolveShipCached(ServerLevel sl) {
        if (cachedShipId >= 0) {
            ServerSubLevel byId = VsShipHelper.getLoadedShipById(sl, cachedShipId);
            if (byId != null) {
                if (--shipLookupCooldown <= 0) shipLookupCooldown = 60;
                return byId;
            }
            cachedShipId = Long.MIN_VALUE;
        }
        if (--shipLookupCooldown > 0) return null;
        shipLookupCooldown = 60;
        ServerSubLevel loaded = VsShipHelper.getLoadedShipAtFast(sl, worldPosition);
        cachedShipId = loaded != null ? VsShipHelper.getShipId(loaded) : -1L;
        if (cachedShipId < 0) shipLookupCooldown = 40;
        return loaded;
    }

    private void forcePoweredState(boolean lit) {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (state.hasProperty(ShipThrusterBlock.POWERED) && state.getValue(ShipThrusterBlock.POWERED) != lit) {
            // Flag 2 = clients only; avoid neighbor updates (would re-enter thrusters)
            level.setBlock(worldPosition, state.setValue(ShipThrusterBlock.POWERED, lit), 2);
        }
    }

    public double getPower() {
        return power;
    }

    public double getMaxForce() {
        return maxForce;
    }

    public void setMaxForce(double force) {
        this.maxForce = Mth.clamp(force, 1_000.0, 2_000_000.0);
        setChanged();
    }

    public boolean isActive() {
        return power > 0.02;
    }

    public void setCcPower(double value) {
        if (value < 0) ccPower = -1;
        else ccPower = Mth.clamp(value, 0.0, 1.0);
        setChanged();
        sync();
    }

    public double getCcPower() {
        return ccPower;
    }

    public float cyclePreset() {
        double next = power <= 0.01 ? 0.25 : power + 0.25;
        if (next > 1.01) next = 0;
        setCcPower(next <= 0.01 ? 0 : next);
        return (float) (ccPower < 0 ? 0 : ccPower);
    }

    /** Called from block neighborChanged — only place that reads redstone. */
    public void onRedstoneChanged(boolean powered) {
        cachedRedstone = powered;
        redstoneInited = true;
    }

    private boolean redstoneInited;

    @Override
    public void onLoad() {
        super.onLoad();
        // Sync redstone once after chunk load (neighborChanged may not re-fire)
        if (level != null && !level.isClientSide && !redstoneInited) {
            cachedRedstone = level.hasNeighborSignal(worldPosition);
            redstoneInited = true;
        }
    }

    public void tick() {
        if (level == null) return;

        // --- Fully idle: zero work (no world queries) ---
        if (!guidanceOwned && ccPower < 0 && !cachedRedstone) {
            if (power > 0.001 || !forceCleared) {
                power = 0;
                forceCleared = false;
                clearPhysicsForce();
                forcePoweredState(false);
                soundPlayed = false;
            }
            return;
        }

        double desired;
        if (guidanceOwned) {
            desired = ccPower >= 0 ? ccPower : 0.0;
        } else if (cachedRedstone) {
            // Redstone is an unconditional full-power input even if a prior manual/CC
            // preset remains stored on the block.
            desired = 1.0;
        } else if (ccPower >= 0) {
            desired = ccPower;
        } else {
            desired = 0.0;
        }

        if (desired < 0.01) {
            if (power > 0.001) {
                power = 0;
                if (!level.isClientSide) {
                    clearPhysicsForce();
                    forcePoweredState(false);
                }
                soundPlayed = false;
            }
            return;
        }

        // Snap-ish ramp (cheaper than every-tick smooth when stable)
        if (Math.abs(power - desired) < 0.02) {
            power = desired;
        } else {
            power += (desired - power) * (level.isClientSide ? 0.4 : 0.35);
        }
        if (power < 0.01) power = 0;
        forceCleared = false;

        if (level.isClientSide) {
            if (power > 0.05 && (level.getGameTime() & 3) == 0) {
                clientPlume();
            }
            return;
        }

        // Server: lit blockstate for clients; no server particles
        forcePoweredState(power > 0.05);

        // Ballistic owns CoM thrust — thruster map must stay empty
        if (guidanceOwned) {
            if (!forceCleared) clearPhysicsForce();
            return;
        }

        if (power <= 0.02) {
            clearPhysicsForce();
            return;
        }

        serverPhysics();
    }

    private void serverPhysics() {
        if (!(level instanceof ServerLevel sl)) return;

        // Redstone is a real standalone force by default. Operators can still disable it.
        if (!XenoPerfConfig.perfEnabled || !XenoPerfConfig.thrusterPhysForceEnabled) {
            if (!forceCleared) clearPhysicsForce();
            return;
        }

        // Optional player gate
        if (!XenoPerfConfig.thrusterForceAlways
                && !playerNear(sl, XenoPerfConfig.thrusterForcePlayerRange)) {
            if (!forceCleared) clearPhysicsForce();
            return;
        }

        long gt = sl.getGameTime();
        int stagger = (worldPosition.getX() * 31 + worldPosition.getZ()) & 3;
        if (((gt + stagger) % PHYS_PUSH_INTERVAL) != 0) return;

        Direction exhaust = getBlockState().getValue(ShipThrusterBlock.FACING);
        Direction thrust = exhaust.getOpposite();
        int ifx = thrust.getStepX();
        int ify = thrust.getStepY();
        int ifz = thrust.getStepZ();
        double fx = ifx * maxForce;
        double fy = ify * maxForce;
        double fz = ifz * maxForce;

        // Re-push periodically even if "stable" so map entry cannot expire silently
        boolean same = ifx == lastPushedFx && ify == lastPushedFy && ifz == lastPushedFz
                && Math.abs(power - lastPushedPower) < 0.03
                && lastPushedPower >= 0;
        if (same && (gt % 40) != stagger) {
            return;
        }

        try {
            ServerSubLevel loaded = resolveShipCached(sl);
            if (loaded == null) return;
            XenoThrusterControl control = XenoThrusterControl.getOrCreate(loaded);
            control.setThruster(thrusterKey(),
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    fx, fy, fz, power);
            registeredForceShipId = VsShipHelper.getShipId(loaded);
            lastPushedPower = power;
            lastPushedFx = ifx;
            lastPushedFy = ify;
            lastPushedFz = ifz;
            forceCleared = false;
            if (!soundPlayed && power > 0.4) {
                sl.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                        0.2f, 0.75f + (float) power * 0.25f);
                soundPlayed = true;
            }
        } catch (Throwable ignored) {
        }
    }

    private void clientPlume() {
        if (level == null || power <= 0.05) return;
        Direction exhaust = getBlockState().getValue(ShipThrusterBlock.FACING);
        RandomSource rng = level.getRandom();
        double ox = worldPosition.getX() + 0.5 + exhaust.getStepX() * 0.55;
        double oy = worldPosition.getY() + 0.5 + exhaust.getStepY() * 0.55;
        double oz = worldPosition.getZ() + 0.5 + exhaust.getStepZ() * 0.55;
        double speed = 0.12 + power * 0.35;
        double jx = (rng.nextDouble() - 0.5) * 0.12;
        double jy = (rng.nextDouble() - 0.5) * 0.12;
        double jz = (rng.nextDouble() - 0.5) * 0.12;
        level.addParticle(ParticleTypes.FLAME,
                ox + jx, oy + jy, oz + jz,
                exhaust.getStepX() * speed, exhaust.getStepY() * speed, exhaust.getStepZ() * speed);
        if (rng.nextFloat() < 0.3f * power) {
            level.addParticle(ParticleTypes.SMOKE,
                    ox, oy, oz,
                    exhaust.getStepX() * speed * 0.5,
                    exhaust.getStepY() * speed * 0.5,
                    exhaust.getStepZ() * speed * 0.5);
        }
    }

    private boolean playerNear(ServerLevel sl, double range) {
        double r2 = range * range;
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 0.5;
        double z = worldPosition.getZ() + 0.5;
        try {
            ServerSubLevel loaded = resolveShipCached(sl);
            if (loaded != null) {
                Vector3d world = loaded.logicalPose().transformPosition(new Vector3d(x, y, z));
                x = world.x;
                y = world.y;
                z = world.z;
            }
        } catch (Throwable ignored) {
            // Ground blocks already use world coordinates.
        }
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(x, y, z) <= r2) return true;
        }
        return false;
    }

    private String thrusterKey() {
        if (cachedKey == null) {
            cachedKey = (level != null ? level.dimension().location() : "u")
                    + "|" + worldPosition.asLong();
        }
        return cachedKey;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) {
            try {
                long ownerId = registeredForceShipId >= 0 ? registeredForceShipId : cachedShipId;
                ServerSubLevel loaded = ownerId >= 0
                        ? VsShipHelper.getLoadedShipById(sl, ownerId)
                        : VsShipHelper.getLoadedShipAtFast(sl, worldPosition);
                if (loaded != null) {
                    XenoThrusterControl c = XenoThrusterControl.get(loaded);
                    if (c != null) c.removeThruster(thrusterKey());
                }
            } catch (Throwable ignored) {
            }
        }
        super.setRemoved();
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Guidance ownership and its visual throttle are runtime-only because the
        // ballistic controller attachment is transient and cannot resume after reload.
        tag.putDouble("Power", guidanceOwned ? 0.0 : power);
        tag.putDouble("CcPower", guidanceOwned ? -1.0 : ccPower);
        tag.putDouble("MaxForce", maxForce);
        if (pairedGuidance != null) tag.putLong("PairedG", pairedGuidance.asLong());
    }

    @Override
    public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        power = tag.getDouble("Power");
        ccPower = tag.contains("CcPower") ? tag.getDouble("CcPower") : -1;
        maxForce = tag.contains("MaxForce") ? tag.getDouble("MaxForce") : DEFAULT_MAX_FORCE;
        if (Math.abs(maxForce - LEGACY_DEFAULT_MAX_FORCE) < 0.5) maxForce = DEFAULT_MAX_FORCE;
        pairedGuidance = tag.contains("PairedG") ? BlockPos.of(tag.getLong("PairedG")) : null;
        if (tag.contains("GuidedClient")) {
            // Network update tag: retain runtime visual state on the client only.
            guidanceOwned = tag.getBoolean("GuidedClient");
        } else {
            boolean staleGuidance = tag.getBoolean("Guided"); // migrate older disk saves
            guidanceOwned = false;
            if (staleGuidance) {
                power = 0;
                ccPower = -1;
            }
        }
        forceCleared = true;
        registeredForceShipId = -1L;
        cachedRedstone = power > 0.05 && ccPower < 0;
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        // Disk persistence deliberately clears runtime guidance, but clients still need
        // the live visual throttle for plumes and the powered model.
        tag.putDouble("Power", power);
        tag.putDouble("CcPower", ccPower);
        tag.putBoolean("GuidedClient", guidanceOwned);
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
