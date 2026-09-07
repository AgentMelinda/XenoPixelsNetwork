package net.bullettrain.xenopixelsmod.client.command;

import com.dragonminez.client.animation.CombatAnimationResolver;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.combat.DmzAnimHelperClient;
import net.bullettrain.xenopixelsmod.client.combat.anim.Bt3AnimationBinding;
import net.bullettrain.xenopixelsmod.client.combat.anim.Bt3PalAnimator;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.loading.object.BakedAnimations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Client-only diagnostics for the DragonMineZ combat animation backend.
 *
 * <p>{@code /xenopal} only hits PlayerAnimationLibrary, which cannot draw while DragonMineZ owns
 * the player renderer. This command goes through {@link Bt3PalAnimator#play} (the live backend,
 * currently DMZ) or a raw DMZ animation name, so a clip can be judged on the GeckoLib rig without
 * a combo, prediction, or knockback.
 *
 * <p>{@code /xenobt3 list} - the {@code combat.xeno_*} names this mod ships.
 * <br>{@code /xenobt3 play <intent>} - one {@link Bt3AnimationIntent}, e.g. {@code jab_left}.
 * <br>{@code /xenobt3 play-name <dmzAnim>} - a raw DragonMineZ animation name.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class Bt3AnimDebugCommands {

    private static final SuggestionProvider<CommandSourceStack> INTENT_SUGGEST =
            (ctx, builder) -> SharedSuggestionProvider.suggest(intentNames(), builder);

    private static final SuggestionProvider<CommandSourceStack> DMZ_NAME_SUGGEST =
            (ctx, builder) -> SharedSuggestionProvider.suggest(dmzNames(), builder);

    private Bt3AnimDebugCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xenobt3")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("doctor").executes(ctx -> doctor(ctx.getSource())))
                .then(Commands.literal("play")
                        .then(Commands.argument("intent", StringArgumentType.word())
                                .suggests(INTENT_SUGGEST)
                                .executes(ctx -> playIntent(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "intent")))))
                .then(Commands.literal("play-name")
                        .then(Commands.argument("dmzAnim", StringArgumentType.word())
                                .suggests(DMZ_NAME_SUGGEST)
                                .executes(ctx -> playName(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "dmzAnim"))))));
    }

    private static int list(CommandSourceStack source) {
        List<String> names = new ArrayList<>(Bt3AnimationBinding.customAnimationNames());
        if (names.isEmpty()) {
            source.sendFailure(Component.literal("§cNo combat.xeno_* names are bound"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "§eBT3 DMZ animations (" + names.size() + "): §f" + String.join(", ", names)), false);
        return names.size();
    }

    private static int playIntent(CommandSourceStack source, String raw) {
        if (Minecraft.getInstance().player == null) {
            return 0;
        }
        Bt3AnimationIntent intent = parseIntent(raw);
        if (intent == null) {
            source.sendFailure(Component.literal(
                    "§cNo BT3 intent " + raw + " — try /xenobt3 play jab_left"));
            return 0;
        }
        Bt3AnimationBinding.Binding binding = Bt3AnimationBinding.of(intent);
        String dmzName = binding == null ? "?" : binding.dmzAnim();
        String resolved = CombatAnimationResolver.resolveAttack(dmzName, false);
        boolean baked = geckoHas(dmzName);
        Bt3PalAnimator.play(Minecraft.getInstance().player, intent);
        source.sendSuccess(() -> Component.literal(
                "§aPlayed " + intent.name() + " §7→ " + dmzName
                        + " §8resolved=" + (resolved.isEmpty() ? "FALLBACK" : resolved)
                        + " baked=" + baked
                        + (Bt3AnimationBinding.USE_PAL ? " PAL" : " DMZ")),
                false);
        return 1;
    }

    private static int doctor(CommandSourceStack source) {
        BakedAnimations baked = GeckoLibCache.getBakedAnimations()
                .get(Bt3AnimationBinding.DMZ_ANIMATION_FILE);
        String resolved = CombatAnimationResolver.resolveAttack("combat.xeno_jab_left", false);
        String camera = Minecraft.getInstance().options.getCameraType().name();
        if (baked == null) {
            source.sendFailure(Component.literal(
                    "§cGeckoLib did not bake " + Bt3AnimationBinding.DMZ_ANIMATION_FILE
                            + " §7resolver=" + (resolved.isEmpty() ? "FALLBACK" : resolved)
                            + " camera=" + camera));
            return 0;
        }
        int count = baked.animations().size();
        boolean jab = baked.getAnimation("combat.xeno_jab_left") != null;
        boolean kick = baked.getAnimation("combat.xeno_flying_kick") != null;
        source.sendSuccess(() -> Component.literal(
                "§eBT3 doctor: baked " + count + " clips in " + Bt3AnimationBinding.DMZ_ANIMATION_FILE
                        + " §7jab=" + jab + " flying_kick=" + kick
                        + " resolver=" + (resolved.isEmpty() ? "FALLBACK" : resolved)
                        + " camera=" + camera), false);
        return count;
    }

    private static boolean geckoHas(String name) {
        BakedAnimations baked = GeckoLibCache.getBakedAnimations()
                .get(Bt3AnimationBinding.DMZ_ANIMATION_FILE);
        if (baked != null && baked.getAnimation(name) != null) {
            return true;
        }
        BakedAnimations dmzCombat = GeckoLibCache.getBakedAnimations().get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "dragonminez", "animations/entity/races/combat.animation.json"));
        return dmzCombat != null && dmzCombat.getAnimation(name) != null;
    }

    private static int playName(CommandSourceStack source, String dmzAnim) {
        if (Minecraft.getInstance().player == null) {
            return 0;
        }
        DmzAnimHelperClient.playLocalMelee(Minecraft.getInstance().player, dmzAnim, false, 1.0f);
        source.sendSuccess(() -> Component.literal(
                "§aPlayed DMZ name " + dmzAnim
                        + " §7— if this is a combat.xeno_* clip and nothing moved, the mixins did not apply"),
                false);
        return 1;
    }

    private static Bt3AnimationIntent parseIntent(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Bt3AnimationIntent.valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static List<String> intentNames() {
        List<String> names = new ArrayList<>();
        for (Bt3AnimationIntent intent : Bt3AnimationIntent.values()) {
            names.add(intent.name().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private static List<String> dmzNames() {
        List<String> names = new ArrayList<>(Bt3AnimationBinding.customAnimationNames());
        for (Bt3AnimationIntent intent : Bt3AnimationIntent.values()) {
            Bt3AnimationBinding.Binding binding = Bt3AnimationBinding.of(intent);
            if (binding != null && !names.contains(binding.dmzAnim())) {
                names.add(binding.dmzAnim());
            }
        }
        return names;
    }
}
