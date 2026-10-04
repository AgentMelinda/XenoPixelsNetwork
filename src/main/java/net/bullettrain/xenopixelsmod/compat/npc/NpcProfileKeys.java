package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Generic key access to a saved NPC profile tag, so a script can read, set or toggle any setting
 * the editor saves - every DMZ tab field included - by its tag name ({@code "AuraOn"},
 * {@code "DmzAppearance.SaiyanTail"}). Keys are case-insensitive; dots walk nested compounds.
 *
 * <p>Only an existing leaf may be written, and only with a value of its stored type, so a script
 * cannot invent keys or change a number into a string. The caller turns the edited tag back into
 * a profile through {@code NpcCombatProfile.fromTag}, which applies the usual clamps.
 */
public final class NpcProfileKeys {
    /** Bookkeeping the profile owns; never script-writable. */
    private static final Set<String> PROTECTED = Set.of("schema", "dmzstatsnapshot", "nativemeleedefaultsapplied");

    private NpcProfileKeys() {}

    /** The stored value as a plain Java value (Boolean for byte flags), or null when absent. */
    public static Object get(CompoundTag root, String path) {
        Leaf leaf = find(root, path);
        if (leaf == null) return null;
        Tag tag = leaf.parent.get(leaf.key);
        if (tag instanceof ByteTag b) return b.getAsByte() != 0;
        if (tag instanceof IntTag i) return i.getAsInt();
        if (tag instanceof FloatTag f) return f.getAsFloat();
        if (tag instanceof DoubleTag d) return d.getAsDouble();
        if (tag instanceof LongTag l) return l.getAsLong();
        if (tag instanceof ShortTag s) return (int) s.getAsShort();
        if (tag instanceof StringTag s) return s.getAsString();
        return tag == null ? null : tag.toString();
    }

    /** Writes an existing, unprotected leaf with a value of its stored type. */
    public static boolean set(CompoundTag root, String path, Object value) {
        Leaf leaf = find(root, path);
        if (leaf == null || value == null || isProtected(path)) return false;
        Tag old = leaf.parent.get(leaf.key);
        Tag next = convert(old, value);
        if (next == null) return false;
        leaf.parent.put(leaf.key, next);
        return true;
    }

    /** Flips a boolean flag. False for a missing, protected or non-flag key. */
    public static boolean toggle(CompoundTag root, String path) {
        Object current = get(root, path);
        return current instanceof Boolean b && set(root, path, !b);
    }

    /** Every writable leaf as a dotted path, in stored order. */
    public static List<String> keys(CompoundTag root) {
        List<String> out = new ArrayList<>();
        collect(root, "", out);
        return out;
    }

    private static void collect(CompoundTag tag, String prefix, List<String> out) {
        for (String key : tag.getAllKeys()) {
            String path = prefix + key;
            Tag child = tag.get(key);
            if (child instanceof CompoundTag nested) {
                if (!isProtected(path)) collect(nested, path + ".", out);
            } else if ((child instanceof NumericTag || child instanceof StringTag) && !isProtected(path)) {
                out.add(path);
            }
        }
    }

    private static Tag convert(Tag old, Object value) {
        if (old instanceof ByteTag) {
            if (value instanceof Boolean b) return ByteTag.valueOf(b);
            if (value instanceof Number n) return ByteTag.valueOf((byte) n.intValue());
            return null;
        }
        if (old instanceof StringTag) {
            return value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                    ? StringTag.valueOf(String.valueOf(value)) : null;
        }
        if (!(value instanceof Number n)) return null;
        double d = n.doubleValue();
        if (!Double.isFinite(d)) return null;
        if (old instanceof IntTag) return IntTag.valueOf(n.intValue());
        if (old instanceof FloatTag) return FloatTag.valueOf(n.floatValue());
        if (old instanceof DoubleTag) return DoubleTag.valueOf(d);
        if (old instanceof LongTag) return LongTag.valueOf(n.longValue());
        if (old instanceof ShortTag) return ShortTag.valueOf(n.shortValue());
        return null;
    }

    private static boolean isProtected(String path) {
        String first = path.split("\\.", 2)[0].toLowerCase(java.util.Locale.ROOT);
        return PROTECTED.contains(first);
    }

    private record Leaf(CompoundTag parent, String key) {}

    /** Resolves a case-insensitive dotted path to an existing non-compound leaf. */
    private static Leaf find(CompoundTag root, String path) {
        if (root == null || path == null || path.isBlank()) return null;
        String[] parts = path.trim().split("\\.");
        CompoundTag at = root;
        for (int i = 0; i < parts.length; i++) {
            String key = actualKey(at, parts[i]);
            if (key == null) return null;
            Tag child = at.get(key);
            if (i == parts.length - 1) {
                return child instanceof NumericTag || child instanceof StringTag ? new Leaf(at, key) : null;
            }
            if (!(child instanceof CompoundTag nested)) return null;
            at = nested;
        }
        return null;
    }

    private static String actualKey(CompoundTag tag, String wanted) {
        if (tag.contains(wanted)) return wanted;
        for (String key : tag.getAllKeys()) if (key.equalsIgnoreCase(wanted)) return key;
        return null;
    }
}
