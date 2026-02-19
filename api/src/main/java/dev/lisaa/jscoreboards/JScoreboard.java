package dev.lisaa.jscoreboards;

import dev.lisaa.jscoreboards.abstraction.InternalObjectiveWrapper;
import dev.lisaa.jscoreboards.abstraction.InternalTeamWrapper;
import dev.lisaa.jscoreboards.exception.DuplicateTeamCreatedException;
import dev.lisaa.jscoreboards.exception.ScoreboardLineTooLongException;
import dev.lisaa.jscoreboards.exception.ScoreboardTeamNameTooLongException;
import dev.lisaa.jscoreboards.versioning.SpigotAPIVersion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public abstract class JScoreboard {
    private JScoreboardOptions options;

    private InternalObjectiveWrapper objectiveWrapper;
    private InternalTeamWrapper teamWrapper;

    private final List<JScoreboardTeam> teams = new ArrayList<>();
    private final List<UUID> activePlayers = new ArrayList<>();

    private final Map<Scoreboard, List<String>> previousLinesMap = new HashMap<>();

    private final int maxLineLength;

    public JScoreboard() {
        try {
            objectiveWrapper = SpigotAPIVersion.getCurrent().makeObjectiveWrapper();
            teamWrapper = SpigotAPIVersion.getCurrent().makeInternalTeamWrapper();
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException |
                 InstantiationException e) {
            e.printStackTrace();
            Bukkit.getLogger().severe("Failed to initialize JScoreboards- please send the full stacktrace above to https://github.com/JordanOsterberg/JScoreboards. If you are using someone else's plugin instead of developing your own, report this issue to them.");
        }
        maxLineLength = 128;
    }

// MARK: Public API

    /**
     * Add a player to the scoreboard
     *
     * @param player The player to add
     */
    public void addPlayer(Player player) {
        if (activePlayers.contains(player.getUniqueId())) return;

        this.activePlayers.add(player.getUniqueId());
    }

    /**
     * Remove the player from the dev.lisaa.jscoreboards.JScoreboard, and remove any teams they may be a member of.
     * This will reset their scoreboard to the server's main scoreboard.
     *
     * @param player The player to remove
     */
    public void removePlayer(Player player) {
        this.activePlayers.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

        teams.forEach(team -> {
            if (team.isOnTeam(player.getUniqueId())) {
                team.removePlayer(player);
            }
        });
    }

    /**
     * Find a team using a name
     *
     * @param name The name to search for. Color codes will be stripped from both the team name and this variable.
     * @return The JScoreboardPlayerTeam found, if any. Will return null if no team exists
     */
    public Optional<JScoreboardTeam> findTeam(String name) {
        return teams.stream()
                .filter(team -> stripColor(team.getName()).equalsIgnoreCase(stripColor(name)))
                .findAny();
    }

    /**
     * Create a team on the scoreboard. ChatColor.WHITE is used as the color for the team.
     *
     * @param name The name for the new team. This name cannot be longer than 16 characters
     * @return The created JScoreboardPlayerTeam
     * @throws DuplicateTeamCreatedException      If a team with that name already exists
     * @throws ScoreboardTeamNameTooLongException If the team's name is longer than 16 characters
     */
    public JScoreboardTeam createTeam(String name, Component displayName) throws DuplicateTeamCreatedException, ScoreboardTeamNameTooLongException {
        return createTeam(name, displayName, NamedTextColor.WHITE);
    }

    /**
     * Create a team on the scoreboard.
     *
     * @param name The name for the new team. This name cannot be longer than 16 characters
     * @return The created JScoreboardPlayerTeam
     * @throws DuplicateTeamCreatedException      If a team with that name already exists
     * @throws ScoreboardTeamNameTooLongException If the team's name is longer than 16 characters
     */
    public JScoreboardTeam createTeam(String name, Component displayName, NamedTextColor teamColor) throws DuplicateTeamCreatedException, ScoreboardTeamNameTooLongException {
        for (JScoreboardTeam team : this.teams) {
            if (stripColor(team.getName()).equalsIgnoreCase(stripColor(name))) {
                throw new DuplicateTeamCreatedException(name);
            }
        }

        if (name.length() > 16) {
            throw new ScoreboardTeamNameTooLongException(name);
        }

        JScoreboardTeam team = new JScoreboardTeam(name, displayName, teamColor, this);
        team.refresh();
        this.teams.add(team);
        return team;
    }

    /**
     * Remove a team from the scoreboard
     *
     * @param team The team to remove from the scoreboard
     */
    public void removeTeam(JScoreboardTeam team) {
        if (team.getScoreboard() != this) return;

        team.destroy();
        this.teams.remove(team);
    }

    /**
     * Destroy the scoreboard. This will reset all players to the server's main scoreboard, and clear all teams.
     * You should call this method inside of your plugin's onDisable method.
     */
    public void destroy() {
        for (UUID playerUUID : activePlayers) {
            Player player = Bukkit.getPlayer(playerUUID);

            if (player != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }

        for (JScoreboardTeam team : teams) {
            team.destroy();
        }

        this.activePlayers.clear();
        this.teams.clear();
    }

    /**
     * Get the teams registered on the Scoreboard
     *
     * @return The teams registered on the scoreboard
     */
    public List<JScoreboardTeam> getTeams() {
        return teams;
    }

    /**
     * Get the options for the Scoreboard.
     * If changed directly, updateScoreboard() must be called manually.
     *
     * @return The options for the scoreboard
     */
    public JScoreboardOptions getOptions() {
        return options;
    }

// MARK: Private API

    /**
     * Update a scoreboard with a list of lines
     * These lines must be in reverse order!
     *
     * @throws ScoreboardLineTooLongException If a String within the lines array is over 64 characters, this dev.lisaa.jscoreboards.exception is thrown.
     */
    protected void updateScoreboard(Scoreboard scoreboard, List<String> lines) throws ScoreboardLineTooLongException {
        Objective objective = objectiveWrapper.getDummyObjective(scoreboard);

        Component title = getTitle(scoreboard);
        if (title == null) {
            title = Component.empty();
        }

        objective.displayName(title);

        if (lines == null) {
            lines = new ArrayList<>();
        }

        if (previousLinesMap.containsKey(scoreboard)) {
            if (previousLinesMap.get(scoreboard).equals(lines)) { // Are the lines the same? Don't take up server resources to change absolutely nothing
                updateTeams(scoreboard); // Update the teams anyway
                return;
            }

            // Size difference means unregister objective to reset and re-register teams correctly
            if (previousLinesMap.get(scoreboard).size() != lines.size()) {
                scoreboard.clearSlot(DisplaySlot.SIDEBAR);
                scoreboard.getEntries().forEach(scoreboard::resetScores);
                scoreboard.getTeams().forEach(team -> {
                    if (team.getName().contains("line")) {
                        team.unregister();
                    }
                });
            }
        }

        // This is a copy instead of reference to prevent previousLinesMap equality check from unexpectedly failing
        previousLinesMap.put(scoreboard, new ArrayList<>(lines));

        // Make sure we have at least one line to display
        if (lines.isEmpty()) {
            lines.add("");
        }

        List<String> reversedLines = new ArrayList<>(lines);
        Collections.reverse(reversedLines);

        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Objective healthObjective;

        if (options.getTabHealthStyle() != JScoreboardTabHealthStyle.NONE) {
            healthObjective = objectiveWrapper.getTabHealthObjective(options.getTabHealthStyle().toWrapped(), scoreboard);
            healthObjective.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        } else {
            healthObjective = objectiveWrapper.getTabHealthObjective(options.getTabHealthStyle().toWrapped(), scoreboard);
            if (healthObjective != null) {
                healthObjective.unregister();
            }
        }

        if (options.shouldShowHealthUnderName()) {
            healthObjective = objectiveWrapper.getNameHealthObjective(scoreboard);
            healthObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        } else {
            healthObjective = objectiveWrapper.getNameHealthObjective(scoreboard);
            if (healthObjective != null) {
                healthObjective.unregister();
            }
        }

        // Clear any existing teams to start fresh
        scoreboard.getTeams().forEach(team -> {
            if (team.getName().startsWith("line")) {
                team.unregister();
            }
        });

        int score = 1;

        for (String entry : reversedLines) {
            if (entry.length() > maxLineLength) {
                throw new ScoreboardLineTooLongException(entry, maxLineLength);
            }

            // Create a unique entry name that won't be visible
            String entryName = getInvisibleEntry(score);

            Team team = scoreboard.registerNewTeam("line" + score);
            team.addEntry(entryName);
            objective.getScore(entryName).setScore(score);

            Component prefix;
            Component suffix = Component.empty();

            int cutoff = 64;
            if (entry.length() <= cutoff) {
                prefix = MiniMessage.miniMessage().deserialize(entry);
            } else {
                prefix = MiniMessage.miniMessage().deserialize(entry.substring(0, cutoff));
                suffix = MiniMessage.miniMessage().deserialize(entry.substring(cutoff));
            }

            team.prefix(prefix);
            team.suffix(suffix);

            // Hide the team entry from display
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);

            score += 1;
        }

        updateTeams(scoreboard);
    }

    /**
     * Update the teams on the scoreboard. Loops over all teams and calls refresh(Scoreboard)
     *
     * @param scoreboard The Bukkit scoreboard to use
     */
    private void updateTeams(Scoreboard scoreboard) {
        this.teams.forEach(team -> team.refresh(scoreboard));
    }

    private String stripColor(String component) {
        return PlainTextComponentSerializer.plainText().serialize(LegacyComponentSerializer.legacySection().deserialize(component));
    }

    protected List<UUID> getActivePlayers() {
        return activePlayers;
    }

    protected InternalObjectiveWrapper getObjectiveWrapper() {
        return objectiveWrapper;
    }

    protected InternalTeamWrapper getTeamWrapper() {
        return teamWrapper;
    }

    /**
     * Set the options of the scoreboard
     *
     * @param options The options
     */
    protected void setOptions(JScoreboardOptions options) {
        this.options = options;
    }

    protected abstract Component getTitle(Scoreboard scoreboard);

    private String getInvisibleEntry(int index) {
        return "§" + Integer.toHexString(index);
    }
}