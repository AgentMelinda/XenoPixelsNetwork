package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import xenoapi.npcs.api.INbt;
import xenoapi.npcs.api.constants.ItemType;
import xenoapi.npcs.api.entity.IEntityLiving;
import xenoapi.npcs.api.entity.IMob;
import xenoapi.npcs.api.entity.data.IData;
import xenoapi.npcs.api.item.IItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A live item stack as XenoAPI's {@link IItemStack}; changes apply to the wrapped stack. Item NBT
 * does not exist in 1.21.1: {@link #hasNbt}/{@link #removeNbt} act on the custom-data component
 * and {@link #getItemNbt} is a detached save snapshot.
 */
public final class XenoItemAdapter implements IItemStack {
    static final int MAX_LORE_LINES = 64;
    static final int MAX_ENCHANT_LEVEL = 255;

    final ItemStack stack;

    public XenoItemAdapter(ItemStack stack) {
        this.stack = Objects.requireNonNull(stack);
    }

    private static RegistryAccess registries(String method) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) throw new IllegalStateException(method + " needs a running server");
        return server.registryAccess();
    }

    private static ResourceLocation id(String method, String id) {
        ResourceLocation key = id == null ? null : ResourceLocation.tryParse(id);
        if (key == null) throw new IllegalArgumentException(method + ": invalid id " + id);
        return key;
    }

    // ------------------------------------------------------------------ size / damage

    @Override public int getStackSize() { return stack.getCount(); }

    /** 1 to the maximum stack size, as the reference documents ("a number between 1 and 64"). */
    @Override
    public void setStackSize(int size) {
        stack.setCount(Math.max(1, Math.min(stack.getMaxStackSize(), size)));
    }

    @Override public int getMaxStackSize() { return stack.getMaxStackSize(); }
    @Override public boolean isDamageable() { return stack.isDamageableItem(); }
    @Override public int getDamage() { return stack.getDamageValue(); }

    @Override
    public void setDamage(int value) {
        if (!stack.isDamageableItem()) throw new IllegalArgumentException("IItemStack.setDamage: item is not damageable");
        stack.setDamageValue(Math.max(0, Math.min(stack.getMaxDamage(), value)));
    }

    @Override public int getMaxDamage() { return stack.getMaxDamage(); }

    // ------------------------------------------------------------------ enchantments

    @Override public boolean isEnchanted() { return stack.isEnchanted(); }

    @Override
    public boolean hasEnchant(String id) {
        ResourceLocation key = id("IItemStack.hasEnchant", id);
        return stack.getEnchantments().keySet().stream().anyMatch(holder -> holder.is(key));
    }

    @Override
    public void addEnchantment(String id, int strength) {
        ResourceLocation key = id("IItemStack.addEnchantment", id);
        if (strength < 1 || strength > MAX_ENCHANT_LEVEL) {
            throw new IllegalArgumentException("IItemStack.addEnchantment: level must be 1-" + MAX_ENCHANT_LEVEL);
        }
        Holder<Enchantment> enchantment = registries("IItemStack.addEnchantment")
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(ResourceKey.create(Registries.ENCHANTMENT, key))
                .orElseThrow(() -> new IllegalArgumentException("Unknown enchantment " + id));
        stack.enchant(enchantment, strength);
    }

    @Override
    public boolean removeEnchant(String id) {
        ResourceLocation key = id("IItemStack.removeEnchant", id);
        if (!hasEnchant(id)) return false;
        EnchantmentHelper.updateEnchantments(stack, enchantments -> enchantments.removeIf(holder -> holder.is(key)));
        return true;
    }

    // ------------------------------------------------------------------ kind / names

    @Override public boolean isBlock() { return stack.getItem() instanceof BlockItem; }
    @Override public boolean isWearable() { return Equipable.get(stack) != null; }
    @Override public boolean isBook() { return stack.is(Items.WRITTEN_BOOK) || stack.is(Items.WRITABLE_BOOK); }
    @Override public boolean isEmpty() { return stack.isEmpty(); }

    @Override
    public int getType() {
        if (isBook()) return ItemType.BOOK;
        if (stack.is(Tags.Items.SEEDS)) return ItemType.SEEDS;
        if (stack.getItem() instanceof BlockItem) return ItemType.BLOCK;
        if (stack.getItem() instanceof ArmorItem) return ItemType.ARMOR;
        if (stack.getItem() instanceof SwordItem) return ItemType.SWORD;
        return ItemType.NORMAL;
    }

    @Override public boolean hasCustomName() { return stack.has(DataComponents.CUSTOM_NAME); }

    @Override
    public void setCustomName(String name) {
        String next = XenoApiAdapters.boundedText("IItemStack.setCustomName", name, 128);
        if (next.isEmpty()) stack.remove(DataComponents.CUSTOM_NAME);
        else stack.set(DataComponents.CUSTOM_NAME, Component.literal(next));
    }

    @Override public String getDisplayName() { return stack.getHoverName().getString(); }
    @Override public String getItemName() { return stack.getItem().getName(stack).getString(); }
    @Override public String getName() { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }

    @Override
    public String[] getLore() {
        ItemLore lore = stack.get(DataComponents.LORE);
        return lore == null ? new String[0] : lore.lines().stream().map(Component::getString).toArray(String[]::new);
    }

    @Override
    public void setLore(String[] lore) {
        if (lore == null || lore.length == 0) {
            stack.remove(DataComponents.LORE);
            return;
        }
        if (lore.length > MAX_LORE_LINES) throw new IllegalArgumentException("IItemStack.setLore: at most " + MAX_LORE_LINES + " lines");
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(Component.literal(XenoApiAdapters.boundedText("IItemStack.setLore", line, 256)));
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    /** Nutrition of a food item; 0 for anything that is not food. */
    @Override
    public int getFoodLevel() {
        var food = stack.get(DataComponents.FOOD);
        return food == null ? 0 : food.nutrition();
    }

    // ------------------------------------------------------------------ data

    @Override public boolean hasNbt() { return stack.has(DataComponents.CUSTOM_DATA); }
    @Override public void removeNbt() { stack.remove(DataComponents.CUSTOM_DATA); }

    /** A detached snapshot of the whole saved stack; writing to it changes nothing. */
    @Override
    public INbt getItemNbt() {
        if (stack.isEmpty()) return XenoApiAdapters.wrap(new CompoundTag());
        return XenoApiAdapters.wrap((CompoundTag) stack.save(registries("IItemStack.getItemNbt")));
    }

    // ------------------------------------------------------------------ copies / comparison

    @Override public IItemStack copy() { return new XenoItemAdapter(stack.copy()); }

    /** Splits {@code stackSize} items off this stack into a new one, as vanilla {@code split}. */
    @Override
    public IItemStack split(int stackSize) {
        if (stackSize < 1) throw new IllegalArgumentException("IItemStack.split: size must be at least 1");
        return new XenoItemAdapter(stack.split(stackSize));
    }

    @Override
    public boolean compare(IItemStack item, boolean ignoreNBT) {
        return compare(item, ignoreNBT, false);
    }

    @Override
    public boolean compare(IItemStack item, boolean ignoreNBT, boolean ignoreDamage) {
        return compare(XenoApiAdapters.unwrap(item), ignoreNBT, ignoreDamage);
    }

    /** Same item, and unless ignored the same components; {@code ignoreDamage} skips durability. */
    @Override
    public boolean compare(ItemStack item, boolean ignoreNBT, boolean ignoreDamage) {
        if (item == null) item = ItemStack.EMPTY;
        if (stack.isEmpty() || item.isEmpty()) return stack.isEmpty() && item.isEmpty();
        if (!ItemStack.isSameItem(stack, item)) return false;
        if (ignoreNBT) return ignoreDamage || stack.getDamageValue() == item.getDamageValue();
        if (!ignoreDamage) return ItemStack.isSameItemSameComponents(stack, item);
        ItemStack left = stack.copy();
        ItemStack right = item.copy();
        left.remove(DataComponents.DAMAGE);
        right.remove(DataComponents.DAMAGE);
        return ItemStack.isSameItemSameComponents(left, right);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof XenoItemAdapter that && that.stack == stack;
    }

    @Override public int hashCode() { return System.identityHashCode(stack); }
    @Override public String toString() { return stack.getCount() + "x " + getName(); }

    // ------------------------------------------------------------------ attributes

    static final double MAX_ATTRIBUTE = 1_000_000.0;

    /** Reference slot codes: -1 all, 0 main hand, 1 off hand, 2 feet, 3 legs, 4 chest, 5 head. */
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
        var kept = new ArrayList<net.minecraft.world.item.component.ItemAttributeModifiers.Entry>();
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

    // ------------------------------------------------------------------ unsupported

    @Override
    public ItemStack getMCItemStack() {
        throw XenoApiAdapters.unsupported("IItemStack.getMCItemStack (raw handles are not exposed)");
    }

    @Override public INbt getNbt() { throw XenoApiAdapters.unsupported("IItemStack.getNbt (1.21 items have components, not NBT; use getItemNbt)"); }
    /**
     * Wears the stack by {@code damage}, as use would: unbreaking applies, and a stack that breaks
     * is used up. {@code living} is who wears it; null wears it with no one to credit.
     */
    @Override
    public void damageItem(int damage, IMob living) {
        if (damage < 1 || damage > 1_000_000) throw new IllegalArgumentException("IItemStack.damageItem: damage must be 1-1000000");
        var holder = XenoApiAdapters.unwrapLiving(living);
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        net.minecraft.server.level.ServerLevel level = holder != null && holder.level() instanceof net.minecraft.server.level.ServerLevel own
                ? own : server == null ? null : server.overworld();
        if (level == null) throw new IllegalStateException("IItemStack.damageItem needs a running server");
        XenoApiAdapters.requireServerThread(level);
        if (!stack.isDamageableItem()) return;
        stack.hurtAndBreak(damage, level, holder, item -> { });
    }
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
    /**
     * Uses this stack as a right-click in the air by a player, from the chosen hand. The stack must
     * be the one in that hand; only players use items this way.
     */
    @Override
    public void use(IEntityLiving entity, boolean isMainHand) {
        if (!(XenoApiAdapters.unwrap(entity) instanceof net.minecraft.server.level.ServerPlayer player)) {
            throw new IllegalArgumentException("IItemStack.use: only a player can use an item");
        }
        var hand = isMainHand ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND;
        if (player.getItemInHand(hand) != stack) {
            throw new IllegalArgumentException("IItemStack.use: the item must be the one in that hand");
        }
        XenoApiAdapters.requireServerThread(player.level());
        player.gameMode.useItem(player, player.level(), stack, hand);
    }
}
