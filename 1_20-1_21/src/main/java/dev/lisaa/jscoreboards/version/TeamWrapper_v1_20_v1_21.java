package dev.lisaa.jscoreboards.version;

import dev.lisaa.jscoreboards.abstraction.InternalTeamWrapper;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.scoreboard.Team;

public class TeamWrapper_v1_20_v1_21 extends InternalTeamWrapper {
    @Override
    public void setColor(Team team, NamedTextColor color) {
        team.color(color);
    }
}
