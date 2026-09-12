package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → client: play one named DragonMineZ combat clip on an NPC or player.
 *
 * <p>DragonMineZ's own animation packets cannot carry this. {@code TriggerAnimationS2C} resolves
 * its target with {@code getPlayerByUUID}, so an NPC entity is simply not reachable through it, and
 * the CustomNPCs Gecko addon's packet only reaches NPCs that are using one of its custom models.
 * What does work is the synthetic player {@code NpcFullDmzRenderer} already builds to draw a
 * Full-mode NPC through DragonMineZ's real player renderer: that proxy is an
 * {@code AbstractClientPlayer}, DragonMineZ mixes {@code IPlayerAnimatable} into that class, and the
 * clip names this mod ships are already registered with its {@code CombatAnimationResolver}. So the
 * clip is named here and applied to the proxy at render time. A real player is the same
 * {@code IPlayerAnimatable} and is applied as soon as the packet arrives.
 *
 * <p>Keyed by UUID rather than entity id, like every other NPC packet in this mod, because a client
 * can receive one before the entity has been spawned in.
 *
 * <p>{@code flags} is protocol 68: bit 0 is KI play-and-hold, bit 1 is a real controller stop.
 */
public final class NpcAnimationPacket {

    public static final int FLAG_HOLD = 1;
    public static final int FLAG_STOP = 2;

    private final UUID entityUuid;
    private final String animation;
    private final float speed;
    private final int flags;

    public NpcAnimationPacket(UUID entityUuid, String animation, float speed) {
        this(entityUuid, animation, speed, 0);
    }

    public NpcAnimationPacket(UUID entityUuid, String animation, float speed, int flags) {
        this.entityUuid = entityUuid;
        this.animation = animation == null ? "" : animation;
        this.speed = speed;
        this.flags = flags;
    }

    public NpcAnimationPacket(FriendlyByteBuf buf) {
        entityUuid = buf.readUUID();
        animation = buf.readUtf(128);
        speed = buf.readFloat();
        flags = buf.readByte() & 0xFF;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(entityUuid);
        buf.writeUtf(animation, 128);
        buf.writeFloat(speed);
        buf.writeByte(flags);
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    public String animation() {
        return animation;
    }

    public float speed() {
        return speed;
    }

    public int flags() {
        return flags;
    }

    public static void handle(NpcAnimationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                net.bullettrain.xenopixelsmod.client.compat.npc.NpcAnimationClient.receive(
                        msg.entityUuid, msg.animation, msg.speed, msg.flags));
        ctx.get().setPacketHandled(true);
    }
}
