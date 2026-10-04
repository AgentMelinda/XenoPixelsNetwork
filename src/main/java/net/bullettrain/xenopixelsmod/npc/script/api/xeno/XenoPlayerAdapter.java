package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import net.bullettrain.xenopixelsmod.features.progression.QuestDialogueFilter;
import net.bullettrain.xenopixelsmod.features.progression.QuestSync;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptEntity;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import xenoapi.npcs.api.IContainer;
import xenoapi.npcs.api.ITimers;
import xenoapi.npcs.api.block.IBlock;
import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.entity.data.IData;
import xenoapi.npcs.api.entity.data.IPlayerMail;
import xenoapi.npcs.api.gui.ICustomGui;
import xenoapi.npcs.api.handler.data.IQuest;
import xenoapi.npcs.api.item.IItemStack;

import java.util.function.Predicate;

/**
 * A server player as XenoAPI's {@link IPlayer}. Quests, messages and script data go through the
 * native {@link ScriptPlayer}; inventory, experience, game mode and spawn use vanilla calls.
 * Quest ids are the native quest slots the {@code player} binding already uses.
 */
public final class XenoPlayerAdapter extends XenoLivingAdapter<ServerPlayer> implements IPlayer<ServerPlayer> {
    static final int MAX_ITEM_AMOUNT = 64 * 36;

    public XenoPlayerAdapter(ServerPlayer entity) {
        super(entity);
    }

    private ScriptPlayer scriptPlayer() {
        return (ScriptPlayer) ScriptEntity.of(entity);
    }

    // ------------------------------------------------------------------ messaging / identity

    @Override public String getDisplayName() { return entity.getDisplayName().getString(); }

    @Override
    public void message(String message) {
        if (net.bullettrain.xenopixelsmod.compat.npc.NpcScriptSay.sanitize(message) == null) {
            throw new IllegalArgumentException("IPlayer.message: message must be non-empty and within the chat limit");
        }
        serverThread();
        scriptPlayer().message(message);
    }

    @Override
    public boolean isOp() {
        var server = entity.getServer();
        return server != null && server.getPlayerList().isOp(entity.getGameProfile());
    }

    @Override
    public void kick(String message) {
        String reason = XenoApiAdapters.boundedText("IPlayer.kick", message, 256);
        serverThread();
        entity.connection.disconnect(Component.literal(reason.isEmpty() ? "Kicked" : reason));
    }

    // ------------------------------------------------------------------ quests (native slots)

    @Override public boolean hasFinishedQuest(int id) { return scriptPlayer().hasFinishedQuest(id); }
    @Override public boolean hasActiveQuest(int id) { return scriptPlayer().hasActiveQuest(id); }

    @Override
    public void startQuest(int id) {
        serverThread();
        scriptPlayer().startQuest(id);
    }

    @Override
    public void finishQuest(int id) {
        serverThread();
        scriptPlayer().finishQuest(id);
    }

    @Override
    public void stopQuest(int id) {
        serverThread();
        scriptPlayer().stopQuest(id);
    }

    // ------------------------------------------------------------------ script data

    @Override public IData getTempdata() { return XenoDataAdapter.ofPlayer(() -> scriptPlayer().getTempdata()); }
    @Override public IData getStoreddata() { return XenoDataAdapter.ofPlayer(() -> scriptPlayer().getStoreddata()); }

    // ------------------------------------------------------------------ game mode / xp / food

    @Override public int getGamemode() { return entity.gameMode.getGameModeForPlayer().getId(); }

    @Override
    public void setGamemode(int mode) {
        if (mode < 0 || mode > 3) throw new IllegalArgumentException("IPlayer.setGamemode: mode must be 0-3");
        serverThread();
        entity.setGameMode(GameType.byId(mode));
    }

    @Override public int getExpLevel() { return entity.experienceLevel; }

    @Override
    public void setExpLevel(int level) {
        if (level < 0 || level > 1_000_000) throw new IllegalArgumentException("IPlayer.setExpLevel: level must be 0-1000000");
        serverThread();
        entity.setExperienceLevels(level);
    }

