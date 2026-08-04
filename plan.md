# XenoPixels LDLib HUD Migration Plan

> Repository: `https://github.com/AgentMelinda/forge-1.20.1-tutorial`
>
> Target branch assessed: `new1`
>
> Minecraft: `1.20.1`
>
> Mod loader: Forge `47.4.10`
>
> Java: `17`
>
> XenoPixels mod ID: `xenopixelsmod`
>
> UI library target: **LDLib 1.0.52 for Forge 1.20.1**

---

## 1. Mission

Replace ModernUI as the UI/rendering dependency used by XenoPixels and migrate the existing HUD rendering layer to LDLib while preserving the mod's current gameplay, DragonMineZ integration, networking, configuration, HUD editing, and overlay ordering.

The finished HUD should support a Dragon Ball Xenoverse 2-inspired presentation, including:

- Player portrait and frame
- Animated health bar
- Animated Ki bar
- Segmented stamina diamonds or cells
- Transformation/charge indicators
- Player badge and status decorations
- Technique slots, cooldowns, key hints, and selected-slot state
- Resolution-independent positioning and scaling
- HUD editor preview and drag positioning

This is a **rendering migration**, not a gameplay rewrite.

---

## 2. Current Repository Assessment

The repository already has the important foundations:

- `XenoHudOverlay` implements Forge `IGuiOverlay`.
- `XenoHudRegistration` registers overlays through `RegisterGuiOverlaysEvent`.
- The Xeno HUD is registered above DragonMineZ's `dragonminez:beam_clash_hud`, with a vanilla hotbar fallback.
- `DmzClientStats` reads live DragonMineZ player values.
- `XenoClientData` supplies fallback client values.
- `XenoHudConfig` and `XenoClientConfig` control visibility, position, scale, and enablement.
- `XenoHudEditScreen` previews and edits the HUD.
- `XenoTechniqueHotbarOverlay` separately renders techniques and cooldown state.
- `DmzHudOverlayBlocker` suppresses selected original DragonMineZ overlays.
- ModernUI dependencies are currently placed on the run classpath and bundled with Forge Jar-in-Jar.

### Existing files that must be inspected before editing

```text
build.gradle
gradle.properties
src/main/resources/META-INF/mods.toml
src/main/java/net/bullettrain/xenopixelsmod/client/XenoHudOverlay.java
src/main/java/net/bullettrain/xenopixelsmod/client/XenoHudRegistration.java
src/main/java/net/bullettrain/xenopixelsmod/client/XenoTechniqueHotbarOverlay.java
src/main/java/net/bullettrain/xenopixelsmod/client/DmzClientStats.java
src/main/java/net/bullettrain/xenopixelsmod/client/DmzHudClientState.java
src/main/java/net/bullettrain/xenopixelsmod/client/DmzHudOverlayBlocker.java
src/main/java/net/bullettrain/xenopixelsmod/client/XenoClientData.java
src/main/java/net/bullettrain/xenopixelsmod/config/XenoHudConfig.java
src/main/java/net/bullettrain/xenopixelsmod/config/XenoClientConfig.java
src/main/java/net/bullettrain/xenopixelsmod/client/screen/XenoHudEditScreen.java
src/main/java/net/bullettrain/xenopixelsmod/client/screen/XenoMenuScreen.java
src/main/java/net/bullettrain/xenopixelsmod/client/screen/XenoContentListScreen.java
```

The AI must locate the actual paths if any class has moved. Do not create duplicate replacement classes under guessed packages.

---

## 3. Non-Negotiable AI Instructions

The coding AI must obey all instructions in this section.

### 3.1 Inspect before changing

1. Read every file listed in the current repository assessment.
2. Search the entire repository before removing ModernUI:

```bash
rg -n "icyllis\\.modernui|ModernUI|modernui_|jarJar|configurations\\.library|\\blibrary\\(" .
```

3. Search all HUD entry points and direct render calls:

```bash
rg -n "XenoHudOverlay|XenoTechniqueHotbarOverlay|renderHud|IGuiOverlay|RegisterGuiOverlaysEvent|RenderGuiOverlayEvent" src/main/java
```

4. Record the baseline build result before making changes.
5. Do not assume the README's abbreviated file tree is complete; the source tree is authoritative.

### 3.2 Never hallucinate the LDLib API

LDLib's Forge 1.20.1 documentation is limited. The AI must **not invent constructors, callback names, render methods, lifecycle hooks, or texture methods**.

After Gradle resolves LDLib, inspect one of these sources before writing integration code:

- The dependency source JAR in the Gradle cache
- IntelliJ's attached sources
- The official `Low-Drag-MC/LDLib-MultiLoader` repository on branch `1.20.1`

