# Native Lord Slug replacement and regeneration

Use `examples/customnpcs/xenopixels_lord_slug_regen.js` in one enabled ECMAScript tab on a native
XenoPixels NPC. It uses the actual native `died`, `damaged`, `meleeAttack`, and `rangedLaunched`
hooks. `attack` and `killed` are not native NPC hook names.

Save the giant NPC in native Clone Tab 1 as `Lord Slug (Giant)` before enabling replacement.
The clone handler maps display names onto native store IDs; a missing clone returns null and
the script logs the missing name. The old NPC is removed only after the clone is successfully
placed. Fractional spawn coordinates are preserved. The giant-name guard prevents that same
script from replacing the giant with another giant when it dies.

Regeneration follows the current maximum health by default, at 6000 health points per second
outside combat and zero during combat, with six seconds of linger after a hit/attack. Native
script ticks arrive every ten server ticks; the elapsed world clock makes the rate independent
of that interval. Duplicate/backwards ticks do not heal. The script never heals a dead NPC.
Set `transformOnDeath: false` to use only regeneration, including on the giant.

The script does not change the NPC editor's respawn settings. A base NPC whose respawn is enabled
can therefore respawn later even after it creates the giant. If the encounter should permanently
replace the base form, disable respawn for the base NPC in its editor.

The native scripting facade now exposes `world.spawnClone`, `npc.despawn`, `npc.setMaxHealth`,
and `npc.isKilled`; clone placement reuses the existing validated native clone handler.
Actual in-game death/clone placement and regeneration remain unverified until exercised in a
fresh client/server using the rebuilt mod.
