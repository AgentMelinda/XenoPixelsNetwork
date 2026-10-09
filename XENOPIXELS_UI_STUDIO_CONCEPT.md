# Xenopixels UI Studio — Ultimate Modular UI Concept

**Document:** combined specification  
**Status:** concept only. No implementation code.  
**Source:** merge of earlier concept drafts, the AdventureHUD studio mockup, and two later fragments (workflow / assets / roadmap / license).  
**Honesty rule:** this file describes a design target. It does not claim the screenshot is a shipping product, that Minecraft already has a layout engine, or that every listed widget is cheap. Large items are marked as such. Placeholder brand URLs from drafts are **not** live products.

---

## 0. One-sentence spec

Xenopixels UI Studio is a **visual authoring environment** that writes versioned UI packs; a **small Minecraft runtime** loads those packs as modular, themeable, data-bound HUDs and screens; a **typed module graph** only wires approved game data and registered actions; gameplay logic, networking, inventories, and server authority stay in real mod code.

---

## 1. What this product is

A creator should be able to:

1. Open Studio.
2. Create a UI project / document (`player_hud`, `skill_menu`, `quest_screen`).
3. Drag components onto a canvas.
4. Anchor, resize, group, and theme them.
5. Bind them to vanilla or mod data providers.
6. Define safe events and visibility conditions.
7. Preview at real resolutions and GUI scales.
8. Export a pack the runtime can load.
9. See that HUD or screen in Minecraft without writing layout Java.
10. Register new component types, providers, and actions from other mods.

It must serve two UI contexts, because the mockup mixes them:

| Context | When it exists | Input |
|---|---|---|
| **HUD overlay** | during gameplay | mostly pass-through; only marked widgets capture |
| **Screen** | inventory, config, dialogue, skill menu | modal or focus-based; blocks world input |

The reference image is an **Adventure HUD pack** (`AdventureHUD`, profile `Fabric 1.20+`) being edited: portrait, hearts, level, compass, minimap, quest tracker, discovery toast, hotbar, skill slots, hint toast. That pack is the flagship demo — not the whole framework.

---

## 2. What this product is not

- Not vanilla Minecraft GUI. Vanilla has no docking IDE, no CSS, no retained scene graph, no flexbox.
- Not a resource-pack recolorer. Resource packs restyle existing textures. This tool **authors new trees**.
- Not ModularUI / LDLib / BrokkGUI / PanelStudio by itself. Those are useful **runtimes or partial editors**. This concept is an **authoring product + runtime contract**.
- Not “build any mod with no code.” Studio removes **layout and presentation code**. Combat, quests, networking, containers, and persistence still need Java (or whatever the mod already uses).
- Not Bedrock.
- Not a license to run arbitrary user code inside the client. **No JavaScript, Lua, or eval in the runtime.**
- Not a promise that the painted 3D landscape in the mockup is the in-game editor camera on day one.
- Not a one-click “export a finished commercial mod JAR with obfuscation.” Export is a UI pack plus optional runtime dependency, not a whole adventure mod.
- Not VR/AR UI design, mobile Minecraft preview, or cross-game export in any near phase.
- Not a claim that a `xenopixels` website, Discord, or social account already exists. Those would be placeholders until the project is real.

---

## 3. Two products, always

Keep these separate in packaging and in your head.

### Studio (authors)

Project, canvas, library, layers, inspector, graph, themes, events, export, debug.

Studio may be:

- **A.** a client-only Minecraft screen (dev world / keybind / title button), or
- **B.** an external desktop app that exports packs and optionally live-previews through a running client.

Pick one as v1 and say it. Do not advertise both until one works.

**Recommendation:** Runtime always in-game. Studio v1 as a **Fabric client-only screen** built on one modern GUI toolkit (do not hand-roll docking with `drawTexture`). External Studio is Phase 3 if authors outgrow the in-game editor.

### Runtime (players)

Loads packs, builds the tree, binds data, renders, routes input, unloads cleanly.

Runtime ships to players. Studio does **not**. If you glue them into one jar, survival servers get an IDE.

Production runtime must not load: editor chrome, undo stacks, graph editor, selection handles, component library, profiler UI.

---

## 4. Design principles

