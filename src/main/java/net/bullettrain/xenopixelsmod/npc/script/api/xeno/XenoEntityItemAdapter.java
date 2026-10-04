package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import xenoapi.npcs.api.entity.IEntityItem;
import xenoapi.npcs.api.item.IItemStack;

import java.util.UUID;

/**
 * A dropped item as XenoAPI's {@link IEntityItem}. The owner is the player the item is reserved
 * for (vanilla's pickup target), by name. Vanilla keeps pickup delay and age private and without a
 * setter for age, so both are read from, and age written through, the entity's own save data.
 */
public final class XenoEntityItemAdapter extends XenoEntityAdapter<ItemEntity> implements IEntityItem<ItemEntity> {
    /** Vanilla's own cap: a short. */
    static final int MAX_TICKS = Short.MAX_VALUE;

    public XenoEntityItemAdapter(ItemEntity entity) {
        super(entity);
    }

    private CompoundTag saved() {
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        return tag;
    }

    @Override
    public String getOwner() {
        UUID target = entity.getTarget();
        var server = entity.getServer();
        if (target == null || server == null) return null;
        ServerPlayer online = server.getPlayerList().getPlayer(target);
        if (online != null) return online.getGameProfile().getName();
        var cache = server.getProfileCache();
        return cache == null ? target.toString() : cache.get(target).map(p -> p.getName()).orElse(target.toString());
    }

    /** Reserves the item for an online player by name; null or blank lets anyone pick it up. */
    @Override
    public void setOwner(String name) {
        serverThread();
        if (name == null || name.isBlank()) {
            entity.setTarget(null);
            return;
        }
        var server = entity.getServer();
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayerByName(name.trim());
        if (player == null) throw new IllegalArgumentException("IEntityItem.setOwner: no online player " + name);
        entity.setTarget(player.getUUID());
    }

    @Override public int getPickupDelay() { return saved().getShort("PickupDelay"); }

    @Override
    public void setPickupDelay(int delay) {
        if (delay < 0 || delay > MAX_TICKS) throw new IllegalArgumentException("IEntityItem.setPickupDelay: 0-" + MAX_TICKS);
        serverThread();
        entity.setPickUpDelay(delay);
    }

    @Override public long getAge() { return entity.getAge(); }

    @Override
    public void setAge(long age) {
        if (age < -MAX_TICKS || age > MAX_TICKS) throw new IllegalArgumentException("IEntityItem.setAge: -32767..32767");
        serverThread();
        CompoundTag tag = saved();
        tag.putShort("Age", (short) age);
        entity.load(tag);
    }

    @Override public int getLifeSpawn() { return entity.lifespan; }

    @Override
    public void setLifeSpawn(int age) {
        if (age < 1 || age > 1_000_000) throw new IllegalArgumentException("IEntityItem.setLifeSpawn: 1-1000000");
        serverThread();
        entity.lifespan = age;
    }

    @Override public IItemStack getItem() { return XenoApiAdapters.wrap(entity.getItem()); }

    @Override
    public void setItem(IItemStack item) {
        ItemStack stack = XenoApiAdapters.unwrap(item).copy();
        if (stack.isEmpty()) throw new IllegalArgumentException("IEntityItem.setItem: item cannot be empty");
        serverThread();
        entity.setItem(stack);
    }
}
