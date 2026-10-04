package net.bullettrain.xenopixelsmod.client.pad2;

import com.dragonminez.client.events.FlySkillEvent;
import com.dragonminez.common.network.C2S.FlightModeC2S;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.stats.character.Status;
import com.mojang.logging.LogUtils;
import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.ControlifyBindApi;
import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.api.bind.RadialIcon;
import dev.isxander.controlify.bindings.BindContext;
import dev.isxander.controlify.bindings.input.EmptyInput;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.input.GamepadInputs;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.DmzClientStats;
import net.bullettrain.xenopixelsmod.client.combat.Bt3DirectBind;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.pad.Bt3ControllerInput;
import net.bullettrain.xenopixelsmod.client.pad.PadChords;
import net.bullettrain.xenopixelsmod.client.pad.PadMode;
import net.bullettrain.xenopixelsmod.client.pad.TechniqueSlotIcon;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers {@link PadLayout} with Controlify, and answers for it afterwards.
 *
 * <p>The rewrite's split: the layout is a table in {@link PadLayout} with no Controlify types in
 * it, and everything Controlify-shaped is here. Previously the two were one six-hundred-line file,
 * which meant the layout could not be read without reading the machinery.
 *
 * <p>Three things the previous integration got right, kept deliberately:
 * <ul>
 *   <li><b>Key emulation, not reimplementation.</b> Every binding presses a {@link KeyMapping} the
 *       keyboard already presses, so no move has two implementations to keep in step.</li>
 *   <li><b>The gate is stored, not just handed to Controlify.</b> {@link #held} must give the same
 *       answer the emulation does, because most of this mod's combat polls the hardware rather
 *       than asking {@code KeyMapping.isDown()}. Reading the raw button there instead would report
 *       a plain melee press while the trigger was held and the button meant Ultimate.</li>
 *   <li><b>Chords are one input carrying several bindings</b>, each gated on a different layer.
 *       Exactly one layer is active, so exactly one can press — the modifier enables its own move
 *       and withholds the base move without either binding knowing the other exists.</li>
 * </ul>
 *
 * <p><b>Never load this class when Controlify is absent.</b> It names Controlify types in its
 * signatures. Everything outside the pad packages goes through {@code XenoPadInput}.
 */
public final class PadBinds {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PadVanish VANISH = new PadVanish(0.68f, 0.32f);

    /** One registered binding, kept with the gate that decides whether it may act. */
    private record Bound(PadLayout.Row row, InputBindingSupplier supplier) {

        /** The raw input, ignoring the gate — used to decide the gates themselves. */
        boolean pressed(ControllerEntity controller) {
            if (controller == null) {
                return false;
            }
            InputBinding binding = supplier.onOrNull(controller);
            return binding != null && binding.digitalNow();
        }

        boolean active(ControllerEntity controller) {
            return pressed(controller) && allows(row, controller);
        }
    }

    private static final Map<KeyMapping, List<Bound>> BY_MAPPING = new IdentityHashMap<>();

    /** Every registered row, in layout order. */
    private static final List<Bound> ALL = new ArrayList<>();

    /** Face bindings belonging to a chord layer, so lock-on can stand down while one is held. */
    private static final List<Bound> CHORD_FACES = new ArrayList<>();

    private static Bound chargeModifier;
    private static Bound lockModifier;
    private static Bound descendBind;
    private static Bound guardBind;

    private static InputBindingSupplier flyToggleAction;
    private static InputBindingSupplier normalModeAction;
    private static InputBindingSupplier bt3ModeAction;
    private static InputBindingSupplier flightModeAction;

    private static int pendingVanishSide;
    private static boolean registered;

    private PadBinds() {
    }

    // ---- registration -----------------------------------------------------------------------

    /** Declares every binding in the table. Called once, from Controlify's own pre-init. */
    public static void register(ControlifyBindApi api) {
        if (registered) {
            return;
        }
        registered = true;

        for (PadLayout.Row row : PadLayout.rows()) {
            registerRow(api, row);
        }

        // The mode switches are not key emulation - they change which layer owns the pad - so they
        // are not rows in the layout table. They are radial actions with no default input.
        normalModeAction = modeAction(api, "normal_mode", new PadTextIcon("MC", 0xFF55FF55),
                EmptyInput.INSTANCE);
        bt3ModeAction = modeAction(api, "bt3_mode", new PadTextIcon("BT3", 0xFFFFAA00),
                EmptyInput.INSTANCE);
        // Radial-only now. L3 became descend-while-flying, which is something a player holds
        // continuously; switching Search/Combat fly is a once-in-a-while choice and belongs on the
        // ring rather than on a button it would fight for.
        flightModeAction = modeAction(api, "flight_mode", new PadTextIcon("FLY", 0xFF55FFFF),
                EmptyInput.INSTANCE);

        // RB toggles flight through DragonMineZ's own entry point rather than by pressing FLY_KEY:
        // DMZ reads that mapping with consumeClick() inside an InputEvent.Key handler, and that
        // event only fires for the real keyboard callback. See tick().
        flyToggleAction = api.registerBinding(builder -> builder
                .id(XenoPixelsMod.MOD_ID, "fly_toggle")
                .name(Component.translatable(com.dragonminez.client.util.KeyBinds.FLY_KEY.getName()))
                .description(Component.translatable("key.xenopixelsmod.pad.desc",
                        Component.translatable(
                                com.dragonminez.client.util.KeyBinds.FLY_KEY.getName())))
                .category(Component.translatable("key.categories.xenopixelsmod"))
                .defaultInput(GamepadInputs.getBind(GamepadInputs.RIGHT_SHOULDER_BUTTON))
                .allowedContexts(BindContext.IN_GAME));

        LOGGER.info("[XenoPixels] Registered {} gamepad bindings with Controlify "
                        + "({} radial candidates). Layout: BT3, rewritten.",
                ALL.size() + 4, ALL.stream().filter(b -> b.row().radialCandidate()).count() + 3);
    }

    private static void registerRow(ControlifyBindApi api, PadLayout.Row row) {
        KeyMapping mapping = row.key() == null ? null : row.key().get();
        if (mapping == null) {
            // A DragonMineZ build without this mapping, or game options that did not exist yet.
            // Said out loud rather than left as a button that does nothing in play.
            LOGGER.warn("[XenoPixels] Gamepad row '{}' has no key mapping to emulate; skipped.",
                    row.id());
            return;
        }

        ResourceLocation iconId = row.radialCandidate() ? registerIcon(api, row) : null;
        InputBindingSupplier supplier = api.registerBinding(builder -> {
            builder.id(XenoPixelsMod.MOD_ID, row.id())
                    .name(Component.translatable(mapping.getName()))
                    .description(Component.translatable("key.xenopixelsmod.pad.desc",
                            Component.translatable(mapping.getName())))
                    .category(Component.translatable("key.categories.xenopixelsmod"))
                    .defaultInput(inputFor(row.input()))
                    .allowedContexts(BindContext.IN_GAME)
                    .addKeyCorrelation(mapping)
                    .keyEmulation(mapping, controller -> allows(row, controller));
            if (iconId != null) {
                builder.radialCandidate(iconId);
            }
            return builder;
        });

        Bound bound = new Bound(row, supplier);
        ALL.add(bound);
        BY_MAPPING.computeIfAbsent(mapping, k -> new ArrayList<>()).add(bound);
        if (row.layer() != null && row.layer() != PadChords.Layer.BASE) {
            CHORD_FACES.add(bound);
        }
        // The three modifiers are read back constantly to decide every other row's layer, so they
        // are remembered by name instead of being looked up by id on every query.
        switch (row.id()) {
            case "ki_charge" -> chargeModifier = bound;
            case "lock_on" -> lockModifier = bound;
            case "descend" -> descendBind = bound;
            case "guard" -> guardBind = bound;
            default -> { }
        }
    }

    private static ResourceLocation registerIcon(ControlifyBindApi api, PadLayout.Row row) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                XenoPixelsMod.MOD_ID, "pad_" + row.id());
        RadialIcon icon = switch (row.icon()) {
            case PadLayout.Icon.Text text -> new PadTextIcon(text.label(), text.color());
            case PadLayout.Icon.TechniqueSlot slot -> new TechniqueSlotIcon(slot.slot());
            case null -> null;
        };
        if (icon == null) {
            return null;
        }
        api.registerRadialIcon(id, icon);
        return id;
    }

    private static InputBindingSupplier modeAction(ControlifyBindApi api, String name,
                                                   RadialIcon icon,
                                                   dev.isxander.controlify.bindings.input.Input input) {
        ResourceLocation iconId = ResourceLocation.fromNamespaceAndPath(
                XenoPixelsMod.MOD_ID, "pad_" + name);
        api.registerRadialIcon(iconId, icon);
        return api.registerBinding(builder -> builder
                .id(XenoPixelsMod.MOD_ID, name)
                .name(Component.translatable("key.xenopixelsmod.pad." + name))
                .description(Component.translatable("key.xenopixelsmod.pad." + name + ".desc"))
                .category(Component.translatable("key.categories.xenopixelsmod"))
                .defaultInput(input)
                .allowedContexts(BindContext.IN_GAME)
                .radialCandidate(iconId));
    }

    /**
     * The layout's symbolic input, as Controlify's own constant.
     *
     * <p>A switch rather than a lookup table, so adding a {@link PadInput} without wiring it here
     * fails to compile instead of silently shipping an unbound action.
     */
    private static dev.isxander.controlify.bindings.input.Input inputFor(PadInput input) {
        ResourceLocation id = switch (input) {
            case WEST -> GamepadInputs.WEST_BUTTON;
            case SOUTH -> GamepadInputs.SOUTH_BUTTON;
            case EAST -> GamepadInputs.EAST_BUTTON;
            case NORTH -> GamepadInputs.NORTH_BUTTON;
            case LEFT_TRIGGER -> GamepadInputs.LEFT_TRIGGER_AXIS;
            case RIGHT_TRIGGER -> GamepadInputs.RIGHT_TRIGGER_AXIS;
            case LEFT_SHOULDER -> GamepadInputs.LEFT_SHOULDER_BUTTON;
            case RIGHT_SHOULDER -> GamepadInputs.RIGHT_SHOULDER_BUTTON;
            case LEFT_STICK_BUTTON -> GamepadInputs.LEFT_STICK_BUTTON;
            case RIGHT_STICK_BUTTON -> GamepadInputs.RIGHT_STICK_BUTTON;
            case BACK -> GamepadInputs.BACK_BUTTON;
            case DPAD_UP -> GamepadInputs.DPAD_UP_BUTTON;
            case DPAD_DOWN -> GamepadInputs.DPAD_DOWN_BUTTON;
            case DPAD_LEFT -> GamepadInputs.DPAD_LEFT_BUTTON;
            case LEFT_STICK_UP -> GamepadInputs.LEFT_STICK_AXIS_UP;
            case LEFT_STICK_DOWN -> GamepadInputs.LEFT_STICK_AXIS_DOWN;
            case LEFT_STICK_LEFT -> GamepadInputs.LEFT_STICK_AXIS_LEFT;
            case LEFT_STICK_RIGHT -> GamepadInputs.LEFT_STICK_AXIS_RIGHT;
            case NONE -> null;
        };
        return id == null ? EmptyInput.INSTANCE : GamepadInputs.getBind(id);
    }

    // ---- gating -----------------------------------------------------------------------------

    /** Whether one row may press right now: its mode, its route, its gate and its chord layer. */
    private static boolean allows(PadLayout.Row row, ControllerEntity controller) {
        if (!bt3Mode()) {
            return false;
        }
        Bt3DirectBind route = row.route();
        if (route != null && !route.enabled()) {
            return false;
        }
        boolean gated = switch (row.gate()) {
            case BT3_MODE -> true;
            case FLYING -> localFlyActive();
            case NO_CHORD_HELD -> !anyChordFaceDown(controller);
        };
        if (!gated) {
            return false;
        }
        return row.layer() == null || PadChords.allows(row.layer(), chargeHeld(controller),
                lockHeld(controller), descendHeld(controller));
    }

    // ---- per-tick ---------------------------------------------------------------------------

    /** Processes the mode actions, the flight toggle and the guard-stick vanish gesture. */
    public static void tick() {
        ControllerEntity controller = current();
        if (controller == null) {
            resetTransientState();
            return;
        }
        if (justPressed(normalModeAction, controller)) {
            setMode(PadMode.NORMAL);
        }
        if (justPressed(bt3ModeAction, controller)) {
            setMode(PadMode.BT3);
        }

        Minecraft mc = Minecraft.getInstance();
        if (!bt3Mode() || mc.player == null || mc.level == null || mc.screen != null) {
            resetTransientState();
            return;
        }
        if (justPressed(flightModeAction, controller)) {
            toggleFlightMode(mc);
        }
        // DMZ reads FLY_KEY with consumeClick() inside an InputEvent.Key handler, and that event
        // is fired only by the real keyboard callback - so no amount of writing to the mapping
        // from here could reach it. toggleFlightFromMenu is DMZ's own entry point for a toggle
        // that did not come from a key press.
        if (!localFlyActive() && justPressed(flyToggleAction, controller)) {
            FlySkillEvent.toggleFlightFromMenu();
        }

        int side = VANISH.sample(flightRoll(),
                guardBind != null && guardBind.active(controller));
        if (side != 0) {
            pendingVanishSide = side;
        }

        // Hold a trigger to raise a ki technique bar. Last, because it reads the same triggers the
        // chord layers do and must see the state this tick already settled on.
        PadKiMenu.tick(mc.player);
    }

    private static void setMode(PadMode mode) {
        if (mode == null || XenoClientConfig.padMode == mode) {
            return;
        }
        XenoClientConfig.padMode = mode;
        XenoClientConfig.save();
        resetTransientState();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(
                    mode == PadMode.BT3
                            ? "message.xenopixelsmod.pad.bt3"
                            : "message.xenopixelsmod.pad.normal"), true);
        }
    }

    private static void toggleFlightMode(Minecraft mc) {
        DmzClientStats.Snapshot stats = DmzClientStats.read(mc.player);
        if (!stats.present || !stats.flyActive) {
            mc.player.displayClientMessage(
                    Component.translatable("message.xenopixelsmod.pad.flight_unavailable"), true);
            return;
        }
        NetworkHandler.sendToServer(new FlightModeC2S());
        boolean combat = stats.flightMode != Status.FLIGHT_COMBAT;
        mc.player.displayClientMessage(Component.translatable(
                combat
                        ? "message.xenopixelsmod.pad.flight_combat"
                        : "message.xenopixelsmod.pad.flight_search"), true);
    }

    private static void resetTransientState() {
        pendingVanishSide = 0;
        VANISH.reset();
        // Abandoned rather than released: leaving BT3 mode, opening a screen or unplugging the
        // controller is not a player choosing to fire what they were charging.
        PadKiMenu.reset();
    }

    // ---- state reads ------------------------------------------------------------------------

    /**
     * Whether the pad is currently pressing {@code mapping}, chord gate included.
     *
     * <p>Most of this mod's combat does not ask {@link KeyMapping#isDown()} — it polls the hardware
     * through GLFW, deliberately. An emulated press moves no physical key, so without this the pad
     * would leave guard, the fist combo, charge, chase, Hakai, beam surge and ki guidance dead
     * while every other binding worked.
     */
    public static boolean held(KeyMapping mapping) {
        if (!bt3Mode()) {
            return false;
        }
        if (XenoClientConfig.padRawPolling) {
            // The raw reads carry no chord gate, so holding LT and pressing X reports a plain melee
            // press as well as the Ultimate it meant. Kept switchable so the two can be compared.
            Boolean raw = rawPoll(mapping);
            if (raw != null) {
                return raw;
            }
        }
        List<Bound> binds = BY_MAPPING.get(mapping);
        if (binds == null) {
            return false;
        }
        ControllerEntity controller = current();
        if (controller == null) {
            return false;
        }
        for (Bound bound : binds) {
            if (bound.active(controller)) {
                return true;
            }
        }
        return false;
    }

    /** The raw hardware answer for the five mappings that have one, or null for the rest. */
    private static Boolean rawPoll(KeyMapping mapping) {
        if (mapping == net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient.CHARGE_FIST) {
            return Bt3ControllerInput.meleePressed();
        }
        if (mapping == net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient.DRAGON_DASH) {
            return Bt3ControllerInput.dashPressed();
        }
        if (mapping == net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient.GUARD) {
            return Bt3ControllerInput.guardPressed();
        }
        if (mapping == com.dragonminez.client.util.KeyBinds.KI_CHARGE) {
            return Bt3ControllerInput.chargeHeld();
        }
        if (mapping == net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient.LOCK_NEXT) {
            return Bt3ControllerInput.lockHeld();
        }
        return null;
    }

    public static int consumeVanishSide() {
        int side = pendingVanishSide;
        pendingVanishSide = 0;
        return side;
    }

    public static boolean suppressesUseItem() {
        return bt3Mode()
                && (Bt3ControllerInput.kiBlastPressed() || Bt3ControllerInput.chargeHeld());
    }

    public static boolean suppressesJump() {
        return bt3Mode() && Bt3ControllerInput.dashPressed();
    }

    public static boolean suppressesSneak() {
        return bt3Mode() && Bt3ControllerInput.guardPressed();
    }

    public static boolean suppressesAttack() {
        return bt3Mode() && Bt3ControllerInput.descendPressed();
    }

    public static boolean suppressesSprint() {
        // DMZ reads keySprint directly to select its faster flight speed, so while flying the
        // sprint binding has to be left alone.
        if (localFlyActive()) {
            return false;
        }
        return bt3Mode()
                && Bt3ControllerInput.isButtonPressed(GamepadInputs.LEFT_STICK_BUTTON);
    }

    /** Whether a built-in Controlify action shares a physical input owned by the BT3 layout. */
    public static boolean conflicts(InputBinding binding) {
        if (binding == null || binding.boundInput() == null) {
            return false;
        }
        ResourceLocation id = binding.id();
        // The radial opener keeps its whole press/hold/release lifecycle even when a player binds
        // it to a button the combat layout otherwise owns - otherwise the menu this rewrite just
        // widened could not be opened at all.
        if (ResourceLocation.fromNamespaceAndPath("controlify", "radial_menu").equals(id)) {
            return false;
        }
        // Movement and camera bindings feed the vanilla Input values DMZ flight reads directly.
        // Suppressing them zeroes analogue stick output and makes controller flight immobile.
        if (isControlifyVanillaBinding(id)) {
            return false;
        }
        for (ResourceLocation input : binding.boundInput().getRelevantInputs()) {
            if (Bt3ControllerInput.conflicts(input)) {
                return true;
            }
        }
        return false;
    }

    private static final java.util.Set<String> CONTROLIFY_VANILLA = java.util.Set.of(
            "walk_forward", "walk_backward", "strafe_left", "strafe_right",
            "look_up", "look_down", "look_left", "look_right",
            "sprint", "sneak", "jump", "attack", "use", "pick_block", "drop", "inventory",
            "swap_hands",
            "hotbar_1", "hotbar_2", "hotbar_3", "hotbar_4", "hotbar_5",
            "hotbar_6", "hotbar_7", "hotbar_8", "hotbar_9");

    private static boolean isControlifyVanillaBinding(ResourceLocation id) {
        return id != null && "controlify".equals(id.getNamespace())
                && CONTROLIFY_VANILLA.contains(id.getPath());
    }

    public static float flightPitch() {
        return Bt3ControllerInput.flightPitch();
    }

    public static float flightRoll() {
        return Bt3ControllerInput.flightRoll();
    }

    public static boolean controllerActive() {
        return current() != null && ControlifyApi.get().currentInputMode().isController();
    }

    private static boolean bt3Mode() {
        return XenoClientConfig.padEnabled && XenoClientConfig.padMode == PadMode.BT3;
    }

    private static boolean localFlyActive() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && DmzClientStats.read(mc.player).flyActive;
    }

    private static boolean chargeHeld(ControllerEntity controller) {
        return chargeModifier != null && chargeModifier.pressed(controller);
    }

    private static boolean lockHeld(ControllerEntity controller) {
        return lockModifier != null && lockModifier.pressed(controller);
    }

    private static boolean descendHeld(ControllerEntity controller) {
        return descendBind != null && descendBind.pressed(controller);
    }

    private static boolean anyChordFaceDown(ControllerEntity controller) {
        for (Bound bound : CHORD_FACES) {
            if (bound.pressed(controller)) {
                return true;
            }
        }
        return false;
    }

    private static boolean justPressed(InputBindingSupplier supplier, ControllerEntity controller) {
        if (supplier == null || controller == null) {
            return false;
        }
        InputBinding binding = supplier.onOrNull(controller);
        return binding != null && binding.justPressed();
    }

    private static ControllerEntity current() {
        return ControlifyApi.get().getCurrentController().orElse(null);
    }
}
