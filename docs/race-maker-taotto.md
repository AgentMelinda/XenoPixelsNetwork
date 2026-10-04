# Race catalogs and Taotto

Updated: 2026-10-04. Available in XenoPixels 0.5.7-1.21.1.

Open `/xenomaker` and select Race Maker. Choose a race pack you created to edit its
catalog. Built-in DragonMineZ race packs remain read-only. Open Hair Editor from
that race and use **Save Pack** to save the current geometry and color as a race
hair style. Select that style in Race Maker; the **Race Hair** button in the
DragonMineZ character creation Hair tab cycles the same catalog.

**Add Body** generates a layered body texture for the selected gender and current
colors. This requires a layered custom race using its own model name or an empty
model name. An empty model name is set to the race ID so DragonMineZ resolves its
generated textures. Races using another model's textures are rejected. The client
reloads the generated resource pack after adding a body. Catalog files live under
`config/dragonminez/races/<race-id>/catalog/`; the generated resource pack lives
under `config/xenopixelsmod/race_assets/`.

Open **Taotto** from the maker hub or run `/xenomaker taotto`. Paint pixels on the
canvas, choose a body part, adjust scale, and drag the placement preview. The
**Fit paint** control centers the painted area on the selected part. New canvases
start fitted to the torso. Dragging ignores transparent margins, oversized paint
can pan across the part, and scaling preserves the painted area's center (0.5.8).
The draft appears in the maker's character preview before **Apply**. Apply saves one
overlay for your player and requests server synchronization. **Clear**, followed
by **Apply**, removes it. Placement is clipped to the selected part's front face.
This overlay is separate from DragonMineZ's existing tattoo preset selection.

Race, hair, and form previews temporarily use the selected race and current draft.
Form Maker includes the stat multipliers supported by DragonMineZ; its unsaved
appearance draft can be previewed without changing the global form registry.

Build, serialization, placement clipping, preview restoration, exact mixin
descriptors, and fresh client/server startup were checked. Interactive catalog
selection, painter rendering on every race/model, and multiplayer synchronization
have not been verified in a running game.
