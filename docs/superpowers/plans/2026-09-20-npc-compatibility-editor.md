# NPC Compatibility and Native DMZ Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore CustomNPCs/MyNPCs wand and DMZ hair compatibility, and build an independent DMZ-styled Xeno NPC editor organized with CustomNPCs/MyNPCs-style menu entries and nested submenus.

**Architecture:** Preserve third-party wand ownership and use narrow optional adapters for hair state/rendering. Build the native editor as its own server-authoritative screen and packet-backed data model, with a category tab bar and nested menu entries for General, Appearance, Combat, Orders/Brain, Dialogue/Quests, Inventory/Equipment, and Settings. Unsupported fields are visibly read-only rather than falsely persisted.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.248, DragonMineZ 2.1.3, NeoForge networking, Mixins, existing Minecraft `Screen`/`Button` widgets, JUnit/Gradle tests.

**Spec:** `docs/superpowers/specs/2026-09-10-npc-compatibility-editor-design.md`

## Global Constraints

- Preserve the dirty working tree; never reset, blanket-checkout, or stage unrelated paths.
- Do not invent CustomNPCs, MyNPCs, DragonMineZ, NeoForge APIs, packet identifiers, entity IDs, or resource paths.
- Keep optional integrations fail-closed and side-safe.
- Preserve `src/main/java/net/bullettrain/xenopixelsmod/api/**` compatibility.
- Keep the existing main network channel and packet ordering stable; append only verified packets when required.
- Server-authorize entity edits, payload bounds, distance/ownership rules, and revisions.
- Do not claim runtime behavior until a fresh client/server observation confirms it.

## Review Focus

- Invalid or oversized hair codes/colors/styles must preserve host/default appearance; test sanitization and fallback in the hair/profile test owner.
- A missing optional host mod or changed class descriptor must not prevent base startup; test adapter gating and compile-side isolation in the compatibility task.
- Third-party wand use must not be cancelled or rerouted; test that Xeno interception is limited to `XenoNpcEntity`.
- Nested editor menu actions must retain the selected category and submenu across redraws; test menu state transitions in the editor task.
- Stale or unauthorized native saves must not partially apply; test revision and permission rejection in the packet task.

---

### Task 1: Audit and lock verified compatibility targets

**Files:**
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcHairBridge.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/mixin/compat/customnpcs/RenderCustomNpcHairMixin.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/mixin/compat/mynpcs/RenderCustomNpcHairMixin.java`
- Modify: relevant optional mixin configuration under `src/main/resources/`
- Test: existing focused NPC compatibility test locations discovered during audit

**Interfaces:**
- Consumes: current `NpcCombatProfile`, `NpcAppearancePacket`, `NpcHairModelData`, and verified host renderer descriptors.
- Produces: a documented, source-backed target list and failing regression tests for hair fallback and adapter gating.

- [ ] **Step 1: Inspect exact host classes and current mixin configuration**

Run source/reference searches and `javap` against the pinned jars as needed. Record only exact constructor/render descriptors and optional mod IDs that exist in the repository or jars.

- [ ] **Step 2: Add regression tests for invalid hair fallback and optional absence**

Pin that invalid code/color/style values do not overwrite the profile’s valid host-preserving values, and that optional integration checks return safely when the adapter mod is absent.

- [ ] **Step 3: Implement the narrowest adapter corrections**

Keep renderer cancellation conditional on a successful full DMZ render. Keep host rendering active when hair state is absent, malformed, or unavailable. Do not change host wand classes.

- [ ] **Step 4: Run focused compatibility tests**

Run the repository’s smallest NPC compatibility test selector and confirm the new regressions pass.

---

### Task 2: Restore shared hair synchronization and host mirrors

**Files:**
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcHairBridge.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcCombatProfile.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/network/NpcAppearancePacket.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/mixin/compat/cnpcgecko/CustomModelDataMixin.java`
- Test: hair/profile/network-focused tests in the existing NPC test source set