1. **Modular.** Every element is a registered component with a typed contract.
2. **Data-driven.** Hierarchy, layout, style, bindings, events, and animation live in documents. Java defines *types* and *providers*, not each HUD.
3. **Resolution-aware.** Design pixels + anchors + containers. Not raw `scaledWidth` baked into packs.
4. **Themeable.** Behavior and skin are separate. One HealthBar, many themes.
5. **Connect is real.** An unbound bar is static art. Bindings are first-class.
6. **Safe.** Packs cannot execute arbitrary code or command the server.
7. **Extensible.** Other mods register components, providers, actions, styles.
8. **Honest about layers.** HUD, Screen, Overlay, Modal, Tooltip, Editor are different contexts.
9. **Client/server split is sacred.** Presentation is client. Gameplay mutations go through validated networking.
10. **Preview is the source of truth.** If it is wrong at GUI scale 2, it is wrong.
11. **Minecraft-looking by default.** Starter widgets use 16×16 / 32×32 pixel art, vanilla-like fonts, and pack-compatible textures. Authors can go elsewhere; the defaults should not look like a web admin panel.

---

## 4.1 Author workflow

This is the intended loop. If a step needs Java, the workflow says so.

1. **Project setup** — New project or template. Pick target loader + Minecraft version. Set baseline canvas (1920×1080, 16:9).
2. **Layout** — Drag from the library. Snap to grid. Set anchors. Parent in Layers.
3. **Style** — Theme tokens, nine-slice frames, local overrides. Not a new tree per skin.
4. **Bind** — Inspector first: `player.health` → bar fill. Graph later. Set update rate if the widget is expensive.
5. **Motion** — Enter / exit fade or toast slide. Preview it.
6. **Test** — Interaction preview. Simulate health loss / XP / quest complete with mock providers. Flip GUI scale and aspect. Debug bounds on.
7. **Export** — Runtime pack + report. Drop pack where the runtime looks. Open the document from the host mod. Play in a real world.

Git is the collaboration story for v1: text JSON, branch, review. Real-time multiplayer co-edit is not a phase of this product.

---

## 5. Studio workspace

Match the mockup’s job, not necessarily its pixel chrome.

| Region | Job |
|---|---|
| Top toolbar | project, modes, play, profile (`Fabric 1.20+`), settings |
| Component Library | searchable palette by category |
| Canvas | design surface + preview |
| Layers & Modules | tree, visibility, lock, z-order |
| Properties | typed inspector |
| Module Graph + Node Details | data/event wiring |
| Theme Preview | tokens and widget states |
| Status bar | grid, snap, scale, document, errors |

Panels should be resizable and hideable. Full docking is nice; it is not MVP. A fixed layout that works is better than a broken dock system.

### Work modes (must be real modes)

| Mode | Job |
|---|---|
| Components | place and edit tree |
| Preview | HUD over world or mock state; interact as a player |
| Themes | tokens, 9-slice, states |
| Events | conditions, keybinds, registered actions |
| Export | write pack + validation report |

Toolbar actions that matter: New, Open, Save, Undo, Redo, Copy, Paste, Duplicate, Group, Preview/Play, Reload.

Show always: project name, document name, canvas resolution, zoom, snap, grid, dirty flag.

---

## 6. Component model

Every instance has:

| Group | Contents |
|---|---|
| Identity | id, display name, type id, tags |
| Transform | x, y, w, h, anchor, pivot, z-order, optional rotation |
| Layout | margin, padding, min/max/preferred size, alignment |
| Visibility | visible, enabled, opacity, condition |
| Style | theme tokens + local overrides |
| Interaction | hover / click / focus / capture policy |
| Data | bindings, defaults, formatters |
| Animation | enter, exit, hover, state |
| Events | declared event list |

Rotation on HUD quads is extra cost. v1 may allow 0/90/180/270 only, or none.

### Hierarchy

Trees, not flat lists. Moving a parent moves children. Containers may own child layout.

Example:

```
HUD (Root)
├─ Player Info
│  ├─ Portrait
│  ├─ Health Bar
│  ├─ Energy Bar
│  └─ Level Text
├─ Compass
├─ Minimap
├─ Quest Tracker
├─ Hotbar
├─ Skill Slots
└─ Toasts
```

### Context flags on each type

Every library item must declare:

- HUD-safe vs Screen-only vs both
- default size and default anchor
- required data inputs
- editor icon

