# 第一版發布前 QA 報告

測試日期：2026-06-12

## 結論

目前版本可供內部測試，但不建議直接公開發布。主要阻斷項目是 Release
產物尚未簽章、正式登入尚未以真實裝置完整驗證，以及套件 ID 仍為
`com.example.adventurerguild`。

## 已通過

- `testDebugUnitTest`：15 項單元測試通過。
- `lintDebug`：0 errors，12 warnings。
- `assembleDebug`、`assembleRelease`、`bundleRelease` 建置成功。
- Cloudflare Worker 簽章 smoke test 通過。
- Cloudflare 公會身分規則通過：
  - 建立管理方與冒險者。
  - 阻擋同一帳號在同一公會同時擔任雙方。
  - 阻擋錯誤身分確認互動計數器。
- Android 乾淨安裝與啟動通過。
- 離線測試管理員登入通過。
- 建立測試公會、產生四筆已發布任務與公會討伐資料通過。
- 管理員任務管理頁面與發布狀態分頁通過。
- 強制停止後重新啟動，登入、公會與任務資料仍存在。
- 關閉網路後，既有本機公會與任務仍可查看。
- 測試期間未發現應用程式 crash 或 ANR。
- 系統能辨識並渲染 3 x 2「冒險者公會」桌面小工具預覽。
- Manifest 已停用系統備份並禁止明文 HTTP。
- Google 登入已處理裝置無可用帳號的 `NoCredentialException`。
- 正式登入頁預設隱藏離線測試帳號；需在五秒內連點標題七次才會暫時顯示。

## 未通過或未完整驗證

1. Release APK/AAB 未簽章，不能作為正式發布產物。
2. Google 登入尚未在真實手機與正式可用帳號上完成端到端驗證。
3. 套件 ID 仍為 `com.example.adventurerguild`，公開發布前應決定永久 ID。
4. 全新本機資料且無網路時，離線測試帳號可以登入，但無法建立公會；
   建立公會目前依賴 Cloudflare API。
5. 桌面小工具已通過系統註冊與預覽渲染，但自動化拖放沒有完成固定，
   仍需在實體手機手動確認放置、更新與任務深層連結。
6. 目前伺服器身分以裝置為基礎，換機、清除資料或遺失裝置時缺少可靠的
   帳號復原流程。

## 發布前必要工作

- 決定永久 `applicationId`。
- 建立並妥善備份正式 release keystore，設定 AAB 簽章。
- 決定正式帳號與復原方式。
- 用至少兩支真實裝置完成管理方與冒險者完整互動流程。
- 手動驗證桌面小工具放置、更新、到期顏色與點擊任務詳情。
- 建立隱私權政策與資料刪除說明。

## 測試畫面

- `screenshots/release_qa/01_clean_launch.png`
- `screenshots/release_qa/02_admin_create_guild.png`
- `screenshots/release_qa/03_admin_quest_management.png`
- `screenshots/release_qa/04_offline_create_guild.png`
- `screenshots/release_qa/05_widget_picker.png`
- `screenshots/release_qa/07_test_accounts_hidden.png`
- `screenshots/release_qa/08_test_accounts_unlocked.png`

## 目前產物

- `app/build/outputs/apk/release/app-release-unsigned.apk`
  - SHA-256: `40329656A373354C944AD75A5DD29663BB2D5DC196EA624EB755BE60688FF6DA`
- `app/build/outputs/bundle/release/app-release.aab`
  - SHA-256: `F2AFA6C93BA22281B640AEEFC002EA318E168901A703CBDEB864EE44D5FFFDB9`
