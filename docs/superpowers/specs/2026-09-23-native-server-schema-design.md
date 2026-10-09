# Native NPC server schema and runtime behaviour — design

**Date:** 2026-09-23
**Status:** approved in conversation; ready for an implementation plan

## Why this exists

The native NPC system grew a subsystem at a time — quests, bubbles, respawn, trades, dialogue
slots, transports — and each round put its new facts wherever was nearest. Nothing is broken by
that individually. Together it means there is no written rule for where a fact belongs, and two
bounds have already drifted past what they were written to bound.

This settles the rule, moves the one fact that is demonstrably in the wrong home, fixes the drift,
and writes down the sync contract.

## Evidence basis, and its limit

Measured on 2026-09-23 against the tree at the time of writing, not recalled:

- `NpcCombatProfile` declares **120** `TAG_` constants.
- `XenoNpcSavePolicy.EDITABLE_KEYS` holds **98** keys; `MAX_KEYS` is **96**.
- `XenoNpcStoreCategory` has **10** categories. Referenced anywhere outside its own file:
  `DIALOGS` (8), `FACTIONS` (7). The other **eight have zero references**.
- `ModNetwork` registers **57** packet types at protocol **85**.
- `XenoNpcRespawnHandler.onServerTick` calls `prune()` and iterates every entry, every tick.
  `MAX_ENTRIES` is 4096 and `ENTRY_LIFETIME` is 30 in-game days.
- Per-entity work in `XenoNpcEntity` is staggered by `(tickCount + getId()) % interval`.

**Not verified:** no measurement of actual server tick cost was taken. The respawn finding is a
reading of the code, not a profile, and the plan should say so rather than claim a speed-up it has
not shown.

## 1. The ownership rule

Five stores exist. Until now, which one a new fact went into was decided per round.

| A fact about… | Lives in | Because |
|---|---|---|
| **this NPC** | entity NBT (flat) | it is born and dies with the NPC |
| **content shared between NPCs** | world store `<world>/XenoNpcs/` | one copy, edited once |
| **this player** | `XenoPlayerData` capability | it follows them between worlds |
| **the world's pending work** | `SavedData` | it outlives every entity involved |
| **a default an operator may override** | datapack | it ships with the pack |

The test for the second row, which is the one that keeps being got wrong: **would two NPCs ever
want to share this, and would an author expect editing it once to change both?** If yes it is
library content and belongs in the store, with the NPC holding a reference.

## 2. Transports move to the store

A transport *network* is shared by definition. Two transporters in one city offer the same
destinations; today each carries a private copy in its own NBT, so renaming a destination means
finding and editing every transporter that lists it.

The `TRANSPORT` store category already exists, ungrouped, with zero readers. The right home is
sitting empty.

**After the move:**

- `transport/<network>.json` in the world store holds a named list of destinations.
- An NPC's profile holds **one string**: the network it serves. `TransportMenu` resolves it.
- The editor's Transporter page edits the *network*, and picks which network an NPC serves — the
  same two-screen shape the dialogue slots already use.

**Migration is one-way, on read.** An NPC whose NBT still holds an inline `Transports` list has it
lifted into the store under a network named for that NPC, its profile rewritten to the reference,
and the fact logged. The inline list is then never read again. Silently dropping it would delete an
operator's work; leaving both readable would give one fact two homes, which is the thing this
document exists to stop.

**Lines deliberately do not move.** The same "twenty guards share one greeting" argument applies,
and it was considered and declined: a guard's lines may legitimately be private to it, and the
migration is the larger of the two. This is recorded so the next reader knows it was a decision
rather than an oversight. The full-override precedence rule — an NPC's own set beats its role's —
is unaffected either way; that is about precedence, not storage.

## 3. The seven empty categories stay, and say why

`CLONES`, `QUESTS`, `BANKS`, `LINKED`, `SPAWNS`, `RECIPES` and `PLAYERDATA` have no reader. They
are **kept**, and each gains a documented reservation naming what it is for and what must be true
before it goes live.

