package dev.lisaa.jscoreboards.versioning;

import dev.lisaa.jscoreboards.abstraction.InternalObjectiveWrapper;
import dev.lisaa.jscoreboards.abstraction.InternalTeamWrapper;
import dev.lisaa.jscoreboards.version.*;
import org.bukkit.Bukkit;

import java.lang.reflect.InvocationTargetException;

public enum SpigotAPIVersion {

  PAPER_1_20(ObjectiveWrapper_v1_20_v1_21.class, TeamWrapper_v1_20_v1_21.class, 12),
  PAPER_1_21(ObjectiveWrapper_v1_20_v1_21.class, TeamWrapper_v1_20_v1_21.class, 13);

  SpigotAPIVersion(
      Class<? extends InternalObjectiveWrapper> internalObjectiveWrapperClass,
      Class<? extends InternalTeamWrapper> internalTeamWrapperClass,
      int index
  ) {
    this.internalObjectiveWrapperClass = internalObjectiveWrapperClass;
    this.internalTeamWrapperClass = internalTeamWrapperClass;
    this.index = index;
  }

  final Class<? extends InternalObjectiveWrapper> internalObjectiveWrapperClass;
  final Class<? extends InternalTeamWrapper> internalTeamWrapperClass;
  final int index;

  /**
   * The current server version this server is running on.
   *
   * If it's not a part of this enum, it will default to using the last version in the enum.
   */
  private static final SpigotAPIVersion current;

  public static SpigotAPIVersion getCurrent() {
    return current;
  }

  public boolean lessThan(SpigotAPIVersion otherVersion) {
    return this.index < otherVersion.index;
  }

  static {
    String mcVersion = Bukkit.getMinecraftVersion(); // 1.21.1

    String[] split = mcVersion.split("\\.");
    String enumName = "PAPER_" + split[0] + "_" + split[1];

    SpigotAPIVersion versionToAssign;

    try {
      versionToAssign = SpigotAPIVersion.valueOf(enumName);
    } catch (IllegalArgumentException ignored) {
      versionToAssign = SpigotAPIVersion.values()[SpigotAPIVersion.values().length - 1];

      Bukkit.getLogger().warning("=================================");
      Bukkit.getLogger().warning("Unsupported Paper version: " + mcVersion);
      Bukkit.getLogger().warning("Defaulting to latest supported version.");
      Bukkit.getLogger().warning("=================================");
    }

    current = versionToAssign;
  }

  public InternalObjectiveWrapper makeObjectiveWrapper() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return internalObjectiveWrapperClass.getDeclaredConstructor().newInstance();
  }

  public InternalTeamWrapper makeInternalTeamWrapper() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return internalTeamWrapperClass.getDeclaredConstructor().newInstance();
  }
}
