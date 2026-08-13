# Releases and in-app updates

Companion reads:

`https://github.com/rururunu/AnyaAndroid/releases/latest/download/latest.json`

If that file is missing, it falls back to the latest GitHub Release `.apk` asset.

## Where is `latest.json`?

| Location           | Notes                                                          |
| ------------------ | -------------------------------------------------------------- |
| **Git repo**       | Template: [`../release/latest.json`](../release/latest.json)   |
| **GitHub Release** | Uploaded asset; **must be named `latest.json`**                |
| **Client URL**     | `releases/latest/download/latest.json` (latest non-prerelease) |

App identity: `versionName` `0.1.0`, `versionCode` `1` (`build-logic` `configureAppDefaults`). Bump **both** on every store-less sideload.

---

## v0.1.0 checklist

1. Build a **release** APK (not `ai.anya.companion.debug`):

```bat
gradlew.bat :app:assembleRelease
```

Output is typically `app/build/outputs/apk/release/app-release.apk`. Rename to **`Anya-v0.1.0.apk`**.

2. Fill size and checksum into [`../release/latest.json`](../release/latest.json):

```powershell
$apk = Get-Item .\Anya-v0.1.0.apk
Get-FileHash $apk.FullName -Algorithm SHA256
$apk.Length
```

Set `sizeBytes` to `$apk.Length` and `sha256` to the hex hash (lowercase). Leave them out only if the APK is not built yet — the client then skips those checks.

3. On GitHub → [Releases](https://github.com/rururunu/AnyaAndroid/releases), create tag **`v0.1.0`**.
   - Body: paste [`../release/v0.1.0.md`](../release/v0.1.0.md)
   - Attach `Anya-v0.1.0.apk` and `latest.json` (filename must stay `latest.json`)

4. Confirm `https://github.com/rururunu/AnyaAndroid/releases/latest/download/latest.json` and tap **Check for updates** in Companion → About.

### `latest.json` fields

| Field         | Required | Role                                      |
| ------------- | -------- | ----------------------------------------- |
| `version`     | yes      | Compared to `versionName` (`0.1.0`)       |
| `versionCode` | yes      | Compared to Android `versionCode`         |
| `apkUrl`      | yes      | Direct download URL for the APK           |
| `notes`       | no       | Shown on About; notification uses 120 chars |
| `sizeBytes`   | recommended | Download progress + integrity           |
| `sha256`      | recommended | Hex SHA-256 of the APK                  |

v0.1.0 URL:

`https://github.com/rururunu/AnyaAndroid/releases/download/v0.1.0/Anya-v0.1.0.apk`

**Related:** [Architecture](./ARCHITECTURE.md) · [Changelog](../CHANGELOG.md) · [Docs index](./README.md)
