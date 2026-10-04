package net.bullettrain.xenopixelsmod.combat.combo;

import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboRouteCatalogTest {

    @Test
    void rushComboIsFourBeatApproachWithReapproach() {
        ComboRoute route = ComboRouteCatalog.bySkillId("rushcombo");
        assertNotNull(route);
        assertEquals("xenopixelsmod:rush_combo", route.strikeId());
        assertEquals(4, route.beatStrikeIds().length);
        assertEquals("xenopixelsmod:rush_left", route.beatStrikeIds()[0]);
        assertEquals("xenopixelsmod:rush_right", route.beatStrikeIds()[1]);
        assertEquals("xenopixelsmod:rush_breaker", route.beatStrikeIds()[2]);
        assertEquals("xenopixelsmod:rush_finisher", route.beatStrikeIds()[3]);
        assertEquals(4, route.firstString().length);
        assertEquals(Bt3AnimationIntent.JAB_LEFT, route.firstString()[0]);
        assertEquals(Bt3AnimationIntent.HEAVY_FINISH, route.firstString()[3]);
        assertTrue(route.autoReapproach());
        assertEquals(4, route.secondString().length);
        assertEquals(1.2, route.finisherKnockback().horizontal(), 1.0e-6);
        assertEquals(0.35, route.finisherKnockback().up(), 1.0e-6);
        assertEquals(0.8, route.finisherKnockback().down(), 1.0e-6);
        assertFalse(route.chaseAfterKnockback());
    }

    @Test
    void liftComboLaunchesThenChasesWithoutReapproach() {
        ComboRoute route = ComboRouteCatalog.bySkillId("liftcombo");
        assertNotNull(route);
        assertEquals("xenopixelsmod:lift_combo", route.strikeId());
        assertTrue(route.firstString().length >= 3);
        assertEquals(Bt3AnimationIntent.FLYING_KICK,
                route.firstString()[route.firstString().length - 1]);
        assertFalse(route.autoReapproach());
        assertTrue(route.chaseAfterKnockback());
        assertEquals(0.4, route.finisherKnockback().horizontal(), 1.0e-6);
        assertEquals(0.9, route.finisherKnockback().up(), 1.0e-6);
        assertEquals(0.8, route.finisherKnockback().down(), 1.0e-6);
        assertEquals(1, route.secondString().length);
    }

    @Test
    void unknownSkillHasNoRoute() {
        assertEquals(null, ComboRouteCatalog.bySkillId("power"));
        assertEquals(null, ComboRouteCatalog.bySkillId(null));
    }
}
