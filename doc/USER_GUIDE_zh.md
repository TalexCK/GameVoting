# GameVoting 使用文档

[English](USER_GUIDE.md) | [返回 README](README_zh.md)

Paper 服务器配合 CloudNet v4 使用 GameVoting 插件的完整指南。

## 目录

- [安装](#安装)
- [配置](#配置)
  - [主配置文件](#主配置文件)
  - [游戏配置](#游戏配置)
  - [语言文件](#语言文件)
  - [数据库设置](#数据库设置)
- [命令](#命令)
  - [玩家命令](#玩家命令)
  - [管理员命令](#管理员命令)
  - [队伍命令](#队伍命令)
- [权限](#权限)
- [投票流程](#投票流程)
  - [自动准备系统](#自动准备系统)
  - [手动投票](#手动投票)
  - [投票后准备阶段](#投票后准备阶段)
- [全息图显示](#全息图显示)
- [数据库集成](#数据库集成)
- [CloudNet 集成](#cloudnet-集成)
- [故障排除](#故障排除)
- [常见问题](#常见问题)

## 安装

### 前置要求

1. **Paper 服务器 1.16+**
   - 从 [PaperMC](https://papermc.io/downloads) 下载
   - 不支持 Spigot 和 Bukkit

2. **Java 17+**
   ```bash
   java -version  # 应显示 17 或更高版本
   ```

3. **CloudNet v4**
   - 版本 4.0.0-RC10 或更高
   - 已正确配置 Bridge 模块

4. **DecentHolograms**
   - 从 [SpigotMC](https://www.spigotmc.org/resources/decentholograms.96927/) 下载
   - 在 GameVoting 之前安装

### 安装步骤

1. **下载插件**
   - 从 GitHub 下载最新版本
   - 或从源码构建：
     ```bash
     git clone https://github.com/yourusername/GameVoting.git
     cd GameVoting
     mvn clean package
     ```

2. **安装插件**
   ```bash
   # 复制到插件文件夹
   cp GameVoting-1.1.0.jar /path/to/server/plugins/
   ```

3. **首次运行**
   - 启动服务器以生成配置文件
   - 停止服务器
   - 配置 `plugins/GameVoting/config.yml` 和 `games.yml`
   - 再次启动服务器

4. **验证安装**
   ```
   /plugins  # 应显示 GameVoting 为绿色
   /vote     # 应显示帮助信息
   ```

## 配置

### 主配置文件

文件：`plugins/GameVoting/config.yml`

```yaml
# 调试模式 - 显示详细日志
debug: false

# 语言选择
# 选项：en-US、en-UK、zh-CN
language: "zh-CN"

# CloudNet 代理服务名称
# 用于玩家传送
proxy-service-name: "Proxy-1"

# 数据库配置
database:
  enabled: true
  type: "postgresql"  # postgresql、mysql、mongodb、none
  host: "localhost"
  port: 5432          # PostgreSQL: 5432, MySQL: 3306, MongoDB: 27017
  database: "gamevoting"
  username: "postgres"
  password: "your_password_here"

# 全息图位置（通过命令管理）
holograms:
  locations: []
```

**配置选项说明：**

- `debug`：启用详细日志以进行故障排除
- `language`：所有玩家的界面语言
- `proxy-service-name`：用于传送的 CloudNet 代理服务名称
- `database.enabled`：启用/禁用数据库功能
- `database.type`：数据库类型（postgresql/mysql/mongodb/none）
- `holograms.locations`：自动管理，使用命令创建/删除

### 游戏配置

文件：`plugins/GameVoting/games.yml`

```yaml
games:
  - id: "skywars"
    name: "空岛战争"
    service-name: "SkyWars-{number}"
    icon: "GOLDEN_SWORD"
    description: "在天空中与其他玩家战斗！"
    lore:
      - "&7经典空岛战争玩法"
      - "&7破坏桥梁并战斗！"
      - ""
      - "&e点击投票！"
    
  - id: "bedwars"
    name: "起床战争"
    service-name: "BedWars-{number}"
    icon: "RED_BED"
    description: "保护你的床并摧毁其他队伍的床！"
    lore:
      - "&7基于团队的床防御"
      - "&7收集资源并战斗！"
      - ""
      - "&e点击投票！"
    
  - id: "duels"
    name: "决斗"
    service-name: "Duels-{number}"
    icon: "DIAMOND_SWORD"
    description: "1v1 战斗竞技场"
    lore:
      - "&7挑战其他玩家"
      - "&7多种装备选项"
      - ""
      - "&e点击投票！"
```

**游戏配置字段：**

- `id`：唯一标识符（内部使用）
- `name`：显示给玩家的名称
- `service-name`：CloudNet 服务名称模式
  - 使用 `{number}` 进行动态服务选择
  - 示例：`SkyWars-{number}` 匹配 `SkyWars-1`、`SkyWars-2` 等
- `icon`：投票菜单中物品的材质名称
  - 必须是有效的 Minecraft 材质名称
  - 参见 [材质列表](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html)
- `description`：投票菜单中显示的简短描述
- `lore`：物品说明行（支持使用 `&` 的颜色代码）

**添加新游戏：**

1. 在 `games.yml` 中添加新游戏条目
2. 重载配置：`/vote reload`
3. 确保相应的 CloudNet 服务存在

### 语言文件

文件：`plugins/GameVoting/lang/*.yml`

插件支持多种语言。你可以通过编辑语言文件来自定义消息。

**可用语言：**
- `en-US.yml` - 英语（美国）
- `en-UK.yml` - 英语（英国）
- `zh-CN.yml` - 简体中文

**语言文件结构示例：**

```yaml
# 投票消息
voting:
  started: "&a投票已开始！时长：&e{duration}秒"
  already_voted: "&c你已经投票给 &e{game}"
  vote_recorded: "&a你对 &e{game} &a的投票已记录！"
  ended: "&6投票结束！获胜者：&e{game} &6，获得 &e{votes} &6票"

# 物品名称
items:
  vote_compass: "&e&l➤ &6游戏投票"
  ready_gray: "&7&l✓ &8准备就绪"
  ready_lime: "&a&l✓ &2已经准备"
  insufficient_players: "&c&l✖ &4人数不足"
  start_voting: "&a&l✓ &2开始投票"

# ... 更多消息 ...
```

**自定义语言：**

1. 编辑相应的语言文件
2. 使用 `&` 表示颜色代码（例如：`&a` = 绿色，`&c` = 红色）
3. 使用 `{placeholders}` 表示动态值
4. 重载：`/vote reload`

### 数据库设置

插件支持三种数据库类型。根据你的需求选择一种。

#### PostgreSQL（推荐）

1. **安装 PostgreSQL**
   ```bash
   # Ubuntu/Debian
   sudo apt install postgresql postgresql-contrib
   
   # 启动服务
   sudo systemctl start postgresql
   ```

2. **创建数据库**
   ```sql
   sudo -u postgres psql
   
   CREATE DATABASE gamevoting;
   CREATE USER gamevoting_user WITH PASSWORD 'secure_password';
   GRANT ALL PRIVILEGES ON DATABASE gamevoting TO gamevoting_user;
   ```

3. **配置插件**
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

1. **安装 MySQL**
   ```bash
   # Ubuntu/Debian
   sudo apt install mysql-server
   
   # 启动服务
   sudo systemctl start mysql
   ```

2. **创建数据库**
   ```sql
   mysql -u root -p
   
   CREATE DATABASE gamevoting;
   CREATE USER 'gamevoting_user'@'localhost' IDENTIFIED BY 'secure_password';
   GRANT ALL PRIVILEGES ON gamevoting.* TO 'gamevoting_user'@'localhost';
   FLUSH PRIVILEGES;
   ```

3. **配置插件**
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

1. **安装 MongoDB**
   ```bash
   # Ubuntu/Debian
   sudo apt install mongodb
   
   # 启动服务
   sudo systemctl start mongodb
   ```

2. **创建数据库（可选）**
   ```bash
   mongosh
   
   use gamevoting
   db.createUser({
     user: "gamevoting_user",
     pwd: "secure_password",
     roles: [{ role: "readWrite", db: "gamevoting" }]
   })
   ```

3. **配置插件**
   ```yaml
   database:
     enabled: true
     type: "mongodb"
     host: "localhost"
     port: 27017
     database: "gamevoting"
     username: "gamevoting_user"  # 可选
     password: "secure_password"  # 可选
   ```

#### 不使用数据库

如果你不需要投票历史追踪：

```yaml
database:
  enabled: false
```

插件将正常工作，但不会保存投票历史。

## 命令

### 玩家命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/vote` | 显示投票菜单（投票期间） | `gamevoting.vote` |
| `/vote join <服务>` | 加入特定游戏服务 | `gamevoting.join` |
| `/party create` | 创建新队伍 | `gamevoting.party.create` |
| `/party invite <玩家>` | 邀请玩家加入队伍 | `gamevoting.party.invite` |
| `/party join <玩家>` | 加入玩家的队伍 | `gamevoting.party.join` |
| `/party leave` | 离开当前队伍 | `gamevoting.party.leave` |
| `/party kick <玩家>` | 将玩家踢出队伍 | `gamevoting.party.kick` |

### 管理员命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/vote start [时长]` | 开始投票（默认60秒） | `gamevoting.admin` |
| `/vote forcestart` | 强制开始游戏（跳过准备阶段） | `gamevoting.admin` |
| `/vote cancel` | 取消当前投票 | `gamevoting.admin` |
| `/vote reload` | 重载配置 | `gamevoting.admin` |
| `/vote holograms create` | 在当前位置创建全息图 | `gamevoting.admin` |
| `/vote holograms list` | 列出所有全息图 | `gamevoting.admin` |
| `/vote holograms remove <id>` | 删除特定全息图 | `gamevoting.admin` |
| `/party disband` | 解散你的队伍 | `gamevoting.party.disband` |

### 队伍命令

队伍系统允许玩家组队并一起传送。

**创建队伍：**
```
/party create
```

**邀请玩家：**
```
/party invite Steve
/party invite Alex
```

**加入队伍：**
```
/party join Steve
```

**管理队伍：**
```
/party leave          # 离开你的当前队伍
/party kick Alex      # 移除玩家（仅队长）
/party disband        # 删除队伍（仅队长）
```

**队伍功能：**
- 队伍成员一起投票
- 自动将队伍传送到游戏服务
- 队长拥有管理权限

## 权限

### 权限节点

```yaml
# 基本投票权限
gamevoting.vote: true

# 加入游戏服务
gamevoting.join: true

# 管理员命令
gamevoting.admin: false

# 队伍系统
gamevoting.party.create: true
gamevoting.party.invite: true
gamevoting.party.join: true
gamevoting.party.leave: true
gamevoting.party.kick: true
gamevoting.party.disband: true
```

### 权限组示例

**玩家权限（permissions.yml 或 LuckPerms）：**
```yaml
groups:
  default:
    permissions:
      - gamevoting.vote
      - gamevoting.join
      - gamevoting.party.*
```

**管理员权限：**
```yaml
groups:
  admin:
    permissions:
      - gamevoting.*
```

## 投票流程

### 自动准备系统

当服务器有 ≥6 名玩家且没有投票进行时：

1. **物品分发**
   - 每位玩家在9号位收到一个绿宝石
   - 物品名称："✓ 开始投票"
   - 不能丢弃或移动

2. **标记准备**
   - 右键点击绿宝石标记准备
   - 绿宝石变为灰色染料
   - 系统广播："玩家 X 已准备！(2/6)"

3. **取消准备**
   - 右键点击灰色染料取消准备
   - 返回绿宝石状态
   - 系统广播："玩家 X 不再准备。(1/6)"

4. **全员准备**
   - 当所有玩家准备完毕时，投票自动开始
   - 时长：默认60秒
   - 所有玩家收到指南针物品

**准备阶段的全息图显示：**
```
═══════════════════════
    ⏳ 准备投票
═══════════════════════
准备玩家：4/6
═══════════════════════
```

### 手动投票

管理员可以随时使用 `/vote start` 开始投票：

1. **启动命令**
   ```
   /vote start          # 默认60秒
   /vote start 120      # 自定义120秒
   ```

2. **投票阶段**
   - 所有玩家在9号位收到指南针
   - 右键打开投票菜单
   - 点击游戏物品进行投票
   - 可以在时间到期前更改投票

3. **投票菜单**
   ```
   ┌─────────────────────┐
   │   游戏投票          │
   ├─────────────────────┤
   │ [剑] 空岛战争       │
   │ 当前票数：3         │
   │                     │
   │ [床] 起床战争       │
   │ 当前票数：2         │
   │                     │
   │ [钻石] 决斗         │
   │ 当前票数：1         │
   └─────────────────────┘
   ```

4. **投票结束**
   - 计时器到期或管理员取消
   - 票数最多的游戏获胜
   - 如果平局，随机选择

**投票期间的全息图显示：**
```
═══════════════════════
    🗳 正在投票
═══════════════════════
空岛战争：████░░░░░░ 4
起床战争：███░░░░░░░ 3
决斗：    █░░░░░░░░░ 1
═══════════════════════
时间：45秒
═══════════════════════
```

### 投票后准备阶段

投票结束后，游戏开始前：

1. **准备物品分发**
   - 所有玩家在9号位收到灰色染料
   - 物品名称："✓ 准备就绪"
   - 说明："右键标记你已准备"

2. **标记准备**
   - 右键点击灰色染料
   - 变为绿色染料
   - 显示名称："✓ 已经准备"
   - 系统广播准备人数

3. **强制开始**
   - 管理员可以使用 `/vote forcestart` 跳过等待
   - 立即开始游戏

4. **全员准备**
   - 当所有玩家准备完毕时，游戏开始
   - 选择 CloudNet 服务
   - 玩家被传送到游戏服务
   - **仅传送投票的玩家**

**全息图显示：**
```
═══════════════════════
   投票结果
═══════════════════════
1. 空岛战争    4 票
2. 起床战争    3 票
3. 决斗        1 票
═══════════════════════
获胜者：空岛战争
═══════════════════════
准备玩家：5/6
═══════════════════════
```

## 全息图显示

### 显示状态

全息图根据服务器状态自动变化：

1. **IDLE** - 没有投票进行
   - 显示历史前10获胜游戏
   - 来自数据库的投票数

2. **PRE_VOTING_READY** - 等待玩家准备
   - 显示准备玩家数量
   - 进度指示器

3. **VOTING_ACTIVE** - 投票进行中
   - 显示所有游戏及当前投票数
   - 剩余时间

4. **VOTE_ENDED** - 投票结束，显示结果
   - 本次会话的前10游戏
   - 突出显示获胜者

5. **POST_TELEPORT** - 玩家传送后
   - 返回 IDLE 状态
   - 显示历史数据

### 管理全息图

**创建全息图：**
```bash
# 站在你想要全息图的位置
/vote holograms create

# 显示成功消息
# 全息图立即出现
```

**列出全息图：**
```bash
/vote holograms list

# 输出：
# 全息图：
# 1. world:100:64:200
# 2. world_nether:0:70:0
```

**删除全息图：**
```bash
/vote holograms remove 1

# 从列表中删除第一个全息图
```

### 全息图自定义

全息图由 DecentHolograms 管理。插件自动：
- 创建/更新全息图内容
- 同步所有全息图位置
- 处理显示状态转换

要手动编辑全息图外观，修改 `HologramDisplayManager.java` 中的全息图显示代码。

## 数据库集成

### 投票历史

启用数据库后，插件记录：

**投票会话：**
- 会话 UUID
- 时间戳
- 获胜游戏 ID 和名称
- 总投票数
- 玩家数量
- 每个游戏的投票分布

**数据库架构：**

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

### 查询历史

**前10获胜游戏：**
```java
VoteHistoryRepository repo = DatabaseManager.getInstance()
    .getVoteHistoryRepository();
    
List<VoteHistory> topGames = repo.getTopWinningGames(10);
```

**最近会话：**
```java
List<VoteHistory> recent = repo.getRecentSessions(20);
```

**游戏统计：**
```java
Map<String, Integer> stats = repo.getGameWinCounts();
```

## CloudNet 集成

### 服务检测

插件自动检测 CloudNet 服务：

1. **服务名称模式**
   - 在 `games.yml` 中定义
   - 示例：`SkyWars-{number}` 匹配 `SkyWars-1`、`SkyWars-2` 等

2. **服务选择**
   - 插件查找所有匹配的服务
   - 随机选择可用服务
   - 检查服务在线状态

3. **服务过滤**
   - 仅显示有运行服务的游戏
   - 将没有可用服务的游戏显示为灰色

### 玩家传送

**传送过程：**

1. 投票结束并确定获胜者
2. 选择 CloudNet 服务
3. 向代理发送传送命令：
   ```
   send <玩家> <服务>
   ```
4. 仅传送投票的玩家
5. 未投票者留在大厅服务器

**代理配置：**

确保你的 CloudNet 代理允许 `send` 命令：
```yaml
# 在 proxy config.yml 中
permissions:
  - cloudnet.command.send
```

### 手动传送

玩家可以手动加入服务：

```bash
/vote join SkyWars-1

# 将玩家发送到指定服务
# 需要 gamevoting.join 权限
```

## 故障排除

### 常见问题

**1. 插件未加载**
```
错误："requires DecentHolograms"
解决方案：安装 DecentHolograms 插件
```

**2. 数据库连接失败**
```
错误："Failed to connect to database"
解决方案：
- 检查数据库是否运行：systemctl status postgresql
- 验证 config.yml 中的凭据
- 检查端口是否正确（PostgreSQL 为 5432）
- 确保数据库存在：psql -U postgres -c "\l"
```

**3. 全息图未出现**
```
解决方案：
- 验证 DecentHolograms 已加载：/plugins
- 检查全息图位置：/vote holograms list
- 尝试重新创建：/vote holograms create
```

**4. 找不到 CloudNet 服务**
```
解决方案：
- 验证 CloudNet 正在运行
- 检查 games.yml 中的服务名称是否与实际服务匹配
- 确保安装了 Bridge 模块
- 检查 CloudNet 日志中的错误
```

**5. 玩家未传送**
```
解决方案：
- 检查 config.yml 中的 proxy-service-name
- 验证代理具有 send 权限
- 确保玩家已投票（仅传送投票者）
- 检查 CloudNet 代理日志
```

**6. 9号位没有物品出现**
```
解决方案：
- 检查玩家数量（绿宝石需要 ≥6 人）
- 验证没有投票进行
- 检查玩家背包权限
- 重启服务器以重置状态
```

### 调试模式

启用调试日志：

```yaml
# config.yml
debug: true
```

检查日志以获取详细信息：
```bash
tail -f logs/latest.log | grep GameVoting
```

### 获取帮助

1. **检查日志** - 大多数问题会在日志中显示错误
2. **验证配置** - 仔细检查 config.yml 和 games.yml
3. **测试依赖项** - 确保 Paper、CloudNet、DecentHolograms 正常工作
4. **GitHub Issues** - 提交包含完整错误日志的 bug 报告

## 常见问题

**问：我可以在没有 CloudNet 的情况下使用此插件吗？**
答：不行，插件需要 CloudNet v4 进行服务管理和传送。

**问：这个插件能在 Spigot 或 Bukkit 上工作吗？**
答：不行，仅支持 Paper 1.16+。

**问：我可以在没有数据库的情况下运行吗？**
答：可以，设置 `database.enabled: false`。你将失去投票历史，但基本功能可用。

**问：我可以添加多少个游戏？**
答：无限制。但是，投票菜单最适合 4-9 个游戏。

**问：玩家可以投票多次吗？**
答：玩家可以在投票期间更改投票，但只有最终投票计数。

**问：如果两个游戏平局会发生什么？**
答：在平局的游戏之间随机选择。

**问：我可以自定义投票菜单吗？**
答：可以，编辑 `games.yml` 中的图标、名称和说明。

**问：如何添加新语言？**
答：复制 `lang/` 中的现有语言文件，翻译它，并在 config.yml 中设置 `language`。

**问：我可以禁用准备系统吗？**
答：不能直接禁用，但管理员可以随时使用 `/vote start` 绕过它。

**问：如果玩家在投票期间断开连接会怎样？**
答：他们的投票将从计数中删除。如果所有准备的玩家都断开连接，准备阶段将重置。

**问：我可以查看投票统计数据吗？**
答：可以，如果启用了数据库，查询 `vote_history` 表或使用 API 方法。

**问：如何备份投票历史？**
答：使用标准数据库工具（pg_dump、mysqldump、mongodump）备份数据库。

---

更多信息，请参见[主 README](README_zh.md) 或[英文文档](USER_GUIDE.md)。
