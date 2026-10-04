package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFormLookup;
import net.bullettrain.xenopixelsmod.compat.npc.NpcTransformSystem;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * C2S: a DMZ-tab action on a Xeno NPC.
 *
 * <p>Transform, descend and stack are server-side state changes on a live entity, so the editor
 * cannot perform them directly the way it edits a profile field. They run through
 * {@link NpcTransformSystem}, which owns the transform hold, the aura and the attribute sync.
 *
 * <p>The guards match {@link XenoNpcSavePacket} exactly - operator level 2, within 64 blocks, and a
 * matching revision - and every rejection answers with {@link NpcProfileSaveResultPacket} rather
 * than returning silently.
 */
public final class XenoNpcActionPacket {

    /** What the client is asking for. Ordinals are the wire format, so append only. */
    public enum Action {
        TRANSFORM,
        DESCEND,
        STACK,
        UNSTACK
    }

    private static final int TRANSFORM_TICKS = 40;

    private final int entityId;
    private final int expectedRevision;
    private final Action action;
    private final String group;
    private final String form;

    public XenoNpcActionPacket(int entityId, int expectedRevision, Action action,
                               String group, String form) {
        this.entityId = entityId;
        this.expectedRevision = expectedRevision;
        this.action = action == null ? Action.DESCEND : action;
        this.group = group == null ? "" : group;
        this.form = form == null ? "" : form;
    }

    public XenoNpcActionPacket(FriendlyByteBuf buf) {
        entityId = buf.readVarInt();
        expectedRevision = buf.readVarInt();
        int ordinal = buf.readVarInt();
        Action[] values = Action.values();
        // A newer client could name an action this build does not have; clamp rather than throw.
        action = ordinal >= 0 && ordinal < values.length ? values[ordinal] : Action.DESCEND;
        group = buf.readUtf(128);
        form = buf.readUtf(128);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(expectedRevision);
        buf.writeVarInt(action.ordinal());
        buf.writeUtf(group, 128);
        buf.writeUtf(form, 128);
    }

    public static void handle(XenoNpcActionPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player == null) {
                return;
            }
            if (!player.hasPermissions(2)) {
                reject(player, NpcProfileSaveResultPacket.NOT_PERMITTED);
                return;
            }
            if (!(player.level().getEntity(packet.entityId) instanceof XenoNpcEntity npc)) {
                reject(player, NpcProfileSaveResultPacket.GONE);
                return;
            }
            if (player.distanceToSqr(npc) > 64.0 * 64.0) {
                reject(player, NpcProfileSaveResultPacket.TOO_FAR);
                return;
            }
            if (npc.npcData().revision() != packet.expectedRevision) {
                reject(player, NpcProfileSaveResultPacket.STALE);
                return;
            }

            boolean ok = switch (packet.action) {
                case TRANSFORM -> transform(npc, packet.group, packet.form);
                case STACK -> NpcTransformSystem.startStack(npc, packet.group, packet.form,
                        TRANSFORM_TICKS);
                case DESCEND -> NpcTransformSystem.descend(npc);
                case UNSTACK -> NpcTransformSystem.unstack(npc);
            };

            ModNetwork.sendToPlayer(player, new NpcProfileSaveResultPacket(ok,
                    ok ? packet.action.name().toLowerCase(java.util.Locale.ROOT) + " applied"
                       : "NPC " + packet.action.name().toLowerCase(java.util.Locale.ROOT)
                         + " was refused"));
        });
        context.get().setPacketHandled(true);
    }

    /**
     * Grants mastery before transforming.
     *
     * <p>{@code NpcTransformSystem.canStart} refuses a form the NPC has no mastery in, which is
     * correct for gameplay but wrong for an operator editor whose whole purpose is to set the NPC
     * up. Granting first makes the editor's Transform button mean "make it so".
     */
    private static boolean transform(XenoNpcEntity npc, String group, String form) {
        if (group.isBlank() || form.isBlank()) {
            return false;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        NpcFormLookup.grantMastery(profile, group, form);
        profile.write(npc);
        return NpcTransformSystem.start(npc, group, form, TRANSFORM_TICKS);
    }

    private static void reject(ServerPlayer player, String reason) {
        ModNetwork.sendToPlayer(player, new NpcProfileSaveResultPacket(false, reason));
    }
}
