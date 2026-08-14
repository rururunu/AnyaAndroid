# 发布与应用内更新

Companion 读取：

`https://github.com/rururunu/AnyaAndroid/releases/latest/download/latest.json`

若没有该文件，再回退到最新 GitHub Release 里的 `.apk` 附件。

## `latest.json` 在哪？

| 位置               | 说明                                                           |
| ------------------ | -------------------------------------------------------------- |
| **Git 仓库**       | 模板：[`../release/latest.json`](../release/latest.json)       |
| **GitHub Release** | 上传的附件，**文件名必须是 `latest.json`**                     |
| **客户端访问**     | `releases/latest/download/latest.json`（最新非预发布）         |

应用标识在 `build-logic` 的 `configureAppDefaults`：当前是 `versionName` `0.1.1`，
`versionCode` `2`。每次旁加载发版必须**两个一起加**。

---

## 发一版

把下面的 `0.1.1` / `v0.1.1` 换成你要发的版本。首个公开标签是 `v0.1.0`。

1. 在 `build-logic` 里同时提高 `versionName` 与 `versionCode`，再构建 **release**
   APK（不要用 `ai.anya.companion.debug`）：

```bat
gradlew.bat :app:assembleRelease
```

产物一般在 `app/build/outputs/apk/release/app-release.apk`。改名为
**`Anya-v0.1.1.apk`**。

2. 把体积和校验和写入 [`../release/latest.json`](../release/latest.json)：

```powershell
$apk = Get-Item .\Anya-v0.1.1.apk
Get-FileHash $apk.FullName -Algorithm SHA256
$apk.Length
```

`version` / `versionCode` 与应用一致，`apkUrl` 填 GitHub 下载地址，`sizeBytes`
填 `$apk.Length`，`sha256` 填小写十六进制。APK 还没打出来时可以先不填体积和哈希
——客户端会跳过这两项校验。

3. 在 GitHub → [Releases](https://github.com/rururunu/AnyaAndroid/releases) 创建标签
   **`v0.1.1`**。
   - 正文用该 tag 的更新说明（见 [`../CHANGELOG.zh-CN.md`](../CHANGELOG.zh-CN.md)）
   - 上传 `Anya-v0.1.1.apk` 和 `latest.json`（**文件名保持 `latest.json`**）

4. 打开 `https://github.com/rururunu/AnyaAndroid/releases/latest/download/latest.json`
   确认，再在 Companion → 关于里点 **检测更新**。

### `latest.json` 字段

| 字段          | 必填 | 作用                                      |
| ------------- | ---- | ----------------------------------------- |
| `version`     | 是   | 与 `versionName`（`0.1.1`）比较           |
| `versionCode` | 是   | 与 Android `versionCode` 比较             |
| `apkUrl`      | 是   | APK 直链                                  |
| `notes`       | 否   | 关于页展示；通知截取前 120 字             |
| `sizeBytes`   | 建议 | 下载进度与完整性                          |
| `sha256`      | 建议 | APK 的十六进制 SHA-256                    |

当前标识对应的地址示例：

`https://github.com/rururunu/AnyaAndroid/releases/download/v0.1.1/Anya-v0.1.1.apk`

**相关：** [架构](./ARCHITECTURE.zh-CN.md) · [更新日志](../CHANGELOG.zh-CN.md) · [文档索引](./README.zh-CN.md)
