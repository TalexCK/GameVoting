# GameVoting

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](../LICENSE)
[![Java Version](https://img.shields.io/badge/java-17%2B-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Paper Version](https://img.shields.io/badge/paper-1.16+-green.svg)](https://papermc.io/)
[![CloudNet Version](https://img.shields.io/badge/cloudnet-4.0.0--RC10-purple.svg)](https://cloudnetservice.eu/)

[English](../README.md) | [User Guide](USER_GUIDE.md) | [使用文档](USER_GUIDE_zh.md)

一个功能强大的 Minecraft 投票系统插件，专为 Paper 服务器和 CloudNet v4 网络环境设计。支持自动游戏选择、玩家准备系统、全息显示和多数据库持久化。

## ✨ 特性

### 🎮 投票系统
- **多阶段投票流程**
  - 基于玩家数量（≥6人）的自动物品分发
  - 使用绿宝石触发的预投票准备阶段
  - 使用指南针菜单界面的投票阶段
  - 使用染料准备指示器的投票后准备阶段
  
- **智能玩家准备系统**
  - 当在线玩家≥6人时，每位玩家获得绿宝石物品
  - 右键标记准备/取消准备状态
  - 所有玩家准备完毕后自动开始投票
  - 实时全息图更新显示准备人数
  
- **灵活的投票模式**
  - 手动启动：`/vote start [时长]` - 总是立即开始投票
  - 自动启动：绿宝石准备系统在所有玩家准备完毕后触发投票
  - 自定义投票时长（默认60秒）

### 📊 全息显示
- **动态显示状态**
  - 空闲状态：历史前10获胜游戏及投票数
  - 准备阶段：显示准备玩家数量和进度
  - 投票进行中：当前可用游戏及实时投票数
  - 投票结束：本次投票前10游戏
  - 传送后：恢复显示历史前10
  
- **多位置支持**
  - 使用 `/vote holograms create` 在任意位置创建全息图
  - 所有全息图位置自动同步
  - 使用简单命令列出和删除全息图

### 🗄️ 数据库集成
- **多数据库支持**
  - PostgreSQL（生产环境推荐）
  - MySQL/MariaDB
  - MongoDB
  - 可选：可以不使用数据库运行
  
- **投票历史追踪**
  - 记录获胜游戏、总投票数、玩家数量
  - 存储每个游戏的详细投票分布
  - 基于时间戳的历史分析
  - 自动统计前10获胜者

### 🌐 CloudNet 集成
- **无缝服务管理**
  - 自动 CloudNet 服务检测
  - 基于服务状态的游戏过滤
  - 通过代理命令传送玩家
  - 仅传送实际投票的玩家
  
- **服务配置**
  - 通过 `games.yml` 进行游戏到服务的映射
  - 可配置的服务名称模式
  - 支持每种游戏类型的多个服务

### 🌍 国际化
- **多语言支持**
  - 英语（en-US、en-UK）
  - 简体中文（zh-CN）
  - 易于添加自定义语言
  
- **完整的翻译覆盖**
  - 所有命令、消息和界面元素
  - 物品名称和描述
  - 全息图显示
  - 错误消息和反馈

### 🎯 高级功能
- **智能物品管理**
  - 自动在9号位分发物品
  - 不同服务器状态对应不同物品
  - 带持久化数据的不可丢弃投票物品
  - 颜色编码的准备状态指示器
  
- **队伍系统集成**
  - 创建和管理玩家队伍
  - 基于队伍的游戏传送
  - 队伍成员管理
  
- **强大的权限系统**
  - 细粒度权限控制
  - 管理员与玩家命令分离
  - 可配置的访问级别

## 📋 需求

- **服务器**
  - Paper 1.16 或更高版本（不支持 Spigot/Bukkit）
  - Java 17 或更高版本
  - CloudNet v4（4.0.0-RC10 或更高版本）
  
- **依赖项**
  - DecentHolograms 2.8.6+（用于全息图显示）
  - CloudNet Driver 和 Bridge 模块
  
- **可选**
  - PostgreSQL 12+ / MySQL 8.0+ / MongoDB 5.0+（用于投票历史）

## 🚀 安装

1. **下载插件**
   ```bash
   # 从源码构建
   git clone https://github.com/yourusername/GameVoting.git
   cd GameVoting
   mvn clean package
   ```

2. **安装依赖**
   - 下载并安装 [DecentHolograms](https://www.spigotmc.org/resources/decentholograms.96927/)
   - 确保 CloudNet v4 已正确配置

3. **部署插件**
   ```bash
   # 将编译后的 JAR 复制到插件文件夹
   cp target/GameVoting-1.1.0.jar /path/to/server/plugins/
   ```

4. **配置插件**
   - 启动服务器以生成默认配置文件
   - 编辑 `plugins/GameVoting/config.yml`
   - 配置 `plugins/GameVoting/games.yml`
   - 重启服务器

## ⚙️ 配置

### config.yml
```yaml
# 调试模式，显示详细日志
debug: false

# 语言：en-US、en-UK、zh-CN
language: "zh-CN"

# CloudNet 代理服务名称
proxy-service-name: "Proxy-1"

# 数据库配置
database:
  enabled: true
  type: "postgresql"  # postgresql、mysql、mongodb
  host: "localhost"
  port: 5432
  database: "gamevoting"
  username: "postgres"
  password: "password"

# 全息图位置（通过命令管理）
holograms:
  locations: []
```

### games.yml
```yaml
games:
  - id: "skywars"
    name: "空岛战争"
    service-name: "SkyWars-{number}"
    icon: "GOLDEN_SWORD"
    description: "在天空中战斗！"
    
  - id: "bedwars"
    name: "起床战争"
    service-name: "BedWars-{number}"
    icon: "RED_BED"
    description: "保护你的床！"
```

## 📖 使用方法

### 玩家使用

1. **自动投票（≥6人）**
   ```
   - 等待9号位出现绿宝石物品
   - 右键标记准备
   - 所有玩家准备完毕后开始投票
   ```

2. **手动投票**
   ```
   - 管理员启动：/vote start
   - 在9号位收到指南针
   - 右键打开投票菜单
   - 选择你喜欢的游戏
   ```

3. **投票后准备**
   ```
   - 投票结束后收到灰色染料
   - 右键标记准备
   - 准备后变为绿色染料
   - 所有玩家准备完毕后开始游戏
   ```

### 管理员使用

```bash
# 手动开始投票
/vote start [时长]

# 强制开始游戏（跳过准备阶段）
/vote forcestart

# 取消当前投票
/vote cancel

# 重载配置
/vote reload

# 全息图管理
/vote holograms create        # 在当前位置创建
/vote holograms list          # 列出所有全息图
/vote holograms remove <id>   # 删除指定全息图

# 加入游戏服务
/vote join <服务名>

# 队伍命令
/party create              # 创建队伍
/party invite <玩家>       # 邀请玩家
/party join <玩家>         # 加入队伍
/party leave              # 离开当前队伍
/party disband            # 解散你的队伍
```

## 🔌 API 使用

### 开发者接口

```java
// 获取投票会话
VotingSession session = VotingSession.getInstance();

// 检查投票状态
boolean isVoting = session.isVotingInProgress();
boolean isReady = session.isReadyPhaseActive();

// 获取投票计数
Map<String, Integer> votes = session.getVoteCounts();

// 访问数据库
VoteHistoryRepository repo = DatabaseManager.getInstance()
    .getVoteHistoryRepository();
List<VoteHistory> history = repo.getTopWinningGames(10);

// 全息图管理
HologramManager manager = plugin.getHologramManager();
manager.updateAllDisplays(DisplayState.VOTING_ACTIVE);
```

## 🏗️ 项目结构

```
GameVoting/
├── src/main/java/com/talexck/gameVoting/
│   ├── GameVoting.java              # 主插件类
│   ├── commands/                     # 命令处理器
│   │   ├── VoteCommand.java         # 投票命令
│   │   └── PartyCommand.java        # 队伍命令
│   ├── config/                       # 配置管理
│   │   ├── GameConfig.java          # 游戏配置
│   │   └── ConfigLoader.java        # 配置加载器
│   ├── voting/                       # 投票系统
│   │   └── VotingSession.java       # 投票会话管理器
│   ├── database/                     # 数据库层
│   │   ├── DatabaseManager.java     # 数据库连接
│   │   ├── models/                  # 数据模型
│   │   └── repositories/            # 数据仓库
│   ├── hologram/                     # 全息图显示
│   │   ├── HologramManager.java     # 全息图管理器
│   │   └── HologramDisplayManager.java
│   ├── listeners/                    # 事件监听器
│   │   ├── VoteItemListener.java    # 投票物品交互
│   │   ├── PlayerJoinListener.java  # 玩家加入事件
│   │   └── VotingPlayerQuitListener.java
│   ├── cloudnet/                     # CloudNet 集成
│   │   └── CloudNetAPI.java         # CloudNet API 包装器
│   ├── party/                        # 队伍系统
│   │   └── PartyManager.java        # 队伍管理
│   └── utils/                        # 工具类
│       ├── item/VoteItem.java       # 投票物品管理
│       ├── MessageUtil.java         # 消息工具
│       └── ActionBarUtil.java       # ActionBar 工具
├── src/main/resources/
│   ├── plugin.yml                    # 插件元数据
│   ├── config.yml                    # 默认配置
│   ├── games.yml                     # 游戏定义
│   └── lang/                         # 语言文件
│       ├── en-US.yml
│       ├── en-UK.yml
│       └── zh-CN.yml
└── pom.xml                           # Maven 配置
```

## 🤝 贡献

欢迎贡献！请遵循以下指南：

1. Fork 本仓库
2. 创建特性分支（`git checkout -b feature/amazing-feature`）
3. 提交你的更改（`git commit -m 'Add amazing feature'`）
4. 推送到分支（`git push origin feature/amazing-feature`）
5. 开启 Pull Request

## 📝 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](../LICENSE) 文件。

## 🙏 鸣谢

- [PaperMC](https://papermc.io/) - 高性能 Minecraft 服务器
- [CloudNet](https://cloudnetservice.eu/) - Minecraft 云系统
- [DecentHolograms](https://github.com/DecentSoftware-eu/DecentHolograms) - 全息图 API
- 本插件的所有贡献者和用户

## 📧 支持

- **问题反馈**：[GitHub Issues](https://github.com/yourusername/GameVoting/issues)
- **英文文档**：[User Guide](USER_GUIDE.md)
- **中文文档**：[使用文档](USER_GUIDE_zh.md)

## 🔄 版本历史

### v1.1.0
- 添加了带绿宝石触发的预投票准备系统
- 实现了带染料指示器的投票后准备阶段
- 添加了基于玩家数量的物品分发
- 增强了全息图显示状态
- 改进了 CloudNet 集成
- 添加了投票历史数据库追踪
- 实现了仅传送已投票玩家的过滤功能

### v1.0.0
- 初始版本
- 基础投票系统
- CloudNet 服务集成
- 全息图显示
- 多语言支持
