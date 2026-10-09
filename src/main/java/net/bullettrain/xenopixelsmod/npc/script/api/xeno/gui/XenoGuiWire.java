package net.bullettrain.xenopixelsmod.npc.script.api.xeno.gui;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.HashSet;
import java.util.Set;

/** Bounded, data-only GUI snapshots. Callback objects and script source never cross the wire. */
public final class XenoGuiWire {
    public static final int MAX_BYTES = 131072;
    public static final int MAX_COMPONENTS = 256;
    public static final int MAX_TEXT = 4096;
    private static final Set<String> ROOT_KEYS = Set.of("Session", "Revision", "Name", "Width", "Height", "Pause", "Escape", "Background", "Nodes");
    private static final Set<String> NODE_KEYS = Set.of("Key", "ID", "Kind", "X", "Y", "W", "H", "Text", "Color", "Texture", "U", "V", "TexW", "TexH", "Hover", "Enabled", "Visible", "Focused", "HideBackground", "CharacterType");
    public static final Set<String> ACTIONS = Set.of("button", "text", "focus", "blur", "hover", "exit", "close", "resize");
    private XenoGuiWire() {}

    public static void validate(CompoundTag tag) {
        if (tag == null || !ROOT_KEYS.containsAll(tag.getAllKeys()) || !tag.hasUUID("Session")) bad();
        integer(tag, "Revision", 1, Integer.MAX_VALUE);
        integer(tag, "Width", 1, 4096); integer(tag, "Height", 1, 4096);
        text(tag, "Name", 1024); resource(tag, "Background");
        flag(tag, "Pause"); flag(tag, "Escape");
        if (!tag.contains("Nodes", Tag.TAG_LIST)) bad();
        var nodes = tag.getList("Nodes", Tag.TAG_COMPOUND);
        if (nodes.size() > MAX_COMPONENTS || (!tag.getList("Nodes", Tag.TAG_COMPOUND).isEmpty() && nodes.getElementType() != Tag.TAG_COMPOUND)) bad();
        // A nonempty list with the wrong element type must not silently become an empty list.
        if (tag.get("Nodes") instanceof net.minecraft.nbt.ListTag original && !original.isEmpty() && original.getElementType() != Tag.TAG_COMPOUND) bad();
        var keys = new HashSet<java.util.UUID>(); var ids = new HashSet<Integer>();
        int characters = tag.getString("Name").length() + tag.getString("Background").length();
        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.getCompound(i);
            if (!NODE_KEYS.containsAll(node.getAllKeys()) || !node.hasUUID("Key") || !keys.add(node.getUUID("Key"))) bad();
            integer(node, "ID", Integer.MIN_VALUE, Integer.MAX_VALUE);
            if (!ids.add(node.getInt("ID"))) bad();
            text(node, "Kind", 16);
            if (!Set.of("button", "label", "textfield", "rect").contains(node.getString("Kind"))) bad();
            integer(node, "X", -8192, 8192); integer(node, "Y", -8192, 8192);
            integer(node, "W", 0, 4096); integer(node, "H", 0, 4096);
            text(node, "Text", MAX_TEXT); resource(node, "Texture");
            if (node.contains("CharacterType")) integer(node, "CharacterType", 0, 4);
            for (String name : new String[]{"U", "V", "TexW", "TexH"}) if (node.contains(name)) integer(node, name, 0, 8192);
            for (String name : new String[]{"Enabled", "Visible", "Focused", "HideBackground"}) if (node.contains(name)) flag(node, name);
            if (node.contains("Color") && !node.contains("Color", Tag.TAG_INT)) bad();
            if (node.contains("Hover") && !node.contains("Hover", Tag.TAG_LIST)) bad();
            var hover = node.getList("Hover", Tag.TAG_STRING);
            if (node.get("Hover") instanceof net.minecraft.nbt.ListTag original && !original.isEmpty() && original.getElementType() != Tag.TAG_STRING) bad();
            if (hover.size() > 16) bad();
            for (int h = 0; h < hover.size(); h++) { if (hover.getString(h).length() > 1024) bad(); characters += hover.getString(h).length(); }
            characters += node.getString("Text").length() + node.getString("Texture").length();
            if (characters > 24576) bad();
        }
    }
    private static void integer(CompoundTag tag, String key, int min, int max) {
        if (!tag.contains(key, Tag.TAG_INT) || tag.getInt(key) < min || tag.getInt(key) > max) bad();
    }
    private static void text(CompoundTag tag, String key, int maximum) {
        if (!tag.contains(key, Tag.TAG_STRING) || tag.getString(key).length() > maximum) bad();
    }
    private static void resource(CompoundTag tag, String key) {
        text(tag, key, 256); String value = tag.getString(key);
        if (!value.isEmpty() && ResourceLocation.tryParse(value) == null) bad();
    }
    private static void flag(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_BYTE) || (tag.getByte(key) != 0 && tag.getByte(key) != 1)) bad();
    }
    private static void bad() { throw new IllegalArgumentException("Invalid or oversized scripted GUI snapshot"); }
    public static void encode(CompoundTag tag, FriendlyByteBuf target) {
        validate(tag);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeNbt(tag);
            if (buffer.readableBytes() > MAX_BYTES) bad();
            byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes); target.writeByteArray(bytes);
        } finally { buffer.release(); }
    }
    public static CompoundTag decode(FriendlyByteBuf source) {
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(source.readByteArray(MAX_BYTES)));
        try {
            Tag value = buffer.readNbt(NbtAccounter.create(MAX_BYTES));
            if (!(value instanceof CompoundTag tag) || buffer.isReadable()) { bad(); return null; }
            validate(tag); return tag;
        } finally { buffer.release(); }
    }
}
