# Quest log — design

**Date:** 2026-09-22 · **Branch:** `1.21.1` · **Status:** awaiting review

## Context

Quests in this repo support exactly one at a time. `XenoPlayerData` holds a single
`questId` / `questProgress` / `questTarget`, and `ParallelQuests.start` refuses a second with
*"Already on quest"*. That was fine while three quests existed in a static map. It stopped being
fine when quests became datapack- and store-definable, because a pack can now define any number and
the player can hold one.

Two things follow from widening it, and the second is the reason this is worth doing beyond
convenience.

**`MasterPrerequisites` quest gates can never be satisfied.** `check()` requires
`requirement.questId().equalsIgnoreCase(data.getQuestId())` **and** `progress >= target`. But
`addQuestProgress` returns true the moment progress reaches target, every caller then runs
`completeQuest`, and `completeQuest` calls `clearQuest()` which blanks `questId`. So the gate fails
while the quest is incomplete and fails again the instant it completes, because the id is gone.
There is no window in which it passes. A master gated on a quest is permanently locked, silently.
Remembering *completed* quests is what fixes it.

**A quest log is a real player-facing system**, not an editor screen: categories, a journal, and a
place quests are handed in. That is what this spec builds.

## Evidence basis, and its limit

From `featureguireferencemynpcs.md`, a reverse-engineered reference whose §25 appendix is recovered
from GUI construction bytecode:

- **`GuiQuestEdit`** — 34 controls in recovered order, including `quest.questlogtext`,
  `quest.completedtext`, the six types, the repeat rules, and items 24–25:
  `quest.npc` → **Complete by npc** and `quest.instant` → **Instant Complete**.
- **`GuiQuestCompletion`** — exactly one recovered control: `quest.complete` → **Complete**.
- **`GuiNpcQuestReward`** — `quest.randomitem`, no/yes, back, `quest.exp`.

**`GuiQuestLog` is not in that appendix.** It appears once in the whole 3368-line document, in
§3.1's prose. So the log screen here follows the *described flow* — categories grouped and naturally
sorted, left-side category buttons, quest list, paged log text, an Objectives section, a
*Complete with …* line, and `quest.noquests` when empty — and this spec does **not** claim to
reproduce its control order, because no recovered evidence for that order exists. Per the
reference's own §24.8: label such cases rather than pretend.

Screenshot evidence: the user's image 69 gives the **New Quest toast** directly — cyan border, dark
navy fill, gold heading, white quest name, top-right.

## 1. Data model

`ParallelQuests.QuestDef` is today
`(id, title, desc, target, QuestObjective.Goal goal, QuestReward reward)`. It gains:

| Field | Type | Notes |
|---|---|---|
| `category` | `String` | Groups the log's left-hand buttons. Blank falls into a default category. |
| `logText` | `String` | The journal body, paged. Distinct from `desc`, which stays the one-line summary. |
| `completeText` | `String` | Shown on hand-in. |
| `completionMode` | `INSTANT` \| `NPC` | **Defaults to `INSTANT`.** |
| `completerNpc` | `String` | Only meaningful in `NPC` mode. |

`XenoQuests.parse` reads them from datapack JSON. The world store already groups quests by folder —
`XenoNpcStoreCategory.QUESTS("quests", true)`, giving `quests/<group>/<id>.json` — so a store quest's
folder **is** its category and needs no field. Only datapack quests under `npcs/quests/*.json`, which
are flat, need `"category"` written.

**`INSTANT` as the default is deliberate.** It is precisely today's behaviour, so no quest that
exists now changes meaning when the field appears.

## 2. Player state

`XenoPlayerData` moves from one quest to:

- `active` — a bounded map of `questId → (progress, target, visited)`.
- `completed` — a set of quest ids with the game time each finished at.

`questVisited` becomes **per quest**. Today it is one set, which is correct for one quest and wrong
for several: a `TALK_TO_NPC` quest and a kill quest running together must not share a visited set,
or talking to an NPC for one would silently consume it for the other. The existing
`MAX_QUEST_VISITED = 256` cap moves inside each entry.

**Migration.** A save holding the old single quest loads as a map of one, with its `questVisited`
becoming that entry's set. A save with no quest loads empty. Both are one-way; the next save writes
the new shape.

**Bounds.** Active quests cap at **32**: the count drives both the sync payload and the log's page
count, and an uncapped map is a payload sized by whatever a pack hands out. `completed` caps at
**512**, oldest evicted — it is never displayed (see §4) but it does live on disk forever, and a
set with no ceiling is one a long-running world grows without limit.

### Consumers to update

All read "the" active quest today and must read the map instead:

- `ProgressionEvents` — `handleDummyHit`, `onDeath`, `onTalkedToNpc` advance **every** active quest
  whose goal matches, not just one; `completeQuest` takes an explicit id.
- `ParallelQuests.start` — stops refusing a second quest, refuses only at the cap or on a duplicate.
- `ParallelQuests.status` — reports all actives.
- `ProgressionCommands` — `abort` takes an id; `status` lists.
- `MasterPrerequisites.check` — reads `completed`, which is the fix.
- `NpcPartyQuestObjectives` — currently reads the single quest for party sharing. **Decision: share
  every matching active**, because the party path already filters by objective, and picking one
  quest out of several would need a rule the player cannot see or control.

## 3. Hand-in

The one behavioural change.

- **`INSTANT`** — unchanged. Progress reaches target, `completeQuest` runs, rewards pay.
- **`NPC`** — progress reaches target and the quest becomes **ready**, not complete. It stays in
  `active`, marked ready, and shows *Complete with &lt;npc&gt;* in the log. Talking to the named NPC
  opens a one-button **Complete** popup, mirroring `GuiQuestCompletion`'s single recovered control.
  Completing pays rewards and moves it to `completed`.

