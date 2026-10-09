# XenoAPI Event Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Post typed XenoAPI events for every native NPC hook and 19 player events, to scripts and to Java listeners. Along the way, align the existing adapters with the owner's reference documentation.

**Architecture:** `XenoEventDispatch` builds one typed `CustomNPCsEvent` per occurrence and delivers it in this order: scripts (old hooks get it as `event.xeno`, new hooks get it directly), then Java listeners on a counting `NpcAPI.events()` bus, then read-back into the native call site. NPC events come from the existing `NpcScriptHost` call sites. Player events come from a new NeoForge subscriber and native player timers.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.248 (MDG 2.0.143), bus 8.0.5, bundled Nashorn, JUnit 5 via `neoForge.unitTest`.

**Spec:** `docs/superpowers/specs/2026-09-27-xenoapi-event-foundation-design.md` (approved 2026-09-27; corrections and the adapter-alignment section added the same day from verified source).

## Global Constraints

- Native only: nothing may depend on, detect, reflect into or call MyNPCs or CustomNPCs. The reference docs at `https://www.kodevelopment.nl/customnpcs/api/1.16.5/` are read-only guidance.
- Additive: no existing hook, binding, wrapper signature, stored-data key or packet id changes. New packets go through `AddonNetwork`, never `ModNetwork` (none in this plan).
- Server-authoritative. Typed events are built and delivered only on the logical server thread.
- No fake values: a field with no native source stays at the constructor's value and is documented. It is never set to a plausible constant.
- No commits, staging, dependency-jar replacement or unrelated cleanup (repository rule `AGENTS.md`). Where this template says "Commit", record the task in `.superpowers/sdd/2026-09-27-native-xenoapi-adapters/progress.md` instead.
- No reflection-based dispatch or dynamic proxies in production code. Explicit delegation only.
- Stored script data bounds: 64 keys, key length 1-64, string values up to 1024 characters, finite numbers only. Temp data: 64 keys.
- Timer bounds: 64 timers, 1-1200000 ticks.
- Runtime success requires a fresh process and an observed log line. Compiling is not proof.
- Base paths: main `src/main/java/net/bullettrain/xenopixelsmod/`, test `src/test/java/net/bullettrain/xenopixelsmod/`. The adapter package is `npc/script/api/xeno/`; the new event package is `npc/script/api/xeno/event/`.
- Gradle commands always take `-PofflineMcMeta`.

## Review Focus

1. **A cancelled toss must not delete the item.** NeoForge removes the stack before posting `ItemTossEvent` (`CommonHooks.onPlayerTossEvent`). Task 8 returns it through `PlayerXenoEvents.returnTossed` and tests that helper.
2. **A throwing Java listener must not stop the native action or other listeners' state.** Task 4 tests that `XenoEventDispatch.post` swallows and rate-limits.
3. **A cancelled player death must not loop.** Task 8 sets health to 1.0 and verifies it at runtime (checklist item P-died).
4. **Old scripts cancelling a hook whose typed event is not cancellable** (NPC `DiedEvent`, `CollideEvent`) must keep their own flag. Task 4 tests `ScriptEvent` write-through against a non-cancellable event.
5. **An unknown item id in `removeItem(String)` / `inventoryItemCount(String)`** returns false / 0 instead of throwing. Task 1 tests `XenoApiAdapters.knownItem`.

---

### Task 1: Align existing adapters with the reference

**Files:**
- Modify: `npc/script/api/xeno/XenoLivingAdapter.java` (`addPotionEffect`)
- Modify: `npc/script/api/xeno/XenoWorldAdapter.java` (`getLightValue`)
- Modify: `npc/script/api/xeno/XenoItemAdapter.java` (`setStackSize`)
- Modify: `npc/script/api/xeno/XenoApiAdapters.java` (add `knownItem`)
- Modify: `npc/script/api/xeno/XenoPlayerAdapter.java` (`matches(String)`, `removeItem(String,int)`)
- Modify: `npc/script/api/xeno/XenoNpcAdapter.java` (`giveItem`, `getOwner`)
- Modify: `npc/job/NpcFollowerJob.java` (add public `followed`)
- Modify: `npc/script/api/xeno/XenoEntityAdapter.java` (`playAnimation`)
- Test: `npc/script/api/xeno/XenoApiAdaptersTest.java`

**Interfaces:**
- Produces: `static Item XenoApiAdapters.knownItem(String id)` returns null for an unknown or blank id, never AIR. `public static LivingEntity NpcFollowerJob.followed(XenoNpcEntity npc, NpcCombatProfile profile)` returns the entity followed now, or null.

- [ ] **Step 1: Write the failing tests** (append to `XenoApiAdaptersTest`)

```java
    @Test
    void stackSizeFollowsTheReferenceRangeOfOneToMax() {
        var adapter = XenoApiAdapters.wrap(new ItemStack(Items.STONE, 5));
        adapter.setStackSize(0);
        assertEquals(1, adapter.getStackSize(), "the reference documents 1..64");
        adapter.setStackSize(999);
        assertEquals(64, adapter.getStackSize());
    }

    @Test
    void unknownItemIdsAreReportedAsAbsentNotAsErrors() {
        assertNull(XenoApiAdapters.knownItem("minecraft:not_an_item"));
        assertNull(XenoApiAdapters.knownItem(""));
        assertNull(XenoApiAdapters.knownItem(null));
        assertNull(XenoApiAdapters.knownItem("minecraft:air"));
        assertSame(Items.STONE, XenoApiAdapters.knownItem("minecraft:stone"));
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests '*XenoApiAdaptersTest' -PofflineMcMeta`
Expected: FAIL. `knownItem` does not compile, and `setStackSize(0)` leaves 0.

- [ ] **Step 3: Implement**

In `XenoApiAdapters`, replace `item(String)` with a pair:

```java
    /** The registered item for {@code id}; null for a blank, unparsable, unknown or air id. */
    static Item knownItem(String id) {
        ResourceLocation key = id == null || id.isBlank() ? null : ResourceLocation.tryParse(id);
        Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        return item == null || item == Items.AIR ? null : item;
    }

    static Item item(String id) {
        Item item = knownItem(id);
        if (item == null) throw new CustomNPCsException("Unknown item id: %s", id);
        return item;
    }
```

In `XenoItemAdapter.setStackSize`:

```java
    /** 1 to the maximum stack size, as the reference documents ("a number between 1 and 64"). */
    @Override
    public void setStackSize(int size) {
        stack.setCount(Math.max(1, Math.min(stack.getMaxStackSize(), size)));
    }
```

In `XenoWorldAdapter.getLightValue`:

```java
    /** Light at the block as a value between 0 and 1, as the reference documents. */
    @Override
    public float getLightValue(int x, int y, int z) {
        return level.getMaxLocalRawBrightness(new BlockPos(x, y, z)) / 15.0f;
    }
```

In `XenoLivingAdapter.addPotionEffect`, rename the flag and invert its use:

```java
    /**
     * {@code duration} is in seconds and {@code strength} is the amplifier. The last flag hides
     * the particles: XenoAPI names it {@code showParticles}, but its javadoc and the reference
     * both say "Whether you want to hide potion particles".
     */
    @Override
    public void addPotionEffect(int effect, int duration, int strength, boolean hideParticles) {
        Holder<MobEffect> holder = effect(effect);
        if (duration < 1 || duration > MAX_EFFECT_SECONDS) {
            throw new IllegalArgumentException("Effect duration must be 1-" + MAX_EFFECT_SECONDS + " seconds");
        }
        int amplifier = Math.max(0, Math.min(255, strength));
        serverThread();
        entity.addEffect(new MobEffectInstance(holder, duration * 20, amplifier, false, !hideParticles));
    }
```

In `XenoPlayerAdapter`, make unknown ids absent:

```java
    private static Predicate<ItemStack> matches(String id) {
        var item = XenoApiAdapters.knownItem(id);
        return item == null ? stack -> false : stack -> stack.is(item);
    }

    /** False for an unknown item id, as the reference documents. */
    @Override
    public boolean removeItem(String id, int amount) {
        if (XenoApiAdapters.knownItem(id) == null) return false;
        return remove(matches(id), amount);
    }
```

(`inventoryItemCount(String)` already goes through `matches(String)` and now reports 0.)

In `NpcFollowerJob`, expose the existing lookup:

```java
    /** The entity this follower is following right now, or null when the job is off or none is in range. */
    public static LivingEntity followed(XenoNpcEntity npc, NpcCombatProfile profile) {
        return npc == null || !active(profile) ? null : nearestNamedNpc(npc, profile.followerName);
    }
```

In `XenoNpcAdapter`, replace `giveItem` and `getOwner`:

```java
    /** Drops what does not fit, unless the player is in creative (reference behaviour). */
    @Override
    public void giveItem(IPlayer player, IItemStack item) {
        if (!(XenoApiAdapters.unwrap(player) instanceof ServerPlayer target)) {
            throw new IllegalArgumentException("ICustomNpc.giveItem: player cannot be null");
        }
        var stack = XenoApiAdapters.unwrap(item).copy();
        if (stack.isEmpty()) return;
        serverThread();
        if (!target.getInventory().add(stack) && !target.getAbilities().instabuild) target.drop(stack, false);
    }

    /**
     * Who this NPC follows (reference: "In case the npc is a Follower or Companion it will return
     * the one who it's following"). An active follower job returns the followed NPC. The
     * COMPANION role returns its owner when online. Otherwise null.
     */
    @Override
    public IEntityLiving getOwner() {
        var profile = net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.readCached(entity);
        var followed = net.bullettrain.xenopixelsmod.npc.job.NpcFollowerJob.followed(entity, profile);
        if (followed != null) return (IEntityLiving) XenoApiAdapters.wrap(followed);
        if (!net.bullettrain.xenopixelsmod.npc.XenoNpcRoleBehaviour.followsOwner(entity.role())) return null;
        var owner = entity.npcData().owner();
        var server = entity.getServer();
        if (owner == null || server == null) return null;
        return (IEntityLiving) XenoApiAdapters.wrap(server.getPlayerList().getPlayer(owner));
    }
```

In `XenoEntityAdapter`, replace `playAnimation`:

```java
    /** Vanilla client animations (reference): 0 swing main hand, 2 wake up (players only), 3 swing off hand. */
    @Override
    public void playAnimation(int type) {
        if (type != 0 && type != 2 && type != 3) throw new IllegalArgumentException("IEntity.playAnimation: type must be 0, 2 or 3");
        if (type == 2 && !(entity instanceof ServerPlayer)) throw new IllegalArgumentException("IEntity.playAnimation: type 2 is for players only");
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity)) throw new IllegalArgumentException("IEntity.playAnimation needs a living entity");
        if (!(entity.level() instanceof ServerLevel level)) throw new IllegalStateException("IEntity.playAnimation needs a server level");
        serverThread();
        level.getChunkSource().broadcastAndSend(entity,
                new net.minecraft.network.protocol.game.ClientboundAnimatePacket(entity, type));
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests '*XenoApiAdaptersTest' --tests '*XenoApiScriptCompatibilityTest' -PofflineMcMeta`
Expected: PASS, including the existing `itemCopyDoesNotMutateOriginalStack`. It calls `setStackSize(2)` and `setStackSize(3)`, both inside 1..64.

- [ ] **Step 5: Record progress** (no commit). Append `Task 1 (event plan): complete` to the ledger.

---

### Task 2: Script data for every entity, the world and items

**Files:**
- Create: `npc/script/api/xeno/XenoBoundedData.java`
- Create: `npc/script/api/xeno/XenoWorldData.java`
- Modify: `npc/script/api/xeno/XenoDataAdapter.java` (add `ofView`)
- Modify: `npc/script/api/xeno/XenoEntityAdapter.java` (`getTempdata`, `getStoreddata`)
- Modify: `npc/script/api/xeno/XenoWorldAdapter.java` (`getTempdata`, `getStoreddata`)
- Modify: `npc/script/api/xeno/XenoItemAdapter.java` (`getTempdata`, `getStoreddata`)
- Test: `npc/script/api/xeno/XenoBoundedDataTest.java`