At minimum, inspect the exact 1.0.52 definitions of:

```text
com.lowdragmc.lowdraglib.gui.widget.Widget
com.lowdragmc.lowdraglib.gui.widget.WidgetGroup
com.lowdragmc.lowdraglib.gui.widget.ImageWidget
com.lowdragmc.lowdraglib.gui.widget.LabelWidget
com.lowdragmc.lowdraglib.gui.widget.ProgressWidget
com.lowdragmc.lowdraglib.gui.widget.DraggableWidgetGroup
com.lowdragmc.lowdraglib.gui.texture.IGuiTexture
com.lowdragmc.lowdraglib.gui.texture.ResourceTexture
com.lowdragmc.lowdraglib.gui.texture.ProgressTexture
com.lowdragmc.lowdraglib.gui.texture.TransformTexture
com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup
```

Create a temporary implementation note such as `docs/ldlib-api-notes.md` containing the verified signatures actually used. Delete it only if the final code and comments make those details obvious.

### 3.3 Preserve Forge as the overlay owner

LDLib should provide reusable widgets and texture/rendering primitives. Forge must continue to own:

- Overlay registration
- Overlay order
- Per-frame HUD invocation
- GUI visibility checks
- Client-only lifecycle

Do **not** replace `RegisterGuiOverlaysEvent` with a screen-only LDLib system. The HUD must remain visible during normal gameplay without opening a menu.

### 3.4 Preserve gameplay and data flow

Do not rewrite or relocate DragonMineZ gameplay logic merely to support the HUD.

Preserve:

- `DmzClientStats` as the primary DragonMineZ read adapter
- `XenoClientData` fallback behavior
- Vanilla-health fallback behavior where currently used
- Technique slot, cooldown, and key-state behavior
- Existing network packets and capability synchronization
- Existing DragonMineZ HUD suppression behavior
- Existing config values and saved user positions whenever practical

No new packet should be added unless the required HUD value does not already exist on the client.

### 3.5 Keep client classes client-only

Any class importing Minecraft client, Forge client, or LDLib GUI/rendering types must remain under client-only initialization and must not be referenced from common static initialization that can load on a dedicated server.

Do not introduce dedicated-server crashes through classloading.

### 3.6 No unnecessary mixins

Do not add a mixin for normal HUD rendering, input polling, positioning, or overlay ordering. The repository already has the required Forge APIs.

A mixin is allowed only when a concrete, documented incompatibility cannot be solved through Forge events or existing mod adapters. Explain and isolate any such mixin before adding it.

### 3.7 Work in small compiling phases

After each phase:

```bash
./gradlew compileJava
```

On Windows:

```powershell
gradlew.bat compileJava
```

Do not combine dependency removal, data refactoring, editor replacement, and visual redesign into one untestable patch.

### 3.8 Do not disturb unrelated dependencies

Do not update or remove these as part of this task:

- DragonMineZ
- Valkyrien Skies
- GeckoLib
- TerraBlender
- Curios
- KotlinForForge
- MixinExtras

Only change an unrelated dependency when it directly blocks the LDLib migration and the reason is documented.

---

## 4. Dependency Strategy

### 4.1 Use the verified Forge 1.20.1 build

Use LDLib release:

```text
mc1.20.1-1.0.52-forge
```

Verified Modrinth Maven coordinates:

```text
maven.modrinth:B1CBVXHX:NzNZILgs
```

Use the exact version ID initially to keep the build reproducible.

### 4.2 Add properties

In `gradle.properties`, remove the ModernUI version properties only after all ModernUI imports have been migrated.

Add:

```properties
# LDLib Forge 1.20.1
ldlib_version=1.0.52
ldlib_modrinth_project=B1CBVXHX
ldlib_modrinth_version=NzNZILgs
```

`ldlib_version` is for readable metadata and dependency ranges. The Modrinth project/version IDs are used for Gradle resolution.

### 4.3 Add the Modrinth Maven repository

In `build.gradle`, add an exclusive Modrinth repository. ForgeGradle must also be included in the exclusive repository mapping:

```gradle
exclusiveContent {
    forRepository {
        maven {
            name = 'Modrinth'
            url = 'https://api.modrinth.com/maven'
        }
    }
    forRepositories(fg.repository)
    filter {
        includeGroup 'maven.modrinth'
    }
}
```

Place this inside the existing `repositories` block.

### 4.4 Add LDLib to dependencies

Use ForgeGradle deobfuscation:

```gradle
implementation fg.deobf(
    "maven.modrinth:${ldlib_modrinth_project}:${ldlib_modrinth_version}"
)
```

First run:

