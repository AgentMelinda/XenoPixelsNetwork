# MyNPCs GUI reference — screen order for the Xeno NPC editor

**Reviewed:** 2026-09-21

The 34 screenshots in this folder (`15.png` through `48.png`) define the required tab order,
sub-screen order, and visible control order for the native Xeno NPC editor. They are a layout and
feature-boundary reference, not permission to invent storage or runtime behavior.

Implementation rule: a control is enabled only when a native Xeno schema, validated server save
path, persistence path, and runtime consumer exist. Reference controls without that backing remain
visible but disabled. Disabled controls never add fields to the save payload.

Runtime status: the implementation described below compiles and has focused unit coverage, but the
complete editor flow has not yet been verified in a fresh Minecraft client process.

## Tab strip

The required order is:

> `Display · Stats · AI · Inventory · Advanced · Global · DMZ · Brain · Delete · X`

`NpcEditorScreenManifest` is the source of truth for this order and for the screenshot-to-screen
mapping.

## Reference screens

### 15 — Display

Name, Title, Model, Living animation, Size, Tint, Glowing, Texture, Cape, Overlay, Showing Layers,
Hitbox, Visible, Availability, Boss Bar, and Color, with the live NPC preview on the right.

Native status: identity, model appearance fields, size, tint, glowing, texture path, hitbox,
visibility, boss bar, color, and preview are enabled. Living animation, texture picker, cape,
overlay, showing layers, and availability are rendered disabled.

### 16 — Stats

Health, AggroRange, Creature Type, Respawn, Melee Props, Ranged Props, Projectile Type, Resistance,
fire/sun/drowning/fall/potion/cobweb toggles, both regen fields, and KI Weapon.

Native status: schema-backed scalar/toggle fields are enabled. The five property editors and regen
fields are rendered disabled because no complete native writable model exists.

### 17 — AI

On Found Enemy, Shelter From, Must See Target, Door Interact, Can Swim, Return To Start, Avoids
Water, Leap At Target, Movement, Attack Invisible, and Mount Control.

Native status: the exact controls and order are present but disabled. Combat action policy belongs
to the Brain tab; these navigation settings have no native writable runtime schema yet.

### 18 — Inventory

Armour and hand slots, NPC preview, Min/Max Exp, loot mode, inventory grid, and nine drop-chance
fields.

Native status: the reference layout is represented with disabled controls. Native Xeno NPCs do not
yet have an equipment/drop inventory model that can safely persist these values.

### 19 — Advanced hub

Role and Job selectors/edit buttons, followed by the 2x4 hub in this order:

| | |
|---|---|
| Lines | Factions |
| Dialogs | Sounds |
| Night | Linked |
| Scenes | Marks |

Native status: the hub and navigation are implemented. Unsupported editors open reference-shaped
disabled screens rather than silently accepting data.

### 20 — Global hub

Banks, Factions, Dialogs, Quests, Transport, PlayerData, Recipes(Broken), Natural Spawns(WIP), and
Linked.

Native status: all nine entries exist in this order and route to explicit sub-screens.

### 21 — Banks

Tab Cost and Upgrade Cost columns, Can Upgrade rows, Start/Max fields, a bank list, and Add/Remove.

Native status: reference-shaped disabled screen; no native bank registry exists.

### 22 — Factions list

Aggressive, Friendly, and Neutral entries with Name, Add, Remove, and Edit controls.

Native status: reference-shaped disabled registry screen. The NPC's own faction string remains
editable on the supported identity path; this screen does not claim a global faction database.

### 23 — Faction editor

Name, ID, Color, Points, Hidden, Attacked by mobs, and Hostile Factions.

Native status: visible through faction navigation but disabled without a native faction schema.

### 24 — Dialogs list

Category and Dialog panes, each with Edit, Remove, and Add controls.

Native status: reference-shaped disabled registry screen.

### 25 — Dialog name prompt

A centered dialog-name text field with confirmation and cancel actions.

Native status: represented as a disabled sub-screen because there is no native writable dialog
registry behind it.

### 26 — Transport

Transport list with Add, Edit, Remove, Open, and Back.

Native status: reference-shaped disabled screen.

### 27 — PlayerData

Player selector and player-data management controls.

Native status: reference-shaped disabled screen; the NPC editor cannot mutate arbitrary player
data.

### 28 — Recipes(Broken)

Recipe registry list and edit actions.

Native status: reference-shaped disabled screen, retaining the reference's own `(Broken)` label.

### 29 — Natural Spawns(WIP)

