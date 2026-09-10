# Changelog

Version-bound history for commits reachable from [`origin/1.21.1`](https://github.com/AgentMelinda/XenoPixelsNetwork/tree/1.21.1). Detailed pages preserve exact Git ranges and commit subjects; summaries are based on repository diffs rather than commit titles alone.

## History notes

- `v0.0.5` through `v0.1.6` are the **Minecraft 1.20.1 / Forge** history inherited by the current branch.
- `v0.1.8-1.21.1` aliases `v0.1.6`; no `v0.1.7` tag exists, and the alias still contains the `0.1.6-1.20.1` build.
- The actual **NeoForge 1.21.1 / Sable port** is commit `aeb771d` in the `v0.1.9` range.
- Untagged local work is not attributed to a released version.

## Unreleased — neon DragonMineZ character screen, Zanzoken disguise fixes

### Changed

- **The themed DragonMineZ menus and HUD are now the default.** `dmzMenuMode` ships as `theme`
  instead of `stock`, so DragonMineZ's own screens — its widgets, scrolling, packets and validation
  — come dressed in Xeno chrome out of the box, and the lock-on, radar and four scouter atlases
  follow the same setting. Config version 11 moves anything written before it; a mode chosen at 11
  or later is kept, so nobody who picks a mode is overridden. Every other route is still one command
  away: `/xenohud menus stock` for DragonMineZ untouched, `screen` and `neon` for the two rebuilds
  of the character page.

- **`/xenostats` — DragonMineZ stats past DragonMineZ's cap, and control of that cap.**
  - `/xenostats limit <1000-2147483647>` raises the per-stat ceiling, `limit off` lifts it as far as
    DragonMineZ can physically hold, `limit dmz` hands the setting back to DragonMineZ's own config
    (the default — a server that has not asked for this keeps DMZ's exact behaviour).
  - `/xenostats set <players> <str|skp|res|vit|pwr|ene|all> <value>`, `add` for a delta (saturating,
    so a large `add` lands on the ceiling rather than wrapping negative), and `get <player>`.
  - The writes are DragonMineZ's own: every value goes through `Stats#setStat`, so DMZ's stat-change
    events fire, its attributes are reapplied and its clamp still runs — it just has a higher number
    to clamp to. Nothing is written behind DragonMineZ's back, so a value set here survives a reload
    exactly as one set with DMZ's own command does.
  - **2,147,483,647 is a wall, not a policy.** DragonMineZ stores each stat in an `int` field on
    `Stats`, so nothing either mod configures raises it; `limit off` says so rather than implying
    "unlimited".
  - Implemented as one `@ModifyReturnValue` on `GeneralServerConfig$GameplayConfig#getMaxValue()`,
    the single getter every DMZ cap funnels through — `Stats#clampStatValue` (the setters),
    `StatsData#getConfiguredMaxValue`, `clampStatToConfiguredMax`, and the `+` button's budget in
    `getMaxAllowedIncreaseForStat`.

- **`/xenopixels npcprofile combat`** — sets `punchable` / `knockable` on the NPC you are looking at
  directly on the server's copy of the entity, and with no argument reports whether a profile is
  actually stored there. Added as the way to tell an editor-path problem from a storage problem when
  a flag does not survive a restart: the editor route is client-driven, and a parsed profile cannot
  distinguish "no profile stored" from "profile stored with defaults", because both flags default to
  true when absent.

### Fixed

- **NPC quest and dialog rewards ran nothing on a server with command blocks disabled.** My NPCs'
  `EspiUtilServer.runCommand` returns "Cant run commands if CommandBlocks are disabled" *before* it
  substitutes `@dp` and before it dispatches, so a reward of `xenopoints add 5000 @dp` silently did
  nothing. This was never a difference between the NPC mods — CustomNPCs carries the identical
  guard. The real fix is `enable-command-block=true` in `server.properties`; for servers that want
  command blocks to stay off, the new `npcCommandsIgnoreCommandBlockSetting` (default **off**,
  `/xenoserver set npccommands true`) answers that one check differently for the NPC command path
  only, leaving real command blocks as disabled as the server owner set them. Off by default because
  NPC commands run at permission level 2 — or 4 with My NPCs' own `NpcUseOpCommands` — so it is the
  operator's call.
- **`{RefPlayer}` is now rewritten in dialog commands too, not just quest rewards.** Five My NPCs
  classes run NPC commands and the rewrite covered exactly one of them; a dialog option is the other
  route a server actually uses to hand out points.
- **A Zanzoken ring no longer collapses on the first hit.** Every image had one point of health and
  *any* removal of *any* image dispersed the whole ring, so with `ZanzokenConfusion` now drawing
  attackers onto the images, the first mob swing ended a ten-second disguise instantly. Only a
  player can destroy an image now — that is the read the technique is built around, and it still
  resolves the whole trick — while a mob's swing, splash damage and the environment leave it
  standing. Losing an image any other way re-homes whoever was hunting it onto another image instead
  of handing them back the real fighter, and the ring ends when nothing is left standing.
- **Stat values just under a unit no longer read as a thousand of the last one.** `StatText.format`
  printed 999,999,999 as `1000.00M`, so the themed panel showed `1000.00M` where the stock panel
  beside it showed `999,999,999` — it looked like the two panels disagreed about the number. Values
  that round into the next unit now roll into it (`1.00B`, `1.00M`), which affects the themed
  multiplier column, the neon screen and the first rebuild alike.
- **The dodger's own body now fades with their Zanzoken images.** Every copy in the ring dimmed as
  it aged while the real player rendered fully solid in one of the slots, so the one body an
  attacker had to pick was the one that announced itself. The real body now draws through the same
  alpha curve and the same configuration as the images, so all of them dim together. The copies and
  the HUD portrait are excluded, so neither double-fades.
- **Zanzoken now actually confuses AI for as long as the ring stands.** The images were marked as
  standing for `zanzokenAfterimageTicks` (40 by default) when the key was pressed, and nothing
  re-marked them when a landed read put the ring up for `zanzokenRingTicks` (200). The mark
  therefore expired four fifths of the way before the ring did, and vanilla mobs, DragonMineZ saga
  enemies and CustomNPCs / My NPCs went back to tracking the real body through a ring that was still
  plainly on screen. The mark now covers whichever duration is longer.
- **Attackers already swinging at the dodger are now moved onto an image.** Only target
  *acquisition* was blocked before, so anything mid-fight simply kept hitting the real body — which
  is most of what is attacking you when you press the key. Each attacker within 32 blocks that had
  the dodger is pointed at one of the images instead, drawn independently so a crowd does not
  converge on one body. Redirecting rather than dropping keeps them fighting rather than stunned,
  and it resolves itself: striking any image disperses the ring.
- **Every DragonMineZ lock-on naming the dodger is redirected**, not just the one belonging to
  whoever swung. A second hunter standing off to the side kept a marker on the real body, and one
  correct marker tells the whole room which body to hit.
- **The disguise now ends with the bodies.** Striking an image drops the whole ring early, but the
  "images are standing" mark ran to its full lifetime regardless, leaving a fighter who had already
  been found untargetable for the remainder.

### Added

- `/xenohud menus neon` opens a second rebuild of DragonMineZ's character page, built from
  `dragonminez_our_style_clean_example_dimensions_2.zip`: whole-slab INFORMATION and STATISTICS
  panels, the top nameplate, three orb icons, six captioned navigation buttons, and the live
  DragonMineZ character rendered inside the scan ring between the panels.
- `tools/gen_dmz_neon_atlas.py` builds that screen's atlas from the vendored bundle under
  `tools/source/dmz_our_style_v2/`. Because the panels are single slabs with their rows drawn into
  the art, the generator also *measures* the row interiors off the art and emits them as constants,
  and samples the readout palette from the bundle's own reference render rather than approximating
  it. It writes a contact sheet and an anchor overlay so a bad crop or a misplaced row is visible
  without launching the game.
- `tools/preview_neon_screen.py` composites the screen from the generated atlas using the same
  layout arithmetic the screen uses, on DragonMineZ's guaranteed 320x240 canvas, so an overflowing
  panel or a colliding readout is caught before a client boot.

### Notes

- Nothing existing changed behaviour: `stock`, `theme` and `screen` all work exactly as before, the
  shipped default is still `stock`, and the first rebuild (`XenoDmzStatsScreen`, from the older HD
  kit) is untouched. The two rebuilds are meant to be compared in play.
- The neon rebuild currently covers the character page. The other five menus still open
  DragonMineZ's own screens in Xeno chrome, so nothing behind V is unreachable.
- Stat spending still sends DragonMineZ's own `IncreaseStatC2S`; the seventh row in the art is the
  training-point total, which has no DMZ packet, so its `+` is drawn dimmed and does nothing.

## v0.3.4-1.21.1 — 2026-09-09 — themed DMZ controls and HUD assets

### Fixed

- The skills interaction mixin now uses DragonMineZ's exact `SkillsMenuScreen` receiver for both
  wrapped superclass calls, preventing the MixinExtras signature failure and follow-on verifier
  crash reproduced during client startup.
- Quest-completion compatibility now reads the inherited packet player from the real My NPCs or
  CustomNPCs packet superclass instead of shadowing a field that is not declared on the target.
- Xeno-themed menu buttons now use the approved exact-UV cyan, gold, and red atlases from
  `dragonminez_our_style_full.zip`, replacing the remaining green stock rows and white controls.
- Lock-on, radar, and blue, green, purple, and red scouter HUD textures follow the existing Xeno
  menu-theme setting. Stock mode continues to use DragonMineZ's original resources.

## v0.3.3-1.21.1 — 2026-09-09 — Xeno DMZ menu interaction polish

### Fixed

- DragonMineZ increment buttons now use the supplied Xeno `+` glyph and are painted into DMZ's
  actual `10x10` normal/hover UV cells, so the right side is no longer cut off in the character or
  settings screens. The replacement character screen shares one render/click rectangle and adds
  cyan hover feedback.
- The skills screen's three compact information regions use label-free dark Xeno frames instead of
  falling back to DragonMineZ's green stock panels, while unrelated `menusmall.png` UVs stay intact.
- Hidden, scissored skills category tabs can no longer steal clicks or hover from the visible list.
  Skill rows now remain interactive through the level column, stop before the scrollbar, and show
  the selected row in gold with cyan hover feedback.

## v0.3.2-1.21.1 — 2026-09-09 — sharp DMZ menus and stable Zanzoken ghosts

### Fixed

- Zanzoken afterimages no longer remap name-tag font buffers to DragonMineZ entity translucency.
  Only the compatible `NEW_ENTITY` vertex format is replaced, preventing the confirmed client crash
  reporting missing `UV1` and `Normal` elements. Ghost rendering now also restores its pose stack
  through `finally` when an entity renderer fails.
- The rebuilt DragonMineZ character screen now uses DragonMineZ's integer-scaled virtual UI and
  matching mouse coordinates instead of rendering directly in fractional screen space.
- The rebuilt character atlas now keeps supplied PNG artwork at four times its logical draw density
  and records separate source and destination dimensions, avoiding the permanently blurred
  pre-shrunk sprites used by the previous atlas.
- Xeno replacement and themed DragonMineZ menus use the transparent dark background without
  Minecraft's post-process world blur. Stock DragonMineZ menu mode is unchanged.

## v0.3.1-1.21.1 — 2026-09-09 — controllable NPC combat, migrated content and HUD fixes

**Verification status:** JUnit suite **448 tests, zero failures/errors**. The rebuilt DMZ menu
atlases pass their generator checks without runtime blur metadata, and the client, server, sources
and jar-in-jar release artifacts build against DragonMineZ 2.1.3.

### Fixed

- The CustomNPCs and My NPCs DMZ wand pages now save independent Knockable and Damage controls.
  Damage-off cancels all incoming damage, while Knockable-off blocks vanilla, DragonMineZ and
  XenoPixels combat displacement without freezing ordinary NPC navigation. Both flags default on
  for existing profiles and are available to both scripting APIs.
- Quest, dialog and script world data now goes through a conservative CustomNPCs-to-My NPCs
  converter. It preserves IDs and structure, rewrites only the known resource/package namespaces,
  normalizes XenoPoints/DMZPoints player reward tokens, writes atomically, and never replaces an
  existing My NPCs file.
- The right-side inventory effect rail is now the sole inventory renderer for active effects;
  Minecraft's competing effect panel is canceled so the same effects are not drawn twice.
- Sparking charge duration and cooldown settings are included in server-config synchronization,
  keeping client feedback aligned with the server's configured timers.
- The client afterimage command now also rejects server-sent Zanzoken body copies and clears
  existing mirages immediately instead of disabling only locally predicted trails.

- Profiled CustomNPCs and My NPCs fighters now use their DMZ/Xeno profile as the sole combat stat
  source when `npcDmzStatsAuthoritative` is enabled. Native melee/ranged damage, knockback, regen,
  explosion strength and all four native resistance channels are neutralized, so a saved
  `Resistance=2.0` can no longer make a DMZ-profiled NPC immune to attacks or ki blasts and native
  melee damage is no longer added a second time.
- Both NPC mods' scripted tick events now run at the configurable `npcScriptTickInterval` (default
  every tick) instead of being hard-wired to once per ten ticks. The original ten-tick invocation
  is replaced rather than duplicated. Quest completions also request immediate persistence/client
  synchronization after rewards and next-quest assignment.
- `/xenopoints` and DragonMineZ's `/dmzpoints` accept CustomNPCs/My NPCs `@dp`, `{RefPlayer}` and
  formatted display-name targets. Literal dialog-player tokens resolve either the real command
  source or the real player at the NPC fake player's command position, and both commands send the
  normal DragonMineZ resource sync immediately.
- Zanzoken stationary body copies now carry alpha through translucent textured render types. Fade mode
  1 starts semi-transparent and fades, mode 2 starts solid and fades, and mode 3 stays at a
  configurable semi-transparent alpha before vanishing. Mode and alpha are synchronized server
  settings, while the existing `zanzokenRingTicks` controls the live ring copies' lifetime.
- DragonMineZ and Xeno status effects no longer stack as oversized vanilla cards beside the
  inventory. They share a compact, spaced icon rail with DragonMineZ/Xeno color accents and hover
  tooltips, clamped inside narrow screens.
- Corrected the DragonMineZ aura-layer mixin callback descriptor to use the renderer's real
  `BakedGeoModel`, `RenderType`, `MultiBufferSource` and `VertexConsumer` parameters, fixing the
  confirmed `InvalidInjectionException` from the runtime log.
- Rebuilt the supplied DMZ menu assets at 4x with no texture blur flag and aspect-preserving
  contain fitting, so supplied frames are no longer cropped to mismatched stock rectangles.
- Replaced the DMZ menu showcase/mockup panels with the bundle's genuinely clean Xeno panels, so
  baked labels such as `Clean (Empty)`, `ELEMENTS (EXTRA)` and duplicate menu titles cannot overlap
  live menu content. The quest atlas now preserves DMZ's node sprites and the skills screen keeps
  DMZ's functional three-region small-panel sheet instead of stretching one decorative strip over it.
- Tightened the replacement stats screen layout: the section header no longer covers the character
  rows, all six statistic rows fit inside their panel, and the detached decorative color orbs were removed.

### Added

- Configurable `npcDmzStatsAuthoritative`, `npcScriptTickInterval`, `zanzokenGhostFadeMode` and
  `zanzokenGhostAlpha` server keys.
- Completed and retained Claude's CustomNPCs-to-My NPCs live entity, world-data and clone migration
  work, including non-destructive conversion and its existing regression coverage.

## v0.2.4-1.21.1 — 2026-09-09 — My NPCs port, world migration and the DMZ menu rebuild

**Verification status:** JUnit suite **440 tests across 77 classes, zero failures/errors**, and all
four asset generators (`gen_dmz_hd_atlas`, `gen_dmz_menu_themes`, `gen_bt3_menu_atlas`,
`gen_bt3_hud_atlas`) report their output up to date. Client, server and jarJar artifacts build.

Not yet exercised in a play session, and worth doing in this order: the world migration against a
**copy** of a server world (it is one-way, and it cannot recover NPCs from a chunk that has already
loaded and re-saved without CustomNPCs); the rebuilt character screen behind `/xenohud menus screen`;
the aura holding a steady size while taking hits; and NPC attack animations no longer competing with
the vanilla swing. The Xeno HUD, its overlay and the modernunified renderer were deliberately left
untouched throughout.

### Fixed


- CustomNPCs NPCs already placed in a world now survive the move to My NPCs. They are stored in
  chunks as entities with `id: customnpcs:customnpc`, and with CustomNPCs uninstalled vanilla cannot
  resolve that id: it logs "Skipping Entity with id" and **discards the entity**, permanently once
  the chunk saves again. `EntityType.by` - the one point every load path funnels through - now
  rewrites that id first, so My NPCs loads them instead and each chunk migrates once and stays
  migrated. Unlike the clone converter this keeps the CustomNPCs-only keys: a clone is a template
  being re-saved, a live entity is somebody's actual NPC.
- Quest and other CustomNPCs world data is copied across to My NPCs on server start. No conversion
  was needed: both mods write the same folder layout, and comparing a world opened under each, the
  dialog files differed only in a timestamp, the player data was byte-identical, and no world-data
  file carries a `customnpcs:` namespace at all. Clones are the exception and are converted on the
  way. The migration never overwrites a file My NPCs already has and never modifies the CustomNPCs
  folder, so re-running it is a no-op and reinstalling the old mod undoes it. Controlled by
  `migrateCustomNpcsWorldData` (default on) and runnable on demand with `/xenopixels migratenpcs`.

- Auras no longer jump up and down. The stat-driven scaling keyed its power-up ramp off the
  "who is being drawn" marker that only DragonMineZ's aura *layer* sets, while `getAuraScale` is
  also called from the world aura, the first-person aura and the character-screen preview - so the
  height multiplier applied on some frames and not others, and could be attributed to whichever
  entity had been drawn last. The ramp is now keyed on `StatsData.getPlayer()`, the object DMZ hands
  the method itself, which also makes the scaling work in first person where it never did. The
  bookkeeping moved into a `RampTable` with no Minecraft types and eight tests, since ownership is
  the part that broke rather than the curve.
- `/xenohud menus` defaults to `stock` again, so DragonMineZ's menus behave exactly as DMZ ships
  them. The rebuilt character screen and the theming both remain; `/xenohud menus screen` or `theme`
  brings either back.

- The DragonMineZ character screen is rebuilt from the master bundle's HD element kit and is now what
  `V` opens by default. Separate panels, header shells, row shells, navigation buttons and icons are
  each drawn at their own proportions instead of being forced through DMZ's 141x213 rectangle, with
  the numbers still read live from `StatsData`, the `+` buttons still sending DMZ's own
  `IncreaseStatC2S`, and the multiplier column in the bundle's `#FECC22`. `/xenohud menus` still
  offers `theme` and `stock`.
- Every piece of that screen is editable through the existing elements editor, as a fifth surface
  beside Panel, Chips, Ki Menu and Party - position, scale, colour, bold, font and per-part reset for
  all 21 parts, in its own config file so nothing in the HUD's layout moves.
- The other five menus now theme from the bundle's named per-menu panels rather than rectangles cut
  out of a full-screen mockup, which is what previously cost 20-38% of several panels. A slot whose
  art would lose more than 35% to the aspect crop is now left as DragonMineZ drew it - the redesign's
  header bars are about 2.2-2.9 wide for their height against DMZ's 5.1 strip, and no crop makes that
  anything but a smear - and the generator reports every skip.
- The new atlas generator refuses to build if a source asset is fully transparent, and writes a
  contact sheet of every sprite at the size the game samples it. Both earn their place: all six
  `clean_icon_*.png` in this kit are empty and the clean orbs are hollow rings, which is exactly how
  six blank navigation buttons shipped from the previous bundle.

- Attacking NPCs no longer look like their limbs snap. Both NPC mods' melee goal calls
  `swing(hand)` and then `doHurtTarget`, and we hook the second of those to start the configured
  attack clip - so the vanilla arm swing and our clip landed on the same model in the same tick, a
  DragonMineZ punch playing underneath a humanoid arm swing. The swing is now suppressed for exactly
  those NPCs whose attack we animate ourselves, and left alone for every other entity in the game,
  which keeps it as the NPC mods' own melee animation where nothing better exists.

- CustomNPCs clones can now be used in the My NPCs cloner. The two formats are near-identical - real
  saves share 170 of ~190 keys, and only two values in the whole tree carry the mod's own namespace -
  so `NpcCloneConverter` rewrites `customnpcs:` to `mynpcs:` by walking the tag tree (leaving
  `dragonminez:` and `minecraft:` ids alone), drops the 17 keys the fork has no field for, and seeds
  the 7 it expects at the values a native clone carries. Anything it does not recognise is kept
  rather than dropped, so a My NPCs update costs fidelity, not data. Pasted files convert on read
  and the originals are never rewritten; `/xenopixels importclones [tab]` makes it permanent by
  writing through My NPCs' own saveClone. Covered by 13 tests against real clone files from both
  mods.
- An NPC with no usable attack animation now performs a plain vanilla swing. Both authored paths can
  be unavailable at once - the Gecko addon is CustomNPCs-only and off the runtime, and DragonMineZ
  clips need FULL appearance mode - which previously meant an attacking NPC showed nothing at all.

- `/xenopoints` now understands the NPC mods' own player tokens. CustomNPCs and My NPCs rewrite
  `@dp` to the interacting player's *display name*, and skip the rewrite altogether when no player is
  in scope, so what reached the command was either a literal `@dp` or a nickname - neither of which
  vanilla's entity selector can resolve, and both of which failed. The command now falls back to
  resolving `@dp` and `{RefPlayer}` against the player it is running for, and matches a plain name
  against usernames first and display names second, ignoring any formatting codes baked into them.
  Vanilla selectors are untouched: brigadier only reaches the fallback when the selector fails to
  parse, so `@a`, `@p`, a username and a UUID all take exactly the path they did before.

- The NPC integration now runs against My NPCs (`mynpcs`), the renamed CustomNPCs fork, which is on
  the dev runtime in its place. My NPCs is CustomNPCs with `noppes.npcs` renamed to `espi.mynpcs`,
  and 41 of the 44 types this mod touches kept their names, so most touchpoints were widened rather
  than duplicated: a new `NpcTypes` resolver looks each class up under whichever root is installed,
  and eight mixins that name no NPC type at all moved to the `customnpcs || mynpcs` gate so one copy
  serves both. Only the parts with real compile-time coupling are twinned - the seven NPC screens,
  which extend `GuiNPCInterface2`, the scripting bridge, and ten mixins.
- The CustomNPCs side is parked, not removed. Its jar stays on the compile classpath so that code
  keeps compiling, every piece of it remains gated on the `customnpcs` mod id, and restoring it is a
  matter of swapping which NPC jar is installed. The CNPC GeckoLib addon is compile-only for now:
  18 of its classes reference `noppes/npcs` and none reference `espi/mynpcs`, so it cannot load
  beside the fork, and its compat mixin already required both mods.

- The DragonMineZ menu rework is parked: `/xenohud menus` now defaults to `stock`, and any existing
  config is migrated to it, so the V menus behave exactly as DragonMineZ ships them. Nothing was
  removed - both reworks, their generated art and their generators are all still in the tree, and
  either comes back with `/xenohud menus theme` or `/xenohud menus screen`. While parked the texture
  hook costs a single field read per draw rather than a thread-local lookup.

- The six DragonMineZ navigation buttons were themed as empty frames. The redesign bundle ships each
  button twice and the `clean_` half has no icon in it at all; the generator now takes the glyph from
  the `example_` half, cuts off its caption pill, and writes a contact sheet of every cell and hover
  state so an empty button is caught without launching the game.
- Content no longer overruns the themed panels. The redesign art paints its own furniture inside the
  frame - row slots, chevrons, a painted STATS button - which DragonMineZ then drew its real widgets
  on top of, at coordinates that had nothing to do with it. Panel interiors are now repainted with
  their own background before the frame is kept, so DMZ's widgets sit on a clear field the way they
  do on stock art. Shipped textures dropped from 5.2 MB to 3.2 MB as a side effect.
- The nearby-party card is now a full editor surface like the Panel, Chips and Ki Menu HUDs: the
  portrait, name, level, leader star, form, sparking label and all three gauges can each be moved,
  scaled, recoloured, bolded and re-fonted, and reset individually.
- Auras now grow with the character and tower while powering up. Size comes from a logarithmic curve
  over battle power, so a billion-power character reads as far larger than a fresh one without
  filling the sky, and while `isActionCharging` is set a ramp lifts the aura into a tall column and
  eases it back down afterwards. Both read from the `StatsData` DragonMineZ already hands its own
  sizing method, so every player's aura is affected, not just your own. Tunable live with
  `/xenohud aura` and switchable off back to DMZ's own sizing.

- The themed DragonMineZ menus are no longer blurry. They were generated at DMZ's own 256x256, so the
  big panel was stored as 141x213 texels and stretched across as many as 564x852 physical pixels at
  high GUI scales. They are now generated at 4x with linear filtering requested through a
  `.png.mcmeta`, which is sound because every `GuiGraphics.blit` overload normalises UVs by the
  texture size its caller passes rather than the size of the file. Art is also resampled once instead
  of twice, and how much each sprite loses to its aspect crop is now reported instead of silent.
- The DragonMineZ menu icons are themed for the first time. The six navigation buttons and the stat
  `+` were still stock art and were in fact unreachable: buttons draw through the six-argument
  `blit`, which reaches neither overload the theme mixin hooked. The mixin now hooks the single
  terminal blit every overload funnels into, and generated `menubuttons.png` /
  `characterbuttons.png` supply the redesigned icons with hover states.
- The stat multiplier column now uses `#FECC22`, sampled from the redesign bundle's own multiplier
  glyphs, instead of a hand-picked gold, and writes whole multipliers as `x1` rather than `x1.0` to
  match that art. Both the themed panel and XenoPixels' own stats screen read the one constant.

- The themed DragonMineZ stats panel now shows each stat's multiplier as its own gold column instead
  of DMZ's inline `x1.5` suffix, and shows `x1.0` on unboosted rows rather than hiding them, so all
  six rows read as a column. The values are still DMZ's own `getTotalMultiplier`; only the layout
  changes, and stock rendering is untouched.
- XenoPixels combat can no longer launch DragonMineZ masters. The existing master guard only caught
  knockback that goes through vanilla `LivingEntity.knockback`, which our BT3 charged kicks, launch
  arcs, rush finishers and transform shockwaves bypass by writing velocity directly. Those now route
  through one chokepoint, governed by the new `protectMastersFromCombatKnockback` server key
  (default on, separate from `protectDmzMasters`). Saga quest enemies are unaffected.
- The DragonMineZ menu rework is switchable again with `/xenohud menus <stock|theme|screen>`. It had
  shipped always-on with no way back to DMZ's own screens. `theme` (the default) keeps DMZ's real
  screens in Xeno chrome; `stock` restores them untouched; `screen` opens XenoPixels' own stats
  screen, which is restored alongside the theming rather than replaced by it.

## v0.2.3-1.21.1 — 2026-09-08 — BT3 controls, scripting and saga recovery

**Verification status:** JUnit suite **330 tests across 61 classes, zero failures/errors**. The
client reached an integrated world with Controlify and YetAnotherConfigLib installed, registered all
42 Xeno gamepad bindings, and detected an Xbox controller. The new common and optional Controlify
mixins applied without injection errors. A dedicated server also reached
`Done` after the client-only pad jars were removed. In-world button-by-button combat and building
acceptance still requires a player session. Pink aura streaking during Multi-Form remains unresolved.

### Fixed

- The six DragonMineZ menus opened through `V` now use screen-specific redesign assets from
  `new_menus_redisgn.zip` without replacing DMZ's real screens, packets, widgets, scrolling, or
  validation. Character, Skills, Quests, Minigames, Party/Server, and Settings keep their stock
  behavior while XenoPixels remaps only their verified menu texture calls.
- The packaged NeoForge dependency minimum is lowered from `21.1.238` to `21.1.233`; development
  and compilation continue to use NeoForge `21.1.238`.
- DragonMineZ saga combat quests now audit their quest-spawned enemies after both normal start and resummon. Missing enemies are recreated with DMZ-compatible ownership, objective, party-scaling, difficulty, AI, health, damage and transformation metadata without duplicating valid spawns.
- CustomNPCs quest rewards using `xenopoints add 5000 {RefPlayer}` now work on dedicated servers. The compatibility hook narrowly maps `{RefPlayer}` to CustomNPCs' verified completing-player token before its existing command dispatch; unrelated commands and valid selectors remain unchanged.
- XenoPixels status effects now render in their own vertical rail to the right of the player inventory instead of sharing the vanilla effect list or colliding with inventory tabs. The rail wraps into additional columns on short screens and retains hover names, levels, and durations; other mods' effects remain unchanged.
- Xeno Rush Left, Right, Breaker, and Finisher are unlocked without being automatically inserted into DragonMineZ's Alt/Ctrl technique slots; manually unbound slots now remain empty across login and reload, including when the legacy auto-equip config was enabled.
- Controlify arbitration now suppresses only physical inputs owned by BT3 mode instead of blanket-blocking built-in actions; normal movement/camera values remain available. Guard now owns sneak suppression, and LT+Y exclusively triggers charged kick.
- Multi-Form clones now mirror successful DragonMineZ ki-wave releases on the same server tick using the real technique data and charge multiplier; obsolete melee-charge packets and autonomous charged-wave behavior were removed.
- Replacing a Zanzoken ring now disperses the previous ring, and the player's ring landing searches for an open collision-safe slot before falling back to the current position.
- Fixed a client crash at the title screen. `resetCharge()` sent the clone ki-charge sync packet
  without checking for a connection, from the client-tick branch that runs every tick while no
  world is loaded; `PacketDistributor.sendToServer` rejects a null connection. **This crash is
  present in the code tagged `v0.2.1`,** which was never pushed. Every packet send in
  `Bt3CombatClient` now goes through one connection-guarded method, so a new call site cannot
  reintroduce it, and the charge sync no longer fires when there was no charge to clear.

### Added

- Adds a server-authoritative `X X X -> A` cinematic rush with universal, race, and iconic-form GeckoLib profiles; four gameplay impacts use fixed server ticks while animation keyframes drive cosmetic sound, particles, and controller rumble.
- Sparking now requires a five-second full-ki Max Power charge: the eight ki segments turn red and convert to gold one by one, reset immediately if charging stops, and activate Sparking when the final segment lights.
- Adds `/xenodmz saga diagnose [player]` and `/xenodmz saga respawn [player]` for operator-visible saga spawn diagnostics and duplicate-safe manual recovery.
- Adds `/xenopoints <add|set|remove> <amount> <targets>` as a command-block and CustomNPC quest friendly counterpart to DragonMineZ training points. For example, `/xenopoints add 500 @p` updates the nearest player's real DMZ points and synchronizes the resource HUD.
- Adds `/xenoki clear all` and `/xenoki clear radius <blocks>` for operators to remove loaded
  DragonMineZ KI attacks and orphaned explosion visuals globally or around the command source.
- Gamepad support through [Controlify](https://modrinth.com/mod/controlify), laid out to match
  Budokai Tenkaichi 3 and written in Xbox button names: melee, ki blast, dash and guard on the
  face buttons, ki charge and lock-on on the left trigger and bumper, fly and descend on the right
  pair, transform on the right stick, and chase/backstep/Sonic Sway on the remaining chords and
  d-pad inputs. Guard plus a left/right stick flick directly drives Xeno's existing side vanish.
- Adds persistent Normal and BT3 controller modes as Controlify radial candidates. Normal mode
  restores unmodified Minecraft mining, placing, inventory and hotbar controls; BT3 mode filters
  Controlify's overlapping Xbox defaults while retaining Pause and the radial menu.
- Left-stick click toggles DragonMineZ Search Fly and Combat Fly. Right bumper activates flight
  when needed and becomes ascend while flight is active; right trigger descends without attacking.
- Chorded moves, as in the original: hold ki charge for Z Burst, Ultimate and Sparking; hold
  lock-on for Zanzoken, Multi-Form and Hakai. Holding a modifier withholds the plain move, so one
  button never fires two.
- Controller state is read from Controlify's verified raw current/previous state for BT3 action detection and analogue flight axes. Existing DragonMineZ key mappings remain the compatibility bridge for actions that expose no direct public invocation API.
- The eight DragonMineZ technique slots are offered to Controlify's radial menu. They are unbound
  by default: their keyboard bindings are Alt+1-4 and Ctrl+1-4, chords no gamepad can produce, and
  a radial keeps working if the number of slots grows.
- The pilot seat reads the left stick as a real analogue stick, assigning its position rather than
  running it through the key ramp, and hands control back to the keyboard on release.
- Controlify is optional. Everything touching it lives in `client/pad` and is reached only through
  Controlify's own `ServiceLoader` entrypoint, so with Controlify absent none of those classes is
  loaded and input behaves exactly as before. A `padEnabled` client config flag switches the layer
  off without uninstalling anything.

See [`docs/releases/v0.2.3-1.21.1.md`](docs/releases/v0.2.3-1.21.1.md) for release scope and verification.

## v0.2.1 — 2026-09-07 — Refactor, bug fixes and techniques

### Refactor and bug fixes

First `0.2.x` release. **There was no 0.2.0** — this work carried a `0.2.0` working version but
was never tagged, so it ships as `v0.2.1`.

**Verification status:** fresh existing JUnit suite: **303 tests across 53 classes, zero failures/errors**.
Dependency APIs were checked against DragonMineZ 2.1.3 and mapped NeoForge artifacts. An isolated
dedicated server reached `Done` and answered a status ping, with compatibility warnings; no
player-combat or graphical client acceptance was performed. Pink aura streaking remains
unresolved. See the [validation and staged review ledger](wiki/Techniques-Approved-Plan-Validation.md)
and [continued handoff](CLAUDE_TECHNIQUES_HANDOFF_2026-09-06.md).

- Retains the pending DMZ-faithful punches, combos, custom rush strikes, obstacle-aware chase, Hakai erasure, HUD and compatibility work.
- Separates native weapon/mining input from Xeno fists, preserving configured Attack and disabled-feature fallback.
- Separates "Xeno fists run" from "the native attack is suppressed", so a block under the crosshair no longer switches fists or a charge in progress off, and punching, holding a charge and guarding all leave block breaking intact.
- Unlocks the four custom rush strikes for every player and answers a rush attempted at a target still on the ground.
- Softens the rush and chase camera to a single gentle writer with a deadzone, so it tracks without oscillating or snapping.
- Fades the rush and chase aim assist to nothing inside contact range, where tracking angles diverge and the camera thrashed.
- Fixes Multi-Form + lock-on crash: NpcGeckoAnim.playAttack now guards against non-NPC entities before reflective field access.
- Fixes clone facing: when no target is locked, clones face outward from formation center instead of all staring at the owner's yaw.
- Adds Multi-Form ki-wave synchronization through DragonMineZ's actual server-side attack lifecycle; clones fire on the owner's successful wave release with the same technique tuning and charge multiplier.
- Fixes a chase started from directly above its target climbing away instead of diving onto it.
- Retires the hold-Space and hold-W chase gestures (off by default, still switchable); chase runs from its own binding.
- Holding W through a combo beat launches the enemy and dashes after them on Search Fly, skipping the success roll.
- Resets fall distance and waives fall damage briefly when a chase ends, so ending one at altitude is survivable.
- Gives Xeno rush strikes their own configurable ki cost and cooldown instead of DragonMineZ's power-scaled thousands.
- Ki guidance follows the crosshair rather than auto-acquiring a nearby target; a deliberate lock-on still homes.
- Adds a working Unbind for techniques bound to DragonMineZ slots, which DMZ's own empty-slot path cannot do.
- Adds Zanzoken, the afterimage dodge: a timed read that cancels the hit and rings the attacker with copies of you. You stand in the ring yourself and their lock-on is redirected onto an image, so neither position nor the lock marker identifies the real body; striking any image disperses the ring. Fools players, not NPC AI. Ships unbound.
- Adds Shi Shin No Ken with current-health-conserving split/recall, original collision-aware local pursuit, melee/basic ki/unlocked DMZ strike combat, shared owner resources, protected lock-on targets, and exactly-once power division. Positive split combat awards rate-limited mastery up to 1000; mastery survives save/respawn. Ships unbound; live combat acceptance remains pending.
- Repairs copy refresh timing, owner armor and queued animations, distinct render identity/cache cleanup, and exception-path pose isolation without claiming to resolve shader streaks.
- Makes chase reliable by default (also Dragon Dash's shared roll), preserves explicitly saved randomness/pay-on-attempt, and ships Z-Burst unbound to avoid DMZ's V Stats binding.
- Rejects unsupported NPC ki dispatch before spending and canonicalizes supported IDs; preserves existing administrator-forced chunks when missile tickets expire; accepts the first seat-control frame after server-clock restart.
- Adds a render path giving each copy its own proxy identity, so it can draw with your DragonMineZ appearance and carry its own aura instead of a plain player model. Switchable off (`cloneDmzAppearance`); unverified in game.
- Makes chase acknowledgments, disconnect/context cleanup and temporary flight ownership authoritative and depletion-safe.
- Refreshes and consumes negative-effect transfers at save/clone/native NPC reset boundaries; cured effects must not return on reconnect.
- Preserves party friendly-fire choices on invitations, detects every effective cockpit input field and releases Hakai-owned glow before restart.
- Repairs the Create Propulsion plasma-particle mixin, which never applied: it shadowed fields that live on vanilla `Particle` rather than on the target, so plasma kept colliding with ships.
- Blue combat boxes are illustrative fixed geometry, not authoritative range or collision volumes.

See [`docs/releases/v0.2.1.md`](docs/releases/v0.2.1.md) for scope, executable regressions and runtime limitations.

## Released versions

### [v0.2.3-1.21.1](docs/releases/v0.2.3-1.21.1.md) — 2026-09-08

**1.21.1 / NeoForge.** BT3-style Controlify controls, CustomNPC quest rewards, ki cleanup commands, clone/charge fixes, and DMZ saga enemy spawn recovery.

### [v0.2.1](docs/releases/v0.2.1.md) — 2026-09-07

**1.21.1 / NeoForge.** Refactor, bug fixes, and the BT3-style technique work: left-click ownership, rush strikes, chase, Zanzoken, Shi Shin No Ken and the copy system.
### [v0.1.11](docs/releases/v0.1.11.md) — 2026-09-01

**1.21.1 / NeoForge.** NPC appearance/combat expansion, Hakai, targeting, aerodynamic flight, HUD and compatibility.

### [v0.1.10](docs/releases/v0.1.10.md) — 2026-08-27

**1.21.1 / NeoForge.** Sable flight controls, expanded combat effects, DMZ parties, and My NPCs/CustomNPCs scripting.

### [v0.1.9](docs/releases/v0.1.9.md) — 2026-08-10

**1.21.1 / NeoForge.** Actual port from the inherited 1.20.1 codebase to NeoForge 1.21.1 and Sable, plus ship systems and release fixes.

### [v0.1.8-1.21.1](docs/releases/v0.1.8-1.21.1.md) — 2026-07-26

**actually 1.20.1 / Forge.** Alias of v0.1.6 with no unique commits; it does not contain the 1.21.1 port.

### [v0.1.6](docs/releases/v0.1.6.md) — 2026-07-26

**1.20.1 / Forge.** Major BT3 combat, progression, ship guidance, compatibility, and performance expansion.

### [v0.1.5](docs/releases/v0.1.5.md) — 2026-07-25

**1.20.1 / Forge.** Live DragonMineZ form-stat scaling and administration.

### [v0.1.4](docs/releases/v0.1.4.md) — 2026-07-25

**1.20.1 / Forge.** Cooldown HUD/editor, technique assistance, combat animation integration, and form scaffolding.

### [v0.1.3](docs/releases/v0.1.3.md) — 2026-07-24

**1.20.1 / Forge.** Simplified square portrait presentation.

### [v0.1.2](docs/releases/v0.1.2.md) — 2026-07-24

**1.20.1 / Forge.** Animated HUD snapshots, movable technique UI, and early scoreboard teammate display.

### [v0.1.1](docs/releases/v0.1.1.md) — 2026-07-23

**1.20.1 / Forge.** Expanded technique charge display to 1000%.

### [v0.1.0](docs/releases/v0.1.0.md) — 2026-07-22

**1.20.1 / Forge.** DragonMineZ technique hotbar and charge meter.

### [v0.0.9](docs/releases/v0.0.9.md) — 2026-07-22

**1.20.1 / Forge.** Modern UI Jar-in-Jar packaging.

### [v0.0.8](docs/releases/v0.0.8.md) — 2026-07-22

**1.20.1 / Forge.** Configurable ki overcharge scaling and catalog expansion.

### [v0.0.7](docs/releases/v0.0.7.md) — 2026-07-22

**1.20.1 / Forge.** XenoPixels identity, initial BT3 combat, DMZ HUD, and custom forms.

### [v0.0.6](docs/releases/v0.0.6.md) — 2026-07-22

**1.20.1 / Forge.** Strawberry Senzu and DMZ regeneration diagnostics.

### [v0.0.5](docs/releases/v0.0.5.md) — 2026-07-22

**1.20.1 / Forge.** First reachable baseline: ores, items, recipes, VS/DMZ hooks, wiki, and release automation.
