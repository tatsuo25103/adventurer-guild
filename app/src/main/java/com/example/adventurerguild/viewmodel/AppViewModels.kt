package com.example.adventurerguild.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adventurerguild.data.AuthRepository
import com.example.adventurerguild.data.QuestRepository
import com.example.adventurerguild.data.RaidRepository
import com.example.adventurerguild.data.RewardRepository
import com.example.adventurerguild.model.*
import com.example.adventurerguild.nearby.NearbyCounterState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class GuildPortalDestination {
    APP_HOME,
    GUILD_SELECTION
}

enum class AuthDestination {
    ACCOUNT_ENTRY,
    ROLE_SELECTION
}

data class GuildUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val user: UserProfile? = null,
    val activeGuild: Guild? = null,
    val authDestination: AuthDestination = AuthDestination.ACCOUNT_ENTRY,
    val portalDestination: GuildPortalDestination = GuildPortalDestination.APP_HOME,
    val joinedGuilds: List<Guild> = emptyList(),
    val managedGuilds: List<Guild> = emptyList(),
    val quests: List<Quest> = emptyList(),
    val questTemplates: List<QuestTemplate> = emptyList(),
    val submissions: List<QuestSubmission> = emptyList(),
    val pendingSubmissions: List<QuestSubmission> = emptyList(),
    val rewards: List<Reward> = emptyList(),
    val redemptions: List<Redemption> = emptyList(),
    val pendingPenaltyRecords: List<PenaltyRecord> = emptyList(),
    val raids: List<GuildRaid> = emptyList(),
    val raidContributions: List<GuildRaidContribution> = emptyList(),
    val guildMembers: List<UserProfile> = emptyList(),
    val joinRequests: List<UserProfile> = emptyList(),
    val counterSessions: List<GuildCounterSession> = emptyList(),
    val nearbyCounter: NearbyCounterState = NearbyCounterState(),
    val accountTransferUserId: String? = null,
    val accountTransferCode: String? = null,
    val accountTransferExpiresAt: Long? = null,
    val generatedInviteGuildId: String? = null,
    val generatedInviteCode: String? = null,
    val generatedInviteOneTime: Boolean = false,
    val generatedInviteExpiresAt: Long? = null
)

