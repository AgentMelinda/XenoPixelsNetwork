# Effekseer effect generator

Generates the mod's Effekseer effects: procedural HD textures, Effekseer 1.80 projects, and the
compiled `.efkefc` files the game plays through AAA Particles.

```
python tools/effekseer/gen_effects.py                  # every set
python tools/effekseer/gen_effects.py thruster sparking
python tools/effekseer/gen_effects.py hakai --preview  # also save editor frame strips
```

- **Editor:** found at `--effekseer <dir>`, then `EFFEKSEER_HOME`, then the bundled
  `tools/new_particles/Effekseer1.80.6Win`. It compiles headless:
  `Tool/Effekseer.exe -cui -in <project> -o <out.efkefc>`.
- **Output:** `src/main/resources/assets/xenopixelsmod/effeks/<slot>/<slot>.efkefc`, plus the
  textures it uses (unused ones are pruned).
- **Editable sources:** `tools/effekseer/<set>/<slot>/<slot>.efkproj`. They open in the Effekseer
  editor for hand tuning.
- **Check:** the editor saves back only the values it understood, so every authored value is
  compared with what it kept. A misspelt element fails the run instead of silently doing
  nothing.
- **Preview** (Windows): opens each effect in the editor, clicks Play and saves
  `tools/effekseer/previews/<slot>.png`. It takes over the mouse for a few seconds per effect, and
  it stops without clicking if the editor window cannot be brought to the front.

| Set | Slots | Look |
| --- | --- | --- |
| `hakai` | `hakai_channel`, `hakai_crumble`, `hakai_erase`, `hakai_palm` | Dragon Ball Super Hakai: purple veil, violet shards breaking away, no explosion |
| `thruster` | `missile_thruster`, `ship_thruster` | Rocket exhaust: white-hot core, orange-red flame cone, Mach diamonds, sparks, red glow, reddish-grey smoke trail |
| `sparking` | `sparking_aura`, `sparking_burst` | Budokai Tenkaichi 3 Sparking: gold ki veil, lightning, embers, dust ring, rocks; a flash and shockwave at the start |
| `aura` / `aura2` / `aura3` | shared colour folders | HD auras: v1 billow column, v2 spiked silhouette, v3 silhouette + punch edges + form-coloured sparking |

**Aura look changes.** A definition or colour tweak for aura3 needs a full
`python tools/effekseer/gen_effects.py aura3` rebuild (~1080 `.efkefc`). `--missing` only
fills holes; it does not rewrite existing files. Motif density changes need generator Phase 2
plus that full rebuild.

**Layout:**
- `efkgen/textures.py`: noise, streaks, masks, distortion maps, motes, glows, rings, shards,
  smoke puffs, Mach discs, lightning and dust rings.
- `efkgen/project.py`: the XML builders, written in the 1.80 element layout.
- `efkgen/build.py`: compile, check and preview.
- `efkgen/effects/*.py`: one module per set, each with `NAME`, `EFFECTS`, `PREVIEW` and
  `textures()`.
- `efkefc2xml.py`: turns any `.efkefc` back into project XML.

**Orientation.** AAA's `rotationFromForward` maps an effect's local +Z onto the forward the game
sends.
- Plumes are authored along +Z.
- Upright effects (Hakai, Sparking) are sent with no rotation (`EffectSlot.upright()`).
- To check a direction in game, run `/xenofx play <slot>` while looking where it should point.
