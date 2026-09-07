# CustomNPCs legacy script compatibility

XenoPixels adds a small runtime compatibility layer to the concrete CustomNPCs entity wrappers.
It does not replace or modify the published CustomNPCs Java interfaces.

## Restored script methods

All entity wrappers receive:

- `getDistanceTo(entity)`
- `getSurroundingEntities(range)` and `getSurroundingEntities(range, type)`
- `getTempData`, `setTempData`, `hasTempData`, `removeTempData`, and `clearTempData`
- `getStoredData`, `setStoredData`, `hasStoredData`, `removeStoredData`, and `clearStoredData`
- `getRider` and `setRider`

Living-entity wrappers also receive:

- `getMinecraftEntity`
- `swingHand`
- `getHeldItem` and `setHeldItem`

`getDistanceTo(null)` returns positive infinity so a disappeared target fails ordinary range
checks instead of terminating the NPC tick script.

The original 1.7.10 wrapper source inspected for this compatibility work did not contain a
`getDistanceTo` method. That method is supplied as a commonly expected convenience, while the
other methods above are aliases for older wrapper names that have direct equivalents in the
current API.

Older NPC-specific display, inventory, AI, and combat-stat setters are not restored here. Their
modern replacements have different types and behavior, so silently emulating them would be more
likely to corrupt NPC configuration than to provide safe compatibility.
