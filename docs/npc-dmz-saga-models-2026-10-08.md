# Native NPC DragonMineZ Saga models

The Display tab's **Select GeckoLib model** action now opens a model-only texture list.
It includes skins with loaded geometry and excludes unrelated GUI, block and item textures.
The ordinary **Select Texture** action still lists all loaded PNGs.

Select namespace `dragonminez`, search `goku`, then select a skin and save the NPC.
The selection sets Model type to `GECKOLIB`, stores the selected PNG as the model asset
and texture, and clears an old explicit animation file or player skin override.
Other appearance and combat fields retain their values.

Examples of exact selectable model asset IDs:

- `dragonminez:textures/entity/sagas/saga_goku_early.png`
- `dragonminez:textures/entity/sagas/saga_goku_mid_base.png`
- `dragonminez:textures/entity/sagas/saga_goku_end_ssj3.png`
- `dragonminez:textures/entity/sagas/saga_vegeta_majin.png`
- `dragonminez:textures/entity/sagas/saga_fgohan_base.png`
- `dragonminez:textures/entity/sagas/saga_saibaman3.png`

DragonMineZ frequently names a skin differently from its shared rig. Goku Early,
Mid Base and End Base all use `geo/entity/sagas/saga_goku.geo.json`; previously these
skins attempted to load nonexistent same-name geometry and fell back to Robot XV.
The resolver now applies the pinned Saga entity mappings and numbered skin variants.
An enabled resource pack supplying an exact same-name rig takes priority.
Saibaman uses its native dedicated animation file; ordinary Saga models retain
`animations/entity/sagas/saga_base.animation.json`.

Evidence on 2026-10-08: `python tools/verify_dmz_saga_model_assets.py` verifies the
dependency SHA-256, 44 model-name override constants through exact-jar `javap`, and
the Saibaman renderer's resource constants, then authors 52 skin aliases. The tool
reads tracked reference source but never modifies it. Jar SHA-256:
`5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`.

Focused resource tests cover Goku forms, numbered variants, resource-pack priority,
unrelated texture rejection and existing pinned geometry/animation assets. They have
not yet been run for this slice. Actual editor selection, saved NPC rendering and
in-game animation remain runtime pending. Skins without a matching or verified
shared rig remain available through the ordinary texture selector; they are not
advertised as working GeckoLib model choices.
