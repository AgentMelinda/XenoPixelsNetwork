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
            op("xenoserver.set", "Use /xenoserver set <key> <value>");

    // -------------------------------------------------------------------------
    // /xenostats  (server — OP default)
    // -------------------------------------------------------------------------
    public static final PermissionNode<Boolean> XENOSTATS_STATUS =
            op("xenostats.status", "Use /xenostats get — read a player's DragonMineZ stats");
    public static final PermissionNode<Boolean> XENOSTATS_SET =
            op("xenostats.set", "Use /xenostats set — write a player's DragonMineZ stats");
    public static final PermissionNode<Boolean> XENOSTATS_LIMIT =
            op("xenostats.limit", "Use /xenostats limit — raise or lift the DragonMineZ stat cap");

    // -------------------------------------------------------------------------
    // /xenoform  (server — OP default)
    // -------------------------------------------------------------------------
    public static final PermissionNode<Boolean> XENOFORM_STATUS =
            op("xenoform.status", "Use /xenoform status — view server form power scale");
    public static final PermissionNode<Boolean> XENOFORM_SET =
            op("xenoform.set", "Use /xenoform set <multiplier> — change server form power scale");

    // -------------------------------------------------------------------------
    // /xenobarrage  (server — OP default)
    // -------------------------------------------------------------------------
    public static final PermissionNode<Boolean> XENOBARRAGE_STATUS =
            op("xenobarrage.status", "Use /xenobarrage status");
    public static final PermissionNode<Boolean> XENOBARRAGE_SET =
            op("xenobarrage.set", "Use /xenobarrage duration|cooldown <ticks>");
    public static final PermissionNode<Boolean> XENOKI_CLEAR =
            op("xenoki.clear", "Use /xenoki clear all|radius <blocks>");
    public static final PermissionNode<Boolean> XENOHOLOGRAMS_CLEAR =
            op("xenoholograms.clear", "Use /xenoholograms clear all|radius <blocks>");

    // -------------------------------------------------------------------------
    // /xenochase  (server — OP default)
    // -------------------------------------------------------------------------
    public static final PermissionNode<Boolean> XENOCHASE_STATUS =
            op("xenochase.status", "Use /xenochase status");
    public static final PermissionNode<Boolean> XENOCHASE_SET =
            op("xenochase.set", "Use /xenochase toggle|range <value>");

    // -------------------------------------------------------------------------
    // Hakai erasure technique (server — OP default)
    // -------------------------------------------------------------------------
    /** Gates actually using the Hakai ability itself, not just the admin command. */
    public static final PermissionNode<Boolean> HAKAI_USE =
            everyone("hakai.use", "Use the Hakai erasure technique");
    /** Allows Xeno rush techniques without changing DMZ's cost, cooldown, or target checks. */
    public static final PermissionNode<Boolean> BT3_RUSH_UNLOCK_BYPASS =
            op("bt3.rush.unlock_bypass", "Use Xeno rush techniques without DMZ unlock requirements");
    public static final PermissionNode<Boolean> HAKAI_SET =
            op("hakai.set", "Use /xenohakai toggle|kicost|range|cooldown <value>");

    // -------------------------------------------------------------------------
    // /xenolock  (server — OP default)
    // -------------------------------------------------------------------------
    public static final PermissionNode<Boolean> XENOLOCK_STATUS =
            op("xenolock.status", "Use /xenolock through status");
    public static final PermissionNode<Boolean> XENOLOCK_SET =
            op("xenolock.set", "Use /xenolock through on|off|toggle");

    public static final PermissionNode<Boolean> XENOAURA_SELF =
            client("xenoaura.self", "Use /xenoaura on|off|toggle on yourself");
    public static final PermissionNode<Boolean> XENOAURA_OTHERS =
            op("xenoaura.others", "Use /xenoaura on|off|toggle <player>");
    public static final PermissionNode<Boolean> XENOAURA_SET = XENOAURA_SELF;
    public static final PermissionNode<Boolean> STACK_SELF =
            client("stack.self", "Use /stack player on|off|toggle on yourself");
    public static final PermissionNode<Boolean> STACK_OTHERS =
            op("stack.others", "Use /stack player on|off|toggle on another player");
    public static final PermissionNode<Boolean> STACK_NPC =
            op("stack.npc", "Use /stack npc on|off|toggle on a CustomNPC");

    // -------------------------------------------------------------------------
    // /xenopixels npcprofile  (server — OP default; also runs via NPC-mod script
    // executeCommand, which uses command-block-equivalent permission)
    // -------------------------------------------------------------------------
    public static final PermissionNode<Boolean> NPCPROFILE_SET =
            op("npcprofile.set", "Use /xenopixels npcprofile set|color|aura ...");
    public static final PermissionNode<Boolean> NPCPROFILE_KIATTACK =
            op("npcprofile.kiattack", "Use /xenopixels npcprofile kiattack|charge|tech ...");
    public static final PermissionNode<Boolean> NPCSAY_TOGGLE =
            op("npcsay", "Use /xenopixels npcsay on|off|toggle");
    public static final PermissionNode<Boolean> GENHAIRCODE =
            op("genhaircode", "Use /xenopixels genhaircode <color>");

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
    public static final PermissionNode<Boolean> XENOPARTS_GLOBAL =
            op("xenoparts.global", "Publish / clear the server-wide HUD parts layout");
    public static final PermissionNode<Boolean> XENOANIM_GLOBAL =
            op("xenoanim.global", "Publish clips and bind live animation slots for every joiner");
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

    private static PermissionNode<Boolean> everyone(String nodeId, String description) {
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
                return playerFallback(source, node);
            }
        }

        // Non-player sources (command blocks, NPC-script executeCommand) get no
        // client-default allowance; they must hold permission level 2.
        return source.hasPermission(2);
    }

    private static boolean playerFallback(CommandSourceStack source, PermissionNode<Boolean> node) {
        if (CLIENT_DEFAULT_ALLOW.contains(node)) {
            return true;
        }
        return source.hasPermission(2);
    }
}
