package com.example.adventurerguild.model

enum class UserRole { GUILD_ADMIN, ADVENTURER }

enum class GuildPermission(val displayName: String) {
    REVIEW_JOIN_REQUESTS("審核加入人員"),
    REMOVE_MEMBERS("移除會員"),
    PUBLISH_QUESTS("發布任務"),
    REVIEW_QUESTS("審查任務"),
    REVIEW_NEARBY_SUBMISSIONS("審核 Nearby 提交"),
    REVIEW_REDEMPTIONS("兌換獎勵"),
    POST_ANNOUNCEMENTS("發布公會公告"),
    SET_VACATION("設定公會休假"),
    ASSIGN_ROLES("指派職務"),
    MANAGE_ROLE_PERMISSIONS("修改職務權限"),
    EDIT_QUESTS("編輯任務"),
    UNPUBLISH_QUESTS("下架/取消任務"),
    VIEW_MEMBER_PROGRESS("查看所有成員任務進度"),
    VIEW_AUDIT_LOG("查看操作紀錄"),
    MANAGE_MANDATORY_QUESTS("設定強制任務"),
    MANAGE_QUEST_PENALTIES("設定任務處罰"),
    MANAGE_QUEST_TEMPLATES("管理任務模板"),
    IMPORT_EXPORT_TEMPLATES("匯入/匯出任務模板 CSV"),
    ADJUST_OVERACHIEVEMENT_REWARDS("調整超額提交獎勵"),
    MANUAL_ADJUST_GP_EXP("手動補發/扣除 GP EXP"),
    VIEW_REWARD_PENALTY_LOG("查看獎懲紀錄"),
    MANAGE_REWARDS("建立/編輯/上下架獎勵"),
    FULFILL_REWARDS("標記獎勵已發放"),
    MANAGE_REWARD_STOCK("管理獎勵庫存"),
    REFUND_REDEMPTIONS("退還兌換 GP"),
    SET_EVENTS("設定活動期間"),
    SET_REFRESH_RULES("設定刷新規則"),
    SET_COMPENSATION_DAYS("設定特殊補償日"),
    SEND_REMINDERS("發送提醒通知"),
    MANAGE_RAIDS("建立/編輯討伐戰"),
    SETTLE_RAIDS("結算討伐戰"),
    ADJUST_RAID_CONTRIBUTIONS("調整個人貢獻值"),
    VIEW_RAID_LEADERBOARD("查看貢獻排行榜"),
    EXPORT_GUILD_DATA("匯出公會資料"),
    IMPORT_GUILD_DATA("匯入公會資料"),
    MANAGE_BACKUPS("管理備份"),
    RESTORE_CHANGES("還原誤操作"),
    MANAGE_GUILD_SETTINGS("管理公會基本設定"),
    DISBAND_GUILD("解散公會")
}

enum class QuestDifficulty(val displayName: String) {
    EASY("簡單"),
    NORMAL("普通"),
    HARD("困難"),
    LEGENDARY("傳說")
}

enum class AdventurerRank(val minExp: Long, val displayName: String) {
    F(0, "F 級冒險者"),
    E(300, "E 級冒險者"),
    D(900, "D 級冒險者"),
    C(2_000, "C 級冒險者"),
    B(4_500, "B 級冒險者"),
    A(8_000, "A 級冒險者"),
    S(15_000, "S 級冒險者");

    companion object {
        fun fromExp(exp: Long): AdventurerRank =
            entries.last { exp >= it.minExp }
    }
}

enum class QuestType(val displayName: String, val description: String) {
    DAILY_QUEST("每日任務", "每日刷新、簡單、低獎勵"),
    WEEKLY_QUEST("每週委託", "每週刷新、中等獎勵"),
    MONTHLY_QUEST("月度征伐", "每月刷新、高獎勵"),
    REPEATABLE_QUEST("常駐委託", "可重複接取"),
    LIMITED_EVENT_QUEST("限時討伐令", "期間限定活動"),
    GUILD_RAID("公會討伐戰", "全公會共同累積進度"),
    HIDDEN_QUEST("秘匿委託", "隱藏條件觸發"),
    MAIN_QUEST("命運篇章", "主線劇情與功能解鎖"),
    SIDE_QUEST("支援委託", "支線與額外挑戰"),
    PROMOTION_QUEST("晉階試煉", "Rank 晉升任務"),
    FORMATION_QUEST("戰團編成令", "多人位置分工，可自選或 Roll 分派")
}

enum class QuestStatus {
    DRAFT,
    PUBLISHED,
    AVAILABLE,
    ACCEPTED,
    IN_PROGRESS,
    SUBMITTED,
    APPROVED,
    REJECTED,
    EXPIRED,
    CANCELLED
}

enum class QuestProofMode(val displayName: String, val description: String) {
    NONE("不需證明", "提交完成即可，由公會依任務結果認定"),
    TEXT("文字回報", "只同步文字說明，不上傳照片或影片"),
    IN_PERSON("當面查看", "照片或影片保留在冒險者裝置，由管理員當面查看後認定")
}

enum class RepeatLimitType(val displayName: String) {
    NONE("無限制"),
    DAILY("每日上限"),
    WEEKLY("每週上限"),
    MONTHLY("每月上限"),
    TOTAL("總次數上限")
}

enum class FormationRollMode(val displayName: String) {
    SELF_SELECT_THEN_ROLL("自選優先，截止後 Roll"),
    IMMEDIATE_ROLL("全員立即 Roll"),
    MANUAL_ROLL("管理員手動 Roll"),
    OPTIONAL_SELF_SELECT("自選但不強制")
}

enum class SubmissionStatus { SUBMITTED, APPROVED, REJECTED, NEEDS_REVISION }

enum class RedemptionStatus { PENDING, APPROVED, REJECTED, FULFILLED }

enum class PenaltyStatus { PENDING, APPLIED, WAIVED }

enum class GuildCounterAction {
    ACCEPT_QUEST,
    SUBMIT_QUEST,
    SETTLE_SUBMISSION
}

enum class GuildCounterSessionStatus {
    WAITING_FOR_COUNTERPART,
    AWAITING_FINAL_CONFIRMATION,
    COMPLETED,
    CANCELLED,
    EXPIRED
}
