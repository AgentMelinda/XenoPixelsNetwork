# Tournament / Roles / Unified Maker Studio (Phase 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Chapter D: player angel roles, 24/7 Budokai queue+KotH (≤12) with admin results, plus one **Unified Maker Studio** — exact DMZ-layout clones of the Race Character Creator, Form Maker (with parts sub-screen), and advanced Hair Editor (per-segment + glow selection), green atlases from `tools/atlas-panels/`, **new race ids when registration evidence is READY**, full-body **true local-player DMZ model** preview that refreshes on every change, and optional Motif density (PR-B3).

**Architecture:** Track A (Tasks 1–6) unchanged: roles → tournament → awards → bracket UI → angel trainer. Track U (Tasks 7–12) Unified Maker Studio: race-pack evidence/IO → shared true-player preview + glow → atlas PanelSpecs → Race / Form / Hair exact-layout screens under `/xenomaker`. Optional Track M (Task 13) Motif density. Handoff = Task 14. Never overload `XenoNpcRole` or `ModNetwork` protocol `"101"`.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.248, DragonMineZ 2.1.3, DMZ SimpleChannel, SavedData, JUnit 5, Python atlas generator (`tools/atlas-panels/`), hair lab submodule (`tools/dmz-hair-builder-site/`), Effekseer for optional Motif. Owner reference screenshots 2026-10-03: Character Creator, Form Maker (+parts), Hair Editor (glow).

**Spec:** `docs/superpowers/specs/2026-10-03-hd-aura-tournament-makers-design.md` (KD7–KD9, KD14–KD20, D0-a…g, PR-D1…D5, PR-D6* Unified Maker, PR-D7 Hair, optional PR-B3)

**Phase 1 prerequisite:** `docs/superpowers/plans/2026-10-03-hd-aura-fx-pipeline-phase1.md` landed (`aa45b89`). Motif density is Task 13 only.

**Rewrite note (2026-10-03 r3):** Owner overturned form-groups-only makers. Track U replaces old Tasks 7–11. Existing `RaceFormGroupMakerScreen` / text-summary `HairMakerScreen` are **superseded** by the three exact-layout screens; keep `PreviewDebounce` and hair document/apply services when they still fit.

## Global Constraints

- NeoForge-only; mod id `xenopixelsmod`; never invent DMZ/Xeno APIs — verify in source / decompiled / `javap` first.
- Dirty tree: never `git add -A`; stage reviewed paths only; commit **only if the owner explicitly asks**.
- `tools/dmz-hair-builder-site/` is a **git submodule**. Prefer golden fixtures under `src/test/resources/hair/`.
- D0 finals (KD14): queue+KotH ≤12; angels cosmetic+trainer only; gods_forms only; FleetChannel SavedData; channel `tournament` protocol `"1"`; static arenas; admin results until D2a.
- Player angel roles ≠ `XenoNpcRole` (KD8). Tournament is Xeno-owned (KD7).
- **Unified Maker (KD15 r3 / KD20):** `/xenomaker` opens exact DMZ-layout clones of (1) Race Character Creator, (2) Form Maker + parts sub-screen, (3) Hair Editor. Atlases from `tools/atlas-panels/` `palette="green"`. **New race ids when Task 7 evidence READY**; if BLOCKED, Create Race stays disabled with cited blocker.
- **Preview (KD20):** full-body visualizer is the **true local-player DMZ model** (in-world / character-flow stack). Do not silently ship text-only or dummy preview — if no verified player-model path, STOP and report.
- **Selection glow:** active race card, form row, appearance category, or hair segment gets a **green glow/outline** like the hair-editor screenshots.
- Hair: per-segment edit; connected = parenting label; no new `connected` boolean unless KD16 vNext. Apply ≠ `CustomizationManager` `hair_style_*`.
- Angel unlocks reuse `TrainerPurchasePacket.purchase` (KD17). No packets on `ModNetwork` `"101"`.
- Runtime claims need fresh process + log. Tournament config defaults **off**.
- Shared wiring may be dirty — whole-file stage only with owner approval.

## Review Focus

- Client “I won” ignored; only admin `/xenotourney result`. **Task 3.**
- Non-angel gods purchase rejected; angel → `xenopixels_gods_forms` only. **Task 6.**
- Tournament off → join/awards no-op. **Tasks 3–4.**
- New race create only after Task 7 READY citation. **Task 7.**
- Every maker change refreshes true player-model preview ≤50 ms. **Task 8.**
- Screens match owner screenshots; atlas chrome; never stretch borders. **Tasks 9–12.**
- Selected part/segment shows green glow. **Tasks 8, 10–12.**
- Hair Apply via cited path or disabled — never `hair_style_*` as codec. **Task 12.**

## File map (Phase 2)

