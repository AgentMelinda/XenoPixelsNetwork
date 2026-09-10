package net.bullettrain.xenopixelsmod.compat.paradigm;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Optional reflection bridge for Paradigm 2.3/2.5 persistent and temporary holograms. */
public final class ParadigmHologramBridge {
    private ParadigmHologramBridge() {
    }

    public static Result clearAll() throws ReflectiveOperationException {
        Object service = service();
        List<String> persistent = new ArrayList<>(((Map<?, ?>) invoke(service, "definitions")).keySet()
                .stream().map(String::valueOf).toList());
        List<String> temporary = temporaryIds(service, null, null, -1.0);
        persistent.forEach(id -> invokeUnchecked(service, "delete", id));
        temporary.forEach(id -> invokeUnchecked(service, "removeTemporary", id));
        return new Result(persistent.size(), temporary.size());
    }

    public static Result clearRadius(ServerLevel level, Vec3 center, double radius)
            throws ReflectiveOperationException {
        Object service = service();
        String dimension = level.dimension().location().toString();
        double radiusSquared = radius * radius;
        Map<?, ?> definitions = (Map<?, ?>) invoke(service, "definitions");
        List<String> persistent = new ArrayList<>();
        for (Map.Entry<?, ?> entry : definitions.entrySet()) {
            if (within(entry.getValue(), dimension, center, radiusSquared)) {
                persistent.add(String.valueOf(entry.getKey()));
            }
        }
        List<String> temporary = temporaryIds(service, dimension, center, radiusSquared);
        persistent.forEach(id -> invokeUnchecked(service, "delete", id));
        temporary.forEach(id -> invokeUnchecked(service, "removeTemporary", id));
        return new Result(persistent.size(), temporary.size());
    }

    public static boolean available() {
        return ModList.get().isLoaded("paradigm");
    }

    private static Object service() throws ReflectiveOperationException {
        if (!available()) {
            throw new IllegalStateException("Paradigm is not loaded");
        }
        Class<?> paradigm = Class.forName("eu.avalanche7.paradigm.Paradigm");
        Object services = paradigm.getMethod("getServices").invoke(null);
        if (services == null) {
            throw new IllegalStateException("Paradigm services are not ready");
        }
        Object service = services.getClass().getMethod("getHologramService").invoke(services);
        if (service == null) {
            throw new IllegalStateException("Paradigm holograms are disabled or not ready");
        }
        return service;
    }

    private static List<String> temporaryIds(Object service, String dimension, Vec3 center,
                                              double radiusSquared) throws ReflectiveOperationException {
        List<String> ids = new ArrayList<>();
        for (Object temporary : (List<?>) invoke(service, "temporaryHolograms")) {
            Object definition = field(temporary, "definition");
            if (dimension == null || within(definition, dimension, center, radiusSquared)) {
                ids.add(String.valueOf(field(temporary, "id")));
            }
        }
        return ids;
    }

    private static boolean within(Object definition, String dimension, Vec3 center, double radiusSquared)
            throws ReflectiveOperationException {
        if (definition == null || !dimension.equals(String.valueOf(field(definition, "dimension")))) {
            return false;
        }
        double x = ((Number) field(definition, "x")).doubleValue();
        double y = ((Number) field(definition, "y")).doubleValue();
        double z = ((Number) field(definition, "z")).doubleValue();
        return center.distanceToSqr(x, y, z) <= radiusSquared;
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getField(name);
        return field.get(target);
    }

    private static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), name, args.length);
        return method.invoke(target, args);
    }

    private static void invokeUnchecked(Object target, String name, String id) {
        try {
            invoke(target, name, id);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            XenoPixelsMod.LOGGER.error("Failed to remove Paradigm hologram {}", id, exception);
        }
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    public record Result(int persistent, int temporary) {
        public int total() {
            return persistent + temporary;
        }
    }
}
