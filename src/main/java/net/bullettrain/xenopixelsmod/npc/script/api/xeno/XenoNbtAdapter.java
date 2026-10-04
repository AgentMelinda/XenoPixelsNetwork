package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import xenoapi.npcs.api.INbt;

import java.util.List;
import java.util.Objects;

/**
 * A live compound tag as XenoAPI's {@link INbt}: writes change the wrapped tag. Tags reached
 * through {@link #getCompound} are the nested live tags, not copies.
 */
public final class XenoNbtAdapter implements INbt {
    /** Bounds for values written through the adapter, so a script cannot grow a tag without limit. */
    static final int MAX_STRING = 32_767;
    static final int MAX_ARRAY = 65_536;

    final CompoundTag tag;

    public XenoNbtAdapter(CompoundTag tag) {
        this.tag = Objects.requireNonNull(tag);
    }

    private static String key(String key) {
        if (key == null) throw new IllegalArgumentException("NBT key cannot be null");
        return key;
    }

    private static void requireArray(int length, boolean isNull, String what) {
        if (isNull || length > MAX_ARRAY) {
            throw new IllegalArgumentException("NBT " + what + " must be non-null and at most " + MAX_ARRAY + " long");
        }
    }

    @Override public void remove(String key) { tag.remove(key(key)); }
    @Override public boolean has(String key) { return key != null && tag.contains(key); }
    @Override public boolean getBoolean(String key) { return tag.getBoolean(key(key)); }
    @Override public void setBoolean(String key, boolean value) { tag.putBoolean(key(key), value); }
    @Override public short getShort(String key) { return tag.getShort(key(key)); }
    @Override public void setShort(String key, short value) { tag.putShort(key(key), value); }
    @Override public int getInteger(String key) { return tag.getInt(key(key)); }
    @Override public void setInteger(String key, int value) { tag.putInt(key(key), value); }
    @Override public byte getByte(String key) { return tag.getByte(key(key)); }
    @Override public void setByte(String key, byte value) { tag.putByte(key(key), value); }
    @Override public long getLong(String key) { return tag.getLong(key(key)); }
    @Override public void setLong(String key, long value) { tag.putLong(key(key), value); }
    @Override public double getDouble(String key) { return tag.getDouble(key(key)); }
    @Override public void setDouble(String key, double value) { tag.putDouble(key(key), value); }
    @Override public float getFloat(String key) { return tag.getFloat(key(key)); }
    @Override public void setFloat(String key, float value) { tag.putFloat(key(key), value); }
    @Override public String getString(String key) { return tag.getString(key(key)); }

    @Override
    public void putString(String key, String value) {
        tag.putString(key(key), string(value));
    }

    private static String string(String value) {
        if (value == null) throw new IllegalArgumentException("NBT string value cannot be null");
        if (value.length() > MAX_STRING) throw new IllegalArgumentException("NBT string is over " + MAX_STRING + " characters");
        return value;
    }

    @Override public byte[] getByteArray(String key) { return tag.getByteArray(key(key)); }

    @Override
    public void setByteArray(String key, byte[] value) {
        requireArray(value == null ? 0 : value.length, value == null, "byte array");
        tag.putByteArray(key(key), value.clone());
    }

    @Override public int[] getIntegerArray(String key) { return tag.getIntArray(key(key)); }

    @Override
    public void setIntegerArray(String key, int[] value) {
        requireArray(value == null ? 0 : value.length, value == null, "int array");
        tag.putIntArray(key(key), value.clone());
    }

