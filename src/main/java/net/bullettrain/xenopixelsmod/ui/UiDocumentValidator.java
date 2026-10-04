package net.bullettrain.xenopixelsmod.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Load-time and export-time checks. Failures are strings, not exceptions. */
public final class UiDocumentValidator {
    private UiDocumentValidator() {
    }

    public static List<String> validate(UiDocument document) {
        List<String> errors = new ArrayList<>();
        if (document == null) {
            errors.add("missing document");
            return errors;
        }
        if (document.version != UiDocument.VERSION) {
            errors.add("unsupported version " + document.version + " (need " + UiDocument.VERSION + ")");
        }
        if (document.id == null || document.id.isBlank()) {
            errors.add("document id required");
        }
        if (!"hud".equals(document.kind) && !"screen".equals(document.kind)
                && !"dmz_menu".equals(document.kind)) {
            errors.add("kind must be hud, screen, or dmz_menu");
        }
        if ("dmz_menu".equals(document.kind) && DmzMenuPage.parse(document.page) == null) {
            errors.add("dmz_menu page must be stats, skills, quests, party, options, or custom");
        }
        if (document.canvasW < 64 || document.canvasH < 64) {
            errors.add("canvas must be at least 64x64");
        }
        if (document.root == null) {
            errors.add("root required");
            return errors;
        }
        Set<String> ids = new HashSet<>();
        Set<UiNode> stack = Collections.newSetFromMap(new IdentityHashMap<>());
        walk(document.root, ids, stack, errors);
        return errors;
    }

    private static void walk(UiNode node, Set<String> ids, Set<UiNode> stack, List<String> errors) {
        if (node == null) {
            errors.add("null node");
            return;
        }
        if (!stack.add(node)) {
            errors.add("cyclic parent at " + node.id);
            return;
        }
        if (node.id == null || node.id.isBlank()) {
            errors.add("node id required");
        } else if (!ids.add(node.id)) {
            errors.add("duplicate id " + node.id);
        }
        if (UiNodeType.byName(node.type) == null) {
            errors.add("unknown type " + node.type + " on " + node.id);
        }
        if (UiAnchor.byName(node.anchor) == null) {
            errors.add("unknown anchor " + node.anchor + " on " + node.id);
        }
        if (node.bind != null && !node.bind.isBlank() && !UiBindings.isKnown(node.bind)) {
            errors.add("unknown binding " + node.bind + " on " + node.id);
        }
        if (node.action != null && !node.action.isBlank()
                && !node.action.startsWith("open_document:")
                && !node.action.startsWith("master_menu:")
                && !node.action.startsWith("play_clip:")
                && !node.action.startsWith("dmz_page:")
                && !"close".equals(node.action)) {
            errors.add("unknown action " + node.action + " on " + node.id);
        }
        if (node.children != null) {
            for (UiNode child : node.children) {
                walk(child, ids, stack, errors);
            }
        }
        stack.remove(node);
    }
}
