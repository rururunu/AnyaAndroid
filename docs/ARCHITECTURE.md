# Anya Companion — Architecture

## Product role

Phone app is a **remote console**. Desktop Anya owns Agent runtime, tools, SQLite, and approvals.
This repo speaks WebSocket protocol to a future Desktop `Remote Gateway`.

## Module map

| Module | Type | Responsibility |
|---|---|---|
| `:app` | application | Hilt app, NavHost, wiring |
| `:feature:*` | android + compose | Screen + ViewModel only |
| `:core:domain` | jvm | Repository contracts + use cases |
| `:core:model` | jvm | Protocol + domain models |
| `:core:common` | jvm | `AnyaResult`, dispatcher qualifiers |
| `:core:network` | android | OkHttp gateway client |
| `:core:data` | android | Repository impl + DataStore credentials |
| `:core:designsystem` | android + compose | Theme / shared UI atoms |
| `build-logic` | included build | Convention plugins |

## Dependency rules

1. `feature` → `domain` / `model` / `designsystem` / `common` only
2. `data` implements `domain` and uses `network`
3. `app` depends on all features + data (pulls Hilt modules onto classpath)
4. No feature → feature dependencies
5. No UI types in `domain` / `model`

## Runtime flow

```text
UI (feature) → UseCase → Repository (domain)
                         ↑
                    data impl → RemoteGatewayClient → Desktop Anya
```

## Convention plugins

- `anya.android.application` — app defaults, Compose, Hilt, build types
- `anya.android.library` — shared Android library baseline
- `anya.android.feature` — library + Compose + Hilt + core deps
- `anya.jvm.library` — pure Kotlin modules (`model` / `domain` / `common`)
