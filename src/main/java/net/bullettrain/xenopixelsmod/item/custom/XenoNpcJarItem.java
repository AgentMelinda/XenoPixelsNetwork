package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRole;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Picks an NPC up and puts it down somewhere else.
 *
 * <p>My NPCs' NPC Jar. It looks like the Cloner and shares all of its machinery (see
 * {@link XenoNpcPayload}), and differs in the one way that matters: <b>the NPC leaves the world.</b>
 * The Cloner is for making eight guards out of one; the Jar is for moving a guard you already have,
 * across a build or between dimensions, without rebuilding it.
 *
 * <h2>Capture is not death</h2>
 *
 * <p>Deliberate, and worth knowing. Respawn is hooked on {@code die()}
 * ({@code XenoNpcEntity.die}), and this calls {@code discard()} — so a respawn-enabled NPC does
 * <em>not</em> come back while it is jarred. If it did, jarring one would duplicate it, which is the
 * opposite of what the tool is for.
 *
 * <p>It also means <b>the jar is the only copy</b>. Losing the item loses the NPC, which is not true
 * of the Cloner, so the tooltip says so.
 *
 * <p>One item, two states. My NPCs registers {@code npcjarempty} and {@code npcjarfilled}
 * separately; a filled jar here is the same item carrying a payload, which is what item components
 * are for and what keeps a full jar from being craftable back into an empty one by accident.
 */
public final class XenoNpcJarItem extends Item implements XenoNpcTool {

    public XenoNpcJarItem(Properties properties) {
        super(properties);
    }

    /** Right-click an NPC to take it. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!(target instanceof XenoNpcEntity npc)) {
            return InteractionResult.PASS;
        }
        return capture(stack, player, npc);
    }

    /** The NPC asks this before its own interaction consumes the click. */
    @Override
    public InteractionResult useOnNpc(ItemStack stack, Player player, XenoNpcEntity npc) {
        return capture(stack, player, npc);
    }

    /** Takes one NPC out of the world and onto the stack. */
    public static InteractionResult capture(ItemStack stack, Player player, XenoNpcEntity npc) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal(
                    "You need operator permission to jar Xeno NPCs."));
            return InteractionResult.FAIL;
        }
        if (XenoNpcPayload.present(stack)) {
            // Refused rather than overwritten: the jarred NPC exists nowhere else, so overwriting
            // would delete it outright, and a right-click is far too cheap an action for that.
            player.sendSystemMessage(Component.literal("§7This jar already holds §f"
                    + XenoNpcPayload.label(stack) + "§7. Release it first."));
            return InteractionResult.FAIL;
        }
        String refusal = XenoNpcPayload.store(stack, npc);
        if (refusal != null) {
            player.sendSystemMessage(Component.literal("§7" + refusal));
            return InteractionResult.FAIL;
        }

        String name = npc.getName().getString();
        // discard, not kill: kill runs die(), which schedules a respawn for an NPC that has one -
        // and a jarred NPC that respawned behind you would be a duplicate rather than a move.
        npc.discard();
        player.sendSystemMessage(Component.literal("§bJarred §f" + name
                + "§b — right-click a block to let it out."));
        return InteractionResult.CONSUME;
    }

    /** Right-click a block to let the NPC out on top of it. */
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
        CompoundTag payload = XenoNpcPayload.read(stack);
        if (payload == null) {
            player.sendSystemMessage(Component.literal(
                    "§7Right-click an NPC with this first to jar it."));
            return InteractionResult.CONSUME;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.CONSUME;
        }

        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        XenoNpcRole role = XenoNpcRole.byId(payload.getString("Role"));
        XenoNpcEntity npc = ModEntities.xenoNpcType(role).create(server);
        if (npc == null) {
            return InteractionResult.CONSUME;
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        npc.moveTo(x, y, z, player.getYRot(), 0.0f);
        // keepOwner true: this is the same NPC that went in, so it keeps its owner and its origin.
        // Only the home moves, because the point of the jar is that it now lives here.
        XenoNpcPayload.apply(npc, payload, player, x, y, z, true);

        if (!server.addFreshEntity(npc)) {
            // The jar is the only copy, so a failed spawn must not empty it.
            player.sendSystemMessage(Component.literal("§7There is no room for it there."));
            return InteractionResult.CONSUME;
        }
        // Emptied only after the NPC is definitely in the world.
        XenoNpcPayload.clear(stack);
        player.sendSystemMessage(Component.literal("§bReleased §f" + npc.getName().getString()));
        return InteractionResult.CONSUME;
    }

    /** Use in air: say what is inside. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        // Deliberately no sneak-to-clear, unlike the Cloner. Clearing a jar destroys the only copy
        // of an NPC, and that must be a command with a confirmation rather than a sneak-right-click.
        player.sendSystemMessage(Component.literal(XenoNpcPayload.present(stack)
                ? "§bHolding §f" + XenoNpcPayload.label(stack)
                : "§7Empty."));
        return InteractionResultHolder.success(stack);
    }

    /** A full jar glints, so a shelf of them is readable at a glance. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return XenoNpcPayload.present(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        String label = XenoNpcPayload.label(stack);
        if (label.isEmpty()) {
            tooltip.add(Component.literal("Empty"));
            tooltip.add(Component.literal("Use NPC: jar it · Use block: let it out"));
            tooltip.add(Component.literal("The NPC leaves the world while jarred."));
            return;
        }
        tooltip.add(Component.literal("Holding: " + label));
        tooltip.add(Component.literal("Use block: let it out"));
        tooltip.add(Component.literal("This jar is the only copy — losing it loses the NPC."));
    }
}