```bash
./gradlew --refresh-dependencies compileJava
```

If Gradle rejects the coordinate wrapper, inspect the resolved artifact and ForgeGradle error before changing syntax. Do not guess repeatedly.

### 4.5 Do not Jar-in-Jar LDLib by default

Recommended distribution model:

- XenoPixels declares LDLib as a required external mod dependency.
- The user/server pack installs the matching LDLib Forge JAR separately.
- XenoPixels does not embed LDLib inside its own JAR.

Reasons:

- It avoids duplicate nested copies when another mod already ships or requires LDLib.
- It makes dependency versions visible to pack maintainers.
- It avoids taking on bundling/license obligations before the library's current distribution license has been reviewed.
- It reduces the chance that two independently embedded LDLib versions load together.

Do not add LDLib to the existing `library` or `jarJar` configurations unless the maintainer explicitly chooses bundling after testing and license review.

### 4.6 Add `mods.toml` dependency

Remove the old ModernUI dependency block after ModernUI has been removed.

Add:

```toml
[[dependencies.${mod_id}]]
modId="ldlib"
mandatory=true
versionRange="[1.0.52,1.1)"
ordering="AFTER"
side="BOTH"
```

LDLib identifies itself as a client-and-server library. Keep `side="BOTH"` unless the exact installed release's metadata and dedicated-server test prove that XenoPixels can safely declare it client-only.

### 4.7 License verification gate

Before distributing a build:

1. Read the license file packaged with the exact LDLib 1.0.52 artifact.
2. Read the official repository license for the checked-out `1.20.1` revision.
3. Resolve any discrepancy between distribution-page metadata and repository metadata.
4. Add required notices to XenoPixels documentation or distribution files.
5. Do not embed or redistribute LDLib until this check is complete.

External dependency installation is preferred for the first implementation.

---

## 5. Target Architecture

Use a model-view-bridge structure.

```text
DragonMineZ / Xeno client data
              │
              ▼
       XenoHudSnapshot
       immutable frame data
              │
              ▼
         XenoHudView
    LDLib widget composition
              │
              ▼
       XenoHudOverlay
 Forge IGuiOverlay lifecycle bridge
```

### 5.1 Data model: `XenoHudSnapshot`

Create an immutable client-side record or final class containing only the values needed to draw one frame.

Suggested fields:

```java
public record XenoHudSnapshot(
    float health,
    float maxHealth,
    float healthRatio,
    float ki,
    float maxKi,
    float kiRatio,
    float stamina,
    float maxStamina,
    float staminaRatio,
    int staminaSegments,
    int filledStaminaSegments,
    boolean charging,
    boolean transformed,
    boolean alive,
    String displayName,
    ResourceLocation portraitTexture
) {}
```

The exact fields must follow current XenoPixels/DragonMineZ data availability. Do not add networking solely to match this suggested shape.

Add a factory/adapter such as:

```text
XenoHudSnapshotFactory.capture(Minecraft minecraft)
```

Responsibilities:

- Read `DmzClientStats` once per frame.
- Apply existing fallback rules once.
- Clamp ratios to `0.0F..1.0F`.
- Prevent division by zero.
- Return a stable snapshot consumed by all widgets for that frame.
- Avoid reflection or capability lookup in each widget.

### 5.2 View: `XenoHudView`

Create a reusable LDLib-backed root view that owns the HUD's visual hierarchy.

Conceptual tree:

```text
XenoHudView / root WidgetGroup
├── background frame ImageWidget
├── PortraitWidget
├── health frame ImageWidget
├── health ProgressWidget or custom textured bar
├── health damage-delay layer
├── ki frame ImageWidget
├── ki ProgressWidget or custom textured bar
├── stamina SegmentedStaminaWidget
├── player badge ImageWidget
├── status/charge indicator
└── optional labels/debug values
```

The exact API calls must come from LDLib 1.0.52 source inspection.

The view should expose a small XenoPixels-owned interface, for example:

```java
public final class XenoHudView {
    public void setSnapshot(XenoHudSnapshot snapshot);
    public void setBounds(int x, int y, int width, int height, float scale);
    public void setEditorMode(boolean editorMode);
    public void render(GuiGraphics graphics, float partialTick);
    public void resetAnimationState();
}
```

These are XenoPixels APIs; their internals may delegate to LDLib widgets using the actual verified LDLib methods.

### 5.3 Bridge: existing `XenoHudOverlay`

Keep `XenoHudOverlay implements IGuiOverlay` as the Forge bridge.

It should be reduced to lifecycle work:

