# XenoPixels Network (v0.3.7-1.21.1) — Code Review

**Method:** the jar was decompiled in full with CFR 0.152 (1,024 source files recovered from `net.bullettrain.xenopixelsmod` and `com.lightning.northstar`) and then read directly — not guessed from behavior or documentation. Every finding below cites the file, and quotes or paraphrases the actual decompiled logic. Two caveats inherent to reviewing decompiled bytecode: (1) local variable names are compiler-assigned, not the original source names, and (2) a handful of oddly-shaped boolean expressions (e.g. `x = (Boolean)value) != false`) are CFR's rendering of the underlying bytecode and may not be verbatim to the original Java — those are noted as decompiler artifacts, not attributed to the developer as style choices.

**Overall impression:** this is unusually careful code for a hobby/community mod — extensive input validation, rate-limiting, cooldown tracking, `SavedData` persistence done correctly, and (per the `neoforge.mods.toml` comments) a level of attention to mod-load-order edge cases that most Forge/NeoForge addons never bother with. The issues found are mostly small, plausible-in-hindsight gaps rather than sloppy code — the kind that tend to hide precisely *because* the surrounding code is solid. Nothing catastrophic was found (no RCE-style vulnerabilities, no obvious crash-on-every-run bugs), but two of the findings below are real economy-integrity bugs worth fixing before they're exploited or hit by an unlucky player.

---

## 1. `PlotSale.buy()` can take a buyer's money and give them nothing (no rollback)

**File:** `net/bullettrain/xenopixelsmod/plot/PlotSale.java`, `buy()` (lines ~142–147)

```java
if (!MmoEconBridge.transfer(buyer.getUUID(), listing.seller(), units)) {
    return Result.TRANSFER_FAILED;
}
if (!manager.setOwner(claimed, buyer.getUUID())) {
    return Result.NO_PLOT;          // <-- money already left the buyer's account
}
```

The currency transfer happens **before** the ownership change, and there is no compensating transfer back to the buyer if `setOwner()` returns `false`. Looking at `PlotManager.setOwner()`:

```java
public boolean setOwner(PlotArea oldPlot, UUID newOwner) {
    int index = this.plots.indexOf(oldPlot);   // PlotArea is a record; equals() is component-wise
    if (index < 0) {
        return false;
    }
    ...
}
```

`setOwner` fails whenever the exact `PlotArea` record instance fetched at the top of `buy()` (`claimed`) no longer has an identical match in the live `plots` list — which happens any time the plot's stored flags or owner changed between the `manager.at(...)` read and this call (a second buyer completing a purchase a tick earlier, an admin editing plot flags, a concurrent lease/eviction, etc.). In that window, the seller has already been paid and keeps the plot — the buyer loses their money for nothing. This is reachable without any malicious intent (two players clicking "buy" on the same listing within the same tick window is enough), and in the worst case a seller could deliberately trigger the race (e.g. by having a second account toggle a plot flag right as a purchase is settling) for repeatable free money.

**Fix:** re-order so the ownership change is attempted first and the money only moves on success, or wrap both steps in a single check-then-commit that re-validates `claimed` immediately before `setOwner`, and refund via `MmoEconBridge.transfer(listing.seller(), buyer.getUUID(), units)` if `setOwner` fails after the transfer already went through.

---

## 2. `mmoecon` — the mod both `SignShopPurchase` and `PlotSale` depend on — is never declared as a dependency

**File:** `META-INF/neoforge.mods.toml`

Every other soft integration this mod has (`create`, `computercraft`, `xaeroworldmap`, `controlify`, `customnpcs`, `cnpcgeckoaddon`, `yawp`) gets its own `[[dependencies.xenopixelsmod]]` block with `type="optional"` and a comment explaining the coupling — a pattern this file otherwise follows carefully. `mmoecon` (`com.casp3rnz.mmoecon.PlayerBalanceManager` / `.Money`, reflected against throughout `MmoEconBridge`) has no such entry anywhere in the file. Both the shop-sign economy (`SignShopPurchase`) and plot buying/selling/leasing (`PlotSale`, `PlotLease`) are entirely gated on it. This looks like a genuine oversight rather than a deliberate omission, given how consistently every *other* reflection-based integration got a declared optional dependency — worth adding `[[dependencies.xenopixelsmod]] modId="mmoecon" type="optional" ordering="AFTER"` for consistency and so mod-list/dependency tooling can see the relationship.

