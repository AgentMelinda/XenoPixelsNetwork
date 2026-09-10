package net.bullettrain.xenopixelsmod.compat.npc.clone;

import net.bullettrain.xenopixelsmod.compat.npc.clone.NpcMigrationPlan.Action;
import net.bullettrain.xenopixelsmod.compat.npc.clone.NpcMigrationPlan.Step;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules that decide what happens to somebody's server world.
 *
 * <p>Worth testing properly rather than eyeballing: this runs automatically on world load, and the
 * guarantee that it never overwrites existing My NPCs data is the reason that is acceptable.
 */
class NpcMigrationPlanTest {

    private static Action actionFor(List<Step> steps, String path) {
        return steps.stream().filter(s -> s.path().equals(path)).findFirst()
                .map(Step::action).orElse(null);
    }

    @Test
    void aQuestIsConverted() {
        List<Step> steps = NpcMigrationPlan.plan(List.of("quests/Saiyan/1.json"), List.of());
        assertEquals(Action.CONVERT, actionFor(steps, "quests/Saiyan/1.json"));
    }

    @Test
    void aCloneIsConvertedRatherThanCopied() {
        // Clones are serialised entities and carry an id; everything else in the folder does not.
        List<Step> steps = NpcMigrationPlan.plan(List.of("clones/1/Goku.json"), List.of());
        assertEquals(Action.CONVERT, actionFor(steps, "clones/1/Goku.json"));
    }

    @Test
    void aDialogIsConverted() {
        List<Step> steps = NpcMigrationPlan.plan(List.of("dialogs/Villager/1.json"), List.of());
        assertEquals(Action.CONVERT, actionFor(steps, "dialogs/Villager/1.json"));
    }

    @Test
    void scriptsAreConvertedAndUnrelatedDataIsCopied() {
        List<Step> steps = NpcMigrationPlan.plan(
                List.of("scripts/global/main.js", "recipes.dat"), List.of());
        assertEquals(Action.CONVERT, actionFor(steps, "scripts/global/main.js"));
        assertEquals(Action.COPY, actionFor(steps, "recipes.dat"));
    }

    @Test
    void anExistingDestinationFileIsNeverOverwritten() {
        List<Step> steps = NpcMigrationPlan.plan(
                List.of("quests/Saiyan/1.json", "clones/1/Goku.json"),
                List.of("quests/Saiyan/1.json", "clones/1/Goku.json"));
        assertEquals(Action.SKIP, actionFor(steps, "quests/Saiyan/1.json"));
        assertEquals(Action.SKIP, actionFor(steps, "clones/1/Goku.json"));
    }

    @Test
    void aSecondRunDoesNothing() {
        List<String> source = List.of("quests/a.json", "clones/1/b.json", "recipes.dat");
        List<Step> first = NpcMigrationPlan.plan(source, List.of());
        assertEquals(3, NpcMigrationPlan.count(first, Action.COPY)
                + NpcMigrationPlan.count(first, Action.CONVERT));

        // After that run the destination holds everything the source did.
        List<Step> second = NpcMigrationPlan.plan(source, source);
        assertTrue(NpcMigrationPlan.nothingToDo(second));
        assertEquals(3, NpcMigrationPlan.count(second, Action.SKIP));
    }

    @Test
    void separatorsAndLeadingSlashesDoNotDefeatTheSkip() {
        // The source listing comes from a filesystem walk on Windows; the destination may not.
        List<Step> steps = NpcMigrationPlan.plan(
                List.of("quests\\Saiyan\\1.json"), List.of("/quests/Saiyan/1.json"));
        assertEquals(Action.SKIP, actionFor(steps, "quests/Saiyan/1.json"));
    }

    @Test
    void theOrderIsStable() {
        List<Step> steps = NpcMigrationPlan.plan(
                List.of("quests/b.json", "clones/1/a.json", "dialogs/c.json"), List.of());
        assertEquals(List.of("clones/1/a.json", "dialogs/c.json", "quests/b.json"),
                steps.stream().map(Step::path).toList());
    }

    @Test
    void emptyAndNullInputsAreHarmless() {
        assertTrue(NpcMigrationPlan.plan(List.of(), List.of()).isEmpty());
        assertTrue(NpcMigrationPlan.plan(null, null).isEmpty());
        assertTrue(NpcMigrationPlan.nothingToDo(List.of()));
    }
}