| File / package | Responsibility |
| --- | --- |
| `…/features/playerrole/*` | Roles SavedData / sync / angel trainer (Track A) |
| `…/features/tournament/*` | Queue+KotH, admin results, network, commands |
| `…/config/XenoServerConfig.java` | `tournamentEnabled`, arenas |
| `…/command/XenoPermissions.java` | `ROLE_*`, `TOURNEY_*`, `MAKER_*` |
| `…/dmz/race/RacePackService.java` | Create/list/validate race packs when evidence READY |
| `…/client/maker/MakerPreviewController.java` | True local-player DMZ full-body preview + glow target |
| `…/client/maker/PreviewDebounce.java` | ≤50 ms debounce (reuse) |
| `…/client/maker/XenoMakerHubScreen.java` | `/xenomaker` → Race / Forms / Hair |
| `…/client/maker/RaceCharacterMakerScreen.java` | Exact Character Creator (Image 1) |
| `…/client/maker/FormMakerScreen.java` | Exact Form Maker (Images 2–4) + parts sub |
| `…/client/maker/HairMakerScreen.java` | Exact Hair Editor (Images 5–6); supersedes text preview |
| `…/hair/*` | Document, strand model, apply service, golden vectors |
| `tools/atlas-panels/xeno_extra_specs.py` | Bracket + maker PanelSpecs (`palette="green"`) |
| `tools/effekseer/efkgen/effects/aura_motifs.py` | Optional PR-B3 |
| `docs/superpowers/evidence/2026-10-03-*.md` | D2a; D5; D6 race reg; D6 preview; D7 hair |
| `src/test/resources/hair/` | Golden vectors |

**Superseded:** text-summary `RaceFormGroupMakerScreen` / old text-only hair preview depth. Retarget open paths to the new screens.

**Execution tracks:** A = 1→6; U = 7→12; M = 13 optional; handoff = 14.

**Shared wiring touch list (ask owner before whole-file stage):**

- `src/main/java/net/bullettrain/xenopixelsmod/XenoPixelsMod.java`
- `src/main/java/net/bullettrain/xenopixelsmod/command/XenoPermissions.java`
- `src/main/java/net/bullettrain/xenopixelsmod/config/XenoServerConfig.java`
- `src/main/java/net/bullettrain/xenopixelsmod/network/form/FormEditorNetwork.java` (angel offering only)
- `src/main/java/net/bullettrain/xenopixelsmod/client/ClientScreens.java`
- `src/main/java/net/bullettrain/xenopixelsmod/client/ClientConnectionState.java`
- `src/main/java/net/bullettrain/xenopixelsmod/dmz/DmzContentBootstrap.java` (only if Task 7 READY needs install hooks)
- `CHANGELOG.md`

---
### Task 1: PR-D1 — PlayerRole foundation (SavedData + sync + `/xenorole`)

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/playerrole/PlayerRoleId.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/playerrole/PlayerRoleSavedData.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/playerrole/PlayerRoleService.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/playerrole/PlayerRoleNetwork.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/command/PlayerRoleCommands.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/command/XenoPermissions.java` (add `ROLE_GET`, `ROLE_SET`)
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/XenoPixelsMod.java` (register network + commands)
- Create: `src/test/java/net/bullettrain/xenopixelsmod/features/playerrole/PlayerRoleSavedDataTest.java`
- Test: `PlayerRoleSavedDataTest`

**Interfaces:**
- Consumes: `FleetChannelSavedData.get` pattern (`server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(...)`); `FormEditorNetwork` SimpleChannel builder style; `XenoPermissions.op(...)`
- Produces:

```java
public enum PlayerRoleId { NONE, ANGEL;
  public static PlayerRoleId parse(String raw); // unknown → NONE
}
public final class PlayerRoleSavedData extends SavedData {
  public static final String FILE_NAME = "xenopixels_player_roles";
  public static PlayerRoleSavedData get(MinecraftServer server);
  public PlayerRoleId roleOf(UUID id);
  public void setRole(UUID id, PlayerRoleId role); // marks dirty; NONE may remove entry
}
public final class PlayerRoleService {
  public static PlayerRoleId get(ServerPlayer player);
  public static void grant(ServerPlayer player, PlayerRoleId role, String source); // source logged
  public static void revoke(ServerPlayer player, String source);
}
// Network: S2C RoleSyncPacket(UUID, String roleId) on join + after grant/revoke
```

- [ ] **Step 1: Write failing persistence test**

```java
@Test
void roundTripPersistsAngelAndUnknownBecomesNone() {
    PlayerRoleSavedData data = new PlayerRoleSavedData();
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
    data.setRole(id, PlayerRoleId.ANGEL);
    CompoundTag tag = data.save(new CompoundTag(), /* HolderLookup.Provider per SavedData API */);
    PlayerRoleSavedData loaded = PlayerRoleSavedData.load(tag, /* registries */);
    assertEquals(PlayerRoleId.ANGEL, loaded.roleOf(id));
    assertEquals(PlayerRoleId.NONE, PlayerRoleId.parse("not-a-role"));
    assertEquals(PlayerRoleId.NONE, loaded.roleOf(UUID.randomUUID()));
}
```

If `save`/`load` need `HolderLookup.Provider`, mirror `FleetChannelSavedData` signatures exactly — do not invent a different SavedData API.

