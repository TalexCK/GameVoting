# GameVoting User Guide

[中文版](USER_GUIDE_zh.md) | [Back to README](../README.md)

This guide describes the current Paper, SchedulerBridge, Velocity, and PostgreSQL deployment.

## Components

The complete installation has four cooperating parts:

- `server-scheduler` owns server definitions, processes, dynamically assigned ports, instance state, and transfer retries.
- SchedulerBridge on Paper exposes the asynchronous `ServerScheduler` service used by GameVoting and reports the server's own readiness and heartbeats.
- SchedulerBridge on Velocity registers ready child servers, synchronizes players, processes transfer requests, reports transfer results, and provides `/ping`.
- The GameVoting Velocity bridge detects client protocol versions and provides `/game` plus the network-wide GameVoting `/help` output.

GameVoting communicates with `server-scheduler` only through `ServerScheduler`. The SchedulerBridge implementation sends token-authenticated HTTP requests to the scheduler's loopback API. The scheduler supplies `SCHEDULER_BRIDGE_URL`, `SCHEDULER_BRIDGE_TOKEN`, `SCHEDULER_SERVER_ID`, and `SCHEDULER_INSTANCE_ID` to managed processes.

## Installation

### Requirements

- Paper 1.21.1 for the current lobby
- Java 17 or newer
- PostgreSQL reachable from the lobby
- A running `server-scheduler`
- Matching SchedulerBridge builds on the lobby, child servers, and Velocity
- ViaVersion and the GameVoting Velocity bridge when version checks and its proxy commands are required
- DecentHolograms only when holograms are enabled

### Paper installation

Place the SchedulerBridge and GameVoting JARs in the lobby `plugins/` directory. Add DecentHolograms when needed. SchedulerBridge must load because GameVoting declares it as a hard dependency.

Start the lobby once and configure these files:

- `plugins/GameVoting/config.yml`
- `plugins/GameVoting/lang/*.yml`
- `plugins/GameVoting/holograms.yml`

### Velocity installation

Install the SchedulerBridge Velocity JAR. It is required for dynamic server registration and queued transfers.

Install ViaVersion before the GameVoting Velocity bridge when the lobby must validate client versions. The bridge declares ViaVersion as a required dependency, also registers `/game`, and replaces the global `/help` command with its permission-aware output.

## Main configuration

File: `plugins/GameVoting/config.yml`

```yaml
debug: false
game-config-mode: "scheduler"
language: "en-US"
spawnpoint:
  enable: false
  x: 0
  y: 64
  z: 0
database:
  enabled: true
  host: "127.0.0.1"
  port: 5432
  database: "gamevoting"
  username: "minigames"
  password: "replace-me"
holograms:
  locations: []
```

Configuration fields:

- `debug` enables additional diagnostic output.
- `language` selects a file under `plugins/GameVoting/lang/`.
- `spawnpoint.enable` controls whether join handling uses the configured lobby coordinates.
- `database.enabled` controls vote-history persistence.
- `database.host`, `port`, `database`, `username`, and `password` configure PostgreSQL.
- `holograms.locations` is maintained by the hologram commands.

PostgreSQL is the only supported persistence backend. On startup, GameVoting creates `vote_history` with a UUID primary key, `TIMESTAMPTZ` timestamp, winning-game fields, counts, and JSONB vote details. It also creates timestamp and winning-game indexes.

The managed minigames deployment renders the connection values from the scheduler's central configuration. No separate environment file is used.

If persistence is intentionally disabled, use:

```yaml
database:
  enabled: false
```

Live voting continues, but history displays and `/vote session list` are unavailable.

## Scheduler game catalog

The managed deployment sets `game-config-mode: "scheduler"` in
`plugins/GameVoting/config.yml`. GameVoting does not create or read `games.yml` in this mode.

Each votable server defines one `gamevoting` object in `servers/<server-id>.json`. The JSON
filename stem is the Scheduler server ID, so no second mapping field exists.

```json
{
  "gamevoting": {
    "order": 170,
    "id": "snowy_skirmish_2",
    "name": "&b&lSnowy Skirmish&c2",
    "description": [
      "&7"
    ],
    "material": "SNOW_BLOCK",
    "custom_model_data": 0,
    "min_version": "1.21.11",
    "max_version": "26.2",
    "min_players": 2,
    "max_players": 12
  }
}
```

`id` is the voting key, `order` controls menu order, and the remaining fields control display,
client-version validation, and player-count availability. Scheduler validates and orders all
entries, `/bridge/v1/games` transports them, and the Paper SchedulerBridge exposes them through
`ServerScheduler.games()`. Both BedWars servers run a strict Minecraft `1.21.11` core and accept
clients from `1.21.11` through `26.2`.

`file` remains available as a compatibility value for `game-config-mode`, but it requires an
explicit external `plugins/GameVoting/games.yml`; no default file is bundled.

