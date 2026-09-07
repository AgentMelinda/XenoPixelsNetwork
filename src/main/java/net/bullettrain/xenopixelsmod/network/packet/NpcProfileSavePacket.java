package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAuraFx;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCounterpartSync;
import net.bullettrain.xenopixelsmod.compat.npc.NpcTransformSystem;
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

    public NpcProfileSavePacket(int entityId, CompoundTag tag, Action action, String group, String form) {
        this.entityId = entityId;
        this.tag = tag == null ? new CompoundTag() : tag;
        this.action = action == null ? Action.SAVE : action;
        this.group = group == null ? "" : group;
        this.form = form == null ? "" : form;
    }

    public NpcProfileSavePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.tag = buf.readNbt();
        this.action = buf.readEnum(Action.class);
        this.group = buf.readUtf();
        this.form = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeNbt(tag);
        buf.writeEnum(action);
        buf.writeUtf(group);
        buf.writeUtf(form);
    }

    public static void handle(NpcProfileSavePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            Entity raw = level.getEntity(msg.entityId);
            if (!(raw instanceof LivingEntity living) || !living.isAlive()
                    || !NpcCounterpartSync.isCustomNpc(living)
                    || !player.hasPermissions(2)) {
                return;
            }
            if (player.distanceToSqr(living) > 64.0 * 64.0) {
                return;
            }
            NpcCombatProfile authoritative = NpcCombatProfile.read(living);
            NpcCombatProfile profile = NpcCombatProfile.fromTag(
                    msg.tag == null ? new CompoundTag() : msg.tag);
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
        });
        ctx.get().setPacketHandled(true);
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
