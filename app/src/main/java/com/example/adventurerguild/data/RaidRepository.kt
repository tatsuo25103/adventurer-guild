package com.example.adventurerguild.data

import com.example.adventurerguild.model.GuildRaid
import com.example.adventurerguild.model.GuildRaidContribution
import com.example.adventurerguild.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class RaidRepository(private val db: FirebaseFirestore) {
    suspend fun listActiveRaids(guildId: String): List<GuildRaid> =
        db.collection(FirestoreCollections.GUILD_RAIDS)
            .whereEqualTo("guildId", guildId)
            .whereEqualTo("active", true)
            .get()
            .await()
            .documents
            .map { it.toGuildRaid() }

    suspend fun contribute(raid: GuildRaid, user: UserProfile, amount: Long) {
        val raidRef = db.collection(FirestoreCollections.GUILD_RAIDS).document(raid.id)
        val contributionRef = db.collection(FirestoreCollections.GUILD_RAID_CONTRIBUTIONS).document()
        val userRef = db.collection(FirestoreCollections.USERS).document(user.uid)
        db.runTransaction { tx ->
            val currentRaid = tx.get(raidRef).toGuildRaid()
            val currentUser = tx.get(userRef).toUserProfile()
            val gpReward = amount * currentRaid.gpRewardPerContribution
            val expReward = amount * currentRaid.expRewardPerContribution
            val newExp = currentUser.exp + expReward

            tx.update(raidRef, "currentProgress", (currentRaid.currentProgress + amount).coerceAtMost(currentRaid.targetProgress))
            tx.update(
                userRef,
                mapOf(
                    "gp" to currentUser.gp + gpReward,
                    "exp" to newExp,
                    "level" to levelFromExp(newExp)
                )
            )
            tx.set(
                contributionRef,
                GuildRaidContribution(
                    id = contributionRef.id,
                    raidId = raid.id,
                    guildId = raid.guildId,
                    userId = user.uid,
                    userName = user.displayName,
                    amount = amount
                ).toFirestoreMap()
            )
        }.await()
    }

    suspend fun upsertRaid(raid: GuildRaid): String {
        val collection = db.collection(FirestoreCollections.GUILD_RAIDS)
        val document = if (raid.id.isBlank()) collection.document() else collection.document(raid.id)
        document.set(raid.copy(id = document.id).toFirestoreMap()).await()
        return document.id
    }

    suspend fun seedDefaultRaid(guildId: String) {
        if (listActiveRaids(guildId).isNotEmpty()) return
        upsertRaid(
            GuildRaid(
                guildId = guildId,
                title = "公會討伐戰：古代魔像",
                description = "全體成員共同累積討伐進度，任何指定行為都可增加貢獻。",
                targetProgress = 100,
                gpRewardPerContribution = 2,
                expRewardPerContribution = 3
            )
        )
    }
}
