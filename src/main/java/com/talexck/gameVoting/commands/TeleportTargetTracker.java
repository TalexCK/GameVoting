package com.talexck.gameVoting.commands;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

final class TeleportTargetTracker {
  private final Set<UUID> targets = new LinkedHashSet<>();

  synchronized void capture(Collection<UUID> playerIds) {
    targets.clear();
    targets.addAll(playerIds);
  }

  synchronized void remove(UUID playerId) {
    targets.remove(playerId);
  }

  synchronized List<UUID> drainOnline(Predicate<UUID> onlinePlayer) {
    List<UUID> result = targets.stream().filter(onlinePlayer).toList();
    targets.clear();
    return result;
  }

  synchronized int size() {
    return targets.size();
  }

  synchronized void clear() {
    targets.clear();
  }
}
