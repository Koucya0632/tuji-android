# 綁進 App 的字型

三套字型都在 `src/main/res/font/`，都是 SIL Open Font License 1.1。
OFL 要求授權條文隨字型一起散布，所以它在這裡，不在別處。

| 家族 | 用途 | 授權 |
|---|---|---|
| GenSenRounded 2（TW / JP，各 R 與 B） | 漢字與假名（ADR-0003） | SIL OFL 1.1 — 全文見 `GenSenRounded2-OFL.txt` |
| Plus Jakarta Sans（Regular / SemiBold / Bold / ExtraBold） | 拉丁字 | SIL OFL 1.1 |
| JetBrains Mono（Regular） | IPA、UID、系統代碼 | SIL OFL 1.1 |

兩件會被忘記的事：

- **這不是唯一的署名義務。** `reading_segments` 衍生自 JMdict 與 JmdictFurigana，
  兩者是 CC BY-SA（ADR-0006）。iOS 的「設定 → 關於」同時列了字型與字典的署名，
  Android 端做到設定頁時要一起搬過去 —— 那一頁不是可有可無的裝飾。
- **只綁兩個 CJK 字重。** ADR-0003 列了三個，但沒有 token 要 Medium，
  而一個沒人用的 CJK 字重是 31 MB 的空氣。
