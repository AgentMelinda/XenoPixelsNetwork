package net.bullettrain.xenopixelsmod.client.npc.dialog;

import com.dragonminez.client.gui.dialogue.NpcDialogueScreen;
import net.minecraft.network.chat.Component;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

/** Narrow access to DMZ 2.2's package-private presentation models.
 * Signatures verified with javap against the pinned 2.2 jar on 2026-10-26.
 * This bridge never accesses DMZ quest services or emits DMZ quest packets.
 */
final class Dmz22DialogueAccess {
    /** The DMZ series this bridge was measured against, per its own jar descriptor. */
    private static final String VERIFIED_SERIES = "2.2";
    private static final boolean READY;
    private static final Constructor<NpcDialogueScreen> SCREEN;
    private static final Method SPEECH, ROOT, OPTION, ENABLED, SET_PAGE;
    static {
        boolean ready = false;
        Constructor<NpcDialogueScreen> screen = null;
        Method speech = null, root = null, option = null, enabled = null, setPage = null;
        try {
            ClassLoader loader = NpcDialogueScreen.class.getClassLoader();
            Class<?> page = Class.forName("com.dragonminez.client.gui.dialogue.DialoguePage", false, loader);
            Class<?> optionClass = Class.forName("com.dragonminez.client.gui.dialogue.DialogueOption", false, loader);
            screen = NpcDialogueScreen.class.getDeclaredConstructor(String.class, int.class,
                    boolean.class, boolean.class, List.class);
            screen.setAccessible(true);
            speech = access(page, "speech", String.class, Supplier.class, Supplier.class);
            root = access(page, "asRoot");
            option = access(optionClass, "of", String.class, Component.class, Runnable.class);
            enabled = access(optionClass, "enabled", boolean.class);
            setPage = access(NpcDialogueScreen.class, "setPage", page);
            String series = installedSeries();
            ready = verifiedSeries(series);
            if (!ready) net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.warn(
                    "DragonMineZ dialogue shapes resolved, but version {} is outside the verified {} series; using Xeno screens",
                    series.isEmpty() ? "absent" : series, VERIFIED_SERIES);
        } catch (ReflectiveOperationException | RuntimeException error) {
            // The mods.toml dependency range is open-ended, so a newer DMZ may rename these members.
            // Falling back to Xeno's own screen is a presentation downgrade; throwing here would
            // take down every conversation, including on servers where nobody opted into this.
            net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.warn(
                    "DragonMineZ dialogue presentation contract unavailable; using Xeno screens", error);
        }
        READY = ready;
        SCREEN = screen;
        SPEECH = speech;
        ROOT = root;
        OPTION = option;
        ENABLED = enabled;
        SET_PAGE = setPage;
    }
    private Dmz22DialogueAccess() {}
    /** The installed DMZ version string, or {@code ""} when DragonMineZ is not present. */
    private static String installedSeries() {
        return net.neoforged.fml.ModList.get().getModContainerById("dragonminez")
                .map(container -> container.getModInfo().getVersion().toString()).orElse("");
    }
    /** Whether the installed DMZ is the series these signatures were read from.
     * The probe above only proves the members still exist with these parameter types. A 2.3 that
     * reorders the two booleans of {@code NpcDialogueScreen}'s constructor, or swaps the element type
     * of its quest list, resolves just as happily and then renders a conversation nobody asked for.
     * Refusing the whole 2.2 series check is deliberate: the dependency range is {@code [2.2,)}, so a
     * newer DMZ loads whether or not this bridge is willing to speak to it.
     */
    private static boolean verifiedSeries(String version) {
        return version.equals(VERIFIED_SERIES) || version.startsWith(VERIFIED_SERIES + ".");
    }
    static boolean ready() {
        return READY;
    }
    private static Method access(Class<?> owner, String name, Class<?>... parameters)
            throws ReflectiveOperationException {
        Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }
    static NpcDialogueScreen screen(int entityId) {
        try { return SCREEN.newInstance("xenopixels", entityId, false, false, List.of()); }
        catch (ReflectiveOperationException error) { throw failure(error); }
    }
    static Object option(String id, Component label, Runnable action, boolean enabled) {
        try { return ENABLED.invoke(OPTION.invoke(null, id, label, action), enabled); }
        catch (ReflectiveOperationException error) { throw failure(error); }
    }
    static void page(NpcDialogueScreen screen, String id, Supplier<Component> line, Supplier<List<Object>> options) {
        try { SET_PAGE.invoke(screen, ROOT.invoke(SPEECH.invoke(null, id, line, options))); }
        catch (ReflectiveOperationException error) { throw failure(error); }
    }
    private static IllegalStateException failure(ReflectiveOperationException error) {
        return new IllegalStateException("DragonMineZ 2.2 dialogue presentation contract changed", error);
    }
}
