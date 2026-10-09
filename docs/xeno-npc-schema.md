# Xeno NPC schema and world store

**Written:** 2026-09-22 · **Store schema:** 1 · **Entity schema (`NpcSchema`):** 2

How Xeno NPC data is shaped on disk, and how it lines up with My NPCs and CustomNPCs. The point of
the alignment is that migrating a server from either of those should be a matter of **renaming keys
inside files**, not restructuring them.

Everything here was read out of `run/mods/mynpcs-neoforge-1.5.0.jar`, the real world saves under
`run/saves/*/mynpcs/` and `run/saves/*/customnpcs/`, and
`build/moddev/artifacts/neoforge-21.1.248-sources.jar`. Rows that could **not** be verified against
a real file are marked *provisional* and must not be treated as settled.

---

## 0. Which store a fact belongs in

Added 2026-09-23. Five stores exist, and until now which one a new fact went into was decided per
round — which is how transport destinations ended up copied into every transporter's own NBT.

| A fact about… | Lives in | Because |
|---|---|---|
| **this NPC** | entity NBT (flat) | it is born and dies with the NPC |
| **content shared between NPCs** | world store `<world>/XenoNpcs/` | one copy, edited once |
| **this player** | `XenoPlayerData` capability | it follows them between worlds |
| **the world's pending work** | `SavedData` | it outlives every entity involved |
| **a default an operator may override** | datapack | it ships with the pack |

**The test for row two, which is the one that keeps being got wrong:**

> Would two NPCs ever want to share this, and would an author expect editing it once to change
> both?

If yes it is library content: it belongs in the store, and the NPC holds a **reference**. Worked
examples in the tree — `NpcDialogSlots` (twelve dialogue references), `transportNetwork` (one
network reference), and `bankId` (one bank reference). Counter-example, decided deliberately:
**ambient lines stay per-NPC**, because a guard's lines may legitimately be private to it.

**Patrol routes stay per-NPC too** (added 2026-09-23). A route is drawn around where one particular
NPC stands, and My NPCs stores it on the NPC. Two guards walking one wall is possible but is not the
common case, and paying a store category plus a sync packet for the exception would be the rule
applied mechanically rather than thoughtfully. `NpcPath` therefore lives on the profile, like lines.

**The bank is the clearest case of the rule cutting both ways at once** (added 2026-09-23). A bank
splits into two facts that look like one:

| Fact | Owner | Because |
|---|---|---|
| tabs, unlock costs, withdraw fee | world store `banks/` | two tellers in one city offer one bank |
| which tabs a player opened, and what is in them | `XenoPlayerData` | it follows the player, not the NPC |

Getting either half wrong is visible immediately: shared contents would show every player the same
vault, and per-NPC tab costs would mean renaming a price meant editing every teller.

**Scripts follow the bank's split exactly** (added 2026-09-26). The source text is library content in
`scripts/`, because two NPCs — a guard post, a whole quest line's cast — should be able to run one
behaviour and have an edit reach all of them. Which script an NPC runs is a fact about that NPC, so
it stays on the profile as `ScriptId`: an id, never a value. The mistake this prevents is the My NPCs
one, where a script lives inside each NPC's own `world_data.json` entry and changing shared behaviour
means editing every copy of it.

---

## 0.2 Defaults, and worlds that already exist

Added 2026-09-23, from the Stay Home and Gestures toggles.

A new per-NPC flag has two defaults, not one, and they are allowed to disagree:

| Where | What decides it | Who gets it |
|---|---|---|
| the field initialiser | `public boolean stayHome = true;` | an NPC with **no profile** — never edited, so it takes today's behaviour |
| the read | `tag.getBoolean(KEY)` — false when absent | an NPC **already saved** with a profile that predates the key |

`NpcCombatProfile.fromTag` short-circuits an *empty* tag to fresh defaults, so "no profile at all"
and "an old profile missing this key" are genuinely different cases and get different answers.

**The rule:** a flag that changes how an NPC behaves on its own — wandering, gesturing, attacking —
reads with `tag.getBoolean`, so nobody's running world changes underneath them. A flag that only
describes something already opt-in may use `!tag.contains(KEY) || tag.getBoolean(KEY)`, which is
what the bard's and the guard's own fields do, because an NPC with no bard job is unaffected by a
bard default either way.