1. Validate client/player state.
2. Respect `Minecraft.options.hideGui`.
3. Respect `XenoClientConfig.xenoHudEnabled`.
4. Respect `XenoHudConfig.visible`.
5. Capture one `XenoHudSnapshot`.
6. Calculate configured position and scale.
7. Update the view.
8. Render the view.
9. Restore any pose/render state in `finally` when needed.

Do not move overlay registration into the view.

### 5.4 Texture registry/constants

Create a central class such as:

```text
client/hud/XenoHudTextures.java
```

It should hold:

- `ResourceLocation` constants
- Atlas regions or UV descriptions
- Frame and fill textures
- Portrait mask/ring
- Stamina segment textures
- Badge and selection art
- Technique slot art

Avoid scattering resource paths and UV numbers across widget classes.

### 5.5 Custom widgets only where necessary

Prefer stock LDLib widgets for normal images, labels, groups, and simple progress bars.

Create custom XenoPixels widgets for behavior specific to the Xenoverse-style HUD:

```text
PortraitWidget
LayeredBarWidget
SegmentedStaminaWidget
ChargePulseWidget
TechniqueSlotWidget
CooldownSweepWidget
```

Do not create a custom widget merely to wrap one `ImageWidget` without adding behavior.

---

## 6. Rendering and Animation Design

### 6.1 Frame-rate-independent animation

Never use “add N per rendered frame.” Use elapsed time or partial-tick-aware interpolation.

Suggested animation state:

- `displayedHealthRatio`
- `delayedHealthRatio`
- `displayedKiRatio`
- `displayedStaminaRatio`
- `pulseTime`
- `lastGameTime` or monotonic timestamp

Recommended behavior:

- Health fill responds quickly.
- A delayed damage layer trails health loss.
- Ki fill interpolates smoothly but remains readable during rapid charging.
- Stamina segments animate individually when spent/restored.
- Selected technique slot uses a restrained pulse or scale effect.

Reset animation state when:

- The player instance changes.
- The world unloads.
- The player respawns.
- Values become invalid.
- Resources are reloaded if texture state is cached.

### 6.2 Ratio safety

Every normalized value must use a helper equivalent to:

```java
private static float safeRatio(float value, float maximum) {
    if (!Float.isFinite(value) || !Float.isFinite(maximum) || maximum <= 0.0F) {
        return 0.0F;
    }
    return Mth.clamp(value / maximum, 0.0F, 1.0F);
}
```

### 6.3 GUI scale and anchor system

Use logical GUI pixels, not raw framebuffer pixels.

Preserve the existing configuration semantics:

- `x`
- `y`
- `scale`
- `visible`

Define one base design size, for example:

```text
Base HUD width: 220 logical pixels
Base HUD height: 72 logical pixels
```

The actual dimensions should match the final texture atlas.

All child widgets should be positioned relative to the root, so moving/scaling the root cannot desynchronize its parts.

### 6.4 Render-state discipline

The AI must verify that rendering does not leak:

- Pose stack transforms
- Shader color
- Blend state
- Depth state
- Scissor state
- Stencil state

Use push/pop or LDLib's verified state-management utilities. Always restore manually changed Minecraft render state.

This is especially important when testing with Embeddium/Oculus/shaders.

---

## 7. Texture and Asset Plan

### 7.1 Create a dedicated atlas

Recommended resource path:

```text
src/main/resources/assets/xenopixelsmod/textures/gui/xeno_hud_ldlib.png
```

Keep the current `xeno_hud.png` during migration for visual comparison and rollback.

Suggested atlas regions:

```text
frame/background
portrait ring
portrait mask/background
health frame
health fill
health delayed-damage fill
ki frame
ki fill
stamina empty segment
stamina full segment
stamina recovering segment
P1/player badge
charge glow
technique frame
technique selected frame
cooldown mask/overlay
key-cap backgrounds
```

### 7.2 Avoid copyrighted asset copying

Create original XenoPixels art inspired by the information hierarchy and energetic style of Xenoverse 2. Do not extract or redistribute Xenoverse 2 textures, fonts, portraits, or icons.

### 7.3 Texture requirements

- Use power-of-two atlas dimensions where practical.
- Keep hard pixel edges where required.
- Leave transparent padding around glowing elements to avoid atlas bleeding.
- Document region coordinates in `XenoHudTextures`.
- Verify behavior with resource packs and resource reload (`F3+T`).

---

## 8. Implementation Phases

## Phase 0 — Baseline and safety checkpoint

### Tasks

1. Checkout the intended branch.
2. Ensure the working tree is clean or commit current work.
3. Run:

```bash
./gradlew clean compileJava
./gradlew build
```

4. Launch the current client and capture screenshots at:
   - GUI scale 2
   - GUI scale 3 or Auto
   - Windowed 1280×720
   - 1920×1080 or native display