### Solo catalog

`solo: false` definitions are available only to voting, `/vote join`, and `/vote gamelist`.
`solo: true` definitions are excluded from every voting path and appear only in `/solo`.

Solo fields are `solo_mode`, `solo_startup`, `solo_max_players`, and `solo_retention_days`.
`shared` represents one joinable server and supports either `always` or `on_demand` startup. Each
shared request contains only its caller. Party membership and leadership are ignored, so a
non-leader starts or joins the shared server without capturing other party members.

`player_world` creates a persistent allocation shared by a frozen one- or two-player list. Clicking
the game first queries Scheduler. A member of an existing allocation immediately reopens that saved
world and only the caller is transferred. If no allocation exists, a creation menu lets the caller
create alone or select one online lobby player. The target receives clickable Accept and Decline
actions in chat. A pending request is not part of the roster; the duo can be created only after
acceptance. Client version and `min_players`, `max_players`, and `solo_max_players` are validated for
both selected players before creation. The scheduler stores allocation ownership and expiry;
`/solo destroy <game-id>` removes the allocation before a replacement roster can be created.

## Voting and launch flow

### Lobby-ready phase

The current minimum to enter the automatic lobby-ready flow is two online players. Players receive the ready item, and the vote starts when everyone is ready. `/vote start [minutes]` bypasses this phase and starts voting immediately. A non-administrator may use it when the minimum player count is met.

The duration argument is in minutes. Examples:

```text
/vote start
/vote start 0.5
/vote start 1.5min
```

### Voting phase

Players use `/vote` or the voting item to select a game. Only games whose `min_player` and `max_player` include the current lobby size are eligible.

### Post-vote ready phase

When the timer ends, GameVoting selects an eligible winner, locks that result, and launches its `server-id` immediately. Players enter the ready phase while the child server starts.

Players are initially marked ready. They may cancel readiness with the item, then use the item or `/vote ready` to become ready again. The ready check validates the player's client version against the winning game's exact version or inclusive range.

When everyone is ready, a ten-second start countdown begins. The vote starter may use `/vote gamestart` during this phase. If the starter forces the game while some players are not ready, only the ready players are selected for transfer.

`/vote forcestart <game-id>` is an administrative bypass. It launches the selected scheduler definition and captures all current lobby players without running a vote.

## READY detection and transfer behavior

The startup and transfer flow is:

1. The scheduler starts the child server on its assigned port.
2. The child SchedulerBridge sends `READY` after the server-load event and sends a heartbeat every ten seconds.
3. GameVoting queries the scheduler once per second.
4. If the instance disappears or stops before `READY`, automatic transfer is cancelled.
5. After `READY`, GameVoting immediately submits the captured UUID list.
6. The Velocity SchedulerBridge registers the server, actively asks ViaVersion to probe it, and holds the transfer until the detected-protocol cache contains that backend.
7. Velocity connects the player immediately after protocol detection succeeds.
8. Success completes the transfer record. Failure schedules another attempt after the configured retry interval, currently 30 seconds.
9. After the last player leaves, the child Bridge waits for five continuous empty minutes before asking the scheduler to stop that exact instance.

The scheduler only exposes queued transfers while the target remains `READY`. A disconnected player therefore produces a failed attempt and can be retried later if the record remains pending.

Any player joining during the empty interval resets the child Bridge timer. The authenticated idle request includes the active instance UUID, and the scheduler rejects it for Proxy, Lobby, stale instances, non-READY instances, or definitions without an idle timeout.

Stopping the pending game with `/vote stopgame`, cancelling the voting session, or replacing the pending target also cancels the lobby readiness poll. A completed callback from an older launch cannot queue players into a newer instance with the same server ID.

## Commands

All `/vote` commands require `gamevoting.vote`, which is granted by default.

### Player and session commands

- `/vote` opens the voting menu during an active vote.
- `/vote start [minutes]` starts voting immediately; administrator permission is required only below the lobby minimum.
- `/vote ready` marks the player ready after version validation.
- `/vote gamestart` lets the current vote starter continue from the ready phase; the console may also execute it.
- `/vote join` queues the player for the most recently started current game.
- `/vote join <game-id>` resolves that game's `server-id`, requires a `READY` instance, and queues the player.
- `/vote session list [page]` displays ten stored sessions per page.

### Administrative commands

These commands require `gamevoting.vote.admin`:

