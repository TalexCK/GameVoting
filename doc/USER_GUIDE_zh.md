# GameVoting 中文使用指南

[English](USER_GUIDE.md) | [返回说明](README_zh.md)

本文档说明当前 Paper、SchedulerBridge、Velocity 与 PostgreSQL 部署方式。

## 组件

完整部署由四部分协作：

- `server-scheduler` 管理服务器定义、进程、动态端口、实例状态和传送重试。
- Paper 上的 SchedulerBridge 向 GameVoting 提供异步 `ServerScheduler` 服务，同时报告本服的就绪状态和心跳。
- Velocity 上的 SchedulerBridge 注册就绪子服、同步玩家、处理传送请求、回报传送结果并提供 `/ping`。
- GameVoting Velocity 桥检测客户端协议版本，并提供 `/game` 和全网 GameVoting `/help`。

GameVoting 只通过 `ServerScheduler` 与 `server-scheduler` 通信。SchedulerBridge 的实现会向调度器仅监听回环地址的 API 发送带令牌验证的 HTTP 请求。调度器会向受管进程提供 `SCHEDULER_BRIDGE_URL`、`SCHEDULER_BRIDGE_TOKEN`、`SCHEDULER_SERVER_ID` 和 `SCHEDULER_INSTANCE_ID`。

## 安装

### 运行要求

- 当前大厅使用 Paper 1.21.1
- Java 17 或更高版本
- 大厅能够连接 PostgreSQL
- `server-scheduler` 正常运行
- 大厅、子服和 Velocity 使用匹配版本的 SchedulerBridge
- 需要版本校验和代理命令时安装 ViaVersion 与 GameVoting Velocity 桥
- 仅在启用全息图时安装 DecentHolograms

### Paper 安装

将 SchedulerBridge 和 GameVoting JAR 放入大厅的 `plugins/`。需要全息图时再安装 DecentHolograms。GameVoting 将 SchedulerBridge 声明为硬依赖，因此它必须成功加载。

首次启动大厅后配置：

- `plugins/GameVoting/config.yml`
- `plugins/GameVoting/lang/*.yml`
- `plugins/GameVoting/holograms.yml`

### Velocity 安装

安装 SchedulerBridge Velocity JAR。动态注册子服和处理传送队列必须依赖它。

大厅需要校验客户端版本时，先安装 ViaVersion，再安装 GameVoting Velocity 桥。该桥将 ViaVersion 声明为硬依赖，还会注册 `/game`，并用按权限生成的内容替换全局 `/help`。

## 主配置

文件：`plugins/GameVoting/config.yml`

```yaml
debug: false
game-config-mode: "scheduler"
language: "zh-CN"
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

配置项：

- `debug`：启用额外诊断输出。
- `language`：选择 `plugins/GameVoting/lang/` 下的语言文件。
- `spawnpoint.enable`：控制玩家加入时是否使用配置的大厅坐标。
- `database.enabled`：控制投票历史持久化。
- `database.host`、`port`、`database`、`username`、`password`：配置 PostgreSQL。
- `holograms.locations`：由全息图命令维护。

PostgreSQL 是唯一支持的持久化后端。GameVoting 启动时会自动创建 `vote_history`：UUID 主键、带时区时间、获胜游戏字段、计数和 JSONB 投票明细，同时创建时间与获胜游戏索引。

当前 Minigames 受管部署会从调度器中央配置渲染这些连接参数，不使用单独的环境文件。

如需主动关闭持久化：

```yaml
database:
  enabled: false
