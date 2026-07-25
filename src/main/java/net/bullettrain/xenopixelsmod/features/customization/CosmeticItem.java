package net.bullettrain.xenopixelsmod.features.customization;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Represents a single registerable cosmetic (face tattoo, marking, headgear,
 * accessory, hairstyle, eye color variant, etc.)
 *
 * The icon is stored as a {@link Supplier} rather than a resolved
 * {@link ItemStack} because {@link CustomizationManager#registerCosmetics()}
 * builds these during class initialization / mod construction, which happens
 * before Forge's {@code DeferredRegister} entries are actually bound to the
 * registry. Resolving an ItemStack eagerly at that point would return an
 * empty/placeholder item. Deferring resolution to render-time (calling
 * {@link #getIcon()}) guarantees the backing Item is fully registered.
 */
public class CosmeticItem {

    private final String id;
    private final String name;
    private final String description;
    private final CustomizationManager.Category category;
    private final Supplier<ItemStack> iconSupplier;

    public CosmeticItem(String id, String name, String description, CustomizationManager.Category category) {
        this(id, name, description, category, () -> ItemStack.EMPTY);
    }

    public CosmeticItem(String id, String name, String description, CustomizationManager.Category category,
                        Supplier<ItemStack> iconSupplier) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = name != null ? name : id;
        this.description = description != null ? description : "";
        this.category = Objects.requireNonNull(category, "category");
        this.iconSupplier = iconSupplier != null ? iconSupplier : () -> ItemStack.EMPTY;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CustomizationManager.Category getCategory() {
        return category;
    }

    /**
     * Resolves the icon lazily. Safe to call any time after mod setup has
     * completed (e.g. during GUI rendering), but not during static init.
     */
    public ItemStack getIcon() {
        ItemStack stack = iconSupplier.get();
        return stack != null ? stack : ItemStack.EMPTY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CosmeticItem other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CosmeticItem{id='" + id + "', name='" + name + "', category=" + category + "}";
    }
}