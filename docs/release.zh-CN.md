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

应用标识：`versionName` `0.1.0`，`versionCode` `1`（`build-logic` 的 `configureAppDefaults`）。每次旁加载发版必须**两个一起加**。

---

## v0.1.0 清单

1. 构建 **release** APK（不要用 `ai.anya.companion.debug`）：

```bat
gradlew.bat :app:assembleRelease
```

产物一般在 `app/build/outputs/apk/release/app-release.apk`。改名为 **`Anya-v0.1.0.apk`**。

2. 把体积和校验和写入 [`../release/latest.json`](../release/latest.json)：

```powershell
$apk = Get-Item .\Anya-v0.1.0.apk
Get-FileHash $apk.FullName -Algorithm SHA256
$apk.Length
```

`sizeBytes` 填 `$apk.Length`，`sha256` 填小写十六进制。APK 还没打出来时可以先不填——客户端会跳过这两项校验。

3. 在 GitHub → [Releases](https://github.com/rururunu/AnyaAndroid/releases) 创建标签 **`v0.1.0`**。
   - 正文粘贴 [`../release/v0.1.0.md`](../release/v0.1.0.md)
   - 上传 `Anya-v0.1.0.apk` 和 `latest.json`（**文件名保持 `latest.json`**）

4. 打开 `https://github.com/rururunu/AnyaAndroid/releases/latest/download/latest.json` 确认，再在 Companion → 关于里点 **检测更新**。

### `latest.json` 字段

| 字段          | 必填 | 作用                                      |
| ------------- | ---- | ----------------------------------------- |
| `version`     | 是   | 与 `versionName`（`0.1.0`）比较           |
| `versionCode` | 是   | 与 Android `versionCode` 比较             |
| `apkUrl`      | 是   | APK 直链                                  |
| `notes`       | 否   | 关于页展示；通知截取前 120 字             |
| `sizeBytes`   | 建议 | 下载进度与完整性                          |
| `sha256`      | 建议 | APK 的十六进制 SHA-256                    |

v0.1.0 地址：

`https://github.com/rururunu/AnyaAndroid/releases/download/v0.1.0/Anya-v0.1.0.apk`

**相关：** [架构](./ARCHITECTURE.zh-CN.md) · [更新日志](../CHANGELOG.zh-CN.md) · [文档索引](./README.zh-CN.md)
