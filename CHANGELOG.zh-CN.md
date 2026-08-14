# Changelog

Companion 的版本记录。桌面 Anya 的变更见 [rururunu/Anya](https://github.com/rururunu/Anya)。

## [0.1.1] — 2026-08-15

多台桌面、主机显示名，以及离开对话后仍能用的收件箱。

### 新增

- 可同时保存多台桌面主机；设置里的主机列表切换，或长按左上角 logo 切换
- 连接时可设定主机显示名（最多 16 字），顶栏「Anya」会换成该名称
- 主机更新隧道域名、令牌等连接信息后，可对该主机重新配对（保留名称）
- **收件 · 结果**：桌面 `share_to_companion` 的文件与链接；可后台分片下载，通知栏看进度
- **收件 · 待确认**：不在那条对话里时，仍能处理工具审批与 `ask_user`

### 说明

需要已开启 Remote Gateway 的桌面 Anya。Companion 不会自己调用模型服务商。

## [0.1.0] — 2026-08-13

首个公开安卓远程控制台。

### 新增

- 桌面二维码 / 手动主机与令牌 / `anya://pair` 配对
- 与电脑同一批会话：流式、取消、Ask / Agent / Plan、切换模型
- 工具审批、`ask_user`、计划批准
- 工作区文件目录、Skills / MCP 列表
- 手机 → 桌面分片上传、桌面 → 手机分片下载（500MB，512KB 分片）
- 局域网 `ws://` 优先，Cloudflare Quick Tunnel `wss://` 回退
- 启动握手超过 5 秒且未收到 `hello.ok` 时可取消连接
- 从 GitHub Releases 应用内更新（`latest.json`）
- 已连接时前台保活

### 说明

需要已开启 Remote Gateway 的桌面 Anya。Companion 不会自己调用模型服务商。

[0.1.1]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.1
[0.1.0]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.0
