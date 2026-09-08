# 登入的設定步驟（程式碼之外）

程式已經接好，但 Google 與 Apple 兩條路各有一段**只能在控制台上做**的設定。
沒做的話不會編譯失敗、不會當機，只會在使用者按下按鈕之後失敗，
而且錯誤訊息不會告訴你少了哪一步。

Email 登入不需要下面任何一步，現在就能用。

---

## 1. Google — GCP 的 Android OAuth client

Credential Manager 要求呼叫端 App 的簽章憑證已經登記在 GCP。
**每一把簽章金鑰都要一個**，所以 debug 與 release 各一個。

GCP Console → APIs & Services → Credentials → Create credentials →
OAuth client ID → **Android**：

| 欄位 | 值 |
|---|---|
| Package name | `app.tuji.android.debug`（debug）／`app.tuji.android`（release） |
| SHA-1（debug，本機 `~/.android/debug.keystore`） | `F4:17:C9:DF:1D:59:71:FE:02:D1:A0:9D:51:6F:EC:DF:3A:73:E7:D9` |
| SHA-1（release） | 由 Play App Signing 提供，上架流程走完才會有 |

> ⚠️ 那個 debug SHA-1 是**這台機器**的。換一台機器開發、或 CI 上跑 instrumented
> 測試，都會是不同的 SHA-1，各自要再登記一次。取得方式：
>
> ```
> keytool -list -v -keystore ~/.android/debug.keystore \
>   -alias androiddebugkey -storepass android -keypass android
> ```

**這個 Android client ID 不會寫進程式碼。** 它的作用只是讓 Google 驗證呼叫端；
App 傳給 Credential Manager 的是 **web** client ID（`TUJI_GOOGLE_WEB_CLIENT_ID`，
在 `secrets.properties`）。這一點很容易搞反——**而且第一次就搞反了**。

### 那個 web client ID 不是 iOS 用的那個

這份文件原本寫「與 iOS 用的是同一個」。錯的，2026-09-08 從 GCP 憑證頁對出來：

| 名稱 | 類型 | ID 開頭 | 誰用 |
|---|---|---|---|
| `Tuji iOS (debug)` | **iOS** | `…fih6` | iOS 的 `TUJI_GOOGLE_CLIENT_ID` |
| `EEPD Web` | **Web application** | `…k3od` | Supabase 自己的 OAuth，**以及 Android** |

`GetSignInWithGoogleOption` 的 `serverClientId` 要的是**後端的 web client**，
而這裡的後端是 Supabase——打 `authorize?provider=google` 時它 302 導向
`accounts.google.com` 帶的 `client_id` 就是 `…k3od` 那個。填 iOS 的那把不會
編譯失敗，只會在使用者按下按鈕之後拿到一個看不出原因的錯誤。

iOS 用 iOS client 是**對的**：兩邊要的東西本來就不一樣。從 iOS 的設定檔
抄值過來，是這個缺陷的來源。

## 2. Google — Supabase 的授權 client ID 清單

Supabase Dashboard → Authentication → Providers → Google →
**Authorized Client IDs** 要包含那個 web client ID。iOS 已經在用，所以多半已經在了。

專案層級的 **Skip nonce checks 目前是開的**（為了 iOS SDK，它拿不到 nonce）。
Android 這邊仍然會送 nonce ——
現在不影響任何事，但哪天有人把那個開關關掉，Android 會繼續能用，
而不是在幾週之後爆掉一個沒人聯想得到是儀表板改動造成的錯誤。

## 3. Apple — Supabase 的 redirect URL

Apple 在 Android 上沒有原生登入，流程會離開 App 進 Custom Tab，
再透過 deep link 回來。

Supabase Dashboard → Authentication → URL Configuration →
**Redirect URLs** 加入：

```
app.tuji.android://auth-callback
```

這個 scheme 刻意**不隨 build type 變**（debug 的 applicationId 有 `.debug` 尾綴，
但 redirect 沒有），所以只需要登記一筆。對應的 intent-filter 在
`app/src/main/AndroidManifest.xml`。

## 4. Apple — Services ID 與金鑰（**這一段本來寫錯了**）

