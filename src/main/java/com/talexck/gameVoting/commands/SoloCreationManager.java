package com.talexck.gameVoting.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SoloCreationManager {
  public enum ResolutionStatus {
    ACCEPTED,
    DECLINED,
    NOT_FOUND,
    EXPIRED,
    INVALID_TARGET,
    DRAFT_MISSING,
    ALREADY_RESERVED,
    PENDING
  }

  public record DraftView(
      UUID owner,
      String gameId,
      UUID pendingPlayer,
      UUID acceptedPlayer,
      UUID inviteToken) {}

  public record Invite(UUID token, UUID owner, UUID target, String gameId, long expiresAt) {}

  public record Resolution(ResolutionStatus status, Invite invite, DraftView draft) {}

  private static final class Draft {
    private final UUID owner;
    private final String gameId;
    private UUID pendingPlayer;
    private UUID acceptedPlayer;
    private UUID inviteToken;

    private Draft(UUID owner, String gameId) {
      this.owner = owner;
      this.gameId = gameId;
    }
  }

  private record ReservationKey(String gameId, UUID player) {}

  private final Map<UUID, Draft> drafts = new HashMap<>();
  private final Map<UUID, Invite> invites = new HashMap<>();
  private final Map<ReservationKey, UUID> reservations = new HashMap<>();

  public synchronized DraftView begin(UUID owner, String gameId) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(gameId, "gameId");
    Draft current = drafts.get(owner);
    if (current != null && current.gameId.equalsIgnoreCase(gameId)) {
      return view(current);
    }
    UUID reservedBy = reservations.get(key(gameId, owner));
    if (reservedBy != null && !reservedBy.equals(owner)) {
      throw new IllegalStateException("player is reserved by another solo creation draft");
    }
    clearOwner(owner);
    Draft created = new Draft(owner, gameId);
    drafts.put(owner, created);
    reservations.put(key(gameId, owner), owner);
    return view(created);
  }

  public synchronized Optional<DraftView> draft(UUID owner, String gameId) {
    Draft current = drafts.get(owner);
    if (current == null || !current.gameId.equalsIgnoreCase(gameId)) {
      return Optional.empty();
    }
    return Optional.of(view(current));
  }

  public synchronized Optional<DraftView> draft(UUID owner) {
    Draft current = drafts.get(owner);
    return current == null ? Optional.empty() : Optional.of(view(current));
  }

  public synchronized Invite invite(
      UUID owner, String gameId, UUID target, long now, long timeoutMillis) {
    if (owner.equals(target)) {
      throw new IllegalArgumentException("solo invite target must differ from owner");
    }
    Draft draft = requireDraft(owner, gameId);
    clearSelection(draft);
    UUID token = UUID.randomUUID();
    Invite invite = new Invite(token, owner, target, draft.gameId, now + timeoutMillis);
    draft.pendingPlayer = target;
    draft.inviteToken = token;
    invites.put(token, invite);
    return invite;
  }

  public synchronized Resolution accept(UUID token, UUID target, long now) {
    Resolution inspected = inspect(token, target, now);
    if (inspected.status() != ResolutionStatus.PENDING) {
      return inspected;
    }
    Invite invite = inspected.invite();
    Draft draft = drafts.get(invite.owner());
    UUID reservedBy = reservations.get(key(invite.gameId(), target));
    if (reservedBy != null && !reservedBy.equals(invite.owner())) {
      return new Resolution(ResolutionStatus.ALREADY_RESERVED, invite, view(draft));
    }
    invites.remove(token);
    draft.pendingPlayer = null;
    draft.inviteToken = null;
    draft.acceptedPlayer = target;
    reservations.put(key(invite.gameId(), target), invite.owner());
    return new Resolution(ResolutionStatus.ACCEPTED, invite, view(draft));
  }

  public synchronized Resolution inspect(UUID token, UUID target, long now) {
    Invite invite = invites.get(token);
    if (invite == null) {
      return new Resolution(ResolutionStatus.NOT_FOUND, null, null);
    }
    if (!invite.target().equals(target)) {
      return new Resolution(ResolutionStatus.INVALID_TARGET, invite, null);
    }
    if (invite.expiresAt() <= now) {
      clearInvite(invite);
      return new Resolution(ResolutionStatus.EXPIRED, invite, null);
    }
    Draft draft = drafts.get(invite.owner());
    if (draft == null
        || !draft.gameId.equalsIgnoreCase(invite.gameId())
        || !token.equals(draft.inviteToken)) {
      invites.remove(token);
      return new Resolution(ResolutionStatus.DRAFT_MISSING, invite, null);
    }
    return new Resolution(ResolutionStatus.PENDING, invite, view(draft));
  }

  public synchronized Resolution decline(UUID token, UUID target, long now) {
    Invite invite = invites.get(token);
    if (invite == null) {
      return new Resolution(ResolutionStatus.NOT_FOUND, null, null);
    }
    if (!invite.target().equals(target)) {
      return new Resolution(ResolutionStatus.INVALID_TARGET, invite, null);
    }
    if (invite.expiresAt() <= now) {
      clearInvite(invite);
      return new Resolution(ResolutionStatus.EXPIRED, invite, null);
    }
    Draft draft = drafts.get(invite.owner());
    clearInvite(invite);
    return new Resolution(
        draft == null ? ResolutionStatus.DRAFT_MISSING : ResolutionStatus.DECLINED,
        invite,
        draft == null ? null : view(draft));
  }

  public synchronized Optional<Invite> expire(UUID token, long now) {
    Invite invite = invites.get(token);
    if (invite == null || invite.expiresAt() > now) {
      return Optional.empty();
    }
    clearInvite(invite);
    return Optional.of(invite);
  }

  public synchronized Optional<Invite> invalidate(UUID token) {
    Invite invite = invites.get(token);
    if (invite == null) {
      return Optional.empty();
    }
    clearInvite(invite);
    return Optional.of(invite);
  }

  public synchronized DraftView clearSelection(UUID owner, String gameId) {
    Draft draft = requireDraft(owner, gameId);
    clearSelection(draft);
    return view(draft);
  }

  public synchronized List<UUID> roster(UUID owner, String gameId) {
    Draft draft = requireDraft(owner, gameId);
    List<UUID> players = new ArrayList<>();
    players.add(owner);
    if (draft.acceptedPlayer != null) {
      players.add(draft.acceptedPlayer);
    }
    return List.copyOf(players);
  }

  public synchronized void clearOwner(UUID owner) {
    clearOwnerAndGetInvite(owner);
  }

  public synchronized Optional<Invite> clearOwnerAndGetInvite(UUID owner) {
    Draft draft = drafts.remove(owner);
    if (draft == null) {
      return Optional.empty();
    }
    Invite invite = draft.inviteToken == null ? null : invites.remove(draft.inviteToken);
    reservations.remove(key(draft.gameId, draft.owner), draft.owner);
    if (draft.acceptedPlayer != null) {
      reservations.remove(key(draft.gameId, draft.acceptedPlayer), draft.owner);
    }
    return Optional.ofNullable(invite);
  }

  public synchronized List<DraftView> clearTarget(UUID target) {
    List<DraftView> changed = new ArrayList<>();
    Iterator<Invite> iterator = invites.values().iterator();
    while (iterator.hasNext()) {
      Invite invite = iterator.next();
      if (!invite.target().equals(target)) {
        continue;
      }
      Draft draft = drafts.get(invite.owner());
      if (draft != null && invite.token().equals(draft.inviteToken)) {
        draft.pendingPlayer = null;
        draft.inviteToken = null;
        changed.add(view(draft));
      }
      iterator.remove();
    }
    return List.copyOf(changed);
  }

  public synchronized List<DraftView> acceptedDrafts(UUID target) {
    return drafts.values().stream()
        .filter(draft -> target.equals(draft.acceptedPlayer))
        .map(SoloCreationManager::view)
        .toList();
  }

  public synchronized boolean isReserved(String gameId, UUID player) {
    return reservations.containsKey(key(gameId, player));
  }

  public synchronized void clear() {
    drafts.clear();
    invites.clear();
    reservations.clear();
  }

  private Draft requireDraft(UUID owner, String gameId) {
    Draft draft = drafts.get(owner);
    if (draft == null || !draft.gameId.equalsIgnoreCase(gameId)) {
      throw new IllegalStateException("solo creation draft does not exist");
    }
    return draft;
  }

  private void clearSelection(Draft draft) {
    if (draft.inviteToken != null) {
      invites.remove(draft.inviteToken);
    }
    if (draft.acceptedPlayer != null) {
      reservations.remove(key(draft.gameId, draft.acceptedPlayer), draft.owner);
    }
    draft.pendingPlayer = null;
    draft.acceptedPlayer = null;
    draft.inviteToken = null;
  }

  private void clearInvite(Invite invite) {
    invites.remove(invite.token());
    Draft draft = drafts.get(invite.owner());
    if (draft != null && invite.token().equals(draft.inviteToken)) {
      draft.pendingPlayer = null;
      draft.inviteToken = null;
    }
  }

  private static DraftView view(Draft draft) {
    return new DraftView(
        draft.owner,
        draft.gameId,
        draft.pendingPlayer,
        draft.acceptedPlayer,
        draft.inviteToken);
  }

  private static ReservationKey key(String gameId, UUID player) {
    return new ReservationKey(gameId.toLowerCase(Locale.ROOT), player);
  }
}