    /** Elements of the list as Java values: numbers, strings, INbt for compounds, arrays. */
    @Override
    public Object[] getList(String key, int type) {
        ListTag list = tag.getList(key(key), type);
        Object[] out = new Object[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = toJava(list.get(i));
        return out;
    }

    @Override
    public int getListType(String key) {
        return tag.get(key(key)) instanceof ListTag list ? list.getElementType() : Tag.TAG_END;
    }

    /** Every element must share one type; mixing types is refused before the tag changes. */
    @Override
    public void setList(String key, Object[] value) {
        requireArray(value == null ? 0 : value.length, value == null, "list");
        String name = key(key);
        ListTag list = new ListTag();
        for (Object element : value) {
            Tag converted = toTag(element);
            if (!list.isEmpty() && list.getElementType() != converted.getId()) {
                throw new IllegalArgumentException("NBT list elements must all have one type");
            }
            list.add(converted);
        }
        tag.put(name, list);
    }

    private static Object toJava(Tag element) {
        if (element instanceof CompoundTag compound) return new XenoNbtAdapter(compound);
        if (element instanceof StringTag string) return string.getAsString();
        if (element instanceof ByteTag b) return b.getAsByte();
        if (element instanceof ShortTag s) return s.getAsShort();
        if (element instanceof IntTag i) return i.getAsInt();
        if (element instanceof LongTag l) return l.getAsLong();
        if (element instanceof FloatTag f) return f.getAsFloat();
        if (element instanceof NumericTag n) return n.getAsDouble();
        if (element instanceof ByteArrayTag bytes) return bytes.getAsByteArray();
        if (element instanceof IntArrayTag ints) return ints.getAsIntArray();
        return element.getAsString();
    }

    private static Tag toTag(Object element) {
        if (element instanceof XenoNbtAdapter nbt) return nbt.tag.copy();
        if (element instanceof String string) return StringTag.valueOf(string(string));
        if (element instanceof Byte b) return ByteTag.valueOf(b);
        if (element instanceof Short s) return ShortTag.valueOf(s);
        if (element instanceof Integer i) return IntTag.valueOf(i);
        if (element instanceof Long l) return LongTag.valueOf(l);
        if (element instanceof Float f) return FloatTag.valueOf(f);
        if (element instanceof Number n) return DoubleTag.valueOf(n.doubleValue());
        if (element instanceof Boolean flag) return ByteTag.valueOf(flag);
        throw new IllegalArgumentException("Unsupported NBT list element: "
                + (element == null ? "null" : element.getClass().getSimpleName()));
    }

    /** The nested live compound; a missing key reads as a new detached empty compound. */
    @Override
    public INbt getCompound(String key) {
        return new XenoNbtAdapter(tag.getCompound(key(key)));
    }

    @Override
    public void setCompound(String key, INbt value) {
        String name = key(key);
        tag.put(name, XenoApiAdapters.unwrap(value).copy());
    }

    @Override public String[] getKeys() { return tag.getAllKeys().toArray(String[]::new); }
    @Override public int getType(String key) { return tag.getTagType(key(key)); }

    /** Tags are plain data, so the handle grants nothing beyond this adapter. */
    @Override public CompoundTag getMCNBT() { return tag; }
    @Override public String toJsonString() { return tag.toString(); }

    @Override
    public boolean isEqual(INbt nbt) {
        return nbt != null && XenoApiAdapters.unwrap(nbt).equals(tag);
    }

    @Override
    public void clear() {
        for (String key : List.copyOf(tag.getAllKeys())) tag.remove(key);
    }

    @Override public boolean isEmpty() { return tag.isEmpty(); }
    @Override public void merge(INbt nbt) { tag.merge(XenoApiAdapters.unwrap(nbt).copy()); }

    @Override
    public void mcSetTag(String key, Tag base) {
        if (base == null) throw new IllegalArgumentException("NBT tag cannot be null");
        tag.put(key(key), base.copy());
    }

    @Override public Tag mcGetTag(String key) { return tag.get(key(key)); }

    @Override
    public boolean equals(Object other) {
        return other instanceof XenoNbtAdapter that && that.tag == tag;
    }

    @Override public int hashCode() { return System.identityHashCode(tag); }
    @Override public String toString() { return toJsonString(); }
}