Do not drop a full Inventory Panel onto a HUD root without a visibility rule. Those are different Minecraft layers.

---

## 7. Component catalog

Not everything ships in v1. The list is the **framework vocabulary**.

### Basic

Text, Icon, Image, Rectangle, Panel / Window, Separator, Spacer.

### Controls

Button, Toggle, Checkbox, Slider, Dropdown, Text input, Scroll area, Tabs, Tooltip.

### Layout containers

Horizontal, Vertical, Stack, Grid, Flow (wrap), Scroll.

Containers are how lists and inventories stay usable when resolution changes. A studio that only has absolute x/y will fail at GUI scale 3.

### Minecraft presentation

Item icon (visual only), player head / portrait, XP bar display, armor / food / air indicators, status-effect row, key prompt, vanilla-style slot chrome.

### Adventure / mod presentation

Health bar, energy / mana / stamina / ki bar, skill slot + cooldown, quest tracker, party frame, target frame, boss frame, notification / discovery toast, ability wheel, vehicle gauge, waypoint / compass strip.

### Specialized (optional modules, not core)

Minimap, radar, world-space billboard (entity/block attached). These need world data and extra renderers. See §16.

### Explicitly not v1

Video players, arbitrary 3D model viewers, particle-authoring on the HUD, shader graph. Those are other products.

### Visual slot vs real inventory slot

A rendered item icon is easy.  
A **real gameplay slot** talks to a container/menu and must follow Minecraft’s legitimate sync rules.

Studio must not treat them as the same component.

---

## 8. Layout engine

Packs store **design pixels** at a stated baseline (mockup: 1920×1080, 16:9). Runtime resolves to the current window and GUI scale.

### Must support

- 9-point anchors + stretch-to-edges
- parent-relative offsets
- min / max size
- margins / padding
- alignment inside containers
- optional “scale with GUI scale” vs “HUD-independent scale”
- safe-area guides: vanilla hotbar, chat, boss bar, effects, debug, recipe book
- z-order: tree order plus layer buckets  
  `background → hud → overlay → popup → tooltip → modal`

### Dirty layout

Rebuild layout only when size, parent, text, children, or GUI scale change. Do not reflow the entire HUD every frame.

### Clipping

Scroll, lists, tab pages, framed minimaps. Use the platform scissor/stencil the version actually has.

### What you do not get from Minecraft

Flexbox, CSS grid, rich text HTML, automatic word-wrap as a platform feature (you implement wrap), a DOM.

---

## 9. Canvas and preview

### Editor canvas

- labeled resolution and aspect
- zoom / pan
- grid and snap (1 / 2 / 4 / 8 / 16)
- snap to grid, edges, centers, siblings, safe area
- select, move, resize, align, distribute, group
- multi-select (can slip to Phase 2 if needed)
- undo / redo for all of the above
- bounding boxes, anchors, parent outlines
- optional debug bounds

### Preview kinds

| Preview | What it is | Honesty |
|---|---|---|
| Editor | widgets on canvas | required |
| Mock world | static or captured scene behind HUD | what the screenshot shows; allowed |
| Live HUD | composite over the real client world | best; requires Studio in-client or a socket |
| Interaction | editor handles off; click as a player | required before export |
| Resolution | 16:9 / 16:10 / 21:9 / 4:3 | required |
| GUI scale | whatever the client actually supports | required |

A painted landscape is fine for marketing and for an external editor. A studio that never tests **real GUI scale** will ship overlapping chat and hotbar.

Play mode must freeze or ignore world input unless you intend authors to walk around with the inspector open.

---

## 10. Layers panel

Tree of the document.

Per row: name, type icon, visibility eye, lock, settings.

Must: select (syncs canvas + inspector), rename, reorder, parent / unparent, duplicate, delete, search.

Distinguish **hidden in editor** from **hidden at runtime**.

Also: drag-to-reorder changes draw order, group / ungroup, import / export a subtree as a template file. Search and filter by name or type.

---

## 11. Properties inspector

Tabs matching the mockup, plus layout:

- **General** — id, type, tags
- **Transform** — x, y, w, h, anchor, rotation policy
- **Layout** — margin, padding, min/max, container options
- **Visibility** — toggle + condition
- **Data** — bindings and type-specific fields (minimap radius, bar min/max, list source)
- **Style** — frame, accent, background, font, overrides
- **Events** — declared events → registered actions
- **Animation** — enter / exit / durations / easing

