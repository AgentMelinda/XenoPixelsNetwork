package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import xenoapi.npcs.api.CustomNPCsException;
import xenoapi.npcs.api.IScoreboard;
import xenoapi.npcs.api.IScoreboardObjective;
import xenoapi.npcs.api.IScoreboardScore;
import xenoapi.npcs.api.IScoreboardTeam;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The vanilla server scoreboard as XenoAPI's {@link IScoreboard}, with objectives, scores and teams
 * over the same objects {@code /scoreboard} and {@code /team} edit. Names are bounded to vanilla's
 * command limits; every mutation runs on the server thread.
 */
final class XenoScoreboardAdapter implements IScoreboard {
    static final int MAX_NAME = 64;
    private final Scoreboard board;

    XenoScoreboardAdapter(Scoreboard board) {
        this.board = board;
    }

    static String name(String method, String value) {
        String name = XenoApiAdapters.boundedText(method, value, MAX_NAME);
        if (name.isEmpty()) throw new IllegalArgumentException(method + ": name cannot be empty");
        return name;
    }

    private Objective objective(String method, String name) {
        Objective objective = board.getObjective(name(method, name));
        if (objective == null) throw new CustomNPCsException("%s: no objective %s", method, name);
        return objective;
    }

    private static void mutate() {
        XenoApiAdapters.requireServerThreadNow();
    }

    @Override
    public IScoreboardObjective[] getObjectives() {
        List<IScoreboardObjective> out = new ArrayList<>();
        for (Objective objective : board.getObjectives()) out.add(new Obj(board, objective));
        return out.toArray(IScoreboardObjective[]::new);
    }

    @Override
    public IScoreboardObjective getObjective(String name) {
        Objective objective = name == null ? null : board.getObjective(name);
        return objective == null ? null : new Obj(board, objective);
    }

    @Override public boolean hasObjective(String objective) { return objective != null && board.getObjective(objective) != null; }

    @Override
    public void removeObjective(String objective) {
        Objective found = objective == null ? null : board.getObjective(objective);
        if (found == null) return;
        mutate();
        board.removeObjective(found);
    }

    @Override
    public IScoreboardObjective addObjective(String objective, String criteria) {
        String name = name("IScoreboard.addObjective", objective);
        ObjectiveCriteria kind = ObjectiveCriteria.byName(criteria == null ? "dummy" : criteria.trim())
                .orElseThrow(() -> new CustomNPCsException("Unknown scoreboard criteria: %s", criteria));
        if (board.getObjective(name) != null) throw new CustomNPCsException("Objective %s already exists", name);
        mutate();
        Objective created = board.addObjective(name, kind, Component.literal(name), kind.getDefaultRenderType(), false, null);
        return new Obj(board, created);
    }

