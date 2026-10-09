# Item script custom NBT on Minecraft 1.21.1

`IItemStack.getNbt()` now returns a writable `INbt` view of the wrapped stack's
`minecraft:custom_data` component. This is separate from `getItemNbt()`, which
returns a detached serialized snapshot of the entire stack and requires server
registry access.

The [official CustomNPCs 1.18.2 contract](https://www.kodevelopment.nl/customnpcs/api/1.18.2/noppes/npcs/api/item/IItemStack.html)
distinguishes extra item NBT from the entire saved item. Legacy extra NBT also held
Minecraft's name and enchantment fields. On 1.21.1 those are separate components:
use `setCustomName`, `setLore`, `addEnchantment` and the item attribute methods for
built-in behavior. Writing legacy keys such as `CustomName`, `display` or
`Enchantments` into custom data preserves them as script data and does not change
the corresponding built-in components. This is a documented version boundary,
not a claim of complete legacy item NBT compatibility.

Reading does not create a component. Setters read the latest component and replace
it with a copied tag, preserving changes from another live view. Existing nested
compounds and compound list entries remain writable through `INbt`; a missing
compound returns a detached empty tag, preserving the existing adapter behavior.
`setCompound`, `setList` and `merge` copy source values. Invalid bounded strings,
arrays or mixed-type lists are rejected before committing the component.

`hasNbt()` is false for absent or empty custom data. `removeNbt()` removes only
custom data; name, lore, damage, enchantments and other item components are retained.
An existing root view follows later removal or replacement. Plain-data NBT handles
read from an item view are detached snapshots; mutate through the `INbt` setters
to persist changes. The raw Minecraft ItemStack handle remains unavailable.

Evidence on 2026-10-08: pinned cached Minecraft source confirms `CustomData.copyTag`,
`CustomData.set` and immutable copy-on-set behavior. Focused tests were added for
independent views, nested updates, list entries, invalid writes, copies and removal.
They have not yet been run for this slice; in-game script mutation remains unverified.
