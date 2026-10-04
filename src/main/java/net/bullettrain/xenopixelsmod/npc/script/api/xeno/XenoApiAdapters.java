package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import xenoapi.npcs.api.CustomNPCsException;
import xenoapi.npcs.api.INbt;
import xenoapi.npcs.api.IPos;
import xenoapi.npcs.api.IWorld;
import xenoapi.npcs.api.constants.EntitiesType;
import xenoapi.npcs.api.entity.IEntity;
import xenoapi.npcs.api.item.IItemStack;

/**
 * Explicit conversions between native Minecraft objects and XenoAPI adapters, plus the checks
 * every adapter shares. Adapters from another implementation are refused: only native adapters
 * reach a native mutation path.
 */
public final class XenoApiAdapters {
    /** Where the capability table lives; every unsupported-operation message points there. */
    public static final String CAPABILITY_DOC = "docs/native-xenoapi-adapters.md";
    static final int MAX_COMMAND = 256;
    static final int MAX_COMMAND_OUTPUT = 4096;

    private XenoApiAdapters() {}

    // ------------------------------------------------------------------ wrapping

    /** The most specific adapter: NPC, player, living, or plain entity. Null stays null. */
    public static IEntity<?> wrap(Entity entity) {
        if (entity == null) return null;
        if (entity instanceof XenoNpcEntity npc) return new XenoNpcAdapter(npc);
        if (entity instanceof ServerPlayer player) return new XenoPlayerAdapter(player);
        if (entity instanceof LivingEntity living) return new XenoLivingAdapter<>(living);
        if (entity instanceof ItemEntity item) return new XenoEntityItemAdapter(item);
        return new XenoEntityAdapter<>(entity);
    }

    public static IWorld wrap(ServerLevel level) { return level == null ? null : new XenoWorldAdapter(level); }
    public static IItemStack wrap(ItemStack stack) { return stack == null ? null : new XenoItemAdapter(stack); }
    public static INbt wrap(CompoundTag tag) { return tag == null ? null : new XenoNbtAdapter(tag); }

    public static IPos position(double x, double y, double z) {
        requireFinite("IPos", x, y, z);
        return new XenoPosAdapter(BlockPos.containing(x, y, z));
    }

    // ------------------------------------------------------------------ unwrapping

    /** The native entity behind an adapter; null for null, refused for a foreign adapter. */
    public static Entity unwrap(IEntity<?> entity) {
        if (entity == null) return null;
        if (entity instanceof XenoEntityAdapter<?> nativeEntity) return nativeEntity.entity;
        throw new IllegalArgumentException("Foreign XenoAPI entity adapter: " + entity.getClass().getName());
    }

    static LivingEntity unwrapLiving(IEntity<?> entity) {
        Entity unwrapped = unwrap(entity);
        if (unwrapped == null || unwrapped instanceof LivingEntity) return (LivingEntity) unwrapped;
        throw new IllegalArgumentException("Expected a living entity, got " + unwrapped.getType());
    }

    /** The native stack behind an item adapter; null reads as the empty stack. */
    static ItemStack unwrap(IItemStack item) {
        if (item == null) return ItemStack.EMPTY;
        if (item instanceof XenoItemAdapter nativeItem) return nativeItem.stack;
        throw new IllegalArgumentException("Foreign XenoAPI item adapter: " + item.getClass().getName());
    }

    static CompoundTag unwrap(INbt tag) {
        if (tag == null) throw new IllegalArgumentException("NBT cannot be null");
        if (tag instanceof XenoNbtAdapter nativeTag) return nativeTag.tag;
        throw new IllegalArgumentException("Foreign XenoAPI NBT adapter: " + tag.getClass().getName());
    }

    static ServerLevel unwrap(IWorld world) {
        if (world == null) throw new IllegalArgumentException("World cannot be null");
        if (world instanceof XenoWorldAdapter nativeWorld) return nativeWorld.level;
        throw new IllegalArgumentException("Foreign XenoAPI world adapter: " + world.getClass().getName());
    }

    // ------------------------------------------------------------------ shared checks

