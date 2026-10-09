package net.bullettrain.xenopixelsmod.combat.v2.combo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The authored combo routes: which node an input starts, and which node follows which.
 *
 * <p>Immutable and Minecraft-free. Built by {@link ComboGraphParser}; a graph that refers to a
 * node it does not contain cannot be constructed, so the live machine never has to handle a
 * dangling id.
 */
public final class ComboGraph {

    private final Map<String, ComboNode> nodes;
    private final Map<ComboInput, String> starts;

    public ComboGraph(Map<ComboInput, String> starts, List<ComboNode> nodes) {
        Map<String, ComboNode> byId = new LinkedHashMap<>();
        for (ComboNode node : nodes) {
            if (byId.put(node.id(), node) != null) {
                throw new IllegalArgumentException("duplicate combo node " + node.id());
            }
        }
        List<String> problems = new ArrayList<>();
        if (starts == null || starts.isEmpty()) problems.add("no start nodes");
        Map<ComboInput, String> startCopy = new EnumMap<>(ComboInput.class);
        if (starts != null) {
            for (Map.Entry<ComboInput, String> e : starts.entrySet()) {
                if (!byId.containsKey(e.getValue())) {
                    problems.add("start " + e.getKey() + " -> missing node " + e.getValue());
                }
                startCopy.put(e.getKey(), e.getValue());
            }
        }
        for (ComboNode node : byId.values()) {
            for (Map.Entry<ComboInput, String> e : node.next().entrySet()) {
                if (!byId.containsKey(e.getValue())) {
                    problems.add(node.id() + " " + e.getKey() + " -> missing node " + e.getValue());
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("invalid combo graph: " + String.join("; ", problems));
        }
        this.nodes = Collections.unmodifiableMap(byId);
        this.starts = Collections.unmodifiableMap(startCopy);
    }

    public ComboNode node(String id) {
        return id == null ? null : nodes.get(id);
    }

    /** The node a fresh string opens with for {@code input}, or null when it opens nothing. */
    public ComboNode start(ComboInput input) {
        return input == null ? null : node(starts.get(input));
    }

    /** The node that follows {@code from} on {@code input}, or null when the string ends there. */
    public ComboNode next(ComboNode from, ComboInput input) {
        if (from == null || input == null) return null;
        return node(from.next().get(input));
    }

    public int size() {
        return nodes.size();
    }

    public Iterable<ComboNode> nodes() {
        return nodes.values();
    }
}
