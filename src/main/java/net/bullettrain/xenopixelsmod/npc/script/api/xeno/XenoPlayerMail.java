package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import xenoapi.npcs.api.entity.data.IPlayerMail;
import xenoapi.npcs.api.handler.data.IQuest;
import xenoapi.npcs.api.item.IItemStack;

/**
 * A letter for {@code IPlayer.sendMail}. There is no mailbox block natively, so delivery is
 * immediate: the letter appears in chat, its items go to the inventory (or the ground when full),
 * and an attached quest is started through the same rules as any other quest start.
 */
public final class XenoPlayerMail implements IPlayerMail {
    static final int SLOTS = 4;
    static final int MAX_TEXT = 1024;

    private String sender;
    private String subject;
    private String text = "";
    private String questId = "";
    private final ItemStack[] items = new ItemStack[SLOTS];

    XenoPlayerMail(String sender, String subject) {
        this.sender = XenoApiAdapters.boundedText("IPlayerMail.sender", sender, 64);
        this.subject = XenoApiAdapters.boundedText("IPlayerMail.subject", subject, 128);
        java.util.Arrays.fill(items, ItemStack.EMPTY);
    }

    @Override public String getSender() { return sender; }
    @Override public void setSender(String sender) { this.sender = XenoApiAdapters.boundedText("IPlayerMail.setSender", sender, 64); }
    @Override public String getSubject() { return subject; }
    @Override public void setSubject(String subject) { this.subject = XenoApiAdapters.boundedText("IPlayerMail.setSubject", subject, 128); }
    @Override public String getText() { return text; }
    @Override public void setText(String text) { this.text = XenoApiAdapters.boundedText("IPlayerMail.setText", text, MAX_TEXT); }

    @Override
    public IQuest getQuest() {
        return questId.isEmpty() || ParallelQuests.definition(questId) == null ? null : new XenoQuestAdapter(questId);
    }

    /** The quest with that CustomNPCs number; an unknown number clears it. */
    @Override
    public void setQuest(int id) {
        String quest = XenoScriptIds.questId(id);
        questId = quest == null ? "" : quest;
    }

    private static int slot(String method, int slot) {
        if (slot < 0 || slot >= SLOTS) throw new IllegalArgumentException(method + ": slot must be 0-" + (SLOTS - 1));
        return slot;
    }

    @Override public IItemStack getItem(int slot) { return XenoApiAdapters.wrap(items[slot("IPlayerMail.getItem", slot)].copy()); }
    @Override public void setItem(int slot, IItemStack item) { items[slot("IPlayerMail.setItem", slot)] = XenoApiAdapters.unwrap(item).copy(); }

    void deliver(ServerPlayer player) {
        String from = sender.isEmpty() ? "Someone" : sender;
        player.sendSystemMessage(Component.literal("✉ " + from + ": ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(subject.isEmpty() ? "(no subject)" : subject).withStyle(ChatFormatting.WHITE)));
        if (!text.isEmpty()) player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            ItemStack copy = stack.copy();
            if (!player.getInventory().add(copy)) player.drop(copy, false);
        }
        if (!questId.isEmpty()) {
            String refusal = ParallelQuests.start(player, questId);
            if (refusal != null) player.sendSystemMessage(Component.literal(refusal));
        }
    }
}
