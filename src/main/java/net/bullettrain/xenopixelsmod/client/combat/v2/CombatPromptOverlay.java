package net.bullettrain.xenopixelsmod.client.combat.v2;

import net.bullettrain.xenopixelsmod.client.XenoCooldownHudOverlay;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.combat.v2.V2State;
import net.bullettrain.xenopixelsmod.combat.v2.combo.BranchFlavor;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboInput;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The combat prompt: what the fighter can do right now, and for how long.
 *
 * <p>Drawn at the bottom of the screen, centred, just above the hotbar and Minecraft's own
 * action-bar text, so it never covers the fight in the middle of the screen. It shows, in order
 * of urgency: how to break a grab that has caught you; where a held victim will be thrown; a
 * super counter that is open; the branches open from the current combo beat, each labelled with
 * what it will actually do, and the time left to pick one; a chase that is open; and, when
 * nothing else is up, that a grab is in reach.
 *
 * <p>Each prompt is one generated atlas plate ({@link PromptPlate}) in the green palette with a
 * single line on it, the key and then the move. The plate is chosen at the width the line needs
 * and blitted 1:1, never stretched.
 *
 * <p>Everything about whether a window is open comes from {@link V2ClientState}, which only the
 * server writes. The single thing judged here is whether the locked target is close enough for
 * the grab hint, and that is a hint only: the server tests reach again when the grab is pressed.
 *
 * <p>Like the moves it describes, the prompt exists only while the fighter is locked on a target.
 * With nothing locked it draws nothing, with two exceptions that are not moves the fighter starts:
 * a fighter caught in a grab is always told how to break out, and one already holding somebody is
 * always shown the throw, because both happen with or without a lock.
 *
 * <p>Under the {@code legacy} and {@code bt3_manual} controllers the grab is the one v2 move there
 * is, so this draws the grab's prompts only, labelled with those controllers' own guard and punch
 * keys: the grab hint, the throw, the break-out, and the chase a throw opens (their dragon homing,
 * one tap of forward). Their other windows keep the indicators they already had.
 */
public final class CombatPromptOverlay {

    private static final int KEY_COLOR = 0xFFFFF2A8;
    private static final int LABEL_COLOR = 0xFFFFFFFF;
    private static final int CAPTION_COLOR = 0xFFD6FFE0;
    private static final int TIMER = 0xFF4DFF88;
    private static final int TIMER_LOW = 0xFFFFC14D;
    private static final int TIMER_TRACK = 0xB0101814;
    /**
     * How far above the bottom of the screen the prompt's lower edge sits. The hotbar, the status
     * rows above it, the held item's name and the action-bar message all live below this line.
     */
    private static final int BOTTOM_OFFSET = 78;
    private static final int GAP = 3;
    private static final int TIMER_HEIGHT = 2;

    /** One plate: its outline, the key that triggers it and what it does. */
    private record Prompt(PromptPlate plate, String key, String label) {}

    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        if (XenoServerClientState.v3Controller()) {
            renderV3(g, mc);
            return;
        }
        LivingEntity locked = CombatStance.lockedTarget();
        if (!XenoClientConfig.showCombatPrompts(locked != null)) return;
        boolean v2 = XenoServerClientState.v2Controller();
        // The legacy and manual controllers share one v2 move, the grab, and so show the grab's
        // prompts and nothing else from here.
        if (!v2 && !(XenoClientConfig.bt3CombatClient && XenoServerClientState.combat())) return;

        Font font = mc.font;
        int cx = g.guiWidth() / 2;
        int bottom = g.guiHeight() - BOTTOM_OFFSET;
        V2State state = V2ClientState.state();
        // Guard and punch, on whichever keys the running controller reads them from.
        String punchKey = v2 ? key(V2Keys.LIGHT) : key(Bt3CombatClient.CHARGE_FIST);
        String grabKeys = (v2 ? key(V2Keys.GUARD) : key(Bt3CombatClient.GUARD)) + "+" + punchKey;

