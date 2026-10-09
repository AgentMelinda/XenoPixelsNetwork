# Runtime proof — DMZ Form Studio

Captured 2026-09-11, 03:50–03:51, from `./gradlew runClient` on branch `1.21.1` at
`3fa456a` + the uncommitted Form Studio work. DragonMineZ 2.1.3, My NPCs 1.5.0, NeoForge
21.1.248. Screens are the **My NPCs** variant (`ConditionalMixinPlugin` logged
`customnpcs=false, mynpcs=true`), reached from the NPC editor's `DMZ` tab → `Forms`.

`page-NN-of-12.png` are consecutive pages of the Create Normal Form editor for a fresh draft.
`page-01-of-ai.png` is the native **Stats** tab (added separately, after those ten).

## What these establish

| Evidence | Seen on |
|---|---|
| Editor opens inside the native `DMZ` tab, no extra top-level tab | all |
| Identity fields and draft gating — `Ready to create` in green | page 01 |
| Separate internal ids vs player-facing names (`custom_form` / `Custom Form`) | page 01 |
| Localisation entries, form **and** group (`Name Locales JSON`, `Group Name`, `Group Locales JSON`) | pages 01–02 |
| Per-form and form-type icon fields defaulting to `xeno_form_01` | page 02 |
| TP costs, native masters, CustomNPC trainers, and the `Trainers…` sub-screen button | page 02 |
| Boolean settings as Yes/No buttons (`keep Base Form Hea`, `has Lightnings`, `form Stackable`) | pages 03, 06, 10 |
| Colour fields with a `Pick` button and a live swatch — red for `#FF0000`, white for `#FFFFFF` | pages 03–06 |
| Uniform body size plus per-axis `Body Size X/Y/Z`, all `0.9375` (DragonMineZ's own default) | pages 02, 06 |
| Every stat multiplier, drain, mastery and cost field | pages 07–09 |
| Requisites, stackability, stack drain, incompatibilities (`["ultimate.ultimate"]`) | pages 09–10 |
| Stats tab keeps the three DMZ values rendered and relabelled `(DMZ)` instead of blanked | `page-01-of-ai` |
| Stats tab carries the synchronized `KI Weapon` Yes/No button | `page-01-of-ai` |

12 pages total, so every serialised `FormConfig.FormData` field in dragonminez-2.1.3 is reachable.

## Known layout defects visible here

These are real and were fixed after the capture; the images predate the fix.

1. **The seventh row collides with the button bar.** The last field on every page is drawn
   behind `< Page` / `Page >` and the three Yes/No toggles — clearest on page 01
   (`Group Name (en_us)` / `Custom Group`) and page 02 (`Body Size (all)` / `0.9375`). Seven rows
   at a 22 px pitch ended 10 px below where the button row starts.
2. **Labels truncate at 18 characters**: `unlock On Skill Le`, `keep Base Form Hea`,
   `max Cost Multiplie`, `form Requisite Typ`, `mastery Per Hit De`, `Native Masters JS0`,
   `Custom Trainers JS`.
3. **The Stats tab's KI Weapon row sat on top of Combat Regen** (`page-01-of-ai`): its label and
   button were placed at fixed `guiTop + 173` / `guiTop + 168`, which is that row. It is now
   anchored to the Health Regen field at runtime and placed one row below it, so it cannot
   collide in either CustomNPCs or My NPCs.

## Not shown

No form was created, saved, previewed, or loaded here, and the radial / skills screens were never
opened — so the three `compat.dmz` mixins still had not class-loaded at capture time.
