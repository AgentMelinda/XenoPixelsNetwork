package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.anim.XenoClipChat;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared send/rewrite helpers for {@code XenoPixels.say} / {@code broadcast}
 * and My NPCs / CustomNPCs player chat.
 *
 * <p>My NPCs' {@code IPlayer.message} and its {@code onServerChat} write-back both call
 * {@code Component.translatable} with user text (or an empty key). On 1.21.1 that hides the
 * line. These methods always send literals. Public rewrites cancel the signed
 * {@code ServerChatEvent} and broadcast vanilla {@code chat.type.text} decoration.
 */
public final class NpcScriptSay {
    public static final int MAX_MESSAGE_LENGTH = 256;
    public static final String CHAT_TYPE_KEY = "chat.type.text";
    private static final ThreadLocal<Boolean> CHAT_CANCELLED = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<String> TYPED_LINE = new ThreadLocal<>();

    private NpcScriptSay() {}

    /**
     * @return the message to send, or {@code null} when it must not be sent
     */
    public static String sanitize(String message) {
        if (message == null) {
            return null;
        }
        if (message.isEmpty() || message.length() > MAX_MESSAGE_LENGTH) {
            return null;
        }
        return message;
    }

    /**
     * My NPCs writes a rewrite back as {@code Component.translatable("").append(...)}.
     * NeoForge then attaches that as unsigned signed-chat content, which clients drop.
     */
    public static boolean isEmptyKeyWriteback(Component message) {
        if (message == null) {
            return false;
        }
        return message.getContents() instanceof TranslatableContents contents
                && contents.getKey().isEmpty();
    }

    /**
     * {@code XenoPixels.cancelChat} sets this so a LOWEST {@code ServerChatEvent}
     * listener can cancel the signed line even when the onServerChat mixin misses.
     * My NPCs never copies {@code ChatEvent} cancel onto the Forge event.
     */
    public static void markChatCancelled() {
        CHAT_CANCELLED.set(Boolean.TRUE);
    }

    /**
     * CustomNPCs {@code ChatEvent} extends Forge {@code Event}, which is not on
     * this mod's compile classpath. Call {@code setCanceled} reflectively and
     * mark the ThreadLocal used by the LOWEST {@code ServerChatEvent} fallback.
     */
    public static boolean cancelScriptChat(Object event) {
        if (event == null) {
            return false;
        }
        markChatCancelled();
        try {
            event.getClass().getMethod("setCanceled", boolean.class).invoke(event, Boolean.TRUE);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

    public static boolean isScriptChatCanceled(Object event) {
        if (event == null) {
            return false;
        }
        try {
            Object value = event.getClass().getMethod("isCanceled").invoke(event);
            return Boolean.TRUE.equals(value);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    /** @return true once if {@link #markChatCancelled()} ran on this thread */
    public static boolean consumeChatCancelled() {
        boolean cancelled = Boolean.TRUE.equals(CHAT_CANCELLED.get());
        CHAT_CANCELLED.remove();
        return cancelled;
    }

    /**
     * Stashes the line the player actually typed for this {@code ServerChatEvent}.
     * {@code getRawText()} is empty when the submitted component is not root
     * {@code PlainTextContents}, so scripts otherwise see {@code <Name> rest} or nothing.
     */
    public static void markTypedLine(String typed) {
        TYPED_LINE.set(typed == null ? "" : typed);
    }

    public static String peekTypedLine() {
        String typed = TYPED_LINE.get();
        return typed == null ? "" : typed;
    }

    public static void clearTypedLine() {
        TYPED_LINE.remove();
    }

    /**
     * Prefer {@code getRawText()}. If that is empty, unwrap vanilla
     * {@code <Name> rest} and the {@code !clip*} decorated fallback.
     */
    public static String typedLine(String rawText, String decorated) {
        if (rawText != null && !rawText.isBlank()) {
            return rawText.trim();
        }
        String clip = XenoClipChat.typedLine(rawText, decorated);
        if (clip != null && !clip.isBlank() && !looksDecorated(clip)) {
            return clip;
        }
        return unwrapDecorated(decorated);
    }

    /**
     * Script-facing line. Uses the ThreadLocal typed text when the event field is
     * empty, still the typed line, or a {@code <Name> typed} decoration. A rewrite
     * such as {@code [Xeno] hello} is returned as-is.
     */
    public static String resolveChatMessage(String eventField) {
        String typed = peekTypedLine();
        String field = eventField == null ? "" : eventField;
        if (!typed.isEmpty()) {
            if (field.isEmpty() || field.equals(typed) || isDecoratedForm(field, typed)) {
                return typed;
            }
            return field;
        }
        return unwrapDecorated(field);
    }

    static boolean looksDecorated(String text) {
        return text != null && text.startsWith("<") && text.contains("> ");
    }

    static boolean isDecoratedForm(String field, String typed) {
        if (field == null || typed == null || typed.isEmpty() || !looksDecorated(field)) {
            return false;
        }
        int split = field.indexOf("> ");
        return split >= 0 && typed.equals(field.substring(split + 2).trim());
    }

    static String unwrapDecorated(String decorated) {
        if (decorated == null) {
            return "";
        }
        String text = decorated.trim();
        if (text.startsWith("<")) {
            int split = text.indexOf("> ");
            if (split >= 0) {
                return text.substring(split + 2).trim();
            }
        }
        return text;
    }

    /** Private system line. Never goes through {@code IPlayer.message}. */
    public static boolean tell(ServerPlayer player, String message) {
        String text = sanitize(message);
        if (text == null || player == null) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server != null && !server.isSameThread()) {
            server.execute(() -> player.sendSystemMessage(Component.literal(text)));
            return true;
        }
        player.sendSystemMessage(Component.literal(text));
        return true;
    }

    /**
     * Public rewrite on 1.21.1. {@code ServerChatEvent.setMessage} is signed-chat
     * unsigned content and clients drop it, so the original signed line is cancelled
     * and this system broadcast replaces it.
     *
     * <p>Must run on the server thread. Chat decorator callbacks can be off-thread;
     * use {@link #broadcastLater} from those.
     */
    public static boolean broadcast(ServerPlayer sender, String message) {
        String text = sanitize(message);
        if (text == null || sender == null || sender.getServer() == null) {
            return false;
        }
        Component line = decorated(sender, text);
        for (ServerPlayer viewer : sender.getServer().getPlayerList().getPlayers()) {
            viewer.sendSystemMessage(line);
        }
        return true;
    }

    /**
     * {@link #broadcast} on the server thread. Returns true once the send is
     * performed or queued; the queue still uses {@link MinecraftServer#execute}.
     */
    public static boolean broadcastLater(ServerPlayer sender, String message) {
        String text = sanitize(message);
        if (text == null || sender == null) {
            return false;
        }
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return false;
        }
        if (server.isSameThread()) {
            return broadcast(sender, text);
        }
        server.execute(() -> broadcast(sender, text));
        return true;
    }

    /**
     * Vanilla public-chat shape: {@code <name> text}. Used when a script rewrites
     * {@code ServerChatEvent}.
     */
    public static Component decorated(ServerPlayer player, String message) {
        return decorated(player.getDisplayName(), message);
    }

    public static Component decorated(Component displayName, String message) {
        String text = message == null ? "" : message;
        return Component.translatable(CHAT_TYPE_KEY, displayName, Component.literal(text));
    }

    /** True when a script changed the typed line and the Forge event should be rewritten. */
    public static boolean shouldRewrite(String rawText, String scriptMessage) {
        if (scriptMessage == null) {
            return false;
        }
        return !scriptMessage.equals(rawText == null ? "" : rawText);
    }
}