Natural-spawn list with name, weight/chance, NPC selection, type, add, and remove controls.

Native status: reference-shaped disabled screen, retaining the reference's own `(WIP)` label.

### 30 — Global Linked

Global linked-NPC list management.

Native status: disabled. The old scalar linked-NPC profile field had no runtime consumer and is not
accepted by ordinary editor saves.

### 31 — Advanced hub, Trader selected

The Advanced hub with Trader selected as the role and its Edit action available.

Native status: a native Trader role may open the Trader reference screen; other native roles keep
Edit Role disabled so Guard, Quest, or Companion cannot misleadingly open Trader. The trade editor
itself remains disabled until a native trade inventory exists.

### 32 — Trader role

Trade slots, item comparisons, linked market name, and trader inventory options.

Native status: reference-shaped disabled screen.

### 33 — Advanced hub, Bard selected

The Advanced hub with Bard selected as the job and its Edit action available.

Native status: represented in the manifest, but Edit Job is disabled because native Xeno NPC data
has no job field. It never invents a Bard selection.

### 34 — Bard job

Sound selection and bard playback/job settings.

Native status: reference-shaped disabled screen.

### 35 — Lines

Line-category hub.

Native status: implemented as navigation to the line selector screen; line persistence is disabled.

### 36 — Line selector

Grid of selectable line entries.

Native status: the selector grid is represented with disabled options because there is no native
line registry.

### 37 — Dialog options

Dialog option list/edit controls.

Native status: reference-shaped disabled screen.

### 38 — Sounds

Living, Angry, Hurt, Death, and Step sound fields, each with Select Sound, followed by Has Pitch.

Native status: implemented. Free-text resource IDs remain editable, and Select Sound opens a
sorted picker backed by `BuiltInRegistries.SOUND_EVENT`. Blank selection keeps the entity default.

### 39 — Night

Editing Mode plus Display, Stats, AI, Inventory, Advanced, Role, and Job alternate-state toggles.

Native status: exact controls are visible but disabled. The previous single night-texture field was
not an adequate alternate-profile model and is no longer editor-writable.

### 40 — Advanced Linked

Per-NPC linked editing list.

Native status: disabled. The editor does not claim that edits propagate when no propagation runtime
exists.

### 41 — Scenes

Scene list with Add, Remove, and Edit actions.

Native status: reference-shaped disabled screen.

### 42–44 — Marks

Empty, populated, and edited mark-list states. A mark is a list entry containing icon, color,
availability, and remove controls.

Native status: the list-shaped controls are represented but disabled. The old single icon/color
fields had no renderer and cannot represent this model, so they are not accepted by editor saves.

### 45 — DMZ

Race, STR/SKP/DEF/VIT/PWR/ENE, aura fields, Appearance, Forms, and transform/stack actions.

Native status: schema-backed stats, aura settings, form selections, DMZ appearance, and server
actions are enabled. DMZ method targets were checked against `libs/dragonminez-2.1.3.jar`.

### 46 — Forms

Form Group/Form and Stack Group/Stack Form selectors, with Edit Selected and Create New.

Native status: selectors use DragonMineZ's loaded form registries. Definition editing/creation is
disabled because DragonMineZ owns those definitions.

### 47 — Appearance

Paginated DMZ mode, identity, part, color, hair, tail, scale, and head-bone controls.

Native status: implemented through `XenoNpcAppearanceScreen`; changes stay local until the parent
editor Save sends the supported profile subset.

### 48 — Brain

Combat Brain, version, Aim, Aggro, Special CD, and paginated action rows containing enabled state,
chance, and modifier.

Native status: implemented for the supported native brain versions. Versions that delegate action
selection to DragonMineZ hide ignored per-action controls instead of presenting dead switches.

## Safety and rendering rules

- Order is part of the specification: tabs, hub entries, fields, and sub-screen navigation.
- Unsupported controls remain disabled and never produce save payload fields.
- Ordinary saves use an explicit key/type allowlist and cannot change the editor lock.
- Lock/unlock uses a separate revision-checked packet.
- No invented DragonMineZ APIs: symbols are verified against repository source or the pinned jar.
- Atlas sprites render at native size. `AtlasPanel.fittedInto` is only a uniform small-viewport
  fallback; sprites are never independently stretched.

## Re-exporting these images

`tools/extract_session_images.py` extracts transcript attachments:

```text
python tools/extract_session_images.py --out XenoMyNpcsGuiOrderImages --start 15 --skip-existing
```

Attachments reach the transcript only after the turn carrying them ends.
