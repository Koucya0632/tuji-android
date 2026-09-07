# Spike: 日文排版 — furigana 與 CJK cascade

- **日期**：2026-09-08
- **里程碑**：M0（時程計劃書 §05 的第一道閘門）
- **結論**：**通過。介面方案不需要改。**
- **附帶結果**：`minSdk` 由計劃書的 26 拉到 **29**（2026-09-08 拍板）。

〈時程計劃書〉把這件事排在第一週而不是介面做完之後，理由是它是唯一可能推翻
整個文字層方案的技術未知。以下是實際跑出來的結果，不是評估。

---

## 問了什麼

兩件事，而且都不是「build 綠了」能回答的：

1. **furigana 的切分畫得出來嗎。** 用正式站 `/api/words` 的真實資料，不是 fixture。
2. **CJK cascade 成不成立。** Latin 必須留在 Plus Jakarta，漢字必須來自 GenSenRounded。
   Android 沒有 iOS 的 `UIFontDescriptor.cascadeList`。

## 怎麼驗的

`CjkCascadeProbe`（`core/design`）同一個字串印三行，只差在字型堆疊：

| 行 | 字型堆疊 | 代表什麼 |
|---|---|---|
| `token` | Plus Jakarta + GenSenRounded（App 實際用的） | 正確答案 |
| `latin only` | Plus Jakarta，不宣告 fallback | cascade 完全沒作用時的樣子 |
| `cjk only` | GenSenRounded 當主字型 | ADR-0003 要防的事故 |

**這個 spike 有對照組是刻意的。** 這個專案在 Cloudflare Workers 那次學過：
沒有對照組的 spike，會把自己造的測試資料錯誤讀成「平台不支援」。

判讀規則：`token` 的 Latin 必須等於 `latin only`、且不等於 `cjk only`；
`token` 的 CJK 必須等於 `cjk only`、且不等於 `latin only`。

## 結果

在 API 36 模擬器上截圖，逐像素比對三行：

```
token       x-extent 64..804  width=741
latin only  x-extent 64..804  width=741
cjk only    x-extent 65..842  width=778

token 與 latin only 的差異只出現在這些欄位：
  x  178..298   ← 圖鑑
  x  448..492   ← 日
  x  504..625   ← 本語
```

也就是說：**Latin 的部分逐像素相同，差異全部落在漢字上。**
`cjk only` 則整行都不一樣（連寬度都多 37px），因為它的 Latin 也被換掉了。

視覺上：`token` 的 圖鑑 有圓角收筆（GenSenRounded），`latin only` 的 圖鑑 是方角
（系統的 Noto Sans CJK），`cjk only` 的 `Tuji` 是圓點的 `j`、不同的 `T`。

furigana 本身：557 個詞裡 295 個有切分，全部畫得出來，包含 `風呂いす` 這種
**一個 ruby 蓋兩個漢字** 的 熟字訓 區塊——那正是 ADR-0006 說「切分是範圍不是
單字」的原因。

## 怎麼做到的

`Typeface.CustomFallbackBuilder`：Plus Jakarta 當主 family，GenSenRounded
`addCustomFallback`，再 `setSystemFallback("sans-serif")` 收尾（emoji 與兩套字型
都沒有的文字需要它，但順序上自訂 fallback 必須排在系統前面）。

`FuriganaHeadword` 沒有 `ViewThatFits` 可用，所以改成明確測量：
用 `TextMeasurer` 量 `FuriganaScaleLadder` 的每一階，取第一個放得下的。
這其實是更誠實的寫法——`ViewThatFits` 跑的就是同一套算術，而 iOS 那版的前身
正是在這裡出錯：算了一個 scale、把 segment 放在縮放後的位置，然後讓每個 `Text`
用全尺寸畫上去疊在鄰居身上。

---

## 拍板了：`minSdk` 26 → 29

`CustomFallbackBuilder` 是 **API 29**（Android 10）。架構計劃書選的 `minSdk` 是 **26**
（Android 8.0）。也就是說計劃書把地板設在了這個能力出現之前。

26–28 這三個版本沒有 cascade 可用，而且只有兩條路，沒有第三條：

| 做法 | 26–28 的後果 |
|---|---|
| 只設 Plus Jakarta，CJK 交給系統替補 | 拉丁字正確，漢字是 Noto 不是圓體 |
| 把 GenSenRounded 設成主字型 | 漢字正確，**但全 App 的拉丁字被換成 GenSenRounded 自己的 Source Sans 衍生體** |

第二條是 ADR-0003 存在的理由，不可能選。第一條可以出貨，但那是三個 Android
版本上永遠錯的字型。

**決定：把地板拉到 29，兩條都不走。**

理由不是「5% 很少」，而是這個專案已經在 iOS 上做過更嚴格的同一個取捨：

| | App 的地板 | 地板的年份 | 排除掉誰 |
|---|---|---|---|
| Tuji iOS | `IPHONEOS_DEPLOYMENT_TARGET = 18.0` | 2024 | iPhone X / 8 / 7 及更早 |
| Tuji Android | `minSdk = 29` | 2019 | Android 9 及更早 |

裝置涵蓋率從 96.1% 降到 91.1%（apilevels.com 的全球數字，會被新興市場的低階機
拉低；Play Console 的裝置目錄之後會給出這個 App 真實受眾的數字）。
要求一個 2019 年的系統，比 iOS 端已經在要求的 2024 年系統寬鬆得多。

拉上去之後 `TujiTypeface` 裡那條「26–28 沒有 cascade」的分支就永遠走不到了，
所以整段刪掉——一段宣稱能處理某個情況、但那個情況不可能發生的程式碼，
比沒有那段更糟。刪完重跑，三行探針與刪之前**逐像素相同**。

順帶一提，26 這個地板在拉高之前已經多收過一筆帳：lint 擋下
`android:windowLightNavigationBar`（API 27）。那個屬性最後仍然沒有加回來，
但理由換了——`enableEdgeToEdge()` 已經在 runtime 管系統列，
XML 再設一次是第二個地方主張同一件事。**拉高 minSdk 讓那個檢查閉嘴，
並沒有讓那個理由消失。**

## 順帶量到的

- **字型體積是真的。** debug APK 53 MB，其中 62 MB 的 `res/font`（壓縮後小一些）
  幾乎全是四個 GenSenRounded OTF。架構計劃書把它列為風險是對的；
  Downloadable Fonts 或子集化是 M6 的事，不是現在。
- `26sp` 的 headword 讓 `FuriganaScaleLadder` 塌成單一階（26 × 0.5 = 13，正好是
  CJK 地板），所以每個詞都用同一個尺寸畫，跟 iOS 一致。

## 留下的東西

- `core/design/CascadeProbe.kt` — 對照組，之後任何人動字型都能重跑
- `core/design/FuriganaHeadword.kt` + `FuriganaScaleLadder`（6 個測試）
- `core/model/Headword.kt` — `headwordDisplay` 的決策邏輯（8 個測試，斷言決策不斷言文案）
- `app/spike/FuriganaSpikeScreen.kt` — 打正式站、畫出 295 個切分
