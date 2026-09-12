package net.bullettrain.xenopixelsmod.client.effect;

import java.util.ArrayList;
import java.util.List;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoInventoryEffects {
    private static final int ICON_SIZE = 18;
    private static final int ICON_INSET = (XenoEffectRailLayout.CELL_SIZE - ICON_SIZE) / 2;

    private XenoInventoryEffects() {}

    @EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ClientExtensions {
        private ClientExtensions() {}

        @SubscribeEvent
        public static void register(RegisterClientExtensionsEvent event) {
            MobEffect[] compactEffects = BuiltInRegistries.MOB_EFFECT.stream()
                    .filter(XenoInventoryEffects::isCompactEffect)
                    .toArray(MobEffect[]::new);
            event.registerMobEffect(new IClientMobEffectExtensions() {
                @Override
                public boolean isVisibleInInventory(MobEffectInstance instance) {
                    return false;
                }
            }, compactEffects);
        }
    }

    @SubscribeEvent
    public static void suppressVanillaInventoryEffects(ScreenEvent.RenderInventoryMobEffects event) {
        if (event.getScreen() instanceof InventoryScreen) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void afterScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen inventory)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        List<MobEffectInstance> effects = minecraft.player.getActiveEffects().stream()
                .toList();
        if (effects.isEmpty()) return;

        List<XenoEffectRailLayout.Cell> cells = XenoEffectRailLayout.layout(
                inventory.getGuiLeft() + inventory.getXSize(), inventory.getGuiTop(),
                inventory.width, inventory.height, effects.size());
        GuiGraphics graphics = event.getGuiGraphics();
        MobEffectInstance hovered = null;

        for (int index = 0; index < effects.size(); index++) {
            MobEffectInstance effect = effects.get(index);
            XenoEffectRailLayout.Cell cell = cells.get(index);
            renderCell(graphics, minecraft, effect, cell.x(), cell.y());
            if (event.getMouseX() >= cell.x() && event.getMouseX() < cell.x() + XenoEffectRailLayout.CELL_SIZE
                    && event.getMouseY() >= cell.y() && event.getMouseY() < cell.y() + XenoEffectRailLayout.CELL_SIZE) {
                hovered = effect;
            }
        }

        if (hovered != null) {
            graphics.renderComponentTooltip(minecraft.font, tooltip(hovered), event.getMouseX(), event.getMouseY());
        }
    }

    private static void renderCell(GuiGraphics graphics, Minecraft minecraft, MobEffectInstance effect, int x, int y) {
        ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
        int accent = key != null && "dragonminez".equals(key.getNamespace()) ? 0xFFFFC928 : 0xFF35D7FF;
        graphics.fill(x, y, x + XenoEffectRailLayout.CELL_SIZE, y + XenoEffectRailLayout.CELL_SIZE, 0xD00A1018);
        graphics.fill(x, y, x + XenoEffectRailLayout.CELL_SIZE, y + 2, accent);
        graphics.fill(x, y, x + 1, y + XenoEffectRailLayout.CELL_SIZE, 0xFF688195);
        graphics.fill(x, y + XenoEffectRailLayout.CELL_SIZE - 1,
                x + XenoEffectRailLayout.CELL_SIZE, y + XenoEffectRailLayout.CELL_SIZE, 0xFF182A38);
        graphics.fill(x + XenoEffectRailLayout.CELL_SIZE - 1, y,
                x + XenoEffectRailLayout.CELL_SIZE, y + XenoEffectRailLayout.CELL_SIZE, 0xFF182A38);

        TextureAtlasSprite sprite = minecraft.getMobEffectTextures().get(effect.getEffect());
        graphics.blit(x + ICON_INSET, y + ICON_INSET, 0, ICON_SIZE, ICON_SIZE, sprite);
    }

    private static boolean isCompactEffect(MobEffect effect) {
        ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
        if (key == null) return false;
        if ("hakai_dissolve".equals(key.getPath())) return false;
        return XenoPixelsMod.MOD_ID.equals(key.getNamespace()) || "dragonminez".equals(key.getNamespace());
    }

    private static List<Component> tooltip(MobEffectInstance effect) {
        List<Component> lines = new ArrayList<>(2);
        String suffix = effect.getAmplifier() > 0 ? " " + roman(effect.getAmplifier() + 1) : "";
        lines.add(Component.translatable(effect.getDescriptionId()).append(suffix));
        if (!effect.isInfiniteDuration()) {
            lines.add(MobEffectUtil.formatDuration(effect, 1.0F, 20.0F));
        }
        return lines;
    }

    private static String roman(int value) {
        if (value < 1 || value > 20) return Integer.toString(value);
        String[] tens = {"", "X", "XX"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return tens[value / 10] + ones[value % 10];
    }
}
