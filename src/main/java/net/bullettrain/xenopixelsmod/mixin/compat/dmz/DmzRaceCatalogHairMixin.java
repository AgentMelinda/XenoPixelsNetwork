package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.gui.character.CharacterCustomizationScreen;
import com.dragonminez.client.gui.character.util.ScaledScreen;
import com.dragonminez.common.stats.character.Character;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.dmz.race.RaceAppearanceCatalog;
import net.bullettrain.xenopixelsmod.dmz.race.RacePackService;
import net.bullettrain.xenopixelsmod.hair.HairApplyService;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds authored race hair to DMZ's working character; its normal creation packet carries it. */
@Mixin(value = CharacterCustomizationScreen.class, remap = false)
public abstract class DmzRaceCatalogHairMixin extends ScaledScreen {
    @Shadow @Final private Character character;
    @Unique private int xenopixels$catalogIndex;

    protected DmzRaceCatalogHairMixin(Component title) { super(title); }

    @Inject(method = "initHairTab", at = @At("RETURN"), require = 1)
    private void xenopixels$catalogHair(int top, CallbackInfo ci) {
        RaceAppearanceCatalog catalog = RaceAppearanceCatalog.loadLive(character.getRace());
        var styles = catalog.hairs();
        if (styles.isEmpty()) return;
        AtlasButton button = new AtlasButton(124, getUiHeight() - 40,
                Component.literal("Race Hair"), "mynpcs_button_row", b -> {
            var style = styles.get(Math.floorMod(xenopixels$catalogIndex, styles.size()));
            try {
                var document = catalog.readHair(RacePackService.dmzRoot(), style);
                character.setHairBase(HairApplyService.toCustomHair(HairApplyService.plan(document)));
                character.setHairId(0);
                character.setHairColor(document.globalColor());
                b.setTooltip(Tooltip.create(Component.literal(style.label() + " (click for next)")));
                xenopixels$catalogIndex++;
            } catch (java.io.IOException ex) {
                XenoPixelsMod.LOGGER.warn("Could not load race hair {} for {}", style.id(), character.getRace(), ex);
                b.setTooltip(Tooltip.create(Component.literal("Could not load " + style.label())));
            }
        });
        button.setTooltip(Tooltip.create(Component.literal("Choose authored hair for this race; click to cycle.")));
        addRenderableWidget(button);
    }
}
