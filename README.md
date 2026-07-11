# GameVoting

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Paper-1.21.1-green.svg)](https://papermc.io/)
[![Storage](https://img.shields.io/badge/storage-PostgreSQL-blue.svg)](https://www.postgresql.org/)

[中文说明](doc/README_zh.md) | [User Guide](doc/USER_GUIDE.md) | [中文使用指南](doc/USER_GUIDE_zh.md)

GameVoting is the lobby voting plugin for the minigames network. It manages voting, readiness, game selection, scheduler-backed server lifecycle, queued player transfers, vote history, holograms, and parties.

The current runtime uses SchedulerBridge for every server operation and PostgreSQL as its only persistence backend.

## Runtime architecture

1. GameVoting obtains the `ServerScheduler` service registered by the Paper SchedulerBridge plugin.
2. Scheduler reads each `gamevoting` entry from `servers/*.json` and passes the ordered catalog through SchedulerBridge.
3. When voting ends, GameVoting launches the winning `server-id` while players enter the ready phase.
4. The child server bridge reports startup and heartbeats to the scheduler. GameVoting polls until the instance state is `READY`.
5. The SchedulerBridge Velocity plugin registers every ready child server at the scheduler-assigned address.
6. GameVoting queues the selected player UUIDs for transfer. Velocity executes each connection request and reports the result.
7. Failed transfers remain queued and are retried by the scheduler. The deployment default is 30 seconds.
8. A child Bridge resets its idle timer whenever a player joins and asks the scheduler to stop the instance only after five continuous empty minutes.

GameVoting never chooses a child server port and never edits Velocity's server list directly.

## Features

- Multi-stage voting with lobby-ready, voting, and post-vote ready phases
- Scheduler-backed launch, status lookup, listing, stop, and transfer operations
- Exact client-version or client-version-range checks before readying
- Player-count filtering with `min_player` and `max_player`
- PostgreSQL vote history with UUID, timestamp, and JSONB vote details
- Optional DecentHolograms displays
- Configurable language files, voting items, BossBars, and ActionBars
- Lobby party management for up to 16 players
- Separate `/solo` catalog with single-caller shared joins and frozen player-world parties
- Optional GameVoting Velocity bridge for client-version detection, Scheduler-backed `/game`, and permission-aware `/help`

## Requirements

- Paper 1.21.1 for the current lobby deployment
- Java 17 or newer; Java 21 is used by the current deployment
- Paper SchedulerBridge plugin on the lobby server
- SchedulerBridge plugin on Velocity and every managed child server
- Running `server-scheduler` with a valid bridge token
- PostgreSQL database named `gamevoting`
- ViaVersion and the GameVoting Velocity bridge when client-version validation and its proxy commands are required
- DecentHolograms 2.8.6 or newer only when holograms are required

GameVoting disables itself if the Paper SchedulerBridge does not register `ServerScheduler`.

## Build

Publish the SchedulerBridge common API to the local Maven repository first:

```bash
cd ../scheduler-bridge
gradle :common:publishToMavenLocal
```

Build the Paper plugin:

```bash
cd ../GameVoting
mvn clean package
```

Build the optional GameVoting Velocity bridge:

```bash
cd velocity-bridge
mvn clean package
```

Artifacts:

- `target/GameVoting-1.1.4.jar`
- `velocity-bridge/target/gamevoting-velocity-bridge-1.0.0.jar`

## Installation

Install these plugins on the lobby Paper server:

- SchedulerBridge
- GameVoting
- DecentHolograms when holograms are enabled

Install these plugins on Velocity:

- SchedulerBridge Velocity plugin
- ViaVersion
- GameVoting Velocity bridge when version validation, `/game`, and the GameVoting `/help` output are required

Start the lobby once, then configure:

- `plugins/GameVoting/config.yml`
- `plugins/GameVoting/lang/*.yml`
- `plugins/GameVoting/holograms.yml`

In the managed deployment, scheduler file rendering supplies the PostgreSQL connection values from the scheduler's central configuration.

## Main configuration

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

PostgreSQL is the only supported persistence backend. The plugin creates the `vote_history` table and its indexes automatically. Setting `database.enabled` to `false` disables history and `/vote session list` while leaving live voting available.

## Scheduler game catalog

The managed deployment enables `game-config-mode: "scheduler"`. In this mode GameVoting never creates or reads `games.yml`. Every votable server owns a `gamevoting` object in `servers/<server-id>.json`:

```json
{
  "gamevoting": {
    "order": 10,
    "id": "GScard",
    "name": "&b&lGSkard",
    "description": [
      "&7Defeat opponents with cards!"
    ],
    "material": "GOLDEN_CARROT",
    "custom_model_data": 0,
    "min_version": "1.21.11",
    "max_version": "26.2",
    "min_players": 4,
    "max_players": 50
  }
}
```

The Scheduler derives `server-id` from the JSON filename, validates the catalog, and passes it through SchedulerBridge to GameVoting. `order` controls menu order. Both BedWars servers run a strict `1.21.11` core and accept clients from `1.21.11` through `26.2`.

The Velocity bridge also reads this catalog from the Scheduler at startup. `/game <game-id>` therefore displays every voting and Solo definition from `servers/*.json`, including its description, supported client versions, player range, and game type. The Velocity `config.yml` game list is used only when the Scheduler catalog cannot be reached.

Set `solo` to `true` to remove a definition from voting and expose it only through `/solo`. Solo definitions also provide `solo_mode` (`shared` or `player_world`), `solo_startup` (`always` or `on_demand`), `solo_max_players`, and `solo_retention_days`. Definitions with `solo: false` remain exclusive to voting and the normal game list.

`shared` starts or joins one scheduler-managed shared server and submits only the caller. It never captures or freezes a party. Clicking a `player_world` game first queries Scheduler for an existing allocation. Existing members immediately reopen their saved world. A player without a saved world enters a creation menu, may create alone or invite exactly one online lobby player, and can create a duo world only after that player accepts the clickable chat request. The accepted one- or two-player roster is frozen at creation and can be replaced only after `/solo destroy <game-id>` removes the saved world.

The GameVoting Velocity bridge requires ViaVersion and reads ViaVersion's original client protocol and version name before consulting Velocity's protocol value. Velocity is used only when ViaVersion has no player protocol. While the proxy bridge is installed, a lobby cache miss is reported as undetected and triggers a refresh instead of treating Paper's backend protocol as the client version.

GameVoting checks scheduler state once per second. As soon as the target enters `READY`, it queues the captured players without a fixed delay. Velocity holds those transfers until ViaVersion has detected the backend protocol, then connects the players immediately.

Stopping or replacing the pending target cancels its readiness poll, so callbacks from an older launch cannot transfer players later.

## Commands

Player-facing commands:

- `/vote` opens the voting menu during an active vote.
- `/vote start [minutes]` starts a vote immediately. The default is one minute; values such as `0.5` and `0.5min` are accepted.
- `/vote ready` marks the player ready after client-version validation.
- `/vote gamestart` lets the vote starter continue from the ready phase.
- `/vote join [game-id]` queues a transfer to the current or selected ready server.
- `/vote session list [page]` shows stored voting sessions.
- `/solo` opens the solo-only catalog.
- `/solo start <game-id>` opens an existing player world or starts the creation flow; shared games launch immediately.
- `/solo destroy <game-id>` destroys the caller's `player_world` allocation so it can be recreated with a new frozen list.

Players also receive a fixed Glowstone Dust item in hotbar slot 6. It cannot be moved or dropped, and right-clicking it opens the same Solo catalog as `/solo`.

Administrative commands:

- `/vote stop`
- `/vote forcestart <game-id>`
- `/vote stopgame <service-id>` stops one active Scheduler service; tab completion only lists online IDs such as `Backstabbed-1`.
- `/vote gamelist`
- `/vote session stop`
- `/vote reload`
- `/vote holograms create`
- `/vote holograms list`
- `/vote holograms remove <id>`
- `/vote lock <player>`
- `/vote unlock <player>`

See the [User Guide](doc/USER_GUIDE.md) for permissions, all current scheduler IDs, transfer behavior, Velocity responsibilities, and troubleshooting.

## License

Licensed under the [MIT License](LICENSE).
