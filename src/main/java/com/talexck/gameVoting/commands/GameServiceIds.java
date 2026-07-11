package com.talexck.gameVoting.commands;

import com.schedulerbridge.common.ServerInstance;
import com.schedulerbridge.common.ServerInstanceState;
import java.util.List;
import java.util.Optional;

final class GameServiceIds {
  private GameServiceIds() {}

  static String display(ServerInstance instance) {
    return instance.serverId() + "-1";
  }

  static boolean isControllable(ServerInstance instance) {
    return instance.state() == ServerInstanceState.STARTING
        || instance.state() == ServerInstanceState.READY;
  }

  static Optional<ServerInstance> resolve(List<ServerInstance> instances, String serviceId) {
    return instances.stream()
        .filter(GameServiceIds::isControllable)
        .filter(instance -> display(instance).equalsIgnoreCase(serviceId))
        .findFirst();
  }
}
