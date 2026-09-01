# Claude handoff: current CustomNPCs + DragonMineZ NPC work

Date: 2026-09-01 (Asia/Jerusalem)  
Workspace: `C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`

This is a scoped handoff for the current DMZ CustomNPC work. Older handoffs in this
repository describe earlier states and may contain obsolete protocol numbers or renderer
behavior. Verify claims below against the referenced source before changing them.

## Repository safety

- The worktree contains many pre-existing modified, deleted and untracked files from other
  work. Do not reset, clean, restore, or broadly reformat the tree.
- Relevant NPC source is largely untracked in Git at the time of writing, so `git diff` alone
  is not a complete inventory. Use `git status --short` and inspect paths directly.
- Platform: Minecraft 1.21.1, NeoForge 21.1.238, Java 21.
- DragonMineZ development dependency: `libs/dragonminez-2.1.3.jar`.
- CustomNPCs runtime jar:
  `run/mods/CustomNPCs-Unofficial-NeoForge-1.21.1.20251230.jar`.
- Network protocol in `ModNetwork` is currently `45`.
- CustomNPC scripting API version in `NpcXenoScriptApi` is currently `4`.

## Current renderer behavior

### Native DMZ aura

FULL-mode NPCs now use `DMZAuraLayer` and DMZ's deferred `AuraRenderer`. The previous
standalone `NpcAuraClient` billboard remains as a fallback for non-FULL appearances, but
`NpcAuraClient.onRenderLevel` skips FULL NPCs so it cannot draw a second aura.

Relevant files:

- `client/compat/npc/NpcFullDmzRenderer.java`
- `client/compat/npc/NpcAuraClient.java`
- `mixin/compat/customnpcs/DmzNpcAuraScaleMixin.java`

`DmzNpcAuraScaleMixin` multiplies DMZ's own resolved race/form model scale by:

1. CustomNPC Display size divided by the CNPC default size (`5`), and
2. the profile's explicit `auraScale` value.

It also feeds `NpcAuraResolver`'s primary/secondary layers and lightning/sparking choices
into DMZ's native shader renderer. Do not re-enable `DmzNpcNativeAuraMixin`; that old mixin
cancelled the native aura and has been removed from source and the compat mixin list.

### Tail colors

An empty `NpcDmzAppearance.tailColor` means native DMZ inheritance. A non-empty `#RRGGBB`
is an NPC-only override.

DMZ has two tail render paths:

- Saiyan: separate `tailenrolled` bone in `DMZRacePartsLayer`, handled by
  `DmzNpcTailColorMixin`.
- Bio-Android/Cell and Frost Demon/Frieza: tail bones embedded in the race body model,
  named `tail1...tail9` or `cola`, handled by `DmzNpcEmbeddedTailColorMixin` during
  `DMZSkinLayer` passes.

With no override, base body colors and active normal/stack form colors remain DMZ-owned.
The `saiyanTail` boolean controls the separate Saiyan tail; Cell and Frost Demon tails are
part of their body models.

### Forms and duplicate-form IDs

`DmzNpcActiveFormMixin` returns the exact group/form-resolved `FormData` from the current
NPC render context. This matters because `supersaiyan/supersaiyan4` and
`oozaru/supersaiyan4` have the same form ID but different models.

`NpcProfileSavePacket.preserveRuntimeState` prevents an appearance-editor draft from
clearing the server's committed form/stack state before a transform action is processed.

### Halo

`NpcHaloLayer` uses DMZ player-like head-local placement: Y `-0.75`, outer radius `0.25`,
without the previous extra `1.25` scale.

## NPC wand UI

- Main DMZ screen and Appearance screen use `NpcColorPicker` instead of the raw CNPC
  `SubGuiColorSelector`.
- `NpcColorPicker` adds a clipped circular position marker, an inner selected-color dot,
  and a live `Selected #RRGGBB` swatch/readout.
- Appearance > Body contains a tail color picker plus Race/Inherit. Race/Inherit clears the
  override rather than copying the current body color.
- Appearance > Hair/Aura contains the visual preview and the aura/effect controls.

## Scripting surface

The global is installed as `XenoPixels`. The authoritative implementation is:

`src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcXenoScriptApi.java`

The co-owner-facing guide generated with this handoff is:

`wiki/CustomNPCs-XenoPixels-Scripting-CoOwner.md`

Do not add method names to documentation unless they exist as public methods in
`NpcXenoScriptApi`.

## Verification status at handoff creation

Verified after the final native aura, embedded-tail, picker and documentation changes:

- `gradlew compileJava --offline --console=plain` passed.
- `gradlew build --offline --console=plain` passed, including 147 JUnit tests.
- A fresh `runClient` smoke reached an integrated world and logged the local player joining.
- The fresh runtime log contained no `Mixin apply for mod xenopixelsmod failed`,
  `InvalidInjectionException`, `InjectionError`, `DmzNpcAuraScaleMixin`,
  `DmzNpcEmbeddedTailColorMixin`, or `DmzPlayerRendererAccessor` error entries.

Still required is manual visual inspection of actual Saiyan, Bio-Android and Frost Demon NPCs.
The automated startup proves the mixins apply and the game reaches a world, but it cannot prove
that aura dimensions, tail pixels, or color-picker presentation look correct on every model/form.

### Final 2026-09-01 regression fixes

- `NpcFullDmzRenderer.visualProfile` must copy the authoritative packet race, active normal
  form, aura color and aura scale in addition to the editable visual-options compound. Without
  those fields, `NpcAuraResolver` resolves the transformed NPC as a base Human and the active
  form cannot provide its native aura layers, color, sparking or lightning settings.
- `DmzNpcAuraScaleMixin.xenopixels$useNpcLightningColor` is a `@ModifyArg` handler and must
  accept only the modified `String`. The previous handler included all target-method arguments;
  Mixin rejected the entire `AuraRenderer` mixin at runtime with `InvalidInjectionException`,
  disabling NPC native aura/lightning hooks. The fixed mixin stores the NPC UUID in a
  thread-local, uses the resolved NPC lightning toggle for both DMZ form checks, supplies its
  color, and clears the context at return.
- DMZ's player-only Soul Punisher, Fake Moon and Cooler Nova setup methods already call
  `Level.addFreshEntity`. `NpcKiAttackDispatcher.releasePlayerAttack` must not add those
  projectiles a second time; doing so produces `UUID of added entity already exists` and breaks
  NPC ki attacks. Generic `kiblast`/`kiwave` AI attacks are intentionally not resource-gated,
  while named techniques retain DMZ energy costs and cooldowns.
- Saiyan tail tinting is applied at the final `RenderBufferUtil.packColor` call for the
  `tailenrolled` bone. Embedded Bio-Android/Frost-Demon tail tinting covers every DMZ skin pass
  while the NPC render context is active, not only one renderer caller.

## Useful verification commands

```powershell
.\gradlew.bat build --offline --console=plain
.\gradlew.bat runClient --offline --console=plain
rg -n "Mixin apply for mod xenopixelsmod failed|InvalidInjectionException|InjectionError" run/logs/latest.log
```

When manually testing, use exactly one FULL-mode NPC aura and confirm:

- no second billboard aura appears;
- Display sizes below and above `5` scale both the model and native DMZ aura together;
- form model scaling still affects the aura;
- primary/secondary colors and sparking/lightning toggles update;
- Race/Inherit tail color follows base, form and stack changes;
- custom tail color affects Saiyan, Bio-Android and Frost Demon tails only.
