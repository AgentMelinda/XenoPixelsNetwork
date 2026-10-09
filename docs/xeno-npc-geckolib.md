# GeckoLib NPC models and attack clips

In the native NPC editor, set **Model type** to `GECKOLIB` and **Model asset** to a resource id
such as `mypack:ogre`. By default the renderer looks for:

- `assets/mypack/geo/ogre.geo.json`
- `assets/mypack/textures/entity/ogre.png`
- `assets/mypack/animations/ogre.animation.json`

**Texture** can override the texture path. **Animation asset** can override the animation JSON
path when the rig uses a different name or directory, for example
`mypack:animations/ogre_combat.animation.json`. If the specified file is missing, the renderer
tries the derived path, then its existing DragonMineZ fallback.

The animation file needs an attack clip whose bone names match the model. The controller first
uses the NPC's configured melee animation if that exact clip is present, then `attack`, then
`attack1_1`, then the first clip name containing `attack`. A rig with no matching attack clip can
render but has no model-specific attack motion. It needs a real animation JSON from the rig author.

The placed MyNPCs importer copies model and texture resource ids, and also copies a
`ModelAnimation` id when the source stores that key. It does not copy model, texture, or animation
asset files; clients must still have the resource pack or mod containing them. A migrated NPC with
a nonstandard animation path can have **Animation asset** set in the editor.

**Melee Speed** controls the attack interval; **Melee Strength** can override damage per hit.
The Sprint skill adds movement speed while chasing and does not alter the attack interval.
Gameplay with a particular third-party rig remains to be verified in a fresh client.
