# Xeno NPC — current plan

## Combined NPC and quest parity request ledger — 2026-09-25

This section records the complete user-requested scope carried into the current implementation. It
extends this already-dirty plan; it does not replace prior notes or claim runtime proof.

### 2026-09-26 implementation checkpoint

- Continuation session round 2 (phases G/L/I): scripting groundwork shipped — JSR-223 engine seam
  with honest no-op fallback, `scripts/` world store, per-NPC `ScriptId` binding, op-2 gated
  fetch/run packets (protocol 95), and `XenoNpcScriptScreen` wired into Advanced/Global; bundling
  GraalJS or Nashorn-standalone remains an owner decision. Natural Spawns are live end to end
  (store, server spawner, sync, editor rows); global Linked points at the real per-NPC editor;
  Recipes is relabelled `Recipes (no bench)` because no consumer exists. Two editor save paths
  (bank edits, scene drafts) that claimed revision 0 — refusing every save after the first — were
  fixed. The unavailable-row sweep is complete: remaining disabled rows are correct conditionals
  or honest unbuilt features (cape/overlay/layers, night profile, mark availability, NPC-to-NPC
  lines), which are the Phase M backlog. Full suite green at 2,628 tests / 364 suites; none of
  this is exercised in a live client yet.
- Continuation session (post-Codex, phases A–K of the quest-parity + todolist plan): the native
  editor gained a vanilla HSV colour picker used by every colour row on the appearance and DMZ tabs,
  a full DMZ Form Maker screen wired to the existing debounced form-save flow (normal and stack
  forms, create/edit/duplicate), a finished Lines authoring set including Random Lines, per-quest
  edit-from-Advanced, decoupled fly toggles with a Can Use Flight gate, and quest repeat/completion/
  command/random-reward/faction/item import parity with round-trip tests. The CNPC quest-completion
  path no longer mints hard-coded skill points (origin-policy leak, regression-tested). The wave and
  hi_wave clips were rebuilt to three eased oscillations with content tests, and
  `/xenoset npcAttackStartRadius` (default 1.0) replaces the fixed 4.5-band attack-start fallback.
  Main/test compile and all touched suites pass headless. Not verified in a fresh client: the form
  maker, picker visuals, flight feel, wave motion, melee radius, and quest grant behaviour. New
  generated atlas panel shapes are parked pending explicit user credit approval.
- Native Item Giver availability now evaluates player level, day/night, up to four quest and
  dialogue gates, two faction gates and two scoreboard gates before handing out items. The
  Advanced > Item Giver > Availability page edits these conditions and saves them through the
  bounded NPC profile policy. Shared Xeno dialogue IDs are recorded when opened and persist with
  player data through save/load and respawn, so before/after dialogue gates have real server state.
  NPC-owned dialogue trees still have no shared ID for these gates. Edit Role now routes every
  native role to its relevant existing editor page. Focused tests and the full 2,509-test suite,
  distribution build and API example addon build pass; the 0.5.0 server jar contains zero nested
  jarjar entries. Gameplay availability and the editor layout have not been verified in a fresh
  client. The reference's optional FTB Quests gate remains open because its integration is absent
  from the current exact runtime set.
- Native Item Giver now has nine serialized item-stack slots, all five MyNPCs delivery methods
  (random, all, missing, none-owned, chained), and per-player seconds/once/Minecraft-day cooldowns.
  An Advanced editor page copies the held item into a selected slot and edits gift lines. Use
  state persists with player data and survives death. The server scans new arrivals within three
  blocks every ten ticks and requires line of sight and free inventory slots before giving items.
  Availability conditions were added in the follow-up above; the optional FTB Quests gate is still
  unimplemented, so this is functional Item Giver support rather than full MyNPCs job parity. The full 2,506-test
  suite, distribution build and API example addon build pass; gameplay delivery has not been
  verified in a fresh client.
- The native Follower job now finds the nearest named XenoNPC, MyNPCs NPC or CustomNPCs NPC within
  twenty blocks and walks toward it. Its Advanced page saves the target's visible name; clearing
  the name stops following. Following yields to combat and scenes, outranks idle patrol/leash
  movement, and backs off briefly when navigation stays blocked. The Companion role's separate
  player-owner behavior remains intact. The pinned MyNPCs entity overrides vanilla `getName()`
  using its DataDisplay name, which the native follower reads without loading optional classes
  directly. The full 2,502-test suite, distribution and API example addon builds pass; the current
  0.5.0 server jar has zero nested jarjar entries. Gameplay pursuit across NPC mods is not yet
  verified.
- The native Healer job now runs on the server as a timed nearby-effect applicator, matching the
  pinned MyNPCs job's range, friendly/hostile/everyone filter and 100-tick potion-effect duration.
  Its Advanced editor page selects range, interval and up to sixteen namespaced effect IDs with
  amplifiers. Profile NBT and the editor save policy bound every value. It is independent of role
  and obeys Job Enabled. The full 2,500-test suite, distribution build and API example addon build
  pass; the current 0.5.0 server jar has zero nested jarjar entries. In-game healing and cross-mod
  faction behavior still need a fresh runtime check. Other reserved jobs remain incomplete.
- Native XenoNPC Display Size now also scales collision dimensions by default, while the authored
  Hitbox multiplier remains independently editable. `/xenoset xenoNpcSizeScalesHitbox off` restores
  the separate behavior for all loaded native NPCs without a relog; `on` re-enables it. The scale
  is synced through the existing entity data, so this change needs no packet version bump.
- The 2026-09-26 13:10:27 client crash was caused by selecting DMZ's
  `saga_slug_giant.png`: its matching `.geo.json` does not exist in the pinned jar. The native
  GeckoLib path now resolves giant/first-person saga texture variants to an existing base rig and
  uses DMZ's shared saga animation when a per-texture clip does not exist. A missing rig falls back
  to DMZ's existing robot model instead of crashing the render thread. Display Size still scales
  the rendered GeckoLib model, including the giant texture. Post-fix in-game rendering remains
  unverified; focused tests, all 2,498 tests, distribution and API example addon builds pass.
- Native Display now carries GeckoLib model kind, model asset, texture, skin and visual size in the
  live appearance packet. The renderer resolves DMZ's master texture paths to their verified geo
  and animation assets. Visual Size and the authored Hitbox multiplier remain separate editor
  values, and now combine for native collision by default; the effective scale syncs through
  entity data. The native boss bar tracks nearby viewers, name, color and health.
- Added twelve physical Zeni denominations: coins 1, 10, 25, 50, 100, 200, 250, 500 and 1,000,
  plus notes 10,000, 100,000 and 1,000,000. A native bank exchanges carried cash with its own
  vault using exact change and an inventory-capacity check before debit. MMO Econ wallet mode
  remains optional and separate. The six NPC tools now have generated cuboid item models and a
  shared neon material atlas under `src/generated/resources`.
- Global faction editing now exposes standing, color, hostile IDs and explicit aggression toward
  selected ordinary mob entity IDs. The native NPC target predicate excludes other XenoNPCs and
  DragonMineZ masters. Faction data and client sync include the new settings. Main protocol is 93.
- Fresh automated validation: 2,494 tests, zero failures; `build jarJar serverJar` and
  `buildApiExampleAddon` passed with `-PofflineMcMeta`. Both server jars currently in `build/libs`
  contain zero `META-INF/jarjar/` entries. None of these new visuals, combat changes or bank
  transactions have a fresh in-game proof.
- Still outstanding from the combined request: native ECMAScript, Lua and Pawn/AMX execution and
  compatible scripting APIs; the remaining Advanced jobs, broader availability uses, cape/overlay/layer
  controls, GeckoLib held-item/armor/DMZ-hair attachments, global recipes and natural spawns;
  server-shared local/URL skin sources; and a full
  runtime audit of texture browsing, model choice, boss bars, tool models, faction combat and
  physical cash. These are not marked as MyNPCs parity yet.

