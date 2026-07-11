package com.talexck.gameVoting.velocity;

import java.util.Optional;
import java.util.UUID;

final class ClientVersionResolver {
  @FunctionalInterface
  interface ViaProtocolLookup {
    ViaProtocol find(UUID playerId);
  }

  record ViaProtocol(int originalProtocol, String versionName) {}

  private ClientVersionResolver() {}

  static Optional<String> resolve(
      UUID playerId, int velocityProtocol, ViaProtocolLookup viaProtocolLookup) {
    try {
      ViaProtocol viaProtocol = viaProtocolLookup.find(playerId);
      if (viaProtocol != null && viaProtocol.originalProtocol() >= 0) {
        String versionName = viaProtocol.versionName();
        if (versionName != null
            && !versionName.isBlank()
            && !versionName.equalsIgnoreCase("unknown")) {
          return Optional.of(versionName);
        }
        String mapped = ProtocolVersionMapper.versionName(viaProtocol.originalProtocol());
        if (mapped != null) {
          return Optional.of(mapped);
        }
      }
    } catch (RuntimeException | LinkageError ignored) {
    }
    return Optional.ofNullable(ProtocolVersionMapper.versionName(velocityProtocol));
  }
}