Authority stays server-side: the popup's button sends a request naming the quest id, and the server
re-checks that the quest is active, ready, in `NPC` mode, that the named NPC is the one being talked
to, and that the player is in range — the same discipline `XenoNpcDialoguePacket` already applies by
re-reading its own dialogue rather than trusting the client.

## 4. Screens

Two tabs beside the vanilla inventory, matching the reference's **Inventory → Factions → Quests**
group. Both attach through `ScreenEvent`, anchored off `getGuiLeft()` / `getXSize()` exactly as
`XenoInventoryEffects` already does for its effect rail — a proven hook in this repo, not a new one.

**Factions tab.** A read-only list: faction name, the player's standing, and Friendly / Neutral /
Hostile. **Corrected 2026-09-22:** this section originally claimed every input already existed. It
did not. `XenoFaction.attitudeAt(int)` and the `ClientFactions` mirror do, but
`XenoPlayerData.factionStanding` is a server capability that reaches no client — `ClientFactions
.Entry.defaultStanding()` is the *faction's* default, not the player's, and reading it here would
show every player on a server identical numbers while looking entirely correct. A second per-player
S2C packet carries the standings; see the client plan's standings task. It is built now rather than later
so the tab group is correct the first time instead of Quests occupying slot two and moving.

*(Their vocabulary is Friendly / Neutral / **Unfriendly**; ours is FRIENDLY / NEUTRAL / **HOSTILE**.
Keeping ours — it is already in `XenoFaction.Attitude` and on the wire.)*

**Quests tab.** Left column of category buttons, naturally sorted so `Side 2` precedes `Side 10`.
Middle column lists that category's quests. Right column shows the selected quest: paged `logText`
with forward/back, an **Objectives** section with progress, and *Complete with …* only in `NPC`
mode. `quest.noquests` equivalent when there is nothing active.

**Active quests only.** §3.1 is explicit — the log reads *active* quests, groups *active* quests,
and shows `quest.noquests` when there are no *active* quests. Completed quests are not listed. They
are kept server-side for `MasterPrerequisites` and for the availability checks a later spec will
add, and are **not synced to the client at all**, which also removes the only part of this payload
that would have grown without bound over a world's lifetime.

**Toast.** `New Quest` in gold over the quest name in white, cyan border, dark navy fill, top-right,
fading after **4 seconds** — reusing `XenoNpcEditorScreen.NOTICE_MS`, whose comment already states
the reasoning: long enough to read, short enough not to become furniture. Matched to image 69.
Rendered through the existing `RegisterGuiLayersEvent` overlay path.

### Sync

Quest state reaches no client today. A new S2C packet carries the player's own **active** quests only —
not the completed set, which nothing on the client displays. Modelled on `SyncFactionsPacket`:
bounded counts, `readUtf` limits on every string, and bounds checked on **read** as well as write. Sent on join, on any quest state change, and after
`/reload`. Appended to `ModNetwork` — ids are positional — with the protocol bumped from **82**.

## Out of scope

Each its own spec:

- **The Availability engine** (§3.5): four dialog checks, four quest checks, daytime, two faction
  conditions, two scoreboard conditions, minimum level. The reference notes it is reused by
  visibility, marks, item giver, conversation and builder. It must not be born inside a quest spec,
  or it will be quest-shaped and need tearing out for the other five.
- Quest types **Item / Location / AreaKill / Manual**, and binding **Dialog** to a specific dialogue
  option rather than the whole conversation.
- Repeat rules (`EnumQuestRepeat`: Repeatable, MC Daily/Weekly, RL Daily/Weekly).
- Mail, `nextQuestid` chaining, Random Item reward.
- The dialogue wheel and top transcript (agreed as the next spec after this one).

## Testing

- **Migration** — an old single-quest save loads as one active with its visited set intact; an empty
  save loads empty; re-saving writes the new shape.
- **The gate** — a completed quest satisfies `MasterPrerequisites`; an active-but-incomplete one does
  not. This is the regression test for a bug that shipped.
- **Per-quest visited** — two quests running together do not consume each other's visited entries.
- **Multi-advance** — one kill advances every active quest whose goal matches, and no others.
- **Hand-in** — an `NPC`-mode quest at target is ready, not complete; completing requires the right
  NPC, in range, with the quest actually ready; `INSTANT` still auto-completes.
- **Bounds** — the active cap holds; the sync packet's caps hold on read as well as write.
- **Natural sort** — `Side 2` before `Side 10`.
- **Protocol** — floor assertion via `ProtocolVersion.current()`, as the existing packet tests do.

## Verification

```
gradlew.bat compileJava -PofflineMcMeta
gradlew.bat test -PofflineMcMeta                      # baseline to beat: 1702
gradlew.bat build jarJar serverJar -PofflineMcMeta    # 0 entries under META-INF/jarjar/
gradlew.bat buildApiExampleAddon -PofflineMcMeta
```

In game, none of which is covered by the above:

1. Start three quests at once; all three appear under their categories.
2. Kill one mob; only the quests whose goal matches advance.
3. Complete a quest, then check a master gated on it — it opens. Today it never does.
4. An `NPC`-mode quest at full progress shows *Complete with …* and does not pay until handed in.
5. Open the inventory: Factions and Quests tabs sit beside it in that order.
6. A new quest raises the toast top-right.
7. Load a world saved before this change — the single active quest survives as one entry.

## Unverified

- The quest log's **control order** is not recovered anywhere in the reference; only its flow is
  described. This spec follows the flow and makes no 1:1 claim.
- Nothing in this repo has been exercised in a running game for several rounds. `plan.md` carries 31
  outstanding in-game checks before these seven.
