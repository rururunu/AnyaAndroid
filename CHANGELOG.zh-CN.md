# Changelog

Companion 的版本记录。桌面 Anya 的变更见 [rururunu/Anya](https://github.com/rururunu/Anya)。

## [0.1.6] - 2026-09-03

暂存队列、应用内主题，以及 agent 结束后对话变空的修复。

### 新增

- Agent 运行中发消息会进入暂存队列（与桌面同一份）。**引导** 为软注入；本轮结束后自动发出下一条暂存
- **设置 → 通用** 可切换主题：跟随系统、浅色、深色
- 上下文占用面板显示本对话缓存命中率（DeepSeek prompt cache，算法与桌面一致）
- 点吉祥物有弹跳式震动反馈

### 修复

- Agent 跑完或在对话里停留一会儿后记录变空：暂存 RPC 返回的 `{ messages: [] }` 被当成聊天历史清空了本地记录
- 附加内容后输入框无法编辑 / 长文本无法滚动：草稿区恢复为可编辑、可滚动

### 说明

暂存队列需要桌面 Anya ≥ 0.2.18（`session.staged.*`）。主题与空会话修复仅在手机端。

## [0.1.5] - 2026-09-03

生图工具栏、相册参考图，以及归档 / 收件箱与桌面 0.2.18 对齐。

### 新增

- 生图模式可调画面比例、清晰度、风格、张数，并可切换桌面生图模型（`imageGen.options` / `imageGen.setModel`）
- 相册图片经 `file.upload` 后以 `path:` 绝对路径作为参考图发送
- 收件结果按对话分组，标题可折叠展开
- 工作区可归档（长按工作区行）；归档页与对话列表同样分「随问 / 工作区」，并支持搜索

### 说明

相册参考图、生图参数与工作区归档需要桌面 Anya ≥ 0.2.18。

## [0.1.4] - 2026-09-02

全新吉祥物，并对齐桌面 Anya 0.2.13–0.2.17：生图模式、生图卡片与 Mermaid 图表。

### 新增

- 移植桌面端吉祥物：启动器 / 启动页图标、连接页与关于页、加载态与空对话页统一换成会眨眼、四处张望、跟随手指、随 Agent 状态转身切换表情、点一下会讨好的小团子；空对话页按当前模式手持道具（手机 / 扳手 / 写字板 / 画笔）
- **生图** 对话模式（对应桌面 `generate_image`）。连接 0.2.13+ 桌面时选择该模式不再导致 compose 同步失败
- 生图工具卡片：解析桌面返回结果，点击后经网关获取工作区副本并支持全屏预览；回复正文里的 `![alt](path:…)` 图片同样渲染为可获取的图块，不再是空链接
- ```` ```mermaid ```` 围栏渲染为可拖动 / 双指缩放的图表，跟随浅色 / 深色主题，可切换查看源码

- 补齐剩余的 Agent 展示面：终端卡片改为深色底 + ANSI 彩色输出；文件编辑卡片行内展示带行号的 diff hunk（仍可一键全屏）；子代理详情面板由父工具的子活动拼出；每条消息的 DeepSeek 缓存命中率；回退（用户消息「回退到此处」/ 代码变更下「撤销本轮更改」），走新增的 `session.rewind` RPC
- 已缓存图片支持「以此图编辑」与「选区涂抹」：在图上涂抹后手机生成与桌面相同的暗化聚光预览，并附带 `![image](path:…)` 让桌面 Images API 以原图作为编辑参考

### 变更

- Agent 执行过程改为与桌面工作时间线一致：流式期间思考、工具调用、中途叙述按实际发生顺序交错展示；结束后前置过程折叠进「已完成」（N 个工具 · N 次搜索 · N 段思考），生成图片与最终回复保留在外。非展示型工具显示为「类型 · 摘要」行，可展开查看详情 / 结果；子代理行内嵌其子任务；`update_tasks` / `todo_write` 渲染为内嵌任务列表
- 回合页脚与桌面一致：小吉祥物按当前状态换表情并配文字（思考中 / 执行工具中 / 回复中 / 等待你的输入），实时与完成后都显示「已处理 N s」，以及「≈ N tokens」和复制按钮。压缩上下文、连接重试 n/m、分析图片等瞬时状态以活动行显示

### 修复

- `hello.appVersion` 改为上报实际安装版本，不再写死 `0.1.2`
- `chat.finished` 不再把待批准的计划门禁 / 工具审批状态覆盖为 Idle

### 说明

需配合桌面 Anya ≥ 0.2.18：网关新增下发 `workTimeline`、`completedAtEpochMs`、`estimatedTokens`、`chat.status`、`chat.userContent`，并允许从 app-data / `peek-public` 目录拉取生成图片。连接旧版桌面时，时间线仅能由实时事件重建，快速提问会话的图片无法获取。Mermaid 渲染依赖 jsDelivr 加载库，需要网络。

## [0.1.3] - 2026-08-21

对齐桌面端的模型元数据，补齐提供商标识、按模型选择的思考强度与实时上下文估算。

### 新增

- 模型选择器按提供商分组，使用桌面端下发的名称与图标，并为常见提供商补全品牌标识
- 按模型能力显示思考控件：优先使用桌面端下发的思考变体，否则提供兼容的推理强度选择
- 上下文占用指示，会随当前模型和输入中的草稿刷新估算值

### 变更

- 所选推理强度会与桌面端的对话编辑状态同步并保持
- 旧版桌面端响应未返回该字段时，保留本地已选的推理强度

### 说明

需要已开启 Remote Gateway 且支持 `context.usage` 与 compose `reasoningEffort` 的桌面 Anya。Companion 不会自行调用模型服务提供商。

## [0.1.2] — 2026-08-15

桌面分享到手机的「获取文件」能打到电脑，Windows 路径也能对上。

### 修复

- 桌面签发的下载地址若是 `http://127.0.0.1:8787/…`，改写到当前局域网或隧道源
- `file.download.begin` 优先用工作区相对路径；HTTP 失败则回退 `workspace.readFile` 分片
- `share_to_companion` 的 Windows 路径不再在第一个空格处被截断

### 说明

需要已开启 Remote Gateway 的桌面 Anya。Companion 不会自己调用模型服务商。

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

[0.1.6]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.6
[0.1.5]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.5
[0.1.4]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.4
[0.1.3]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.3
[0.1.2]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.2
[0.1.1]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.1
[0.1.0]: https://github.com/rururunu/AnyaAndroid/releases/tag/v0.1.0
