package net.bullettrain.xenopixelsmod.command;

import net.neoforged.fml.common.EventBusSubscriber;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContext;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContextKey;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * One Forge {@link PermissionNode} per XenoPixels command / subcommand.
 * Compatible with LuckPerms / FTB Ranks / vanilla op fallbacks.
 *
 * <p>Node ids: {@code xenopixelsmod.&lt;path&gt;}
 * (e.g. {@code xenopixelsmod.dmzhud.toggle}, {@code xenopixelsmod.xenohud.cd.shape}).
 *
 * <p>{@link #ADMIN} grants every node.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoPermissions {
    private static final List<PermissionNode<Boolean>> NODES = new ArrayList<>();
    private static final Set<PermissionNode<Boolean>> CLIENT_DEFAULT_ALLOW = new HashSet<>();

    /** Super-node: grants all XenoPixels command permissions. Default: OP level 2. */
    public static final PermissionNode<Boolean> ADMIN =
            op("admin", "Full XenoPixels admin (all command permissions).");

    // -------------------------------------------------------------------------
    // /dmzhud  (server — OP default)
    // -------------------------------------------------------------------------
    public static final PermissionNode<Boolean> DMZHUD_TOGGLE =
            op("dmzhud.toggle", "Use /dmzhud toggle");
    public static final PermissionNode<Boolean> DMZHUD_ON =
            op("dmzhud.on", "Use /dmzhud on");
    public static final PermissionNode<Boolean> DMZHUD_OFF =
            op("dmzhud.off", "Use /dmzhud off");
    public static final PermissionNode<Boolean> DMZHUD_STATUS =
            op("dmzhud.status", "Use /dmzhud status");

    // -------------------------------------------------------------------------
    // /xenoserver  (server — OP default)
    // -------------------------------------------------------------------------
    public static final PermissionNode<Boolean> XENOSERVER_RELOAD =
            op("xenoserver.reload", "Use /xenoserver reload");
    public static final PermissionNode<Boolean> XENOSERVER_STATUS =
            op("xenoserver.status", "Use /xenoserver status");
    public static final PermissionNode<Boolean> XENOSERVER_SET =
            op("xenoserver.set", "Use /xenoserver set <key> <true|false>");

    // -------------------------------------------------------------------------
    // /xenoform  (server — OP default)
    // -------------------------------------------------------------------------
    public static final PermissionNode<Boolean> XENOFORM_STATUS =
            op("xenoform.status", "Use /xenoform status — view server form power scale");
    public static final PermissionNode<Boolean> XENOFORM_SET =
            op("xenoform.set", "Use /xenoform set <multiplier> — change server form power scale");

    // -------------------------------------------------------------------------
    // /xenohud  (client — everyone default)
    // -------------------------------------------------------------------------
    public static final PermissionNode<Boolean> XENOHUD_TOGGLE =
            client("xenohud.toggle", "Use /xenohud toggle");
    public static final PermissionNode<Boolean> XENOHUD_SHOW =
            client("xenohud.show", "Use /xenohud show");
    public static final PermissionNode<Boolean> XENOHUD_HIDE =
            client("xenohud.hide", "Use /xenohud hide");
    public static final PermissionNode<Boolean> XENOHUD_EDIT =
            client("xenohud.edit", "Use /xenohud edit");
    public static final PermissionNode<Boolean> XENOHUD_RESET =
            client("xenohud.reset", "Use /xenohud reset");
    public static final PermissionNode<Boolean> XENOHUD_RENDERER =
            client("xenohud.renderer", "Use /xenohud renderer");
    public static final PermissionNode<Boolean> XENOHUD_TECHRENDERER =
            client("xenohud.techrenderer", "Use /xenohud techrenderer");
    public static final PermissionNode<Boolean> XENOHUD_PORTRAIT =
            client("xenohud.portrait", "Use /xenohud portrait");
    public static final PermissionNode<Boolean> XENOHUD_PARTY =
            client("xenohud.party", "Use /xenohud party");
    public static final PermissionNode<Boolean> XENOHUD_TECHHUD_EDIT =
            client("xenohud.techhud.edit", "Use /xenohud techhud edit");
    public static final PermissionNode<Boolean> XENOHUD_TECHHUD_RESET =
            client("xenohud.techhud.reset", "Use /xenohud techhud reset");
    public static final PermissionNode<Boolean> XENOHUD_CD_EDIT =
            client("xenohud.cd.edit", "Use /xenohud cd edit");
    public static final PermissionNode<Boolean> XENOHUD_CD_SHOW =
            client("xenohud.cd.show", "Use /xenohud cd show");
    public static final PermissionNode<Boolean> XENOHUD_CD_HIDE =
            client("xenohud.cd.hide", "Use /xenohud cd hide");
    public static final PermissionNode<Boolean> XENOHUD_CD_RESET =
            client("xenohud.cd.reset", "Use /xenohud cd reset");
    public static final PermissionNode<Boolean> XENOHUD_CD_SHAPE =
            client("xenohud.cd.shape", "Use /xenohud cd shape");

    // -------------------------------------------------------------------------
    // /xenoclient  (client — everyone default)
    // -------------------------------------------------------------------------
    public static final PermissionNode<Boolean> XENOCLIENT_RELOAD =
            client("xenoclient.reload", "Use /xenoclient reload");
    public static final PermissionNode<Boolean> XENOCLIENT_STATUS =
            client("xenoclient.status", "Use /xenoclient status");
    public static final PermissionNode<Boolean> XENOCLIENT_SET =
            client("xenoclient.set", "Use /xenoclient set <key> <true|false>");

    private XenoPermissions() {
    }

    private static PermissionNode<Boolean> op(String nodeId, String description) {
        return register(nodeId, description, XenoPermissions::opDefault, false);
    }

    private static PermissionNode<Boolean> client(String nodeId, String description) {
        return register(nodeId, description, XenoPermissions::everyoneDefault, true);
    }

    private static PermissionNode<Boolean> register(
            String nodeId,
            String description,
            PermissionNode.PermissionResolver<Boolean> resolver,
            boolean clientDefaultAllow) {
        @SuppressWarnings("unchecked")
        PermissionDynamicContextKey<?>[] noCtx = new PermissionDynamicContextKey[0];
        PermissionNode<Boolean> node = new PermissionNode<>(
                XenoPixelsMod.MOD_ID,
                nodeId,
                PermissionTypes.BOOLEAN,
                resolver,
                noCtx);
        node.setInformation(Component.literal(nodeId), Component.literal(description));
        NODES.add(node);
        if (clientDefaultAllow) {
            CLIENT_DEFAULT_ALLOW.add(node);
        }
        return node;
    }

    private static Boolean opDefault(ServerPlayer player, UUID uuid, PermissionDynamicContext<?>... ctx) {
        return player != null && player.hasPermissions(2);
    }

    private static Boolean everyoneDefault(ServerPlayer player, UUID uuid, PermissionDynamicContext<?>... ctx) {
        return true;
    }

    @SubscribeEvent
    public static void onPermissionGather(PermissionGatherEvent.Nodes event) {
        event.addNodes(NODES.toArray(PermissionNode[]::new));
    }

    /** Brigadier {@code .requires(...)} helper. */
    public static Predicate<CommandSourceStack> require(PermissionNode<Boolean> node) {
        return src -> hasPermission(src, node);
    }

    public static boolean hasPermission(CommandSourceStack source, PermissionNode<Boolean> node) {
        if (source == null || node == null) return false;

        if (source.getEntity() instanceof ServerPlayer player) {
            try {
                if (Boolean.TRUE.equals(PermissionAPI.getPermission(player, ADMIN))) {
                    return true;
                }
                return Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
            } catch (Throwable t) {
                return fallbackLevel(source, node);
            }
        }

        return fallbackLevel(source, node);
    }

    private static boolean fallbackLevel(CommandSourceStack source, PermissionNode<Boolean> node) {
        if (CLIENT_DEFAULT_ALLOW.contains(node)) {
            return true;
        }
        return source.hasPermission(2);
    }
}
