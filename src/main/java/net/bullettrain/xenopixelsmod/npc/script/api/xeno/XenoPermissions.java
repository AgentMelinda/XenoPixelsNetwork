package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Permission nodes for XenoAPI's {@code hasPermission}/{@code hasPermissionNode}, answered by
 * NeoForge's {@link PermissionAPI}, so a permissions mod decides. Only registered boolean nodes
 * count: an unknown name is false rather than guessed from operator status.
 */
final class XenoPermissions {
    private XenoPermissions() {}

    @SuppressWarnings("unchecked")
    static PermissionNode<Boolean> node(String name) {
        if (name == null || name.isBlank()) return null;
        String wanted = name.trim();
        for (PermissionNode<?> node : PermissionAPI.getRegisteredNodes()) {
            if (node.getNodeName().equals(wanted) && node.getType() == PermissionTypes.BOOLEAN) {
                return (PermissionNode<Boolean>) node;
            }
        }
        return null;
    }

    static boolean has(ServerPlayer player, String name) {
        PermissionNode<Boolean> node = node(name);
        return node != null && player != null && Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
    }
}
