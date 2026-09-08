# Handoff: DMZ Ki Weapons — Iris/shader compat + NPC toggle support

> Written for: Codex (or whichever agent picks this up next)
> Repository: `XenoPixelsNetwork_qwen` (branch `new2`), Minecraft 1.21.1, NeoForge 21.1.238
> Everything below is either (a) confirmed by decompiling/reading the actual class files this
> session (Vineflower / `javap -p -c -s` against `libs/dragonminez-2.1.3.jar` and the NeoForge
> sources jar, or a full `Read` of this mod's own source), or (b) explicitly marked **UNCONFIRMED /
> NEEDS LIVE TESTING**. Nothing below is guessed from naming conventions. Do not add code that
> calls a method or reads a field that isn't cited with a file path (or class + `javap` signature)
> somewhere in this document — if you need a capability that isn't here, decompile/verify it first,
> the same way this document was built.

## 1. What's being asked for

Two related asks from the user, in this order:

1. **"add ki weapons iris compat to our xenopixels it aint working with shaders"** — DragonMineZ
   (DMZ) has an existing player mechanic called **Ki Weapons**: with the `kimanipulation` skill
   active, a player can materialize an energy blade/claw-lance/scythe in their main hand. The user
   reports this doesn't render correctly when a shader pack (Iris) is active. **This mod
   (XenoPixelsMod) currently has zero code touching ki weapons at all** — confirmed by grepping
   the whole `src/main/java` tree for `kiweapon`/`kimanipulation` (case-insensitive): zero matches.
   So this is a from-scratch compat feature, not a regression in existing code.
2. **"add a way to use and toggle ki weapons and ki weapon types on npcs please"** — extend this
   mod's CustomNPC ↔ DMZ integration (the `FULL` appearance mode, where an NPC is rendered through
   a synthetic DMZ player proxy) so an NPC can also materialize a ki weapon, toggleable and
   type-selectable from both the in-game appearance editor GUI and the CustomNPCs Nashorn script
   API — following the same patterns already used for aura/halo/hair.
3. Follow-up from the user: **the ki weapon's position on an NPC will likely need the same kind of
   fix the aura and hair needed** (NPC-size-aware scale/pivot correction). This has **not been
   observed yet** — no NPC ki-weapon rendering exists yet to observe — see §6.

Asking the user for the exact shader-side symptom (invisible? black/unlit? flickering?) got
**"not sure — apply the same fix pattern used for the aura."** Important caveat spelled out in
§4: the aura's fix and the ki-weapon's likely fix are probably **not** the same code path — verify
before assuming.

## 2. DMZ's Ki Weapon system — confirmed API (decompiled from `libs/dragonminez-2.1.3.jar`)

### 2.1 Entry points off `StatsData`

```java
public com.dragonminez.common.stats.character.Status getStatus();   // StatsData
public com.dragonminez.common.stats.skills.Skills getSkills();      // StatsData
public com.dragonminez.common.stats.character.Character getCharacter(); // StatsData
```
(`Skills` lives in package `com.dragonminez.common.stats.skills`, not `.stats.extras`.)

### 2.2 `com.dragonminez.common.stats.skills.Skills` (backed by inner/sibling class `Skill`)

```java
public boolean hasSkill(String name);
public boolean isSkillActive(String name);
public void setSkillActive(String name, boolean active);
public void toggleSkillActive(String name);
public void registerDefaultSkill(String name, int maxLevel);
public int getSkillLevel(String name);
public void setSkillLevel(String name, int level);
// + save/load/toBytes/fromBytes/copyFrom, not relevant here
```

**Critical, bytecode-confirmed gotcha:** `isSkillActive`/`setSkillActive` look the skill up by
lower-cased name in an internal `Map<String, Skill> skillMap`. If the entry isn't present:
- `isSkillActive` returns `false` (not an exception).
- `setSkillActive` **silently no-ops** (`ifnull → return`).

