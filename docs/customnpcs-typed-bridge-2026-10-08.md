# Native script access to the typed NPC API

Implementation date: 2026-10-08. This bridge exposes the repository's existing native
adapters. It does not establish full CustomNPCs 1.18.2 behavioral compatibility.

JavaScript native NPC tabs can use:

```js
var npc = event.npc.getAPI();
var world = event.npc.getWorld().getAPI();
var api = event.getAPI(); // Same registered provider as the existing XenoAPI binding.
var display = npc.getDisplay();
var stats = npc.getStats();
var timers = npc.getTimers();
```

An actual player wrapper also supports `event.player.getAPI()` / `player.getAPI()`.
Check for a player before using it: hooks may have null player fields.

The original `event.API` quest-helper field, `ScriptEntity.getType()` resource-id string,
and `ScriptWorld.getDimension()` resource-id string retain their existing contracts.
The typed interfaces have different contracts (`IEntity.getType()` is an integer,
`IWorld.getDimension()` returns `IDimension`); scripts must select the typed view explicitly.
This avoids silently changing existing scripts' return types. Existing typed event
`event.xeno` and root `XenoAPI` bindings remain available.

The Functions panel now catalogs root/native wrapper calls plus typed NPC, world, player,
and root API methods. It follows zero-argument getters returning declared
`xenoapi.npcs.api` interfaces to a bounded depth of three, without following cycles. It
filters Object/static/synthetic methods, raw Minecraft/NeoForge types, class objects,
filesystem types, and `getMC*` accessors from the panel. This is a discovery filter, not
a replacement for the existing Nashorn sandbox or adapter validation.

Methods taking interface arguments or returning collections still appear at their parent
path; their results are not automatically traversed in the panel. A listed interface
method is not a promise that its adapter supports the subsystem. Unsupported operations
remain explicit exceptions; see `docs/native-xenoapi-adapters.md` and the full API audit
in `docs/combat-v3/customnpcs-api-audit-2026-10-08.md`.

Example: `examples/customnpcs/customnpcs_full_api_bridge.js`. The dimension example uses
the actually implemented `IDimension.getId()`; official-version differences must be
checked rather than assuming the 1.18.2 signature exists in this port.

Validation on 2026-10-09: focused catalog and legacy-return-contract tests passed, followed
by the full 3,622-test suite and client/server/addon distribution builds. A fresh API test
client entered its integrated world and produced matching network ping/pong records.
These startup checks do not verify every typed script method or in-game NPC transformation.