Bound fields must look different from literals.

Custom components describe their fields with inspector metadata (number + range, boolean, enum, color, style ref, text, resource location). That is how the Studio shows unknown types without hardcoding every mod widget.

---

## 12. Style, theme, and states

### Style

A named bundle a component can reference (`EnderSlate.SkillSlot`) with local overrides.

### Theme

A project asset: tokens + sprite ids + insets + text styles + spacing.

Tokens the mockup already implies: frame, accent, background, text, danger, success.

Starter theme set (data files, not hardcoded widgets):

- Vanilla
- Dark / Ender Slate
- Medieval (wood / stone frames)
- Modern (clean panels — still pixel-sliced, not CSS Material)
- Neon (glow as extra sprites, not a free shader graph)

Themes are data, not if-statements in HealthBar. Community theme packs are later distribution, not a built-in store.

### Nine-slice

Required for windows, buttons, dialogue, tooltips, frames. Corners must not stretch.

### States

Default, Hover, Pressed, Focused, Selected, Disabled, plus component-specific (Ready, Cooldown, Active, Locked, Warning, Critical).

Theme Preview must show a sample widget in those states — not only color chips.

### Runtime theme change

Swapping textures may require a resource reload. Do not promise instant CSS-like restyle of atlas sprites unless you built that path. Token colors can change without a reload. Be explicit per property.

### Textures

Use Minecraft resource locations. Crop, tile, stretch, nine-slice, optional animated sprite if the adapter supports it.

### Asset pipeline

Studio should browse **vanilla atlases** and project textures.

Import rules:

| Format | Studio | Runtime |
|---|---|---|
| PNG (power-of-two friendly, reasonable size) | yes | after copy into pack as a resource |
| Custom pixel frames / nine-slice metadata | yes | yes |
| GIF / APNG | editor preview only if you bother | convert to a sprite sheet or reject |
| JPG | convert to PNG or reject (lossy UI looks bad) | no raw JPG HUD sprites |
| MP4 / video | **no** in v1–v3 | Minecraft is not a video widget host |

Validate: size, format, missing file, illegal path. Auto-slice into an atlas is an optimization, not a reason to accept arbitrary desktop media.

Do not assume the runtime can load a file off the author’s Desktop at game time. Export **copies** legal assets into the pack.

---

## 13. Data providers and binding

Bindings connect component properties to named values. They serialize. They are not scripts. When bound data changes, the widget updates — that is the whole point. Each binding may declare an update policy (every tick, on change, every N ticks) so a minimap does not resample the world at full client tick if it cannot afford it.

### Built-in providers (vanilla-backed)

**PlayerData** — health, maxHealth, absorption, food, saturation, air, armor, xp, level, pose, gamemode, name, skin  
**WorldData** — dimension, biome, time, weather, light, difficulty  
**Position** — block pos, facing  
**HotbarData** — nine stacks, selected index, attack cooldown  
**EffectsData** — active status effects

### Registered providers (mods)

Vanilla has **no** quest list and **no** skill bar. Those exist only if a mod registers them.

Suggested contracts:

**QuestData** — id, title key, body key, state (locked/active/completed/failed), optional progress, optional world pos  
**WaypointData** — id, name, pos, icon, dimension  
**SkillData** — id, icon, keybind, cooldown ticks, usable  
**PartyData** — other players the client is allowed to know  
**Custom** — `Combat.ki`, `Vehicle.speed`, whatever the host mod exposes

Studio ships **mock providers** so Preview is not empty. Runtime swaps mocks for real registrations. If the adventure mod is absent, quest and skill widgets render empty-but-valid — they do not crash.

### Formatting (presentation only)

`85 / 100`, `85%`, `3.2s`, `X: 120 Y: 64 Z: -230`.

### Calculated values

A **tiny** expression layer is allowed later: `health / maxHealth`. It is not a programming language. No loops, no calls, no world mutation.

### Local UI variables

`selectedTab`, `expanded`, `currentPage`. These are client presentation state, not server stats.

---

## 14. Events, conditions, actions, security

### Events

On Click, Right Click, Hover, Hover Exit, Focus, Blur, Key, Value Changed, Visibility Changed, Open, Close, plus type-specific (On Waypoint).