A synthetic NPC proxy's `StatsData`/`Skills` never went through DMZ's normal player-login skill
registration, so `"kimanipulation"` will **not** be in its `skillMap` by default.
`registerDefaultSkill(String, int)` creates the entry if missing (confirmed idempotent — if the
skill already exists it only touches `maxLevel`, leaving `level`/`isActive` alone, so it's safe to
call every render frame). **You must call `registerDefaultSkill("kimanipulation", <maxLevel>)`
before `setSkillActive("kimanipulation", true)`** or the toggle will do nothing.

The only existing use of `getSkills()` anywhere in this mod's source is
`src/main/java/net/bullettrain/xenopixelsmod/client/combat/TechniqueSlotAssist.java:143` — reads
a real player's `"kicontrol"` skill level for UI gating. It never activates anything and is
unrelated to NPCs. No prior NPC-side skill activation exists anywhere in this codebase.

### 2.3 `com.dragonminez.common.stats.character.Status`

```java
private String kiWeaponType;              // no-arg ctor default: "blade" (verified: ldc "blade"; putfield)
public String getKiWeaponType();          // trivial getter
public void setKiWeaponType(String);      // trivial setter — no validation, no lower-casing
public void validateKiWeaponType();       // see below
```
There is **no `"none"` sentinel written anywhere in `Status` or `KiWeaponHelper`** — `"none"` as
an off-state is purely `DMZWeaponsLayer`'s own rendering convention layered on top of this raw
string (see §2.5, gate #5). `validateKiWeaponType()`:
```java
List<String> types = ConfigManager.getCombatConfig().getKiWeaponTypes();
if (types.isEmpty()) return;
if (kiWeaponType == null || !types.contains(kiWeaponType.toLowerCase()))
    kiWeaponType = types.get(0);   // falls back to the first configured type
```
**UNCONFIRMED:** where/when `validateKiWeaponType()` is actually invoked (on load? on tick? on
network sync?) was not located this session. Don't assume it self-corrects an invalid type string
automatically — verify by tracing `StatsData`'s load/tick path, or just always pass a known-valid
lower-cased type string and avoid relying on this method at all.

### 2.4 `com.dragonminez.common.combat.logic.weapon.KiWeaponHelper`

```java
public static float[] resolveColorForType(String type, float[] fallback);
public static float[] resolveColor(String hexOrNull, float[] fallback);
```
`resolveColorForType` looks up `ConfigManager.getCombatConfig().getKiWeaponConfig(type)`, reads
its `getForcedColor()`, and calls `resolveColor(forcedColor, fallback)`. `resolveColor` returns
`fallback` if the hex is null/blank/`#FFFFFF`, otherwise parses `#RRGGBB` into a `float[3]`.
**No type whitelist exists in code** — the whitelist is data-driven, see §2.5.

### 2.5 `com.dragonminez.common.config.CombatConfig` / `CombatConfig$KiWeaponConfig`

```java
private Map<String, CombatConfig$KiWeaponConfig> kiWeaponsConfig;
public CombatConfig$KiWeaponConfig getKiWeaponConfig(String);
public List<String> getKiWeaponTypes();   // keySet() of kiWeaponsConfig
```
Default shipped config (`data/dragonminez/previousConfigs/combat.json`, key `kiWeaponsConfig`)
defines exactly `scythe`, `clawlance`, `blade`, all with `forcedColor: "#FFFFFF"` (meaning: by
default, no forced tint — the weapon uses the character's ki/aura color).

**Separately, and this is the part to double check before building a type picker:** the jar
physically only ships matching render assets (geo model + texture) for these three:
```
assets/dragonminez/geo/weapons/kiweapon_blade.geo.json
assets/dragonminez/geo/weapons/kiweapon_clawlance.geo.json
assets/dragonminez/geo/weapons/kiweapon_scythe.geo.json
assets/dragonminez/textures/entity/weapons/kiweapon_{blade,clawlance,scythe}.png
```
`CombatConfig`'s constant pool also references additional keys like `coral_blade`/`twin_blade`
(damage-balance config entries) that do **not** have a matching geo/texture pair in the jar.
**UNCONFIRMED** whether those are pure damage-formula aliases with no visual asset, or map onto
one of the three existing models under an alias. Whatever type value you expose in the NPC GUI
cycle button / script API, constrain it to `blade` / `clawlance` / `scythe` unless you've confirmed
otherwise — anything else will silently render nothing (see gate #8 below, no crash either way).

### 2.6 Real player activation flow — `com.dragonminez.common.network.C2S.SelectKiWeaponC2S`

Reconstructed verbatim from bytecode (`handle` → two lambdas):
```java
// server receives SelectKiWeaponC2S(String type):
ServerPlayer sender = ctx.getSender();
if (sender == null) return;
if (sender.hasEffect(MainEffects.STUN)) return;
StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, sender).orElse(null); // via .ifPresent
if (stats == null) return;

if (!stats.getSkills().hasSkill("kimanipulation")) return;      // skill must already be unlocked
List<String> validTypes = ConfigManager.getCombatConfig().getKiWeaponTypes();
if (!type.isEmpty() && !validTypes.contains(type.toLowerCase())) return;

boolean wasActive = stats.getSkills().isSkillActive("kimanipulation");
String currentType = stats.getStatus().getKiWeaponType();
if (wasActive && currentType != null && currentType.equalsIgnoreCase(type)) {
    stats.getSkills().setSkillActive("kimanipulation", false);   // re-selecting same type = toggle OFF
} else {
    stats.getStatus().setKiWeaponType(type.toLowerCase());
    if (!wasActive) stats.getSkills().setSkillActive("kimanipulation", true);
}
sender.refreshDimensions();
NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(sender), sender);
```
This is what a *real player* does. It's a useful reference for expected semantics (re-picking the
active type turns it off), but the NPC path doesn't need to replicate this packet — it should just
call `Skills.registerDefaultSkill` + `setSkillActive` + `Status.setKiWeaponType` directly on the
proxy's `StatsData` inside `NpcFullDmzRenderer` (see §5.4).

There is **no dedicated `KiManipulation` class** anywhere in the jar — `"kimanipulation"` is a pure
data-driven skill id defined in `data/dragonminez/previousConfigs/skills.json`, with no per-tick
server logic, no separate level-requirement class. Level gating (if any) is purely
`Skill.level`/`Skill.isActive`, and `isSkillActive` only checks the boolean flag, not level.

### 2.7 The actual render gate — `com.dragonminez.client.render.layer.DMZWeaponsLayer.renderForBone(...)`

Full decompiled source was produced this session
(`com/dragonminez/client/render/layer/DMZWeaponsLayer.class` → Vineflower). Gate, in exact order:

1. Bone name must be `"right_arm"` or `"left_arm"`, else skip entirely (this layer never touches
   any other bone).
2. `animatable.isSpectator()` → skip if true.
3. `StatsProvider.get(StatsCapability.INSTANCE, animatable)` must resolve non-null `StatsData`.
4. `stats.getSkills().isSkillActive("kimanipulation")` must be `true`.
5. `stats.getStatus().getKiWeaponType()` must be non-null and not `equalsIgnoreCase("none")`.
6. **`animatable.getMainHandItem().isEmpty()` must be `true`.** If the entity is holding *any*
   item in its main hand, the ki weapon does not render — same as a real player. Since
   `ProxyPlayer.getItemBySlot(EquipmentSlot)`
   (`src/main/java/net/bullettrain/xenopixelsmod/client/compat/npc/NpcFullDmzRenderer.java:76-78`)
   forwards to `owner.getItemBySlot(slot)`, **a CustomNPC's real held main-hand item will suppress
   its ki weapon**, exactly like a player. Decide/ask whether NPCs with `kiWeaponOn` should have
   their configured held item forced empty, or whether this exclusivity should just be documented
   as expected (matching real player behavior) — not something to silently "fix" without a product
   decision.
7. `animatable.getMainArm() == HumanoidArm.RIGHT` is compared against which bone is being
   processed, so the model only attaches to the matching arm.
8. Model/texture are resolved via `ResourceLocation`s built from `type.toLowerCase()`:
   `dragonminez:geo/weapons/kiweapon_<type>.geo.json` /
   `dragonminez:textures/entity/weapons/kiweapon_<type>.png`, existence-checked through
   `Minecraft.getResourceManager().getResource(...)`. Missing either → silent no-render, no crash.
   The bone name inside that baked model is `kiweapon_<type>`.
9. Arm-pivot math for regular vs. Oozaru bodies (relevant to §6):
   ```java
   private static final float[] HUMAN_ARM_RIGHT = {-5.0F, 22.0F, 0.0F};
   private static final float[] HUMAN_ARM_LEFT  = { 5.0F, 22.0F, 0.0F};
   private static final float[] OOZARU_ARM_RIGHT = {-12.0F, 74.0F, 0.0F};
   private static final float[] OOZARU_ARM_LEFT  = { 21.0F, 74.0F, 0.0F};
   // if isOozaru: translate to oozaruPivot/16, scale by 3.8, translate back by -humanPivot/16
   ```
10. Rendering itself: `bufferSource.getBuffer(ModRenderTypes.energy2(texture))`, then
    `this.getRenderer().renderRecursively(poseStack, animatable, targetBone, weaponRenderType,
    bufferSource, vertexConsumer, true, partialTick, packedLight, OverlayTexture.NO_OVERLAY,
    ARGB32.colorFromFloat(0.65F, r, g, b))` — the standard GeckoLib recursive-bone render call,
    using the **same `poseStack`/`bufferSource` the rest of the player model renders with.**

### 2.8 The render pipeline for the glow itself — `com.dragonminez.client.render.util.ModRenderTypes.energy2(...)`

Decompiled fully this session. `energy2` is:
```java
create("energy2", DefaultVertexFormat.NEW_ENTITY, Mode.QUADS, 256, false, true,
   CompositeState.builder()
      .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)   // a VANILLA shader (enderman eyes)
      .setTextureState(new TextureStateShard(texture, true, true))
      .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
      .setCullState(NO_CULL)
      .setWriteMaskState(COLOR_DEPTH_WRITE)
      .setOverlayState(OVERLAY)
      .createCompositeState(false));
```
This is a **normal `RenderType` fed through the standard `MultiBufferSource` pipeline**, using a
vanilla-shipped shader Iris already ships a gbuffer program for by convention (spider/enderman
eyes). This is structurally **different** from how the aura is drawn.

Also checked `com.dragonminez.client.render.util.IrisCompat` (decompiled fully): it is **only**
an `isShaderPackInUse()` detector via reflection against `IrisApi` — no RenderType registration
helpers, no other Iris-specific compat surface exists in DMZ anywhere.

## 3. Why the aura's fix does NOT obviously transfer here — read before touching Iris code

For contrast, the aura fix already shipped in this mod
(`src/main/java/net/bullettrain/xenopixelsmod/client/compat/npc/NpcAuraClient.java`) works around
a completely different problem: it does a **raw `VertexBuffer.drawWithShader(...)` call inside a
`RenderLevelStageEvent.AFTER_LEVEL` handler**, entirely outside the normal entity-render
`MultiBufferSource` pipeline. That needed:
- `mc.getMainRenderTarget().bindWrite(false)` before drawing, because a shader pack's own passes
  between `AFTER_ENTITIES` and `AFTER_LEVEL` can leave a different render target bound (line 125).
- Reconstructing the camera pose manually from `camera.getXRot()/getYRot()` instead of trusting
  `event.getModelViewMatrix()`, **gated behind `IrisCompat.isShaderPackInUse()`** (lines 156-163),
  because Iris replaces/transforms that matrix in a way that broke the naive approach.

**Ki weapons render inside the normal GeckoLib entity-render pass** (§2.7 point 10 — standard
`poseStack`/`bufferSource`, not a separate `RenderLevelStageEvent` draw). There is no evidence yet
that the same render-target-rebind or camera-matrix-reconstruction problem applies here — that
class of bug is specific to code that manually bypasses the normal pipeline, which `DMZWeaponsLayer`
does not do.

**Do not port the aura's fix mechanically.** Instead:
1. Get a real player (or an NPC, once §5 is done) with `kimanipulation` active and a valid
   `kiWeaponType` set, load an actual Iris shader pack, and **observe the real symptom** —
   invisible? black/unlit? wrong color? actually fine already? (Nobody has observed this yet this
   session — the user said "not sure" when asked.)
2. Only once the actual failure mode is known, decide the fix. Plausible causes worth checking,
   roughly in order of how well they fit a vanilla-shader + GeoRenderLayer render path: (a) some
   shader packs override `gbuffers_spidereye`/similar vanilla-shader gbuffer programs in a way that
   assumes the standard `Entity`/`Player` bone hierarchy and doesn't expect this to run on a
   synthetic/aliased player proxy or GeckoLib's animation stack; (b) an ordering issue between
   GeckoLib's render layers and Iris's shadow/gbuffer pass; (c) something entirely unrelated to
   Iris and actually a `DMZWeaponsLayer` bug that just happens to be more visible/less visible with
   shaders on (e.g. lighting-dependent). Don't commit to a hypothesis without observing first.

## 4. Confirmed integration points in this mod's NPC pipeline (for the toggle feature)

All of the following comes from a fresh, full read of each file this session — file paths and
line numbers are accurate as of this handoff, but re-read before editing since other agents may
have touched these files since.

### 4.1 `NpcCombatProfile.java` (563 lines) — where the new fields go

Combat-adjacent visual toggles live directly on this class (parallel to `haloOn`, `auraOn`), **not**
on `NpcDmzAppearance` — that class's own javadoc (lines 8-9) states it's a "player-independent
subset" of purely cosmetic/body-shape data and that "combat/form history deliberately stays in
`NpcCombatProfile`." Add:
- Two new fields, e.g. `boolean kiWeaponOn` and `String kiWeaponType` (default `"blade"` to match
  DMZ's own `Status` default, §2.3), following the exact existing convention: a `TAG_KI_WEAPON_ON`
  / `TAG_KI_WEAPON_TYPE` constant each (pattern at lines 33-74), a read line in `fromTag()` (151-244)
  with a `tag.contains(...)` guard, a write line in `writeTag()` (268-321).
- **Also add both to `visualOptionsTag()` (486-505) / `applyVisualOptions()` (507-527)** — this is
  the *compact* NBT subset that actually reaches every nearby client via
  `NpcAppearancePacket`/`NpcAppearanceFx` (see §4.2). It already carries `auraOn`/`haloOn`/rocks/
  sparking/lightning — a ki-weapon toggle is the same category of live combat-visual state and
  belongs there, not just in the full-profile NBT used only by the editor's save/load.
- Bump `CURRENT_SCHEMA` from `4` to `5` (line 31) — this repo's established convention whenever a
  new persisted field is added (see existing schema-gated branches at lines 174, 220).

### 4.2 Client sync — no wire-format changes needed

- `NpcAppearancePacket.java` carries `visualOptions` as an **opaque `CompoundTag`**
  (`buf.writeNbt`/`readNbt`, lines ~78-81/100-101) — it's exactly `NpcCombatProfile.
  visualOptionsTag()`. Adding fields to that tag requires **zero changes** to the packet's
  constructor, `encode()`, or wire format.
- `NpcAppearanceClient.java`'s `State` record already carries the whole `CompoundTag
  visualOptions()` verbatim (no per-field unpacking) — no changes needed here either.
- `NpcAppearanceFx.java`'s `packet(LivingEntity)` builds the packet straight from
  `NpcCombatProfile.read(living).visualOptionsTag()` — automatically includes new tags.
- `ModNetwork.java`: `PROTOCOL` is currently `"45"`. **Convention in this repo (see its own
  javadoc changelog) is to bump `PROTOCOL` and add a changelog line even for NBT-content-only
  changes**, despite no encoder/decoder method changing. Bump to `"46"` with a new changelog line
  describing the ki-weapon toggle addition.

### 4.3 `NpcFullDmzRenderer.java` (492 lines) — where to drive the proxy's `StatsData`

- In `render()` (lines 91-134): right next to the existing
  `stats.getStatus().setForceHalo(visual.haloOn);` (line 110), after `visual` (a `NpcCombatProfile`
  from `visualProfile(state)`, line 108) is available, add:
  ```java
  stats.getSkills().registerDefaultSkill("kimanipulation", 1); // must exist before setSkillActive works (§2.2)
  stats.getSkills().setSkillActive("kimanipulation", visual.kiWeaponOn);
  if (visual.kiWeaponOn) stats.getStatus().setKiWeaponType(
          visual.kiWeaponType.isBlank() ? "blade" : visual.kiWeaponType.toLowerCase());
  ```
  (Pick the actual `maxLevel` argument deliberately — `registerDefaultSkill`'s second argument only
  affects `Skill.maxLevel`, which nothing in the render-gate checks per §2.7, so any positive value
  works; `1` is the safe minimal choice unless something else reads `getMaxSkillLevel`.)
- Same pattern in `renderPreview()` (lines 137-218), next to
  `stats.getStatus().setForceHalo(visualProfile(state).haloOn);` (line 155), so the appearance
  editor's live preview shows the weapon too.
- `visualProfile(NpcAppearanceClient.State)` (lines 438-456; calls `applyVisualOptions`, line 441)
  will expose `visual.kiWeaponOn`/`visual.kiWeaponType` automatically once §4.1 is done.
- Remember §2.7 gate #6: the NPC's real held main-hand item (forwarded through
  `ProxyPlayer.getItemBySlot`) will suppress the weapon exactly like a player — decide the product
  behavior here (see §2.7).

### 4.4 `GuiNpcDmzAppearance.java` (475 lines) — GUI widget

Add a new block inside `initStyle(...)` (lines 158-171), alongside the existing halo/aura-rocks/
sparking/lightning toggles:
- Reuse `toggle(id, label, x, y, boolean value)` (lines 173-176) for `kiWeaponOn` — same helper
  already used for `ID_HALO`/`ID_AURA_ON`/`ID_ROCKS`/`ID_SPARKING`/`ID_LIGHTNING`.
- For `kiWeaponType`, reuse the same single-button-cycle pattern `ID_MODE` already uses for
  `NpcDmzAppearance.Mode.next()` (button relabeled to the current enum name, handled in
  `buttonEvent`, line 211) — either add a tiny enum or just cycle a fixed `String[]{"blade",
  "clawlance", "scythe"}` index, per §2.5's asset-backed whitelist.
- New `ID_*` constants go in the existing block (lines 73-79); wire into `buttonEvent(...)`
  (205-262, mutate `draft` then fall through to `preview(draft); init();`) and into `pull()`
  (287-312, read widget state back into `draft` before each click/blur) exactly like the existing
  toggles.
- `ID_APPLY` (249-254) already sends the full profile via `NpcProfileSavePacket` — no change needed
  there beyond the new fields existing on `NpcCombatProfile`.

### 4.5 `NpcXenoScriptApi.java` (254 lines) — script API

- `VERSION` is currently `"5"` (line 15) — bump to `"6"` following the same convention as
  `ModNetwork.PROTOCOL`.
- Add `setKiWeaponOn(ICustomNpc npc, boolean on)` using the existing `mutate(ICustomNpc, ProfileEdit)`
  helper (lines 227-228), the exact one-liner pattern already used for `setHalo`/`setAuraRocks`/
  `setAuraSparking`/`setAuraLightning` (lines 75-78):
  ```java
  public boolean setKiWeaponOn(ICustomNpc npc, boolean on) {
      return mutate(npc, p -> p.kiWeaponOn = on);
  }
  ```
- Add `setKiWeaponType(ICustomNpc npc, String type)` — validate against the asset-backed whitelist
  (`blade`/`clawlance`/`scythe`, §2.5) before mutating and return `false` on rejection, following
  `setTailColor`'s validate-then-reject-on-bad-input style (lines 81-87), rather than
  `setHairCode`'s no-validation style (lines 223-225) — an invalid ki-weapon type silently renders
  nothing client-side (§2.7 point 8), so failing loudly in the script API is more useful to script
  authors than accepting garbage.
- Add `kiWeaponOn`/`kiWeaponType` entries to `getProfile(ICustomNpc npc)`'s read-side map dump
  (lines 27-50), parity with the existing `out.put("halo", p.haloOn)` (line 39).

## 5. Suggested implementation order

1. `NpcCombatProfile.java`: add the two fields + NBT plumbing + schema bump (§4.1).
2. `ModNetwork.java`: bump `PROTOCOL` + changelog line (§4.2 — purely a version/documentation bump,
   no wire-format code changes needed elsewhere).
3. `NpcFullDmzRenderer.java`: drive the proxy's `Skills`/`Status` in `render()`/`renderPreview()`
   (§4.3). Compile and get an NPC's DMZ profile toggled on via a temporary hardcoded value or a
   quick script call, and **observe whether it renders and where it's positioned** before writing
   any GUI/script plumbing — this is the step that will surface the position/scale question in §6
   for real, instead of guessing at it.
4. `GuiNpcDmzAppearance.java`: GUI toggle + type cycle (§4.4).
5. `NpcXenoScriptApi.java`: script API methods + version bump (§4.5).
6. Only after step 3 shows real in-game rendering: load an actual Iris shader pack and observe
   whether ki weapons (on a player first — simpler repro, no NPC plumbing needed — then on an NPC)
   actually have a shader problem at all, and what it looks like, before writing any Iris-specific
   code (§3).

## 6. Ki-weapon position/scale on NPCs — not yet observed, don't guess

The user expects the ki weapon will need the same kind of NPC-size-aware correction the aura and
hair needed. Relevant facts, but **no conclusion yet** since nothing has rendered on an NPC so far:

- `NpcFullDmzRenderer.render()` (line ~126-128) already scales the **entire** pose before handing
  off to DMZ's renderer: `float npcScale = Math.max(0.05f, NpcDisplayApply.getSize(owner) /
  5.0f); pose.scale(npcScale, npcScale, npcScale);` — since `DMZWeaponsLayer.renderForBone` runs
  *inside* that same already-scaled `poseStack`/render tree (§2.7 point 10), the weapon may well
  inherit correct NPC scaling "for free," unlike the aura (a separate raw draw call in a totally
  different event, which is why it needed its own explicit size math in `NpcAuraClient.
  cnpcSizeMul()`) or the hair (which hit a *different* bug entirely — a vanilla `scale(-1,-1,1)`
  mirror quirk in `LivingEntityRenderer.setupRotations()` that GeckoLib doesn't replicate).
- The Oozaru-specific pivot correction in `DMZWeaponsLayer` (§2.7 point 9,
  `HUMAN_ARM_RIGHT/LEFT` vs `OOZARU_ARM_RIGHT/LEFT`) is keyed off `character.isOozaruCached()` —
  this should already work for an NPC in an Oozaru form the same way it works for a player, since
  it reads off the same synced `Character` object (`syncCharacter()` already sets `character.
  setRace`/form data every frame).
- **Do the implementation in §5 step 3 first, then look at an actual NPC with a ki weapon
  materialized in-game.** If it's already positioned correctly, there's nothing to fix here and
  this section can be closed out as "not a bug." If it's off, diagnose the actual observed
  displacement (too high/low/wrong scale/detached from the hand) against the pivot math in §2.7
  point 9 and `NpcDisplayApply.getSize()`'s actual value range, rather than assuming it needs the
  same billboard-style fix the aura did — that fix targeted a structurally different rendering
  path.

## 7. Verification checklist

- `gradlew build --offline` (or `compileJava --offline`) compiles clean after each numbered step
  in §5.
- Give a player (or NPC once wired up) the `kimanipulation` skill and a valid `kiWeaponType`;
  confirm the weapon renders in-hand with an empty main-hand item, and disappears if a real item is
  placed in that hand (§2.7 gate #6 — expected DMZ behavior, not a bug).
- Toggle `kiWeaponOn`/cycle `kiWeaponType` from the new GUI section on an NPC in FULL DMZ mode;
  confirm it updates live in the appearance-editor preview (`renderPreview`) and on the world NPC
  (`render`), and persists across a relog (NBT round-trip through §4.1).
- Call the new script methods (`setKiWeaponOn`, `setKiWeaponType`) from a CustomNPCs script and
  confirm `getProfile(npc)` reflects the change.
- With an Iris shader pack loaded: observe the real symptom on a player first (§3/§5 step 6)
  before writing any fix, then re-check on an NPC.
- Visually confirm ki-weapon position/scale on at least one differently-sized NPC (`NpcDisplayApply.
  getSize()` != default) and one Oozaru-form NPC, per §6.
