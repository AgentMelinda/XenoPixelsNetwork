package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcProfileSavePacketTest {
    @Test
    void editorDraftCannotClearCommittedTransformState() {
        NpcCombatProfile authoritative = new NpcCombatProfile();
        authoritative.formGroup = "supersaiyan";
        authoritative.formId = "supersaiyan3";
        authoritative.stackGroup = "kaioken";
        authoritative.stackId = "kaioken4";
        authoritative.formPower = 3.5;
        authoritative.baseSize = 5;

        NpcCombatProfile edited = new NpcCombatProfile();
        edited.selectedFormGroup = "supersaiyan";
        edited.selectedFormId = "supersaiyan4";
        edited.strength = 9001;

        NpcProfileSavePacket.preserveRuntimeState(authoritative, edited);

        assertEquals("supersaiyan", edited.formGroup);
        assertEquals("supersaiyan3", edited.formId);
        assertEquals("kaioken", edited.stackGroup);
        assertEquals("kaioken4", edited.stackId);
        assertEquals(3.5, edited.formPower);
        assertEquals(5, edited.baseSize);
        assertEquals("supersaiyan4", edited.selectedFormId);
        assertEquals(9001, edited.strength);
    }

    @Test
    void sameNamedSsj4TargetKeepsItsSelectedGroup() {
        NpcCombatProfile authoritative = new NpcCombatProfile();
        authoritative.formGroup = "supersaiyan";
        authoritative.formId = "supersaiyan4";

        NpcCombatProfile edited = new NpcCombatProfile();
        edited.selectedFormGroup = "oozaru";
        edited.selectedFormId = "supersaiyan4";

        NpcProfileSavePacket.preserveRuntimeState(authoritative, edited);

        assertEquals("supersaiyan", edited.formGroup);
        assertEquals("supersaiyan4", edited.formId);
        assertEquals("oozaru", edited.selectedFormGroup);
        assertEquals("supersaiyan4", edited.selectedFormId);
    }
}
