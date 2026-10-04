package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogTreeImportTest {
    @Test
    void joinsSlotNodesAndKeepsEachSlotAsAnAssignableStart() {
        CompoundTag start = dialog(1, "Welcome\ntraveler");
        ListTag options = new ListTag();
        options.add(option("Tell me more", 1, 2));
        start.put("Options", options);
        CompoundTag more = dialog(2, "Details");
        ListTag endOptions = new ListTag();
        endOptions.add(option("Goodbye", 0, -1));
        more.put("Options", endOptions);

        DialogTreeImport.Conversion conversion = DialogTreeImport.convert(
                Map.of(1, start, 2, more), new NpcImportReport(), "mynpcs_village");

        assertEquals(2, conversion.dialogues().size());
        DialogTreeImport.Imported first = conversion.dialogues().get(0);
        assertEquals("n1", first.dialogue().start());
        assertEquals("Welcome\ntraveler", first.dialogue().startNode().text());
        assertEquals("n2", first.dialogue().startNode().options().get(0).target());
        assertEquals("dialog_2", conversion.sourceSlots().get(2).id());
        assertEquals(XenoDialogue.OptionType.QUIT,
                conversion.dialogues().get(1).dialogue().startNode().options().get(0).type());
    }

    @Test
    void reportsInvalidSourceOptionsInsteadOfExecutingThem() {
        CompoundTag source = dialog(3, "Question");
        ListTag options = new ListTag();
        options.add(option("invalid", 2, 0));
        source.put("Options", options);
        NpcImportReport report = new NpcImportReport();

        DialogTreeImport.convert(Map.of(3, source), report, "customnpcs_village");

        assertTrue(report.notes().stream().anyMatch(note -> note.contains("invalid option type 2")));
    }

    @Test
    void reportsSourceAvailabilityRulesThatXenoCannotEnforce() {
        CompoundTag source = dialog(4, "Only at night");
        source.putBoolean("AvailabilityDayTime", true);
        NpcImportReport report = new NpcImportReport();

        DialogTreeImport.convert(Map.of(4, source), report, "mynpcs_town");

        assertTrue(report.notes().stream().anyMatch(note -> note.contains("availability conditions")));
    }

    private static CompoundTag dialog(int id, String text) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DialogId", id);
        tag.putString("DialogText", text);
        tag.put("Options", new ListTag());
        return tag;
    }

    private static CompoundTag option(String title, int type, int target) {
        CompoundTag option = new CompoundTag();
        option.putString("Title", title);
        option.putInt("OptionType", type);
        option.putInt("Dialog", target);
        CompoundTag row = new CompoundTag();
        row.put("Option", option);
        return row;
    }
}
