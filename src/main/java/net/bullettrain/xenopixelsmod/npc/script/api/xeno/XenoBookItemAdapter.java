package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import xenoapi.npcs.api.item.IItemBook;

/** Real 1.21.1 book components; page strings are literal text, never executable JSON components. */
public final class XenoBookItemAdapter extends XenoItemAdapter implements IItemBook {
    static final int MAX_AUTHOR = 128;
    public XenoBookItemAdapter(ItemStack stack) {
        super(stack);
        if (!isBook()) throw new IllegalArgumentException("IItemBook requires a written or writable book");
    }

    @Override public String[] getText() {
        if (stack.is(Items.WRITTEN_BOOK)) return written().getPages(false).stream()
                .map(Component::getString).toArray(String[]::new);
        return stack.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY)
                .getPages(false).toArray(String[]::new);
    }

    @Override public void setText(String[] pages) {
        if (pages == null || pages.length > WritableBookContent.MAX_PAGES)
            throw new IllegalArgumentException("IItemBook.setText requires at most 100 non-null pages");
        // Validate the entire operation before replacing the immutable component.
        List<String> text = new ArrayList<>(pages.length);
        for (String page : pages) {
            if (page == null || page.length() > WritableBookContent.PAGE_EDIT_LENGTH)
                throw new IllegalArgumentException("IItemBook.setText pages must be non-null and at most 1024 characters");
            text.add(page);
        }
        if (stack.is(Items.WRITTEN_BOOK)) {
            var old = written();
            List<Filterable<Component>> content = text.stream()
                    .map(page -> Filterable.<Component>passThrough(Component.literal(page))).toList();
            stack.set(DataComponents.WRITTEN_BOOK_CONTENT,
                    new WrittenBookContent(old.title(), old.author(), old.generation(), content, true));
        } else {
            stack.set(DataComponents.WRITABLE_BOOK_CONTENT,
                    new WritableBookContent(text.stream().map(Filterable::passThrough).toList()));
        }
    }

    private WrittenBookContent written() {
        return stack.getOrDefault(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.EMPTY);
    }
    private void requireWritten(String method) {
        if (!stack.is(Items.WRITTEN_BOOK))
            throw new IllegalArgumentException(method + " requires a signed written book; writable books have no title or author component");
    }
    @Override public String getAuthor() { return stack.is(Items.WRITTEN_BOOK) ? written().author() : ""; }
    @Override public String getTitle() { return stack.is(Items.WRITTEN_BOOK) ? written().title().raw() : ""; }
    @Override public void setAuthor(String author) {
        requireWritten("IItemBook.setAuthor");
        if (author == null || author.length() > MAX_AUTHOR) throw new IllegalArgumentException("Book author must be at most 128 characters");
        var old = written();
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(old.title(), author, old.generation(), old.pages(), old.resolved()));
    }
    @Override public void setTitle(String title) {
        requireWritten("IItemBook.setTitle");
        if (title == null || title.length() > WrittenBookContent.TITLE_MAX_LENGTH)
            throw new IllegalArgumentException("Book title must be at most 32 characters");
        var old = written();
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(title), old.author(), old.generation(), old.pages(), old.resolved()));
    }
}