- [ ] **Step 2: Run test — expect FAIL (missing class)**

```powershell
./gradlew.bat test --offline -PofflineMcMeta --tests net.bullettrain.xenopixelsmod.features.playerrole.PlayerRoleSavedDataTest
```

- [ ] **Step 3: Implement PlayerRoleId + PlayerRoleSavedData + PlayerRoleService**

```java
public static PlayerRoleSavedData get(MinecraftServer server) {
    return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(
            new Factory<>(PlayerRoleSavedData::new, PlayerRoleSavedData::load), FILE_NAME);
}
```

NBT shape: list of `{UUID, Role}` compounds under key `Roles`. Empty/missing → NONE. `setRole` calls `setDirty()`.

`PlayerRoleService.grant`: set role, log `source` (e.g. `"admin:/xenorole"`, `"tournament:<matchId>"`), send sync packet. **No combat stat modifiers.**

- [ ] **Step 4: Implement PlayerRoleNetwork**

```java
private static final String PROTOCOL = "1";
private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
        .named(ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "player_roles"))
        .networkProtocolVersion(() -> PROTOCOL)
        .clientAcceptedVersions(PROTOCOL::equals)
        .serverAcceptedVersions(PROTOCOL::equals)
        .simpleChannel();
// message id 0: RoleSyncPacket PLAY_TO_CLIENT
```

Do **not** put these packets on `ModNetwork` `"101"`.

- [ ] **Step 5: `/xenorole` + permissions**

```text
/xenorole get <player>
/xenorole set <player> none|angel
/xenorole clear <player>
```

Gate with `XenoPermissions.ROLE_GET`, `ROLE_SET` (op default), registered like existing nodes.

- [ ] **Step 6: Re-run PlayerRoleSavedDataTest — expect PASS**

- [ ] **Step 7: CHANGELOG Unreleased** — player roles SavedData + `/xenorole`; cosmetic only; not verified in game.

- [ ] **Step 8: Commit only if owner asks** (explicit paths only)

---

### Task 2: PR-D2a — Tournament KO evidence spike (docs only; no auto-KO)

**Files:**
- Create: `docs/superpowers/evidence/2026-10-03-tournament-ko-events.md`
- Test: none (evidence gate)

**Interfaces:**
- Consumes: NeoForge/vanilla event APIs verified in dependency jars or `tools/generated`
- Produces: written list of **verified** death/damage hooks usable later; until then D2 stays admin-only

- [ ] **Step 1: Inventory candidate events in source**

Search NeoForge event bus types and any DMZ combat death helpers already used by XenoPixels (e.g. existing `@SubscribeEvent` on `LivingDeathEvent`). Record for each:

| Event / method | Jar / class verified | Can identify killer? | Safe for awards? |
| --- | --- | --- | --- |

Minimum to document: `net.neoforged.neoforge.event.entity.living.LivingDeathEvent` (exact 1.21.1 name from jar), and whether PvP killer is `getSource().getEntity()`.

- [ ] **Step 2: Write the evidence appendix**

Must state explicitly:

```markdown
## Verdict
Automatic KO for tournament awards: BLOCKED | READY

If BLOCKED: keep `/xenotourney result` only.
If READY: list the exact subscribe target + filter rules (same-dimension, both entrants, match active).
```

Even if READY, **do not implement auto-KO in this task** — Task 3 ships admin results only; auto-KO is a follow-on after owner ask.

Do **not** invent DMZ tournament KO APIs.

- [ ] **Step 3: Commit only if owner asks**

---

### Task 3: PR-D2 — Tournament core (queue + KotH, static arenas, admin results)

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/tournament/TournamentMode.java` (`QUEUE_KOTH`, `EVENT_ELIM`)
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/tournament/EntrantStatus.java` (`QUEUED`, `ACTIVE`, `ELIMINATED`)
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/tournament/MatchResult.java` record
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/tournament/TournamentSavedData.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/tournament/TournamentService.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/tournament/TournamentNetwork.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/tournament/TournamentCommands.java`
- Modify: `XenoServerConfig.java` — `tournamentEnabled = false`; arena fields; bump `CURRENT_CONFIG_VERSION` if new keys (follow existing migration style; config version ≠ network protocol)
- Modify: `XenoPermissions.java` — `TOURNEY_JOIN`, `TOURNEY_LEAVE`, `TOURNEY_STATUS`, `TOURNEY_ARENAS`, `TOURNEY_RESULT`
- Modify: `XenoPixelsMod.java` — register
- Create: `src/test/java/net/bullettrain/xenopixelsmod/features/tournament/TournamentServiceTest.java`
- Test: `TournamentServiceTest`

**Interfaces:**
- Consumes: FleetChannel SavedData pattern; does **not** award angels yet (Task 4)
- Produces:

