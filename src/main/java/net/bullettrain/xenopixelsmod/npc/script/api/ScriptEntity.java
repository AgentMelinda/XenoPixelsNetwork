package net.bullettrain.xenopixelsmod.npc.script.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * A living entity as a script sees it. Read-mostly: position, health, identity. The wrapped entity
 * is never handed to the script, so nothing past these methods is reachable.
 */
public class ScriptEntity {
    protected final LivingEntity entity;

    protected ScriptEntity(LivingEntity entity) {
        this.entity = entity;
    }

    /** The right wrapper for any living entity, or null for null. */
    public static ScriptEntity of(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            return new ScriptPlayer(player);
        }
        return new ScriptEntity(entity);
    }

    LivingEntity unwrap() {
        return entity;
    }

    public String getName() {
        return entity.getName().getString();
    }

    public String getUUID() {
        return entity.getUUID().toString();
    }

    /** Registry id, e.g. {@code minecraft:zombie}. */
    public String getType() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    public boolean isPlayer() {
        return entity instanceof Player;
    }

    public boolean isAlive() {
        return entity.isAlive();
    }

    /** True while the entity is sneaking (holding shift), as CustomNPCs' {@code isSneaking}. */
    public boolean isSneaking() {
        return entity.isShiftKeyDown();
    }

    public double getX() {
        return entity.getX();
    }

    public double getY() {
        return entity.getY();
    }

    public double getZ() {
        return entity.getZ();
    }

    public float getHealth() {
        return entity.getHealth();
    }

    public float getMaxHealth() {
        return entity.getMaxHealth();
    }

    /** A speech bubble over this entity (players too), in the UI's own palette. */
    public boolean say(String text) {
        return say(text, "", "");
    }

    /** A speech bubble in {@code palette} (blue, gold, green, red). */
    public boolean say(String text, String palette) {
        return say(text, palette, "");
    }

    /** A speech bubble in {@code palette} and {@code shape} (rounded, thought, shout, banner). */
    public boolean say(String text, String palette, String shape) {
        String line = net.bullettrain.xenopixelsmod.compat.npc.NpcScriptSay.sanitize(text);
        if (line == null) {
            return false;
        }
        return net.bullettrain.xenopixelsmod.npc.lines.XenoNpcSpeech.say(entity, line, null,
                palette == null ? "" : palette,
                net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.byName(shape,
                        net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.INHERIT));
    }

    /** Distance in blocks to another wrapped entity; -1 for null. */
    public double distanceTo(ScriptEntity other) {
        return other == null ? -1.0 : entity.distanceTo(other.entity);
    }

    @Override
    public String toString() {
        return getName();
    }
}
