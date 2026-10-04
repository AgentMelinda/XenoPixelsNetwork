package net.bullettrain.xenopixelsmod.client.npc.dialog;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.client.npc.speech.SpeechBubbleLayout;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a dialogue option can be clicked, and why it used to be the wrong place.
 *
 * <p>The hit rectangle was a <b>constant</b> 120x18 GUI pixels around each option's projected
 * centre. A bubble shrinks as the player backs away; a constant does not. Option centres sit about
 * 0.35 world metres apart, so on a 360-pixel-tall GUI at a 70 degree field of view they project
 * roughly 22 pixels apart at four metres, 18 at five, and 11 at eight - against a box 18 pixels
 * tall. Past about four and a half metres the boxes grew into one another and the second option
 * could not be selected at all.
 *
 * <p>That mattered little while a mouse cursor did the aiming and the player could see exactly
 * where it was. It matters a great deal now the crosshair aims, because the player cannot see the
 * box at all - only the bubble it is supposed to describe.
 */
class DialogueBubbleHitTest {

    /** Local bubble units to world metres, from {@code DialogueBubbleRenderer}. */
    private static final float BUBBLE_SCALE = 0.012f;

    /** A typical one-line bubble, in local units. */
    private static final float BUBBLE_HEIGHT = 26.0f;

    private static final int GUI_HEIGHT = 360;

    /** Vertical half-angle of the default field of view. */
    private static final double TAN_HALF_FOV = Math.tan(Math.toRadians(35.0));

    /** Where a world-space vertical offset from the eye line lands, in GUI pixels. */
    private static float projectY(double worldOffset, double distance) {
        return (float) (GUI_HEIGHT / 2.0
                - worldOffset * GUI_HEIGHT / (2.0 * distance * TAN_HALF_FOV));
    }

