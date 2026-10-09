package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import java.util.List;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptEngines;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptScope;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import org.junit.jupiter.api.Test;
import xenoapi.npcs.api.item.IItemArmor;
import xenoapi.npcs.api.item.IItemBlock;
import xenoapi.npcs.api.item.IItemBook;
import static org.junit.jupiter.api.Assertions.*;

class XenoSpecializedItemTest {
    @Test void signedBookChangesPreserveGenerationAndOtherMetadata() {
        var stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Original"), "Goku", 2,
                List.of(Filterable.passThrough(Component.literal("Original page"))), true));
        var book = (IItemBook) XenoApiAdapters.wrap(stack);
        book.setAuthor("Piccolo");
        assertEquals(2, stack.get(DataComponents.WRITTEN_BOOK_CONTENT).generation());
        assertArrayEquals(new String[]{"Original page"}, book.getText());
        book.setTitle("Training");
        book.setText(new String[]{"First", "{\"text\":\"literal\"}"});
        assertEquals("Piccolo", book.getAuthor());
        assertEquals("Training", book.getTitle());
        assertEquals(2, stack.get(DataComponents.WRITTEN_BOOK_CONTENT).generation());
        assertEquals("{\"text\":\"literal\"}", book.getText()[1]);
        assertTrue(stack.get(DataComponents.WRITTEN_BOOK_CONTENT).resolved());
        var copy = (IItemBook) book.copy();
        copy.setTitle("Copied");
        assertEquals("Training", book.getTitle());
    }

    @Test void invalidBookChangesDoNotCommitPartOfTheRequest() {
        var stack = new ItemStack(Items.WRITABLE_BOOK);
        var book = (IItemBook) XenoApiAdapters.wrap(stack);
        book.setText(new String[]{"Keep"});
        var original = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        assertThrows(IllegalArgumentException.class, () -> book.setText(new String[]{"Changed", null}));
        assertThrows(IllegalArgumentException.class, () -> book.setText(new String[]{"x".repeat(1025)}));
        assertThrows(IllegalArgumentException.class, () -> book.setText(new String[101]));
        assertThrows(IllegalArgumentException.class, () -> book.setAuthor("Goku"));
        assertThrows(IllegalArgumentException.class, () -> book.setTitle("Training"));
        assertSame(original, stack.get(DataComponents.WRITABLE_BOOK_CONTENT));
        assertFalse(stack.has(DataComponents.WRITTEN_BOOK_CONTENT));
        assertArrayEquals(new String[]{"Keep"}, book.getText());
    }

    @Test void armorAndBlockSpecializationsSurviveCopyAndSplit() {
        var armor = (IItemArmor) XenoApiAdapters.wrap(new ItemStack(Items.IRON_HELMET));
        assertEquals(EquipmentSlot.HEAD, armor.getSlotType());
        assertEquals("minecraft:iron", armor.getArmorMaterial());
        assertInstanceOf(IItemArmor.class, armor.copy());
        var blocks = (IItemBlock) XenoApiAdapters.wrap(new ItemStack(Items.STONE, 4));
        assertEquals("minecraft:stone", blocks.getBlockName());
        assertInstanceOf(IItemBlock.class, blocks.split(2));
        assertEquals(2, blocks.getStackSize());
    }

    @Test void specializedMethodsAreCallableInTheRealSandboxedScriptEngine() {
        var book = XenoApiAdapters.wrap(new ItemStack(Items.WRITTEN_BOOK));
        var armor = XenoApiAdapters.wrap(new ItemStack(Items.IRON_HELMET));
        var block = XenoApiAdapters.wrap(new ItemStack(Items.STONE));
        var result = NpcScriptEngines.current().run(
                "function run() { book.setText(['BT3']); book.setAuthor('Goku'); "
                        + "book.getNbt().setInteger('power', 9); return book.getText()[0] + ':' "
                        + "+ book.getNbt().getInteger('power') + ':' + armor.getArmorMaterial() "
                        + "+ ':' + block.getBlockName(); }",
                NpcScriptScope.builder().put("book", book).put("armor", armor).put("block", block).build(), "run");
        assertTrue(result.ok(), result.describe());
        assertEquals("BT3:9:minecraft:iron:minecraft:stone", result.value());
    }
}
