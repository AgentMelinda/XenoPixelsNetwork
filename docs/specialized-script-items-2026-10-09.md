# Specialized script items

Implemented on 2026-10-09 against the pinned Minecraft 1.21.1/NeoForge 21.1.248 source.

`XenoAPI` item factories and every existing native item-wrapping path now return a specific
adapter for armor, writable/written books and block items. `copy()` and `split()` retain it.

```js
var book = XenoAPI.getIWorld("minecraft:overworld").createItem("minecraft:written_book", 1);
book.setText(["Training with Goku", "Charged punch"]);
book.setTitle("Training");
book.setAuthor("Goku");
book.getNbt().setInteger("trainingLevel", 3);
```

Book pages are literal text. Page changes are limited to 100 pages and 1024 characters per page;
the whole request is validated before changing the component. Written-book generation and
unrelated metadata survive changes. Title length is limited to 32 and author length to 128.
Writable books have no native title/author component: those setters refuse instead of writing
metadata Minecraft would ignore. Creating a written book explicitly supports all setters.

Armor material is its registered resource id, such as `minecraft:iron`, and its slot comes
from the actual ArmorItem. Block items report their block's registry id, which can differ
from the item's id. Scripts still cannot obtain a raw ItemStack through `getMCItemStack()`.

Focused Java and real Nashorn tests passed on 2026-10-09. No live game book editing,
inventory synchronization, or dedicated-server script execution has been observed yet.
