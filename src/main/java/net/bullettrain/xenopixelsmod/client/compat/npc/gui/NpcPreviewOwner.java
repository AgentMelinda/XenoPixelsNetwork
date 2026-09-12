package net.bullettrain.xenopixelsmod.client.compat.npc.gui;

/**
 * Marker for wand screens that already draw a DMZ NPC visualizer.
 *
 * <p>The shared {@code GuiNPCInterface2} mixin parks {@link NpcPreviewPanel} on every native tab.
 * DMZ screens that host their own panel (or an equivalent inline preview) implement this so the
 * mixin does not draw a second one on top.
 */
public interface NpcPreviewOwner {
}