    /** The boxes for a stack of options, as they would project at {@code distance} metres. */
    private static List<DialogueBubbleRenderer.OptionHit> stackAt(int count, double distance) {
        List<DialogueBubbleRenderer.OptionHit> boxes = new ArrayList<>();
        float cursor = 0.0f;
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                cursor += DialogueBubbleLayout.GAP_BETWEEN;
            }
            // Local Y grows downward, so a larger local offset is a lower world position.
            double worldTop = -cursor * BUBBLE_SCALE;
            double worldBottom = -(cursor + BUBBLE_HEIGHT) * BUBBLE_SCALE;
            boxes.add(DialogueBubbleRenderer.boxFrom(i,
                    GUI_HEIGHT / 2.0f, projectY(worldTop, distance),
                    GUI_HEIGHT / 2.0f, projectY(worldBottom, distance),
                    3.0f));
            cursor += BUBBLE_HEIGHT;
        }
        return boxes;
    }

    private static boolean overlaps(DialogueBubbleRenderer.OptionHit a,
                                    DialogueBubbleRenderer.OptionHit b) {
        return a.x0() < b.x1() && b.x0() < a.x1() && a.y0() < b.y1() && b.y0() < a.y1();
    }

    @Test
    void optionBoxesNeverOverlapAtAnyConversationDistance() {
        // The table from the bug report, asserted. The old constant box failed 5 m and beyond.
        for (double distance : List.of(2.0, 3.0, 4.0, 5.0, 8.0, 16.0, 30.0)) {
            List<DialogueBubbleRenderer.OptionHit> boxes = stackAt(4, distance);
            for (int i = 1; i < boxes.size(); i++) {
                assertFalse(overlaps(boxes.get(i - 1), boxes.get(i)),
                        "options " + (i - 1) + " and " + i + " overlap at " + distance + " m");
            }
        }
    }

    @Test
    void aBoxShrinksWithDistanceTheWayTheBubbleDoes() {
        // The whole point: the box has to track the art. A constant cannot.
        float near = height(stackAt(1, 3.0).get(0));
        float far = height(stackAt(1, 12.0).get(0));
        assertTrue(far < near, "the box must shrink as the bubble does");
        // Four times the distance, roughly a quarter the size.
        assertTrue(far < near / 3.0f, "near " + near + " vs far " + far);
    }

    private static float height(DialogueBubbleRenderer.OptionHit hit) {
        return hit.y1() - hit.y0();
    }

    @Test
    void aBoxIsNeverZeroAreaHoweverFarAway() {
        // A degenerate box is one nothing can ever hit, including by accident.
        DialogueBubbleRenderer.OptionHit far = stackAt(1, 1000.0).get(0);
        assertTrue(far.usable(), "a very distant option still has some area");
        assertTrue(height(far) > 0.0f);
    }

    @Test
    void theBoxFollowsTheSpriteAspectRatherThanAFixedWidth() {
        DialogueBubbleRenderer.OptionHit wide =
                DialogueBubbleRenderer.boxFrom(0, 100, 100, 100, 120, 4.0f);
        DialogueBubbleRenderer.OptionHit narrow =
                DialogueBubbleRenderer.boxFrom(0, 100, 100, 100, 120, 1.0f);
        assertTrue(wide.x1() - wide.x0() > narrow.x1() - narrow.x0(),
                "a wider sprite should give a wider target");
        assertEquals(height(wide), height(narrow), "and the same height either way");
    }

    @Test
    void anOffScreenOptionIsNotSelectable() {
        DialogueBubbleRenderer.OptionHit none = DialogueBubbleRenderer.noHit(3);
        assertFalse(none.usable());
        assertFalse(none.contains(0, 0), "a rectangle covering nothing must contain nothing");
    }

    @Test
    void theStackGapClearsTheTail() {
        // Every bubble sprite ends in a tail. The old three-pixel gap left each tail almost
        // touching the bubble below it, which is the crowding the player reported.
        float tail = BUBBLE_HEIGHT * SpeechBubbleLayout.TAIL_HEIGHT_FRACTION;
        assertTrue(DialogueBubbleLayout.GAP_BETWEEN > tail,
                "gap " + DialogueBubbleLayout.GAP_BETWEEN + " should clear a tail of " + tail);
    }

    // ------------------------------------------------------------ input routing

    @Test
    void eitherMouseButtonAnswers() throws IOException {
        String input = source("client/npc/dialog/DialogueBubbleInput.java");
        assertTrue(input.contains("!event.isAttack() && !event.isUseItem()"),
                "both buttons should reach the picker");
    }

    @Test
    void aClickIsConsumedEvenWhenItHitsNothing() throws IOException {
        // Otherwise a stray click punches the NPC in the middle of its own sentence. This is the
        // one place the usual "sit alongside vanilla input" rule is deliberately not followed.
        String input = source("client/npc/dialog/DialogueBubbleInput.java");
        int cancel = input.indexOf("event.setCanceled(true)");
        int pick = input.indexOf("pickAtCrosshair()");
        assertTrue(cancel >= 0 && pick >= 0);
        assertTrue(cancel < pick, "the click is consumed before the pick can fail");
    }

    @Test
    void vanillaInputIsUntouchedWhenNoConversationIsOpen() throws IOException {
        String input = source("client/npc/dialog/DialogueBubbleInput.java");
        int at = input.indexOf("public static void onClick");
        assertTrue(at >= 0, "the handler should exist");
        String body = input.substring(at, input.indexOf("pickAtCrosshair", at));
        assertTrue(body.contains("session == null || !usesCrosshair()"),
                "no session means the event is left entirely alone");

        int guard = body.indexOf("return;");
        int cancel = body.indexOf("setCanceled");
        assertTrue(guard >= 0, "there should be an early return");
        assertTrue(cancel >= 0, "and a cancel after it");
        assertTrue(guard < cancel, "the guard must come first, or vanilla clicks are eaten");
    }

    @Test
    void noScreenIsOpenedOnTheCrosshairRoute() throws IOException {
        // The whole request: the conversation must not take the mouse.
        String screen = source("client/npc/dialog/DialogueBubbleScreen.java");
        int at = screen.indexOf("public static void open(");
        String body = screen.substring(at, at + 600);
        assertTrue(body.contains("dialogueCrosshair"), "the route is chosen here");
        assertTrue(body.indexOf("return;") < body.indexOf("setScreen"),
                "and the crosshair route returns before any screen is opened");
    }

    @Test
    void bothRoutesShareOneDecisionAboutWhatAnOptionDoes() throws IOException {
        // Two copies would drift, and the half most likely to drift is what reaches the server.
        String screen = source("client/npc/dialog/DialogueBubbleScreen.java");
        String input = source("client/npc/dialog/DialogueBubbleInput.java");
        assertTrue(screen.contains("DialogueBubbleChoice.choose("));
        assertTrue(input.contains("DialogueBubbleChoice.choose("));
        assertFalse(screen.contains("new XenoNpcDialoguePacket("),
                "the screen should no longer build the packet itself");
    }

    @Test
    void theServerStillDecidesWhatAQuestOptionDoes() throws IOException {
        // Moving the input must not move the authority. The client sends the canonical server index;
        // the server re-reads its own dialogue.
        String choice = source("client/npc/dialog/DialogueBubbleChoice.java");
        assertTrue(choice.contains("new XenoNpcDialoguePacket(\n                        session.entityId(), session.nodeId(), optionIndex)"));
        assertTrue(choice.contains("serverOptionIndex(session, index)"),
                "filtered offers must retain their original option identity");
    }

    @Test
    void theOlderScreenIsStillReachable() throws IOException {
        // A replaced route stays switchable rather than being deleted.
        String config = source("client/config/XenoClientConfig.java");
        assertTrue(config.contains("dialogueCrosshair"));
        assertTrue(config.contains("d.dialogueCrosshair = dialogueCrosshair;"),
                "and the flag must actually persist, or 'turn it off' is not true");
        assertTrue(config.contains("d.dialogueBubbles = dialogueBubbles;"),
                "dialogueBubbles was never written to the config file either");
    }

    @Test
    void theInputHandlerIsClientOnly() throws IOException {
        // A dedicated server must never load a class that reaches Minecraft.getInstance().
        String input = source("client/npc/dialog/DialogueBubbleInput.java");
        assertTrue(input.contains("value = Dist.CLIENT"),
                "the subscriber must be client-gated");
    }

    private static String source(String relative) throws IOException {
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative);
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