    @Override
    public void setPlayerScore(String player, String objective, int score) {
        Objective found = objective("IScoreboard.setPlayerScore", objective);
        if (found.getCriteria().isReadOnly()) throw new CustomNPCsException("Objective %s is read-only", objective);
        mutate();
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(name("IScoreboard.setPlayerScore", player)), found).set(score);
    }

    @Override
    public int getPlayerScore(String player, String objective) {
        Objective found = objective == null ? null : board.getObjective(objective);
        if (found == null || player == null) return 0;
        ReadOnlyScoreInfo info = board.getPlayerScoreInfo(ScoreHolder.forNameOnly(player), found);
        return info == null ? 0 : info.value();
    }

    @Override
    public boolean hasPlayerObjective(String player, String objective) {
        Objective found = objective == null ? null : board.getObjective(objective);
        return found != null && player != null && board.getPlayerScoreInfo(ScoreHolder.forNameOnly(player), found) != null;
    }

    @Override
    public void deletePlayerScore(String player, String objective) {
        Objective found = objective == null ? null : board.getObjective(objective);
        if (found == null || player == null) return;
        mutate();
        board.resetSinglePlayerScore(ScoreHolder.forNameOnly(player), found);
    }

    @Override
    public IScoreboardTeam[] getTeams() {
        List<IScoreboardTeam> out = new ArrayList<>();
        for (PlayerTeam team : board.getPlayerTeams()) out.add(new Team(board, team));
        return out.toArray(IScoreboardTeam[]::new);
    }

    @Override public boolean hasTeam(String name) { return name != null && board.getPlayerTeam(name) != null; }

    @Override
    public IScoreboardTeam addTeam(String name) {
        String team = name("IScoreboard.addTeam", name);
        if (board.getPlayerTeam(team) != null) throw new CustomNPCsException("Team %s already exists", team);
        mutate();
        return new Team(board, board.addPlayerTeam(team));
    }

    @Override
    public IScoreboardTeam getTeam(String name) {
        PlayerTeam team = name == null ? null : board.getPlayerTeam(name);
        return team == null ? null : new Team(board, team);
    }

    @Override
    public void removeTeam(String name) {
        PlayerTeam team = name == null ? null : board.getPlayerTeam(name);
        if (team == null) return;
        mutate();
        board.removePlayerTeam(team);
    }

    @Override
    public IScoreboardTeam getPlayerTeam(String player) {
        PlayerTeam team = player == null ? null : board.getPlayersTeam(player);
        return team == null ? null : new Team(board, team);
    }

    @Override
    public void removePlayerTeam(String player) {
        if (player == null || board.getPlayersTeam(player) == null) return;
        mutate();
        board.removePlayerFromTeam(player);
    }

    /** Every name that holds a score. */
    @Override
    public String[] getPlayerList() {
        return board.getTrackedPlayers().stream().map(ScoreHolder::getScoreboardName).toArray(String[]::new);
    }

    // ------------------------------------------------------------------ objective / score

    private record Obj(Scoreboard board, Objective objective) implements IScoreboardObjective {
        @Override public String getName() { return objective.getName(); }
        @Override public String getDisplayName() { return objective.getDisplayName().getString(); }

        @Override
        public void setDisplayName(String name) {
            String value = XenoApiAdapters.boundedText("IScoreboardObjective.setDisplayName", name, 128);
            mutate();
            objective.setDisplayName(Component.literal(value));
        }

        @Override public String getCriteria() { return objective.getCriteria().getName(); }
        @Override public boolean isReadyOnly() { return objective.getCriteria().isReadOnly(); }

        @Override
        public IScoreboardScore[] getScores() {
            List<IScoreboardScore> out = new ArrayList<>();
            for (PlayerScoreEntry entry : board.listPlayerScores(objective)) out.add(new Score(board, objective, entry.owner()));
            return out.toArray(IScoreboardScore[]::new);
        }

        @Override
        public IScoreboardScore getScore(String player) {
            return hasScore(player) ? new Score(board, objective, player) : null;
        }

        @Override
        public boolean hasScore(String player) {
            return player != null && board.getPlayerScoreInfo(ScoreHolder.forNameOnly(player), objective) != null;
        }

        @Override
        public IScoreboardScore createScore(String player) {
            String name = name("IScoreboardObjective.createScore", player);
            mutate();
            board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(name), objective);
            return new Score(board, objective, name);
        }

        @Override
        public void removeScore(String player) {
            if (player == null) return;
            mutate();
            board.resetSinglePlayerScore(ScoreHolder.forNameOnly(player), objective);
        }
    }

    private record Score(Scoreboard board, Objective objective, String player) implements IScoreboardScore {
        @Override
        public int getValue() {
            ReadOnlyScoreInfo info = board.getPlayerScoreInfo(ScoreHolder.forNameOnly(player), objective);
            return info == null ? 0 : info.value();
        }

        @Override
        public void setValue(int val) {
            if (objective.getCriteria().isReadOnly()) throw new CustomNPCsException("Objective %s is read-only", objective.getName());
            mutate();
            board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(player), objective).set(val);
        }

        @Override public String getPlayerName() { return player; }
    }

    // ------------------------------------------------------------------ team

    private record Team(Scoreboard board, PlayerTeam team) implements IScoreboardTeam {
        @Override public String getName() { return team.getName(); }
        @Override public String getDisplayName() { return team.getDisplayName().getString(); }

        @Override
        public void setDisplayName(String name) {
            String value = XenoApiAdapters.boundedText("IScoreboardTeam.setDisplayName", name, 128);
            mutate();
            team.setDisplayName(Component.literal(value));
        }

        @Override
        public void addPlayer(String player) {
            String name = name("IScoreboardTeam.addPlayer", player);
            mutate();
            board.addPlayerToTeam(name, team);
        }

        @Override public boolean hasPlayer(String player) { return player != null && team.getPlayers().contains(player); }

        @Override
        public void removePlayer(String player) {
            if (!hasPlayer(player)) return;
            mutate();
            board.removePlayerFromTeam(player, team);
        }

        @Override public String[] getPlayers() { return team.getPlayers().toArray(String[]::new); }

        @Override
        public void clearPlayers() {
            mutate();
            for (String player : List.copyOf(team.getPlayers())) board.removePlayerFromTeam(player, team);
        }

        @Override public boolean getFriendlyFire() { return team.isAllowFriendlyFire(); }

        @Override
        public void setFriendlyFire(boolean bo) {
            mutate();
            team.setAllowFriendlyFire(bo);
        }

        /** A vanilla colour name such as {@code red} or {@code dark_blue}. */
        @Override
        public void setColor(String color) {
            ChatFormatting format = color == null ? null : ChatFormatting.getByName(color.trim().toLowerCase(Locale.ROOT));
            if (format == null || !format.isColor()) throw new CustomNPCsException("Unknown team colour: %s", color);
            mutate();
            team.setColor(format);
        }

        @Override public String getColor() { return team.getColor().getName(); }

        @Override
        public void setSeeInvisibleTeamPlayers(boolean bo) {
            mutate();
            team.setSeeFriendlyInvisibles(bo);
        }

        @Override public boolean getSeeInvisibleTeamPlayers() { return team.canSeeFriendlyInvisibles(); }
    }
}
