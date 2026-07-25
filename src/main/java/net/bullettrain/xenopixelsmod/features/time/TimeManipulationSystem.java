package net.bullettrain.xenopixelsmod.features.time;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature 4: Time Manipulation Mechanics
 *
 * Data-layer time / weather control keyed by player or area id (DMZ-friendly).
 */
public final class TimeManipulationSystem {

    private TimeManipulationSystem() {
    }

    public enum TimeMode {
        NORMAL(1.0f),
        SLOW_MOTION(0.5f),
        TIME_FREEZE(0.0f),
        SPEED_BOOST(2.0f);

        private final float speedMultiplier;

        TimeMode(float multiplier) {
            this.speedMultiplier = multiplier;
        }

        public float getSpeedMultiplier() {
            return speedMultiplier;
        }
    }

    public enum WeatherCondition {
        CLEAR("Clear skies, optimal visibility"),
        CLOUDY("Partly cloudy, neutral conditions"),
        RAIN("Rainy weather - slippery surfaces"),
        STORM("Thunderstorm - lightning hazards"),
        FOG("Foggy conditions - reduced visibility"),
        SNOW("Snowing - obscured vision");

        private final String description;

        WeatherCondition(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum TimeEffectType {
        SLOW_MOTION,
        SPEED_BOOST,
        TIME_FREEZE,
        WEATHER_CHANGE,
        VISUAL_EFFECT,
        TIME_SPEED
    }

    public static final class WeatherEffect {
        private final WeatherCondition condition;
        private final String label;

        public WeatherEffect(WeatherCondition condition, String label) {
            this.condition = condition;
            this.label = label != null ? label : condition.name();
        }

        public WeatherCondition getCondition() {
            return condition;
        }

        public String getLabel() {
            return label;
        }
    }

    public static final class TimeAbility {
        public enum Type {
            TIME_SPEED,
            TIME_FREEZE,
            WEATHER_CHANGE,
            VISUAL_EFFECT
        }

        private final String abilityId;
        private final String displayName;
        private final Type type;
        private final int durationSeconds;
        private final float multiplier;
        private final WeatherCondition weatherTarget;

        private TimeAbility(
                String abilityId,
                String displayName,
                Type type,
                int durationSeconds,
                float multiplier,
                WeatherCondition weatherTarget) {
            this.abilityId = abilityId;
            this.displayName = displayName;
            this.type = type;
            this.durationSeconds = durationSeconds;
            this.multiplier = multiplier;
            this.weatherTarget = weatherTarget;
        }

        public static TimeAbility createSpeedBoost(String id, String name, int duration) {
            return new TimeAbility(id, name, Type.TIME_SPEED, duration, 2.0f, null);
        }

        public static TimeAbility createSlowMotion(String id, String name, int duration) {
            return new TimeAbility(id, name, Type.TIME_SPEED, duration, 0.5f, null);
        }

        public static TimeAbility createTimeFreeze(String id, String name, int duration) {
            return new TimeAbility(id, name, Type.TIME_FREEZE, duration, 0.0f, null);
        }

        public static TimeAbility createWeatherChange(String id, String name, WeatherCondition weather) {
            return new TimeAbility(id, name, Type.WEATHER_CHANGE, 120, 1.0f, weather);
        }

        public String getAbilityId() {
            return abilityId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Type getType() {
            return type;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }

        public float getMultiplier() {
            return multiplier;
        }

        public WeatherCondition getWeatherTarget() {
            return weatherTarget;
        }
    }

    public static final class TimeEffect {
        private final String id;
        private final TimeEffectType type;
        private final float multiplier;
        private final int durationSeconds;

        public TimeEffect(TimeEffectType type, float multiplier, int durationSeconds) {
            this.id = "effect_" + System.nanoTime();
            this.type = type;
            this.multiplier = multiplier;
            this.durationSeconds = durationSeconds;
        }

        public String getId() {
            return id;
        }

        public TimeEffectType getType() {
            return type;
        }

        public float getMultiplier() {
            return multiplier;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }
    }

    public static final class TimeState {
        private final String targetId;
        private TimeMode currentMode;
        private WeatherCondition currentWeather;
        private final List<TimeEffect> activeEffects = new ArrayList<>();

        public TimeState(String targetId, TimeMode mode) {
            this.targetId = targetId;
            this.currentMode = mode != null ? mode : TimeMode.NORMAL;
            this.currentWeather = WeatherCondition.CLEAR;
            XenoPixelsMod.LOGGER.debug(
                    "Time manipulation: Created state for '{}' with mode {}", targetId, this.currentMode);
        }

        public String getTargetId() {
            return targetId;
        }

        public void applyEffect(TimeEffectType type, float multiplier, int durationSeconds) {
            removeEffectByType(type);
            activeEffects.add(new TimeEffect(type, multiplier, durationSeconds));
            XenoPixelsMod.LOGGER.debug(
                    "Time manipulation: Applied effect {} to '{}' for {}s", type, targetId, durationSeconds);
        }

        public void removeEffect(String effectId) {
            activeEffects.removeIf(e -> e.getId().equals(effectId));
        }

        public void removeEffectByType(TimeEffectType type) {
            activeEffects.removeIf(e -> e.getType() == type);
        }

        public TimeMode getCurrentMode() {
            return currentMode;
        }

        public void setCurrentMode(TimeMode mode) {
            this.currentMode = mode != null ? mode : TimeMode.NORMAL;
        }

        public WeatherCondition getCurrentWeather() {
            return currentWeather;
        }

        /** Alias used by isActive checks. */
        public WeatherCondition getWeatherCondition() {
            return currentWeather;
        }

        public void setCurrentWeather(WeatherCondition weather) {
            this.currentWeather = weather != null ? weather : WeatherCondition.CLEAR;
        }

        public List<TimeEffect> getActiveEffects() {
            return new ArrayList<>(activeEffects);
        }

        void clearEffects() {
            activeEffects.clear();
        }
    }

    private static final Map<String, TimeState> TIME_STATES = new ConcurrentHashMap<>();

    public static void applyEffect(String targetId, TimeAbility ability) {
        if (targetId == null || ability == null) {
            return;
        }
        TimeState state = TIME_STATES.computeIfAbsent(targetId, k -> new TimeState(k, TimeMode.NORMAL));

        switch (ability.getType()) {
            case TIME_SPEED -> {
                TimeEffectType effectType = ability.getMultiplier() >= 1.0f
                        ? TimeEffectType.SPEED_BOOST
                        : TimeEffectType.SLOW_MOTION;
                state.applyEffect(effectType, ability.getMultiplier(), ability.getDurationSeconds());
                if (ability.getMultiplier() >= 2.0f) {
                    state.setCurrentMode(TimeMode.SPEED_BOOST);
                } else if (ability.getMultiplier() <= 0.5f) {
                    state.setCurrentMode(TimeMode.SLOW_MOTION);
                }
            }
            case TIME_FREEZE -> {
                state.applyEffect(TimeEffectType.TIME_FREEZE, ability.getMultiplier(), ability.getDurationSeconds());
                state.setCurrentMode(TimeMode.TIME_FREEZE);
            }
            case WEATHER_CHANGE -> {
                WeatherCondition weather = ability.getWeatherTarget() != null
                        ? ability.getWeatherTarget()
                        : WeatherCondition.CLEAR;
                state.setCurrentWeather(weather);
                state.applyEffect(TimeEffectType.WEATHER_CHANGE, 1.0f, ability.getDurationSeconds());
            }
            case VISUAL_EFFECT -> state.applyEffect(
                    TimeEffectType.VISUAL_EFFECT, ability.getMultiplier(), ability.getDurationSeconds());
            default -> XenoPixelsMod.LOGGER.warn("Unknown time effect type: {}", ability.getType());
        }

        XenoPixelsMod.LOGGER.debug(
                "Time manipulation: Applied ability '{}' to target '{}'",
                ability.getDisplayName(), targetId);
    }

    public static void removeEffect(String targetId, String effectId) {
        TimeState state = TIME_STATES.get(targetId);
        if (state != null) {
            state.removeEffect(effectId);
        }
    }

    public static void removeAllEffects(String targetId) {
        TimeState state = TIME_STATES.get(targetId);
        if (state != null) {
            state.clearEffects();
            state.setCurrentMode(TimeMode.NORMAL);
            state.setCurrentWeather(WeatherCondition.CLEAR);
        }
    }

    public static TimeState getState(String targetId) {
        return TIME_STATES.get(targetId);
    }

    public static boolean isActive(String targetId) {
        TimeState state = TIME_STATES.get(targetId);
        return state != null
                && (state.getCurrentMode() != TimeMode.NORMAL
                || state.getWeatherCondition() != WeatherCondition.CLEAR);
    }

    public static List<TimeState> getAllActiveStates() {
        return new ArrayList<>(TIME_STATES.values());
    }

    public static void clearAllStates() {
        TIME_STATES.clear();
        XenoPixelsMod.LOGGER.info("Time manipulation: All states cleared");
    }
}