### Implementation status — 2026-09-27

- Native scripting continuation: all 22 unchanged `examples/customnpcs/*.js` files now have a
  native host path: 19 NPC tabs and 3 Global → Player Scripts tabs. `XenoPixels` script calls
  route into Xeno services; NPC timers persist on the entity, `rangedLaunched` fires from the ki
  dispatcher, player `init/login/logout/chat` hooks use their own global store category, and
  numeric imported quest slots resolve only when unique. The server jar packages Nashorn classes
  directly and has zero `META-INF/jarjar/` entries. Protocol is 98. Focused tests compile all
  22 unchanged scripts with bundled Nashorn. A fresh 2026-09-27 `runApiTestClient` process logged
  Nashorn startup, protocol 98, and the example addon ping/pong; native NPC/player hook execution
  still needs direct gameplay proof. The full test suite has one existing source-text assertion
  failure in `SceneTriggersTest.damageThatDidNotLandDoesNotFire`; the hurt behavior was not changed
  for that test. See `docs/native-mynpcs-script-compat.md` for the supported MyNPCs script surface.

- 2026-09-27 continuation: the Full-DMZ flight render proxy now gives DMZ's fly-clip predicate
  a forward delta in the smoothed body direction, so turning toward an airborne target selects
  the forward flight clip. The inline color picker uses the generated blue frame and shows the
  selected saturation on its value bar. Native NPC scripts now expose MyNPCs' exact
  `event.npc.getStoreddata()` and `getTempdata()` method names with the six pinned `IData` calls;
  persistent numeric values remain numeric. `meleeAttack(event)` fires for native direct melee
  damage and can change or cancel that hit; `damaged` can change incoming damage and `target`
  cancellation is honored. Script editor docs and examples show the compatible syntax. Focused
  and full unit suites and distribution/API builds passed. Flight appearance, picker visuals,
  script hooks and combat behavior still need a fresh in-game check. Other MyNPCs event domains,
  including timers and generic projectiles, are not implemented as native script hooks.

- 2026-09-27 Claude follow-up: see `CLAUDE_HANDOFF_2026-09-27.md` and the CHANGELOG entry of the
  same date for the audit fixes, flight steering, bundled Nashorn script runtime and 1:1 script
  screen, inline colour picker, script speech bubbles and the todolist items. Protocol 97.
  Unit/build ladder green; live game not exercised.

- Profiler-verified tick-cost fix shipped: `NpcCombatProfile.readCached()` (tag-identity
  WeakHashMap cache) replaced per-tick profile NBT re-parsing at ~35 read-only call sites across
  23 files, and `NpcFormDisplayTuning` config JSON parsing is memoized with (mtime,size)
  invalidation plus a 1 s stat throttle. Spark CPU captures on an identical dedicated-server
  scenario (36 NPCs, 15 force-loaded chunks, ~95 s windows) show server-thread busy time falling
  from 45.3 s (46.7%) to 8.7 s (9.0%); NBT self-frames collapsed to ~1%. The world was never
  TPS-saturated (~19.97 TPS throughout), so busy-time is the honest metric. Three early profiles
  were uploaded to spark.lucko.me (codes recorded in `QWEN_HANDOFF_2026-09-27.md`).
- Phase N (importer) closed: `PlacedNpcProfileMigrator` gained six apply methods (~47 keys);
  `NpcSourceKeys` now marks 77 keys CONSUMED and 119 UNMAPPED with honest reasons. Remaining
  MyNPCs areas (bank/store/transport/controller records, movement keys, potion/weapon editor
  ordinals) are data-blocked, not code-blocked. 123 importer tests green.
- Phase M (schema honesty) closed: editor rows for cape/overlay/layers and the dead `aimAccuracy`
  field carry honest disabled labels pinned by `EditorSchemaHonestyTest`; `docs/xeno-npc-schema.md`
  updated. Follow-ups shipped the same day: heavy-hit (`vanishStrike`, `boneCrusher`) paths now
  trigger `playActionAnim`, and `XenoNpcEntity.routeAttack` falls back to the DMZ `attack1_1`
  clip when a rig lacks a plain `attack` clip. Both are compile/test-verified only; in-game
  animation is not verified.
- Validation ladder: full `./gradlew test -PofflineMcMeta` green (2,668 tests, 0 failures, re-run
  after the final animation edits); `build jarJar serverJar` SUCCESS; `xenopixelsmod-Server-0.5.0-1.21.1.jar`
  has zero `META-INF/jarjar/` entries; `buildApiExampleAddon` SUCCESS. `runApiTestClient` now boots
  fully: root cause of the prior `UnsatisfiedLinkError: lwjgl.dll` was that the `lwjgl-*-natives-windows-x64`
  jars are absent from this machine's Gradle cache, so LWJGL had nothing to extract and no library
  path. `build.gradle` now sets `org.lwjgl.librarypath` for the `client`/`apiTestClient` runs to a
  gitignored `run/lwjgl-natives/` dir holding the matching 3.3.3+5 natives (resolves to null when
  absent so other machines/CI fall back normally). A fresh run reached GL 4.6, loaded
  `XenoPixels API Example 1.0.0`, joined `New World (7)`, and ran the API live on the server thread
  (`[xeno-api-example] sparking.meter count=1 detail=Dev`) with NPC animation clips resolving. Note:
  `C:\JavaUWP\staging\cache\natives-1.21` is a different LWJGL build (its jemalloc lacks
  `je_free_sized`) and is rejected by LWJGL's version check, so it is deliberately not used.
- New user-reported gaps opened (not yet implemented): aerial pursuit still approaches sideways
  (W+A/W+D strafe look) with body/rotation glitching while tracking the player, and the color
  picker needs a rewrite onto generated bluish DMZ-style atlas assets with the picking fixed.

### Implementation status — 2026-09-26

- Native editor reliability follow-up: ordinary text fields now retain partial input while role,
  job, toggle, cycle, and new store-ID choices refresh dependent controls without visiting another
  page. Native Save waits for a revision-scoped server acceptance result and keeps the draft open on
  rejection. The editor holds its controls during that round trip. The main packet protocol is 92.
  Display and Night now open an atlas texture browser over effective vanilla, mod, and enabled
  resource-pack PNGs, with namespace/category filters, search, scrolling, and a preview. The
  Display page also accepts a Minecraft account name; the native humanoid and GeckoLib renderers
  resolve its server-synced UUID through the pinned client SkinManager, and FULL DMZ already reads
  the same profile field. Bard and Guard honor a persisted Job Enabled switch; old job NPCs default
  to enabled. Focused tests and the full 2,487-test suite pass, along with the 0.5.0 client/server
  distribution build; the server jar has zero nested jarjar entries. The new picker and save flow have not
  been exercised in a fresh game client.
- Outstanding parts of the user's all-controls request: local PNG upload shared by a server,
  URL skin rendering in the native NPC path and player-name skin behavior in a live client;
  alternate night profiles; cape,
  overlay and layer controls; availability rules; NPC-to-NPC conversations; trade ignore-damage,
  ignore-NBT and linked markets; global linked templates; working global recipes/carpentry and
  natural spawns; and the reserved jobs beyond Bard, Healer, Guard, Item Giver and Follower. These need server schemas,
  validation and runtime consumers before their controls can be made live. The loaded-assets
  browser is not a complete MyNPCs skin-source browser.

- 2026-09-26 flight grounding follow-up: the earlier "grounded targets switch off Saga flight"
  pass fixed the brains' `steer()` but not the four-tick `NpcCounterpartSync` push — the bridge
  re-applied `flySkillOn` onto the CNPC navigator right after every landing, which is what kept
  the live-client symptoms (bobbing near target, sideways drift, no grounding when the retaliate
  target lands or switches to a walking mob, FPS/TPS churn from repeated `updateAI` navigator
  rebuilds). `NpcFlightBridge.apply` now suppresses the flying push while the NPC is engaged with
  a grounded live target, and v1 `NpcCombatBrain.land` damps air-chase momentum like the saga
  brain's. New `NpcFlightBridgeTest`; focused tests and the full suite pass. Live-client
  re-verification pending; the broad "many NPCs" TPS report still lacks a profiler capture
  (not verified — needs spark before further tick-cost work).

