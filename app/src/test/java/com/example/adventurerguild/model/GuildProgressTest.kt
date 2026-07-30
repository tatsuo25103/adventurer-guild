package com.example.adventurerguild.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GuildProgressTest {
    @Test
    fun legacyProgressMigratesToFirstGuildAndRemainsIsolated() {
        val legacy = UserProfile(
            uid = "user-1",
            guildId = "default-guild",
            gp = 2_000,
            exp = 450,
            level = 3,
            rank = AdventurerRank.E
        )

        val guildA = legacy.activateGuildProgress("guild-a")
        assertEquals(2_000, guildA.gp)
        assertEquals(450, guildA.exp)

        val changedA = guildA.withGuildProgress(
            "guild-a",
            guildA.progressForGuild("guild-a").copy(gp = 1_850)
        )
        val guildB = changedA.activateGuildProgress("guild-b")

        assertEquals(0, guildB.gp)
        assertEquals(0, guildB.exp)
        assertEquals(1_850, guildB.activateGuildProgress("guild-a").gp)
    }
}
