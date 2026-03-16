# MultiAuth 架构文档

## 项目简介

MultiAuth 是一个用于 [Velocity](https://velocitypowered.com/) 代理服务器的认证插件。它支持多种认证方式，包括 Yggdrasil（正版）认证、本地密码认证（SQLite/MySQL）以及 UniAuth 外部认证，同时与 LimboAPI、Floodgate（基岩版）、TAB 等插件深度集成。

---

## 项目结构

```
MultiAuth/
├── api/                        # 公共 API 模块（供第三方插件使用）
│   └── src/main/java/cn/jason31416/authx/api/
│       ├── AuthX.java              # API 单例访问入口
│       ├── AuthXApi.java           # 插件 API 接口定义
│       ├── AbstractAuthenticator.java  # 认证后端抽象基类
│       ├── IDatabaseHandler.java   # 数据库操作接口
│       └── ILoginSession.java      # 玩家登录会话接口
├── core/                       # 核心插件模块
│   └── src/main/java/cn/jason31416/authX/
│       ├── AuthXPlugin.java        # 插件入口，Velocity @Plugin 注解
│       ├── authbackend/            # 认证后端实现
│       ├── command/                # 命令处理器
│       ├── handler/                # 事件处理与业务逻辑
│       ├── hook/                   # 第三方插件集成钩子
│       ├── injection/              # 数据包注入（拦截加密握手）
│       ├── message/                # 消息与语言文件系统
│       └── util/                   # 工具类
├── lib/
│   └── velocity-3.4.0-SNAPSHOT-523.jar  # 本地 Velocity 依赖
├── build.gradle.kts            # 根构建脚本（Gradle Kotlin DSL）
├── settings.gradle.kts         # 项目设置（模块声明）
├── api/build.gradle.kts        # API 模块构建脚本
├── core/build.gradle.kts       # Core 模块构建脚本（含 Shadow 打包）
└── ARCHITECTURE.md             # 本文档
```

---

## 模块划分

### `api` 模块

对外暴露的公共接口，供依赖 MultiAuth 的第三方插件使用。包含以下核心抽象：

| 类/接口 | 说明 |
|---|---|
| `AuthX` | API 单例，通过 `AuthX.getApi()` 获取 `AuthXApi` 实例 |
| `AuthXApi` | 插件顶层接口：获取认证器、数据库操作器、玩家会话 |
| `AbstractAuthenticator` | 认证后端抽象类，定义注册、认证、注销等操作及 `RequestResult`/`UserStatus` 枚举 |
| `IDatabaseHandler` | 数据库操作接口：UUID 映射、认证方式存储 |
| `ILoginSession` | 玩家登录会话数据：用户名、UUID、认证方式 |

### `core` 模块

插件的完整实现，打包为可部署到 Velocity 服务器的 fat-jar（通过 Shadow 插件合并依赖）。

---

## 核心组件详解

### 1. 插件入口（`AuthXPlugin`）

- 使用 `@Plugin(id = "multiauth", name = "MultiAuth")` 注册为 Velocity 插件
- 在 `onProxyInitialization` 中完成全部初始化：
  1. 读取配置（`Config.init()`）
  2. 加载语言文件（`MessageLoader`）
  3. 初始化数据库（`DatabaseHandler`）
  4. 创建 Limbo 虚拟世界（通过 LimboAPI）
  5. 实例化认证后端（`LocalAuthenticator` / `UniauthAuthenticator`）
  6. 注册事件监听器（`EventListener`）
  7. 注册命令（`/authx`、`/account`）
  8. 注入数据包处理器（`PacketInjector`）
  9. 注册 TAB 占位符（如 TAB 插件已加载）

### 2. 认证流程（`EventListener`）

玩家登录时依次触发以下事件：

```
客户端连接
    │
    ▼
PreLoginEvent（身份判定）
    ├─ 验证用户名（正则）
    ├─ 查询数据库用户状态（REGISTERED / IMPORTED / NOT_EXIST）
    ├─ 判断是否为正版账号（UUID 模式匹配 / HTTP 请求 Yggdrasil）
    │      ├─ 是正版 → 设置在线模式，无需密码
    │      └─ 非正版 → 设置离线模式，需密码验证
    └─ 创建 LoginSession 会话对象
          │
          ▼
LoginLimboRegisterEvent（进入 Limbo 世界）
    └─ 需要密码验证的玩家 → 进入 Limbo 认证世界
          │
          ▼
GameProfileRequestEvent（设置 UUID）
    └─ 根据认证结果设置玩家正确的 UUID
```

### 3. Limbo 认证界面（`LimboHandler`）

玩家进入 Limbo 虚拟世界后，通过聊天框输入密码完成认证：

```
进入 Limbo
    │
    ├─ [新玩家] 状态机: LOGIN → REGISTER_PASSWORD → REGISTER_CONFIRM_PASSWORD
    │      ├─ 验证密码正则
    │      ├─ 验证两次输入一致
    │      └─ BCrypt 哈希后写入数据库
    │
    ├─ [已注册玩家] 状态机: LOGIN
    │      ├─ BCrypt 验证密码
    │      ├─ 超出尝试次数 → 踢出
    │      └─ 倒计时超时 → 踢出
    │
    └─ [正版账号首次绑定密码]
           └─ 强制要求输入密码后继续
```

- BossBar 显示剩余认证时间
- 认证成功后断开 Limbo 连接，玩家进入主服务器

### 4. Yggdrasil 正版验证（`YggdrasilAuthenticator`）

支持多个 Yggdrasil 服务器并行检测（默认：Mojang 官方 + LittleSkin）：

```java
// 异步并行请求所有服务器
List<CompletableFuture<Boolean>> futures = servers.stream()
    .map(server -> checkExists(username, serverId, server))
    .toList();
// 任一服务器确认正版即通过
```

- 10 秒请求超时
- 支持 IP 验证（`verify-ip` 配置项）
- 失败结果缓存 1 小时，避免重复请求

### 5. 数据库层（`DatabaseHandler`）

基于 HikariCP 连接池，支持 SQLite（默认）和 MySQL：

| 数据表 | 说明 |
|---|---|
| `users` | 用户名、BCrypt 哈希密码、注册时间 |
| `authmethods` | 玩家已使用的认证方式（yggdrasil/offline/littleskin）及偏好设置 |
| `uuiddata` | 用户名与 UUID 的映射关系 |
| `passwordbackup` | 密码备份（用于账号恢复） |

HikariCP 在 Shadow 打包时被重定向到 `cn.jason31416.authX.lib.hikari`，避免与服务器上其他插件的 HikariCP 产生冲突。

### 6. 数据包注入（`PacketInjector` / `XLoginSessionHandler`）

通过反射拦截 Velocity 内部的加密握手数据包，将 `EncryptionResponsePacket` 替换为自定义的 `XEncryptionResponse`，从而实现在标准 Velocity 认证流程之外的自定义处理逻辑。

### 7. 命令系统

| 命令 | 权限 | 说明 |
|---|---|---|
| `/authx changepass <player> <newpass>` | `authx.admin` | 强制修改指定玩家密码 |
| `/authx unregister <player>` | `authx.admin` | 删除玩家账号 |
| `/authx setuuid <player> [uuid]` | `authx.admin` | 手动设置玩家 UUID |
| `/authx reload` | `authx.admin` | 重载配置文件 |
| `/authx recover` | `authx.admin` | 从备份恢复账号数据 |
| `/account changepass <old> <new>` | 无 | 玩家自行修改密码 |
| `/account unregister <password>` | 无 | 玩家自行注销账号 |

### 8. 第三方插件集成

| 插件 | 集成方式 |
|---|---|
| **LimboAPI** | 必需依赖，用于创建和管理虚拟认证世界 |
| **Floodgate** | 可选，自动识别基岩版玩家并跳过密码验证 |
| **TAB** | 可选，注册 `%authx-auth-tag%` 占位符，显示玩家认证方式前缀 |

---

## 认证方式汇总

| 认证方式 | 触发条件 | 实现类 |
|---|---|---|
| Yggdrasil（Mojang 正版） | UUID 匹配或 HTTP 验证通过 | `YggdrasilAuthenticator` |
| Yggdrasil（LittleSkin） | LittleSkin 服务器验证通过 | `YggdrasilAuthenticator` |
| 本地密码（SQLite/MySQL） | 非正版玩家，密码存于本地数据库 | `LocalAuthenticator` |
| UniAuth 外部认证 | 配置 `method: UNIAUTH` | `UniauthAuthenticator` |
| Floodgate（基岩版直通） | Floodgate 标识的基岩版玩家 | `FloodgateHandler` |

---

## 构建系统

项目使用 **Gradle Kotlin DSL** 进行构建，分为两个子模块：

- **`:api`** — 编译为 `MultiAuth-<version>-Api.jar`，供第三方插件依赖
- **`:core`** — 通过 `com.gradleup.shadow` 插件打包为包含所有运行时依赖的 fat-jar `MultiAuth-<version>.jar`

```bash
# 编译全部
./gradlew build

# 仅打包 core（fat-jar）
./gradlew shadowJar

# 仅打包 API
./gradlew :api:jar
```

---

## 配置文件说明

主配置文件位于插件数据目录下的 `config.yml`：

| 配置项 | 说明 |
|---|---|
| `lang` | 语言（`en-us` / `zh-cn`） |
| `authentication.filter-method` | 正版判断方式：`UUID`（纯 UUID 匹配）、`REQUEST`（HTTP 验证）、`AUTO`（混合） |
| `authentication.yggdrasil.auth-servers` | Yggdrasil 服务器列表 |
| `authentication.password.method` | 密码后端：`SQLITE` / `MYSQL` / `UNIAUTH` |
| `authentication.password.auth-time` | 认证超时时间（秒） |
| `authentication.password.attempt-count` | 最大密码尝试次数 |
| `limbo-world.*` | Limbo 世界配置（位置、游戏模式、维度） |
| `regex.username-regex` | 用户名合法性正则 |
| `regex.password-regex` | 密码复杂度正则 |

---

## 数据流总览

```
玩家连接
    │
    ▼
[EventListener.onPreLogin]
    ├─ 查 authmethods 表 → 已知认证方式？
    ├─ 调 YggdrasilAuthenticator → 是正版？
    └─ 创建 LoginSession（记录认证状态）
          │
          ▼ 需要密码时
[LimboHandler]（Limbo 虚拟世界）
    ├─ 聊天框接收密码
    ├─ LocalAuthenticator.authenticate() → BCrypt 比对
    └─ 认证成功 → 断开 Limbo，进入主服务器
          │
          ▼ 认证成功后
[DatabaseHandler]
    └─ 更新 uuiddata / authmethods 表
```
