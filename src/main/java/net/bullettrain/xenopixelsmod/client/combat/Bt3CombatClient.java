package net.bullettrain.xenopixelsmod.client.combat;

import com.dragonminez.client.events.DMZClientEvent;
import com.dragonminez.client.events.LockOnEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.DmzClientStats;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;
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
 * Lock-on required for movement techs.
 * Hold charge keys anywhere (stamina); best with lock-on for targeting.
 *
 * - Attack → combo / finisher
 * - Double-tap A/D → vanish behind
 * - Double-tap W → chase dash
 * - Double-tap S → backstep
 * - Hold R → charge fist (DMZ charge anim + glow) → release
 * - Hold F → charge kick (DMZ heavy charge + kick anim) → release
 * - Hold N → dragon dash charge (DMZ ki_charge) → release (launch + chase)
 */
public final class Bt3CombatClient {
    public static final KeyMapping DASH_LEFT = new KeyMapping(
            "key.xenopixelsmod.bt3_dash_left", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_A, "key.categories.xenopixelsmod");
    public static final KeyMapping DASH_RIGHT = new KeyMapping(
            "key.xenopixelsmod.bt3_dash_right", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_D, "key.categories.xenopixelsmod");
    public static final KeyMapping CHASE = new KeyMapping(
            "key.xenopixelsmod.bt3_chase", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_W, "key.categories.xenopixelsmod");
    public static final KeyMapping BACKSTEP = new KeyMapping(
            "key.xenopixelsmod.bt3_backstep", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_S, "key.categories.xenopixelsmod");
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

            tickCharge(mc);

            LivingEntity locked = LockOnEvent.getLockedTarget();
            if (locked == null || !locked.isAlive()) {
                comboStep = 0;
                captureKeys(mc);
                return;
            }

            long now = System.currentTimeMillis();
            boolean leftDown = isLeftDown(mc);
            boolean rightDown = isRightDown(mc);
            boolean forwardDown = isForwardDown(mc);
            boolean backDown = isBackDown(mc);

            if (leftDown && !leftWasDown && now - lastLeftTapMs <= DOUBLE_TAP_MS) {
                tryMove(mc, locked, Bt3CombatPacket.Action.VANISH);
            }
            if (rightDown && !rightWasDown && now - lastRightTapMs <= DOUBLE_TAP_MS) {
                tryMove(mc, locked, Bt3CombatPacket.Action.VANISH);
            }
            if (forwardDown && !forwardWasDown && now - lastForwardTapMs <= DOUBLE_TAP_MS) {
                tryMove(mc, locked, Bt3CombatPacket.Action.CHASE_DASH);
            }
            if (backDown && !backWasDown && now - lastBackTapMs <= DOUBLE_TAP_MS) {
                tryMove(mc, locked, Bt3CombatPacket.Action.BACKSTEP);
            }

