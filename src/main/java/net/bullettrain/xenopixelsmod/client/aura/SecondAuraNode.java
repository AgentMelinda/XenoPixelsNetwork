package net.bullettrain.xenopixelsmod.client.aura;

import com.dragonminez.client.gui.radial.AbstractRadialNode;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * "Toggle Second Aura" under Ki Actions in DragonMineZ's X menu (2026-10-02 owner): switches
 * your own second aura, the HD aura, on and off for everyone who sees you. In the Hakaishin form
 * the same entry reads "Toggle Hakai no Energy". It sends {@code /secondaura on|off}; the state
 * shown is the one the server sent back ({@link SecondAuraClientState}).
 */
public final class SecondAuraNode extends AbstractRadialNode {
    /** The form group of xenopixels_hakaishin.json. */
    static final String HAKAISHIN_GROUP = "xenopixels_hakaishin";

    @Override
    public Component label(StatsData stats) {
        return Component.translatable(labelKey(hakaishin(stats)));
    }

    static String labelKey(boolean hakaishin) {
        return hakaishin ? "gui.xenopixelsmod.radial.hakai_energy" : "gui.xenopixelsmod.radial.second_aura";
    }

    private static boolean hakaishin(StatsData stats) {
        var character = stats == null ? null : stats.getCharacter();
        return character != null && character.hasActiveForm()
                && HAKAISHIN_GROUP.equals(character.getActiveFormGroup());
    }

    @Override
    public ResourceLocation icon(StatsData stats) {
        return icon("aura");
    }

    @Override
    public boolean active(StatsData stats) {
        var player = Minecraft.getInstance().player;
        return player != null && SecondAuraClientState.on(player.getId());
    }

    @Override
    public int labelColor(StatsData stats) {
        return active(stats) ? GREEN : RED;
    }

    @Override
    public void onSelect(StatsData stats) {
        boolean wasOn = active(stats);
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player.connection.sendCommand(wasOn ? "secondaura off" : "secondaura on");
        playToggle(!wasOn);
    }
}