```

实时投票仍可运行，但历史全息图和 `/vote session list` 不可用。

## Scheduler 游戏目录

当前受管部署在 `plugins/GameVoting/config.yml` 中启用
`game-config-mode: "scheduler"`。该模式不会创建或读取 `games.yml`。

每个可投票子服都在 `servers/<server-id>.json` 中定义一个 `gamevoting` 节点。JSON
文件名去掉扩展名后就是 Scheduler 服务器 ID，因此不再维护第二份映射字段。

```json
{
  "gamevoting": {
    "order": 170,
    "id": "snowy_skirmish_2",
    "name": "&b&l雪地乱斗&c2",
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

`id` 是投票键，`order` 控制菜单顺序，其他字段负责显示、客户端版本验证和人数范围。
Scheduler 校验并排序全部条目，`/bridge/v1/games` 负责传输，Paper SchedulerBridge
通过 `ServerScheduler.games()` 提供给 GameVoting。两个 BedWars 服务端核心严格固定为
Minecraft `1.21.11`，允许客户端范围为 `1.21.11` 至 `26.2`。

`game-config-mode` 仍接受 `file` 作为兼容模式，但必须手动提供外部
`plugins/GameVoting/games.yml`，JAR 不再内置默认文件。

### Solo 目录

`solo: false` 的定义只会进入普通投票、`/vote join` 和 `/vote gamelist`；
`solo: true` 的定义会从所有投票路径排除，只显示在 `/solo`。

Solo 字段包括 `solo_mode`、`solo_startup`、`solo_max_players` 和
`solo_retention_days`。`shared` 表示共享服务器，可配置 `always` 或 `on_demand`。每次
Shared 请求只包含调用者本人，不检查其是否为队长，也不会捕获其他队员；非队长同样可以
启动或加入共享服务器。

`player_world` 会冻结一人或两人名单并创建持久世界。点击游戏后会先向 Scheduler 查询：
如果玩家已经属于现有存档，则直接重新启动该世界，并只传送本次点击者；如果没有存档，
则进入创建界面，可以直接创建单人世界，也可以选择一名当前大厅在线玩家。受邀玩家会在
聊天栏收到可点击的接受和拒绝操作；待处理邀请不计入名单，只有对方接受后才能创建双人
世界。启动前会为所有选中玩家校验客户端版本、`min_players`、`max_players` 和
`solo_max_players`。调度器负责保存所有者、成员与过期时间；使用
`/solo destroy <game-id>` 删除存档后，才允许用新名单重建。

## 投票与启动流程

### 大厅准备阶段

当前自动大厅准备流程的最低在线人数是两人。达到人数后玩家获得准备物品，所有人准备后开始投票。`/vote start [分钟]` 会跳过此阶段并立即开始投票；人数已达最低值时，普通玩家也可执行该命令。

时长参数单位是分钟：

```text
/vote start
/vote start 0.5
/vote start 1.5min
```

### 投票阶段

玩家通过 `/vote` 或投票物品选择游戏。只有当前大厅人数落在 `min_player` 到 `max_player` 范围内的游戏才有资格参与。

### 投票后准备阶段

计时结束后，GameVoting 选择符合人数条件的获胜游戏并锁定结果，然后立即启动其 `server-id`。子服启动期间，玩家进入准备阶段。

玩家初始默认准备，可以用物品取消准备，再通过物品或 `/vote ready` 恢复。恢复准备时会根据获胜游戏的精确版本或包含边界的版本范围校验客户端版本。

全员准备后开始十秒开局倒计时。本轮投票发起者可以在此阶段使用 `/vote gamestart`。如果发起者在仍有未准备玩家时强制继续，只会选择已准备玩家进行传送。

`/vote forcestart <game-id>` 是管理员绕过流程的命令，会直接启动指定调度器定义，并捕获当前全部大厅玩家，不进行正式投票。

## READY 检测与传送

启动与传送流程如下：

1. 调度器在分配的端口上启动子服。
2. 子服 SchedulerBridge 在服务器加载事件后发送 `READY`，随后每十秒发送一次心跳。
3. GameVoting 每秒查询一次调度器。
4. 如果实例在 `READY` 前消失或停止，自动传送会取消。
5. 进入 `READY` 后，GameVoting 立即提交已捕获的 UUID 列表。
6. Velocity SchedulerBridge 注册服务器、主动要求 ViaVersion 探测，并在目标后端进入协议检测缓存前暂存传送。
7. 协议检测成功后，Velocity 立即连接玩家。
8. 成功结果会完成传送记录；失败结果会在配置的间隔后重试，当前为 30 秒。
9. 最后一名玩家离开后，子服 Bridge 只在连续空服 5 分钟后通知调度器关闭该实例。

只有目标保持 `READY` 时，调度器才会向 Velocity 提供待处理传送。玩家断线会导致本次尝试失败；只要记录仍未完成，之后仍可重试。

空服期间只要有玩家进入，子服 Bridge 就会重置计时。带 Token 的空服请求包含当前实例
UUID；Proxy、Lobby、过期实例、非 `READY` 实例和未配置空服超时的定义都会被调度器拒绝。

使用 `/vote stopgame` 停止待传送游戏、取消投票会话或替换待传送目标时，大厅会取消
READY 轮询。旧启动流程已经返回的异步回调不会把玩家加入同名新实例。

## 命令

所有 `/vote` 命令都需要默认授予的 `gamevoting.vote`。

### 玩家与会话命令

- `/vote`：投票进行中打开菜单。
- `/vote start [分钟]`：立即开始投票；仅在大厅人数不足时要求管理员权限。
- `/vote ready`：通过版本校验后标记准备。
- `/vote gamestart`：允许本轮投票发起者从准备阶段继续；控制台也可以执行。
- `/vote join`：将玩家加入最近启动的当前游戏传送队列。
- `/vote join <game-id>`：解析该游戏的 `server-id`，要求实例处于 `READY`，然后提交传送。
- `/vote session list [page]`：每页显示十条已保存会话。

### 管理命令

以下命令需要 `gamevoting.vote.admin`：

- `/vote stop`：停止当前投票计时并显示结果，不启动获胜游戏。
- `/vote forcestart <game-id>`：立即启动对应服务器，并在完成启动检测后传送当前大厅玩家快照。
- `/vote stopgame <实例ID>`：精确停止一个正在运行的 Scheduler 实例；补全只显示在线实例，例如 `Backstabbed-1`。
- `/vote gamelist`：列出当前调度器实例处于 `READY` 的已配置游戏。
- `/vote session stop`：清理当前大厅投票或准备会话。如果还要停止已预启动的子服，需另行使用 `/vote stopgame <实例ID>`。
- `/vote reload`：重载主配置、游戏、全息图位置和语言文件。
- `/vote holograms create`：保存玩家当前位置。
- `/vote holograms list`：显示全息图 ID 与位置。
- `/vote holograms remove <id>`：删除指定全息图位置。

投票锁定命令需要 `gamevoting.vote.lock`：

- `/vote lock <player>`：锁定该玩家下一次符合条件的投票。
- `/vote unlock <player>`：移除待生效的投票锁定。

### Solo 命令

所有 Solo 命令需要默认授予的 `gamevoting.solo`：

- `/solo`：打开 Solo 游戏目录。
- `/solo start <game-id>`：直接提交启动请求。
- `/solo destroy <game-id>`：仅适用于 `player_world`，销毁调用者的世界分配。

快捷栏第 6 格固定放置萤石粉入口；该物品不能移动或丢弃，右键会打开 Solo 游戏目录。

### 队伍命令

所有队伍命令需要 `gamevoting.party`：

队伍最多包含 16 名玩家（含队长）。

- `/party create`
- `/party invite <player>`
- `/party accept`
- `/party decline`
- `/party exit`
- `/party list`
- `/party transfer <player>`
- `/party disband`

`/party vote` 和 `/party forcestart` 目前是预留命令，只会返回尚未开放的提示。

## 权限

- `gamevoting.vote`：打开和使用投票命令，默认 `true`。
- `gamevoting.vote.admin`：管理投票和调度器实例，默认 `op`。
- `gamevoting.vote.lock`：管理单轮投票锁定，默认 `false`。
- `gamevoting.party`：使用队伍命令，默认 `true`。
- `gamevoting.party.leader`：队长功能，默认 `true`。
- `gamevoting.solo`：打开、启动和重置 Solo 目录项目，默认 `true`。

## Velocity 组件职责

### SchedulerBridge Velocity 插件

它是运行管理桥：

- 每两秒拉取一次调度器传送任务；
- 每十秒以及连接事件后同步玩家 UUID、名称、延迟和当前服务器；
- 每两秒查询一次调度器实例；
- 将每个 `READY` 子服注册为 `127.0.0.1:<调度器端口>`；
- 移除不再就绪的动态服务器条目；
- 在注册新 Solo 实例前刷新冻结访问名单；
- 在 Velocity 最后一个预连接事件阶段校验最终目标，不在冻结名单中的玩家会被拒绝；
- 执行 Velocity 连接请求，并回报成功或失败；
- 提供 `/ping`，列出全部在线玩家的当前服务器和延迟。

### GameVoting Velocity 桥

它是投票功能桥：

- 硬依赖 ViaVersion，并在玩家登录和切换服务器时缓存其原始客户端协议版本名；
- 只有 ViaVersion 没有该玩家协议时才回退到 Velocity 协议；
- 通过 `gamevoting:version` 响应大厅的版本请求；
- 根据自己的 Velocity `config.yml` 提供 `/game <game>`；
- 用可配置、按权限显示的 GameVoting 帮助替换 `/help`。

代理桥存在但大厅尚未取得缓存时，大厅会请求刷新并报告版本未检测，不会使用 Paper 面向
后端的协议冒充客户端版本。该桥不负责启动服务器、分配端口、注册子服或执行传送队列；
这些职责属于 SchedulerBridge。

## 全息图

全息图是可选功能。未安装 DecentHolograms 时，GameVoting 会记录功能已禁用并继续运行。

显示内容跟随状态变化：

- 空闲：历史获胜游戏；
- 大厅准备：准备进度；
- 投票中：实时票数；
- 投票后准备：当前结果。

## 故障排除

### 插件启动时被禁用

预期控制台信息：

```text
SchedulerBridge did not register ServerScheduler
```

确认 Paper SchedulerBridge JAR 存在、先于 GameVoting 加载，并已获得调度器 API 地址和令牌。

### 数据库初始化失败

运行时错误信息使用英文，例如：

```text
Failed to initialize PostgreSQL connection
Failed to initialize VoteHistoryRepository
```

检查主机、端口、数据库、用户名、密码、授权，以及大厅进程到数据库的连通性。

### 子服一直没有进入 READY

确认子服安装正确平台的桥接组件，并获得匹配的 `SCHEDULER_SERVER_ID`、`SCHEDULER_INSTANCE_ID`、`SCHEDULER_BRIDGE_URL` 和 `SCHEDULER_BRIDGE_TOKEN`。检查调度器实例列表与子服日志，不要把端口或显示名填写到 `server-id`。

### 传送持续重试

在 Velocity 执行 `/ping`，确认玩家仍在线，并且目标服务器使用预期的调度器 ID。检查实例是否持续处于 `READY`、动态地址是否已注册，以及玩家能否完成 Velocity 连接。

### `/vote join <game-id>` 提示不可用

该命令不会启动缺失的服务器，只会在配置的 `server-id` 已处于 `READY` 时传送。请先通过正常投票流程或 `/vote forcestart <game-id>` 启动游戏。
