package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.path.NpcPath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * Places the points of an NPC's patrol route.
 *
 * <p>My NPCs' Moving Path tool, and the same shape: bind it to an NPC, then click blocks to lay
 * out where it walks. The alternative — typing coordinates into the editor — means walking to each
 * spot and reopening a screen for every point, which is not how anyone draws a route.
 *
 * <p>Built on {@link XenoNpcWandItem}'s shape, including its operator check. <b>Everything is
 * decided on the server</b>: the tool sends a click, and the server resolves the NPC, re-checks
 * the permission and the distance, and bounds the point count. The bound NPC id on the stack is a
 * convenience, never an authority.
 */
public final class XenoNpcPathToolItem extends Item implements XenoNpcTool {

    private static final String BOUND_KEY = "XenoPathNpc";

    /**
     * How far from the NPC a point may be placed.
     *
     * <p>Generous, because a route is meant to go somewhere — but bounded, so a stack carried to
     * another biome cannot append a point the NPC could never walk to.
     */
    private static final double MAX_PLACE_DISTANCE = 64.0;

    public XenoNpcPathToolItem(Properties properties) {
        super(properties);
    }

    /** Right-click an NPC to bind the tool to it. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!(target instanceof XenoNpcEntity npc)) {
            return InteractionResult.PASS;
        }
        return bind(stack, player, npc);
    }

    /** The NPC asks this before its own interaction consumes the click. */
    @Override
    public InteractionResult useOnNpc(ItemStack stack, Player player, XenoNpcEntity npc) {
        return bind(stack, player, npc);
    }

    /** Called by the NPC before its own interaction consumes the click. */
    public static InteractionResult bind(ItemStack stack, Player player, XenoNpcEntity npc) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal(
                    "You need operator permission to edit Xeno NPC paths."));
            return InteractionResult.FAIL;
        }
        setBound(stack, npc.getUUID());
        NpcPath path = NpcCombatProfile.read(npc).path;
        player.sendSystemMessage(Component.literal("§bPath tool bound to §f"
                + npc.getName().getString() + "§b — " + path.size() + " point(s), "
                + path.mode().id()));
        return InteractionResult.CONSUME;
    }

    /** Right-click a block to append it to the bound NPC's route. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
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
                    "§7Right-click an NPC with this first to bind it."));
            return InteractionResult.CONSUME;
        }

        // The point sits on top of the clicked face, so clicking the ground gives the block the
        // NPC will stand on rather than the one inside it.
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        if (npc.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)
                > MAX_PLACE_DISTANCE * MAX_PLACE_DISTANCE) {
            player.sendSystemMessage(Component.literal(
                    "§7That is too far from " + npc.getName().getString() + " to walk to."));
            return InteractionResult.CONSUME;
        }

        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        if (!profile.path.add(new NpcPath.Point(pos.getX(), pos.getY(), pos.getZ()))) {
            player.sendSystemMessage(Component.literal(
                    "§7That route is full at " + NpcPath.MAX_POINTS + " points."));
            return InteractionResult.CONSUME;
        }
        profile.write(npc);
        // The route changed under the walker, so it rejoins at the nearest point rather than
        // continuing toward an index that may now mean something else.
        npc.setPathIndex(-1);
        player.sendSystemMessage(Component.literal("§bPoint " + profile.path.size() + " §7at "
                + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
        return InteractionResult.CONSUME;
    }

    /** Use in air: report the route. Sneak-use in air: clear it. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!player.hasPermissions(2)) {
            return InteractionResultHolder.fail(stack);
        }
        XenoNpcEntity npc = bound(stack, level);
        if (npc == null) {
            player.sendSystemMessage(Component.literal(
                    "§7Right-click an NPC with this first to bind it."));
            return InteractionResultHolder.success(stack);
        }
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        if (player.isShiftKeyDown()) {
            profile.path.clear();
            profile.write(npc);
            npc.setPathIndex(-1);
            player.sendSystemMessage(Component.literal("§fCleared "
                    + npc.getName().getString() + "'s route."));
            return InteractionResultHolder.success(stack);
        }
        player.sendSystemMessage(Component.literal("§b" + npc.getName().getString() + "§7 — "
                + profile.path.size() + " point(s), " + profile.path.mode().id()
                + ", speed " + profile.path.speed()));
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.literal("Use NPC: bind · Use block: add point"));
        tooltip.add(Component.literal("Use air: show route · Sneak-use air: clear"));
        tooltip.add(Component.literal("Reorder and delete points in the NPC editor."));
    }

    /**
     * The NPC this stack is bound to, or null.
     *
     * <p>Resolved by scanning the level rather than by any id the client supplied: the stack holds
     * a UUID, and a UUID is a request to find that entity, not permission to act on it.
     */
    private static XenoNpcEntity bound(ItemStack stack, Level level) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID(BOUND_KEY) || !(level instanceof ServerLevel server)) {
            return null;
        }
        UUID id = tag.getUUID(BOUND_KEY);
        Entity entity = server.getEntity(id);
        return entity instanceof XenoNpcEntity npc && npc.isAlive() ? npc : null;
    }

    private static void setBound(ItemStack stack, UUID id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(BOUND_KEY, id));
    }
}
