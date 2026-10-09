# XenoAPI

A fork of [Noppes/CustomNPCsAPI](https://github.com/Noppes/CustomNPCsAPI) ported to
**Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21**, in preparation for use by `xenopixelsmod`.

The API is the interface layer only. There is no implementation in this repository.

## Origin

- Forked from upstream branch `master`, commit `e6496d6896ba59f5afb55d1ad128c9b3d1896edf`
  ("IBlock.setChanged", the upstream 1.19.1 update).
- Upstream publishes no LICENSE file. Its licensing terms have not been established here.

## Changes from upstream

- Package `noppes.npcs.api` renamed to `xenoapi.npcs.api`. Class names are unchanged. The rename
  also avoids a split-package clash with mods that ship `noppes.npcs.api`
  (CustomNPCs-Unofficial, My NPCs).
- Sources moved to `src/main/java`, and a standalone Gradle build was added (ModDevGradle 2.0.143).
- Forge event bus replaced with the NeoForge bus (`net.neoforged.bus.api`):
  - `@Cancelable` was replaced by `implements ICancellableEvent`.
  - `ForgeEvent` wraps `net.neoforged.bus.api.Event`. Its cancel methods delegate only when the
    wrapped event is an `ICancellableEvent`.
  - `ForgeEvent.EntityEvent` and `ForgeEvent.LevelEvent` take the
    `net.neoforged.neoforge.event.entity.EntityEvent` and
    `net.neoforged.neoforge.event.level.LevelEvent` types.
- `NpcAPI` no longer checks for the `customnpcs` mod or reflectively loads
  `WrapperNpcAPI`. The implementing mod registers itself instead (see below).
- `ScrollItem.getNbt` and `ScrollItem.create(CompoundTag, ...)` take a `HolderLookup.Provider`,
  because 1.21.1 `Component.Serializer` requires one.
- `PotionEffectType.getMCType`/`getMCAllTypes` return `Holder<MobEffect>`. They are backed by an
  explicit `MobEffects` table: 1.21.1 no longer assigns fixed numeric ids, so `byId` would be
  off by one. The int constants are unchanged.
- `NpcEvent.DiedEvent` / `PlayerEvent.DiedEvent` read `DamageSource.getMsgId()`, because the
  `msgId` field no longer exists.

## Howto

The implementing mod registers once, early (e.g. during mod construction):

```java
NpcAPI.setInstance(new MyNpcAPIImpl());
```

Consumers check `NpcAPI.IsAvailable()` before calling `NpcAPI.Instance()`, and register event
listeners with `NpcAPI.Instance().events().register(yourEventClass)`.

## Build

```
./gradlew build
```

The output is `build/libs/xenoapi-<version>.jar`, a plain library jar, not a mod.