5. Record current HUD coordinates and config file values.
6. Test the current HUD editor and technique hotbar.
7. Record current suppression of DragonMineZ HUD elements.

### Acceptance criteria

- Baseline build status is known.
- Existing unrelated warnings are documented.
- Before/after screenshots are available.
- A rollback commit or branch exists.

---

## Phase 1 — Resolve LDLib without removing ModernUI

### Tasks

1. Add the LDLib properties.
2. Add the exclusive Modrinth Maven repository.
3. Add the LDLib dependency.
4. Keep ModernUI temporarily so current screens still compile.
5. Run dependency and compile checks:

```bash
./gradlew --refresh-dependencies compileJava
./gradlew dependencies
```

6. Confirm only one LDLib version is present in the runtime dependency graph.
7. Start `runClient` and verify the title screen loads.
8. Start `runServer` or a dedicated-server configuration to detect client classloading problems.

### Acceptance criteria

- LDLib 1.0.52 resolves.
- Current XenoPixels code still compiles.
- Client launches.
- Dedicated server reaches normal startup.
- No duplicate-LDLib warning appears.

---

## Phase 2 — LDLib API verification spike

### Tasks

1. Inspect the LDLib 1.0.52 source JAR.
2. Identify the exact supported method for rendering a `Widget`/`WidgetGroup` outside a normal LDLib menu.
3. Determine whether a modular UI container is mandatory or whether widget background/foreground methods can be safely driven by the Forge overlay bridge.
4. Verify how LDLib passes:
   - `GuiGraphics`
   - mouse coordinates
   - partial ticks
   - position and size
   - visibility
   - progress values
   - textures
5. Create a temporary proof-of-concept overlay containing:
   - one LDLib image
   - one progress bar
   - one text label
6. Register it under a temporary debug config flag or render it inside the current HUD.
7. Remove the spike once the integration route is proven.

### Decision rule

If LDLib widgets cannot be safely rendered as a normal persistent Forge overlay without constructing an inappropriate menu/screen lifecycle, use LDLib's verified texture/rendering primitives behind XenoPixels-owned overlay components rather than forcing its full screen framework into the HUD.

The objective is reliable gameplay HUD rendering, not maximum use of library classes.

### Acceptance criteria

- The exact LDLib rendering path is documented.
- A live Forge overlay successfully renders an LDLib-backed image and progress value.
- No invented API remains in code.
- No render state leak is observed.

---

## Phase 3 — Extract a stable HUD snapshot

### Tasks

1. Add `XenoHudSnapshot`.
2. Add `XenoHudSnapshotFactory` or equivalent adapter.
3. Move live-value selection and fallback logic out of low-level drawing code.
4. Keep current visual rendering unchanged temporarily.
5. Make `XenoHudOverlay` render the old graphics using the snapshot.
6. Add focused unit tests for pure math/helpers when the project test setup permits:
   - ratio clamping
   - zero maximum
   - negative values
   - values above maximum
   - NaN/infinity
   - stamina segment calculation

### Acceptance criteria

- The old HUD looks and behaves the same.
- DragonMineZ values are read once per frame, not once per widget.
- Drawing code no longer performs scattered fallback decisions.
- Invalid values cannot create NaN geometry.

---

## Phase 4 — Build the LDLib-backed main HUD view

### Tasks

1. Add `XenoHudView`.
2. Add `XenoHudTextures`.
3. Build the root group and static frame.
4. Implement portrait rendering.
5. Implement health and delayed-damage layers.
6. Implement Ki.
7. Implement segmented stamina.
8. Add badge and status indicators.
9. Feed the view from `XenoHudSnapshot`.
10. Add a temporary config toggle:

```text
legacyHudRenderer=true/false
```

This toggle is for migration testing only and should be removed after sign-off.

### Acceptance criteria

- Both legacy and LDLib renderers can be compared with the same snapshot.
- All major values match the old renderer.
- Position and scale settings still work.
- `hideGui` and visibility config work.
- The HUD survives GUI scale changes and window resize.

---

## Phase 5 — Migrate the HUD editor

### Tasks

1. Inspect how `XenoHudEditScreen` currently calls `XenoHudOverlay.renderHud`.
2. Preserve one shared render path between gameplay and editor preview.
3. Do not maintain a second visual implementation for the editor.
4. Use either:
   - the same `XenoHudView` in editor mode, or
   - a thin preview host that delegates to it.
5. Preserve drag behavior and config writes.
6. Preserve reset/default controls.
7. If LDLib's `DraggableWidgetGroup` is suitable after source inspection, use it. Otherwise retain current screen drag input and only delegate rendering.
8. Keep the editor's drag bounds in logical GUI coordinates.