---

## 0.1 The sync contract

Four syncs currently fan out of `OnDatapackSyncEvent` — factions, store index, quests, standings.
The rule for adding a fifth:

**What is sent.** What the client must *display*. Never what it must not be able to *assert*: a
destination's coordinates, the completed-quest set, another player's anything. The quest sync
carries active quests only and deliberately not completed ones, which also stops a list that grows
for a world's lifetime from being on the wire.

**When.** On join *and* after `/reload` — `OnDatapackSyncEvent` carries both, with a null player
for the reload that must fan out to everybody — **plus an explicit push at every server-side
mutation of the synced fact.** A sync that fires only on the event goes stale the moment anything
changes: that is exactly the bug the faction importer hit, where five factions were written and the
editor went on saying "No factions yet." until the operator relogged.

**What may come back.** An id, never a value. The server re-resolves every id against its own state
and re-checks every permission, because the filtered list a client was sent is a display and never
a permission. `XenoNpcTravelPacket` is the worked example: the client names a destination id, and
the server checks the role, the distance, that the destination is on *that NPC's* network, and the
unlock — all again.

| Sync | Sent on join / reload | Pushed on mutation | Meets the contract |
|---|---|---|---|
| `SyncFactionsPacket` | yes | yes — editor writes and imports | ✅ |
| `SyncNpcStoreIndexPacket` | yes | yes — store writes | ✅ |
| `SyncQuestsPacket` | yes | yes — start, finish, complete, abort | ✅ |
| `SyncStandingsPacket` | yes | yes — rides the quest push | ✅ |
| `SyncBanksPacket` | yes — rides `XenoFactionSync` | yes — store writes to `BANKS` | ✅ |
| `SyncNaturalSpawnsPacket` | yes — rides `XenoFactionSync` | yes — store writes to `SPAWNS` | ✅ |

`SyncBanksPacket` carries tab costs, because the editor's Banks page shows them. It carries **no
account**: what a player holds in a vault never reaches another player's client, and never needs to
reach their own this way — the container menu syncs the slots it opens.

---

## 1. Where things live

| | My NPCs | CustomNPCs | Ours |
|---|---|---|---|
| Root | `<world>/mynpcs/` | `<world>/customnpcs/` | `<world>/XenoNpcs/` |
| Live NPCs | entity NBT in region files | same | same |
| Authored content | folders + `.dat` blobs | same | folders only |
| File format | SNBT in `.json` via their `NBTJsonUtil` | same | SNBT in `.json` via `NbtUtils.structureToSnbt` |

```
<world>/XenoNpcs/
  clones/<tab>/<id>.json
  dialogs/<group>/<id>.json
  quests/<group>/<id>.json
  factions/<id>.json
  banks/<id>.json
  transport/<id>.json
  linked/<id>.json
  spawns/<id>.json
  recipes/<id>.json
  scripts/<id>.json
  player_scripts/<id>.json
  playerdata/                  reserved; written by nothing
```

**One deliberate divergence.** My NPCs keeps factions, banks, transport, recipes and spawns as a
single gzipped `.dat` each. Every category here is a folder of one file per entry. A per-entry file
can be diffed, hand-edited, backed up, and — the part that matters — **fail alone**; one bad byte in
a combined blob takes every faction with it. Importing one of their blobs is therefore a *fan-out*
(one file becomes N), a cost paid once by the importer rather than every day by the operator.

**A format note for the importer.** Their `.json` is not JSON and is not quite SNBT: real
dialogue files in `run/saves/` contain **raw, unescaped newlines inside quoted strings**. Quotes are
escaped; newlines are not. We write real SNBT and do not inherit the quirk.

