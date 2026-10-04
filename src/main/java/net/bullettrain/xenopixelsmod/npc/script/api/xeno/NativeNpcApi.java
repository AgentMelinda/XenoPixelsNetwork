package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import xenoapi.npcs.api.CustomNPCsException;
import xenoapi.npcs.api.IContainer;
import xenoapi.npcs.api.IDamageSource;
import xenoapi.npcs.api.INbt;
import xenoapi.npcs.api.IPos;
import xenoapi.npcs.api.IWorld;
import xenoapi.npcs.api.NpcAPI;
import xenoapi.npcs.api.block.IBlock;
import xenoapi.npcs.api.entity.ICustomNpc;
import xenoapi.npcs.api.entity.IEntity;
import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.entity.data.IPlayerMail;
import xenoapi.npcs.api.gui.ICustomGui;
import xenoapi.npcs.api.handler.ICloneHandler;
import xenoapi.npcs.api.handler.IDialogHandler;
import xenoapi.npcs.api.handler.IFactionHandler;
import xenoapi.npcs.api.handler.IQuestHandler;
import xenoapi.npcs.api.handler.IRecipeHandler;
import xenoapi.npcs.api.item.IItemStack;

import java.io.File;
import java.util.function.Supplier;

/**
 * The native implementation of XenoAPI's {@link NpcAPI}, registered once at mod construction.
 * Nothing here holds a server or level: world-dependent calls resolve the running server per call
 * and fail with {@link IllegalStateException} before startup and after shutdown.
 *
 * <p>{@link #events()} is a dedicated bus for API consumers. Gameplay does not post typed XenoAPI
 * events to it yet; native NPC scripts keep their existing hook dispatch.
 */
public final class NativeNpcApi extends NpcAPI {
    private final net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventBus events =
            new net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventBus(BusBuilder.builder()
                    .setExceptionHandler(net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch::onListenerError)
                    .build());
    private final Supplier<MinecraftServer> servers;

    NativeNpcApi(Supplier<MinecraftServer> servers) {
        this.servers = servers;
    }

    /**
     * Registers the native implementation as {@link NpcAPI#Instance()} once. A second call is a
     * no-op; an implementation from another mod already in place is left alone and logged.
     */
    public static synchronized void register() {
        NpcAPI current = NpcAPI.Instance();
        if (current instanceof NativeNpcApi) return;
        if (current != null) {
            XenoPixelsMod.LOGGER.warn("XenoAPI already provided by {}; native Xeno NPC adapters not registered",
                    current.getClass().getName());
            return;
        }
        NpcAPI.setInstance(new NativeNpcApi(ServerLifecycleHooks::getCurrentServer));
        XenoPixelsMod.LOGGER.info("XenoAPI: native Xeno NPC implementation registered");
    }

    /** The running server, or a documented failure before startup / after shutdown. */
    MinecraftServer server(String method) {
        MinecraftServer server = servers.get();
        if (server == null || !server.isRunning()) {
            throw new IllegalStateException("XenoAPI " + method + " needs a running server");
        }
        return server;
    }

    private static ServerLevel serverLevel(Level level, String method) {
        if (level instanceof ServerLevel server) return server;
        throw new IllegalArgumentException("XenoAPI " + method + " needs a server level");
    }

    // ------------------------------------------------------------------ NPCs

    /** A new native humanoid NPC that is not yet in the world; call {@code spawn()} to add it. */
    @Override
    public ICustomNpc createNPC(Level world) {
        ServerLevel level = serverLevel(world, "createNPC");
        XenoApiAdapters.requireServerThread(level);
        XenoNpcEntity npc = ModEntities.XENO_NPC_HUMANOID.get().create(level);
        if (npc == null) throw new CustomNPCsException("Native NPC could not be created");
        return new XenoNpcAdapter(npc);
    }