---

## 3. `MmoEconBridge` resolves overloaded methods by name + argument count only — not by parameter type

**File:** `net/bullettrain/xenopixelsmod/compat/mmoecon/MmoEconBridge.java`, `findMethod()`

```java
private static Method findMethod(Class<?> type, String name, int parameterCount) {
    for (Method method : type.getMethods()) {
        if (!method.getName().equals(name) || method.getParameterCount() != parameterCount) continue;
        return method;
    }
    return null;
}
```

`Class.getMethods()` makes **no ordering guarantee** across JVM versions/vendors for methods that aren't in a simple inheritance chain. If `PlayerBalanceManager` (a class this mod doesn't control and only sees through reflection) ever ships two same-name, same-arity overloads — e.g. `subtractBalance(UUID, long)` and `subtractBalance(UUID, double)` — this will silently pick whichever one the JVM happens to return first, with no guarantee it's the intended one, and no error if it picks wrong (a `long`/`double` argument mismatch on `Method.invoke` with boxed args will just throw `IllegalArgumentException`, which is swallowed — see finding 4 — rather than surfacing "wrong overload chosen").

**Fix:** pass the actual argument array (already available at every call site) and match with `getParameterTypes()`, or at minimum log which method was resolved once per class-load so a bad match is diagnosable.

---

## 4. `invokeStaticVoid()` reports success purely from "didn't throw" — it never looks at what the call actually did

**File:** `net/bullettrain/xenopixelsmod/compat/mmoecon/MmoEconBridge.java`, `invokeStaticVoid()`, used by `withdraw()`

```java
static boolean invokeStaticVoid(Class<?> type, String name, Object ... args) {
    ...
    try {
        method.invoke(null, args);
        return true;                 // <-- always true if no exception was thrown
    }
    catch (ReflectiveOperationException | RuntimeException exception) {
        ...
        return false;
    }
}
```

`withdraw()` treats this `true` as "the balance was actually subtracted." If the reflected `subtractBalance` method ever has a return value indicating logical failure (declined, insufficient funds detected internally, ledger write failed) rather than throwing, that signal is thrown away and the mod proceeds as if the withdrawal succeeded — handing out the purchased item/plot while the player may not actually have been charged (or was double-charged if the real method partially applied the change before "failing"). Combined with finding 3, a wrong-overload call that happens not to throw would also be silently treated as a success.

**Fix:** if the bridged API's method returns a boolean/status, check it explicitly instead of `invokeStaticVoid` discarding whatever `method.invoke(...)` returned.

---

## 5. `toUnits()`'s fallback path can silently misprice everything by orders of magnitude

**File:** `net/bullettrain/xenopixelsmod/compat/mmoecon/MmoEconBridge.java`, `toUnits()`

```java
public static long toUnits(double price) {
    Class<?> money = MmoEconBridge.money();
    if (money == null) {
        return Math.round(price);        // fallback: treats "price" as already being in base units
    }
    ...
}
```

`available()` (the gate `SignShopPurchase`/`PlotSale` check before doing any economy operation) only verifies `balanceManager() != null` — it never checks `money() != null`. So there's a reachable state where `available()` returns `true` (balance manager loaded fine) but `money()` fails to load (e.g. a slightly mismatched `mmoecon` build where `Money` was renamed/moved), and every price on the server silently goes through `Math.round(price)` instead of the real `Money.fromDouble(price)` conversion. If `Money`'s real unit scale isn't 1:1 with raw currency (cents, or any other subdivision), this fallback doesn't fail loudly — it just charges/pays the wrong amount forever, which is far worse than refusing to run.

**Fix:** fold `money() != null` into `available()` so a partially-loaded integration is treated as fully unavailable (`Result.NO_ECONOMY`) rather than silently falling back to a guess.

