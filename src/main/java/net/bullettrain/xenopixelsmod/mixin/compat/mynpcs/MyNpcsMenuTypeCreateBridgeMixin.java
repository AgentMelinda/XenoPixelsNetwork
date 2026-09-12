package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Adds the {@code (int, Inventory, Object)} overload of {@link MenuType#create} that My NPCs
 * reflectively looks up when it opens a container GUI.
 *
 * <p><b>Target:</b> {@link MenuType}. <b>Written for:</b> Minecraft 1.21.1 / NeoForge 21.1.248,
 * My NPCs 1.5.0. <b>Side:</b> common — only My NPCs' server path uses it, and the method is inert
 * until something calls it.
 *
 * <p><b>The bug.</b> {@code espi.mynpcs.EspiUtilServer$3#createMenu} builds every container GUI by
 * reflection:
 *
 * <pre>
 * Method m = type.getClass().getMethod("create", int.class, Inventory.class, Object.class);
 * RegistryFriendlyByteBuf data =
 *         new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
 * extraDataWriter.accept(data);                 // writes the int the factory reads back
 * return (AbstractContainerMenu) m.invoke(type, containerId, inventory, data);
 * </pre>
 *
 * <p>{@link Class#getMethod} matches parameter types <em>exactly</em>, and {@link MenuType} declares
 * its three-argument factory as {@code create(int, Inventory, RegistryFriendlyByteBuf)} — never
 * {@code Object}. The lookup therefore always throws {@link NoSuchMethodException}, and both
 * {@code catch} blocks fall back to {@code MenuType#create(int, Inventory)}, which hands the
 * container factory a <b>null</b> buffer. Every My NPCs factory reads that buffer immediately
 * ({@code espi.mynpcs.CustomContainer} line 88, {@code data.readInt()}), so the server thread dies
 * with:
 *
 * <pre>
 * java.lang.NullPointerException: Cannot invoke "RegistryFriendlyByteBuf.readInt()" because
 *     "data" is null
 *   at espi.mynpcs.CustomContainer.lambda$init$14(CustomContainer.java:88)
 *   at net.neoforged.neoforge.network.IContainerFactory.create(IContainerFactory.java:36)
 *   at net.minecraft.world.inventory.MenuType.create(MenuType.java:54)
 *   at espi.mynpcs.EspiUtilServer$3.createMenu(EspiUtilServer.java:302)
 *   at net.minecraft.server.level.ServerPlayer.openMenu(ServerPlayer.java:1159)
 *   at espi.mynpcs.EspiUtilServer.openContainerGui(EspiUtilServer.java:359)
 *   at espi.mynpcs.packets.server.SPacketGuiOpen.lambda$sendOpenGui$1(SPacketGuiOpen.java:80)
 * </pre>
 *
 * <p>The NPC wand's inventory tab is what sends that packet, which is why it cannot be opened or
 * used; every other My NPCs container GUI (trader, follower, etc.) fails the same way.
 *
 * <p><b>The fix.</b> Declaring the overload My NPCs asks for makes its reflective lookup resolve,
 * so the intended path runs with the buffer it has already populated correctly. The method only
 * forwards to the real three-argument factory; nothing in vanilla or My NPCs calls it by name, and
 * a pack without My NPCs never sees it at all (gated by {@code ConditionalMixinPlugin} on the
 * {@code .compat.mynpcs.} package). The alternative — patching
 * {@code EspiUtilServer$3#createMenu} directly — would have to rebuild the buffer inside the
 * {@link NoSuchMethodException} handler, where it has not been written yet.
 *
 * <p><b>Guideline notes</b> (§18): additive-only, one delegating method, no behaviour change to
 * {@link MenuType} itself. Deliberately <b>not</b> {@code @Unique}: the name and parameter types
 * must survive verbatim for {@link Class#getMethod} to find them. My NPCs is not modified.
 */
@Mixin(MenuType.class)
public abstract class MyNpcsMenuTypeCreateBridgeMixin {

    @SuppressWarnings("unchecked")
    public AbstractContainerMenu create(int containerId, Inventory inventory, Object data) {
        return ((MenuType<AbstractContainerMenu>) (Object) this)
                .create(containerId, inventory, (RegistryFriendlyByteBuf) data);
    }
}