- `/vote stop` stops the active voting timer, displays results, and does not launch a winner.
- `/vote forcestart <game-id>` launches the configured server immediately and transfers the current lobby snapshot after startup handling.
- `/vote stopgame <service-id>` stops exactly one active Scheduler service. Tab completion only lists online service IDs such as `Backstabbed-1`.
- `/vote gamelist` lists configured games whose scheduler instance is currently `READY`.
- `/vote session stop` clears the current lobby voting or ready session. Use `/vote stopgame <service-id>` separately when a prestarted child must also be stopped.
- `/vote reload` reloads the main config, games, hologram locations, and language files.
- `/vote holograms create` stores the player's current location.
- `/vote holograms list` displays stored hologram IDs and locations.
- `/vote holograms remove <id>` removes a stored hologram location.

Vote-lock commands require `gamevoting.vote.lock`:

- `/vote lock <player>` locks the player's next eligible vote.
- `/vote unlock <player>` removes the pending vote lock.

### Solo commands

All solo commands require `gamevoting.solo`, granted by default:

- `/solo` opens the solo catalog.
- `/solo start <game-id>` submits a launch directly.
- `/solo destroy <game-id>` is available only for `player_world` definitions.

Hotbar slot 6 contains a fixed Glowstone Dust shortcut. It cannot be moved or dropped, and right-clicking it opens the Solo catalog.

### Party commands

All party commands require `gamevoting.party`:

Parties contain at most 16 players, including the leader.

- `/party create`
- `/party invite <player>`
- `/party accept`
- `/party decline`
- `/party exit`
- `/party list`
- `/party transfer <player>`
- `/party disband`

`/party vote` and `/party forcestart` are reserved and currently return a not-yet-available response.

## Permissions

- `gamevoting.vote`: open and use voting commands; default `true`.
- `gamevoting.vote.admin`: manage votes and scheduler instances; default `op`.
- `gamevoting.vote.lock`: manage one-round vote locks; default `false`.
- `gamevoting.party`: use party commands; default `true`.
- `gamevoting.party.leader`: party-leader features; default `true`.
- `gamevoting.solo`: open, launch, and reset entries in the solo catalog; default `true`.

## Velocity responsibilities

### SchedulerBridge Velocity plugin

This is the operational bridge:

- polls scheduler transfers every two seconds;
- synchronizes player UUID, name, ping, and current server every ten seconds and after connection events;
- queries scheduler instances every two seconds;
- registers every `READY` child server at `127.0.0.1:<scheduler-port>`;
- removes dynamically managed entries that are no longer ready;
- refreshes frozen solo access lists before registering a new solo instance;
- rejects final pre-connect targets at the last Velocity event stage when the player is not in the frozen list;
- executes Velocity connection requests and reports success or failure;
- provides `/ping`, listing every online player's current server and latency.

### GameVoting Velocity bridge

This is the voting feature bridge:

- requires ViaVersion and caches its original client protocol version name on login and server changes;
- falls back to Velocity's protocol only when ViaVersion has no protocol for the player;
- responds to lobby requests over `gamevoting:version`;
- loads the complete game catalog from Scheduler and provides `/game <game>` with its description, rules, aliases, supported versions, player range, and voting or Solo type;
- keeps the managed Velocity `config.yml` limited to command help and never duplicates Scheduler game content there;
- replaces `/help` with configurable, permission-aware GameVoting help.

When the lobby has this proxy bridge but no cached response yet, it requests a refresh and reports the version as undetected. It never substitutes Paper's backend-facing protocol for the client version. The bridge does not launch servers, assign ports, register child servers, or execute queued transfers. Those duties belong to SchedulerBridge.

## Holograms

Holograms are optional. Without DecentHolograms, GameVoting logs that hologram features are disabled and continues running.

Displays follow the current state:

- idle: historical winning games;
- lobby-ready: ready progress;
- voting: live vote totals;
- post-vote ready: current result.

## Troubleshooting

### Plugin disables during startup

Expected console message:

```text
SchedulerBridge did not register ServerScheduler
```

Confirm that the Paper SchedulerBridge JAR is present, loaded before GameVoting, and configured with the scheduler API base and token.

### Database initialization fails

Expected console messages are in English, for example:

```text
Failed to initialize PostgreSQL connection
Failed to initialize VoteHistoryRepository
```

Confirm host, port, database, username, password, grants, and connectivity from the lobby process.

### Child server never becomes READY

Confirm that the child uses the correct platform bridge and receives matching `SCHEDULER_SERVER_ID`, `SCHEDULER_INSTANCE_ID`, `SCHEDULER_BRIDGE_URL`, and `SCHEDULER_BRIDGE_TOKEN`. Check the scheduler instance list and child log. Do not replace `server-id` with a port or display name.

### Transfer keeps retrying

Run `/ping` on Velocity and verify that the player is connected and the target server appears under the expected scheduler ID. Check that the instance remains `READY`, its dynamic address is registered, and the player can complete a Velocity connection request.

### `/vote join <game-id>` reports unavailable

The command does not start a missing server. It only transfers to the configured `server-id` when that instance is already `READY`. Start it through the voting flow or `/vote forcestart <game-id>` first.