- 2026-09-26 follow-up: native XenoNPC Ki Sense lock now tracks the current retaliator's
  midpoint every server tick with DMZ Z-lock style 0.15 yaw/pitch smoothing. Locked ki shots use
  full target-motion lead at launch and remain non-homing. The flight chase closes to a one-block
  center gap for ordinary hitboxes, expanding only for larger bodies; other NPC/clone callers keep
  their historical three-block gap. Both combat brain paths release flight when the target lands,
  including a DMZ Fly-off player standing on support during a transient false `onGround` tick.
  Focused tests, the full 2,483-test suite, and distribution packaging pass; the server jar has
  zero `META-INF/jarjar/` entries. In-world visual behavior remains not verified.

- Follow-up fixes now expose the existing native XenoNPC Knockable and Damageable protections in
  the DMZ hub, with their server-validated save fields. The DMZ Skills page adds an opt-in
  retaliator lock under Ki Sense; the lock requires an active skill, Retaliate mode, DMZ's
  Ki Sense range and line of sight. It is server-side NPC acquisition, not DMZ's client-only
  player Z key. Grounded targets switch off Saga flight like pressing the flight toggle;
  airborne targets stay in flight even when the NPC reaches their height. Close-range
  steering separates horizontal standoff from vertical correction, and vanilla leap yields
  while Saga flight owns movement. Skills and Techniques page text is scaled to 90% uniformly.
- Regression tests cover signed flight thresholds, grounded-target landing policy, stable close
  horizontal steering, Ki Sense lock requirements, profile round-trip and editor save acceptance.
  On 2026-09-25, focused tests and the full `./gradlew.bat test` run passed (2,481 tests,
  0 failures). No in-world flight or target-lock behavior was verified in this pass.
- Implemented the requested V9 toggle-aware combat profile, ordered attack slots, and suppression of
  generic swing animation when a usable ordered clip is configured. Regression coverage includes
  enabled, disabled, and blank slots. Profiles predating the new schema receive the requested four
  native XenoNPC defaults in right/left/right/left order; existing authored slot sequences and
  MyNPCs/CustomNPCs profiles are preserved.
- Implemented the requested controls in the **native XenoNPC editor**: the DMZ hub has a Fly
  toggle; its Skills page exposes configured DragonMineZ skills with enabled and level controls;
  Techniques exposes selection plus damage/cooldown levels; Forms exposes form and stack mastery.
  These controls persist through the native save allowlist. The hub Fly control sets both the DMZ
  skill and Brain Fly action; the Brain page can still opt out. Combat flight requires both the brain's
  Fly action and the NPC's enabled DMZ Fly skill, and the brain no longer silently enables that
  skill while chasing. While Saga flight owns movement, native ground navigation and leash movement
  yield; landing restores gravity and releases the movement claim. The legacy MyNPCs/CustomNPCs
  screens are not the destination for these XenoNPC features.
- Smoothed close-range DMZ Saga flight: approach speed now brakes toward a three-block target gap,
  and the flight state uses a four-block takeoff threshold and releases on landing. This
  targets the near-target sideways weave and vertical bob caused by full-speed overshoot and
  toggling flight at one height threshold.
- Unified NPC speech and dialogue palette resolution. Automated coverage verifies that a lowercase
  red profile setting survives visual-options conversion and resolves to the generated red atlas.
  Advanced Dialogue now preserves each answer palette while filtering quest options and sends
  speaker-line and answer palettes to the client. Regression coverage confirms a red answer stays
  red through filtering and the packet instead of inheriting the NPC's default answer palette. The
  main channel protocol is 91 for this wire-format change.
- Implemented the quest authoring and runtime changes listed above, the dedicated journal and
  completion card. The journal, completion card, and new-quest toast use generated atlas art. Their
  screens now dim the world without invoking Minecraft's blur post-process; the journal remains on
  the inventory's left side beside faction standings. Long quest titles wrap in journal details and
  the completion card.
- The historical implementation notes below this ledger describe earlier snapshots; use this
  section for current scope and status.
- Automated validation on 2026-09-25 passed: `./gradlew.bat test` (2,479 tests,
  0 failures), `./gradlew.bat build jarJar serverJar -PofflineMcMeta`, and
  `./gradlew.bat buildApiExampleAddon -PofflineMcMeta`. The server jar contains no
  `META-INF/jarjar/` entries, and the DragonMineZ dependency SHA-256 matches `gradle.properties`.
- The user authorized launching Minecraft. `./gradlew.bat runClient` without
  `-PofflineMcMeta` loaded a fresh 0.5.0 dev client; its log includes a Controlify mixin error.
  No world was opened and no screenshot was taken during that launch. The user later confirmed
  bubble colors render as selected. Dialogue, quest hand-in, JEI/REI overlay interaction, V9
  flight pursuit and attack appearance remain gameplay checks.

### Saga combat and editor reliability

- Add a V9 DragonMineZ saga-combat brain closer to ordinary DMZ saga quest NPCs: pursue, search,
  and fly toward the target; use configured attack animations in order; support ki waves and ki
  clashes; keep the new behaviors selectable through the editor toggles; leave deflection disabled
  by default for this profile.
- Stop retaliation from endlessly attacking empty air. If ordered attack animations are enabled,
  skip the generic XenoNPC punch/hit animation so it does not overlap the chosen animation.
- Keep editor choices synchronized with available server options, report rejected saves instead of
  closing as if they succeeded, and synchronize NPC renames to connected clients without requiring
  a relog.

### Dialogue bubbles

- Keep the speaker question/dialogue blue and positioned above the NPC; put answer bubbles below it
  as a separated group above the NPC. Prevent answer bubbles from overlapping each other or the
  speaker bubble.
- Preserve Minecraft text formatting, support per-answer bubble color with yellow/orange as the
  default answer palette, and make the color picker change rendered bubble colors.
- Allow the bubble head offset to be set to zero while preserving the previous 0.7-block default
  for existing and newly created NPCs.
- Ease bubble motion and removal smoothly; make ambient/random bubbles independently disableable.
- Allow dialogue options to select already-defined global quests rather than requiring users to
  type quest IDs. Dialogue quest offers must start the same server-owned quest definitions used by
  the rest of the quest system.

### Quest behavior and authoring

- Import MyNPCs and CustomNPCs quests into Xeno quest definitions for use by Xeno dialogue options,
  retaining objectives, titles, descriptions, rewards, completer, and quest text where supported.
- Make KILL_NPC accept long full visible NPC names, offer a loaded-target cycle/picker for Xeno,
  MyNPCs, and CustomNPCs when installed, and count actual deaths of the selected target.
- Support quest command aliases: `@dp` resolves to the player completing the quest; vanilla `@p`
  searches from the quest-giver location, falling back to the claimant when the quest had no NPC
  giver (for example a command-started quest).
- Expose repeat behavior as exactly: No, Yes, MC daily, MC weekly, RL daily, RL weekly. Keep the
  current two-XenoSkill-point reward as the default, add an independent point toggle and an
  independent reward-action toggle, with reward actions disabled by default.
- Support selecting an existing global quest from NPC dialogue choices. Do not create a sample or
  test quest automatically when an NPC is assigned the Quest role.
- Preserve NPC-hand-in quests as ready until claimed; remove them from the active journal after the
  successful hand-in/final claim.

### Journal and completion presentation

