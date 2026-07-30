package com.example.adventurerguild.data

import com.example.adventurerguild.model.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class QuestRepository(private val db: FirebaseFirestore) {
    suspend fun listPublishedQuests(guildId: String): List<Quest> =
        db.collection(FirestoreCollections.QUESTS)
            .whereEqualTo("guildId", guildId)
            .whereIn("status", listOf(QuestStatus.PUBLISHED.name, QuestStatus.AVAILABLE.name))
            .get()
            .await()
            .documents
            .map { it.toQuest() }
            .filter { it.announcedAtMillis == null || it.announcedAtMillis <= System.currentTimeMillis() }

    suspend fun listAllQuests(guildId: String): List<Quest> =
        db.collection(FirestoreCollections.QUESTS)
            .whereEqualTo("guildId", guildId)
            .get()
            .await()
            .documents
            .map { it.toQuest() }

    suspend fun upsertQuest(quest: Quest): String {
        val collection = db.collection(FirestoreCollections.QUESTS)
        val document = if (quest.id.isBlank()) collection.document() else collection.document(quest.id)
        document.set(quest.copy(id = document.id).toFirestoreMap()).await()
        return document.id
    }

    suspend fun setQuestStatus(questId: String, status: QuestStatus) {
        db.collection(FirestoreCollections.QUESTS).document(questId)
            .update("status", status.name)
            .await()
    }

    suspend fun acceptQuest(questId: String, userId: String) {
        val userRef = db.collection(FirestoreCollections.USERS).document(userId)
        val questRef = db.collection(FirestoreCollections.QUESTS).document(questId)
        db.runTransaction { tx ->
            val user = tx.get(userRef).toUserProfile()
            val quest = tx.get(questRef).toQuest()
            validateRepositoryQuestAcceptance(quest, user)
            tx.update(userRef, "acceptedQuestIds", FieldValue.arrayUnion(questId))
        }.await()
    }

    suspend fun submitQuest(
        quest: Quest,
        user: UserProfile,
        proofText: String,
        proofImageUrl: String?,
        overachieved: Boolean,
        overachievementText: String
    ) {
        val storedQuest = db.collection(FirestoreCollections.QUESTS).document(quest.id).get().await().toQuest()
        val storedUser = db.collection(FirestoreCollections.USERS).document(user.uid).get().await().toUserProfile()
        val hasPending = db.collection(FirestoreCollections.QUEST_SUBMISSIONS)
            .whereEqualTo("questId", storedQuest.id)
            .whereEqualTo("userId", storedUser.uid)
            .whereEqualTo("status", SubmissionStatus.SUBMITTED.name)
            .get()
            .await()
            .documents
            .isNotEmpty()
        validateRepositoryQuestSubmission(storedQuest, storedUser, proofText, hasPending)
        val submission = QuestSubmission(
            questId = storedQuest.id,
            questTitle = storedQuest.title,
            userId = storedUser.uid,
            userName = storedUser.displayName,
            proofMode = storedQuest.proofMode,
            proofText = proofText,
            proofImageUrl = proofImageUrl,
            overachieved = overachieved,
            overachievementText = overachievementText,
            gpReward = storedQuest.gpReward + storedQuest.bonusGp,
            expReward = storedQuest.expReward + storedQuest.bonusExp
        )
        db.collection(FirestoreCollections.QUEST_SUBMISSIONS).add(submission.toFirestoreMap()).await()
    }

    suspend fun listPendingSubmissions(): List<QuestSubmission> =
        db.collection(FirestoreCollections.QUEST_SUBMISSIONS)
            .whereEqualTo("status", SubmissionStatus.SUBMITTED.name)
            .get()
            .await()
            .documents
            .map { it.toSubmission() }

    suspend fun listUserSubmissions(userId: String): List<QuestSubmission> =
        db.collection(FirestoreCollections.QUEST_SUBMISSIONS)
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .map { it.toSubmission() }

    suspend fun reviewSubmission(
        submission: QuestSubmission,
        adminId: String,
        approved: Boolean,
        note: String?,
        bonusGp: Long,
        bonusExp: Long
    ) {
        val userRef = db.collection(FirestoreCollections.USERS).document(submission.userId)
        val submissionRef = db.collection(FirestoreCollections.QUEST_SUBMISSIONS).document(submission.id)
        val questRef = db.collection(FirestoreCollections.QUESTS).document(submission.questId)

        db.runTransaction { tx ->
            val user = tx.get(userRef).toUserProfile()
            val quest = tx.get(questRef).toQuest()
            val storedSubmission = tx.get(submissionRef).toSubmission()
            require(storedSubmission.status == SubmissionStatus.SUBMITTED) { "此任務回報已結算，不能重複審核。" }
            val finalBonusGp = bonusGp.coerceAtLeast(0)
            val finalBonusExp = bonusExp.coerceAtLeast(0)
            val newExp = if (approved) user.exp + submission.expReward + finalBonusExp else user.exp
            val newGp = if (approved) user.gp + submission.gpReward + finalBonusGp else user.gp
            val newRank = if (approved && quest.type == QuestType.PROMOTION_QUEST && user.canStartPromotionTrial()) {
                user.nextPromotionRank() ?: user.rank
            } else {
                user.rank
            }

            tx.update(
                submissionRef,
                mapOf(
                    "status" to if (approved) SubmissionStatus.APPROVED.name else SubmissionStatus.NEEDS_REVISION.name,
                    "reviewedBy" to adminId,
                    "reviewedAtMillis" to System.currentTimeMillis(),
                    "reviewBonusGp" to if (approved) finalBonusGp else 0,
                    "reviewBonusExp" to if (approved) finalBonusExp else 0,
                    "reviewNote" to note
                )
            )
            if (approved) {
                tx.update(
                    userRef,
                    mapOf(
                        "gp" to newGp,
                        "exp" to newExp,
                        "level" to levelFromExp(newExp),
                        "rank" to newRank.name,
                        "title" to newRank.displayName
                    )
                )
            }
        }.await()
    }

    suspend fun seedDefaultQuests(adminId: String, guildId: String) {
        val existing = listAllQuests(guildId)
        if (existing.isNotEmpty()) return
        val quests = listOf(
            Quest(title = "清理告示板", description = "完成今日一項簡單任務並提交文字回報。", type = QuestType.DAILY_QUEST, status = QuestStatus.PUBLISHED, gpReward = 20, expReward = 30, createdBy = adminId, activeWeekdays = listOf(1, 2, 3, 4, 5, 6, 7)),
            Quest(title = "補給線護送", description = "本週完成三次指定行動後回報。", type = QuestType.WEEKLY_QUEST, status = QuestStatus.PUBLISHED, gpReward = 90, expReward = 120, targetCount = 3, createdBy = adminId),
            Quest(title = "月度征伐：深林異變", description = "完成高難度挑戰，提交截圖或詳細記錄。", type = QuestType.MONTHLY_QUEST, status = QuestStatus.PUBLISHED, gpReward = 300, expReward = 450, createdBy = adminId),
            Quest(title = "晉階試煉：E 級門檻", description = "累積足夠 EXP 後提交晉階申請。", type = QuestType.PROMOTION_QUEST, status = QuestStatus.PUBLISHED, gpReward = 50, expReward = 100, createdBy = adminId)
        )
        quests.forEach { upsertQuest(it.copy(guildId = guildId)) }
    }
}

private fun UserProfile.nextPromotionRank(): AdventurerRank? =
    AdventurerRank.entries.firstOrNull { it.ordinal > rank.ordinal }

private fun UserProfile.canStartPromotionTrial(): Boolean {
    val nextRank = nextPromotionRank() ?: return false
    return exp >= nextRank.minExp
}

private fun validateRepositoryQuestAcceptance(quest: Quest, user: UserProfile) {
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
    if (quest.type == QuestType.PROMOTION_QUEST) {
        require(user.canStartPromotionTrial()) { "尚未達到下一階 EXP 門檻，不能接取晉階試煉。" }
    }
}

private fun validateRepositoryQuestSubmission(
    quest: Quest,
    user: UserProfile,
    proofText: String,
    hasPendingSubmission: Boolean
) {
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
    if (quest.type == QuestType.PROMOTION_QUEST) {
        require(user.canStartPromotionTrial()) { "尚未達到下一階 EXP 門檻，不能提交晉階試煉。" }
    }
    if (quest.proofMode == QuestProofMode.TEXT) {
        require(proofText.isNotBlank()) { "此任務需要填寫文字回報。" }
    }
    require(!hasPendingSubmission) { "此任務已有待審回報，請等管理員審核後再提交。" }
}
