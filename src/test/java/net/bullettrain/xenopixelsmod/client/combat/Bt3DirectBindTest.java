package net.bullettrain.xenopixelsmod.client.combat;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the parts of the route table that do not read the config.
 *
 * <p>{@code enabled()} and {@code set(...)} deliberately go untested here: they reach
 * {@code XenoClientConfig}, whose static initialiser resolves the Forge config directory, which
 * does not exist outside a running game. The enum's own constants do not touch it until one of
 * those methods is called, so everything below stays on this side of that line.
 */
class Bt3DirectBindTest {

    @Test
    void everyRouteHasItsOwnId() {
        // /xenobind builds one literal per id, so a duplicate would silently shadow a route and
        // leave it with no way to be switched back on.
        Set<String> ids = new HashSet<>();
        for (Bt3DirectBind route : Bt3DirectBind.values()) {
            assertTrue(ids.add(route.id()), "duplicate id: " + route.id());
        }
        assertEquals(Bt3DirectBind.values().length, ids.size());
    }

    @Test
    void everyRouteHasItsOwnLabel() {
        Set<String> labels = new HashSet<>();
        for (Bt3DirectBind route : Bt3DirectBind.values()) {
            assertTrue(labels.add(route.label()), "duplicate label: " + route.label());
        }
    }

    @Test
    void idsAreCommandSafe() {
        // Brigadier literals cannot contain spaces or upper case; a route whose id did would fail
        // at registration, taking every client command in the mod down with it.
        for (Bt3DirectBind route : Bt3DirectBind.values()) {
            String id = route.id();
            assertNotNull(id);
            assertTrue(id.matches("[a-z0-9_]+"), "id is not command-safe: " + id);
        }
    }

    @Test
    void byIdFindsEveryRoute() {
        for (Bt3DirectBind route : Bt3DirectBind.values()) {
            assertSame(route, Bt3DirectBind.byId(route.id()));
        }
    }

    @Test
    void byIdIgnoresCaseAndRejectsJunk() {
        assertSame(Bt3DirectBind.HAKAI, Bt3DirectBind.byId("HAKAI"));
        assertSame(Bt3DirectBind.HAKAI, Bt3DirectBind.byId("Hakai"));
        assertNull(Bt3DirectBind.byId("not_a_route"));
        assertNull(Bt3DirectBind.byId(null));
    }
}
