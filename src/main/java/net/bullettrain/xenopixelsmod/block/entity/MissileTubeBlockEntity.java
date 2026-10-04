package net.bullettrain.xenopixelsmod.block.entity;

import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.bullettrain.xenopixelsmod.block.custom.MissileTubeBlock;
import net.bullettrain.xenopixelsmod.item.custom.MissileItem;
import net.bullettrain.xenopixelsmod.missile.BallisticMissileEntity;
import net.bullettrain.xenopixelsmod.missile.MissileChunkLoadManager;
import net.bullettrain.xenopixelsmod.missile.MissileEject;
import net.bullettrain.xenopixelsmod.missile.MissileSize;
import net.bullettrain.xenopixelsmod.missile.MissileSpeed;
import net.bullettrain.xenopixelsmod.missile.MissileWarhead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Launch tube — no continuous server tick when idle (cooldown uses scheduled block ticks).
 * Slot 0 is the missile body; slot 1 is an optional warhead.
 */
public class MissileTubeBlockEntity extends BlockEntity {
    private int cooldown;
    private boolean armed = true;
    /** 0 = inherit speed from the firing guidance computer. */
    private int speedLevel;
    private int siloClearance = MissileEject.DEFAULT_CLEARANCE;
    /** 0 = unused; else keep ejecting until world Y reaches this. */
    private int launchWorldY;
    private ItemStack missile = ItemStack.EMPTY;
    private ItemStack warhead = ItemStack.EMPTY;

