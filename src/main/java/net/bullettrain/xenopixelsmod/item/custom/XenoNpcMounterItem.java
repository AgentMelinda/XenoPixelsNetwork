package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
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
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * Sits an NPC on something.
 *
 * <p>My NPCs' Mounter, with its two actions: <b>Mount</b> puts the NPC on another entity, and
 * <b>Mount Player</b> puts it on you. A guard on a horse, a passenger in a boat, a companion riding
 * along — all things that are tedious to arrange otherwise and trivial with a click each.
 *
 * <p>Two clicks: pick the NPC up with a right-click, then right-click whatever it should ride.
 * Use in air after binding to ride the NPC yourself; sneak-use in air still puts the NPC on you.
 * Sneak-right-click the NPC to get it off whatever it is riding.
 *
 * <p><b>A player is not mounted without their say-so.</b> Right-clicking another player with this
 * refuses; mounting yourself uses the sneak-use-in-air action, which only you can perform. An NPC
 * that could be dumped on a player by anyone holding an item is a griefing tool, and the reference's
 * "Mount Player" means <em>me</em>.
 */
public final class XenoNpcMounterItem extends Item implements XenoNpcTool {

    private static final String BOUND_KEY = "XenoMountNpc";

    /** How far from the NPC it may still be mounted. The same reach the editor is opened at. */
    private static final double MAX_DISTANCE_SQ = 8.0 * 8.0;

    public XenoNpcMounterItem(Properties properties) {
        super(properties);
    }

    /**
     * Right-click an entity: either pick up an NPC, or mount the held one on it.
     *
     * <p>Order matters. A bound NPC means "put it on this", including when the thing clicked is
     * another NPC — riding one NPC on another is a legitimate thing to want, so the bound check
     * comes first and picking up is what happens when nothing is held.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!player.hasPermissions(2)) {
            return InteractionResult.FAIL;
        }
        XenoNpcEntity held = bound(stack, player.level());
        if (held != null) {
            return mount(stack, player, held, target);
        }
        if (target instanceof XenoNpcEntity npc) {
            return pickUp(stack, player, npc);
        }
        return InteractionResult.PASS;
    }

    /** The NPC asks this before its own interaction consumes the click. */
    @Override
    public InteractionResult useOnNpc(ItemStack stack, Player player, XenoNpcEntity npc) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal(
                    "You need operator permission to mount Xeno NPCs."));
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            if (!npc.isPassenger()) {
                player.sendSystemMessage(Component.literal("§7It is not riding anything."));
                return InteractionResult.CONSUME;
            }
            npc.stopRiding();
            player.sendSystemMessage(Component.literal("§f" + npc.getName().getString()
                    + " got off."));
            return InteractionResult.CONSUME;
        }
        XenoNpcEntity held = bound(stack, player.level());
        return held != null ? mount(stack, player, held, npc) : pickUp(stack, player, npc);
    }

    private static InteractionResult pickUp(ItemStack stack, Player player, XenoNpcEntity npc) {
        setBound(stack, npc.getUUID());
        player.sendSystemMessage(Component.literal("§bHolding §f" + npc.getName().getString()
                + "§b — right-click what it should ride, use air to ride it, or sneak-use air to ride you."));
        return InteractionResult.CONSUME;
    }

    /**
     * Puts the NPC on the target.
     *
     * <p>Refuses another player, refuses mounting something onto itself, and refuses across a long
     * distance — the bound UUID lives on the stack and could otherwise be carried anywhere.
     */
    private static InteractionResult mount(ItemStack stack, Player player, XenoNpcEntity npc,
                                           Entity target) {
        if (target == npc) {
            player.sendSystemMessage(Component.literal("§7It cannot ride itself."));
            return InteractionResult.CONSUME;
        }
        if (target instanceof Player && target != player) {
            // See the class note: only you can put an NPC on you.
            player.sendSystemMessage(Component.literal(
                    "§7Only that player can put an NPC on themselves."));
            return InteractionResult.CONSUME;
        }
        if (npc.distanceToSqr(target) > MAX_DISTANCE_SQ
                || player.distanceToSqr(npc) > MAX_DISTANCE_SQ) {
            player.sendSystemMessage(Component.literal("§7They are too far apart."));
            return InteractionResult.CONSUME;
        }
        if (!npc.startRiding(target, true)) {
            // force=true still refuses a few things - an entity that cannot take a passenger, or a
            // cycle. Saying so beats a click that silently does nothing.
            player.sendSystemMessage(Component.literal("§7It cannot ride that."));
            return InteractionResult.CONSUME;
        }
        clearBound(stack);
        player.sendSystemMessage(Component.literal("§b" + npc.getName().getString()
                + "§b is riding §f" + target.getName().getString()));
        return InteractionResult.CONSUME;
    }

    /** Sneak-use in air: the held NPC rides you. Use in air: report. */
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
                    "§7Right-click an NPC with this first."));
            return InteractionResultHolder.success(stack);
        }
        if (player.isShiftKeyDown()) {
            mount(stack, player, npc, player);
            return InteractionResultHolder.success(stack);
        }
        if (!npc.mountControlEnabled()) {
            player.sendSystemMessage(Component.literal(
                    "§7Mount Control is disabled for " + npc.getName().getString() + "."));
            return InteractionResultHolder.success(stack);
        }
        if (npc.distanceToSqr(player) > MAX_DISTANCE_SQ) {
            player.sendSystemMessage(Component.literal("§7You are too far away to ride it."));
            return InteractionResultHolder.success(stack);
        }
        if (!player.startRiding(npc, true)) {
            player.sendSystemMessage(Component.literal("§7You cannot ride that NPC right now."));
            return InteractionResultHolder.success(stack);
        }
        clearBound(stack);
        player.sendSystemMessage(Component.literal("§bRiding §f" + npc.getName().getString()));
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.literal("Use NPC: pick up · Use entity: mount it there"));
        tooltip.add(Component.literal("Use air: ride it · Sneak-use air: it rides you"));
        tooltip.add(Component.literal("Sneak-use NPC: get it off whatever it rides"));
    }

    /**
     * The NPC this stack is holding, or null.
     *
     * <p>Resolved by scanning the level rather than by trusting the id: a UUID on a stack is a
     * request to find that entity, not permission to act on it — the same rule
     * {@code XenoNpcPathToolItem} follows.
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

    private static void clearBound(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(BOUND_KEY));
    }
}
