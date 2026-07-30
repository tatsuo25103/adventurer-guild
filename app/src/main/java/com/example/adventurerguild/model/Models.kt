package com.example.adventurerguild.model

import java.util.UUID

data class GuildProgress(
    val gp: Long = 0,
    val exp: Long = 0,
    val level: Int = 1,
    val rank: AdventurerRank = AdventurerRank.F,
    val title: String = "新手冒險者"
)

data class UserProfile(
    val uid: String = "",
    val cloudUserId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: UserRole = UserRole.ADVENTURER,
    val guildId: String = "default-guild",
    val gp: Long = 0,
    val exp: Long = 0,
    val level: Int = 1,
    val rank: AdventurerRank = AdventurerRank.F,
    val title: String = "新手冒險者",
    val customTitle: String = "",
    val guildRoles: Map<String, String> = emptyMap(),
    val acceptedQuestIds: List<String> = emptyList(),
    val joinedGuildIds: List<String> = emptyList(),
    val managedGuildIds: List<String> = emptyList(),
    val guildProgress: Map<String, GuildProgress> = emptyMap()
)

fun UserProfile.progressForGuild(guildId: String): GuildProgress =
    guildProgress[guildId] ?: if (guildProgress.isEmpty() || this.guildId == guildId) {
        GuildProgress(gp = gp, exp = exp, level = level, rank = rank, title = title)
    } else {
        GuildProgress()
    }

fun UserProfile.activateGuildProgress(guildId: String): UserProfile {
    val progress = progressForGuild(guildId)
    return copy(
        guildId = guildId,
        gp = progress.gp,
        exp = progress.exp,
        level = progress.level,
        rank = progress.rank,
        title = progress.title,
        guildProgress = guildProgress + (guildId to progress)
    )
}

fun UserProfile.withGuildProgress(guildId: String, progress: GuildProgress): UserProfile {
    val updatedProgress = guildProgress + (guildId to progress)
    return if (this.guildId == guildId) {
        copy(
            gp = progress.gp,
            exp = progress.exp,
            level = progress.level,
            rank = progress.rank,
            title = progress.title,
            guildProgress = updatedProgress
        )
    } else {
        copy(guildProgress = updatedProgress)
    }
}

fun UserProfile.displayTitle(): String =
    customTitle.ifBlank { title.ifBlank { rank.displayName } }

fun UserProfile.guildRoleTitle(guildId: String): String =
    guildRoles[guildId].orEmpty()

fun UserProfile.isGuildManager(guild: Guild): Boolean =
    guild.ownerUid == uid || guild.id in managedGuildIds

fun UserProfile.isGuildAdventurer(guild: Guild): Boolean =
    guild.id in joinedGuildIds && !isGuildManager(guild)

object GuildRoleCatalog {
    val defaultRoles = listOf(
        "公會會長",
        "副會長",
        "任務官",
        "獎勵官",
        "討伐隊長",
        "生活導師",
        "見習成員",
        "一般成員"
    )
}

fun defaultGuildRolePermissions(): Map<String, List<String>> = mapOf(
    GuildRoleCatalog.defaultRoles[0] to GuildPermission.entries.map { it.name },
    GuildRoleCatalog.defaultRoles[1] to GuildPermission.entries.map { it.name },
    GuildRoleCatalog.defaultRoles[2] to listOf(
        GuildPermission.PUBLISH_QUESTS.name,
        GuildPermission.REVIEW_QUESTS.name,
        GuildPermission.REVIEW_NEARBY_SUBMISSIONS.name,
        GuildPermission.POST_ANNOUNCEMENTS.name,
        GuildPermission.EDIT_QUESTS.name,
        GuildPermission.UNPUBLISH_QUESTS.name,
        GuildPermission.MANAGE_QUEST_TEMPLATES.name,
        GuildPermission.VIEW_MEMBER_PROGRESS.name
    ),
    GuildRoleCatalog.defaultRoles[3] to listOf(
        GuildPermission.REVIEW_REDEMPTIONS.name,
        GuildPermission.MANAGE_REWARDS.name,
        GuildPermission.FULFILL_REWARDS.name,
        GuildPermission.MANAGE_REWARD_STOCK.name,
        GuildPermission.REFUND_REDEMPTIONS.name
    ),
    GuildRoleCatalog.defaultRoles[4] to listOf(
        GuildPermission.PUBLISH_QUESTS.name,
        GuildPermission.REVIEW_QUESTS.name,
        GuildPermission.REVIEW_NEARBY_SUBMISSIONS.name,
        GuildPermission.MANAGE_RAIDS.name,
        GuildPermission.SETTLE_RAIDS.name,
        GuildPermission.ADJUST_RAID_CONTRIBUTIONS.name,
        GuildPermission.VIEW_RAID_LEADERBOARD.name
    ),
    GuildRoleCatalog.defaultRoles[5] to listOf(
        GuildPermission.REVIEW_QUESTS.name,
        GuildPermission.REVIEW_NEARBY_SUBMISSIONS.name,
        GuildPermission.VIEW_MEMBER_PROGRESS.name,
        GuildPermission.SET_COMPENSATION_DAYS.name
    ),
    GuildRoleCatalog.defaultRoles[6] to emptyList(),
    GuildRoleCatalog.defaultRoles[7] to emptyList()
)