class GuildViewModel(
    private val authRepository: AuthRepository,
    private val questRepository: QuestRepository,
    private val rewardRepository: RewardRepository,
    private val raidRepository: RaidRepository
) : ViewModel(), GuildController {
    private val _state = MutableStateFlow(GuildUiState(loading = true))
    override val state: StateFlow<GuildUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { authRepository.loadCurrentProfile() }
                .onSuccess { profile ->
                    _state.value = _state.value.copy(user = profile, loading = false)
                    profile?.let { refreshAll(it) }
                }
                .onFailure { showError(it) }
        }
    }

    override fun register(email: String, password: String, displayName: String, asAdmin: Boolean) = launchBusy {
        val profile = authRepository.register(email, password, displayName, asAdmin)
        seedMvpData(profile)
        _state.value = GuildUiState(user = profile)
        refreshAll(profile)
    }

    override fun login(email: String, password: String) = launchBusy {
        val profile = authRepository.login(email, password)
        seedMvpData(profile)
        _state.value = GuildUiState(user = profile)
        refreshAll(profile)
    }

    override fun loginWithDevice(asAdmin: Boolean) {
        reportError("此資料來源不支援裝置 UUID 登入。")
    }

    override fun updateDisplayName(displayName: String) {
        reportError("此資料來源尚未支援名稱修改。")
    }

    override fun rotateGuildInvite(guildId: String) {
        reportError("此資料來源尚未支援邀請碼輪替。")
    }

    override fun createOneTimeGuildInvite(guildId: String) {
        reportError("此資料來源尚未支援一次性邀請。")
    }

    override fun loginWithGoogle(idToken: String, asAdmin: Boolean) = launchBusy {
        val profile = authRepository.loginWithGoogle(idToken, asAdmin)
        seedMvpData(profile)
        _state.value = GuildUiState(user = profile)
        refreshAll(profile)
    }

    override fun logout() {
        authRepository.logout()
        _state.value = GuildUiState()
    }

    override fun returnToAccountEntry() {
        authRepository.logout()
        _state.value = GuildUiState(authDestination = AuthDestination.ACCOUNT_ENTRY)
    }

    override fun returnToRoleSelection() {
        authRepository.logout()
        _state.value = GuildUiState(authDestination = AuthDestination.ROLE_SELECTION)
    }

    override fun reportError(message: String) {
        _state.value = _state.value.copy(loading = false, error = message)
    }

    override fun updateCustomTitle(title: String) = launchBusy {
        val user = requireUser()
        authRepository.updateCustomTitle(user.uid, title)
        val updatedUser = authRepository.loadProfile(user.uid)
        _state.value = _state.value.copy(user = updatedUser)
        refreshAll(updatedUser)
    }

    override fun assignGuildRole(member: UserProfile, guildId: String, roleTitle: String) = launchBusy {
        val user = requireUser()
        require(guildId in user.managedGuildIds || user.role == UserRole.GUILD_ADMIN) { "只有公會管理員可以指派職務。" }
        authRepository.assignGuildRole(member.uid, guildId, roleTitle)
        refreshAll(authRepository.loadProfile(user.uid))
    }

    override fun updateGuildRankTitle(rank: AdventurerRank, title: String) {
        reportError("Firebase 模式的公會分級名稱尚未接上；目前請使用離線模式試玩。")
    }

    override fun updateGuildRolePermission(roleTitle: String, permission: GuildPermission, enabled: Boolean) {
        reportError("Firebase 模式的職務權限尚未接上；目前請使用離線模式試玩。")
    }

    override fun approveJoinRequest(member: UserProfile, approved: Boolean) {
        reportError("Firebase 模式的加入審核尚未接上；目前請使用離線模式試玩。")
    }

    override fun removeGuildMember(member: UserProfile) {
        reportError("Firebase 模式的移除會員尚未接上；目前請使用離線模式試玩。")
    }

    override fun updateGuildAnnouncement(message: String) {
        reportError("Firebase 模式的公會公告尚未接上；目前請使用離線模式試玩。")
    }

    override fun updateGuildVacation(enabled: Boolean, note: String) {
        reportError("Firebase 模式的公會休假尚未接上；目前請使用離線模式試玩。")
    }

    override fun refresh() {
        _state.value.user?.let { user ->
            viewModelScope.launch { refreshAll(user) }
        }
    }

    override fun createGuild(name: String) {
        reportError("Firebase 模式的公會建立尚未接上；目前請使用離線模式試玩。")
    }

    override fun joinGuild(inviteCode: String) {
        reportError("Firebase 模式的加入公會尚未接上；目前請使用離線模式試玩。")
    }

    override fun joinGuildAsManager(inviteCode: String) {
        reportError("Firebase 模式的管理方加入尚未接上；目前請使用離線模式試玩。")
    }

    override fun selectGuild(guildId: String) {
        val user = _state.value.user ?: return
        val guild = Guild(id = user.guildId, name = "Default Guild", ownerUid = user.uid, inviteCode = "DEFAULT")
        _state.value = _state.value.copy(activeGuild = guild, joinedGuilds = listOf(guild), managedGuilds = if (user.role == UserRole.GUILD_ADMIN) listOf(guild) else emptyList())
        refresh()
    }

    override fun backToGuildPortal() {
        _state.value = _state.value.copy(
            activeGuild = null,
            portalDestination = GuildPortalDestination.APP_HOME
        )
    }

    override fun backToGuildSelection() {
        _state.value = _state.value.copy(
            activeGuild = null,
            portalDestination = GuildPortalDestination.GUILD_SELECTION
        )
    }

    override fun createQuest(
        title: String,
        description: String,
        type: QuestType,
        gp: Long,
        exp: Long,
        announcedAtMillis: Long?,
        acceptStartsAtMillis: Long?,
        hasTimeLimit: Boolean,
        startsAtMillis: Long?,
        endsAtMillis: Long?,
        penaltyGp: Long,
        penaltyExp: Long,
        activeWeekdays: List<Int>,
        difficulty: QuestDifficulty,
        tags: List<String>,
        minRank: AdventurerRank,
        assignedAdventurerIds: List<String>,
        assignedReviewerIds: List<String>,
        prerequisiteQuestIds: List<String>,
        bonusGp: Long,
        bonusExp: Long,
        gracePeriodDays: Int,
        submissionDeadlineDays: Int,
        weeklyRefreshWeekday: Int?,
        monthlyRefreshDay: Int?,
        repeatLimitType: RepeatLimitType,
        repeatLimitCount: Int,
        formationSlots: List<QuestSlot>,
        formationRequired: Boolean,
        formationMinSlotsPerUser: Int,
        formationMaxSlotsPerUser: Int,
        formationRollMode: FormationRollMode,
        formationAutoRollAtMillis: Long?,
        proofMode: QuestProofMode,
        autoReviewEnabled: Boolean,
        pinned: Boolean,
        createAsDraft: Boolean
    ) = launchBusy {
        val user = requireUser()
        require(type != QuestType.GUILD_RAID) { "公會討伐戰請在討伐頁建立，不走一般任務流程。" }
        if (type == QuestType.LIMITED_EVENT_QUEST) {
            require(endsAtMillis != null) { "限時討伐令必須設定活動結束日期。" }
        }
        val guild = _state.value.activeGuild ?: Guild(id = user.guildId, name = "Default Guild", ownerUid = user.uid)
        val guildUsers = (_state.value.guildMembers + user).distinctBy { it.uid }
        val quest = Quest(
                guildId = user.guildId,
                title = title,
                description = description,
                type = type,
                status = if (createAsDraft) QuestStatus.DRAFT else QuestStatus.PUBLISHED,
                gpReward = gp,
                expReward = exp,
                createdBy = user.uid,
                announcedAtMillis = announcedAtMillis,
                acceptStartsAtMillis = acceptStartsAtMillis,
                hasTimeLimit = hasTimeLimit || type == QuestType.LIMITED_EVENT_QUEST,
                startsAtMillis = startsAtMillis,
                endsAtMillis = endsAtMillis,
                penaltyGp = if (type == QuestType.LIMITED_EVENT_QUEST) 0 else penaltyGp,
                penaltyExp = if (type == QuestType.LIMITED_EVENT_QUEST) 0 else penaltyExp,
                activeWeekdays = activeWeekdays,
                difficulty = difficulty,
                tags = tags,
                minRank = minRank,
                assignedAdventurerIds = assignedAdventurerIds.distinct(),
                assignedReviewerIds = assignedReviewerIds.distinct(),
                prerequisiteQuestIds = prerequisiteQuestIds.distinct().filterNot { it.isBlank() },
                bonusGp = bonusGp,
                bonusExp = bonusExp,
                gracePeriodDays = if (type == QuestType.LIMITED_EVENT_QUEST) 0 else gracePeriodDays,
                submissionDeadlineDays = if (type == QuestType.LIMITED_EVENT_QUEST) 0 else submissionDeadlineDays,
                weeklyRefreshWeekday = weeklyRefreshWeekday,
                monthlyRefreshDay = monthlyRefreshDay,
                repeatLimitType = if (type == QuestType.REPEATABLE_QUEST) repeatLimitType else RepeatLimitType.NONE,
                repeatLimitCount = if (type == QuestType.REPEATABLE_QUEST) repeatLimitCount.coerceAtLeast(0) else 0,
                formationSlots = if (type == QuestType.FORMATION_QUEST) formationSlots.normalizedFormationSlots() else emptyList(),
                formationRequired = type == QuestType.FORMATION_QUEST && formationRequired,
                formationMinSlotsPerUser = if (type == QuestType.FORMATION_QUEST) formationMinSlotsPerUser.coerceAtLeast(0) else 0,
                formationMaxSlotsPerUser = if (type == QuestType.FORMATION_QUEST) formationMaxSlotsPerUser.coerceAtLeast(1) else 1,
                formationRollMode = if (type == QuestType.FORMATION_QUEST) formationRollMode else FormationRollMode.OPTIONAL_SELF_SELECT,
                formationAutoRollAtMillis = if (type == QuestType.FORMATION_QUEST) formationAutoRollAtMillis else null,
                proofMode = proofMode,
                autoReviewEnabled = autoReviewEnabled,
                pinned = pinned
            ).normalizedTimingPolicy()
        quest.validateGovernancePolicy(guild, guildUsers, user)
        questRepository.upsertQuest(quest)
        refreshAll(user)
    }

    override fun editQuest(original: Quest, updated: Quest, changeSummary: String) = launchBusy {
        val user = requireUser()
        if (updated.type == QuestType.LIMITED_EVENT_QUEST) {
            require(updated.endsAtMillis != null) { "限時討伐令必須設定活動結束日期。" }
        }
        val guild = _state.value.activeGuild ?: Guild(id = user.guildId, name = "Default Guild", ownerUid = user.uid)
        val guildUsers = (_state.value.guildMembers + user).distinctBy { it.uid }
        val normalized = updated.normalizedTimingPolicy()
        normalized.validateGovernancePolicy(guild, guildUsers, user)
        questRepository.upsertQuest(original.applyEditPolicy(normalized, changeSummary, System.currentTimeMillis()))
        refreshAll(user)
    }

    override fun duplicateQuest(quest: Quest) = launchBusy {
        val user = requireUser()
        questRepository.upsertQuest(
            quest.copy(
                id = "",
                title = "${quest.title} Copy",
                status = QuestStatus.DRAFT,
                createdBy = user.uid,
                pendingChangeSummary = null,
                pendingChangeEffectiveCycle = null,
                pendingChangeCreatedAtMillis = null,
                pendingChangeQuest = null
            )
        )
        refreshAll(user)
    }

    override fun seedChildDailyQuests() = launchBusy {
        val user = requireUser()
        childDailyQuestTemplates(user.guildId, user.uid).forEach { questRepository.upsertQuest(it) }
        refreshAll(user)
    }

    override fun saveQuestAsTemplate(quest: Quest) {
        val user = _state.value.user ?: return
        val templateQuest = quest.asTemplateQuest()
        val template = QuestTemplate(
            id = "template-${System.currentTimeMillis()}",
            guildId = user.guildId,
            name = quest.title,
            quest = templateQuest,
            createdBy = user.uid
        )
        _state.value = _state.value.copy(
            questTemplates = (_state.value.questTemplates + template).distinctBy { it.name to it.guildId }
        )
    }

    override fun submitQuest(
        quest: Quest,
        proofText: String,
        proofImageUrl: String?,
        overachieved: Boolean,
        overachievementText: String
    ) = launchBusy {
        val user = requireUser()
        validateFirebaseQuestSubmission(quest, user)
        if (quest.proofMode == QuestProofMode.TEXT) {
            require(proofText.isNotBlank()) { "此任務需要填寫文字回報。" }
        }
        questRepository.submitQuest(quest, user, proofText, proofImageUrl, overachieved, overachievementText)
        refreshAll(user)
    }

    override fun acceptQuest(quest: Quest) = launchBusy {
        val user = requireUser()
        validateFirebaseQuestAcceptance(quest, user)
        questRepository.acceptQuest(quest.id, user.uid)
        val updatedUser = authRepository.loadProfile(user.uid)
        _state.value = _state.value.copy(user = updatedUser)
        refreshAll(updatedUser)
    }

    override fun selectFormationSlot(quest: Quest, slot: QuestSlot) {
        reportError("雲端模式尚未支援戰團編成令選位；目前請使用離線模式測試。")
    }

    override fun rollFormationQuest(quest: Quest) {
        reportError("雲端模式尚未支援戰團編成令 Roll；目前請使用離線模式測試。")
    }

    override fun confirmCounterSession(session: GuildCounterSession) {
        reportError("雲端模式尚未支援公會櫃檯；目前請使用離線模式測試雙方會面流程。")
    }

    override fun cancelCounterSession(session: GuildCounterSession) {
        reportError("雲端模式尚未支援公會櫃檯；目前請使用離線模式測試雙方會面流程。")
    }

    override fun startNearbyCounter(session: GuildCounterSession) {
        reportError("此模式尚未支援 Nearby 櫃檯。")
    }

    override fun confirmNearbyCounter(session: GuildCounterSession) {
        reportError("此模式尚未支援 Nearby 櫃檯。")
    }

    override fun stopNearbyCounter() = Unit

    override fun setQuestStatus(quest: Quest, status: QuestStatus) = launchBusy {
        val user = requireUser()
        val guild = _state.value.activeGuild ?: Guild(id = user.guildId, name = "Default Guild", ownerUid = user.uid)
        val guildUsers = (_state.value.guildMembers + user).distinctBy { it.uid }
        quest.validateStatusTransition(status)
        if (status == QuestStatus.PUBLISHED || status == QuestStatus.AVAILABLE) {
            quest.copy(status = status).validateGovernancePolicy(guild, guildUsers, user)
        }
        questRepository.setQuestStatus(quest.id, status)
        refreshAll(user)
    }

    override fun reviewSubmission(
        submission: QuestSubmission,
        approved: Boolean,
        note: String?,
        bonusGp: Long,
        bonusExp: Long
    ) = launchBusy {
        val user = requireUser()
        val guild = _state.value.activeGuild ?: error("尚未選擇公會。")
        val quest = _state.value.quests.firstOrNull { it.id == submission.questId } ?: error("找不到任務。")
        require(user.canReviewQuestSubmission(guild, quest)) { "沒有審核此任務回報的權限。" }
        require(submission.status == SubmissionStatus.SUBMITTED) { "此任務回報已結算，不能重複審核。" }
        if (bonusGp > 0 || bonusExp > 0) {
            require(user.hasGuildPermission(guild, GuildPermission.ADJUST_OVERACHIEVEMENT_REWARDS)) { "沒有調整超額提交獎勵權限。" }
        }
        questRepository.reviewSubmission(submission, user.uid, approved, note, bonusGp, bonusExp)
        val updatedUser = authRepository.loadProfile(user.uid)
        _state.value = _state.value.copy(user = updatedUser)
        refreshAll(updatedUser)
    }

    override fun createReward(name: String, description: String, cost: Long) = launchBusy {
        val user = requireUser()
        rewardRepository.upsertReward(Reward(guildId = user.guildId, name = name, description = description, gpCost = cost))
        refreshAll(user)
    }

    override fun redeem(reward: Reward) = launchBusy {
        val user = requireUser()
        rewardRepository.redeem(reward, user)
        val updatedUser = authRepository.loadProfile(user.uid)
        _state.value = _state.value.copy(user = updatedUser)
        refreshAll(updatedUser)
    }

    override fun reviewRedemption(redemption: Redemption, approved: Boolean) = launchBusy {
        val user = requireUser()
        rewardRepository.reviewRedemption(redemption, user.uid, approved)
        refreshAll(user)
    }

    override fun reviewPenalty(record: PenaltyRecord, apply: Boolean) {
        reportError("Firebase 模式的處罰審核尚未接上；目前請使用離線模式試玩。")
    }

    override fun adjustMemberGpExp(member: UserProfile, gpDelta: Long, expDelta: Long, reason: String) {
        reportError("Firebase 模式的手動獎懲修正尚未接上；目前請使用離線模式試玩。")
    }

    override fun contributeToRaid(raid: GuildRaid, amount: Long) = launchBusy {
        val user = requireUser()
        raidRepository.contribute(raid, user, amount)
        val updatedUser = authRepository.loadProfile(user.uid)
        _state.value = _state.value.copy(user = updatedUser)
        refreshAll(updatedUser)
    }

    private suspend fun refreshAll(user: UserProfile) {
        val quests = if (user.role == UserRole.GUILD_ADMIN) {
            questRepository.listAllQuests(user.guildId)
        } else {
            questRepository.listPublishedQuests(user.guildId)
        }
        val guild = _state.value.activeGuild
        val questById = quests.associateBy { it.id }
        val pendingSubmissions = if (user.role == UserRole.GUILD_ADMIN && guild != null) {
            questRepository.listPendingSubmissions().filter { submission ->
                val quest = questById[submission.questId]
                quest?.guildId == guild.id && user.canReviewQuestSubmission(guild, quest)
            }
        } else {
            emptyList()
        }
        _state.value = _state.value.copy(
            loading = false,
            error = null,
            quests = quests,
            submissions = questRepository.listUserSubmissions(user.uid),
            pendingSubmissions = pendingSubmissions,
            rewards = rewardRepository.listRewards(user.guildId),
            redemptions = if (user.role == UserRole.GUILD_ADMIN) rewardRepository.listPendingRedemptions() else emptyList(),
            raids = raidRepository.listActiveRaids(user.guildId),
            guildMembers = authRepository.listGuildMembers(user.guildId)
        )
    }

    private fun validateFirebaseQuestAcceptance(quest: Quest, user: UserProfile) {
        val now = System.currentTimeMillis()
        require(user.role == UserRole.ADVENTURER) { "只有冒險者可以接取任務。" }
        require(quest.guildId == user.guildId) { "任務不屬於目前公會。" }
        require(quest.status == QuestStatus.PUBLISHED || quest.status == QuestStatus.AVAILABLE) { "任務尚未上架。" }
        require(!quest.type.isStrictCycleType()) { "每日、每週與每月任務是強制任務，不需要接取。" }
        require(quest.type != QuestType.GUILD_RAID) { "公會討伐戰不需個別接取。" }
        require(quest.assignedAdventurerIds.isEmpty() || user.uid in quest.assignedAdventurerIds) { "此任務已指名給其他冒險者。" }
        require(user.rank.ordinal >= quest.minRank.ordinal) { "Rank 不足，無法接取此任務。" }
        require(quest.id !in user.acceptedQuestIds) { "你已經接取此任務。" }
        require(quest.announcedAtMillis == null || quest.announcedAtMillis <= now) { "任務尚未公告。" }
        require(quest.acceptStartsAtMillis == null || quest.acceptStartsAtMillis <= now) { "任務尚未開放接取。" }
        require(!quest.hasTimeLimit || quest.startsAtMillis == null || quest.startsAtMillis <= now) { "任務尚未開始。" }
        require(!quest.hasTimeLimit || quest.endsAtMillis == null || now <= quest.endsAtMillis) { "任務已過期。" }
    }

    private fun validateFirebaseQuestSubmission(quest: Quest, user: UserProfile) {
        val now = System.currentTimeMillis()
        require(user.role == UserRole.ADVENTURER) { "只有冒險者可以提交任務。" }
        require(quest.guildId == user.guildId) { "任務不屬於目前公會。" }
        require(quest.status == QuestStatus.PUBLISHED || quest.status == QuestStatus.AVAILABLE) { "任務尚未上架。" }
        require(quest.type != QuestType.GUILD_RAID) { "公會討伐戰不使用一般回報流程。" }
        require(quest.assignedAdventurerIds.isEmpty() || user.uid in quest.assignedAdventurerIds) { "此任務已指名給其他冒險者。" }
        require(user.rank.ordinal >= quest.minRank.ordinal) { "Rank 不足，無法提交此任務。" }
        require(quest.announcedAtMillis == null || quest.announcedAtMillis <= now) { "任務尚未公告。" }
        require(!quest.hasTimeLimit || quest.startsAtMillis == null || quest.startsAtMillis <= now) { "任務尚未開始。" }
        require(!quest.hasTimeLimit || quest.endsAtMillis == null || now <= quest.endsAtMillis) { "任務已過期。" }
        require(quest.type.isStrictCycleType() || quest.type == QuestType.REPEATABLE_QUEST || quest.type == QuestType.FORMATION_QUEST || quest.id in user.acceptedQuestIds) {
            "非固定強制任務請先接取後再提交回報。"
        }
        require(_state.value.submissions.none {
            it.questId == quest.id && it.userId == user.uid && it.status == SubmissionStatus.SUBMITTED
        }) { "此任務已有待審回報，請等管理員審核後再提交。" }
    }

    private suspend fun seedMvpData(user: UserProfile) {
        questRepository.seedDefaultQuests(user.uid, user.guildId)
        rewardRepository.seedDefaultRewards(user.guildId)
        raidRepository.seedDefaultRaid(user.guildId)
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { block() }
                .onFailure { showError(it) }
            _state.value = _state.value.copy(loading = false)
        }
    }

    private fun showError(throwable: Throwable) {
        _state.value = _state.value.copy(loading = false, error = throwable.message ?: "發生未知錯誤")
    }

    private fun requireUser(): UserProfile = _state.value.user ?: error("尚未登入")
}

