package dev.lisaa.jscoreboards;

import dev.lisaa.jscoreboards.exception.ScoreboardTeamNameTooLongException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JScoreboardTeam {

    private final String name;
    private final String internalName;
    private final int weight;
    private final Component displayName;
    @Nullable
    private final NamedTextColor teamColor;
    private final Set<UUID> entities = new HashSet<>();
    private final JScoreboard scoreboard;
    private final Map<Team.Option, Team.OptionStatus> options;

    protected JScoreboardTeam(
            @NotNull String name,
            int weight,
            @NotNull Component displayName,
            @Nullable NamedTextColor teamColor,
            @NotNull JScoreboard scoreboard,
            @Nullable Map<Team.Option, Team.OptionStatus> options
    ) {
        this.weight = weight;
        this.name = formatWeight(weight) + name;
        this.internalName = name;
        this.displayName = displayName;
        this.teamColor = teamColor;
        this.scoreboard = scoreboard;
        this.options = options == null
                ? Collections.emptyMap()
                : new HashMap<>(options);
    }

    public static Builder builder() {
        return new Builder();
    }

    /* ========================================================= */
    /* ========================== BUILDER ====================== */
    /* ========================================================= */

    public static class Builder {

        private String name;
        private int weight;
        private Component displayName = Component.empty();
        private NamedTextColor teamColor;
        private JScoreboard scoreboard;
        private Map<Team.Option, Team.OptionStatus> options;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }

        public Builder displayName(Component displayName) {
            this.displayName = displayName == null ? Component.empty() : displayName;
            return this;
        }

        public Builder color(NamedTextColor color) {
            this.teamColor = color;
            return this;
        }

        public Builder scoreboard(JScoreboard scoreboard) {
            this.scoreboard = scoreboard;
            return this;
        }

        public Builder options(Map<Team.Option, Team.OptionStatus> options) {
            this.options = options;
            return this;
        }

        @NotNull
        public JScoreboardTeam build() {

            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Team name cannot be null or empty");
            }

            if (name.length() > 16) {
                throw new ScoreboardTeamNameTooLongException("Team name cannot be longer than 16 characters");
            }

            if (weight < 0 || weight > 999) {
                throw new IllegalArgumentException("Weight must be between 0 and 999");
            }

            if (scoreboard == null) {
                throw new IllegalStateException("Scoreboard cannot be null");
            }

            return new JScoreboardTeam(
                    name,
                    weight,
                    displayName,
                    teamColor,
                    scoreboard,
                    options
            );
        }
    }

    /* ========================================================= */
    /* ======================== REFRESH ======================== */
    /* ========================================================= */

    public void refresh() {

        if (scoreboard instanceof JPerPlayerScoreboard perPlayer) {
            for (UUID uuid : perPlayer.getActivePlayers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    refresh(player.getScoreboard());
                }
            }
        } else if (scoreboard instanceof JGlobalScoreboard global) {
            refresh(global.toBukkitScoreboard());
        }
    }

    public void refresh(Scoreboard board) {

        if (board == null) return;

        Team team = toBukkitTeam(board);
        if (team == null) return;

        for (UUID uuid : entities) {

            Player player = Bukkit.getPlayer(uuid);

            String entry = player != null
                    ? player.getName()
                    : uuid.toString();

            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
        }

        if (teamColor != null) {
            scoreboard.getTeamWrapper().setColor(team, teamColor);
        }

        team.prefix(displayName);
    }

    /* ========================================================= */
    /* ======================= TEAM ACCESS ===================== */
    /* ========================================================= */

    public Team toBukkitTeam(Scoreboard board) {

        if (board == null) return null;

        Team team = board.getTeam(name);

        if (team == null) {
            team = board.registerNewTeam(name);

            if (options != null && !options.isEmpty()) {
                options.forEach(team::setOption);
            }
        }

        return team;
    }

    /* ========================================================= */
    /* ======================= ENTITY MGMT ===================== */
    /* ========================================================= */

    public void addPlayer(Player player) {
        addEntity(player.getUniqueId());
    }

    public void addEntity(Entity entity) {
        addEntity(entity.getUniqueId());
    }

    public void addEntity(UUID uuid) {
        if (entities.add(uuid)) {
            refresh();
        }
    }

    public void removePlayer(Player player) {
        removeEntity(player.getUniqueId(), player.getName());
    }

    public void removeEntity(Entity entity) {
        removeEntity(entity.getUniqueId(), entity.getUniqueId().toString());
    }

    private void removeEntity(UUID uuid, String entry) {

        if (!entities.remove(uuid)) return;

        if (scoreboard instanceof JPerPlayerScoreboard perPlayer) {

            for (UUID scoreboardPlayer : perPlayer.getActivePlayers()) {

                Player player = Bukkit.getPlayer(scoreboardPlayer);
                if (player == null) continue;

                Team team = player.getScoreboard().getTeam(name);
                if (team != null) {
                    team.removeEntry(entry);
                }
            }

        } else if (scoreboard instanceof JGlobalScoreboard global) {

            Team team = global.toBukkitScoreboard().getTeam(name);
            if (team != null) {
                team.removeEntry(entry);
            }
        }

        refresh();
    }

    /* ========================================================= */
    /* ========================== DESTROY ====================== */
    /* ========================================================= */

    public void destroy() {

        if (scoreboard instanceof JPerPlayerScoreboard perPlayer) {

            for (UUID uuid : perPlayer.getActivePlayers()) {

                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;

                Team team = player.getScoreboard().getTeam(name);
                if (team != null) {
                    team.unregister();
                }
            }

        } else if (scoreboard instanceof JGlobalScoreboard global) {

            Team team = global.toBukkitScoreboard().getTeam(name);
            if (team != null) {
                team.unregister();
            }
        }

        entities.clear();
    }

    /* ========================================================= */
    /* ========================== GETTERS ====================== */
    /* ========================================================= */

    public String getName() {
        return name;
    }

    public String getInternalName() {
        return internalName;
    }

    public int getWeight() {
        return weight;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public @Nullable NamedTextColor getTeamColor() {
        return teamColor;
    }

    public JScoreboard getScoreboard() {
        return scoreboard;
    }

    public Set<UUID> getEntities() {
        return Collections.unmodifiableSet(entities);
    }

    public boolean isOnTeam(UUID uuid) {
        return entities.contains(uuid);
    }

    public Map<Team.Option, Team.OptionStatus> getOptions() {
        return options;
    }

    private String formatWeight(int weight) {
        return String.format("%03d", weight);
    }
}