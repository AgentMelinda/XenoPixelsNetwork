package net.bullettrain.xenopixelsmod.client.flight;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import dev.ryanhcode.sable.mixinterface.camera.camera_zoom.CameraZoomExtension;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.combat.DmzAnimHelperClient;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.SeatFlightInputPacket;
import net.bullettrain.xenopixelsmod.network.packet.SeatToggleBindPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Pilot input while seated in a {@link XenoPilotSeatEntity}.
 *
 * <p>This assembles a control frame on the client and sends it; it never moves anything. The
 * server decides what, if any, of it is honored.
 *
 * <p><b>How mouse flight works here.</b> The commanded heading <i>is</i> where the player is
 * looking, and the ship's existing PD attitude stabilizer flies it there — which is what
 * produces the War Thunder feel of the airframe swinging toward the reticle rather than
 * snapping to it. Doing it this way means the cursor is never captured or warped: chat,
 * inventory and every other screen behave exactly as they always do, and there is no mouse
 * state to leak if the player dies or disconnects while flying. A raw-delta mode that moves a
 * reticle independently of the head is the natural next step, and is deliberately not smuggled
 * in behind a mixin on the vanilla mouse handler here.
 *
 * <p><b>Axis mapping.</b> A/D pitch, Q/E roll, W/S yaw, all integrated as rates so the airframe
 * can be rolled through inverted and turned onto any heading. Under mouse-aim the mouse commands
 * pitch and heading together and A/D becomes a pitch trim; W/S does not trim heading there —
 * mouse-aim's yaw stays purely look-driven. Outside mouse-aim, W/S has no rate-integrated
 * attitude command at all; it drives keyboard-mode YAW panels directly by stick position instead,
 * the same as A/D and Q/E do for PITCH/ROLL panels (see {@code AeroFlightCore.deflectFor}).
 *
 * <p>Movement keys are read from vanilla's own mappings rather than duplicated: a second set of
 * WASD bindings would fight the real ones and break flight for anyone who has rebound them.
 *
 * <p><b>Throttle is scroll-driven.</b> Scrolling up/down while seated adjusts throttle directly
 * and cancels the event, so the same wheel does not also cycle the hotbar underneath the pilot —
 * vanilla's own hotbar-scroll handling never sees the input while seated. This steps aside in
 * third-person ({@link #THIRD_PERSON_TOGGLE}), where the same wheel is left for Sable's own
 * scroll-to-zoom on its sub-level view camera instead.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoFlightControls {

    private static final String CATEGORY = "key.categories.xenopixelsmod";

    /** Roll. A pilot's hands sit on Q/E/A/D/W/S together, and this is where the reference this is
     * modelled on puts roll authority — A/D is pitch and W/S is yaw instead of the more familiar
     * flight-game layout, by explicit request. */
    public static final KeyMapping ROLL_LEFT = new KeyMapping("key.xenopixelsmod.flight_roll_left",
            XenoSeatKeyContext.INSTANCE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, CATEGORY);
    public static final KeyMapping ROLL_RIGHT = new KeyMapping("key.xenopixelsmod.flight_roll_right",
            XenoSeatKeyContext.INSTANCE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, CATEGORY);
    /** Steps flaps 0 → 25 → 50 → 75 → 100 → 0. */
    public static final KeyMapping FLAP_STEP = new KeyMapping("key.xenopixelsmod.flight_flaps",
            XenoSeatKeyContext.INSTANCE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping MOUSE_AIM_TOGGLE = new KeyMapping("key.xenopixelsmod.flight_mouse_aim",
            XenoSeatKeyContext.INSTANCE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    /** Level the wings and zero the trim. */
    public static final KeyMapping CENTER_CONTROLS = new KeyMapping("key.xenopixelsmod.flight_center",
            XenoSeatKeyContext.INSTANCE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
    /** Hold to cut thrust without losing the throttle setting. */
    public static final KeyMapping AIR_BRAKE = new KeyMapping("key.xenopixelsmod.flight_air_brake",
            XenoSeatKeyContext.INSTANCE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    /**
     * Held-key throttle, alongside the scroll wheel. The scroll wheel itself has no equivalent
     * in vanilla's Controls screen — it isn't bindable as a {@link KeyMapping} at all — so these
     * exist purely to give throttle a discoverable, rebindable key entry. Unbound by default:
     * scroll already works out of the box, so nothing forces a default key that might collide
     * with something the player already uses.
     */
    public static final KeyMapping THROTTLE_UP = new KeyMapping("key.xenopixelsmod.flight_throttle_up",
            XenoSeatKeyContext.INSTANCE, InputConstants.UNKNOWN, CATEGORY);
    public static final KeyMapping THROTTLE_DOWN = new KeyMapping("key.xenopixelsmod.flight_throttle_down",
            XenoSeatKeyContext.INSTANCE, InputConstants.UNKNOWN, CATEGORY);
    /**
     * Bind or unbind the seat from the nearest flight controller without having to dismount and
     * remount. Sends {@link SeatToggleBindPacket}; the server does the actual work and reports
     * the result, exactly as mounting already does. Unbound by default, like the throttle keys
     * above: every nearby letter in this key context is already spoken for (Q/E/G/K/X/Z here,
     * R/L/Y in {@code XenoTargetingControls}), so this is rebindable rather than forcing a
     * collision onto an existing control.
     */
    public static final KeyMapping TOGGLE_BINDING = new KeyMapping("key.xenopixelsmod.flight_toggle_binding",
            XenoSeatKeyContext.INSTANCE, InputConstants.UNKNOWN, CATEGORY);
    /**
     * Switches the seat between first-person and Sable's own third-person "sub-level view"
     * camera ({@link SableCameraTypes#SUB_LEVEL_VIEW}, the same camera type
     * {@code ContraptionControlCamera.sableSubLevelView()} already treats as a control camera).
     * Scroll becomes Sable's own built-in camera zoom in that view instead of throttle — see
     * {@link #onMouseScroll}. Unbound by default, like the throttle keys above.
     */
    public static final KeyMapping THIRD_PERSON_TOGGLE = new KeyMapping("key.xenopixelsmod.flight_third_person",
            XenoSeatKeyContext.INSTANCE, InputConstants.UNKNOWN, CATEGORY);
    /**
     * Hold to zoom Sable's own sub-level-view camera in/out — the same
     * {@link dev.ryanhcode.sable.mixinterface.camera.camera_zoom.CameraZoomExtension} its own
     * scroll-to-zoom mixin drives (see {@link #updateZoomKeys}), so this is an alternate control
     * for exactly the same zoom, not a second implementation of it. Only does anything while the
     * camera is actually in {@link SableCameraTypes#SUB_LEVEL_VIEW} /
     * {@code SUB_LEVEL_VIEW_UNLOCKED} — i.e. after {@link #THIRD_PERSON_TOGGLE}.
     */
    public static final KeyMapping ZOOM_IN = new KeyMapping("key.xenopixelsmod.flight_zoom_in",
            XenoSeatKeyContext.INSTANCE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_ADD, CATEGORY);
    public static final KeyMapping ZOOM_OUT = new KeyMapping("key.xenopixelsmod.flight_zoom_out",
            XenoSeatKeyContext.INSTANCE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_SUBTRACT, CATEGORY);

    /** Flap stages, in the order the flap key walks them. */
    private static final double[] FLAP_STAGES = {0.0, 0.25, 0.5, 0.75, 1.0};

    /** Keeps the server's input from going stale even when the pilot is holding still. */
    private static final int KEEPALIVE_TICKS = 5;
    /** Floor on send spacing: 10 frames a second is plenty for a control surface. */
    private static final int MIN_SEND_INTERVAL_TICKS = 2;
    /** Off-boresight span the response curve normalizes against; see {@link MouseResponseCurve}. */
    private static final double MOUSE_CURVE_SPAN_DEG = 60.0;
    /** Tracking rate at sensitivity 1.0 and the curve's full multiplier. */
    private static final double MOUSE_BASE_RATE_DEG_PER_SEC = 220.0;
    /** Fallback spring rate for the WASD stick if the config value is somehow unreadable
     * (units per second, 0..1). The live value is {@code XenoClientConfig.flightStickRampPerSec}. */
    private static final double STICK_CATCH_PER_SEC_FALLBACK = 4.0;
    /** Extra pitch W/S trims on top of the mouse-derived aim, at full stick. */
    private static final double STICK_PITCH_TRIM_DEG = 20.0;
    /** Commanded pitch is held short of vertical: straight up is a singularity for the solver. */
    private static final double MAX_PITCH_DEG = 89.0;

    private static double throttle;
    private static double rollDeg;
    private static double yawDeg;
    private static double pitchDeg;
    /** Smoothed stick, -1..1. Springs to 0 when the key is released. A/D = pitch, Q/E = roll,
     * W/S = yaw — see {@link #updateWasdStick}. Also sent to the server directly for keyboard-mode
     * flap control; see {@link net.bullettrain.xenopixelsmod.aero.AeroAction.SetAttitude}. */
    private static double stickPitch;
    private static double stickRoll;
    private static double stickYaw;
    /**
     * Base commanded pitch before the W/S trim: chased toward the look angle under mouse-aim,
     * integrated by W/S otherwise. Kept apart from {@link #pitchDeg} so the trim is an offset on
     * the value that is sent rather than something the chase has to undo again next tick.
     */
    private static double pitchAimDeg;
    private static int flapStage;
    /**
     * Ticks left in which flap frames still count as an instruction. Long enough to survive the
     * send interval and a dropped frame, short enough that it stops fighting auto-flap.
     */
    private static int flapCommandTicks;

    /** True while our own {@link #THIRD_PERSON_TOGGLE} has put the camera into Sable's sub-level
     * view — tracked so leaving the seat only restores first-person if we're the ones who left it
     * in that state, not if the player had deliberately set some other camera type themselves. */
    private static boolean thirdPersonActive;

    private static boolean wasSeated;
    /** True after this client entered a seat; lets dismount cleanup run once and remain idempotent. */
    private static boolean seatVisualStateActive;
    private static int ticksSinceSend = KEEPALIVE_TICKS;
    private static FlightInputChanges.Frame sentFrame;

    private XenoFlightControls() {
    }

    @EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(ROLL_LEFT);
            event.register(ROLL_RIGHT);
            event.register(FLAP_STEP);
            event.register(MOUSE_AIM_TOGGLE);
            event.register(CENTER_CONTROLS);
            event.register(AIR_BRAKE);
            event.register(THROTTLE_UP);
            event.register(THROTTLE_DOWN);
            event.register(TOGGLE_BINDING);
            event.register(THIRD_PERSON_TOGGLE);
            event.register(ZOOM_IN);
            event.register(ZOOM_OUT);
        }
    }

    /** True while the local player is flying, which is what the HUD keys off. */
    public static boolean seated() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() instanceof XenoPilotSeatEntity;
    }

    public static double throttle() {
        return throttle;
    }

    public static double flap() {
        return FLAP_STAGES[flapStage];
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || !(player.getVehicle() instanceof XenoPilotSeatEntity seat)) {
            // Not flying: drain the queues so a press made out of the seat is not banked and
            // replayed the moment the player sits down.
            drain();
            if (wasSeated || seatVisualStateActive) {
                cleanupSeatVisualState(player);
                reset();
            }
            wasSeated = false;
            restoreFirstPersonIfOurs(mc);
            return;
        }
        // Mouse-aim eases the commanded heading toward the look angle rather than snapping to it
        // (see updateAttitude); on the very first frame that would mean crawling from a reset
        // 0,0 instead of responding at once, so that one frame snaps directly.
        boolean justMounted = !wasSeated;
        wasSeated = true;
        if (justMounted) seatVisualStateActive = true;

        // With a screen open the controls are frozen but the stream keeps running. Reading keys
        // here would make typing in chat fly the ship; stopping the stream instead would let the
        // server's staleness rule cut the throttle mid-flight, which is worse — that rule exists
        // to catch a pilot who has gone away, and a pilot reading their inventory has not.
        boolean frozen = mc.screen != null;
        double dt = 0.05;
        if (frozen) {
            drain();
        } else {
            handleToggles(mc);
            while (TOGGLE_BINDING.consumeClick()) {
                ModNetwork.sendToServer(new SeatToggleBindPacket(seat.getId()));
            }
            updateThrottleKeys(dt);
            updateWasdStick(mc, dt);
            updateAttitude(player, mc, dt, justMounted);
            updateZoomKeys(mc, dt);
        }

        boolean airBrake = !frozen && AIR_BRAKE.isDown();
        boolean flapCommanded = flapCommandTicks > 0;
        if (flapCommandTicks > 0) flapCommandTicks--;
        maybeSend(seat.getId(), airBrake, flapCommanded);
    }

    private static void handleToggles(Minecraft mc) {
        while (FLAP_STEP.consumeClick()) {
            flapStage = (flapStage + 1) % FLAP_STAGES.length;
            flapCommandTicks = 10;
            actionBar(mc, String.format("Flaps %.0f%%", FLAP_STAGES[flapStage] * 100.0));
        }
        while (MOUSE_AIM_TOGGLE.consumeClick()) {
            XenoClientConfig.flightMouseAim = !XenoClientConfig.flightMouseAim;
            XenoClientConfig.save();
            actionBar(mc, XenoClientConfig.flightMouseAim
                    ? "Mouse-aim flight" : "Keyboard flight");
        }
        while (CENTER_CONTROLS.consumeClick()) {
            rollDeg = 0.0;
            pitchDeg = 0.0;
            pitchAimDeg = 0.0;
            stickPitch = 0.0;
            stickRoll = 0.0;
            stickYaw = 0.0;
            actionBar(mc, "Controls centered");
        }
        while (THIRD_PERSON_TOGGLE.consumeClick()) {
            if (thirdPersonActive) {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                thirdPersonActive = false;
                actionBar(mc, "First-person view");
            } else {
                mc.options.setCameraType(SableCameraTypes.SUB_LEVEL_VIEW);
                thirdPersonActive = true;
                actionBar(mc, "Third-person view — scroll or +/- to zoom");
            }
        }
    }

    /**
     * Hold {@link #ZOOM_IN}/{@link #ZOOM_OUT} to nudge Sable's own sub-level-view camera zoom, the
     * exact same {@code sable$zoomAmount} its scroll-to-zoom mixin
     * ({@code dev.ryanhcode.sable.mixin.camera.camera_zoom.MouseHandlerMixin}) adjusts — smaller
     * is closer/zoomed-in, matching "scroll up = zoom in". Sable's own per-tick clamp
     * ({@code CameraMixin.sable$clampZoom}) keeps whatever value is set here in range, so no
     * bounds are needed here. A no-op outside that camera view, and outside the seat.
     */
    private static void updateZoomKeys(Minecraft mc, double dt) {
        if (!ZOOM_IN.isDown() && !ZOOM_OUT.isDown()) return;
        CameraType type = mc.options.getCameraType();
        if (type != SableCameraTypes.SUB_LEVEL_VIEW && type != SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) return;
        if (!(mc.gameRenderer.getMainCamera() instanceof CameraZoomExtension zoom)) return;
        float delta = (float) (XenoClientConfig.flightZoomKeyRatePerSec * dt);
        if (ZOOM_IN.isDown()) zoom.sable$setZoomAmount(zoom.sable$getZoomAmount() - delta);
        if (ZOOM_OUT.isDown()) zoom.sable$setZoomAmount(zoom.sable$getZoomAmount() + delta);
    }

    /** Only undoes our own {@link #THIRD_PERSON_TOGGLE}; leaves any other camera type alone. */
    private static void restoreFirstPersonIfOurs(Minecraft mc) {
        if (!thirdPersonActive) return;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        thirdPersonActive = false;
    }

    /**
     * The stick, smoothed: A/D is pitch, Q/E is roll, W/S is yaw — all three rate commands
     * consumed by {@link #updateAttitude}, and all three spring back to centre on release so the
     * rate stops rather than a surface snapping. A/D reads vanilla's own movement mappings so a
     * player who rebound them keeps flying with the keys they actually use; Q/E are this mod's
     * own {@link #ROLL_LEFT}/{@link #ROLL_RIGHT} mappings.
     */
    private static void updateWasdStick(Minecraft mc, double dt) {
        double wantPitch = 0.0;
        double wantRoll = 0.0;
        double wantYaw = 0.0;
        if (mc.options.keyLeft.isDown()) wantPitch -= 1.0;
        if (mc.options.keyRight.isDown()) wantPitch += 1.0;
        if (ROLL_LEFT.isDown()) wantRoll -= 1.0;
        if (ROLL_RIGHT.isDown()) wantRoll += 1.0;
        if (mc.options.keyUp.isDown()) wantYaw += 1.0;
        if (mc.options.keyDown.isDown()) wantYaw -= 1.0;
        double rampPerSec = XenoClientConfig.flightStickRampPerSec > 0.0f
                ? XenoClientConfig.flightStickRampPerSec : STICK_CATCH_PER_SEC_FALLBACK;
        double step = rampPerSec * dt;
        stickPitch = approach(stickPitch, Mth.clamp(wantPitch, -1.0, 1.0), step);
        stickRoll = approach(stickRoll, Mth.clamp(wantRoll, -1.0, 1.0), step);
        stickYaw = approach(stickYaw, Mth.clamp(wantYaw, -1.0, 1.0), step);
    }

    private static double approach(double current, double target, double step) {
        if (Math.abs(target - current) <= step) return target;
        return current + Math.copySign(step, target - current);
    }

    /** Signed per-tick yaw step, wrap-aware so crossing the +/-180 seam takes the short way. */
    private static double yawStep(double rawYawDeg, double dt) {
        return axisStep(Mth.wrapDegrees((float) (rawYawDeg - yawDeg)), dt,
                XenoClientConfig.flightMouseSensitivityX);
    }

    /**
     * Shared deadzone/curve/rate-limit maths for one axis. {@code delta} is target minus current;
     * returns the signed step to take this tick, never overshooting the target.
     *
     * <p>Sensitivity is per-axis. Horizontal and vertical mouse travel are not interchangeable —
     * a pitch axis with only 89 degrees of usable travel each way wants a different rate cap from
     * a heading axis that wraps — which is why this takes the multiplier rather than reading one
     * global one.
     */
    private static double axisStep(double delta, double dt, double sensitivity) {
        double absDelta = Math.abs(delta);
        if (absDelta < XenoClientConfig.flightMouseDeadzoneDeg) return 0.0;
        double t = Math.min(1.0, absDelta / MOUSE_CURVE_SPAN_DEG);
        double curveMul = MouseResponseCurve.byName(XenoClientConfig.flightMouseCurve).apply(t);
        double maxStep = MOUSE_BASE_RATE_DEG_PER_SEC * sensitivity * curveMul * dt;
        return absDelta <= maxStep ? delta : Math.copySign(maxStep, delta);
    }

    /**
     * Scroll wheel doubles as throttle while seated: up increases, down decreases, and the event
     * is canceled so vanilla's own hotbar-switch handling never runs off the same input.
     *
     * <p>In third-person ({@link #thirdPersonActive}) this steps aside entirely and leaves the
     * scroll alone, so Sable's own scroll-to-zoom mixin on its sub-level view camera gets it
     * instead — matching the "contraption cam" zoom feel, without this mod reimplementing zoom.
     */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !(player.getVehicle() instanceof XenoPilotSeatEntity) || mc.screen != null) return;
        if (thirdPersonActive) return;

        double delta = event.getScrollDeltaY();
        if (delta == 0.0) return;
        event.setCanceled(true);

        double step = XenoClientConfig.flightThrottleScrollStep;
        throttle = Mth.clamp(throttle + Math.signum(delta) * step, 0.0, 1.0);
    }

    /**
     * Turn this tick's stick positions into the commanded attitude.
     *
     * <p><b>Keyboard axes.</b> A/D is pitch, Q/E is roll, both integrated as a <i>rate</i> into a
     * stored command rather than a sprung angle, which is what makes a barrel roll reachable at
     * all: a sprung stick can only hold the offset it is deflected to, so roll used to stop at
     * the &#177;89&#176; the commanded value was clamped to. W/S (yaw) has no rate-integrated
     * attitude command at all outside mouse-aim — it drives keyboard-mode YAW panels directly by
     * stick position instead (see {@code AeroFlightCore.deflectFor}), the same as A/D and Q/E do
     * for PITCH/ROLL panels.
     *
     * <p><b>Mouse-aim owns pitch and heading together.</b> The commanded attitude chases wherever
     * the pilot is looking on both axes, through the same deadzone / response-curve / rate-limit
     * pipeline and with its own sensitivity per axis. A/D survives in that mode as a pitch trim on
     * top of where the mouse aims; W/S does not trim heading there — mouse-aim's yaw stays exactly
     * look-driven, unchanged from before this axis remap.
     *
     * <p>Roll wraps; pitch is clamped to {@link #MAX_PITCH_DEG}, because a commanded attitude of
     * exactly straight up or down is a gimbal singularity for the attitude solver and because the
     * server clamps the same value to the same range.
     */
    private static void updateAttitude(LocalPlayer player, Minecraft mc, double dt, boolean justMounted) {
        // Roll: Q/E integrated, with an optional arcade auto-level once the stick is centred.
        rollDeg = Mth.wrapDegrees((float)
                (rollDeg + stickRoll * XenoClientConfig.flightRollRateDegPerSec * dt));
        if (XenoClientConfig.flightAutoLevel && Math.abs(stickRoll) < 0.05) {
            rollDeg = approach(rollDeg, 0.0, XenoClientConfig.flightAutoLevelRateDegPerSec * dt);
        }

        if (XenoClientConfig.flightMouseAim) {
            double rawYaw = Mth.wrapDegrees(player.getYRot() + 180.0f);
            // Minecraft's xRot is positive looking down; a commanded attitude is positive nose-up.
            double rawPitch = -player.getXRot();
            if (XenoClientConfig.flightInvertPitch) rawPitch = -rawPitch;
            rawPitch = Mth.clamp(rawPitch, -MAX_PITCH_DEG, MAX_PITCH_DEG);
            if (justMounted) {
                // Easing from a reset 0,0 would spend the first second of a mount crawling toward
                // where the pilot is already looking. That one frame snaps.
                yawDeg = rawYaw;
                pitchAimDeg = rawPitch;
            } else {
                yawDeg = Mth.wrapDegrees((float) (yawDeg + yawStep(rawYaw, dt)));
                pitchAimDeg = Mth.clamp(pitchAimDeg
                                + axisStep(rawPitch - pitchAimDeg, dt,
                                        XenoClientConfig.flightMouseSensitivityY),
                        -MAX_PITCH_DEG, MAX_PITCH_DEG);
            }
            double trim = stickPitch * STICK_PITCH_TRIM_DEG;
            if (XenoClientConfig.flightInvertPitch) trim = -trim;
            pitchDeg = Mth.clamp(pitchAimDeg + trim, -MAX_PITCH_DEG, MAX_PITCH_DEG);
        } else {
            double pitchInput = stickPitch;
            if (XenoClientConfig.flightInvertPitch) pitchInput = -pitchInput;
            pitchAimDeg = Mth.clamp(
                    pitchAimDeg + pitchInput * XenoClientConfig.flightPitchRateDegPerSec * dt,
                    -MAX_PITCH_DEG, MAX_PITCH_DEG);
            pitchDeg = pitchAimDeg;
        }
    }

    /**
     * Seated WASD is the stick, not walking.
     *
     * <p><b>{@code shiftKeyDown} is deliberately left alone.</b> It is not just a movement flag:
     * {@link LocalPlayer#tick()} forwards it to the server inside {@code ServerboundPlayerInputPacket},
     * and {@code Player.rideTick()} reads it back as {@code wantsToStopRiding()} — it <i>is</i>
     * the dismount signal. Clearing it here left a pilot with no way out of the seat at all,
     * since the seat block also refuses a right-click from a player who is already a passenger.
     */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        if (!(player.getVehicle() instanceof XenoPilotSeatEntity)) return;
        event.getInput().forwardImpulse = 0.0f;
        event.getInput().leftImpulse = 0.0f;
        event.getInput().jumping = false;
    }

    /**
     * Swallow the vanilla actions whose keys the cockpit bindings deliberately share, before
     * {@code Minecraft.handleKeybinds()} gets to act on them.
     *
     * <p>Roll sits on Q and E because that is where a pilot's hands go, and those are vanilla's
     * drop-item and open-inventory keys. A conflict context cannot help here — it governs this
     * mod's own mappings, not Minecraft's — so without this a roll-left throws the pilot's held
     * item out of the cockpit and a roll-right opens the inventory instead of banking. Middle
     * mouse is the lock button and vanilla's pick-block for the same reason.
     *
     * <p>Runs in {@link ClientTickEvent.Pre}, which fires as the first statement of
     * {@code Minecraft.tick()} — before {@code handleKeybinds()} consumes these same queues, and
     * therefore the only point at which draining them actually suppresses the vanilla action.
     * Chat is <i>not</i> suppressed: silently disabling chat on a multiplayer server would be a
     * far worse trade than moving a binding, which is why no cockpit binding sits on T.
     */
    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        if (!XenoClientConfig.flightSuppressVanillaKeysInSeat) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (!(mc.player.getVehicle() instanceof XenoPilotSeatEntity)) return;
        while (mc.options.keyDrop.consumeClick()) { }
        while (mc.options.keyPickItem.consumeClick()) { }
        while (mc.options.keyInventory.consumeClick()) { }
    }

    /**
     * Send only when something moved, and never faster than the interval — with a keepalive so
     * the server does not decide a still-but-present pilot has gone away.
     */
    private static void maybeSend(int seatEntityId, boolean airBrake, boolean flapCommanded) {
        ticksSinceSend++;
        double flap = FLAP_STAGES[flapStage];
        FlightInputChanges.Frame frame = new FlightInputChanges.Frame(seatEntityId, throttle, flap,
                yawDeg, pitchDeg, rollDeg, stickPitch, stickRoll, stickYaw,
                XenoClientConfig.flightMouseAim, airBrake, XenoClientConfig.flightAutoLevel);
        boolean changed = frame.differs(sentFrame) || flapCommanded;

        if (!changed && ticksSinceSend < KEEPALIVE_TICKS) return;
        if (ticksSinceSend < MIN_SEND_INTERVAL_TICKS) return;

        ModNetwork.sendToServer(new SeatFlightInputPacket(seatEntityId, yawDeg, pitchDeg, rollDeg,
                stickPitch, stickRoll, stickYaw, XenoClientConfig.flightMouseAim,
                throttle, flap, airBrake, XenoClientConfig.flightAutoLevel, flapCommanded));
        ticksSinceSend = 0;
        sentFrame = frame;
    }

    /** Ramps throttle while a throttle key is held, alongside (not instead of) the scroll wheel. */
    private static void updateThrottleKeys(double dt) {
        double rate = XenoClientConfig.flightThrottleKeyRatePerSec * dt;
        if (THROTTLE_UP.isDown()) throttle = Mth.clamp(throttle + rate, 0.0, 1.0);
        if (THROTTLE_DOWN.isDown()) throttle = Mth.clamp(throttle - rate, 0.0, 1.0);
    }

    private static void drain() {
        while (ROLL_LEFT.consumeClick()) { }
        while (ROLL_RIGHT.consumeClick()) { }
        while (FLAP_STEP.consumeClick()) { }
        while (MOUSE_AIM_TOGGLE.consumeClick()) { }
        while (CENTER_CONTROLS.consumeClick()) { }
        while (AIR_BRAKE.consumeClick()) { }
        while (THROTTLE_UP.consumeClick()) { }
        while (THROTTLE_DOWN.consumeClick()) { }
        while (TOGGLE_BINDING.consumeClick()) { }
        while (THIRD_PERSON_TOGGLE.consumeClick()) { }
    }

    /** Clears only animation state this cockpit may have started; held items and player visibility stay untouched. */
    private static void cleanupSeatVisualState(LocalPlayer player) {
        if (player != null) {
            DmzAnimHelperClient.playLocalChargeStop(player);
        }
        DmzAnimHelperClient.ClientStrikeChain.clear();
        seatVisualStateActive = false;
    }

    /** Forget the whole control position. Leaving the seat must not carry throttle into the next one. */
    public static void reset() {
        throttle = 0.0;
        rollDeg = 0.0;
        yawDeg = 0.0;
        pitchDeg = 0.0;
        flapStage = 0;
        flapCommandTicks = 0;
        stickPitch = 0.0;
        stickRoll = 0.0;
        stickYaw = 0.0;
        pitchAimDeg = 0.0;
        ticksSinceSend = KEEPALIVE_TICKS;
        sentFrame = null;
    }

    private static void actionBar(Minecraft mc, String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§b" + message), true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        restoreFirstPersonIfOurs(Minecraft.getInstance());
    }
}
