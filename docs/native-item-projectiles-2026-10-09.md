# Native scripted item projectiles

Implementation evidence date: 2026-10-09. Focused tests, the full 3,649-test suite, distribution
build and standalone API-example boundary checks passed. Gameplay verification remains pending.

The two typed `ICustomNpc.shootItem` overloads now create an actual registered
`xenopixelsmod:npc_item_projectile`, derived from Minecraft's `ThrowableItemProjectile`.
Its item is a one-count copy of the supplied stack. Shooting does not consume or modify
the caller's stack. The normal thrown-item renderer draws that item; it is not a ki blast
or a vanilla snowball whose damage behavior happens to differ.

The native 1.21.1 throwable base supplies swept collision, the NeoForge projectile-impact
hook, owner identity/save data, item synchronization and item save data. An entity hit
uses the ordinary thrown damage source with the NPC as owner. Knockback requires positive
post-mitigation `LivingDamageEvent.Post` damage and the existing protection-aware knockback
owner. Team allies and the owner are excluded. Entity or block impact removes the projectile.
The projectile expires after 200 flight ticks and is discarded before moving toward an
unloaded destination chunk or outside the world border. No chunk tickets are acquired.

Launch and re-heading require finite nonzero direction, a loaded target position and at
most 256 blocks. Entity targets must share the level; launch targets must be another live
living entity. Accuracy is bounded to 0–100. Speed uses existing NPC projectile tuning,
clamped to 0.1–3 blocks/tick; an unset/invalid speed uses 1.5. Negative/nonfinite damage uses 1;
explicit zero strength remains zero damage. Dead or removed NPCs cannot launch.
Damage, knockback, speed, accuracy and flight age are saved separately from the base item
and owner data. Gravity uses the real entity no-gravity flag.

The typed projectile exposes item replacement, gravity, accuracy and all three heading
overloads through its existing safe entity adapter. `getItem()` returns a detached item
view; use `setItem()` to commit changes and synchronize the displayed stack.

`enableEvents()` captures the exact currently executing NPC script tab, including successful
top-level evaluation. Server flight ticks deliver `ProjectileEvent.UpdateEvent` to
`projectileTick`; collisions deliver `ImpactEvent` to `projectileImpact`. Entity targets
are typed entity adapters; block targets are `IBlock`. These events are noncancellable.
Scripts run before the typed Java event bus. Other NPC tabs are not called.
Calls outside an executing NPC tab fail explicitly. Bindings are weak and transient:
removal, script edits, disabling scripts, host replacement or level changes stop delivery;
saved/reloaded projectiles do not restore a JavaScript execution context. Three callback
errors disable that binding. Reentrant delivery of the same projectile/hook is suppressed.
After callbacks, movement and collision targets are revalidated before applying damage.

Remaining gaps are explicit:

- Inventory projectile prototypes and automatic NPC ranged item firing are not wired by
  this slice. Both inventory prototype methods remain separately tracked gaps.
- The basic item projectile currently uses normal gravity or no gravity. Constant and
  accelerated gravity modes explicitly refuse launch rather than silently using normal gravity. Existing ki-only
  accelerate/constant gravity, explosion, status-effect, sticking, spinning, trail and
  custom size controls are not promised for this new entity.

Source checked: exact `build/moddev/artifacts/neoforge-21.1.248-sources.jar` for
`ThrowableProjectile`, `ThrowableItemProjectile`, and `Snowball`; repository typed
`IProjectile`/`ICustomNpc` contracts; existing `NpcCombatProfile` and `NpcKiProjectileEffects`.
The [official shootItem signatures](https://www.kodevelopment.nl/customnpcs/api/1.18.2/noppes/npcs/api/entity/ICustomNpc.html)
match the ported overloads. Interface declaration coverage does not prove runtime parity.

Focused tests: `ItemProjectileRulesTest` and `ProjectileScriptContextTest`, including real
Nashorn typed fields and top-level callback capture. A fresh process must still prove spawn,
rendering, impact damage, saved/reloaded ownership and item appearance, and API calls from
Nashorn. No such gameplay proof has been produced for this slice.
