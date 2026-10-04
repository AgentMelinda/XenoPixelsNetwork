package net.bullettrain.xenopixelsmod.client.npc.dialog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialogueBubbleLayoutTest {
    @Test
    void verticalChoicesStackUpFromTheNpcAnchor() {
        assertEquals(List.of(-69, -40, -20),
                DialogueBubbleLayout.stackTops(List.of(20, 11, 20)));
    }

    @Test
    void rowChoicesUseTwoColumnsAndAlignEachRowsTailAtTheNpcAnchor() {
        assertEquals(List.of(-59, -50, -30),
                DialogueBubbleLayout.rowTops(List.of(20, 11, 30)));
        assertEquals(List.of(-59, -50, -30, -20),
                DialogueBubbleLayout.rowTops(List.of(20, 11, 30, 20)));
    }

    @Test
    void twoColumnAnswersSitOnEitherSideOfTheNpc() {
        assertEquals(List.of(-54.5f, 54.5f, 0.0f),
                DialogueBubbleLayout.rowCentres(List.of(100, 100, 80)));
    }

    @Test
    void emptyChoiceLayoutsStayEmpty() {
        assertEquals(List.of(), DialogueBubbleLayout.stackTops(List.of()));
        assertEquals(List.of(), DialogueBubbleLayout.rowTops(List.of()));
    }

    @Test
    void groupHeightAccountsForStackAndPairedRows() {
        assertEquals(69, DialogueBubbleLayout.groupHeight(List.of(20, 11, 20), false));
        assertEquals(59, DialogueBubbleLayout.groupHeight(List.of(20, 11, 30), true));
    }
}