- Replace the inventory-side quest panel with a dedicated Xeno journal screen containing a
  scrollable quest list and a detail pane. Long quest titles must remain readable (clip in the list
  with full-title access and wrap in details); keep JEI/REI from intercepting interaction while the
  journal is open when the installed integration permits it.
- On completion, show a Xeno-generated completion card with the quest title, author-selected
  description, reward summary, and dismiss action. Let each quest choose its frame shape and
  palette. Remove completed entries from the active journal after a successful final hand-in.
- Generate the journal and completion frames through `tools/atlas-panels/xeno_extra_specs.py` using
  the documented atlas generator workflow; use native sprite dimensions and all four supported
  palettes. Store generated resources under `src/generated/resources` per repository guidance.
- Generate the “New Quest” toast frame through the same tool; a registered toast sprite without its
  four palette PNGs is a runtime asset defect.

### Native XenoNPC DMZ editor parity

- Put DMZ Fly, skill, technique, and mastery controls in the native XenoNPC editor. The DMZ hub
  exposes Skills, Techniques, Forms, ordered Attacks, Appearance, and the Fly toggle.
- For each configured DMZ skill, expose enabled state and level. For each supported executable
  technique, expose selection plus damage and cooldown levels. Expose normal and stack mastery for
  the selected form. Persist all these values through the server-validated XenoNPC save path.
- Make the V9 combat brain use flight only when both its Fly action and the native NPC's DMZ Fly
  skill permit it; retain the skill's stored enabled state while chasing. Do not route these
  XenoNPC controls into the MyNPCs or CustomNPCs editor.
- Expose native XenoNPC Knockable and Damageable toggles using the existing server-protected
  profile fields. When DMZ Ki Sense is enabled, provide a separate option to keep the NPC locked on
  to its retaliated attacker. Grounded retaliators should turn Saga flight off; keep its close-range
  steering from bobbing vertically or sliding sideways, and reduce Skills/Techniques menu text
  together so those pages remain visually uniform.

### Durable project updates and validation constraints

- Update this root `plan.md`, `CHANGELOG.md`, the existing parity HTML in the repository root, and
  `C:/Users/Admin/Downloads/xeno-npc-parity-map.html`. The user directed that the root parity file
  be created from the Downloads copy if it did not already exist.
- Bump the mod artifact version to `0.5.0-1.21.1`; increment the sequential main network protocol
  for the appended completion-popup packet. Preserve main-channel packet order.
- Keep the repo's Java 21 / Minecraft 1.21.1 / NeoForge 21.1.248 target and protect pre-existing
  dirty and untracked work. Do not commit or rewrite history.
- The user previously prohibited in-game testing and screenshots, then authorized a client launch
  on 2026-09-25. A fresh dev client loaded, but no world was opened and no screenshot was taken.
  The user later confirmed bubble colors render as selected. Do not treat startup as gameplay
  proof; keep future gameplay checks to a disposable test world.

### Decisions captured from user responses

- Remove a quest from the active journal after NPC hand-in or final claim.
- Use a dedicated Xeno journal with a scrollable quest list and detail pane.
- Choose completion popup palette and frame per quest.
- Anchor `@p` at the quest giver, with claimant fallback; `@dp` is the claimant.
- Include Xeno NPCs, MyNPCs NPCs, and CustomNPCs NPCs in the kill-target picker when those mods are
  present.
- Quest rewards use independent toggles and preserve the current two-point default.
- Cover all requests in this combined NPC/quest effort in the plan ledger.

Historical implementation snapshot updated: 2026-09-21 · Target: MC 1.21.1 / NeoForge 21.1.248 / DragonMineZ 2.1.3 / `xenopixelsmod`

**Claims here are repo-source or decompiled-jar verified.** Where something is not verified, it says
so. Nothing is inferred from a name.

Current gate: `build jarJar serverJar -PofflineMcMeta` green, **1521 tests, 0 failures**, server jar
has 0 `META-INF/jarjar/` entries.

---

## Note for whoever picks this up: two agents are working here

A Codex session reworked the NPC editor in parallel and recorded its decisions **only** inside
`XenoMyNpcsGuiOrderImages/docs.md`. Read that file before touching the editor. What it added:

| File | What it does |
|---|---|
| `network/packet/XenoNpcSavePolicy.java` | Server-side whitelist of editable profile keys, with per-key tag-type checks and a 96-key cap. Anything else is rejected. |
| `client/npc/editor/NpcEditorScreenManifest.java` | One source of truth for tab and sub-screen order, mapped to the 34 reference screenshots. |
| `client/npc/NpcSoundCatalog.java`, `XenoNpcSoundPickerScreen.java` | The `[Select Sound]` picker. |
| `network/packet/XenoNpcEditorLockPacket.java` | Lock/Unlock as its own packet. Better than routing it through the save. |

**The whitelist is the thing to understand.** A profile key is editor-writable only once something
*consumes* it. That rule is right and is now enforced by `XenoNpcSavePolicyTest`. Codex used it to
disable four fields that were stored but unread; this round gave each one a consumer and put the key
back. **If you add a profile field, add its consumer before adding its key.**

---

## Done this round

### A. `/xenostructure summon` reaches every DragonMineZ NPC

`compat/dmz/DmzEntityCatalog.java` reads `BuiltInRegistries.ENTITY_TYPE` instead of a hand-written
map, so a DMZ update widens the command on its own.

```
/xenostructure summon master <id>    23 masters (14 were reachable before)
/xenostructure summon saga   <id>    144
/xenostructure summon mob    <id>    traders, Red Ribbon, dragons, wildlife, space pod
/xenostructure summon quest  <id>    13 quest givers
/xenostructure summon <master>       unchanged, still the structure-master form
```

14 projectiles and effects are excluded by an explicit list, **not** by `MobCategory.MISC` — that
category also holds the space pod and the eternal dragons, which are worth summoning.

**Bulma works because she is not an entity.** `SAGA_BULMA` exists but is a story fighter with no
dialogue. The quest giver is `dragonminez:quest_npc` with `QuestNpcId="bulma"`; DMZ's own
`QuestNPCEntity.mobInteract` opens `QuestNPCDialogueScreen`, so the command only has to set the
identity. The 13 ids come from the `quest_giver` fields of DMZ's shipped sidequest JSONs.

**Unverified:** DMZ ships `saga_bulma` art but no quest-NPC Bulma model or texture that I could
find, so a summoned quest giver may render as the generic NPC. Do not invent art for this.

### B. The four disabled fields, each given a real consumer first

| Field | Consumer built | Then |
|---|---|---|
| `NightTexture` | `NpcCombatProfile.textureFor(level)`, read by **both** renderers so they cannot disagree after dusk | key re-enabled |
| `LinkedNpcs` | `npc/XenoNpcLinkPropagation.java` — copies an accepted save to every linked NPC | key re-enabled |
| `MarkIcon` / `MarkColor` | six generated atlas glyphs + `client/npc/mark/NpcMarkRenderer.java` | keys re-enabled |
| `EditingLocked` | — | **stays out**; its own packet is the better route |

Details worth keeping:

- **Link propagation is one hop.** Two NPCs linked to each other is normal, not a mistake; following
  links transitively would bounce an edit between them forever. One hop makes a cycle harmless
  without a visited set. A locked target refuses the copy, or the lock would be bypassable by
  editing something linked to it. An unreachable link is **skipped, never pruned** — an NPC in an
  unloaded chunk is not a dead link.
- **Only policy-accepted keys propagate**, so a linked NPC can never receive a field the editor is
  not allowed to write directly.
- **Marks sync through `visualOptionsTag`**, because the profile is server-side and the renderer is
  not. Same trap the battle-power work hit.
- **The mark glyphs are original**, generated by `render_mark` in the atlas generator, which says so
  in its own docstring. MyNPCs' icons are its art and are not reproduced. The Marks screen is still
  **one** mark, not the reference's list with per-entry availability — those two rows stay disabled
  rather than pretending a single mark is a list of one.

---

