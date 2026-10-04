package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAuraFx;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCounterpartSync;
import net.bullettrain.xenopixelsmod.compat.npc.NpcTransformSystem;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Supplier;

/**
 * C2S: write {@link NpcCombatProfile} from the CustomNPCs DMZ editor tab.
 */
public final class NpcProfileSavePacket {
    public enum Action {
        SAVE,
        TRANSFORM,
        DESCEND,
        STACK,
        UNSTACK
    }

    private final int entityId;
    private final CompoundTag tag;
    private final Action action;
    private final String group;
    private final String form;
    /**
     * The server profile the screen started from. When present only keys that differ between it
     * and {@link #tag} are applied, over the current server profile; everything else (script and
     * command edits, keys the client never knew) keeps its server value.
     */
    private final CompoundTag baseline;

    public NpcProfileSavePacket(int entityId, CompoundTag tag, Action action, String group, String form) {
        this(entityId, tag, action, group, form, null);
    }

    public NpcProfileSavePacket(int entityId, CompoundTag tag, Action action, String group, String form,
                                CompoundTag baseline) {
        this.entityId = entityId;
        this.tag = tag == null ? new CompoundTag() : tag;
        this.action = action == null ? Action.SAVE : action;
        this.group = group == null ? "" : group;
        this.form = form == null ? "" : form;
        this.baseline = baseline;
    }

    public NpcProfileSavePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.tag = buf.readNbt();
        this.action = buf.readEnum(Action.class);
        this.group = buf.readUtf();
        this.form = buf.readUtf();
        this.baseline = buf.readBoolean() ? buf.readNbt() : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeNbt(tag);
        buf.writeEnum(action);
        buf.writeUtf(group);
        buf.writeUtf(form);
        buf.writeBoolean(baseline != null);
        if (baseline != null) buf.writeNbt(baseline);
    }

    public static void handle(NpcProfileSavePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            Entity raw = level.getEntity(msg.entityId);
            if (!(raw instanceof LivingEntity living)) {
                reply(player, false, NpcProfileSaveResultPacket.GONE);
                return;
            }
            if (!living.isAlive() || !NpcCounterpartSync.isCustomNpc(living)) {
                reply(player, false, NpcProfileSaveResultPacket.NOT_EDITABLE);
                return;
            }
            if (!player.hasPermissions(2)) {
                reply(player, false, NpcProfileSaveResultPacket.NOT_PERMITTED);
                return;
            }
            if (player.distanceToSqr(living) > 64.0 * 64.0) {
                reply(player, false, NpcProfileSaveResultPacket.TOO_FAR);
                return;
            }
            NpcCombatProfile authoritative = NpcCombatProfile.read(living);
            CompoundTag edited = msg.tag == null ? new CompoundTag() : msg.tag;
            NpcCombatProfile profile = NpcCombatProfile.fromTag(msg.baseline == null ? edited
                    : net.bullettrain.xenopixelsmod.compat.npc.NpcProfileDiff.merge(
                            authoritative.toTag(), msg.baseline, edited));
            preserveRuntimeState(authoritative, profile);
            profile.write(living);
            NpcAuraFx.setActive(living, profile.auraOn);
            if (msg.action == Action.TRANSFORM) {
                NpcTransformSystem.start(living, msg.group, msg.form, NpcTransformSystem.DEFAULT_TICKS);
            } else if (msg.action == Action.DESCEND) {
                NpcTransformSystem.descend(living);
            } else if (msg.action == Action.STACK) {
                NpcTransformSystem.startStack(living, msg.group, msg.form, NpcTransformSystem.DEFAULT_TICKS);
            } else if (msg.action == Action.UNSTACK) {
                NpcTransformSystem.unstack(living);
            }
            reply(player, true, NpcProfileSaveResultPacket.OK);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void reply(ServerPlayer player, boolean saved, String reason) {
        ModNetwork.sendToPlayer(player, new NpcProfileSaveResultPacket(saved, reason));
    }

    /**
     * Wand drafts are client-owned editor values, but committed transformations are live
     * server state. Keeping these fields prevents any menu save (especially TRANSFORM) from
     * clearing the current form before the requested hold has completed.
     */
    static void preserveRuntimeState(NpcCombatProfile authoritative, NpcCombatProfile edited) {
        if (authoritative == null || edited == null) return;
        edited.formGroup = authoritative.formGroup;
        edited.formId = authoritative.formId;
        edited.stackGroup = authoritative.stackGroup;
        edited.stackId = authoritative.stackId;
        edited.formPower = authoritative.formPower;
        edited.baseSize = authoritative.baseSize;
    }
}
