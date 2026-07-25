package net.bullettrain.xenopixelsmod.client.combat;

import com.dragonminez.client.events.DMZClientEvent;
import com.dragonminez.client.events.LockOnEvent;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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

    private Bt3CombatClient() {}

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

    @Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
        }
    }

    @Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
    public static class ForgeBus {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();

            // One-shot: old options.txt often dual-mapped WASD onto these optional alts,
            // which steals key edges from movement / flight and confuses combat input.
            if (!scrubbedDualWasdBinds && mc.player != null) {
                scrubbedDualWasdBinds = true;
                scrubDualWasdBinds(mc);
            }

            if (mc.player == null || mc.level == null || mc.screen != null) {
                resetCharge();
                return;
            }
            if (!XenoClientConfig.bt3CombatClient || !XenoServerClientState.combat()) {
                resetCharge();
                return;
            }

            if (moveCooldown > 0) moveCooldown--;
            if (comboTicksLeft > 0) {
                comboTicksLeft--;
                if (comboTicksLeft == 0) comboStep = 0;
            }

            DmzAnimHelperClient.ClientStrikeChain.tick(mc.player);

            // Charge fist/kick always; dragon only while locked (see tickCharge)
            tickCharge(mc);

            LivingEntity locked = LockOnEvent.getLockedTarget();
            if (locked != null && !locked.isAlive()) locked = null;

            long now = System.currentTimeMillis();
            // Vanilla move keys only — never dual combat KeyMappings on WASD
            boolean leftDown = mc.options.keyLeft.isDown();
            boolean rightDown = mc.options.keyRight.isDown();
            boolean forwardDown = mc.options.keyUp.isDown();
            boolean backDown = mc.options.keyDown.isDown();

            // Vanish / chase / backstep: LOCK-ON ONLY
            if (locked != null) {
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
                        tryMove(mc, locked, Bt3CombatPacket.Action.CHASE_DASH, 0);
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
            } else {
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

        @SubscribeEvent
        public static void onAttackStart(DMZClientEvent.PlayerAttackStart event) {
            // Combo: lock-on OR freelook (not lock-only)
            if (chargeMode != ChargeMode.NONE) return;
            if (!XenoClientConfig.bt3CombatClient || !XenoClientConfig.bt3ComboClient) return;
            if (!XenoServerClientState.combo()) return;

            LocalPlayer player = event.getPlayer();
            if (player == null) return;

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
            // No combo lunge — stay planted while mashing
            int tid = target != null ? target.getId() : -1;
            ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(
                    Bt3CombatPacket.Action.COMBO_HIT, tid, comboStep));
        }

        @SubscribeEvent
        public static void onClickInput(InputEvent.InteractionKeyMappingTriggered event) {
            // Block vanilla attack while charging so charge release owns the hit
            if (chargeMode != ChargeMode.NONE && event.isAttack()) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
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
            // Kick: freelook optional (air kick OK)
            if (target == null) {
                target = findLookTarget(mc, XenoServerClientState.get().chargeAttackRange);
            }
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
        if (dist > maxRange) {
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
                new ResourceLocation("dragonminez", leave ? "evasion1" : "evasion2"));
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
