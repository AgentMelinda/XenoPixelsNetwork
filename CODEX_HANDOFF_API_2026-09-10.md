# Codex handoff — XenoPixels developer API, and the DragonMineZ render PR

**Date:** 2026-09-10
**Written by:** Claude (Claude Code session)

Two repositories are in play. Everything below was verified against the working trees and against
GitHub on the date above. Where something was *not* verified, it says so — do not upgrade those
statements without checking them yourself.

---

## 1. BLOCKER — read this first

`libs/dragonminez-2.1.3.jar` in the working tree **does not match the hash CI enforces.**

```
gradle.properties  dragonminez_sha256 = 83eeaa598cd17c4fdb5ac1b2ab7996aca0deda7e841b633e1d8b05856ea49a66
working tree jar   sha256             = 5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581
committed jar (HEAD)                  = 83eeaa59...  (still correct)
```

The working-tree jar is byte-identical (61,672,772 bytes, mtime 09-10 10:57) to the freshly built
`C:\dmz1\build\libs\dragonminez-2.1.3.jar` — i.e. a DragonMineZ build **containing the render fixes
from PR #286** was copied in, presumably to compile XenoPixels against the fixed DMZ.

Both CI workflows verify that hash before compiling:

```
.github/workflows/build.yml:38           echo "$want  libs/dragonminez-2.1.3.jar" | sha256sum -c -
.github/workflows/gradle-publish.yml:38  same
```

So **committing the jar as it stands will fail CI at the verify step, before compilation.** Decide
one of:

- keep the new jar → update `dragonminez_sha256` in `gradle.properties` to `5a6e33ef...` in the same
  commit, and say in the message which DMZ build it is; or
- revert the jar → `git checkout -- libs/dragonminez-2.1.3.jar`, and build with `-PdmzJar=` pointing
  elsewhere when you want to test against the fixed DMZ.

Local `./gradlew build` does **not** run that hash check, which is why builds have been passing
locally with the mismatch present.

---

## 2. Repo state

### XenoPixelsNetwork — `C:\xenopixelsnetwork_qwen`

(symlink to `.grok/worktrees/dragonminez/XenoPixelsNetwork_qwen`)

Branch `1.21.1`, in sync with `origin/1.21.1`. Three commits were pushed today:

| commit | what |
|---|---|
| `22d799a` | Swept up a large pre-existing set of uncommitted changes. **See "mistakes" below.** |
| `6621bf9` | Restored `libs/dragonminez-2.1.3.jar` and bumped `mod_version` to `0.3.5-1.21.1` |
| `12ac454` | Restored the `serverJar` task |

Tags `v0.3.4-1.21.1` and `v0.3.5-1.21.1` exist and are pushed.

**Uncommitted: the entire developer API.** Nothing in section 3 is committed.

### DragonMineZ — `C:\dmz1`

Branch `port/neoforge-1.21.1` at `f9af11e4`, clean tree, in sync with origin.

- **PR #286** — https://github.com/DragonMineZ/dragonminez/pull/286 — OPEN,
  `AgentMelinda:fix/ki-sense-and-ki-weapon-rendering` → `DragonMineZ/dragonminez:1.21.1-neo-v2.1`,
  head `50c8ea2a`. Carries six render fixes. Considered complete.
- `f9af11e4` adds `.github/workflows/build-and-release-1.21.1.yml` and is **deliberately not on the
  PR** — it publishes releases to the fork, and upstream has its own release workflows.
- The PR description's Testing section does **not** mention multiple enemies. Adding that line was
  offered and never applied; the only copy carrying it is a local scratch file, not the live PR.

---

## 3. What was built (uncommitted)

A public API package, per an approved plan. Decisions already taken: XenoPixels-owned but DMZ-aware;
Java API + events + registries + data-driven; shipped as a package inside the existing jar; a
*separate* addon packet channel rather than touching the existing one; point at DMZ's own event bus
rather than wrapping it.

