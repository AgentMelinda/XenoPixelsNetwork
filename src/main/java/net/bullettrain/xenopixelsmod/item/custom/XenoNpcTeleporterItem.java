package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Moves an NPC somewhere else, including into another dimension.
 *
 * <p>My NPCs' Teleporter. Its GUI is a dimension list and a TP button; this is the same two choices
 * without a screen — right-click the NPC to hold it, right-click a block to send it there, and
 * sneak-use in air to step through the dimension list. A list of three or four entries does not need
 * a window, and skipping one keeps the whole tool server-authoritative with no packet of its own.
 *
 * <p><b>Why a tool rather than the existing leash-and-walk.</b> An NPC that has to walk cannot cross
 * a dimension at all, and will not cross a canyon. Moving an authored NPC is otherwise a delete and
 * a rebuild.
 *
 * <p>Everything is decided server-side. The stack carries a UUID and a dimension id; the server
 * re-resolves both, and a dimension named on a stack carried in from elsewhere simply does not
 * resolve.
 */
public final class XenoNpcTeleporterItem extends Item implements XenoNpcTool {

    private static final String BOUND_KEY = "XenoTpNpc";
    private static final String DIMENSION_KEY = "XenoTpDimension";

    public XenoNpcTeleporterItem(Properties properties) {
        super(properties);
    }

    /** Right-click an NPC to hold it. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!(target instanceof XenoNpcEntity npc)) {
            return InteractionResult.PASS;
        }
        return hold(stack, player, npc);
    }

    /** The NPC asks this before its own interaction consumes the click. */
    @Override
    public InteractionResult useOnNpc(ItemStack stack, Player player, XenoNpcEntity npc) {
        return hold(stack, player, npc);
    }

    private static InteractionResult hold(ItemStack stack, Player player, XenoNpcEntity npc) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal(
                    "You need operator permission to teleport Xeno NPCs."));
            return InteractionResult.FAIL;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(BOUND_KEY,
                npc.getUUID()));
        player.sendSystemMessage(Component.literal("§bHolding §f" + npc.getName().getString()
                + "§b — right-click where it should go."));
        return InteractionResult.CONSUME;
    }

    /** Right-click a block to send the held NPC there. */
    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!player.hasPermissions(2)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        XenoNpcEntity npc = bound(stack, level);
        if (npc == null) {
            player.sendSystemMessage(Component.literal(
                    "§7Right-click an NPC with this first, and stay in its dimension."));
            return InteractionResult.CONSUME;
        }
        var pos = context.getClickedPos().relative(context.getClickedFace());
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;

        ServerLevel destination = destination(stack, level);
        if (destination == null) {
            player.sendSystemMessage(Component.literal("§7That dimension is not loaded."));
            return InteractionResult.CONSUME;
        }

        String name = npc.getName().getString();
        Entity moved = move(npc, destination, x, y, z);
        if (moved == null) {
            player.sendSystemMessage(Component.literal("§7It could not be moved there."));
            return InteractionResult.CONSUME;
        }
        // The home follows. An NPC teleported across a build and then walked back to its old spot
        // by the leash would read as the teleport having failed.
        if (moved instanceof XenoNpcEntity placed) {
            placed.npcData().setHome(x, y, z);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(BOUND_KEY));
        player.sendSystemMessage(Component.literal("§bSent §f" + name + "§b to "
                + destination.dimension().location()));
        return InteractionResult.CONSUME;
    }

    /**
     * Moves the NPC, changing dimension when it has to.
     *
     * <p>A same-dimension move is a {@code teleportTo}; a cross-dimension one is
     * {@code changeDimension}, which <b>returns a different entity</b> — the original is removed and
     * a copy is created in the destination. Treating the return value as the NPC from then on is the
     * whole reason this method exists rather than being two lines at the call site.
     */
    private static Entity move(XenoNpcEntity npc, ServerLevel destination,
                               double x, double y, double z) {
        if (npc.level() == destination) {
            npc.teleportTo(x, y, z);
            return npc;
        }
        return npc.changeDimension(new DimensionTransition(destination,
                new net.minecraft.world.phys.Vec3(x, y, z),
                net.minecraft.world.phys.Vec3.ZERO, npc.getYRot(), npc.getXRot(),
                DimensionTransition.DO_NOTHING));
    }

    /** Sneak-use in air: step through the dimensions. Use in air: report. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!player.hasPermissions(2) || !(level instanceof ServerLevel server)) {
            return InteractionResultHolder.fail(stack);
        }
        if (player.isShiftKeyDown()) {
            cycleDimension(stack, server, player);
            return InteractionResultHolder.success(stack);
        }
        XenoNpcEntity npc = bound(stack, level);
        player.sendSystemMessage(Component.literal((npc == null
                ? "§7Nothing held."
                : "§bHolding §f" + npc.getName().getString())
                + "§7 Destination: §f" + dimensionId(stack, level)));
        return InteractionResultHolder.success(stack);
    }

    /**
     * Moves to the next loaded dimension.
     *
     * <p>Read from {@code getAllLevels} at the moment it is asked rather than from a fixed list, so
     * a dimension added by another mod is offered and one that is gone is not.
     */
    private static void cycleDimension(ItemStack stack, ServerLevel level, Player player) {
        List<ResourceKey<Level>> all = new ArrayList<>();
        for (ServerLevel candidate : level.getServer().getAllLevels()) {
            all.add(candidate.dimension());
        }
        if (all.isEmpty()) {
            return;
        }
        all.sort((a, b) -> a.location().compareTo(b.location()));
        ResourceLocation current = ResourceLocation.tryParse(dimensionId(stack, level));
        int index = 0;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).location().equals(current)) {
                index = i + 1;
                break;
            }
        }
        ResourceKey<Level> next = all.get(index % all.size());
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putString(DIMENSION_KEY, next.location().toString()));
        player.sendSystemMessage(Component.literal("§bDestination: §f" + next.location()));
    }

    /** The chosen dimension, defaulting to the one the holder is standing in. */
    private static String dimensionId(ItemStack stack, Level level) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String stored = tag.getString(DIMENSION_KEY);
        return stored.isEmpty() ? level.dimension().location().toString() : stored;
    }

    /** The chosen dimension as a live level, or null when it does not resolve. */
    private static ServerLevel destination(ItemStack stack, Level level) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(dimensionId(stack, level));
        if (id == null) {
            return null;
        }
        // Re-resolved against the running server, never trusted from the stack: an id typed or
        // carried in from another world is a request, not an authority.
        return server.getServer().getLevel(ResourceKey.create(net.minecraft.core.registries
                .Registries.DIMENSION, id));
    }

    private static XenoNpcEntity bound(ItemStack stack, Level level) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID(BOUND_KEY) || !(level instanceof ServerLevel server)) {
            return null;
        }
        UUID id = tag.getUUID(BOUND_KEY);
        Entity entity = server.getEntity(id);
        return entity instanceof XenoNpcEntity npc && npc.isAlive() ? npc : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.literal("Use NPC: hold it · Use block: send it there"));
        tooltip.add(Component.literal("Sneak-use air: choose a dimension"));
        tooltip.add(Component.literal("Its home moves with it."));
    }
}
