package net.bullettrain.xenopixelsmod.combat.v3.ki;

import net.bullettrain.xenopixelsmod.fx.ki.KiLook;

/**
 * How one technique's ki looks and flies: its kind, its colours, and its numbers.
 *
 * <p>The kind comes from the technique's archetype; the colours are the ones DragonMineZ gives
 * the familiar attack it stands for (read from {@code PredefinedTechniques} in the pinned 2.1.3
 * jar), so a Kamehameha is the same blue in V3 as it is in DragonMineZ.
 *
 * @param core    the attack's main colour, 0xRRGGBB
 * @param edge    its second colour; only shown where it differs (a Makankosappo's spiral)
 * @param size    scale of the effect: an orb's radius is half a block at 1, a wave's about 0.8
 * @param speed   blocks a tick (a beam's head always moves at {@link V3KiPath#BEAM_SPEED})
 * @param shots   how many projectiles the attack fires
 * @param hits    how many times a beam hits once it has arrived
 * @param damage  the whole attack's damage, as a multiple of a V3 strike
 */
public record V3KiStyle(KiLook.Kind kind, int core, int edge, float size, double speed, int shots, int hits,
                        float damage, int nativeRenderType, String fxTrail, String fxImpact, String fxCharge) {
    public V3KiStyle(KiLook.Kind kind, int core, int edge, float size, double speed, int shots, int hits,
                     float damage, int nativeRenderType) {
        this(kind, core, edge, size, speed, shots, hits, damage, nativeRenderType, null, null, null);
    }

    public V3KiStyle {
        fxTrail = bundledAsset(fxTrail);
        fxImpact = bundledAsset(fxImpact);
        fxCharge = bundledAsset(fxCharge);
    }

    public V3KiStyle(KiLook.Kind kind, int core, int edge, float size, double speed, int shots, int hits, float damage) {
        this(kind, core, edge, size, speed, shots, hits, damage, 0);
    }
    /** Prefer {@link XenoKiProfileCatalog} colours / size / speed when a technique is known. */
    public static V3KiStyle of(net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueDefinition technique) {
        if (technique == null) return of(null, null);
        XenoKiProfile profile = XenoKiProfileCatalog.resolve(technique);
        V3KiStyle base = of(technique.type(), technique.kiTechnique());
        int core = profile.styleCore() != null ? profile.styleCore() & 0xFFFFFF : base.core();
        int edge = profile.styleEdge() != null ? profile.styleEdge() & 0xFFFFFF : base.edge();
        float size = profile.size() != null && Float.isFinite(profile.size())
                ? Math.clamp(profile.size(), 0.2f, 8f) : base.size();
        double speed = profile.speed() != null && Float.isFinite(profile.speed())
                ? Math.clamp(profile.speed(), 0.05f, 8f) : base.speed();
        float damage = profile.damageMultiplier() != null
                ? base.damage() * Math.clamp(profile.damageMultiplier(), 0.1f, 10f) : base.damage();
        return new V3KiStyle(base.kind(), core, edge, size, speed, base.shots(), base.hits(), damage,
                profile.renderType() != null && profile.renderType() >= 0 && profile.renderType() <= 9
                        ? profile.renderType() : base.nativeRenderType(),
                profile.fxTrail(), profile.fxImpact(), profile.fxCharge());
    }

    public static V3KiStyle of(String type, String nativeId) {
        int[] colours = colours(nativeId);
        int core = colours[0];
        int edge = colours[1];
        V3KiStyle style = switch (type == null ? "" : type) {
            case "beam" -> new V3KiStyle(KiLook.Kind.WAVE, core, edge, "final_flash".equals(nativeId) ? 1.4f : 1.0f,
                    0, 1, 5, 3.0f);
            // The spiralled beam is a laser with the second colour wound round it.
            case "laser" -> new V3KiStyle("makkanko".equals(nativeId) ? KiLook.Kind.BEAM : KiLook.Kind.LASER, core, edge,
                    1.0f, 0, 1, 2, 1.8f);
            case "disc" -> new V3KiStyle(KiLook.Kind.DISK, core, edge, 0.8f, 2.2, 1, 1, 2.4f);
            case "volley" -> new V3KiStyle(KiLook.Kind.BARRAGE, core, edge, 0.55f, 2.0, 10, 1, 2.6f);
            case "giant_ball" -> new V3KiStyle(KiLook.Kind.GIANT_BALL, core, edge, 2.4f, 0.9, 1, 1, 4.5f);
            default -> new V3KiStyle(KiLook.Kind.MEDIUM_BALL, core, edge, "big_bang".equals(nativeId) ? 1.6f : 1.0f,
                    1.6, 1, 1, 2.0f);
        };
        return new V3KiStyle(style.kind, core, edge, style.size, style.speed, style.shots, style.hits,
                style.damage, renderer(style.kind, nativeId));
    }

    /** Exact TechniqueDispatcher/setup defaults in the pinned DragonMineZ 2.1.3 jar. */
    static int renderer(KiLook.Kind kind, String id) {
        return switch (kind) {
            case WAVE -> switch (id == null ? "" : id) {
                case "kamehameha" -> 1; case "galick_gun" -> 2; case "final_flash" -> 3; case "masenko" -> 4;
                default -> 0;
            };
            case BEAM -> "makkanko".equals(id) ? 1 : 2;
            case GIANT_BALL -> "spiritbomb".equals(id) ? 5 : "supernova".equals(id) ? 6 : 2;
            case BARRAGE -> 9;
            case MEDIUM_BALL -> "sokidan".equals(id) ? 8 : 1;
            default -> 0;
        };
    }

    /** {main colour, second colour} as DragonMineZ registers them (pinned 2.1.3). */
    private static int[] colours(String nativeId) {
        return switch (nativeId == null ? "" : nativeId) {
            case "kamehameha" -> new int[] {5240831, 5240831};       // DMZ blue
            case "big_bang" -> new int[] {5240831, 5240831};         // DMZ big_bang interior
            case "sokidan" -> new int[] {5220863, 5220863};
            case "galick_gun" -> new int[] {13504739, 11407587};     // purple
            case "death_beam", "emperor_death_beam" -> new int[] {13504739, 13504739};
            case "final_flash" -> new int[] {16750848, 16750848};    // gold
            case "burning_attack" -> new int[] {16755200, 16755200};
            case "masenko", "kienzan", "kienzan_doble" -> new int[] {16771584, 16771584}; // yellow
            case "makkanko" -> new int[] {16770363, 12860415};       // yellow + spiral
            case "supernova", "supernova_cooler" -> new int[] {16750848, 16729088};
            case "spiritbomb" -> new int[] {3211249, 63743};         // blue spirit bomb
            case "ki_barrage" -> new int[] {16776960, 16776960};     // yellow barrage
            case "final_explosion" -> new int[] {16750848, 16729088}; // orange radial blast
            case "taiyoken" -> new int[] {16777215, 16776960};       // white/yellow flash
            case "soul_punisher" -> new int[] {10027263, 6684774};   // violet soul blast
            case "fake_moon" -> new int[] {16776960, 16755200};
            default -> new int[] {16776960, 16776960};
        };
    }

    /** The same attack after its charge: {@code power} 1 is a tap, 2 a full charge. */
    public V3KiStyle charged(float power) {
        float p = Float.isFinite(power) ? Math.clamp(power, 1f, 2f) : 1f;
        return new V3KiStyle(kind, core, edge, size * (1f + 0.5f * (p - 1f)), speed, shots, hits,
                damage * p, nativeRenderType, fxTrail, fxImpact, fxCharge);
    }

    public boolean beam() {
        return KiLook.beam(kind);
    }

    /** How close a flying shot's centre must come to its target's side to land. */
    public double radius() {
        return kind == KiLook.Kind.GIANT_BALL ? size : 0.5 * size;
    }

    public String asset(KiLook.Part part) {
        if (part == KiLook.Part.CHARGE && fxCharge != null) return fxCharge;
        if ((part == KiLook.Part.IMPACT || part == KiLook.Part.EXPLOSION) && fxImpact != null) return fxImpact;
        if ((part == KiLook.Part.WAVE_BODY || part == KiLook.Part.LASER || part == KiLook.Part.BALL
                || part == KiLook.Part.GIANT || part == KiLook.Part.DISC) && fxTrail != null) return fxTrail;
        return KiLook.asset(part, core);
    }

    private static String bundledAsset(String asset) {
        if (asset == null || !asset.matches("ki_[a-z0-9_]+")) return null;
        String path = "/assets/xenopixelsmod/effeks/ki/" + asset + ".efkefc";
        return V3KiStyle.class.getResource(path) != null ? asset : null;
    }
}
