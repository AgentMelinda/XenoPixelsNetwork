package net.bullettrain.xenopixelsmod.client.combat.v2;

import com.dragonminez.client.events.LockOnEvent;
import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient;
import net.bullettrain.xenopixelsmod.client.combat.LockOnCycle;
import net.bullettrain.xenopixelsmod.client.keybind.XenoKeybinds;
import net.bullettrain.xenopixelsmod.combat.v2.V2Direction;
import net.bullettrain.xenopixelsmod.combat.v2.V2Input;
import net.bullettrain.xenopixelsmod.combat.v2.V2State;
import net.bullettrain.xenopixelsmod.combat.v2.grab.GrabRules;
import net.bullettrain.xenopixelsmod.network.Bt3CombatPacket;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.CombatV2InputPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * The one place the v2 client reads combat keys.
 *
 * <p>Every v1 move was its own tick method polling its own keys with its own cooldown. Here one
 * pass a tick reads the keys and sends intent: a {@link CombatV2InputPacket} for everything v2
 * owns, or the legacy packet for the two moves it still borrows from v1 (guard and the ki blast).
 * Nothing is predicted and no cooldown is kept here; the server answers with state and the prompt
 * draws that.
 *
 * <p><b>Nothing here does anything without a DragonMineZ lock-on.</b> Every move is sent at the
 * locked target and only while there is one; with no lock this pass reads the lock key and
 * nothing else, and every key keeps its ordinary meaning. See {@link CombatStance}. The one
 * exception is breaking out of a grab, which a fighter may do without a lock of their own.
 *
 * <p>Called from {@code Bt3CombatClient}'s tick, after its own "is this player able to fight"
 * checks (alive, in a world, no screen open), only while the server runs the v2 controller.
 *
 * <p>One move is shared with the {@code legacy} and {@code bt3_manual} controllers: the grab.
 * Under those, {@code Bt3CombatClient} keeps reading its own keys and calls
 * {@link #tickSharedGrab} with them; nothing else in this class runs there.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class V2InputLayer {

    private static final ChargeGesture LIGHT_CHARGE = new ChargeGesture();
    private static final ChargeGesture HEAVY_CHARGE = new ChargeGesture();
    private static boolean lightWas, vanishWas, kiWas, lockWas;
    private static final DragonDashGesture DASH = new DragonDashGesture();
    private static boolean dashStopSent;
    private static boolean lightWasGrab;
    /** The other controllers' punch and guard, as of the last tick they asked about the grab. */
    private static boolean sharedAttackWas;
    private static boolean sharedGuardWas;
    /** Ticks their punch key has been down, counting the current one. */
    private static int sharedAttackHeld;
    /** This client was in a grab on the last v2 tick. */
    private static boolean grabWas;
    private static boolean guarding;
    /** Ticks the guard key has been down this press. */
    private static int guardHeld;
    /** The guard key did something as a guard this press, so its release is not an inventory tap. */
    private static boolean guardUsed;
    /** Ticks for which a click of the inventory key is let through to Minecraft untouched. */
    private static int inventoryPassTicks;
    private static V2Direction lastGrabDirection = V2Direction.NONE;
    private static final TapGesture.DoubleTap FORWARD = new TapGesture.DoubleTap();
    private static final VanishGesture VANISH_GESTURE = new VanishGesture();
    private static boolean backWas;
    private static boolean firstTickDone;
    /** The shared DragonMineZ dash mapping is suppressed only while v2 owns the guard key. */
    private static boolean guardClaimsDash;

    private V2InputLayer() {}

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        V2ClientState.tick();
        firstTick(mc);

        boolean stance = CombatStance.update(mc);
        // The lock is the target of everything below, and without one nothing below sends.
        LivingEntity locked = CombatStance.lockedTarget();
        int targetId = locked == null ? -1 : locked.getId();
        V2Direction direction = heldDirection(mc);

        tickLock(player, locked);
        tickGuard(mc, player, stance);
        tickLight(mc, stance, targetId, direction);
        tickHeavy(mc, stance, targetId, direction);
        tickGrabDirection(targetId, direction);
        tickVanish(mc, locked, direction);
        tickKi(stance, targetId);
        tickMovement(mc, locked);
        tickDragonDash(stance, targetId, direction);
    }

    // ---- attacks ----

    private static void tickLight(Minecraft mc, boolean stance, int targetId, V2Direction direction) {
        boolean grabbed = V2ClientState.state() == V2State.GRABBED;
        // Locked on, with the fists rule shared with v1 (empty hands, no technique-bar modifier).
        // Held in a grab the same press is the way out, and that needs no lock.
        boolean down = V2Keys.down(V2Keys.LIGHT);
        if (down && !lightWas) {
            lightWasGrab = guarding || grabbed;
            // Guard + light is the grab, and the same press techs a grab that has caught you.
            if (lightWasGrab) {
                guardUsed = true;
                send(V2Input.GRAB, targetId, direction, 0);
            }
        }
        ChargeGesture.Action action = LIGHT_CHARGE.update(down, stance && Bt3CombatClient.fistsActive(mc)
                && !guarding && !lightWasGrab && !HEAVY_CHARGE.charging() && !V2ClientState.state().committed());
        attackGesture(action, false, targetId, direction);
        if (!down) lightWasGrab = false;
        lightWas = down;
    }

    private static void tickHeavy(Minecraft mc, boolean stance, int targetId, V2Direction direction) {
        // Outside the stance the heavy key is whatever else it is bound to: use, place, interact.
        ChargeGesture.Action action = HEAVY_CHARGE.update(V2Keys.down(V2Keys.HEAVY),
                stance && Bt3CombatClient.fistsActive(mc) && !guarding && !LIGHT_CHARGE.charging()
                        && !V2ClientState.state().committed());
        attackGesture(action, true, targetId, direction);
    }

    private static void attackGesture(ChargeGesture.Action action, boolean kick, int targetId, V2Direction direction) {
        switch (action) {
            case TAP -> send(kick ? V2Input.HEAVY_PRESS : V2Input.LIGHT_PRESS, targetId, direction, 0);
            case START -> send(kick ? V2Input.HEAVY_CHARGE_START : V2Input.LIGHT_CHARGE_START, targetId, direction, 0);
            case RELEASE -> send(kick ? V2Input.HEAVY_HOLD : V2Input.LIGHT_HOLD, targetId, direction, 0);
            case CANCEL -> send(V2Input.CHARGE_CANCEL, -1, direction, 0);
            default -> { }
        }
    }

    public static boolean charging() { return LIGHT_CHARGE.charging() || HEAVY_CHARGE.charging(); }
    public static boolean kickCharging() { return HEAVY_CHARGE.charging(); }
    public static float chargeProgress() { return Math.max(LIGHT_CHARGE.progress(), HEAVY_CHARGE.progress()); }

    /** While holding someone, the throw follows the direction held at the moment of release. */
    private static void tickGrabDirection(int targetId, V2Direction direction) {
        if (V2ClientState.state() != V2State.GRAB_HOLD) {
            lastGrabDirection = V2Direction.NONE;
            return;
        }
        if (direction != lastGrabDirection) {
            lastGrabDirection = direction;
            send(V2Input.GRAB, targetId, direction, 0);
        }
    }

    // ---- the grab, shared with the legacy and manual controllers ----

    /**
     * The one v2 move the other controllers have. Called from {@code Bt3CombatClient}'s own tick
     * with that controller's own keys, so nothing here needs to know what they are bound to.
     *
     * <p>Guard and punch together is the grab, at the locked target and only with one, exactly as
     * in v2; the two keys may go down a few ticks apart in either order ({@link GrabRules#chord}).
     * Punch on its own breaks a grab that has caught the fighter, lock or no lock. While holding
     * someone, the direction held is where they will be thrown.
     *
     * @param guardUp    the controller's guard is up this tick
     * @param attackDown the controller's punch key is physically down
     */
    public static void tickSharedGrab(Minecraft mc, boolean guardUp, boolean attackDown) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        LivingEntity locked = CombatStance.lockedTarget();
        int targetId = locked == null ? -1 : locked.getId();
        V2Direction direction = heldDirection(mc);
        boolean attackPressed = attackDown && !sharedAttackWas;
        sharedAttackHeld = attackDown ? sharedAttackHeld + 1 : 0;
        boolean chord = GrabRules.chord(guardUp, guardUp && !sharedGuardWas, attackDown,
                attackPressed, sharedAttackHeld);
        sharedAttackWas = attackDown;
        sharedGuardWas = guardUp;
        if (V2ClientState.state() == V2State.GRABBED) {
            if (attackPressed) send(V2Input.GRAB, targetId, direction, 0);
        } else if (chord && locked != null && CombatStance.handsFree(player)) {
            send(V2Input.GRAB, targetId, direction, 0);
        }
        tickGrabDirection(targetId, direction);
    }

    // ---- guard ----

    /**
     * Guard is held on R. A player may still bind it to their inventory key if they prefer.
     *
     * <p>The guard goes up on the press, with no wait, because a block that starts late is not a
     * block. If the key then comes back up almost at once and it never did anything as a guard
     * (no hit taken, no grab thrown), the press was a tap, and the inventory click that the
     * stance held back is handed to Minecraft after all. With the default layout E opens the
     * inventory normally and R blocks.
     */
    private static void tickGuard(Minecraft mc, LocalPlayer player, boolean stance) {
        boolean down = stance && V2Keys.down(V2Keys.GUARD);
        // A grab takes the guard down on the server at both ends. If the key is still held when
        // the grab is over the guard goes back up, or the fighter would be standing behind a
        // guard that only they believe in.
        boolean grab = V2ClientState.inGrab();
        if (grabWas && !grab && guarding && down) {
            ModNetwork.sendToServer(new Bt3CombatPacket(Bt3CombatPacket.Action.GUARD, -1, 1));
        }
        grabWas = grab;
        if (down && !guarding) {
            cancelCharges();
            guardHeld = 0;
            guardUsed = false;
        }
        if (down) {
            guardHeld++;
            if (player.hurtTime > 0) guardUsed = true;
        }
        if (down == guarding) return;
        guarding = down;
        ModNetwork.sendToServer(new Bt3CombatPacket(Bt3CombatPacket.Action.GUARD, -1, down ? 1 : 0));
        if (!down && TapGesture.isTap(guardHeld, guardUsed)
                && V2Keys.sameKey(V2Keys.GUARD, mc.options.keyInventory)) {
            inventoryPassTicks = 2;
            KeyMapping.click(mc.options.keyInventory.getKey());
        }
    }

    // ---- vanish / ki ----

    /**
     * Vanish, at the locked target. The server decides what the press is: a super counter if the
     * locked target just landed a hit, otherwise a vanish behind it throughout the lock range.
     *
     * <p>It needs the lock like every other move, but not empty hands: it is the way out of
     * trouble and must answer whatever the fighter is holding.
     */
    private static void tickVanish(Minecraft mc, LivingEntity locked, V2Direction direction) {
        V2Direction side = VANISH_GESTURE.update(locked != null,
                mc.options.keyLeft.isDown(), mc.options.keyRight.isDown(), System.currentTimeMillis());
        boolean down = locked != null && V2Keys.down(V2Keys.VANISH);
        if (locked != null && (side != V2Direction.NONE || (down && !vanishWas))) {
            send(V2Input.VANISH, locked.getId(), side != V2Direction.NONE ? side : direction, 0);
        }
        vanishWas = down;
    }

    /** The ki blast is still v1's: v2 only gives it a key that works at any point in a fight. */
    private static void tickKi(boolean stance, int targetId) {
        boolean down = stance && V2Keys.down(V2Keys.KI_BLAST);
        if (down && !kiWas) {
            ModNetwork.sendToServer(new Bt3CombatPacket(
                    Bt3CombatPacket.Action.KI_BLAST_CANCEL, targetId, 0));
        }
        kiWas = down;
    }

    // ---- movement ----

    /**
     * Double-taps of the movement keys, read only while locked on so walking about never dashes.
     *
     * <p>Forward twice is the chase (once is enough while a launched target can be homed on);
     * left or right twice is handled by {@link #tickVanish}. Back stops an active chase.
     */
    private static void tickMovement(Minecraft mc, LivingEntity locked) {
        boolean forward = mc.options.keyUp.isDown();
        boolean back = mc.options.keyDown.isDown();
        boolean forwardPressed = FORWARD.pressed(forward);
        boolean forwardTwice = FORWARD.update(forward);
        if (locked == null) FORWARD.clear();

        if (locked != null) {
            if (forwardPressed && V2ClientState.homingOpen()) {
                // Dragon homing: one tap of forward after a launch flies to whoever was launched.
                // The server knows who that is; the lock is what makes the request valid.
                send(V2Input.CHASE, locked.getId(), V2Direction.FORWARD, 0);
                FORWARD.clear();
            } else if (forwardTwice) {
                send(V2Input.CHASE, locked.getId(), V2Direction.FORWARD, 0);
            }
        }
        if (back && !backWas && V2ClientState.state() == V2State.TRAVEL) {
            // Stopping is always allowed, lock or no lock: a chase must never be a trap.
            send(V2Input.CHASE_STOP, -1, V2Direction.BACK, 0);
        }
        backWas = back;
    }

    private static void tickDragonDash(boolean stance, int targetId, V2Direction direction) {
        boolean allowed = stance && !guarding && Bt3CombatClient.fistsActive(Minecraft.getInstance())
                && !V2ClientState.state().committed();
        boolean continuation = V2ClientState.dashOpenAgainst(targetId);
        if (V2ClientState.dashOpen() && (!allowed || !continuation)) {
            if (!dashStopSent) send(V2Input.CHASE_STOP, -1, V2Direction.NONE, 0);
            dashStopSent = true;
        } else {
            dashStopSent = false;
        }
        int charge = DASH.update(V2Keys.down(Bt3CombatClient.DRAGON_DASH), allowed, continuation);
        if (charge != DragonDashGesture.NONE) send(V2Input.DRAGON_DASH, targetId, direction, charge);
    }

    // ---- lock ----

    /**
     * The v2 lock key is DragonMineZ's own lock, not a second one beside it, and it is the one
     * key this pass reads with nothing locked: it is how v2 is switched on. Going through
     * {@code toggleLock} means it obeys everything the DragonMineZ key does: the Ki Sense skill
     * and its range, who may be targeted, and the "lock through blocks" setting.
     */
    private static void tickLock(LocalPlayer player, LivingEntity locked) {
        boolean down = V2Keys.down(V2Keys.LOCK) && CombatStance.handsFree(player);
        if (down && !lockWas) {
            LockOnEvent.toggleLock();
            if (locked == null && LockOnEvent.getLockedTarget() == null && !hasKiSense(player)) {
                player.displayClientMessage(
                        Component.literal("§7Lock-on needs the Ki Sense skill"), true);
            }
        }
        lockWas = down;
    }

    private static boolean hasKiSense(LocalPlayer player) {
        try {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            return data != null && data.getSkills() != null && data.getSkills().getSkillLevel("kisense") > 0;
        } catch (Throwable t) {
            return true;
        }
    }

    // ---- helpers ----

    private static V2Direction heldDirection(Minecraft mc) {
        if (mc.options.keyUp.isDown()) return V2Direction.FORWARD;
        if (mc.options.keyDown.isDown()) return V2Direction.BACK;
        if (mc.options.keyLeft.isDown()) return V2Direction.LEFT;
        if (mc.options.keyRight.isDown()) return V2Direction.RIGHT;
        if (mc.options.keyJump.isDown()) return V2Direction.UP;
        if (mc.options.keyShift.isDown()) return V2Direction.DOWN;
        return V2Direction.NONE;
    }

    private static void send(V2Input input, int targetId, V2Direction direction, int charge) {
        ModNetwork.sendToServer(new CombatV2InputPacket(input, targetId, direction, charge));
    }

    /**
     * First tick of v2 on this client: move a profile off the old default keys, run the keybind
     * cleanup if it is on, and say what was done.
     */
    private static void firstTick(Minecraft mc) {
        if (firstTickDone) return;
        firstTickDone = true;
        V2Keys.migrateLayout(mc);
        XenoKeybinds.CleanResult result = XenoKeybinds.autoCleanOnce(mc);
        if (result != null && mc.player != null) {
            mc.player.displayClientMessage(Component.literal(
                    "§aXenoCombat v2§7: unbound " + result.unbound()
                            + " key(s) from other mods. Backup: §f" + result.backup().getFileName()
                            + "§7. Undo with §f/xenokeybind restore§7; turn off with §f/xenokeybind auto false"),
                    false);
        }
    }

    /** Releases anything held, for the tick v2 stops being the active controller. */
    public static void release() {
        if (V2ClientState.dashOpen() && Minecraft.getInstance().getConnection() != null) {
            send(V2Input.CHASE_STOP, -1, V2Direction.NONE, 0);
        }
        DASH.reset();
        dashStopSent = false;
        if (charging() && Minecraft.getInstance().getConnection() != null) {
            send(V2Input.CHARGE_CANCEL, -1, V2Direction.NONE, 0);
        }
        LIGHT_CHARGE.reset();
        HEAVY_CHARGE.reset();
        dropGuard();
        lightWas = vanishWas = kiWas = lockWas = false;
        backWas = false;
        grabWas = false;
        inventoryPassTicks = 0;
        FORWARD.clear();
        VANISH_GESTURE.reset();
        CombatStance.reset();
    }

    private static void cancelCharges() {
        Minecraft mc = Minecraft.getInstance();
        boolean cancelled = LIGHT_CHARGE.update(V2Keys.down(V2Keys.LIGHT), false) == ChargeGesture.Action.CANCEL;
        cancelled |= HEAVY_CHARGE.update(V2Keys.down(V2Keys.HEAVY), false) == ChargeGesture.Action.CANCEL;
        if (cancelled && mc.getConnection() != null) send(V2Input.CHARGE_CANCEL, -1, V2Direction.NONE, 0);
    }

    /** Screens, seats and disabled combat cancel a hold and require a fresh press afterwards. */
    public static void pause() {
        DASH.update(V2Keys.down(Bt3CombatClient.DRAGON_DASH), false, false);
        if (!dashStopSent && V2ClientState.dashOpen() && Minecraft.getInstance().getConnection() != null) {
            send(V2Input.CHASE_STOP, -1, V2Direction.NONE, 0);
            dashStopSent = true;
        }
        cancelCharges();
        dropGuard();
    }

    private static void dropGuard() {
        if (!guarding) return;
        guarding = false;
        // Not a tap: a guard dropped for the fighter must never open the inventory.
        guardUsed = true;
        if (Minecraft.getInstance().getConnection() != null) {
            ModNetwork.sendToServer(new Bt3CombatPacket(Bt3CombatPacket.Action.GUARD, -1, 0));
        }
    }

    public static boolean guarding() {
        return guarding;
    }

    // ---- keeping the shared keys to one meaning ----

    /**
     * While locked on, a key the layout shares with Minecraft means the combat move only. The
     * other mapping's queued presses are thrown away before anything reads them, and only when
     * the two really are on the same physical key, so a player who has moved either binding is
     * left alone.
     *
     * <p>{@link ClientTickEvent.Pre} fires as the first statement of the client tick, ahead of
     * Minecraft's own key handling.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        // The input pass does not run with a screen open or out of a world, so a guard held into
        // one would never be let go, and the stance would keep its last answer.
        if (mc.screen != null || mc.player == null) {
            if (guarding) dropGuard();
            VANISH_GESTURE.reset();
            FORWARD.clear();
            CombatStance.suspend();
        }
        boolean passInventory = inventoryPassTicks > 0;
        if (passInventory) inventoryPassTicks--;
        updateGuardDashClaim();
        if (!CombatStance.active()) return;
        if (!passInventory) drainIfShared(V2Keys.GUARD, mc.options.keyInventory);
        drainIfShared(V2Keys.KI_BLAST, mc.options.keyDrop);
        drainIfShared(V2Keys.LOCK, mc.options.keyPickItem);
    }

    /** DMZ reads its dash mapping on the key event, before the next client tick. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKey(InputEvent.Key event) {
        updateGuardDashClaim();
    }

    private static void updateGuardDashClaim() {
        boolean claim = CombatStance.active() && V2Keys.sameKey(V2Keys.GUARD, KeyBinds.DASH_KEY);
        if (claim) {
            KeyBinds.DASH_KEY.setDown(false);
            while (KeyBinds.DASH_KEY.consumeClick()) {
            }
        } else if (guardClaimsDash) {
            KeyBinds.DASH_KEY.setDown(Minecraft.getInstance().screen == null && V2Keys.down(KeyBinds.DASH_KEY));
        }
        guardClaimsDash = claim;
    }

    private static void drainIfShared(KeyMapping combat, KeyMapping other) {
        if (!V2Keys.sameKey(combat, other)) return;
        while (other.consumeClick()) {
        }
    }

    /** Right mouse while locked on is the heavy attack, not "use". */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!CombatStance.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.isUseItem() && V2Keys.sameKey(V2Keys.HEAVY, mc.options.keyUse)) {
            event.setSwingHand(false);
            event.setCanceled(true);
        } else if (event.isPickBlock() && V2Keys.sameKey(V2Keys.LOCK, mc.options.keyPickItem)) {
            event.setCanceled(true);
        }
    }

    /** The wheel cycles the lock while one is held, instead of scrolling the hotbar. */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!CombatStance.active() || event.getScrollDeltaY() == 0.0) return;
        LockOnCycle.cycle(event.getScrollDeltaY() > 0 ? 1 : -1);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        V2ClientState.reset();
        guarding = false;
        firstTickDone = false;
        sharedAttackWas = false;
        sharedGuardWas = false;
        sharedAttackHeld = 0;
        release();
    }
}
