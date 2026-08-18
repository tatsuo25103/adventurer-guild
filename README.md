# 冒險者公會

**繁體中文（預設）** · [English](README.en.md) · [Deutsch](README.de.md) · [日本語](README.ja.md)

[![Release](https://img.shields.io/github/v/release/tatsuo25103/adventurer-guild?include_prereleases&label=Public%20Beta)](https://github.com/tatsuo25103/adventurer-guild/releases)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](#安裝)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)](#開發與編譯)

「冒險者公會」是一款把日常生活、學習與家庭合作轉化成 RPG 公會任務的 Android App。

父母、照顧者、教師或社團幹部可以擔任公會管理方，建立任務、審核成果與管理獎勵；孩子或成員則以冒險者身分完成任務、獲得 GP 與 EXP。設計重點不是監控，而是讓原本容易變成催促的事情，多一點共同目標、遊戲感與面對面的互動。

> **目前狀態：V0.2.0 Public Beta。** 功能、資料格式與伺服器介面仍可能調整。請保留重要資料的額外紀錄，並只從本專案的 GitHub Releases 下載測試版。

[下載 V0.2.0 公開測試版](https://github.com/tatsuo25103/adventurer-guild/releases/tag/v0.2.0)
· [完整使用手冊](docs/USER_GUIDE.zh-TW.md)
· [V0.2.0 更新說明](docs/RELEASE_NOTES_V0.2.0.zh-TW.md)
· [所有更新紀錄](CHANGELOG.md)

## 1. 安裝

### 1.1 系統需求

- Android 7.0（API 24）以上。
- 建議保留足夠空間供本機任務資料與選用的證明媒體使用。
- 線上同步需要網路；Nearby 當面簽核需要鄰近裝置功能及相應權限。

### 1.2 安裝 APK

1. 前往 [V0.2.0 GitHub Release](https://github.com/tatsuo25103/adventurer-guild/releases/tag/v0.2.0)。
2. 下載 `adventurer-guild-0.2.0-debug.apk`。
3. 在 Android 設定中允許目前使用的瀏覽器或檔案管理器「安裝未知應用程式」。
4. 開啟 APK 並完成安裝。

### 1.3 更新並保留資料

直接安裝較新版本覆蓋舊版本即可。**不要先解除安裝 App**，因為目前完整資料以裝置本機為主，解除安裝會移除本機資料。

Android 更新必須同時符合：

- 套件名稱相同：`com.example.adventurerguild`
- 新版 `versionCode` 高於舊版
- APK 使用相同簽章

若顯示「未安裝應用程式」，先不要移除舊版。請記錄舊版與新版檔名、App 內版本號及 Android 顯示的錯誤，再到 GitHub Issues 回報。

> 公開測試 APK 目前使用既有測試簽章以保留早期測試資料，尚不是 Google Play 正式發行簽章。

## 2. 快速開始

1. 首次啟動選擇「使用此裝置的 UUID 帳號」。
2. 設定自己的顯示名稱。
3. 選擇以「冒險者」或「公會管理方」身分進入。
4. 管理方建立公會；冒險者使用邀請碼、邀請連結或 QR Code 加入。
5. 管理方發布任務，冒險者在「手上任務」或任務看板查看與回報。
6. 管理方審核後，系統依任務設定發放 GP、EXP 或套用處罰。

同一帳號可加入多個公會，也可在不同公會使用不同身分；但不能在同一公會同時擔任冒險者與管理方，避免自己提交、自己審核。

## 3. 主要功能

### 3.1 冒險者

- 優先查看手上任務，再瀏覽公會任務看板。
- 固定週期任務自動進入手上任務，一般任務可自行接取。
- 提交文字回報，或依任務設定進行當面成果確認。
- 獲得 GP、EXP、Level、Rank 與自訂稱號。
- 使用 GP 在公會商店兌換獎勵。
- 將桌面小工具設定為不同公會，直接查看未完成與即將到期任務。

### 3.2 公會管理方

- 建立公會並產生一般、可替換或一次性邀請 QR Code。
- 建立、編輯、公告、上架、下架與複製任務。
- 使用 CSV 任務模板，或將既有任務儲存為模板。
- 指定任務對象、審核人員、證明模式、自動審核、獎勵與處罰。
- 查看每項任務的執行人員，以及個別冒險者的任務進度。
- 管理成員職務與細分權限。
- 管理獎勵商店與兌換流程。
- 設定公會休假，暫停常態循環任務與相關處罰。

公會會長擁有公會內所有權限。若任務指定了審核人員，除會長外，只有指定人員可審核該任務；未指定審核人員時，Nearby 任務使用獨立的 Nearby 審核權限。

## 4. 任務種類

| 種類 | 用途 | 接取與循環 |
|---|---|---|
| **每日任務** | 喝水、洗手、整理書包等日常習慣 | 可指定一週中的生效日；每天結算與刷新；不需接取 |
| **每週委託** | 每週家務、固定練習或週目標 | 指定每週一天結算與刷新；不需接取 |
| **月度征伐** | 月目標或高難度挑戰 | 指定每月 1–31 日；該月沒有此日期時於月底結算；不需接取 |
| **常駐委託** | 平時考滿分、完成練習頁數等不固定時程的重複任務 | 長期留在看板，可重複提交並限制每日、每週、每月或總次數 |
| **戰團編成令** | 洗碗、掃地、班級打掃等多人分工 | 一個任務含多個位置；可自選、截止後 Roll、立即 Roll 或手動分配 |
| **限時討伐令** | 節慶活動、短期挑戰與期間限定委託 | 有明確活動期與倒數，過期後停止接取並封存 |
| **命運篇章** | 主線劇情、連續目標與功能解鎖 | 支援前置任務及完成後解鎖內容 |
| **晉階試煉** | 冒險者 Rank 晉升 | 達到指定條件後出現，審核通過才完成升階 |

固定週期任務不需要另外接取。週期結束後直接進入下一輪，不提供寬限或補交；公會休假期間則暫停常態循環及相應處罰。

## 5. 任務提交與審核

一般流程：

```text
公會建立並發布任務
    -> 冒險者接取或由週期規則自動加入
    -> 冒險者提交完成回報
    -> 管理方審核
    -> 發放 GP / EXP，或依規則處理處罰
```

任務可選擇：

- 不需證明：提交完成即可，由管理方判定。
- 文字回報：只同步必要文字，不上傳照片或影片。
- 當面查看：照片或影片保留在冒險者手機，管理方當面查看後透過 Nearby 完成簽核。
- 超額提交：冒險者標記超額成果，由管理方決定額外 GP／EXP；自動審核不會自動計算超額獎勵。

Nearby 是建立任務時的特殊選項，不是所有任務的預設提交方式。

## 6. 帳號、移機與多公會

App 以裝置 UUID 建立本機帳號，使用者可以自訂顯示名稱。帳號識別不依賴 Google 登入，避免把整個使用流程綁定個人 Google 帳號。

更換手機前，請在舊手機的「帳號移機」產生一次性移機碼，並在有效時間內於新手機輸入。移機完成前不要解除安裝舊手機上的 App，也不要清除 App 資料。

目前仍屬公開測試階段；移機或重大更新前，建議另外記錄重要的公會邀請、GP 與任務狀態。

## 7. 資料與隱私

App 採離線優先設計：

- 裝置保留完整操作資料與本機媒體。
- Cloudflare Workers + D1 只處理帳號繼承、公會成員關係、邀請、必要文字同步、短期互動狀態與防重放資料。
- 照片、影片及本機媒體 URI 不預設上傳伺服器。
- 裝置請求使用 ECDSA 簽章、時間戳與單次 nonce；服務端仍需對每項操作檢查公會身分與權限。
- 公開版本庫不包含 Worker 網址、D1 database ID、Google 設定檔、私密金鑰或帳號識別碼。

請勿在任務標題、說明或文字回報中填入不必要的姓名、住址、學校、醫療資料或其他敏感資訊。

## 8. 桌面小工具

長按 Android 首頁空白處，選擇「小工具」，再加入「冒險者公會任務」。每個小工具實例可設定不同公會，因此同時參與或管理多個公會時，可以在桌面放置多個小工具。

小工具會顯示未完成任務摘要，並以不同顏色提示快到期與即將到期的項目。點擊任務會開啟該公會的冒險者任務詳情；若目前身分不相符，App 會先引導選擇正確公會與身分。

## 9. 語言

App 內建繁體中文、English、Deutsch 與日本語。公會名稱、任務標題、任務說明及使用者回報保留建立者原文，不會自動翻譯。

## 10. 公開測試限制

- 目前以 APK 方式發佈，尚未上架 Google Play。
- 公開測試 APK 使用既有測試簽章。
- 離線與雲端狀態衝突處理仍需更多真實多裝置測試。
- Nearby 會受 Android 版本、廠牌省電設定、藍牙及鄰近裝置權限影響。
- Google Drive／Firebase 相關程式碼保留作為早期實驗或相容層，不是目前主要帳號與同步途徑。

問題回報請附上 App 版本、Android 版本、操作身分、所屬公會數量、重現步驟與去除私人內容後的畫面。

## 11. 開發與編譯

技術架構：Kotlin、Jetpack Compose、MVVM-style state management、Cloudflare Workers + D1、Nearby Connections 與 Android App Widget。

需求：

- JDK 17
- Android SDK 35
- Node.js 20+（僅 Cloudflare Worker）
- Gradle Wrapper

建立本機私密設定：

```powershell
Copy-Item private.properties.example private.properties
```

只在被 Git 忽略的 `private.properties` 填入自己的服務位址：

```properties
GOOGLE_WEB_CLIENT_ID=
CLOUDFLARE_API_BASE_URL=https://your-worker.example.workers.dev
```

編譯與測試：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

輸出：

```text
app\build\outputs\apk\debug\adventurer-guild-<version>-debug.apk
```

Cloudflare 本機及部署說明請見 [cloudflare/README.md](cloudflare/README.md)。`private.properties`、`google-services.json`、`cloudflare/wrangler.jsonc`、部署網址、D1 database ID、APK 與本機測試資料不得提交到版本庫。

## 12. 版本

目前公開測試版為 **V0.2.0**。此版本整理裝置 UUID 帳號、多公會身分、任務治理、邀請 QR Code、桌面小工具、多語介面、Cloudflare 輕量同步及當面 Nearby 簽核。

完整內容請見 [V0.2.0 更新說明](docs/RELEASE_NOTES_V0.2.0.zh-TW.md)，歷史版本請見 [Releases](https://github.com/tatsuo25103/adventurer-guild/releases) 與 [CHANGELOG.md](CHANGELOG.md)。

## 13. 使用提醒

本 App 是家庭互動、社團合作與習慣養成工具，不應用來羞辱、威脅、公開排名孩子的敏感表現，或施加不符合年齡的處罰。任務與獎懲應透明、合理，並讓參與者有表達、拒絕不安全任務及共同調整規則的機會。本 App 不能取代醫療、心理、教育或兒童發展專業建議。