### Conditions

Show transformation HUD if `TransformationActive`.  
Low-health style if `HealthPercent < 0.25`.  
Hide while a Screen is open, while F1 hide-HUD is on, while sleeping, while debug overlay is up.

Conditions are a constrained predicate list, not eval().

### Actions (allow-list)

Packs may only fire **registered** actions:

- open / close a UI document
- set a local UI variable
- change tab
- play animation
- emit a named client event
- request a **registered** mod action (ability use, open vanilla inventory)

The adventure mod implements the ability. The HUD does not.

### Forbidden in packs

- running commands
- giving items
- changing gamemode
- writing server state
- downloading remote layouts mid-game
- embedding Java / JS snippets

Client HUD packs are not a cheat console.

### Input policy

HUD default: **non-modal**. Clicks pass through empty pixels. Only opaque interactive widgets consume.

Screens: normal focus + modal stack.

Also required:

- one focused widget per context
- overlap resolved by visibility, enabled, z-order, clip, modal
- keybind conflict report (two skill slots on E)
- F1 still hides framework HUD
- opening a vanilla Screen must not leave ghost capture

Hint widgets (“Press E to open inventory”) are labels, not a second inventory keybind, unless the author bound a registered action.

---

## 15. Module graph (Connect)

The mockup’s graph is **data-flow and presentation wiring**, not a replacement for the mod.

Example nodes:

- Player Data → Health / Energy / Level
- Quest System → Active Quests
- HUD Manager → Initialize / Update / Render
- Health Bar ← Update Value
- Minimap ← Player Position, World Data
- Hotbar ← Slot Data
- Skill Slots ← Cooldowns

Rules:

- typed ports (number, text, item, vec3, bool, event, list, object)
- illegal wires fail validation
- Node Details shows inputs, outputs, description
- graph edits do not require a Java rebuild

**Phase rule:** the graph is Phase 3. v1 bindings can be chosen in the inspector (`player.health` → bar.value). Shipping a pretty graph with no type checker is worse than no graph.

v1 graph, when it exists, only **wires existing providers to existing widgets** and registered actions. It does not invent gameplay.

---

## 16. Hard Minecraft problems (do not bury these)

### Minimap

The selected widget in the mockup is a circular map with terrain color, player arrow, zoom, radius, waypoints, coordinates, biome tint, click events.

That is a map renderer.

Options:

1. Wrap vanilla map items — looks like vanilla maps.
2. Sample nearby chunks into a top-down color buffer — closer to the art; costs CPU/GPU; needs throttle.
3. Frame another minimap mod — fastest, not yours.
4. Fake a static image in Studio and omit runtime minimap in v1 — honest scope.

If AdventureHUD’s identity is that map, budget option 2 as its own milestone. Multiplayer players, caves, Nether ceilings, and claimed chunks are extra.

### Hotbar and XP bar

Choose one policy per pack and write it down:

- replace vanilla hotbar / XP
- hide vanilla and draw yours
- decorate around vanilla

Replacement owns offhand, attack indicator, spectator hotbar, and every mixin from AppleSkin, Raised, tooltip mods, RPG hotbars. Compatibility is the job.

### Quests and skills

Widgets. Not engines.

Studio can preview sample quests and skills. It must not become the quest database or the spellcaster.

### World-space UI

Billboards on entities, waypoint beams, world health bars — different renderer than screen HUD. Later, optional, separate.

---

## 17. Animation

v1: fade, slide on toasts, duration, simple easing. Triggers: open, close, hover, condition flip.

Later: pulse, shake, glow, progress lerp, optional timeline with keyframes.

Reduced-motion option for accessibility. Production should be able to disable expensive effects.

A timeline editor is not MVP.

---

## 18. Text, fonts, localization

- alignment, wrap, clip, max width, shadow, outline, color
- literal text **or** translation key **or** bound value
- Minecraft’s real font path for the target version
- custom fonts only where the version allows them

The mockup is English. Shipped packs cannot be.

---

## 19. Assets and project shape

A project contains:

- metadata (name, target loader, target version)
- UI documents
- templates / prefabs
- themes and styles
- animations
- assets (textures referenced as resource locations)
- editor-only layout (panel sizes, last zoom) — **not** shipped in the runtime pack

