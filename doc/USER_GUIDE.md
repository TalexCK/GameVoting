# GameVoting User Guide

[中文版](USER_GUIDE_zh.md) | [Back to README](../README.md)

Complete guide for using the GameVoting plugin on your Paper server with CloudNet v4.

## Table of Contents

- [Installation](#installation)
- [Configuration](#configuration)
  - [Main Configuration](#main-configuration)
  - [Game Configuration](#game-configuration)
  - [Language Files](#language-files)
  - [Database Setup](#database-setup)
- [Commands](#commands)
  - [Player Commands](#player-commands)
  - [Admin Commands](#admin-commands)
  - [Party Commands](#party-commands)
- [Permissions](#permissions)
- [Voting Flow](#voting-flow)
  - [Automatic Ready System](#automatic-ready-system)
  - [Manual Voting](#manual-voting)
  - [Post-Voting Ready Phase](#post-voting-ready-phase)
- [Hologram Displays](#hologram-displays)
- [Database Integration](#database-integration)
- [CloudNet Integration](#cloudnet-integration)
- [Troubleshooting](#troubleshooting)
- [FAQ](#faq)

## Installation

### Prerequisites

1. **Paper Server 1.16+**
   - Download from [PaperMC](https://papermc.io/downloads)
   - Spigot and Bukkit are NOT supported

2. **Java 17+**
   ```bash
   java -version  # Should show version 17 or higher
   ```

3. **CloudNet v4**
   - Version 4.0.0-RC10 or higher
   - Properly configured with Bridge module

4. **DecentHolograms**
   - Download from [SpigotMC](https://www.spigotmc.org/resources/decentholograms.96927/)
   - Install before GameVoting

### Installation Steps

1. **Download Plugin**
   - Download the latest release from GitHub
   - Or build from source:
     ```bash
     git clone https://github.com/yourusername/GameVoting.git
     cd GameVoting
     mvn clean package
     ```

2. **Install Plugin**
   ```bash
   # Copy to plugins folder
   cp GameVoting-1.1.0.jar /path/to/server/plugins/
   ```

3. **First Run**
   - Start the server to generate configuration files
   - Stop the server
   - Configure `plugins/GameVoting/config.yml` and `games.yml`
   - Start the server again

4. **Verify Installation**
   ```
   /plugins  # Should show GameVoting in green
   /vote     # Should show help message
   ```

## Configuration

### Main Configuration

File: `plugins/GameVoting/config.yml`

```yaml
# Debug mode - Shows detailed logging
debug: false

# Language selection
# Options: en-US, en-UK, zh-CN
language: "en-US"

# CloudNet proxy service name
# Used for player teleportation
proxy-service-name: "Proxy-1"

# Database configuration
database:
  enabled: true
  type: "postgresql"  # postgresql, mysql, mongodb, none
  host: "localhost"
  port: 5432          # PostgreSQL: 5432, MySQL: 3306, MongoDB: 27017
  database: "gamevoting"
  username: "postgres"
  password: "your_password_here"

# Hologram locations (managed via commands)
holograms:
  locations: []
```

**Configuration Options:**

- `debug`: Enable detailed logging for troubleshooting
- `language`: Interface language for all players
- `proxy-service-name`: CloudNet proxy service name for teleportation
- `database.enabled`: Enable/disable database features
- `database.type`: Database type (postgresql/mysql/mongodb/none)
- `holograms.locations`: Auto-managed, use commands to create/remove

### Game Configuration

File: `plugins/GameVoting/games.yml`

```yaml
games:
  - id: "skywars"
    name: "SkyWars"
    service-name: "SkyWars-{number}"
    icon: "GOLDEN_SWORD"
    description: "Fight other players in the sky!"
    lore:
      - "&7Classic skywars gameplay"
      - "&7Break bridges and fight!"
      - ""
      - "&eClick to vote!"
    
  - id: "bedwars"
    name: "BedWars"
    service-name: "BedWars-{number}"
    icon: "RED_BED"
    description: "Protect your bed and destroy others!"
    lore:
      - "&7Team-based bed defense"
      - "&7Collect resources and fight!"
      - ""
      - "&eClick to vote!"
    
  - id: "duels"
    name: "Duels"
    service-name: "Duels-{number}"
    icon: "DIAMOND_SWORD"
    description: "1v1 combat arena"
    lore:
      - "&7Challenge other players"
      - "&7Various kit options"
      - ""
      - "&eClick to vote!"
```

**Game Configuration Fields:**

- `id`: Unique identifier (used internally)
- `name`: Display name shown to players
- `service-name`: CloudNet service name pattern
  - Use `{number}` for dynamic service selection
  - Example: `SkyWars-{number}` matches `SkyWars-1`, `SkyWars-2`, etc.
- `icon`: Material name for the item in voting menu
  - Must be valid Minecraft material name
  - See [Material List](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html)
- `description`: Short description shown in voting menu
- `lore`: Item lore lines (supports color codes with `&`)

**Adding New Games:**

1. Add new game entry to `games.yml`
2. Reload configuration: `/vote reload`
3. Ensure corresponding CloudNet service exists

### Language Files

Files: `plugins/GameVoting/lang/*.yml`

The plugin supports multiple languages. You can customize messages by editing language files.

**Available Languages:**
- `en-US.yml` - English (United States)
- `en-UK.yml` - English (United Kingdom)
- `zh-CN.yml` - Simplified Chinese

**Example Language File Structure:**

```yaml
# Voting messages
voting:
  started: "&aVoting has started! Duration: &e{duration}s"
  already_voted: "&cYou have already voted for &e{game}"
  vote_recorded: "&aYour vote for &e{game} &ahas been recorded!"
  ended: "&6Voting ended! Winner: &e{game} &6with &e{votes} &6votes"

# Item names
items:
  vote_compass: "&e&l➤ &6Game Voting"
  ready_gray: "&7&l✓ &8Ready Up"
  ready_lime: "&a&l✓ &2Already Ready"
  insufficient_players: "&c&l✖ &4Insufficient Players"
  start_voting: "&a&l✓ &2Start Voting"

# ... more messages ...
```

**Customizing Languages:**

1. Edit the appropriate language file
2. Use `&` for color codes (e.g., `&a` = green, `&c` = red)
3. Use `{placeholders}` for dynamic values
4. Reload: `/vote reload`

### Database Setup

The plugin supports three database types. Choose one based on your needs.

#### PostgreSQL (Recommended)

1. **Install PostgreSQL**
   ```bash
   # Ubuntu/Debian
   sudo apt install postgresql postgresql-contrib
   
   # Start service
   sudo systemctl start postgresql
   ```

2. **Create Database**
   ```sql
   sudo -u postgres psql
   
   CREATE DATABASE gamevoting;
   CREATE USER gamevoting_user WITH PASSWORD 'secure_password';
   GRANT ALL PRIVILEGES ON DATABASE gamevoting TO gamevoting_user;
   ```

3. **Configure Plugin**
   ```yaml
   database:
     enabled: true
     type: "postgresql"
     host: "localhost"
     port: 5432
     database: "gamevoting"
     username: "gamevoting_user"
     password: "secure_password"
   ```

#### MySQL/MariaDB

1. **Install MySQL**
   ```bash
   # Ubuntu/Debian
   sudo apt install mysql-server
   
   # Start service
   sudo systemctl start mysql
   ```

2. **Create Database**
   ```sql
   mysql -u root -p
   
   CREATE DATABASE gamevoting;
   CREATE USER 'gamevoting_user'@'localhost' IDENTIFIED BY 'secure_password';
   GRANT ALL PRIVILEGES ON gamevoting.* TO 'gamevoting_user'@'localhost';
   FLUSH PRIVILEGES;
   ```

3. **Configure Plugin**
   ```yaml
   database:
     enabled: true
     type: "mysql"
     host: "localhost"
     port: 3306
     database: "gamevoting"
     username: "gamevoting_user"
     password: "secure_password"
   ```

#### MongoDB

1. **Install MongoDB**
   ```bash
   # Ubuntu/Debian
   sudo apt install mongodb
   
   # Start service
   sudo systemctl start mongodb
   ```

2. **Create Database (Optional)**
   ```bash
   mongosh
   
   use gamevoting
   db.createUser({
     user: "gamevoting_user",
     pwd: "secure_password",
     roles: [{ role: "readWrite", db: "gamevoting" }]
   })
   ```

3. **Configure Plugin**
   ```yaml
   database:
     enabled: true
     type: "mongodb"
     host: "localhost"
     port: 27017
     database: "gamevoting"
     username: "gamevoting_user"  # Optional
     password: "secure_password"  # Optional
   ```

#### No Database

If you don't need vote history tracking:

```yaml
database:
  enabled: false
```

The plugin will work normally but won't save vote history.

## Commands

### Player Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/vote` | Show voting menu (during voting) | `gamevoting.vote` |
| `/vote join <service>` | Join a specific game service | `gamevoting.join` |
| `/party create` | Create a new party | `gamevoting.party.create` |
| `/party invite <player>` | Invite player to party | `gamevoting.party.invite` |
| `/party join <player>` | Join a player's party | `gamevoting.party.join` |
| `/party leave` | Leave current party | `gamevoting.party.leave` |
| `/party kick <player>` | Kick player from party | `gamevoting.party.kick` |

### Admin Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/vote start [duration]` | Start voting (default 60s) | `gamevoting.admin` |
| `/vote forcestart` | Force start game (skip ready phase) | `gamevoting.admin` |
| `/vote cancel` | Cancel active voting | `gamevoting.admin` |
| `/vote reload` | Reload configurations | `gamevoting.admin` |
| `/vote holograms create` | Create hologram at current location | `gamevoting.admin` |
| `/vote holograms list` | List all holograms | `gamevoting.admin` |
| `/vote holograms remove <id>` | Remove specific hologram | `gamevoting.admin` |
| `/party disband` | Disband your party | `gamevoting.party.disband` |

### Party Commands

The party system allows players to group up and teleport together.

**Creating a Party:**
```
/party create
```

**Inviting Players:**
```
/party invite Steve
/party invite Alex
```

**Joining a Party:**
```
/party join Steve
```

**Managing Party:**
```
/party leave          # Leave your current party
/party kick Alex      # Remove a player (party leader only)
/party disband        # Delete the party (party leader only)
```

**Party Features:**
- Party members vote together
- Automatic party teleportation to game service
- Party leader has management permissions

## Permissions

### Permission Nodes

```yaml
# Basic voting permission
gamevoting.vote: true

# Join game services
gamevoting.join: true

# Admin commands
gamevoting.admin: false

# Party system
gamevoting.party.create: true
gamevoting.party.invite: true
gamevoting.party.join: true
gamevoting.party.leave: true
gamevoting.party.kick: true
gamevoting.party.disband: true
```

### Permission Groups Example

**For players (permissions.yml or LuckPerms):**
```yaml
groups:
  default:
    permissions:
      - gamevoting.vote
      - gamevoting.join
      - gamevoting.party.*
```

**For admins:**
```yaml
groups:
  admin:
    permissions:
      - gamevoting.*
```

## Voting Flow

### Automatic Ready System

When server has ≥6 players and no voting is active:

1. **Item Distribution**
   - Each player receives an emerald in slot 9
   - Item name: "✓ Start Voting"
   - Cannot be dropped or moved

2. **Marking Ready**
   - Right-click the emerald to mark ready
   - Emerald changes to gray dye
   - System broadcasts: "Player X is ready! (2/6)"

3. **Unmark Ready**
   - Right-click gray dye to unmark
   - Returns to emerald state
   - System broadcasts: "Player X is no longer ready. (1/6)"

4. **All Ready**
   - When all players are ready, voting starts automatically
   - Duration: Default 60 seconds
   - All players receive compass items

**Hologram Display During Ready Phase:**
```
═══════════════════════
    ⏳ Preparing Vote
═══════════════════════
Players Ready: 4/6
═══════════════════════
```

### Manual Voting

Admin can start voting anytime with `/vote start`:

1. **Start Command**
   ```
   /vote start          # 60 seconds default
   /vote start 120      # 120 seconds custom
   ```

2. **Voting Phase**
   - All players receive compass in slot 9
   - Right-click to open voting menu
   - Click game item to vote
   - Can change vote before time expires

3. **Voting Menu**
   ```
   ┌─────────────────────┐
   │   Game Voting       │
   ├─────────────────────┤
   │ [Sword] SkyWars     │
   │ Current votes: 3    │
   │                     │
   │ [Bed] BedWars       │
   │ Current votes: 2    │
   │                     │
   │ [Diamond] Duels     │
   │ Current votes: 1    │
   └─────────────────────┘
   ```

4. **Voting End**
   - Timer expires or admin cancels
   - Game with most votes wins
   - If tied, random selection

**Hologram Display During Voting:**
```
═══════════════════════
    🗳 VOTING ACTIVE
═══════════════════════
SkyWars: ████░░░░░░ 4
BedWars: ███░░░░░░░ 3
Duels:   █░░░░░░░░░ 1
═══════════════════════
Time: 45s
═══════════════════════
```

### Post-Voting Ready Phase

After voting ends, before game starts:

1. **Ready Item Distribution**
   - All players receive gray dye in slot 9
   - Item name: "✓ Ready Up"
   - Lore: "Right-click to mark yourself as ready"

2. **Marking Ready**
   - Right-click gray dye
   - Changes to lime dye
   - Display name: "✓ Already Ready"
   - System broadcasts ready count

3. **Force Start**
   - Admin can use `/vote forcestart` to skip waiting
   - Immediately starts game

4. **All Ready**
   - When all players ready, game starts
   - CloudNet service is selected
   - Players teleported to game service
   - **Only players who voted are teleported**

**Hologram Display:**
```
═══════════════════════
   Voting Results
═══════════════════════
1. SkyWars        4 votes
2. BedWars        3 votes
3. Duels          1 vote
═══════════════════════
Winner: SkyWars
═══════════════════════
Players Ready: 5/6
═══════════════════════
```

## Hologram Displays

### Display States

The hologram automatically changes based on server state:

1. **IDLE** - No voting active
   - Shows top 10 historical winning games
   - Vote counts from database

2. **PRE_VOTING_READY** - Waiting for players to ready up
   - Shows ready player count
   - Progress indicator

3. **VOTING_ACTIVE** - Voting in progress
   - Shows all games with current vote counts
   - Remaining time

4. **VOTE_ENDED** - Voting ended, showing results
   - Top 10 games from current session
   - Winner highlighted

5. **POST_TELEPORT** - After players teleported
   - Returns to IDLE state
   - Shows historical data

### Managing Holograms

**Create Hologram:**
```bash
# Stand where you want the hologram
/vote holograms create

# Success message shown
# Hologram appears immediately
```

**List Holograms:**
```bash
/vote holograms list

# Output:
# Holograms:
# 1. world:100:64:200
# 2. world_nether:0:70:0
```

**Remove Hologram:**
```bash
/vote holograms remove 1

# Removes first hologram from list
```

### Hologram Customization

Holograms are managed by DecentHolograms. The plugin automatically:
- Creates/updates hologram content
- Synchronizes all hologram locations
- Handles display state transitions

To manually edit hologram appearance, modify the hologram display code in `HologramDisplayManager.java`.

## Database Integration

### Vote History

When database is enabled, the plugin records:

**Vote Sessions:**
- Session UUID
- Timestamp
- Winning game ID and name
- Total vote count
- Player count
- Vote breakdown per game

**Database Schema:**

PostgreSQL/MySQL:
```sql
CREATE TABLE vote_history (
    session_id UUID PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL,
    winning_game_id VARCHAR(50) NOT NULL,
    winning_game_name VARCHAR(100) NOT NULL,
    total_votes INTEGER NOT NULL,
    player_count INTEGER NOT NULL,
    vote_details JSONB NOT NULL
);
```

MongoDB:
```javascript
{
  _id: ObjectId,
  sessionId: UUID,
  timestamp: ISODate,
  winningGameId: String,
  winningGameName: String,
  totalVotes: Number,
  playerCount: Number,
  voteDetails: {
    "skywars": 5,
    "bedwars": 3,
    "duels": 1
  }
}
```

### Querying History

**Top Winning Games:**
```java
VoteHistoryRepository repo = DatabaseManager.getInstance()
    .getVoteHistoryRepository();
    
List<VoteHistory> topGames = repo.getTopWinningGames(10);
```

**Recent Sessions:**
```java
List<VoteHistory> recent = repo.getRecentSessions(20);
```

**Game Statistics:**
```java
Map<String, Integer> stats = repo.getGameWinCounts();
```

## CloudNet Integration

### Service Detection

The plugin automatically detects CloudNet services:

1. **Service Name Patterns**
   - Defined in `games.yml`
   - Example: `SkyWars-{number}` matches `SkyWars-1`, `SkyWars-2`, etc.

2. **Service Selection**
   - Plugin finds all matching services
   - Selects random available service
   - Checks service online status

3. **Service Filtering**
   - Only shows games with running services
   - Grays out games with no available services

### Player Teleportation

**Teleport Process:**

1. Voting ends and winner determined
2. CloudNet service selected
3. Teleport commands sent to proxy:
   ```
   send <player> <service>
   ```
4. Only players who voted are teleported
5. Non-voters stay on hub server

**Proxy Configuration:**

Ensure your CloudNet proxy allows `send` commands:
```yaml
# In proxy config.yml
permissions:
  - cloudnet.command.send
```

### Manual Teleportation

Players can manually join services:

```bash
/vote join SkyWars-1

# Sends player to specified service
# Requires gamevoting.join permission
```

## Troubleshooting

### Common Issues

**1. Plugin not loading**
```
Error: "requires DecentHolograms"
Solution: Install DecentHolograms plugin
```

**2. Database connection failed**
```
Error: "Failed to connect to database"
Solutions:
- Check database is running: systemctl status postgresql
- Verify credentials in config.yml
- Check port is correct (5432 for PostgreSQL)
- Ensure database exists: psql -U postgres -c "\l"
```

**3. Holograms not appearing**
```
Solutions:
- Verify DecentHolograms is loaded: /plugins
- Check hologram locations: /vote holograms list
- Try recreating: /vote holograms create
```

**4. CloudNet services not found**
```
Solutions:
- Verify CloudNet is running
- Check service names in games.yml match actual services
- Ensure Bridge module is installed
- Check CloudNet logs for errors
```

**5. Players not teleporting**
```
Solutions:
- Check proxy-service-name in config.yml
- Verify proxy has send permission
- Ensure players voted (only voters teleport)
- Check CloudNet proxy logs
```

**6. Items not appearing in slot 9**
```
Solutions:
- Check player count (need ≥6 for emerald)
- Verify no voting is active
- Check player inventory permissions
- Restart server to reset states
```

### Debug Mode

Enable debug logging:

```yaml
# config.yml
debug: true
```

Check logs for detailed information:
```bash
tail -f logs/latest.log | grep GameVoting
```

### Getting Help

1. **Check logs** - Most issues show errors in logs
2. **Verify configuration** - Double-check config.yml and games.yml
3. **Test dependencies** - Ensure Paper, CloudNet, DecentHolograms work
4. **GitHub Issues** - Report bugs with full error logs

## FAQ

**Q: Can I use this plugin without CloudNet?**
A: No, the plugin requires CloudNet v4 for service management and teleportation.

**Q: Does this work with Spigot or Bukkit?**
A: No, only Paper 1.16+ is supported.

**Q: Can I run without a database?**
A: Yes, set `database.enabled: false`. You'll lose vote history but basic features work.

**Q: How many games can I add?**
A: Unlimited. However, voting menu works best with 4-9 games.

**Q: Can players vote multiple times?**
A: Players can change their vote during voting, but only their final vote counts.

**Q: What happens if two games tie?**
A: Random selection between tied games.

**Q: Can I customize the voting menu?**
A: Yes, edit `games.yml` for icons, names, and lore.

**Q: How do I add a new language?**
A: Copy an existing language file in `lang/`, translate it, and set `language` in config.yml.

**Q: Can I disable the ready system?**
A: Not directly, but admins can always use `/vote start` to bypass it.

**Q: What if a player disconnects during voting?**
A: Their vote is removed from the count. If all ready players disconnect, ready phase resets.

**Q: Can I see vote statistics?**
A: Yes, if database enabled, query `vote_history` table or use API methods.

**Q: How do I backup vote history?**
A: Backup your database using standard database tools (pg_dump, mysqldump, mongodump).

---

For more information, see the [main README](../README.md) or [Chinese documentation](USER_GUIDE_zh.md).
