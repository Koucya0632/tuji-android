# Play 商店頁與隱私權標籤（草稿）

上架需要的**文字**都在這裡，四種語言各一份。上傳需要通過驗證的開發者帳號，
所以這份文件先寫好，帳號一通過就是貼上去。

前置與順序見 `PLAY-BILLING-SETUP.md`；那份講商品與 Billing，這份講商店頁。

---

## 1. 應用程式資訊

| 欄位 | 值 |
|---|---|
| 套件名稱 | `app.tuji.android` |
| 預設語言 | **English (United States)** |
| 類別 | 教育 / Education |
| 內容分級 | 需填問卷；本 App 有使用者產生內容（物見），要如實回答 |

> ⚠️ 預設語言選英文，與 `values/` 一致。Play 的預設語言是**沒有對應翻譯時的
> fallback**，和 Android 資源解析是同一個道理 —— 選中文會讓一個法國人看到中文。

## 2. 商店頁文案

### English（預設）

- **App name**：Tuji — Picture Dictionary
- **Short description**（80 字以內）：
  `Learn Japanese and English from pictures. Review what you forget, keep what you see.`
- **Full description**：
  ```
  Tuji teaches words the way you meet them — as things, not as a list.

  • A picture dictionary of 557 everyday words across ten themes
  • Spaced review that asks again exactly when you are about to forget
  • Listening questions built on real recordings, not synthesised speech
  • Make your own cards: photograph a thing, and Tuji names it for you
  • Sightings — see and save what other learners have published

  Japanese entries carry furigana. Everything works offline; answers you give
  without a connection are kept and sent when one comes back.
  ```

### 繁體中文

- **應用程式名稱**：Tuji 圖鑑
- **簡短說明**：`用圖片學日文和英文。忘記的會再問一次，看過的留得住。`
- **完整說明**：
  ```
  Tuji 用你遇見它們的方式教單字——當作東西，而不是一張清單。

  • 十個主題、557 個日常單字的圖片字典
  • 間隔複習，在你快忘記的時候剛好再問一次
  • 聽句題用真人錄音，不是合成語音
  • 自製圖鑑：拍下一個東西，Tuji 幫你認出它的名字
  • 物見——看別人發布的，也把它收進自己的圖鑑

  日文詞條附振假名。離線也能用，沒有網路時作答的評分會留著，連上線再送出。
  ```

### 简体中文

- **应用名称**：Tuji 图鉴
- **简短说明**：`用图片学日文和英文。忘记的会再问一次，看过的留得住。`
- **完整说明**：（同繁中，用简体）

### 日本語

- **アプリ名**：Tuji 図鑑
- **簡単な説明**：`写真で日本語と英語を学ぶ。忘れそうな頃にもう一度たずねます。`
- **詳しい説明**：
  ```
  Tuji は、単語をリストではなく「もの」として覚えます。

  • 10 のテーマ、557 語の日常語を写真で引ける図鑑
  • 忘れかけた頃に出題する間隔反復
  • 合成音声ではなく実際の録音を使った聞き取り問題
  • 自作図鑑：ものを撮ると、Tuji が名前を教えます
  • 物見——ほかの学習者が公開したものを見て、保存できます

  日本語の見出しにはふりがながつきます。オフラインでも使え、通信のないときの
  回答は保存され、つながったときに送信されます。
  ```

## 3. 截圖

各語言至少 2 張、最多 8 張，手機尺寸必填。建議這五張，每張都是**真的畫面**
（用 `scripts/emulator.sh shot` 搭配 per-app locale 拍，見下）：

1. 今日 —— 有待複習數字與今日目標
2. 複習中的選字卡 —— 圖片與四個選項
3. 聽句題 —— 模糊的例句與兩張圖
4. 圖鑑書架 —— 十個分類
5. 自製圖鑑的候選畫面 —— 拍完之後的辨識結果

換語言拍圖：

```
adb shell cmd locale set-app-locales app.tuji.android --locales ja-JP
adb shell am force-stop app.tuji.android
```

> 不要用去背或加字的行銷圖冒充截圖。Play 允許裝飾，但審核看的是它與實際
> 畫面是否一致，而這個 App 的畫面本來就長得可以直接用。

## 4. 隱私權標籤（Data safety）

照實填。這個 App 實際收集的：

| 資料類型 | 是否收集 | 是否分享 | 用途 | 可否刪除 |
|---|---|---|---|---|
| 電子郵件地址 | 是 | 否 | 帳號 | 是 |
| 使用者 ID（TJ UID） | 是 | 否 | 帳號 | 是 |
| 照片 | 是 | 否 | 自製圖鑑的辨識與卡片 | 是 |
| App 互動（作答紀錄） | 是 | 否 | 排定複習時間 | 是 |
| 崩潰紀錄／診斷 | 否 | — | — | — |

- **傳輸中加密**：是（全部走 HTTPS）
- **可要求刪除**：是（帳號刪除會連帶刪掉上述全部）
- **照片會離開裝置**：**是，而且要說清楚** —— 拍下的照片會上傳做 AI 辨識。
  這一格填錯是下架等級的問題，不是小事。

> 物見是使用者對使用者的公開內容，所以內容分級問卷裡的「使用者產生內容」
> 要回答「是」，並說明有檢舉與封鎖機制（App 內兩者都有）。

## 5. 還沒能做的

| 項目 | 卡在 |
|---|---|
| 建立商店頁 | 開發者帳號驗證 |
| 上傳截圖 | 同上 |
| 封閉測試 | 同上，且有人數與天數門檻 |
| 送審 | 全部之後 |

封閉測試的門檻**以 Play Console 當下顯示的為準**，並假設它會佔掉數週日曆
時間，無法用加班換取。時程計劃書 §06 把它列為最容易低估的一項。
