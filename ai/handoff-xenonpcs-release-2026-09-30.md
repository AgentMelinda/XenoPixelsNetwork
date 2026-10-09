# Handoff — XenoNPCs dual release and Forge runtime fixes

**Date:** 2026-09-30  
**Repository:** C:/XenoPixelsNetwork_qwen  
**Branch:** 1.21.1  
**HEAD:** 53a025e45a4fecc2aeb1a2d062db550f88bbf571

## Current state

Main repository remains dirty and uncommitted; preserve all pre-existing changes. Full dirty path lists and artifact hashes/sizes are in [the evidence JSON](handoff-xenonpcs-release-2026-09-30-evidence.json).

Separate XenoNPCs repositories were explicitly authorized for commits, pushes and replacement of v0.0.1. Final NeoForge commit: d5f7bc0a815155bdb29d0507c323de9eb721d190. Final Forge commit: b77ef2b5a2695c53e26d8773a8399e8e7f215e06. Annotated tag object: 9a623780fa1a074eb088b3343a6fae3788a39be4.

Forge client PID 24168 is left running for the user's gameplay checks. Its classes match the final addon build. A running client and untracked logs mean neither project is claimed clean.

## Changes

- Corrected Forge DMZ LockOnEvent synthetic lambda targets against exact CurseMaven file 8469416.
- Ported GeckoLib JVM descriptors, animation controller return type and DMZ Molang callback signature against the exact mapped 4.8.3 jar.
- Bundled MixinExtras Forge 0.4.1 in the -all runtime jar. CI selects this jar and checks the nested dependency, then labels the released asset with the Minecraft version.
- Forge speech/dialogue bubbles render at AFTER_WEATHER: the exact Forge 47.4.10 GameRenderer AFTER_LEVEL call passes its projection stack, while the aura pass uses the world pose at AFTER_WEATHER. NeoForge stage is unchanged.
- Removed melee swing from ranged aiming, which made the Full DMZ proxy punch during ki casts.
- Exported the omitted wave/laser fixed-aim mixins. Refresh target aim after DMZ rewrites charging/launch wave position and orientation, avoiding the brain's level body pitch overwriting shot aim. No tracking after launch.
- Normal RUN approach no longer falls through to teleport chase. Explicit DASH/vanish remain available. Out-of-reach pressure combo releases movement ownership instead of remaining active forever.
- Landing probes only below the player's feet and counts floor support even while Fly skill remains enabled. Existing flight tests updated. Existing combo source-shape assertion updated for the changed movement-yield rule.
- User corrected respawn complaint: it does respawn. User also confirmed the alleged ki-blast crash was the agent's normal shutdown for rebuilding; do not label it a reproduced ki-hit crash.

## Verified

Commands: root `./gradlew.bat test build jarJar serverJar -PofflineMcMeta --no-daemon`; NeoForge addon `./gradlew.bat build -PofflineMcMeta --no-daemon`; Forge addon `./gradlew.bat build --no-daemon`. All exit 0 on final runs. Root: 3004 tests, NeoForge: 903, Forge: 868; zero failures/errors/skips in included tests. Forge's 34 separately excluded runtime tests remain unverified.

Final server jar has zero META-INF/jarjar entries. Addon class major versions are 65 (Java 21) and 61 (Java 17). Forge runtime jar contains META-INF/jarjar/mixinextras-forge-0.4.1.jar. Both addon configs contain common.KiWaveAimMixin and common.KiLaserAimMixin. Actionlint 1.7.12 validates release workflow.

Fresh Forge runClient log: MixinExtras loaded at 13:03:08.696; earlier run joined at 13:03:41.370 after the missing-runtime fix. Final client joined at 13:27:44.594. Window title confirms Singleplayer; no fresh fatal exception observed as of the handoff. Earlier world crash at 12:57:51 was ClassMetadataNotFoundException for MixinExtras Operation; initial loading crash at 12:36:47 was the invalid LockOnEvent lambda. Builds alone do not prove these gameplay fixes.

Release workflow: https://github.com/AgentMelinda/XenoNPCs/actions/runs/36702730032 . All three jobs succeeded. Release is public, not draft, with exactly two labelled jars. Downloaded assets passed class version, loader metadata, mixin registration and SHA-256 checks against GitHub digests. Release: https://github.com/AgentMelinda/XenoNPCs/releases/tag/v0.0.1

## Not verified

Final visual bubble alignment, ranged animation behaviour, movement feel, landing during retaliation, airborne wave accuracy at 10–20 blocks, optional CustomNPCs/CNPC Gecko integration and 34 runtime-only Forge tests. User was asked to test the newly opened client. No latest gameplay confirmation has arrived at handoff-writing time.

## Next steps

1. Finish CI publication and inspect/download both release assets; record CI hashes separately from local hashes.
2. Use fresh user gameplay feedback to confirm remaining behaviours. Preserve not-verified statements until actual observations exist.
3. If new issues arise, keep the release draft and diagnose against current logs/exact jars; do not change unrelated dirty main-repository paths.

## Published artifacts

- xenonpcs-0.0.1-1.20.1.jar: 13537142 bytes; SHA-256 bafed37109840426de28f6a97a2cd0deee201b67a12068f6e18c88b96ab257df
- xenonpcs-0.0.1-1.21.1.jar: 15481529 bytes; SHA-256 2381b9061fc13f17c146d3cc29abb4c589df1c204b60f886879aec21d97f9a37