**Corrected 2026-09-22.** This paragraph used to end "anything reading *their* files must handle it,
because `TagParser` is not obliged to accept it." Measured against NeoForge 21.1.248, `TagParser`
**does** accept a raw newline inside a quoted string and returns it intact. The first importer took
the warning at face value, escaped them to `
` before parsing, and broke — Mojang's parser accepts
only `\` and `\"` and rejects `
` as an invalid escape. The escaping pass was the bug.
`ForeignSnbt` now hands their text straight to `TagParser`, and `NpcImportTest
.rawNewlinesSurviveTheParser` pins it so a future version that tightens this is caught by a test.

### Id rules

Ids become filenames, so they are a security boundary. `XenoNpcStorePaths` **rejects, never
rewrites** — silently turning `My Faction` into `my_faction` means the operator's next lookup misses
and the entry looks like it vanished.

- `a-z 0-9 _ - .`, 1–64 characters, lower case only.
- No leading `.` or `-`, no trailing `.`, never `.` or `..`.
- Not a Windows device name (`con`, `nul`, `com1`–`com9`, `lpt1`–`lpt9`, `prn`, `aux`).
- Lower case is enforced because NTFS treats `Guards` and `guards` as one file and ext4 as two.
  Accepting both would mean a world that behaves differently depending on the operator's filesystem.

### Bounds

| Limit | Value | Where |
|---|---|---|
| File size | 256 KiB | checked with `Files.size` **before** the file is opened |
| Nesting depth | 32 | counted on the raw text **before** `TagParser` sees it |
| Entries per category | 512 | on read and on write |
| Factions | 256 | matches `SyncFactionsPacket.MAX_FACTIONS`, so nothing is stored that cannot be sent |
| Keys per entry | 96 | on the write packet |

---

## 2. Versioning

Every store file carries `Schema` (int) at its root, the same spelling `NpcCombatProfile` already
uses — a clone file *is* a profile payload, so a second name would put two version numbers in one
tag. My NPCs does the same thing under the name `ModRev`, currently **18**.

- **Absent** reads as 1. Reading it as 0 would re-run migrations that had already been applied.
- **Older** is upgraded on the way into memory. The file on disk is untouched until that entry is
  next saved, so a mistaken migration cannot rewrite a world before anyone notices.
- **Newer** is **refused**: not read, not deleted, not overwritten, and the whole category goes
  read-only for the session. Reading it would drop fields this build does not know about; writing
  over it would make that loss permanent. Refusing to read while still allowing a write is exactly
  the silent truncation this rule exists to prevent.

---

## 3. The entity tag

Flat at the root, as both reference mods are. Their `readAdditionalSaveData` hands the *same* root
compound to every sub-object, so the `"display"` / `"stats"` strings in their code are error-log
labels, not sub-compounds. A real clone has 177–187 sibling keys.

Going flat means our keys are siblings of vanilla's, where a clash is silent — one side overwrites
the other and the entity stops round-tripping. `XenoNpcFlatSchemaTest` holds that line, checking our
key set against the one extracted from `Entity`, `LivingEntity` and `Mob`.

Two clashes were found doing this, both real:

| Key | Clashed with | Resolution |
|---|---|---|
| `Brain` | `LivingEntity` writes `Brain` as a compound of brain memories | **Dropped.** Ours held the constant `V5` and `fromTag` never read it back — a stored-but-unread field wearing a taken name. The live one is the profile's `BrainVersion`. |
| `Schema` | `NpcCombatProfile` writes `Schema` (currently 17) | Identity's renamed to **`NpcSchema`**. Flat, each would have read the other's number and the profile would have re-run its whole migration chain over an already-migrated tag. |

### Identity keys (`XenoNpcData`)

| Ours | Type | My NPCs | Note |
|---|---|---|---|
| `NpcSchema` | int | `ModRev` | theirs is one number for the whole tag; ours covers identity only |
| `Role` | string | `Role` (int) | theirs is a `RoleType` ordinal, ours an id — *the importer needs the ordinal table* |
| `Name` | string | `Name` | |
| `Title` | string | `Title` | |
| `Faction` | string | `FactionID` (int) | theirs is a numeric slot, ours an id — **needs a slot→id map** |
| `Revision` | int | — | ours; guards a stale editor save |
| `Owner` | UUID | — | |
| `SourceMod` | string | — | provenance, for exactly this migration |
| `SourceUuid` | UUID | — | |
| `HomeX` / `HomeY` / `HomeZ` | double | `StartPosNew` (int array) | theirs is one array of three |
| `LeashRadius` | double | `WalkingRange` (int) | |

The combat profile's ~90 keys join the same root. They are listed by
`NpcCombatProfile.toTag()` and are already flat inside it, so hoisting them was mechanical.

### Inventory keys (`NpcGear`, `NpcDropList`)

Advanced &gt; Inventory. All five are written only when set, so a world of untouched NPCs grows no
keys at all — and an NPC saved before this shipped comes back wearing nothing and dropping nothing
rather than inheriting today's defaults (see §0.2).

| Ours | Type | Holds |
|---|---|---|
| `Gear` | compound | `Head` / `Chest` / `Legs` / `Feet` / `Main` / `Off`, each a **serialised `ItemStack`** |
| `NpcDrops` | list | up to 9 × `{I: <serialised stack>, P: chance%}` |
| `LootMode` | int | `NpcLootMode` ordinal — **on the wire, append only** |
| `MinExp` / `MaxExp` | int | 0–32767, the reference GUI's own cap |

**The tag is the storage; the stack is a view of it.** Two reasons, both in `NpcSlotStack`:

- Since 1.20.5 an `ItemStack` cannot be written or read without a `HolderLookup.Provider`, and
  `NpcCombatProfile.toTag()` has none — it is called from packet encoding, from snapshots and from
  tests. Holding live stacks would mean threading a provider through every one of those.
- An item whose mod has been uninstalled does not parse, so a slot holding a *parsed* stack would
  have nothing to write back and the item would be gone for good. Keeping the tag means an
  unreadable slot is copied out exactly as it came in. That is the property item ids give
  `NpcTrade`, without giving up the enchantments and custom names an id cannot carry.

Each slot tag is bounded at 16KB, measured **uncompressed** — a gzipped measure lets a tag of
repeated bytes through at a fraction of its real weight and then expand once parsed, which is the
allocation the bound exists to refuse.

Two asymmetries worth knowing:

- **Worn gear never drops.** `NpcGear.applyTo` equips every slot at drop chance zero, so a player
  cannot strip an NPC's authored appearance by killing it. Drops are authored on `NpcDrops`, which
  is why the reference page has nine chance fields and only seven slot buttons — they were never one
  per slot.
- **Gear is worn on every render path but drawn on one.** `XenoNpcRenderer` carries
  `HumanoidArmorLayer` and `ItemInHandLayer`, so the plain humanoid shows it. The GeckoLib,
  entity-mimic and full-DMZ branches draw their own models and never reach a layer.

There is no `Projectile` key. The reference page has a seventh slot for one, but nothing here would
fire it: no `RangedAttackMob`, no ranged goal, and `AimAccuracy` still has no consumer.

### Curios slots

Not in our tag at all — Curios stores them on the entity, and the editor menu pushes them straight
through. What this mod supplies is the binding:
`data/xenopixelsmod/curios/entities/xeno_npcs.json` gives all eight NPC entity types twelve slots.

| Source | Slots |
|---|---|
| The ten this mod already gives MyNPCs/CustomNPCs NPCs (`.../entities/npcs.json`) | `curio`, `head`, `necklace`, `back`, `body`, `bracelet`, `hands`, `ring`, `belt`, `charm` |
| DragonMineZ's own, read from its jar | `head_tech`, `weights` |

DMZ declares its two in `data/dragonminez/curios/slots/*.json` and binds them in
`.../entities/dmzslots.json` to **`minecraft:player` alone** — which is why a Xeno NPC had none
until this binding existed. Reusing ids rather than minting new ones is what makes DMZ's scouters
and weights drop straight in.

**Weights render on an NPC; they do not train it.** DMZ's training runs off `TrainingRewardC2S` and
`TrainingAnimationC2S`, client-to-server packets sent by a player, with no NPC path into any of it.

Curios is on the compile classpath but is **not declared in `neoforge.mods.toml`**, so every call
goes through `NpcCurios`, and `NpcCuriosImpl` is the only new file that names a Curios class
(`NpcCuriosInventory`, the older integration, is the other).

### Scene keys

| Ours | Type | Default | Holds |
|---|---|---|---|
| `SceneId` | string | "" | which shared scene this NPC performs |
| `SceneTrigger` | string | `MANUAL` | what makes it play |

**Stored by name, not ordinal** — as `XenoNpcScene.Kind` already is, and unlike the store
categories whose ordinals *are* the wire format. So the trigger list may be reordered freely, and a
value from a newer build that this one has never heard of reads back as `MANUAL` rather than as
whatever happens to sit at that index.

`SceneTrigger` is written **only alongside a scene id**: a trigger on an NPC with no scene cannot do
anything, and writing it unconditionally would grow the key on every untouched NPC in a world. That
makes it another instance of the `PROFILE_SHAPE` trap — the policy's shape sample must set *both*, or
the whitelisted key is rejected at the type check.

| Trigger | Fires when | Raised from |
|---|---|---|
| `MANUAL` | `/xenoscene start` only | the command; `fire()` refuses it |
| `INTERACT` | a right-click, before dialogue or lines | `mobInteract` |
| `APPROACH` | a player within 6 blocks | the tick, staggered by entity id |
| `DAMAGED` | damage that actually landed | `hurt`, gated on `super.hurt` |
| `DEATH` | death, **before** `super.die()` | `die` |
| `TIMER` | every 30s, unprompted | the tick |

A ten-second cooldown and a mid-play check keep a combo from restarting a scene from step one
several times a second. `DEATH` sits on the same edge as the `KILLED` line and for the same reason:
after `super.die()` the entity may be out of its trackers, and a last word nobody can see is not a
last word.

### Carried and saved NPCs

An NPC can leave the world onto an item stack or into the world store. Both use **one format** —
`XenoNpcData.editorPayload`, the same payload the editor screen opens with — so a template saved to
the library and one carried on a Cloner cannot disagree about what an NPC is. A curated subset would
be a list that silently falls behind every time a field is added.

| Where | Key / path | Carried by |
|---|---|---|
| Item stack | `XenoNpcPayload` (+ `XenoNpcPayloadName`) in `CUSTOM_DATA` | Cloner, NPC Jar |
| World store | `clones/<tab>/<id>.json`, tabs 1–9 | `/xenoclone`, `XenoNpcClones` |

Writing it back is `restoreFromTag` plus a profile write — the identical two halves
`XenoNpcRespawnHandler` uses. Restoring only one is the bug that made a respawned NPC come back as a
default one wearing its old name.

Three things are reset rather than copied when a **new** NPC is stamped from a payload (`keepOwner`
false — the Cloner and `/xenoclone place`): the **home**, or every clone walks back to the original's
spot the moment the leash checks; the **owner**; and the **provenance**, since a clone was made here
rather than imported. Releasing a **jarred** NPC passes `keepOwner` true — it is the same NPC that
went in, and only its home moves.

Payloads are bounded at 256KB, measured with `NpcSlotStack.tagBytes` (uncompressed — see the
Inventory section for why), and a tab holds at most 64 templates.

### Stats keys

Advanced &gt; Stats.

| Ours | Type | Default | Behind it |
|---|---|---|---|
| `HealthRegen` | float | 0 | health healed **per second** out of a fight |
| `CombatRegen` | float | 0 | the same, during one |

Both zero by default, which is what every NPC did before they existed — nothing heals on its own.
Zero is also what an absent key reads back as, so unlike the AI switches these need no fallback.

Two numbers rather than one because they want very different values: out of combat is about
recovering between fights, in combat is about how long a boss stands up. One value covering both
would make any boss unkillable or any guard permanently wounded.

Clamped to 0–100 on load as well as in the editor: a negative rate would *damage* the NPC through
`heal()`, and an enormous one makes it unkillable. Being hit counts as a fight for five seconds even
with nothing targeted — an NPC shot at from a distance has no target of its own but is plainly in
one, and that is exactly where the two rates differ most.

### AI keys

Advanced &gt; AI. Written every save, so absence means a profile from before they existed.

| Ours | Type | Default | Behind it |
|---|---|---|---|
| `AiCanSwim` | boolean | **true** | a gated `FloatGoal` |
| `AiAvoidsWater` | boolean | false | `setPathfindingMalus(PathType.WATER, 8)` |
| `AiDoorInteract` | int | 0 = Disabled | gated `OpenDoorGoal` / `BreakDoorGoal`, plus `setCanOpenDoors` — **ordinal on the wire, append only** |
| `AiLeapAtTarget` | boolean | false | a gated `LeapAtTargetGoal` |
| `AiReturnToStart` | boolean | **true** | `XenoNpcBehaviour.tickLeash` stands down when off |

The two that default **true** are read with `flagOrDefault`, not `getBoolean` — an NPC saved before
these existed has no key, and `getBoolean` answers false, which would have sunk every NPC standing
in water and unanchored every one with a home.

**Gates, not conditional registration.** `registerGoals` runs once at construction, so a goal left
out there could never come back when its switch is turned on. Every goal is added always and refuses
to start while its switch is off — the shape `StayHomeStrollGoal` established. The two settings that
are entity state rather than per-tick decisions (the water malus, the navigation's door permission)
are pushed from `NpcCombatProfile.write` instead, and both branches are written so turning a switch
off undoes it.

`Break` follows vanilla's own Hard-difficulty rule, taken from `BreakDoorGoal`'s predicate rather
than invented, so an NPC is consistent with every other door-breaking mob.

**Shelter From and Mount Control have no keys.** There is no vanilla mechanism to hang either on, so
they stay disabled rows with a line saying why — a stored value nothing reads is what §0's table
keeps out.

### Brain keys

| Ours | Type | Holds |
|---|---|---|
| `BrainXenoSpecials` | boolean | whether the layer this mod added on top of the DMZ port may fire |

Always written, so it is never absent from a tag saved by this build — which is what the v7
migration keys off. Absent means a save that predates it, and it reads back as **true**, because
that is what every brain did before the switch existed.

`NpcCombatBrainVersion.usesXenoSpecials()` decides whether a version consults it: false for **v7**
alone, which is the ported DragonMineZ tree and nothing of ours. A profile stored as **v7 without
this key** migrates to **v8** on load — v7 used to mean "no per-action toggles" while still firing
the BT3 combo poses and the teleport moves, and redefining it in place would have taken those away
from every NPC already set to it.

A "Xeno special" is the BT3 combo choreography and the repositioning set in `NpcCombatMoves` —
chase, vanish, backstep, teleportAbove. Not Hakai: no brain casts it, only the script APIs do.

### Reading a world saved before the flattening

`XenoNpcData.unwrapLegacy` lifts a tag that still has its identity nested under `XenoNpcData`,
renaming `Schema`→`NpcSchema` and dropping `Brain` on the way up. It is written back flat on the
next save. Recognised by the nested compound's presence rather than a version number, because a
version-1 tag kept its number *inside* that compound where a root read cannot see it.

---

## 4. Per-category shapes

### `factions/<id>.json` — verified

The id is the filename and is not repeated inside: one fact, one place.

| Ours | Type | My NPCs | Note |
|---|---|---|---|
| `Name` | string | `Name` | |
| `Color` | int | `Color` | |
| `HostileTo` | list of string | `AttackFactions` (list of **compound**) | theirs are slots — see below |
| `DefaultStanding` | int | `DefaultPoints` | **converted**, not clamped — see below |
| `AttackedByMobs` | bool | `GetsAttacked` | |
| — | | `FriendlyPoints` | read as the upper threshold of the conversion |
| — | | `NeutralPoints` | read as the lower threshold of the conversion |
| — | | `HideFaction` | **not carried** — nothing reads it |
| — | | `Slot` | **not carried** — the filename is the id |

Both remaining omissions are deliberate. Writing them would be stored fields nothing reads, which is
the shape this codebase keeps out. The importer **drops them with a logged note**, the way
`NpcCloneConverter.droppedKeys` already reports what it cannot rehome.

#### `AttackFactions` is a list of compounds, not of ints

This entry said "list of int" and was wrong, on a file nobody had yet seen populated. The 1.5.0 jar
reads it with `NBTTags.getIntegerSet(tag.getList("AttackFactions", 10))`, and that helper is
`getCompound(i).getInt("Integer")` — element type **10**, compound, each holding the slot under the
key `Integer`. A real `factions.dat` confirms it.

The cost of the wrong reading was invisible rather than loud: `ListTag.getInt` answers zero for a
compound element, so every hostility in the file resolved to slot 0 and each faction came out
hostile to whichever faction happened to occupy that slot. The importer now reads the compound
shape, and still accepts the int-list and int-array forms older writes used.

#### `DefaultPoints` is converted, not clamped

The two scales are unrelated. Theirs starts at zero and carries two **per-faction** thresholds;
ours runs -1000..1000 against the fixed `XenoFaction.FRIENDLY_AT` and `HOSTILE_BELOW`. Clamping one
onto the other changed what a faction thought of the player: in a default install, `Neutral` (1000
points) arrived FRIENDLY and `Aggressive` (0 points) arrived NEUTRAL — three of the five factions
wrong.

`Faction.playerStatus` in the jar is exactly `points >= friendlyPoints` friendly,
`points < neutralPoints` hostile, neutral between. `FactionImport.convertStanding` maps each of
those three bands onto the matching band of ours and keeps the position within the band
proportional. Note the boundary: their hostile test is strict and ours is not, so their
`neutralPoints` — their lowest *neutral* value — maps to `HOSTILE_BELOW + 1`, not to
`HOSTILE_BELOW`.

### `dialogs/<group>/<id>.json` — ours differs, knowingly

My NPCs files **one node per file**, numbered, linked by integer `DialogId`, grouped by a category
folder (`dialogs/Villager/1.json`). Ours stores **one dialogue per file**, with its nodes as an
ordered list inside — the shape `XenoDialogueNbt` already reads and writes, and the same shape the
per-NPC dialogue editor produces.

This is the one category where the import is a **fan-in** rather than a rename. It is recorded here
rather than quietly absorbed, because it is the one place the "rename only" promise does not hold.

### `clones/<tab>/<id>.json` — provisional

The flat entity tag plus `Schema`. My NPCs adds `ClonedName`, `ClonedDate` and `ClonedTab` — but
those are written by its `addClone` path and are **absent from all three real clone files** in this
repo, so do not assume they are there.

### `spawns/<id>.json` — live

Written by the editor's Global → Natural Spawns page, read by `NpcNaturalSpawns` and consumed by
`NpcNaturalSpawnService`. One rule per file, ungrouped:

| Key | Meaning |
|---|---|
| `Name` | display title; falls back to the entry id when blank |
| `Biomes` | list of biome ids; empty means "any biome" |
| `Weight` | 1–1000, relative chance among the rules that match right now |
| `CloneTab` | clone library tab, 1–9 |
| `CloneId` | clone entry id — a **reference**, never a copy of the payload |
| `Time` | `any` \| `day` \| `night` |

A rule carries no NPC identity. It points into `clones/`, so editing the saved clone changes every
rule that references it, which is the same rule transports follow. `XenoNpcStoreWritePacket` refuses
a write whose `CloneId` is blank or whose `Time` is not one of the three values, and
`NpcNaturalSpawnService` marks what it places with `XenoNaturalSpawn` on the entity's persistent
data — that marker is both the per-rule cap and the cleanup rule's only handle. Ambient NPCs are
placed with respawning switched **off**, after the payload is applied; see §5.

An edit from the editor carries the entry's current revision, read out of the store index the server
broadcasts, not a literal 0: the store refuses a write whose expected revision is stale, so sending 0
would let the first edit of a rule through and every later one fail.

### `scripts/<id>.json` — live

Written by the script editor (`XenoNpcScriptScreen`, reached from Global → Scripts or an NPC's
Advanced → Scripts), stored by `XenoNpcStoreWritePacket`, read by `XenoNpcScripts` and executed by
`NpcScriptHost`. Ungrouped, one file per script; native NPCs hold script tab references to these ids.

| Key | Meaning |
|---|---|
| `Name` | display label, also what the store index shows; falls back to the id when blank |
| `Language` | engine name to ask for, default `ecmascript`; anything other than an ECMAScript alias is refused by name |
| `Enabled` | 1/0; **absent means enabled**, so a hand-written file with only `Name` and `Script` runs |
| `Script` | the source text |

Bounds, all enforced on the way in by `XenoNpcScripts.rejectPayload`: name 128 characters (the index
label clamp, so nothing is silently shortened later), language 32, source 32 768.

**The NPC holds references, not text.** `ScriptTabs` on the profile lists tab and loaded-library
ids into this folder; legacy `ScriptId` becomes one tab. Ids are path-validated before save.

**Source text is deliberately absent from the store index.** The index is rebroadcast to every
player after every write; putting script bodies in it would ship every script in the world to
everyone, continuously, to serve a screen almost nobody opens. Content therefore travels on request
through `NpcScriptPacket` (fetch or run) and `NpcScriptResultPacket` (the answer). Fetch requires
permission level 2; running or writing a script requires level 4.

**What is stored is text, not authority.** Bundled Nashorn runs with `--no-java` and a class filter
that denies host-class lookup. There is no execution timeout; authoring remains operator-only.

### `player_scripts/<id>.json` — live

Global → Player Scripts stores the same `Name`, `Language`, `Enabled`, and `Script` fields plus
`ChatOnly` (1/0). The native player host loads enabled tabs for `init`, `login`, `logout`, and
`chat`; `ChatOnly` restricts a tab to `chat`. The category is ungrouped and was appended after
`scripts` to preserve earlier category wire ids. See `native-mynpcs-script-compat.md` for the
hook and API inventory.

### `recipes`, `linked` — provisional

Nothing reads these two. No real file exists for either in this repo (every sampled `.dat` held an
empty list), so their per-entry keys are **not settled** and are not written down here as though
they were. They will be defined when something consumes them, per the standing rule that a control
goes live only once something reads what it writes. `recipes/` additionally waits on a block to
craft at: the editor's Recipes page is disabled because there is no bench, not only because there is
no reader.

The other categories in this section's old title — `quests`, `banks`, `transport` — do have readers
now, so their shapes live with those readers and with §0.1 rather than being restated here.

`playerdata/` is reserved and written by nothing: ours already live on `XenoPlayerData`, which
already persists. Two writers for one fact and no rule for which wins is worse than one.

---

## 5. Known migration hazards

1. **Companion data.** `NpcCloneConverter` used to drop all twelve `Companion*` keys, on the premise
   that the fork had removed the companion system. It has not — `espi.mynpcs.roles.RoleCompanion`
   reads every one, and the real fixture `goku_customnpcs.snbt` carries `Role: 6`
   (`RoleType.COMPANION`) alongside them. Dropping them while keeping `Role: 6` handed My NPCs a
   companion with its stage, talents, experience, inventory, age and owner silently reset. **Fixed**,
   with a test pinning it. Anything reusing that drop-list should start from the fixed one.
2. **Numeric ids.** Their factions, dialogs, quests and transport entries are keyed by **int slots**;
   ours are strings. Every one needs a slot→id map built during the import, and an NPC's
   `FactionID` resolved through it.
3. **Two roots.** `ServerCloneController.getDir()` in the 1.5.0 install resolves to the **game
   directory** (`run/mynpcs/clones`), not the world folder. Clones therefore come from a different
   root than dialogs and quests do. The comment in `NpcWorldMigrator` saying otherwise is wrong.
4. **`PositionOffsetX` vs `PositionXOffset`.** Both spellings appear across versions of the two
   mods. Handle both.
5. **`RespawnEnabled` is restored by the payload, not by the reader.** `XenoNpcPayload.apply` writes
   the template's own `RespawnEnabled` back onto the NPC it is building, so anything that wants a
   placed NPC to stay dead must switch respawning off **after** the apply. Placing an ambient NPC
   before the switch hands every death a scheduled respawn, and the per-rule cap fills with
   permanent copies of one kill. `NpcNaturalSpawnService.place` is ordered that way and
   `NpcNaturalSpawnWiringTest` pins the order by reading the source.

---

## 6. Related

- `docs/xeno-npc-mynpcs-feature-boundary.md` — what we do and do not reproduce.
- `compat/npc/clone/` — the tested CustomNPCs → My NPCs migrator this pattern comes from.
- `XenoNpcFlatSchemaTest`, `XenoNpcWorldStoreTest`, `XenoFactionNbtTest` — the checks behind the
  claims above.