private fun Quest.applyEditPolicy(updated: Quest, changeSummary: String, now: Long): Quest {
    val isUnannounced = announcedAtMillis == null || announcedAtMillis > now
    if (isUnannounced || !isFixedCycleQuest()) {
        return updated.copy(
            pendingChangeSummary = null,
            pendingChangeEffectiveCycle = null,
            pendingChangeCreatedAtMillis = null
        )
    }
    return copy(
        pendingChangeSummary = changeSummary.ifBlank { describeQuestChanges(this, updated) },
        pendingChangeEffectiveCycle = nextCycleLabel(),
        pendingChangeCreatedAtMillis = now,
        pendingChangeQuest = updated.copy(
            id = id,
            guildId = guildId,
            status = status,
            createdBy = createdBy,
            pendingChangeSummary = null,
            pendingChangeEffectiveCycle = null,
            pendingChangeCreatedAtMillis = null,
            pendingChangeQuest = null
        )
    )
}

private fun Quest.isFixedCycleQuest(): Boolean =
    type == QuestType.DAILY_QUEST || type == QuestType.WEEKLY_QUEST || type == QuestType.MONTHLY_QUEST

private fun Quest.nextCycleLabel(): String = when (type) {
    QuestType.DAILY_QUEST -> "下一個每日循環"
    QuestType.WEEKLY_QUEST -> "下一個每週循環"
    QuestType.MONTHLY_QUEST -> "下一個每月循環"
    else -> "下一個循環"
}