---

## 6. Shop signs discovered late get permanently registered with no owner

**File:** `net/bullettrain/xenopixelsmod/shop/SignShopPurchase.java` (lines ~57–66), compare with `net/bullettrain/xenopixelsmod/mixin/common/SignShopUpdateMixin.java`

The *only* code path that registers a shop with a real owner is the sign-edit mixin, which captures `this.player.getUUID()` at the moment a player finishes editing the sign text. `SignShopPurchase.buy()` has a second, independent registration path for when a sign's text already parses as a valid shop listing but no `SignShopManager.Entry` exists yet for that block (e.g. the sign arrived via world/structure load, a WorldEdit paste, a dispenser, or a `SavedData` desync after `/reload`):

```java
SignShopManager.Entry entry = manager.at(dimension, pos);
if (data == null && entry != null) { data = entry.data(); }
if (data == null) { return Result.NO_SHOP; }
if (entry == null) {
    manager.put(dimension, pos, null, data);   // owner hard-coded to null, forever
}
```

Once this fires, the shop is permanently ownerless in the saved data — there is no later code path shown anywhere in the shop package that backfills an owner onto an existing entry. An ownerless shop can't be managed/removed by whoever's item it's selling, and (depending on how `owner` is used elsewhere, e.g. in `SignListingProtection`) may end up either unprotected against being overwritten by anyone, or permanently un-editable.

