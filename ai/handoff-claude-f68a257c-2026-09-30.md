# Handoff — Claude f68a257c continuation and NPC vertical approach

**Date:** 2026-09-30  
**Repository:** C:/XenoPixelsNetwork_qwen (same filesystem directory as C:/Users/Admin/.grok/worktrees/dragonminez/XenoPixelsNetwork_qwen)  
**Branch:** 1.21.1  
**HEAD:** 53a025e45a4fecc2aeb1a2d062db550f88bbf571

## Current state

- Recovered the actual 71 MB Claude JSONL session f68a257c-007c-4d05-ab17-568a18ef1399. Its final tool result was a failing NpcLedgeApproachTest on 2026-09-30, followed by the session limit.
- Main repository started with 886 dirty status entries. Existing work is user-owned; no commits, staging, tags, pushes, or history rewrites were performed in this continuation.
- XenoNPCs: branch 1.21.1, HEAD f6b6cb4453617feae63a447b584fb41e6eff25ce.
- XenoNPCs-1.20.1: branch 1.20.1, HEAD 4068bc38354c68e18377a36cf5698ea81e2cf169. Its generated files are mostly untracked.
- All three repositories had upstream divergence 0 ahead / 0 behind when checked; this uses local remote-tracking refs, not a fresh fetch.
- Backups of both generated source/project trees and status snapshots are in C:/Users/Admin/AppData/Local/Temp/codex-claude-f68a257c-z7wf94rx. Export regenerated only its generated paths, preserving build/, run/, libs/, and .git.
- Full final dirty-path manifests, artifact/dependency hashes, and process state are in [the evidence JSON](handoff-claude-f68a257c-2026-09-30-evidence.json). Final dirty entry counts: main 889, XenoNPCs 23, XenoNPCs-1.20.1 22. No Java processes remained at handoff. A dirty tree is intentional.

## Changes

- NpcLedgeApproach: reject recentering navigation in the player's column when a height gap remains; stop stale navigation there; retain the forward step off a detected edge toward a lower target.
- XenoNpcBrainV5: native role chase uses withinMelee and the same ledge decision instead of an independent 3D-distance moveTo loop that bypassed the height-gap gate.
- NpcBrainKiRotation, NpcSagaCombatBrain, NpcKiAim: preserve yaw within a 0.1-block horizontal offset so tiny offsets in the same column do not flip facing.
- XenoServerConfig: version 25 migrates exactly the obsolete 1.5 height-gap setting in pre-25 files to 0.5 once. Other values and later overrides survive. The main dev config also has a preexisting npcAttackStartRadius=4.5; it was not changed.
- NpcLedgeApproachTest: original regression and horizontal/facing cases. NpcMeleeHeightMigrationTest: old defaults, custom tuning, newer overrides, repeat migration.
- Claude's V9 arm-swing fallback in NpcSagaCombos/NpcSagaCombatBrain was already present; validated it rather than attributing it as newly written here.
- tools/xenonpcs/patches_1_20_1.py: convert the exported mod constructor to the Forge FMLJavaModLoadingContext constructor, obtaining its event bus through getModEventBus. Both signatures and Forge constructor selection were inspected with javap against javafmllanguage-1.20.1-47.4.10.jar.
- Re-exported both versions from the shared source, including these fixes. No public API or packet registry changes.

## Verified

