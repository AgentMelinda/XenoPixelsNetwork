# Tournament KO event evidence (PR-D2a)

**Date:** 2026-10-03  
**Scope:** Evidence gate only — no auto-KO implementation, no tournament Java.  
**Pinned stack:** Minecraft 1.21.1, NeoForge `21.1.248` (`gradle.properties` `neo_version`), jar  
`build/moddev/artifacts/neoforge-21.1.248.jar` (SHA-256  
`1FB8153A2643C8834006BF12AD69E55BCF7CB2C84268E9E783AA5A4D031990A7`).

Cross-links: design §D0-g / PR-D2a in  
`docs/superpowers/specs/2026-10-03-hd-aura-tournament-makers-design.md`; plan Task 2 in  
`docs/superpowers/plans/2026-10-03-tournament-roles-makers-phase2.md`.

## Method

1. Searched `src/` for `@SubscribeEvent` handlers on death/damage events.
2. Confirmed class names with `javap` / `jar tf` against `neoforge-21.1.248.jar` and sources jar.
3. Confirmed `DamageSource.getEntity()` / `getDirectEntity()` on merged Minecraft jar.
4. Scanned `tools/generated/dmz_decompiled_full` for tournament bracket/KO APIs — **none found**  
   (only armour set `a18_tournament` and quest id strings such as `04_enter_the_world_tournament`).

**Not claimed:** in-game tournament PvP runtime proof. This appendix is jar + in-repo citation only.

## Candidate inventory

| Event / method | Jar / class verified | Can identify killer? | Safe for awards? |
| --- | --- | --- | --- |
| `net.neoforged.neoforge.event.entity.living.LivingDeathEvent` | `neoforge-21.1.248.jar` — `javap` shows ctor `(LivingEntity, DamageSource)`, `getSource()`, extends `LivingEvent`, implements `ICancellableEvent`. Sources jar Javadoc: fired from `LivingEntity#die` / `Player#die` / `ServerPlayer#die` via `CommonHooks#onLivingDeath`; cancel → entity does not die. Bus: `NeoForge.EVENT_BUS`. | **Yes (PvP).** Victim = `event.getEntity()`. Killer = `event.getSource().getEntity()` when that entity is the causing player; for projectiles also consider `getDirectEntity()` and projectile owner (see in-repo patterns below). | **Yes, with filters** (server-only, both entrants, match active, same dimension, not canceled, killer ≠ victim). |
| `DamageSource.getEntity()` / `getDirectEntity()` | `neoforge-21.1.248-merged.jar` `javap` on `net.minecraft.world.damagesource.DamageSource`. | Causing entity vs direct (e.g. arrow) entity. | Supporting API only — not an event. |
| `LivingDamageEvent.Pre` / `.Post` | Same NeoForge jar; abstract `LivingDamageEvent` with nested `Pre`/`Post`; both expose `getSource()`. | Attacker via `getSource().getEntity()` while damage is applied — **not** a death. | **No** for KO awards (damage ≠ match end). Useful for FX / modifiers only. |
| `LivingIncomingDamageEvent` | Same jar; cancellable; `getSource()` / `getAmount()`. | Same source helpers as above. | **No** for KO awards (pre-apply / cancel path). Used for FF / protection. |
| `AttackEntityEvent` | `net.neoforged.neoforge.event.entity.player.AttackEntityEvent` — click/target cancel. | Attacker = event player; target = `getTarget()`. | **No** for KO awards (not lethal). |
| `LivingHurtEvent` | **Absent** from `neoforge-21.1.248.jar` living event package (`jar tf` — no `LivingHurtEvent`). Replaced by Incoming/Damage Pre/Post in this NeoForge line. | n/a | Do not subscribe. |
| DMZ tournament KO / bracket API | `tools/generated/dmz_decompiled_full` — armour `a18_tournament_*`, quests `04_enter_the_world_tournament` / `35_otherworld_tournament` / `bojack_tournament_cleanup`; DMZ does subscribe to `LivingDeathEvent` for stats/otherworld (e.g. `ForgeCommonEvents.onPlayerDeath`) but exposes **no** reusable matchmaking/KO award API. | n/a | **Do not invent.** Xeno-owned tournament service only. |

### In-repo kill attribution precedents (citations)

- `ProgressionEvents.onDeath` —  
  `src/main/java/net/bullettrain/xenopixelsmod/features/progression/ProgressionEvents.java:169-173`  
  resolves `ServerPlayer` killer from `getSource().getEntity()` then `getDirectEntity()`.
- `PlayerXenoEvents.onDeath` —  
  `src/main/java/net/bullettrain/xenopixelsmod/npc/script/api/xeno/event/PlayerXenoEvents.java:193-207`  
  fires player `died` / `kill` from `LivingDeathEvent`; may **cancel** death and set health to `1.0f`.
- `NpcProfileLifecycle.onKillSpeech` —  
  `src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcProfileLifecycle.java:93-108`  
  uses `getSource().getEntity()` as killer.
- `PartyEvents.ownerPlayer` —  
  `src/main/java/net/bullettrain/xenopixelsmod/features/party/PartyEvents.java:62-77`  
  on `LivingIncomingDamageEvent`: `getEntity()` → `getDirectEntity()` → projectile owners (pattern for indirect hits; damage event, not awards).

## Recommended subscribe target (for a later auto-KO PR)

```text
@SubscribeEvent(priority = EventPriority.LOW)  // after cancel handlers
public static void onTournamentDeath(LivingDeathEvent event) { … }
```

Exact type: `net.neoforged.neoforge.event.entity.living.LivingDeathEvent`  
Bus: NeoForge game bus (`@EventBusSubscriber` / `NeoForge.EVENT_BUS`), server logic only.

### Filter rules (required before committing a match result / awards)

1. `!event.getEntity().level().isClientSide()`
2. `!event.isCanceled()` (scripts / XenoAPI `died` can cancel; see `PlayerXenoEvents`)
3. Victim `instanceof ServerPlayer`
4. Killer resolved as `ServerPlayer` from `getSource().getEntity()`, else `getDirectEntity()`, else projectile owner (same shape as `PartyEvents.ownerPlayer`) — and `killer != victim`
5. Active tournament match exists; both UUIDs are the match entrants
6. Same dimension as the match / configured arena dimension
7. Ignore non-entrant deaths and deaths outside an active match
8. Commit result **server-side only**; never trust a client “I won” packet for awards (design KD / review focus)

`LivingDamageEvent` / `LivingIncomingDamageEvent` must not grant tournament wins.

## Verdict

Automatic KO for tournament awards: **READY**

Subscribe target: `net.neoforged.neoforge.event.entity.living.LivingDeathEvent` on the NeoForge game bus, with the filter rules above (same-dimension, both entrants, match active, server-only, not canceled, killer ≠ victim via `DamageSource.getEntity()` / `getDirectEntity()` / projectile owner).

Until a follow-on PR implements that subscriber behind config, keep **`/xenotourney result`** (admin/report) as the only result commit path (D0-g / PR-D2). This spike does **not** ship auto-KO.

## Unverified / follow-ups

- Fresh in-game PvP death under an active tournament match (log proof) — not done here.
- Whether DMZ Otherworld / revive flow after `LivingDeathEvent` should be suppressed inside arenas (product decision for the implementer; event still fires).
- Ring-out / forfeit without a death event — still admin `/xenotourney result` only.