```java
public final class TournamentService {
  public static final int MAX_QUEUED = 12;
  /** @return false if disabled, full, or already queued */
  public static boolean enqueue(ServerPlayer player);
  public static boolean leaveQueue(ServerPlayer player);
  /** King challenges next queued challenger when idle; uses static arena index */
  public static Optional<String> tryStartChallenge(MinecraftServer server);
  /** Admin-only v1 — never trust a client “I won” packet */
  public static void reportResult(MinecraftServer server, String matchId, UUID winner, ServerPlayer reporter);
  public static List<UUID> queuedSnapshot(MinecraftServer server);
}
```

Config field names (JSON keys must match load/save like other booleans/strings in `XenoServerConfig`):

```java
public static boolean tournamentEnabled = false;
public static String tournamentArenaDimension = "minecraft:overworld";
public static String tournamentArenaPositions = "0,64,0;8,64,0";
```

- [ ] **Step 1: Write failing queue tests**

```java
@Test
void enqueueCapsAtTwelveAndDisabledFlagBlocksJoin() {
    TournamentSavedData data = new TournamentSavedData();
    assertTrue(TournamentService.enqueueInto(data, uuid(1)));
    for (int i = 2; i <= 12; i++) assertTrue(TournamentService.enqueueInto(data, uuid(i)));
    assertFalse(TournamentService.enqueueInto(data, uuid(13)));
    assertEquals(12, data.queuedCount());
}

@Test
void reportResultRecordsWinnerWithoutGrantingRole() {
    TournamentSavedData data = new TournamentSavedData();
    String matchId = TournamentService.startMatchForTest(data, uuid(1), uuid(2), 0);
    TournamentService.reportResultInto(data, matchId, uuid(1), "admin:test");
    assertEquals(uuid(1), data.match(matchId).winner());
    // Role grant is Task 4 — assert no PlayerRole coupling here
}
```

Expose package-visible `enqueueInto` / `reportResultInto` for unit tests if MinecraftServer is unavailable — do not invent a fake DMZ API.

- [ ] **Step 2: Run test — expect FAIL**

```powershell
./gradlew.bat test --offline -PofflineMcMeta --tests net.bullettrain.xenopixelsmod.features.tournament.TournamentServiceTest
```

- [ ] **Step 3: Implement SavedData + Service + Network + Commands**

`/xenotourney` surface:

```text
/xenotourney join
/xenotourney leave
/xenotourney status
/xenotourney result <matchId> <winnerPlayer>
/xenotourney arenas
```

`TournamentNetwork`: `ResourceLocation(MOD_ID, "tournament")`, protocol `"1"`. Client may receive queue snapshot S2C for UI (Task 5). **Do not add a client win C2S packet.**

When `!XenoServerConfig.tournamentEnabled`, `enqueue` returns false with a system message.

- [ ] **Step 4: Run TournamentServiceTest — expect PASS**

- [ ] **Step 5: CHANGELOG** — tournament queue+KotH admin results; flag default off; auto-KO not shipped; not verified in game.

- [ ] **Step 6: Commit only if owner asks**

---

### Task 4: PR-D3 — Tournament → Angel awards

**Files:**
- Modify: `TournamentService.reportResult…` to call `PlayerRoleService.grant(winner, ANGEL, "tournament:"+matchId)` when tournament is enabled and match is valid
- Create: `src/test/java/net/bullettrain/xenopixelsmod/features/tournament/TournamentAwardTest.java`
- Test: `TournamentAwardTest`

**Interfaces:**
- Consumes: `PlayerRoleService.grant`, `TournamentService.reportResult`
- Produces: server-committed results → angel role; no client path; no combat mods

- [ ] **Step 1: Failing test**

