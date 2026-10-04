package net.bullettrain.xenopixelsmod.npc;

import java.util.Locale;

public enum XenoNpcRole {
    HUMANOID("humanoid", false),
    CREATURE("creature", true),
    TRADER("trader", false),
    GUARD("guard", false),
    COMPANION("companion", false),
    QUEST("quest", false),
    /** Sends a player to somewhere on its destination list. My NPCs' Transporter role. */
    TRANSPORTER("transporter", false),
    /** Keeps a per-bank vault with physical Zeni cash and optional MMO Econ wallet exchange. */
    BANK("bank", false);

    private final String id;
    private final boolean creature;

    XenoNpcRole(String id, boolean creature) {
        this.id = id;
        this.creature = creature;
    }

    public String id() { return id; }
    public boolean creature() { return creature; }
    public String label() {
        return switch (this) {
            case HUMANOID -> "Humanoid";
            case CREATURE -> "Creature";
            case TRADER -> "Trader";
            case GUARD -> "Guard";
            case COMPANION -> "Companion";
            case QUEST -> "Quest";
            case TRANSPORTER -> "Transport";
            case BANK -> "Bank";
        };
    }

    public static XenoNpcRole byId(String raw) {
        if (raw != null) {
            String id = raw.trim().toLowerCase(Locale.ROOT);
            for (XenoNpcRole role : values()) if (role.id.equals(id)) return role;
        }
        return HUMANOID;
    }

    public XenoNpcRole next() {
        XenoNpcRole[] roles = values();
        return roles[(ordinal() + 1) % roles.length];
    }
}
