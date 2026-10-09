package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
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

class XenoSpecializedItemsTest {
    @Test void factoryExposesActualArmorSlotMaterialAndBlockIdentity() {
        var armor = assertInstanceOf(IItemArmor.class, XenoApiAdapters.wrap(new ItemStack(Items.DIAMOND_HELMET)));
        assertEquals(EquipmentSlot.HEAD, armor.getSlotType());
        assertEquals(3, armor.getArmorSlot());
        assertEquals("minecraft:diamond", armor.getArmorMaterial());
        assertEquals(0, assertInstanceOf(IItemArmor.class,
                XenoApiAdapters.wrap(new ItemStack(Items.IRON_BOOTS))).getArmorSlot());
        var block = assertInstanceOf(IItemBlock.class, XenoApiAdapters.wrap(new ItemStack(Items.STONE, 3)));
        assertEquals("minecraft:stone", block.getBlockName());
        assertInstanceOf(IItemBlock.class, block.copy());
        assertInstanceOf(IItemBlock.class, block.split(1));
        assertEquals(2, block.getStackSize());
    }

    @Test void writablePagesAreLiveAndCopiesRemainSpecializedAndIndependent() {
        ItemStack stack = new ItemStack(Items.WRITABLE_BOOK);
        var book = assertInstanceOf(IItemBook.class, XenoApiAdapters.wrap(stack));
        book.setText(new String[] {"A first page", "A second page"});
        assertArrayEquals(new String[] {"A first page", "A second page"}, book.getText());
        assertEquals(2, stack.get(DataComponents.WRITABLE_BOOK_CONTENT).pages().size());
        var copy = assertInstanceOf(IItemBook.class, book.copy());
        copy.setText(new String[] {"Changed copy"});
        assertEquals("A first page", book.getText()[0]);
        assertEquals("", book.getAuthor());
        assertThrows(IllegalArgumentException.class, () -> book.setAuthor("Goku"));
        assertThrows(IllegalArgumentException.class, () -> book.setTitle("Signed"));
        assertFalse(stack.has(DataComponents.WRITTEN_BOOK_CONTENT));
    }

    @Test void writtenMetadataEditsPreserveGenerationFiltersAndRichPages() {
        var stack = new ItemStack(Items.WRITTEN_BOOK);
        var page = new Filterable<Component>(Component.literal("original"), Optional.of(Component.literal("filtered")));
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(new Filterable<>("title", Optional.of("safe title")), "author", 2, List.of(page), false));
        var book = assertInstanceOf(IItemBook.class, XenoApiAdapters.wrap(stack));
        book.setAuthor("Vegeta");
        var afterAuthor = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        assertEquals(2, afterAuthor.generation());
        assertEquals(Optional.of("safe title"), afterAuthor.title().filtered());
        assertSame(page, afterAuthor.pages().getFirst());
        assertFalse(afterAuthor.resolved());
        book.setTitle("Training");
        assertEquals("Vegeta", book.getAuthor());
        assertEquals("Training", book.getTitle());
        assertEquals(2, stack.get(DataComponents.WRITTEN_BOOK_CONTENT).generation());
        book.setText(new String[] {"{\"text\":\"literal, not parsed\"}"});
        assertEquals("{\"text\":\"literal, not parsed\"}", book.getText()[0]);
        assertTrue(stack.get(DataComponents.WRITTEN_BOOK_CONTENT).resolved());
    }

    @Test void invalidPageAndMetadataChangesAreAtomic() {
        var stack = new ItemStack(Items.WRITTEN_BOOK);
        var book = assertInstanceOf(IItemBook.class, XenoApiAdapters.wrap(stack));
        book.setText(new String[] {"keep"});
        var before = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        assertThrows(IllegalArgumentException.class, () -> book.setText(new String[] {"replace", null}));
        assertThrows(IllegalArgumentException.class, () -> book.setText(new String[101]));
        assertThrows(IllegalArgumentException.class, () -> book.setText(new String[] {"a".repeat(1025)}));
        assertThrows(IllegalArgumentException.class, () -> book.setTitle("x".repeat(33)));
        assertThrows(IllegalArgumentException.class, () -> book.setAuthor("x".repeat(129)));
        assertSame(before, stack.get(DataComponents.WRITTEN_BOOK_CONTENT));
    }

    @Test void actualNashornCanInvokeSpecializedMethodsFromFactoryObjects() {
        var book = XenoApiAdapters.wrap(new ItemStack(Items.WRITTEN_BOOK));
        var armor = XenoApiAdapters.wrap(new ItemStack(Items.IRON_CHESTPLATE));
        var block = XenoApiAdapters.wrap(new ItemStack(Items.STONE));
        var result = net.bullettrain.xenopixelsmod.npc.script.NpcScriptEngines.current().run(
                "function check() { book.setTitle('Training'); book.setAuthor('Goku'); "
                        + "return book.getTitle() + '|' + book.getAuthor() + '|' + armor.getArmorSlot() + '|' + block.getBlockName(); }",
                net.bullettrain.xenopixelsmod.npc.script.NpcScriptScope.builder()
                        .put("book", book).put("armor", armor).put("block", block).build(), "check");
        assertTrue(result.ok(), result.describe());
        assertEquals("Training|Goku|2|minecraft:stone", result.value());
    }
}
