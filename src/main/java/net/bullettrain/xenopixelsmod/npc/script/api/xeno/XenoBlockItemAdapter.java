package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import xenoapi.npcs.api.item.IItemBlock;

/** Block specialization returns the block registry id, which may differ from its item's id. */
public final class XenoBlockItemAdapter extends XenoItemAdapter implements IItemBlock {
    public XenoBlockItemAdapter(ItemStack stack) {
        super(stack);
        if (!(stack.getItem() instanceof BlockItem)) throw new IllegalArgumentException("IItemBlock requires a block item");
    }
    @Override public String getBlockName() {
        return BuiltInRegistries.BLOCK.getKey(((BlockItem) stack.getItem()).getBlock()).toString();
    }
}