### Acceptance criteria

- Editor preview exactly matches gameplay HUD.
- Dragging changes the existing `XenoHudConfig.x/y` values.
- Scale changes are previewed immediately.
- Reset restores documented defaults.
- Closing/reopening the game preserves settings.

---

## Phase 6 — Migrate the technique hotbar

### Tasks

1. Keep `XenoTechniqueHotbarOverlay` registered separately initially.
2. Create reusable `TechniqueSlotWidget` and `CooldownSweepWidget` only after main HUD stability.
3. Preserve:
   - DragonMineZ equipped slots
   - cooldown values
   - disabled/unavailable states
   - selected slot
   - modifier-key hints
   - current hotbar placement logic
4. Reuse `XenoHudTextures` or create `XenoTechniqueTextures` when the atlas becomes too large.
5. Avoid per-frame allocation of widget trees or texture objects.

### Acceptance criteria

- Every slot displays the same technique as before.
- Selection and cooldown behavior remain accurate.
- Modifier keys behave exactly as before.
- Empty and unavailable slots are visually distinct.
- No noticeable frame-time regression occurs.

---

## Phase 7 — Remove ModernUI safely

### Tasks

1. Run the ModernUI search again.
2. Migrate or rewrite all remaining ModernUI-dependent screens.
3. Only after zero source imports remain, remove from `build.gradle`:
   - ModernUI Core
   - ModernUI Markflow
   - ModernUI Forge
   - ModernUI-specific excludes
   - ModernUI Jar-in-Jar entries
4. Determine whether the custom `library` configuration and `jarJar.enable()` are used by anything else.
5. Remove `library`/Jar-in-Jar infrastructure only if nothing else requires it.
6. Remove from `gradle.properties`:

```properties
modernui_version=...
modernui_core_version=...
```

7. Remove the ModernUI `mods.toml` dependency.
8. Run:

```bash
rg -n "icyllis\\.modernui|ModernUI|modernui_" .
./gradlew clean build
```

### Acceptance criteria

- Search returns no active ModernUI code or properties.
- XenoPixels builds from a clean Gradle cache.
- Produced JAR contains no nested ModernUI artifacts.
- Menu, content list, HUD editor, gameplay HUD, and technique hotbar all open/render.

---

## Phase 8 — Finalize metadata and documentation

### Tasks

1. Add the LDLib dependency to `mods.toml`.
2. Update README requirements/install instructions.
3. State the required LDLib version.
4. Add asset and license notices.
5. Remove temporary legacy renderer and migration toggle.
6. Delete dead procedural render helpers only after no call sites remain.
7. Keep generally useful helpers only when they have tests or clear users.
8. Generate a release build.

### Acceptance criteria

- Fresh installation fails clearly when LDLib is absent.
- Fresh installation launches when LDLib 1.0.52 is present.
- No ModernUI dependency remains.
- No temporary debug flags or spike classes remain.
- README accurately lists required mods.

---

## 9. Suggested Source Layout

Use the existing package root and avoid broad package churn.

```text
src/main/java/net/bullettrain/xenopixelsmod/client/
├── XenoHudRegistration.java
├── DmzClientStats.java
├── DmzHudClientState.java
├── DmzHudOverlayBlocker.java
├── XenoClientData.java
├── hud/
│   ├── XenoHudOverlay.java
│   ├── XenoHudSnapshot.java
│   ├── XenoHudSnapshotFactory.java
│   ├── XenoHudView.java
│   ├── XenoHudTextures.java
│   ├── animation/
│   │   ├── HudAnimationState.java
│   │   └── Interpolation.java
│   └── widget/
│       ├── PortraitWidget.java
│       ├── LayeredBarWidget.java
│       ├── SegmentedStaminaWidget.java
│       └── ChargePulseWidget.java
└── technique/
    ├── XenoTechniqueHotbarOverlay.java
    ├── TechniqueHotbarView.java
    ├── TechniqueSlotWidget.java
    └── CooldownSweepWidget.java
```

Moving the existing overlay classes into subpackages is optional. If moving them creates noisy import churn or risks event registration, keep their current paths and add only new support classes under `client/hud` and `client/technique`.

---

## 10. Forge Overlay Bridge Pseudocode

This block describes responsibilities only. It is **not compile-ready LDLib code**.

