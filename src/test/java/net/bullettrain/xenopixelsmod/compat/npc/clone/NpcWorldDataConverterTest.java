package net.bullettrain.xenopixelsmod.compat.npc.clone;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcWorldDataConverterTest {
    @Test
    void scriptsOnlyRewriteKnownNamespacesAndPackages() {
        String source = "Java.type('noppes.npcs.api.NpcAPI');\nvar id='customnpcs:npcscripted';\nvar untouched='customnpc';";
        String converted = NpcWorldDataConverter.convertScript(source);
        assertTrue(converted.contains("espi.mynpcs.api.NpcAPI"));
        assertTrue(converted.contains("mynpcs:npcscripted"));
        assertTrue(converted.contains("untouched='customnpc'"));
    }

    @Test
    void structuredDataKeepsIdsAndNormalizesPointRewards() {
        CompoundTag source = new CompoundTag();
        source.putInt("Id", 42);
        source.putString("Command", "dmzpoints add 5000 {RefPlayer}");
        ListTag options = new ListTag();
        options.add(StringTag.valueOf("customnpcs:dialog_option"));
        source.put("Options", options);

        CompoundTag converted = NpcWorldDataConverter.convertStructured(source);
        assertEquals(42, converted.getInt("Id"));
        assertEquals("dmzpoints add 5000 @dp", converted.getString("Command"));
        assertEquals("mynpcs:dialog_option", converted.getList("Options", 8).getString(0));
    }
}