## Done — Phase B so far

- **Quest rewards.** `QuestReward` (skill points, xp, items, commands) on each `QuestDef`;
  `completeQuest` reads the finished quest's own reward instead of a hardcoded +2. `DEFAULT` is
  still that +2, so a quest declaring nothing pays what it always did. Faction points deliberately
  omitted - no faction system to receive them, with a test that fails if one is added early.
- **Dialogue quest options.** `XenoNpcDialoguePacket` handled COMMAND and dropped everything else
  ("until the quest engine exists"). It exists, so QUEST options now route through
  `ParallelQuests.start` - one set of rules, shared with `/xenoquest start`. The client enables them
  (ROLE stays disabled, no economy roles) and still sends only the option index.
- **Chat bubbles: three of six categories had no trigger.** KILL, RANDOM and WORLD were loadable
  from role definitions and never spoken. KILL fires from its own `LivingDeathEvent` handler
  (the existing one is about the entity that *died* and bails for non-CustomNPCs); RANDOM/WORLD
  from a staggered tick via `XenoNpcSpeech.ambient`, 20s cooldown, RANDOM with a player inside 16
  blocks and WORLD without.
- **Tab strip fit.** Narrowing the frame to 420 left the ten tabs ~one cell too wide and pushed X
  off the edge. `AtlasTabStrip` now takes a width budget and steps cells down the generated ladder,
  widest first, never below what a label needs. Plus the reference's gap before Delete.

## Done — the visualizer / dialogue / attacks / roles round

- **Editor size.** Shorter frames generated (200/240/280 beside the 320) and the shortest that
  holds the page is drawn, so a sparse tab no longer leaves a band above Save/Close. Pagination
  still runs against the full height, so page breaks cannot shift and nothing can be clipped.
- **Tab strip fit.** `AtlasTabStrip` takes a width budget and steps cells down the generated ladder,
  widest first, never below what a label needs. Plus the reference's gap before Delete. This is what
  had pushed X past the frame edge after the frame was narrowed.
- **Visualizer extracted** to `client/npc/editor/NpcLivePreview` - native, no `client/compat/npc/...`
  dependency - and placed on the appearance screen, which never had one because it is a separate
  `ScaledScreen`.
- **Appearance 1:1** with screenshot 47: `Body / Face / Hair / Aura` sub-tabs, reference row order,
  and the body-type cycler reads `n / total`. A test asserts every control lands on exactly one tab.
- **Attack pages.** The 20 ordered slots and `NpcMeleeAnimCycle` already worked; nothing could
  author them. New Attacks screen off the DMZ hub, and the count raised to **40** (safe: one
  constant, and the reader clamps over a stored list, so a 20-slot save loads unchanged).
- **Dialogue bubbles.** Line plus option bubbles above the NPC on the speech-bubble billboard, four
  palettes per NPC via cycle buttons. Clicks are projected through `WorldToScreenCache`; number keys
  1-9 also pick, because a bubble can end up off screen. Navigation resolves client-side, quests and
  commands go to the server as an index. The old screen stays behind `XenoClientConfig.dialogueBubbles`.
- **Roles.** Trader and Quest no longer fight or retaliate and stay near where they were placed;
  Guard picks up `Monster` targets near its home and returns to it; Companion follows its owner as
  before. `XenoNpcRoleBehaviour` holds the predicates so the brain and the targeting cannot disagree.

## Done — factions reach the client

- **The datapack trap, a third time.** `XenoFactions` is a `SimpleJsonResourceReloadListener`, so it
  exists only on the server. Both faction screens were showing three hardcoded fake names
  (`Aggressive` / `2` / `#dd0000`) because the client had no way to know what a pack had loaded -
  the same shape as the mark icons and the bubble palettes.
- `client/npc/faction/ClientFactions` - a thin client mirror (`id`, `name`, `color`, `hostileTo`,
  `defaultStanding`). Insertion-ordered: it held a `Map.copyOf` at first, which is **unordered**, so
  every list below would have shuffled between reloads. A test caught it.
- `network/packet/SyncFactionsPacket` - S2C, capped at 256 factions and 64 hostile entries each,
  and bounded on **read** as well as write. Negative standings are shifted by `MAX_STANDING`
  because VarInt is unsigned-friendly only.
- `npc/faction/XenoFactionSync` - on `OnDatapackSyncEvent`, which fires on join *and* after every
  `/reload`, so an operator who writes a faction sees it without rejoining.
- **Advanced > Factions had no way to set an NPC's faction at all.** It pointed at the registry
  browser, same as Global > Factions; `currentFaction()` was read on save and written straight back.
  Split: `npcFactionRows()` assigns, over a cycler of loaded ids plus `(none)`, showing the chosen
  faction's colour and hostility list, and warning when an NPC names one a pack has since removed.
  `factionListRows()` browses. Add/Remove stay disabled on the browser and say why - a datapack owns
  the registry, so editing a faction means editing its JSON.
- Protocol **77 → 78**; the packet is appended, as they always are.

## Done — dialogue actually works on a dedicated server

`OpenXenoNpcDialoguePacket` carried only the dialogue's **id**, with a comment reasoning that "both
sides load the same datapack". They do not. `XenoDialogues` registers through
`AddReloadListenerEvent`, which is server-only - so on a dedicated server the client's map is empty
forever, `XenoDialogues.get(id)` returned null, and **no conversation ever opened**. It looked fine
only in single-player, where the integrated server shares the JVM and therefore the static map with
the client. Fourth instance of the same trap, after the marks, the bubble palettes and the factions.

- The packet now carries the tree. Bounded on read as well as write: 128 nodes, 16 options a node,
  512 characters of node text, 256 of option text. Over-long text is **clamped rather than
  rejected**, because `writeUtf` throws past its cap and a dropped packet opens nothing at all.
- Authority is unchanged. Picking an option still goes back as an **index** through
  `XenoNpcDialoguePacket`, which re-reads the dialogue from the server's own datapack before acting.
  What travels here is what to draw.
- `XenoDialogue.fromJson` and `XenoDialogues.apply` held `Map.copyOf`, which is **unordered** -
  fixed alongside `ClientFactions`, so node and dialogue order follow the pack.
- Protocol **78 → 79**. Adding fields to an existing packet is as much a wire change as adding one.
- Tests pin the protocol as a **floor** (`ProtocolVersion.current() >= N`) rather than an exact
  number, so a later unrelated bump does not force someone to edit a check that was doing its job.

## Done — a dialogue can be written from the editor

Advanced > Dialogs was twelve disabled `Select Option` rows saying it "requires a native writable
registry". That was true of the *approach*, not of the feature: dialogues load from a datapack, and a
`SimpleJsonResourceReloadListener` reads a pack and has nothing to write back to, so an in-game
editor could never own one that way.

So it does not go there. An NPC's dialogue lives on its **own combat profile**, beside the attack
slots and the bubble palettes, and travels the save path they already use.

- `npc/dialog/XenoDialogueNbt` — the tree as NBT. Nodes are a **list, not a compound**, because NBT
  compounds do not keep insertion order and node order is what the editor pages through.
- `NpcCombatProfile.dialogueTag` / `dialogue()` / `setDialogue`. Deliberately **not** in
  `visualOptionsTag`: a conversation is needed by the one player who opens it, and the open packet
  carries it then — replicating every nearby NPC's whole script to every nearby player would be
  paying for something nobody reads.
- **The two coexist.** `XenoNpcEntity.dialogueFor` prefers the NPC's own and falls back to the one
  its role names, so a pack can still ship one conversation per role and giving a single NPC its own
  script does not disturb the rest. `XenoNpcDialoguePacket` **delegates to the same method** — if the
  two sides resolved differently, picking option 2 would run option 2 of a different tree.
