package net.bullettrain.xenopixelsmod.combat.v3;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.extras.ActionMode;
import com.dragonminez.compat.util.LazyOptional;
import net.bullettrain.xenopixelsmod.command.XenoAuraCommands;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary DMZ Kaioken stack for Strike "Kaioken Attack": ascend for the cast window,
 * descend (clear stack) when the technique ends / is cancelled, with aura forced on.
 */
public final class V3KaiokenBurst {
    private static final String GROUP = "kaioken";
    private static final String FORM = "x3";
    private static final Map<UUID, State> ACTIVE = new ConcurrentHashMap<>();

    private V3KaiokenBurst() {}

    public static boolean isKaiokenTechnique(String techniqueId, String name) {
        String id = techniqueId == null ? "" : techniqueId.toLowerCase();
        String n = name == null ? "" : name.toLowerCase();
        return id.contains("kaiohken") || id.contains("kaioken")
                || n.contains("kaioken") || n.contains("kaiohken");
    }

    public static void begin(ServerPlayer player, int durationTicks) {
        if (player == null || durationTicks < 1) return;
        StatsData data = stats(player);
        if (data == null) return;
        if (ConfigManager.getStackForm(GROUP, FORM) == null) return;
        State prior = ACTIVE.get(player.getUUID());
        boolean hadAura = data.getStatus().isAuraActive();
        String prevGroup = data.getCharacter().getActiveStackFormGroup();
        String prevForm = data.getCharacter().getActiveStackForm();
        data.getStatus().setSelectedAction(ActionMode.STACK);
        data.getCharacter().setSelectedStackFormGroup(GROUP);
        data.getCharacter().setSelectedStackForm(FORM);
        data.getCharacter().recordPreviousStackForm();
        data.getCharacter().setActiveStackForm(GROUP, FORM);
        player.refreshDimensions();
        XenoAuraCommands.apply(player, true);
        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
        ACTIVE.put(player.getUUID(), new State(
                prior != null ? prior.hadAura : hadAura,
                prior != null ? prior.prevGroup : (prevGroup == null ? "" : prevGroup),
                prior != null ? prior.prevForm : (prevForm == null ? "" : prevForm)));
    }

    /** Descend: clear Kaioken stack and restore prior aura preference. */
    public static void end(ServerPlayer player) {
        if (player == null) return;
        State state = ACTIVE.remove(player.getUUID());
        if (state == null) return;
        StatsData data = stats(player);
        if (data == null) return;
        data.getCharacter().clearActiveStackForm(player);
        data.getCharacter().clearPreviousStackFormRecord();
        player.removeEffect(MainEffects.STACK_TRANSFORMED);
        player.refreshDimensions();
        XenoAuraCommands.apply(player, state.hadAura);
        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
    }

    private static StatsData stats(ServerPlayer player) {
        LazyOptional<StatsData> opt = StatsProvider.get(StatsCapability.INSTANCE, player);
        return opt != null ? opt.orElse(null) : null;
    }

    private record State(boolean hadAura, String prevGroup, String prevForm) {}
}
