# UI Studio — implemented vs still needed

Updated: 2026-09-14 · Target: MC 1.21.1 / NeoForge 21.1.248 / mod id `xenopixelsmod`

This is an inventory of the NeoForge adapter in this jar, not a redesign of
[`XENOPIXELS_UI_STUDIO_CONCEPT.md`](../XENOPIXELS_UI_STUDIO_CONCEPT.md). That file stays the
design target. Claims below are **repo source** only: they are not in-world proof.

## Implemented (code in tree)

- **Document model** under `src/main/java/net/bullettrain/xenopixelsmod/ui/`: `UiDocument`,
  `UiAnchor`, `UiNode` / `UiNodeType`, `UiLayoutEngine`, `UiDocumentValidator`, `UiDocumentIO`.
- **Runtime** `UiRuntime` loads packs from `config/xenopixelsmod/ui/`, seeds bundled
  `demo_hud` and `demo_screen`, and keeps the overlay **off** until `/xenoui enable true`.
- **Bindings allow-list** in `UiBindings` (unknown keys fail validation instead of drawing zero).
- **Vanilla-chrome editor** `UiStudioScreen`: drag, resize, snap, inspector, undo. This is **not**
  the concept PNG docking IDE.
- **Client commands:** `/xenoui studio|reload|enable|hud|screen`.

## Still needed (concept, not this adapter)

- Fabric export
- Docking IDE / layers / module graph
- Minimap / compass / quest widgets
- Live multi-scale preview product
- Addon component registry
- Separate Studio vs runtime jars
- Theme system as specified in the concept doc
