package net.bullettrain.xenopixelsmod.compat.computercraft;

import dan200.computercraft.api.ComputerCraftAPI;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.create.elevator.ElevatorMethods;
import net.neoforged.fml.ModList;

/**
 * Optional ComputerCraft: Tweaked integration.
 * Loaded reflectively from common setup so missing CC never crashes classloading of core code.
 */
public final class CcCompat {
    private CcCompat() {}

    public static void register() {
        if (!ModList.get().isLoaded("computercraft")) {
            XenoPixelsMod.LOGGER.info("ComputerCraft not present — thruster/VLS CC methods skipped");
            return;
        }
        try {
            ComputerCraftAPI.registerGenericSource(new ShipThrusterPeripheral());
            ComputerCraftAPI.registerGenericSource(new VlsGuidancePeripheral());
            XenoPixelsMod.LOGGER.info("Registered CC:Tweaked generic peripherals (thruster + VLS guidance)");
        } catch (Throwable t) {
            XenoPixelsMod.LOGGER.warn("Failed to register CC peripherals: {}", t.toString());
        }
        registerCreateElevator();
    }

    /**
     * Create elevator methods, in their own try so a Create problem cannot take our own
     * peripherals down with it.
     *
     * <p>Gated on the elevator class actually loading, not only on the {@code create} mod id
     * being present: Create is a fast-moving mod, and a release that moves or removes the
     * elevator API would otherwise turn an optional integration into a startup crash. Touching
     * {@code ElevatorMethods} is what loads Create types, so the check has to happen before the
     * constructor runs — hence the reflective probe rather than a direct reference.
     */
    private static void registerCreateElevator() {
        // Literals, not constants on ElevatorMethods: naming that class here at all is the one
        // thing this method must not do before the probe below has passed.
        if (!ModList.get().isLoaded("create")) {
            XenoPixelsMod.LOGGER.info("Create not present — elevator CC methods skipped");
            return;
        }
        if (ModList.get().isLoaded("createelevatorcc")) {
            // Both register create_elevator methods on the same block entity, and CC refuses
            // duplicate method names on one peripheral. Theirs was first; stand down.
            XenoPixelsMod.LOGGER.info("CC:LiftLink present — deferring elevator CC methods to it");
            return;
        }
        try {
            Class.forName("com.simibubi.create.content.contraptions.elevator.ElevatorContactBlockEntity",
                    false, CcCompat.class.getClassLoader());
        } catch (Throwable t) {
            XenoPixelsMod.LOGGER.warn(
                    "Create is installed but its elevator API is missing — elevator CC methods skipped ({})",
                    t.toString());
            return;
        }
        try {
            doRegisterCreateElevator();
            XenoPixelsMod.LOGGER.info("Registered CC:Tweaked generic peripheral (Create elevator)");
        } catch (Throwable t) {
            XenoPixelsMod.LOGGER.warn("Failed to register Create elevator CC methods: {}", t.toString());
        }
    }

    /**
     * The only method that names {@link ElevatorMethods}, and therefore the only one whose
     * verification can pull Create's classes in. Kept separate so the guards above have all
     * returned before the JVM ever has a reason to resolve it.
     */
    private static void doRegisterCreateElevator() {
        ComputerCraftAPI.registerGenericSource(new ElevatorMethods());
    }
}
