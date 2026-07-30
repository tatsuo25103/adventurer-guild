package com.example.adventurerguild.data

import com.example.adventurerguild.model.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class RewardRepository(private val db: FirebaseFirestore) {
    suspend fun listRewards(guildId: String): List<Reward> =
        db.collection(FirestoreCollections.REWARDS)
            .whereEqualTo("guildId", guildId)
            .whereEqualTo("active", true)
            .get()
            .await()
            .documents
            .map { it.toReward() }

    suspend fun upsertReward(reward: Reward): String {
        val collection = db.collection(FirestoreCollections.REWARDS)
        val document = if (reward.id.isBlank()) collection.document() else collection.document(reward.id)
        document.set(reward.copy(id = document.id).toFirestoreMap()).await()
        return document.id
    }

    suspend fun redeem(reward: Reward, user: UserProfile) {
        val userRef = db.collection(FirestoreCollections.USERS).document(user.uid)
        val redemptionRef = db.collection(FirestoreCollections.REDEMPTIONS).document()
        db.runTransaction { tx ->
            val currentUser = tx.get(userRef).toUserProfile()
            require(currentUser.gp >= reward.gpCost) { "GP 不足，無法兌換。" }
            tx.update(userRef, "gp", currentUser.gp - reward.gpCost)
            tx.set(
                redemptionRef,
                Redemption(
                    id = redemptionRef.id,
                    rewardId = reward.id,
                    rewardName = reward.name,
                    userId = user.uid,
                    userName = user.displayName,
                    gpCost = reward.gpCost
                ).toFirestoreMap()
            )
        }.await()
    }

    suspend fun listPendingRedemptions(): List<Redemption> =
        db.collection(FirestoreCollections.REDEMPTIONS)
            .whereEqualTo("status", RedemptionStatus.PENDING.name)
            .get()
            .await()
            .documents
            .map { it.toRedemption() }

    suspend fun reviewRedemption(redemption: Redemption, adminId: String, approved: Boolean) {
        db.collection(FirestoreCollections.REDEMPTIONS).document(redemption.id)
            .update(
                mapOf(
                    "status" to if (approved) RedemptionStatus.APPROVED.name else RedemptionStatus.REJECTED.name,
                    "reviewedBy" to adminId,
                    "reviewedAtMillis" to System.currentTimeMillis()
                )
            )
            .await()
    }

    suspend fun seedDefaultRewards(guildId: String) {
        if (listRewards(guildId).isNotEmpty()) return
        listOf(
            Reward(name = "公會徽章", description = "個人頁展示用稱號徽章。", gpCost = 80),
            Reward(name = "補給券", description = "可向公會兌換一次補給。", gpCost = 150),
            Reward(name = "傳說委託入場券", description = "解鎖一次高階挑戰資格。", gpCost = 500)
        ).forEach { upsertReward(it.copy(guildId = guildId)) }
    }
}
