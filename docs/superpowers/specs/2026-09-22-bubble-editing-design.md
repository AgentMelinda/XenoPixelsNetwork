# Bubble editing — design

**Date:** 2026-09-22
**Status:** approved in conversation; ready for an implementation plan

## Context

Three gaps, chosen by the user out of four candidates. Live in-world editing was explicitly
excluded.

What already works, and is **not** in scope to rebuild:

- **Bubble palette** is per-NPC and editable today — a cycler writes `profile.bubblePalette`
  (`XenoNpcEditorScreen.java:1176-1190`), it saves through `NpcCombatProfile`'s
  `TAG_BUBBLE_PALETTE`, and both renderers read it. That whole path is the proof that the fields
  §1 adds will work; they follow it exactly.
- **Dialogue content** is per-NPC and editable today — `DialogueDraft`, with Dialogs → node →
  option pages.

## Evidence basis, and its limit

Everything below was read out of repo source while writing this. Line numbers are from the tree at
the time of writing and should be treated as pointers, not contracts.

- `SpeechBubbleQueue.ACTIVE` is a `Map<Integer, Bubble>` keyed by entity id, and `show()` does
  `ACTIVE.put(...)` — **one bubble per NPC**, a new line replacing the old.
- `SpeechBubbleRenderer` iterates `active.entrySet()` and translates each to
  `entity.getBbHeight() + HEIGHT_ABOVE_ENTITY` with `HEIGHT_ABOVE_ENTITY = 0.7f`. There is **no
  cross-NPC deconfliction anywhere in the loop**.
- `RenderSystem.disableDepthTest()` is called before that loop.
- `SpeechBubbleQueue.DEFAULT_LIFETIME_TICKS = 60L`, `FADE_TICKS = 10.0f`.
- `SpeechBubbleLayout` owns `LINE_HEIGHT = 10`, `FONT_HEIGHT = 9`, `PADDING_X = 14`,
  `PADDING_Y = 12`, plus `textBudget()` and `maxLines()`.
- `XenoNpcSpeech.java:120` resolves lines as `XenoNpcLineSets.get(definition.refs().lines())` —
  **per role, from a datapack**. `NpcCombatProfile` has no lines field at all.
- `XenoNpcLines.Category` is `INTERACT, ATTACK, KILL, KILLED, RANDOM, WORLD`.
- `XenoDialogue.Node` is `record Node(String text, List<Option> options)`.
- `DialogueBubbleRenderer.java:200` already does `theme(profile.bubblePalette)`.

**Not verified:** nothing here has been run in a game. Every rendering claim below is a claim about
code, not about pixels.

## The correction this spec exists to record

When the user reported "some air bubles are on one of eachother", the change made was to
`DialogueBubbleLayout`'s `GAP_BELOW_LINE` / `GAP_BETWEEN` — the spacing between answer options
*inside one dialogue stack*. That was the wrong system. Ambient bubbles are one-per-NPC and cannot
stack on a single NPC; what overlaps is **two nearby NPCs' bubbles in screen space**, and nothing
in the renderer prevents it. §2 is the actual fix.

## 1. Geometry and timing, per NPC

Three fields on `NpcCombatProfile`, beside `bubblePalette`:

| Field | Default | Replaces |
|---|---|---|
| `bubbleHeight` (float) | `0.7f` | `SpeechBubbleRenderer.HEIGHT_ABOVE_ENTITY` |
| `bubbleDurationTicks` (int) | `60` | `SpeechBubbleQueue.DEFAULT_LIFETIME_TICKS` |
| `bubbleMaxLines` (int) | current `SpeechBubbleLayout.maxLines()` | that method's return |

The existing constants stay exactly where they are and become the defaults. **An NPC that sets
none of these must render identically to today** — that is the invariant a test pins, because it is
what protects every NPC already placed in a world.

Bounds, checked on read as well as write: height `0.0f`–`4.0f`, duration `1`–`1200` ticks (one
minute), max lines `1`–`8`. A value outside its range is clamped, not refused: these arrive from a
save packet, and a crafted one must not be able to put a bubble a kilometre up or pin it on screen
forever.

Each gets an editor row on the Bubble page that already exists. Per the standing rule, the
save-whitelist key is the **last** step — the row goes in only once the renderer reads the field.

## 2. Ordering and overlap

