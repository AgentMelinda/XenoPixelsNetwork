package net.bullettrain.xenopixelsmod.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local client preferences for UI and combat feel.
 * Written to {@code config/xenopixelsmod-client.json}.
 * Combat still requires matching server flags.
 *
 * <p>Not {@code @OnlyIn(CLIENT)}: client mixins reference this class, and Forge would
 * strip it on dedicated servers causing {@code ClassMetadataNotFoundException}.
 * File I/O still only runs from client setup.
 */
public final class XenoClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-client.json");
    /** Version 3 restores Guard while its dedicated key keeps right-click placement vanilla. */
    private static final int CURRENT_CONFIG_VERSION = 3;

    // --- UI ---
    public static boolean xenoHudEnabled = true;
    /** Custom Alt/Ctrl KI technique slot bar + charge meter (replaces DMZ techniquehud). */
    public static boolean techniqueHotbarEnabled = true;
    public static boolean titleScreenButton = true;
    public static boolean pauseScreenButton = true;
    public static boolean xenoMenuEnabled = true;
    public static boolean contentScreensEnabled = true;
    public static boolean joinServerButton = true;
    public static boolean hudEditEnabled = true;
    public static boolean senzuCooldownMessages = true;
    /** XV2-style party/team HP+KI mini bars for nearby scoreboard-team allies. */
    public static boolean partyHudEnabled = true;
    /** Compact BT3 cooldown chip strip (vanish / chase / combo / charge). */
    public static boolean cooldownHudEnabled = true;

    // --- Combat client (prediction / input) ---
    public static boolean bt3CombatClient = true;
    public static boolean bt3ComboClient = true;
    public static boolean bt3VanishClient = true;
    public static boolean bt3ChaseDashClient = true;
    /**
     * Hold Space to chase. Off by default: Space is also DragonMineZ's flight ascend, so every
     * ascent with a lock-on fired a chase attempt, spent ki and rolled the chase-success chance —
     * which is where the stream of "Chase failed" came from. Kept behind a switch rather than
     * deleted so it can be turned back on and compared in play.
     */
    /**
     * Draw copies with the fighter's real DragonMineZ body — hair, body type, race parts, form and
     * aura — instead of a plain player model. Off falls back to the vanilla model, which is the
     * safe path if anything about the DMZ render bridge misbehaves.
     */
    public static boolean cloneDmzAppearance = true;
    public static boolean bt3ChaseSpaceGesture = false;
    /**
     * Double-tap then hold W to chase. Off by default: W is the movement key, so the gesture only
     * worked behind a rising-edge gate and an arming window that existed purely to stop walking
     * forward from launching a chase.
     */
    public static boolean bt3ChaseWGesture = false;
    public static boolean bt3BackstepClient = true;
    public static boolean bt3ChargeAttackClient = true;
    public static boolean bt3DragonDashClient = true;
    public static boolean bt3GuardClient = true;
    public static boolean bt3SuperCounterClient = true;
    public static boolean bt3KiBlastCancelClient = true;
    public static boolean bt3ZBurstClient = true;
    public static boolean bt3LockCycleClient = true;
    /**
     * DMZ Z-lock acquire + persist ignore block occlusion. Off restores stock
     * {@code hasLineOfSight} (androids still keep lock through walls).
     */
    public static boolean lockOnThroughBlocks = true;
    /**
     * Skills delete-X confirm window in milliseconds. 0 deletes on the first click.
     */
    public static int deleteConfirmMs = 3000;
    public static boolean bt3CombatSfx = true;
    public static boolean bt3ChargeGlow = true;
    /** Draw the client-side DMZ-style blue combat attack/hurt boxes. */
    public static boolean bt3CombatHitboxes = false;
    /** Client afterimage trails for vanish-style moves. */
    public static boolean bt3Afterimage = true;
    /** Local DMZ charge/punch/kick animations. */
    public static boolean bt3CombatAnims = true;
    /**
     * Pin the head (and the hair hanging off it) to the body for the combo window instead of
     * letting it track the camera. Off is stock DragonMineZ look, which is the comparison to
     * make if the head or hair ever looks wrong during a string.
     */
    public static boolean bt3MashHeadFollow = true;
    /** Delayed 2nd/3rd punch-kick chain anims. */
    public static boolean bt3KickChainAnims = true;
    /** Crit/spark particles on charge release and impacts. */
    public static boolean bt3CombatParticles = true;
    /**
     * Camera shake on impact. Opt-out because unrequested camera motion is an accessibility
     * problem, not a taste one; turning it off leaves the flash and the world particles, so no
     * information is lost with it.
     */
    public static boolean bt3ScreenShake = true;
    /** Scales the shake. 0 is equivalent to disabling it; above 1 exaggerates it. */
    public static float bt3ScreenShakeStrength = 1.0f;
    /**
     * Allow DMZ {@code EffectsEvents} camera shake (stagger, form charge, nearby casts)
     * in every camera mode. Off by default. Xeno impact shake is {@link #bt3ScreenShake}.
     */
    public static boolean dmzCameraShake = false;
    /** The tinted vignette flash on impact, on its own switch so it survives disabling shake. */
    public static boolean bt3ImpactFlash = true;
    /** Legacy serialized setting retained for config compatibility; dedicated-key Guard is instant. */
    public static int bt3GuardHoldTicks = 5;
    /**
     * Abbreviate HUD figures as k/M. Off shows the exact number, which is what a player
     * checking whether a heal landed actually wants.
     */
    public static boolean hudCompactNumbers = true;
    /** Draw current/max values inside the HP, KI and stamina lanes on the modern panel. */
    public static boolean hudBarNumbers = true;
    /** Report the held fire key so a beam can be sustained. Off opts out entirely. */
    public static boolean beamSurgeClient = true;
    /** Actionbar readout of the surge input chain: key held, wave owned, packets sent. */
    public static boolean beamSurgeDebug = false;
    /** Radial speed lines while moving fast. Camera-adjacent, so it gets its own switch. */
    public static boolean speedLinesEnabled = true;
    /** Warm vignette while sparking. Separate switch: it is on screen for seconds. */
    public static boolean sparkingTintEnabled = true;
    /** Hide Alt/Ctrl technique hotbar while chat/command screen is open. */
    public static boolean techniqueHotbarHideInChat = true;
    /**
     * Clamp Create entity scans on Sable ships on this client. Independent of
     * {@code /xenoperf set sablecull} (server MSPT). Off restores the pose-exploded
     * query and will tank FPS.
     */
    public static boolean sableContraptionCullClient = true;

    // --- Seated flight (pilot seat) ---
    /** Flight HUD while seated in a pilot seat: throttle, flaps, speed, stall. */
    public static boolean flightHudEnabled = true;
    /**
     * Mouse-aim flight: the ship flies toward wherever you are looking, and the attitude
     * stabilizer supplies the lag. Off (the default) leaves yaw/pitch/roll to the keyboard sticks,
     * driven directly by the control surfaces with the arcade rate damper — no mouse capture.
     */
    public static boolean flightMouseAim = false;
    /** Invert commanded pitch relative to the pitch stick (W/S). */
    public static boolean flightInvertPitch = false;
    /**
     * Suppress vanilla's drop-item, pick-block and open-inventory actions while seated in a
     * pilot seat, so the cockpit bindings that share those keys (roll on Q/E, lock on middle
     * mouse) actually reach the flight controls instead of the vanilla action firing alongside.
     *
     * <p>Turn this off to keep the vanilla behaviour; roll and lock then need rebinding to keys
     * of your own. Chat is never suppressed by this, so no cockpit binding sits on T.
     */
    public static boolean flightSuppressVanillaKeysInSeat = true;
    /**
     * Mouse-aim sensitivity: the maximum rate, in degrees per second, at which the commanded
     * heading may follow the raw look angle. Below 1.0 the heading lags further behind where you
     * look, above it tracks closer to instantly — this is deliberately a rate cap on the
     * <i>setpoint</i>, not a scale on look-angle magnitude, since mouse-aim already reads an
     * absolute angle (vanilla's own sensitivity slider governs how fast that angle itself
     * changes). Keeps the "how twitchy does the plane feel" knob meaningful without decoupling
     * the reticle from the camera.
     */
    public static float flightMouseSensitivityX = 1.0f;
    /**
     * The same knob for the pitch axis. Split from the heading axis because the two are not
     * interchangeable: heading wraps and has unlimited travel, while pitch has 89 usable degrees
     * each way, so a rate that feels right for one is usually too fast or too slow for the other.
     */
    public static float flightMouseSensitivityY = 1.0f;
    /** Look-angle change this tick below which the commanded heading does not move at all. */
    public static float flightMouseDeadzoneDeg = 0.3f;
    /** Shapes how the heading rate-limit above scales with how far off-boresight you are looking. */
    public static String flightMouseCurve = "LINEAR";
    /** Roll rate in degrees per second at full A/D deflection. Roll wraps, so this is what sets
     * how long a full 360 barrel roll takes. */
    public static float flightRollRateDegPerSec = 90.0f;
    /** Rudder authority in degrees per second while Q or E is held. */
    public static float flightYawRateDegPerSec = 60.0f;
    /** Pitch rate in degrees per second at full W/S deflection, when mouse-aim is off. */
    public static float flightPitchRateDegPerSec = 45.0f;
    /**
     * Ease the wings back to level once the roll stick is released, the way an arcade flight
     * model does. Off leaves the aircraft wherever it was rolled to, which is what a pilot flying
     * a manual model wants; Center Controls levels it either way.
     */
    public static boolean flightAutoLevel = true;
    /** How fast auto-level rolls back toward wings-level, in degrees per second. */
    public static float flightAutoLevelRateDegPerSec = 45.0f;
    /** How fast a WASD/QE control stick springs toward the held direction and decays back on
     * release, in units per second (0..1). Lower = softer onset (a tap is a smaller nudge). */
    public static float flightStickRampPerSec = 4.0f;
    /** How fast holding the zoom-in/zoom-out keys ({@code +}/{@code -} by default) moves Sable's
     * own sub-level-view camera zoom, in the same units per second that its scroll-to-zoom uses
     * per notch. Only active while the camera is in that third-person view (see
     * {@code XenoFlightControls#THIRD_PERSON_TOGGLE}). A starting guess, tune to taste. */
    public static float flightZoomKeyRatePerSec = 4.0f;
    /** How fast the throttle keys sweep the full range, in fractions per second. */
    /** Throttle fraction added or removed per scroll-wheel click while seated. */
    public static float flightThrottleScrollStep = 0.05f;
    /** Throttle fraction per second while a Throttle Up/Down key is held (Controls-menu alternative to scroll). */
    public static float flightThrottleKeyRatePerSec = 0.6f;

    // ---- Seat crosshair / lock-on HUD ----
    public static boolean crosshairEnabled = true;
    public static float crosshairSize = 10.0f;
    public static float crosshairThickness = 1.5f;
    public static float crosshairOpacity = 0.9f;
    public static int crosshairColorNormal = 0xFFE8F4FF;
    public static int crosshairColorLocking = 0xFFFFC94D;
    public static int crosshairColorLocked = 0xFFFF5D5D;
    public static int crosshairColorInvalid = 0xFF808080;
    public static int crosshairColorLead = 0xFF7BE58C;
    public static boolean showTargetName = true;
    public static boolean showTargetDistance = true;
    public static boolean showLockProgress = true;
    public static boolean showLeadMarker = true;
    public static boolean showOffscreenArrow = true;
    /**
     * Projectile speed (blocks/s) the client-side lead marker assumes. Purely a display aid — it
     * changes only where the marker is drawn, never whether a shot actually lands, which the
     * server alone decides at hit time. Tune this to whatever attack the pilot is using.
     */
    public static float leadAssistProjectileSpeed = 60.0f;
    /** Gravity used only by the visual lead marker, in blocks per second squared. */
    public static float leadAssistGravity = 9.8f;
    /** Fraction of the pilot's current velocity inherited by the visual projectile model. */
    public static float leadAssistShooterVelocityInheritance = 1.0f;

    private XenoClientConfig() {}

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;
            boolean migrateGuardDefault = data.configVersion < CURRENT_CONFIG_VERSION;
            apply(data);
            if (migrateGuardDefault) {
                // Guard is active again; its B binding no longer intercepts vanilla Use/place.
                bt3GuardClient = true;
                save();
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load client config", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(snapshot(), writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save client config", e);
        }
    }

    public static Data snapshot() {
        Data d = new Data();
        d.configVersion = CURRENT_CONFIG_VERSION;
        d.xenoHudEnabled = xenoHudEnabled;
        d.techniqueHotbarEnabled = techniqueHotbarEnabled;
        d.titleScreenButton = titleScreenButton;
        d.pauseScreenButton = pauseScreenButton;
        d.xenoMenuEnabled = xenoMenuEnabled;
        d.contentScreensEnabled = contentScreensEnabled;
        d.joinServerButton = joinServerButton;
        d.hudEditEnabled = hudEditEnabled;
        d.senzuCooldownMessages = senzuCooldownMessages;
        d.partyHudEnabled = partyHudEnabled;
        d.cooldownHudEnabled = cooldownHudEnabled;
        d.bt3CombatClient = bt3CombatClient;
        d.bt3ComboClient = bt3ComboClient;
        d.bt3VanishClient = bt3VanishClient;
        d.bt3ChaseDashClient = bt3ChaseDashClient;
        d.cloneDmzAppearance = cloneDmzAppearance;
        d.bt3ChaseSpaceGesture = bt3ChaseSpaceGesture;
        d.bt3ChaseWGesture = bt3ChaseWGesture;
        d.bt3BackstepClient = bt3BackstepClient;
        d.bt3ChargeAttackClient = bt3ChargeAttackClient;
        d.bt3DragonDashClient = bt3DragonDashClient;
        d.bt3GuardClient = bt3GuardClient;
        d.bt3SuperCounterClient = bt3SuperCounterClient;
        d.bt3KiBlastCancelClient = bt3KiBlastCancelClient;
        d.bt3ZBurstClient = bt3ZBurstClient;
        d.bt3LockCycleClient = bt3LockCycleClient;
        d.lockOnThroughBlocks = lockOnThroughBlocks;
        d.deleteConfirmMs = deleteConfirmMs;
        d.bt3CombatSfx = bt3CombatSfx;
        d.bt3ChargeGlow = bt3ChargeGlow;
        d.bt3Afterimage = bt3Afterimage;
        d.bt3CombatAnims = bt3CombatAnims;
        d.bt3MashHeadFollow = bt3MashHeadFollow;
        d.bt3KickChainAnims = bt3KickChainAnims;
        d.bt3CombatParticles = bt3CombatParticles;
        d.bt3ScreenShake = bt3ScreenShake;
        d.bt3ScreenShakeStrength = bt3ScreenShakeStrength;
        d.dmzCameraShake = dmzCameraShake;
        d.bt3ImpactFlash = bt3ImpactFlash;
        d.bt3GuardHoldTicks = bt3GuardHoldTicks;
        d.hudCompactNumbers = hudCompactNumbers;
        d.hudBarNumbers = hudBarNumbers;
        d.beamSurgeClient = beamSurgeClient;
        d.beamSurgeDebug = beamSurgeDebug;
        d.speedLinesEnabled = speedLinesEnabled;
        d.sparkingTintEnabled = sparkingTintEnabled;
        d.techniqueHotbarHideInChat = techniqueHotbarHideInChat;
        d.sableContraptionCullClient = sableContraptionCullClient;
        d.flightHudEnabled = flightHudEnabled;
        d.flightMouseAim = flightMouseAim;
        d.flightInvertPitch = flightInvertPitch;
        d.flightSuppressVanillaKeysInSeat = flightSuppressVanillaKeysInSeat;
        d.flightMouseSensitivityX = flightMouseSensitivityX;
        d.flightMouseSensitivityY = flightMouseSensitivityY;
        d.flightMouseDeadzoneDeg = flightMouseDeadzoneDeg;
        d.flightMouseCurve = flightMouseCurve;
        d.flightRollRateDegPerSec = flightRollRateDegPerSec;
        d.flightYawRateDegPerSec = flightYawRateDegPerSec;
        d.flightPitchRateDegPerSec = flightPitchRateDegPerSec;
        d.flightAutoLevel = flightAutoLevel;
        d.flightAutoLevelRateDegPerSec = flightAutoLevelRateDegPerSec;
        d.flightStickRampPerSec = flightStickRampPerSec;
        d.flightZoomKeyRatePerSec = flightZoomKeyRatePerSec;
        d.crosshairEnabled = crosshairEnabled;
        d.crosshairSize = crosshairSize;
        d.crosshairThickness = crosshairThickness;
        d.crosshairOpacity = crosshairOpacity;
        d.crosshairColorNormal = crosshairColorNormal;
        d.crosshairColorLocking = crosshairColorLocking;
        d.crosshairColorLocked = crosshairColorLocked;
        d.crosshairColorInvalid = crosshairColorInvalid;
        d.crosshairColorLead = crosshairColorLead;
        d.showTargetName = showTargetName;
        d.showTargetDistance = showTargetDistance;
        d.showLockProgress = showLockProgress;
        d.showLeadMarker = showLeadMarker;
        d.showOffscreenArrow = showOffscreenArrow;
        d.leadAssistProjectileSpeed = leadAssistProjectileSpeed;
        d.leadAssistGravity = leadAssistGravity;
        d.leadAssistShooterVelocityInheritance = leadAssistShooterVelocityInheritance;
        d.flightThrottleScrollStep = flightThrottleScrollStep;
        d.flightThrottleKeyRatePerSec = flightThrottleKeyRatePerSec;
        return d;
    }

    public static void apply(Data d) {
        if (d == null) return;
        xenoHudEnabled = d.xenoHudEnabled;
        techniqueHotbarEnabled = d.techniqueHotbarEnabled;
        titleScreenButton = d.titleScreenButton;
        pauseScreenButton = d.pauseScreenButton;
        xenoMenuEnabled = d.xenoMenuEnabled;
        contentScreensEnabled = d.contentScreensEnabled;
        joinServerButton = d.joinServerButton;
        hudEditEnabled = d.hudEditEnabled;
        senzuCooldownMessages = d.senzuCooldownMessages;
        partyHudEnabled = d.partyHudEnabled;
        cooldownHudEnabled = d.cooldownHudEnabled;
        bt3CombatClient = d.bt3CombatClient;
        bt3ComboClient = d.bt3ComboClient;
        bt3VanishClient = d.bt3VanishClient;
        bt3ChaseDashClient = d.bt3ChaseDashClient;
        cloneDmzAppearance = d.cloneDmzAppearance;
        bt3ChaseSpaceGesture = d.bt3ChaseSpaceGesture;
        bt3ChaseWGesture = d.bt3ChaseWGesture;
        bt3BackstepClient = d.bt3BackstepClient;
        bt3ChargeAttackClient = d.bt3ChargeAttackClient;
        bt3DragonDashClient = d.bt3DragonDashClient;
        bt3GuardClient = d.bt3GuardClient;
        bt3SuperCounterClient = d.bt3SuperCounterClient;
        bt3KiBlastCancelClient = d.bt3KiBlastCancelClient;
        bt3ZBurstClient = d.bt3ZBurstClient;
        bt3LockCycleClient = d.bt3LockCycleClient;
        lockOnThroughBlocks = d.lockOnThroughBlocks == null || d.lockOnThroughBlocks;
        deleteConfirmMs = d.deleteConfirmMs == null ? 3000 : Math.max(0, Math.min(30_000, d.deleteConfirmMs));
        bt3CombatSfx = d.bt3CombatSfx;
        bt3ChargeGlow = d.bt3ChargeGlow;
        bt3Afterimage = d.bt3Afterimage;
        bt3CombatAnims = d.bt3CombatAnims;
        bt3MashHeadFollow = d.bt3MashHeadFollow;
        bt3KickChainAnims = d.bt3KickChainAnims;
        bt3CombatParticles = d.bt3CombatParticles;
        bt3ScreenShake = d.bt3ScreenShake;
        bt3ScreenShakeStrength = d.bt3ScreenShakeStrength;
        dmzCameraShake = d.dmzCameraShake;
        bt3ImpactFlash = d.bt3ImpactFlash;
        bt3GuardHoldTicks = Math.max(0, d.bt3GuardHoldTicks);
        hudCompactNumbers = d.hudCompactNumbers;
        hudBarNumbers = d.hudBarNumbers;
        beamSurgeClient = d.beamSurgeClient;
        beamSurgeDebug = d.beamSurgeDebug;
        speedLinesEnabled = d.speedLinesEnabled;
        sparkingTintEnabled = d.sparkingTintEnabled;
        techniqueHotbarHideInChat = d.techniqueHotbarHideInChat;
        sableContraptionCullClient = d.sableContraptionCullClient == null || d.sableContraptionCullClient;
        flightHudEnabled = d.flightHudEnabled == null || d.flightHudEnabled;
        flightMouseAim = d.flightMouseAim != null && d.flightMouseAim;
        flightInvertPitch = d.flightInvertPitch != null && d.flightInvertPitch;
        flightSuppressVanillaKeysInSeat = d.flightSuppressVanillaKeysInSeat == null
                || d.flightSuppressVanillaKeysInSeat;
        flightMouseSensitivityX = d.flightMouseSensitivityX == null ? 1.0f
                : Math.max(0.2f, Math.min(3.0f, d.flightMouseSensitivityX));
        flightMouseSensitivityY = d.flightMouseSensitivityY == null ? 1.0f
                : Math.max(0.2f, Math.min(3.0f, d.flightMouseSensitivityY));
        flightMouseDeadzoneDeg = d.flightMouseDeadzoneDeg == null ? 0.3f
                : Math.max(0.0f, Math.min(10.0f, d.flightMouseDeadzoneDeg));
        flightMouseCurve = net.bullettrain.xenopixelsmod.client.flight.MouseResponseCurve
                .byName(d.flightMouseCurve).name();
        flightRollRateDegPerSec = d.flightRollRateDegPerSec == null ? 90.0f
                : Math.max(5.0f, Math.min(360.0f, d.flightRollRateDegPerSec));
        flightYawRateDegPerSec = d.flightYawRateDegPerSec == null ? 60.0f
                : Math.max(5.0f, Math.min(360.0f, d.flightYawRateDegPerSec));
        flightPitchRateDegPerSec = d.flightPitchRateDegPerSec == null ? 45.0f
                : Math.max(5.0f, Math.min(360.0f, d.flightPitchRateDegPerSec));
        flightAutoLevel = d.flightAutoLevel == null || d.flightAutoLevel;
        flightAutoLevelRateDegPerSec = d.flightAutoLevelRateDegPerSec == null ? 45.0f
                : Math.max(0.0f, Math.min(360.0f, d.flightAutoLevelRateDegPerSec));
        flightStickRampPerSec = d.flightStickRampPerSec == null ? 4.0f
                : Math.max(1.0f, Math.min(20.0f, d.flightStickRampPerSec));
        flightZoomKeyRatePerSec = d.flightZoomKeyRatePerSec == null ? 4.0f
                : Math.max(0.1f, Math.min(50.0f, d.flightZoomKeyRatePerSec));
        crosshairEnabled = d.crosshairEnabled == null || d.crosshairEnabled;
        crosshairSize = d.crosshairSize == null ? 10.0f : Math.max(2.0f, Math.min(64.0f, d.crosshairSize));
        crosshairThickness = d.crosshairThickness == null ? 1.5f
                : Math.max(0.5f, Math.min(8.0f, d.crosshairThickness));
        crosshairOpacity = d.crosshairOpacity == null ? 0.9f : Math.max(0.05f, Math.min(1.0f, d.crosshairOpacity));
        crosshairColorNormal = d.crosshairColorNormal == null ? 0xFFE8F4FF : d.crosshairColorNormal;
        crosshairColorLocking = d.crosshairColorLocking == null ? 0xFFFFC94D : d.crosshairColorLocking;
        crosshairColorLocked = d.crosshairColorLocked == null ? 0xFFFF5D5D : d.crosshairColorLocked;
        crosshairColorInvalid = d.crosshairColorInvalid == null ? 0xFF808080 : d.crosshairColorInvalid;
        crosshairColorLead = d.crosshairColorLead == null ? 0xFF7BE58C : d.crosshairColorLead;
        showTargetName = d.showTargetName == null || d.showTargetName;
        showTargetDistance = d.showTargetDistance == null || d.showTargetDistance;
        showLockProgress = d.showLockProgress == null || d.showLockProgress;
        showLeadMarker = d.showLeadMarker == null || d.showLeadMarker;
        showOffscreenArrow = d.showOffscreenArrow == null || d.showOffscreenArrow;
        leadAssistProjectileSpeed = d.leadAssistProjectileSpeed == null ? 60.0f
                : Math.max(1.0f, Math.min(1000.0f, d.leadAssistProjectileSpeed));
        leadAssistGravity = d.leadAssistGravity == null ? 9.8f
                : Math.max(0.0f, Math.min(100.0f, d.leadAssistGravity));
        leadAssistShooterVelocityInheritance = d.leadAssistShooterVelocityInheritance == null ? 1.0f
                : Math.max(0.0f, Math.min(1.0f, d.leadAssistShooterVelocityInheritance));
        flightThrottleScrollStep = d.flightThrottleScrollStep == null ? 0.05f
                : Math.max(0.01f, Math.min(0.5f, d.flightThrottleScrollStep));
        flightThrottleKeyRatePerSec = d.flightThrottleKeyRatePerSec == null ? 0.6f
                : Math.max(0.05f, Math.min(4.0f, d.flightThrottleKeyRatePerSec));
    }

    public static class Data {
        public int configVersion;
        public boolean xenoHudEnabled = true;
        public boolean techniqueHotbarEnabled = true;
        public boolean titleScreenButton = true;
        public boolean pauseScreenButton = true;
        public boolean xenoMenuEnabled = true;
        public boolean contentScreensEnabled = true;
        public boolean joinServerButton = true;
        public boolean hudEditEnabled = true;
        public boolean senzuCooldownMessages = true;
        public boolean partyHudEnabled = true;
        public boolean cooldownHudEnabled = true;
        public boolean bt3CombatClient = true;
        public boolean bt3ComboClient = true;
        public boolean bt3VanishClient = true;
        public boolean bt3ChaseDashClient = true;
        public boolean cloneDmzAppearance = true;
        public boolean bt3ChaseSpaceGesture = false;
        public boolean bt3ChaseWGesture = false;
        public boolean bt3BackstepClient = true;
        public boolean bt3ChargeAttackClient = true;
        public boolean bt3DragonDashClient = true;
        public boolean bt3GuardClient = true;
        public boolean bt3SuperCounterClient = true;
        public boolean bt3KiBlastCancelClient = true;
        public boolean bt3ZBurstClient = true;
        public boolean bt3LockCycleClient = true;
        public Boolean lockOnThroughBlocks;
        public Integer deleteConfirmMs;
        public boolean bt3CombatSfx = true;
        public boolean bt3ChargeGlow = true;
        public boolean bt3Afterimage = true;
        public boolean bt3CombatAnims = true;
        public boolean bt3MashHeadFollow = true;
        public boolean bt3KickChainAnims = true;
        public boolean bt3CombatParticles = true;
        public boolean bt3ScreenShake = true;
        public float bt3ScreenShakeStrength = 1.0f;
        public boolean dmzCameraShake = false;
        public boolean bt3ImpactFlash = true;
        public int bt3GuardHoldTicks = 5;
        public boolean hudCompactNumbers = true;
        public boolean hudBarNumbers = true;
        public boolean beamSurgeClient = true;
        public boolean beamSurgeDebug = false;
        public boolean speedLinesEnabled = true;
        public boolean sparkingTintEnabled = true;
        public boolean techniqueHotbarHideInChat = true;
        public Boolean sableContraptionCullClient;
        public Boolean flightHudEnabled;
        public Boolean flightMouseAim;
        public Boolean flightInvertPitch;
        public Boolean flightSuppressVanillaKeysInSeat;
        public Float flightMouseSensitivityX;
        public Float flightMouseSensitivityY;
        public Float flightMouseDeadzoneDeg;
        public String flightMouseCurve;
        public Float flightRollRateDegPerSec;
        public Float flightYawRateDegPerSec;
        public Float flightPitchRateDegPerSec;
        public Boolean flightAutoLevel;
        public Float flightAutoLevelRateDegPerSec;
        public Float flightStickRampPerSec;
        public Float flightZoomKeyRatePerSec;
        public Boolean crosshairEnabled;
        public Float crosshairSize;
        public Float crosshairThickness;
        public Float crosshairOpacity;
        public Integer crosshairColorNormal;
        public Integer crosshairColorLocking;
        public Integer crosshairColorLocked;
        public Integer crosshairColorInvalid;
        public Integer crosshairColorLead;
        public Boolean showTargetName;
        public Boolean showTargetDistance;
        public Boolean showLockProgress;
        public Boolean showLeadMarker;
        public Boolean showOffscreenArrow;
        public Float leadAssistProjectileSpeed;
        public Float leadAssistGravity;
        public Float leadAssistShooterVelocityInheritance;
        public Float flightThrottleScrollStep;
        public Float flightThrottleKeyRatePerSec;
    }
}
