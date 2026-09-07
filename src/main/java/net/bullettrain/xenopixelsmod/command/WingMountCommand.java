package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.custom.WingPanelBlock;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /wingmount <vertical|horizontal|x|y|z>} — sets the {@code AXIS} of the wing panel the
 * player is looking at. The panel configurator no longer touches {@code AXIS} at all, so this and
 * the Create wrench are the two ways to correct a panel's mount. A wing panel is a 3px slab and the
 * wrench needs the raycast to land on it; this reaches ~6 blocks and is easier to aim.
 *
 * <p>{@code vertical} = {@code Z}, {@code horizontal} = {@code Y}. {@code AXIS} drives Sable's lift
 * normal and the hitbox; the panel's rendered look is per-block-type ({@code /xenowing orient}).
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class WingMountCommand {
    private WingMountCommand() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(command("wingmount"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("vertical").executes(ctx -> set(ctx.getSource(), Direction.Axis.Z)))
                .then(Commands.literal("horizontal").executes(ctx -> set(ctx.getSource(), Direction.Axis.Y)));
        for (Direction.Axis axis : Direction.Axis.values()) {
            node.then(Commands.literal(axis.getSerializedName()).executes(ctx -> set(ctx.getSource(), axis)));
        }
        return node;
    }

    private static int set(CommandSourceStack source, Direction.Axis axis) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        HitResult hit = player.pick(6.0, 1.0f, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("§cLook directly at a wing panel."));
            return 0;
        }
        BlockPos pos = block.getBlockPos();
        BlockState state = player.level().getBlockState(pos);
        if (!(state.getBlock() instanceof WingPanelBlock)) {
            source.sendFailure(Component.literal("§cThat is not a wing panel."));
            return 0;
        }
        if (state.getValue(WingPanelBlock.AXIS) == axis) {
            source.sendSuccess(() -> Component.literal("§7Wing panel is already mounted "
                    + axis.getSerializedName().toUpperCase(java.util.Locale.ROOT) + "."), false);
            return 1;
        }
        player.level().setBlock(pos, state.setValue(WingPanelBlock.AXIS, axis), Block.UPDATE_ALL);
        source.sendSuccess(() -> Component.literal("§bWing panel mount: §f"
                + axis.getSerializedName().toUpperCase(java.util.Locale.ROOT)), true);
        return 1;
    }
}