```java
public final class XenoHudOverlay implements IGuiOverlay {
    private final XenoHudView view = new XenoHudView();

    @Override
    public void render(
        ForgeGui forgeGui,
        GuiGraphics graphics,
        float partialTick,
        int screenWidth,
        int screenHeight
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.level == null) return;
        if (minecraft.options.hideGui) return;
        if (!XenoClientConfig.xenoHudEnabled.get()) return;
        if (!XenoHudConfig.visible()) return;

        XenoHudSnapshot snapshot = XenoHudSnapshotFactory.capture(minecraft);

        int x = XenoHudConfig.x();
        int y = XenoHudConfig.y();
        float scale = XenoHudConfig.scale();

        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);

            view.setSnapshot(snapshot);
            view.setEditorMode(false);
            view.setBounds(0, 0, BASE_WIDTH, BASE_HEIGHT, scale);
            view.render(graphics, partialTick);
        } finally {
            graphics.pose().popPose();
            // Restore any other explicitly modified state.
        }
    }
}
```

Replace config accessors with the repository's actual API. Preserve the current overlay registration instance pattern.

---

## 11. Performance Rules

The implementation must not:

- Build a new widget tree every frame.
- Create new `ResourceLocation` objects every frame.
- Reload textures every frame.
- Repeatedly reflect into DragonMineZ per child widget.
- Allocate lists for stamina segments every frame.
- Log every frame.
- Recalculate static UV regions every frame.

Recommended lifecycle:

- Construct widget tree once.
- Update scalar values each frame.
- Rebuild only when layout/theme/resource state changes.
- Cache static texture objects.
- Keep snapshot allocation minimal; a single small immutable record per frame is acceptable, but a mutable reusable frame model may be used if profiling shows allocation pressure.

Profile before introducing complicated caches.

---

## 12. Compatibility Test Matrix

### Core environments

- Forge client with required mods
- Integrated single-player server
- Dedicated Forge server
- Multiplayer client connection

### Display cases

- 1280×720 window
- 1920×1080 window
- Ultrawide window if available
- GUI scale 1
- GUI scale 2
- GUI scale 3
- GUI scale Auto
- Fullscreen toggle during play
- Window resize during play

### Player states

- Fresh join before all data packets arrive
- Normal combat
- Taking damage rapidly
- Healing
- Ki charging and draining
- Stamina spending and recovery
- Transformation state changes
- Death screen and respawn
- Spectator mode where applicable
- Dimension change
- Logout to title and reconnect

### HUD behavior

- F1/hide GUI
- Xeno HUD enabled/disabled
- HUD visible/hidden config
- Position editing
- Scale editing
- Reset to defaults
- Technique selection
- Technique cooldown
- Modifier-key states
- DragonMineZ original HUD suppression
- Resource reload with `F3+T`

### Rendering compatibility

- Vanilla rendering
- Embeddium if present in the pack
- Oculus without shader
- Oculus with the pack's supported shader configuration
- No clipping, stencil corruption, or overlay depth leakage

Document any shader combination that cannot be supported rather than hiding the issue.

---

## 13. Build and Validation Commands

Use the repository wrapper.

### Linux/macOS

```bash
./gradlew clean compileJava
./gradlew runClient
./gradlew runServer
./gradlew build
```

### Windows

```powershell
gradlew.bat clean compileJava
gradlew.bat runClient
gradlew.bat runServer
gradlew.bat build
```

### Useful searches

```bash
rg -n "icyllis\\.modernui|ModernUI|modernui_" .
rg -n "com\\.lowdragmc\\.lowdraglib" src/main/java
rg -n "XenoHudOverlay|XenoTechniqueHotbarOverlay|renderHud" src/main/java
rg -n "dragonminez:beam_clash_hud|registerAbove|registerAboveAll" src/main/java
```

### JAR inspection

```bash
jar tf build/libs/*.jar | sort
```

Confirm that:

- XenoPixels classes and assets are present.
- ModernUI is not nested after migration.
- LDLib is not nested unless bundling was explicitly approved.
- No development-only source or debug artifact is included.

---

## 14. AI Reporting Format After Each Phase

The coding AI must report:

```markdown
## Phase N Result

### Files changed
- path/to/file: concise reason

### Behavior preserved
- item

### Behavior added
- item

### Commands run
- command: PASS/FAIL

### Manual tests
- test: PASS/FAIL/NOT RUN

### Known issues
- issue or "None"

### Next phase
- exact next task
```

Do not claim a launch or visual test passed unless it was actually run.

---

## 15. Ready-to-Use Coding-Agent Prompt

Copy the following prompt into the coding AI while it has the repository open:

