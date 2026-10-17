package net.bullettrain.xenopixelsmod.combat.v3;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.util.TransformationsHelper;
import com.dragonminez.server.events.players.actionmode.FormModeHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Uses only DMZ-unlocked forms and DMZ's own form action/cost path. */
final class SuperRushTransformation {
    private record Choice(String group, FormConfig.FormData form) {
        String key() { return group + ":" + form.getName(); }
    }
    private static final Map<UUID, String> LAST = new HashMap<>();
    private SuperRushTransformation() {}

    static boolean transform(ServerPlayer player) {
        if (player == null) return false;
        StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (stats == null || stats.getCharacter() == null) return false;
        var character = stats.getCharacter();
        String race = character.getRaceName();
        List<Choice> choices = new ArrayList<>();
        for (Map.Entry<String, FormConfig> entry : ConfigManager.getAllFormsForRace(race).entrySet()) {
            String group = entry.getKey();
            if (character.hasActiveForm() && group.equalsIgnoreCase(character.getActiveFormGroup())) continue;
            for (FormConfig.FormData form : TransformationsHelper.getUnlockedForms(stats, race, group))
                if (form != null && !form.getName().equalsIgnoreCase(character.getActiveForm()))
                    choices.add(new Choice(group, form));
        }
        String previous = LAST.get(player.getUUID());
        choices.removeIf(choice -> choice.key().equals(previous));
        if (choices.isEmpty()) return false;
        Choice chosen = choices.get(player.getRandom().nextInt(choices.size()));
        character.setSelectedFormGroup(chosen.group());
        character.setSelectedForm(chosen.form().getName());
        // FormModeHandler enforces DMZ's energy, stamina, health, trigger-item and mastery rules.
        new FormModeHandler().performAction(player, stats);
        boolean applied = chosen.group().equalsIgnoreCase(character.getActiveFormGroup())
                && chosen.form().getName().equalsIgnoreCase(character.getActiveForm());
        if (applied) LAST.put(player.getUUID(), chosen.key());
        return applied;
    }

    static void clear(UUID player) { LAST.remove(player); }
    static void clearAll() { LAST.clear(); }
}
