package net.bullettrain.xenopixelsmod.client;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;

/**
 * Client-side mirror of server combat / feature flags (synced on login + reload).
 * Not {@code @OnlyIn}: common network packets reference this class.
 */
public final class XenoServerClientState {
    private static XenoServerConfig.Data data = new XenoServerConfig.Data();

    private XenoServerClientState() {}

    public static void apply(XenoServerConfig.Data incoming) {
        if (incoming == null) return;
        data = incoming;
        DmzHudClientState.setDmzHudEnabled(incoming.dmzHudEnabled);
    }

    public static XenoServerConfig.Data get() {
        return data;
    }

    public static boolean combat() {
        return data.bt3CombatEnabled;
    }

    public static boolean combo() {
        return data.bt3CombatEnabled && data.bt3ComboEnabled;
    }

    public static boolean vanish() {
        return data.bt3CombatEnabled && data.bt3VanishEnabled;
    }

    public static boolean chase() {
        return data.bt3CombatEnabled && data.bt3ChaseDashEnabled;
    }

    public static boolean backstep() {
        return data.bt3CombatEnabled && data.bt3BackstepEnabled;
    }

    public static boolean finisher() {
        return data.bt3CombatEnabled && data.bt3FinisherEnabled;
    }

    public static boolean chargeAttack() {
        return data.bt3CombatEnabled && data.bt3ChargeAttackEnabled;
    }

    public static boolean dragonDash() {
        return data.bt3CombatEnabled && data.bt3DragonDashEnabled;
    }

    public static boolean guard() {
        return data.bt3CombatEnabled && data.bt3GuardEnabled;
    }

    public static boolean superCounter() {
        return data.bt3CombatEnabled && data.bt3SuperCounterEnabled;
    }

    public static boolean kiBlastCancel() {
        return data.bt3CombatEnabled && data.bt3KiBlastCancelEnabled;
    }

    public static boolean zBurst() {
        return data.bt3CombatEnabled && data.bt3ZBurstEnabled;
    }

    public static boolean lockCycle() {
        return data.bt3CombatEnabled && data.bt3LockCycleEnabled;
    }

    public static boolean comboPunchesOnly() {
        return data.bt3CombatEnabled && data.bt3ComboEnabled && data.bt3ComboPunchesOnly;
    }
}
