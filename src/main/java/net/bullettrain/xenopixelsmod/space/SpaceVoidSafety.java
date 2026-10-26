package net.bullettrain.xenopixelsmod.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Set;

/**
 * §19.1's void-damage rule: falling out of a <b>space</b> dimension is a survivable mistake rather than
 * a death, and it is switchable by config, by command, and per charted body.
 *
 * <p><b>Why cancelling is not enough on its own.</b> Vanilla deals the {@code out_of_world} damage from
 * {@code LivingEntity.onBelowWorld()} at 4♥ per tick once {@code Entity.checkBelowWorld()} sees
 * {@code y < minBuildHeight - 64} — five ticks kills a healthy player — and a space dimension has no
 * floor to land on. Cancelling the damage alone would replace a fast death with an infinite fall. So
 * protection is cancel <em>plus</em> a bounded return: the entity is placed back at the handoff anchor
 * its dimension was charted with (the Y arrivals already use) or, when nothing is charted there, near
 * the top of the dimension's own build volume, with its motion zeroed. A rescued player who does
 * nothing gets re-rescued every few seconds rather than dying once.
 *
 * <p><b>Fail-closed scope.</b> Only a dimension {@link SpaceDimensions} calls space is eligible — one bound
 * to a chart body, or one listed in {@code spaceDimensions}. Every other dimension, including the
 * overworld, nether and End, keeps vanilla void damage, because "nothing can kill me at the bottom of the
 * world" is not a survival rule anyone authored for a normal map. The rule also deliberately ignores
 * {@link XenoSpaceConfig#enabled()}: the LOD ladder and gravity passes are per-tick machinery, while
 * not dying is a rule a server can want with the tick work switched off.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SpaceVoidSafety {
    /** How far below the dimension's top face an uncharted rescue lands. */
    private static final int UNCHARTED_HEADROOM = 8;

    private SpaceVoidSafety() {}

    /** What the rule decided for one dimension. */
    public enum Verdict {
        /** Not a space dimension — vanilla void damage, untouched. */
        VANILLA,
        /** Space dimension where damage is enabled ({@code voidDamage=on}, or {@code void_damage:true}). */
        DAMAGE,
        /** Space dimension that is protected: damage cancelled and the faller returned to a safe Y. */
        PROTECTED
    }

    /**
     * One dimension's decision and the anchor it would rescue to, resolved in a single chart lookup so
     * the per-event path and {@code /xenospace voiddamage status} cannot drift apart.
     *
     * @param verdict      what happens to {@code out_of_world} damage here
     * @param anchorY      the charted body's handoff anchor, or {@link Double#NaN} when the dimension
     *                     has no chart row at all
     */
    public record Rule(Verdict verdict, double anchorY) {}

    /**
     * The inheritance chain with no Minecraft types in it, so the precedence a pack author writes is the
     * thing under test: the chart row that binds the dimension decides when it authored
     * {@code void_damage}, otherwise the global switch decides, and a dimension that is not space at all
     * is never touched.
     */
    public static Verdict verdict(Boolean authoredOverride, boolean globalSwitch, boolean spaceDimension) {
        if (!spaceDimension) return Verdict.VANILLA;
        boolean damage = authoredOverride == null ? globalSwitch : authoredOverride;
        return damage ? Verdict.DAMAGE : Verdict.PROTECTED;
    }

    /**
     * Resolves one dimension against one chart snapshot. Package-visible inputs only — no server, no
     * level — which is what keeps it callable from a headless test.
     *
     * @param chart         the live chart
     * @param dimension     the dimension's id
     * @param globalSwitch  {@code space.voidDamage}
     * @param listedSpace   {@code space.spaceDimensions}, the unbound dimensions still called space
     * @return the verdict plus the rescue anchor for that dimension
     */
    public static Rule ruleFor(ChartRegistry chart, ResourceLocation dimension, boolean globalSwitch,
                               Set<ResourceLocation> listedSpace) {
        int slot = chart == null || dimension == null ? -1 : chart.slotFor(dimension);
        return ruleForSlot(chart, dimension, globalSwitch, listedSpace, slot);
    }

    /** Cell-aware rule used by players in the shared planet-surface dimension. */
    public static Rule ruleFor(ChartRegistry chart, ResourceLocation dimension, boolean globalSwitch,
                               Set<ResourceLocation> listedSpace, int blockX, int blockZ) {
        int slot = chart == null || dimension == null ? -1
                : ChartSurfaceBodyResolver.slotAt(chart, dimension.toString(), blockX, blockZ);
        return ruleForSlot(chart, dimension, globalSwitch, listedSpace, slot);
    }

    private static Rule ruleForSlot(ChartRegistry chart, ResourceLocation dimension, boolean globalSwitch,
                                    Set<ResourceLocation> listedSpace, int slot) {
        Boolean authored = slot < 0 ? null : chart.body(slot).voidDamage();
        double anchor = slot < 0 ? Double.NaN : chart.body(slot).handoffAnchorY();
        boolean space = SpaceDimensions.isSpace(chart, dimension, listedSpace);
        return new Rule(verdict(authored, globalSwitch, space), anchor);
    }

    /** The live decision for whoever is asking, from the server's current chart and config. */
    public static Rule currentRule(MinecraftServer server, ResourceLocation dimension) {
        return ruleFor(SpaceChartService.state(server).runtime.chart, dimension,
                XenoSpaceConfig.voidDamage(), XenoSpaceConfig.spaceDimensions());
    }

    private static Rule currentRule(MinecraftServer server, ResourceLocation dimension, int blockX, int blockZ) {
        return ruleFor(SpaceChartService.state(server).runtime.chart, dimension,
                XenoSpaceConfig.voidDamage(), XenoSpaceConfig.spaceDimensions(), blockX, blockZ);
    }

    /**
     * The Y a rescued faller is returned to: the dimension's charted anchor when it has one, else
     * {@value #UNCHARTED_HEADROOM} blocks below the dimension's own top face. A hand-edited pack can
     * author an anchor outside the build volume, which would teleport the faller into the void they just
     * escaped, so an unusable anchor degrades to the dimension top rather than being honoured.
     *
     * @param anchorY         the bound body's handoff anchor, or NaN when nothing is charted there
     * @param minBuildHeight  the dimension's lowest buildable Y
     * @param height          the dimension's total build height in blocks
     * @return a Y inside the build volume and above the below-world trigger
     */
    public static double returnY(double anchorY, int minBuildHeight, int height) {
        int top = minBuildHeight + Math.max(1, height);
        // Never below the volume's own floor: the below-world trigger is 64 blocks under it, so a
        // rescue that lands below the floor has not rescued anything.
        double fallback = Math.max(minBuildHeight, top - UNCHARTED_HEADROOM);
        if (!Double.isFinite(anchorY)) return fallback;
        if (anchorY < minBuildHeight || anchorY > top) return fallback;
        return anchorY;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        // A client level is never a ServerLevel, so this one pattern match is also the side guard that
        // keeps the chart — which throws when touched off the server thread — out of the render path.
        if (!(victim.level() instanceof ServerLevel level)) return;
        DamageSource source = event.getSource();
        if (!source.is(DamageTypes.FELL_OUT_OF_WORLD)) return;
        var position = victim.blockPosition();
        Rule rule = currentRule(level.getServer(), level.dimension().location(), position.getX(), position.getZ());
        if (rule.verdict() != Verdict.PROTECTED) return;
        event.setCanceled(true);
        // Unbounded Voidsea (switch voidseaUnboundedHeight): no floor - keep flying/falling freely; the old rescue is
        // only the last safety net at the Voidsea height limit.
        if (XenoSpaceConfig.voidseaUnboundedHeight()
                && net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST.equals(level.dimension().location())
                && victim.getY() > -XenoSpaceConfig.VOIDSEA_HEIGHT_LIMIT) {
            // Same exemption the rescue grants: a survival player falling with no floor would otherwise be kicked by
            // vanilla's anti-fly watchdog ("Flying is not enabled on this server", seen in Codex's CL-123 player test).
            if (victim instanceof ServerPlayer player) SpaceFlightGuard.grant(player);
            return;
        }
        rescue(victim, level, rule.anchorY());
    }

    /** Puts the faller back inside the dimension with no downward motion. */
    private static void rescue(LivingEntity victim, ServerLevel level, double anchorY) {
        double y = returnY(anchorY, level.getMinBuildHeight(), level.getHeight());
        // Absolute position and absolute rotations: same-level repositioning, no visible snap.
        boolean moved = victim.teleportTo(level, victim.getX(), y, victim.getZ(), Set.of(),
                victim.getYRot(), victim.getXRot());
        victim.setDeltaMovement(0.0, 0.0, 0.0);
        if (victim instanceof ServerPlayer player) {
            // The rescued player has no floor under them and will start falling again immediately, so the
            // anti-fly watchdog would count them from here; exempt them for as long as the rescue lasts.
            SpaceFlightGuard.grant(player);
            player.displayClientMessage(Component.literal(
                    "§dVoid safety: returned to Y " + Math.round(y) + " (space void damage off)"), true);
        }
        if (!moved) {
            // The boolean is the sync contract; false means the teleport was rejected and this faller
            // will be back here next tick, so without this line the loop would be invisible.
            XenoPixelsMod.LOGGER.warn("Space void safety could not reposition {} in {}",
                    victim.getScoreboardName(), level.dimension().location());
        }
    }

    /** Echoed by {@code /xenospace voiddamage status}; reports what would happen, changing nothing. */
    public static String status(MinecraftServer server, ResourceLocation dimension) {
        Rule rule = currentRule(server, dimension);
        return statusText(dimension, rule);
    }

    /** Cell-aware status for a command issued by a player inside planet_surfaces. */
    public static String status(MinecraftServer server, ResourceLocation dimension, int blockX, int blockZ) {
        Rule rule = ruleFor(SpaceChartService.state(server).runtime.chart, dimension,
                XenoSpaceConfig.voidDamage(), XenoSpaceConfig.spaceDimensions(), blockX, blockZ);
        return statusText(dimension, rule);
    }

    private static String statusText(ResourceLocation dimension, Rule rule) {
        return "void damage " + (XenoSpaceConfig.voidDamage() ? "on" : "off") + " globally, "
                + describe(rule.verdict()) + " in " + dimension
                + " (space dims=" + XenoSpaceConfig.spaceDimensions() + ")"
                + (Double.isNaN(rule.anchorY()) ? "" : ", rescue anchor Y=" + Math.round(rule.anchorY()));
    }

    private static String describe(Verdict verdict) {
        return switch (verdict) {
            case VANILLA -> "vanilla, not a space dimension";
            case DAMAGE -> "lethal here";
            case PROTECTED -> "protected here";
        };
    }
}
