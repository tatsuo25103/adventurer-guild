package com.example.adventurerguild.drive

import com.example.adventurerguild.model.QuestStatus
import com.example.adventurerguild.model.QuestType
import com.example.adventurerguild.model.AdventurerRank
import com.example.adventurerguild.model.QuestDifficulty
import com.example.adventurerguild.model.RedemptionStatus
import com.example.adventurerguild.model.SubmissionStatus

object GuildDriveSyncContract {
    const val MANIFEST = "guild_manifest.json"
    const val QUESTS_DIR = "quests"
    const val RAIDS_DIR = "guild_raids"
    const val REWARDS_DIR = "rewards"
    const val MEMBER_EVENTS_DIR = "member_events"
    const val SNAPSHOTS_DIR = "snapshots"
    const val CURRENT_STATE = "snapshots/current_state.json"
}

data class DriveGuildJoinRequest(
    val schemaVersion: Int = 1,
    val requestId: String,
    val guildId: String,
    val inviteCode: String,
    val applicantUid: String,
    val applicantEmail: String,
    val applicantName: String,
    val requestedSide: GuildDriveMemberSide,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class GuildDriveManifest(
    val schemaVersion: Int = 1,
    val guildId: String,
    val guildName: String,
    val ownerUid: String,
    val ownerEmail: String,
    val updatedAtMillis: Long,
    val version: Long = 1,
    val latestEventSeq: Long = 0,
    val snapshotFile: String = GuildDriveSyncContract.CURRENT_STATE,
    val minAppVersionCode: Int = 1
)

data class DriveQuestConfig(
    val id: String,
    val title: String,
    val description: String,
    val type: QuestType,
    val status: QuestStatus,
    val gpReward: Long,
    val expReward: Long,
    val targetCount: Long = 1,
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
    val pinned: Boolean = false,
    val sortOrder: Int = 0,
    val pendingChangeSummary: String? = null,
    val pendingChangeEffectiveCycle: String? = null,
    val pendingChangeCreatedAtMillis: Long? = null,
    val autoReviewEnabled: Boolean = false,
    val revision: Long = 1
)

data class DriveMemberEvent(
    val id: String,
    val guildId: String,
    val userId: String,
    val userName: String,
    val eventType: DriveMemberEventType,
    val targetId: String,
    val payloadText: String = "",
    val amount: Long = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)

enum class DriveMemberEventType {
    ACCEPT_QUEST,
    SUBMIT_QUEST,
    REVIEW_SUBMISSION,
    CHANGE_QUEST,
    RAID_CONTRIBUTION,
    REQUEST_REDEMPTION,
    REVIEW_REDEMPTION,
    CHANGE_REWARD,
    CHANGE_GUILD_SETTINGS,
    CHANGE_MEMBER_ROLE
}

data class DriveGuildStateSnapshot(
    val guildId: String,
    val generatedAtMillis: Long,
    val questSubmissions: List<DriveSubmissionState>,
    val raidProgress: List<DriveRaidProgressState>,
    val redemptions: List<DriveRedemptionState>
)

data class DriveSubmissionState(
    val submissionId: String,
    val questId: String,
    val userId: String,
    val status: SubmissionStatus,
    val proofText: String,
    val proofImageUrl: String? = null,
    val overachieved: Boolean = false,
    val overachievementText: String = "",
    val reviewedBy: String? = null,
    val reviewedAtMillis: Long? = null,
    val reviewBonusGp: Long = 0,
    val reviewBonusExp: Long = 0
)

data class DriveRaidProgressState(
    val raidId: String,
    val currentProgress: Long,
    val contributionByUser: Map<String, Long>
)

data class DriveRedemptionState(
    val redemptionId: String,
    val rewardId: String,
    val userId: String,
    val status: RedemptionStatus
)