        if (state == V2State.GRABBED) {
            if (V2ClientState.grabTicksLeft() > 0) {
                // Punch alone breaks out. v2 words it as the grab's own chord, which is how its
                // players already know it; the other controllers drop the guard for the length
                // of a hold, so naming the guard key there would be naming a key that does nothing.
                drawRow(g, font, cx, bottom, List.of(new Prompt(PromptPlate.ALERT,
                        v2 ? grabKeys : punchKey, "Break free")), V2ClientState.grabFraction());
            } else {
                drawRow(g, font, cx, bottom, List.of(new Prompt(PromptPlate.ALERT, "", "Grabbed")), 0f);
            }
            return;
        }
        if (state == V2State.GRAB_HOLD) {
            // Already holding someone: the throw happens, and can be aimed, lock or no lock.
            int top = drawRow(g, font, cx, bottom, List.of(new Prompt(PromptPlate.GRAB, "", "Throw")),
                    V2ClientState.grabFraction());
            String caption = key(mc.options.keyUp) + " forward   " + key(mc.options.keyDown) + " back   "
                    + key(mc.options.keyJump) + " up   " + key(mc.options.keyShift) + " down";
            g.drawCenteredString(font, caption, cx, top - 11, CAPTION_COLOR);
            return;
        }
        if (!v2) {
            // What a throw opens under these controllers is their own dragon homing. Offered
            // while still locked on whoever was thrown, which throwChaseLive checks itself.
            if (Bt3CombatClient.throwChaseLive()) {
                drawRow(g, font, cx, bottom, List.of(new Prompt(PromptPlate.TRAVEL, key(mc.options.keyUp), "Chase")),
                        V2ClientState.homingFraction());
                return;
            }
            if (locked != null && grabInReach(mc, locked, CombatStance.handsFree(mc.player))) {
                drawRow(g, font, cx, bottom, List.of(new Prompt(PromptPlate.GRAB, grabKeys, "Grab")), 0f);
            }
            return;
        }
        // Everything below is a move, and there are no moves without a lock.
        if (locked == null) return;

        if (V2ClientState.counterOpenAgainst(locked.getId())) {
            String vanishKeys = "2x" + key(mc.options.keyLeft) + "/" + key(mc.options.keyRight);
            drawRow(g, font, cx, bottom, List.of(new Prompt(PromptPlate.ALERT, vanishKeys, "Counter")),
                    V2ClientState.counterFraction());
            return;
        }

