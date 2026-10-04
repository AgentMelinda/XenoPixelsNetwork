package net.bullettrain.xenopixelsmod.npc;

import java.util.Collection;
import java.util.Locale;

/** Selects an attack clip from names actually baked for one GeckoLib model. */
final class NpcAttackClipSelector {
    private NpcAttackClipSelector() {}

    static String select(Collection<String> names, String configured) {
        if (names == null || names.isEmpty()) return null;
        String wanted = configured == null ? "" : configured.trim();
        if (!wanted.isEmpty() && names.contains(wanted)) return wanted;
        if (names.contains("attack")) return "attack";
        if (names.contains("attack1_1")) return "attack1_1";
        return names.stream().filter(name -> name != null
                        && name.toLowerCase(Locale.ROOT).contains("attack"))
                .sorted().findFirst().orElse(null);
    }
}
