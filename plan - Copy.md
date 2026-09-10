# Xeno UI Asset Integration and Client Crash Repair

  ## Summary

  - Repair the current SkillsMenuScreen mixin crash before applying further visual changes.
  - Integrate selected artwork from dragonminez_our_style_full.zip into Xeno-themed menus, lock-on, radar, and all four scouter HUD variants.
  - Preserve stock DragonMineZ visuals whenever the Xeno menu theme is disabled.
  - Bump the mod to 0.3.4-1.21.1, run the full test/build pipeline, and verify through runClient.

  ## Implementation Changes

  - Mixin stability
      - Change both DmzSkillsInteractionMixin wrapped-super-call receiver parameters from BaseMenuScreen to the exact SkillsMenuScreen type required by MixinExtras.
      - Verify both mouseClicked and render wrappers apply without InvalidInjectionException or the resulting VerifyError.
      - Keep the aura-layer handlers strongly typed with BakedGeoModel, RenderType, MultiBufferSource, and VertexConsumer; the current source and 0.3.3 development bytecode already contain the correct
        descriptor, so do not revert them to Object.

      - Treat the reported aura descriptor error as a stale-jar deployment issue unless it reproduces with the newly built jar.

  - Menu assets
      - Extend the existing deterministic texture generator rather than manually editing generated PNG atlases.
      - Source reusable pieces from _OUR_STYLE_SEPARATED_ELEMENTS: character/menu buttons, large/small panels, quest panels, icon tabs, and tooltip corners.
      - Apply the established palette consistently: cyan for normal/hover framing, gold for selected and important actions, red only for destructive actions, unavailable states, or warnings.
      - Preserve exact DMZ UV cell sizes and transparent padding so visual bounds, texture sampling, and widget hitboxes remain aligned.
      - Cover character/stats, skills, quests, minigames, settings, party, dialog-style panels, and navigation tabs without replacing elements that are already cleaner in the current Xeno theme.

  - Lock-on, radar, and scouters
      - Generate Xeno-owned versions of the ZIP’s lock-on and radar elements plus green, red, blue, and purple scouter atlases.
      - Redirect DMZ’s direct texture lookups to the Xeno resources only while dmzMenusThemed() is enabled; return the original dragonminez resources otherwise.
      - Retain each scouter’s identifying lens color while recoloring shared framing and indicators into the cyan/gold/red Xeno state language.
      - Preserve original atlas dimensions, UV positions, radar-dot behavior, text placement, alpha, and nearest-neighbor pixel sharpness.

  ## Interfaces

  - No new public scripting, networking, or server APIs.
  - Existing /xenohud menus theme behavior expands to control the themed lock-on, radar, and scouter visuals.
  - No separate HUD-theme configuration is added.

  ## Test Plan

  - Compile main and test sources and run the complete unit-test suite.
  - Add focused tests for themed-versus-stock resource selection and retain the existing skills hitbox tests.
  - Run texture generators twice and require the second run to report no changes.
  - Run runClient and confirm:
      - No failed Xeno mixins or verifier errors.
      - Title screen and world entry complete successfully.
      - Skills hover, selection, scrolling, tabs, keyboard input, and clicks remain stable.
      - Sparking aura rendering does not produce an injection error.
      - Every menu remains sharp and correctly aligned at multiple GUI scales and window sizes.
      - Lock-on, both radar types, and all four scouter colors switch between stock and Xeno artwork with the theme setting.

  - Run test build serverJar, inspect produced artifacts, and report client/server jar hashes.

  ## Assumptions

  - The ZIP is the approved source for reusable artwork.
  - Assets are recolored and recomposed programmatically with nearest-neighbor scaling; no AI-generated replacements are required.
  - Only menus, lock-on, radar, and scouter HUDs are included; combat HUD bars, radial actions, radar gameplay logic, and scouter mechanics remain unchanged.
  - Release version and changelog date will be 0.3.4-1.21.1 — September 9, 2026.