- `client/npc/editor/DialogueDraft` — mutable and ordered, because `XenoDialogue` will not hold a
  conversation mid-edit: a node just added has no name, and a start pointing at a node about to be
  renamed resolves to nothing. Renaming a node **moves the start and every option pointing at it**;
  without that, a rename silently dead-ends every link while the dialogue still looks valid.
- `problems()` is the other half of that trade. A draft tolerates a broken state, so something has
  to say so: the screen lists what is missing, saves anyway, and the NPC keeps using its role's
  dialogue until the draft resolves. Refusing to save a half-written conversation would be worse —
  half-written is the normal state of one being written.
- Three screens: the line list (with `*` on the start and an `Opens on` cycler), one line (name,
  says, its answers), one answer (reads, a type cycler, and **only the field that type uses** — the
  other three would be controls writing where nothing reads).
- Whitelist key `Dialogue` **last**, with `validateDialogue` bounding depth and every string against
  the same limits the open packet carries. A dialogue that saved but was too large to send would
  save and then never open.
- A default node name is numbered by **list position**, so `node_2` is the second line. A test
  caught it naming the second line `node_1` — nothing clashed, but it reads as a mistake next to the
  row numbers.

## Done — quests declare what they want, and a pack can define them

`ProgressionEvents.onDeath` switched on the quest's **id**: `case "kill_mobs"`, `case
"kill_players"`, and a `default` that counted any non-player death. So the three quests that existed
were the only three that could — a fourth meant editing the event handler, and any quest the switch
did not recognise silently became "kill anything". That last part is the dangerous half: it does not
fail, it quietly finishes the wrong quest. The dialogue editor will happily let an author type any
quest id, which is what made this urgent.

- `QuestObjective` with six types, each with a real hook behind it: `KILL_MOBS`, `KILL_PLAYERS`,
  `KILL_TYPE` (parameterised by entity id), `DUMMY_DAMAGE`, `DUMMY_HITS`, `TALK_TO_NPC`. An
  objective nothing advances is a quest that can never be finished, so none were added speculatively.
- `QuestDef` gained a `goal`; both handlers now ask `ParallelQuests.goalFor(id)`. An unknown quest
  returns **null**, not a default goal — a default would restore exactly the old behaviour.
- `XenoQuests` — a datapack loader at `data/<ns>/npcs/quests/*.json`, the same shape as
  `XenoDialogues` and `XenoFactions`. A pack quest **shadows** a built-in of the same id, so the
  three shipped ones are defaults rather than reserved names.
- Loading is strict where being lenient would lie: an unrecognised `objective` name, a `kill_type`
  with no parameter, or a `target` below 1 all refuse the quest rather than producing one that
  counts the wrong thing or can never finish. A bad file costs one quest, not the pack.
- `QuestReward.fromJson` — skill points, experience, items, **faction points** and commands, so a
  pack can pay standing now that factions exist.
- `TALK_TO_NPC` counts **once per NPC**, tracked in `XenoPlayerData.questVisited` (cleared on quest
  start and end, bounded, and persisted). "Speak to three masters" has to mean three masters;
  without this it would mean clicking one master three times.
- `/xenoquest list` shows each quest's objective and target and **reports quests that failed to
  load** — an id offered by a dialogue that answers "unknown quest" with no clue why is the worst
  version of this.
- Two shipped example quests exercise the new types: `wolf_trouble` (`kill_type`) and
  `pay_respects` (`talk_to_npc`).

## Done — a MyNPCs-shaped schema, a writable world store, and four persistence bugs

### The persistence bugs, all verified from NeoForge source rather than recalled

- **Placed NPCs were deleted when players walked 128 blocks away.** `ModEntities` registers Xeno
  NPCs as `MobCategory.CREATURE`; `XenoNpcEntity` never called `setPersistenceRequired()` nor
  overrode `removeWhenFarAway`. `Mob.checkDespawn` discards a mob past the category's despawn
  distance (128 for CREATURE), `removeWhenFarAway` defaults to **true**, and `ServerLevel` calls it
  every tick. An operator placed an NPC, rode away, and it was **gone** — discarded, not killed, so
  the respawn store never heard about it. Fixed with `requiresCustomPersistence()` → true, chosen
  over a constructor flag because an override needs no NBT and so also rescues NPCs already standing
  in existing worlds.
- **Dead NPCs stopped respawning after any restart.** The deadline was
  `MinecraftServer.getTickCount()`, a plain `private int` never written to disk that restarts at 0
  each launch. Now `getGameTime()`, which `PrimaryLevelData` persists under `"Time"`. Entries
  written under the old scheme are due immediately — they were overdue anyway.
- **`PlotLease` had the identical bug**, so after a restart no existing lease was charged. Three
  call sites now agree on the clock, including the "due in" readout, which was the last one still
  reading the old counter. The ticker's *other* `getTickCount()` is a throttle and correctly left.
- **Respawn entries were kept forever** when their chunk never loaded. Capped and expired.
- **`XenoPlayerData.loadNBT` could zero a player's pools** — `Ki`/`MaxKi`/`Stamina`/`MaxStamina`
  were read unguarded while every field below them was guarded. Guarded now.
- **The repo's own converter was losing Companion data.** `NpcCloneConverter` dropped all twelve
  `Companion*` keys believing MyNPCs had no home for them; `RoleCompanion` reads every one, and the
  real fixture carries `Role: 6` (COMPANION) alongside them. Dropping them while keeping `Role: 6`
  reset a companion's stage, talents, experience, inventory, age and owner, silently.

### The entity tag is flat, as theirs is

Verified from the jar and from real saves: their `readAdditionalSaveData` hands the *same* root
compound to every sub-object, so `"display"`/`"stats"` are error-log labels and not sub-compounds —
a real clone has 177–187 sibling keys. Ours now matches.

The collision audit (`XenoNpcFlatSchemaTest`, checked against the vanilla key set extracted from
`Entity`/`LivingEntity`/`Mob`) found **two** real clashes, one of which I had not predicted:

- `Brain` against `LivingEntity`'s brain-memory compound. **Dropped** rather than renamed: ours held
  the constant V5 and `fromTag` never read it back, so it was a stored-but-unread field wearing a
  taken name. The profile's `BrainVersion` is the live one.
- `Schema` against `NpcCombatProfile`'s own, currently 17. Identity's is now `NpcSchema` — flat,
  each would have read the other's number and the profile would have re-run its entire migration
  chain over an already-migrated tag.

`XenoNpcData.unwrapLegacy` lifts a pre-flatten tag on read, so existing worlds load and are written
back flat on their next save.

### The world store

`<world>/XenoNpcs/{clones,dialogs,quests,factions,banks,transport,linked,spawns,recipes}/`, SNBT in
`.json`, one file per entry. Built pure-first: `XenoNpcStorePaths` (ids → paths, **rejects rather
than rewrites**), `XenoNpcStoreSchema` (per-file version; older upgrades, **newer is refused and
locks the category**), `SnbtFiles` (size checked before opening, depth counted before `TagParser`
sees it, atomic temp+move lifted from `NpcWorldMigrator`), then `XenoNpcWorldStore` over them.

- Every category is a **folder**, where MyNPCs keeps factions/banks/transport/recipes/spawns as one
  blob each. A per-entry file fails alone; one bad byte in a blob takes every faction with it.
- `XenoNpcDataSource` layers **store → datapack → built-in**, the precedence
  `ParallelQuests.definition` already set. Shipped pack content keeps working, and deleting a world
  entry brings the pack's back rather than leaving a hole.
- Wired into three real consumers so none of it is dead code: `XenoNpcEntity.dialogueFor`,
  `SyncFactionsPacket.current()`, and `QuestReward`'s faction grant.
- **Global ▸ Factions and Global ▸ Dialogs are live**, through a new operator-gated
  `XenoNpcStoreWritePacket` that bounds-checks its wire ordinal and answers every rejection. Dialogs
  qualified because `XenoNpcDataSource.dialogue` already consumes what the screen writes — the rule
  is a control goes live once its value is read, and not before.
- `SyncNpcStoreIndexPacket` + `ClientNpcStoreIndex` carry **what the store holds**, not its
  contents: listing needs a name, editing needs the whole thing and only one at a time.
  Broadcasting every dialogue in the world to every client would be paying continuously for
  something almost nobody opens. It rides the faction sync's hook — same two moments, so there is
  no window where one arrived and the other had not — and any accepted write re-broadcasts it, so a
  second operator's screen cannot keep listing what the first deleted.
- Found while wiring it: **neither client mirror was cleared on disconnect**. Quitting to the menu
  and opening a different save left the previous world's factions listed in the editor — worse than
  an empty screen, because they look real until something is opened. Both now clear in
  `ClientConnectionState`.
- Protocol **79 → 81**.
- The stale `"Unavailable - no native server registry exists."` is gone — it stopped being true the
  moment the store landed.

`docs/xeno-npc-schema.md` carries the key-by-key MyNPCs mapping the importer will be written
against, with provisional rows marked as provisional rather than filled in by inference.

## Done — a review pass against the project's own skill

Ran `xenopixels-addon-development`'s audit and checklists over this round's work. Its two reported
FAILs are false positives in the audit script (it looks for a `java_version` key in
`gradle.properties` where this repo uses a Gradle toolchain, and greps for a literal
`dragonminez-2.1.3.jar` where the build interpolates `${dragonminez_version}` — the same run passed
the jar's SHA-256 check). Two genuine gaps came out of the checklists:

- **No stale-write detection on store writes.** The skill's packet review asks for "editor lock or
  current interaction state" on every write. `XenoNpcSavePacket` has carried `expectedRevision`
  since it was written; the store write packet shipped without an equivalent, so of two operators
  editing one faction the second silently overwrote the first and **both screens reported success**.
  Nothing would have appeared in a log — the first operator's edits would simply be gone, and the
  obvious explanation ("it didn't save") would have been wrong. Entries now carry a `Rev` that
  increments on every accepted write, the index sends it to the client, and a mismatch is refused
  as `STALE` with the current index pushed back so the screen can recover. Protocol **81 → 82**.
- **Dedicated-server classloading was safe but unenforced.** `SyncNpcStoreIndexPacket.current()`
  and `SyncFactionsPacket.current()` both run on the server and both build a type living in a
  `client.*` package. That is fine today — the server jar ships all of `sourceSets.main.output`, and
  both mirrors import only `java.util` — but the guard was "nobody adds a `Minecraft` reference to
  these two files", enforced by nothing. A `Minecraft.getInstance()` in either would crash a
  dedicated server the first time it built the packet and show up nowhere until then. Now a test.

## Done — dialogue answers at the crosshair, and the bubbles stopped colliding

The bubbles were drawn in the world but an invisible `Screen` sat on top of them purely to supply a
cursor and catch clicks. It worked, and it took the mouse — the player could not look around while
an NPC talked, which is the opposite of what moving dialogue out of a menu was for.

- **No screen on the new route.** `DialogueBubbleInput` answers on
  `InteractionKeyMappingTriggered` — the same event `Bt3CombatClient` already uses, so there is one
  way of suppressing a click here rather than two. Either mouse button picks whatever bubble the
  crosshair is on; sneak leaves; 1–9 still work, moved to a tick handler now there is no screen to
  receive keys.
- **Both buttons are consumed while a conversation is open, even on a miss.** Otherwise a stray
  click punches the NPC mid-sentence. This is the one place the standing "sit alongside vanilla
  input" rule is deliberately not followed, and the test says so out loud.
- **The overlap was a constant.** `hitFor` wrapped a fixed 120×18 GUI-pixel box around each
  option's projected centre. A bubble shrinks with distance; a constant does not. Option centres
  sit ~0.35 m apart, projecting ~22 px apart at 4 m, ~18 at 5 m and ~11 at 8 m against an 18 px-tall
  box — so past about 4.5 m the boxes swallowed each other and the second option could not be
  selected at all. The box is now built from the bubble's **projected top and bottom** with width
  from the sprite's own aspect ratio, so it tracks the art at every range. The distance table is a
  test.
- **The visual crowding** was `GAP_BETWEEN = 3` against a tail worth 22% of sprite height (~6 px):
  each tail nearly touched the bubble below. Raised to clear it.
- `DialogueBubbleChoice` holds what an option *does*, shared by both routes — two copies would
  drift, and the half most likely to drift is what reaches the server. Authority is unchanged: the
  client sends an **index**, the server re-reads its own dialogue.
- **The older screen stays**, behind `dialogueCrosshair`. Found while wiring it: `dialogueBubbles`
  was never written to or read from the config file, so its own javadoc's "reachable by turning
  this off" was not true. Both flags persist now.

## Done — a strict-evidence audit pass

Ran the project skill's audit in `--strict-evidence` mode, which asks of every broad catch around an
external call whether it fails closed for permissions, packets, saves and gameplay authority.

- **One genuine fail-open, fixed.** `NpcFormLookup.compatible()` answered **true** - "these forms
  may stack" - when the DragonMineZ call threw, and swallowed the exception unnamed. It gates
  `NpcTransformSystem:138` and `:330`, so a DMZ change that broke `isIncompatibleWith` would have
  let an NPC stack two forms a pack declared incompatible, silently. Now refuses and logs why.
  Refusing a stack is visible and recoverable; granting one a pack forbade is neither. A null form
  on either side still answers yes - that is "nothing to be incompatible with", not a failed check.
- **Three other fail-open-shaped catches checked by hand and left alone**, because they already
  fail closed: `WorldSaveFlush` (whose own comment records fixing exactly this bug before),
  `ConditionalMixinPlugin` (an unresolvable mod means the mixin is not applied),
  `NpcScriptSay`/`MinecraftFistOwnershipMixin` (both fail toward suppressing). `NpcWorldMigrator`'s
  MyNPCs reflection returns a failure result and does nothing.
- **The 531 "string-based external symbols" is mostly not what it sounds like:** 312 `@Inject`-style
  injection points and 70 `@Mixin(targets=)` declarations, all of which Mixin verifies at classload
  and which therefore cannot silently rot. The number worth carrying is the **136** reflective
  `Class.forName`/`getMethod` lookups, which fail at runtime instead.
- **Two audit-script bugs fixed upstream** in `C:\skills\minecraft-agent-skills` and re-synced: it
  looked for a `java_version` key in `gradle.properties` where this repo uses a Gradle toolchain
  (now reads `JavaLanguageVersion.of`, `options.release`, `sourceCompatibility`), and it grepped for
  a literal `dragonminez-2.1.3.jar` where the build interpolates `${dragonminez_version}` - which
  made it fail a repo for naming the same jar whose SHA-256 it passed in the same run. Strict mode
  now reports 1 failure instead of 3, and the remaining one is definitional: it converts any warning
  into a failure, so a repo integrating four optional mods can never clear it. Left as designed
  rather than quietly loosened.

## Done — reflection evidence recorded, not just counted

The previous pass reported "136 reflective lookups" and stopped there. The project skill's
security-evidence rule is stricter than that: every reflective string crossing a trust boundary
needs an artifact SHA-256 and an inspected symbol, and *"verified in jar"* without both is not
evidence. So the work was done rather than counted — `docs/reflection-evidence.md`.

- **MyNPCs** `ServerCloneController.Instance` / `getDir()` — verified by `javap` against
  `mynpcs-neoforge-1.5.0.jar` SHA-256 `6bbfc44e…1aaf`. Both shapes match their use: `Instance` is
  `static` (so `.get(null)` is right) and `getDir()` returns `java.io.File` (so the cast is right).
- **Create** `ControlledContraptionEntity.controllerPos` — verified against
  `create-1.21.1-6.0.10.jar` SHA-256 `ef87fe57…e37a`. The member is `protected`, so
  `getDeclaredField` is correct where `getField` would have failed.
- **Six mods have no artifact here** — LinearReader, Paradigm, RocketNautics, cnpc-gecko-addon,
  egg, Sable — so their symbols are recorded as **unverified**, not assumed. What could be
  established is that none of them grants authority on failure: each either disables its own
  isolated integration or reports an explicit error.
- The **531** headline is misleading and should not be quoted alone: 382 of it is mixin
  declarations that Mixin resolves at classload, where a wrong target is a startup failure rather
  than a silent one. The set that can rot unnoticed is the 136 runtime lookups.

Nothing in that document has runtime proof. It is `javap` against hashed artifacts plus source
inspection of failure paths — which is the strongest evidence available without a running game, and
explicitly not the same thing.

## Next — Phase C: finish what is half-built

| System | Files | State |
|---|---:|---|
| Roles | 16 | behaviour done; the economy/trade side is not built |
| Scripting | 15 | partial |
| Dialog | 13 | engine, bubbles, multiplayer, and per-NPC authoring all done |
| Quests | 6 | objectives, datapack loading and rewards done; a quest **log** is still missing |
| Factions | 8 | done — registry, hostility, both screens, quest rewards, and writable in game |
| World store | 11 | store, schema, versioning, client index, factions + dialogs live |
| Jobs, Scenes, Banks, Transport, Pather | **0** | store folders exist; nothing reads them |

1. **Roles** — per-role *behaviour* landed (see above). What is left is the **economy** side: the
   Trader screen is still 18 disabled slots because there is no trade inventory, and the `ROLE`
   dialogue option stays inert until there is something for it to open. The option screen now says
   so in place rather than hiding the type.
2. **A quest log.** Still exactly one active quest: `XenoPlayerData` holds a single
   `questId`/`questProgress`/`questTarget`, and starting a second refuses with "Already on quest".
   Now that a pack can define any number, one-at-a-time is the binding limit. This changes the
   player-data shape, the save/load, and every handler that reads "the" active quest.
3. **The importer.** `/xenonpcimport all` now previews factions, shared dialogues, supported quest
   definitions, and currently loaded placed NPCs; `/xenonpcimport all confirm` writes them and backs
   up each converted entity first. The file importer reuses the existing CustomNPC structured-data
   and clone converters without requiring either source mod at runtime. Unsupported quest kinds,
   unsafe command actions, and missing links are listed in the report. Placed NPCs in unloaded chunks
   are not scanned; run the command after loading the areas that need conversion. Banks, transport,
   spawns, recipes, linked entries, and full-world offline entity conversion remain future work.
4. **Jobs, Scenes, Banks, Transport, Pather** — still zero code. The store now has folders for
   several of them; nothing reads those folders yet, and the screens say so in place.

**Factions** are wired end to end: datapack registry, client sync, NPC assignment, hostility, and
quest standing rewards through `QuestReward.FactionGrant`.

---

## Constraints

- **No invented APIs.** Read repo source or the DMZ jar first. Verified this round:
  `QuestNPCEntity.setNpcId`, the `QuestNpc*` NBT keys, the 13 quest-giver ids, `Level.isNight()`
  (via DMZ's own decompiled use of it), and `MobBattlePowerHelper.isDmzManaged` matching only the
  `dragonminez` namespace.
- **No invented art.** Generated is fine and is how this whole atlas works — but say so, the way
  `render_mark` and `render_speech_bubble` both do.
- **Never stretch atlas sprites** — generate the size. See `docs/atlas-ui-doco.md`.
- **No dead controls.** The whitelist key is the *last* step, after the consumer exists.
- **Untracked work is unrecoverable.** `client/ui/`, `client/npc/`, `npc/` and the atlas are all
  untracked, and Codex's entire editor rework lives in there. Never `git add -A`,
  `git reset --hard`, or blanket checkout.
- **Packet order is append-only**, with a protocol note. Protocol is **97** (2026-09-27).

## Verification

```
gradlew.bat compileJava -PofflineMcMeta
gradlew.bat test -PofflineMcMeta
gradlew.bat build jarJar serverJar -PofflineMcMeta
```

`META-INF/jarjar/` holds **ModernUI only** - that one is deliberately bundled (`build.gradle:352`).
Anything else appearing there, DragonMineZ above all, is the failure to watch for.

In game — **none of this round has been run in a game yet**:

1. `/xenostructure summon quest bulma` → right-clicking her opens DMZ's quest dialogue, not our
   editor.
2. `/xenostructure summon master beerus`, `summon saga frieza_final` → both spawn; completion lists
   no `ki_blast`.
3. Bare `/xenostructure summon goku_house` still behaves exactly as before.
4. Set a night texture, wait for dusk → the skin swaps, and swaps back at dawn.
5. Link two NPCs by UUID, edit one, save → the other takes the same change, and the chat says so.
6. Set a mark → it floats above the head in the chosen colour.
7. Write a faction JSON under `data/<pack>/npcs/factions/`, run `/reload` **without rejoining** →
   it appears in Global > Factions, and Advanced > Factions can cycle an NPC onto it.
8. Give two NPCs mutually hostile factions → they fight each other on sight.
9. **On a dedicated server**, talk to an NPC whose role names a dialogue → the bubbles open. This
   is the case that never worked; any single-player check of it passed by accident.
10. Write a dialogue on an NPC: Advanced > Dialogs > Add line, type a line, add an answer, point a
    second line at it. The list shows `*` on the start, and `Unfinished` names anything missing.
11. Save, reopen the editor → the lines come back in the order they were written.
12. Talk to that NPC → it says the authored line, not its role's.
13. Delete the start line → the next one takes over rather than the dialogue going dead.
14. `/xenoquest list` → the two shipped pack quests appear with their objective and target, and a
    deliberately broken quest JSON is named under "Failed to load".
15. `/xenoquest start wolf_trouble`, kill a wolf → progress; kill a zombie → no progress. That
    second half is the old `default` branch's bug.
16. `/xenoquest start pay_respects`, talk to the same master three times → progress stays at 1/3.
17. **Place an NPC, ride 200 blocks away, come back** → it is still there. Today it is not.
18. Kill an NPC, restart the server, wait → it respawns. Today it does not.
19. Open a world saved before this round → the NPC keeps its name, stats and appearance.
20. Global ▸ Factions ▸ type an id ▸ Add → a file appears under `<world>/XenoNpcs/factions/`, every
    client's list updates, and `/reload` does not erase it.
21. Delete that file by hand and restart → the faction is gone and any datapack default is back.
22. Hand-edit a store file to `Schema: 999` → it is refused with a log line, the file is untouched,
    and the category refuses writes for that session.
23. Global ▸ Dialogs ▸ type an id ▸ Add → it lists, and a file appears under
    `<world>/XenoNpcs/dialogs/npc/`. Open it, Remove → it is gone from both.
24. With two operators connected, one adds a faction → it appears on the other's screen without a
    rejoin.
25. Quit to the menu and open a **different** world → the editor's faction and dialog lists are
    empty, not the previous world's.
26. **Two operators, one faction.** Both open Global ▸ Factions, both edit `guards`, both save.
    The second is refused with "reopen the screen" and the first operator's edit survives. Before
    the revision check the second silently won and both screens said success.
27. **Talk to an NPC** → the mouse stays free, you can look around, and the crosshair picks a
    bubble on either click. Sneak leaves.
28. Back away to ~8 m mid-conversation → option 2 is still selectable. Before the fix its box was
    swallowed by option 1's.
29. Click while the crosshair is on no bubble → nothing happens, and the NPC takes no damage.
30. Set `dialogueCrosshair` false in the client config → the old cursor screen comes back.
31. Earlier rounds' checks in `docs/handoff-2026-09-21-xeno-npc.md` still stand, and the combat,
   battle-power and speech-bubble work remains unconfirmed in a running game.
