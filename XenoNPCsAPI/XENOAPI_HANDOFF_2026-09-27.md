# Handoff — XenoAPI: CustomNPCsAPI port to 1.21.1 NeoForge

**Date:** 2026-09-27  
**Repository:** C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen\CustomNPCsAPI  
**Branch:** xenoapi-1.21.1 (created from origin/master)  
**HEAD:** e6496d6896ba59f5afb55d1ad128c9b3d1896edf (upstream; nothing committed yet)

## Current state

- The work is uncommitted.
  - 128 files are staged as renames from `noppes/` to `src/main/java/xenoapi/`.
  - The rest are unstaged edits plus new files: `build.gradle`, `settings.gradle`,
    `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `.gitignore`, and this file.
- The parent XenoPixelsNetwork tree was not touched. Its existing dirty paths belong to the user.
- Toolchain: NeoForge 21.1.248, which pins `net.neoforged:bus:8.0.5` and
  `fancymodloader:loader:4.0.43` (from `neoforge-21.1.248-moddev-config.json`).
- The Gradle wrapper was copied from the parent (Gradle 8.8).

## Changes

- Public API: the package was renamed to `xenoapi.npcs.api`. See README "Changes from upstream"
  for the full list:
  - NeoForge bus
  - `ICancellableEvent`
  - `NpcAPI.setInstance`
  - `ScrollItem` `HolderLookup.Provider` parameters
  - `PotionEffectType` `Holder<MobEffect>`
  - `DamageSource.getMsgId()`
- Every replacement symbol was checked with `javap` or sources against
  `../build/moddev/artifacts/neoforge-21.1.248-{merged,sources}.jar`, `bus-8.0.5.jar`, and
  `loader-4.0.43.jar`.

## Verified

- `./gradlew build`: BUILD SUCCESSFUL. There are 12 pre-existing `[dep-ann]` warnings from
  upstream javadoc `@deprecated` tags.
- `build/libs/xenoapi-1.21.1-0.1.0.jar`: 128332 bytes, sha256
  `8a59e067c7ec22ab4277e19925c6c9d9d9c58bf2ffa52d67e0dba75920b9affd`, 234 entries, no `noppes` entries.
- `build/libs/xenoapi-1.21.1-0.1.0-sources.jar`: 74246 bytes, sha256
  `2c28286e577596284959b13958fa16d5650d6ba4841d6223039cfd69a79ae87b`.
- `grep -rnE "noppes\.|minecraftforge" src` finds nothing.
- `javap`: `NpcEvent$DamagedEvent` and `ForgeEvent` implement `net.neoforged.bus.api.ICancellableEvent`,
  and `NpcAPI.setInstance(NpcAPI)` exists.
- `PotionEffectType` constants 1–32 match the declaration order of the first 32 `MobEffects` fields
  in the 1.21.1 sources.

## Not verified

- No implementation of `NpcAPI` exists, so no event has been posted or received and nothing has run
  in game.
- `ScrollItem` JSON round-trip with a real registry provider.
- Upstream license terms: upstream ships no LICENSE file.

## Next steps

1. Review the diff, then commit on `xenoapi-1.21.1` if approved. Stage explicit paths.
2. In xenopixelsmod, implement `NpcAPI`, call `NpcAPI.setInstance(...)` during mod construction,
   and depend on the xenoapi jar.
3. Resolve licensing with the upstream author before redistributing.
