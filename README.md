# Anya Companion (Android)

远程连接电脑端 [Anya](../AltAltAi) Agent 的安卓 Companion：发消息、审批、查看代码与工作区状态。

> Agent **只在 PC 上运行**。本应用通过桌面 Remote Gateway（WebSocket）远程操控同一套会话与审批流。

## 架构

```text
app/                     # 组装层：Application、导航、DI 入口
feature/                 # 按功能垂直切片（UI + ViewModel）
  pairing/               # 手动配对（后续扫码）
  sessions/              # 会话列表与连接状态
  chat/                  # 发消息 / 流式回复
  approval/              # 工具审批 / ask_user
  workspace/             # 工作区快照与变更
  settings/              # 断开 / 解除配对
core/
  common/                # Result、Dispatcher 限定符
  model/                 # 协议与领域模型（纯 Kotlin）
  domain/                # Repository 接口 + UseCase
  network/               # OkHttp WebSocket Gateway 客户端
  data/                  # Repository 实现、DataStore 凭证
  designsystem/          # Compose 主题与基础组件
build-logic/             # 约定式 Gradle 插件（工程化）
```

依赖方向（单向）：

```text
app → feature:* → domain ← data
                 ↘ model / common / designsystem
data → network → model
```

详见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 技术栈

| 项 | 选择 |
|---|---|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| 异步 | Kotlin Coroutines + Flow |
| 网络 | OkHttp WebSocket |
| 序列化 | kotlinx.serialization |
| 本地 | DataStore Preferences |
| 构建 | AGP 9.0 · Gradle 9.1 · Kotlin 2.2 · Version Catalog · Convention Plugins |

## 本地运行

1. 用 **Android Studio** 打开本目录（推荐），或命令行构建
2. 确认 `local.properties` 中 `sdk.dir` 指向本机 Android SDK（可参考 `local.properties.example`）
3. 构建：

```bat
gradlew.bat :app:assembleDebug
```

当前环境已验证：`BUILD SUCCESSFUL`（`app-debug.apk`）。

> 本机若使用 **Java 25** 跑 Gradle，需要 **Gradle ≥ 9.1**（已配置）。Android 编译目标仍为 **JVM 17**。

4. 桌面 Anya 侧 Remote Gateway 尚未合入前：配对会落盘设备凭证，真实握手/事件投影待联调。

## 协议草图

见 `core/model/.../protocol/Protocol.kt`：

- 客户端：`hello` / `chat.send` / `approval.respond` / `workspace.*`
- 服务端：`hello.ok` / `event`（投影 BusEvent） / `rpc.result`

## 下一步

1. 在 Anya 桌面端实现 `Remote Gateway`
2. 联调配对握手与事件投影
3. 审批推送通知 + Diff 阅读器
4. QR 扫码配对
5. UI 补全 Compose `Modifier` 布局修饰（骨架阶段为降低工具链摩擦做了精简）
