package xenoapi.npcs.api;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.bus.api.IEventBus;
import xenoapi.npcs.api.block.IBlock;
import xenoapi.npcs.api.entity.ICustomNpc;
import xenoapi.npcs.api.entity.IEntity;
import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.entity.data.IPlayerMail;
import xenoapi.npcs.api.gui.ICustomGui;
import xenoapi.npcs.api.handler.*;
import xenoapi.npcs.api.item.IItemStack;

import java.io.File;

/**
 * Note this API should only be used Server side not on the client
 *
 */
public abstract class NpcAPI {	
	private static volatile NpcAPI instance = null;
		
	/**
	 * Doesnt spawn the npc in the world
	 */
	public abstract ICustomNpc createNPC(Level world);
	
	/**
	 * Creates and spawns an npc
	 */
	public abstract ICustomNpc spawnNPC(Level level, int x, int y, int z);


	public abstract IEntity getIEntity(Entity entity);

	public abstract IBlock getIBlock(Level level, BlockPos pos);

	public abstract IContainer getIContainer(Container container);

	public abstract IContainer getIContainer(AbstractContainerMenu container);
	
	public abstract IItemStack getIItemStack(ItemStack itemstack);
	
	public abstract IWorld getIWorld(ServerLevel world);

	/**
	 * @param dimension 'minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end'
	 */
	public abstract IWorld getIWorld(String dimension);

	public abstract IWorld getIWorld(DimensionType dimension);

	public abstract IWorld[] getIWorlds();

	public abstract INbt getINbt(CompoundTag compound);

	public abstract IPos getIPos(double x, double y, double z);
	
	public abstract IFactionHandler getFactions();
	
	public abstract IRecipeHandler getRecipes();
	
	public abstract IQuestHandler getQuests();
	
	public abstract IDialogHandler getDialogs();
	
	public abstract ICloneHandler getClones();

	public abstract IDamageSource getIDamageSource(DamageSource damagesource);

	public abstract INbt stringToNbt(String str);
	
	public abstract IPlayerMail createMail(String sender, String subject);

	/**
	 * @author Ryan
	 */
	public abstract ICustomGui createCustomGui(String name, int width, int height, boolean pauseGame, IPlayer player);
	
	/**
	 * Get player data even if they are offline
	 * @param uuid
	 * @return
	 */
	public abstract INbt getRawPlayerData(String uuid);
	
	/**
	 * Used by modders
	 * @return The event bus where you register CustomNPCEvents
	 */
	public abstract IEventBus events();

	public abstract void registerScriptEvent(Class c);
	
	/**
	 * Use to register your own /noppes subcommand
	 */
	//public abstract void registerCommand(CommandNoppesBase command);

	/**
	 * @return Returns the .minecraft/customnpcs folder or [yourserverfolder]/customnpcs
	 */
	public abstract File getGlobalDir();

	/**
	 * @return Returns the .minecraft/saves/[yourworld]/customnpcs folder or [yourserverfolder]/[yourworld]/customnpcs
	 */
	public abstract File getLevelDir();
			
	/**
	 * @return true once an implementation has been registered with {@link #setInstance(NpcAPI)}
	 */
	public static boolean IsAvailable(){
		return instance != null;
	}

	/**
	 * @return The registered implementation, or null if none has been registered yet
	 */
	public static NpcAPI Instance(){
		return instance;
	}

	/**
	 * Registers the implementation of this API. Can only be called once.
	 * @param impl The implementation
	 */
	public static synchronized void setInstance(NpcAPI impl){
		if(impl == null)
			throw new IllegalArgumentException("NpcAPI implementation cannot be null");
		if(instance != null)
			throw new IllegalStateException("NpcAPI implementation already registered: " + instance.getClass().getName());
		instance = impl;
	}

	public abstract boolean hasPermissionNode(String permission);

	/**
	 * @param world The world in which the command is executed
	 * @param command The Command to execute
	 * @return
	 */
	public abstract String executeCommand(IWorld world, String command);

	/**
	 * @param world The world in which the command is executed
	 * @param command The Command to execute
	 * @return
	 */
	public abstract String executeCommandSilent(IWorld world, String command);
	
	/**
	 * @author Nikedemos
	 * @param dictionary 0:roman, 1:japanese, 2:slavic, 3:welsh, 4:saami, 5:old-norse, 6:ancient-greek, 7:aztec, 8:classic-cnpcs, 9:spanish
	 * @param gender 0:random, 1:male, 2:female
	 * @return Returns a randomly generated name
	 */
	public abstract String getRandomName(int dictionary, int gender);
}