```java
@Test
void adminResultGrantsAngelWithTournamentSource() {
    UUID winner = uuid(1);
    // Arrange in-memory role + tournament stores the same way Service uses in tests
    // after reportResult…
    assertEquals(PlayerRoleId.ANGEL, roles.roleOf(winner));
    // source string must start with "tournament:"
}

@Test
void disabledTournamentDoesNotGrantAngel() {
    // tournamentEnabled=false path: reportResult either rejects or records without grant
    assertEquals(PlayerRoleId.NONE, roles.roleOf(uuid(1)));
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Wire grant only inside server `reportResult` after match validity checks (both entrants, match open, winner is an entrant)**

If Task 2 appendix is READY and owner later asks for auto-KO, that is a **follow-on PR** — do not enable it in this task.

CHANGELOG wording: angel grant from **admin-reported** tournament results (not “auto angel on KO”).

- [ ] **Step 4: Run tests — PASS**

- [ ] **Step 5: Commit only if owner asks**

---

### Task 5: PR-D4 — Tournament green atlas UI

**Files:**
- Modify: `tools/atlas-panels/xeno_extra_specs.py` — add bracket row / queue panel specs if missing sizes (`palette="green"`)
- Run atlas generator for new panels only (path-scoped)
- Create: `src/main/java/net/bullettrain/xenopixelsmod/client/tournament/TournamentQueueScreen.java`
- Wire open from client command / status packet handler via `ClientScreens`
- Create: atlas sprite existence test if the repo already tests atlas panels that way
- Test: focused compile + any existing atlas test pattern

**Interfaces:**
- Consumes: S2C queue/match snapshot from `TournamentNetwork`; `XenoAtlasSprites` / `Atlas*` widgets per `docs/atlas-ui-doco.md`
- Produces: read-only bracket/queue screen; **never stretch** baked borders

- [ ] **Step 1: Check PanelSpecs**

```powershell
python tools/atlas-panels/dmz_atlas_generator.py --help
# Inspect xeno_extra_specs.py / panels_manifest.json for green queue/bracket sizes
```

Add PanelSpecs only when a needed size is missing. Generate with `palette="green"`.

- [ ] **Step 2: Implement screen that renders queued names + active match from last S2C snapshot**

Empty queue shows a clear empty state (D0-a UX).

- [ ] **Step 3: Client open path** — e.g. `/xenotourney status` also opens UI for the sender when run client-side, **or** a dedicated client command — document the chosen path in CHANGELOG. Do not invent a second network protocol.

- [ ] **Step 4: Build client jar if UI assets changed; label not verified in game**

- [ ] **Step 5: Commit only if owner asks**

---

### Task 6: PR-D5 — Angel as gods master (evidence appendix first, then wire)

**Files:**
- Create: `docs/superpowers/evidence/2026-10-03-angel-trainer-offerings.md`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/playerrole/AngelTrainerGate.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/features/playerrole/AngelTrainerEvents.java`
- Modify (only after appendix READY): `FormEditorNetwork.TrainerPurchasePacket.resolveTrainerOffering` so angel players resolve through `AngelTrainerGate` (keep debit/apply in existing `purchase`)
- Create: `src/test/java/net/bullettrain/xenopixelsmod/features/playerrole/AngelGodsOfferingsTest.java`
- Test: `AngelGodsOfferingsTest`

**Interfaces:**
- Consumes: `DmzFormMetadataRegistry.trainerOfferings` (NPC path), `PlayerRoleService.get`, `TrainerPurchasePacket.purchase` apply path in `FormEditorNetwork` (evaluate → `removeTrainingPoints` → `setSkillLevel` → `StatsSyncS2C` / `ProgressionSyncS2C`)
- Produces: angels offer **only** formTypes belonging to group `xenopixels_gods_forms` (D0-c)

Verified group JSON shape (do not invent formType strings): `data/xenopixelsmod/dmz/races/saiyan/forms/xenopixels_gods_forms.json` uses `"groupName": "xenopixels_gods_forms"` and `"formType": "xenopixels_divinity"` with form keys like `ssg`, `ssb`, … — assert against **registry metadata group / formType** as loaded by DMZ/Xeno, not against CustomizationManager.

- [ ] **Step 1: Evidence appendix**

Document how NPC trainers register offerings today (`DmzFormMetadataRegistry` / skill master save path). Answer:

```markdown
## Can a player UUID / ServerPlayer be a trainer offerings source?
YES / NO — cite class/method.

## Apply path
Must remain FormEditorNetwork.TrainerPurchasePacket.purchase
(evaluate → removeTrainingPoints → setSkillLevel → sync).
```

If NO without inventing APIs → stop after appendix; leave angel cosmetic-only.

- [ ] **Step 2: If YES — failing filter test**

```java
@Test
void angelOfferingsOnlyGodsFormsGroup() {
    assertTrue(AngelTrainerGate.isAngelTrainerRole(PlayerRoleId.ANGEL));
    assertFalse(AngelTrainerGate.isAngelTrainerRole(PlayerRoleId.NONE));
    // resolveOffering rejects non-gods formTypes; accepts a known gods offering
    assertNull(AngelTrainerGate.resolveOffering("not_a_gods_form"));
}
```

Adjust assertions to the real `DmzFormMetadata` fields (`formType`, group name) after reading one gods form from registry/JSON.

- [ ] **Step 3: Implement gate; wire interact → existing trainer menu / `FormEditorNetwork.purchase` only when role is ANGEL**

No combat modifiers. No `CombatSkills.grant` for forms.

`resolveTrainerOffering` sketch (keep apply in `purchase`):

```java
if (trainer instanceof ServerPlayer angel && AngelTrainerGate.isAngelTrainer(angel)) {
    return AngelTrainerGate.resolveOffering(formType);
}
```

- [ ] **Step 4: Tests PASS; CHANGELOG notes evidence citation + gods_forms-only**

- [ ] **Step 5: Commit only if owner asks**

---


### Task 7: PR-D6a — New race registration evidence + RacePack IO

