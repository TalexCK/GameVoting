# GameVoting Plugin

## 简介

一个支持 CloudNet v4 的 Minecraft 投票插件，提供了游戏投票功能和可自定义的 GUI 界面。

## 特性

- ✅ **CloudNet v4 支持** - 使用官方 Driver API 进行集成
- ✅ **ChestUI 工具** - 内置的箱子界面系统，支持分页
- ✅ **异步操作** - 所有 CloudNet 操作都是异步的
- ✅ **服务管理** - 完整的 CloudNet 服务管理功能

## 依赖要求

- **Minecraft**: 1.20.1+
- **Java**: 17+
- **CloudNet**: 4.0.0-RC10+
- **平台**: Paper/Spigot

## CloudNet v4 集成

### 对接方式

本插件使用 **CloudNet Driver API**。

### 优势

- ✅ 原生 CloudNet 集成
- ✅ 更高的性能和稳定性
- ✅ 自动依赖注入，无需手动配置连接
- ✅ 支持所有 CloudNet 功能（服务、任务、组）

## 安装

1. 确保服务器运行在 CloudNet v4 环境中
2. 将插件 JAR 文件放入 `plugins/` 目录
3. 重启服务器

**重要**: 此插件必须在 CloudNet 服务上运行，不能也不推荐在独立的 Spigot 服务器上使用。

## 构建

```bash
mvn clean package
```

编译后的 JAR 文件位于 `target/`

## 配置

无需配置！插件会自动连接到 CloudNet 环境。

## 技术架构

### CloudNet Driver API

使用 CloudNet 的依赖注入层获取提供者：

- `CloudServiceProvider` - 服务管理
- `ServiceTaskProvider` - 任务管理
- `GroupConfigurationProvider` - 组配置管理

### ChestUI 系统

提供了完整的箱子界面工具包：

- `ChestUI` - 基础箱子界面
- `PaginatedChestUI` - 分页箱子界面
- `ChestUIBuilder` - 构建器模式创建界面
- `ClickableItem` - 可点击物品封装

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！