這份文件原本說「Service ID 與 return URL 是 iOS 上架時就設好的，Android 不需要
另外開」。**那是錯的**，2026-09-08 實測推翻：

```
GET {SUPABASE_URL}/auth/v1/authorize?provider=apple&redirect_to=app.tuji.android://auth-callback
→ HTTP 400 {"error_code":"validation_failed",
            "msg":"Unsupported provider: missing OAuth secret"}

對照組（Google 同一個呼叫）→ HTTP 302，導向 accounts.google.com
```

原因是**兩條 Apple 流程需要的東西不一樣**：

| | iOS 走的 | Android 走的 |
|---|---|---|
| 機制 | 原生 Sign in with Apple，直接拿 `id_token` | 網頁 OAuth，離開 App 進瀏覽器 |
| Supabase 需要 | Client ID 清單填 **bundle ID** | Client ID 填 **Services ID**，外加一把 **secret** |
| 目前狀態 | 已設定（`app.tuji.ios` 等三個） | `external_apple_secret` 是空的 |

所以 Android 的 Apple 登入要多做這些，全部在 Apple Developer：

1. **Identifiers → Services IDs** 新增一個（例如 `app.tuji.signin`）。
   它不是 bundle ID，是另一種識別碼，之後會變成 Supabase 的 Apple Client ID。
2. 該 Services ID 的 **Sign in with Apple → Configure**：
   - Primary App ID 選現有的 `app.tuji.ios`
   - **Return URLs** 填 Supabase 的 callback，不是 App 的 scheme：
     `https://<project-ref>.supabase.co/auth/v1/callback`
3. **Keys** 新增一把 Sign in with Apple 金鑰，下載 `.p8`（**只能下載一次**）。
4. 用 Team ID／Key ID／Services ID／`.p8` 簽出一個 client secret JWT
   （Supabase Dashboard 的 Apple provider 頁面有產生器）。
5. Supabase Dashboard → Authentication → Providers → Apple：
   - Client IDs 加上那個 **Services ID**（原本的 bundle ID 要保留，iOS 還在用）
   - Secret Key 填上第 4 步的 JWT

> ⚠️ 第 4 步簽出來的 JWT **有效期最長六個月**，到期後 Android 的 Apple 登入
> 會整條停掉而 iOS 不受影響——因為 iOS 根本不走這條。到期日要記在行事曆上。

### 2026-09-08 已完成，實際用的值

| 項目 | 值 |
|---|---|
| Services ID | `app.tuji.signin` |
| Primary App ID | `app.tuji.ios` |
| Return URL | `https://pobmxnxdftnvdmnbkmvi.supabase.co/auth/v1/callback` |
| Team ID | `TH28V27744` |
| Key ID | `3Y26WS7U67` |
| **client secret 到期日** | **2027-03-07 UTC** ← 到期前要重簽 |

`.p8` 在 `~/Desktop/tuji_docs/`，**不在版控裡**，而且 Apple 只給下載一次。

重簽的工具不需要 PyJWT（本機沒裝）：
`scripts/apple-client-secret.py <p8> <TeamID> <KeyID> <ServicesID>`，
只用 openssl 簽，然後把 DER 簽章轉成 ES256 要的 raw r‖s。

**兩個踩到的坑：**

1. **`external_apple_client_id` 的第一個才是網頁流程用的 client_id。**
   把 Services ID 加在清單*最後*，Supabase 仍然送 `app.tuji.ios.debug`
   給 Apple，Apple 回 `invalid_request`。要放**第一個**；其餘 bundle ID
   留在後面，iOS 的原生路徑照樣通過。
2. **設定改完不會立刻生效。** PATCH 回 200 之後仍會讀到舊的 client_id，
   等幾十秒才換。改完先重打 authorize 確認，不要急著再改一次設定——
   否則會把生效延遲當成設定錯誤，愈改愈亂。

驗證方式（**先問 Apple，再寫 Supabase**）：拿簽好的 secret 打 Apple 的
token 端點，帶一個假的 code。

```
invalid_grant  → client_id 與 secret 都對，只有 code 是假的   ← 要的答案
invalid_client → client_id 或 secret 有問題
```

