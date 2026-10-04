package net.bullettrain.xenopixelsmod.npc.bank;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's container menus.
 *
 * <p>There are two, and the bank was the first the mod ever registered — everything else it opens is
 * either a plain {@code Screen} or vanilla's {@code Merchant}. Registered through
 * {@link IMenuTypeExtension#create} rather than {@code MenuType::new} because the bank needs to
 * hand the client which tab, how many slots are unlocked, and whether an economy is present at the
 * moment the screen opens; the plain supplier has nowhere to put that.
 */
public final class ModMenus {

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, XenoPixelsMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<XenoNpcBankMenu>> NPC_BANK =
            MENUS.register("npc_bank",
                    () -> IMenuTypeExtension.create(XenoNpcBankMenu::new));

    /**
     * The NPC's gear, drops and Curios as real slots.
     *
     * <p>Also {@link IMenuTypeExtension#create} rather than a plain supplier, because the screen has
     * to know which NPC it is looking at and which Curios slots that NPC actually has - both decided
     * server-side at the moment it opens, and neither expressible through {@code MenuType::new}.
     */
    public static final DeferredHolder<MenuType<?>,
            MenuType<net.bullettrain.xenopixelsmod.npc.inventory.XenoNpcInventoryMenu>>
            NPC_INVENTORY = MENUS.register("npc_inventory",
                    () -> IMenuTypeExtension.create(
                            net.bullettrain.xenopixelsmod.npc.inventory
                                    .XenoNpcInventoryMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
