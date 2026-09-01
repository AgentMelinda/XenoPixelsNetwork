package net.bullettrain.xenopixelsmod.compat.npc;

import com.goodbird.cnpcgeckoaddon.network.PacketSyncAnimation;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncTileAnimation;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Gecko addon {@code type()} uses {@code CustomPacketPayload.createType("cnpcgeckoaddon"+simpleName)}
 * which parses as {@code minecraft:cnpcgeckoaddonpacketsyncanimation}. NeoForge
 * {@code NetworkRegistry.register} throws on the {@code minecraft} namespace, so the codec
 * never lands and encode falls through to {@code DiscardedPayload}.
 */
public final class CnpcGeckoPayloads {
    public static final CustomPacketPayload.Type<PacketSyncAnimation> SYNC_ANIMATION =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("cnpcgeckoaddon", "sync_animation"));
    public static final CustomPacketPayload.Type<PacketSyncTileAnimation> SYNC_TILE_ANIMATION =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("cnpcgeckoaddon", "sync_tile_animation"));

    private CnpcGeckoPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(SYNC_ANIMATION, codec(PacketSyncAnimation::encode, PacketSyncAnimation::decode),
                (payload, ctx) -> PacketSyncAnimation.handle(payload));
        registrar.playToClient(SYNC_TILE_ANIMATION, codec(PacketSyncTileAnimation::encode, PacketSyncTileAnimation::decode),
                (payload, ctx) -> PacketSyncTileAnimation.handle(payload));
        XenoPixelsMod.LOGGER.info("Registered CNPC-Gecko-Addon payloads as {}:{} and {}:{}",
                SYNC_ANIMATION.id().getNamespace(), SYNC_ANIMATION.id().getPath(),
                SYNC_TILE_ANIMATION.id().getNamespace(), SYNC_TILE_ANIMATION.id().getPath());
    }

    private static <T extends CustomPacketPayload> StreamCodec<FriendlyByteBuf, T> codec(
            StreamMember<T> encoder, java.util.function.Function<FriendlyByteBuf, T> decoder) {
        return CustomPacketPayload.codec(encoder::encode, decoder::apply);
    }

    @FunctionalInterface
    private interface StreamMember<T> {
        void encode(T packet, FriendlyByteBuf buf);
    }
}
