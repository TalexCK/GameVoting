# GameVoting

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java Version](https://img.shields.io/badge/java-17%2B-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Paper Version](https://img.shields.io/badge/paper-1.16+-green.svg)](https://papermc.io/)
[![CloudNet Version](https://img.shields.io/badge/cloudnet-4.0.0--RC10-purple.svg)](https://cloudnetservice.eu/)

[中文文档](doc/README_zh.md) | [User Guide](doc/USER_GUIDE.md) | [使用文档](doc/USER_GUIDE_zh.md)

A powerful and feature-rich Minecraft voting system plugin for Paper servers, designed for CloudNet v4 network environments. Support for automatic game selection, player ready system, holographic displays, and multi-database persistence.

## ✨ Features

### 🎮 Voting System
- **Multi-phase Voting Flow**
  - Automatic item distribution based on player count (≥6 players)
  - Pre-voting ready phase with emerald trigger items
  - Voting phase with compass menu interface
  - Post-voting ready phase with dye-based ready indicators
  
- **Smart Player Ready System**
  - Players receive emerald items when ≥6 players online
  - Right-click to mark ready/unready status
  - Automatic voting start when all players are ready
  - Real-time hologram updates showing ready count
  
- **Flexible Voting Modes**
  - Manual start: `/vote start [duration]` - Always starts voting immediately
  - Automatic start: Emerald ready system triggers voting when all players ready
  - Custom voting duration (default 60 seconds)

### 📊 Holographic Displays
- **Dynamic Display States**
  - Idle: Historical top 10 winning games with vote counts
  - Pre-voting Ready: Shows ready player count and progress
  - Voting Active: Current available games with live vote counts
  - Vote Ended: Top 10 voted games from current session
  - Post-teleport: Returns to historical top 10
  
- **Multi-location Support**
  - Create holograms at any location with `/vote holograms create`
  - Automatic synchronization across all hologram locations
  - List and remove holograms with simple commands

### 🗄️ Database Integration
- **Multi-database Support**
  - PostgreSQL (recommended for production)
  - MySQL/MariaDB
  - MongoDB
  - Optional: Can run without database
  
- **Vote History Tracking**
  - Records winning game, total votes, player count
  - Stores detailed vote breakdown per game
  - Timestamp-based historical analytics
  - Automatic top 10 winner statistics

### 🌐 CloudNet Integration
- **Seamless Service Management**
  - Automatic CloudNet service detection
  - Service status-based game filtering
  - Player teleportation via proxy commands
  - Only teleports players who actually voted
  
- **Service Configuration**
  - Game-to-service mapping via `games.yml`
  - Configurable service name patterns
  - Support for multiple services per game type

### 🌍 Internationalization
- **Multi-language Support**
  - English (en-US, en-UK)
  - Simplified Chinese (zh-CN)
  - Easy to add custom languages
  
- **Complete Translation Coverage**
  - All commands, messages, and UI elements
  - Item names and lore
  - Hologram displays
  - Error messages and feedback

### 🎯 Advanced Features
- **Smart Item Management**
  - Automatic item distribution in slot 9
  - Different items for different server states
  - Undropable vote items with persistent data
  - Color-coded ready status indicators
  
- **Party System Integration**
  - Create and manage player parties
  - Party-based game teleportation
  - Party member management
  
- **Robust Permission System**
  - Fine-grained permission control
  - Admin vs player command separation
  - Configurable access levels

## 📋 Requirements

- **Server**
  - Paper 1.16 or higher (Spigot/Bukkit not supported)
  - Java 17 or higher
  - CloudNet v4 (4.0.0-RC10 or higher)
  
- **Dependencies**
  - DecentHolograms 2.8.6+ (for hologram displays)
  - CloudNet Driver & Bridge modules
  
- **Optional**
  - PostgreSQL 12+ / MySQL 8.0+ / MongoDB 5.0+ (for vote history)

## 🚀 Installation

1. **Download the plugin**
   ```bash
   # Build from source
   git clone https://github.com/yourusername/GameVoting.git
   cd GameVoting
   mvn clean package
   ```

2. **Install dependencies**
   - Download and install [DecentHolograms](https://www.spigotmc.org/resources/decentholograms.96927/)
   - Ensure CloudNet v4 is properly configured

3. **Deploy the plugin**
   ```bash
   # Copy the compiled JAR to your plugins folder
   cp target/GameVoting-1.1.0.jar /path/to/server/plugins/
   ```

4. **Configure the plugin**
   - Start the server to generate default configuration files
   - Edit `plugins/GameVoting/config.yml`
   - Configure `plugins/GameVoting/games.yml`
   - Restart the server

## ⚙️ Configuration

### config.yml
```yaml
# Debug mode for detailed logging
debug: false

# Language: en-US, en-UK, zh-CN
language: "en-US"

# CloudNet proxy service name
proxy-service-name: "Proxy-1"

# Database configuration
database:
  enabled: true
  type: "postgresql"  # postgresql, mysql, mongodb
  host: "localhost"
  port: 5432
  database: "gamevoting"
  username: "postgres"
  password: "password"

# Hologram locations (managed via commands)
holograms:
  locations: []
```

### games.yml
```yaml
games:
  - id: "skywars"
    name: "SkyWars"
    service-name: "SkyWars-{number}"
    icon: "GOLDEN_SWORD"
    description: "Fight in the sky!"
    
  - id: "bedwars"
    name: "BedWars"
    service-name: "BedWars-{number}"
    icon: "RED_BED"
    description: "Protect your bed!"
```

## 📖 Usage

### For Players

1. **Automatic Voting (≥6 players)**
   ```
   - Wait for emerald item in slot 9
   - Right-click to mark ready
   - Voting starts when all players ready
   ```

2. **Manual Voting**
   ```
   - Admin starts: /vote start
   - Receive compass in slot 9
   - Right-click to open voting menu
   - Select your preferred game
   ```

3. **Post-voting Ready**
   ```
   - Receive gray dye after voting ends
   - Right-click to mark ready
   - Changes to lime dye when ready
   - Game starts when all players ready
   ```

### For Administrators

```bash
# Start voting manually
/vote start [duration]

# Force start game (skip ready phase)
/vote forcestart

# Cancel active voting
/vote cancel

# Reload configuration
/vote reload

# Hologram management
/vote holograms create        # Create at current location
/vote holograms list          # List all holograms
/vote holograms remove <id>   # Remove specific hologram

# Join game service
/vote join <service>

# Party commands
/party create              # Create a party
/party invite <player>     # Invite a player
/party join <player>       # Join a party
/party leave              # Leave current party
/party disband            # Disband your party
```

## 🔌 API Usage

### For Developers

```java
// Get voting session
VotingSession session = VotingSession.getInstance();

// Check voting state
boolean isVoting = session.isVotingInProgress();
boolean isReady = session.isReadyPhaseActive();

// Get vote counts
Map<String, Integer> votes = session.getVoteCounts();

// Access database
VoteHistoryRepository repo = DatabaseManager.getInstance()
    .getVoteHistoryRepository();
List<VoteHistory> history = repo.getTopWinningGames(10);

// Hologram management
HologramManager manager = plugin.getHologramManager();
manager.updateAllDisplays(DisplayState.VOTING_ACTIVE);
```

## 🏗️ Project Structure

```
GameVoting/
├── src/main/java/com/talexck/gameVoting/
│   ├── GameVoting.java              # Main plugin class
│   ├── commands/                     # Command handlers
│   │   ├── VoteCommand.java         # Voting commands
│   │   └── PartyCommand.java        # Party commands
│   ├── config/                       # Configuration management
│   │   ├── GameConfig.java          # Game configuration
│   │   └── ConfigLoader.java        # Config loader
│   ├── voting/                       # Voting system
│   │   └── VotingSession.java       # Vote session manager
│   ├── database/                     # Database layer
│   │   ├── DatabaseManager.java     # Database connections
│   │   ├── models/                  # Data models
│   │   └── repositories/            # Data repositories
│   ├── hologram/                     # Hologram displays
│   │   ├── HologramManager.java     # Hologram manager
│   │   └── HologramDisplayManager.java
│   ├── listeners/                    # Event listeners
│   │   ├── VoteItemListener.java    # Vote item interactions
│   │   ├── PlayerJoinListener.java  # Player join events
│   │   └── VotingPlayerQuitListener.java
│   ├── cloudnet/                     # CloudNet integration
│   │   └── CloudNetAPI.java         # CloudNet API wrapper
│   ├── party/                        # Party system
│   │   └── PartyManager.java        # Party management
│   └── utils/                        # Utility classes
│       ├── item/VoteItem.java       # Vote item management
│       ├── MessageUtil.java         # Message utilities
│       └── ActionBarUtil.java       # ActionBar utilities
├── src/main/resources/
│   ├── plugin.yml                    # Plugin metadata
│   ├── config.yml                    # Default config
│   ├── games.yml                     # Game definitions
│   └── lang/                         # Language files
│       ├── en-US.yml
│       ├── en-UK.yml
│       └── zh-CN.yml
└── pom.xml                           # Maven configuration
```

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [PaperMC](https://papermc.io/) - High-performance Minecraft server
- [CloudNet](https://cloudnetservice.eu/) - Minecraft cloud system
- [DecentHolograms](https://github.com/DecentSoftware-eu/DecentHolograms) - Hologram API
- All contributors and users of this plugin

## 📧 Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/GameVoting/issues)
- **Documentation**: [User Guide](doc/USER_GUIDE.md)
- **Chinese Documentation**: [使用文档](doc/USER_GUIDE_zh.md)

## 🔄 Version History

### v1.1.0
- Added pre-voting ready system with emerald triggers
- Implemented post-voting ready phase with dye indicators
- Added player count-based item distribution
- Enhanced hologram display states
- Improved CloudNet integration
- Added vote history database tracking
- Teleport filtering for voted players only

### v1.0.0
- Initial release
- Basic voting system
- CloudNet service integration
- Hologram displays
- Multi-language support
