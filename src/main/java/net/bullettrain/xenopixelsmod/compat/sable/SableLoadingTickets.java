package net.bullettrain.xenopixelsmod.compat.sable;

import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicket;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;

/** Public ticket accessors verified in Sable 2.0.3 (getters) and 2.0.5 (record components). */
public final class SableLoadingTickets {
    private static final MethodHandle TYPE = accessor("type", "getType", SubLevelLoadingTicketType.class);
    private static final MethodHandle KEY = accessor("key", "getKey", Object.class);
    private static final MethodHandle SUB_LEVEL_ID = accessor("subLevelId", "getSubLevelId", UUID.class);

    private SableLoadingTickets() {}

    @SuppressWarnings("unchecked")
    public static <T> SubLevelLoadingTicketType<T> type(SubLevelLoadingTicket<T> ticket) {
        return (SubLevelLoadingTicketType<T>) read(TYPE, ticket);
    }

    @SuppressWarnings("unchecked")
    public static <T> T key(SubLevelLoadingTicket<T> ticket) {
        return (T) read(KEY, ticket);
    }

    public static UUID subLevelId(SubLevelLoadingTicket<?> ticket) {
        return (UUID) read(SUB_LEVEL_ID, ticket);
    }

    private static MethodHandle accessor(String recordName, String getterName, Class<?> result) {
        var signature = MethodType.methodType(result);
        var lookup = MethodHandles.publicLookup();
        try {
            MethodHandle handle;
            try {
                handle = lookup.findVirtual(SubLevelLoadingTicket.class, recordName, signature);
            } catch (NoSuchMethodException legacy) {
                handle = lookup.findVirtual(SubLevelLoadingTicket.class, getterName, signature);
            }
            return handle.asType(MethodType.methodType(Object.class, SubLevelLoadingTicket.class));
        } catch (NoSuchMethodException | IllegalAccessException incompatible) {
            throw new IllegalStateException("Unsupported Sable loading-ticket accessor: "
                    + recordName + " / " + getterName, incompatible);
        }
    }

    private static Object read(MethodHandle accessor, SubLevelLoadingTicket<?> ticket) {
        try {
            return accessor.invokeExact(ticket);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot read Sable loading ticket", failure);
        }
    }
}
