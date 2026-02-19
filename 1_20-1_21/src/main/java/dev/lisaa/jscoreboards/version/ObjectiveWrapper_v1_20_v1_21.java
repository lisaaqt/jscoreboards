package dev.lisaa.jscoreboards.version;

import dev.lisaa.jscoreboards.abstraction.InternalObjectiveWrapper;
import dev.lisaa.jscoreboards.abstraction.WrappedHealthStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;

public final class ObjectiveWrapper_v1_20_v1_21 extends InternalObjectiveWrapper {
  @Override
  public Objective getNameHealthObjective(Scoreboard scoreboard) {
    Objective healthObjective = scoreboard.getObjective("nameHealth");
    if (healthObjective == null) {
      healthObjective = scoreboard.registerNewObjective(
          "nameHealth",
          Criteria.HEALTH,
          LegacyComponentSerializer.legacy('&').deserialize("&c❤")
      );
    }
    return healthObjective;
  }

  @Override
  public Objective getTabHealthObjective(WrappedHealthStyle wrappedHealthStyle, Scoreboard scoreboard) {
    Objective healthObjective = scoreboard.getObjective("tabHealth");
    if (healthObjective == null) {
      healthObjective = scoreboard.registerNewObjective(
          "tabHealth",
          Criteria.HEALTH,
          Component.text("health"),
          wrappedHealthStyle == WrappedHealthStyle.HEARTS ? RenderType.HEARTS : RenderType.INTEGER
      );
    }
    return healthObjective;
  }

  @Override
  public Objective getDummyObjective(Scoreboard scoreboard) {
    Objective objective = scoreboard.getObjective("dummy");
    if (objective == null) {
      objective = scoreboard.registerNewObjective("dummy", Criteria.DUMMY, Component.text("dummy"));
    }
    return objective;
  }
}
