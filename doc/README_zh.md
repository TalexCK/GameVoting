# GameVoting

[![许可证](https://img.shields.io/badge/license-MIT-blue.svg)](../LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Paper-1.21.1-green.svg)](https://papermc.io/)
[![数据存储](https://img.shields.io/badge/storage-PostgreSQL-blue.svg)](https://www.postgresql.org/)

[English](../README.md) | [User Guide](USER_GUIDE.md) | [中文使用指南](USER_GUIDE_zh.md)

GameVoting 是 Minigames 网络的大厅投票插件，负责投票、准备确认、游戏选择、由调度器管理的子服生命周期、玩家传送队列、投票历史、全息图和队伍功能。

当前运行架构只通过 SchedulerBridge 管理服务器，并只使用 PostgreSQL 持久化数据。

## 运行架构

1. GameVoting 从 Paper 的服务管理器取得 SchedulerBridge 注册的 `ServerScheduler`。
2. Scheduler 从 `servers/*.json` 读取 `gamevoting` 条目，并通过 SchedulerBridge 传入有序游戏目录。
3. 投票结束后，GameVoting 在玩家进入准备阶段时立即启动获胜游戏的 `server-id`。
4. 子服桥接组件向调度器报告启动状态和心跳，GameVoting 持续查询，直到实例进入 `READY`。
5. Velocity 上的 SchedulerBridge 自动用调度器分配的地址注册所有就绪子服。
6. GameVoting 把需要传送的玩家 UUID 加入队列，Velocity 执行连接并回报结果。
7. 失败的传送保留在队列中，由调度器重试；当前部署默认间隔为 30 秒。
8. 子服 Bridge 在玩家进入时重置空服计时，只有连续 5 分钟无人时才通知调度器关闭实例。

GameVoting 不分配子服端口，也不直接修改 Velocity 的服务器列表。

## 功能

- 大厅准备、正式投票、投票后准备的多阶段流程
- 通过调度桥启动、查询、列出、停止服务器并提交传送
- 准备阶段的精确客户端版本或版本范围校验
- 使用 `min_player` 和 `max_player` 按大厅人数筛选游戏
- 使用 UUID、带时区时间和 JSONB 明细保存投票历史
- 可选的 DecentHolograms 全息图
- 可配置的语言、投票物品、BossBar 和 ActionBar
- 最多支持 16 名玩家的大厅队伍管理
- 独立的 `/solo` 目录、单玩家共享服加入和不可变的玩家世界队伍名单
- 可选的 GameVoting Velocity 桥，用于客户端版本检测、Scheduler 动态 `/game` 和按权限显示的 `/help`

## 运行要求

- 当前大厅部署使用 Paper 1.21.1
- Java 17 或更高版本；当前部署使用 Java 21
- 大厅必须安装 Paper 版 SchedulerBridge
- Velocity 和所有受管子服必须安装对应平台的 SchedulerBridge
- `server-scheduler` 正常运行，且桥接令牌一致
- 已创建名为 `gamevoting` 的 PostgreSQL 数据库
- 需要客户端版本校验和代理命令时安装 ViaVersion 与 GameVoting Velocity 桥
- 仅在启用全息图时需要 DecentHolograms 2.8.6 或更高版本

如果 Paper SchedulerBridge 没有注册 `ServerScheduler`，GameVoting 会禁用自身。

## 构建

先把 SchedulerBridge 公共 API 发布到本机 Maven 仓库：

```bash
cd ../scheduler-bridge
gradle :common:publishToMavenLocal
```

构建 Paper 插件：

```bash
cd ../GameVoting
mvn clean package
```

构建可选的 GameVoting Velocity 桥：

```bash
cd velocity-bridge
mvn clean package
```

产物：

- `target/GameVoting-1.1.4.jar`
- `velocity-bridge/target/gamevoting-velocity-bridge-1.0.0.jar`

## 安装

大厅 Paper 服务器需要安装：

- SchedulerBridge
- GameVoting
- 启用全息图时安装 DecentHolograms

Velocity 需要安装：

- SchedulerBridge Velocity 插件
- ViaVersion
- 需要版本校验、`/game` 和 GameVoting `/help` 时安装 GameVoting Velocity 桥

首次启动大厅后配置：

- `plugins/GameVoting/config.yml`
- `plugins/GameVoting/lang/*.yml`
- `plugins/GameVoting/holograms.yml`

在当前受管部署中，调度器的文件渲染会从中央配置写入 PostgreSQL 连接参数。

## 主配置

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

PostgreSQL 是唯一支持的持久化后端。插件会自动创建 `vote_history` 表和索引。将 `database.enabled` 设为 `false` 会关闭历史记录和 `/vote session list`，但实时投票仍可运行。

## Scheduler 游戏目录

当前部署启用 `game-config-mode: "scheduler"`。该模式下 GameVoting 不会创建或读取
`games.yml`，每个可投票子服都在 `servers/<server-id>.json` 中维护 `gamevoting`：

```json
{
  "gamevoting": {
    "order": 10,
    "id": "GScard",
    "name": "&b&lGSkard",
    "description": [
      "&7用卡牌击败对手!"
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

Scheduler 直接用 JSON 文件名作为 `server-id`，校验目录后通过 SchedulerBridge 传给
GameVoting。`order` 控制菜单顺序。两个 BedWars 服务端核心严格固定为 `1.21.11`，
允许客户端范围为 `1.21.11` 至 `26.2`。

将 `solo` 设为 `true` 后，该定义不会出现在普通投票或游戏列表中，只会出现在
`/solo`。Solo 定义还包含 `solo_mode`（`shared` 或 `player_world`）、
`solo_startup`（`always` 或 `on_demand`）、`solo_max_players` 和
`solo_retention_days`。`solo: false` 的定义只属于普通投票目录。

`shared` 启动或加入一台由调度器管理的共享服务器，每次只提交点击者本人，不会捕获或冻结
队伍。点击 `player_world` 游戏时会先向 Scheduler 查询现有存档：名单内玩家会直接重新启动
原世界；没有存档的玩家会进入创建界面，可以直接创建单人世界，也可以选择一名当前大厅
在线玩家并发送可点击接受或拒绝的聊天申请。只有对方接受后才能按双人名单创建，创建后的
一人或两人名单会冻结；使用 `/solo destroy <game-id>` 销毁存档后才能更换名单重建。

GameVoting Velocity 桥硬依赖 ViaVersion，优先读取 ViaVersion 保存的原始客户端协议号和
版本名；只有 ViaVersion 没有该玩家的协议时才回退到 Velocity 协议。代理桥存在但大厅缓存
尚未返回时，大厅只会报告版本未检测并请求刷新，不会把 Paper 后端协议冒充客户端协议。

GameVoting 每秒查询一次调度器状态；实例进入 `READY` 后立即提交已捕获玩家，不再固定等待。Velocity 会暂存传送，直到 ViaVersion 已检测到目标后端协议，再立即连接玩家。

停止或替换待传送目标时，插件会取消 READY 轮询，旧启动流程的回调不会在之后继续传送玩家。

## 命令

玩家命令：

- `/vote` 在投票进行中打开投票菜单。
- `/vote start [分钟]` 立即开始投票，默认一分钟；支持 `0.5` 和 `0.5min` 这类参数。
- `/vote ready` 在客户端版本校验通过后标记准备。
- `/vote gamestart` 允许本轮投票发起者从准备阶段继续开局。
- `/vote join [game-id]` 将玩家传送请求加入当前或指定就绪子服的队列。
- `/vote session list [page]` 查看已保存的投票历史。
- `/solo` 打开仅包含 Solo 游戏的菜单。
- `/solo start <game-id>` 进入现有玩家世界或打开新世界创建流程；共享游戏会直接启动。
- `/solo destroy <game-id>` 销毁调用者的 `player_world` 世界，以便使用新名单重建。

玩家快捷栏第 6 格固定提供萤石粉；该物品不能移动或丢弃，右键会打开与 `/solo` 相同的
Solo 目录。

管理命令：

- `/vote stop`
- `/vote forcestart <game-id>`
- `/vote stopgame <实例ID>`：停止一个正在运行的 Scheduler 实例；补全只显示在线实例，例如 `Backstabbed-1`。
- `/vote gamelist`
- `/vote session stop`
- `/vote reload`
- `/vote holograms create`
- `/vote holograms list`
- `/vote holograms remove <id>`
- `/vote lock <player>`
- `/vote unlock <player>`

完整的权限、当前调度器 ID、传送行为、Velocity 组件职责和故障排除请查看[中文使用指南](USER_GUIDE_zh.md)。

## 许可证

本项目采用 [MIT License](../LICENSE)。
