package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRole;
import net.bullettrain.xenopixelsmod.npc.transport.TransportDestination;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;

import java.util.Set;
import java.util.function.Supplier;

/**
 * C2S: "send me to this destination".
 *
 * <p>The client names a destination; it does not name a position. Every coordinate comes from the
 * NPC's own list on the server, so a crafted packet can at worst ask for a destination that NPC
 * genuinely offers — never an arbitrary teleport.
 *
 * <p>Checked here, in order: the entity exists and is a transporter, the player is close enough to
 * be talking to it, the destination is on <em>that</em> NPC's list, the player has unlocked it, and
 * the dimension resolves. A failure answers the player rather than passing silently, because a
 * travel button that does nothing is indistinguishable from a broken one.
 */
public record XenoNpcTravelPacket(int entityId, String destinationId) {

    /** Ids are store-shaped; this is the same cap {@code XenoNpcStorePaths} enforces. */
    private static final int MAX_ID = 64;

    /**
     * How far a player may be from the NPC and still travel.
     *
     * <p>Matches the reach a conversation is held at. Without it a client could keep a stale
     * entity id and travel from anywhere in the world.
     */
    private static final double MAX_DISTANCE_SQ = 8.0 * 8.0;

    public XenoNpcTravelPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(MAX_ID));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(destinationId == null ? "" : destinationId, MAX_ID);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            Entity entity = player.level().getEntity(entityId);
            if (!(entity instanceof XenoNpcEntity npc) || npc.role() != XenoNpcRole.TRANSPORTER) {
                return;
            }
            if (player.distanceToSqr(npc) > MAX_DISTANCE_SQ) {
                // A stale entity id kept from across the world is the shape of the abuse here.
                player.sendSystemMessage(Component.literal("You are too far away."));
                return;
            }

            NpcCombatProfile profile = NpcCombatProfile.read(npc);
            // The NPC's network is the authority, resolved server-side from the store. The client
            // named a destination id and nothing else; it cannot reach a destination this NPC's
            // network does not list, even one some other network does.
            net.bullettrain.xenopixelsmod.npc.transport.TransportNetwork network =
                    net.bullettrain.xenopixelsmod.npc.transport.TransportNetworks
                            .get(profile.transportNetwork);
            TransportDestination destination =
                    network == null ? null : network.byId(destinationId);
            if (destination == null || !destination.usable()) {
                // The list is the authority. A destination this NPC does not offer is refused even
                // if some other NPC in the world does offer it.
                player.sendSystemMessage(Component.literal("That destination is not available."));
                return;
            }

            Set<String> unlocked = unlockedFor(player);
            if (destination.unlock() == TransportDestination.Unlock.VISITED
                    && !unlocked.contains(destination.id())) {
                // Re-checked server side even though the client was only sent what it may see: the
                // client's copy of the list is a display, never a permission.
                player.sendSystemMessage(Component.literal("You have not discovered that yet."));
                return;
            }

            ServerLevel target = levelFor(player, destination.dimension());
            if (target == null) {
                player.sendSystemMessage(Component.literal(
                        "That destination's world is not loaded."));
                XenoPixelsMod.LOGGER.warn("NPC transport: dimension '{}' does not resolve",
                        destination.dimension());
                return;
            }

            travel(player, target, destination);
        });
        ctx.setPacketHandled(true);
    }

    /** Moves the player, and records the arrival so a VISITED destination stays reachable. */
    private static void travel(ServerPlayer player, ServerLevel target,
                               TransportDestination destination) {
        player.changeDimension(new DimensionTransition(target,
                new net.minecraft.world.phys.Vec3(destination.x(), destination.y(),
                        destination.z()),
                net.minecraft.world.phys.Vec3.ZERO,
                destination.yaw(), player.getXRot(),
                DimensionTransition.DO_NOTHING));
        net.bullettrain.xenopixelsmod.capability.XenoCapabilities.get(player).ifPresent(data ->
                data.unlockTransport(destination.id()));
        player.sendSystemMessage(Component.literal("§bTravelled to §f" + destination.name()));
    }

    private static Set<String> unlockedFor(ServerPlayer player) {
        return net.bullettrain.xenopixelsmod.capability.XenoCapabilities.get(player)
                .map(data -> data.unlockedTransports())
                .orElse(Set.of());
    }

    /**
     * A dimension id to a live level, or null.
     *
     * <p>Null covers both "not a valid id" and "this server does not have that world", which are
     * the same thing from the player's side: somewhere they cannot be sent.
     */
    private static ServerLevel levelFor(ServerPlayer player, String dimension) {
        ResourceLocation key = ResourceLocation.tryParse(dimension);
        if (key == null || player.getServer() == null) {
            return null;
        }
        return player.getServer().getLevel(ResourceKey.create(net.minecraft.core.registries
                .Registries.DIMENSION, key));
    }
}
