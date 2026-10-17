package net.bullettrain.xenopixelsmod.combat.v3;

/** Server-owned combo reward settings. All times are server ticks. */
public record RandomRushSettings(boolean enabled, boolean meterEnabled, boolean deadlineEnabled,
                                 int windowTicks, int fillPerPress, boolean freezeDuringAttack,
                                 boolean freezeAfterAttack, int postFreezeTicks) {
    public RandomRushSettings() { this(true,true,true,80,125,true,true,80); }
    public RandomRushSettings {
        windowTicks = Math.clamp(windowTicks, 1, 1200);
        fillPerPress = Math.clamp(fillPerPress, 1, 1000);
        postFreezeTicks = Math.clamp(postFreezeTicks, 0, 1200);
    }
    public RandomRushSettings with(String key, Object value) {
        return new RandomRushSettings(key.equals("enabled")?(boolean)value:enabled,
            key.equals("meterEnabled")?(boolean)value:meterEnabled,
            key.equals("deadlineEnabled")?(boolean)value:deadlineEnabled,
            key.equals("windowTicks")?(int)value:windowTicks,
            key.equals("fillPerPress")?(int)value:fillPerPress,
            key.equals("freezeDuringAttack")?(boolean)value:freezeDuringAttack,
            key.equals("freezeAfterAttack")?(boolean)value:freezeAfterAttack,
            key.equals("postFreezeTicks")?(int)value:postFreezeTicks);
    }
}
