package net.bullettrain.xenopixelsmod.npc.script;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the actual example in Nashorn; fake entities test logic, not in-game clone placement. */
class LordSlugScriptTest {
    public static final class World {
        public long time;
        public int spawns;
        public boolean missing;
        public double spawnX;
        public int spawnTab;
        public String spawnName;
        public long getTotalTime() { return time; }
        public Npc spawnClone(double x, double y, double z, int tab, String name) {
            spawns++;
            spawnX = x;
            spawnTab = tab;
            spawnName = name;
            return missing ? null : new Npc(this);
        }
    }
    public static final class Npc {
        public final World world;
        public float hp = 100, max = 200;
        public boolean removed;
        public Npc target;
        public String name = "Lord Slug";
        public Npc(World world) { this.world = world; }
        public World getWorld() { return world; }
        public float getHealth() { return hp; }
        public float getMaxHealth() { return max; }
        public void setHealth(float value) { hp = value; }
        public void setMaxHealth(float value) { max = value; }
        public boolean isKilled() { return hp <= 0 || removed; }
        public boolean isAlive() { return !isKilled(); }
        public Npc getAttackTarget() { return target; }
        public String getName() { return name; }
        public double getX() { return 1.75; }
        public double getY() { return 64; }
        public double getZ() { return -2.25; }
        public void despawn() { removed = true; }
    }
    public static final class Event {
        public final Npc npc;
        public Event(Npc npc) { this.npc = npc; }
    }
    public static final class Log {
        public String message;
        public void line(String value) { message = value; }
    }
    @AfterEach void reset() { NpcScriptEngines.reset(); }

    private NpcScriptEngine.Instance load(Log log) throws Exception {
        Path path = Path.of(System.getProperty("xenopixels.projectDir"), "examples/customnpcs/xenopixels_lord_slug_regen.js");
        var instance = NpcScriptEngines.current().instantiate(Files.readString(path)
                + "\nCONFIG.passiveRegenPerSecond = 10;", NpcScriptScope.builder().put("log", log).build());
        assertTrue(instance.ok(), instance.loadResult().describe());
        return instance;
    }
    private void call(NpcScriptEngine.Instance script, String hook, Event event) {
        var result = script.call(hook, event);
        assertTrue(result.ok(), result.describe());
    }

    @Test void elapsedTicksHealWithoutDuplicateTickOrCombatHealing() throws Exception {
        var world = new World();
        var npc = new Npc(world);
        var event = new Event(npc);
        var script = load(new Log());
        call(script, "init", event);
        world.time = 10;
        call(script, "tick", event);
        assertEquals(105, npc.hp);
        call(script, "tick", event);
        assertEquals(105, npc.hp);
        call(script, "damaged", event);
        world.time = 20;
        call(script, "tick", event);
        assertEquals(105, npc.hp);
        world.time = 140;
        call(script, "tick", event);
        assertEquals(155, npc.hp, "a long pause is bounded to five seconds of healing");
        npc.hp = 0;
        world.time = 150;
        call(script, "tick", event);
        assertEquals(0, npc.hp, "dead NPCs cannot be revived by regeneration");
    }
    @Test void deathPlacesExactlyOneCloneBeforeRemovingOriginal() throws Exception {
        var world = new World();
        var npc = new Npc(world);
        var event = new Event(npc);
        var script = load(new Log());
        call(script, "init", event);
        npc.hp = 0;
        call(script, "died", event);
        call(script, "died", event);
        assertEquals(1, world.spawns);
        assertEquals(1.75, world.spawnX);
        assertEquals(1, world.spawnTab);
        assertEquals("Lord Slug (Giant)", world.spawnName);
        assertTrue(npc.removed);
    }
    @Test void missingCloneAndGiantDoNotRemoveOrSpawnReplacement() throws Exception {
        var world = new World();
        world.missing = true;
        var npc = new Npc(world);
        var log = new Log();
        var script = load(log);
        call(script, "died", new Event(npc));
        assertFalse(npc.removed);
        assertTrue(log.message.contains("Clone not found"));
        npc.name = "Lord Slug (Giant)";
        call(script, "init", new Event(npc));
        call(script, "died", new Event(npc));
        assertEquals(1, world.spawns, "giant replacement is suppressed");
    }
}