**Interfaces:**
- Consumes: verified targets from Task 1.
- Produces: bounded hair values that survive server save, appearance sync, client cache, and render-layer lookup.

- [ ] **Step 1: Write tests for round-trip hair state**

Cover enabled state, custom code, preset style, color normalization, empty fallback, and oversized input rejection. Assert that a rejected value leaves the previous valid value unchanged.

- [ ] **Step 2: Trace and correct packet serialization**

Use the current packet field order and append no fields unless required. Enforce the existing payload limits on both decode and server application.

- [ ] **Step 3: Correct mirror application without destructive host overwrite**

Apply only bounded Xeno mirror fields to Gecko model data. Preserve host/default values when the Xeno profile is empty or invalid.

- [ ] **Step 4: Run focused hair and packet tests**

Run the focused selectors and inspect test output for serialization or side-safety failures.

---

### Task 3: Restore third-party wand creation without interception

**Files:**
- Modify: only confirmed conflicting optional mixins under `src/main/java/net/bullettrain/xenopixelsmod/mixin/compat/customnpcs/` and `mixin/compat/mynpcs/`
- Modify: matching optional mixin JSON configuration if a target must be removed or narrowed
- Test: compatibility/bootstrap tests covering absent and present optional mods

**Interfaces:**
- Consumes: optional target evidence from Task 1 and shared hair behavior from Task 2.
- Produces: unchanged host wand creation semantics; Xeno wand interception limited to native Xeno NPC targets and additive import.

- [ ] **Step 1: Reproduce the host-wand failure from current source and descriptors**

Identify the exact Xeno hook that cancels, replaces, or crashes the host creation path. Do not edit until the target and failure are confirmed.

- [ ] **Step 2: Add a regression test for target scoping**

Assert that `XenoNpcWandItem.interactLivingEntity` returns `PASS` for non-Xeno entities without a verified import action and that native Xeno targets alone open the editor.

- [ ] **Step 3: Narrow or remove only the conflicting hook**

Preserve host mixins that add non-invasive DMZ tabs. Never cancel host wand events, replace host screens, or add a hard dependency.

- [ ] **Step 4: Run compile and compatibility tests**

Run the targeted compile/test command with offline metadata and confirm both optional-mod configurations load safely.

---

### Task 4: Add the native editor menu model and nested entries

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/XenoNpcEditorMenu.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/XenoNpcEditorSection.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/XenoNpcEditorEntry.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/client/npc/XenoNpcEditorMenuTest.java` or the repository’s established equivalent

**Interfaces:**
- Consumes: native editor data tags and verified native field names.
- Produces: deterministic sections, menu entries, submenu entries, selection state, and read-only flags for `XenoNpcEditorScreen`.

- [ ] **Step 1: Write menu-state tests**

Test the exact hierarchy:

```text
General
  Identity
  Ownership
  Display
Appearance
  Hair
  Skin and Colors
  Forms and Aura
Combat
  Stats
  Targeting
  Equipment
Orders / Brain
  Movement Orders
  Combat Orders
  Schedule
  Brain v5 Budget
Dialogue / Quests
  Dialogue
  Saga References
  Quest References
Inventory / Equipment
  Inventory
  Drops
  Equipment
Settings
  Interaction
  Permissions
  Persistence
  Import Provenance
