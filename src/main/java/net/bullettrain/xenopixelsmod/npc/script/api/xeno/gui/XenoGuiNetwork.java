package net.bullettrain.xenopixelsmod.npc.script.api.xeno.gui;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.api.network.AddonPacketDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import java.util.function.Consumer;

/** Namespaced GUI packets, isolated from the sequential main channel and client classes. */
public final class XenoGuiNetwork {
    private static Consumer<CompoundTag> clientSnapshot = tag -> {};
    private static Consumer<UUID> clientClose = id -> {};
    private static boolean registered;
    private XenoGuiNetwork() {}
    public static void clientHandlers(Consumer<CompoundTag> snapshots, Consumer<UUID> closes) {
        clientSnapshot = java.util.Objects.requireNonNull(snapshots); clientClose = java.util.Objects.requireNonNull(closes);
    }
    public static synchronized void register() {
        if (registered) return;
        AddonNetwork.register(id("snapshot_v1"), Snapshot.class, AddonPacketDirection.CLIENTBOUND,
                (packet, buffer) -> XenoGuiWire.encode(packet.tag, buffer), buffer -> new Snapshot(XenoGuiWire.decode(buffer)),
                (packet, context) -> context.enqueueWork(() -> clientSnapshot.accept(packet.tag.copy())));
        AddonNetwork.register(id("close_v1"), Close.class, AddonPacketDirection.CLIENTBOUND,
                (packet, buffer) -> buffer.writeUUID(packet.session), buffer -> new Close(buffer.readUUID()),
                (packet, context) -> context.enqueueWork(() -> clientClose.accept(packet.session)));
        AddonNetwork.register(id("input_v1"), Input.class, AddonPacketDirection.SERVERBOUND,
                Input::encode, Input::decode, (packet, context) -> context.enqueueWork(() -> context.sender().ifPresent(player ->
                        XenoGuiSessions.input(player, packet.session, packet.revision, packet.sequence, packet.component,
                                packet.action, packet.value, packet.width, packet.height))));
        registered = true;
    }
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "script_gui/" + name); }
    public static void sendSnapshot(ServerPlayer player, CompoundTag tag) { XenoGuiWire.validate(tag); AddonNetwork.sendToPlayer(player, new Snapshot(tag.copy())); }
    public static void sendClose(ServerPlayer player, UUID session) { AddonNetwork.sendToPlayer(player, new Close(session)); }
    public static void sendInput(UUID session, int revision, long sequence, UUID component, String action, String value, int width, int height) {
        AddonNetwork.sendToServer(new Input(session, revision, sequence, component, action, value, width, height));
    }
    public record Snapshot(CompoundTag tag) {}
    public record Close(UUID session) {}
    public record Input(UUID session, int revision, long sequence, UUID component, String action, String value, int width, int height) {
        public Input {
            java.util.Objects.requireNonNull(session); java.util.Objects.requireNonNull(component);
            if (revision < 1 || sequence < 1 || action == null || !XenoGuiWire.ACTIONS.contains(action)
                    || value == null || value.length() > XenoGuiWire.MAX_TEXT || width < 0 || width > 8192 || height < 0 || height > 8192)
                throw new IllegalArgumentException("Invalid scripted GUI input");
        }
        static void encode(Input packet, FriendlyByteBuf buffer) {
            buffer.writeUUID(packet.session); buffer.writeVarInt(packet.revision); buffer.writeVarLong(packet.sequence);
            buffer.writeUUID(packet.component); buffer.writeUtf(packet.action, 16); buffer.writeUtf(packet.value, XenoGuiWire.MAX_TEXT);
            buffer.writeVarInt(packet.width); buffer.writeVarInt(packet.height);
        }
        static Input decode(FriendlyByteBuf buffer) {
            return new Input(buffer.readUUID(), buffer.readVarInt(), buffer.readVarLong(), buffer.readUUID(),
                    buffer.readUtf(16), buffer.readUtf(XenoGuiWire.MAX_TEXT), buffer.readVarInt(), buffer.readVarInt());
        }
    }
}
