package net.bullettrain.xenopixelsmod.client.npc;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.NpcProfileSaveClientState;
import net.bullettrain.xenopixelsmod.client.npc.script.XenoScriptLayout;
import net.bullettrain.xenopixelsmod.client.npc.store.ClientNpcStoreIndex;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasTabStrip;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoCodeArea;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.BindXenoNpcScriptPacket;
import net.bullettrain.xenopixelsmod.network.packet.NpcScriptPacket;
import net.bullettrain.xenopixelsmod.network.packet.XenoNpcStoreWritePacket;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcScripts;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStorePaths;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * The NPC script editor, laid out 1:1 with CustomNPCs' / My NPCs' {@code GuiScriptInterface}
 * (geometry in {@link XenoScriptLayout}, drawn from generated blue atlas panels).
 *
 * <ul>
 *   <li>Tabs across the top: <b>Settings</b>, then one tab per script ("1".."n"), then "+" while
 *       there are fewer than {@link NpcScriptContainer#MAX_TABS}. Switching tabs keeps the text.</li>
 *   <li>Script tab: the code area on the left; on the right Show/Hide Functions, Clear / Paste,
 *       Copy / Remove, Load Scripts and the list of scripts loaded into this tab. With functions
 *       shown, the right column lists the hooks this runtime fires and the calls it binds; a double
 *       click inserts one.</li>
 *   <li>Settings tab: the console (newest first), Language, Enabled, Open scripts folder (single
 *       player only), Copy / Clear, and API Doc / Examples, which open in-screen references to the
 *       real bindings - there is no website to link, so no link is invented.</li>
 * </ul>
 *
 * <p>Like the reference there is no Save button: tab text is kept while switching and written when
 * the screen closes. Each tab's text is its own entry in the world store's {@code scripts} folder,
 * written through the revision-checked store packet; the container (tabs, loaded scripts,
 * language, enabled) goes back to the NPC editor's save (editor mode) or straight to the NPC
 * (script tool). In library mode the tabs are the stored scripts themselves.
 */
public final class XenoNpcScriptScreen extends ScaledScreen {

    /** Drawn at the vanilla GUI Scale; see {@link NpcGuiScale}. */
    @Override
    protected float computeDynamicScale(float available) {
        return NpcGuiScale.dynamicScale(super.computeDynamicScale(available));
    }
    private static final int GOLD = 0xFFFFC14A;
    private static final int CYAN = 0xFF80D8FF;
    private static final int MUTED = 0xFF8AA4B8;
    private static final int LIGHT = 0xFFE7EDF3;
    private static final int WARN = 0xFFFF8A80;
    private static final int ROW_H = 10;
    /** ECMAScript is the one language the bundled engine runs; CustomNPCs lists the server's. */
    private static final List<String> LANGUAGES = List.of("ECMAScript");

    /** Calls a script can make, shown under the hooks when functions are visible. */
    private static final List<String> API = List.of(
            "npc.say(text)", "npc.say(text, palette)", "npc.say(text, palette, shape)",
            "npc.sayTo(player, text)", "npc.getName()", "npc.getTarget()", "npc.setTarget(entity)",
            "npc.clearTarget()", "npc.getHealth()", "npc.setHealth(v)", "npc.setPosition(x, y, z)",
            "npc.getStoredData(key)", "npc.setStoredData(key, v)", "npc.getTempData(key)",
            "npc.setTempData(key, v)", "npc.executeCommand(cmd)", "npc.getWorld()",
            "event.npc.getStoreddata()", "event.npc.getTempdata()",
            "data.put(key, v)", "data.get(key)", "data.has(key)",
            "data.remove(key)", "data.getKeys()", "data.clear()",
            "world.getTime()", "world.isDay()", "world.getPlayer(name)", "world.broadcast(text)",
            "player.message(text)", "player.addXenoPoints(n)", "player.startQuest(id)",
            "event.npc", "event.player", "event.source", "event.entity", "event.target",
            "event.damage", "event.getOption()",
            "event.setCanceled(true)", "log.line(text)");

    private enum Mode { EDITOR, TOOL, LIBRARY, PLAYER, FORGE }

    private enum Overlay { NONE, LOAD, REMOVE, HELP }

    /** One tab: the store script it edits, the scripts loaded ahead of it, and its text. */
    private static final class TabState {
        String id;
        final List<String> loaded = new ArrayList<>();
        String text = "";
        boolean fetched;
        boolean dirty;
        boolean created;
        String language = XenoNpcScripts.DEFAULT_LANGUAGE;
        boolean enabled = true;
        boolean chatOnly;

        TabState(String id) {
            this.id = id;
        }
    }

    private final Screen parent;
    private final Mode mode;
    private final int entityId;
    @Nullable
    private final Consumer<CompoundTag> onEditorClose;
    private final List<TabState> tabs = new ArrayList<>();
    private final List<String> console = new ArrayList<>();
    private boolean enabled = true;
    private String language = XenoNpcScripts.DEFAULT_LANGUAGE;
    private String engine = "";
    private int active = 1;
    private boolean showFunctions;
    private Overlay overlay = Overlay.NONE;
    private int pendingSaves;
    private int seenSaveResult;
    private boolean closing;

    private XenoScriptLayout layout;
    private int left;
    private int top;
    private XenoCodeArea code;
    private XenoCodeArea consoleArea;
    private XenoCodeArea helpArea;
    private ListBox sideList;
    private ListBox hooksList;
    private ListBox constsList;
    private ListBox availableList;
    private ListBox loadedList;

    /** From the NPC editor: the container returns to the editor's own save. */
    public static XenoNpcScriptScreen forEditor(Screen parent, int entityId, NpcScriptContainer container,
                                                Consumer<CompoundTag> onClose) {
        return forEditor(parent, entityId, container, new ListTag(), onClose);
    }

    /** As above, with the NPC's recent console lines (errors and prints) from the editor payload. */
    public static XenoNpcScriptScreen forEditor(Screen parent, int entityId, NpcScriptContainer container,
                                                ListTag console, Consumer<CompoundTag> onClose) {
        CompoundTag payload = containerTag(container);
        if (console != null && !console.isEmpty()) payload.put("Console", console.copy());
        return new XenoNpcScriptScreen(parent, Mode.EDITOR, entityId, payload, onClose);
    }

    /** From the script tool: the container is written to the NPC on close. */
    public static XenoNpcScriptScreen forTool(Screen parent, int entityId, CompoundTag payload) {
        return new XenoNpcScriptScreen(parent, Mode.TOOL, entityId, payload, null);
    }

    /** From Global: every stored script is a tab. */
    public static XenoNpcScriptScreen library(Screen parent) {
        return new XenoNpcScriptScreen(parent, Mode.LIBRARY, -1, new CompoundTag(), null);
    }

    /** Global player script tabs. Each tab has its own globals and hooks. */
    public static XenoNpcScriptScreen playerScripts(Screen parent) {
        return new XenoNpcScriptScreen(parent, Mode.PLAYER, -1, new CompoundTag(), null);
    }

    /** Forge (world event) script tabs. Each tab has its own globals and hooks. */
    public static XenoNpcScriptScreen forgeScripts(Screen parent) {
        return new XenoNpcScriptScreen(parent, Mode.FORGE, -1, new CompoundTag(), null);
    }

    private XenoNpcStoreCategory storeCategory() {
        return mode == Mode.PLAYER ? XenoNpcStoreCategory.PLAYER_SCRIPTS
                : mode == Mode.FORGE ? XenoNpcStoreCategory.FORGE_SCRIPTS : XenoNpcStoreCategory.SCRIPTS;
    }

    /** Tabs are store entries of their own (library, player, forge), not an NPC's container. */
    private boolean globalStore() {
        return mode == Mode.LIBRARY || mode == Mode.PLAYER || mode == Mode.FORGE;
    }

    /** Fetch ids for the global categories carry their category, see NpcScriptPacket. */
    private String wireId(String id) {
        return mode == Mode.PLAYER ? "player:" + id : mode == Mode.FORGE ? "forge:" + id : id;
    }

    private XenoNpcScriptScreen(Screen parent, Mode mode, int entityId, CompoundTag payload,
                                @Nullable Consumer<CompoundTag> onEditorClose) {
        super(Component.literal("Scripts"));
        this.parent = parent;
        this.mode = mode;
        this.entityId = entityId;
        this.onEditorClose = onEditorClose;
        if (globalStore()) {
            List<String> ids = new ArrayList<>();
            for (ClientNpcStoreIndex.Entry entry : ClientNpcStoreIndex.of(storeCategory())) {
                ids.add(entry.id());
            }
            ids.sort(String::compareTo);
            for (String id : ids) {
                if (tabs.size() >= NpcScriptContainer.MAX_TABS) break;
                tabs.add(new TabState(id));
            }
        } else {
            NpcScriptContainer container = NpcScriptContainer.read(payload);
            enabled = container.enabled();
            language = container.language();
            for (NpcScriptContainer.Tab tab : container.tabs()) {
                TabState state = new TabState(tab.scriptId());
                state.loaded.addAll(tab.loaded());
                tabs.add(state);
            }
            ListTag lines = payload.getList("Console", Tag.TAG_STRING);
            for (int i = 0; i < lines.size(); i++) console.add(lines.getString(i));
        }
        active = tabs.isEmpty() ? 0 : 1;
    }

    private static CompoundTag containerTag(NpcScriptContainer container) {
        CompoundTag tag = new CompoundTag();
        if (container != null) container.write(tag);
        return tag;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void init() {
        super.init();
        seenSaveResult = NpcProfileSaveClientState.sequence();
        layout = XenoScriptLayout.fit(getUiWidth(), getUiHeight());
        left = (getUiWidth() - layout.w) / 2;
        top = Math.max(20, (getUiHeight() - layout.h) / 2 + 10);
        boolean asked = false;
        for (TabState tab : tabs) {
            if (!tab.fetched && !tab.id.isEmpty() && !tab.created) {
                ModNetwork.sendToServer(new NpcScriptPacket(
                        wireId(tab.id), "", "", false));
                asked = true;
            }
        }
        if (!asked && engine.isEmpty()) {
            // Nothing to fetch; a blank fetch still answers with the server's engine line.
            ModNetwork.sendToServer(new NpcScriptPacket("", "", "", false));
        }
        rebuild();
    }

    @Override
    public void tick() {
        super.tick();
        int seq = NpcProfileSaveClientState.sequence();
        if (seq == seenSaveResult) return;
        seenSaveResult = seq;
        if (pendingSaves <= 0) return;
        pendingSaves--;
        if (!NpcProfileSaveClientState.success()) {
            log("save refused: " + NpcProfileSaveClientState.message());
        }
    }

    /** Reply to a fetch (tab text) from {@code NpcScriptResultPacket}. */
    public void receive(String id, String message, String transcript, @Nullable CompoundTag payload,
                        boolean ran, boolean ok) {
        if (message != null && message.startsWith("script engine:")) {
            engine = message.substring("script engine:".length()).trim();
        }
        for (TabState tab : tabs) {
            if (!wireId(tab.id).equals(id) || tab.fetched) continue;
            tab.fetched = true;
            if (ok && payload != null) {
                XenoNpcScripts.Script script = XenoNpcScripts.Script.of(id, payload);
                tab.text = script.script();
                tab.language = script.language();
                tab.enabled = script.enabled();
                tab.chatOnly = payload.getBoolean("ChatOnly");
            } else if (!ran && message != null && !message.isBlank()
                    && !message.startsWith("script engine:")) {
                log(id + ": " + message);
            }
        }
        if (ran && !ok && message != null && !message.isBlank()) log(id + ": " + message);
        if (ran && transcript != null && !transcript.isBlank()) log(transcript);
        rebuild();
    }

    private void log(String line) {
        String stamp;
        synchronized (XenoNpcScriptScreen.class) {
            stamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        }
        console.add(0, stamp + " " + line);
        while (console.size() > 200) console.remove(console.size() - 1);
        if (consoleArea != null) consoleArea.setText(String.join("\n", console));
    }

    // ---------------------------------------------------------------- model

    private TabState current() {
        return active >= 1 && active <= tabs.size() ? tabs.get(active - 1) : null;
    }

    private void keepCodeText() {
        TabState tab = current();
        if (tab != null && code != null && !tab.text.equals(code.getText())) {
            tab.text = code.getText();
            tab.dirty = true;
        }
    }

    private String newScriptId() {
        String prefix = mode == Mode.LIBRARY ? "script_" : mode == Mode.PLAYER ? "player_"
                : mode == Mode.FORGE ? "forge_" : "npc_" + npcKey() + "_";
        for (int n = 1; ; n++) {
            String id = prefix + n;
            boolean taken = ClientNpcStoreIndex.find(storeCategory(),
                    XenoNpcScripts.NO_GROUP, id) != null;
            for (TabState tab : tabs) taken |= tab.id.equals(id);
            if (!taken && XenoNpcStorePaths.reject(id) == null) return id;
        }
    }

    private String npcKey() {
        if (minecraft != null && minecraft.level != null && minecraft.level.getEntity(entityId) != null) {
            return minecraft.level.getEntity(entityId).getUUID().toString().substring(0, 8);
        }
        return "e" + Math.max(0, entityId);
    }

    private void addTab() {
        keepCodeText();
        if (tabs.size() >= NpcScriptContainer.MAX_TABS) return;
        TabState tab = new TabState(newScriptId());
        tab.fetched = true;
        tab.created = true;
        tab.dirty = true;
        tab.language = language;
        tabs.add(tab);
        active = tabs.size();
        rebuild();
    }

    private void selectTab(int index) {
        keepCodeText();
        if (index == tabs.size() + 1) {
            addTab();
            return;
        }
        active = Math.max(0, Math.min(tabs.size(), index));
        overlay = Overlay.NONE;
        rebuild();
    }

    private void removeTab() {
        TabState tab = current();
        if (tab == null) return;
        tabs.remove(tab);
        // A script this screen created for this NPC goes with its tab; a shared library script
        // someone chose to load is only unlinked, never deleted from under other NPCs.
        boolean ownScript = globalStore() || tab.id.startsWith("npc_" + npcKey() + "_");
        if (ownScript && !tab.created) {
            ModNetwork.sendToServer(new XenoNpcStoreWritePacket(storeCategory().ordinal(),
                    XenoNpcScripts.NO_GROUP, tab.id, true, revisionOf(tab.id), new CompoundTag()));
            pendingSaves++;
        }
        active = 0;
        overlay = Overlay.NONE;
        rebuild();
    }

    private int revisionOf(String id) {
        ClientNpcStoreIndex.Entry entry =
                ClientNpcStoreIndex.find(storeCategory(), XenoNpcScripts.NO_GROUP, id);
        return entry == null ? 0 : entry.revision();
    }

    /** Writes every changed tab to the store, then hands the container back. */
    private void saveAll() {
        keepCodeText();
        for (TabState tab : tabs) {
            if (!tab.dirty) continue;
            String lang = globalStore() ? tab.language : language;
            boolean on = !globalStore() || tab.enabled;
            CompoundTag payload = new XenoNpcScripts.Script(tab.id, tab.id, lang, on, tab.text).toTag();
            if (mode == Mode.PLAYER) payload.putBoolean("ChatOnly", tab.chatOnly);
            String invalid = XenoNpcScripts.rejectPayload(payload);
            if (invalid != null) {
                log(tab.id + " not saved: " + invalid);
                continue;
            }
            ModNetwork.sendToServer(new XenoNpcStoreWritePacket(storeCategory().ordinal(),
                    XenoNpcScripts.NO_GROUP, tab.id, false, revisionOf(tab.id), payload));
            pendingSaves++;
            tab.dirty = false;
            tab.created = false;
        }
        if (globalStore()) return;
        NpcScriptContainer container = new NpcScriptContainer();
        container.setEnabled(enabled);
        container.setLanguage(language);
        List<NpcScriptContainer.Tab> out = new ArrayList<>();
        for (TabState tab : tabs) out.add(new NpcScriptContainer.Tab(tab.id, tab.loaded));
        container.setTabs(out);
        CompoundTag tag = containerTag(container);
        if (mode == Mode.TOOL) {
            ModNetwork.sendToServer(new BindXenoNpcScriptPacket(entityId, tag));
        } else if (onEditorClose != null) {
            onEditorClose.accept(tag);
        }
    }

    // ---------------------------------------------------------------- layout

    private void rebuild() {
        clearWidgets();
        code = null;
        consoleArea = null;
        helpArea = null;
        sideList = hooksList = constsList = availableList = loadedList = null;

        List<Component> labels = new ArrayList<>();
        labels.add(Component.literal("Settings"));
        for (int i = 1; i <= tabs.size(); i++) labels.add(Component.literal(String.valueOf(i)));
        if (tabs.size() < NpcScriptContainer.MAX_TABS) labels.add(Component.literal("+"));
        addRenderableWidget(new AtlasTabStrip(left + 4, top - 17, labels, active, this::selectTab));

        // An overlay replaces the tab's own widgets rather than drawing over them: vanilla
        // flushes button text after every quad, so labels underneath bled through the overlay.
        switch (overlay) {
            case LOAD -> buildLoadOverlay();
            case REMOVE -> buildRemoveOverlay();
            case HELP -> buildHelpOverlay();
            default -> {
                if (active == 0) {
                    buildSettings();
                } else {
                    buildScriptTab();
                }
            }
        }
    }

    private void buildScriptTab() {
        TabState tab = current();
        int y = top + XenoScriptLayout.MARGIN;
        code = new XenoCodeArea(left + XenoScriptLayout.MARGIN, y, layout.code(), false, true);
        code.setText(tab == null ? "" : tab.text);
        code.setOnChange(text -> {
            TabState t = current();
            if (t != null) {
                t.text = text;
                t.dirty = true;
            }
        });
        addRenderableWidget(code);
        if (overlay == Overlay.NONE) setFocused(code);

        int x5 = left + layout.sideX();
        addRenderableWidget(button(x5, y, 121, showFunctions ? "Hide Functions" : "Show Functions", () -> {
            keepCodeText();
            showFunctions = !showFunctions;
            rebuild();
        }));
        if (showFunctions) {
            hooksList = new ListBox(x5, y + 22, layout.hooks(),
                    mode == Mode.PLAYER ? net.bullettrain.xenopixelsmod.npc.script.PlayerScriptHost.PLAYER_HOOKS
                            : mode == Mode.FORGE ? net.bullettrain.xenopixelsmod.npc.script.ForgeScriptHost.HOOKS
                            : NpcScriptHost.HOOKS,
                    item -> code.insert("function " + item + "(event) {\n    \n}\n"));
            constsList = new ListBox(x5, y + 22 + layout.hooksH + 4, layout.consts(), API,
                    item -> code.insert(item));
            return;
        }
        addRenderableWidget(button(x5, y + 22, 60, "Clear", () -> {
            code.setText("");
            TabState t = current();
            if (t != null) {
                t.text = "";
                t.dirty = true;
            }
        }));
        addRenderableWidget(button(x5 + 61, y + 22, 60, "Paste", () -> {
            if (minecraft == null) return;
            code.setText(minecraft.keyboardHandler.getClipboard());
            TabState t = current();
            if (t != null) {
                t.text = code.getText();
                t.dirty = true;
            }
        }));
        addRenderableWidget(button(x5, y + 43, 60, "Copy", () -> {
            if (minecraft != null) minecraft.keyboardHandler.setClipboard(code.getText());
        }));
        addRenderableWidget(button(x5 + 61, y + 43, 60, "Remove", () -> {
            keepCodeText();
            overlay = Overlay.REMOVE;
            rebuild();
        }));
        if (mode == Mode.PLAYER && tab != null) {
            addRenderableWidget(button(x5, y + 66, 121,
                    tab.chatOnly ? "Chat hook only: Yes" : "Chat hook only: No", () -> {
                        tab.chatOnly = !tab.chatOnly;
                        tab.dirty = true;
                        rebuild();
                    }));
        } else {
            addRenderableWidget(button(x5, y + 66, 121, "Load Scripts", () -> {
                keepCodeText();
                overlay = Overlay.LOAD;
                rebuild();
            }));
        }
        sideList = new ListBox(x5, top + XenoScriptLayout.FILES_TOP, layout.files(),
                tab == null ? List.of() : tab.loaded, item -> { });
        sideList.selectable = false;
    }

    private void buildSettings() {
        consoleArea = new XenoCodeArea(left + XenoScriptLayout.MARGIN, top + XenoScriptLayout.MARGIN,
                layout.console(),
                true, false);
        consoleArea.setText(String.join("\n", console));
        addRenderableWidget(consoleArea);

        int x4 = left + layout.settingsX();
        TabState lastTab = tabs.isEmpty() ? null : tabs.get(0);
        String shownLanguage = LANGUAGES.get(0);
        addRenderableWidget(button(x4 + 81, top + 10, 80, shownLanguage, () -> {
            // One engine, one language: the cycler stays, as in the reference, and cycles to itself.
            language = XenoNpcScripts.DEFAULT_LANGUAGE;
        }));
        boolean on = globalStore() ? lastTab == null || lastTab.enabled : enabled;
        addRenderableWidget(button(x4 + 81, top + 32, 50, on ? "Yes" : "No", () -> {
            if (globalStore()) {
                for (TabState tab : tabs) {
                    tab.enabled = !on;
                    tab.dirty = true;
                }
            } else {
                enabled = !enabled;
            }
            rebuild();
        }));
        if (minecraft != null && minecraft.hasSingleplayerServer()) {
            addRenderableWidget(button(x4, top + 56, 150, "Open scripts folder", this::openFolder));
        }
        addRenderableWidget(button(x4, top + 80, 80, "API Doc", () -> openHelp(apiDoc())));
        addRenderableWidget(button(x4 + 81, top + 80, 80, "Examples", () -> openHelp(examples())));
        int bottomRow = top + layout.h - XenoScriptLayout.MARGIN - 20;
        addRenderableWidget(button(x4, bottomRow, 60, "Copy", () -> {
            if (minecraft != null) minecraft.keyboardHandler.setClipboard(String.join("\n", console));
        }));
        addRenderableWidget(button(x4 + 61, bottomRow, 60, "Clear", () -> {
            console.clear();
            rebuild();
        }));
    }

    private void openFolder() {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) return;
        java.nio.file.Path folder = minecraft.getSingleplayerServer().getWorldPath(
                new net.minecraft.world.level.storage.LevelResource(
                        net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore.ROOT_FOLDER))
                .resolve(storeCategory().folder());
        folder.toFile().mkdirs();
        net.minecraft.Util.getPlatform().openFile(folder.toFile());
    }

    private String helpText = "";

    private void openHelp(String text) {
        helpText = text;
        overlay = Overlay.HELP;
        rebuild();
    }

    private void buildHelpOverlay() {
        helpArea = new XenoCodeArea(left + XenoScriptLayout.MARGIN, top + XenoScriptLayout.MARGIN,
                layout.code(), true, false);
        helpArea.setText(helpText);
        addRenderableWidget(helpArea);
        setFocused(helpArea);
        addRenderableWidget(button(left + layout.sideX(), top + XenoScriptLayout.MARGIN, 60, "Done", () -> {
            overlay = Overlay.NONE;
            rebuild();
        }));
    }

    private void buildLoadOverlay() {
        TabState tab = current();
        if (tab == null) {
            overlay = Overlay.NONE;
            return;
        }
        int px = left + (layout.w - 346) / 2;
        int py = top + Math.max(0, (layout.h - 216) / 2);
        List<String> available = new ArrayList<>();
        for (ClientNpcStoreIndex.Entry entry : ClientNpcStoreIndex.of(XenoNpcStoreCategory.SCRIPTS)) {
            if (!entry.id().equals(tab.id) && !tab.loaded.contains(entry.id())) available.add(entry.id());
        }
        available.sort(String::compareTo);
        availableList = new ListBox(px + 4, py + 14, "xeno_script_list_w140_h180", available, item -> { });
        loadedList = new ListBox(px + 200, py + 14, "xeno_script_list_w140_h180", tab.loaded, item -> { });
        addRenderableWidget(button(px + 145, py + 40, 55, ">", () -> {
            String pick = availableList.selectedItem();
            if (pick != null && tab.loaded.size() < NpcScriptContainer.MAX_LOADED) tab.loaded.add(pick);
            rebuild();
        }));
        addRenderableWidget(button(px + 145, py + 62, 55, "<", () -> {
            String pick = loadedList.selectedItem();
            if (pick != null) tab.loaded.remove(pick);
            rebuild();
        }));
        addRenderableWidget(button(px + 145, py + 90, 55, ">>", () -> {
            for (String id : available) {
                if (tab.loaded.size() >= NpcScriptContainer.MAX_LOADED) break;
                tab.loaded.add(id);
            }
            rebuild();
        }));
        addRenderableWidget(button(px + 145, py + 112, 55, "<<", () -> {
            tab.loaded.clear();
            rebuild();
        }));
        addRenderableWidget(button(px + 260, py + 194, 60, "Done", () -> {
            overlay = Overlay.NONE;
            rebuild();
        }));
    }

    private void buildRemoveOverlay() {
        int px = left + (layout.w - 210) / 2;
        int py = top + (layout.h - 150) / 2;
        addRenderableWidget(button(px + 38, py + 112, 60, "Yes", this::removeTab));
        addRenderableWidget(button(px + 112, py + 112, 60, "No", () -> {
            overlay = Overlay.NONE;
            rebuild();
        }));
    }

    private AtlasButton button(int x, int y, int w, String label, Runnable action) {
        return new AtlasButton(x, y, Component.literal(label), XenoScriptLayout.button(w),
                b -> action.run());
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xA0000000);
        beginUiScale(g);
        XenoAtlasSprites.blit(g, layout.frame(), left, top);
        int uiMouseX = (int) toUiX(mouseX);
        int uiMouseY = (int) toUiY(mouseY);
        if (active == 0 && overlay == Overlay.NONE) {
            int x4 = left + layout.settingsX();
            g.drawString(font, "Language", x4, top + 16, LIGHT, false);
            g.drawString(font, "Enabled", x4, top + 38, LIGHT, false);
            String status = engine.isEmpty() ? "" : "Engine: " + engine;
            g.drawString(font, status, x4, top + 106, CYAN, false);
            if (globalStore()) {
                g.drawString(font, "Library: " + tabs.size() + " scripts", x4, top + 118, MUTED, false);
            }
        }
        super.render(g, uiMouseX, uiMouseY, partialTick);
        if (sideList != null) sideList.render(g, uiMouseX, uiMouseY);
        if (hooksList != null) {
            hooksList.render(g, uiMouseX, uiMouseY);
            constsList.render(g, uiMouseX, uiMouseY);
        }
        if (overlay == Overlay.LOAD && availableList != null) {
            int px = left + (layout.w - 346) / 2;
            int py = top + Math.max(0, (layout.h - 216) / 2);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            XenoAtlasSprites.blit(g, "xeno_script_load_panel", px, py);
            g.drawString(font, "Available Scripts", px + 6, py + 4, LIGHT, false);
            g.drawString(font, "Loaded Scripts", px + 202, py + 4, LIGHT, false);
            availableList.render(g, uiMouseX, uiMouseY);
            loadedList.render(g, uiMouseX, uiMouseY);
            g.pose().popPose();
            renderOverlayButtons(g, mouseX, mouseY, partialTick);
        }
        if (overlay == Overlay.REMOVE) {
            int px = left + (layout.w - 210) / 2;
            int py = top + (layout.h - 150) / 2;
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            XenoAtlasSprites.blit(g, "xeno_color_picker_panel", XenoAtlasSprites.Theme.GOLD, px, py);
            TabState tab = current();
            g.drawCenteredString(font, "Remove script tab " + active + "?", px + 105, py + 30, GOLD);
            if (tab != null) {
                g.drawCenteredString(font, tab.id, px + 105, py + 48, LIGHT);
            }
            g.drawCenteredString(font, "This cannot be undone.", px + 105, py + 70, WARN);
            g.pose().popPose();
            renderOverlayButtons(g, mouseX, mouseY, partialTick);
        }
        endUiScale(g);
    }

    /** Overlay buttons are the last widgets added; draw them again above the overlay panel. */
    private void renderOverlayButtons(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 310);
        int count = overlay == Overlay.LOAD ? 5 : 2;
        var widgets = children();
        for (int i = Math.max(0, widgets.size() - count); i < widgets.size(); i++) {
            if (widgets.get(i) instanceof AtlasButton b) {
                b.render(g, (int) toUiX(mouseX), (int) toUiY(mouseY), partialTick);
            }
        }
        g.pose().popPose();
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double ux = toUiX(mouseX);
        double uy = toUiY(mouseY);
        if (overlay == Overlay.LOAD) {
            if (availableList.click(ux, uy) || loadedList.click(ux, uy)) return true;
            if (clickLastWidgets(mouseX, mouseY, button, 5)) return true;
            return true;
        }
        if (overlay == Overlay.REMOVE) {
            clickLastWidgets(mouseX, mouseY, button, 2);
            return true;
        }
        if (sideList != null && sideList.click(ux, uy)) return true;
        if (hooksList != null && (hooksList.click(ux, uy) || constsList.click(ux, uy))) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Clicks go only to the overlay's own buttons while it is open. */
    private boolean clickLastWidgets(double mouseX, double mouseY, int button, int count) {
        var widgets = children();
        for (int i = widgets.size() - 1; i >= Math.max(0, widgets.size() - count); i--) {
            if (widgets.get(i) instanceof AtlasButton b
                    && b.mouseClicked(toUiX(mouseX), toUiY(mouseY), button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        double ux = toUiX(mouseX);
        double uy = toUiY(mouseY);
        for (ListBox list : new ListBox[] {sideList, hooksList, constsList, availableList, loadedList}) {
            if (list != null && list.scroll(ux, uy, sy)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && overlay != Overlay.NONE) {
            overlay = Overlay.NONE;
            rebuild();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
        if (!closing) {
            closing = true;
            saveAll();
        }
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- references

    private static String apiDoc() {
        StringBuilder out = new StringBuilder();
        out.append("// Xeno NPC scripts - ECMAScript (bundled Nashorn, sandboxed: no Java access)\n");
        out.append("// Each tab has its own globals. Define a function named after a hook;\n");
        out.append("// it is called with one argument, event.\n\n// Hooks\n");
        for (String hook : NpcScriptHost.HOOKS) out.append("function ").append(hook).append("(event) {}\n");
        out.append("\n// tick runs every ").append(NpcScriptHost.TICK_INTERVAL).append(" ticks.\n");
        out.append("// interact, damaged and meleeAttack may call event.setCanceled(true).\n");
        out.append("// meleeAttack may change event.damage (damage before defender mitigation).\n");
        out.append("// Storeddata keeps numbers and strings; tempdata lasts until script host reload.\n\n// Calls\n");
        for (String call : API) out.append(call).append('\n');
        out.append("\n// Bubble palettes: blue, gold, green, red\n");
        out.append("// Bubble shapes:   rounded, thought, shout, banner\n");
        return out.toString();
    }

    private static String examples() {
        return String.join("\n",
                "// Greets a player in a gold shout bubble, then counts visits.",
                "function interact(event) {",
                "    var npc = event.npc;",
                "    var data = npc.getStoreddata();",
                "    var visits = (data.get(\"visits\") || 0) + 1;",
                "    data.put(\"visits\", visits);",
                "    npc.say(\"Hey \" + event.player.getName() + \"!\", \"gold\", \"shout\");",
                "    event.player.message(\"You have visited \" + visits + \" times.\");",
                "}",
                "",
                "// Thinks out loud now and then.",
                "function tick(event) {",
                "    if (Math.random() < 0.02) {",
                "        event.npc.say(\"Hmm...\", \"blue\", \"thought\");",
                "    }",
                "}",
                "",
                "// Refuses to be hurt by players below full health.",
                "function damaged(event) {",
                "    if (event.player && event.player.getHealth() < event.player.getMaxHealth()) {",
                "        event.setCanceled(true);",
                "        event.npc.say(\"Heal up first.\", \"red\", \"banner\");",
                "    }",
                "}");
    }

    // ---------------------------------------------------------------- lists

    /** A scrolling list over a generated panel; double-click runs the action. */
    private final class ListBox {
        final int x;
        final int y;
        final String sprite;
        final int w;
        final int h;
        final List<String> items;
        final Consumer<String> onDouble;
        boolean selectable = true;
        int scroll;
        int selected = -1;
        long lastClick;

        ListBox(int x, int y, String sprite, List<String> items, Consumer<String> onDouble) {
            this.x = x;
            this.y = y;
            this.sprite = sprite;
            this.w = XenoAtlasSprites.get(sprite).width();
            this.h = XenoAtlasSprites.get(sprite).height();
            this.items = items;
            this.onDouble = onDouble;
        }

        int visible() {
            return Math.max(1, (h - 8) / ROW_H);
        }

        String selectedItem() {
            return selected >= 0 && selected < items.size() ? items.get(selected) : null;
        }

        boolean contains(double ux, double uy) {
            return ux >= x && ux < x + w && uy >= y && uy < y + h;
        }

        boolean click(double ux, double uy) {
            if (!contains(ux, uy)) return false;
            int index = scroll + (int) ((uy - y - 4) / ROW_H);
            if (index < 0 || index >= items.size()) return true;
            long now = System.currentTimeMillis();
            if (index == selected && now - lastClick < 300) onDouble.accept(items.get(index));
            if (selectable) selected = index;
            lastClick = now;
            return true;
        }

        boolean scroll(double ux, double uy, double sy) {
            if (!contains(ux, uy)) return false;
            scroll = Math.max(0, Math.min(Math.max(0, items.size() - visible()),
                    scroll + (sy > 0 ? -2 : 2)));
            return true;
        }

        void render(GuiGraphics g, int mx, int my) {
            XenoAtlasSprites.blit(g, sprite, x, y);
            XenoCodeArea.scissor(g, x + 3, y + 3, x + w - 3, y + h - 3);
            for (int row = 0; row < visible() && scroll + row < items.size(); row++) {
                int index = scroll + row;
                int ry = y + 4 + row * ROW_H;
                boolean over = mx >= x && mx < x + w && my >= ry && my < ry + ROW_H;
                if (index == selected) g.fill(x + 3, ry - 1, x + w - 3, ry + ROW_H - 1, 0x60FFC14A);
                else if (over) g.fill(x + 3, ry - 1, x + w - 3, ry + ROW_H - 1, 0x30FFFFFF);
                g.drawString(font, items.get(index), x + 6, ry, index == selected ? GOLD : LIGHT, false);
            }
            g.disableScissor();
            if (items.isEmpty()) {
                g.drawString(font, "(none)", x + 6, y + 4, MUTED, false);
            }
        }
    }
}
