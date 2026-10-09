package net.bullettrain.xenopixelsmod.client.keybind;

import com.dragonminez.client.util.KeyBinds;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient;
import net.bullettrain.xenopixelsmod.client.combat.v2.V2Keys;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The XenoPixels keybind system: back up every binding, unbind the ones that belong to other mods,
 * restore a backup, and apply the v2 layout.
 *
 * <p>A modpack ships dozens of mods that each claim a handful of keys, and the v2 layout needs
 * most of the keyboard around WASD. The cleanup clears everything that is not Minecraft's,
 * DragonMineZ's or XenoPixels' so those keys are free. It never runs without first writing a
 * backup, and {@code /xenokeybind restore} puts every binding back exactly as it was.
 *
 * <p>One kind of foreign binding is left alone: another mod's held modifier, such as Create's
 * "Shift modifier". See {@link KeybindRules#heldModifier}. Unbinding those crashed the game, and
 * {@link #repairOnce} puts them back on profiles an earlier build already cleaned.
 *
 * <p>Client only. Nothing here is sent to or decided by a server.
 */
public final class XenoKeybinds {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** What a cleanup did, for the command's feedback. */
    public record CleanResult(Path backup, int unbound, int kept) {}

    private XenoKeybinds() {}

    public static Path backupDir() {
        return FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod").resolve("keybind-backups");
    }

    // ---- ownership ----

    /**
     * Minecraft's own mappings, by identity. Taken from the {@link Options} fields rather than
     * guessed from translation keys: a mod is free to name a key {@code key.something} or to put
     * it in a vanilla category, and neither makes it Minecraft's.
     */
    static Set<KeyMapping> vanilla(Options o) {
        Set<KeyMapping> set = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(set,
                o.keyUp, o.keyLeft, o.keyDown, o.keyRight, o.keyJump, o.keyShift, o.keySprint,
                o.keyInventory, o.keySwapOffhand, o.keyDrop, o.keyUse, o.keyAttack, o.keyPickItem,
                o.keyChat, o.keyPlayerList, o.keyCommand, o.keySocialInteractions, o.keyScreenshot,
                o.keyTogglePerspective, o.keySmoothCamera, o.keyFullscreen, o.keySpectatorOutlines,
                o.keyAdvancements, o.keySaveHotbarActivator, o.keyLoadHotbarActivator);
        Collections.addAll(set, o.keyHotbarSlots);
        return set;
    }

    /** Whether the cleanup may unbind {@code mapping}: the whole of {@link KeybindRules#mayUnbind}. */
    private static boolean mayUnbind(KeyMapping mapping, Set<KeyMapping> vanilla) {
        InputConstants.Key byDefault = mapping.getDefaultKey();
        return KeybindRules.mayUnbind(mapping.getName(), vanilla.contains(mapping),
                XenoClientConfig.keybindKeepPrefixes,
                byDefault.getType() == InputConstants.Type.KEYSYM, byDefault.getValue());
    }

    /** How many bindings a cleanup would unbind right now, without changing anything. */
    public static int countForeign(Minecraft mc) {
        Set<KeyMapping> vanilla = vanilla(mc.options);
        int count = 0;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (!mapping.isUnbound() && mayUnbind(mapping, vanilla)) count++;
        }
        return count;
    }

    // ---- backup / restore ----

    /** Writes every mapping's current key and modifier to a new backup file. */
    public static Path backup(Minecraft mc) throws IOException {
        Path dir = backupDir();
        Files.createDirectories(dir);
        Path file = dir.resolve(KeybindRules.backupFileName(LocalDateTime.now().format(STAMP)));
        int suffix = 1;
        while (Files.exists(file)) {
            file = dir.resolve(KeybindRules.backupFileName(
                    LocalDateTime.now().format(STAMP) + "-" + suffix++));
        }
        JsonObject bindings = new JsonObject();
        for (KeyMapping mapping : mc.options.keyMappings) {
            JsonObject entry = new JsonObject();
            entry.addProperty("key", mapping.getKey().getName());
            entry.addProperty("modifier", mapping.getKeyModifier().name());
            bindings.add(mapping.getName(), entry);
        }
        JsonObject root = new JsonObject();
        root.addProperty("created", LocalDateTime.now().toString());
        root.add("bindings", bindings);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        return file;
    }

    /** Backup file names, newest first. */
    public static List<String> backups() {
        Path dir = backupDir();
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            List<String> names = new ArrayList<>(files
                    .map(p -> p.getFileName().toString())
                    .filter(KeybindRules::isBackupFileName)
                    .toList());
            names.sort(Collections.reverseOrder());
            return names;
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Puts back the bindings recorded in a backup. A mapping the backup does not mention (a mod
     * added since) is left as it is; an entry for a mapping that no longer exists is skipped.
     *
     * @param fileName a name from {@link #backups()}, or null for the newest
     * @return how many mappings were set, or -1 when there is no such backup
     */
    public static int restore(Minecraft mc, String fileName) throws IOException {
        String name = fileName;
        if (name == null) {
            List<String> all = backups();
            if (all.isEmpty()) return -1;
            name = all.get(0);
        }
        // A plain file name of our own shape only: this is typed into a command.
        if (!KeybindRules.isBackupFileName(name)) return -1;
        Path file = backupDir().resolve(name);
        if (!Files.isRegularFile(file)) return -1;

        JsonObject bindings;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("bindings")) return -1;
            bindings = root.getAsJsonObject().getAsJsonObject("bindings");
        } catch (RuntimeException e) {
            throw new IOException("unreadable backup " + name, e);
        }
        int restored = 0;
        for (KeyMapping mapping : mc.options.keyMappings) {
            JsonElement element = bindings.get(mapping.getName());
            if (element == null || !element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            try {
                InputConstants.Key key = InputConstants.getKey(entry.get("key").getAsString());
                KeyModifier modifier = KeyModifier.valueOf(entry.get("modifier").getAsString());
                mapping.setKeyModifierAndCode(modifier, key);
                restored++;
            } catch (RuntimeException e) {
                XenoPixelsMod.LOGGER.warn("Keybind restore: skipped {} ({})", mapping.getName(), e.toString());
            }
        }
        commit(mc);
        return restored;
    }

    // ---- clean ----

    /**
     * Backs up, then unbinds every mapping that is not Minecraft's, DragonMineZ's or XenoPixels',
     * except other mods' held modifiers. If the backup cannot be written nothing is unbound.
     */
    public static CleanResult clean(Minecraft mc) throws IOException {
        Path backup = backup(mc);
        Set<KeyMapping> vanilla = vanilla(mc.options);
        int unbound = 0;
        int kept = 0;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (!mayUnbind(mapping, vanilla)) {
                kept++;
                continue;
            }
            if (mapping.isUnbound()) continue;
            mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
            unbound++;
        }
        commit(mc);
        return new CleanResult(backup, unbound, kept);
    }

    // ---- preset ----

    /**
     * Applies the Xenoverse-style PC layout. Backs up first.
     *
     * <p>Only XenoPixels and DragonMineZ mappings are set. Minecraft's own keys are never moved:
     * where the layout shares a key with one (Q, right mouse, middle mouse) the combat stance
     * decides which meaning the press has.
     *
     * @return the backup written before anything changed
     */
    public static Path applyXv2Preset(Minecraft mc) throws IOException {
        Path backup = backup(mc);
        set(V2Keys.LIGHT, KeyModifier.NONE, mouse(GLFW.GLFW_MOUSE_BUTTON_LEFT));
        set(V2Keys.HEAVY, KeyModifier.NONE, mouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        set(V2Keys.GUARD, KeyModifier.NONE, key(GLFW.GLFW_KEY_R));
        // B, not V: V is DragonMineZ's stats menu, which opens on the key press itself.
        set(V2Keys.VANISH, KeyModifier.NONE, key(GLFW.GLFW_KEY_B));
        set(V2Keys.KI_BLAST, KeyModifier.NONE, key(GLFW.GLFW_KEY_Q));
        set(V2Keys.LOCK, KeyModifier.NONE, mouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE));
        set(Bt3CombatClient.DRAGON_DASH, KeyModifier.NONE, key(GLFW.GLFW_KEY_N));
        set(Bt3CombatClient.ZANZOKEN, KeyModifier.ALT, key(GLFW.GLFW_KEY_SPACE));

        // Super palette on Ctrl, ultimate palette on Alt: DragonMineZ's own technique slots.
        KeyMapping[] slots = KeyBinds.TECHNIQUE_SLOTS;
        if (slots != null && slots.length >= 6) {
            set(slots[0], KeyModifier.CONTROL, mouse(GLFW.GLFW_MOUSE_BUTTON_LEFT));
            set(slots[1], KeyModifier.CONTROL, mouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT));
            set(slots[2], KeyModifier.CONTROL, key(GLFW.GLFW_KEY_Q));
            set(slots[3], KeyModifier.CONTROL, key(GLFW.GLFW_KEY_SPACE));
            set(slots[4], KeyModifier.ALT, mouse(GLFW.GLFW_MOUSE_BUTTON_LEFT));
            set(slots[5], KeyModifier.ALT, mouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        }
        set(KeyBinds.INSTANT_TRANSFORM, KeyModifier.ALT, key(GLFW.GLFW_KEY_Q));
        commit(mc);
        return backup;
    }

    private static void set(KeyMapping mapping, KeyModifier modifier, InputConstants.Key key) {
        if (mapping != null) mapping.setKeyModifierAndCode(modifier, key);
    }

    private static InputConstants.Key key(int glfw) {
        return InputConstants.Type.KEYSYM.getOrCreate(glfw);
    }

    private static InputConstants.Key mouse(int glfw) {
        return InputConstants.Type.MOUSE.getOrCreate(glfw);
    }

    private static void commit(Minecraft mc) {
        KeyMapping.resetMapping();
        mc.options.save();
    }

    // ---- automatic ----

    /**
     * Runs the cleanup once, the first time v2 is active for this client, when the player has
     * left the automatic cleanup on. Says what it did and how to undo it.
     *
     * @return the result, or null when it did not run
     */
    public static CleanResult autoCleanOnce(Minecraft mc) {
        if (!XenoClientConfig.keybindAutoClean || XenoClientConfig.keybindAutoCleanDone) return null;
        XenoClientConfig.keybindAutoCleanDone = true;
        XenoClientConfig.save();
        if (countForeign(mc) == 0) return null;
        try {
            return clean(mc);
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Keybind auto-clean skipped: backup could not be written", e);
            return null;
        }
    }

    /** Mapping name to "modifier+key" for every bound mapping the cleanup would unbind. */
    public static Map<String, String> foreign(Minecraft mc) {
        Set<KeyMapping> vanilla = vanilla(mc.options);
        Map<String, String> out = new java.util.TreeMap<>();
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping.isUnbound()) continue;
            if (!mayUnbind(mapping, vanilla)) continue;
            out.put(mapping.getName(), mapping.getTranslatedKeyMessage().getString());
        }
        return out;
    }

    // ---- repair ----

    /** Bumped when a cleanup rule is tightened and profiles cleaned under the old rule need mending. */
    private static final int REPAIR = 1;

    /**
     * Re-binds what an earlier cleanup must not have unbound: other mods' held modifiers. Runs
     * once per profile, and only on a profile a cleanup has actually touched (one with a backup),
     * so a modifier the player unbound themselves is never put back.
     *
     * <p>Each one returns to the key the newest backup recorded for it, which is what it was on
     * before the cleanup, or to its default when no backup mentions it.
     *
     * @return how many bindings were put back
     */
    public static int repairOnce(Minecraft mc) {
        if (XenoClientConfig.keybindRepair >= REPAIR) return 0;
        XenoClientConfig.keybindRepair = REPAIR;
        XenoClientConfig.save();
        List<String> backups = backups();
        if (backups.isEmpty()) return 0;
        JsonObject recorded = readBindings(backupDir().resolve(backups.get(0)));

        Set<KeyMapping> vanilla = vanilla(mc.options);
        int repaired = 0;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (!mapping.isUnbound()) continue;
            InputConstants.Key byDefault = mapping.getDefaultKey();
            if (!KeybindRules.heldModifier(byDefault.getType() == InputConstants.Type.KEYSYM, byDefault.getValue())) {
                continue;
            }
            // Ours, DragonMineZ's and Minecraft's were never unbound by a cleanup.
            if (KeybindRules.keep(mapping.getName(), vanilla.contains(mapping), List.of())) continue;

            InputConstants.Key key = byDefault;
            KeyModifier modifier = KeyModifier.NONE;
            JsonElement element = recorded == null ? null : recorded.get(mapping.getName());
            if (element != null && element.isJsonObject()) {
                try {
                    JsonObject entry = element.getAsJsonObject();
                    InputConstants.Key was = InputConstants.getKey(entry.get("key").getAsString());
                    if (!was.equals(InputConstants.UNKNOWN)) {
                        key = was;
                        modifier = KeyModifier.valueOf(entry.get("modifier").getAsString());
                    }
                } catch (RuntimeException e) {
                    // An entry that does not parse: the default it is.
                }
            }
            mapping.setKeyModifierAndCode(modifier, key);
            repaired++;
            XenoPixelsMod.LOGGER.info("Keybind repair: {} bound to {} again", mapping.getName(), key.getName());
        }
        if (repaired > 0) commit(mc);
        return repaired;
    }

    /**
     * Takes Iris's shader reload off R, under every combat controller, and puts it on Page Up.
     *
     * <p>R is DragonMineZ's dash. Checked on every launch rather than once, so restoring a
     * keybind backup that had it on R does not bring the chunk rebuild back.
     *
     * @return true when the binding was moved
     */
    public static boolean moveShaderReload(Minecraft mc) {
        boolean firstTime = XenoClientConfig.shaderReloadKey < 1;
        if (firstTime) {
            XenoClientConfig.shaderReloadKey = 1;
            XenoClientConfig.save();
        }
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (!KeybindRules.SHADER_RELOAD.equals(mapping.getName())) continue;
            InputConstants.Key key = mapping.getKey();
            if (!KeybindRules.moveShaderReload(mapping.isUnbound(),
                    key.getType() == InputConstants.Type.KEYSYM, key.getValue(), firstTime)) {
                return false;
            }
            mapping.setKeyModifierAndCode(KeyModifier.NONE,
                    InputConstants.Type.KEYSYM.getOrCreate(KeybindRules.KEY_PAGE_UP));
            commit(mc);
            XenoPixelsMod.LOGGER.info("Shader reload ({}) moved to Page Up", mapping.getName());
            return true;
        }
        return false;
    }

    private static JsonObject readBindings(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            return root.isJsonObject() && root.getAsJsonObject().has("bindings")
                    ? root.getAsJsonObject().getAsJsonObject("bindings") : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Runs {@link #repairOnce} the first tick a player exists, whichever combat controller the
     * server runs: the damage is to the profile, not to a mode.
     */
    @net.neoforged.fml.common.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID,
            value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Repair {
        private static boolean checked;

        private Repair() {}

        @net.neoforged.bus.api.SubscribeEvent
        public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (checked) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            checked = true;
            int repaired = repairOnce(mc);
            if (repaired > 0) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§aXenoPixels§7: put back " + repaired + " modifier key binding(s) of other mods "
                                + "that the keybind cleanup had unbound. They are needed for held-key "
                                + "tooltips and were crashing the creative search."), false);
            }
            if (moveShaderReload(mc)) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§aXenoPixels§7: shader reload is on §fPage Up§7 now, so R no longer rebuilds "
                                + "the chunks. Change it in Controls if you like."), false);
            }
        }
    }
}
