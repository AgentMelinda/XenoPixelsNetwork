package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptScope;
import net.bullettrain.xenopixelsmod.npc.script.XenoScriptRunner;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcScripts;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * C2S: an operator asked to open one stored script, or to run one.
 *
 * <p>One packet for both because both are the same request from the same screen with the same
 * guard, and the sequential channel pays per registration. {@code run == false} means "fetch the
 * stored entry so I can edit it"; {@code run == true} means "evaluate this". The reply is
 * {@link NpcScriptResultPacket} either way.
 *
 * <p>Script <em>content</em> is deliberately not in the store index. The index is rebroadcast to
 * every player after every write, so putting source text in it would ship every script in the world
 * to everyone, continuously, to serve a screen almost nobody opens. Same reasoning as the dialogue
 * library, which has its own fetch packet.
 *
 * <p>{@code source} carries unsaved editor text, which is what a script editor is for: an author
 * should be able to try a change before committing it. That makes this the one place in the mod
 * where script text reaches the engine without having passed through
 * {@link XenoNpcStoreWritePacket}'s validation, so the two guards that matter - permission level 2
 * and the length cap on the wire - are repeated here rather than trusted from the caller.
 */
public record NpcScriptPacket(String id, String source, String entrypoint, boolean run) {

    private static final int MAX_ID = 64;
    private static final int MAX_ENTRYPOINT = 64;

    /**
     * Minimum milliseconds between two runs from one player.
     *
     * <p>Not a fairness rule. Each run evaluates arbitrary code on the server thread, and a client
     * that fired this every tick would starve it. An operator pressing a button cannot reach this
     * rate, so it only ever answers a malfunctioning or hostile client - and the only client that
     * can send it is already an op, which is why the response is a refusal rather than a report.
     */
    private static final long RUN_COOLDOWN_MS = 250;

    private static final Map<UUID, Long> LAST_RUN = new ConcurrentHashMap<>();

    public NpcScriptPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(MAX_ID), buf.readUtf(XenoNpcScripts.MAX_SCRIPT_CHARS),
                buf.readUtf(MAX_ENTRYPOINT), buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id == null ? "" : id, MAX_ID);
        buf.writeUtf(source == null ? "" : source, XenoNpcScripts.MAX_SCRIPT_CHARS);
        buf.writeUtf(entrypoint == null ? "" : entrypoint, MAX_ENTRYPOINT);
        buf.writeBoolean(run);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            // The same bar as writing the store. Script text is the most powerful thing an operator
            // can type into this mod, so nothing below is allowed to assume the caller checked.
            if (!player.hasPermissions(2)) {
                reply(player, "script access rejected: operator permission level 2 required",
                        "", null, false, false);
                return;
            }
            // Running evaluates arbitrary text on the server thread: the scripter's bar, as writing.
            if (run && !player.hasPermissions(XenoNpcScripts.PERMISSION)) {
                reply(player, "script run rejected: operator permission level "
                        + XenoNpcScripts.PERMISSION + " required", "", null, true, false);
                return;
            }
            if (run) {
                run(player);
            } else {
                fetch(player);
            }
        });
        ctx.setPacketHandled(true);
    }

    /** Sends the stored entry back so the editor can show it. */
    private void fetch(ServerPlayer player) {
        // The engine line rides every fetch reply: the client cannot know which engine the server
        // has (its own classpath says nothing about the server's).
        String engine = net.bullettrain.xenopixelsmod.npc.script.NpcScriptEngines.describe();
        // "player:<id>" and "forge:<id>" name global tabs in their own store categories.
        var category = id == null ? null
                : id.startsWith("player:") ? net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory.PLAYER_SCRIPTS
                : id.startsWith("forge:") ? net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory.FORGE_SCRIPTS
                : null;
        String storeId = category == null ? id : id.substring(id.indexOf(':') + 1);
        XenoNpcScripts.Script script = category != null
                ? XenoNpcScripts.load(category, storeId)
                : XenoNpcScripts.load(id);
        if (script == null) {
            reply(player, engine, "", null, false, false);
            return;
        }
        CompoundTag payload = script.toTag();
        if (category == net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory.PLAYER_SCRIPTS) {
            var store = net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores.get();
            if (store != null) {
                CompoundTag saved = store.get(category, XenoNpcScripts.NO_GROUP, storeId);
                if (saved != null) payload.putBoolean("ChatOnly", saved.getBoolean("ChatOnly"));
            }
        }
        reply(player, engine, "", payload, false, true);
    }

    /** Evaluates, and reports what the script printed as well as what it returned. */
    private void run(ServerPlayer player) {
        UUID key = player.getUUID();
        long now = System.currentTimeMillis();
        Long last = LAST_RUN.put(key, now);
        if (last != null && now - last < RUN_COOLDOWN_MS) {
            reply(player, "that ran a moment ago; try again shortly", "", null, true, false);
            return;
        }
        NpcScriptScope scope = NpcScriptScope.builder()
                .putString("player", player.getName().getString())
                .build();
        XenoScriptRunner.Outcome outcome = text().isBlank()
                ? XenoScriptRunner.run(id, entrypoint, scope)
                : XenoScriptRunner.runText(scriptForText(), entrypoint, scope);
        if (!outcome.ok()) {
            player.sendSystemMessage(Component.literal("[Xeno NPC script] " + id + " — "
                    + outcome.describe()).withStyle(ChatFormatting.RED));
        }
        reply(player, outcome.describe(), outcome.transcript(), null, true, outcome.ok());
    }

    /**
     * The submitted source wrapped as a script entry, so a run of unsaved text still carries the
     * language the stored entry asked for. Falls back to the default when there is nothing stored.
     */
    private XenoNpcScripts.Script scriptForText() {
        XenoNpcScripts.Script stored = XenoNpcScripts.load(id);
        String language = stored == null ? XenoNpcScripts.DEFAULT_LANGUAGE : stored.language();
        String name = stored == null ? id : stored.name();
        return new XenoNpcScripts.Script(id, name, language, true, text());
    }

    private String text() {
        return source == null ? "" : source;
    }

    private void reply(ServerPlayer player, String status, String output,
                       CompoundTag payload, boolean ran, boolean ok) {
        ModNetwork.sendToPlayer(player, new NpcScriptResultPacket(id, status, output, payload,
                ran, ok));
    }

    /**
     * Drops per-player run timing. Without this the map keeps an entry for every operator who ever
     * ran a script, which survives a world unload for no reason.
     */
    public static void clearCooldowns() {
        LAST_RUN.clear();
    }
}
