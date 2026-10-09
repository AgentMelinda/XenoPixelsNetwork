package net.bullettrain.xenopixelsmod.combat.v3.technique;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class V3TechniqueSelectionTest {
    private static V3TechniqueDefinition definition(String id, String name, long start, String status) {
        return new V3TechniqueDefinition(id, name, name, "rush", 20, 10, 20, null, status,
                List.of(start), List.of(new V3Beat(V3Beat.Kind.END, 20, 0, "", 0)));
    }

    @Test void selectsAuthoredOccurrenceAndPreservesUnrelatedEntriesAndInput() {
        String first = "xenopixelsmod:bt3_first", authored = "xenopixelsmod:bt3_authored";
        var definitions = Map.of(first, definition(first, "Kamehameha", 1, "archetype_placeholder"),
                authored, definition(authored, "Kamehameha", 2, "reference_timed_unverified"));
        List<String> input = List.of("dmz:kamehameha", first, "custom", authored, authored);
        assertEquals(List.of("dmz:kamehameha", "custom", authored), V3TechniqueSelection.visible(input, definitions::get));
        assertEquals(5, input.size());
    }

    @Test void canonicalWinsOverSavedAliasRegardlessOfListOrder() {
        String id = "xenopixelsmod:bt3_first", alias = "xenopixelsmod:bt3_old";
        var definition = definition(id, "Meteor", 1, "archetype_placeholder");
        var definitions = Map.of(id, definition, alias, definition);
        assertEquals(List.of(id), V3TechniqueSelection.visible(List.of(alias, id), definitions::get));
        assertEquals(List.of(id), V3TechniqueSelection.visible(List.of(id, alias), definitions::get));
        assertEquals(List.of(alias), V3TechniqueSelection.visible(List.of(alias), definitions::get));
    }

    @Test void earliestOccurrenceWinsAndDifferentAttackNamesRemain() {
        String early = "xenopixelsmod:bt3_early", late = "xenopixelsmod:bt3_late", kick = "xenopixelsmod:bt3_kick";
        var definitions = Map.of(early, definition(early, "Meteor", 1, "archetype_placeholder"),
                late, definition(late, "Meteor", 2, "archetype_placeholder"),
                kick, definition(kick, "Kick", 3, "archetype_placeholder"));
        assertEquals(List.of(kick, early), V3TechniqueSelection.visible(List.of(late, kick, early), definitions::get));
    }
}