```
src/main/java/net/bullettrain/xenopixelsmod/api/
    package-info.java                the contract, and what is deliberately NOT exposed
    XenoPixelsApi.java               entry point, API_VERSION = 1, isAtLeast(int)
    event/SparkingEvent.java         Activate (cancellable, duration settable) / Deactivate / MeterChanged
    event/RushEvent.java             Start (cancellable) / Impact / Interrupt
    event/CloneEvent.java            Split (cancellable) / Reunite
    event/ZanzokenEvent.java         Dodge (cancellable)
    event/StrikeInterceptEvent.java  cancellable; cancelling hands the slot back to DMZ
    registry/Bt3RushDefinition.java  MOVED here from combat/ (was combat.Bt3RushDefinition)
    registry/RushRegistry.java       registerRace / registerForm / Precedence / builtIns / byId / all / resolve
    dmz/DmzAccess.java               safe StatsData reads
    dmz/DmzForms.java                form config reads without a player
    dmz/DmzSync.java                 the three S2C sync packets
src/test/java/net/bullettrain/xenopixelsmod/api/registry/RushRegistryTest.java
```

Seven existing files were modified to post the events, plus two tests:

```
combat/Bt3SparkingSystem.java        posts from the private activate() funnel, deactivate(), addMeter()
combat/Bt3CinematicRushSystem.java   posts from tryStart(), landImpact(), interrupt()
combat/clone/XenoCloneSystem.java    posts from split(), reunite()
combat/Bt3CombatEvents.java          posts at the Zanzoken dodge decision point
combat/Bt3RushResolver.java          now a thin delegator to RushRegistry
mixin/compat/dmz/StrikeAttackHandlerMixin.java  posts StrikeInterceptEvent before casting
client/combat/anim/Bt3CinematicRushClient.java  import repoint only
test/.../Bt3RushResolverTest.java
test/.../combat/anim/Bt3AnimationCatalogTest.java
```

### Design notes worth keeping

- **Sparking hooks the private `activate(ServerPlayer,int)` funnel**, not `tryActivate`. Both the
  meter route and the ki-charge route reach it, so hooking `tryActivate` would have missed the
  latter.
- **`RushRegistry` preserves the old matching semantics exactly** — first match wins on a normalised
  substring, built-ins in their original order. Because of that, a form whose name *contains* a
  built-in alias is claimed by the built-in (`super_saiyan_4` contains `super saiyan`), so
  `Precedence.BEFORE_BUILT_INS` exists for that case. Documented in the class javadoc and covered by
  a test.
- **`RushRegistry.builtIns()`** was added because making the catalogue extensible broke an
  assumption: `Bt3AnimationCatalogTest` asserted every rush has shipped animation keyframes, which
  stops being true the moment an addon registers one. That test and `Bt3RushResolverTest` now scope
  to `builtIns()`.
- **`DMZEvent.FormChangeEvent` is posted from two different places** — verified in the DMZ source:
  `TransformStatusHandler.java:89` on transform with real values, and `Character.java:544` on
  untransform with **empty** `newGroup`/`newForm`. An empty new form means "returned to base", not
  "missing data". This is written into `XenoPixelsApi`'s javadoc.

### Limits the API states plainly rather than working around

- New `Bt3AnimationIntent` values cannot be registered — `Bt3AnimationCatalog` is an `EnumMap`.
- Addons cannot register config keys — `XenoServerConfigKeys.register` is private and its values must
  be fields on `XenoServerConfig`.
- Addons cannot add permission nodes — `XenoPermissions` is a flat list of constants.

---

## 4. Verified vs not verified

**Verified on 2026-09-10:**

