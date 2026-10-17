package net.bullettrain.xenopixelsmod.combat.v3.ki;

import java.util.UUID;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.api.network.AddonPacketDirection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Cast identity and animation/music lifetime, isolated from the sequential main channel. */
public record CastPresentation(UUID caster, UUID cast, UUID target, long generation,
                               boolean finished, String prefix, String music, int elapsedTicks) {
    public CastPresentation {
        java.util.Objects.requireNonNull(caster); java.util.Objects.requireNonNull(cast);
        java.util.Objects.requireNonNull(target);
        if (generation < 0 || prefix == null || prefix.length() > 64 || music == null || music.length() > 128
                || elapsedTicks < 0 || elapsedTicks > 120000)
            throw new IllegalArgumentException("Invalid cast presentation");
    }
    public static void register() {
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "combat/cast_presentation_v1"),
                CastPresentation.class, AddonPacketDirection.CLIENTBOUND, CastPresentation::encode,
                CastPresentation::decode, (packet, context) -> context.enqueueWork(() -> {
                    if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.combat.v3.ClientCastPresentation.accept(packet);
                }));
    }
    private static void encode(CastPresentation p, FriendlyByteBuf b) {
        b.writeUUID(p.caster); b.writeUUID(p.cast); b.writeUUID(p.target); b.writeVarLong(p.generation);
        b.writeBoolean(p.finished); b.writeUtf(p.prefix, 64); b.writeUtf(p.music, 128);
        b.writeVarInt(p.elapsedTicks);
    }
    private static CastPresentation decode(FriendlyByteBuf b) {
        return new CastPresentation(b.readUUID(), b.readUUID(), b.readUUID(), b.readVarLong(),
                b.readBoolean(), b.readUtf(64), b.readUtf(128), b.readVarInt());
    }
    public void send(ServerPlayer player, net.minecraft.world.entity.LivingEntity victim) {
        if (!AddonNetwork.isReady()) return;
        AddonNetwork.sendToTrackingAndSelf(player, this);
        if (victim instanceof ServerPlayer targetPlayer && targetPlayer != player)
            AddonNetwork.sendToPlayer(targetPlayer, this);
    }
}