They are not free: `loadAll()` walks every category's directory on world open, and
`SyncNpcStoreIndexPacket` loops all ten on every sync. That cost is accepted in exchange for the
directory layout staying stable, because the importer's whole difficulty was formats that were
never pinned down.

Each reservation must state the one thing that unblocks it:

| Category | Reserved for | Blocked on |
|---|---|---|
| `CLONES` | saved NPC templates | a template apply path |
| `QUESTS` | shared quest definitions | quests currently load from datapack only |
| `BANKS` | the Bank role | the role does not exist |
| `LINKED` | linked-NPC groups | `LinkedNpcs` is per-NPC today |
| `SPAWNS` | natural spawn rules | no spawner exists |
| `RECIPES` | carpentry recipes | no bench exists |
| `PLAYERDATA` | **nothing — see below** | — |

`PLAYERDATA` is the exception and is documented as **permanently empty**. Per-player state lives on
`XenoPlayerData`, which already persists, already syncs, and already has the quest, standing and
transport-unlock data in it. Two homes for one fact with no rule about which wins is worse than
one home, and this names which one wins.

## 4. The drift

**The whitelist outgrew its bound.** 98 keys, `MAX_KEYS = 96`. It has not bitten because the editor
sends only the keys it dirtied, so a real save is a small subset — but the cap no longer bounds
what it was written to bound. `MAX_KEYS` becomes `EDITABLE_KEYS.size()` exactly —
derived, not hand-maintained — so a save may carry every editable key and nothing else. The margin
was the bug: a hand-written number is a second thing to remember to change, and it was not
changed. A test asserts the two are equal, so they cannot diverge again silently.

**The respawn handler scans every tick.** It prunes and walks up to 4096 entries twenty times a
second, to act on the few that are due. Entries are kept in due order and only the head is examined
per tick; pruning moves to the moment an entry is added or fires, which is when the set actually
changes.

This is a code reading, not a measurement. The plan should add the cheap measurement rather than
assert an improvement.

## 5. The sync contract

Four syncs currently fan out of `OnDatapackSyncEvent` — factions, store index, quests, standings —
and the rule for adding a fifth is unwritten. The contract to write down:

- **What is sent:** the client gets what it must *display*. It never gets what it must not be able
  to *assert* — a destination's coordinates, a completed-quest set, another player's anything.
- **When:** on join and after `/reload`, both of which `OnDatapackSyncEvent` carries, plus an
  explicit push at every server-side mutation of the synced fact. A sync that happens only on the
  event goes stale the moment anything changes, which is the bug the faction import hit.
- **What the client may send back:** an id, never a value. The server re-resolves every id against
  its own state, and re-checks every permission, because the filtered list a client was sent is a
  display and never a permission.

Each of the four existing syncs is recorded against that contract, and any that does not meet it is
named.

## Out of scope

- **Bank, Jobs, Pather, Scenes.** Grounded only in shipped code, by decision. They extend the
  contract when built rather than being predicted by it — the same mistake that left the importer
  with six categories whose formats nobody could verify.
- **Moving lines.** Declined above, with reasons.
- **The entity tag's 120 keys.** Their flat layout is settled in `docs/xeno-npc-schema.md` and is
  not reopened here.

## Testing

Pure and unit-testable:

- The whitelist's size and its cap agree, and a save at exactly the cap is accepted.
- A profile carrying an inline transport list migrates to a reference, and the destinations survive.
- A profile already holding a reference is untouched by the migration.
- An NPC pointing at a network that no longer exists offers nothing, rather than throwing.
- Two NPCs pointing at one network see the same destinations, and editing it once changes both.
- The respawn queue returns due entries in due order and nothing before its time.
- Every store category either has a reader or a documented reservation — a category with neither
  fails the test.

Not testable here, so in-game checks:

- A world saved before the move opens with its transporters still working.
- Tick cost before and after the respawn change, measured rather than assumed.

## Verification

```
gradlew.bat test -PofflineMcMeta          # baseline to beat: 1983 tests, 0 failures
gradlew.bat build jarJar serverJar -PofflineMcMeta
gradlew.bat buildApiExampleAddon -PofflineMcMeta
```
