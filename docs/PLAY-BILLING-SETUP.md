# Play Billing 的設定步驟（程式碼之外）

M4 的完成定義是「用測試帳號完成一次真實購買，權益在 iOS 上同一帳號也生效」。
**那句話裡沒有一個字是程式碼做得到的** —— 它需要一個通過驗證的 Play 開發者
帳號、一個建好的訂閱商品、以及設定好的授權測試者。

2026-09-30 的狀態：**開發者帳號（個人）已驗證，應用程式 `app.tuji.android` 已建立，上傳金鑰已設定。** 下一步是第 3 節。

App 這側已經做好的部分見最後一節。

---

## 為什麼這份清單要先寫

時程計劃書 §06 說得很直接：封閉測試的日曆時間**可能比 M5 整個里程碑還長**，
而且完全不受寫程式的速度影響。把步驟先寫下來，帳號一通過就能照著做，不用
那天再重新查一次。

---

## 1. 開發者帳號（已完成，個人帳號）

- Google Play Console 註冊，付一次性費用
- 身分驗證；若以個人身分註冊還要地址驗證
- **必須在能建立商店頁之前完成**

> 這一步之後的每一步都依賴它。沒通過之前，下面的畫面在 Console 裡根本不存在。

## 2. 建立應用程式

| 欄位 | 值 |
|---|---|
| 應用程式名稱 | Tuji 圖鑑 |
| 套件名稱 | `app.tuji.android` |

套件名稱**建立後不能改**，而且要跟 release 版的 `applicationId` 一致
（debug 版的 `.debug` 尾綴只用於本機，不上架）。

## 3. 上傳一個版本到測試軌道

### 上傳金鑰

release 版用**上傳金鑰**簽章；使用者裝到的版本由 Play App Signing 用 Google
保管的另一把金鑰重簽。

- 金鑰：`~/.tuji-android/upload-keystore.jks`（alias `upload`，RSA 4096）
- 密碼與路徑：repo 根目錄的 `keystore.properties`（已在 `.gitignore`，權限 600）
- 沒有這個檔案時 release 版不簽章，其餘一切照常 —— CI 就是這樣

**兩個都要備份**（例如密碼管理器）。遺失上傳金鑰不會失去 App，可以在 Play
Console 申請重設，但要等 Google 處理。

產出上傳用的 AAB：

```sh
./gradlew :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

每次上傳 `versionCode` 都要比上一次大。

訂閱商品必須在**已上傳過含 Billing 函式庫的 APK/AAB** 之後才能建立。
先上內部測試軌道即可，不需要送審。

上傳後 Play App Signing 會給一個 **release SHA-1**，那要回到 GCP 再建第二個
Android OAuth client（見 `AUTH-SETUP.md` 第 1 節），否則 release 版的 Google
登入會失敗而 debug 版正常。

## 4. 建立應用程式內商品（一次性）

2026-10 起 Android 只賣 iOS 罐頭點數制下賣的東西：**永久會員**與**點數包**。
Pro 訂閱不在 Android 販售。商品 ID 與 iOS 完全相同，後端用同一份清單判斷。

Play Console → 營利 → 產品 → 應用程式內產品

| 商品 ID | 類型 | 價格（對齊 iOS） |
|---|---|---|
| `app.tuji.lifetime` | 一次性（App 不消耗；後端 acknowledge） | 對齊 iOS 永久會員 |
| `app.tuji.credits.1000` | 一次性（後端 consume） | USD 0.99 |
| `app.tuji.credits.4000` | 一次性（後端 consume） | USD 2.99 |
| `app.tuji.credits.7000` | 一次性（後端 consume） | USD 4.99 |

> ⚠️ 商品 ID **建立後不能改也不能重複使用**。四個都要「啟用」，App 才查得到價格。
> 「消耗型／非消耗型」在 Play 不是商品屬性，是由後端呼叫 consume 與否決定。

## 5. 授權測試者

Play Console → 設定 → 授權測試 → 加入測試帳號的 Gmail。

授權測試者的購買 `purchaseType = 0`，後端視為 **sandbox**：
- 永久會員照常生效（與 Apple sandbox 相同，審查需要）。
- 點數包只會入帳到 sandbox 錢包，所以測試帳號要列在 `AI_CREDITS_REVIEW_USER_IDS`；
  否則後端回 503、不 consume，Google 三天後自動退款。

## 6. 後端：服務帳號（`tuji-web`）

後端已實作（`lib/billing/play*.ts`）：

```
POST /api/billing/play/verify   ← App 送 { productId, purchaseToken }
GET  /api/cron/play-voided      ← 每日 04:00 UTC（vercel.json），讀 Voided Purchases API 處理退款
```

**不使用 Pub/Sub／RTDN。** 一次性商品沒有續訂，退款每天掃最近 30 天即可，
全部寫入都冪等，漏跑一天也不會漏退款。

要做的設定：

1. GCP → IAM → 建立 service account，建立 JSON 金鑰。
2. GCP 啟用 **Google Play Android Developer API**。
3. Play Console → 使用者和權限 → 邀請該 service account email，給本 App：
   「查看財務資料」與「管理訂單和訂閱」。
4. Vercel 環境變數：
   - `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` = 整份 JSON 金鑰
   - （選填）`GOOGLE_PLAY_PACKAGE_NAME`，預設 `app.tuji.android`
   - 點數包另需既有的 `CREDITS_PURCHASE_ENABLED`／`CREDITS_STORE_PROCESSING_ENABLED`

沒設定金鑰時，verify 回 503（不授權、不 acknowledge → Google 自動退款），cron 回 `disabled`。

### 交付順序：先授權，再 acknowledge / consume

Google 對三天內沒 acknowledge 的購買自動退款。所以後端**先寫入權益，成功後才**
acknowledge（永久會員）或 consume（點數包）。授權失敗 = 錢自動退回；
App 每次啟動、開會員頁、按「恢復購買」都會重送 Play 還列著的未完成購買。

已經有付費永久會員（任一商店）再買一次 → 後端回 409、**刻意不 acknowledge** → Google 自動退款。

## 7. 送審與封閉測試

新的個人開發者帳號有「一定人數測試者、持續一定天數」的要求。
**以 Play Console 當下顯示的為準**，並假設它會佔掉數週日曆時間。

---

## App 這側已經做好的（2026-10-09）

| 東西 | 狀態 |
|---|---|
| Play Billing 9.1.0 客戶端（`billing/PlayBilling.kt`） | ✅ |
| 購買綁帳號：`setObfuscatedAccountId(Tuji user id)` | ✅ 後端只授權給同一 id |
| 會員方案頁：永久會員購買鈕、點數包加購、恢復購買、狀態提示 | ✅ |
| 未完成購買自動補交付（啟動／開頁／恢復購買） | ✅ |
| 後端 Google 驗證與退款 cron | ✅（`tuji-web`） |
| Play Console 商品、服務帳號、授權測試者 | ⛔ 第 4–6 節，需人工 |