**Interfaces:**
- Consumes: `XenoDataAdapter.View` (package-private, 6 methods: put, get, remove, has, getKeys, clear).
- Produces: `static XenoDataAdapter XenoDataAdapter.ofView(Supplier<XenoDataAdapter.View> view)`. `static XenoDataAdapter.View XenoBoundedData.stored(Supplier<CompoundTag> read, Consumer<CompoundTag> write)`. `static XenoDataAdapter.View XenoBoundedData.temp(Map<String, Object> map)`. `static XenoWorldData XenoWorldData.get(MinecraftServer server)`. `CompoundTag XenoWorldData.stored()`. `static Map<String, Object> XenoWorldData.temp()`.

- [ ] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class XenoBoundedDataTest {
    @Test
    void storedDataKeepsNativeBoundsAndTypes() {
        CompoundTag[] owner = {new CompoundTag()};
        var data = XenoDataAdapter.ofView(() -> XenoBoundedData.stored(() -> owner[0], t -> owner[0] = t));
        data.put("n", 3);
        data.put("s", "text");
        data.put("bad", new Object());
        data.put("x".repeat(65), 1);
        data.put("long", "y".repeat(1025));
        data.put("nan", Double.NaN);
        assertEquals(3.0, data.get("n"));
        assertEquals("text", data.get("s"));
        assertFalse(data.has("bad"));
        assertFalse(data.has("x".repeat(65)));
        assertFalse(data.has("long"));
        assertFalse(data.has("nan"));
        for (int i = 0; i < 100; i++) data.put("k" + i, i);
        assertEquals(64, data.getKeys().length, "at most 64 stored keys");
        data.clear();
        assertEquals(0, data.getKeys().length);
    }

    @Test
    void tempDataHoldsAnyValueUpTo64Keys() {
        var map = new HashMap<String, Object>();
        var data = XenoDataAdapter.ofView(() -> XenoBoundedData.temp(map));
        Object value = new Object();
        data.put("o", value);
        assertSame(value, data.get("o"));
        for (int i = 0; i < 100; i++) data.put("k" + i, i);
        assertEquals(64, map.size());
        data.remove("o");
        assertFalse(data.has("o"));
    }

    @Test
    void itemStoredDataLivesInTheCustomDataComponent() {
        ItemStack stack = new ItemStack(Items.STONE);
        var item = XenoApiAdapters.wrap(stack);
        item.getStoreddata().put("owner", "goku");
        assertEquals("goku", XenoApiAdapters.wrap(stack).getStoreddata().get("owner"));
        assertTrue(item.hasNbt(), "stored item data is custom data");
        item.getTempdata().put("t", 1);
        assertEquals(1, item.getTempdata().get("t"));
        assertNull(XenoApiAdapters.wrap(stack.copy()).getTempdata().get("t"), "temp data is per stack instance");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests '*XenoBoundedDataTest' -PofflineMcMeta`
Expected: FAIL. `ofView` and `XenoBoundedData` do not exist.

- [ ] **Step 3: Implement**

Add to `XenoDataAdapter`:

```java
    /** An adapter over any native view; the view is fetched on every call. */
    static XenoDataAdapter ofView(Supplier<View> view) {
        return new XenoDataAdapter(view);
    }
```

Create `XenoBoundedData.java`:

```java
package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Stored and temporary script data with the native bounds: stored data keeps only strings and
 * finite numbers (64 keys, keys of 1-64 characters, strings up to 1024); temp data keeps any value
 * (64 keys). Refused writes change nothing.
 */
final class XenoBoundedData {
    static final int MAX_KEYS = 64;
    static final int MAX_KEY = 64;
    static final int MAX_VALUE = 1024;

    private XenoBoundedData() {}

    private static boolean validKey(String key) {
        return key != null && !key.isBlank() && key.length() <= MAX_KEY;
    }

    /** {@code read} returns the live tag; {@code write} stores a changed tag back. */
    static XenoDataAdapter.View stored(Supplier<CompoundTag> read, Consumer<CompoundTag> write) {
        return new XenoDataAdapter.View() {
            public void put(String key, Object value) {
                if (!validKey(key)) return;
                CompoundTag tag = read.get();
                if (!tag.contains(key) && tag.size() >= MAX_KEYS) return;
                if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
                    tag.putDouble(key, number.doubleValue());
                } else if (value instanceof String string && string.length() <= MAX_VALUE) {
                    tag.putString(key, string);
                } else {
                    return;
                }
                write.accept(tag);
            }

            public Object get(String key) {
                CompoundTag tag = read.get();
                if (key == null || !tag.contains(key)) return null;
                return tag.get(key) instanceof NumericTag number ? number.getAsDouble() : tag.getString(key);
            }

            public void remove(String key) {
                if (key == null) return;
                CompoundTag tag = read.get();
                if (tag.contains(key)) {
                    tag.remove(key);
                    write.accept(tag);
                }
            }

            public boolean has(String key) { return key != null && read.get().contains(key); }
            public String[] getKeys() { return read.get().getAllKeys().toArray(String[]::new); }

            public void clear() {
                CompoundTag tag = read.get();
                for (String key : List.copyOf(tag.getAllKeys())) tag.remove(key);
                write.accept(tag);
            }
        };
    }

    static XenoDataAdapter.View temp(Map<String, Object> map) {
        return new XenoDataAdapter.View() {
            public void put(String key, Object value) {
                if (key == null) return;
                if (value == null) map.remove(key);
                else if (map.containsKey(key) || map.size() < MAX_KEYS) map.put(key, value);
            }
            public Object get(String key) { return key == null ? null : map.get(key); }
            public void remove(String key) { if (key != null) map.remove(key); }
            public boolean has(String key) { return key != null && map.containsKey(key); }
            public String[] getKeys() { return map.keySet().toArray(String[]::new); }
            public void clear() { map.clear(); }
        };
    }
}
```

Create `XenoWorldData.java`:

```java
package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * XenoAPI world data. Stored data persists in one overworld SavedData. Temp data is one
 * server-wide map, "the same cross dimension" as the reference documents, cleared at server stop.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoWorldData extends SavedData {
    static final String NAME = "xenopixelsmod_xenoapi_world";
    private static final Map<String, Object> TEMP = new HashMap<>();
    private CompoundTag stored = new CompoundTag();

    static XenoWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(XenoWorldData::new, XenoWorldData::load), NAME);
    }

    private static XenoWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        XenoWorldData data = new XenoWorldData();
        data.stored = tag.getCompound("Stored").copy();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Stored", stored.copy());
        return tag;
    }

    CompoundTag stored() { return stored; }

    void storedChanged(CompoundTag tag) {
        stored = tag;
        setDirty();
    }

    static Map<String, Object> temp() { return TEMP; }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { TEMP.clear(); }
}
```

In `XenoEntityAdapter`, replace both data methods. Also add the temp-data map field under the class's `MAX_MOTION` constant:

```java
    /** Per-entity temp data; weak so an unloaded entity takes its data with it. Server thread only. */
    private static final java.util.Map<Entity, java.util.Map<String, Object>> TEMP = new java.util.WeakHashMap<>();
    private static final String STORED_DATA = "XenoScriptData";

    /** Temp data: any value, gone when the entity unloads (reference: "only until it's reloaded"). */
    @Override
    public IData getTempdata() {
        serverThread();
        return XenoDataAdapter.ofView(() -> XenoBoundedData.temp(
                TEMP.computeIfAbsent(entity, ignored -> new java.util.HashMap<>())));
    }

    /** Stored data: strings and numbers in the entity's persistent data, under the native NPC key. */
    @Override
    public IData getStoreddata() {
        return XenoDataAdapter.ofView(() -> XenoBoundedData.stored(
                () -> entity.getPersistentData().getCompound(STORED_DATA),
                tag -> entity.getPersistentData().put(STORED_DATA, tag)));
    }
```

(`XenoNpcAdapter` and `XenoPlayerAdapter` keep their overrides, which use the native owners.)

In `XenoWorldAdapter`, replace both data methods:

```java
    @Override
    public IData getTempdata() {
        serverThread();
        return XenoDataAdapter.ofView(() -> XenoBoundedData.temp(XenoWorldData.temp()));
    }

    @Override
    public IData getStoreddata() {
        XenoWorldData data = XenoWorldData.get(level.getServer());
        return XenoDataAdapter.ofView(() -> XenoBoundedData.stored(data::stored, data::storedChanged));
    }
