package com.example.adventurerguild.data

import com.example.adventurerguild.model.*
import com.google.firebase.firestore.DocumentSnapshot

fun DocumentSnapshot.toUserProfile(): UserProfile {
    val exp = getLong("exp") ?: 0
    return UserProfile(
        uid = id,
        email = getString("email").orEmpty(),
        displayName = getString("displayName").orEmpty(),
        role = enumValueOrDefault(getString("role"), UserRole.ADVENTURER),
        guildId = getString("guildId") ?: "default-guild",
        gp = getLong("gp") ?: 0,
        exp = exp,
        level = (getLong("level") ?: levelFromExp(exp)).toInt(),
        rank = enumValueOrDefault(getString("rank"), AdventurerRank.fromExp(exp)),
        title = getString("title") ?: "新手冒險者",
        acceptedQuestIds = (get("acceptedQuestIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        joinedGuildIds = (get("joinedGuildIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        managedGuildIds = (get("managedGuildIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        customTitle = getString("customTitle").orEmpty(),
        guildRoles = (get("guildRoles") as? Map<*, *>)
            ?.mapNotNull { (key, value) -> (key as? String)?.let { it to (value as? String).orEmpty() } }
            ?.toMap()
            ?: emptyMap(),
        guildProgress = (get("guildProgress") as? Map<*, *>)
            ?.mapNotNull { (guildId, rawProgress) ->
                val id = guildId as? String ?: return@mapNotNull null
                val progress = rawProgress as? Map<*, *> ?: return@mapNotNull null
                val guildExp = (progress["exp"] as? Number)?.toLong() ?: 0
                id to GuildProgress(
                    gp = (progress["gp"] as? Number)?.toLong() ?: 0,
                    exp = guildExp,
                    level = (progress["level"] as? Number)?.toInt() ?: levelFromExp(guildExp).toInt(),
                    rank = enumValueOrDefault(progress["rank"] as? String, AdventurerRank.fromExp(guildExp)),
                    title = (progress["title"] as? String).orEmpty().ifBlank { AdventurerRank.fromExp(guildExp).displayName }
                )
            }
            ?.toMap()
            ?: emptyMap()
    )
}

fun UserProfile.toFirestoreMap(): Map<String, Any?> = mapOf(
    "email" to email,
    "displayName" to displayName,
    "role" to role.name,
    "guildId" to guildId,
    "gp" to gp,
    "exp" to exp,
    "level" to level,
    "rank" to rank.name,
    "title" to title,
    "acceptedQuestIds" to acceptedQuestIds,
    "joinedGuildIds" to joinedGuildIds,
    "managedGuildIds" to managedGuildIds,
    "customTitle" to customTitle,
    "guildRoles" to guildRoles,
    "guildProgress" to guildProgress.mapValues { (_, progress) ->
        mapOf(
            "gp" to progress.gp,
            "exp" to progress.exp,
            "level" to progress.level,
            "rank" to progress.rank.name,
            "title" to progress.title
        )
    }
)

fun DocumentSnapshot.toQuest(): Quest = Quest(
    id = id,
    guildId = getString("guildId") ?: "default-guild",
    title = getString("title").orEmpty(),
    description = getString("description").orEmpty(),
    type = enumValueOrDefault(getString("type"), QuestType.DAILY_QUEST),
    status = enumValueOrDefault(getString("status"), QuestStatus.DRAFT),
    gpReward = getLong("gpReward") ?: 10,
    expReward = getLong("expReward") ?: 10,
    targetCount = getLong("targetCount") ?: 1,
    createdBy = getString("createdBy").orEmpty(),
    announcedAtMillis = getLong("announcedAtMillis"),
    acceptStartsAtMillis = getLong("acceptStartsAtMillis"),
    startsAtMillis = getLong("startsAtMillis"),
    endsAtMillis = getLong("endsAtMillis"),
    hasTimeLimit = getBoolean("hasTimeLimit") ?: (getLong("startsAtMillis") != null || getLong("endsAtMillis") != null),
    penaltyGp = getLong("penaltyGp") ?: 0,
    penaltyExp = getLong("penaltyExp") ?: 0,
    activeWeekdays = (get("activeWeekdays") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList(),
    difficulty = enumValueOrDefault(getString("difficulty"), QuestDifficulty.NORMAL),
    tags = (get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
    minRank = enumValueOrDefault(getString("minRank"), AdventurerRank.F),
    assignedAdventurerIds = (get("assignedAdventurerIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
    assignedReviewerIds = (get("assignedReviewerIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
    prerequisiteQuestIds = (get("prerequisiteQuestIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
    bonusGp = getLong("bonusGp") ?: 0,
    bonusExp = getLong("bonusExp") ?: 0,
    gracePeriodDays = (getLong("gracePeriodDays") ?: 0).toInt(),
    submissionDeadlineDays = (getLong("submissionDeadlineDays") ?: 0).toInt(),
    weeklyRefreshWeekday = getLong("weeklyRefreshWeekday")?.toInt(),
    monthlyRefreshDay = getLong("monthlyRefreshDay")?.toInt(),
    repeatLimitType = enumValueOrDefault(getString("repeatLimitType"), RepeatLimitType.NONE),
    repeatLimitCount = (getLong("repeatLimitCount") ?: 0).toInt(),
    proofMode = enumValueOrDefault(getString("proofMode"), QuestProofMode.TEXT),
    autoReviewEnabled = getBoolean("autoReviewEnabled") ?: false,
    pinned = getBoolean("pinned") ?: false,
    sortOrder = (getLong("sortOrder") ?: 0).toInt(),
    pendingChangeSummary = getString("pendingChangeSummary"),
    pendingChangeEffectiveCycle = getString("pendingChangeEffectiveCycle"),
    pendingChangeCreatedAtMillis = getLong("pendingChangeCreatedAtMillis")
)

fun Quest.toFirestoreMap(): Map<String, Any?> = mapOf(
    "guildId" to guildId,
    "title" to title,
    "description" to description,
    "type" to type.name,
    "status" to status.name,
    "gpReward" to gpReward,
    "expReward" to expReward,
    "targetCount" to targetCount,
    "createdBy" to createdBy,
    "announcedAtMillis" to announcedAtMillis,
    "acceptStartsAtMillis" to acceptStartsAtMillis,
    "startsAtMillis" to startsAtMillis,
    "endsAtMillis" to endsAtMillis,
    "hasTimeLimit" to hasTimeLimit,
    "penaltyGp" to penaltyGp,
    "penaltyExp" to penaltyExp,
    "activeWeekdays" to activeWeekdays,
    "difficulty" to difficulty.name,
    "tags" to tags,
    "minRank" to minRank.name,
    "assignedAdventurerIds" to assignedAdventurerIds,
    "assignedReviewerIds" to assignedReviewerIds,
    "prerequisiteQuestIds" to prerequisiteQuestIds,
    "bonusGp" to bonusGp,
    "bonusExp" to bonusExp,
    "gracePeriodDays" to gracePeriodDays,
    "submissionDeadlineDays" to submissionDeadlineDays,
    "weeklyRefreshWeekday" to weeklyRefreshWeekday,
    "monthlyRefreshDay" to monthlyRefreshDay,
    "repeatLimitType" to repeatLimitType.name,
    "repeatLimitCount" to repeatLimitCount,
    "proofMode" to proofMode.name,
    "autoReviewEnabled" to autoReviewEnabled,
    "pinned" to pinned,
    "sortOrder" to sortOrder,
    "pendingChangeSummary" to pendingChangeSummary,
    "pendingChangeEffectiveCycle" to pendingChangeEffectiveCycle,
    "pendingChangeCreatedAtMillis" to pendingChangeCreatedAtMillis
)

fun DocumentSnapshot.toSubmission(): QuestSubmission = QuestSubmission(
    id = id,
    questId = getString("questId").orEmpty(),
    questTitle = getString("questTitle").orEmpty(),
    userId = getString("userId").orEmpty(),
    userName = getString("userName").orEmpty(),
    proofMode = enumValueOrDefault(getString("proofMode"), QuestProofMode.TEXT),
    proofText = getString("proofText").orEmpty(),
    proofImageUrl = getString("proofImageUrl"),
    overachieved = getBoolean("overachieved") ?: false,
    overachievementText = getString("overachievementText").orEmpty(),
    status = enumValueOrDefault(getString("status"), SubmissionStatus.SUBMITTED),
    gpReward = getLong("gpReward") ?: 0,
    expReward = getLong("expReward") ?: 0,
    submittedAtMillis = getLong("submittedAtMillis") ?: 0,
    reviewedBy = getString("reviewedBy"),
    reviewedAtMillis = getLong("reviewedAtMillis"),
    reviewBonusGp = getLong("reviewBonusGp") ?: 0,
    reviewBonusExp = getLong("reviewBonusExp") ?: 0,
    reviewNote = getString("reviewNote")
)

fun QuestSubmission.toFirestoreMap(): Map<String, Any?> = mapOf(
    "questId" to questId,
    "questTitle" to questTitle,
    "userId" to userId,
    "userName" to userName,
    "proofMode" to proofMode.name,
    "proofText" to proofText,
    "proofImageUrl" to proofImageUrl,
    "overachieved" to overachieved,
    "overachievementText" to overachievementText,
    "status" to status.name,
    "gpReward" to gpReward,
    "expReward" to expReward,
    "submittedAtMillis" to submittedAtMillis,
    "reviewedBy" to reviewedBy,
    "reviewedAtMillis" to reviewedAtMillis,
    "reviewBonusGp" to reviewBonusGp,
    "reviewBonusExp" to reviewBonusExp,
    "reviewNote" to reviewNote
)

fun DocumentSnapshot.toReward(): Reward = Reward(
    id = id,
    guildId = getString("guildId") ?: "default-guild",
    name = getString("name").orEmpty(),
    description = getString("description").orEmpty(),
    gpCost = getLong("gpCost") ?: 100,
    stock = getLong("stock"),
    active = getBoolean("active") ?: true
)

fun Reward.toFirestoreMap(): Map<String, Any?> = mapOf(
    "guildId" to guildId,
    "name" to name,
    "description" to description,
    "gpCost" to gpCost,
    "stock" to stock,
    "active" to active
)

fun DocumentSnapshot.toRedemption(): Redemption = Redemption(
    id = id,
    rewardId = getString("rewardId").orEmpty(),
    rewardName = getString("rewardName").orEmpty(),
    userId = getString("userId").orEmpty(),
    userName = getString("userName").orEmpty(),
    gpCost = getLong("gpCost") ?: 0,
    status = enumValueOrDefault(getString("status"), RedemptionStatus.PENDING),
    requestedAtMillis = getLong("requestedAtMillis") ?: 0,
    reviewedBy = getString("reviewedBy"),
    reviewedAtMillis = getLong("reviewedAtMillis")
)

fun Redemption.toFirestoreMap(): Map<String, Any?> = mapOf(
    "rewardId" to rewardId,
    "rewardName" to rewardName,
    "userId" to userId,
    "userName" to userName,
    "gpCost" to gpCost,
    "status" to status.name,
    "requestedAtMillis" to requestedAtMillis,
    "reviewedBy" to reviewedBy,
    "reviewedAtMillis" to reviewedAtMillis
)

fun DocumentSnapshot.toGuildRaid(): GuildRaid = GuildRaid(
    id = id,
    guildId = getString("guildId") ?: "default-guild",
    title = getString("title").orEmpty(),
    description = getString("description").orEmpty(),
    targetProgress = getLong("targetProgress") ?: 100,
    currentProgress = getLong("currentProgress") ?: 0,
    gpRewardPerContribution = getLong("gpRewardPerContribution") ?: 1,
    expRewardPerContribution = getLong("expRewardPerContribution") ?: 1,
    active = getBoolean("active") ?: true,
    endsAtMillis = getLong("endsAtMillis")
)

fun GuildRaid.toFirestoreMap(): Map<String, Any?> = mapOf(
    "guildId" to guildId,
    "title" to title,
    "description" to description,
    "targetProgress" to targetProgress,
    "currentProgress" to currentProgress,
    "gpRewardPerContribution" to gpRewardPerContribution,
    "expRewardPerContribution" to expRewardPerContribution,
    "active" to active,
    "endsAtMillis" to endsAtMillis
)

fun GuildRaidContribution.toFirestoreMap(): Map<String, Any?> = mapOf(
    "raidId" to raidId,
    "guildId" to guildId,
    "userId" to userId,
    "userName" to userName,
    "amount" to amount,
    "createdAtMillis" to createdAtMillis
)

inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, default: T): T =
    raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

fun levelFromExp(exp: Long): Long = (exp / 100) + 1
