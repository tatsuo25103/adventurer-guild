package com.example.adventurerguild.viewmodel

import com.example.adventurerguild.model.GuildRaid
import com.example.adventurerguild.model.GuildPermission
import com.example.adventurerguild.model.Quest
import com.example.adventurerguild.model.AdventurerRank
import com.example.adventurerguild.model.PenaltyRecord
import com.example.adventurerguild.model.QuestDifficulty
import com.example.adventurerguild.model.QuestStatus
import com.example.adventurerguild.model.QuestSubmission
import com.example.adventurerguild.model.QuestType
import com.example.adventurerguild.model.Redemption
import com.example.adventurerguild.model.Reward
import com.example.adventurerguild.model.UserProfile
import kotlinx.coroutines.flow.StateFlow

interface GuildController {
    val state: StateFlow<GuildUiState>

    fun register(email: String, password: String, displayName: String, asAdmin: Boolean)
    fun login(email: String, password: String)
    fun loginWithDevice(asAdmin: Boolean)
    fun loginOfflineTest(asAdmin: Boolean) {
        reportError("此登入方式僅供離線測試模式使用。")
    }
    fun loginWithGoogle(idToken: String, asAdmin: Boolean)
    fun setDriveAccessToken(accessToken: String?) {}
    fun createAccountTransferCode() {
        reportError("此版本不支援帳號移機。")
    }
    fun inheritCloudAccount(userId: String, transferCode: String) {
        reportError("此版本不支援帳號移機。")
    }
    fun logout()
    fun returnToAccountEntry() {
        logout()
    }
    fun returnToRoleSelection() {
        logout()
    }
    fun reportError(message: String)
    fun refresh()
    fun refreshCounterSessions() {}
    fun updateDisplayName(displayName: String)
    fun updateCustomTitle(title: String)
    fun rotateGuildInvite(guildId: String)
    fun createOneTimeGuildInvite(guildId: String)
    fun assignGuildRole(member: UserProfile, guildId: String, roleTitle: String)
    fun updateGuildRankTitle(rank: AdventurerRank, title: String)
    fun updateGuildRolePermission(roleTitle: String, permission: GuildPermission, enabled: Boolean)
    fun approveJoinRequest(member: UserProfile, approved: Boolean)
    fun removeGuildMember(member: UserProfile)
    fun updateGuildAnnouncement(message: String)
    fun updateGuildVacation(enabled: Boolean, note: String)
    fun createGuild(name: String)
    fun joinGuild(inviteCode: String)
    fun joinGuildAsManager(inviteCode: String)
    fun selectGuild(guildId: String)
    fun backToGuildPortal()
    fun backToGuildSelection() {
        backToGuildPortal()
    }
    fun createQuest(
        title: String,
        description: String,
        type: QuestType,
        gp: Long,
        exp: Long,
        announcedAtMillis: Long? = null,
        acceptStartsAtMillis: Long? = null,
        hasTimeLimit: Boolean = false,
        startsAtMillis: Long? = null,
        endsAtMillis: Long? = null,
        penaltyGp: Long = 0,
        penaltyExp: Long = 0,
        activeWeekdays: List<Int> = emptyList(),
        difficulty: QuestDifficulty = QuestDifficulty.NORMAL,
        tags: List<String> = emptyList(),
        minRank: AdventurerRank = AdventurerRank.F,
        assignedAdventurerIds: List<String> = emptyList(),
        assignedReviewerIds: List<String> = emptyList(),
        prerequisiteQuestIds: List<String> = emptyList(),
        bonusGp: Long = 0,
        bonusExp: Long = 0,
        gracePeriodDays: Int = 0,
        submissionDeadlineDays: Int = 0,
        weeklyRefreshWeekday: Int? = null,
        monthlyRefreshDay: Int? = null,
        repeatLimitType: com.example.adventurerguild.model.RepeatLimitType = com.example.adventurerguild.model.RepeatLimitType.NONE,
        repeatLimitCount: Int = 0,
        formationSlots: List<com.example.adventurerguild.model.QuestSlot> = emptyList(),
        formationRequired: Boolean = false,
        formationMinSlotsPerUser: Int = 1,
        formationMaxSlotsPerUser: Int = 1,
        formationRollMode: com.example.adventurerguild.model.FormationRollMode = com.example.adventurerguild.model.FormationRollMode.OPTIONAL_SELF_SELECT,
        formationAutoRollAtMillis: Long? = null,
        proofMode: com.example.adventurerguild.model.QuestProofMode = com.example.adventurerguild.model.QuestProofMode.TEXT,
        autoReviewEnabled: Boolean = false,
        pinned: Boolean = false,
        createAsDraft: Boolean = false
    )
    fun editQuest(original: Quest, updated: Quest, changeSummary: String = "")
    fun duplicateQuest(quest: Quest)
    fun seedChildDailyQuests()
    fun saveQuestAsTemplate(quest: Quest)
    fun submitQuest(
        quest: Quest,
        proofText: String = "",
        proofImageUrl: String? = null,
        overachieved: Boolean = false,
        overachievementText: String = ""
    )
    fun acceptQuest(quest: Quest)
    fun selectFormationSlot(quest: Quest, slot: com.example.adventurerguild.model.QuestSlot)
    fun rollFormationQuest(quest: Quest)
    fun confirmCounterSession(session: com.example.adventurerguild.model.GuildCounterSession)
    fun cancelCounterSession(session: com.example.adventurerguild.model.GuildCounterSession)
    fun startNearbyCounter(session: com.example.adventurerguild.model.GuildCounterSession)
    fun confirmNearbyCounter(session: com.example.adventurerguild.model.GuildCounterSession)
    fun stopNearbyCounter()
    fun setQuestStatus(quest: Quest, status: QuestStatus)
    fun reviewSubmission(
        submission: QuestSubmission,
        approved: Boolean,
        note: String? = null,
        bonusGp: Long = 0,
        bonusExp: Long = 0
    )
    fun createReward(name: String, description: String, cost: Long)
    fun redeem(reward: Reward)
    fun reviewRedemption(redemption: Redemption, approved: Boolean)
    fun reviewPenalty(record: PenaltyRecord, apply: Boolean)
    fun adjustMemberGpExp(member: UserProfile, gpDelta: Long, expDelta: Long, reason: String)
    fun contributeToRaid(raid: GuildRaid, amount: Long)
}
