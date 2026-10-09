# MyNPCs / CustomNPCs importer — design

**Date:** 2026-09-22
**Status:** implemented for factions, shared dialogue files, supported quest definitions, and loaded placed NPCs; a placed NPC also carries its job, scripts, protection, spoken lines and form, and every source key left behind is named in the report; unloaded chunk entities and unsupported quest/dialogue actions remain out of scope

## What this is

Read a world's My NPCs or CustomNPCs authored content off disk and write it into
`<world>/XenoNpcs/`, so a server can move to the native NPC system without retyping its content.

## The constraint that shapes everything

> "we cannot relay on mynpcs side code from xenopixels the npc system must not relay on other
> system that relay on other mods"

So the importer reads **files**, never their classes. It must work with neither mod installed —
which is in fact the normal case for a migration, since an operator removing My NPCs will not keep
it on the classpath to be migrated away from.

This rules out the existing path. `NpcWorldMigrator` resolves its source directory by reflecting
`espi.mynpcs.controllers.ServerCloneController` (`NpcWorldMigrator.java:37`). That class is fine
for a *converter that runs alongside My NPCs*, which is what it was written for, and wrong for
this. Directories are located by convention here instead.

## Evidence basis, and its limit

From `docs/xeno-npc-schema.md`, which was itself built by reading the 1.5.0 jar and real save files:

- Roots are `<world>/mynpcs/` and `<world>/customnpcs/`, **except clones**, which the 1.5.0 install
  writes to the **game directory** (`run/mynpcs/clones`) rather than the world folder.
- Their `.json` is SNBT-ish, not JSON, and real dialogue files contain **raw unescaped newlines
  inside quoted strings**. Quotes are escaped; newlines are not. `TagParser` is not obliged to
  accept that.
- Factions, banks, transport, recipes and spawns are single gzipped `.dat` blobs; importing one is
  a fan-out into our per-entry files.
- Their factions, dialogs, quests and transport entries are keyed by **int slots**; ours by string
  ids. Every import needs a slot to id map.
- `PositionOffsetX` and `PositionXOffset` both appear across versions. Both must be handled.

The importer now handles only source shapes that have been checked against the pinned MyNPCs jar,
repository migration converters, or saved data. Quest conversion is deliberately partial: only a
single-target kill quest maps to an executable Xeno objective. Other quest kinds and source actions
without a safe equivalent are reported and skipped. A placed NPC's own entity tag is mapped field by
field (see "Placed NPC fields" below); bank, transport, spawn, recipe and linked content stays
outside this importer, and every source field that stays outside is named in the report instead of
vanishing.

## Scope of this slice

**In:** command-driven import of supported shared content and currently loaded placed NPCs.

| Piece | Responsibility |
|---|---|
| `ForeignNpcRoots` | Locate their directories by convention. No foreign classes, no reflection. |
| `ForeignSnbt` | Parse their dialect, tolerating raw newlines inside quoted strings. |
| `SlotIndex` | Their int slot to our string id, and back. |
| `FactionImport` | One of their faction tags to one of ours, reporting what it dropped. |
| `NpcImportReport` | What was imported, dropped, skipped and why. |
| `PlacedNpcProfileMigrator` | One of their placed-NPC entity tags to our profile tag on our entity. |
| `NpcScriptImport` | Their per-NPC script list to `SCRIPTS` store entries, and the bound id. |
| `NpcSourceKeys` | Accounted-for bookkeeping: every source key is consumed, explained, or reported. |
| `/xenonpcimport all` | Dry run for factions, dialogs, quests, and loaded placed NPCs. Add `confirm` to write with per-entity backups. |

**Out, and why:**

- **Unloaded live entities.** The command replaces entities in chunks already loaded by the server.
  It does not open region files or force-load every chunk; the report says how many loaded entities
  were found and names this limitation. Run the command in relevant areas to cover more chunks.
- **Unsupported quest kinds and dialogue actions.** They are reported entry by entry; command
  rewards are skipped for safety, and quest types without a matching Xeno objective are not saved.
- **Banks, transport, spawns, recipes, linked.** These are separate stores with their own source
  shapes, not entity fields, so the placed-NPC path never invents a destination for them. Where a
  source NPC carries the keys that would have fed them (`Bank`, `BankId`, `Transports`, `Controller`,
  `NpcInv`, `NpcScenes`, `MountControl`, `Linked`), the report names the keys and says why they were
  left behind.

## Placed NPC fields

`PlacedNpcProfileMigrator` reads a source entity tag and writes our profile tag onto the entity we
spawn in its place. What it maps today:

