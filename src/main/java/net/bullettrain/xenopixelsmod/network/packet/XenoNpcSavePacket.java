package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public final class XenoNpcSavePacket {
    private final int entityId;
    private final int expectedRevision;
    private final String name;
    private final String title;
    private final String faction;
    @Nullable
    private final CompoundTag profileTag;
    /**
     * The second destination. Home, leash and respawn live on {@code XenoNpcData}, not on the
     * combat profile, so they cannot ride {@code profileTag} - {@code NpcCombatProfile.fromTag}
     * would drop them silently and the save would look like it worked.
     */
    @Nullable
    private final CompoundTag dataTag;

    public XenoNpcSavePacket(int entityId, int expectedRevision, String name, String title,
                             String faction, @Nullable CompoundTag profileTag) {
        this(entityId, expectedRevision, name, title, faction, profileTag, null);
    }

    public XenoNpcSavePacket(int entityId, int expectedRevision, String name, String title,
                             String faction, @Nullable CompoundTag profileTag,
                             @Nullable CompoundTag dataTag) {
        this.entityId = entityId;
        this.expectedRevision = expectedRevision;
        this.name = name == null ? "" : name;
        this.title = title == null ? "" : title;
        this.faction = faction == null ? "" : faction;
        this.profileTag = profileTag;
        this.dataTag = dataTag;
    }

    public XenoNpcSavePacket(FriendlyByteBuf buf) {
        entityId = buf.readVarInt();
        expectedRevision = buf.readVarInt();
        name = buf.readUtf(64);
        title = buf.readUtf(64);
        faction = buf.readUtf(64);
        profileTag = buf.readBoolean() ? buf.readNbt() : null;
        // Appended, never inserted: an older decoder reads the fields above in the same order.
        dataTag = buf.readBoolean() ? buf.readNbt() : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(expectedRevision);
        buf.writeUtf(name, 64);
        buf.writeUtf(title, 64);
        buf.writeUtf(faction, 64);
        if (profileTag == null) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeNbt(profileTag);
        }
        if (dataTag == null) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeNbt(dataTag);
        }
    }

    /**
     * Applies an editor save.
     *
     * <p>Every rejection answers the client. These guards used to return silently, which is the
     * same defect {@code NpcProfileSaveResultPacket} was introduced for on the wand path: a save
     * refused for permission, range or a stale id looked exactly like one that succeeded. The
     * native editor result carries the submitted entity and revision, so an unrelated save reply
     * cannot close a pending draft.
     *
     * <p>{@code packet.profileTag} is a subset - only the keys the editor actually changed - and is
     * merged over the NPC's current profile. That is what keeps a save from overwriting appearance,
     * transform or mastery fields owned by the other DMZ screens.
     */
    public static void handle(XenoNpcSavePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) {
                reject(player, packet, NpcProfileSaveResultPacket.NOT_PERMITTED);
                return;
            }
            if (packet.name.length() > 64 || packet.title.length() > 64
                    || packet.faction.length() > 64) {
                reject(player, packet, NpcProfileSaveResultPacket.NOT_EDITABLE);
                return;
            }
            if (!(player.level().getEntity(packet.entityId) instanceof XenoNpcEntity npc)) {
                reject(player, packet, NpcProfileSaveResultPacket.GONE);
                return;
            }
            if (player.distanceToSqr(npc) > 64.0 * 64.0) {
                reject(player, packet, NpcProfileSaveResultPacket.TOO_FAR);
                return;
            }
            if (npc.npcData().revision() != packet.expectedRevision) {
                reject(player, packet, NpcProfileSaveResultPacket.STALE);
                return;
            }
            XenoNpcSavePolicy.Validation dataValidation =
                    XenoNpcDataSavePolicy.validate(packet.dataTag);
            if (!dataValidation.accepted()) {
                // Same class of failure as a profile rejection, so it answers the same way.
                reject(player, packet, NpcProfileSaveResultPacket.NOT_EDITABLE);
                return;
            }
            XenoNpcSavePolicy.Validation validation = XenoNpcSavePolicy.validate(packet.profileTag);
            if (!validation.accepted()) {
                reject(player, packet, NpcProfileSaveResultPacket.NOT_EDITABLE);
                return;
            }

            NpcCombatProfile existing = NpcCombatProfile.read(npc);
            if (existing.editingLocked) {
                reject(player, packet, NpcProfileSaveResultPacket.LOCKED);
                return;
            }

            NpcCombatProfile updated = existing;
            boolean profileChanged = false;
            if (packet.profileTag != null && !packet.profileTag.isEmpty()) {
                CompoundTag before = existing.toTag();
                CompoundTag merged = XenoNpcSavePolicy.merge(before, packet.profileTag);
                updated = NpcCombatProfile.fromTag(merged);
                profileChanged = !before.equals(updated.toTag());
            }

            // Applied through the data object's own setters rather than by writing the tag back,
            // so every clamp they carry - a non-finite home, a leash past 512, a delay of zero -
            // still runs on a value that arrived over the network.
            boolean dataChanged = false;
            if (packet.dataTag != null && !packet.dataTag.isEmpty()) {
                dataChanged = applyEditorData(npc.npcData(), packet.dataTag);
            }

            boolean identityChanged = npc.npcData().applyEditorIdentity(
                    packet.name, packet.title, packet.faction);
            if (identityChanged) {
                npc.refreshNameplate();
            }
            if (profileChanged) {
                updated.write(npc);
                if (packet.profileTag.contains("Trades")) {
                    npc.invalidateTradeOffers();
                }
                // Visibility, glow and hitbox are set rather than asked, so an edit needs applying
                // now; otherwise it would not show until the NPC reloaded.
                net.bullettrain.xenopixelsmod.npc.XenoNpcBehaviour.apply(npc, updated);
                // Push the new appearance to everyone watching. Without this the FULL DMZ renderer
                // keeps drawing from the client state it was last given, so an edit only showed up
                // after the NPC was re-tracked.
                net.bullettrain.xenopixelsmod.compat.npc.NpcAppearanceFx.sync(npc);
            }
            if (identityChanged || profileChanged || dataChanged) {
                npc.npcData().markEdited();
            }
            // Advanced > Linked: the same accepted subset goes to everything linked from this NPC.
            // Only the keys the policy already passed, so a linked NPC can never receive a field
            // the editor is not allowed to write directly.
            int linked = profileChanged
                    ? net.bullettrain.xenopixelsmod.npc.XenoNpcLinkPropagation.propagate(
                            player.server, npc, packet.profileTag)
                    : 0;
            if (linked > 0) {
                player.sendSystemMessage(Component.literal(
                        "Applied to " + linked + " linked NPC" + (linked == 1 ? "" : "s")));
            }
            // The editor shows the result itself; a chat line per save would spam once the
            // editor autosaves on every change.
            ModNetwork.sendToPlayer(player, new XenoNpcEditorSaveResultPacket(packet.entityId,
                    packet.expectedRevision, npc.npcData().revision(), true,
                    "Saved (revision " + npc.npcData().revision() + ")"));
        });
        context.get().setPacketHandled(true);
    }

    /**
     * Writes an accepted data payload onto the NPC.
     *
     * <p>Each key goes through its own setter, which is where the clamping lives. Writing the tag
     * back wholesale would skip all of it, and these values arrive over the network.
     *
     * @return whether anything actually changed, so an unchanged save does not bump the revision
     */
    private static boolean applyEditorData(net.bullettrain.xenopixelsmod.npc.XenoNpcData data,
                                           CompoundTag tag) {
        CompoundTag before = data.toTag();
        if (tag.contains("Role")) {
            data.setRole(net.bullettrain.xenopixelsmod.npc.XenoNpcRole.byId(
                    tag.getString("Role")));
        }
        if (tag.contains("HomeX") && tag.contains("HomeY") && tag.contains("HomeZ")) {
            data.setHome(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        }
        if (tag.contains("LeashRadius")) {
            data.setLeashRadius(tag.getDouble("LeashRadius"));
        }
        if (tag.contains("RespawnEnabled")) {
            data.setRespawnEnabled(tag.getBoolean("RespawnEnabled"));
        }
        if (tag.contains("RespawnDelayTicks")) {
            data.setRespawnDelayTicks(tag.getInt("RespawnDelayTicks"));
        }
        CompoundTag after = data.toTag();
        // Revision moves on every touch(), so it is excluded from the comparison - otherwise a
        // save that changed nothing would still read as a change.
        before.remove("Revision");
        after.remove("Revision");
        return !before.equals(after);
    }

    private static void reject(ServerPlayer player, XenoNpcSavePacket packet, String reason) {
        ModNetwork.sendToPlayer(player, new XenoNpcEditorSaveResultPacket(packet.entityId,
                packet.expectedRevision, -1, false, reason));
    }
}