            if (leftDown && !leftWasDown) lastLeftTapMs = now;
            if (rightDown && !rightWasDown) lastRightTapMs = now;
            if (forwardDown && !forwardWasDown) lastForwardTapMs = now;
            if (backDown && !backWasDown) lastBackTapMs = now;
            captureKeys(mc);
        }

        @SubscribeEvent
        public static void onAttackStart(DMZClientEvent.PlayerAttackStart event) {
            if (chargeMode != ChargeMode.NONE) return;
            if (!XenoClientConfig.bt3CombatClient || !XenoClientConfig.bt3ComboClient) return;
            if (!XenoServerClientState.combo()) return;

            LivingEntity locked = LockOnEvent.getLockedTarget();
            if (locked == null || !locked.isAlive()) return;
            LocalPlayer player = event.getPlayer();
            if (player == null) return;

            int max = Math.max(1, XenoServerClientState.get().maxComboSteps);
            comboStep = comboStep <= 0 || comboTicksLeft <= 0 ? 1 : Math.min(max, comboStep + 1);
            comboTicksLeft = COMBO_WINDOW_TICKS;

            applyClientComboLunge(player, locked, comboStep, comboStep >= max && XenoServerClientState.finisher());
            ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(
                    Bt3CombatPacket.Action.COMBO_HIT, locked.getId(), comboStep));
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

        // Start charge on press
        if (chargeMode == ChargeMode.NONE) {
            if (canDragon && dragonDown && !dragonWasDown) {
                beginCharge(ChargeMode.DRAGON);
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
            DmzAnimHelper.playLocalChargeStart(mc.player, style);
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
        DmzAnimHelper.playLocalChargeStop(player);
        ModNetwork.CHANNEL.sendToServer(new ChargeAnimPacket(ChargeAnimPacket.Phase.CANCEL, style));
        resetCharge();

        if (percent < 20) {
            return; // too weak, cancel (pose already stopped)
        }

        LivingEntity target = LockOnEvent.getLockedTarget();
        if (target == null || !target.isAlive()) {
            target = findLookTarget(mc, mode == ChargeMode.DRAGON
                    ? XenoServerClientState.get().dragonDashRange
                    : XenoServerClientState.get().chargeAttackRange);
        }

        // Kick works without a target (air kick); fist/dragon still need one
        if (target == null && mode != ChargeMode.KICK) {
            player.displayClientMessage(Component.literal("§bNo target"), true);
            return;
        }

        // W/S while charging kick → vertical launch bias (+1 up / -1 down)
        int verticalBias = 0;
        if (mode == ChargeMode.KICK) {
            if (isForwardDown(mc)) verticalBias = 1;
            else if (isBackDown(mc)) verticalBias = -1;
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

        // Local DMZ fire / kick / punch animation (server also broadcasts)
        DmzAnimHelper.playLocalChargeRelease(player, style, full);

        // Client movement prediction
        if (mode == ChargeMode.DRAGON && target != null) {
            if (XenoClientConfig.bt3CombatSfx) playLocalIt(mc, true);
            Vec3 land = Bt3CombatPacket.chaseLanding(player, target);
            player.setPos(land.x, land.y, land.z);
            player.setDeltaMovement(Vec3.ZERO);
            face(player, target);
            if (XenoClientConfig.bt3CombatSfx) playLocalIt(mc, false);
        } else if (mode == ChargeMode.KICK) {
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            if (flat.lengthSqr() < 1.0e-4) flat = new Vec3(0, 0, 1);
            flat = flat.normalize();
            double forward = 0.45 + progress * 0.55;
            double up;
            if (verticalBias > 0) {
                up = (player.onGround() ? 0.45 : 0.65) + progress * Math.max(0.5, srv.kickUpLaunch) * 0.4;
                forward *= 0.5;
            } else if (verticalBias < 0) {
                up = -0.2 - progress * Math.max(0.4, srv.kickDownLaunch) * 0.35;
                forward *= 0.7;
            } else {
                up = player.onGround() ? 0.18 + progress * 0.12 : 0.28 + progress * 0.35;
            }
            player.setDeltaMovement(flat.scale(forward).add(0, up, 0));
            player.hasImpulse = true;
            player.fallDistance = 0f;
            if (target != null) face(player, target);
        } else if (target != null) {
            applyClientComboLunge(player, target, 3, full);
        }

        int tid = target != null ? target.getId() : -1;
        ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(action, tid, 0, percent, verticalBias));
        moveCooldown = MOVE_COOLDOWN_TICKS;
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
                DmzAnimHelper.playLocalChargeStop(mc.player);
            }
        }
        chargeMode = ChargeMode.NONE;
        chargeTicks = 0;
        chargeFullyGlowed = false;
    }

    private static void captureKeys(Minecraft mc) {
        leftWasDown = isLeftDown(mc);
        rightWasDown = isRightDown(mc);
        forwardWasDown = isForwardDown(mc);
        backWasDown = isBackDown(mc);
    }

    private static void tryMove(Minecraft mc, LivingEntity locked, Bt3CombatPacket.Action action) {
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
        applyClientMove(player, locked, action);
        if (XenoClientConfig.bt3CombatSfx) playLocalIt(mc, false);

        ModNetwork.CHANNEL.sendToServer(new Bt3CombatPacket(action, locked.getId(), 0));
        moveCooldown = MOVE_COOLDOWN_TICKS;
    }

    private static void applyClientMove(LocalPlayer player, LivingEntity target, Bt3CombatPacket.Action action) {
        Vec3 dest = switch (action) {
            case VANISH -> Bt3CombatPacket.vanishBehind(player, target);
            case CHASE_DASH -> Bt3CombatPacket.chaseLanding(player, target);
            case BACKSTEP -> Bt3CombatPacket.backstepDest(player, target);
            default -> player.position();
        };
        player.setPos(dest.x, dest.y, dest.z);
        if (action == Bt3CombatPacket.Action.CHASE_DASH) {
            Vec3 to = target.position().subtract(player.position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.lengthSqr() > 1.0e-4) {
                player.setDeltaMovement(flat.normalize().scale(0.5).add(0, 0.04, 0));
            } else {
                player.setDeltaMovement(Vec3.ZERO);
            }
        } else if (action == Bt3CombatPacket.Action.BACKSTEP) {
            Vec3 away = new Vec3(player.getX() - target.getX(), 0, player.getZ() - target.getZ());
            if (away.lengthSqr() > 1.0e-4) {
                player.setDeltaMovement(away.normalize().scale(0.3).add(0, 0.06, 0));
            } else {
                player.setDeltaMovement(Vec3.ZERO);
            }
        } else {
            player.setDeltaMovement(Vec3.ZERO);
        }
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

    private static boolean isLeftDown(Minecraft mc) {
        return DASH_LEFT.isDown() || mc.options.keyLeft.isDown();
    }

    private static boolean isRightDown(Minecraft mc) {
        return DASH_RIGHT.isDown() || mc.options.keyRight.isDown();
    }

    private static boolean isForwardDown(Minecraft mc) {
        return CHASE.isDown() || mc.options.keyUp.isDown();
    }

    private static boolean isBackDown(Minecraft mc) {
        return BACKSTEP.isDown() || mc.options.keyDown.isDown();
    }

    private static void applyClientComboLunge(LocalPlayer player, LivingEntity target, int step, boolean finisher) {
        Vec3 to = target.position().subtract(player.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        if (flat.lengthSqr() < 1.0e-4) return;
        flat = flat.normalize();
        double power = finisher ? 1.05 : 0.4 + step * 0.06;
        player.setDeltaMovement(flat.scale(power).add(0, finisher ? 0.08 : 0.04, 0));
        player.hasImpulse = true;
        face(player, target);
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
