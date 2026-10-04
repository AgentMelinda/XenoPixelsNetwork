package net.bullettrain.xenopixelsmod.client.pad2;

import com.mojang.logging.LogUtils;
import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.api.bind.RadialIcon;
import dev.isxander.controlify.bindings.RadialIcons;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.input.InputComponent;
import dev.isxander.controlify.gui.screen.RadialItems;
import dev.isxander.controlify.gui.screen.RadialMenuScreen;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds the widened in-game radial.
 *
 * <p>Controlify's {@code RadialItems.createBindings} returns {@code new RadialItem[8]}. Its
 * <em>screen</em>, however, is already generic: {@code RadialMenuScreen.init} lays the ring out at
 * 2π/{@code arraylength} with a radius of {@code Math.max(…, 43f)}. So a longer array needs no
 * screen patch at all — only a longer array.
 *
 * <p><b>This is used for the in-game radial only.</b> {@code createBindings} has a second caller,
 * {@code ControllerConfigScreenFactory}, which passes a {@code RadialItems$BindingEditMode} whose
 * {@code setRadialItem} does {@code radialActions.set(i, ((RadialItemRecord) item).id())} — it
 * casts to a package-private type and indexes the eight-entry list. Handing that path a longer
 * array of our own item type would be a {@code ClassCastException} and an
 * {@code IndexOutOfBoundsException}. The in-game path passes a null edit mode, so it is not
 * editable and writes nothing back, which is exactly why it is safe to widen and the config screen
 * is not.
 *
 * <p>Every symbol used here was read from the pinned Controlify jar with {@code javap}:
 * {@code RadialItem} is a public interface, {@code InputBinding.fakePress()} and
 * {@code radialIcon()} are public, {@code InputComponent.getBinding} is public,
 * {@code RadialIcons.getIcons()} is public, and the
 * {@code settings().input.radialMenu.radialActions} chain is public fields throughout.
 */
public final class PadWideRadial {

    private static final Logger LOGGER = LogUtils.getLogger();

    private PadWideRadial() {
    }

    /**
     * Controlify's array, with this mod's extra entries appended.
     *
     * <p>Returns {@code stock} unchanged on any failure. This runs on the input path, a frame
     * before a screen opens: a radial that is eight long is a disappointment, and an exception
     * here would be a crash.
     */
    public static RadialMenuScreen.RadialItem[] widen(RadialMenuScreen.RadialItem[] stock,
                                                      ControllerEntity controller) {
        if (stock == null || controller == null) {
            return stock;
        }
        try {
            return build(stock, controller);
        } catch (RuntimeException | LinkageError failure) {
            // LinkageError as well as RuntimeException: everything above the api package is
            // Controlify internals, so a Controlify update is far more likely to change a
            // signature out from under this than to make it throw.
            LOGGER.warn("[XenoPixels] Could not widen the radial menu; "
                    + "falling back to Controlify's own {} slots.", stock.length, failure);
            return stock;
        }
    }

    private static RadialMenuScreen.RadialItem[] build(RadialMenuScreen.RadialItem[] stock,
                                                       ControllerEntity controller) {
        List<String> stockIds = new ArrayList<>(stock.length);
        for (ResourceLocation id : controller.settings().input.radialMenu.radialActions) {
            stockIds.add(id == null ? "" : id.toString());
        }
        // The settings list and the array Controlify built from it should be the same length. If
        // they are not, the array is the authority - it is what the screen will actually draw.
        while (stockIds.size() < stock.length) {
            stockIds.add("");
        }

        List<String> merged = PadRadialSlots.merge(stockIds.subList(0, stock.length), extras());
        if (merged.size() <= stock.length) {
            return stock;
        }

        InputComponent input = controller.input().orElse(null);
        RadialMenuScreen.RadialItem[] widened = new RadialMenuScreen.RadialItem[merged.size()];
        // Controlify's own entries are carried over as the very objects it built, not rebuilt from
        // their ids. Anything it does to them - its empty-slot marker, an item icon, a game-mode
        // entry - survives untouched, and a slot a player left deliberately empty stays empty
        // rather than being closed up and shifting every action after it onto a new angle.
        System.arraycopy(stock, 0, widened, 0, stock.length);
        for (int i = stock.length; i < merged.size(); i++) {
            widened[i] = itemFor(merged.get(i), input);
        }
        return widened;
    }

    /** The configured extras, or the defaults when nothing has been configured. */
    static List<String> extras() {
        List<String> configured = XenoClientConfig.padRadialExtras;
        return configured == null || configured.isEmpty()
                ? PadRadialSlots.DEFAULT_EXTRAS : configured;
    }

    /**
     * One radial entry for a binding id.
     *
     * <p>Does what Controlify's own private {@code getItemForBinding} does: find the binding, take
     * its name, resolve its registered radial icon, and fire it with {@code fakePress()}. An id
     * that does not resolve, or a binding that never declared itself a radial candidate, becomes
     * Controlify's own empty item rather than being dropped — dropping one would renumber every
     * slot after it, which a player would experience as their radial rearranging itself.
     */
    private static RadialMenuScreen.RadialItem itemFor(String rawId, InputComponent input) {
        if (input == null) {
            return RadialItems.EMPTY_ACTION;
        }
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            return RadialItems.EMPTY_ACTION;
        }
        InputBinding binding = input.getBinding(id);
        if (binding == null) {
            return RadialItems.EMPTY_ACTION;
        }
        Optional<ResourceLocation> iconId = binding.radialIcon();
        if (iconId.isEmpty()) {
            // Registered, but never offered itself to the radial. Showing it would give a player a
            // slot with no icon that Controlify's own editor does not list.
            return RadialItems.EMPTY_ACTION;
        }
        RadialIcon icon = RadialIcons.getIcons().get(iconId.get());
        return new BindingItem(binding.name(), icon == null ? RadialIcon.EMPTY : icon, binding);
    }

    /**
     * A radial entry backed by one of this mod's bindings.
     *
     * <p>Implements Controlify's public {@code RadialItem} interface. It is deliberately <b>not</b>
     * its {@code RadialItemRecord}: that type is package-private, and it is the type the config
     * screen's edit mode casts to. Using our own makes it structurally impossible for one of these
     * to reach that cast.
     */
    private record BindingItem(Component name, RadialIcon icon, InputBinding binding)
            implements RadialMenuScreen.RadialItem {

        @Override
        public boolean playAction() {
            binding.fakePress();
            return true;
        }
    }
}