    @Override public int getHunger() { return entity.getFoodData().getFoodLevel(); }

    @Override
    public void setHunger(int level) {
        serverThread();
        entity.getFoodData().setFoodLevel(Math.max(0, Math.min(20, level)));
    }

    @Override
    public boolean hasAdvancement(String achievement) {
        ResourceLocation id = achievement == null ? null : ResourceLocation.tryParse(achievement);
        var server = entity.getServer();
        if (id == null || server == null) return false;
        var holder = server.getAdvancements().get(id);
        return holder != null && entity.getAdvancements().getOrStartProgress(holder).isDone();
    }

    // ------------------------------------------------------------------ spawn point

    @Override
    public void setSpawnpoint(int x, int y, int z) {
        serverThread();
        entity.setRespawnPosition(entity.level().dimension(), new BlockPos(x, y, z), 0.0f, true, false);
    }

    @Override
    public void resetSpawnpoint() {
        serverThread();
        entity.setRespawnPosition(Level.OVERWORLD, null, 0.0f, false, false);
    }

    // ------------------------------------------------------------------ inventory

    private static Predicate<ItemStack> matches(IItemStack item) {
        ItemStack wanted = XenoApiAdapters.unwrap(item);
        if (wanted.isEmpty()) throw new IllegalArgumentException("Item cannot be empty");
        return stack -> ItemStack.isSameItemSameComponents(stack, wanted);
    }

    private static Predicate<ItemStack> matches(String id) {
        var item = XenoApiAdapters.knownItem(id);
        return item == null ? stack -> false : stack -> stack.is(item);
    }

