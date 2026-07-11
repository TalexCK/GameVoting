package com.talexck.gameVoting.voting;

/** Represents the result of a vote operation. */
public enum VoteResult {
  /** Vote was successfully added. */
  ADDED,

  /** Vote was successfully removed. */
  REMOVED,

  /** Player has reached the maximum vote limit (3 votes). */
  LIMIT_REACHED,

  /** 玩家对同一游戏的否定票已达上限。 */
  NEGATIVE_VOTE_LIMIT_REACHED,

  /** Voting session is not currently active. */
  SESSION_INACTIVE,

  /** 玩家本轮投票已被锁定。 */
  PLAYER_LOCKED,

  /** 没有可移除的票。 */
  NOTHING_TO_REMOVE,
}
