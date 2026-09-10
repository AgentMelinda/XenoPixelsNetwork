# XenoPixels Network Repository Guidance

**Updated:** 2026-09-10

These instructions apply to the entire repository. Read `ai/README.md` and the relevant
skill playbook before changing code, resources, build logic, binaries, or release material.

## Truth and evidence

- Never invent a Minecraft, NeoForge, DragonMineZ, Sable, or addon API.
- Verify symbols in repository source first, then tracked decompiled reference source, then
  `javap` against the exact jar, and only then official documentation for the pinned version.
- Label runtime claims as verified only when a fresh process produced a current log or observable
  result. A build or unit test is not proof that an event fired in game.
- Use explicit dates such as `2026-09-10`; do not write ambiguous “today” into durable docs.
- Preserve “not verified” statements until the missing check has actually been performed.

## Working-tree safety

- Inspect `git status --short` before and after work. Existing changes belong to the user unless
  the task explicitly includes them.
- Never use `git add -A`, `git add .`, blanket checkout/reset, or destructive cleanup in a dirty
  tree. Stage reviewed paths explicitly.
- Do not delete untracked archives or large assets to satisfy GitHub warnings. The hard limit and
  advisory warning are different; preserve anything not reproducible.
- Review `build.gradle`, `gradle.properties`, workflows, mixin configs, and binary changes
  individually before staging.
- Do not commit, tag, push, or rewrite history unless the user explicitly requests it.

## Repository contract

- Target Java 21, Minecraft 1.21.1, NeoForge 21.1.248, and mod id `xenopixelsmod`.
- `libs/dragonminez-2.1.3.jar` is a tracked compile/runtime dependency. Its SHA-256 must exactly
  match `dragonminez_sha256` in `gradle.properties` and both must change together.
- Treat `src/main/java/net/bullettrain/xenopixelsmod/api/**` as published compatibility surface.
  Everything outside it is internal unless another documented integration says otherwise.
- Keep the main `ModNetwork` sequential channel stable. Addon packets belong in the separate
  namespaced `AddonNetwork` registry.
- Third-party DMZ patches are additive-only. Never add delete or overwrite semantics without a new
  owner decision and migration/backup design.
- Prefer the narrowest reliable mixin injection, preserve side safety, and verify targets against
  the exact dependency build.

## Generated and external material

- Do not edit `build/`, `run/`, Gradle caches, or example build outputs as source.
- `tools/generated/` is tracked reference/decompiled material. Change it only for a task that
  explicitly regenerates that reference set.
- `dragonmine_complete_ui_elements_master_bundle/`, `ui/`, and tracked archives are source
  assets, not disposable build output.
- Generated resources belong under `src/generated/resources`; authored resources belong under
  `src/main/resources`.

## Validation

- Start with focused tests for changed code, then run `./gradlew test`.
- Distribution validation is `./gradlew build jarJar serverJar -PofflineMcMeta`.
- The standalone API boundary check is `./gradlew buildApiExampleAddon -PofflineMcMeta`.
- Runtime API startup/packet proof is `./gradlew runApiTestClient`; inspect a fresh
  `run/logs/latest.log` rather than relying only on task exit status.
- Confirm the server jar has zero entries below `META-INF/jarjar/`.
- Do not fix unrelated failures while validating a focused change; report them separately.

## Handoffs

- Record branch, full commit hashes, dirty paths, exact commands, artifact hashes/sizes, verified
  observations, unverified items, and safe next steps.
- Never claim a clean tree while a process is still running code loaded before the latest rebuild.
- Use the template in `ai/handoff-template.md`.
