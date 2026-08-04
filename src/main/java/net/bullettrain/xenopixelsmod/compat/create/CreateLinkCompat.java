package net.bullettrain.xenopixelsmod.compat.create;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Soft integration with Create mod's Link Controller and Redstone Links.
 * Provides VS2 ship thruster control through Create's contraption linking system.
 * No hard dependency - uses reflection for compatibility.
 */
public class CreateLinkCompat {
    
    private static Boolean createLoaded = null;
    private static final WeakHashMap<BlockPos, Object> linkControllerCache = new WeakHashMap<>();
    
    private CreateLinkCompat() {}
    
    public static boolean isCreateLoaded() {
        if (createLoaded == null) {
            createLoaded = ModList.get().isLoaded("create");
            if (createLoaded) {
                XenoPixelsMod.LOGGER.info("Create detected - Link/Redstone integration active");
            }
        }
        return createLoaded;
    }
    
    /**
     * Check if a block is a Create Link Controller
     */
    public static boolean isLinkController(BlockEntity be) {
        if (be == null || !isCreateLoaded()) return false;
        String cn = be.getClass().getName();
        return cn.contains("LinkControllerBlockEntity") && cn.contains("create");
    }
    
    /**
     * Check if a block is a Create Redstone Link
     */
    public static boolean isRedstoneLink(BlockEntity be) {
        if (be == null || !isCreateLoaded()) return false;
        String cn = be.getClass().getName();
        return cn.contains("RedstoneLinkBlockEntity") && cn.contains("create");
    }
    
    /**
     * Try to connect VS2 thrusters to Create contraption via Link Controller
     * @param level World level
     * @param controllerPos Link Controller position
     * @param thrusterPos VS2 Thruster position
     * @return true if connection successful
     */
    public static boolean connectThrusterToContraption(Level level, BlockPos controllerPos, BlockPos thrusterPos) {
        if (!isCreateLoaded() || level == null) return false;
        
        BlockEntity controllerBE = level.getBlockEntity(controllerPos);
        if (!isLinkController(controllerBE)) return false;
        
        try {
            // Reflection call to Create's LinkController.addSubcontraption method
            Method addMethod = controllerBE.getClass().getMethod("addSubContraption", BlockPos.class);
            Object result = addMethod.invoke(controllerBE, thrusterPos);
            
            if (result instanceof Boolean && (Boolean) result) {
                linkControllerCache.put(controllerPos.immutable(), controllerBE);
                XenoPixelsMod.LOGGER.debug("Connected VS2 thruster at {} to Link Controller at {}", thrusterPos, controllerPos);
                return true;
            }
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.warn("Failed to connect thruster to Create contraption: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Disconnect thruster from contraption
     */
    public static boolean disconnectThruster(Level level, BlockPos controllerPos, BlockPos thrusterPos) {
        if (!isCreateLoaded() || level == null) return false;
        
        BlockEntity controllerBE = linkControllerCache.get(controllerPos);
        if (controllerBE == null) {
            controllerBE = level.getBlockEntity(controllerPos);
        }
        
        if (!isLinkController(controllerBE)) return false;
        
        try {
            Method removeMethod = controllerBE.getClass().getMethod("removeSubContraption", BlockPos.class);
            Object result = removeMethod.invoke(controllerBE, thrusterPos);
            
            if (result instanceof Boolean && (Boolean) result) {
                linkControllerCache.remove(controllerPos);
                XenoPixelsMod.LOGGER.debug("Disconnected VS2 thruster at {} from Link Controller", thrusterPos);
                return true;
            }
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.warn("Failed to disconnect thruster: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Set Redstone Link frequency for VS2 control
     * @param level World level
     * @param linkPos Redstone Link position
     * @param frequency Frequency array [major, minor]
     * @return true if successful
     */
    public static boolean setRedstoneLinkFrequency(Level level, BlockPos linkPos, int[] frequency) {
        if (!isCreateLoaded() || level == null || frequency.length != 2) return false;
        
        BlockEntity linkBE = level.getBlockEntity(linkPos);
        if (!isRedstoneLink(linkBE)) return false;
        
        try {
            Method setFreqMethod = linkBE.getClass().getMethod("setFrequency", int[].class);
            setFreqMethod.invoke(linkBE, (Object) frequency);
            XenoPixelsMod.LOGGER.debug("Set Redstone Link at {} to frequency [{}, {}]", linkPos, frequency[0], frequency[1]);
            return true;
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.warn("Failed to set Redstone Link frequency: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Get Redstone Link signal strength
     */
    public static int getRedstoneLinkSignal(Level level, BlockPos linkPos) {
        if (!isCreateLoaded() || level == null) return 0;
        
        BlockEntity linkBE = level.getBlockEntity(linkPos);
        if (!isRedstoneLink(linkBE)) return 0;
        
        try {
            Method getSignalMethod = linkBE.getClass().getMethod("getSignalStrength");
            Object result = getSignalMethod.invoke(linkBE);
            if (result instanceof Integer) {
                return (Integer) result;
            }
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.warn("Failed to get Redstone Link signal: {}", e.getMessage());
        }
        return 0;
    }
    
    /**
     * Clear cache on world unload
     */
    public static void clearCache() {
        linkControllerCache.clear();
    }
}
