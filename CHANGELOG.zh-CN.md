# Changelog

Companion 的版本记录。桌面 Anya 的变更见 [rururunu/Anya](https://github.com/rururunu/Anya)。

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

[0.1.0]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.0
