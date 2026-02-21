package dev.lisaa.jscoreboards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;

public class JScoreboardOptions {

  private final JScoreboardTabHealthStyle tabHealthStyle;
  private final boolean showHealthUnderName;

  protected JScoreboardOptions(JScoreboardTabHealthStyle tabHealthStyle, boolean showHealthUnderName) {
    this.tabHealthStyle = tabHealthStyle;
    this.showHealthUnderName = showHealthUnderName;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private JScoreboardTabHealthStyle tabHealthStyle;
    private boolean showHealthUnderName;

    public Builder tabHealthStyle(JScoreboardTabHealthStyle tabHealthStyle) {
      this.tabHealthStyle = tabHealthStyle;
      return this;
    }

    public Builder showHealthUnderName(boolean showHealthUnderName) {
      this.showHealthUnderName = showHealthUnderName;
      return this;
    }

    public JScoreboardOptions build() {
      return new JScoreboardOptions(this.tabHealthStyle, this.showHealthUnderName);
    }
  }

  public static JScoreboardOptions DEFAULT_OPTIONS = new JScoreboardOptions(JScoreboardTabHealthStyle.NONE, false);

  public JScoreboardTabHealthStyle getTabHealthStyle() {
    return this.tabHealthStyle;
  }

  public boolean shouldShowHealthUnderName() {
    return this.showHealthUnderName;
  }
}