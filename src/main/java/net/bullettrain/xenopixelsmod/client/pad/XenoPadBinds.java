package net.bullettrain.xenopixelsmod.client.pad;

import com.dragonminez.client.events.FlySkillEvent;
import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.common.network.C2S.FlightModeC2S;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.stats.character.Status;
import com.mojang.logging.LogUtils;
import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.ControlifyBindApi;
import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.api.bind.InputBindingBuilder;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.api.bind.RadialIcon;
import dev.isxander.controlify.bindings.BindContext;
import dev.isxander.controlify.bindings.input.EmptyInput;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.input.GamepadInputs;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.DmzClientStats;
import net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient;
import net.bullettrain.xenopixelsmod.client.combat.Bt3DirectBind;
import net.bullettrain.xenopixelsmod.client.combat.XenoTargetingControls;
import net.bullettrain.xenopixelsmod.client.PartyClientControls;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Every gamepad binding this mod contributes to Controlify, and the state reads that go with them.
 *
 * <p>The layout is Budokai Tenkaichi 3's, written in Xbox names. Most bindings are pure
 * {@linkplain InputBindingBuilder#keyEmulation key emulation}: they press an existing
 * {@link KeyMapping} — ours or DragonMineZ's — so the pad drives exactly the code the keyboard
 * already drives, with no second implementation of any move to keep in step.
 *
 * <p>Chords come from several bindings sharing one face button, each gated on a different
 * {@link PadChords.Layer}. Controlify has no chord concept and {@code defaultInput} takes a single
 * input, but {@code keyEmulation} accepts a predicate, and since exactly one layer is active at a
 * time exactly one of them can press. That is how holding the trigger both enables the charged
 * move and withholds the plain one.
 *
 * <p><b>This class must never be loaded when Controlify is absent</b> — it names Controlify types
 * in field and method signatures. Everything outside {@code client.pad} goes through
 * {@link XenoPadInput}, which is free of them.
 */
public final class XenoPadBinds {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PadVanishGesture VANISH_GESTURE = new PadVanishGesture(0.68f, 0.32f);

    /**
     * One registered binding, kept with the gate that decides whether it may act.
     *
     * <p>The gate has to be stored rather than only handed to Controlify, because
     * {@link #held(KeyMapping)} answers for this mod's own hardware-polling input code and must
     * give the same answer the emulation does. Reading the raw button there instead would report
     * a plain melee press while the trigger was held and the button really meant Ultimate.
     */
    private record PadBind(InputBindingSupplier supplier, Function<ControllerEntity, Boolean> gate) {

        boolean active(ControllerEntity controller) {
            if (controller == null) return false;
            InputBinding binding = supplier.onOrNull(controller);
            return binding != null && binding.digitalNow() && Boolean.TRUE.equals(gate.apply(controller));
        }

        /** The raw input, ignoring the gate — used to decide the gates themselves. */
        boolean pressed(ControllerEntity controller) {
            if (controller == null) return false;
            InputBinding binding = supplier.onOrNull(controller);
            return binding != null && binding.digitalNow();
        }
    }

    /** Bindings that press a key, looked up by the key they press. */
    private static final Map<KeyMapping, List<PadBind>> BY_MAPPING = new IdentityHashMap<>();

    /** Held ki charge — the left trigger, and the first chord modifier. */
    private static PadBind chargeModifier;
    /** Held lock-on — the left bumper, and the second chord modifier. */
    private static PadBind lockModifier;
    private static PadBind guardBind;
    private static PadBind dashBind;
    private static PadBind descendBind;

    private static InputBindingSupplier flyToggleAction;
    private static InputBindingSupplier normalModeAction;
    private static InputBindingSupplier bt3ModeAction;
    private static InputBindingSupplier flightModeAction;

    /** Face bindings belonging to a chord layer, so lock-on can stand down while one is held. */
    private static final List<PadBind> CHORD_FACES = new ArrayList<>();

    private static int pendingVanishSide;

    private static boolean registered;

    private XenoPadBinds() {
    }

    // ---- registration -------------------------------------------------------------------

    /** Declares every binding. Called once, from Controlify's own pre-init. */
    public static void register(ControlifyBindApi api) {
        if (registered) return;
        registered = true;
        Minecraft mc = Minecraft.getInstance();

        // --- Base layer: the four face buttons, unmodified. ---
        layered(api, "melee", Bt3CombatClient.CHARGE_FIST,
                GamepadInputs.WEST_BUTTON, PadChords.Layer.BASE);
        dashBind = layered(api, "dash", Bt3CombatClient.DRAGON_DASH,
                GamepadInputs.SOUTH_BUTTON, PadChords.Layer.BASE);
        guardBind = layered(api, "guard", Bt3CombatClient.GUARD,
                GamepadInputs.EAST_BUTTON, PadChords.Layer.BASE);
        // Beam surge shares the melee button here for the same reason it shares the left mouse
        // button on the keyboard: it only does anything while one of your own waves is firing, so
        // it costs the melee press nothing to sit on top of it.
        layered(api, "beam_surge", Bt3CombatClient.BEAM_SURGE,
                GamepadInputs.WEST_BUTTON, PadChords.Layer.BASE);
        // DragonMineZ's ki blast is not a key binding at all: it is the chord "Second Function +
        // use item", read at ClientStatsEventsMixin. One pad button therefore has to hold both
        // keys down, which is two bindings sharing the north face button.
        layered(api, "ki_blast", KeyBinds.SECOND_FUNCTION_KEY,
                GamepadInputs.NORTH_BUTTON, PadChords.Layer.BASE);
        if (mc.options != null) {
            layered(api, "ki_blast_use", mc.options.keyUse,
                    GamepadInputs.NORTH_BUTTON, PadChords.Layer.BASE);
        } else {
            // Never expected, but it would silently cost the pad its ki blast, so say so loudly
            // rather than leave a dead button to be discovered in play.
            LOGGER.warn("[XenoPixels] Controlify pre-init ran before game options existed; "
                    + "the gamepad ki blast binding was skipped.");
        }

        // --- Charge layer: hold the left trigger. ---
        layered(api, "z_burst", Bt3CombatClient.Z_BURST,
                GamepadInputs.SOUTH_BUTTON, PadChords.Layer.CHARGE, Bt3DirectBind.Z_BURST);
        layered(api, "ultimate", Bt3CombatClient.ULTIMATE,
                GamepadInputs.WEST_BUTTON, PadChords.Layer.CHARGE, Bt3DirectBind.ULTIMATE);
        layered(api, "sparking", Bt3CombatClient.SPARKING,
                GamepadInputs.EAST_BUTTON, PadChords.Layer.CHARGE, Bt3DirectBind.SPARKING);
        layered(api, "charged_kick", Bt3CombatClient.CHARGE_KICK,
                GamepadInputs.NORTH_BUTTON, PadChords.Layer.CHARGE);
        // --- Descend layer: hold the right trigger. ---
        // RT+Y also triggers charged kick so the player can kick while descending. It goes through
        // the same layer machinery as every other chord so the base ki blast stands down for it;
        // gating it on the raw trigger instead let both fire from one press.
        layered(api, "charged_kick_rt", Bt3CombatClient.CHARGE_KICK,
                GamepadInputs.NORTH_BUTTON, PadChords.Layer.DESCEND);

        // --- Lock layer: hold the left bumper. ---
        // Zanzoken and Multi-Form ship unbound on the keyboard because the key space is full. The
        // pad's is not, so here they get a real default.
        layered(api, "zanzoken", Bt3CombatClient.ZANZOKEN,
                GamepadInputs.WEST_BUTTON, PadChords.Layer.LOCK, Bt3DirectBind.ZANZOKEN);
        layered(api, "multiform", Bt3CombatClient.MULTIFORM,
                GamepadInputs.NORTH_BUTTON, PadChords.Layer.LOCK, Bt3DirectBind.MULTIFORM);
        layered(api, "hakai", Bt3CombatClient.HAKAI,
                GamepadInputs.EAST_BUTTON, PadChords.Layer.LOCK, Bt3DirectBind.HAKAI);

        // --- The modifiers themselves, which are also actions in their own right. ---
        chargeModifier = bind(api, "ki_charge", KeyBinds.KI_CHARGE,
                GamepadInputs.LEFT_TRIGGER_AXIS, controller -> bt3Mode());
        // Lock-on stands down while a chord face button is held, so reaching for a chord does not
        // also cycle the target out from under the move you were aiming.
        lockModifier = bind(api, "lock_on", Bt3CombatClient.LOCK_NEXT,
                GamepadInputs.LEFT_SHOULDER_BUTTON,
                controller -> bt3Mode() && !anyChordFaceDown(controller));

        // --- Unlayered bindings. ---
        // RB toggles flight via edge-triggered pulse (DMZ uses consumeClick).
        // Ascend while flying is handled by emulating jump on the same button.
        flyToggleAction = api.registerBinding(builder -> builder
                .id(XenoPixelsMod.MOD_ID, "fly_toggle")
                .name(Component.translatable(KeyBinds.FLY_KEY.getName()))
                .description(Component.translatable("key.xenopixelsmod.pad.desc",
                        Component.translatable(KeyBinds.FLY_KEY.getName())))
                .category(Component.translatable("key.categories.xenopixelsmod"))
                .defaultInput(GamepadInputs.getBind(GamepadInputs.RIGHT_SHOULDER_BUTTON))
                .allowedContexts(BindContext.IN_GAME));
        if (mc.options != null) {
            bind(api, "ascend", mc.options.keyJump,
                    GamepadInputs.RIGHT_SHOULDER_BUTTON,
                    controller -> bt3Mode() && localFlyActive());
        } else {
            LOGGER.warn("[XenoPixels] Controlify pre-init ran before game options existed; "
                    + "the gamepad ascend binding was skipped.");
        }
        descendBind = bind(api, "descend", KeyBinds.DESCEND, GamepadInputs.RIGHT_TRIGGER_AXIS,
                controller -> bt3Mode());

        // --- Flight direction. ---
        // DragonMineZ steers flight from Options.keyUp/keyDown/keyLeft/keyRight.isDown()
        // (FlySkillEvent.handleFlightMovement), but Controlify does not press those: its
        // ControllerPlayerMovement replaces LocalPlayer.input outright and writes analogue
        // forwardImpulse/leftImpulse instead. The stick therefore walks you on the ground and does
        // nothing at all in Search or Combat Fly, while ascend and descend work because those are
        // key-emulated the way everything here is.
        //
        // Emulating the four movement keys off the left stick gives DMZ exactly what WASD gives it.
        // The gate is flight-only, so ground movement stays Controlify's analogue path untouched.
        if (mc.options != null) {
            bind(api, "fly_forward", mc.options.keyUp, GamepadInputs.LEFT_STICK_AXIS_UP,
                    controller -> flightKeysEmulated());
            bind(api, "fly_back", mc.options.keyDown, GamepadInputs.LEFT_STICK_AXIS_DOWN,
                    controller -> flightKeysEmulated());
            bind(api, "fly_left", mc.options.keyLeft, GamepadInputs.LEFT_STICK_AXIS_LEFT,
                    controller -> flightKeysEmulated());
            bind(api, "fly_right", mc.options.keyRight, GamepadInputs.LEFT_STICK_AXIS_RIGHT,
                    controller -> flightKeysEmulated());
        } else {
            LOGGER.warn("[XenoPixels] Controlify pre-init ran before game options existed; "
                    + "gamepad flight steering was skipped.");
        }
        bind(api, "transform", KeyBinds.ACTION_KEY, GamepadInputs.RIGHT_STICK_BUTTON,
                controller -> bt3Mode());
        bind(api, "stats_menu", KeyBinds.STATS_MENU, GamepadInputs.BACK_BUTTON,
                controller -> bt3Mode());
        bind(api, "chase", Bt3CombatClient.CHASE, GamepadInputs.DPAD_UP_BUTTON,
                controller -> bt3Mode());
        bind(api, "backstep", Bt3CombatClient.BACKSTEP, GamepadInputs.DPAD_DOWN_BUTTON,
                controller -> bt3Mode());
        bind(api, "sonic_left", Bt3CombatClient.SONIC_SWAY_LEFT,
                GamepadInputs.DPAD_LEFT_BUTTON,
                controller -> bt3Mode() && Bt3DirectBind.SONIC_SWAY_LEFT.enabled());
        layered(api, "sonic_right", Bt3CombatClient.SONIC_SWAY_RIGHT,
                GamepadInputs.SOUTH_BUTTON, PadChords.Layer.LOCK, Bt3DirectBind.SONIC_SWAY_RIGHT);

        ResourceLocation normalIcon = registerIcon(api, "normal_mode", "MC", 0xFF55FF55);
        ResourceLocation bt3Icon = registerIcon(api, "bt3_mode", "BT3", 0xFFFFAA00);
        ResourceLocation flightIcon = registerIcon(api, "flight_mode", "FLY", 0xFF55FFFF);
        normalModeAction = radialAction(api, "normal_mode", normalIcon, EmptyInput.INSTANCE);
        bt3ModeAction = radialAction(api, "bt3_mode", bt3Icon, EmptyInput.INSTANCE);
        flightModeAction = radialAction(api, "flight_mode", flightIcon,
                GamepadInputs.getBind(GamepadInputs.LEFT_STICK_BUTTON));

        // --- Technique slots: unbound, and offered to Controlify's radial menu. ---
        // DragonMineZ reaches these with Alt+1..4 and Ctrl+1..4, chords no gamepad can produce.
        // A radial is also the shape that survives the slot count growing later.
        KeyMapping[] slots = KeyBinds.TECHNIQUE_SLOTS;
        for (int i = 0; i < slots.length; i++) {
            techniqueSlot(api, i, slots[i]);
        }

        // --- Everything else Xeno owns that no button could reach. ---
        // The BT3 layout spends every face button, both bumpers, both triggers, both sticks and
        // the d-pad, so these would otherwise be keyboard-only on a pad. Colour-coded by area so
        // the radial reads at a glance: orange combat, cyan targeting, green party.
        int combat = 0xFFFFAA00;
        radialBinding(api, "dash_left", Bt3CombatClient.DASH_LEFT, new PadModeIcon("<", combat));
        radialBinding(api, "dash_right", Bt3CombatClient.DASH_RIGHT, new PadModeIcon(">", combat));
        radialBinding(api, "ki_blast_cancel", Bt3CombatClient.KI_BLAST_CANCEL,
                new PadModeIcon("KC", combat));
        // LB cycles targets forward; there is no button left for the other direction.
        radialBinding(api, "lock_prev", Bt3CombatClient.LOCK_PREV, new PadModeIcon("LP", combat));
        radialBinding(api, "ki_guidance", Bt3CombatClient.KI_GUIDANCE,
                new PadModeIcon("KG", combat));

        int targeting = 0xFF55FFFF;
        radialBinding(api, "target_lock", XenoTargetingControls.LOCK_TOGGLE,
                new PadModeIcon("LK", targeting));
        radialBinding(api, "target_clear", XenoTargetingControls.CLEAR_LOCK,
                new PadModeIcon("CL", targeting));
        radialBinding(api, "target_cycle", XenoTargetingControls.CYCLE_TARGET,
                new PadModeIcon("CY", targeting));
        radialBinding(api, "target_lead", XenoTargetingControls.TOGGLE_LEAD,
                new PadModeIcon("LD", targeting));

        int party = 0xFF55FF55;
        radialBinding(api, "party_screen", PartyClientControls.OPEN_PARTY,
                new PadModeIcon("PT", party));
        radialBinding(api, "party_ping", PartyClientControls.PING_TARGET,
                new PadModeIcon("PG", party));

        // Bindings that emulate a key live in BY_MAPPING; the mode/flight radial actions and the
        // flight toggle do not, so they are counted separately rather than as a fixed number that
        // silently drifts whenever a binding is added or the technique slot count changes.
        int emulating = BY_MAPPING.values().stream().mapToInt(List::size).sum();
        int standalone = 0;
        for (InputBindingSupplier supplier : new InputBindingSupplier[]{
                flyToggleAction, normalModeAction, bt3ModeAction, flightModeAction}) {
            if (supplier != null) standalone++;
        }
        LOGGER.info("[XenoPixels] Registered {} gamepad bindings with Controlify.",
                emulating + standalone);
    }

    /**
     * A binding that presses {@code mapping} only while {@code layer} is the active one, and only
     * while {@code route}'s direct input is switched on.
     *
     * <p>A move that has moved to the radial menu or a DragonMineZ slot keeps its chord here; the
     * switch decides whether the chord still acts, so turning the route back on restores it
     * without the binding having to be re-registered.
     */
    private static PadBind layered(ControlifyBindApi api, String name, KeyMapping mapping,
                                   ResourceLocation input, PadChords.Layer layer,
                                   Bt3DirectBind route) {
        PadBind padBind = bind(api, name, mapping, input,
                controller -> bt3Mode() && route.enabled()
                        && PadChords.allows(layer, chargeHeld(controller), lockHeld(controller),
                                descendHeld(controller)));
        if (padBind != null && layer != PadChords.Layer.BASE) {
            CHORD_FACES.add(padBind);
        }
        return padBind;
    }

    /** A binding that presses {@code mapping} only while {@code layer} is the active one. */
    private static PadBind layered(ControlifyBindApi api, String name, KeyMapping mapping,
                                   ResourceLocation input, PadChords.Layer layer) {
        PadBind padBind = bind(api, name, mapping, input,
                controller -> bt3Mode()
                        && PadChords.allows(layer, chargeHeld(controller), lockHeld(controller),
                                descendHeld(controller)));
        if (padBind != null && layer != PadChords.Layer.BASE) {
            CHORD_FACES.add(padBind);
        }
        return padBind;
    }

    private static PadBind bind(ControlifyBindApi api, String name, KeyMapping mapping,
                                ResourceLocation input, Function<ControllerEntity, Boolean> gate) {
        if (mapping == null) {
            LOGGER.warn("[XenoPixels] Gamepad binding '{}' has no key mapping to emulate; skipped.",
                    name);
            return null;
        }
        InputBindingSupplier supplier = api.registerBinding(builder -> builder
                .id(XenoPixelsMod.MOD_ID, name)
                .name(Component.translatable(mapping.getName()))
                .description(Component.translatable("key.xenopixelsmod.pad.desc",
                        Component.translatable(mapping.getName())))
                .category(Component.translatable("key.categories.xenopixelsmod"))
                .defaultInput(GamepadInputs.getBind(input))
                .allowedContexts(BindContext.IN_GAME)
                .addKeyCorrelation(mapping)
                .keyEmulation(mapping, gate));
        PadBind padBind = new PadBind(supplier, gate);
        BY_MAPPING.computeIfAbsent(mapping, k -> new ArrayList<>()).add(padBind);
        return padBind;
    }

    private static ResourceLocation registerIcon(ControlifyBindApi api, String name,
                                                  String label, int color) {
        ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(
                XenoPixelsMod.MOD_ID, "pad_" + name);
        api.registerRadialIcon(icon, new PadModeIcon(label, color));
        return icon;
    }

    private static InputBindingSupplier radialAction(ControlifyBindApi api, String name,
                                                      ResourceLocation icon,
                                                      dev.isxander.controlify.bindings.input.Input input) {
        return api.registerBinding(builder -> builder
                .id(XenoPixelsMod.MOD_ID, name)
                .name(Component.translatable("key.xenopixelsmod.pad." + name))
                .description(Component.translatable("key.xenopixelsmod.pad." + name + ".desc"))
                .category(Component.translatable("key.categories.xenopixelsmod"))
                .defaultInput(input)
                .allowedContexts(BindContext.IN_GAME)
                .radialCandidate(icon));
    }

    private static void techniqueSlot(ControlifyBindApi api, int index, KeyMapping mapping) {
        radialBinding(api, "technique_slot_" + (index + 1), mapping,
                new TechniqueSlotIcon(index + 1));
    }

    /**
     * A binding that ships unbound and offers itself to Controlify's radial menu.
     *
     * <p>The pad has roughly sixteen inputs and this mod has forty bindings, so most actions can
     * never own a button. A radial candidate costs no button at all, still shows up in Controlify's
     * config for anyone who wants to bind it to one, and survives the action list growing.
     *
     * <p>Registered like every other binding otherwise: it emulates the same {@link KeyMapping} the
     * keyboard uses, so there is no second implementation of the action, and it lands in
     * {@link #BY_MAPPING} so {@link #held} answers for it too.
     */
    private static void radialBinding(ControlifyBindApi api, String name, KeyMapping mapping,
                                      RadialIcon icon) {
        if (mapping == null) return;
        ResourceLocation iconId = ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, name);
        api.registerRadialIcon(iconId, icon);
        InputBindingSupplier supplier = api.registerBinding(builder -> builder
                .id(XenoPixelsMod.MOD_ID, name)
                .name(Component.translatable(mapping.getName()))
                .description(Component.translatable("key.xenopixelsmod.pad.desc",
                        Component.translatable(mapping.getName())))
                .category(Component.translatable("key.categories.xenopixelsmod"))
                .defaultInput(EmptyInput.INSTANCE)
                .allowedContexts(BindContext.IN_GAME)
                .radialCandidate(iconId)
                .addKeyCorrelation(mapping)
                .keyEmulation(mapping, controller -> bt3Mode()));
        BY_MAPPING.computeIfAbsent(mapping, k -> new ArrayList<>())
                .add(new PadBind(supplier, controller -> bt3Mode()));
    }

    /** Processes radial actions, the flight-mode toggle and the guard-stick vanish gesture. */
    public static void tick() {
        ControllerEntity controller = current();
        if (controller == null) {
            resetTransientState();
            return;
        }
        if (justPressed(normalModeAction, controller)) setMode(PadMode.NORMAL);
        if (justPressed(bt3ModeAction, controller)) setMode(PadMode.BT3);

        Minecraft mc = Minecraft.getInstance();
        if (!bt3Mode() || mc.player == null || mc.level == null || mc.screen != null) {
            resetTransientState();
            return;
        }
        if (justPressed(flightModeAction, controller)) toggleFlightMode(mc);

        // Flight toggle, edge-triggered on the fly_toggle binding so a player who rebinds it in
        // Controlify's UI is obeyed.
        //
        // This calls DragonMineZ directly rather than pressing FLY_KEY. DMZ reads that mapping in
        // FlySkillEvent.onKeyPress(InputEvent.Key) through consumeClick(), and InputEvent.Key is
        // only fired by the real keyboard callback, so no amount of writing to the mapping from
        // here can reach it. toggleFlightFromMenu is DMZ's own entry point for a toggle that did
        // not come from a key press, and it resolves the local player itself.
        //
        // Nothing writes to FLY_KEY any more either: this package's contract is that the pad adds
        // an input source and never suppresses the keyboard, and the old per-tick setDown(false)
        // broke that.
        if (!localFlyActive() && justPressed(flyToggleAction, controller)) {
            FlySkillEvent.toggleFlightFromMenu();
        }

        // Vanish gesture uses left stick roll and guard state
        sampleVanishGesture(controller);
    }

    private static void setMode(PadMode mode) {
        if (mode == null || XenoClientConfig.padMode == mode) return;
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

    private static void sampleVanishGesture(ControllerEntity controller) {
        int side = VANISH_GESTURE.sample(flightRoll(),
                guardBind != null && guardBind.active(controller));
        if (side != 0) pendingVanishSide = side;
    }

    private static void resetTransientState() {
        pendingVanishSide = 0;
        VANISH_GESTURE.reset();
    }

    // ---- state reads --------------------------------------------------------------------

    /**
     * Whether the pad is currently pressing {@code mapping}, chord gate included.
     *
     * <p>This exists because most of this mod's combat does not ask {@link KeyMapping#isDown()}.
     * It polls the hardware through GLFW instead, deliberately — see the note above
     * {@code Bt3CombatClient.heldNow}. An emulated press moves no physical key, so without this
     * the pad would leave guard, the fist combo, charge, chase, Hakai, beam surge and ki guidance
     * dead while every other binding worked, which is a maddening thing to debug.
     */
    public static boolean held(KeyMapping mapping) {
        if (!bt3Mode()) return false;
        if (XenoClientConfig.padRawPolling) {
            // Reads the physical button and nothing else: no chord gate, no remapping. Holding the
            // left trigger and pressing X therefore reports a plain melee press at the same time
            // as the Ultimate it really meant, which is the failure the gated path below exists to
            // prevent. Kept switchable so the two can be compared in play.
            if (mapping == Bt3CombatClient.CHARGE_FIST) return Bt3ControllerInput.meleePressed();
            if (mapping == Bt3CombatClient.DRAGON_DASH) return Bt3ControllerInput.dashPressed();
            if (mapping == Bt3CombatClient.GUARD) return Bt3ControllerInput.guardPressed();
            if (mapping == KeyBinds.KI_CHARGE) return Bt3ControllerInput.chargeHeld();
            if (mapping == Bt3CombatClient.LOCK_NEXT) return Bt3ControllerInput.lockHeld();
        }
        List<PadBind> binds = BY_MAPPING.get(mapping);
        if (binds == null) return false;
        ControllerEntity controller = current();
        if (controller == null) return false;
        for (PadBind padBind : binds) {
            if (padBind.active(controller)) return true;
        }
        return false;
    }

    public static int consumeVanishSide() {
        // Vanish side is sampled via gesture; we keep the existing mechanism.
        int side = pendingVanishSide;
        pendingVanishSide = 0;
        return side;
    }

    public static boolean suppressesUseItem() {
        if (!bt3Mode()) return false;
        return Bt3ControllerInput.kiBlastPressed() || Bt3ControllerInput.chargeHeld();
    }

    public static boolean suppressesJump() {
        return bt3Mode() && Bt3ControllerInput.dashPressed();
    }

    public static boolean suppressesSneak() {
        return bt3Mode() && Bt3ControllerInput.guardPressed();
    }

    /** Whether a built-in Controlify action shares a physical input owned by the BT3 layout. */
    public static boolean conflicts(InputBinding binding) {
        if (binding == null || binding.boundInput() == null) return false;
        ResourceLocation id = binding.id();
        // The radial opener must retain its complete press/hold/release lifecycle even when a
        // player binds it to a button the combat layout otherwise owns.
        if (ResourceLocation.fromNamespaceAndPath("controlify", "radial_menu").equals(id)) {
            return false;
        }
        // Movement and camera bindings feed vanilla Input values that DMZ flight reads directly.
        // Suppressing them zeroes analogue stick output, making controller flight immobile.
        if (isControlifyVanillaBinding(id)) {
            return false;
        }
        // Use Bt3ControllerInput to determine conflicts instead of BT3_INPUTS set
        for (ResourceLocation input : binding.boundInput().getRelevantInputs()) {
            if (Bt3ControllerInput.conflicts(input)) return true;
        }
        return false;
    }

    private static boolean isControlifyVanillaBinding(ResourceLocation id) {
        if (id == null || !"controlify".equals(id.getNamespace())) return false;
        String path = id.getPath();
        return "walk_forward".equals(path) || "walk_backward".equals(path)
                || "strafe_left".equals(path) || "strafe_right".equals(path)
                || "look_up".equals(path) || "look_down".equals(path)
                || "look_left".equals(path) || "look_right".equals(path)
                || "sprint".equals(path) || "sneak".equals(path)
                || "jump".equals(path) || "attack".equals(path)
                || "use".equals(path) || "pick_block".equals(path)
                || "drop".equals(path) || "inventory".equals(path)
                || "swap_hands".equals(path) || "hotbar_1".equals(path)
                || "hotbar_2".equals(path) || "hotbar_3".equals(path)
                || "hotbar_4".equals(path) || "hotbar_5".equals(path)
                || "hotbar_6".equals(path) || "hotbar_7".equals(path)
                || "hotbar_8".equals(path) || "hotbar_9".equals(path);
    }

    public static boolean suppressesAttack() {
        return bt3Mode() && Bt3ControllerInput.descendPressed();
    }

    public static boolean suppressesSprint() {
        // DMZ reads keySprint directly to select its faster flight speed.
        if (localFlyActive()) return false;
        return bt3Mode() && Bt3ControllerInput.isButtonPressed(GamepadInputs.LEFT_STICK_BUTTON);
    }

    /** Left stick as pitch, positive nose-up. */
    public static float flightPitch() {
        return Bt3ControllerInput.flightPitch();
    }

    /** Left stick as roll, positive to the right. */
    public static float flightRoll() {
        return Bt3ControllerInput.flightRoll();
    }

    /** True when Controlify reports a controller as the active input device. */
    public static boolean controllerActive() {
        return current() != null && ControlifyApi.get().currentInputMode().isController();
    }

    private static boolean bt3Mode() {
        return XenoClientConfig.padEnabled && XenoClientConfig.padMode == PadMode.BT3;
    }

    /**
     * Whether the stick should be pressing the vanilla movement keys for DragonMineZ flight.
     *
     * <p>Flight only, so Controlify's own analogue ground movement is left alone. This stays on
     * even under {@code padAnalogueFlight}: DMZ builds its flight direction from these keys as
     * {@code isDown() ? 1 : 0}, so the keys are what supply the direction either way, and the
     * analogue option only scales the speed that comes out.
     */
    private static boolean flightKeysEmulated() {
        return bt3Mode() && localFlyActive();
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
        for (PadBind padBind : CHORD_FACES) {
            if (padBind.pressed(controller)) return true;
        }
        return false;
    }

    private static boolean justPressed(InputBindingSupplier supplier, ControllerEntity controller) {
        if (supplier == null || controller == null) return false;
        InputBinding binding = supplier.onOrNull(controller);
        return binding != null && binding.justPressed();
    }

    private static ControllerEntity current() {
        return ControlifyApi.get().getCurrentController().orElse(null);
    }
}
