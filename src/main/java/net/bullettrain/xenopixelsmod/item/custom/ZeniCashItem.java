package net.bullettrain.xenopixelsmod.item.custom;

import net.minecraft.world.item.Item;

/** One physical Zeni denomination. The value is fixed by its registered item type. */
public final class ZeniCashItem extends Item {
    private final long zeni;

    public ZeniCashItem(Properties properties, long zeni) {
        super(properties);
        if (zeni <= 0L) throw new IllegalArgumentException("Zeni value must be positive");
        this.zeni = zeni;
    }

    public long zeni() {
        return zeni;
    }
}