Asset browser with thumbnails when practical.

### Templates

Reusable subtrees: Player Frame, Quest Entry, Skill Slot, Toast.

### Repeater

A template instanced per list item (party, quests, buffs, notifications). Needed for any real RPG HUD. Phase 2.

### Variants

Same component, different visual config: skill slot ready / cooldown / locked.

### Prefab vs instance

Changing a template can update instances, or instances can detach. Pick a rule and document it.

---

## 20. Extensibility and plugins

Other mods register:

- component types (id, properties schema, renderer, input, editor preview)
- data providers
- actions
- styles / themes
- documents they want opened
- optional new **graph node types** (still typed; still no arbitrary code)
- optional export validators / pack post-processors

Studio discovers registrations and puts them in the library. Unknown type in a pack = error panel, not a crash.

Theme packs and template packs are data. They can be distributed as resource-pack-like folders or `.xpxui` libraries. A public marketplace is a **separate product**, not a Studio milestone.

### Scripting — rejected for runtime

Drafts proposed JavaScript / Lua with game API access and async ops. That contradicts the security rule.

- Runtime: **no** user scripts.
- Studio: may later use a locked expression language (`health / maxHealth`) already described.
- Complex logic: Java (or the host mod’s existing language) behind a registered action or provider.

If a future “Studio plugin” language exists, it runs in the **editor process only** and cannot be embedded into a pack players load.

---

## 21. Documents, packs, export

### Project format

Canonical project is a **folder** of text files, Git-friendly. Suggested extension for a zipped shareable project: `.xpxui`.

Do not invent a binary blob that cannot be diffed.

```
AdventureHUD.xpxui/
  project.json              # name, target loader, target MC version, canvas default
  documents/
    player_hud.json
    skill_menu.json
  graph/
    player_hud.graph.json   # optional; inspector bindings may live inside the document
  themes/
    ender_slate.json
  templates/
    quest_entry.json
    skill_slot.json
  assets/
    textures/               # copied into a resource-pack layout on export
    preview/                # editor-only mock scenes; not shipped to players
  editor/
    workspace.json          # panel layout, zoom, last selection — editor only
```

`project.json` / document JSON must be versioned. Old packs either migrate or fail with a clear format-version error.

### UI document

One HUD or one Screen: tree, layout, styles, bindings, events, animations.

### Export targets (honest)

| Target | What you actually get |
|---|---|
| **Runtime pack** | documents + theme + textures as a resource-pack-shaped folder the runtime reads |
| **Library embed** | the same pack inside another mod’s `assets/` plus a “open this document” call |
| **Resource pack** | textures + theme tokens only. Layout will not run from a vanilla resource pack with no runtime |
| **Mod JAR** | only if you **bundle the already-built runtime** + pack. Studio does not compile the author’s gameplay Java, does not obfuscate their mod, and does not invent mixins |

First target profile: **Fabric 1.20+** (matches the mockup).  
Later adapters: Forge, NeoForge, Quilt.  
**1.16.5+ as a single “export once, run everywhere” promise is a lie.** Render and HUD hooks changed too many times. Support older versions only with a dedicated adapter and a separate test matrix.

Minimum payload:

- manifest (format version, target loader, target MC version, required APIs)
- documents
- resolved theme refs
- graph / bindings
- asset refs
- validation report

Export **fails** when: missing type, missing texture, broken wire, Screen-only widget on HUD root, unknown provider, duplicate ids, cyclic parent, asset that is not a Minecraft-legal texture.

“Export” does not mean the adventure mod appears from nothing. The host mod still opens documents through the runtime API.

### Load sequence

1. Read pack.
2. Resolve types.
3. Build tree.
4. Apply theme.
5. Bind providers.
6. Layout.
7. Render / input.
8. On error: isolate the widget, log, keep the rest up if possible.

Hot reload: Save + Reload button is the baseline. File watching is extra. Live connection to a running client is a Studio-path-B feature, not MVP.

---

## 22. Runtime architecture (conceptual)

```
Studio  →  Pack (documents + theme + assets)
                ↓
         Runtime Loader
           /          \
    Widget Tree     Binder / Graph
           \          /
            Renderer + Input
                  ↓
     Minecraft adapter (HUD layer / Screens / resources / ticks)
                  ↓
           Data Providers
```

