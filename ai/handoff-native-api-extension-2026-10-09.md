# Handoff — Native NPC API extensions

**Date:** 2026-10-09  
**Repository:** C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen  
**Branch:** 1.21.1  
**HEAD:** aac595cb44d0f91b1845b43682c92d02bfa1a117

## Current state

No commits, staging or history changes. Preserve the existing dirty user tree. Full dirty
paths are recorded in `ai/dirty-paths-native-api-extension-2026-10-09.txt`. Upstream divergence
remains 0/0. This snapshot supersedes artifact/test figures in the earlier same-date handoff.
The owned test client PID 10768 was closed gracefully and its Gradle session exited 0.
The final callback-build test client PID 6584 was also closed gracefully after world/packet proof.
Normal shell filesystem/CIM access can fail; legitimate task-scoped escalation succeeded.

Pinned DMZ 2.1.3 SHA-256 remains
`5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`.

## Changes

- Persisted display-name modes, dead-body hiding and attacking-only boss bars, appearance
  synchronization, server-derived name visibility, renderer gates and save-policy validation.
- Persisted patrol pauses, real 20-tick dwell at successful arrivals, no dwell on timeout,
  actual native navigation-type getter; unsupported navigator replacement stays explicit.
- Transporter typed read-only single-destination view with fresh native network reads.
  Ambiguous multi-destination/origin-location semantics are explicitly refused.
- Registered real item throwable, client item renderer, specialized typed factory, both
  shootItem overloads, ordinary damage/collision/persistence and accepted-damage knockback.
  Explicit zero strength is harmless; dead NPCs cannot launch. Steering and launch are
  finite/loaded/bounded; copies do not consume caller stacks. Constant/accelerated item
  gravity explicitly refuses instead of silently changing its behavior.
- Two independent source reviews found no display blocker; projectile review found zero
  strength and gravity-mode issues, both corrected before the final build/runtime check.
- Exact NPC-tab projectile callbacks now capture weak/transient invocation context, validate
  host/tab/world/source identity, suppress reentrancy and recheck collision after callbacks.
  Ordered length-delimited script fingerprints prevent stale ownership after tab restructuring.
  Player/Forge script contexts remain outside this implemented scope. Nine focused tests include
  real Nashorn typed-field delivery, top-level capture and error/context restoration.

No published addon API signature or main packet registry order changed by this batch.
Native entity registry gains `xenopixelsmod:npc_item_projectile`.

## Verified

Focused command (exit 0):

```powershell
.\gradlew.bat test --tests '*NpcProfileVisibilityTest' --tests '*XenoNpcDisplaySavePolicyTest' --tests '*NpcPathTest' --tests '*XenoTransporterAdapterTest' --tests '*ItemProjectileRulesTest' --tests '*XenoApiAdaptersTest' --tests '*BundledNashornTest' -PofflineMcMeta
```

Final command after review fixes (exit 0):

```powershell
.\gradlew.bat test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta
```

JUnit XML after projectile callbacks: 568 suites, 3,649 tests, zero failures/errors/skips.
`git -c core.whitespace=cr-at-eol -c core.safecrlf=false diff --check` passed.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| build/libs/xenopixelsmod-0.5.11-1.21.1.jar | 76265283 | 226fbc8edcf0616f0ac0e1e7b4c026e08d7913abef2380875bcc8f35d2bc03ba |
| build/libs/xenopixelsmod-Server-0.5.11-1.21.1.jar | 52118434 | bbdc11da905a0e57ce3d8d04c93e5e9c1f83e68782f47afa073452d5cbabb620 |

Server entries under META-INF/jarjar contain only the directory, metadata.json and
aaa_particles-neoforge-1.21.1-2.3.1.jar. No Nashorn or Modern UI nested jars.

Both fresh `runApiTestClient` runs exited 0. The final callback-build fresh run reached world/packet
proof and was closed gracefully at 06:39:06. No Java process remained after shutdown. Evidence copied to
`ai/evidence/api-test-client-extension-2026-10-09.log`:

- 06:37:44.884: native XenoAPI implementation registered.
- 06:37:58.458: integrated Minecraft 1.21.1 server started.
- 06:38:06.323: Dev logged into the fresh world process.
- 06:38:10.145: network.ping nonce 2158410151800.
- 06:38:10.150: matching network.pong nonce 2158410151800.
- 06:39:06: all dimensions saved; client shut down normally.

Controlify remappable Shadow error, optional class warnings, GL_INVALID_ENUM messages and
shutdown fsync warnings remain unrelated observed issues. They did not prevent world/packet proof.

## Not verified

Full CustomNPCs behavioral parity remains incomplete. GUI, several jobs/roles, scripted
blocks/items, inventory projectile prototypes and Player/Forge container projectile callbacks remain
gaps. Raw engine handles stay refused by the sandbox. The audit declaration inventory is
not a count of working runtime functions.

Actual item launch/render/impact/reload, transporter interaction, path dwell and conditional
display have not been exercised in game. Startup and packet success do not prove those paths.
Previous combat/model/music gameplay limitations remain as recorded in the earlier handoff;
all Strike choreographies and the full YouTube DBZ OST are not complete.

## Next steps

1. Extend exact-container projectile capture to Player/Forge hosts if required. NPC-tab capture
   is implemented; ordinary host broadcast must never replace exact-tab projectile delivery.
   Native GUI implementation research is recorded in docs/customnpcs-gui-native-plan-2026-10-09.md;
   this document is a plan, not operational GUI support.
2. Verify native projectile, path and display scenarios from actual script calls in a fresh world.
3. Continue audited API gaps using exact native source; do not replace unsupported behavior
   with successful no-ops. See docs/combat-v3/customnpcs-api-audit-2026-10-08.md.
4. Preserve previous user instructions: no Claude transfer, local Downloads MP4 for combat
   reference, Hebrew communication and English attack names/code comments. OST source question
   remains unanswered. No commit/push/deployment without explicit authorization.
