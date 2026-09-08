package net.bullettrain.xenopixelsmod.client.combat;

import net.neoforged.fml.common.EventBusSubscriber;

import com.dragonminez.client.events.DMZClientEvent;
import com.dragonminez.client.events.LockOnEvent;
import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.DmzClientStats;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.pad.XenoPadInput;
import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;
import net.bullettrain.xenopixelsmod.client.combat.DmzAnimHelperClient;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.network.Bt3CombatPacket;
import net.bullettrain.xenopixelsmod.network.ChargeAnimPacket;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.GuidanceHoldPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.bullettrain.xenopixelsmod.combat.RushCamera;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * BT3 / Sparking Zero combat (client input + prediction).
 *
 * <pre>
 * Lock-on ONLY:  vanish (A/D), chase (W), backstep (S), dragon dash (N)
 * Anywhere:      combo (DMZ attack / left click), held-left-click charge punch, charge kick (MMB)
 * </pre>
 *
 * Double-tap uses vanilla move keys only — no second WASD KeyMapping.
 */
public final class Bt3CombatClient {
    // Unbound optional alts — never dual-map WASD (breaks flight / launch)
    public static final KeyMapping DASH_LEFT = new KeyMapping(
            "key.xenopixelsmod.bt3_dash_left", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, -1, "key.categories.xenopixelsmod");
    public static final KeyMapping DASH_RIGHT = new KeyMapping(
            "key.xenopixelsmod.bt3_dash_right", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, -1, "key.categories.xenopixelsmod");
    public static final KeyMapping CHASE = new KeyMapping(
            "key.xenopixelsmod.bt3_chase", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, -1, "key.categories.xenopixelsmod");
    public static final KeyMapping BACKSTEP = new KeyMapping(
            "key.xenopixelsmod.bt3_backstep", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, -1, "key.categories.xenopixelsmod");
    /** Xeno's own punch/combo input, deliberately bound to left click. */
    public static final KeyMapping CHARGE_FIST = new KeyMapping(
            "key.xenopixelsmod.bt3_charge_fist", KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT, "key.categories.xenopixelsmod");
    public static final KeyMapping CHARGE_KICK = new KeyMapping(
            "key.xenopixelsmod.bt3_charge_kick", KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, "key.categories.xenopixelsmod");
    public static final KeyMapping DRAGON_DASH = new KeyMapping(
            "key.xenopixelsmod.bt3_dragon_dash", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.xenopixelsmod");
    /** Hold to guard / block (STM drain); shares DMZ Block's former key with Use/place. */
    public static final KeyMapping GUARD = new KeyMapping(
            "key.xenopixelsmod.bt3_guard", KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, "key.categories.xenopixelsmod");
    /** Mid-combo Z-Burst step-in. */
    public static final KeyMapping Z_BURST = new KeyMapping(
            "key.xenopixelsmod.bt3_zburst", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.xenopixelsmod");
    /**
     * Hold while your own ki wave is firing to make it grow.
     *
     * <p>Left mouse: hold it on a firing beam and the beam keeps growing, which is the gesture
     * players already expect from every other "pour more into it" mechanic.
     *
     * <p>It has been through two defaults before this. C was unusable because C is DMZ's
     * {@code ki_charge}, so holding it to surge also started a ki charge; Left Alt worked but sat
     * under the same hand as the Alt+1..4 technique chord that casts the wave in the first place.
     * {@code migrateBeamSurgeKey} moves profiles off both.
     *
     * <p><b>Left mouse is also attack.</b> The surge polls the physical button through
     * {@code KeyBinds.isPhysicallyDown}, which reads GLFW directly, so it registers regardless of
     * how NeoForge resolves the conflict — but the vanilla attack binding still fires on the same
     * press, and the controls screen will flag the clash.
     */
    public static final KeyMapping BEAM_SURGE = new KeyMapping(
            "key.xenopixelsmod.bt3_beam_surge", KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT, "key.categories.xenopixelsmod");
    /** Mid-combo ki blast cancel. */
    public static final KeyMapping KI_BLAST_CANCEL = new KeyMapping(
            "key.xenopixelsmod.bt3_ki_blast_cancel", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.xenopixelsmod");
    /** Cycle lock-on to next target. */
    public static final KeyMapping LOCK_NEXT = new KeyMapping(
            "key.xenopixelsmod.bt3_lock_next", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, "key.categories.xenopixelsmod");
    /** Cycle lock-on to previous target. */
    public static final KeyMapping LOCK_PREV = new KeyMapping(
            "key.xenopixelsmod.bt3_lock_prev", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, "key.categories.xenopixelsmod");
    /** Sonic sway left (side-step + i-frames). */
    public static final KeyMapping SONIC_SWAY_LEFT = new KeyMapping(
            "key.xenopixelsmod.bt3_sonic_left", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_COMMA, "key.categories.xenopixelsmod");
    /** Sonic sway right. */
    public static final KeyMapping SONIC_SWAY_RIGHT = new KeyMapping(
            "key.xenopixelsmod.bt3_sonic_right", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD, "key.categories.xenopixelsmod");
    /** Ultimate skill smash. */
    public static final KeyMapping ULTIMATE = new KeyMapping(
            "key.xenopixelsmod.bt3_ultimate", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "key.categories.xenopixelsmod");
    /** Activate sparking when meter full. */
    public static final KeyMapping SPARKING = new KeyMapping(
            "key.xenopixelsmod.bt3_sparking", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.xenopixelsmod");
    /** Hold J to channel Hakai. */
    public static final KeyMapping HAKAI = new KeyMapping(
            "key.xenopixelsmod.bt3_hakai", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.xenopixelsmod");
    /**
     * Zanzoken: the afterimage dodge. Tapped as a hit is about to land, not held — the window is
     * a read, so a hold binding would turn it into a worse block.
     *
     * <p>Ships unbound, the way several DragonMineZ actions do. Every key close enough to the
     * movement hand to suit a reactive dodge is already taken by vanilla, DMZ or this mod, so the
     * choice belongs to the player rather than to a default that collides with something.
     */
    /**
     * Shi Shin No Ken: divide into several bodies, or reunite. Unbound for the same reason
     * Zanzoken is — the key space is already full.
     */
    public static final KeyMapping MULTIFORM = new KeyMapping(
            "key.xenopixelsmod.bt3_multiform", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
            "key.categories.xenopixelsmod");
    public static final KeyMapping ZANZOKEN = new KeyMapping(
            "key.xenopixelsmod.bt3_zanzoken", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
            "key.categories.xenopixelsmod");
    /** Hold to home ki on lock-on (Ki Guidance skill). */
    public static final KeyMapping KI_GUIDANCE = new KeyMapping(
            "key.xenopixelsmod.ki_guidance", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_ALT, "key.categories.xenopixelsmod");
    /** Same hold, extra mouse button. */
    public static final KeyMapping KI_GUIDANCE_MOUSE = new KeyMapping(
            "key.xenopixelsmod.ki_guidance_mouse", KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5, "key.categories.xenopixelsmod");

    private static final int COMBO_WINDOW_TICKS = 18;
    /**
     * Ticks between beats of a held mash, from the server config so it can be retuned in game.
     *
     * <p>This used to be a hard 4, which is exactly the server's
     * {@code Bt3CombatLimiter.MIN_ACTION_INTERVAL_TICKS} anti-spam floor measured on the server's
     * own tick clock. Sitting on that boundary meant ordinary jitter got beats silently rejected,
     * and a rejected beat leaves the client a step ahead of the server, so the next confirmation
     * disagrees with the prediction and a second clip starts on top of the one already playing.
     * The default now leaves headroom above the floor.
     */
    private static int holdComboIntervalTicks() {
        return Math.max(2, Math.min(20, XenoServerClientState.get().comboMashIntervalTicks));
    }

    /**
     * Reads Xeno's <em>own</em> punch binding, not Minecraft's Attack.
     *
     * <p>{@link #CHARGE_FIST} defaults to left click, so in a stock profile this is the same
     * press. Polling {@code options.keyAttack} instead meant unbinding Minecraft Attack — which is
     * a legitimate thing to want, since Xeno owns the click anyway — silently killed every punch,
     * combo and charged fist even though Xeno's binding was still sitting on left click.
     * {@code keyAttack} remains the fallback for a profile where the Xeno binding was cleared.
     */
    private static boolean leftMouseDown(Minecraft mc) {
        if (mc == null || mc.screen != null) return false;
        if (CHARGE_FIST != null && !CHARGE_FIST.isUnbound()) return heldNow(CHARGE_FIST);
        return heldNow(mc.options.keyAttack);
    }
    /** Press shorter than this is a tap (one mash beat + arm charge). Longer is hold-to-mash. */
    private static final int FIST_TAP_MAX_TICKS = 6;
    /** Window after a short left-click hold in which a second hold starts a charged fist. */
    private static final long FIST_CHARGE_ARM_MS = 400L;
    /** W-fallback only: press shorter than this arms the follow-up hold. */
    private static final int CHASE_TAP_MAX_TICKS = 6;
    /** W-fallback only: window after a W tap in which holding W starts the chase. */
    private static final long CHASE_ARM_MS = 400L;
    /**
     * Dedicated chase key: the chase starts on the first tick the key is down. No arming tap and
     * no hold delay — pressing it is the whole gesture.
     */
    private static final int CHASE_HOLD_START_TICKS = 1;
    private static final int MOVE_COOLDOWN_TICKS = 8;
    private static final long DOUBLE_TAP_MS = 280L;

    private static int comboStep;
    /** Last-tick W so a tap (rising edge) can arm a mash launcher without stealing walk-hold. */
    private static boolean comboWDownLastTick;
    private static int chaseHoldTicks;
    private static long chaseArmedUntilMs;
    private static boolean chaseWDownLastTick;
    private enum ChaseInput {
        NONE, DEDICATED, SPACE, W,
        /**
         * A dragon dash launched out of a combo beat. Held by the raw W key rather than the
         * retired hold-W gesture: that gesture is off by default, and reusing it here would have
         * ended the dash on the very next tick.
         */
        COMBO_PURSUE
    }
    private static ChaseInput chaseInput = ChaseInput.NONE;
    /** One "lock on first" per hold, not one per tick of it. */
    private static boolean chaseNoLockNoticed;
    /** First W tap timestamp for the double-tap-then-hold rush gesture. */
    private static long rushFirstTapMs;
    private static boolean rushRepeating;
    /** One hint per W gesture when a rush is attempted against a target still on the ground. */
    private static boolean rushGroundedNoticed;
    private static int rushSequenceStep;
    /** Consumed by the next mash beat: W was tapped during this string. */
    private static boolean comboLauncherArmed;
    /**
     * Client mirror of the server's per-string rush route, rotated on the same rule
     * ({@code Bt3CombatLimiter.nextComboStep}) so local prediction picks the beat the server is
     * about to pick. Drift is possible if the two disagree about when a string ended; the
     * server's broadcast is what settles it.
     */
    private static int clientComboRoute;
    private static boolean clientComboEverStarted;
    /**
     * Client mirror of the server's per-string airborne latch. Set when a string starts, not
     * per hit, so a jump mid-combo cannot swap prediction onto the air route.
     */
    private static boolean clientComboAirborne;
    private static int comboTicksLeft;
    /** Last mash pose this string; used so head-follow only runs on spin / flying-kick beats. */
    private static net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent lastMashIntent;
    private static int holdComboCooldown;
    private static int fistHoldTicks;
    private static long fistChargeArmedUntilMs;
    private static int moveCooldown;
    private static int moveCooldownMax = MOVE_COOLDOWN_TICKS;
    private static Bt3CombatPacket.Action lastMoveAction = Bt3CombatPacket.Action.VANISH;

    private static boolean leftWasDown, rightWasDown, forwardWasDown, backWasDown;
    private static long lastLeftTapMs, lastRightTapMs, lastForwardTapMs, lastBackTapMs;

    private enum ChargeMode { NONE, FIST, KICK, DRAGON }

    private static ChargeMode chargeMode = ChargeMode.NONE;
    private static int chargeTicks;
    private static boolean chargeFullyGlowed;
    private static boolean fistWasDown, kickWasDown, dragonWasDown;
    private static boolean guardWasDown;
    private static boolean clientGuarding;
    /** True after a HAKAI_START this hold; release does not cancel the channel. */
    private static boolean hakaiSentThisHold;
    private static int zBurstCd;
    private static int kiBlastCd;
    private static int counterFlashTicks;
    private static int sonicCd;
    private static int ultimateCd;
    private static float clientSparkingMeter;

    private Bt3CombatClient() {}

    public static boolean isGuarding() {
        return clientGuarding;
    }

    public static boolean isCounterWindowFlash() {
        return counterFlashTicks > 0;
    }

    public static int getZBurstCd() {
        return Math.max(0, zBurstCd);
    }

    public static int getKiBlastCd() {
        return Math.max(0, kiBlastCd);
    }

    public static int getSonicCd() {
        return Math.max(0, sonicCd);
    }

    public static int getUltimateCd() {
        return Math.max(0, ultimateCd);
    }

    public static float getSparkingMeter() {
        return Math.max(0f, Math.min(1f, clientSparkingMeter / 100f));
    }

    /** 0..1 current charge progress for glow renderer. */
    public static float getChargeProgress() {
        if (chargeMode == ChargeMode.NONE) return 0f;
        int max = Math.max(1, XenoServerClientState.get().chargeMaxTicks);
        return Math.min(1f, chargeTicks / (float) max);
    }

    public static boolean isFullyCharged() {
        return chargeMode != ChargeMode.NONE && getChargeProgress() >= 1f;
    }

    public static boolean isCharging() {
        return chargeMode != ChargeMode.NONE;
    }

    public static boolean isHakaiChanneling() {
        return hakaiSentThisHold;
    }

    /** Spin/flight beats own head yaw; ordinary punches keep following DMZ camera look. */
    public static boolean mashBodyYawOwnsHead() {
        if (comboTicksLeft > 0 && lastMashIntent != null) {
            return lastMashIntent.isBodyYawPose();
        }
        return chargeMode == ChargeMode.KICK || chargeMode == ChargeMode.DRAGON;
    }

    public static boolean isKickCharge() {
        return chargeMode == ChargeMode.KICK;
    }

    public static boolean isDragonCharge() {
        return chargeMode == ChargeMode.DRAGON;
    }

    // --- Cooldown HUD accessors ---

    public static int getMoveCooldownTicks() {
        return Math.max(0, moveCooldown);
    }

    public static int getMoveCooldownMaxTicks() {
        return Math.max(1, moveCooldownMax);
    }

    public static float getMoveCooldownFraction() {
        if (moveCooldownMax <= 0 || moveCooldown <= 0) return 0f;
        return Math.min(1f, moveCooldown / (float) moveCooldownMax);
    }

    public static boolean isMoveOnCooldown() {
        return moveCooldown > 0;
    }

    public static Bt3CombatPacket.Action getLastMoveAction() {
        return lastMoveAction != null ? lastMoveAction : Bt3CombatPacket.Action.VANISH;
    }

    public static int getComboStep() {
        return comboStep;
    }

    public static int getComboTicksLeft() {
        return Math.max(0, comboTicksLeft);
    }

    public static int getComboWindowMaxTicks() {
        return COMBO_WINDOW_TICKS;
    }

    public static float getComboWindowFraction() {
        if (comboTicksLeft <= 0) return 0f;
        return Math.min(1f, comboTicksLeft / (float) COMBO_WINDOW_TICKS);
    }

    private static void startMoveCooldown(Bt3CombatPacket.Action action) {
        lastMoveAction = action;
        moveCooldownMax = MOVE_COOLDOWN_TICKS;
        moveCooldown = moveCooldownMax;
    }

    private static boolean scrubbedDualWasdBinds;
    /** One-shot per session: transfer DMZ Block's key to Xeno Guard and retire duplicate blocking. */
    private static boolean migratedGuardBinding;
    /** One-shot per session: move Beam Surge off C, which is DMZ's ki_charge. */
    private static boolean migratedBeamSurgeBinding;
    /** One-shot: move Ki Guidance off G onto Left Alt. */
    private static boolean migratedGuidanceBinding;
    /** One-shot: move Hakai off Ctrl+J onto J. */
    private static boolean migratedHakaiBinding;
    private static boolean migratedAttackBinding;

    @EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(DASH_LEFT);
            event.register(DASH_RIGHT);
            event.register(CHASE);
            event.register(BACKSTEP);
            event.register(CHARGE_KICK);
            event.register(CHARGE_FIST);
            event.register(DRAGON_DASH);
            event.register(GUARD);
            event.register(Z_BURST);
            event.register(KI_BLAST_CANCEL);
            event.register(BEAM_SURGE);
            event.register(LOCK_NEXT);
            event.register(LOCK_PREV);
            event.register(SONIC_SWAY_LEFT);
            event.register(SONIC_SWAY_RIGHT);
            event.register(ULTIMATE);
            event.register(SPARKING);
            event.register(HAKAI);
            event.register(ZANZOKEN);
            event.register(MULTIFORM);
            event.register(KI_GUIDANCE);
            event.register(KI_GUIDANCE_MOUSE);
        }
    }

    @EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
    public static class ForgeBus {
        /** Discard every queued ground-combat press, for the ticks combat is not the active context. */
        private static void drainCombatKeys() {
            while (DASH_LEFT.consumeClick()) { }
            while (DASH_RIGHT.consumeClick()) { }
            while (CHASE.consumeClick()) { }
            while (BACKSTEP.consumeClick()) { }
            while (CHARGE_KICK.consumeClick()) { }
            while (DRAGON_DASH.consumeClick()) { }
            while (GUARD.consumeClick()) { }
            while (Z_BURST.consumeClick()) { }
            while (BEAM_SURGE.consumeClick()) { }
            while (KI_BLAST_CANCEL.consumeClick()) { }
            while (LOCK_NEXT.consumeClick()) { }
            while (LOCK_PREV.consumeClick()) { }
            while (SONIC_SWAY_LEFT.consumeClick()) { }
            while (SONIC_SWAY_RIGHT.consumeClick()) { }
            while (ULTIMATE.consumeClick()) { }
            while (SPARKING.consumeClick()) { }
            while (HAKAI.consumeClick()) { }
            while (ZANZOKEN.consumeClick()) { }
            while (MULTIFORM.consumeClick()) { }
            while (KI_GUIDANCE.consumeClick()) { }
            while (KI_GUIDANCE_MOUSE.consumeClick()) { }
        }

        @SubscribeEvent
        public static void onLogin(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
            resetConnectionState();
        }

        @SubscribeEvent
        public static void onLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            resetConnectionState();
        }

        private static net.minecraft.world.level.Level inputLevel;

        private static void resetConnectionState() {
            inputLevel = null;
            xenoDigging = false;
            stopClientChase(false);
            resetCharge();
            resetRushHold();
            moveCooldown = 0;
            clientGuarding = false;
            clearGuardInputState();
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            XenoPadInput.tick();
            Minecraft mc = Minecraft.getInstance();

            if (inputLevel != mc.level) {
                stopClientChase(mc.getConnection() != null);
                resetCharge();
                resetRushHold();
                inputLevel = mc.level;
            }

            // One-shot: old options.txt often dual-mapped WASD onto these optional alts,
            // which steals key edges from movement / flight and confuses combat input.
            if (!scrubbedDualWasdBinds && mc.player != null) {
                scrubbedDualWasdBinds = true;
                scrubDualWasdBinds(mc);
            }

            if (!migratedGuardBinding && mc.player != null) {
                migratedGuardBinding = true;
                transferDmzGuardKey(mc);
            }

            if (!migratedBeamSurgeBinding && mc.player != null) {
                migratedBeamSurgeBinding = true;
                migrateBeamSurgeKey(mc);
            }

            if (!migratedGuidanceBinding && mc.player != null) {
                migratedGuidanceBinding = true;
                migrateGuidanceKey(mc);
            }

            if (!migratedHakaiBinding && mc.player != null) {
                migratedHakaiBinding = true;
                migrateHakaiKey(mc);
            }
            if (!migratedAttackBinding && mc.player != null) {
                migratedAttackBinding = true;
                restoreLegacyAttackBinding(mc);
            }

            if (mc.player == null || mc.level == null || mc.screen != null || !mc.player.isAlive() || mc.player.isSpectator()) {
                stopClientChase(mc.getConnection() != null);
                if (clientGuarding) {
                    clientGuarding = false;
                    send(new Bt3CombatPacket(
                            Bt3CombatPacket.Action.GUARD, -1, 0));
                }
                clearGuardInputState();
                cancelHakaiIfChanneling();
                resetCharge();
                resetRushHold();
                guidanceWasDown = false;
                return;
            }
            // A pilot strapped into a control chair is flying, not fighting on foot. Several
            // cockpit bindings deliberately sit on the same keys as ground combat (lock on
            // middle mouse vs. charge kick, clear-lock on R vs. charge fist, lead toggle on Y
            // vs. sparking); without this both fire from one press. Ground combat yields,
            // because the seat is the more specific context — you cannot dragon-dash out of a
            // chair anyway. Presses are drained rather than ignored so none is banked and
            // replayed the instant the pilot stands up.
            if (mc.player.getVehicle() instanceof net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity) {
                stopClientChase(true);
                if (clientGuarding) {
                    clientGuarding = false;
                    send(new Bt3CombatPacket(
                            Bt3CombatPacket.Action.GUARD, -1, 0));
                }
                clearGuardInputState();
                cancelHakaiIfChanneling();
                resetCharge();
                resetRushHold();
                guidanceWasDown = false;
                drainCombatKeys();
                return;
            }
            tickGuidanceHold(mc);
            tickHakai(mc);
            if (!XenoClientConfig.bt3CombatClient || !XenoServerClientState.combat()) {
                stopClientChase(true);
                if (clientGuarding) {
                    clientGuarding = false;
                    send(new Bt3CombatPacket(
                            Bt3CombatPacket.Action.GUARD, -1, 0));
                }
                clearGuardInputState();
                resetCharge();
                resetRushHold();
                return;
            }

            sampleComboLauncherTap(mc);

            if (moveCooldown > 0) moveCooldown--;
            if (zBurstCd > 0) zBurstCd--;
            if (kiBlastCd > 0) kiBlastCd--;
            if (sonicCd > 0) sonicCd--;
            if (ultimateCd > 0) ultimateCd--;
            if (counterFlashTicks > 0) counterFlashTicks--;
            if (comboTicksLeft > 0) {
                comboTicksLeft--;
                if (comboTicksLeft == 0) {
                    comboStep = 0;
                    lastMashIntent = null;
                    // The string is over, so a confirmation arriving late must not be matched
                    // against the beat predicted for the string that just ended.
                    net.bullettrain.xenopixelsmod.client.combat.anim.Bt3AnimIntentClient.clearPrediction();
                }
            }

            DmzAnimHelperClient.ClientStrikeChain.tick(mc.player);

            // Left click owns Xeno's empty-hand mash/charge.
            tickCharge(mc);
            tickFistKey(mc);
            tickGuard(mc);
            tickChase(mc);
            tickXenoDrivenDig(mc);
            tickZanzoken(mc);
            tickMultiForm(mc);
            tickPhase1Keys(mc);

            LivingEntity locked = LockOnEvent.getLockedTarget();
            if (locked != null && !locked.isAlive()) locked = null;

            int padVanishSide = XenoPadInput.consumeVanishSide();
            if (locked != null && padVanishSide != 0) {
                tryMove(mc, locked, Bt3CombatPacket.Action.VANISH, padVanishSide);
            }

            long now = System.currentTimeMillis();
            // Vanilla move keys only — never dual combat KeyMappings on WASD
            boolean leftDown = mc.options.keyLeft.isDown();
            boolean rightDown = mc.options.keyRight.isDown();
            boolean forwardDown = mc.options.keyUp.isDown();
            boolean backDown = mc.options.keyDown.isDown();

            // Dragon homing: single W after a charged-kick knockback (lock not required)
            if (forwardDown && !forwardWasDown && DragonHomingClient.isLive()) {
                tryDragonHoming(mc, locked);
                lastForwardTapMs = 0;
            } else if (locked != null) {
                if (leftDown && !leftWasDown) {
                    if (lastLeftTapMs > 0 && now - lastLeftTapMs <= DOUBLE_TAP_MS) {
                        tryMove(mc, locked, Bt3CombatPacket.Action.VANISH, -1); // A = left vanish
                        lastLeftTapMs = 0;
                    } else {
                        lastLeftTapMs = now;
                    }
                }
                if (rightDown && !rightWasDown) {
                    if (lastRightTapMs > 0 && now - lastRightTapMs <= DOUBLE_TAP_MS) {
                        tryMove(mc, locked, Bt3CombatPacket.Action.VANISH, 1); // D = right vanish
                        lastRightTapMs = 0;
                    } else {
                        lastRightTapMs = now;
                    }
                }
                if (forwardDown) {
                    double dist = mc.player != null ? mc.player.distanceTo(locked) : 999;
                    double rushRange = Math.max(4.0, XenoServerClientState.get().rushChainRange);
                    boolean launched = isLaunched(locked)
                            || (mc.player != null && !mc.player.onGround()
                            && mc.player.getY() - locked.getY() > 1.5);
                    boolean rushEnabled = XenoClientConfig.bt3CombatClient
                            && XenoServerClientState.get().bt3RushChainEnabled;
                    // Sampled before the launch check so a double tap at a grounded target is still
                    // recognised as a rush attempt and can be answered.
                    boolean rising = !forwardWasDown;
                    boolean doubleTap = rising && rushFirstTapMs > 0
                            && now - rushFirstTapMs <= DOUBLE_TAP_MS;
                    if (rising) {
                        rushFirstTapMs = doubleTap ? 0 : now;
                    }
                    if (launched && dist <= rushRange && rushEnabled) {
                        rushGroundedNoticed = false;
                        // Single camera writer for the rush: tryRushChain no longer steers.
                        if (rushRepeating || doubleTap) {
                            lockRushView(mc.player, locked);
                        }
                        if ((doubleTap || rushRepeating) && moveCooldown <= 0) {
                            tryRushChain(mc, locked);
                            rushRepeating = true;
                        }
                    } else {
                        // A rush follows a launcher, as in BT3. Say so once per gesture rather than
                        // doing nothing silently, which reads as the feature being broken.
                        if (doubleTap && rushEnabled && dist <= rushRange
                                && !rushGroundedNoticed && mc.player != null) {
                            rushGroundedNoticed = true;
                            mc.player.displayClientMessage(
                                    Component.literal("§bRush: launch them first"), true);
                        }
                        resetRushHold();
                    }
                } else {
                    rushGroundedNoticed = false;
                    resetRushHold();
                }
                if (backDown && !backWasDown) {
                    if (lastBackTapMs > 0 && now - lastBackTapMs <= DOUBLE_TAP_MS) {
                        tryMove(mc, locked, Bt3CombatPacket.Action.BACKSTEP, 0);
                        lastBackTapMs = 0;
                    } else {
                        lastBackTapMs = now;
                    }
                }
            } else if (!(forwardDown && DragonHomingClient.isLive())) {
                // Not locked: clear pending double-taps so freelook walk never arms vanish
                lastLeftTapMs = lastRightTapMs = lastForwardTapMs = lastBackTapMs = 0;
                resetRushHold();
            }

            leftWasDown = leftDown;
            rightWasDown = rightDown;
            forwardWasDown = forwardDown;
            backWasDown = backDown;

            // Swallow optional alt bind clicks so old options.txt dual-maps never single-fire
            while (DASH_LEFT.consumeClick()) {}
            while (DASH_RIGHT.consumeClick()) {}
            while (CHASE.consumeClick()) {}
            while (BACKSTEP.consumeClick()) {}
        }

        /**
         * Unbind optional vanish/chase alts if they still share WASD with movement
         * (legacy options.txt dual-maps). Double-tap uses vanilla move keys only.
         */
        private static void scrubDualWasdBinds(Minecraft mc) {
            boolean changed = false;
            changed |= unbindIfSamePhysicalKey(DASH_LEFT, mc.options.keyLeft);
            changed |= unbindIfSamePhysicalKey(DASH_RIGHT, mc.options.keyRight);
            changed |= unbindIfSamePhysicalKey(CHASE, mc.options.keyUp);
            changed |= unbindIfSamePhysicalKey(BACKSTEP, mc.options.keyDown);
            if (changed) {
                mc.options.save();
                XenoPixelsMod.LOGGER.info(
                        "Unbound dual-mapped BT3 WASD combat alts (use double-tap move keys for vanish/chase/backstep)");
            }
        }

        private static boolean unbindIfSamePhysicalKey(KeyMapping combat, KeyMapping move) {
            if (combat == null || move == null) return false;
            try {
                if (combat.isUnbound()) return false;
                if (combat.getKey().getValue() != move.getKey().getValue()) return false;
                if (combat.getKey().getType() != move.getKey().getType()) return false;
                combat.setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        /** Transfer DMZ Block's configured input to Xeno Guard without changing Minecraft Use/place. */
        private static void transferDmzGuardKey(Minecraft mc) {
            try {
                KeyMapping dmzBlock = KeyBinds.BLOCK_KEY;
                if (dmzBlock == null) return;
                if (!dmzBlock.isUnbound()) {
                    GUARD.setKeyModifierAndCode(dmzBlock.getKeyModifier(), dmzBlock.getKey());
                    dmzBlock.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
                } else if (GUARD.getKey().getType() == InputConstants.Type.KEYSYM
                        && GUARD.getKey().getValue() == GLFW.GLFW_KEY_B) {
                    // Recover profiles migrated by the previous build, which had already discarded
                    // DMZ's default RMB binding and moved Xeno Guard to B.
                    GUARD.setKeyModifierAndCode(KeyModifier.NONE,
                            InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_RIGHT));
                } else {
                    return;
                }
                KeyMapping.resetMapping();
                mc.options.save();
                XenoPixelsMod.LOGGER.info(
                        "Transferred DMZ Block key to Xeno Guard; Minecraft Use/place remains unchanged");
            } catch (Throwable t) {
                XenoPixelsMod.LOGGER.warn("Failed to transfer DMZ Guard key binding: {}", t.toString());
            }
        }

        /**
         * Move Beam Surge onto left mouse from either default it shipped with before.
         *
         * <p>C was DMZ's {@code ki_charge}, so holding it to grow a wave also started a ki charge
         * and the surge never read as working. Left Alt replaced it and worked, but sits under the
         * same hand as the Alt+1..4 chord that casts the wave.
         *
         * <p>Deliberately narrow: only a binding still sitting on exactly one of those two former
         * defaults is moved, so anyone who rebound it on purpose is left alone.
         */
        private static void migrateBeamSurgeKey(Minecraft mc) {
            try {
                if (BEAM_SURGE.isUnbound()) return;
                InputConstants.Key key = BEAM_SURGE.getKey();
                if (key.getType() != InputConstants.Type.KEYSYM) return;
                if (key.getValue() != GLFW.GLFW_KEY_C && key.getValue() != GLFW.GLFW_KEY_LEFT_ALT) {
                    return;
                }
                String from = key.getValue() == GLFW.GLFW_KEY_C ? "C" : "Left Alt";
                BEAM_SURGE.setKey(
                        InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_LEFT));
                KeyMapping.resetMapping();
                mc.options.save();
                XenoPixelsMod.LOGGER.info(
                        "Migrated Beam Surge from {} to Left Mouse; hold it on a firing beam to grow it",
                        from);
            } catch (Throwable t) {
                XenoPixelsMod.LOGGER.warn("Failed to migrate Beam Surge key binding: {}", t.toString());
            }
        }

        private static void migrateGuidanceKey(Minecraft mc) {
            try {
                if (KI_GUIDANCE.isUnbound()) return;
                InputConstants.Key key = KI_GUIDANCE.getKey();
                if (key.getType() != InputConstants.Type.KEYSYM) return;
                if (key.getValue() != GLFW.GLFW_KEY_G && key.getValue() != GLFW.GLFW_KEY_LEFT_ALT) return;
                String from = key.getValue() == GLFW.GLFW_KEY_G ? "G" : "Left Alt";
                KI_GUIDANCE.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_RIGHT_ALT));
                KeyMapping.resetMapping();
                mc.options.save();
                XenoPixelsMod.LOGGER.info("Migrated Ki Guidance from {} to Right Alt", from);
            } catch (Throwable t) {
                XenoPixelsMod.LOGGER.warn("Failed to migrate Ki Guidance key binding: {}", t.toString());
            }
        }

        private static void migrateHakaiKey(Minecraft mc) {
            try {
                if (HAKAI.isUnbound()) return;
                if (HAKAI.getKeyModifier() != KeyModifier.CONTROL) return;
                InputConstants.Key key = HAKAI.getKey();
                if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() != GLFW.GLFW_KEY_J) {
                    return;
                }

                HAKAI.setKeyModifierAndCode(KeyModifier.NONE,
                        InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J));
                KeyMapping.resetMapping();
                mc.options.save();
                XenoPixelsMod.LOGGER.info("Migrated Hakai from Ctrl+J to J");
            } catch (Throwable t) {
                XenoPixelsMod.LOGGER.warn("Failed to migrate Hakai key binding: {}", t.toString());
            }
        }

        /**
         * Repairs the one genuinely broken profile: an old build unbound Minecraft Attack, and if
         * Xeno's own punch binding is cleared too the player is left with no way to attack or mine
         * at all.
         *
         * <p>Deliberately does nothing while {@link #CHARGE_FIST} is bound. Unbinding Minecraft
         * Attack on purpose is supported — Xeno reads its own binding for punches and drives the
         * block dig itself — so forcing Attack back onto left click every launch would just undo
         * the player's choice.
         */
        private static void restoreLegacyAttackBinding(Minecraft mc) {
            KeyMapping attack = mc.options.keyAttack;
            if (attack == null || !attack.isUnbound()) return;
            if (CHARGE_FIST != null && !CHARGE_FIST.isUnbound()) return;
            attack.setKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_LEFT));
            KeyMapping.resetMapping();
            mc.options.save();
        }

        @SubscribeEvent
        public static void onAttackStart(DMZClientEvent.PlayerAttackStart event) {
            // The active left-click mapping is polled by tickFistKey so taps and holds share one
            // state machine. The interaction event below suppresses duplicate DMZ melee.
        }

        /**
         * Backstop only. {@link net.bullettrain.xenopixelsmod.mixin.client.MinecraftFistOwnershipMixin}
         * normally cancels the attack earlier, before this hook is ever reached, and reissues the
         * dig itself.
         */
        @SubscribeEvent
        public static void onClickInput(InputEvent.InteractionKeyMappingTriggered event) {
            if (!event.isAttack()) return;
            Minecraft mc = Minecraft.getInstance();
            if (!suppressesNativeAttack(mc)) return;
            // Never cancelled on a block: Minecraft.startAttack and continueAttack both bail out on
            // a cancelled click hook, so that would kill the dig and every tick of its progress.
            // The vanilla arm swing is still dropped so it does not fight the Xeno punch animation.
            if (suppressesNativeMelee(mc)) {
                event.setCanceled(true);
            }
            event.setSwingHand(false);
        }

        @SubscribeEvent
        public static void onClientHurt(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || event.getEntity() != mc.player) return;
            if (!XenoClientConfig.bt3SuperCounterClient || !XenoServerClientState.superCounter()) return;
            if (event.getNewDamage() <= 0.05f) return;
            // Visual only — server owns the real counter window
            counterFlashTicks = Math.max(counterFlashTicks,
                    Math.max(4, XenoServerClientState.get().superCounterWindowTicks));
            mc.player.displayClientMessage(Component.literal("§bCounter window — vanish!"), true);
        }
    }

    /** Xeno Guard engages immediately while the shared Use/place input remains available. */
    private static void tickGuard(Minecraft mc) {
        boolean down = GUARD.isDown();
        boolean want = XenoClientConfig.bt3GuardClient && XenoServerClientState.guard()
                && down && chargeMode == ChargeMode.NONE;
        LocalPlayer local = mc.player;
        if (want && !guardWasDown) {
            clientGuarding = true;
            send(new Bt3CombatPacket(Bt3CombatPacket.Action.GUARD, -1, 1));
            // Local DMZ block pose (base.block) — same family as DMZ hold animations
            if (local != null) {
                DmzAnimHelperClient.playLocalBlockStart(local);
            }
        } else if (!want && guardWasDown && clientGuarding) {
            clientGuarding = false;
            send(new Bt3CombatPacket(Bt3CombatPacket.Action.GUARD, -1, 0));
            if (local != null) {
                DmzAnimHelperClient.playLocalBlockStop(local);
            }
        } else if (!want) {
            if (clientGuarding && local != null) {
                DmzAnimHelperClient.playLocalBlockStop(local);
            }
            clientGuarding = false;
        } else if (want && local != null && XenoClientConfig.bt3CombatAnims) {
            // Re-assert hold pose periodically so other anims don't leave us idle
            if (local.tickCount % 20 == 0) {
                DmzAnimHelperClient.playLocalBlockStart(local);
            }
        }
        guardWasDown = want;
    }

    private static void clearGuardInputState() {
        guardWasDown = false;
    }

    /**
     * Lock-on first, then the same look / nearest search as {@code /xenohakai use}.
     * One START per hold; release does not cancel.
     */
    private static void tryStartHakai(LocalPlayer player) {
        LivingEntity target = LockOnEvent.getLockedTarget();
        if (target != null && !target.isAlive()) target = null;
        if (target == null) {
            double range = XenoServerClientState.get().hakaiMaxRange;
            if (range <= 0) range = 15.0;
            target = findLookTarget(Minecraft.getInstance(), Math.max(6.0, range));
        }
        if (target == null) {
            if (player.tickCount % 40 == 0) {
                player.displayClientMessage(Component.literal(
                        "§cHakai: look at a living entity (or lock on)"), true);
            }
            return;
        }
        hakaiSentThisHold = true;
        player.displayClientMessage(Component.literal("§dHakai"), true);
        DmzAnimHelperClient.playLocalHakaiHold(player);
        send(new Bt3CombatPacket(
                Bt3CombatPacket.Action.HAKAI_START, target.getId(), 0));
    }

    /**
     * Polls raw J even when BT3 combat HUD is off. Hold starts; release cancels and restores.
     */
    private static void tickHakai(Minecraft mc) {
        boolean held = hakaiKeyHeld(mc);
        if (!held) {
            if (hakaiSentThisHold) {
                hakaiSentThisHold = false;
                if (mc.player != null) {
                    DmzAnimHelperClient.playLocalHakaiStop(mc.player);
                }
                send(new Bt3CombatPacket(
                        Bt3CombatPacket.Action.HAKAI_CANCEL, -1, 0));
            }
            while (HAKAI.consumeClick()) { }
            return;
        }
        if (!hakaiSentThisHold && chargeMode == ChargeMode.NONE) {
            tryStartHakai(mc.player);
        } else if (hakaiSentThisHold && mc.player != null && mc.player.tickCount % 20 == 0) {
            DmzAnimHelperClient.playLocalHakaiHold(mc.player);
        }
        while (HAKAI.consumeClick()) { }
    }

    private static boolean hakaiKeyHeld(Minecraft mc) {
        if (mc.player == null || mc.screen != null) return false;
        try {
            if (InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_J)) return true;
        } catch (Throwable ignored) {
        }
        return heldNow(HAKAI);
    }

    /**
     * Is this binding's key or mouse button physically down right now?
     *
     * <p>Reads the device directly rather than trusting {@code KeyMapping.isDown()}, which is a
     * press-counting state machine: it can be left stale by a screen opening mid-hold, and it
     * dispatches through modifier buckets that a rebind does not always follow. Every hold in this
     * class - mash, charge, chase, Hakai - has to be correct for the whole duration of the hold,
     * not just at the edges, and any of them can be bound to a mouse button (mash is on Mouse4 for
     * at least one player), so both device types are handled here rather than at each call site.
     */
    private static boolean heldNow(KeyMapping mapping) {
        if (mapping == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return false;
        if (physicallyDown(mapping)) return true;
        try {
            InputConstants.Key key = mapping.getKey();
            long window = mc.getWindow().getWindow();
            if (key.getType() == InputConstants.Type.KEYSYM) {
                return key.getValue() >= 0 && InputConstants.isKeyDown(window, key.getValue());
            }
            if (key.getType() == InputConstants.Type.MOUSE) {
                return key.getValue() >= 0
                        && GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void cancelHakaiIfChanneling() {
        hakaiSentThisHold = false;
    }

    private static boolean guidanceWasDown;

    private static void tickGuidanceHold(Minecraft mc) {
        if (mc.player == null || mc.screen != null) {
            guidanceWasDown = false;
            return;
        }
        boolean down = guidanceKeyDown();
        if (down) {
            LivingEntity locked = LockOnEvent.getLockedTarget();
            int targetId = locked != null && locked.isAlive() ? locked.getId() : -1;
            var look = mc.gameRenderer.getMainCamera().getLookVector();
            send(new GuidanceHoldPacket(targetId, new Vec3(look.x(), look.y(), look.z())));
        }
        if (down && !guidanceWasDown) {
            mc.player.displayClientMessage(Component.literal("§bKi Guidance"), true);
        }
        guidanceWasDown = down;
    }

    private static boolean guidanceKeyDown() {
        if (physicallyDown(KI_GUIDANCE) || physicallyDown(KI_GUIDANCE_MOUSE)) return true;
        long window = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    private static boolean physicallyDown(KeyMapping mapping) {
        // A gamepad press moves no physical key, so the pad is an additional source here, never a
        // replacement: the device read below is untouched and keyboard and pad work at once. This
        // one method is the choke point for the whole class - heldNow, guidanceKeyDown and
        // chordDown all come through here - which is why the pad only has to be wired in once.
        // Screen-gated to match Controlify's own key emulation, which stands down while a screen
        // is open; the keyboard path keeps leaving that judgement to its callers, as before.
        if (Minecraft.getInstance().screen == null && XenoPadInput.held(mapping)) return true;
        try {
            return KeyBinds.isPhysicallyDown(mapping);
        } catch (Throwable t) {
            return mapping.isDown();
        }
    }

    /**
     * {@link #physicallyDown} for a binding that carries a key modifier: false unless that
     * modifier is currently active, otherwise the same direct GLFW read of the binding's
     * <em>current</em> key — so a rebind in the Controls menu is honoured while
     * {@code KeyMapping.isDown()}'s modifier-bucket dispatch is bypassed entirely.
     */
    private static boolean chordDown(KeyMapping mapping) {
        try {
            return KeyBinds.isChordDown(mapping);
        } catch (Throwable t) {
            return mapping.isDown();
        }
    }

    private static void tickPhase1Keys(Minecraft mc) {
        LivingEntity locked = LockOnEvent.getLockedTarget();
        if (locked != null && !locked.isAlive()) locked = null;

        // Lock-on cycle
        if (XenoClientConfig.bt3LockCycleClient && XenoServerClientState.lockCycle()) {
            while (LOCK_NEXT.consumeClick()) {
                LockOnCycle.cycle(1);
            }
            while (LOCK_PREV.consumeClick()) {
                LockOnCycle.cycle(-1);
            }
        } else {
            while (LOCK_NEXT.consumeClick()) {}
            while (LOCK_PREV.consumeClick()) {}
        }

        // Z-Burst mid-combo
        while (Z_BURST.consumeClick()) {
            if (!XenoClientConfig.bt3ZBurstClient || !XenoServerClientState.zBurst()) continue;
            if (clientGuarding || chargeMode != ChargeMode.NONE) continue;
            if (comboStep <= 0 || comboTicksLeft <= 0) {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§7Z-Burst: mid-combo only"), true);
                }
                continue;
            }
            if (locked == null) {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§7Z-Burst: lock on first"), true);
                }
                continue;
            }
            if (zBurstCd > 0 || moveCooldown > 0) continue;
            send(new Bt3CombatPacket(
                    Bt3CombatPacket.Action.Z_BURST, locked.getId(), comboStep));
            zBurstCd = 12;
            startMoveCooldown(Bt3CombatPacket.Action.Z_BURST);
            comboTicksLeft = COMBO_WINDOW_TICKS;
        }

        // Ki blast cancel mid-combo. Surge moved to Left Alt, so the two no longer share a key
        // and cancel no longer has to stand down while a beam is out.
        while (KI_BLAST_CANCEL.consumeClick()) {
            if (!XenoClientConfig.bt3KiBlastCancelClient || !XenoServerClientState.kiBlastCancel()) continue;
            if (clientGuarding || chargeMode != ChargeMode.NONE) continue;
            if (comboStep <= 0 || comboTicksLeft <= 0) {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§7Ki cancel: mid-combo only"), true);
                }
                continue;
            }
            if (kiBlastCd > 0) continue;
            int tid = locked != null ? locked.getId() : -1;
            send(new Bt3CombatPacket(
                    Bt3CombatPacket.Action.KI_BLAST_CANCEL, tid, comboStep));
            kiBlastCd = 10;
            // Cancel string
            comboStep = 0;
            comboTicksLeft = 0;
            startMoveCooldown(Bt3CombatPacket.Action.KI_BLAST_CANCEL);
            clientSparkingMeter = Math.min(100f, clientSparkingMeter + 4f);
        }

        // Sonic sway
        while (SONIC_SWAY_LEFT.consumeClick()) {
            trySonic(mc, -1);
        }
        while (SONIC_SWAY_RIGHT.consumeClick()) {
            trySonic(mc, 1);
        }

        // Ultimate
        while (ULTIMATE.consumeClick()) {
            if (!XenoServerClientState.get().bt3UltimateEnabled) continue;
            if (clientGuarding || chargeMode != ChargeMode.NONE || ultimateCd > 0) continue;
            int tid = locked != null ? locked.getId() : -1;
            if (tid < 0) {
                LivingEntity look = findLookTarget(mc, 10.0);
                tid = look != null ? look.getId() : -1;
            }
            send(new Bt3CombatPacket(
                    Bt3CombatPacket.Action.ULTIMATE, tid, 0));
            ultimateCd = Math.max(40, XenoServerClientState.get().ultimateCooldownTicks);
            startMoveCooldown(Bt3CombatPacket.Action.ULTIMATE);
            clientSparkingMeter = Math.min(100f, clientSparkingMeter + 12f);
        }

        // Sparking activate
        while (SPARKING.consumeClick()) {
            if (!XenoServerClientState.get().bt3SparkingEnabled) continue;
            send(new Bt3CombatPacket(
                    Bt3CombatPacket.Action.SPARKING, -1, 0));
            // Optimistic full-meter clear for HUD; server is authority
            if (clientSparkingMeter >= 99f) clientSparkingMeter = 0f;
        }
    }

    private static void trySonic(Minecraft mc, int side) {
        if (!XenoServerClientState.get().bt3SonicSwayEnabled) return;
        if (clientGuarding || chargeMode != ChargeMode.NONE) return;
        if (sonicCd > 0 || moveCooldown > 0) return;
        LocalPlayer p = mc.player;
        if (p == null) return;
        Vec3 from = p.position();
        send(new Bt3CombatPacket(
                Bt3CombatPacket.Action.SONIC_SWAY, -1, side));
        sonicCd = Math.max(8, XenoServerClientState.get().sonicSwayCooldownTicks);
        startMoveCooldown(Bt3CombatPacket.Action.SONIC_SWAY);
        // Local afterimage prediction
        if (XenoClientConfig.bt3Afterimage) {
            try {
                float yaw = p.getYRot() * ((float) Math.PI / 180F);
                Vec3 right = new Vec3(-Math.sin(yaw + Math.PI / 2), 0, Math.cos(yaw + Math.PI / 2));
                if (side < 0) right = right.scale(-1);
                Vec3 to = from.add(right.scale(2.4));
                net.bullettrain.xenopixelsmod.combat.AfterimageFx.spawnTrailClient(p, from, to, 5);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void tryRushChain(Minecraft mc, LivingEntity locked) {
        if (locked == null || mc.player == null) return;
        if (moveCooldown > 0) return;
        // The caller already steered the view this tick. Steering again here applied the ease
        // twice in one tick, which is what made the rush camera jerk.
        rushSequenceStep = Math.floorMod(rushSequenceStep, 4) + 1;
        send(new Bt3CombatPacket(
                Bt3CombatPacket.Action.RUSH_CHAIN, locked.getId(), rushSequenceStep));
        startMoveCooldown(Bt3CombatPacket.Action.RUSH_CHAIN);
        comboTicksLeft = COMBO_WINDOW_TICKS;
        clientSparkingMeter = Math.min(100f, clientSparkingMeter + 8f);
    }

    /**
     * Keeps the rush and chase view on the locked victim.
     *
     * <p>All the arithmetic lives in {@link RushCamera} so it can be tested; this is only the part
     * that has to touch a live player. The assist fades to nothing at contact range, which is where
     * a rush actually lands: the fighter is already facing the target there, and tracking angles at
     * that distance is what made the camera thrash.
     */
    private static void lockRushView(LocalPlayer player, LivingEntity target) {
        if (player == null || target == null) return;
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        // Mid-body rather than eye-to-eye: at contact the eye offset is the steepest possible
        // angle at the worst possible moment.
        double dy = (target.getY() + target.getBbHeight() * 0.5) - player.getEyeY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0e-4 && Math.abs(dy) < 1.0e-4) return;

        float strength = RushCamera.assistStrength(Math.sqrt(horizontal * horizontal + dy * dy));
        if (strength <= 0.0f) return;

        float yawError = Mth.degreesDifference(player.getYRot(), RushCamera.wantYaw(dx, dz));
        if (RushCamera.shouldSteer(yawError, strength)) {
            player.setYRot(player.getYRot()
                    + RushCamera.step(yawError, RushCamera.YAW_EASE, strength));
        }
        float pitchError = RushCamera.wantPitch(dy, horizontal) - player.getXRot();
        if (RushCamera.shouldSteer(pitchError, strength)) {
            player.setXRot(Mth.clamp(player.getXRot()
                    + RushCamera.step(pitchError, RushCamera.PITCH_EASE, strength), -90f, 90f));
        }
        // yBodyRotO is deliberately left alone. Overwriting the previous-tick value removes the
        // renderer's interpolation, so the body snapped to the new angle once every tick.
        player.setYHeadRot(player.getYRot());
        player.yBodyRot = player.getYRot();
    }


    private static void resetRushHold() {
        rushRepeating = false;
        rushSequenceStep = 0;
    }

    /**
     * Latch a W tap until the next mash beat. Holding W to walk does not arm — only a rising
     * edge while a string is live (or R is already down).
     */
    private static void sampleComboLauncherTap(Minecraft mc) {
        boolean down = mc.options.keyUp.isDown();
        boolean mashLive = comboTicksLeft > 0 || fistHoldTicks > 0 || leftMouseDown(Minecraft.getInstance());
        if (down && !comboWDownLastTick && mashLive) {
            comboLauncherArmed = true;
        }
        if (!mashLive) {
            comboLauncherArmed = false;
        }
        comboWDownLastTick = down;
    }

    /**
     * Which held direction key is pinning the mash to a single move, as a
     * {@code Bt3ComboChoreography.MASH_STYLE_*} constant.
     *
     * <p>Read from the vanilla strafe bindings rather than a binding of our own so it follows
     * whatever the player has A and D remapped to. Holding one of these does not disturb the
     * side vanish, which fires on a double tap's rising edge and so ignores a key that is simply
     * held down.
     */
    private static int heldMashStyle(Minecraft mc) {
        if (mc == null || mc.options == null) {
            return net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.MASH_STYLE_ROUTE;
        }
        if (mc.options.keyLeft.isDown()) {
            return net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.MASH_STYLE_PUNCH;
        }
        if (mc.options.keyRight.isDown()) {
            return net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.MASH_STYLE_UPPERCUT;
        }
        return net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.MASH_STYLE_ROUTE;
    }

    /** Shared by left-click and hold-R so the HUD combo chip counts both. */
    private static void fireComboBeat(LocalPlayer player) {
        LivingEntity target = LockOnEvent.getLockedTarget();
        if (target != null && !target.isAlive()) target = null;
        if (target == null) {
            double range = Math.max(6.0, XenoServerClientState.get().chargeAttackRange);
            target = findLookTarget(Minecraft.getInstance(), range);
        }

        int countCap = 99;
        if (comboStep <= 0 || comboTicksLeft <= 0) {
            comboStep = 1;
            if (clientComboEverStarted) {
                clientComboRoute = Math.floorMod(clientComboRoute + 1,
                        Math.max(1, net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.routeCount()));
            } else {
                clientComboRoute = 0;
                clientComboEverStarted = true;
            }
            clientComboAirborne = !player.onGround();
        } else {
            comboStep = Math.min(countCap, comboStep + 1);
        }
        comboTicksLeft = COMBO_WINDOW_TICKS;

        faceBodyToCrosshair(player);
        // Holding W through a beat is the dragon dash: the hit launches, and the fighter goes with
        // it. A tap still only launches. Separate from the retired standalone hold-W chase gesture
        // — this one is only read while a string is live, so walking never triggers it.
        boolean pursue = XenoClientConfig.bt3ChaseDashClient && XenoServerClientState.chase()
                && Minecraft.getInstance().options.keyUp.isDown();
        int verticalBias = (comboLauncherArmed || pursue) ? 1 : 0;
        comboLauncherArmed = false;
        int mashStyle = verticalBias > 0 ? net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.MASH_STYLE_ROUTE
                : heldMashStyle(Minecraft.getInstance());
        // Same precedence the server runs in handleCombo, off the same pure functions, so the
        // prediction below matches the confirmation and nothing is played twice.
        net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent styled =
                net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.styled(mashStyle, comboStep);
        if (styled != null) {
            lastMashIntent = styled;
        } else {
            net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent authored =
                    net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.resolve(
                            clientComboRoute, comboStep, false, clientComboAirborne);
            lastMashIntent = net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.overlay(
                    authored, verticalBias);
        }
        if (XenoClientConfig.bt3CombatAnims) {
            net.bullettrain.xenopixelsmod.client.combat.anim.Bt3AnimIntentClient.predictLocal(
                    player, lastMashIntent);
        }
        int tid = target != null ? target.getId() : -1;
        send(new Bt3CombatPacket(
                Bt3CombatPacket.Action.COMBO_HIT, tid, comboStep, 0, verticalBias, mashStyle));
        clientSparkingMeter = Math.min(100f, clientSparkingMeter
                + Math.max(1f, XenoServerClientState.get().sparkingBuildPerHit * 0.5f));

        // The launcher above opens DragonHoming on the server, and a homing chase skips both the
        // near-range guard and the success roll — so this reads as one continuous move rather than
        // a hit followed by a coin flip.
        if (pursue && target != null && moveCooldown <= 0) {
            send(new Bt3CombatPacket(
                    Bt3CombatPacket.Action.CHASE_DASH, target.getId(), 0));
            ClientChaseFlightState.request();
            chaseInput = ChaseInput.COMBO_PURSUE;
            startMoveCooldown(Bt3CombatPacket.Action.CHASE_DASH);
        }
    }

    /** Space or a dedicated binding chases directly; W retains tap-then-hold arming. */
    private static void tickChase(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!XenoClientConfig.bt3ChaseDashClient || !XenoServerClientState.chase()) {
            stopClientChase(true);
            chaseWDownLastTick = false;
            return;
        }

        boolean dedicatedDown = CHASE != null && !CHASE.isUnbound() && heldNow(CHASE);
        // Space is DragonMineZ's flight ascend and W is the movement key, so both gestures are off
        // by default. The branches below stay in place behind these switches rather than being
        // deleted, so either can be turned back on and compared in play.
        boolean spaceDown = XenoClientConfig.bt3ChaseSpaceGesture && mc.options.keyJump.isDown();
        boolean wDown = XenoClientConfig.bt3ChaseWGesture && mc.options.keyUp.isDown();
        long now = System.currentTimeMillis();
        noticeChaseNeedsBinding(player, dedicatedDown);

        if (chaseInput != ChaseInput.NONE) {
            boolean sourceDown = switch (chaseInput) {
                case DEDICATED -> dedicatedDown;
                case SPACE -> spaceDown;
                case W -> wDown;
                case COMBO_PURSUE -> mc.options.keyUp.isDown();
                default -> false;
            };
            if (!sourceDown) {
                stopClientChase(true);
            } else if (ClientChaseFlightState.isActive()) {
                LivingEntity locked = LockOnEvent.getLockedTarget();
                if (locked != null && locked.isAlive()) lockRushView(player, locked);
                chaseHoldTicks++;
            }
            chaseWDownLastTick = wDown;
            return;
        }

        if (!wDown && chaseWDownLastTick && chaseHoldTicks > 0
                && chaseHoldTicks <= CHASE_TAP_MAX_TICKS) {
            chaseArmedUntilMs = now + CHASE_ARM_MS;
            player.displayClientMessage(Component.literal("§bChase: hold W"), true);
        }

        ChaseInput requested = dedicatedDown ? ChaseInput.DEDICATED
                : spaceDown ? ChaseInput.SPACE
                : (wDown && !chaseWDownLastTick && now < chaseArmedUntilMs)
                ? ChaseInput.W : ChaseInput.NONE;
        if (wDown) chaseHoldTicks++; else chaseHoldTicks = 0;
        chaseWDownLastTick = wDown;
        if (requested == ChaseInput.NONE) {
            if (!dedicatedDown && !spaceDown && !wDown) chaseNoLockNoticed = false;
            return;
        }

        LivingEntity locked = LockOnEvent.getLockedTarget();
        if (locked == null || !locked.isAlive()) {
            if (!chaseNoLockNoticed) {
                chaseNoLockNoticed = true;
                player.displayClientMessage(Component.literal("§cChase: lock on first"), true);
            }
            return;
        }
        if (!tryMove(mc, locked, Bt3CombatPacket.Action.CHASE_DASH, 0)) {
            // A rejected request must not latch the hold. Space and a dedicated binding stay
            // live and retry on the next tick, so the chase starts the instant the reason
            // clears (move cooldown, range, ki) without releasing and pressing again. W keeps
            // its tap-then-hold arming: its rising-edge gate above means it waits for a new tap.
            return;
        }
        chaseArmedUntilMs = 0;
        chaseInput = requested;
        ClientChaseFlightState.request();
    }

    /** One request per press: the server owns the window, the cooldown and the ki. */
    private static void tickZanzoken(Minecraft mc) {
        if (mc.player == null) return;
        boolean pressed = false;
        while (ZANZOKEN.consumeClick()) {
            pressed = true;
        }
        if (!pressed) return;
        if (!XenoClientConfig.bt3CombatClient || !XenoServerClientState.combat()) return;
        send(new Bt3CombatPacket(Bt3CombatPacket.Action.ZANZOKEN, -1, 0));
    }

    /** One toggle per press: the server owns whether the fighter is currently divided. */
    private static void tickMultiForm(Minecraft mc) {
        if (mc.player == null) return;
        boolean pressed = false;
        while (MULTIFORM.consumeClick()) {
            pressed = true;
        }
        if (!pressed) return;
        if (!XenoClientConfig.bt3CombatClient || !XenoServerClientState.combat()) return;
        send(new Bt3CombatPacket(Bt3CombatPacket.Action.MULTIFORM, -1, 0));
    }

    /** One-shot per session, so turning the gestures off cannot leave chase silently dead. */
    private static boolean chaseBindingNoticed;

    /**
     * With both gestures off and no Chase key bound there is no way to start a chase and nothing
     * on screen saying so. Say it once rather than let the technique look broken.
     */
    private static void noticeChaseNeedsBinding(LocalPlayer player, boolean dedicatedDown) {
        if (chaseBindingNoticed || dedicatedDown || player == null) return;
        if (XenoClientConfig.bt3ChaseSpaceGesture || XenoClientConfig.bt3ChaseWGesture) return;
        if (CHASE == null || !CHASE.isUnbound()) return;
        chaseBindingNoticed = true;
        player.displayClientMessage(
                Component.literal("§7Chase has no key — bind \"Chase\" in Controls"), true);
    }

    private static void stopClientChase(boolean notifyServer) {
        if (notifyServer && (ClientChaseFlightState.isActive() || ClientChaseFlightState.isPending())) {
            send(new Bt3CombatPacket(
                    Bt3CombatPacket.Action.CHASE_STOP, -1, 0));
        }
        ClientChaseFlightState.reset();
        chaseArmedUntilMs = 0;
        chaseWDownLastTick = false;
        chaseInput = ChaseInput.NONE;
        chaseHoldTicks = 0;
        chaseNoLockNoticed = false;
    }

    /**
     * Left click is mash and charged-fist:
     * hold from rest mashes the 13-hit string; tap then press-and-hold charges a punch.
     */
    private static void tickFistKey(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!fistsActive(mc) || clientGuarding) {
            fistHoldTicks = 0;
            holdComboCooldown = 0;
            fistChargeArmedUntilMs = 0;
            return;
        }
        if (TechniqueSlotAssist.isTechniqueBarModifierHeld()
                || TechniqueSlotAssist.isDmzTechniqueCharging(player)
                || chargeMode != ChargeMode.NONE) {
            fistHoldTicks = 0;
            holdComboCooldown = 0;
            return;
        }
        boolean down = leftMouseDown(Minecraft.getInstance());
        boolean canMash = XenoClientConfig.bt3ComboClient && XenoServerClientState.combo();
        boolean canCharge = XenoClientConfig.bt3ChargeAttackClient && XenoServerClientState.chargeAttack();
        long now = System.currentTimeMillis();
        if (down) {
            fistHoldTicks++;
            if (canCharge && now < fistChargeArmedUntilMs && fistHoldTicks == 1) {
                fistChargeArmedUntilMs = 0;
                beginCharge(ChargeMode.FIST);
                holdComboCooldown = 0;
                return;
            }
            if (!canMash) return;
            if (fistHoldTicks <= FIST_TAP_MAX_TICKS) return;
            if (holdComboCooldown > 0) {
                holdComboCooldown--;
                return;
            }
            holdComboCooldown = holdComboIntervalTicks();
            fireComboBeat(player);
            return;
        }
        if (fistHoldTicks > 0 && fistHoldTicks <= FIST_TAP_MAX_TICKS && (canMash || canCharge)) {
            if (canMash) fireComboBeat(player);
            if (canCharge) {
                fistChargeArmedUntilMs = now + FIST_CHARGE_ARM_MS;
                player.displayClientMessage(Component.literal("§cCharge punch: hold Left Click"), true);
            }
        }
        fistHoldTicks = 0;
        holdComboCooldown = 0;
    }

    /**
     * Do Xeno fists run for this click? Independent of what the crosshair is on, so a punch swings
     * at a wall and a charge survives the player glancing at the ground. Use
     * {@link #ownsFistInput} instead when deciding whether to suppress the native attack.
     */
    public static boolean fistsActive(Minecraft mc) {
        LocalPlayer player = mc == null ? null : mc.player;
        if (player == null) return false;
        return net.bullettrain.xenopixelsmod.combat.FistInputPolicy.fistsActive(
                handsFreeForFists(mc),
                XenoClientConfig.bt3CombatClient && XenoServerClientState.combat(),
                XenoClientConfig.bt3ComboClient && XenoServerClientState.combo(),
                XenoClientConfig.bt3ChargeAttackClient && XenoServerClientState.chargeAttack(),
                mc.screen == null && player.isAlive() && !player.isSpectator()
                        && !(player.getVehicle() instanceof net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity)
                        && !TechniqueSlotAssist.isTechniqueBarModifierHeld()
                        && !TechniqueSlotAssist.isDmzTechniqueCharging(player));
    }

    /**
     * Does Xeno own the native attack exclusively? Only when the crosshair is not on a block —
     * there the native path just starts and continues a dig, so mining and the punch share the
     * click the way vanilla left click already serves both.
     */
    public static boolean ownsFistInput(Minecraft mc) {
        return fistsActive(mc) && !isBlockTarget(mc);
    }

    /**
     * Does Xeno take the click away from the native attack entirely this tick?
     *
     * <p>Deliberately includes a block target. DragonMineZ's own {@code startAttack} hook only
     * yields to mining when nothing is in front of the block, so leaving a block target to the
     * native path lets DMZ's punch and an Xeno fist both land on one click whenever a mob is
     * standing against a wall. Xeno takes every case and reissues the dig through
     * {@link #digWhileSuppressed}.
     */
    public static boolean suppressesNativeAttack(Minecraft mc) {
        return fistsActive(mc) || chargeMode != ChargeMode.NONE
                || (clientGuarding && handsFreeForFists(mc));
    }

    /**
     * Xeno only has a claim on the click with both hands empty and no ki weapon out — the same
     * rule DragonMineZ applies to its own block, which never raises with an item held.
     *
     * <p>Guard itself still engages with something in hand, so blocks can be placed while guarding,
     * but it stops suppressing the attack there. Xeno has no fist to throw with an item held, so
     * suppressing would just mean the swing goes nowhere.
     */
    private static boolean handsFreeForFists(Minecraft mc) {
        LocalPlayer player = mc == null ? null : mc.player;
        if (player == null) return false;
        return net.bullettrain.xenopixelsmod.combat.FistInputPolicy.emptyHands(
                player.getMainHandItem().isEmpty(), player.getOffhandItem().isEmpty(),
                PlayerAttackHelper.isKiWeaponActive(player));
    }

    /**
     * Should the vanilla attack itself be cancelled for this click?
     *
     * <p>Only away from a block. DragonMineZ's melee is already stood down by
     * {@link net.bullettrain.xenopixelsmod.mixin.client.PlayerAttackHelperGateMixin}, so the
     * remaining reason to cancel is Minecraft's own {@code gameMode.attack} on an entity, which
     * would double up with an Xeno fist. On a block the native path is left completely alone and
     * mining behaves exactly like vanilla, tools included.
     */
    public static boolean suppressesNativeMelee(Minecraft mc) {
        return suppressesNativeAttack(mc) && !isBlockTarget(mc);
    }

    /**
     * Reissues the vanilla dig that suppressing the attack would otherwise have thrown away, so a
     * punch, a held charge and a raised guard all still break blocks exactly like normal left
     * click. Mirrors what {@code Minecraft.startAttack} and {@code Minecraft.continueAttack} do
     * for a block hit result, and nothing else — no entity is ever damaged from here.
     */
    /** True once an Xeno-driven dig is in progress, so the next tick continues it. */
    private static boolean xenoDigging;

    /**
     * Mines from Xeno's own punch binding when Minecraft's Attack key cannot do it.
     *
     * <p>{@code Minecraft.startAttack} and {@code continueAttack} are only ever called while
     * {@code options.keyAttack} is down, so with Attack unbound — or bound to some other key —
     * {@link #digWhileSuppressed} would never be reached from the mixin and a punch would stop
     * breaking blocks. This drives the same lifecycle from the Xeno binding instead, and stands
     * down whenever the vanilla key is the one being held so a dig can never be issued twice.
     */
    private static void tickXenoDrivenDig(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null || mc.gameMode == null) return;
        boolean vanillaDriving = heldNow(mc.options.keyAttack);
        boolean xenoHeld = !vanillaDriving && leftMouseDown(mc) && suppressesNativeAttack(mc);
        if (xenoHeld) {
            digWhileSuppressed(mc, xenoDigging);
            xenoDigging = true;
        } else if (xenoDigging) {
            xenoDigging = false;
            mc.gameMode.stopDestroyBlock();
        }
    }

    public static void digWhileSuppressed(Minecraft mc, boolean continuing) {
        LocalPlayer player = mc == null ? null : mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) return;
        if (player.isUsingItem() || !isBlockTarget(mc)
                || !(mc.hitResult instanceof BlockHitResult hit)) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (mc.level.getBlockState(pos).isAir()) {
            mc.gameMode.stopDestroyBlock();
            return;
        }
        boolean progressed = continuing
                ? mc.gameMode.continueDestroyBlock(pos, hit.getDirection())
                : mc.gameMode.startDestroyBlock(pos, hit.getDirection());
        if (progressed && continuing) {
            mc.particleEngine.addBlockHitEffects(pos, hit);
        }
    }

    private static boolean isBlockTarget(Minecraft mc) {
        return mc != null && mc.hitResult != null
                && mc.hitResult.getType() == HitResult.Type.BLOCK;
    }

    static boolean isLaunched(LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        if (!target.onGround()) return true;
        Vec3 d = target.getDeltaMovement();
        return d.y > 0.08 || d.horizontalDistanceSqr() > 0.0064;
    }

    private static void tickCharge(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (chargeMode == ChargeMode.FIST && !fistsActive(mc)) {
            resetCharge();
            return;
        }
        boolean fistDown = leftMouseDown(mc) && fistsActive(mc) && !clientGuarding;
        boolean kickDown = heldNow(CHARGE_KICK);
        boolean dragonDown = heldNow(DRAGON_DASH);

        boolean canCharge = XenoClientConfig.bt3ChargeAttackClient && XenoServerClientState.chargeAttack();
        boolean canDragon = XenoClientConfig.bt3DragonDashClient && XenoServerClientState.dragonDash();

        // Never steal input while the player is on the KI technique bar (Alt/Ctrl+1-4)
        // or already charging a DMZ technique — R is also DMZ dash and fist-charge.
        boolean techBarOpen = TechniqueSlotAssist.isTechniqueBarModifierHeld();
        boolean dmzKiCharging = TechniqueSlotAssist.isDmzTechniqueCharging(player);
        if (techBarOpen || dmzKiCharging) {
            if (chargeMode != ChargeMode.NONE) {
                // Abort our charge cleanly so DMZ technique release owns the moment
                resetCharge();
            }
            fistWasDown = fistDown;
            kickWasDown = kickDown;
            dragonWasDown = dragonDown;
            return;
        }

        // Start charge on press. Dragon = lock-on only. Kick = no target required.
        // Charged fist is tap-R then hold-R — see tickFistKey.
        if (chargeMode == ChargeMode.NONE) {
            LivingEntity locked = LockOnEvent.getLockedTarget();
            boolean hasLock = locked != null && locked.isAlive();
            if (canDragon && dragonDown && !dragonWasDown) {
                if (hasLock) {
                    beginCharge(ChargeMode.DRAGON);
                } else if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§7Dragon dash: lock on first"), true);
                }
            } else if (canCharge && kickDown && !kickWasDown) {
                beginCharge(ChargeMode.KICK);
            }
        }

        if (chargeMode != ChargeMode.NONE) {
            boolean stillHeld = switch (chargeMode) {
                case FIST -> fistDown && canCharge;
                case KICK -> kickDown && canCharge;
                case DRAGON -> dragonDown && canDragon;
                default -> false;
            };

            if (!stillHeld) {
                releaseCharge(mc);
            } else {
                int max = Math.max(1, XenoServerClientState.get().chargeMaxTicks);
                if (chargeTicks < max) {
                    chargeTicks++;
                    // Soft client stamina preview drain message only — server spends on release
                    if (chargeTicks % 10 == 0) {
                        DmzClientStats.Snapshot snap = DmzClientStats.read(player);
                        float hold = XenoServerClientState.get().chargeHoldStaminaPerSec;
                        if (snap.present && snap.stamina < hold) {
                            player.displayClientMessage(Component.literal("§eLow stamina"), true);
                        }
                    }
                }
                if (chargeTicks >= max && !chargeFullyGlowed) {
                    chargeFullyGlowed = true;
                    if (XenoClientConfig.bt3CombatSfx) {
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(
                                SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 0.85f));
                    }
                    player.displayClientMessage(Component.literal(
                            chargeMode == ChargeMode.DRAGON ? "§6DRAGON DASH READY"
                                    : (chargeMode == ChargeMode.KICK ? "§dKICK CHARGED" : "§cFIST CHARGED")), true);
                }
            }
        }

        fistWasDown = fistDown;
        kickWasDown = kickDown;
        dragonWasDown = dragonDown;
    }

    private static void beginCharge(ChargeMode mode) {
        chargeMode = mode;
        chargeTicks = 0;
        chargeFullyGlowed = false;

        DmzAnimHelper.ChargeStyle style = toStyle(mode, false);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            DmzAnimHelperClient.playLocalChargeStart(mc.player, style);
        }
        send(new ChargeAnimPacket(ChargeAnimPacket.Phase.START, style));
    }

    private static DmzAnimHelper.ChargeStyle toStyle(ChargeMode mode, boolean fullyCharged) {
        return switch (mode) {
            case KICK -> DmzAnimHelper.ChargeStyle.KICK;
            case DRAGON -> DmzAnimHelper.ChargeStyle.DRAGON;
            case FIST -> fullyCharged ? DmzAnimHelper.ChargeStyle.FIST_HEAVY : DmzAnimHelper.ChargeStyle.FIST_LIGHT;
            default -> DmzAnimHelper.ChargeStyle.FIST_LIGHT;
        };
    }

    private static void releaseCharge(Minecraft mc) {
        if (chargeMode == ChargeMode.NONE) return;
        LocalPlayer player = mc.player;
        if (player == null) {
            resetCharge();
            return;
        }

        float progress = getChargeProgress();
        int percent = Math.round(progress * 100f);
        boolean full = percent >= 95;
        ChargeMode mode = chargeMode;
        DmzAnimHelper.ChargeStyle style = toStyle(mode, full);

        // Stop hold pose for everyone
        DmzAnimHelperClient.playLocalChargeStop(player);
        send(new ChargeAnimPacket(ChargeAnimPacket.Phase.CANCEL, style));
        resetCharge();

        if (percent < 20) {
            return; // too weak, cancel (pose already stopped)
        }

        LivingEntity target = LockOnEvent.getLockedTarget();
        if (target != null && !target.isAlive()) target = null;

        // Dragon: lock-on only (no freelook fallback)
        if (mode == ChargeMode.DRAGON) {
            if (target == null) {
                player.displayClientMessage(Component.literal("§7Dragon dash: lock on first"), true);
                return;
            }
        } else if (mode == ChargeMode.FIST || mode == ChargeMode.KICK) {
            // Fist/kick: lock-on preferred, else freelook — but empty space is fine.
            if (target == null) {
                target = findLookTarget(mc, XenoServerClientState.get().chargeAttackRange);
            }
        }

        // W/S while charging kick → vertical launch bias (+1 up / -1 down)
        int verticalBias = 0;
        if (mode == ChargeMode.KICK) {
            if (mc.options.keyUp.isDown()) verticalBias = 1;
            else if (mc.options.keyDown.isDown()) verticalBias = -1;
            if (verticalBias == 0) DragonHomingClient.close();
            else DragonHomingClient.open();
        }

        DmzClientStats.Snapshot snap = DmzClientStats.read(player);
        XenoServerConfig.Data srv = XenoServerClientState.get();
        float needStam;
        if (mode == ChargeMode.DRAGON) {
            needStam = srv.dragonDashStaminaCost * (0.5f + 0.5f * progress);
        } else if (mode == ChargeMode.KICK) {
            float baseKick = srv.kickChargeStaminaCost > 0 ? srv.kickChargeStaminaCost
                    : (srv.chargeStaminaCost > 0 ? srv.chargeStaminaCost : 18f);
            needStam = baseKick * (0.45f + 0.55f * progress);
            if (verticalBias != 0) needStam += srv.kickVerticalExtraStamina * (0.5f + 0.5f * progress);
        } else {
            float baseFist = srv.fistChargeStaminaCost > 0 ? srv.fistChargeStaminaCost
                    : (srv.chargeStaminaCost > 0 ? srv.chargeStaminaCost : 18f);
            needStam = baseFist * (0.45f + 0.55f * progress);
        }
        if (snap.present && snap.stamina < needStam * 0.85f) {
            player.displayClientMessage(Component.literal("§eNot enough stamina"), true);
            return;
        }

        Bt3CombatPacket.Action action = switch (mode) {
            case FIST -> Bt3CombatPacket.Action.CHARGE_FIST;
            case KICK -> Bt3CombatPacket.Action.CHARGE_KICK;
            case DRAGON -> Bt3CombatPacket.Action.DRAGON_DASH;
            default -> null;
        };
        if (action == null) return;

        // Local DMZ fire / kick / punch chain + particles (server also broadcasts)
        DmzAnimHelperClient.playLocalChargeRelease(player, style, full, verticalBias);

        // Client movement prediction — fist/kick stay planted (no step-in toward target)
        if (mode == ChargeMode.DRAGON && target != null) {
            if (XenoClientConfig.bt3CombatSfx) playLocalIt(mc, true);
            Vec3 land = Bt3CombatPacket.chaseLanding(player, target);
            player.setPos(land.x, land.y, land.z);
            player.setDeltaMovement(Vec3.ZERO);
            face(player, target);
            if (XenoClientConfig.bt3CombatSfx) playLocalIt(mc, false);
        }
        // KICK / FIST: intentionally no setDeltaMovement toward target

        int tid = target != null ? target.getId() : -1;
        send(new Bt3CombatPacket(action, tid, 0, percent, verticalBias));
        startMoveCooldown(action);
    }

    private static LivingEntity findLookTarget(Minecraft mc, double range) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return null;
        Vec3 eye = player.getEyePosition(1f);
        Vec3 look = player.getLookAngle();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (var e : mc.level.getEntities(player, player.getBoundingBox().inflate(range),
                ent -> ent instanceof LivingEntity le && le.isAlive() && le != player)) {
            if (!(e instanceof LivingEntity living)) continue;
            // Never freelook-target DMZ masters
            try {
                if (net.bullettrain.xenopixelsmod.event.DmzMasterProtection.isDmzMaster(living)) continue;
            } catch (Throwable ignored) {
            }
            Vec3 to = living.getEyePosition(1f).subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 0.5) continue;
            double dot = look.dot(to.normalize());
            if (dot < 0.72) continue;
            double score = dist * (1.5 - dot);
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    /**
     * Every packet this class sends goes through here.
     *
     * <p>{@code PacketDistributor.sendToServer} calls {@code Objects.requireNonNull} on the
     * connection and throws if there is none. Several of these paths run from the client tick's
     * bail-out branch, which fires <em>every tick at the title screen</em> — where there is no
     * connection at all — so an unguarded send there crashes the game before the menu is usable.
     * Dropping the packet is always the right answer: with no server there is no server state to
     * correct.
     */
    private static void send(Object packet) {
        if (Minecraft.getInstance().getConnection() == null) return;
        ModNetwork.CHANNEL.sendToServer(packet);
    }

    private static void resetCharge() {
        if (chargeMode != ChargeMode.NONE) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                send(new ChargeAnimPacket(ChargeAnimPacket.Phase.CANCEL, toStyle(chargeMode, false)));
            }
            if (mc.player != null) {
                DmzAnimHelperClient.playLocalChargeStop(mc.player);
            }
        }
        chargeMode = ChargeMode.NONE;
        chargeTicks = 0;
        chargeFullyGlowed = false;
    }

    private static void captureKeys(Minecraft mc) {
        leftWasDown = mc.options.keyLeft.isDown();
        rightWasDown = mc.options.keyRight.isDown();
        forwardWasDown = mc.options.keyUp.isDown();
        backWasDown = mc.options.keyDown.isDown();
    }

    /** Single W after a charged kick: fly to the launched victim. Server picks the victim. */
    private static void tryDragonHoming(Minecraft mc, LivingEntity locked) {
        if (moveCooldown > 0 || chargeMode != ChargeMode.NONE) return;
        if (!XenoClientConfig.bt3ChaseDashClient || !XenoServerClientState.chase()) return;
        LocalPlayer player = mc.player;
        if (player == null) return;
        DragonHomingClient.close();
        int targetId = locked != null && locked.isAlive() ? locked.getId() : 0;
        if (XenoClientConfig.bt3CombatSfx) playLocalIt(mc, true);
        send(new Bt3CombatPacket(
                Bt3CombatPacket.Action.CHASE_DASH, targetId, 0));
        startMoveCooldown(Bt3CombatPacket.Action.CHASE_DASH);
    }

    /**
     * @param side for VANISH only: -1 left (A), +1 right (D); ignored otherwise
     */
    private static boolean tryMove(Minecraft mc, LivingEntity locked, Bt3CombatPacket.Action action, int side) {
        if (moveCooldown > 0 || chargeMode != ChargeMode.NONE) return false;
        LocalPlayer player = mc.player;
        if (player == null) return false;

        XenoServerConfig.Data srv = XenoServerClientState.get();
        boolean okClient = switch (action) {
            case VANISH -> XenoClientConfig.bt3VanishClient && XenoServerClientState.vanish();
            case CHASE_DASH -> XenoClientConfig.bt3ChaseDashClient && XenoServerClientState.chase();
            case BACKSTEP -> XenoClientConfig.bt3BackstepClient && XenoServerClientState.backstep();
            default -> false;
        };
        if (!okClient) return false;

        double maxRange = switch (action) {
            case VANISH -> srv.vanishMaxRange;
            case CHASE_DASH -> srv.chaseMaxRange;
            case BACKSTEP -> srv.backstepMaxRange;
            default -> 7.0;
        };
        float kiCost = switch (action) {
            case VANISH -> srv.vanishKiCost;
            case CHASE_DASH -> srv.chaseKiCost;
            case BACKSTEP -> srv.backstepKiCost;
            default -> 8f;
        };

        double dist = player.distanceTo(locked);
        if (action == Bt3CombatPacket.Action.CHASE_DASH && dist < 2.5) {
            player.displayClientMessage(Component.literal("§bAlready in range — attack!"), true);
            return false;
        }
        if (maxRange > 0 && dist > maxRange) {
            player.displayClientMessage(Component.literal(
                    "§bGet closer (max " + (int) maxRange + ")"), true);
            return false;
        }

        DmzClientStats.Snapshot snap = DmzClientStats.read(player);
        if (snap.present && snap.energy < kiCost) {
            player.displayClientMessage(Component.literal("§bNot enough KI"), true);
            return false;
        }

        if (XenoClientConfig.bt3CombatSfx) playLocalIt(mc, true);
        applyClientMove(player, locked, action, side);
        if (XenoClientConfig.bt3CombatSfx) playLocalIt(mc, false);

        // comboStep carries vanish side for the server
        int payload = action == Bt3CombatPacket.Action.VANISH ? side : 0;
        send(new Bt3CombatPacket(action, locked.getId(), payload));
        startMoveCooldown(action);
        return true;
    }

    private static void applyClientMove(LocalPlayer player, LivingEntity target,
                                        Bt3CombatPacket.Action action, int side) {
        // Chase is a velocity fly — never snap locally.
        if (action == Bt3CombatPacket.Action.CHASE_DASH) {
            return;
        }
        // Match server dest exactly; hard-stop velocity
        Vec3 dest = switch (action) {
            case VANISH -> Bt3CombatPacket.vanishLanding(player, target, side);
            case CHASE_DASH -> Bt3CombatPacket.chaseLanding(player, target);
            case BACKSTEP -> Bt3CombatPacket.backstepDest(player, target);
            default -> player.position();
        };
        player.setPos(dest.x, dest.y, dest.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.hasImpulse = true;
        player.fallDistance = 0f;
        face(player, target);
    }

    private static void playLocalIt(Minecraft mc, boolean leave) {
        SoundEvent dmz = BuiltInRegistries.SOUND_EVENT.get(
                ResourceLocation.fromNamespaceAndPath("dragonminez", leave ? "evasion1" : "evasion2"));
        SoundEvent sfx = dmz != null ? dmz : SoundEvents.ENDERMAN_TELEPORT;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sfx, leave ? 1.05f : 1.2f, 0.9f));
    }

    private static void face(LocalPlayer player, LivingEntity target) {
        float yaw = yawToward(player, target);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
    }

    /** Match ordinary DMZ punches: body follows camera/crosshair yaw even while locked on. */
    private static void faceBodyToCrosshair(LocalPlayer player) {
        float yaw = player.getYRot();
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
    }

    private static float yawToward(LocalPlayer player, LivingEntity target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }
}