這樣不會把一份壞設定寫進正式站才發現。

---

## 2026-09-08 實際查到的狀態

用 Supabase Management API 讀 `config/auth` 對照出來的，不是照文件推的：

| 項目 | 狀態 |
|---|---|
| Google provider 已啟用 | ✅ |
| Google client ID 清單含 Android 用的那個（`…k3od…`／EEPD Web） | ✅ 已經在裡面 |
| `skip_nonce_check` | ✅ 開著（見上面第 2 節） |
| **GCP 的 Android OAuth client** | ❌ 只能在 GCP Console 開，沒有 API |
| Apple provider 已啟用 | ✅ 但只有 iOS 的原生路徑 |
| **Apple 的 Services ID 與 secret** | ❌ `external_apple_secret` 是空的 |
| `app.tuji.android://auth-callback` 在 redirect 清單裡 | ✅ 2026-09-08 用 Management API 補上（原本三條保留） |

也就是說 **Google 只差 GCP 那一步**（Supabase 這側早就好了），
而 **Apple 差一整段 Apple Developer 的設定**。

### 那條 redirect URL 是給誰用的

不是給 Google 的。兩條路走的機制不同：

| | Google | Apple |
|---|---|---|
| 程式 | `GoogleCredentialBridge` → Credential Manager | `supabase.auth.signInWith(Apple)` |
| 流程 | **原生**，直接拿 `id_token` 交給 Supabase | **離開 App** 進 Custom Tab，再 deep link 回來 |
| 用到 redirect URL 嗎 | 否 | 是 |

所以補上那條 redirect 是 **Apple 的前置**，它本身不會讓任何一條路變得能用——
Google 仍然卡在 GCP，Apple 仍然卡在 Services ID。

---

## 驗證順序

1. **Email** — 現在就能驗，不需要上面任何一步。
2. **Google** — 做完 1 與 2 之後，在**有 Google Play 服務且已登入 Google 帳號**
   的裝置上測。兩個實測到的坑：

   - **這個專案的 HUAWEI（LIO-L29 / Mate 30 Pro）不能用來驗。**
     它是 2019 禁令後的機器，`pm list packages | grep com.google.android.gms`
     回 0 —— 完全沒有 Play 服務。Google 登入在上面**不可能成功**，
     和設定對不對無關。它側載了幾個 Google App，很容易誤判成「有 Google」。
   - **本機的模擬器可以用。** 它是 **Play Store 映像**（`com.google.android.gms`
     與 `com.android.vending` 都在），只是預設沒有登入帳號。
     用 `adb shell am start -a android.settings.ADD_ACCOUNT_SETTINGS` 加一個即可。
     （早期版本的這份文件說模擬器不能測，那是把 `google_apis` 映像
     和 Play Store 映像搞混了。）

   分辨裝置能不能用，一行就夠：
   ```
   adb -s <serial> shell pm list packages | grep -c com.google.android.gms
   ```
   回 0 就換一台，不要浪費時間查設定。
3. **Apple** — 做完 3 之後，觀察是否開啟瀏覽器、且回到 App 時已登入。
   回不來的話先查 redirect URL 有沒有登記，再查 intent-filter。

## 失敗長什麼樣

| 現象 | 多半是 |
|---|---|
| Google 按下去立刻失敗，log 有 `NoCredentialException` | 裝置上沒有 Google 帳號，或步驟 1 沒做 |
| Google 拿到 token 但 Supabase 拒絕 | 步驟 2 的 client ID 清單 |
| Google 跳出帳號選單、選完卻失敗 | `TUJI_GOOGLE_WEB_CLIENT_ID` 填成 iOS 那把了（見第 1 節） |
| Apple 開了瀏覽器，登入完回到 App 仍是登出 | 步驟 3 的 redirect URL，或 intent-filter 沒對上 |
| 畫面顯示「登入沒有成功，請稍後再試」 | 這是 `AuthFailure.Unknown` 的文案。**真正的原因在 logcat 的 `TujiAuth` tag**——伺服器的英文訊息刻意不顯示給使用者 |
