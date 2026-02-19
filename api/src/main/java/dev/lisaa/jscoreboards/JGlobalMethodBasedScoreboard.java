package dev.lisaa.jscoreboards;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class JGlobalMethodBasedScoreboard extends JGlobalScoreboard {
  private Component title = Component.empty();
  private List<String> lines = new ArrayList<>();

  public JGlobalMethodBasedScoreboard(JScoreboardOptions options) {
    super(options);

    setTitleSupplier(() -> title);
    setLinesSupplier(() -> lines);
  }

  public JGlobalMethodBasedScoreboard() {
    this(JScoreboardOptions.defaultOptions);
  }

  public void setTitle(Component title) {
    this.title = title;
    updateScoreboard();
  }

  public void setLines(List<String> lines) {
    this.lines = lines;
    updateScoreboard();
  }

  public void setLines(String... lines) {
    setLines(Arrays.asList(lines));
  }
}