**Fix:** either refuse to auto-register (return `Result.NO_SHOP` and let the mixin's own re-registration on next edit fix it) or attribute the buyer/finder rather than hard-coding `null`, whichever matches the intended design.

---

## 7. Seven `catch (Throwable ...) {}` blocks swallow real errors, not just expected failures

**Files:** `dmz/form/DmzFormEditorService.java:546`, `combat/ZanzokenConfusion.java:135,156`, `compat/npc/NpcCombatProfile.java:1130,1139`, `aero/AeroLinkManager.java:205,242`, `client/combat/AfterimageGhostRenderer.java:138`, `client/anim/XenoAnimClip.java:628`, `vs/ShipBallisticController.java:533`

Most of these are defensible as "best effort, keep going" guards around third-party compat calls or malformed config data — but several catch `Throwable` rather than `Exception`, which also swallows `Error` subtypes: `NoSuchMethodError`/`LinkageError` from a mismatched compat mod version, `StackOverflowError`, even `OutOfMemoryError` in the worst case. When a compat integration silently breaks (e.g. `AeroLinkManager`'s `ExternalThrusterCompat.setThrottle` starts throwing `NoSuchMethodError` because the external mod updated its API), the current code makes that indistinguishable from "nothing happened" — no log line, no way for a server admin to discover why thrusters stopped responding. `XenoAnimClip`'s catch (line 628) additionally means one malformed motion entry in an animation JSON silently drops that motion with zero indication to whoever authored the file.

**Fix:** narrow these to the specific exception types actually expected (e.g. `ReflectiveOperationException` for the compat-call sites, `JsonParseException`/`NumberFormatException` for the parsing sites), and add a `LOGGER.debug(...)`/`.warn(...)` even where the catch is intentionally non-fatal — `MmoEconBridge`'s own `invokeStatic` (which logs on failure) is the right model already used elsewhere in this same codebase.

---

## 8. Duplicate dependency declaration in `neoforge.mods.toml`

**File:** `META-INF/neoforge.mods.toml`, lines 72 and 156

```toml
[[dependencies.xenopixelsmod]]
modId="create"
type="optional"
versionRange="[6.0,)"
ordering="AFTER"
side="BOTH"
```

...appears twice, verbatim (once near the top with `xaeroworldmap`/`controlify`, and again later next to the comment about the Create elevator ComputerCraft peripheral). Harmless — NeoForge just processes the same optional dependency twice — but it's a genuine copy-paste leftover in a file that is otherwise meticulously commented and clearly hand-maintained; worth deleting one of the two blocks (probably merging the two explanatory comments onto the single remaining one).

---

## 9. Minor: dead branch in `ShipBallisticController`'s up-vector helper

**File:** `net/bullettrain/xenopixelsmod/vs/ShipBallisticController.java`, lines 316–318

```java
double ux = Math.abs(ny) < 0.9 ? 0.0 : 0.0;   // both branches are 0.0
double uy = Math.abs(ny) < 0.9 ? 1.0 : 0.0;
double uz = Math.abs(ny) < 0.9 ? 0.0 : 1.0;
```

This is **not a functional bug** — the surrounding math (picking world-Y vs. world-Z as the "up" helper depending on whether the nose vector is near-vertical, to avoid a degenerate cross product) is correct, and `ux` genuinely is 0 in both cases. But writing `ux` as a ternary that is identical on both sides is exactly the shape of a copy-paste mistake (as if a third case that used to vary `ux` was simplified away without simplifying the ternary itself), and it will read as suspicious to the next person auditing this file for the exact reason it caught this review's attention. Replace with a plain `double ux = 0.0;` to make the intent unambiguous.

---

## Things that looked risky but checked out fine

Worth naming explicitly, since these are exactly the spots a review should scrutinize and it would be misleading to only report problems:

- **`TargetLockManager`** — careful rate-limiting, cooldowns, map pruning, and cleanup hooks on logout/death/server-stop; the mix of `ConcurrentHashMap` (for `STATES`, iterated during the server-tick sweep) and plain `HashMap` (for the rate-limit maps) is safe because every mutation site is reached only via `enqueueWork` (main server thread) or a main-thread tick event — there's no actual cross-thread access despite the mixed collection types.
- **`SignShopSyntax`** — quantity is bounds-checked to `[1, 9999]` and price to `[0, +∞)` excluding `Infinity`/`NaN` (the `>= 0.0` comparison already rejects `NaN` for free, since all comparisons against `NaN` are `false` in Java) before a listing is ever accepted, closing off the negative-price/negative-quantity dupe vector this reviewer initially suspected.
- **`ShipBallisticController.terminalAcceleration()` / `segmentDistanceSquared()`** — every division site checked (`distance < 1e-6`, `denom <= 1e-12`, `desiredSpeed` clamped to a minimum of 8.0, `responseTime` clamped to a minimum of 0.2) — no reachable divide-by-zero despite the dense vector math.
- **`SignShopPurchase.buy()`** — funds are checked, then withdrawn, then (only on success) the item is handed over, in the correct order, unlike the plot-sale flow in finding 1.
- **`PlotLease.settleDue()`** — funds re-checked immediately before withdrawal rather than trusting a stale check.

---

## Summary

| # | Finding | File | Severity |
|---|---|---|---|
| 1 | Plot purchase can charge the buyer with no refund if ownership transfer fails | `plot/PlotSale.java` | **High** — real money loss |
| 2 | `mmoecon` never declared as a mod dependency despite being load-bearing for two economy features | `META-INF/neoforge.mods.toml` | Medium |
| 3 | Reflection resolves overloaded methods by name+arity only, not parameter types | `compat/mmoecon/MmoEconBridge.java` | Medium (latent) |
| 4 | Void-reflection helper reports success from "didn't throw," ignoring actual outcome | `compat/mmoecon/MmoEconBridge.java` | Medium |
| 5 | Currency-unit fallback can silently misprice everything if only half the bridge loads | `compat/mmoecon/MmoEconBridge.java` | Medium |
| 6 | Late-discovered shop signs get permanently registered with `owner = null` | `shop/SignShopPurchase.java` | Low–Medium |
| 7 | `catch (Throwable) {}` masks real errors (`LinkageError`, etc.), not just expected ones | 7 files | Low–Medium (diagnosability) |
| 8 | Duplicate `create` optional-dependency block | `META-INF/neoforge.mods.toml` | Cosmetic |
| 9 | Dead/misleading ternary branch (harmless but confusing) | `vs/ShipBallisticController.java` | Cosmetic |

Findings 1 and 5 are the two worth prioritizing — both are economy-integrity issues that would only surface intermittently (a race condition, and a partial-load edge case respectively), which is exactly the kind of bug that survives testing and then shows up on a live server weeks later.
