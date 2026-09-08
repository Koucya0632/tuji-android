# Play Billing 的設定步驟（程式碼之外）

M4 的完成定義是「用測試帳號完成一次真實購買，權益在 iOS 上同一帳號也生效」。
**那句話裡沒有一個字是程式碼做得到的** —— 它需要一個通過驗證的 Play 開發者
帳號、一個建好的訂閱商品、以及設定好的授權測試者。

2026-09-09 的狀態：**開發者帳號申請中／等驗證。** 所以下面第 1 節之後全部卡住。

App 這側已經做好的部分見最後一節。

---

## 為什麼這份清單要先寫

時程計劃書 §06 說得很直接：封閉測試的日曆時間**可能比 M5 整個里程碑還長**，
而且完全不受寫程式的速度影響。把步驟先寫下來，帳號一通過就能照著做，不用
那天再重新查一次。

---

## 1. 開發者帳號（進行中）

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

訂閱商品必須在**已上傳過含 Billing 函式庫的 APK/AAB** 之後才能建立。
先上內部測試軌道即可，不需要送審。

上傳後 Play App Signing 會給一個 **release SHA-1**，那要回到 GCP 再建第二個
Android OAuth client（見 `AUTH-SETUP.md` 第 1 節），否則 release 版的 Google
登入會失敗而 debug 版正常。

## 4. 建立訂閱商品

Play Console → 營利 → 訂閱項目

| 欄位 | 值 |
|---|---|
| 商品 ID | `tuji_pro_monthly`（暫定，決定後要寫進 `secrets.properties`） |
| 基本方案 | 月繳，自動續訂 |
| 定價 | 對齊 iOS 的定價 |

> ⚠️ 商品 ID **建立後不能改也不能重複使用**。

## 5. 授權測試者

Play Console → 設定 → 授權測試 → 加入測試帳號的 Gmail。

授權測試者購買**不會真的扣款**，但走的是完整的 Billing 流程 —— 這正是計劃書
§05 那個「Play Billing 端到端 spike」要驗的東西。

## 6. 後端：Google Play 驗證器與 RTDN

**這一段目前不存在。** 後端（`tuji-web`）只有 Apple 那條：

```
lib/billing/verifier.ts          Apple SignedDataVerifier
lib/billing/appstore.ts
app/api/billing/verify/route.ts               ← iOS 送 StoreKit JWS 來
app/api/billing/appstore-notifications/route.ts
```

Google 那條要做的：

- **服務帳號**：GCP 建一個 service account，在 Play Console 授權它讀
  「財務資料／訂閱」。金鑰是一個 JSON，放進伺服器的環境變數。
- **驗證端點**：收 Android 送來的 `purchaseToken`，打 Google Play Developer
  API 的 `purchases.subscriptionsv2.get` 驗證，寫進 `user_entitlements`，
  `source` 填 `'play'`。
- **RTDN**：Google 的續訂／退款／過期通知走 Cloud Pub/Sub，不是 webhook。
  要建一個 Pub/Sub topic，在 Play Console 填它的名稱，再讓伺服器訂閱。
  這是與 Apple 最不一樣的一塊，別假設可以照抄。

## 7. 送審與封閉測試

新的個人開發者帳號有「一定人數測試者、持續一定天數」的要求。
**以 Play Console 當下顯示的為準**，並假設它會佔掉數週日曆時間。

---

## App 這側已經做好的（2026-09-09）

| 東西 | 狀態 |
|---|---|
| 我的（帳號、方案、用量、登出） | ✅ |
| 讀 `/api/atlas/entitlement` 與 `/api/users/me` | ✅ |
| ADR-0001 雙商店規則與 `PurchaseGate` | ✅ 8 個測試 |
| 訂閱按鈕 | ⛔ **刻意不顯示**，見下 |
| Play Billing 客戶端 | ⛔ 沒有商品可以查，還沒做 |
| 後端 Google 驗證器與 RTDN | ⛔ 見第 6 節 |

### 訂閱按鈕為什麼是「不顯示」而不是「停用」

`AccountViewModel` 有一個 `billingAvailable` 旗標，預設 `false`。它為 false 時
畫面**不畫那個按鈕**，改成一句話說明還不能訂閱。

停用的按鈕會邀請一次點擊，而那次點擊什麼也學不到；一句話說得出原因，也說得出
什麼會改變它。商店設好的那天，這個旗標翻成 `true`，只有一個地方要改。

`PurchaseGate` 的規則（ADR-0001）與這個旗標是**兩件事**：前者回答「這個帳號
可不可以買」，後者回答「有沒有東西可以賣」。兩個都要成立才會出現按鈕。