`SpeechBubbleRenderer`'s loop takes an ordered list instead of `entrySet()`, sorted by squared
distance from the camera, **far to near**, so the nearest bubble paints last and therefore on top.

This fixes two things at once:

- **The flicker.** `HashMap` iteration order is unspecified and free to change between frames, so
  today, when two bubbles overlap, which one is on top is arbitrary *per frame*.
- **Meaningless occlusion.** With the depth test off, a distant NPC's bubble can paint over a near
  one purely by map order.

Depth test stays **off**. Turning it on would let an NPC's own body and the terrain clip its
bubble, which is a bigger regression than the overlap.

Bubbles still overlap; they just do so stably, with the nearest readable. Stacking, hiding and
fading were all considered and rejected as needing per-frame screen-space projection for a problem
that stable ordering mostly solves.

The sort is a **pure static helper** — positions in, ordered entity ids out — so a unit test can
pin it with no client. The renderer only walks the result.

## 3. Ambient lines, per NPC

A `lines` map on `NpcCombatProfile`: `Category → List<String>`, six categories.

**Full override.** An NPC that defines *any* lines replaces the role's set entirely; an NPC that
defines none falls through to `definition.refs().lines()` exactly as today. Per-category fallback
was considered and rejected by the user: one rule is easier to reason about and to show honestly in
an editor than six independent ones.

The consequence is stated plainly because it will surprise someone: giving one guard a custom
greeting costs that guard its role's combat lines too, until they are typed in as well. The editor
should say so where the override is turned on.

This follows the path dialogue already took, and for the same reason the editor's own javadoc
gives: a datapack is not writable at runtime, so anything an operator must edit in game lives on
the profile.

Bounds: **16 lines per category**, **256 characters per line**, checked on read and on write. These
become entity NBT and a save payload; both need a ceiling.

Editor: a Lines page — pick a category, see its lines, add / edit / remove.

## 4. Per-node palette

`XenoDialogue.Node` gains an optional `palette` string. Blank means inherit the NPC's
`bubblePalette`, so **every dialogue that exists today is unchanged**.

`DialogueBubbleRenderer.java:200` already resolves a theme from a palette string; it takes the
node's when non-blank and the profile's otherwise.

This is a **wire change** — the dialogue tree travels in the packet — so the protocol bumps
**83 → 84**. Unknown palette names resolve to the default rather than throwing, matching
`NpcCombatProfile.canonicalPalette`.

Per-node *geometry* is deliberately not included. Palette only.

## Out of scope

- **Live in-world editing** — excluded by the user.
- **NPC-to-NPC conversation lines** — that is MyNPCs' Conversation *job*, and belongs with Jobs.
- **Per-node bubble geometry** — palette only, per §4.
- **The `WORLD` category's trigger.** It exists in the enum and can be edited here, but what fires
  it is not defined in this spec.

## Testing

Pure and unit-testable, no client required:

- An NPC with no bubble fields set renders at the old constants. The compatibility invariant.
- Out-of-range height / duration / max-lines clamp rather than refuse.
- The distance sort orders far → near, is stable for equal distances, and handles an empty set.
- A profile with any lines overrides the role set completely; one with none falls through.
- Line count and length caps hold on read as well as write.
- A node with a blank palette inherits the profile's; a node with an unknown one gets the default.
- A dialogue written before this change round-trips with every node inheriting.

Not testable here, and therefore in-game checks:

- That two nearby NPCs' bubbles stop flickering.
- That a raised `bubbleHeight` actually clears the NPC's head.

## Verification

```
gradlew.bat test -PofflineMcMeta          # baseline to beat: 1806 tests, 0 failures
gradlew.bat build jarJar serverJar -PofflineMcMeta
gradlew.bat buildApiExampleAddon -PofflineMcMeta
```

In game:

1. Two NPCs side by side, both talking → the nearer bubble is consistently on top, with no
   frame-to-frame flicker.
2. Raise one NPC's `bubbleHeight` → its bubble rises; the other does not move.
3. Set `bubbleDurationTicks` to 20 on one NPC → its line clears in a second, the other still takes
   three.
4. Give one guard a custom `INTERACT` line → it uses only its own lines, in every category.
5. Clear that guard's lines → it returns to the role's datapack set.
6. Give a dialogue node a palette → that node's bubble changes colour and the others do not.
7. Open a world saved before this change → every NPC's bubbles look exactly as they did.
8. Connect a client on protocol 83 → refused, not misreading packets.
