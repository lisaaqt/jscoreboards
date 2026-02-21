package dev.lisaa.jscoreboards.impl;

import dev.lisaa.jscoreboards.JScoreboard;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * @author Lisa Kapahnke
 * @project jscoreboards
 * @created 21.02.2026 - 16:59
 * <p>
 * You are not allowed to modify or make changes to
 * this file without permission.
 **/
public class SimpleChatEventImplementation implements Listener {

    private final JScoreboard scoreboard;
    private final Component errorComponent;

    public SimpleChatEventImplementation(JScoreboard scoreboard, Component errorComponent) {
        this.scoreboard = scoreboard;
        this.errorComponent = errorComponent;
    }

    @EventHandler
    public void handle(AsyncChatEvent event) {
        var player = event.getPlayer();
        var team = scoreboard.getTeamOf(player).orElse(null);

        if (team == null) {
            event.setCancelled(true);
            if (errorComponent != null)
                player.sendMessage(errorComponent);
            return;
        }
        event.renderer((source, sourceDisplayName, message, viewer) ->
                Component.text()
                        .append(team.getDisplayName())
                        .append(player.displayName())
                        .append(Component.text(" › ").color(NamedTextColor.DARK_GRAY))
                        .append(message).color(NamedTextColor.GRAY).build()
        );
    }
}