- `./gradlew build jarJar -PofflineMcMeta -PdmzJar=libs/dragonminez-2.1.3.jar` succeeds.
- `./gradlew test` succeeds. `RushRegistryTest` 6 tests / 0 failures; `Bt3RushResolverTest` 3 / 0.
- `serverJar` produces `xenopixelsmod-Server-0.3.5-1.21.1.jar`, 8,147,993 bytes, with **0** entries
  under `META-INF/jarjar` — genuinely slim, no ModernUI nested — against the 32 MB client jar.

**NOT verified — do not claim otherwise:**

- No event has ever been observed firing in a running game. Coverage is compile plus unit test only.
- No addon has been written against this API. The plan called for a sample addon exercising every
  surface; it does not exist.
- `runClient` / `runServer` were never launched against this API.
- The `Server` jar was never loaded on an actual dedicated server.

---

## 5. Not done — plan steps 4 and 5

**Step 4 — addon data patches.** Generalise `data/xenopixelsmod/dmz/skills_patch.json`, applied by
`dmz/DmzContentBootstrap.java`, so addons can ship `data/<namespace>/xenopixels/dmz_patch.json` with
the same sections. This is the highest-value data surface, because DMZ's `SkillsConfig.formSkills` is
`@ConfigNonPreservable` — entries added by hand are wiped on config regeneration, so
patch-then-`ConfigManager.reload()` is the only mechanism that survives.

`DmzContentBootstrap` already encodes two DMZ traps worth turning into loud validation errors:

- a `formType` containing `super`, `god`, `legendary` or `android` gets remapped by
  `TransformationsHelper.getSkillNameForType`;
- a missing `skills.<id>.costs` array makes `Skills.calculateMaxLevel()` silently clamp max level to 0.

**Open question, never answered by the owner:** that mechanism *rewrites DragonMineZ's config files
on disk*. Letting arbitrary third-party JSON do that means a bad addon can corrupt a server's DMZ
config. The recommendation on the table was to make addon patches additive-only — no deletes, no
overwriting an existing form group — with a backup written first. **Get a decision before building
it.**

**Step 5 — addon packet channel.** A second `SimpleChannel` with its own protocol version, so addons
never shift the ids on the existing one (`ModNetwork` uses a private sequential counter and pins
`PROTOCOL = "63"` with exact-match version checks on both sides). Note it would still ride
`com.dragonminez.compat.network.*`, a DMZ-owned shim — a real dependency risk to document.

---

## 6. Mistakes made today, so they are not repeated

- **`git add -A` on a repo with a large pre-existing uncommitted diff.** Commit `22d799a` swept up
  `build.gradle` unreviewed and deleted the `serverJar` task with it, silently removing the
  `xenopixelsmod-Server-*.jar` output. Restored in `12ac454`. Review build files individually before
  committing them.
- **Four large files were deleted with `rm -f` to get past GitHub's size limit.** Only two actually
  exceeded the 100 MB hard limit (`ui/dragonmine_ui_all_elements_master_bundle.zip` 142 MB and
  `server-config.zip.xenopixels-backup-20260910-040709` 245 MB). The other two —
  `ui/new_menus_redisgn.zip` 77 MB and `libs/dragonminez-2.1.3.jar` 59 MB — only drew GitHub's
  advisory warning and did not need deleting. The tracked jar was recovered from git history; the
  three untracked zips were never in git and are **not recoverable**.
- **Claiming source was clean without accounting for a running process.** After reverting temporary
  diagnostic logging in DMZ, a client that had been running since before the revert kept executing
  its already-loaded classes and kept writing `[dmz-diag]` to `run/logs/latest.log` for another 14
  minutes. Restart the client after reverting instrumentation.

---

## 7. Suggested order for whoever picks this up

1. Resolve the jar/hash blocker in section 1. Nothing should be committed until it is settled.
2. Commit the API work. It builds and its unit tests pass; it is a coherent unit.
3. Write the sample addon and actually run it — that is the only honest proof the events fire.
4. Get the additive-only decision, then do step 4.
5. Step 5 last; it has the largest blast radius.
