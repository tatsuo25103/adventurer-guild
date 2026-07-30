package com.example.adventurerguild.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuildCounterSecurityPolicyTest {
    private val guild = Guild(id = "guild", ownerUid = "owner")
    private val owner = UserProfile(
        uid = "owner",
        role = UserRole.GUILD_ADMIN,
        managedGuildIds = listOf("guild")
    )
    private val adventurer = UserProfile(
        uid = "adventurer",
        role = UserRole.ADVENTURER,
        joinedGuildIds = listOf("guild")
    )

    @Test
    fun expiredOrCompletedSessionCannotBeConfirmed() {
        val expired = GuildCounterSession(
            guildId = "guild",
            adventurerUid = adventurer.uid,
            expiresAtMillis = 99
        )
        assertFalse(GuildCounterSecurityPolicy.canConfirm(expired, owner, guild, 100))
        assertFalse(
            GuildCounterSecurityPolicy.canConfirm(
                expired.copy(
                    status = GuildCounterSessionStatus.COMPLETED,
                    expiresAtMillis = 200
                ),
                owner,
                guild,
                100
            )
        )
    }

    @Test
    fun onlyCounterpartCanConfirmEachStage() {
        val acceptance = GuildCounterSession(
            guildId = "guild",
            action = GuildCounterAction.ACCEPT_QUEST,
            adventurerUid = adventurer.uid,
            expiresAtMillis = 200
        )
        assertTrue(GuildCounterSecurityPolicy.canConfirm(acceptance, owner, guild, 100))
        assertFalse(GuildCounterSecurityPolicy.canConfirm(acceptance, adventurer, guild, 100))

        val settlement = acceptance.copy(
            action = GuildCounterAction.SETTLE_SUBMISSION,
            status = GuildCounterSessionStatus.AWAITING_FINAL_CONFIRMATION
        )
        assertTrue(GuildCounterSecurityPolicy.canConfirm(settlement, adventurer, guild, 100))
        assertFalse(GuildCounterSecurityPolicy.canConfirm(settlement, owner, guild, 100))
    }

    @Test
    fun cloudCopyNeverContainsLocalMediaData() {
        val session = GuildCounterSession(
            proofMode = QuestProofMode.IN_PERSON,
            localProofImageUri = "content://private/photo",
            proofImageSha256 = "private-fingerprint",
            retainProofCopyApproved = true
        )
        val safe = GuildCounterSecurityPolicy.cloudSafeCopy(session)
        assertEquals(QuestProofMode.IN_PERSON, safe.proofMode)
        assertNull(safe.localProofImageUri)
        assertNull(safe.proofImageSha256)
        assertFalse(safe.retainProofCopyApproved)
    }
}
