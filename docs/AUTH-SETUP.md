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
在 `secrets.properties`，與 iOS 用的是同一個）。這一點很容易搞反。

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

Apple Developer 那邊的 Service ID 與 return URL 是 iOS 上架時就設好的，
Android 不需要另外開——它走的是 Supabase 的 OAuth，不是 Apple 的原生 SDK。

---

## 驗證順序

1. **Email** — 現在就能驗，不需要上面任何一步。
2. **Google** — 做完 1 與 2 之後，在**有 Google 帳號的實機或 Play 版模擬器**上測。
   `google_apis` 的模擬器映像沒有登入帳號，會拿到 `NoCredentialException`。
3. **Apple** — 做完 3 之後，觀察是否開啟瀏覽器、且回到 App 時已登入。
   回不來的話先查 redirect URL 有沒有登記，再查 intent-filter。

## 失敗長什麼樣

| 現象 | 多半是 |
|---|---|
| Google 按下去立刻失敗，log 有 `NoCredentialException` | 裝置上沒有 Google 帳號，或步驟 1 沒做 |
| Google 拿到 token 但 Supabase 拒絕 | 步驟 2 的 client ID 清單 |
| Apple 開了瀏覽器，登入完回到 App 仍是登出 | 步驟 3 的 redirect URL，或 intent-filter 沒對上 |
| 畫面顯示「登入沒有成功，請稍後再試」 | 這是 `AuthFailure.Unknown` 的文案。**真正的原因在 logcat 的 `TujiAuth` tag**——伺服器的英文訊息刻意不顯示給使用者 |