        if (V2ClientState.dashOpenAgainst(locked.getId())) {
            drawRow(g, font, cx, bottom, List.of(new Prompt(PromptPlate.TRAVEL,
                    key(Bt3CombatClient.DRAGON_DASH), "Dash vanish")), V2ClientState.dashFraction());
            return;
        }
        List<Prompt> branches = new ArrayList<>(4);
        if (V2ClientState.branchOpen(ComboInput.LIGHT)) {
            branches.add(branch(ComboInput.LIGHT, key(V2Keys.LIGHT)));
        }
        if (V2ClientState.branchOpen(ComboInput.HEAVY)) {
            branches.add(branch(ComboInput.HEAVY, key(V2Keys.HEAVY)));
        }
        if (V2ClientState.branchOpen(ComboInput.GRAB) && V2ClientState.grabReady()) {
            branches.add(new Prompt(PromptPlate.GRAB, grabKeys, "Grab"));
        }
        if (V2ClientState.branchOpen(ComboInput.RUSH)) {
            branches.add(new Prompt(PromptPlate.TRAVEL, key(Bt3CombatClient.DRAGON_DASH), "Rush"));
        }
        if (!branches.isEmpty()) {
            drawRow(g, font, cx, bottom, branches, V2ClientState.windowFraction());
            return;
        }
        if (V2ClientState.homingOpen()) {
            drawRow(g, font, cx, bottom, List.of(new Prompt(PromptPlate.TRAVEL, key(mc.options.keyUp), "Chase")),
                    V2ClientState.homingFraction());
            return;
        }
        if (grabInReach(mc, locked, CombatStance.active())) {
            drawRow(g, font, cx, bottom, List.of(new Prompt(PromptPlate.GRAB, grabKeys, "Grab")), 0f);
        }
    }

    /**
     * Combat V3's indicators: same plates, same place, driven by the V3 state packet. The lock is
     * the server-approved one, so the row stays up for a target outside client entity tracking.
     */
    private static void renderV3(GuiGraphics g, Minecraft mc) {
        boolean locked = net.bullettrain.xenopixelsmod.client.combat.v3.V3ClientState.target() != null;
        if (!XenoClientConfig.showCombatPrompts(locked)) return;
        if (!(XenoClientConfig.bt3CombatClient && XenoServerClientState.combat())) return;
        var state = net.bullettrain.xenopixelsmod.client.combat.v3.V3ClientState.state();
        if (state == null) return;
        var row = net.bullettrain.xenopixelsmod.client.combat.v3.V3Prompts.row(locked, state.state(),
                state.chargeTicks(), state.windowTicksLeft(), state.windowTicksTotal(), state.window());
        if (row.prompts().isEmpty()) return;
        List<Prompt> prompts = new ArrayList<>(row.prompts().size());
        for (var prompt : row.prompts()) {
            String key = switch (prompt.slot()) {
                case LIGHT -> key(V2Keys.LIGHT);
                case HEAVY -> key(V2Keys.HEAVY);
                case DASH -> key(Bt3CombatClient.DRAGON_DASH);
                case GRAB -> key(V2Keys.GUARD) + "+" + key(V2Keys.LIGHT);
                case CHASE -> key(mc.options.keyUp);
                case VANISH -> "2x" + key(mc.options.keyLeft) + "/" + key(mc.options.keyRight);
                case NONE -> "";
            };
            prompts.add(new Prompt(prompt.plate(), key, prompt.label()));
        }
        drawRow(g, mc.font, g.guiWidth() / 2, g.guiHeight() - BOTTOM_OFFSET, prompts, row.fraction());
    }

    /** A light or heavy branch, on the plate and under the name of what it leads to. */
    private static Prompt branch(ComboInput input, String key) {
        BranchFlavor flavor = V2ClientState.branchFlavor(input);
        return new Prompt(flavor == BranchFlavor.PUNCH ? PromptPlate.PUNCH : PromptPlate.FINISH,
                key, flavor.label());
    }

    /**
     * A hint only: is the locked target close enough that pressing grab would reach it?
     *
     * @param able the fighter could throw one at all: in the v2 stance, or bare-handed under the
     *             other controllers
     */
    private static boolean grabInReach(Minecraft mc, LivingEntity locked, boolean able) {
        return able && V2ClientState.grabReady()
                && mc.player.distanceTo(locked) <= V2ClientState.grabRange();
    }

    /**
     * Draws prompts side by side, centred on {@code cx}, with their lower edge on {@code bottom}
     * and one shared timer bar under them.
     *
     * @param fraction 0..1 of the time left; 0 draws no bar
     * @return the y of the top of the row, for anything drawn above it
     */
    private static int drawRow(GuiGraphics g, Font font, int cx, int bottom, List<Prompt> prompts, float fraction) {
        int count = prompts.size();
        String[] shapes = new String[count];
        String[] texts = new String[count];
        int total = GAP * (count - 1);
        int rowHeight = 0;
        for (int i = 0; i < count; i++) {
            Prompt p = prompts.get(i);
            texts[i] = fitted(font, p);
            shapes[i] = XenoAtlasSprites.combatPrompt(p.plate().kind(), p.plate().widthFor(font.width(texts[i])));
            total += XenoAtlasSprites.get(shapes[i], XenoAtlasSprites.Theme.GREEN).width();
            rowHeight = Math.max(rowHeight, p.plate().height());
        }
        int plateBottom = bottom - (fraction > 0f ? TIMER_HEIGHT + 1 : 0);
        int top = plateBottom - rowHeight;
        int x = cx - total / 2;
        for (int i = 0; i < count; i++) {
            Prompt p = prompts.get(i);
            PromptPlate plate = p.plate();
            int width = XenoAtlasSprites.get(shapes[i], XenoAtlasSprites.Theme.GREEN).width();
            int y = plateBottom - plate.height();
            XenoAtlasSprites.blit(g, shapes[i], XenoAtlasSprites.Theme.GREEN, x, y);
            drawText(g, font, p, texts[i], x + plate.textX(width, font.width(texts[i])), y + plate.textY());
            x += width + GAP;
        }
        if (fraction > 0f) drawTimer(g, cx - total / 2, plateBottom + 1, total, fraction);
        return top;
    }

    /**
     * The line a prompt shows: "KEY Move", or the move alone when a key rebound to something long
     * would not fit on even the widest plate.
     */
    private static String fitted(Font font, Prompt p) {
        if (p.key().isEmpty()) return p.label();
        String full = p.key() + " " + p.label();
        int room = p.plate().textRoom(XenoAtlasSprites.maxCombatPromptWidth());
        return font.width(full) <= room ? full : p.label();
    }

    private static void drawText(GuiGraphics g, Font font, Prompt p, String text, int x, int y) {
        if (p.key().isEmpty() || !text.startsWith(p.key() + " ")) {
            g.drawString(font, text, x, y, LABEL_COLOR, true);
            return;
        }
        String key = p.key() + " ";
        g.drawString(font, key, x, y, KEY_COLOR, true);
        g.drawString(font, p.label(), x + font.width(key), y, LABEL_COLOR, true);
    }

    private static void drawTimer(GuiGraphics g, int x, int y, int width, float fraction) {
        g.fill(x, y, x + width, y + TIMER_HEIGHT, TIMER_TRACK);
        int lit = Math.round(width * Math.max(0f, Math.min(1f, fraction)));
        // Shrinks toward the centre, so the bar reads as closing in rather than draining away.
        int inset = (width - lit) / 2;
        g.fill(x + inset, y, x + inset + lit, y + TIMER_HEIGHT, fraction < 0.3f ? TIMER_LOW : TIMER);
    }

    /** The same short key names the rest of the HUD prints: LMB, RMB, E, B. */
    private static String key(KeyMapping mapping) {
        return XenoCooldownHudOverlay.keyLabel(mapping);
    }
}