```text
You are implementing the XenoPixels LDLib HUD migration described in plan.md.

Work only on the next incomplete phase. Read plan.md completely before editing.
Inspect the current repository and exact dependency source before writing code.
Never invent LDLib 1.0.52 methods or constructors; verify every API against the
resolved source JAR or the official LDLib-MultiLoader 1.20.1 source.

Preserve Forge IGuiOverlay registration and ordering, DragonMineZ stat adapters,
networking, fallback behavior, HUD configuration, HUD editor behavior, technique
hotbar behavior, and DragonMineZ HUD suppression. This is a rendering migration,
not a gameplay rewrite.

Keep all Minecraft client and LDLib GUI classes client-only. Do not add mixins
unless a documented blocker makes Forge events insufficient. Do not update
unrelated dependency versions.

Before editing:
1. Show the relevant existing files and summarize their current responsibilities.
2. Run or report the current compile status.
3. State the smallest patch for this phase.

While editing:
1. Make minimal coherent changes.
2. Reuse the existing package root net.bullettrain.xenopixelsmod.
3. Avoid duplicate replacement classes.
4. Do not build widgets or ResourceLocations every frame.
5. Keep one shared render path for gameplay and editor preview.
6. Preserve existing configuration keys when possible.

After editing:
1. Run compileJava.
2. Run build when the phase changes dependencies or metadata.
3. Run the relevant client/server smoke test when available.
4. Search for stale imports or dependency references.
5. Report results using the phase reporting format in plan.md.
6. Be explicit about tests that were not run.

Stop after the current phase is complete and compiling. Do not silently continue
into the next phase.
```

---

## 16. Definition of Done

The migration is complete only when all of the following are true:

- XenoPixels uses LDLib 1.0.52 for the new HUD rendering/component layer.
- Forge still registers and invokes the persistent gameplay overlays.
- Health, Ki, stamina, portrait, status, and technique UI work with live DragonMineZ data.
- Existing fallback behavior remains functional during delayed/missing sync.
- HUD position, scale, visibility, and editor workflow remain functional.
- DragonMineZ overlay suppression still works.
- ModernUI source imports, Gradle properties, dependencies, metadata, and nested artifacts are removed.
- Clean client and dedicated-server builds pass.
- The HUD works across tested GUI scales and resolutions.
- Render state is restored after each overlay render.
- No new per-frame logging or obvious avoidable allocation remains.
- Required LDLib installation and version are documented.
- LDLib license/notice requirements have been reviewed for the chosen distribution model.
- No copied Xenoverse 2 assets are distributed.

---

## 17. Rollback Plan

If the LDLib view cannot be made stable:

1. Keep `XenoHudSnapshot` and its tests; this separation benefits the legacy renderer.
2. Restore the legacy procedural `XenoHudOverlay` as the active renderer.
3. Remove the LDLib dependency and `mods.toml` entry.
4. Restore ModernUI only for screens that still require it.
5. Keep the migration work on a feature branch for later investigation.
6. Do not ship a partially migrated build with two active HUDs or duplicate library versions.

---

## 18. Verified Reference Points

These references were used to prepare this plan:

- XenoPixels repository and `new1` branch:
  `https://github.com/AgentMelinda/forge-1.20.1-tutorial/tree/new1`
- XenoPixels build configuration:
  `https://github.com/AgentMelinda/forge-1.20.1-tutorial/blob/new1/build.gradle`
- XenoPixels Gradle properties:
  `https://github.com/AgentMelinda/forge-1.20.1-tutorial/blob/new1/gradle.properties`
- XenoPixels mod metadata:
  `https://github.com/AgentMelinda/forge-1.20.1-tutorial/blob/new1/src/main/resources/META-INF/mods.toml`
- Current Xeno HUD overlay:
  `https://github.com/AgentMelinda/forge-1.20.1-tutorial/blob/new1/src/main/java/net/bullettrain/xenopixelsmod/client/XenoHudOverlay.java`
- Current overlay registration:
  `https://github.com/AgentMelinda/forge-1.20.1-tutorial/blob/new1/src/main/java/net/bullettrain/xenopixelsmod/client/XenoHudRegistration.java`
- Current technique hotbar overlay:
  `https://github.com/AgentMelinda/forge-1.20.1-tutorial/blob/new1/src/main/java/net/bullettrain/xenopixelsmod/client/XenoTechniqueHotbarOverlay.java`
- LDLib 1.20.1 source branch:
  `https://github.com/Low-Drag-MC/LDLib-MultiLoader/tree/1.20.1`
- LDLib version/project properties:
  `https://github.com/Low-Drag-MC/LDLib-MultiLoader/blob/1.20.1/gradle.properties`
- LDLib Forge build configuration:
  `https://github.com/Low-Drag-MC/LDLib-MultiLoader/blob/1.20.1/forge/build.gradle`
- LDLib 1.0.52 Forge distribution and Maven coordinates:
  `https://modrinth.com/mod/ldlib/version/mc1.20.1-1.0.52-forge`