private fun describeQuestChanges(old: Quest, updated: Quest): String {
    val fields = buildList {
        if (old.title != updated.title) add("標題")
        if (old.description != updated.description) add("描述")
        if (old.type != updated.type) add("類型")
        if (old.gpReward != updated.gpReward) add("GP")
        if (old.expReward != updated.expReward) add("EXP")
        if (old.announcedAtMillis != updated.announcedAtMillis) add("公告日期")
        if (old.acceptStartsAtMillis != updated.acceptStartsAtMillis) add("開放接取日期")
        if (old.hasTimeLimit != updated.hasTimeLimit || old.startsAtMillis != updated.startsAtMillis || old.endsAtMillis != updated.endsAtMillis) add("期間")
        if (old.penaltyGp != updated.penaltyGp || old.penaltyExp != updated.penaltyExp) add("未完成處罰")
        if (old.activeWeekdays != updated.activeWeekdays) add("星期")
    }
    return if (fields.isEmpty()) "任務設定已調整" else fields.joinToString("、") + "將調整"
}

private fun Quest.asTemplateQuest(): Quest = copy(
    id = "",
    status = QuestStatus.DRAFT,
    announcedAtMillis = null,
    acceptStartsAtMillis = null,
    startsAtMillis = null,
    endsAtMillis = null,
    createdBy = "",
    pendingChangeSummary = null,
    pendingChangeEffectiveCycle = null,
    pendingChangeCreatedAtMillis = null,
    pendingChangeQuest = null
).normalizedTimingPolicy()
