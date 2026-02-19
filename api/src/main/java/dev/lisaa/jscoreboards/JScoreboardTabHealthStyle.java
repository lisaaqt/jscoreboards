package dev.lisaa.jscoreboards;

import dev.lisaa.jscoreboards.abstraction.WrappedHealthStyle;

public enum JScoreboardTabHealthStyle {
  NONE,
  HEARTS,
  NUMBER;

  public WrappedHealthStyle toWrapped() {
    return switch (this) {
      case HEARTS -> WrappedHealthStyle.HEARTS;
      case NONE -> WrappedHealthStyle.NONE;
      case NUMBER -> WrappedHealthStyle.NUMBER;
    };

  }
}