| Source (MyNPCs / CustomNPCs) | Native destination |
|---|---|
| `NpcJob` (int ordinal) | profile `Job`, an `XenoNpcJob` id; declaration order matches the source ordinal, and an empty job writes nothing |
| `ScriptLanguage`, `ScriptEnabled`, `Scripts[].Script` | ungrouped `SCRIPTS` store entries (id `<sourceMod>_<uuid>_s<n>`), whose first enabled id is bound as profile `ScriptId` |
| `Resistances.Melee/Arrow/Explosion/Knockback` | profile `NpcResistanceProps` as percentages; the source multiplies damage by `2 - value`, so a value below 1.0 (the NPC takes *extra* damage) has nowhere to go and the report names those keys instead of pretending the block was fully consumed |
| `ImmuneToFire`, `PotionImmune`, `NoFallDamage`, `CanDrown`, `IgnoreCobweb`, `BossBar` | matching profile booleans, each read only when present; `IgnoreCobweb` inverts into `CobwebAffected` |
| `NpcInteractLines`, `NpcAttackLines`, `NpcKillLines`, `NpcKilledLines`, `NpcLines` | profile `NpcLines` under `INTERACT`, `ATTACK`, `KILL`, `KILLED`, `RANDOM` |
| `CurrentForm`, `CurrentFormGroup` | profile `Form`, `FormGroup` |

Two things learned the hard way:

- The DragonMineZ attachment lives at `xenopixelsmod:npc_dmz_stats`, not at the
  `neoforge:attachments` container the migrator used to read. The old path never matched, so an
  imported NPC lost its stats, and because `NpcCounterpartSync.apply` copies the profile attachment
  back over the entity, an empty profile actively overwrote the surviving one. Legacy
  `NeoForgeData` / `xenopixels:npc_dmz_stats` locations are still read as fallbacks.
- A script language we cannot run is recorded rather than dropped: the store entry keeps the
  language name, so the author sees what the source wanted instead of an empty script.

Anything not in that table is accounted for by `NpcSourceKeys.audit`, which walks the source tag
once the entity is converted. Every top-level key must be consumed by the migrator, dismissed as
per-instance runtime state (vanilla NBT and the source's own lowercase runtime keys such as
technique cooldowns), or folded into one report line naming its exact keys. `NpcSourceKeysTest`
runs that audit over the shipped clone fixtures and fails if a real key ends up unexplained, so a
source-mod update cannot quietly start losing data.

## The four deliberate faction drops

Per the schema doc, ours does not carry `FriendlyPoints`, `NeutralPoints`, `HideFaction` or `Slot`.
The first two are constants here (`XenoFaction.FRIENDLY_AT`, `HOSTILE_BELOW`), the third is read by
nothing, and the fourth is the filename. The importer **drops them with a logged note** rather than
writing fields nothing reads.

`AttackFactions` is a list of their int slots and becomes our list of string ids through the
`SlotIndex`. A slot with no entry in the index is dropped and reported — it names a faction that was
not in the file, and inventing an id for it would produce a hostility toward something that does
not exist.

## Naming

Their names are free text; our ids are a filesystem boundary (`a-z 0-9 _ - .`, 1–64, lower case,
no reserved Windows device name). `XenoNpcStorePaths` **rejects rather than rewrites** for content
an operator typed — but an import has no operator to correct, so it must derive an id.

`SlotIndex` lowercases, replaces every illegal run with `_`, trims leading and trailing separators,
truncates to 64, and appends `_2`, `_3` … on collision. Every derived id is reported next to the
original name, because a silent rename is how an operator's next lookup misses.

A name that reduces to nothing (all punctuation) falls back to `faction_<slot>`, which is stable
and traceable rather than random.

## Testing

Pure and unit-testable, no server:

- Their dialect parses, including a raw newline inside a quoted string and an escaped quote.
- A file that is not SNBT at all fails with its filename, and does not take the import with it.
- Slot to id: lowercasing, illegal characters, collisions, over-length, all-punctuation, and the
  reserved Windows device names.
- A faction round-trips: name, colour, default standing, attacked-by-mobs.
- `AttackFactions` slots resolve through the index; an unknown slot is dropped and reported.
- The four documented keys are dropped and each appears in the report.
- A standing past ±1000 clamps.

The importer has fixture tests for dry run, writing, duplicate protection and per-file parse
failure. A real server save and in-world placed-NPC conversion still require a fresh game run.

## Verification

```
gradlew.bat test -PofflineMcMeta
gradlew.bat build jarJar serverJar -PofflineMcMeta
gradlew.bat buildApiExampleAddon -PofflineMcMeta
```

In game (not verified by unit tests):

1. `/xenonpcimport all` previews; `/xenonpcimport all confirm` imports factions, dialogues, quests,
   and loaded placed NPCs with backups. Re-running reports existing entries rather than overwriting.
2. A world with `mynpcs/factions.dat` → factions appear under `<world>/XenoNpcs/factions/`, one
   file each.
3. A faction hostile to another → its `HostileTo` names our derived id, not a number.
4. Two factions named `Guards` and `guards` → distinct ids, both reported.
5. **Neither mod installed** → the import still runs. This is the case that matters; it is the
   whole point of reading files rather than classes.
6. Run twice → the second reports existing entries and does not overwrite them.
