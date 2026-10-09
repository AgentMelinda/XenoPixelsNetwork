# Xeno NPCs v5 Foundation

**Status date:** 2026-09-20

Xeno NPCs is the native, performance-oriented replacement path for My NPCs and CustomNPCs.
The current implementation is a vertical slice, not feature parity with `doco.md` yet.

## Current native contract

- Six stable entity roles: `humanoid`, `creature`, `trader`, `guard`, `companion`, and `quest`.
- Native entities always persist Brain v5 data. Brain v5 is not exposed as a legacy NPC combat-tab option.
- Role definitions reload from `data/xenopixelsmod/xeno_npcs/roles/*.json` and fail closed to bounded defaults.
- Decision work is staggered by entity id and protected by a shared per-tick budget. The brain does not scan all entities.
- The Xeno NPC Wand creates native entities, opens their dedicated editor, and performs explicit one-way imports.
- Legacy imports create a separate native NPC and retain source mod and UUID provenance. The source NPC is never overwritten or deleted.

## Role definition schema

```json
{
  "schema": 1,
  "role": "guard",
  "brain": "v5",
  "body_family": "humanoid",
  "decision_ticks": {
    "combat": 8,
    "active": 16,
    "idle": 40
  },
  "refs": {
    "dialogue": "xenopixelsmod:npcs/dialogue/default",
    "quest": "xenopixelsmod:npcs/quests/default",
    "trades": "xenopixelsmod:npcs/trades/default",
    "job": "xenopixelsmod:npcs/jobs/guard"
  }
}
```

The schema requires Brain v5, validates body families, bounds all decision intervals to 1–1200 ticks,
and validates optional references as namespaced resource ids. Reference targets are reserved groundwork;
their dialogue, quest, trade, and job registries are not implemented yet.

## Legacy AI integration

- Brain v4 is available to the existing My NPCs and CustomNPCs combat integration.
- It uses bounded decisions, recovery pressure, combo selection, ranged approach logic, and existing verified combat helpers.
- The legacy combat-tab cycle remains v1 → v2 → v3 → v4. It never selects native-only v5.

## Planned parity areas

The following `doco.md` feature families remain future work and are not runtime-verified as native Xeno NPC features:

- display/model customization and skin presets;
- advanced AI paths, flying/swimming modes, shelter, mounts, and home areas;
- dialogue, quests, factions/reputation, trades, banks, transporters, and mail;
- jobs, roles, followers, companions, guards, spawners, cloning, and natural spawning;
- scripts and event hooks with a deliberately constrained security model;
- remote management, import previews with field-level selection, and bulk migration tooling.

Each subsystem should use indexed registries, bounded work queues, staggered updates, and no unbounded
per-tick world scans. Runtime behavior beyond the verified startup, datapack reload, and API packet proof
must remain marked not verified until exercised in a fresh game process.