**Files:**
- Create: `docs/superpowers/evidence/2026-10-03-race-registration-path.md`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/dmz/race/RacePackService.java` **only if** appendix READY
- Create: `src/test/java/net/bullettrain/xenopixelsmod/dmz/race/RacePackServiceTest.java`
- Possibly modify: `DmzContentBootstrap.java` (install list) — only with READY citation
- Test: `RacePackServiceTest` or evidence-only if BLOCKED

**Owner decision (2026-10-03):** full race registration **if evidence READY**. UI still ships; Create Race disabled when BLOCKED.

**Interfaces:**
- Consumes: decompiled `RaceCharacterConfig`, existing `data/xenopixelsmod/dmz/races/<id>/`, `DmzContentBootstrap` install patterns, DMZ race discovery (`javap` / `tools/generated`)
- Produces:

```java
public final class RacePackService {
  public static Set<String> knownRaceIds();           // installed + xeno packs
  public static ValidationResult validateRaceId(String id);
  /** READY path only — creates races/<id>/ skeleton (character.json + forms dir). */
  public static CreateResult createRacePack(String raceId, RacePackTemplate template);
}
```

- [ ] **Step 1: Evidence appendix**

Inventory how DMZ discovers races (config folder scan vs hardcoded enum). Cite class/method. Verdict:

```markdown
## Verdict
New race id registration: BLOCKED | READY
If READY: exact create/install steps (files + bootstrap hook).
If BLOCKED: Create Race UI disabled; cite blocker; known-race edit still allowed.
```

Do **not** invent a registrar.

- [ ] **Step 2: If READY — failing tests for validate + create skeleton under a temp dir / in-memory fixture**

```java
@Test
void rejectsIllegalRaceId() {
    assertFalse(RacePackService.validateRaceId("Not Valid").ok());
}
@Test
void createRacePackWritesCharacterJsonWhenReady() { /* fixture fs */ }
```

- [ ] **Step 3: If BLOCKED — stop after appendix; CHANGELOG notes Create Race gated**

- [ ] **Step 4: Commit only if owner asks**

---

### Task 8: PR-D6b — MakerPreviewController (true local-player model + glow)

**Files:**
- Create: `docs/superpowers/evidence/2026-10-03-maker-preview-player-model.md`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/client/maker/MakerPreviewController.java`
- Keep/reuse: `PreviewDebounce.java` (≤50 ms)
- Create: `src/test/java/net/bullettrain/xenopixelsmod/client/maker/PreviewDebounceTest.java` (if missing)
- Test: debounce unit + compile; preview path cited

**Owner decision:** preview **must** be the true first-person-ready / local-player DMZ model stack — same body/hair/aura/clothes the player uses — not a disposable dummy and not text lines.

**Interfaces:**

```java
public final class MakerPreviewController {
  public enum GlowTarget { NONE, RACE_CARD, FORM_ROW, PART_CATEGORY, HAIR_SEGMENT }
  public void bindLocalPlayer(Minecraft mc);
  public void setGlow(GlowTarget kind, String id);
  public void markDirty();                 // schedule ≤50 ms refresh
  public void render(GuiGraphics g, int x, int y, int w, int h, float partial);
}
```

- [ ] **Step 1: Evidence — find how DMZ Character Creator / form GUIs render the player**

Cite `Class#method` (e.g. entity render / inventory-style player draw / DMZ character preview). If none exists that can show the **local player** stack → verdict BLOCKED; STOP and ask owner (do not ship text fallback as “done”).

- [ ] **Step 2: Debounce test**

```java
@Test
void firesAfterFiftyMs() {
    PreviewDebounce d = new PreviewDebounce(50);
    d.markChanged(0L);
    assertFalse(d.shouldFire(49L));
    assertTrue(d.shouldFire(50L));
}
```

- [ ] **Step 3: Implement controller with glow target id; every maker screen calls `markDirty` on widget change**

Glow = green outline/bloom matching Hair Editor screenshots (selected style / strand).

- [ ] **Step 4: CHANGELOG — preview path citation; runtime unverified**

- [ ] **Step 5: Commit only if owner asks**

---

### Task 9: PR-D6c — Atlas PanelSpecs for exact maker layouts

**Files:**
- Modify: `tools/atlas-panels/xeno_extra_specs.py` — Character Creator / Form Maker / Hair Editor panel sizes (`palette="green"`)
- Run: `python tools/atlas-panels/dmz_atlas_generator.py` for **new panels only**
- Wire sprites into `XenoAtlasSprites` if the repo’s pattern requires enum regen (`gen_java_enum.py`)
- Test: existing atlas panel test pattern / compile

**Reference layouts (owner screenshots):**
1. **Race Character Creator** — top race cards + Male/Female; left category column (Body, Eyes, Mouth, Hair, Aura, Clothes, Extra); center part grid; right full-body preview.
2. **Form Maker** — left form list; center form settings (Aura Color/Type, Form Type, Model Scale, …); left/center aura + form avatar previews; parts sub-screen entry (Image 2).
3. **Hair Editor** — left style list; center large hair preview with **green glow** on selection; Hair Color / Extra Color; Apply / Cancel.

- [ ] **Step 1: Diff needed sizes vs `panels_manifest.json`; add only missing PanelSpecs**

Never stretch baked borders (`docs/atlas-ui-doco.md`).

- [ ] **Step 2: Generate green panels; path-scoped resources**

- [ ] **Step 3: Commit only if owner asks**

---

