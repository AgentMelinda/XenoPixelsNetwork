package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.item.custom.XenoNpcPayload;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.SyncNpcStoreIndexPacket;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRole;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcClones;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * The saved-clone library, as operator commands.
 *
 * <p>My NPCs puts this behind the cloner's own GUI with tabs 1–9. Commands rather than a screen,
 * because the store's other authoring surfaces are already commands ({@code /xenoscene},
 * {@code /xenonpcimport}) and a template library is a list of names — the thing commands are good at
 * and a bespoke screen adds least to.
 *
 * <p>The Cloner item still carries one template on the stack for immediate reuse; this is for
 * templates that should outlive the item.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoNpcCloneCommands {

    private XenoNpcCloneCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xenoclone")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(ctx -> listAll(ctx.getSource()))
                        .then(Commands.argument("tab",
                                        IntegerArgumentType.integer(XenoNpcClones.MIN_TAB,
                                                XenoNpcClones.MAX_TAB))
                                .executes(ctx -> list(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "tab")))))
                .then(Commands.literal("save")
                        .then(Commands.argument("tab",
                                        IntegerArgumentType.integer(XenoNpcClones.MIN_TAB,
                                                XenoNpcClones.MAX_TAB))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("npc", EntityArgument.entity())
                                                .executes(ctx -> save(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "tab"),
                                                        StringArgumentType.getString(ctx, "id")))))))
                .then(Commands.literal("place")
                        .then(Commands.argument("tab",
                                        IntegerArgumentType.integer(XenoNpcClones.MIN_TAB,
                                                XenoNpcClones.MAX_TAB))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> place(ctx,
                                                IntegerArgumentType.getInteger(ctx, "tab"),
                                                StringArgumentType.getString(ctx, "id")))))));
    }

    /** Every tab that holds anything, so an operator can find a template without guessing. */
    private static int listAll(CommandSourceStack source) {
        int total = XenoNpcClones.total();
        if (total == 0) {
            source.sendSuccess(() -> Component.literal(
                    "§7No saved clones. §f/xenoclone save <tab> <id> <npc>"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§b" + total + " saved clone(s):"), false);
        for (int tab = XenoNpcClones.MIN_TAB; tab <= XenoNpcClones.MAX_TAB; tab++) {
            List<String> ids = XenoNpcClones.ids(tab);
            if (ids.isEmpty()) {
                continue;
            }
            final int shown = tab;
            source.sendSuccess(() -> Component.literal("§7 tab §f" + shown + "§7: "
                    + String.join(", ", ids)), false);
        }
        return total;
    }

    private static int list(CommandSourceStack source, int tab) {
        List<String> ids = XenoNpcClones.ids(tab);
        if (ids.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Tab " + tab + " is empty."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§bTab " + tab + "§7: "
                + String.join(", ", ids)), false);
        return ids.size();
    }

    /** Saves the named NPC into a tab. */
    private static int save(CommandContext<CommandSourceStack> ctx, int tab, String id)
            throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(ctx, "npc");
        if (!(entity instanceof XenoNpcEntity npc)) {
            ctx.getSource().sendFailure(Component.literal("That is not a Xeno NPC."));
            return 0;
        }
        boolean overwrite = XenoNpcClones.load(tab, id) != null;
        String refusal = XenoNpcClones.save(npc, tab, id);
        if (refusal != null) {
            ctx.getSource().sendFailure(Component.literal(refusal));
            return 0;
        }
        // The index is what the editor's pickers read, so a save nobody syncs is a template the
        // client cannot see until it reconnects.
        ModNetwork.sendToAll(SyncNpcStoreIndexPacket.current());
        String name = npc.getName().getString();
        ctx.getSource().sendSuccess(() -> Component.literal((overwrite ? "§eReplaced §f" : "§bSaved §f")
                + name + "§7 as §f" + id + "§7 in tab §f" + tab), true);
        return 1;
    }

    /**
     * Places a saved template where the caller is standing.
     *
     * <p>The other half of the reservation: something that can spend a clone. Same restore path the
     * Cloner item and the respawn handler use, so a template placed here and one placed from a stack
     * produce the same NPC.
     */
    private static int place(CommandContext<CommandSourceStack> ctx, int tab, String id)
            throws CommandSyntaxException {
        CompoundTag payload = XenoNpcClones.load(tab, id);
        if (payload == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "No clone called " + id + " in tab " + tab + "."));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();

        XenoNpcRole role = XenoNpcRole.byId(payload.getString("Role"));
        XenoNpcEntity npc = ModEntities.xenoNpcType(role).create(level);
        if (npc == null) {
            ctx.getSource().sendFailure(Component.literal("That clone names a role that is gone."));
            return 0;
        }
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        npc.moveTo(x, y, z, player.getYRot(), 0.0f);
        // keepOwner false: placing from the library makes a new NPC, exactly as the Cloner does.
        XenoNpcPayload.apply(npc, payload, player, x, y, z, false);
        if (!level.addFreshEntity(npc)) {
            ctx.getSource().sendFailure(Component.literal("There is no room for it here."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§bPlaced §f"
                + npc.getName().getString()), true);
        return 1;
    }
}