- Read AGENTS.md, ai/README.md, repo-facts, safety, validation, Java/build/runtime playbooks and xenopixels-addon-development skill/native-NPC reference.
- node C:/Users/Admin/.agents/skills/xenopixels-addon-development/scripts/audit-project.mjs C:/XenoPixelsNetwork_qwen: 0 failures, 6 broad integration/evidence warnings. Root DMZ binary/property hash match passed.
- ./gradlew.bat test --tests '*NpcLedgeApproachTest' --tests '*NpcV9SwingFallbackTest' --tests '*NpcComboSwingRangeTest' -PofflineMcMeta --no-daemon: passed.
- ./gradlew.bat test --tests '*NpcLedgeApproachTest' --tests '*NpcV9SwingFallbackTest' --tests '*NpcComboSwingRangeTest' --tests '*NpcMeleeHeightReachTest' --tests '*MovementWiringTest' -PofflineMcMeta --no-daemon: passed.
- ./gradlew.bat test --tests '*NpcMeleeHeightMigrationTest' --tests '*NpcLedgeApproachTest' -PofflineMcMeta --no-daemon: passed.
- Final main ./gradlew.bat test build jarJar serverJar -PofflineMcMeta --no-daemon: BUILD SUCCESSFUL; 3004 tests, no failures/errors/skips. Current 0.5.0 server jar has zero entries under META-INF/jarjar/.
- python tools/xenonpcs/export_xenonpcs.py and --target 1.20.1: succeeded. python ../tools/xenonpcs/test_export.py . in XenoNPCs: export ok.
- Final XenoNPCs ./gradlew.bat build -PofflineMcMeta --no-daemon: BUILD SUCCESSFUL; 903 tests, no failures/errors/skips. Log: XenoNPCs/build-codex-resume-1211.log.
- Final XenoNPCs-1.20.1 ./gradlew.bat build --no-daemon: BUILD SUCCESSFUL; 868 executed tests, no failures/errors/skips. The 34 runtime-only exclusions are explicitly listed in runtime-only-tests.txt and EXPORT_REPORT.md; they are not verified. Log: XenoNPCs-1.20.1/build-codex-resume-1201.log.
- Fresh 1.20.1 ./gradlew.bat runServer --no-daemon: mod constructor succeeded; 12:15:32.633 log registers native XenoAPI; 12:16:03.985 log reaches Done; scripting engine initializes at 12:16:04.446. This run preceded the final movement/config re-export. The console stop was not forwarded; the owned process tree was ended after timeout (exit 1). Startup evidence is from logs, not the task exit.
- Fresh final 1.21.1 ./gradlew.bat runServer --no-daemon --init-script C:/Users/Admin/AppData/Local/Temp/codex-claude-f68a257c-z7wf94rx/server-stdin.gradle: 12:24:20.175 registers native XenoAPI; 12:24:21.595 logs npcMeleeHeightReach 1.5 -> 0.5; 12:24:26.966 reaches Done. Saved runtime config reports version 25 and value 0.5. Console: XenoNPCs/runserver-codex-resume-1211.log. Shutdown result recorded in companion evidence.
- The final 1.21.1 server shut down normally: Stopping server at 12:24:58.522, all dimensions saved, BUILD SUCCESSFUL, process exit 0.
- Artifact sizes/hashes and final tree/process state are in the companion JSON. Older jars already in build/libs are not current artifacts.
- git diff --check on the config file reports extensive preexisting CRLF/trailing-whitespace additions against HEAD; they were not normalized as an unrelated change.

## Not verified

- No actual player/NPC ledge chase, swing rendering, respawn flicker, collision, or packet behavior was observed in game. Unit checks and server startup do not prove those behaviors.
- No client boot or CustomNPCs/cnpcgecko gameplay checks in this continuation. The absence of a supplied MyNPCs 1.20.1 integration remains a port limitation.
- The final 1.20.1 movement/config changes have build/unit evidence; its startup log above was from the earlier constructor-fix build.
- Combined one-tag/two-version release workflow, overlay signature-drift checks, full mixin target audit, dedicated compat runtime checks, release/tag/wiki publishing, and the previous player fall-damage report remain unfinished from the larger Claude plan. No release was published here.
- The simple column guard does not prove correct routing for every multi-level staircase or platform layout. Exercise those manually before release.

## Next steps

1. Test the linked rebuilt 1.21.1 jar in a fresh client: NPC above player at same X/Z, forward edge descent, player above NPC, ordinary same-floor chase, and respawn at the authored home. Record actual observations and logs.
2. Check existing per-NPC range/height tuning if air punches persist. The global old-default migration is verified; custom values remain editable.
3. Finish remaining 1.20.1 client/mixin/optional-mod validation and drift checks, then implement and validate the combined release workflow from the approved Claude plan.
4. Review only the task paths before any staging/publication. Keep the main XenoPixels tree uncommitted as directed in the recovered session; prior release authorization applied to the XenoNPCs repositories.
