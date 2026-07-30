# Google Drive 公會資料夾同步設計

這個專案可以改成「Google 帳號登入 + 公會會長 Google Drive 資料夾作為任務設定來源」。登入與 Drive 授權要分開處理：登入只確認使用者身份；真正需要讀寫 Drive 時再請求 Drive scope。

## 建議架構

公會會長建立或選定一個 Google Drive 資料夾，App 在資料夾中維護以下結構：

```text
AdventurerGuild-{guildId}/
  guild_manifest.json
  quests/
    quest_daily_cleanup.json
    quest_weekly_supply_guard.json
  guild_raids/
    raid_ancient_golem.json
  rewards/
    reward_badge.json
  member_events/
    {uid}_{timestamp}_accept_quest.json
    {uid}_{timestamp}_submit_quest.json
    {uid}_{timestamp}_raid_contribution.json
  snapshots/
    current_state.json
```

## 同步規則

- `quests/`、`guild_raids/`、`rewards/`：由 Guild Admin 編輯，是任務與商店設定來源。
- `member_events/`：冒險者只新增事件檔，不直接改任務設定檔。
- `snapshots/current_state.json`：由 Guild Admin App 匯總事件後產生，冒險者下載它顯示審核結果、GP/EXP、討伐進度。
- 每個檔案帶 `revision` 或 `updatedAtMillis`，下載時用較新的版本覆蓋本機快取。
- 離線時先寫本機 outbox，恢復連線後上傳到 `member_events/`。

這樣可以避免多人同時修改同一個 JSON 造成衝突。Google Drive 是檔案同步，不是 Firestore 那種即時資料庫；append-only event files 會穩很多。

## 加入公會流程

1. Guild Admin 建立公會資料夾。
2. App 產生 guild invite code，至少包含 `guildId` 與 Drive `folderId`。
3. Guild Admin 分享該資料夾給成員的 Google 帳號，或分享給 Google Group。
4. Adventurer 用 Google 帳號登入後輸入 invite code。
5. App 用 Drive API 讀取 `guild_manifest.json`，下載任務、獎勵、討伐與最新 snapshot。

## Drive 權限選擇

首選是讓 App 建立與管理公會資料夾，並使用較窄的 `https://www.googleapis.com/auth/drive.file` scope。這通常比完整 Drive scope 更容易通過隱私審查。

如果要讀取使用者手動建立、不是由 App 建立或選取授權的任意資料夾，可能需要更廣的 Drive scope，這會增加 OAuth 審核與隱私說明成本。

## 取捨

優點：

- 不需要自建伺服器。
- 公會會長可直接備份、複製或手動檢視 JSON。
- 任務設定可以用 Drive 分享權限控制。

限制：

- 不適合大量即時多人互動。
- 審核、GP 發放、Rank 更新會是「同步後更新」，不是即時推播。
- 需要處理 Drive 權限、檔案衝突、離線 outbox、資料夾被刪除或移動等狀況。

MVP 建議：任務設定先走 Drive；帳號身份仍使用 Firebase Auth with Google；成員 GP/EXP 可以先保留 Firestore，等 Drive event/snapshot 流程穩定後再完全移除 Firestore 狀態。
