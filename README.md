# tuji-android

Tuji 的 Android 版。與 `tuji-ios`（SwiftUI，已上架 1.1.2）功能對等為目標，
後端 `tuji-web` 兩端完全共用、不分岔。

計劃書：〈Tuji Android 架構計劃書〉與〈Tuji Android 時程計劃書〉。
目前進度：**M0 地基**。

## 需要什麼

- JDK 21 — `brew install openjdk@21`
- Android SDK — `brew install --cask android-commandlinetools`，
  再裝 `platform-tools`、`platforms;android-37.1`、`build-tools;37.0.0`、
  `emulator`、`system-images;android-36;google_apis;arm64-v8a`
- `local.properties` 指到 SDK（`sdk.dir=...`），不進版控
- `secrets.properties` — `cp secrets.properties.example secrets.properties` 後填值。
  值與 `tuji-ios/Config/Secrets.xcconfig` 相同

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
```

## 跑起來

```bash
./gradlew build                 # 編譯 + 全部單元測試
./scripts/check-test-counts.sh  # 測試「有沒有真的跑」，不是「有沒有紅」
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`check-test-counts.sh` 不是多餘的。iOS 那邊付過代價：suite 崩潰時 xcodebuild
照樣印 ✔，唯一的差別是測試總數變小。Gradle 的等價情形是某個模組的 test task
被跳過、被過濾成零、或根本沒產出 XML——三種都是 `BUILD SUCCESSFUL`。

## 模組

| 模組 | 內容 | 對應 iOS |
|---|---|---|
| `app` | Compose 畫面與 navigation | `Tuji/Features`、`Tuji/Navigation` |
| `core:auth` | 登入狀態機、Supabase、Google／Apple 通路 | `Tuji/Core/Auth` |
| `core:design` | 紙與墨 token、字型 cascade、共用元件 | `Tuji/Core/Theme`、`Tuji/Components` |
| `core:model` | DTO 與純邏輯（無 Android 相依，測試跑在 JVM 上） | `Tuji/Core/Models` |
| `core:network` | Ktor client、endpoint 與存取策略 | `Tuji/Core/Networking` |

## 已經踩過的雷，不要再踩一次

這些是 iOS 端已經付出代價學到的，寫在對應檔案的註解裡：

- **`learning` 的 wire 值是 `zh-ja` 不是 `ja`。** 傳錯會拿到 200 加上英文圖鑑——
  比報錯更糟，因為它看起來像資料。
- **401 重試要重送同一個 request，不是重建它。** iOS 的舊版從原始 body 重建，
  結果重試的照片上傳整個沒有 body。
- **JSON 要容忍未知欄位。** 後端加欄位不會通知客戶端。
- **Postgres NUMERIC 會序列化成字串。** `"0.9500"` 不是 `0.95`，
  硬解 Double 會變成使用者看到的「資料解析失敗」。
- **CJK 字型不能整組蓋掉拉丁字型**（ADR-0003）。見 `docs/SPIKE-FURIGANA.md`。
- **測試不要斷言在地化文案。** 斷言決策，不斷言句子。
- **伺服器的錯誤訊息不要直接顯示給使用者。** iOS 的登入畫面曾經印出一段
  「Service for this project is restricted due to… exceed_cached_egress_quota」——
  那是給開發者看的帳單通知。`AuthFailure` 是 enum 就是為了讓這件事在結構上不可能發生。
- **Debug build 打的是正式 Supabase。** 這個專案的 dev 與 prod 是同一個 project ref，
  模擬器上動的是真實使用者的資料。

## 文件

- `docs/SPIKE-FURIGANA.md` — M0 的日文排版閘門，含結論與 `minSdk` 的決定
- `docs/AUTH-SETUP.md` — 登入在控制台上的三步設定（GCP／Supabase），**沒做不會編譯失敗，只會在使用者按下去之後失敗**
- `../docs/android/PLAY_LAUNCH_PLAN.md` — 上架計劃
- `../docs/android/PLAY_COMPLIANCE_CHECKLIST.md` — 送審前逐項勾選