    /** The error every contract method without a native equivalent throws, naming the method. */
    public static UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException("XenoAPI " + method
                + " is not supported by native Xeno NPCs; see " + CAPABILITY_DOC);
    }

    /** Refuses client levels and calls from any thread but the server's own. */
    static void requireServerThread(Level level) {
        if (level == null || level.isClientSide()) {
            throw new IllegalStateException("XenoAPI mutations run only on the logical server");
        }
        MinecraftServer server = level.getServer();
        if (server != null && !server.isSameThread()) {
            throw new IllegalStateException("XenoAPI mutations must run on the server thread");
        }
    }

    /** For views with no level of their own: refuses calls from any thread but the running server's. */
    static void requireServerThreadNow() {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null && !server.isSameThread()) {
            throw new IllegalStateException("XenoAPI mutations must run on the server thread");
        }
    }

    static void requireFinite(String method, double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException(method + ": values must be finite");
        }
    }

    /** Trimmed text of at most {@code max} characters; null reads as empty. */
    static String boundedText(String method, String text, int max) {
        String value = text == null ? "" : text.trim();
        if (value.length() > max) throw new IllegalArgumentException(method + ": text is over " + max + " characters");
        return value;
    }

    /** True for the entity's own XenoAPI type and every broader family it belongs to. */
    static boolean typeOf(Entity entity, int type) {
        return switch (type) {
            case EntitiesType.ANY -> true;
            case EntitiesType.PLAYER -> entity instanceof ServerPlayer;
            case EntitiesType.NPC -> entity instanceof XenoNpcEntity;
            case EntitiesType.MONSTER -> entity instanceof Enemy && entity instanceof LivingEntity;
            case EntitiesType.ANIMAL -> entity instanceof Animal;
            case EntitiesType.LIVING -> entity instanceof LivingEntity;
            case EntitiesType.ITEM -> entity instanceof ItemEntity;
            case EntitiesType.PROJECTILE -> entity instanceof Projectile;
            case EntitiesType.ARROW -> entity instanceof AbstractArrow;
            case EntitiesType.THROWABLE -> entity instanceof ThrowableProjectile;
            case EntitiesType.VILLAGER -> entity instanceof AbstractVillager;
            case EntitiesType.UNKNOWN -> wrap(entity).getType() == EntitiesType.UNKNOWN;
            default -> false;
        };
    }

    /** The registered item for {@code id}; null for a blank, unparsable, unknown or air id. */
    static Item knownItem(String id) {
        ResourceLocation key = id == null || id.isBlank() ? null : ResourceLocation.tryParse(id);
        Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        return item == null || item == Items.AIR ? null : item;
    }

    static Item item(String id) {
        Item item = knownItem(id);
        if (item == null) throw new CustomNPCsException("Unknown item id: %s", id);
        return item;
    }

    static SoundEvent sound(String id) {
        ResourceLocation key = id == null ? null : ResourceLocation.tryParse(id);
        SoundEvent sound = key == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(key).orElse(null);
        if (sound == null) throw new CustomNPCsException("Unknown sound id: %s", id);
        return sound;
    }

    /**
     * Runs {@code command} from {@code source} and returns what it printed. Output reaches only
     * the returned text: nothing is sent to chat or to operators.
     */
    static String runCommand(MinecraftServer server, CommandSourceStack source, String command) {
        if (command == null || command.isBlank() || command.length() > MAX_COMMAND) {
            throw new IllegalArgumentException("Command must be 1-" + MAX_COMMAND + " characters");
        }
        StringBuilder output = new StringBuilder();
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                if (output.length() >= MAX_COMMAND_OUTPUT) return;
                if (!output.isEmpty()) output.append('\n');
                output.append(message.getString());
            }

            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        String line = command.startsWith("/") ? command.substring(1) : command;
        server.getCommands().performPrefixedCommand(source.withSource(capture), line);
        return output.length() > MAX_COMMAND_OUTPUT ? output.substring(0, MAX_COMMAND_OUTPUT) : output.toString();
    }
}
