package net.bullettrain.xenopixelsmod.npc;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XenoNpcRoleDefinitionTest {
    @Test
    void everyBuiltInRoleDefinitionValidates() throws Exception {
        for (XenoNpcRole role : XenoNpcRole.values()) {
            String path = "/data/xenopixelsmod/xeno_npcs/roles/" + role.id() + ".json";
            try (var stream = XenoNpcRoleDefinitionTest.class.getResourceAsStream(path)) {
                if (stream == null) throw new AssertionError("Missing " + path);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                XenoNpcRoleDefinition definition = XenoNpcRoleDefinition.fromJson(json, role);
                assertEquals(role, definition.role());
                assertEquals(XenoNpcRoleDefinition.SCHEMA_VERSION, definition.schema());
            }
        }
    }

    @Test
    void rejectsWrongBrainAndRunawayDecisionIntervals() {
        assertThrows(IllegalArgumentException.class, () -> XenoNpcRoleDefinition.fromJson(
                JsonParser.parseString("""
                        {"schema":1,"role":"guard","brain":"v4","body_family":"humanoid",
                         "decision_ticks":{"combat":8,"active":20,"idle":40}}
                        """), XenoNpcRole.GUARD));
        assertThrows(IllegalArgumentException.class, () -> XenoNpcRoleDefinition.fromJson(
                JsonParser.parseString("""
                        {"schema":1,"role":"guard","brain":"v5","body_family":"humanoid",
                         "decision_ticks":{"combat":0,"active":20,"idle":40}}
                        """), XenoNpcRole.GUARD));
    }
}