### Task 10: PR-D6d — Race Character Maker screen (exact Image 1)

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/client/maker/RaceCharacterMakerScreen.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/client/maker/XenoMakerHubScreen.java` (or command opens this screen with mode)
- Wire: `/xenomaker` (+ `race` sub) via client commands / `ClientScreens`
- Permissions: `MAKER_OPEN` (and `MAKER_RACE_CREATE` if Task 7 READY)
- Test: compile + open-path unit if present

**Layout contract (match screenshot):**
- Race card row (Saiyan / Namekian / Human / Arcosian / Majin / **+ new races from Task 7**)
- Body type Male/Female
- Categories: Body, Eyes, Mouth, Hair, Aura, Clothes, Extra — selecting a category highlights it and filters the part grid
- Part tiles in center; selected part **green glow**
- Right: `MakerPreviewController` full-body true player model; updates on every change

- [ ] **Step 1: Screen skeleton with atlas frame + empty states**

- [ ] **Step 2: Wire categories + part grid to verified appearance/part APIs** (`NpcAppearanceParts` / DMZ character parts — cite; do not invent)

- [ ] **Step 3: Create Race button — enabled only if Task 7 READY; else tooltip cites appendix**

- [ ] **Step 4: Preview + glow on selection; CHANGELOG; not verified in game**

- [ ] **Step 5: Commit only if owner asks**

**Owner 2026-10-03 chrome / controls upgrade (binding):**
- Gold frames/banners for hub + Race title (`banner_top` Theme.GOLD); green Task-9 maker
  panels (`xeno_maker_*`) stay green for inner category/grid/preview.
- Advanced controls: `AtlasCycle` (category + presets + gender), `AtlasButton`,
  `InlineColorPicker` + `ColorSwatch` for Skin/Eyes/Hair (preview-local only).
- `MakerPresetCatalog.labels(Category, race, gender)` — live from `RaceMakerParts` /
  `TextureCounter` / `HairManager` when READY; documented FALLBACK when counts are 0.
  Aura/Clothes remain empty (citation gap). Units: `MakerPresetCatalogTest`.

---

### Task 11: PR-D6e — Form Maker screen (exact Images 2–4) + parts sub-screen

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/client/maker/FormMakerScreen.java`
- Reuse: `DmzFormDocument` / `FormEditorNetwork.save` / metadata registry
- Parts sub-screen: nested panel or child screen reachable from Form Maker (Image 2 owner note)
- Test: form save still through `FormEditorNetwork`; guard unknown fields

**Layout contract:**
- Left: scrollable form list (Base, SSJ, …) with selected row highlight/glow
- Center: Form Settings — Aura Color, Aura Type, Form Type, Model Scale, Race Type, Transformation Type, Hair Color, etc. (fields that exist on verified `DmzFormDocument` / metadata only)
- Dual preview: aura/form avatar + full-body `MakerPreviewController`
- Save/load via existing form editor network — **no invented form schema keys**

- [ ] **Step 1: Screen + form list bound to known race/group (Task 7 packs if READY)**

- [ ] **Step 2: Settings widgets → document fields; `markDirty` preview each change**

- [ ] **Step 3: Parts sub-screen for in-form appearance parts (shared glow + preview)**

- [ ] **Step 4: Save path = `FormEditorNetwork`; CHANGELOG**

- [ ] **Step 5: Commit only if owner asks**

---

### Task 12: PR-D7 — Advanced Hair Editor (exact Images 5–6) + codec/export/apply

**Files:**
- Replace UX of: `src/main/java/net/bullettrain/xenopixelsmod/client/maker/HairMakerScreen.java` (exact layout; drop text-summary-as-preview)
- Keep/extend: `…/hair/HairMakerDocument.java`, `HairStrandModel.java`, `HairApplyService.java`
- Evidence: `docs/superpowers/evidence/2026-10-03-hair-codec-vectors.md`, `…-hair-apply-path.md` (create/update)
- Fixtures: `src/test/resources/hair/golden-vectors.json`
- Tests: `HairCodecVectorsTest`, `HairApplyServiceTest`

**Layout contract:**
- Left: hair style list (Default Hair Style, Super Saiyan, …)
- Center: large hair/style preview with **green glow outline on the selected style or strand**
- Per-segment editors: length / curve / scale / rotation (lab fields); Connected label = parenting (no new boolean)
- Hair Color + Extra Color pickers
- Export JSON; Apply = replace-current-style via cited `UpdateCustomHairC2S` when READY

- [ ] **Step 1: Golden vectors (≥2) under `src/test/resources/hair/`; failing fixture test**

- [ ] **Step 2: Exact-layout screen + per-segment list; glow on selected segment/style**

- [ ] **Step 3: Preview uses `MakerPreviewController` (true player hair stack), debounce ≤50 ms**

- [ ] **Step 4: Export path; Apply enablement from apply-appendix status (`path_ready_runtime_unverified` or disabled)**

- [ ] **Step 5: Tests PASS; CHANGELOG; commit only if owner asks** (omit submodule pathspecs)

---

### Task 13 (optional): PR-B3 — Generator Motif density (aura3 rebuild)