```

Test that selecting a section resets the submenu only when appropriate, and unavailable entries remain read-only.

- [ ] **Step 2: Implement immutable menu metadata and selection state**

Use explicit IDs, labels, parent IDs, and capability/read-only flags. Do not use host-mod classes or reflection in this client-only model.

- [ ] **Step 3: Run menu tests**

Verify stable ordering and nested selection transitions.

---

### Task 5: Build the independent DMZ-styled native editor shell

**Files:**
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/XenoNpcEditorScreen.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/network/packet/OpenXenoNpcEditorPacket.java` only if additional bounded data is required
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/client/ClientScreens.java` only if screen construction requires it

**Interfaces:**
- Consumes: `XenoNpcEditorMenu` hierarchy and current native `CompoundTag` snapshot.
- Produces: DMZ-styled independent editor with section tabs, nested submenu entries, local draft state, and explicit unavailable states.

- [ ] **Step 1: Add screen interaction tests or pure helper tests**

Cover section selection, submenu selection, keyboard/mouse-safe bounds, and draft changes not mutating the original snapshot before save.

- [ ] **Step 2: Implement the shell**

Render the DragonMineZ plate when available, retain a safe fallback panel, draw category tabs, nested left navigation, title/breadcrumb, content area, and Save/Close controls. Use the exact menu IDs from Task 4.

- [ ] **Step 3: Implement General and Appearance/Hair pages**

Make identity, faction, bounded hair code/style/color, and enabled state editable locally. Preview changes client-side only until Save.

- [ ] **Step 4: Implement read-only placeholders for unverified pages**

Show the complete menu hierarchy and explain when a page is unavailable. Do not write unsupported values into the native profile.

- [ ] **Step 5: Run client compile and menu/screen tests**

Confirm no host-mod classes are loaded by the native screen and no horizontal/visual state path depends on optional mods.

---

### Task 6: Add verified Orders/Brain native editing

**Files:**
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/client/npc/XenoNpcEditorScreen.java`
- Modify: native Xeno NPC data and packet files identified by exact source inspection
- Test: native brain/order serialization and authorization tests

**Interfaces:**
- Consumes: existing native Brain v5 data schema and revisioned save path.
- Produces: bounded Movement Orders, Combat Orders, Schedule, and Brain Budget controls only for verified native fields.

- [ ] **Step 1: Write serialization and rejection tests**

Test valid order values, invalid enum/number bounds, oversized schedule strings, stale revision, and unauthorized player rejection.

- [ ] **Step 2: Implement bounded draft fields and server validation**

Use existing native packet patterns. Reject invalid fields before mutation and preserve all prior values on failure.

- [ ] **Step 3: Connect the Orders/Brain submenu**

Render controls for verified fields and retain read-only treatment for any field not backed by native data.

- [ ] **Step 4: Run focused native editor tests**

Run the native NPC, brain, and packet selectors.

---

### Task 7: Validate, review, and document runtime evidence

**Files:**
- Modify: `plan.md` with current progress and exact validation evidence
- Modify: `ai/handoff-template.md`-based session handoff artifact if required
- Modify: directly related docs only when behavior/contracts changed

**Interfaces:**
- Consumes: completed Tasks 1–6.
- Produces: verified build/runtime report and explicit manual-pending matrix.

- [ ] **Step 1: Run focused tests and compile**

Run the smallest changed-area test selectors, then `./gradlew.bat compileJava -PofflineMcMeta`.

- [ ] **Step 2: Run repository validation**

Run `./gradlew.bat test -PofflineMcMeta`, `./gradlew.bat build jarJar serverJar -PofflineMcMeta`, and `./gradlew.bat buildApiExampleAddon -PofflineMcMeta` as applicable to changed packets/API boundaries.

- [ ] **Step 3: Inspect packaged output**

Confirm the server jar has zero entries under `META-INF/jarjar/` and inspect only changed artifacts.

- [ ] **Step 4: Perform fresh runtime checks**

Start a fresh client/server process and inspect `run/logs/latest.log` for startup and editor packet evidence. Manually check both third-party wands, DMZ hair rendering, native editor opening, General/Appearance save, Orders/Brain behavior, and absent optional mods.

- [ ] **Step 5: Request final code review**

Use the repository code-review workflow to inspect only the focused changes and resolve findings before completion.

- [ ] **Step 6: Update plan and handoff**

Record branch, commit hash, dirty paths, commands, artifact hashes/sizes, verified observations, unverified items, and safe next steps. Do not commit or push unless explicitly requested.