Conceptual render ops components may request: rectangle, texture, text, clip on/off. The **version adapter** turns those into `GuiGraphics` (or whatever the target version uses).

Core framework stays loader-agnostic. Adapters handle: init, resources, HUD hook, screen open, ticks, keys, networking hook for *request action*.

Suggested artifacts:

- `xenopixels-ui-runtime` — players + other mods
- `xenopixels-ui-studio` — authors, client-only
- `xenopixels-ui-adventure-api` — optional quests / skills / waypoints

First target: **Fabric 1.20+**, matching the mockup profile. Other loaders are adapters later.

---

## 23. Client / server boundary

Client: layout, render, hover, local animation, local UI variables, opening screens.

Server: stats that matter, inventory truth, ability success, quest completion.

If a skill slot is clicked, runtime sends a **registered request**. Server validates. HUD never decides that the fireball landed.

Editing Studio in multiplayer is a local dev tool. Production servers should be able to disable Studio entirely.

---

## 24. Performance and production

Avoid every frame: rebuild tree, parse JSON, reload textures, full layout, huge allocations, linear search of the whole tree.

Do:

- cache styles, texture refs, static layout, static text, binding lookup
- dirty layout only
- configurable **bind update rate** per widget (health can be every tick; biome name can be rare)
- atlas / batch draw calls where the adapter allows
- skip off-screen or fully clipped widgets
- pool transient objects if profiling says so

Budget goal (**aspirational until measured**): a 20-widget bound HUD without a minimap should stay on the order of **1–2 ms** on modest hardware. Drafts that print “&lt;1 ms always” as a guarantee are marketing. A minimap, item renders in every skill slot, and world-space billboards do not share that budget.

Editor may be heavy. Runtime must not be. Production must strip Studio.

---

## 25. Debug, validation, errors

- bounds, id, type, anchor, parent, z, visibility, live binding values
- later profiler: visible count, layout passes, bind updates, slow widgets
- error list: missing texture, unknown type, duplicate id, bad wire, cyclic parent, missing theme
- click error → select in canvas + layers + inspector
- configurable log categories so players are not spammed

Validation runs in editor and again at export.

---

## 26. Compatibility and vanilla HUD

The framework should not assume exclusive ownership of the screen, all keys, all HUD layers, all fonts.

Policy knobs per pack:

- draw with vanilla hearts / hide vanilla hearts
- replace hotbar or not
- respect F1
- yield to pause and debug overlays

Safe-area overlays help. They cannot know every other HUD mod’s pixels. Overlap assistance is not a guarantee.

Minimize global mixins. Isolate rendering and input.

---

## 27. Accessibility

Tools, not magic: per-module scale, readable text, high-contrast theme, not color-only state, reduced motion, keyboard focus on Screens.

“Screen reader compatibility” for a Minecraft HUD is not a free checkbox. Vanilla does not expose a real accessibility tree for custom `GuiGraphics` widgets. Promise keyboard navigation and readable contrast first. Promise OS screen readers only if you build an actual narration path.

Hide-HUD must hide this HUD too.

---

## 28. Phased delivery

### MVP — core editor + core runtime

Components: Root, Panel, Text, Image, Icon, Button, Progress Bar, Horizontal, Vertical, Scroll.

Editor: canvas, select, drag, resize, anchors, layers, properties (General / Transform / Visibility / Style), grid, snap, save / load, undo / redo, mock preview, validation.

Runtime: load document, layout, style, visibility, inspector-chosen bindings to PlayerData, button events to registered actions, F1 respect.

No minimap, no graph editor, no timeline, no world-space UI, no “build any RPG.”

Demo that proves MVP: a player frame (portrait + health + level text) plus a labeled button that opens a Screen.

### Phase 2 — adventure presentation + theme

Health / energy bars done honestly, quest tracker against QuestData, skill slots against SkillData, toasts, tabs, templates, repeater, theme tokens + nine-slice, animations fade/slide, live or interaction preview, GUI-scale preview, Forge/NeoForge adapter if needed.

### Phase 3 — Connect + harder widgets

Typed module graph, node details, richer conditions, asset browser polish, custom component registration UX, hot reload.

Then **either** minimap **or** vanilla hotbar replacement. Not both in one phase.

### Phase 4 — extras

