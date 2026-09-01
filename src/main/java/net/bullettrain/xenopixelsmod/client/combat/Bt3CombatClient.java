package net.bullettrain.xenopixelsmod.client.combat;

import net.neoforged.fml.common.EventBusSubscriber;

import com.dragonminez.client.events.DMZClientEvent;
import com.dragonminez.client.events.LockOnEvent;
import com.dragonminez.client.util.KeyBinds;
import com.mojang.blaze3d.platform.InputConstants;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.DmzClientStats;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
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
 * Anywhere:      combo (attack), charge fist (R), charge kick (MMB / air OK)
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
    public static final KeyMapping CHARGE_FIST = new KeyMapping(
            "key.xenopixelsmod.bt3_charge_fist", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.xenopixelsmod");
    public static final KeyMapping CHARGE_KICK = new KeyMapping(
            "key.xenopixelsmod.bt3_charge_kick", KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, "key.categories.xenopixelsmod");
    public static final KeyMapping DRAGON_DASH = new KeyMapping(
            "key.xenopixelsmod.bt3_dragon_dash", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.xenopixelsmod");
    /**
     * Hold to guard / block (STM drain).
     * Dedicated B default keeps vanilla right-click exclusively available for Use/place.
     */
    public static final KeyMapping GUARD = new KeyMapping(
            "key.xenopixelsmod.bt3_guard", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.xenopixelsmod");
    /** Mid-combo Z-Burst step-in. */
    public static final KeyMapping Z_BURST = new KeyMapping(
            "key.xenopixelsmod.bt3_zburst", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.xenopixelsmod");
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
    /** Hold with the configured Attack binding to channel Hakai. */
    public static final KeyMapping HAKAI = new KeyMapping(
            "key.xenopixelsmod.bt3_hakai", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, "key.categories.xenopixelsmod");
    /** Hold to home ki on lock-on (Ki Guidance skill). */
    public static final KeyMapping KI_GUIDANCE = new KeyMapping(
            "key.xenopixelsmod.ki_guidance", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_ALT, "key.categories.xenopixelsmod");
    /** Same hold, extra mouse button. */
    public static final KeyMapping KI_GUIDANCE_MOUSE = new KeyMapping(
            "key.xenopixelsmod.ki_guidance_mouse", KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5, "key.categories.xenopixelsmod");

    private static final int COMBO_WINDOW_TICKS = 18;
    private static final int MOVE_COOLDOWN_TICKS = 8;
    private static final long DOUBLE_TAP_MS = 280L;

    private static int comboStep;
    private static int comboTicksLeft;
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
    /** True from the moment a HAKAI_START is sent until Ctrl/click is released (or reset). */
    private static boolean hakaiChanneling;
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
    /** One-shot per session: repair bindings written by the old right-click Guard takeover. */
    private static boolean migratedGuardBinding;
    /** One-shot per session: move Beam Surge off C, which is DMZ's ki_charge. */
    private static boolean migratedBeamSurgeBinding;
    /** One-shot: move Ki Guidance off G onto Left Alt. */
    private static boolean migratedGuidanceBinding;

    @EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(DASH_LEFT);
            event.register(DASH_RIGHT);
            event.register(CHASE);
            event.register(BACKSTEP);
            event.register(CHARGE_FIST);
            event.register(CHARGE_KICK);
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
            while (CHARGE_FIST.consumeClick()) { }
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
            while (KI_GUIDANCE.consumeClick()) { }
            while (KI_GUIDANCE_MOUSE.consumeClick()) { }
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();

            // One-shot: old options.txt often dual-mapped WASD onto these optional alts,
            // which steals key edges from movement / flight and confuses combat input.
            if (!scrubbedDualWasdBinds && mc.player != null) {
                scrubbedDualWasdBinds = true;
                scrubDualWasdBinds(mc);
            }

            if (!migratedGuardBinding && mc.player != null) {
                migratedGuardBinding = true;
                ensureDedicatedGuardKey(mc);
            }

            if (!migratedBeamSurgeBinding && mc.player != null) {
                migratedBeamSurgeBinding = true;
                migrateBeamSurgeKey(mc);
            }

            if (!migratedGuidanceBinding && mc.player != null) {
                migratedGuidanceBinding = true;
                migrateGuidanceKey(mc);
            }

            if (mc.player == null || mc.level == null || mc.screen != null) {
                if (clientGuarding) {
                    clientGuarding = false;
                    ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(
                            Bt3CombatPacket.Action.GUARD, -1, 0));
                }
                clearGuardInputState();
                cancelHakaiIfChanneling();
                resetCharge();
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
                if (clientGuarding) {
                    clientGuarding = false;
                    ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(
                            Bt3CombatPacket.Action.GUARD, -1, 0));
                }
                clearGuardInputState();
                cancelHakaiIfChanneling();
                resetCharge();
                guidanceWasDown = false;
                drainCombatKeys();
                return;
            }
            tickGuidanceHold(mc);
            if (!XenoClientConfig.bt3CombatClient || !XenoServerClientState.combat()) {
                if (clientGuarding) {
                    clientGuarding = false;
                    ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(
                            Bt3CombatPacket.Action.GUARD, -1, 0));
                }
                clearGuardInputState();
                cancelHakaiIfChanneling();
                resetCharge();
                return;
            }

            if (moveCooldown > 0) moveCooldown--;
            if (zBurstCd > 0) zBurstCd--;
            if (kiBlastCd > 0) kiBlastCd--;
            if (sonicCd > 0) sonicCd--;
            if (ultimateCd > 0) ultimateCd--;
            if (counterFlashTicks > 0) counterFlashTicks--;
            if (comboTicksLeft > 0) {
                comboTicksLeft--;
                if (comboTicksLeft == 0) comboStep = 0;
            }

            DmzAnimHelperClient.ClientStrikeChain.tick(mc.player);

            // Charge fist/kick always; dragon only while locked (see tickCharge)
            tickCharge(mc);
            tickGuard(mc);
            tickHakai(mc);
            tickPhase1Keys(mc);

            LivingEntity locked = LockOnEvent.getLockedTarget();
            if (locked != null && !locked.isAlive()) locked = null;

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
                if (forwardDown && !forwardWasDown) {
                    if (lastForwardTapMs > 0 && now - lastForwardTapMs <= DOUBLE_TAP_MS) {
                        // Air rush chain when target is airborne; else normal chase
                        boolean air = locked != null && (!locked.onGround()
                                || locked.getDeltaMovement().y > 0.08
                                || locked.getY() - mc.player.getY() > 1.2);
                        if (air && XenoClientConfig.bt3CombatClient
                                && XenoServerClientState.get().bt3RushChainEnabled
                                && comboStep > 0) {
                            tryRushChain(mc, locked);
                        } else {
                            tryMove(mc, locked, Bt3CombatPacket.Action.CHASE_DASH, 0);
                        }
                        lastForwardTapMs = 0;
                    } else {
                        lastForwardTapMs = now;
                    }
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

        /** Restore the dedicated B binding after builds that copied DMZ Block's RMB binding. */
        private static void ensureDedicatedGuardKey(Minecraft mc) {
            try {
                boolean changed = false;
                if (isRightMouse(GUARD)) {
                    GUARD.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_B));
                    changed = true;
                }
                KeyMapping dmzBlock = KeyBinds.BLOCK_KEY;
                if (dmzBlock != null && isRightMouse(dmzBlock)) {
                    dmzBlock.setKey(InputConstants.UNKNOWN);
                    changed = true;
                }
                if (!changed) return;
                KeyMapping.resetMapping();
                mc.options.save();
                XenoPixelsMod.LOGGER.info("Migrated Guard to B; right-click is reserved for vanilla Use/place");
            } catch (Throwable t) {
                XenoPixelsMod.LOGGER.warn("Failed to migrate Guard key binding: {}", t.toString());
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

        private static boolean isRightMouse(KeyMapping mapping) {
            return mapping != null && !mapping.isUnbound()
                    && mapping.getKey().getType() == InputConstants.Type.MOUSE
                    && mapping.getKey().getValue() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        }

        @SubscribeEvent
        public static void onAttackStart(DMZClientEvent.PlayerAttackStart event) {
            if (chargeMode != ChargeMode.NONE) return;
            if (!XenoClientConfig.bt3CombatClient) return;

            LocalPlayer player = event.getPlayer();
            if (player == null) return;

            // Hakai: its modifier + Attack is a separate move, resolved before combo owns the click.
            if (hakaiModifierHeld()) {
                tryStartHakai(player);
                return;
            }

            // Combo: lock-on OR freelook (not lock-only)
            if (!XenoClientConfig.bt3ComboClient) return;
            if (!XenoServerClientState.combo()) return;

            LivingEntity target = LockOnEvent.getLockedTarget();
            if (target != null && !target.isAlive()) target = null;
            if (target == null) {
                double range = Math.max(6.0, XenoServerClientState.get().chargeAttackRange);
                target = findLookTarget(Minecraft.getInstance(), range);
            }

            // Free-running counter (display can go past 5). maxComboSteps = finisher interval only.
            int finisherEvery = Math.max(1, XenoServerClientState.get().maxComboSteps);
            int countCap = 99;
            if (comboStep <= 0 || comboTicksLeft <= 0) {
                comboStep = 1;
            } else {
                comboStep = Math.min(countCap, comboStep + 1);
            }
            comboTicksLeft = COMBO_WINDOW_TICKS;

            boolean finisher = XenoServerClientState.finisher()
                    && comboStep > 0
                    && comboStep % finisherEvery == 0;
            if (XenoClientConfig.bt3CombatAnims) {
                if (comboStep % 2 == 0) {
                    DmzAnimHelperClient.playLocalComboKick(player, comboStep, finisher);
                } else if (XenoServerClientState.comboPunchesOnly()) {
                    DmzAnimHelperClient.playLocalComboPunch(player, comboStep, finisher);
                }
            }
            // No combo lunge — stay planted while mashing
            int tid = target != null ? target.getId() : -1;
            ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(
                    Bt3CombatPacket.Action.COMBO_HIT, tid, comboStep));
            clientSparkingMeter = Math.min(100f, clientSparkingMeter
                    + Math.max(1f, XenoServerClientState.get().sparkingBuildPerHit * 0.5f));
        }

        @SubscribeEvent
        public static void onClickInput(InputEvent.InteractionKeyMappingTriggered event) {
            // Block vanilla attack while charging so charge release owns the hit
            if (chargeMode != ChargeMode.NONE && event.isAttack()) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
            // Guard: no vanilla attack / use (RMB place) while holding block
            if (clientGuarding && (event.isAttack() || event.isUseItem())) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
            // Hakai: modifier+Attack is redirected in onAttackStart; suppress the vanilla swing too.
            if (event.isAttack() && hakaiModifierHeld()) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
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

    /** Dedicated-key Guard engages immediately; right-click is never deferred or replayed. */
    private static void tickGuard(Minecraft mc) {
        boolean down = GUARD.isDown();
        boolean want = XenoClientConfig.bt3GuardClient && XenoServerClientState.guard()
                && down && chargeMode == ChargeMode.NONE;
        LocalPlayer local = mc.player;
        if (want && !guardWasDown) {
            clientGuarding = true;
            ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(Bt3CombatPacket.Action.GUARD, -1, 1));
            // Local DMZ block pose (base.block) — same family as DMZ hold animations
            if (local != null) {
                DmzAnimHelperClient.playLocalBlockStart(local);
            }
        } else if (!want && guardWasDown && clientGuarding) {
            clientGuarding = false;
            ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(Bt3CombatPacket.Action.GUARD, -1, 0));
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
     * Resolves the current target the same two-tier way every other lock-on-or-freelook move
     * does (lock-on first, crosshair raycast as fallback) and sends the start packet. The
     * server re-derives every gate (config, permission, range, ki, cooldown) itself; this is
     * only target resolution and the initial "start channeling" flag for the release watcher.
     */
    private static void tryStartHakai(LocalPlayer player) {
        LivingEntity target = LockOnEvent.getLockedTarget();
        if (target != null && !target.isAlive()) target = null;
        if (target == null) {
            double range = Math.max(6.0, XenoServerClientState.get().chargeAttackRange);
            target = findLookTarget(Minecraft.getInstance(), range);
        }
        if (target == null) return;
        hakaiChanneling = true;
        ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(
                Bt3CombatPacket.Action.HAKAI_START, target.getId(), 0));
    }

    /** Cancels when either configured input is released; the server enforces everything else. */
    private static void tickHakai(Minecraft mc) {
        if (!hakaiChanneling) return;
        if (mc.player == null || mc.screen != null
                || !hakaiModifierHeld() || !physicallyDown(mc.options.keyAttack)) {
            cancelHakaiIfChanneling();
        }
    }

    private static void cancelHakaiIfChanneling() {
        if (hakaiChanneling) {
            hakaiChanneling = false;
            ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(
                    Bt3CombatPacket.Action.HAKAI_CANCEL, -1, 0));
        }
    }

    private static boolean hakaiModifierHeld() {
        return physicallyDown(HAKAI);
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
            ModNetwork.sendToServer(new GuidanceHoldPacket(targetId, new Vec3(look.x(), look.y(), look.z())));
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
        try {
            return KeyBinds.isPhysicallyDown(mapping);
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
            ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(
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
            ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(
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
            ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(
                    Bt3CombatPacket.Action.ULTIMATE, tid, 0));
            ultimateCd = Math.max(40, XenoServerClientState.get().ultimateCooldownTicks);
            startMoveCooldown(Bt3CombatPacket.Action.ULTIMATE);
            clientSparkingMeter = Math.min(100f, clientSparkingMeter + 12f);
        }

        // Sparking activate
        while (SPARKING.consumeClick()) {
            if (!XenoServerClientState.get().bt3SparkingEnabled) continue;
            ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(
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
        ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(
                Bt3CombatPacket.Action.SONIC_SWAY, -1, side));
        sonicCd = Math.max(8, XenoServerClientState.get().sonicSwayCooldownTicks);
        startMoveCooldown(Bt3CombatPacket.Action.SONIC_SWAY);
        // Local afterimage prediction
        try {
            float yaw = p.getYRot() * ((float) Math.PI / 180F);
            Vec3 right = new Vec3(-Math.sin(yaw + Math.PI / 2), 0, Math.cos(yaw + Math.PI / 2));
            if (side < 0) right = right.scale(-1);
            Vec3 to = from.add(right.scale(2.4));
            net.bullettrain.xenopixelsmod.combat.AfterimageFx.spawnTrailClient(p, from, to, 5);
        } catch (Throwable ignored) {
        }
    }

    private static void tryRushChain(Minecraft mc, LivingEntity locked) {
        if (locked == null || mc.player == null) return;
        if (moveCooldown > 0) return;
        ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(
                Bt3CombatPacket.Action.RUSH_CHAIN, locked.getId(), comboStep));
        startMoveCooldown(Bt3CombatPacket.Action.RUSH_CHAIN);
        comboTicksLeft = COMBO_WINDOW_TICKS;
        clientSparkingMeter = Math.min(100f, clientSparkingMeter + 8f);
        // Local snap prediction
        try {
            Vec3 land = Bt3CombatPacket.chaseLanding(mc.player, locked);
            mc.player.setPos(land.x, locked.getY(), land.z);
            mc.player.setDeltaMovement(Vec3.ZERO);
        } catch (Throwable ignored) {
        }
    }

    private static void tickCharge(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        boolean fistDown = CHARGE_FIST.isDown();
        boolean kickDown = CHARGE_KICK.isDown();
        boolean dragonDown = DRAGON_DASH.isDown();

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

        // Start charge on press
        // Dragon = lock-on only. Fist/kick = freelook OK (kick also air).
        if (chargeMode == ChargeMode.NONE) {
            LivingEntity locked = LockOnEvent.getLockedTarget();
            boolean hasLock = locked != null && locked.isAlive();
            if (canDragon && dragonDown && !dragonWasDown) {
                if (hasLock) {
                    beginCharge(ChargeMode.DRAGON);
                } else if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§7Dragon dash: lock on first"), true);
                }
            } else if (canCharge && fistDown && !fistWasDown) {
                beginCharge(ChargeMode.FIST);
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
        ModNetwork.CHANNEL.sendToServer(new ChargeAnimPacket(ChargeAnimPacket.Phase.START, style));
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
        ModNetwork.CHANNEL.sendToServer(new ChargeAnimPacket(ChargeAnimPacket.Phase.CANCEL, style));
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
        } else if (mode == ChargeMode.FIST) {
            // Punch: lock-on preferred, else freelook target
            if (target == null) {
                target = findLookTarget(mc, XenoServerClientState.get().chargeAttackRange);
            }
            if (target == null) {
                player.displayClientMessage(Component.literal("§bNo target"), true);
                return;
            }
        } else if (mode == ChargeMode.KICK) {
            // Kick: freelook optional (air kick OK). Arm single-W dragon homing after launch.
            if (target == null) {
                target = findLookTarget(mc, XenoServerClientState.get().chargeAttackRange);
            }
            DragonHomingClient.open();
        }

        // W/S while charging kick → vertical launch bias (+1 up / -1 down)
        int verticalBias = 0;
        if (mode == ChargeMode.KICK) {
            if (mc.options.keyUp.isDown()) verticalBias = 1;
            else if (mc.options.keyDown.isDown()) verticalBias = -1;
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
        ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(action, tid, 0, percent, verticalBias));
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

    private static void resetCharge() {
        if (chargeMode != ChargeMode.NONE) {
            Minecraft mc = Minecraft.getInstance();
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
        ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(
                Bt3CombatPacket.Action.CHASE_DASH, targetId, 0));
        startMoveCooldown(Bt3CombatPacket.Action.CHASE_DASH);
    }

    /**
     * @param side for VANISH only: -1 left (A), +1 right (D); ignored otherwise
     */
    private static void tryMove(Minecraft mc, LivingEntity locked, Bt3CombatPacket.Action action, int side) {
        if (moveCooldown > 0 || chargeMode != ChargeMode.NONE) return;
        LocalPlayer player = mc.player;
        if (player == null) return;

        XenoServerConfig.Data srv = XenoServerClientState.get();
        boolean okClient = switch (action) {
            case VANISH -> XenoClientConfig.bt3VanishClient && XenoServerClientState.vanish();
            case CHASE_DASH -> XenoClientConfig.bt3ChaseDashClient && XenoServerClientState.chase();
            case BACKSTEP -> XenoClientConfig.bt3BackstepClient && XenoServerClientState.backstep();
            default -> false;
        };
        if (!okClient) return;

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
            return;
        }
        if (maxRange > 0 && dist > maxRange) {
            player.displayClientMessage(Component.literal("§bGet closer"), true);
            return;
        }

        DmzClientStats.Snapshot snap = DmzClientStats.read(player);
        if (snap.present && snap.energy < kiCost) {
            player.displayClientMessage(Component.literal("§bNot enough KI"), true);
            return;
        }

        if (XenoClientConfig.bt3CombatSfx) playLocalIt(mc, true);
        applyClientMove(player, locked, action, side);
        if (XenoClientConfig.bt3CombatSfx) playLocalIt(mc, false);

        // comboStep carries vanish side for the server
        int payload = action == Bt3CombatPacket.Action.VANISH ? side : 0;
        ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(action, locked.getId(), payload));
        startMoveCooldown(action);
    }

    private static void applyClientMove(LocalPlayer player, LivingEntity target,
                                        Bt3CombatPacket.Action action, int side) {
        // Chase is a velocity fly — never snap locally.
        if (action == Bt3CombatPacket.Action.CHASE_DASH) {
            return;
        }
        // Match server dest exactly; hard-stop velocity
        Vec3 dest = switch (action) {
            case VANISH -> Bt3CombatPacket.vanishBehind(player, target, side);
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
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
    }
}
