package dev.lisaa.jscoreboards;

import com.google.common.collect.ImmutableSet;
import dev.lisaa.jscoreboards.abstraction.InternalObjectiveWrapper;
import dev.lisaa.jscoreboards.abstraction.InternalTeamWrapper;
import dev.lisaa.jscoreboards.exception.DuplicateTeamCreatedException;
import dev.lisaa.jscoreboards.exception.ScoreboardLineTooLongException;
import dev.lisaa.jscoreboards.versioning.SpigotAPIVersion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public abstract class JScoreboard {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int SPLIT_LIMIT = 64;
    private static final int MAX_LINE_LENGTH = 128;

    private JScoreboardOptions options = JScoreboardOptions.DEFAULT_OPTIONS;

    private final InternalObjectiveWrapper objectiveWrapper;
    private final InternalTeamWrapper teamWrapper;

    private final Set<UUID> activePlayers = new HashSet<>();
    private final List<JScoreboardTeam> teams = new ArrayList<>();
    private final Map<Scoreboard, List<String>> previousLinesMap = new HashMap<>();

    protected JScoreboard() {
        try {
            this.objectiveWrapper = SpigotAPIVersion.getCurrent().makeObjectiveWrapper();
            this.teamWrapper = SpigotAPIVersion.getCurrent().makeInternalTeamWrapper();
        } catch (NoSuchMethodException | IllegalAccessException |
                 InvocationTargetException | InstantiationException e) {
            throw new RuntimeException("Failed to initialize JScoreboard", e);
        }
    }

    /* ========================================================= */
    /* ======================== PUBLIC API ===================== */
    /* ========================================================= */

    public void addPlayer(Player player) {
        activePlayers.add(player.getUniqueId());
    }

    public void removePlayer(Player player) {
        if (!activePlayers.remove(player.getUniqueId())) return;

        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

        teams.forEach(team -> {
            if (team.isOnTeam(player.getUniqueId())) {
                team.removePlayer(player);
            }
        });
    }

    public Optional<JScoreboardTeam> findTeam(String name) {
        return teams.stream()
                .filter(team ->
                        stripColor(team.getInternalName())
                                .equalsIgnoreCase(stripColor(name)))
                .findFirst();
    }

    public void addTeams(JScoreboardTeam... newTeams)
            throws DuplicateTeamCreatedException {

        for (JScoreboardTeam team : newTeams) {

            if (team.getScoreboard() != this) {
                throw new IllegalArgumentException(
                        "Team " + team.getInternalName() + " belongs to another scoreboard."
                );
            }

            if (!teams.contains(team)) {
                team.refresh();
                teams.add(team);
            }
        }
    }

    public void removeTeam(JScoreboardTeam team) {
        if (team.getScoreboard() != this) return;

        team.destroy();
        teams.remove(team);
    }

    public void shutdown() {

        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();

        for (UUID uuid : activePlayers) {

            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            Scoreboard board = resolveScoreboard(player);

            if (board != null) {
                // Remove sidebar
                board.clearSlot(DisplaySlot.SIDEBAR);

                // Reset scores
                board.getEntries().forEach(board::resetScores);

                // Unregister line teams
                board.getTeams().forEach(team -> {
                    if (team.getName().startsWith("line")) {
                        team.unregister();
                    }
                });

                previousLinesMap.remove(board);
            }

            player.setScoreboard(main);
        }

        // Destroy custom teams
        teams.forEach(JScoreboardTeam::destroy);

        activePlayers.clear();
        teams.clear();
        previousLinesMap.clear();
    }

    public ImmutableSet<JScoreboardTeam> getTeams() {
        return ImmutableSet.copyOf(teams);
    }

    public JScoreboardOptions getOptions() {
        return options;
    }

    protected void setOptions(JScoreboardOptions options) {
        this.options = options == null
                ? JScoreboardOptions.DEFAULT_OPTIONS
                : options;
    }

    protected Set<UUID> getActivePlayers() {
        return activePlayers;
    }

    protected InternalObjectiveWrapper getObjectiveWrapper() {
        return objectiveWrapper;
    }

    protected InternalTeamWrapper getTeamWrapper() {
        return teamWrapper;
    }

    protected abstract Component getTitle(Scoreboard scoreboard);

    /* ========================================================= */
    /* ===================== LINE UPDATES ====================== */
    /* ========================================================= */

    public void updateLine(Player player, int line, String text)
            throws ScoreboardLineTooLongException {

        Scoreboard scoreboard = resolveScoreboard(player);
        if (scoreboard == null) return;

        updateLineInternal(scoreboard, line, text, true);
    }

    public void updateLine(Player player, int line)
            throws ScoreboardLineTooLongException {

        Scoreboard scoreboard = resolveScoreboard(player);
        if (scoreboard == null) return;

        updateLineInternal(scoreboard, line, null, false);
    }

    private void updateLineInternal(Scoreboard scoreboard,
                                    int line,
                                    String newText,
                                    boolean overwrite)
            throws ScoreboardLineTooLongException {

        List<String> cached = previousLinesMap.get(scoreboard);
        if (cached == null) return;

        int logicalIndex = cached.size() - line;
        if (logicalIndex < 0 || logicalIndex >= cached.size()) return;

        String text = overwrite ? newText : cached.get(logicalIndex);

        if (overwrite) {
            cached.set(logicalIndex, newText);
        }

        Team team = scoreboard.getTeam("line" + line);
        if (team != null) {
            applyText(team, text);
        }
    }

    private Scoreboard resolveScoreboard(Player player) {
        if (this instanceof JPerPlayerScoreboard perPlayer) {
            return perPlayer.getScoreboard(player);
        }
        return player.getScoreboard();
    }

    /* ========================================================= */
    /* ==================== SCOREBOARD UPDATE ================== */
    /* ========================================================= */

    protected void updateScoreboard(Scoreboard scoreboard, List<String> lines)
            throws ScoreboardLineTooLongException {
        updateInternal(scoreboard, lines, true);
    }

    protected void updateLines(Scoreboard scoreboard, List<String> lines)
            throws ScoreboardLineTooLongException {
        updateInternal(scoreboard, lines, false);
    }

    private void updateInternal(Scoreboard scoreboard,
                                List<String> input,
                                boolean updateTitle)
            throws ScoreboardLineTooLongException {

        Objective objective = objectiveWrapper.getDummyObjective(scoreboard);

        if (updateTitle) {
            objective.displayName(
                    Optional.ofNullable(getTitle(scoreboard))
                            .orElse(Component.empty())
            );
        }

        List<String> lines = new ArrayList<>(
                Optional.ofNullable(input).orElse(Collections.emptyList())
        );

        List<String> previous = previousLinesMap.get(scoreboard);

        if (previous != null && previous.equals(lines)) {
            updateTeams(scoreboard);
            return;
        }

        if (previous != null && previous.size() != lines.size()) {
            resetScoreboard(scoreboard);
        }

        previousLinesMap.put(scoreboard, new ArrayList<>(lines));

        if (lines.isEmpty()) {
            lines.add("");
        }

        Collections.reverse(lines);

        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        configureHealthObjectives(scoreboard);

        renderLines(scoreboard, objective, lines);
    }

    /* ========================================================= */
    /* ===================== LINE RENDERING ==================== */
    /* ========================================================= */

    private void renderLines(Scoreboard scoreboard,
                             Objective objective,
                             List<String> lines)
            throws ScoreboardLineTooLongException {

        for (int i = 0; i < lines.size(); i++) {

            String text = lines.get(i);

            if (text.length() > MAX_LINE_LENGTH) {
                throw new ScoreboardLineTooLongException(text, MAX_LINE_LENGTH);
            }

            int score = i + 1;
            String teamName = "line" + score;
            String entry = getInvisibleEntry(score);

            Team team = scoreboard.getTeam(teamName);

            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
                team.addEntry(entry);
                objective.getScore(entry).setScore(score);
            }

            applyText(team, text);
        }

        cleanupExtraLines(scoreboard, lines.size());
    }

    private void applyText(Team team, String text) {

        Component prefix;
        Component suffix = Component.empty();

        if (text.length() <= SPLIT_LIMIT) {
            prefix = MINI.deserialize(text);
        } else {
            prefix = MINI.deserialize(text.substring(0, SPLIT_LIMIT));
            suffix = MINI.deserialize(text.substring(SPLIT_LIMIT));
        }

        team.prefix(prefix);
        team.suffix(suffix);
    }

    private void cleanupExtraLines(Scoreboard scoreboard, int maxLines) {

        for (Team team : scoreboard.getTeams()) {

            String name = team.getName();
            if (!name.startsWith("line")) continue;

            try {
                int index = Integer.parseInt(name.substring(4));
                if (index > maxLines) {
                    team.unregister();
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void resetScoreboard(Scoreboard scoreboard) {

        scoreboard.clearSlot(DisplaySlot.SIDEBAR);
        scoreboard.getEntries().forEach(scoreboard::resetScores);

        scoreboard.getTeams().forEach(team -> {
            if (team.getName().startsWith("line")) {
                team.unregister();
            }
        });
    }

    private void configureHealthObjectives(Scoreboard scoreboard) {

        if (options == null) return;

        Objective tab = objectiveWrapper.getTabHealthObjective(
                options.getTabHealthStyle().toWrapped(),
                scoreboard
        );

        if (options.getTabHealthStyle() != JScoreboardTabHealthStyle.NONE) {
            if (tab != null) tab.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        } else if (tab != null) {
            tab.unregister();
        }

        Objective below = objectiveWrapper.getNameHealthObjective(scoreboard);

        if (options.shouldShowHealthUnderName()) {
            if (below != null) below.setDisplaySlot(DisplaySlot.BELOW_NAME);
        } else if (below != null) {
            below.unregister();
        }
    }

    private void updateTeams(Scoreboard scoreboard) {
        teams.forEach(team -> team.refresh(scoreboard));
    }

    private String stripColor(String component) {
        return PlainTextComponentSerializer.plainText().serialize(
                LegacyComponentSerializer.legacySection().deserialize(component)
        );
    }

    private String getInvisibleEntry(int index) {
        return "§" + Integer.toHexString(index);
    }
}