data class Guild(
    val id: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val ownerEmail: String = "",
    val inviteCode: String = "",
    val driveFolderId: String? = null,
    val driveStateFileId: String? = null,
    val driveInviteFileId: String? = null,
    val driveManagersFolderId: String? = null,
    val driveMemberInboxesFolderId: String? = null,
    val driveAttachmentsFolderId: String? = null,
    val driveAuditFolderId: String? = null,
    val driveBackupsFolderId: String? = null,
    val driveMemberInboxIds: Map<String, String> = emptyMap(),
    val driveMemberAttachmentFolderIds: Map<String, String> = emptyMap(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    val rankTitles: Map<String, String> = defaultRankTitles(),
    val rolePermissions: Map<String, List<String>> = defaultGuildRolePermissions(),
    val joinRequestUserIds: List<String> = emptyList(),
    val cloudJoinRequestIds: Map<String, String> = emptyMap(),
    val cloudJoinRequestedSides: Map<String, String> = emptyMap(),
    val announcement: String = "",
    val vacationEnabled: Boolean = false,
    val vacationNote: String = ""
)

fun defaultRankTitles(): Map<String, String> = mapOf(
    AdventurerRank.F.name to "木牌",
    AdventurerRank.E.name to "鐵牌",
    AdventurerRank.D.name to "銅牌（Copper）",
    AdventurerRank.C.name to "銀牌（Silver）",
    AdventurerRank.B.name to "金牌（Gold）",
    AdventurerRank.A.name to "密銀牌（Mithril）",
    AdventurerRank.S.name to "精鋼牌（Adamantine）"
)

fun Guild.rankTitle(rank: AdventurerRank): String =
    rankTitles[rank.name].orEmpty().ifBlank { defaultRankTitles()[rank.name] ?: rank.displayName }

fun UserProfile.hasGuildPermission(guild: Guild, permission: GuildPermission): Boolean {
    if (!isGuildManager(guild)) return false
    if (guild.ownerUid == uid) return true
    val roleTitle = guildRoleTitle(guild.id)
    return permission.name in guild.rolePermissions[roleTitle].orEmpty()
}

fun UserProfile.canReviewQuestSubmission(guild: Guild, quest: Quest): Boolean {
    if (!isGuildManager(guild)) return false
    if (guild.ownerUid == uid) return true
    if (quest.assignedReviewerIds.isNotEmpty()) return uid in quest.assignedReviewerIds
    val permission = if (quest.proofMode == QuestProofMode.IN_PERSON) {
        GuildPermission.REVIEW_NEARBY_SUBMISSIONS
    } else {
        GuildPermission.REVIEW_QUESTS
    }
    return hasGuildPermission(guild, permission)
}

fun Quest.validateGovernancePolicy(
    guild: Guild,
    guildUsers: Collection<UserProfile>,
    actor: UserProfile? = null
) {
    require(guildId == guild.id) { "任務不屬於目前公會。" }
    require(title.isNotBlank()) { "任務標題不可空白。" }
    require(gpReward >= 0 && expReward >= 0 && bonusGp >= 0 && bonusExp >= 0) {
        "任務獎勵不可為負數。"
    }
    require(penaltyGp >= 0 && penaltyExp >= 0) { "未完成處罰不可為負數。" }
    require(activeWeekdays.all { it in 1..7 }) { "每日任務星期必須介於週一到週日。" }
    require(weeklyRefreshWeekday == null || weeklyRefreshWeekday in 1..7) {
        "每週刷新日必須介於週一到週日。"
    }
    require(monthlyRefreshDay == null || monthlyRefreshDay in 1..31) {
        "每月刷新日必須介於 1 到 31 日。"
    }
    if (announcedAtMillis != null && acceptStartsAtMillis != null) {
        require(announcedAtMillis <= acceptStartsAtMillis) { "公告日期不可晚於開放接取日期。" }
    }
    if (hasTimeLimit && startsAtMillis != null && endsAtMillis != null) {
        require(startsAtMillis <= endsAtMillis) { "任務開始日期不可晚於結束日期。" }
    }
    if (hasTimeLimit && acceptStartsAtMillis != null && endsAtMillis != null) {
        require(acceptStartsAtMillis <= endsAtMillis) { "開放接取日期不可晚於任務結束日期。" }
    }
    if (type == QuestType.LIMITED_EVENT_QUEST) {
        require(endsAtMillis != null) { "限時討伐令必須設定活動結束日期。" }
    }
    require(type != QuestType.GUILD_RAID) { "公會討伐戰不走一般任務流程。" }
    if (autoReviewEnabled) {
        require(type != QuestType.PROMOTION_QUEST) { "晉階試煉不可使用自動審核。" }
        if (actor != null) {
            require(actor.hasGuildPermission(guild, GuildPermission.REVIEW_QUESTS)) {
                "啟用自動審核需要審查任務權限。"
            }
        }
    }
    val usersById = guildUsers.associateBy { it.uid }
    val invalidAdventurerIds = assignedAdventurerIds.distinct().filter { adventurerId ->
        usersById[adventurerId]?.isGuildAdventurer(guild) != true
    }
    require(invalidAdventurerIds.isEmpty()) { "指名對象必須是此公會的冒險者。" }
    val invalidReviewerIds = assignedReviewerIds.distinct().filter { reviewerId ->
        val reviewer = usersById[reviewerId]
        reviewer == null || !reviewer.isGuildManager(guild)
    }
    require(invalidReviewerIds.isEmpty()) { "指定審核者必須是此公會管理方。" }
    if (type == QuestType.REPEATABLE_QUEST && repeatLimitType != RepeatLimitType.NONE) {
        require(repeatLimitCount > 0) { "設定提交上限時，次數必須大於 0。" }
    }
    if (type == QuestType.FORMATION_QUEST) {
        require(formationSlots.isNotEmpty()) { "戰團編成令至少需要一個位置。" }
        require(formationMinSlotsPerUser >= 0) { "每人最少位置不可為負數。" }
        require(formationMaxSlotsPerUser >= 1) { "每人最多位置至少為 1。" }
        require(formationMinSlotsPerUser <= formationMaxSlotsPerUser) {
            "每人最少位置不可大於最多位置。"
        }
    }
}

fun Quest.validateStatusTransition(targetStatus: QuestStatus, nowMillis: Long = System.currentTimeMillis()) {
    if (status == targetStatus) return
    require(status != QuestStatus.CANCELLED) { "已取消的任務不能重新變更狀態，請複製後重新上架。" }
    require(status != QuestStatus.EXPIRED) { "已過期的任務不能重新上架，請複製後重新設定期間。" }
    when (targetStatus) {
        QuestStatus.PUBLISHED,
        QuestStatus.AVAILABLE -> {
            require(type != QuestType.GUILD_RAID) { "公會討伐戰不能用一般任務狀態上架。" }
            require(type != QuestType.LIMITED_EVENT_QUEST || endsAtMillis != null) { "限時討伐令必須設定活動結束日期。" }
            require(endsAtMillis == null || nowMillis <= endsAtMillis) { "任務已超過結束日期，不能上架。" }
        }
        QuestStatus.DRAFT -> require(status == QuestStatus.PUBLISHED || status == QuestStatus.AVAILABLE) {
            "只有已上架任務可以下架回待上架。"
        }
        QuestStatus.CANCELLED -> require(status != QuestStatus.APPROVED) { "已完成任務不能取消。" }
        QuestStatus.EXPIRED -> Unit
        QuestStatus.ACCEPTED,
        QuestStatus.IN_PROGRESS,
        QuestStatus.SUBMITTED,
        QuestStatus.APPROVED,
        QuestStatus.REJECTED -> error("此狀態由任務流程自動產生，不能由管理員直接切換。")
    }
}

data class Quest(
    val id: String = "",
    val guildId: String = "default-guild",
    val title: String = "",
    val description: String = "",
    val type: QuestType = QuestType.DAILY_QUEST,
    val status: QuestStatus = QuestStatus.DRAFT,
    val gpReward: Long = 10,
    val expReward: Long = 10,
    val targetCount: Long = 1,
    val createdBy: String = "",
    val announcedAtMillis: Long? = null,
    val acceptStartsAtMillis: Long? = null,
    val startsAtMillis: Long? = null,
    val endsAtMillis: Long? = null,
    val hasTimeLimit: Boolean = false,
    val penaltyGp: Long = 0,
    val penaltyExp: Long = 0,
    val activeWeekdays: List<Int> = emptyList(),
    val difficulty: QuestDifficulty = QuestDifficulty.NORMAL,
    val tags: List<String> = emptyList(),
    val minRank: AdventurerRank = AdventurerRank.F,
    val assignedAdventurerIds: List<String> = emptyList(),
    val assignedReviewerIds: List<String> = emptyList(),
    val prerequisiteQuestIds: List<String> = emptyList(),
    val bonusGp: Long = 0,
    val bonusExp: Long = 0,
    val gracePeriodDays: Int = 0,
    val submissionDeadlineDays: Int = 0,
    val weeklyRefreshWeekday: Int? = null,
    val monthlyRefreshDay: Int? = null,
    val repeatLimitType: RepeatLimitType = RepeatLimitType.NONE,
    val repeatLimitCount: Int = 0,
    val formationSlots: List<QuestSlot> = emptyList(),
    val formationAssignments: List<QuestSlotAssignment> = emptyList(),
    val formationRequired: Boolean = false,
    val formationMinSlotsPerUser: Int = 1,
    val formationMaxSlotsPerUser: Int = 1,
    val formationRollMode: FormationRollMode = FormationRollMode.OPTIONAL_SELF_SELECT,
    val formationAutoRollAtMillis: Long? = null,
    val proofMode: QuestProofMode = QuestProofMode.TEXT,
    val autoReviewEnabled: Boolean = false,
    val pinned: Boolean = false,
    val sortOrder: Int = 0,
    val pendingChangeSummary: String? = null,
    val pendingChangeEffectiveCycle: String? = null,
    val pendingChangeCreatedAtMillis: Long? = null,
    val pendingChangeQuest: Quest? = null
)

data class QuestSlot(
    val id: String = "",
    val name: String = "",
    val capacity: Int = 1,
    val gpReward: Long = 0,
    val expReward: Long = 0,
    val penaltyGp: Long = 0,
    val penaltyExp: Long = 0,
    val description: String = "",
    val selfSelectable: Boolean = true,
    val minRank: AdventurerRank = AdventurerRank.F
)

data class QuestSlotAssignment(
    val slotId: String = "",
    val userId: String = "",
    val userName: String = "",
    val assignedByRoll: Boolean = false,
    val assignedAtMillis: Long = System.currentTimeMillis()
)

fun List<QuestSlot>.normalizedFormationSlots(): List<QuestSlot> =
    filter { it.name.isNotBlank() }
        .map {
            it.copy(
                id = it.id.ifBlank { "slot-${UUID.randomUUID()}" },
                capacity = it.capacity.coerceAtLeast(1),
                gpReward = it.gpReward.coerceAtLeast(0),
                expReward = it.expReward.coerceAtLeast(0),
                penaltyGp = it.penaltyGp.coerceAtLeast(0),
                penaltyExp = it.penaltyExp.coerceAtLeast(0)
            )
        }

fun Quest.assignedFormationSlots(userId: String): List<QuestSlot> {
    val assignedIds = formationAssignments.filter { it.userId == userId }.map { it.slotId }.toSet()
    return formationSlots.filter { it.id in assignedIds }
}

fun QuestType.isStrictCycleType(): Boolean =
    this == QuestType.DAILY_QUEST || this == QuestType.WEEKLY_QUEST || this == QuestType.MONTHLY_QUEST

fun Quest.normalizedTimingPolicy(): Quest =
    when {
        type.isStrictCycleType() -> copy(gracePeriodDays = 0, submissionDeadlineDays = 0)
        type == QuestType.LIMITED_EVENT_QUEST -> copy(
            hasTimeLimit = true,
            penaltyGp = 0,
            penaltyExp = 0,
            gracePeriodDays = 0,
            submissionDeadlineDays = 0
        )
        else -> copy(
            gracePeriodDays = gracePeriodDays.coerceAtLeast(0),
            submissionDeadlineDays = submissionDeadlineDays.coerceAtLeast(0)
        )
    }

data class QuestTemplate(
    val id: String = "",
    val guildId: String = "default-guild",
    val name: String = "",
    val quest: Quest = Quest(),
    val createdBy: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class QuestSubmission(
    val id: String = "",
    val questId: String = "",
    val questTitle: String = "",
    val userId: String = "",
    val userName: String = "",
    val proofMode: QuestProofMode = QuestProofMode.TEXT,
    val proofText: String = "",
    val proofImageUrl: String? = null,
    val overachieved: Boolean = false,
    val overachievementText: String = "",
    val formationSlotIds: List<String> = emptyList(),
    val formationSlotNames: List<String> = emptyList(),
    val status: SubmissionStatus = SubmissionStatus.SUBMITTED,
    val gpReward: Long = 0,
    val expReward: Long = 0,
    val submittedAtMillis: Long = System.currentTimeMillis(),
    val reviewedBy: String? = null,
    val reviewedAtMillis: Long? = null,
    val reviewBonusGp: Long = 0,
    val reviewBonusExp: Long = 0,
    val reviewNote: String? = null
)

data class GuildCounterSession(
    val id: String = "",
    val guildId: String = "",
    val action: GuildCounterAction = GuildCounterAction.ACCEPT_QUEST,
    val status: GuildCounterSessionStatus = GuildCounterSessionStatus.WAITING_FOR_COUNTERPART,
    val questId: String = "",
    val questTitle: String = "",
    val submissionId: String? = null,
    val adventurerUid: String = "",
    val adventurerName: String = "",
    val managerUid: String? = null,
    val managerName: String? = null,
    val proofMode: QuestProofMode = QuestProofMode.TEXT,
    val proofText: String = "",
    val localProofImageUri: String? = null,
    val proofImageSha256: String? = null,
    val retainProofCopyApproved: Boolean = false,
    val overachieved: Boolean = false,
    val overachievementText: String = "",
    val approved: Boolean? = null,
    val proposedBonusGp: Long = 0,
    val proposedBonusExp: Long = 0,
    val reviewNote: String? = null,
    val nonce: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val expiresAtMillis: Long = System.currentTimeMillis() + 10 * 60 * 1000,
    val adventurerConfirmedAtMillis: Long? = null,
    val managerConfirmedAtMillis: Long? = null,
    val completedAtMillis: Long? = null,
    val receiptSha256: String? = null
)

data class GuildRaid(
    val id: String = "",
    val guildId: String = "default-guild",
    val title: String = "",
    val description: String = "",
    val targetProgress: Long = 100,
    val currentProgress: Long = 0,
    val gpRewardPerContribution: Long = 1,
    val expRewardPerContribution: Long = 1,
    val active: Boolean = true,
    val endsAtMillis: Long? = null
)

data class GuildRaidContribution(
    val id: String = "",
    val raidId: String = "",
    val guildId: String = "default-guild",
    val userId: String = "",
    val userName: String = "",
    val amount: Long = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class Reward(
    val id: String = "",
    val guildId: String = "default-guild",
    val name: String = "",
    val description: String = "",
    val gpCost: Long = 100,
    val stock: Long? = null,
    val active: Boolean = true
)

data class Redemption(
    val id: String = "",
    val rewardId: String = "",
    val rewardName: String = "",
    val userId: String = "",
    val userName: String = "",
    val gpCost: Long = 0,
    val status: RedemptionStatus = RedemptionStatus.PENDING,
    val requestedAtMillis: Long = System.currentTimeMillis(),
    val reviewedBy: String? = null,
    val reviewedAtMillis: Long? = null
)

data class PenaltyRecord(
    val id: String = "",
    val guildId: String = "default-guild",
    val questId: String = "",
    val questTitle: String = "",
    val userId: String = "",
    val userName: String = "",
    val cycleKey: String = "",
    val penaltyGp: Long = 0,
    val penaltyExp: Long = 0,
    val reason: String = "",
    val status: PenaltyStatus = PenaltyStatus.PENDING,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val reviewedBy: String? = null,
    val reviewedAtMillis: Long? = null
)

data class Achievement(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val badgeIcon: String = ""
)

data class UserAchievement(
    val id: String = "",
    val achievementId: String = "",
    val userId: String = "",
    val unlockedAtMillis: Long = System.currentTimeMillis()
)
