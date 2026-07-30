# 冒險者公會

「冒險者公會」是一款把日常生活轉化成 RPG 公會任務的 Android App。

本專案希望透過任務、GP、EXP、Rank 與獎勵，陪伴孩子建立規律的生活習慣，例如喝水、洗手、整理書包、完成作業與協助家務。父母或照顧者可以扮演公會管理方，孩子則以冒險者身分完成任務、回報成果並獲得獎勵。

重點不是監控孩子，而是讓原本容易變成催促或爭執的事情，多一點遊戲感、共同目標與面對面的親子互動。

## 核心理念

- 用遊戲任務引導孩子建立正常、健康且可持續的生活習慣。
- 讓父母與孩子一起討論任務、難度、獎勵與處罰。
- 將「要求孩子做事」轉化成公會委託與完成後的正向回饋。
- 鼓勵當面查看成果與共同簽核，而不是把家庭互動完全變成冷冰冰的線上流程。
- 任務內容與獎懲應由家庭共同制定，並符合孩子的年齡與能力。

## 角色

### 冒險者

- 加入一個或多個公會。
- 查看手上任務與任務看板。
- 接取一般任務，固定週期任務則自動進入手上任務。
- 提交文字回報或進行當面成果確認。
- 獲得 GP、EXP、Level、Rank 與自訂稱號。
- 使用 GP 在公會商店兌換獎勵。

### 公會管理方

- 建立及管理公會。
- 建立、編輯、公告、上架與下架任務。
- 管理任務模板、指定冒險者與指定審核人員。
- 審核任務、調整獎勵與處罰。
- 管理成員、職務及細分權限。
- 建立獎勵商店並審核兌換。
- 設定公會休假，暫停常態循環任務。

同一帳號可以在不同公會使用不同身分，但不能在同一個公會同時擔任冒險者與管理方，避免自己提交、自己審核。

## 任務系統

- 每日任務：可選擇一週中的生效日，每日結算與刷新。
- 每週委託：指定每週一天結算與刷新。
- 月度征伐：指定每月 1 至 31 日；若該月沒有指定日期，改於月底結算。
- 常駐委託：長期顯示於任務看板，可重複提交並設定次數上限。
- 戰團編成令：多人分工任務，可自選位置或由系統 Roll 分派。
- 限時討伐令：活動期間限定，過期後封存。
- 命運篇章：具有前置任務與功能解鎖的主線任務。
- 晉階試煉：達到條件後用於 Rank 晉升。

固定週期任務不需要另外接取。週期結束後直接進入下一輪，不提供寬限或補交。

## 資料與隱私

App 採離線優先設計，裝置保留完整操作資料。雲端服務盡量只負責帳號繼承、公會成員關係、邀請、事件通知與必要的文字同步，以降低營運成本及個人資料暴露。

照片、影片等證明不預設上傳雲端。需要影像證明時，可以由冒險者保留在自己的手機上，與管理方當面查看後進行 Nearby 簽核。Nearby 是任務建立時的特殊選項，不是所有任務的強制提交方式。

請勿在任務內容中放入不必要的個人敏感資料。公開發佈前仍應完成安全性檢查、服務端授權驗證及隱私政策。

## 語言

App 內建以下介面語言：

- 繁體中文
- English
- Deutsch
- 日本語

公會名稱、任務標題、任務說明及使用者回報保留建立者原文，不會自動翻譯。

## 技術架構

- Kotlin
- Jetpack Compose
- MVVM-style state management
- 離線本機資料儲存
- Cloudflare Workers + D1 輕量文字同步
- Nearby Connections 當面簽核
- Android App Widget

套件名稱：

```text
com.example.adventurerguild
```

## 編譯

需求：

- JDK 17
- Android SDK 35
- Gradle Wrapper

先建立本機私密設定：

```powershell
Copy-Item private.properties.example private.properties
```

在被 Git 忽略的 `private.properties` 中設定自己的服務位址。Google 登入
目前不是主要帳號方式，未使用時可將 `GOOGLE_WEB_CLIENT_ID` 留空。

```properties
GOOGLE_WEB_CLIENT_ID=
CLOUDFLARE_API_BASE_URL=https://your-worker.example.workers.dev
```

編譯 Debug APK：

```powershell
.\gradlew.bat :app:assembleDebug
```

APK 輸出位置：

```text
app\build\outputs\apk\debug\adventurer-guild-<version>-debug.apk
```

每次提供可安裝更新時都必須提高 `versionCode`，並使用相同簽章，否則 Android 無法覆蓋安裝並保留原有本機資料。

`private.properties`、`google-services.json`、`cloudflare/wrangler.jsonc`、
Cloudflare 帳號與資料庫識別碼都不得提交到版本庫。公開範例只保留空值或
`REPLACE_WITH_...` 佔位符。

## Cloudflare

Cloudflare Worker 與 D1 相關程式位於：

```text
cloudflare\
```

正式部署前請確認：

- Worker secrets 沒有提交到版本庫。
- Worker 網址與 D1 database ID 只存在本機忽略檔案。
- D1 schema 已套用最新 migration。
- 所有公會、任務、提交與審核 API 都在服務端驗證身分及權限。
- 一次性邀請與移機碼具有有效期限、單次使用限制及防重放保護。

## 使用提醒

本 App 是家庭互動與習慣養成工具，不應用來羞辱、威脅或施加不符合年齡的處罰，也不能取代醫療、心理、教育或兒童發展專業建議。任務與獎懲應透明、合理，並讓孩子有表達及共同調整規則的機會。
