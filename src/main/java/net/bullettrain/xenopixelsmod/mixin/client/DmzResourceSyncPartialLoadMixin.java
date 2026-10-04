package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.common.network.ClientPacketHandler;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.bullettrain.xenopixelsmod.compat.dmz.DmzPartialStatsNbt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops DragonMineZ {@code ResourceSyncS2C} from exploding the client task queue.
 *
 * <p><b>Target:</b> {@code ClientPacketHandler#handleStatsSyncPacket(int, CompoundTag)}.
 * <b>Written for:</b> Minecraft 1.21.1 / NeoForge 21.1.248, DragonMineZ 2.1.3.
 * <b>Side:</b> client. Verified with {@code javap} against {@code libs/dragonminez-2.1.3.jar}.
 *
 * <p><b>Why this method.</b> {@code ResourceSyncS2C} and {@code StatsSyncS2C} share this handler.
 * The resource packet only writes {@code Resources} and {@code Status}. {@code StatsData.load}
 * then throws {@code ClassNotFoundException: PlayerQuestData not found in NBT}, which
 * {@code handleStatsSyncPacket} wraps as {@code RuntimeException}. Combat, death, and NPC-brain
 * hits all send that cheap packet.
 *
 * <p>{@code StatsSyncS2C} still includes {@code PlayerQuestData} and falls through to vanilla
 * {@code load}. Disk / capability restore is untouched.
 */
@Mixin(value = ClientPacketHandler.class, remap = false)
public abstract class DmzResourceSyncPartialLoadMixin {

    @Inject(method = "handleStatsSyncPacket", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xenopixels$mergePartialResourceSync(int playerId, CompoundTag nbt,
                                                            CallbackInfo ci) {
        if (!DmzPartialStatsNbt.shouldMergePartially(nbt)) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(playerId) instanceof Player player) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
                CompoundTag resources = DmzPartialStatsNbt.resources(nbt);
                if (resources != null) {
                    data.getResources().load(resources);
                }
                CompoundTag status = DmzPartialStatsNbt.status(nbt);
                if (status != null) {
                    data.getStatus().load(status);
                }
            });
        }
        ci.cancel();
    }
}
