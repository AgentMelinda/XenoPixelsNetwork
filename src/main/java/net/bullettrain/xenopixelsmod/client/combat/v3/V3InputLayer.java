package net.bullettrain.xenopixelsmod.client.combat.v3;

import java.util.UUID;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient;
import net.bullettrain.xenopixelsmod.client.combat.v2.V2Keys;
import net.bullettrain.xenopixelsmod.combat.v3.V3Direction;
import net.bullettrain.xenopixelsmod.combat.v3.V3Input;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * V3 mouse input while the fighter holds an approved lock.
 *
 * <p>A left tap is sent on release so that it cannot also become a charge. The server executes it
 * with DragonMineZ's native melee primitives. Holds and right-button presses are also server intents.
 * Without a lock nothing here claims the mouse and every click is native.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class V3InputLayer {
    private static final MouseCombatGesture.Pair MOUSE = new MouseCombatGesture.Pair();
    private static boolean dashWas;
    private static boolean forwardWas;
    private static boolean backWas;
    private static boolean vanishWas;
    private static boolean kiWas;
    private static final net.bullettrain.xenopixelsmod.client.combat.v2.TapGesture.DoubleTap FORWARD = new net.bullettrain.xenopixelsmod.client.combat.v2.TapGesture.DoubleTap();
    private static boolean grabLightWas;
    private static V3Direction grabSteer = V3Direction.NONE;
    private static final net.bullettrain.xenopixelsmod.client.combat.v2.TapGesture.DoubleTap VANISH_LEFT =
            new net.bullettrain.xenopixelsmod.client.combat.v2.TapGesture.DoubleTap();
    private static final net.bullettrain.xenopixelsmod.client.combat.v2.TapGesture.DoubleTap VANISH_RIGHT =
            new net.bullettrain.xenopixelsmod.client.combat.v2.TapGesture.DoubleTap();

    private V3InputLayer() {}

    private static boolean techniqueSlotWas;

    public static void tick(Minecraft mc) {
        if (mc == null || mc.player == null) return;
        var fighterState = V3ClientState.fighterState();
        boolean lightDown = V2Keys.down(V2Keys.LIGHT);
        boolean lightPressed = lightDown && !grabLightWas;
        grabLightWas = lightDown;
        if (fighterState == net.bullettrain.xenopixelsmod.combat.v3.V3State.GRABBED || fighterState == net.bullettrain.xenopixelsmod.combat.v3.V3State.GRAB_HOLD) {
            // Both hands are in the grab: light breaks out, movement keys steer the throw.
            MOUSE.update(true, true, false);
            V3Direction steer = heldDirection(mc);
            if (fighterState == net.bullettrain.xenopixelsmod.combat.v3.V3State.GRABBED) {
                if (lightPressed) V3ClientState.send(V3Input.GRAB, null, V3Direction.NONE);
            } else if (steer != grabSteer) {
                V3ClientState.send(V3Input.GRAB, null, steer);
            }
            grabSteer = steer;
            return;
        }
        grabSteer = V3Direction.NONE;
        // DragonMineZ's technique slot keys: letting go fires a ki technique that was being charged.
        boolean slotDown = false;
        for (net.minecraft.client.KeyMapping slot : com.dragonminez.client.util.KeyBinds.TECHNIQUE_SLOTS) {
            slotDown |= com.dragonminez.client.util.KeyBinds.isChordDown(slot);
        }
        if (techniqueSlotWas && !slotDown) V3ClientState.send(V3Input.TECHNIQUE_RELEASE, null, V3Direction.NONE);
        techniqueSlotWas = slotDown;
        boolean guardHeld = V2Keys.down(V2Keys.GUARD);
        boolean owned = Bt3CombatClient.fistsActive(mc) && !guardHeld;
        var actions = MOUSE.update(V2Keys.down(V2Keys.LIGHT), V2Keys.down(V2Keys.HEAVY), owned);
        var target = V3ClientState.target();
        UUID id = target == null ? null : target.target();
        // Each fresh N press follows an active dash chain; W/S steer its launcher.
        boolean dashDown = V2Keys.down(Bt3CombatClient.DRAGON_DASH);
        if (dashDown && !dashWas && id != null) {
            var state = V3ClientState.state();
            boolean follow = state != null && dashFollow(state.state(), state.window(), state.windowTicksLeft());
            V3ClientState.send(follow ? V3Input.DASH_CROSS : V3Input.DRAGON_DASH, id, heldDirection(mc));
        }
        dashWas = dashDown;
        if (id != null) {
            // Guard + light is the grab.
            if (guardHeld && lightPressed && Bt3CombatClient.fistsActive(mc)) {
                V3ClientState.send(V3Input.GRAB, id, heldDirection(mc));
            }
            // One tap of forward takes the chase a launch opened.
            boolean forward = mc.options.keyUp.isDown();
            boolean forwardTwice = FORWARD.update(forward);
            if (forward && !forwardWas && V3ClientState.window() == net.bullettrain.xenopixelsmod.combat.v3.V3Window.CHASE) {
                V3ClientState.send(V3Input.CHASE_START, id, V3Direction.NONE);
                FORWARD.clear();
            } else if (forwardTwice) {
                // Forward twice is the chase at any time, as in v2.
                V3ClientState.send(V3Input.CHASE_START, id, V3Direction.NONE);
            }
            forwardWas = forward;
            // Double-tap a strafe key: vanish to that side of the target, or counter if one is open.
            if (VANISH_LEFT.update(mc.options.keyLeft.isDown())) V3ClientState.send(V3Input.VANISH, id, V3Direction.LEFT);
            // The v2 vanish key works too, steered by whichever strafe key is held.
            boolean vanishDown = V2Keys.down(V2Keys.VANISH);
            if (vanishDown && !vanishWas) {
                V3Direction side = mc.options.keyLeft.isDown() ? V3Direction.LEFT
                        : mc.options.keyRight.isDown() ? V3Direction.RIGHT : V3Direction.NONE;
                V3ClientState.send(V3Input.VANISH, id, side);
            }
            vanishWas = vanishDown;
            // Back stops a chase or a dash in flight: travelling must never be a trap.
            boolean back = mc.options.keyDown.isDown();
            var snapshot = V3ClientState.state();
            boolean dashRoute = snapshot != null && dashFollow(snapshot.state(), snapshot.window(), snapshot.windowTicksLeft());
            if (back && !backWas && !dashDown && (!dashRoute || guardHeld)
                    && fighterState == net.bullettrain.xenopixelsmod.combat.v3.V3State.TRAVEL) {
                V3ClientState.send(V3Input.CHASE_STOP, null, V3Direction.NONE);
            }
            backWas = back;
            // The ki blast is the shared one every controller uses, on the v2 ki key.
            boolean kiDown = V2Keys.down(V2Keys.KI_BLAST) && Bt3CombatClient.fistsActive(mc);
            if (kiDown && !kiWas) {
                var tracked = V3ClientState.trackedTarget();
                net.bullettrain.xenopixelsmod.network.ModNetwork.sendToServer(new net.bullettrain.xenopixelsmod.network.Bt3CombatPacket(
                        net.bullettrain.xenopixelsmod.network.Bt3CombatPacket.Action.KI_BLAST_CANCEL,
                        tracked == null ? -1 : tracked.getId(), 0));
            }
            kiWas = kiDown;
            if (VANISH_RIGHT.update(mc.options.keyRight.isDown())) V3ClientState.send(V3Input.VANISH, id, V3Direction.RIGHT);
        } else {
            forwardWas = mc.options.keyUp.isDown();
            FORWARD.clear();
            vanishWas = kiWas = false;
            VANISH_LEFT.clear();
            VANISH_RIGHT.clear();
        }
        switch (actions.left()) {
            case TAP -> V3ClientState.send(V3Input.LIGHT_TAP, id, V3Direction.NONE);
            case START -> V3ClientState.send(V3Input.LIGHT_CHARGE_START, id, V3Direction.NONE);
            case RELEASE -> V3ClientState.send(V3Input.CHARGE_RELEASE, id, V3Direction.NONE);
            case CANCEL -> V3ClientState.send(V3Input.CHARGE_CANCEL, null, V3Direction.NONE);
            case NONE -> { }
        }
        switch (actions.right()) {
            case TAP -> V3ClientState.send(V3Input.HEAVY_TAP, id, heldDirection(mc));
            case START -> V3ClientState.send(V3Input.HEAVY_CHARGE_START, id, V3Direction.NONE);
            case RELEASE -> V3ClientState.send(V3Input.CHARGE_RELEASE, id, V3Direction.NONE);
            case CANCEL -> V3ClientState.send(V3Input.CHARGE_CANCEL, null, V3Direction.NONE);
            case NONE -> { }
        }
    }

    public static boolean dashFollow(net.bullettrain.xenopixelsmod.combat.v3.V3State state,
                                     net.bullettrain.xenopixelsmod.combat.v3.V3Window window, int ticks) {
        return window == net.bullettrain.xenopixelsmod.combat.v3.V3Window.DASH_CROSS
                && (state == net.bullettrain.xenopixelsmod.combat.v3.V3State.TRAVEL || ticks > 0);
    }

    /** The movement key held as the heavy lands picks its shove; forward wins a tie. */
    private static V3Direction heldDirection(Minecraft mc) {
        // Jump wins for grab throw-up; dash still uses W/S as forward/back for vertical launch.
        if (mc.options.keyJump.isDown()) return V3Direction.UP;
        if (mc.options.keyUp.isDown()) return V3Direction.FORWARD;
        if (mc.options.keyDown.isDown()) return V3Direction.BACK;
        if (mc.options.keyLeft.isDown()) return V3Direction.LEFT;
        if (mc.options.keyRight.isDown()) return V3Direction.RIGHT;
        return V3Direction.NONE;
    }

    /** Screen, death, seat or disabled combat: cancel, and require a physical release to restart. */
    public static void pause() {
        var actions = MOUSE.update(true, true, false);
        if (actions.left() == MouseCombatGesture.Action.CANCEL || actions.right() == MouseCombatGesture.Action.CANCEL) {
            V3ClientState.send(V3Input.CHARGE_CANCEL, null, V3Direction.NONE);
        }
    }

    /** Controller switched away from V3. */
    public static void release() {
        pause();
        MOUSE.reset();
    }

    public static boolean charging() { return MOUSE.left().charging() || MOUSE.right().charging(); }
    public static boolean kickCharging() { return MOUSE.right().charging(); }
    public static float chargeProgress() { return Math.max(MOUSE.left().progress(), MOUSE.right().progress()); }

    /** Right mouse while V3 owns the fists is the heavy attack, not "use". */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !XenoServerClientState.v3Controller()) return;
        Minecraft mc = Minecraft.getInstance();
        if (Bt3CombatClient.fistsActive(mc) && V2Keys.sameKey(V2Keys.HEAVY, mc.options.keyUse)) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }
}
