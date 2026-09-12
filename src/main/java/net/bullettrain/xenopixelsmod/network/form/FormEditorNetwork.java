package net.bullettrain.xenopixelsmod.network.form;

import com.dragonminez.compat.network.NetworkDirection;
import com.dragonminez.compat.network.NetworkEvent;
import com.dragonminez.compat.network.NetworkRegistry;
import com.dragonminez.compat.network.simple.SimpleChannel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.ClientPacketHandlers;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCounterpartSync;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormEditorClientState;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormEditorService;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormKind;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadataRegistry;
import net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerPurchase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;

import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FormEditorNetwork {
    private static final int MAX_JSON = DmzFormEditorService.MAX_JSON_CHARS;
    /** Bumped when {@link TrainerOpenPacket} changed from a form-type list to a menu payload. */
    private static final String PROTOCOL = "3";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "form_editor"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();
    private static boolean registered;

    private FormEditorNetwork() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(SavePacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SavePacket::new).encoder(SavePacket::encode)
                .consumerMainThread(SavePacket::handle).add();
        CHANNEL.messageBuilder(MetadataPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(MetadataPacket::new).encoder(MetadataPacket::encode)
                .consumerMainThread(MetadataPacket::handle).add();
        CHANNEL.messageBuilder(ResultPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ResultPacket::new).encoder(ResultPacket::encode)
                .consumerMainThread(ResultPacket::handle).add();
        CHANNEL.messageBuilder(TrainerOpenPacket.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(TrainerOpenPacket::new).encoder(TrainerOpenPacket::encode)
                .consumerMainThread(TrainerOpenPacket::handle).add();
        CHANNEL.messageBuilder(TrainerPurchasePacket.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .decoder(TrainerPurchasePacket::new).encoder(TrainerPurchasePacket::encode)
                .consumerMainThread(TrainerPurchasePacket::handle).add();
    }

    public static void save(DmzFormKind kind, String race, String group, String formJson,
                            String metadataJson, long revision, String sessionId) {
        CHANNEL.sendToServer(new SavePacket(kind, race, group, formJson, metadataJson, revision, sessionId));
    }

    public static void sendMetadata(ServerPlayer player) {
        CHANNEL.sendToPlayer(new MetadataPacket(DmzFormMetadataRegistry.snapshotJson()), player);
    }

    public static void sendTrainer(ServerPlayer player, LivingEntity trainer,
                                   List<net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadata> offerings) {
        sendTrainerMenu(player, trainer, net.bullettrain.xenopixelsmod.dmz.form.DmzSkillMaster.menuFor(
                trainer.getUUID(),
                net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadataRegistry
                        .trainerOfferingEntries(trainer.getUUID())));
    }

    /** Sends a menu the caller already assembled, used when the offerings are computed elsewhere. */
    public static void sendTrainerMenu(ServerPlayer player, LivingEntity trainer,
                                       net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu menu) {
        CHANNEL.sendToPlayer(new TrainerOpenPacket(trainer.getId(), trainer.getName().getString(),
                menu == null ? net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.EMPTY : menu),
                player);
    }

    public static void purchase(int trainerEntityId, String formType) {
        CHANNEL.sendToServer(new TrainerPurchasePacket(trainerEntityId, formType));
    }

    private static void broadcastMetadata(ServerPlayer source) {
        if (source.getServer() == null) return;
        MetadataPacket packet = new MetadataPacket(DmzFormMetadataRegistry.snapshotJson());
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            CHANNEL.sendToPlayer(packet, player);
        }
    }

    public record SavePacket(DmzFormKind kind, String race, String group, String formJson,
                             String metadataJson, long revision, String sessionId) {
        public SavePacket(FriendlyByteBuf buffer) {
            this(buffer.readEnum(DmzFormKind.class), buffer.readUtf(64), buffer.readUtf(64),
                    buffer.readUtf(MAX_JSON), buffer.readUtf(MAX_JSON), buffer.readLong(), buffer.readUtf(64));
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeEnum(kind);
            buffer.writeUtf(race == null ? "" : race, 64);
            buffer.writeUtf(group == null ? "" : group, 64);
            buffer.writeUtf(formJson == null ? "" : formJson, MAX_JSON);
            buffer.writeUtf(metadataJson == null ? "" : metadataJson, MAX_JSON);
            buffer.writeLong(revision);
            buffer.writeUtf(sessionId == null ? "" : sessionId, 64);
        }

        public static void handle(SavePacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                DmzFormEditorService.Result result = DmzFormEditorService.save(player, packet.kind,
                        packet.race, packet.group, packet.formJson, packet.metadataJson,
                        packet.revision, packet.sessionId);
                CHANNEL.sendToPlayer(new ResultPacket(result.success(), result.revision(), result.message()), player);
                if (result.success()) broadcastMetadata(player);
            });
            context.setPacketHandled(true);
        }
    }

    public record MetadataPacket(String json) {
        public MetadataPacket(FriendlyByteBuf buffer) {
            this(buffer.readUtf(MAX_JSON));
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(json == null ? "" : json, MAX_JSON);
        }

        public static void handle(MetadataPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DmzFormMetadataRegistry.applySnapshot(packet.json));
            context.setPacketHandled(true);
        }
    }

    public record ResultPacket(boolean success, long revision, String message) {
        public ResultPacket(FriendlyByteBuf buffer) {
            this(buffer.readBoolean(), buffer.readLong(), buffer.readUtf(512));
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBoolean(success);
            buffer.writeLong(revision);
            buffer.writeUtf(message == null ? "" : message, 512);
        }

        public static void handle(ResultPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DmzFormEditorClientState.accept(
                    packet.success, packet.revision, packet.message));
            context.setPacketHandled(true);
        }
    }

    public record TrainerOpenPacket(int entityId, String name,
                                    net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu menu) {
        public TrainerOpenPacket(FriendlyByteBuf buffer) {
            this(buffer.readVarInt(), buffer.readUtf(
                            net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_NAME_LENGTH),
                    readMenu(buffer));
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(entityId);
            buffer.writeUtf(clamp(name,
                    net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_NAME_LENGTH),
                    net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_NAME_LENGTH);
            var menu = this.menu == null
                    ? net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.EMPTY : this.menu;
            buffer.writeUtf(menu.title(),
                    net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_TITLE_LENGTH);
            writeMap(buffer, menu.groupNames());
            writeMap(buffer, menu.body());
            List<net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.Entry> entries = menu.entries();
            buffer.writeVarInt(entries.size());
            for (var entry : entries) {
                buffer.writeUtf(clamp(entry.kind(),
                        net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_KIND_LENGTH),
                        net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_KIND_LENGTH);
                writeId(buffer, entry.race());
                writeId(buffer, entry.group());
                writeId(buffer, entry.formType());
                writeId(buffer, entry.formId());
                writeId(buffer, entry.label());
            }
        }

        public static void handle(TrainerOpenPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> ClientPacketHandlers.openDmzTrainer(
                    packet.entityId, packet.name, packet.menu));
            context.setPacketHandled(true);
        }
    }

    public record TrainerPurchasePacket(int entityId, String formType) {
        public TrainerPurchasePacket(FriendlyByteBuf buffer) {
            this(buffer.readVarInt(), buffer.readUtf(64));
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(entityId);
            buffer.writeUtf(formType == null ? "" : formType, 64);
        }

        public static void handle(TrainerPurchasePacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> purchase(context.getSender(), packet));
            context.setPacketHandled(true);
        }

        private static void purchase(ServerPlayer player, TrainerPurchasePacket packet) {
            if (player == null || !(player.level().getEntity(packet.entityId) instanceof LivingEntity trainer)
                    || !NpcCounterpartSync.isCustomNpc(trainer)
                    || player.distanceToSqr(trainer) > 8.0 * 8.0) return;
            net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadata selected = null;
            for (var metadata : DmzFormMetadataRegistry.trainerOfferings(trainer.getUUID())) {
                if (packet.formType.equalsIgnoreCase(metadata.formType)) {
                    selected = metadata;
                    break;
                }
            }
            if (selected == null) return;
            var metadata = selected;
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
                String race = data.getCharacter().getRaceName();
                DmzTrainerPurchase decision = DmzTrainerPurchase.evaluate(metadata, race,
                        data.getSkills().getSkillLevel(metadata.formType),
                        data.getResources().getTrainingPoints());
                if (!decision.allowed()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(decision.message()));
                    return;
                }
                data.getResources().removeTrainingPoints(decision.cost());
                data.getSkills().setSkillLevel(metadata.formType, decision.nextLevel());
                data.updateTransformationSkillLimits(race);
                NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
                NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(decision.message()));
            });
        }
    }

    /**
     * Decodes the trainer menu, bounding every collection before it is allocated.
     *
     * <p>The count is read first and clamped, so a hostile or corrupted packet can never make the
     * client allocate more than the menu caps allow.
     */
    private static net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu readMenu(FriendlyByteBuf buffer) {
        String title = buffer.readUtf(
                net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_TITLE_LENGTH);
        Map<String, String> groupNames = readMap(buffer);
        Map<String, String> body = readMap(buffer);
        int count = Math.min(net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_ENTRIES,
                Math.max(0, buffer.readVarInt()));
        List<net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.Entry> entries =
                new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.Entry(
                    buffer.readUtf(net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_KIND_LENGTH),
                    buffer.readUtf(net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_ID_LENGTH),
                    buffer.readUtf(net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_ID_LENGTH),
                    buffer.readUtf(net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_ID_LENGTH),
                    buffer.readUtf(net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_ID_LENGTH),
                    buffer.readUtf(net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_ID_LENGTH)));
        }
        return new net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu(title, groupNames, body, entries);
    }

    private static Map<String, String> readMap(FriendlyByteBuf buffer) {
        int count = Math.min(net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_LOCALES,
                Math.max(0, buffer.readVarInt()));
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String key = buffer.readUtf(
                    net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_LOCALE_LENGTH);
            result.put(key, buffer.readUtf(
                    net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_BODY_LENGTH));
        }
        return result;
    }

    private static void writeMap(FriendlyByteBuf buffer, Map<String, String> values) {
        Map<String, String> map = values == null ? Map.of() : values;
        int count = Math.min(net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_LOCALES,
                map.size());
        buffer.writeVarInt(count);
        int written = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (written >= count) break;
            buffer.writeUtf(clamp(entry.getKey(),
                            net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_LOCALE_LENGTH),
                    net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_LOCALE_LENGTH);
            buffer.writeUtf(clamp(entry.getValue(),
                            net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_BODY_LENGTH),
                    net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_BODY_LENGTH);
            written++;
        }
    }

    private static void writeId(FriendlyByteBuf buffer, String value) {
        buffer.writeUtf(clamp(value, net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_ID_LENGTH),
                net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.MAX_ID_LENGTH);
    }

    private static String clamp(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
