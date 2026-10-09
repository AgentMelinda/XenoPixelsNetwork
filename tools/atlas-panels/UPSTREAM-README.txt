Drop-in Java integration for the generated DMZ-style / My-NPCs-sized panels
============================================================================

WHAT'S HERE
  src/main/java/com/yourmod/client/gui/panels/
    PanelTexture.java      - enum, one constant per generated PNG (96 total:
                              24 shapes x green/blue/red/gold), auto-generated
                              from panels_manifest.json. Has .blit(graphics,x,y)
                              to draw any panel at its native size.
    PanelButton.java        - a Button subclass that uses a PanelTexture as its
                              background instead of vanilla's widgets.png,
                              modeled on DragonMineZ's own CustomTextureButton.
    ScaledScreen.java        - resolution-independent UI scaling, ported from
                              DragonMineZ's own ScaledScreen.java (decompiled
                              from the real mod jar). Draw at a virtual
                              getUiWidth() x getUiHeight() canvas instead of
                              real screen pixels; beginUiScale()/endUiScale()
                              handle the rest. This is REAL layout code, not
                              hardcoded positions — the same mechanism every
                              actual DMZ menu screen uses.
    BaseMenuScreen.java      - the shared "menu shell" on top of ScaledScreen:
                              a tab bar (BaseMenuScreen.registerTab(...)),
                              open/close zoom animation, and slide animations
                              for switching between tabs (getLeftPanelSwitch-
                              Offset/getRightPanelSwitchOffset/getTopPanel-
                              SwitchOffset). Also ported from DragonMineZ's
                              own BaseMenuScreen.java; see the doc comment at
                              the top of the file for exactly what had to be
                              genericized (DMZ-specific config/keybind/
                              character-stats hooks that don't exist outside
                              their mod) versus what's untouched original
                              logic (all the animation timing/easing math).
    ExamplePanelScreen.java - a working BaseMenuScreen: a panel + 3 buttons,
                              all positioned relative to getUiWidth()/
                              getUiHeight() rather than hardcoded pixels, with
                              the open animation and panel-slide offset wired
                              up for real. Read its render()/init() for how
                              UI-space positioning, beginUiScale(), and the
                              mouse-coordinate conversion fit together.

  src/main/resources/assets/yourmodid/textures/gui/panels/
    all 96 PNGs, already in the exact folder Minecraft expects.

  The full pipeline that produced everything above, so nothing here is a
  dead end if you want to change a shape/size or add a new one:
    dmz_atlas_generator.py  - the actual texture generator (Python/PIL).
                              Defines every panel shape as a PanelSpec (name,
                              width, height, corner style, ...) in its
                              base_specs list, renders each one in all 4
                              palettes, and writes panels/*.png +
                              panels/*.json + panels_manifest.json.
    panels_manifest.json    - one JSON record per PNG (name, file, width,
                              height, shape, palette, and the exact
                              graphics.blit(...) call for it) — this is
                              PanelTexture.java's source of truth.
    gen_java_enum.py        - reads panels_manifest.json and writes
                              PanelTexture.java. Re-run this any time you
                              add/change shapes, so the enum can never drift
                              out of sync with the actual PNGs by hand-typo.

  Regenerating end to end, after editing base_specs in dmz_atlas_generator.py:
    python3 dmz_atlas_generator.py   # -> panels/*.png, panels_manifest.json
    python3 gen_java_enum.py         # -> PanelTexture.java
  then copy the new panels/*.png over the ones in
  src/main/resources/assets/yourmodid/textures/gui/panels/, and the new
  PanelTexture.java over the one in src/main/java/.../panels/.

SETUP (2 steps)
  1. Copy both src/main/java/... and src/main/resources/... into your mod's
     own src/main/ folder (merge, don't overwrite anything else there).
  2. Find/replace "yourmodid" -> your real mod id, in TWO places:
       - PanelTexture.java, line ~127: the string literal inside
         ResourceLocation.fromNamespaceAndPath("yourmodid", ...)
       - the resource folder itself: rename
         assets/yourmodid/  ->  assets/<your real mod id>/
     If you regenerate PanelTexture.java later with gen_java_enum.py, edit
     MOD_ID_PLACEHOLDER at the top of that script first instead, so you
     don't have to redo the find/replace by hand each time.

     Also change the "package com.yourmod..." line at the top of all 5
     .java files to wherever you actually want this in your mod's package
     tree — they're all in one package and import each other unqualified,
     so move/rename them together.

THEN
  - PanelTexture.MYNPCS_MAIN_PANEL_GREEN.blit(graphics, x, y) draws any panel.
  - PanelButton.build(x, y, PanelTexture.MYNPCS_BUTTON_ROW_BLUE, Component.literal("Show"), btn -> {...})
    gives you a clickable button using one of the button shapes.
  - Open new ExamplePanelScreen() from anywhere (a keybind, a command, an
    item) to see it live: minecraft.setScreen(new ExamplePanelScreen());
  - Want more than one tab (Party/Skills/Config-style switching, with the
    slide animation)? Write a 2nd class the same way ExamplePanelScreen is
    written, then in your client setup call:
      BaseMenuScreen.registerTab(YOUR_KEYBIND_1, ExamplePanelScreen::new);
      BaseMenuScreen.registerTab(YOUR_KEYBIND_2, YourOtherScreen::new);
    Nav buttons for every registered tab are then built automatically by
    BaseMenuScreen.initNavigationButtons().

NOTES
  - Every blit is 1:1, no stretching — same convention the real DragonMineZ
    mod uses (its menubig.png/menusmall.png calls are always native-size
    too). If you need a size that doesn't exist yet, add a new PanelSpec to
    dmz_atlas_generator.py's base_specs, rerun it, then rerun
    gen_java_enum.py — don't stretch an existing texture at runtime, the
    border proportions are baked in at generation time and will distort.
  - PanelButton has no dedicated hover art (the generator draws one look per
    shape x palette, not a lit/unlit pair), so hover defaults to a
    translucent white overlay. Pass a second PanelTexture as hoverTexture
    to use a different palette variant (e.g. gold) as the hover face
    instead of the overlay — see PanelButton.build()'s 2nd overload.