    /** A native humanoid NPC placed at the block centre and added to the world, home set there. */
    @Override
    public ICustomNpc spawnNPC(Level world, int x, int y, int z) {
        ServerLevel level = serverLevel(world, "spawnNPC");
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
            throw new IllegalArgumentException("XenoAPI spawnNPC: position is not loaded or is outside the build height");
        }
        XenoApiAdapters.requireServerThread(level);
        XenoNpcEntity npc = ModEntities.XENO_NPC_HUMANOID.get().create(level);
        if (npc == null) throw new CustomNPCsException("Native NPC could not be created");
        npc.moveTo(x + 0.5, y, z + 0.5, 0.0f, 0.0f);
        npc.npcData().setHome(npc.getX(), npc.getY(), npc.getZ());
        level.addFreshEntity(npc);
        return new XenoNpcAdapter(npc);
    }

    // ------------------------------------------------------------------ conversions

    @Override public IEntity getIEntity(Entity entity) { return XenoApiAdapters.wrap(entity); }

    @Override
    public IBlock getIBlock(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) throw new IllegalArgumentException("XenoAPI getIBlock needs a server level");
        if (pos == null) throw new IllegalArgumentException("XenoAPI getIBlock: pos cannot be null");
        return new XenoBlockAdapter(server, pos);
    }
    @Override public IItemStack getIItemStack(ItemStack itemstack) { return XenoApiAdapters.wrap(itemstack); }
    @Override public IWorld getIWorld(ServerLevel world) { return XenoApiAdapters.wrap(world); }
    @Override public INbt getINbt(CompoundTag compound) { return XenoApiAdapters.wrap(compound); }
    @Override public IPos getIPos(double x, double y, double z) { return XenoApiAdapters.position(x, y, z); }

    @Override
    public IDamageSource getIDamageSource(DamageSource damagesource) {
        return damagesource == null ? null : new XenoDamageSourceAdapter(damagesource);
    }

    /** The level for a dimension id such as {@code minecraft:the_nether}, or null. */
    @Override
    public IWorld getIWorld(String dimension) {
        ResourceLocation id = dimension == null ? null : ResourceLocation.tryParse(dimension);
        if (id == null) throw new IllegalArgumentException("XenoAPI getIWorld: invalid dimension id " + dimension);
        for (ServerLevel level : server("getIWorld").getAllLevels()) {
            if (level.dimension().location().equals(id)) return XenoApiAdapters.wrap(level);
        }
        return null;
    }

    /** The first level of that dimension type, or null. */
    @Override
    public IWorld getIWorld(DimensionType dimension) {
        if (dimension == null) throw new IllegalArgumentException("XenoAPI getIWorld: dimension type cannot be null");
        for (ServerLevel level : server("getIWorld").getAllLevels()) {
            if (level.dimensionType() == dimension) return XenoApiAdapters.wrap(level);
        }
        return null;
    }

    @Override
    public IWorld[] getIWorlds() {
        java.util.List<IWorld> worlds = new java.util.ArrayList<>();
        for (ServerLevel level : server("getIWorlds").getAllLevels()) worlds.add(XenoApiAdapters.wrap(level));
        return worlds.toArray(IWorld[]::new);
    }

    @Override
    public INbt stringToNbt(String str) {
        if (str == null || str.length() > 65_536) throw new CustomNPCsException("NBT text must be non-null and at most 65536 characters");
        try {
            return XenoApiAdapters.wrap(TagParser.parseTag(str));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new CustomNPCsException(e, "Invalid NBT: %s", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ commands / files

    /** Runs a command at the world's spawn at permission level 2 and returns its output. */
    @Override
    public String executeCommand(IWorld world, String command) {
        return command(world, command, "executeCommand");
    }

    /** As {@link #executeCommand}; output never reaches chat or operators in either form. */
    @Override
    public String executeCommandSilent(IWorld world, String command) {
        return command(world, command, "executeCommandSilent");
    }

    private String command(IWorld world, String command, String method) {
        ServerLevel level = XenoApiAdapters.unwrap(world);
        MinecraftServer server = server(method);
        XenoApiAdapters.requireServerThread(level);
        CommandSourceStack source = new CommandSourceStack(CommandSource.NULL,
                Vec3.atBottomCenterOf(level.getSharedSpawnPos()), Vec2.ZERO, level, 2,
                "XenoAPI", Component.literal("XenoAPI"), server, null);
        return XenoApiAdapters.runCommand(server, source, command);
    }

    /** The current world's save folder. */
    @Override
    public File getLevelDir() {
        return server("getLevelDir").getWorldPath(LevelResource.ROOT).toFile();
    }

    @Override public IEventBus events() { return events; }

    /** The counting bus behind events(), for the build-only-when-listened gate. */
    public net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventBus eventBus() { return events; }

    // ------------------------------------------------------------------ containers

    @Override public IContainer getIContainer(Container container) { return XenoContainerAdapter.of(container); }
    @Override public IContainer getIContainer(AbstractContainerMenu container) { return XenoContainerAdapter.of(container); }

    // ------------------------------------------------------------------ content handlers

    /** Store and datapack factions; numbers are the ones imported content carries. */
    @Override public IFactionHandler getFactions() { return new XenoFactionHandler(); }

    /** Store, datapack and built-in quests. */
    @Override public IQuestHandler getQuests() { return new XenoQuestHandler(); }

    /** Stored conversations; an imported dialog N is its tree {@code dialog_N}. */
    @Override public IDialogHandler getDialogs() { return new XenoDialogHandler(); }

    /** The world store's clone library, tabs 1-9. */
    @Override public ICloneHandler getClones() { return new XenoCloneHandler(); }

    @Override
    public IPlayerMail createMail(String sender, String subject) {
        return new XenoPlayerMail(sender, subject);
    }

    /**
     * A detached copy of a player's saved data: the live player's when online, otherwise their file
     * in the world's player data folder. Null for a player this world has never seen.
     */
    @Override
    public INbt getRawPlayerData(String uuid) {
        java.util.UUID id;
        try {
            id = java.util.UUID.fromString(uuid == null ? "" : uuid.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("XenoAPI getRawPlayerData: not a UUID: " + uuid);
        }
        MinecraftServer server = server("getRawPlayerData");
        var online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            XenoApiAdapters.requireServerThread(online.level());
            CompoundTag tag = new CompoundTag();
            online.saveWithoutId(tag);
            return XenoApiAdapters.wrap(tag);
        }
        java.nio.file.Path file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat");
        if (!java.nio.file.Files.isRegularFile(file)) return null;
        try {
            return XenoApiAdapters.wrap(net.minecraft.nbt.NbtIo.readCompressed(file,
                    net.minecraft.nbt.NbtAccounter.create(16L * 1024L * 1024L)));
        } catch (java.io.IOException e) {
            throw new CustomNPCsException(e, "Could not read player data for %s", id);
        }
    }

    /** The world's Xeno NPC store folder ({@code <world>/XenoNpcs}), where scripts and content live. */
    @Override
    public File getGlobalDir() {
        var store = net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores.get();
        if (store != null) return store.root().toFile();
        return server("getGlobalDir").getWorldPath(LevelResource.ROOT)
                .resolve(net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore.ROOT_FOLDER).toFile();
    }

    /** Whether a boolean NeoForge permission node of that name is registered. */
    @Override
    public boolean hasPermissionNode(String permission) {
        return XenoPermissions.node(permission) != null;
    }

    // ------------------------------------------------------------------ unsupported

    @Override public IRecipeHandler getRecipes() { throw XenoApiAdapters.unsupported("NpcAPI.getRecipes (there is no carpentry bench natively)"); }
    @Override public void registerScriptEvent(Class c) { throw XenoApiAdapters.unsupported("NpcAPI.registerScriptEvent (forge scripts use a fixed hook list)"); }
    @Override public String getRandomName(int dictionary, int gender) { throw XenoApiAdapters.unsupported("NpcAPI.getRandomName (CustomNPCs' name dictionaries are not bundled)"); }

    @Override
    public ICustomGui createCustomGui(String name, int width, int height, boolean pauseGame, IPlayer player) {
        throw XenoApiAdapters.unsupported("NpcAPI.createCustomGui (custom GUIs are not implemented natively)");
    }
}