Radar, world-space markers, timeline, profiler, controller nav, external desktop Studio, multi-document workflows.

### Not a phase of this framework

Asset marketplace, live co-editing, mobile preview, VR/AR UI, AI auto-layout, “export to other voxel games,” obfuscated commercial JAR wizard. Those drafts belong in a different company pitch.

---

## 29. Non-goals

- Bedrock
- every loader on day one
- in-world collaborative editing
- replacing Minecraft’s whole GUI stack
- executing user code in the runtime
- automatic conversion of existing Java screens
- 1.8–1.16
- matching the mockup pixel-for-pixel at every GUI scale
- store / marketplace
- “no code for the entire adventure mod”
- JS/Lua/game-API scripts inside packs
- GIF/MP4 widgets
- guaranteed &lt;1 ms/frame
- 1.16.5-through-latest as one binary
- a public website, Discord, or Twitter that does not exist yet
- selling the editor before a runtime exists

---

## 30. Success criteria

The concept is real when:

1. An author builds a simple HUD (hearts, label, quest list) in Studio, exports, and sees it in survival **without writing layout Java**.
2. Theme token change updates Preview and the pack without duplicating the tree.
3. A broken binding or wire is an error, not a silent zeroed bar.
4. F1, chat, pause, and another Screen do not leave ghost widgets or stolen mouse.
5. A 1920×1080 design still works at GUI scale 1 and 3 without sitting on the hotbar unless replace-hotbar was chosen.
6. Quest / skill widgets survive without the adventure mod and fill when the API is present.
7. Studio is not on dedicated servers and not required for players.
8. A second demo Screen (skill menu: tabs, list, detail, button, tooltip, scroll) can be built from documents + a few registered actions.

A painted IDE is not success. Those checks are.

---

## 31. Risks that kill the project if ignored

1. Treating the screenshot as one feature. It is five tools.
2. Starting with minimap and node graphs.
3. Hotbar replacement as an afterthought.
4. Designing only at 1080p scale 1.
5. Shipping Studio inside the player runtime.
6. Letting packs run commands.
7. Fighting every HUD mixin on the planet without a layer policy.
8. Promising “no code” for gameplay.
9. Hand-rolling the Studio chrome in raw vanilla blit calls.
10. Pretending QuestData exists in vanilla.

---

## 32. Final layer cake

1. **Studio** — visual development.
2. **Documents / packs** — saved interface.
3. **Component framework** — reusable types.
4. **Layout engine** — size and position.
5. **Style / theme engine** — appearance and states.
6. **Binding layer** — providers → properties.
7. **Event / action layer** — constrained interaction.
8. **Animation layer** — presentation motion.
9. **Graph** — optional visual wiring of 6–7.
10. **Runtime manager** — load, tick, render, close.
11. **Minecraft adapter** — real render, input, resources, lifecycle.
12. **Extension API** — components, providers, actions from other mods.

Gameplay, world, combat, containers, persistence, and permissions stay outside this cake.

---

## 33. The rule that keeps it maintainable

Studio is a **UI layer**.

It is allowed to own layout, rendering, presentation, interaction, reusable components, and author tooling.

It is not allowed to own the game.

---

## 34. License, desktop Studio, and community (decisions, not facts)

These showed up in later drafts. They are **policy choices**, not features of the UI system.

### License (pick and write it down)

A workable split:

- **Runtime:** open (MIT or similar) so other mods can depend on it.
- **Studio:** your choice — open, source-available, or proprietary.
- **Packs authors make:** the author’s license. Studio must not claim their HUD.

Do not ship a “free personal / paid commercial” editor before there is a runtime other people can load.

### If Studio is a desktop app (path B)

Then, and only then, desktop requirements matter. Draft ballpark:

- OS: Windows / macOS / Linux
- RAM: 4 GB min, 8 GB comfortable
- Display: 1280×720 min; 1920×1080 to match the design baseline
- GPU: whatever you need to blit an editor; OpenGL 3.3+ is a reasonable floor if you roll your own preview

The **exported runtime** still needs a real Minecraft + loader + Java version matrix. That matrix is per adapter, not “Java 17 and 1.16.5+ Forge 39 and Fabric 0.14 in one sentence.”

### Community

When the project exists: docs, issue tracker, runtime source, example packs, video later. Do not print a docs URL or support inbox as if they resolve today.