**Files:**
- Create: `tools/effekseer/efkgen/effects/aura_motifs.py`
- Modify: `tools/effekseer/efkgen/effects/aura3.py` to read motif edge/thunder/IMPACT scales
- Full regen: `python tools/effekseer/gen_effects.py aura3` (**no** `--missing`)
- Verify: `Aura3EffectsTest`
- Modify: `CHANGELOG.md`

Skip unless the owner explicitly asks for Motif density this cycle.

- [ ] **Step 1: Define motif table + wire `licks`/`bolts`/IMPACT without inventing client density fields**

- [ ] **Step 2: Full regen + `Aura3EffectsTest` PASS**

- [ ] **Step 3: Path-scoped stage only if owner asks to commit**

---

### Task 14: Phase 2 handoff checklist

- [ ] **Step 1: Focused suite**

```powershell
./gradlew.bat test --offline -PofflineMcMeta `
  --tests net.bullettrain.xenopixelsmod.features.playerrole.* `
  --tests net.bullettrain.xenopixelsmod.features.tournament.* `
  --tests net.bullettrain.xenopixelsmod.dmz.race.* `
  --tests net.bullettrain.xenopixelsmod.client.maker.* `
  --tests net.bullettrain.xenopixelsmod.hair.* `
  --tests net.bullettrain.xenopixelsmod.fx.aura.Aura3EffectsTest
```

Drop globs that do not exist yet.

- [ ] **Step 2: Record jar SHA (if built), dirty count, evidence verdicts (D2a / D5 / D6 race / D6 preview / D7c), HEAD hash**

- [ ] **Step 3: Path-scoped commit only if owner asks**

```powershell
# Parent-repo paths only — omit tools/dmz-hair-builder-site/**
git add docs/superpowers/plans/2026-10-03-tournament-roles-makers-phase2.md
git add docs/superpowers/specs/2026-10-03-hd-aura-tournament-makers-design.md
git add docs/superpowers/evidence/2026-10-03-*.md
git add src/main/java/net/bullettrain/xenopixelsmod/features/playerrole/
git add src/main/java/net/bullettrain/xenopixelsmod/features/tournament/
git add src/main/java/net/bullettrain/xenopixelsmod/dmz/race/
git add src/main/java/net/bullettrain/xenopixelsmod/client/maker/
git add src/main/java/net/bullettrain/xenopixelsmod/client/tournament/
git add src/main/java/net/bullettrain/xenopixelsmod/hair/
git add src/test/java/net/bullettrain/xenopixelsmod/features/playerrole/
git add src/test/java/net/bullettrain/xenopixelsmod/features/tournament/
git add src/test/java/net/bullettrain/xenopixelsmod/dmz/race/
git add src/test/java/net/bullettrain/xenopixelsmod/client/maker/
git add src/test/java/net/bullettrain/xenopixelsmod/hair/
git add src/test/resources/hair/
# Shared wiring — only if owner approved whole-file include
```

- [ ] **Step 4: Do not copy jar / launch game / push unless owner asks**

- [ ] **Step 5: Stop — NPC todolist items 1–14 remain out of scope**

---

## Out of scope

- NPC todolist 1–14
- Inventing a race registrar when Task 7 is BLOCKED
- Instanced tournament dimensions
- Auto-KO before owner-requested follow-on after D2a READY
- Hair `connected` boolean schema vNext (unless owner reopens KD16)
- Silently replacing true-player preview with text/dummy and calling makers “done”
- Fabric / multiloader
- Promoting maker/tournament APIs into `api/**` without owner decision
- Claiming in-game verification from unit tests or builds alone

## Spec coverage (self-check)

| Spec item | Task |
| --- | --- |
| KD14 / D0-a…g | Tasks 1, 3 |
| KD8 player roles ≠ NpcRole | Task 1 |
| KD7 Xeno tournament | Task 3 |
| D2a auto-KO gate | Task 2 |
| PR-D3 awards | Task 4 |
| PR-D4 atlas UI | Task 5 |
| KD17 / PR-D5 trainer | Task 6 |
| KD15 r3 / KD20 Unified Maker + new race | Tasks 7–11 |
| KD9 / KD16 / KD20 hair + glow + true preview | Tasks 8, 12 |
| KD5 / KD13 Motif Phase 2 | Task 13 optional |
| KD18 Aura3EffectsTest | Task 13 + 14 |
| NPC 1–14 | Out of scope |

## Placeholder / consistency scan

- Channels: `player_roles` + `tournament`, protocol `"1"` — not `ModNetwork` `"101"`.
- `PlayerRoleId.ANGEL` spelling consistent.
- `MAX_QUEUED = 12`.
- `/xenomaker` is the unified entry; Race / Forms / Hair are exact-layout screens.
- New race create gated on Task 7 READY.
- Preview = true local-player DMZ model + green glow selection.
- Hair Apply cites `UpdateCustomHairC2S` (or BLOCKED) — never `hair_style_*` codec.
- Parent git staging omits `tools/dmz-hair-builder-site/**`.