```

In `XenoItemAdapter`, replace both data methods:

```java
    private static final java.util.Map<ItemStack, java.util.Map<String, Object>> TEMP =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final String STORED_DATA = "XenoScriptData";

    /** Per stack instance (ItemStack keeps identity equality), gone when the stack is collected. */
    @Override
    public IData getTempdata() {
        return XenoDataAdapter.ofView(() -> XenoBoundedData.temp(
                TEMP.computeIfAbsent(stack, ignored -> new java.util.HashMap<>())));
    }

    /** Strings and numbers in the custom-data component under {@code XenoScriptData}. */
    @Override
    public IData getStoreddata() {
        return XenoDataAdapter.ofView(() -> XenoBoundedData.stored(
                () -> stack.getOrDefault(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY)
                        .copyTag().getCompound(STORED_DATA),
                tag -> net.minecraft.world.item.component.CustomData.update(DataComponents.CUSTOM_DATA, stack,
                        root -> root.put(STORED_DATA, tag))));
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests '*XenoBoundedDataTest' --tests '*XenoApiAdaptersTest' -PofflineMcMeta`
Expected: PASS.

- [ ] **Step 5: Record progress** (no commit).

---

### Task 3: Item attributes and attack damage

**Files:**
- Modify: `npc/script/api/xeno/XenoItemAdapter.java` (`getAttackDamage`, both `setAttribute`, `getAttribute`, `hasAttribute`)
- Test: `npc/script/api/xeno/XenoItemAttributesTest.java`

**Interfaces:**
- Produces: `static EquipmentSlotGroup XenoItemAdapter.slotGroup(int slot)`, mapping -1 ANY, 0 MAINHAND, 1 OFFHAND, 2 FEET, 3 LEGS, 4 CHEST, 5 HEAD. Anything else throws `IllegalArgumentException`.

- [ ] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XenoItemAttributesTest {
    private static final String ATTACK = "minecraft:generic.attack_damage";

    @Test
    void attackDamageIsTheMainHandAddValueSum() {
        // SwordItem.createAttributes(IRON, 3, -2.4f): 3 + iron bonus 2 = 5.
        assertEquals(5.0, XenoApiAdapters.wrap(new ItemStack(Items.IRON_SWORD)).getAttackDamage(), 1e-9);
        assertEquals(0.0, XenoApiAdapters.wrap(new ItemStack(Items.STONE)).getAttackDamage(), 1e-9);
    }

    @Test
    void setAttributeAddsAReplaceableModifierPerSlot() {
        var sword = XenoApiAdapters.wrap(new ItemStack(Items.IRON_SWORD));
        sword.setAttribute(ATTACK, 2.0, 0);
        assertEquals(7.0, sword.getAttackDamage(), 1e-9);
        sword.setAttribute(ATTACK, 4.0, 0);
        assertEquals(9.0, sword.getAttackDamage(), 1e-9, "setting again replaces, not stacks");
        assertTrue(sword.hasAttribute(ATTACK));
        sword.setAttribute(ATTACK, 0.0, 0);
        assertEquals(5.0, sword.getAttackDamage(), 1e-9, "zero removes the scripted modifier");
    }

    @Test
    void attributeInputsAreValidated() {
        var stone = XenoApiAdapters.wrap(new ItemStack(Items.STONE));
        assertThrows(IllegalArgumentException.class, () -> stone.setAttribute("minecraft:not_real", 1.0, 0));
        assertThrows(IllegalArgumentException.class, () -> stone.setAttribute(ATTACK, 1.0, 6));
        assertThrows(IllegalArgumentException.class, () -> stone.setAttribute(ATTACK, Double.NaN));
        assertFalse(stone.hasAttribute(ATTACK));
        assertEquals(0.0, stone.getAttribute(ATTACK), 1e-9);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests '*XenoItemAttributesTest' -PofflineMcMeta`
Expected: FAIL with `UnsupportedOperationException` from the current attribute methods.

- [ ] **Step 3: Implement** (replace the five unsupported methods in `XenoItemAdapter`)

```java
    static final double MAX_ATTRIBUTE = 1_000_000.0;

    static net.minecraft.world.entity.EquipmentSlotGroup slotGroup(int slot) {
        return switch (slot) {
            case -1 -> net.minecraft.world.entity.EquipmentSlotGroup.ANY;
            case 0 -> net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND;
            case 1 -> net.minecraft.world.entity.EquipmentSlotGroup.OFFHAND;
            case 2 -> net.minecraft.world.entity.EquipmentSlotGroup.FEET;
            case 3 -> net.minecraft.world.entity.EquipmentSlotGroup.LEGS;
            case 4 -> net.minecraft.world.entity.EquipmentSlotGroup.CHEST;
            case 5 -> net.minecraft.world.entity.EquipmentSlotGroup.HEAD;
            default -> throw new IllegalArgumentException("Attribute slot must be -1..5, got " + slot);
        };
    }

    private static Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute(String name) {
        ResourceLocation id = name == null ? null : ResourceLocation.tryParse(name);
        if (id == null) throw new IllegalArgumentException("Unknown attribute " + name);
        return BuiltInRegistries.ATTRIBUTE.getHolder(id)
                .<Holder<net.minecraft.world.entity.ai.attributes.Attribute>>map(holder -> holder)
                .orElseThrow(() -> new IllegalArgumentException("Unknown attribute " + name));
    }

    private static ResourceLocation modifierId(Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, int slot) {
        String path = attribute.unwrapKey().orElseThrow().location().getPath().replace('.', '_');
        return ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "xenoapi/" + path + "/" + (slot < 0 ? "any" : slot));
    }

    /** Sum of main-hand ADD_VALUE attack-damage modifiers, e.g. 5.0 for an iron sword. */
    @Override
    public double getAttackDamage() {
        double[] total = {0.0};
        stack.getAttributeModifiers().forEach(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                    && mod.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
                total[0] += mod.amount();
            }
        });
        return total[0];
    }

    @Override
    public void setAttribute(String name, double value) {
        setAttribute(name, value, -1);
    }

    /** An ADD_VALUE modifier this adapter owns for that attribute and slot; 0 removes it. */
    @Override
    public void setAttribute(String name, double value, int slot) {
        if (!Double.isFinite(value) || Math.abs(value) > MAX_ATTRIBUTE) {
            throw new IllegalArgumentException("IItemStack.setAttribute: value must be finite and within " + MAX_ATTRIBUTE);
        }
        var attribute = attribute(name);
        var group = slotGroup(slot);
        ResourceLocation id = modifierId(attribute, slot);
        var kept = new java.util.ArrayList<net.minecraft.world.item.component.ItemAttributeModifiers.Entry>();
        for (var entry : stack.getAttributeModifiers().modifiers()) {
            if (!entry.matches(attribute, id)) kept.add(entry);
        }
        if (value != 0.0) {
            kept.add(new net.minecraft.world.item.component.ItemAttributeModifiers.Entry(attribute,
                    new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, value,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE), group));
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS,
                new net.minecraft.world.item.component.ItemAttributeModifiers(kept, true));
    }

    /** Sum of ADD_VALUE modifiers for that attribute across all slots. */
    @Override
    public double getAttribute(String name) {
        var attribute = attribute(name);
        double total = 0.0;
        for (var entry : stack.getAttributeModifiers().modifiers()) {
            if (entry.attribute().equals(attribute)
                    && entry.modifier().operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
                total += entry.modifier().amount();
            }
        }
        return total;
    }

    @Override
    public boolean hasAttribute(String name) {
        var attribute = attribute(name);
        return stack.getAttributeModifiers().modifiers().stream().anyMatch(entry -> entry.attribute().equals(attribute));
    }
```

The test's `getAttribute` on stone with an unknown attribute is not expected; `ATTACK` is known and returns 0.0.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests '*XenoItemAttributesTest' -PofflineMcMeta`
Expected: PASS. If `ItemAttributeModifiers.modifiers()` or `Entry.attribute()` fail to compile, run `javap -cp build/moddev/artifacts/neoforge-21.1.248-merged.jar 'net.minecraft.world.item.component.ItemAttributeModifiers$Entry'` and use the record accessors it lists. `matches(Holder, ResourceLocation)` and `forEach(EquipmentSlot, BiConsumer)` were verified on 2026-09-27.

- [ ] **Step 5: Record progress** (no commit).

---

### Task 4: Counting event bus, dispatcher core, `event.xeno`

**Files:**
- Create: `npc/script/api/xeno/event/XenoEventBus.java`
- Create: `npc/script/api/xeno/event/XenoEventDispatch.java`
- Modify: `npc/script/api/xeno/NativeNpcApi.java` (the events field, `events()`, new `eventBus()`)
- Modify: `npc/script/api/ScriptEvent.java` (`xeno` field, new constructor, write-through, `syncFromXeno`)
- Test: `npc/script/api/xeno/event/XenoEventDispatchTest.java`

**Interfaces:**
- Produces:
  - `final class XenoEventBus implements IEventBus` with `XenoEventBus(IEventBus delegate)` and `boolean hasListeners()`.
  - `NativeNpcApi.eventBus()` returning `XenoEventBus`.
  - `XenoEventDispatch`:
    - `static boolean javaListening()`
    - `static boolean wanted(boolean scriptWants)`
    - `static <E extends Event> E post(E event)`
    - `static boolean isCanceled(Event e)`
    - `static void setCanceled(Event e, boolean canceled)`
    - `static Float damage(Event e)` (null when the event has no damage field)
    - `static void setDamage(Event e, float damage)`
    - `static boolean enter(UUID owner, String hook)`
    - `static void exit(UUID owner, String hook)`
  - `ScriptEvent`:
    - field `public final CustomNPCsEvent xeno`
    - constructor `ScriptEvent(String hook, ScriptNpc npc, ScriptEntity player, ScriptEntity source, ScriptEntity entity, float damage, CustomNPCsEvent xeno)`
    - `public void syncFromXeno()`

- [ ] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.npc.script.api.xeno.event;

import net.bullettrain.xenopixelsmod.npc.script.api.ScriptEvent;
import net.neoforged.bus.api.BusBuilder;
import org.junit.jupiter.api.Test;
import xenoapi.npcs.api.event.NpcEvent;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class XenoEventDispatchTest {
    @Test
    void theBusCountsRegistrationsAndForwards() {
        XenoEventBus bus = new XenoEventBus(BusBuilder.builder().build());
        assertFalse(bus.hasListeners());
        int[] seen = {0};
        bus.addListener(NpcEvent.InitEvent.class, e -> seen[0]++);
        assertTrue(bus.hasListeners());
        bus.post(new NpcEvent.InitEvent(null));
        assertEquals(1, seen[0]);
    }

    @Test
    void cancelAndDamageAccessorsFollowTheTypedEvent() {
        var damaged = new NpcEvent.DamagedEvent(null, null, 4.0f, null);
        assertEquals(4.0f, XenoEventDispatch.damage(damaged));
        XenoEventDispatch.setDamage(damaged, 9.0f);
        assertEquals(9.0f, damaged.damage);
        XenoEventDispatch.setCanceled(damaged, true);
        assertTrue(XenoEventDispatch.isCanceled(damaged));
        var init = new NpcEvent.InitEvent(null);
        assertNull(XenoEventDispatch.damage(init));
        XenoEventDispatch.setCanceled(init, true);
        assertFalse(XenoEventDispatch.isCanceled(init), "a non-cancellable event cannot be cancelled");
    }

    @Test
    void scriptEventEditsWriteThroughAndReadBack() {
        var damaged = new NpcEvent.DamagedEvent(null, null, 4.0f, null);
        var event = new ScriptEvent("damaged", null, null, null, null, 4.0f, damaged);
        assertSame(damaged, event.xeno);
        event.setDamage(2.0f);
        event.setCanceled(true);
        assertEquals(2.0f, damaged.damage);
        assertTrue(damaged.isCanceled());
        damaged.setCanceled(false);   // a Java listener overrides the script
        damaged.damage = 7.0f;
        event.syncFromXeno();
        assertFalse(event.isCanceled());
        assertEquals(7.0f, event.getDamage());
    }

    @Test
    void oldHooksKeepTheirOwnCancelWhenTheTypedEventIsNotCancellable() {
        var collide = new NpcEvent.CollideEvent(null, null);
        var event = new ScriptEvent("collide", null, null, null, null, 0.0f, collide);
        event.setCanceled(true);
        event.syncFromXeno();
        assertTrue(event.isCanceled(), "collide's typed event is not cancellable, so the old flag stands");
    }

    @Test
    void reentryIsRefusedUntilExit() {
        UUID id = UUID.randomUUID();
        assertTrue(XenoEventDispatch.enter(id, "target"));
        assertFalse(XenoEventDispatch.enter(id, "target"));
        XenoEventDispatch.exit(id, "target");
        assertTrue(XenoEventDispatch.enter(id, "target"));
        XenoEventDispatch.exit(id, "target");
    }

    @Test
    void aThrowingListenerDoesNotEscapePost() {
        XenoEventBus bus = new XenoEventBus(BusBuilder.builder().build());
        bus.addListener(NpcEvent.InitEvent.class, e -> { throw new IllegalStateException("boom"); });
        var event = new NpcEvent.InitEvent(null);
        assertSame(event, XenoEventDispatch.post(bus, event));
    }
}
```

`CollideEvent` stands in for every non-cancellable typed event (`DiedEvent`, `KilledEntityEvent`, `RangedLaunchedEvent`, `InitEvent`, `UpdateEvent`, `TimerEvent`). `DiedEvent` itself cannot be built with a null damage source, because its constructor calls `getMsgId()`.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests '*XenoEventDispatchTest' -PofflineMcMeta`
Expected: FAIL to compile (new classes and constructor missing).

- [ ] **Step 3: Implement**

`XenoEventBus.java`:

```java
package net.bullettrain.xenopixelsmod.npc.script.api.xeno.event;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * NpcAPI.events(): forwards every call to a real bus and counts registrations, because
 * IEventBus has no listener query (bus 8.0.5). Typed events are built only when someone listens.
 */
public final class XenoEventBus implements IEventBus {
    private final IEventBus delegate;
    private final AtomicInteger registrations = new AtomicInteger();

    public XenoEventBus(IEventBus delegate) { this.delegate = Objects.requireNonNull(delegate); }

    public boolean hasListeners() { return registrations.get() > 0; }

    @Override public void register(Object target) { registrations.incrementAndGet(); delegate.register(target); }
    @Override public <T extends Event> void addListener(Consumer<T> consumer) { registrations.incrementAndGet(); delegate.addListener(consumer); }
    @Override public <T extends Event> void addListener(Class<T> type, Consumer<T> consumer) { registrations.incrementAndGet(); delegate.addListener(type, consumer); }
    @Override public <T extends Event> void addListener(EventPriority priority, Consumer<T> consumer) { registrations.incrementAndGet(); delegate.addListener(priority, consumer); }
    @Override public <T extends Event> void addListener(EventPriority priority, Class<T> type, Consumer<T> consumer) { registrations.incrementAndGet(); delegate.addListener(priority, type, consumer); }
    @Override public <T extends Event> void addListener(EventPriority priority, boolean receiveCanceled, Consumer<T> consumer) { registrations.incrementAndGet(); delegate.addListener(priority, receiveCanceled, consumer); }
    @Override public <T extends Event> void addListener(EventPriority priority, boolean receiveCanceled, Class<T> type, Consumer<T> consumer) { registrations.incrementAndGet(); delegate.addListener(priority, receiveCanceled, type, consumer); }
    @Override public <T extends Event> void addListener(boolean receiveCanceled, Consumer<T> consumer) { registrations.incrementAndGet(); delegate.addListener(receiveCanceled, consumer); }
    @Override public <T extends Event> void addListener(boolean receiveCanceled, Class<T> type, Consumer<T> consumer) { registrations.incrementAndGet(); delegate.addListener(receiveCanceled, type, consumer); }
    @Override public void unregister(Object target) { delegate.unregister(target); }
    @Override public <T extends Event> T post(T event) { return delegate.post(event); }
    @Override public <T extends Event> T post(EventPriority phase, T event) { return delegate.post(phase, event); }
    @Override public void start() { delegate.start(); }
}
```

`unregister` does not decrement the count. A stale "listening" answer only costs building an event nobody reads.

If `javap -cp <bus-8.0.5.jar> net.neoforged.bus.api.IEventBus` lists an abstract method not above, forward it the same way.

`XenoEventDispatch.java`:

```java
package net.bullettrain.xenopixelsmod.npc.script.api.xeno.event;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.npc.script.api.xeno.NativeNpcApi;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import xenoapi.npcs.api.NpcAPI;
import xenoapi.npcs.api.event.NpcEvent;
import xenoapi.npcs.api.event.PlayerEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared delivery rules for typed XenoAPI events: build gate, Java post, state access, re-entry. */
public final class XenoEventDispatch {
    private static final long LOG_INTERVAL_MS = 60_000;
    private static final Map<String, Long> LAST_LOG = new ConcurrentHashMap<>();
    private static final ThreadLocal<Set<String>> ACTIVE = ThreadLocal.withInitial(HashSet::new);

    private XenoEventDispatch() {}

    public static boolean javaListening() {
        return NpcAPI.Instance() instanceof NativeNpcApi api && api.eventBus().hasListeners();
    }

    /** Build a typed event only when a script defines the hook or a Java listener exists. */
    public static boolean wanted(boolean scriptWants) {
        return scriptWants || javaListening();
    }

    /** Posts to NpcAPI.events() after scripts ran; listener exceptions are logged, never thrown. */
    public static <E extends Event> E post(E event) {
        NpcAPI api = NpcAPI.Instance();
        return api == null ? event : post(api.events(), event);
    }

    static <E extends Event> E post(IEventBus bus, E event) {
        try {
            bus.post(event);
        } catch (RuntimeException e) {
            StackTraceElement[] trace = e.getStackTrace();
            String origin = trace.length == 0 ? e.getClass().getName() : trace[0].getClassName();
            long now = System.currentTimeMillis();
            Long last = LAST_LOG.get(origin);
            if (last == null || now - last >= LOG_INTERVAL_MS) {
                LAST_LOG.put(origin, now);
                XenoPixelsMod.LOGGER.warn("XenoAPI listener {} threw on {}: {}", origin,
                        event.getClass().getSimpleName(), e.toString());
            }
        }
        return event;
    }

    public static boolean isCanceled(Event event) {
        return event instanceof ICancellableEvent cancellable && cancellable.isCanceled();
    }

    public static void setCanceled(Event event, boolean canceled) {
        if (event instanceof ICancellableEvent cancellable) cancellable.setCanceled(canceled);
    }

    /** The event's mutable damage, or null for an event without one. */
    public static Float damage(Event event) {
        if (event instanceof NpcEvent.DamagedEvent e) return e.damage;
        if (event instanceof NpcEvent.MeleeAttackEvent e) return e.damage;
        if (event instanceof NpcEvent.RangedLaunchedEvent e) return e.damage;
        if (event instanceof PlayerEvent.DamagedEvent e) return e.damage;
        if (event instanceof PlayerEvent.DamagedEntityEvent e) return e.damage;
        return null;
    }

    public static void setDamage(Event event, float damage) {
        if (!Float.isFinite(damage)) return;
        float value = Math.max(0.0f, damage);
        if (event instanceof NpcEvent.DamagedEvent e) e.damage = value;
        else if (event instanceof NpcEvent.MeleeAttackEvent e) e.damage = value;
        else if (event instanceof NpcEvent.RangedLaunchedEvent e) e.damage = value;
        else if (event instanceof PlayerEvent.DamagedEvent e) e.damage = value;
        else if (event instanceof PlayerEvent.DamagedEntityEvent e) e.damage = value;
    }

    /** False when this owner is already inside this hook on this thread (a hook re-firing itself). */
    public static boolean enter(UUID owner, String hook) {
        return ACTIVE.get().add(owner + "|" + hook);
    }

    public static void exit(UUID owner, String hook) {
        ACTIVE.get().remove(owner + "|" + hook);
    }
}
```

In `NativeNpcApi`, replace the events field and `events()`:

```java
    private final net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventBus events =
            new net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventBus(BusBuilder.builder().build());

    @Override public IEventBus events() { return events; }

    /** The counting bus behind events(), for the build-only-when-listened gate. */
    public net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventBus eventBus() { return events; }
```

In `ScriptEvent`, add the field, a delegating constructor, the write-through and `syncFromXeno`. Keep every existing member:

```java
    /** The typed XenoAPI event for this occurrence, or null where none applies (native dialog hooks). */
    public final xenoapi.npcs.api.event.CustomNPCsEvent xeno;

    public ScriptEvent(String hook, ScriptNpc npc, ScriptEntity player, ScriptEntity source,
                       ScriptEntity entity, float damage) {
        this(hook, npc, player, source, entity, damage, null);
    }

    public ScriptEvent(String hook, ScriptNpc npc, ScriptEntity player, ScriptEntity source,
                       ScriptEntity entity, float damage, xenoapi.npcs.api.event.CustomNPCsEvent xeno) {
        this.hook = hook;
        this.npc = npc;
        this.player = player;
        this.source = source;
        this.entity = entity;
        this.target = entity;
        this.damage = damage;
        this.xeno = xeno;
    }

    public void setDamage(float damage) {
        if (Float.isFinite(damage)) {
            this.damage = Math.max(0.0f, damage);
            if (xeno != null) net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.setDamage(xeno, this.damage);
        }
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
        if (xeno != null) net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.setCanceled(xeno, canceled);
    }

    /** After Java listeners ran: the typed event's cancel and damage are final where it has them. */
    public void syncFromXeno() {
        if (xeno == null) return;
        if (xeno instanceof net.neoforged.bus.api.ICancellableEvent cancellable) canceled = cancellable.isCanceled();
        Float typed = net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.damage(xeno);
        if (typed != null) damage = typed;
    }
```

Remove the old six-argument constructor body and the old `setDamage`/`setCanceled` bodies these replace.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests '*XenoEventDispatchTest' --tests '*NativeNpcApiTest' --tests '*XenoApiScriptCompatibilityTest' -PofflineMcMeta`
Expected: PASS.

- [ ] **Step 5: Record progress** (no commit).

---

### Task 5: Typed NPC events at every native call site

**Files:**
- Modify: `npc/script/NpcScriptHost.java`
- Modify: `npc/XenoNpcEntity.java` (tick ~574, interact ~657, hurt ~1049, setTarget ~1077 and ~1088, die ~1157, doPush ~1401)
- Modify: `compat/npc/NpcMeleeDamage.java:294`
- Modify: `compat/npc/NpcKiAttackDispatcher.java:45`
- Modify: `compat/npc/NpcProfileLifecycle.java:100`
- Test: `npc/script/NpcScriptTypedAliasTest.java`

**Interfaces:**
- Consumes: `XenoEventDispatch.{wanted, post, enter, exit}`, `ScriptEvent(…, CustomNPCsEvent)`, `ScriptEvent.syncFromXeno()`, `XenoNpcAdapter(XenoNpcEntity)`.
- Produces:
  - `NpcScriptHost.fireEvent(XenoNpcEntity npc, String hook, LivingEntity player, LivingEntity source, LivingEntity entity, float damage, Function<ICustomNpc<?>, ? extends CustomNPCsEvent> typed)` returns `ScriptEvent`. It is null only when neither scripts nor listeners want the event.
  - `NpcScriptHost.fire(…same seven…)` returns `boolean` (canceled).
  - `static String NpcScriptHost.typedAlias(String hook)`: `rangedLaunched` gives `rangedAttack`, anything else gives null.

- [ ] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.npc.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcScriptTypedAliasTest {
    @Test
    void theContractNameForRangedLaunchedIsAnAdditionalHook() {
        assertEquals("rangedAttack", NpcScriptHost.typedAlias("rangedLaunched"));
        assertNull(NpcScriptHost.typedAlias("damaged"));
        assertTrue(NpcScriptHost.HOOKS.contains("rangedAttack"));
        assertTrue(NpcScriptHost.HOOKS.contains("rangedLaunched"), "the native name keeps working");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests '*NpcScriptTypedAliasTest' -PofflineMcMeta`
Expected: FAIL (`typedAlias` missing).

- [ ] **Step 3: Implement `NpcScriptHost`**

Add `"rangedAttack"` to the end of `HOOKS`. Add a typed-alias map:

```java
    /** Contract hook names that differ from a native one; the typed event goes to both. */
    private static final Map<String, String> TYPED_ALIASES = Map.of("rangedLaunched", "rangedAttack");
    /** NPCs whose InitEvent reached Java without a script host; cleared with forget/clearAll. */
    private static final java.util.Set<UUID> JAVA_INITED = ConcurrentHashMap.newKeySet();

    static String typedAlias(String hook) {
        return TYPED_ALIASES.get(hook);
    }

    private static boolean defines(Host host, String hook) {
        if (host == null) return false;
        String alias = typedAlias(hook);
        for (NpcScriptEngine.Instance tab : host.tabs) {
            if (tab.hasFunction(hook) || (alias != null && tab.hasFunction(alias))) return true;
        }
        return false;
    }
```

Replace `fire(npc, hook, player, source, entity, damage)` and `fireEvent(...)` with delegating versions. Add the typed path:

```java
    public static boolean fire(XenoNpcEntity npc, String hook, LivingEntity player,
                               LivingEntity source, LivingEntity entity, float damage) {
        return fire(npc, hook, player, source, entity, damage, null);
    }

    public static boolean fire(XenoNpcEntity npc, String hook, LivingEntity player, LivingEntity source,
                               LivingEntity entity, float damage,
                               java.util.function.Function<xenoapi.npcs.api.entity.ICustomNpc<?>,
                                       ? extends xenoapi.npcs.api.event.CustomNPCsEvent> typed) {
        ScriptEvent event = fireEvent(npc, hook, player, source, entity, damage, typed);
        return event != null && event.isCanceled();
    }

    public static ScriptEvent fireEvent(XenoNpcEntity npc, String hook, LivingEntity player,
                                        LivingEntity source, LivingEntity entity, float damage) {
        return fireEvent(npc, hook, player, source, entity, damage, null);
    }

    /**
     * Scripts, then Java listeners, then read-back. Returns null only when neither a script nor a
     * listener wanted this occurrence (the native path then proceeds unchanged).
     */
    public static ScriptEvent fireEvent(XenoNpcEntity npc, String hook, LivingEntity player,
                                        LivingEntity source, LivingEntity entity, float damage,
                                        java.util.function.Function<xenoapi.npcs.api.entity.ICustomNpc<?>,
                                                ? extends xenoapi.npcs.api.event.CustomNPCsEvent> typed) {
        if (npc == null || npc.level().isClientSide()) return null;
        NpcScriptContainer container = NpcCombatProfile.readCached(npc).scripts;
        Host host = null;
        if (container == null || container.isEmpty() || !container.enabled()) {
            if (!HOSTS.isEmpty()) HOSTS.remove(npc.getUUID());
        } else {
            host = hostFor(npc, container);
        }
        boolean java = typed != null && net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.javaListening();
        if (host == null && !java) return null;
        if (!net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.enter(npc.getUUID(), hook)) return null;
        try {
            if (host == null && java && JAVA_INITED.add(npc.getUUID())) {
                net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.post(
                        new xenoapi.npcs.api.event.NpcEvent.InitEvent(
                                new net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoNpcAdapter(npc)));
            }
            // Build the typed event only when a tab defines this hook (or its alias) or Java listens.
            xenoapi.npcs.api.event.CustomNPCsEvent xeno = typed == null || !(java || defines(host, hook)) ? null
                    : typed.apply(new net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoNpcAdapter(npc));
            SharedState state = host != null ? new SharedState(host.temp, host.timers) : sharedState(npc);
            ScriptEvent event = new ScriptEvent(hook, new ScriptNpc(npc, state.temp(), state.timers()),
                    ScriptEntity.of(player), ScriptEntity.of(source), ScriptEntity.of(entity), damage, xeno);
            if (host != null) {
                dispatch(npc, host, hook, event);
                String alias = typedAlias(hook);
                if (alias != null && xeno != null) dispatchTyped(npc, host, alias, xeno);
            }
            if (xeno != null) {
                net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.post(xeno);
                event.syncFromXeno();
            }
            return event;
        } finally {
            net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.exit(npc.getUUID(), hook);
        }
    }

    private static void dispatchTyped(XenoNpcEntity npc, Host host, String hook, Object typed) {
        for (NpcScriptEngine.Instance tab : host.tabs) {
            if (!tab.hasFunction(hook)) continue;
            NpcScriptResult result = tab.call(hook, typed);
            if (!result.ok()) reportError(npc, host, hook + ": " + result.describe());
        }
    }
```

In `fireInit`, construct the init event with the typed view and post it:

```java
    private static void fireInit(XenoNpcEntity npc, Host host) {
        var xeno = new xenoapi.npcs.api.event.NpcEvent.InitEvent(
                new net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoNpcAdapter(npc));
        ScriptEvent event = new ScriptEvent("init", new ScriptNpc(npc, host.temp, host.timers), null, null,
                null, 0.0f, xeno);
        for (NpcScriptEngine.Instance tab : host.tabs) {
            if (tab.hasFunction("init")) {
                NpcScriptResult result = tab.call("init", event);
                if (!result.ok()) reportError(npc, host, "init: " + result.describe());
            }
        }
        JAVA_INITED.add(npc.getUUID());
        net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.post(xeno);
    }
```

In `tickTimers`, tick pending timers too and post the typed timer event:

```java
    public static void tickTimers(XenoNpcEntity npc) {
        Host host = HOSTS.get(npc.getUUID());
        SharedState pending = host == null ? PENDING.get(npc.getUUID()) : null;
        ScriptTimers timers = host != null ? host.timers : pending != null ? pending.timers() : null;
        if (timers == null) return;
        timers.tick(npc.level().getGameTime(), id -> {
            var xeno = new xenoapi.npcs.api.event.NpcEvent.TimerEvent(
                    new net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoNpcAdapter(npc), id);
            if (host != null) {
                ScriptEvent event = new ScriptEvent("timer", new ScriptNpc(npc, host.temp, host.timers),
                        null, null, null, 0, xeno);
                event.id = id;
                dispatch(npc, host, "timer", event);
            }
            net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.post(xeno);
        });
    }
```

In `forget` and `clearAll`, also clear `JAVA_INITED` (`JAVA_INITED.remove(npc)` / `JAVA_INITED.clear()`).

- [ ] **Step 4: Wire each call site.** Every change passes a typed factory as the last argument. `NpcEvent` is `xenoapi.npcs.api.event.NpcEvent`.

`XenoNpcEntity` tick:
```java
                net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(this, "tick", null, null, null, 0.0f,
                        n -> new xenoapi.npcs.api.event.NpcEvent.UpdateEvent(n));
```
Interact (keep the MAIN_HAND guard):
```java
                && net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(this, "interact", player, null, null, 0.0f,
                        n -> new xenoapi.npcs.api.event.NpcEvent.InteractEvent(n, player)))
```
Hurt. Change the `fireEvent` call and apply `clearTarget` after the existing damage read-back:
```java
            var scriptEvent = net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fireEvent(
                    this, "damaged", attacker instanceof Player ? attacker : null,
                    attacker, null, amount,
                    n -> new xenoapi.npcs.api.event.NpcEvent.DamagedEvent(n, source.getEntity(), amount, source));
            if (scriptEvent != null) {
                if (scriptEvent.isCanceled()) return false;
                float scripted = scriptEvent.getDamage();
                amount = Float.isFinite(scripted) ? Math.max(0.0f, scripted) : 0.0f;
                if (scriptEvent.xeno instanceof xenoapi.npcs.api.event.NpcEvent.DamagedEvent typedDamaged
                        && typedDamaged.clearTarget) setTarget(null);
            }
```
`amount` is reassigned, so pass a captured `final float incoming = amount;` into the lambda: declare it just before `fireEvent`.

Target:
```java
                if (net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(this, "target",
                        target instanceof Player ? target : null, null, target, 0.0f,
                        n -> new xenoapi.npcs.api.event.NpcEvent.TargetEvent(n, chosen))) return;
```
`target` is reassigned earlier in the method, so declare `final LivingEntity chosen = target;` before the `try`.

Target lost:
```java
                net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(this, "targetLost", null, null, previous, 0.0f,
                        n -> new xenoapi.npcs.api.event.NpcEvent.TargetLostEvent(n, previous));
```
Died:
```java
            net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(this, "died", killer instanceof Player ? killer : null, killer, null, 0.0f,
                    n -> new xenoapi.npcs.api.event.NpcEvent.DiedEvent(n, source, source.getEntity()));
```
Collide:
```java
            net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(this, "collide", living instanceof Player ? living : null, null, living, 0.0f,
                    n -> new xenoapi.npcs.api.event.NpcEvent.CollideEvent(n, living));
```
`NpcMeleeDamage` (declare `final float dealt = event.getNewDamage();` first):
```java
                var scriptEvent = net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fireEvent(
                        npc, "meleeAttack", target instanceof net.minecraft.world.entity.player.Player
                                ? target : null, null, target, dealt,
                        n -> new xenoapi.npcs.api.event.NpcEvent.MeleeAttackEvent(n, target, dealt));
```
`NpcKiAttackDispatcher.rangedHook`:
```java
            net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(
                    npc, "rangedLaunched", null, null, target, 0, n ->
                            new xenoapi.npcs.api.event.NpcEvent.RangedLaunchedEvent(n, target, 0.0f));
```
`NpcProfileLifecycle` kill:
```java
            net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(killer, "kill",
                    event.getEntity() instanceof net.minecraft.world.entity.player.Player
                            ? event.getEntity() : null, null, event.getEntity(), 0.0f,
                    n -> new xenoapi.npcs.api.event.NpcEvent.KilledEntityEvent(n, event.getEntity()));
```

`dialog` and `dialogOption` call sites stay unchanged; their `DialogEvent` arrives in sub-project 2.

- [ ] **Step 5: Run tests and compile**

Run: `./gradlew test --tests '*NpcScriptTypedAliasTest' --tests '*NpcScriptSharedStateTest' --tests '*XenoApiScriptCompatibilityTest' --tests '*BundledNashornTest' -PofflineMcMeta`
Expected: PASS. Then run `./gradlew compileJava -PofflineMcMeta`; expected: no errors.

- [ ] **Step 6: Record progress** (no commit).

---

### Task 6: Native player timers and the player typed-hook path

**Files:**
- Modify: `capability/XenoPlayerData.java` (field, `saveNBT`, `loadNBT`, `copyFrom`)
- Create: `npc/script/PlayerScriptTimers.java`
- Modify: `npc/script/PlayerScriptHost.java` (`hasHook`, `fireTyped`, typed-aware `fire`, `PLAYER_HOOKS`)
- Modify: `npc/script/api/xeno/XenoTimersAdapter.java` (backing interface, `forTimers`)
- Modify: `npc/script/api/xeno/XenoPlayerAdapter.java` (`getTimers`)
- Test: `npc/script/PlayerScriptTimersTest.java`

**Interfaces:**
- Produces:
  - `CompoundTag XenoPlayerData.scriptTimers()`.
  - `static ScriptTimers PlayerScriptTimers.of(ServerPlayer player)`.
  - `static void PlayerScriptTimers.forget(UUID player)`.
  - `static XenoTimersAdapter XenoTimersAdapter.forTimers(ScriptTimers timers, LongSupplier now)`.
  - `PlayerScriptHost`:
    - `public static final List<String> PLAYER_HOOKS`
    - `public static boolean hasHook(ServerPlayer player, String hook)`
    - `public static void fireTyped(ServerPlayer player, String hook, Object typed)`
    - `static ScriptEvent fire(ServerPlayer player, String hook, String message, CustomNPCsEvent xeno)`

- [ ] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptTimers;
import net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoTimersAdapter;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import xenoapi.npcs.api.CustomNPCsException;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class PlayerScriptTimersTest {
    @Test
    void playerTimersSurviveSaveAndLoadSeparatelyFromStoredData() {
        XenoPlayerData data = new XenoPlayerData();
        ScriptTimers timers = new ScriptTimers(data.scriptTimers());
        assertTrue(timers.forceStart(7, 20, false, 100));
        CompoundTag saved = new CompoundTag();
        data.saveNBT(saved);
        XenoPlayerData loaded = new XenoPlayerData();
        loaded.loadNBT(saved);
        assertTrue(new ScriptTimers(loaded.scriptTimers()).has(7));
        assertEquals(0, loaded.scriptData().size(), "timers never appear as stored script data");
    }

    @Test
    void theXenoApiViewUsesTheSameTimersAndRefusesLikeNpcTimers() {
        ScriptTimers timers = new ScriptTimers();
        long[] now = {50};
        XenoTimersAdapter api = XenoTimersAdapter.forTimers(timers, () -> now[0]);
        api.start(3, 10, false);
        assertThrows(CustomNPCsException.class, () -> api.start(3, 10, false));
        var fired = new ArrayList<Integer>();
        timers.tick(60, fired::add);
        assertEquals(java.util.List.of(3), fired);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests '*PlayerScriptTimersTest' -PofflineMcMeta`
Expected: FAIL to compile (`scriptTimers`, `forTimers` missing).

- [ ] **Step 3: Implement**

`XenoPlayerData`: add `private CompoundTag scriptTimers = new CompoundTag();` and `public CompoundTag scriptTimers() { return scriptTimers; }`. Then:
- in `copyFrom`: `this.scriptTimers = other.scriptTimers.copy();`
- in `saveNBT` after the `XenoScriptData` line: `tag.put("XenoScriptTimers", scriptTimers.copy());`
- in `loadNBT` after script data: `scriptTimers = tag.getCompound("XenoScriptTimers").copy();`

`ScriptTimers` validates entries on load, so `loadNBT` needs no bounds of its own.

`XenoTimersAdapter`: replace the field with a small backing interface. Keep the public constructor:

```java
    /** The six native timer operations, over NPC TimerView or a raw ScriptTimers. */
    interface Backing {
        boolean start(int id, int ticks, boolean repeat);
        boolean forceStart(int id, int ticks, boolean repeat);
        boolean has(int id);
        boolean stop(int id);
        boolean reset(int id);
        void clear();
    }

    private final Supplier<Backing> timers;

    public XenoTimersAdapter(Supplier<ScriptNpc.TimerView> npcTimers) {
        Objects.requireNonNull(npcTimers);
        this.timers = () -> {
            ScriptNpc.TimerView view = npcTimers.get();
            return new Backing() {
                public boolean start(int id, int ticks, boolean repeat) { return view.start(id, ticks, repeat); }
                public boolean forceStart(int id, int ticks, boolean repeat) { return view.forceStart(id, ticks, repeat); }
                public boolean has(int id) { return view.has(id); }
                public boolean stop(int id) { return view.stop(id); }
                public boolean reset(int id) { return view.reset(id); }
                public void clear() { view.clear(); }
            };
        };
    }

    private XenoTimersAdapter(Supplier<Backing> timers, boolean raw) { this.timers = timers; }

    /** A XenoAPI view over native timers read with {@code now} (player timers). */
    public static XenoTimersAdapter forTimers(ScriptTimers timers, java.util.function.LongSupplier now) {
        Objects.requireNonNull(timers);
        Backing backing = new Backing() {
            public boolean start(int id, int ticks, boolean repeat) { return timers.start(id, ticks, repeat, now.getAsLong()); }
            public boolean forceStart(int id, int ticks, boolean repeat) { return timers.forceStart(id, ticks, repeat, now.getAsLong()); }
            public boolean has(int id) { return timers.has(id); }
            public boolean stop(int id) { return timers.stop(id); }
            public boolean reset(int id) { return timers.reset(id, now.getAsLong()); }
            public void clear() { timers.clear(); }
        };
        return new XenoTimersAdapter(() -> backing, true);
    }
```

In the six `ITimers` methods, replace `timers.get()` (formerly a `TimerView`) with the `Backing` the supplier returns. The logic is unchanged: `start` throws when `has(id)` or when native start refuses; `reset` throws when native reset refuses. Import `ScriptTimers`.

`PlayerScriptTimers.java`:

```java
package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptTimers;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Native player timers, saved in the player's XenoScriptTimers and ticked every server tick. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlayerScriptTimers {
    private static final Map<UUID, ScriptTimers> TIMERS = new ConcurrentHashMap<>();

    private PlayerScriptTimers() {}

    public static ScriptTimers of(ServerPlayer player) {
        return TIMERS.computeIfAbsent(player.getUUID(), ignored -> new ScriptTimers(
                XenoCapabilities.get(player).orElseThrow().scriptTimers()));
    }

    public static void forget(UUID player) { TIMERS.remove(player); }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ScriptTimers timers = TIMERS.get(player.getUUID());
        if (timers == null) return;
        timers.tick(player.level().getGameTime(), id -> {
            var typed = new xenoapi.npcs.api.event.PlayerEvent.TimerEvent(
                    (xenoapi.npcs.api.entity.IPlayer<?>) net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoApiAdapters.wrap(player), id);
            PlayerScriptHost.fireTyped(player, "timer", typed);
            net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.post(typed);
        });
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) { TIMERS.clear(); }
}
```

Timers are ticked only after something asked for them through `of`, for example `IPlayer.getTimers`. `TIMERS` then holds them until logout. On login, call `PlayerScriptTimers.of(player)` so saved timers resume (next paragraph).

`PlayerScriptHost`:
- Add `PLAYER_HOOKS = List.of("init", "tick", "interact", "attack", "broken", "toss", "pickedUp", "containerOpen", "containerClosed", "damagedEntity", "rangedLaunched", "died", "kill", "damaged", "timer", "login", "logout", "levelUp", "chat")`.
- Add:

```java
    public static boolean hasHook(ServerPlayer player, String hook) {
        for (Tab tab : host(player).tabs) {
            if (tab.chatOnly && !hook.equals("chat")) continue;
            if (tab.instance.hasFunction(hook)) return true;
        }
        return false;
    }

    /** A new-style hook: the typed XenoAPI event is the argument. */
    public static void fireTyped(ServerPlayer player, String hook, Object typed) {
        for (Tab tab : host(player).tabs) {
            if (tab.chatOnly && !hook.equals("chat")) continue;
            if (!tab.instance.hasFunction(hook)) continue;
            NpcScriptResult result = tab.instance.call(hook, typed);
            if (!result.ok()) XenoPixelsMod.LOGGER.error("Player script {} {}: {}", tab.id, hook, result.describe());
        }
    }
```

- Change `fire(player, hook, message)` to `fire(player, hook, message, xeno)`. It builds `new ScriptEvent(hook, null, ScriptEntity.of(player), null, null, 0, xeno)`. After the tab loop, it runs `if (xeno != null) { XenoEventDispatch.post(xeno); event.syncFromXeno(); }`, then the chat block below.
- In `onLogin`:

```java
            IPlayer<?> api = (IPlayer<?>) XenoApiAdapters.wrap(player);
            PlayerScriptTimers.of(player);
            fire(player, "init", null, new xenoapi.npcs.api.event.PlayerEvent.InitEvent(api));
            fire(player, "login", null, new xenoapi.npcs.api.event.PlayerEvent.LoginEvent(api));
```

- In `onLogout`: `fire(player, "logout", null, new PlayerEvent.LogoutEvent(api))`, then also `PlayerScriptTimers.forget(player.getUUID())`.
- In both chat paths, pass `new PlayerEvent.ChatEvent(api, raw)`. The final message is chosen explicitly after the post, in `fire`:

```java
        if (xeno instanceof xenoapi.npcs.api.event.PlayerEvent.ChatEvent chat
                && chat.message != null && !chat.message.equals(message)) {
            // A Java listener rewrote the line after the scripts ran; it runs last, so it wins.
            event.message = chat.message;
        }
```

  Old scripts still rewrite through `event.message`, and a cancel from either side goes through `syncFromXeno`.

`XenoPlayerAdapter.getTimers`:

```java
    @Override
    public ITimers getTimers() {
        serverThread();
        return XenoTimersAdapter.forTimers(net.bullettrain.xenopixelsmod.npc.script.PlayerScriptTimers.of(entity),
                () -> entity.level().getGameTime());
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests '*PlayerScriptTimersTest' --tests '*XenoApiAdaptersTest' --tests '*PlayerScriptDataTest' -PofflineMcMeta`
Expected: PASS. The existing timer test in `XenoApiAdaptersTest` still uses the public `TimerView` constructor.

- [ ] **Step 5: Record progress** (no commit).

---

### Task 7: Minimal `IBlock` and `IContainer`

**Files:**
- Create: `npc/script/api/xeno/XenoBlockAdapter.java`
- Create: `npc/script/api/xeno/XenoContainerAdapter.java`
- Modify: `NativeNpcApi` (`getIBlock`, both `getIContainer`), `XenoWorldAdapter` (`getBlock(int,int,int)`, `getBlock(IPos)`), `XenoPlayerAdapter` (`getInventory`, `getOpenContainer`)
- Test: `npc/script/api/xeno/XenoContainerAdapterTest.java`

**Interfaces:**
- Produces:
  - `XenoBlockAdapter(ServerLevel level, BlockPos pos)` implements `IBlock`.
  - `XenoContainerAdapter.of(Container container)` and `XenoContainerAdapter.of(AbstractContainerMenu menu)` return `IContainer`, or null for null.

- [ ] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XenoContainerAdapterTest {
    @Test
    void containerReadsAndBoundedWritesReachTheBackingInventory() {
        SimpleContainer inventory = new SimpleContainer(3);
        inventory.setItem(1, new ItemStack(Items.STONE, 4));
        var container = XenoContainerAdapter.of(inventory);
        assertEquals(3, container.getSize());
        assertEquals(4, container.getSlot(1).getStackSize());
        assertEquals(4, container.count(XenoApiAdapters.wrap(new ItemStack(Items.STONE)), true, true));
        container.setSlot(0, XenoApiAdapters.wrap(new ItemStack(Items.DIRT, 2)));
        assertEquals(Items.DIRT, inventory.getItem(0).getItem());
        assertThrows(IllegalArgumentException.class, () -> container.setSlot(3, null));
        assertThrows(IllegalArgumentException.class, () -> container.getSlot(-1));
        assertEquals(3, container.getItems().length);
        assertThrows(UnsupportedOperationException.class, container::getMCInventory);
        assertNull(XenoContainerAdapter.of((net.minecraft.world.Container) null));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests '*XenoContainerAdapterTest' -PofflineMcMeta`
Expected: FAIL to compile.

- [ ] **Step 3: Implement**

`XenoContainerAdapter.java`:

```java
package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import xenoapi.npcs.api.IContainer;
import xenoapi.npcs.api.item.IItemStack;

import java.util.function.IntFunction;

/** A container or menu as XenoAPI's IContainer; slot reads are live stacks. */
public final class XenoContainerAdapter implements IContainer {
    private final int size;
    private final IntFunction<ItemStack> get;
    private final java.util.function.BiConsumer<Integer, ItemStack> set;

    private XenoContainerAdapter(int size, IntFunction<ItemStack> get, java.util.function.BiConsumer<Integer, ItemStack> set) {
        this.size = size;
        this.get = get;
        this.set = set;
    }

    public static IContainer of(Container container) {
        return container == null ? null : new XenoContainerAdapter(container.getContainerSize(),
                container::getItem, (slot, stack) -> { container.setItem(slot, stack); container.setChanged(); });
    }

    public static IContainer of(AbstractContainerMenu menu) {
        return menu == null ? null : new XenoContainerAdapter(menu.slots.size(),
                slot -> menu.getSlot(slot).getItem(), (slot, stack) -> { menu.getSlot(slot).set(stack); menu.broadcastChanges(); });
    }

    private int slot(int slot) {
        if (slot < 0 || slot >= size) throw new IllegalArgumentException("Slot must be 0-" + (size - 1) + ", got " + slot);
        return slot;
    }

    @Override public int getSize() { return size; }
    @Override public IItemStack getSlot(int slot) { return XenoApiAdapters.wrap(get.apply(slot(slot))); }
    @Override public void setSlot(int slot, IItemStack item) { set.accept(slot(slot), XenoApiAdapters.unwrap(item).copy()); }

    @Override
    public int count(IItemStack item, boolean ignoreDamage, boolean ignoreNBT) {
        int total = 0;
        for (int i = 0; i < size; i++) {
            ItemStack stack = get.apply(i);
            if (!stack.isEmpty() && item.compare(stack, ignoreNBT, ignoreDamage)) total += stack.getCount();
        }
        return total;
    }

    @Override
    public IItemStack[] getItems() {
        IItemStack[] items = new IItemStack[size];
        for (int i = 0; i < size; i++) items[i] = XenoApiAdapters.wrap(get.apply(i));
        return items;
    }

    @Override public Container getMCInventory() { throw XenoApiAdapters.unsupported("IContainer.getMCInventory (raw handles are not exposed)"); }
    @Override public AbstractContainerMenu getMCContainer() { throw XenoApiAdapters.unsupported("IContainer.getMCContainer (raw handles are not exposed)"); }
}
```

`XenoBlockAdapter.java`: native reads per the spec. Every other contract method throws `XenoApiAdapters.unsupported("IBlock.<name>")`.

```java
package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import xenoapi.npcs.api.IContainer;
import xenoapi.npcs.api.INbt;
import xenoapi.npcs.api.IPos;
import xenoapi.npcs.api.IWorld;
import xenoapi.npcs.api.block.IBlock;
import xenoapi.npcs.api.entity.IEntityLiving;
import xenoapi.npcs.api.entity.data.IData;

import java.util.Objects;

/** A block position as XenoAPI's IBlock, read-mostly in sub-project 1. The state is read live. */
public final class XenoBlockAdapter implements IBlock {
    private final ServerLevel level;
    private final BlockPos pos;

    public XenoBlockAdapter(ServerLevel level, BlockPos pos) {
        this.level = Objects.requireNonNull(level);
        this.pos = Objects.requireNonNull(pos).immutable();
    }

    private BlockState state() { return level.getBlockState(pos); }

    @Override public int getX() { return pos.getX(); }
    @Override public int getY() { return pos.getY(); }
    @Override public int getZ() { return pos.getZ(); }
    @Override public IPos getPos() { return new XenoPosAdapter(pos); }
    @Override public String getName() { return BuiltInRegistries.BLOCK.getKey(state().getBlock()).toString(); }
    @Override public String getDisplayName() { return state().getBlock().getName().getString(); }
    @Override public boolean isAir() { return state().isAir(); }
    @Override public boolean isRemoved() { return state().isAir(); }
    @Override public IWorld getWorld() { return XenoApiAdapters.wrap(level); }
    @Override public boolean hasTileEntity() { return level.getBlockEntity(pos) != null; }
    @Override public boolean isContainer() { return level.getBlockEntity(pos) instanceof Container; }

    @Override
    public IContainer getContainer() {
        return level.getBlockEntity(pos) instanceof Container container ? XenoContainerAdapter.of(container) : null;
    }

    /** The property's current value as text, or null for a property this block does not have. */
    @Override
    public Object getProperty(String name) {
        for (Property<?> property : state().getProperties()) {
            if (property.getName().equals(name)) return String.valueOf(state().getValue(property));
        }
        return null;
    }

    @Override
    public String[] getProperties() {
        return state().getProperties().stream().map(Property::getName).toArray(String[]::new);
    }

    /** A detached snapshot of the block entity's save data; null without a block entity. */
    @Override
    public INbt getBlockEntityNBT() {
        BlockEntity be = level.getBlockEntity(pos);
        return be == null ? null : XenoApiAdapters.wrap(be.saveWithFullMetadata(level.registryAccess()));
    }

    @Override public boolean equals(Object o) { return o instanceof XenoBlockAdapter b && b.level == level && b.pos.equals(pos); }
    @Override public int hashCode() { return pos.hashCode(); }

    @Override public void setProperty(String name, Object val) { throw XenoApiAdapters.unsupported("IBlock.setProperty"); }
    @Override public void remove() { throw XenoApiAdapters.unsupported("IBlock.remove (use IWorld.removeBlock)"); }
    @Override public IBlock setBlock(String name) { throw XenoApiAdapters.unsupported("IBlock.setBlock"); }
    @Override public IBlock setBlock(IBlock block) { throw XenoApiAdapters.unsupported("IBlock.setBlock"); }
    @Override public IData getTempdata() { throw XenoApiAdapters.unsupported("IBlock.getTempdata"); }
    @Override public IData getStoreddata() { throw XenoApiAdapters.unsupported("IBlock.getStoreddata"); }
    @Override public void setTileEntityNBT(INbt nbt) { throw XenoApiAdapters.unsupported("IBlock.setTileEntityNBT"); }
    @Override public BlockEntity getMCTileEntity() { throw XenoApiAdapters.unsupported("IBlock.getMCTileEntity (raw handles are not exposed)"); }
    @Override public Block getMCBlock() { throw XenoApiAdapters.unsupported("IBlock.getMCBlock (raw handles are not exposed)"); }
    @Override public BlockState getMCBlockState() { throw XenoApiAdapters.unsupported("IBlock.getMCBlockState (raw handles are not exposed)"); }
    @Override public void blockEvent(int type, int data) { throw XenoApiAdapters.unsupported("IBlock.blockEvent"); }
    @Override public void interact(int side, IEntityLiving entity) { throw XenoApiAdapters.unsupported("IBlock.interact"); }
    @Override public void setChanged() { throw XenoApiAdapters.unsupported("IBlock.setChanged"); }
}
```

If `javap` shows the `IBlock` method set differs from the 2026-09-27 listing (`getX … setChanged`, 28 methods), follow the compiler's list.

Wire:
- `NativeNpcApi.getIBlock`: `if (!(level instanceof ServerLevel server)) throw new IllegalArgumentException("XenoAPI getIBlock needs a server level"); return new XenoBlockAdapter(server, pos);`
- `NativeNpcApi.getIContainer(Container)`: `XenoContainerAdapter.of(container)`.
- `NativeNpcApi.getIContainer(AbstractContainerMenu)`: `XenoContainerAdapter.of(container)`.
- `XenoWorldAdapter.getBlock(int x, int y, int z)`: `new XenoBlockAdapter(level, new BlockPos(x, y, z))`.
- `XenoWorldAdapter.getBlock(IPos)`: same, from the pos coordinates.
- `XenoPlayerAdapter.getInventory`: `XenoContainerAdapter.of(entity.getInventory())`.
- `XenoPlayerAdapter.getOpenContainer`: `XenoContainerAdapter.of(entity.containerMenu)`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests '*XenoContainerAdapterTest' --tests '*NativeNpcApiTest' -PofflineMcMeta`
Expected: PASS. Then update `NativeNpcApiTest.unsupportedFacilitiesNameTheirMethod` if it asserted on `getIBlock`/`getIContainer`; it asserts `getFactions`, `createMail` and `registerScriptEvent`, which stay unsupported.

- [ ] **Step 5: Record progress** (no commit).

---

### Task 8: Player event subscriber

**Files:**
- Create: `npc/script/api/xeno/event/PlayerXenoEvents.java`
- Test: `npc/script/api/xeno/event/PlayerXenoEventsTest.java`

**Interfaces:**
- Consumes: `PlayerScriptHost.{hasHook, fireTyped}`, `XenoEventDispatch.{wanted, post, isCanceled, damage}`, `XenoApiAdapters.wrap`, `XenoBlockAdapter`, `XenoContainerAdapter`.
- Produces: `static boolean PlayerXenoEvents.returnTossed(net.minecraft.world.entity.player.Inventory inventory, ItemStack stack)`. It is true when the stack went back into the inventory.

- [ ] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.npc.script.api.xeno.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerXenoEventsTest {
    @Test
    void mainHandFilterAcceptsOnlyTheMainHand() {
        assertTrue(PlayerXenoEvents.mainHand(net.minecraft.world.InteractionHand.MAIN_HAND));
        assertFalse(PlayerXenoEvents.mainHand(net.minecraft.world.InteractionHand.OFF_HAND));
    }

    @Test
    void breakExperienceIsMatchedToTheSameBreak() {
        var key = PlayerXenoEvents.breakKey("minecraft:overworld", new net.minecraft.core.BlockPos(1, 2, 3), java.util.UUID.randomUUID(), 40L);
        PlayerXenoEvents.rememberBreakExp(key, 7);
        assertEquals(7, PlayerXenoEvents.takeBreakExp(key));
        assertNull(PlayerXenoEvents.takeBreakExp(key), "taken once");
    }
}
```

`returnTossed` needs a real player inventory. It is covered by runtime checklist item P-toss rather than a unit test, because `Inventory` requires a `Player`. The helper stays small so the runtime check is conclusive.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests '*PlayerXenoEventsTest' -PofflineMcMeta`
Expected: FAIL to compile.

- [ ] **Step 3: Implement `PlayerXenoEvents`**

```java
package net.bullettrain.xenopixelsmod.npc.script.api.xeno.event;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.npc.script.PlayerScriptHost;
import net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoApiAdapters;
import net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoBlockAdapter;
import net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoContainerAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.event.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Posts typed PlayerEvents from NeoForge events: scripts first, then Java, then read-back. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlayerXenoEvents {
    private static final Map<String, Integer> BREAK_EXP = new ConcurrentHashMap<>();

    private PlayerXenoEvents() {}

    static boolean mainHand(InteractionHand hand) { return hand == InteractionHand.MAIN_HAND; }

    static String breakKey(String dimension, BlockPos pos, UUID player, long gameTime) {
        return dimension + "|" + pos.asLong() + "|" + player + "|" + gameTime;
    }

    static void rememberBreakExp(String key, int exp) { BREAK_EXP.put(key, exp); }
    static Integer takeBreakExp(String key) { return BREAK_EXP.remove(key); }

    /** Puts a cancelled toss back; false when the inventory cannot take it. */
    static boolean returnTossed(net.minecraft.world.entity.player.Inventory inventory, ItemStack stack) {
        return inventory.add(stack);
    }

    private static IPlayer<?> api(ServerPlayer player) {
        return (IPlayer<?>) XenoApiAdapters.wrap(player);
    }

    /** Scripts (new hook), then Java. Returns the event for read-back, or null when unwanted. */
    private static <E extends xenoapi.npcs.api.event.CustomNPCsEvent> E deliver(
            ServerPlayer player, String hook, java.util.function.Supplier<E> build) {
        boolean scripted = PlayerScriptHost.hasHook(player, hook);
        if (!XenoEventDispatch.wanted(scripted)) return null;
        if (!XenoEventDispatch.enter(player.getUUID(), hook)) return null;
        try {
            E event = build.get();
            if (scripted) PlayerScriptHost.fireTyped(player, hook, event);
            return XenoEventDispatch.post(event);
        } finally {
            XenoEventDispatch.exit(player.getUUID(), hook);
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.tickCount % net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.TICK_INTERVAL == 0) {
            deliver(player, "tick", () -> new PlayerEvent.UpdateEvent(api(player)));
        }
    }

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !mainHand(event.getHand())) return;
        var typed = deliver(player, "interact", () -> new PlayerEvent.InteractEvent(api(player), 1, XenoApiAdapters.wrap(event.getTarget())));
        if (typed != null && typed.isCanceled()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !mainHand(event.getHand())) return;
        var typed = deliver(player, "interact", () -> new PlayerEvent.InteractEvent(api(player), 2,
                new XenoBlockAdapter(player.serverLevel(), event.getPos())));
        if (typed != null && typed.isCanceled()) event.setCanceled(true);
    }

    /** Right-click air with an item: NeoForge does not fire this when a block or entity is targeted. */
    @SubscribeEvent
    public static void onInteractItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !mainHand(event.getHand())) return;
        var typed = deliver(player, "interact", () -> new PlayerEvent.InteractEvent(api(player), 0, null));
        if (typed != null && typed.isCanceled()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var typed = deliver(player, "attack", () -> new PlayerEvent.AttackEvent(api(player), 1, XenoApiAdapters.wrap(event.getTarget())));
        if (typed != null && typed.isCanceled()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttackBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        var typed = deliver(player, "attack", () -> new PlayerEvent.AttackEvent(api(player), 2,
                new XenoBlockAdapter(player.serverLevel(), event.getPos())));
        if (typed != null && typed.isCanceled()) event.setCanceled(true);
    }

    /** Attacker's damagedEntity first, then the victim's damaged; the second sees the first's damage. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            float amount = event.getAmount();
            var typed = deliver(attacker, "damagedEntity", () -> new PlayerEvent.DamagedEntityEvent(api(attacker),
                    event.getEntity(), amount, event.getSource()));
            if (typed != null) {
                if (typed.isCanceled()) { event.setCanceled(true); return; }
                event.setAmount(Math.max(0.0f, Float.isFinite(typed.damage) ? typed.damage : amount));
            }
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            float amount = event.getAmount();
            var typed = deliver(victim, "damaged", () -> new PlayerEvent.DamagedEvent(api(victim),
                    event.getSource().getEntity(), amount, event.getSource()));
            if (typed != null) {
                if (typed.isCanceled()) { event.setCanceled(true); return; }
                event.setAmount(Math.max(0.0f, Float.isFinite(typed.damage) ? typed.damage : amount));
                if (typed.clearTarget && event.getSource().getEntity() instanceof Mob mob && mob.getTarget() == victim) {
                    mob.setTarget(null);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof ServerPlayer victim) {
            var typed = deliver(victim, "died", () -> new PlayerEvent.DiedEvent(api(victim), event.getSource(), event.getSource().getEntity()));
            if (typed != null && typed.isCanceled()) {
                event.setCanceled(true);
                victim.setHealth(1.0f);   // a cancelled death must not repeat next tick
                return;
            }
        }
        if (event.getSource().getEntity() instanceof ServerPlayer killer && killer != event.getEntity()) {
            LivingEntity victim = event.getEntity();
            deliver(killer, "kill", () -> new PlayerEvent.KilledEntityEvent(api(killer), victim));
        }
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getEntity().getItem();
        var typed = deliver(player, "toss", () -> new PlayerEvent.TossEvent(api(player), XenoApiAdapters.wrap(stack)));
        if (typed == null || !typed.isCanceled()) return;
        // CommonHooks.onPlayerTossEvent already removed the stack; a plain cancel would delete it.
        if (returnTossed(player.getInventory(), stack.copy())) {
            event.setCanceled(true);
            player.inventoryMenu.broadcastChanges();
        } else {
            XenoPixelsMod.LOGGER.info("XenoAPI toss cancel ignored for {}: inventory full, item dropped", player.getScoreboardName());
        }
    }

    @SubscribeEvent
    public static void onPickUp(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemEntity().getItem();
        var typed = deliver(player, "pickedUp", () -> new PlayerEvent.PickUpEvent(api(player), XenoApiAdapters.wrap(stack)));
        if (typed != null && typed.isCanceled()) event.setCanPickup(TriState.FALSE);
    }

    @SubscribeEvent
    public static void onRangedLaunch(ArrowLooseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var typed = deliver(player, "rangedLaunched", () -> new PlayerEvent.RangedLaunchedEvent(api(player)));
        if (typed != null && typed.isCanceled()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLevelChange(PlayerXpEvent.LevelChange event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int change = event.getLevels();
        // XenoAPI's LevelUpEvent is not cancellable (verified in PlayerEvent.java); notify only.
        deliver(player, "levelUp", () -> new PlayerEvent.LevelUpEvent(api(player), change));
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        int exp = event.getState().getExpDrop(level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
        var typed = deliver(player, "broken", () -> new PlayerEvent.BreakEvent(api(player), new XenoBlockAdapter(level, pos), exp));
        if (typed == null) return;
        if (typed.isCanceled()) { event.setCanceled(true); return; }
        if (typed.exp != exp) {
            rememberBreakExp(breakKey(level.dimension().location().toString(), pos, player.getUUID(), level.getGameTime()),
                    Math.max(0, typed.exp));
        }
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;
        Integer exp = takeBreakExp(breakKey(event.getLevel().dimension().location().toString(), event.getPos(),
                player.getUUID(), event.getLevel().getGameTime()));
        if (exp != null) event.setDroppedExperience(exp);
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            deliver(player, "containerOpen", () -> new PlayerEvent.ContainerOpen(api(player), XenoContainerAdapter.of(event.getContainer())));
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            deliver(player, "containerClosed", () -> new PlayerEvent.ContainerClosed(api(player), XenoContainerAdapter.of(event.getContainer())));
        }
    }
}
```

Field access such as `typed.damage`, `typed.clearTarget` and `typed.exp` uses the public fields verified on 2026-09-27 (`PlayerEvent.java` lines 168, 223-224, and `BreakEvent.exp`). If `BreakEvent.exp` is `final`, the compiler will say so. In that case, drop the `rememberBreakExp` branch and document `exp` as read-only in the capability doc.

`BlockEvent.getLevel()` returns `LevelAccessor`, hence the `instanceof ServerLevel` check.

Stale break-exp entries are removed by `takeBreakExp`. Entries for breaks that dropped nothing expire: add `BREAK_EXP.clear()` to a `ServerTickEvent.Post` handler every 200 ticks, or on `ServerStoppedEvent` at minimum. Implement both.

- [ ] **Step 4: Run tests and compile**

Run: `./gradlew test --tests '*PlayerXenoEventsTest' -PofflineMcMeta` and `./gradlew compileJava -PofflineMcMeta`
Expected: PASS, and no compile errors.

- [ ] **Step 5: Record progress** (no commit).

---

### Task 9: Surface, docs, consumer, validation and runtime evidence

**Files:**
- Modify: `examples/xenopixels-api-addon/src/main/java/net/bullettrain/xenopixels/example/ApiExampleAddon.java` (per-class counters, `/xenoapitest events`)
- Create: `examples/customnpcs/xenoapi_player_events.js`
- Modify: `examples/customnpcs/README.md`, `docs/native-xenoapi-adapters.md` (regenerate the capability table; add an events section)
- Modify: `src/test/java/.../npc/script/api/xeno/ExampleScriptsCompileTest.java` (expected count 3)
- Create: `ai/handoff-xenoapi-event-foundation-2026-09-27.md` (repository template)

- [ ] **Step 1: Example addon listeners.** In the constructor, after the existing listeners:

```java
        if (NpcAPI.IsAvailable()) {
            NpcAPI.Instance().events().addListener(xenoapi.npcs.api.event.CustomNPCsEvent.class,
                    event -> observed("xenoapi." + event.getClass().getSimpleName(), ""));
        }
```

Add a subcommand next to `xenoapi`:

```java
                .then(Commands.literal("events")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            COUNTS.entrySet().stream().filter(e -> e.getKey().startsWith("xenoapi."))
                                    .sorted(Map.Entry.comparingByKey())
                                    .forEach(e -> player.sendSystemMessage(Component.literal(e.getKey() + "=" + e.getValue().get())));
                            return 1;
                        }))
```

`observed` logs at INFO per event. Keep that as it is: the runtime checklist greps these lines.

The addon may construct before XenoPixels registers the API, and `NpcAPI.IsAvailable()` would then be false. If so, move the listener registration into an `FMLCommonSetupEvent` listener on the mod bus: `modEventBus.addListener(FMLCommonSetupEvent.class, e -> …)`. The constructor then takes `IEventBus modEventBus`.

- [ ] **Step 2: Example script** `examples/customnpcs/xenoapi_player_events.js`, for Global → Player Scripts:

```js
/**
 * XenoAPI player events for NATIVE Xeno player scripts (Global -> Player Scripts).
 * New hooks receive the typed XenoAPI event; existing hooks (init, login, logout, chat)
 * keep today's event and add event.xeno.
 */
"use strict";

function login(event) {
    if (event.xeno != null) event.xeno.player.message("XenoAPI events are on.");
}

function broken(event) {
    event.player.message("Broke " + event.block.getName() + " for " + event.exp + " xp.");
}

function damaged(event) {
    if (event.damage > 10) event.damage = 10;       // cap incoming hits at 5 hearts
}

function toss(event) {
    if (event.item.getName() === "minecraft:diamond") {
        event.setCanceled(true);                     // the diamond goes back to the inventory
        event.player.message("Diamonds stay with you.");
    }
}

function levelUp(event) {
    event.player.message("Level change: " + event.change);
    event.player.getTimers().forceStart(1, 40, false);
}

function timer(event) {
    if (event.id === 1) event.player.message("Two seconds after your level change.");
}
```

Set the expected count in `ExampleScriptsCompileTest` to 3 and list the file in `examples/customnpcs/README.md`.

- [ ] **Step 3: Docs.** Regenerate the capability table with the brace-matching extraction recorded in the 2026-09-27 session. Store the script as `scripts/xenoapi_capabilities.py` so it is reproducible: it reads each adapter under `npc/script/api/xeno/`, counts `@Override` methods whose body starts with `throw XenoApiAdapters.unsupported`, and writes the tables. Add an "Events" section to `docs/native-xenoapi-adapters.md` with the 12 NPC and 19 player mappings from the spec, the rules, and the fields with no native source.

- [ ] **Step 4: Validation ladder**

Run in order and record exit codes:
- `./gradlew test -PofflineMcMeta`. Expected: all new tests pass. The known unrelated `SceneTriggersTest.damageThatDidNotLandDoesNotFire` may still fail; record it and do not fix it.
- `./gradlew build jarJar serverJar -PofflineMcMeta -x test`
- `./gradlew buildApiExampleAddon -PofflineMcMeta`
- Archive checks: one `xenoapi/npcs/api/NpcAPI.class` per distribution jar, 0 duplicate paths, 0 `META-INF/jarjar/` files in the server jar. Record bytes and SHA-256.
- Dedicated server: `./gradlew runServer -PofflineMcMeta` (if the run config exists; otherwise record "not run"). Wait for `Done (` in `run/logs/latest.log`, then check it for `XenoAPI: native Xeno NPC implementation registered` and for no `XenoAPI` errors. Then stop it.

- [ ] **Step 5: Runtime checklist.** Start a fresh `./gradlew runApiTestClient -PofflineMcMeta`, join a world, and paste `xenoapi_player_events.js` into Player Scripts plus `xenoapi_native_greeter.js` into an NPC. For each item, record the `latest.log` line `[xeno-api-example] xenoapi.<Class> count=` with its timestamp, or **not verified**:

  - NPC: Init, Update, Interact, Damaged, Died, KilledEntity, Target, TargetLost, Collide, MeleeAttack, RangedLaunched, Timer.
  - Player: Init, Login, Logout (rejoin), Update, Chat, Interact (0 item in air, 1 entity, 2 block), Attack (1 entity, 2 block), DamagedEntity, Damaged, Died, KilledEntity, Toss, PickUp, RangedLaunched (bow), LevelUp, Break, ContainerOpen, ContainerClosed, Timer.
  - P-toss: toss a diamond with the example script → the diamond is back in the inventory and none is on the ground.
  - P-died: cancel death from a test script → the player stands at 1 health and does not die again next tick.
  - P-damaged: take a 20-damage hit → at most 10 is applied.
  - Profiler: with 20 scripted native NPCs in view and the addon listener registered, run `/debug start`, wait 60 s, then `/debug stop`. Record mean tick time from the report in `run/debug/` as measured. No TPS claim beyond that number.

- [ ] **Step 6: Handoff.** Write `ai/handoff-xenoapi-event-foundation-2026-09-27.md` from `ai/handoff-template.md`. It records branch, HEAD, dirty paths touched, commands with results, artifact hashes, each runtime line observed, every **not verified** item, and next steps (sub-project 2 spec). Run `git status --short` and confirm nothing is staged.

---

## Self-review

- **Spec coverage.**
  - Intent and constraints: Global Constraints.
  - Architecture (ordering, write-through): Task 4.
  - NPC events: Task 5.
  - Player mapping: Task 8, with login/logout/chat/init in Task 6.
  - Rules:
    - hands, toss, damage order and death: Task 8.
    - break exp: Task 8.
    - air clicks: Task 8 type 0 via `RightClickItem`.
  - NPC event data (DiedEvent line/drops, RangedLaunched projectiles, clearTarget): Task 5. `clearTarget` is honoured in hurt. Other fields keep their constructor values and are documented in Task 9.
  - Player timers: Task 6.
  - Minimal IBlock/IContainer: Task 7.
  - Cost controls: Task 4 (gate, bus counter) and Task 5/8 (re-entry, 10-tick update).
  - Profiler sample: Task 9 Step 5.
  - Errors: Task 4 (listener exceptions) and the existing script-error path.
  - Adapter alignment: Tasks 1-3.
  - Testing/acceptance: Task 9.
- **Placeholders.** Three steps depend on how the compiler reads the exact artifact (`Entry` accessors, `BreakEvent.exp` finality, addon construction order). Each names the command that decides and the alternative. None is left open.
- **Type consistency.** These names are used identically across tasks: `XenoEventDispatch.post/wanted/enter/exit/damage/setDamage/isCanceled/setCanceled`, `XenoEventBus.hasListeners`, `NativeNpcApi.eventBus`, `ScriptEvent.xeno/syncFromXeno`, `PlayerScriptHost.hasHook/fireTyped`, `PlayerScriptTimers.of/forget`, `XenoTimersAdapter.forTimers`, `XenoContainerAdapter.of`, `XenoBlockAdapter(ServerLevel, BlockPos)`, `XenoDataAdapter.ofView`, `XenoBoundedData.stored/temp`, `XenoApiAdapters.knownItem`.
- **Review Focus.** Each of the five lines names its owning task and test or runtime item.