    public MissileTubeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MISSILE_TUBE.get(), pos, state);
    }

    public static boolean canLaunch(boolean armed, int cooldown, ItemStack body) {
        return armed && cooldown <= 0 && MissileItem.sizeOf(body) != null;
    }

    public boolean isArmed() {
        return armed;
    }

    public void setArmed(boolean armed) {
        this.armed = armed;
        markAndSync();
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

    public int getSpeedLevel() {
        return speedLevel;
    }

    public void setSpeedLevel(int speedLevel) {
        this.speedLevel = speedLevel <= 0 ? 0 : MissileSpeed.clampLevel(speedLevel);
        markAndSync();
    }

    public int getSiloClearance() {
        return siloClearance;
    }

    public void setSiloClearance(int siloClearance) {
        this.siloClearance = MissileEject.clampClearance(siloClearance);
        markAndSync();
    }

    public int getLaunchWorldY() {
        return launchWorldY;
    }

    public void setLaunchWorldY(int launchWorldY) {
        this.launchWorldY = MissileEject.clampLaunchWorldY(launchWorldY);
        markAndSync();
    }

    public ItemStack getMissile() {
        return missile;
    }

    public ItemStack getWarhead() {
        return warhead;
    }

    public boolean isLoaded() {
        return MissileItem.sizeOf(missile) != null;
    }

    public void tickCooldown() {
        if (cooldown <= 0) return;
        cooldown--;
        if (cooldown == 0) {
            markAndSync();
        } else if (level instanceof ServerLevel sl) {
            sl.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    public boolean tryInsert(Player player, ItemStack held) {
        if (held == null || held.isEmpty()) return false;
        if (MissileItem.isBody(held) && MissileItem.sizeOf(held) != null) {
            if (!missile.isEmpty()) return false;
            missile = held.copyWithCount(1);
            held.shrink(1);
            markAndSync();
            return true;
        }
        if (MissileWarhead.isWarhead(held)) {
            if (!warhead.isEmpty()) return false;
            warhead = held.copyWithCount(1);
            held.shrink(1);
            markAndSync();
            return true;
        }
        return false;
    }

    public boolean tryExtract(Player player) {
        ItemStack taken = !warhead.isEmpty() ? warhead : missile;
        if (taken.isEmpty()) return false;
        if (taken == warhead) {
            warhead = ItemStack.EMPTY;
        } else {
            missile = ItemStack.EMPTY;
        }
        if (!player.getInventory().add(taken)) {
            player.drop(taken, false);
        }
        markAndSync();
        return true;
    }

    public void dropContents() {
        if (level == null) return;
        if (!missile.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), missile);
            missile = ItemStack.EMPTY;
        }
        if (!warhead.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), warhead);
            warhead = ItemStack.EMPTY;
        }
    }

    public Component statusLine(Direction facing) {
        String body = missile.isEmpty() ? "empty" : missile.getHoverName().getString();
        String payload = warhead.isEmpty() ? "inert" : warhead.getHoverName().getString();
        String speed = speedLevel <= 0 ? "inherit" : Integer.toString(speedLevel);
        String height = launchWorldY <= 0 ? "auto" : Integer.toString(launchWorldY);
        return Component.literal(String.format(
                "§6Missile tube §7facing %s · %s · %s · %s · speed=%s · clear=%d · y=%s%s",
                facing.getName(),
                armed ? "§aARMED" : "§cSAFE",
                body,
                payload,
                speed,
                siloClearance,
                height,
                cooldown > 0 ? " §7(reload " + (cooldown / 20) + "s)" : ""));
    }

    /**
     * @return true if a missile was spawned
     */
    public boolean tryLaunch(BlockPos target, double boostAccel, int boostTicks,
                             boolean terminal, float yield) {
        return tryLaunch(target, boostAccel, boostTicks, terminal, yield,
                net.bullettrain.xenopixelsmod.missile.BallisticCalculator.EARTH_GRAVITY,
                net.bullettrain.xenopixelsmod.missile.BallisticCalculator.DEFAULT_DRAG,
                0, 0);
    }

    public boolean tryLaunch(BlockPos target, double boostAccel, int boostTicks,
                             boolean terminal, float yield, double gravitySi, double dragCoefficient) {
        return tryLaunch(target, boostAccel, boostTicks, terminal, yield,
                gravitySi, dragCoefficient, 0, 0);
    }

    public boolean tryLaunch(BlockPos target, double boostAccel, int boostTicks,
                             boolean terminal, float yield, double gravitySi, double dragCoefficient,
                             int apexY, int cruiseY) {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!canLaunch(armed, cooldown, missile) || target == null) return false;

        MissileSize size = MissileItem.sizeOf(missile);
        if (size == null) return false;

        double accel = boostAccel;
        int ticks = boostTicks;
        if (speedLevel > 0) {
            accel = MissileSpeed.accelFor(speedLevel);
            ticks = MissileSpeed.ticksFor(speedLevel);
        }
        float blast = MissileWarhead.explosionPower(warhead, size);

        Direction face = getBlockState().getValue(MissileTubeBlock.FACING);
        double along = getBlockState().is(ModBlocks.MISSILE_TUBE_FORK.get()) ? 1.05 : 0.8;
        Vec3 localSpawn = Vec3.atCenterOf(worldPosition)
                .add(face.getStepX() * along, face.getStepY() * along, face.getStepZ() * along);

        Vec3 spawn = localSpawn;
        try {
            Vec3 world = dev.ryanhcode.sable.companion.SableCompanion.INSTANCE
                    .projectOutOfSubLevel(sl, localSpawn);
            if (world != null) spawn = world;
        } catch (Throwable ignored) {
        }

        Vec3 tgt = Vec3.atCenterOf(target);
        Vec3 loft = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        if (loft.lengthSqr() < 1.0e-6) loft = new Vec3(0, 1, 0);

        BallisticMissileEntity.LaunchConfig config = new BallisticMissileEntity.LaunchConfig();
        config.spawn = spawn;
        config.target = tgt;
        config.loft = loft;
        config.boostAccel = accel;
        config.boostTicks = ticks;
        config.terminal = terminal;
        config.yield = blast;
        config.gravitySi = gravitySi;
        config.dragCoefficient = dragCoefficient;
        config.apexY = apexY;
        config.cruiseY = cruiseY;
        config.siloClearance = siloClearance;
        config.launchWorldY = launchWorldY;
        config.size = size;
        config.warhead = warhead.copy();
        config.ejectOrigin = spawn;
        // The tube face is only the rail axis; after it the round flies the planned corridor.
        config.guided = true;

        BallisticMissileEntity spawned = BallisticMissileEntity.create(sl, config);
        if (spawned == null) return false;

        sl.addFreshEntity(spawned);
        missile = ItemStack.EMPTY;
        warhead = ItemStack.EMPTY;
        cooldown = 20 * 8;
        armed = true;
        markAndSync();
        sl.scheduleTick(worldPosition, getBlockState().getBlock(), 1);

        sl.playSound(null, worldPosition, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 1.2f, 0.7f);
        MissileChunkLoadManager.trackLiveMissile(sl, spawn, target);
        return true;
    }

    private void markAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Cooldown", cooldown);
        tag.putBoolean("Armed", armed);
        tag.putInt("SpeedLvl", speedLevel);
        tag.putInt("SiloClear", siloClearance);
        tag.putInt("LaunchY", launchWorldY);
        if (!missile.isEmpty()) {
            tag.put("Missile", missile.save(registries));
        }
        if (!warhead.isEmpty()) {
            tag.put("Warhead", warhead.save(registries));
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cooldown = tag.getInt("Cooldown");
        armed = !tag.contains("Armed") || tag.getBoolean("Armed");
        speedLevel = tag.getInt("SpeedLvl");
        siloClearance = tag.contains("SiloClear")
                ? MissileEject.clampClearance(tag.getInt("SiloClear"))
                : MissileEject.DEFAULT_CLEARANCE;
        launchWorldY = MissileEject.clampLaunchWorldY(tag.getInt("LaunchY"));
        missile = stackFrom(tag, "Missile", registries);
        warhead = stackFrom(tag, "Warhead", registries);
    }

    private static ItemStack stackFrom(CompoundTag tag, String key, HolderLookup.Provider registries) {
        if (!tag.contains(key)) return ItemStack.EMPTY;
        Tag saved = tag.get(key);
        return saved == null ? ItemStack.EMPTY : ItemStack.parse(registries, saved).orElse(ItemStack.EMPTY);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (cooldown > 0 && level instanceof ServerLevel sl && !level.isClientSide) {
            sl.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
