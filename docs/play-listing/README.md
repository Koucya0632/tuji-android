# Play 商店頁文字

`<locale>/title.txt`（30 字內）、`<locale>/short_description.txt`（80 字內）與 `<locale>/full_description.txt`（4000 字內），
語系對應 Play Console：en-US（預設）、zh-TW、zh-CN、ja-JP。ja-JP 還沒有 `title.txt`，標題以 Play Console 為準。

2026-11-24 起，en-US、zh-TW、zh-CN 對「要去日本的人」說話（母 repo `docs/adr/0003-store-listing-speaks-to-people-going-to-japan.md`）；
zh-CN 用大陸用語，不再只是由繁中轉字。文字由母 repo `ad_assets/app-store/aso-go-japan-20261124/` 產出，並通過審稿。

由 tuji-ios `fastlane/metadata/*/description.txt` 轉來，換掉 Apple 專屬的兩行
（Apple ID 付款、Apple EULA 連結），改成 Google Play 帳戶付款。iOS 改說明時兩邊一起改。

推廣圖在父 repo `ad_assets/play-store/`。