    private int count(Predicate<ItemStack> filter) {
        int total = 0;
        var inventory = entity.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && filter.test(stack)) total += stack.getCount();
        }
        return total;
    }

    /** Removes exactly {@code amount} matching items, or nothing when there are not enough. */
    private boolean remove(Predicate<ItemStack> filter, int amount) {
        if (amount < 1 || amount > MAX_ITEM_AMOUNT) throw new IllegalArgumentException("Amount must be 1-" + MAX_ITEM_AMOUNT);
        serverThread();
        if (count(filter) < amount) return false;
        var inventory = entity.getInventory();
        int left = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !filter.test(stack)) continue;
            int taken = Math.min(left, stack.getCount());
            stack.shrink(taken);
            left -= taken;
        }
        inventory.setChanged();
        return true;
    }

    @Override public int inventoryItemCount(IItemStack item) { return count(matches(item)); }
    @Override public int inventoryItemCount(String id) { return count(matches(id)); }
    @Override public boolean removeItem(IItemStack item, int amount) { return remove(matches(item), amount); }
    /** False for an unknown item id, as the reference documents. */
    @Override
    public boolean removeItem(String id, int amount) {
        if (XenoApiAdapters.knownItem(id) == null) return false;
        return remove(matches(id), amount);
    }

    @Override
    public void removeAllItems(IItemStack item) {
        Predicate<ItemStack> filter = matches(item);
        serverThread();
        entity.getInventory().clearOrCountMatchingItems(filter, -1, entity.inventoryMenu.getCraftSlots());
    }

    @Override public IItemStack getInventoryHeldItem() { return XenoApiAdapters.wrap(entity.getMainHandItem()); }

    @Override
    public boolean giveItem(IItemStack item) {
        ItemStack stack = XenoApiAdapters.unwrap(item).copy();
        if (stack.isEmpty()) return false;
        serverThread();
        return entity.getInventory().add(stack);
    }

    @Override
    public boolean giveItem(String id, int amount) {
        var item = XenoApiAdapters.item(id);
        if (amount < 1 || amount > item.getDefaultMaxStackSize()) {
            throw new IllegalArgumentException("IPlayer.giveItem: amount must be 1-" + item.getDefaultMaxStackSize());
        }
        serverThread();
        return entity.getInventory().add(new ItemStack(item, amount));
    }

    @Override
    public void giveOrDropItems(IItemStack[] items) {
        if (items == null) return;
        ItemStack[] stacks = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) stacks[i] = XenoApiAdapters.unwrap(items[i]).copy();
        serverThread();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && !entity.getInventory().add(stack)) entity.drop(stack, false);
        }
    }

    @Override
    public void updatePlayerInventory() {
        serverThread();
        entity.inventoryMenu.broadcastChanges();
    }

    @Override
    public void closeGui() {
        serverThread();
        entity.closeContainer();
    }

    // ------------------------------------------------------------------ sound

    @Override
    public void playSound(String sound, float volume, float pitch) {
        SoundEvent event = XenoApiAdapters.sound(sound);
        XenoApiAdapters.requireFinite("IPlayer.playSound", volume, pitch);
        serverThread();
        entity.playNotifySound(event, SoundSource.MASTER, Math.max(0.0f, Math.min(4.0f, volume)),
                Math.max(0.5f, Math.min(2.0f, pitch)));
    }

    // ------------------------------------------------------------------ factions (imported numbers)

    private static String faction(String method, int number) {
        String id = XenoScriptIds.factionId(number);
        if (id == null) throw new xenoapi.npcs.api.CustomNPCsException("%s: no faction has number %s", method, number);
        return id;
    }

    /** -1 hostile, 0 neutral, 1 friendly; an unknown faction number reads as neutral. */
    @Override
    public int factionStatus(int factionId) {
        String id = XenoScriptIds.factionId(factionId);
        return id == null ? 0 : XenoFactionAdapter.status(entity, id);
    }

    @Override
    public void addFactionPoints(int faction, int points) {
        String id = faction("IPlayer.addFactionPoints", faction);
        serverThread();
        data().addFactionStanding(id, points);
    }

    @Override
    public int getFactionPoints(int faction) {
        String id = XenoScriptIds.factionId(faction);
        return id == null ? 0 : XenoCapabilities.get(entity).map(d -> d.getFactionStanding(id)).orElse(0);
    }

    private XenoPlayerData data() {
        return XenoCapabilities.get(entity).orElseThrow(() -> new IllegalStateException("The player has no Xeno data"));
    }

    // ------------------------------------------------------------------ quests

    /** Drops the quest whether active or finished, so it can be taken again from the start. */
    @Override
    public void removeQuest(int id) {
        String quest = XenoScriptIds.questId(id);
        if (quest == null) return;
        serverThread();
        XenoPlayerData data = data();
        data.quests().abandon(quest);
        data.quests().forgetCompleted(quest);
        QuestSync.push(entity);
    }

    @Override
    public IQuest[] getActiveQuests() {
        java.util.List<IQuest> out = new java.util.ArrayList<>();
        for (var active : data().quests().actives()) {
            if (ParallelQuests.definition(active.id()) != null) out.add(new XenoQuestAdapter(active.id()));
        }
        return out.toArray(IQuest[]::new);
    }

    @Override
    public IQuest[] getFinishedQuests() {
        java.util.List<IQuest> out = new java.util.ArrayList<>();
        for (String id : data().quests().completed()) {
            if (ParallelQuests.definition(id) != null) out.add(new XenoQuestAdapter(id));
        }
        return out.toArray(IQuest[]::new);
    }

    /** Whether starting it now would succeed: it exists, its availability passes, and it is not held or on cooldown. */
    @Override
    public boolean canQuestBeAccepted(int id) {
        String quest = XenoScriptIds.questId(id);
        ParallelQuests.QuestDef def = quest == null ? null : ParallelQuests.definition(quest);
        if (def == null) return false;
        XenoPlayerData data = data();
        if (data.quests().isActive(def.id())) return false;
        if (data.quests().hasCompleted(def.id()) && !def.repeat().canRestart(
                data.quests().completedAt(def.id()), data.quests().completedAtReal(def.id()),
                entity.level().getGameTime(), System.currentTimeMillis())) {
            return false;
        }
        return QuestDialogueFilter.canOffer(entity, data, def.id());
    }

    // ------------------------------------------------------------------ dialogs (imported numbers)

    private static XenoScriptIds.Ref dialog(String method, int number) {
        XenoScriptIds.Ref ref = XenoScriptIds.dialog(number);
        if (ref == null) throw new xenoapi.npcs.api.CustomNPCsException("%s: no dialog has number %s", method, number);
        return ref;
    }

    @Override
    public boolean hasReadDialog(int id) {
        XenoScriptIds.Ref ref = XenoScriptIds.dialog(id);
        return ref != null && data().hasViewedDialogue(ref.group() + "/" + ref.id());
    }

    /** Opens the dialog as {@code name}, as though an NPC of that name had started it. */
    @Override
    public void showDialog(int id, String name) {
        XenoScriptIds.Ref ref = dialog("IPlayer.showDialog", id);
        String speaker = XenoApiAdapters.boundedText("IPlayer.showDialog", name, 64);
        serverThread();
        show(entity, new XenoDialogAdapter(ref.group(), ref.id()), entity.getId(), speaker);
    }

    /** Filters the tree for this player, records it as read, and opens it anchored on {@code hostEntityId}. */
    static void show(ServerPlayer player, XenoDialogAdapter dialog, int hostEntityId, String speaker) {
        var tree = QuestDialogueFilter.forPlayer(player, dialog.asShown());
        if (tree == null) return;
        net.bullettrain.xenopixelsmod.features.progression.ProgressionEvents.onDialogViewed(player, tree.start());
        XenoCapabilities.get(player).ifPresent(data -> data.recordViewedDialogue(dialog.ref()));
        net.bullettrain.xenopixelsmod.npc.dialog.ScriptShownDialogues.show(player, hostEntityId, speaker, tree, dialog.ref());
    }

    @Override
    public void removeDialog(int id) {
        XenoScriptIds.Ref ref = XenoScriptIds.dialog(id);
        if (ref == null) return;
        serverThread();
        data().forgetViewedDialogue(ref.group() + "/" + ref.id());
    }

    @Override
    public void addDialog(int id) {
        XenoScriptIds.Ref ref = dialog("IPlayer.addDialog", id);
        serverThread();
        data().recordViewedDialogue(ref.group() + "/" + ref.id());
    }

    // ------------------------------------------------------------------ inventory / permissions / timers

    @Override public IContainer getInventory() { return XenoContainerAdapter.of(entity.getInventory()); }

    /** A registered boolean NeoForge permission node, decided by the server's permission handler. */
    @Override public boolean hasPermission(String permission) { return XenoPermissions.has(entity, permission); }

    @Override
    public ITimers getTimers() {
        serverThread();
        return XenoTimersAdapter.forTimers(net.bullettrain.xenopixelsmod.npc.script.PlayerScriptTimers.of(entity),
                () -> entity.level().getGameTime());
    }

    // ------------------------------------------------------------------ spawn point

    /** The player's respawn block, or the world spawn when they have none. */
    @Override
    public IBlock getSpawnPoint() {
        var server = entity.getServer();
        BlockPos pos = entity.getRespawnPosition();
        ServerLevel level = server == null || pos == null ? null : server.getLevel(entity.getRespawnDimension());
        if (level == null) {
            level = server == null ? entity.serverLevel() : server.overworld();
            pos = level.getSharedSpawnPos();
        }
        return new XenoBlockAdapter(level, pos);
    }

    @Override
    public void setSpawnPoint(IBlock block) {
        if (!(block instanceof XenoBlockAdapter target)) {
            throw new IllegalArgumentException("IPlayer.setSpawnPoint: block must be a native block");
        }
        serverThread();
        entity.setRespawnPosition(target.level().dimension(), target.blockPos(), 0.0f, true, false);
    }

    // ------------------------------------------------------------------ messages

    /** A title and subtitle on screen; CustomNPCs' notification kinds all read the same. */
    @Override
    public void sendNotification(String title, String msg, int type) {
        String head = XenoApiAdapters.boundedText("IPlayer.sendNotification", title, 256);
        String body = XenoApiAdapters.boundedText("IPlayer.sendNotification", msg, 256);
        serverThread();
        entity.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(head)));
        entity.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal(body)));
    }

    /** Delivered at once: chat, items to the inventory, and the attached quest started. */
    @Override
    public void sendMail(IPlayerMail mail) {
        if (!(mail instanceof XenoPlayerMail letter)) {
            throw new IllegalArgumentException("IPlayer.sendMail: mail must come from NpcAPI.createMail");
        }
        serverThread();
        letter.deliver(entity);
    }

    /** Resets quests, read dialogs, faction points, transport and item-giver history; bank vaults stay. */
    @Override
    public void clearData() {
        serverThread();
        data().clearNpcProgress();
        QuestSync.push(entity);
    }

    // ------------------------------------------------------------------ music / website / trigger

    private static final java.util.Map<java.util.UUID, ResourceLocation> MUSIC =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Plays a sound to this player on the music channel. Native sounds are one-shot: {@code loops}
     * and {@code background} have no client-side player to honour them and are ignored.
     */
    @Override
    public void playMusic(String sound, boolean background, boolean loops) {
        SoundEvent event = XenoApiAdapters.sound(sound);
        serverThread();
        stopMusic();
        MUSIC.put(entity.getUUID(), event.getLocation());
        entity.playNotifySound(event, SoundSource.MUSIC, 1.0f, 1.0f);
    }

    @Override
    public void stopMusic() {
        serverThread();
        ResourceLocation playing = MUSIC.remove(entity.getUUID());
        if (playing != null) {
            entity.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(playing, SoundSource.MUSIC));
        }
    }

    /** A clickable link in chat: the client asks the player before opening it, as vanilla links do. */
    @Override
    public void openWebsite(String url) {
        String link = XenoApiAdapters.boundedText("IPlayer.openWebsite", url, 512);
        java.net.URI uri;
        try {
            uri = new java.net.URI(link);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("IPlayer.openWebsite: not a valid URL");
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("IPlayer.openWebsite: only http and https links");
        }
        serverThread();
        entity.sendSystemMessage(Component.literal(link).withStyle(style -> style
                .withColor(net.minecraft.ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, link))));
    }

    @Override
    public void trigger(int id, Object... arguments) {
        XenoScriptTriggers.fire(entity.serverLevel(), entity.blockPosition(), entity, id, arguments);
    }

    @Override public IContainer getOpenContainer() { return XenoContainerAdapter.of(entity.containerMenu); }

    // ------------------------------------------------------------------ unsupported

    /** A player's name is their account's; no server can rename it. */
    @Override public void setName(String name) { throw XenoApiAdapters.unsupported("IPlayer.setName (player names come from the account)"); }
    @Override public Object getPixelmonData() { throw XenoApiAdapters.unsupported("IPlayer.getPixelmonData (Pixelmon is not supported)"); }
    @Override public void showCustomGui(ICustomGui gui) { throw XenoApiAdapters.unsupported("IPlayer.showCustomGui (custom GUIs are not implemented natively)"); }
    @Override public ICustomGui getCustomGui() { throw XenoApiAdapters.unsupported("IPlayer.getCustomGui (custom GUIs are not implemented natively)"); }
    @Override public int getScreenWidth() { throw XenoApiAdapters.unsupported("IPlayer.getScreenWidth (the server never learns the client's screen size)"); }
    @Override public int getScreenHeight() { throw XenoApiAdapters.unsupported("IPlayer.getScreenHeight (the server never learns the client's screen size)"); }

    @Override
    public ServerPlayer getMCEntity() {
        throw XenoApiAdapters.unsupported("IPlayer.getMCEntity (raw handles are not exposed)");
    }
}
