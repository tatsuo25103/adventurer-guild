package com.example.adventurerguild.model

import org.junit.Test

class QuestGovernanceValidationTest {
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
    private val guild = Guild(id = "guild", ownerUid = owner.uid)

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAdventurerFromAnotherGuild() {
        Quest(
            guildId = guild.id,
            title = "Assigned quest",
            assignedAdventurerIds = listOf("outsider")
        ).validateGovernancePolicy(guild, listOf(owner, adventurer), owner)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnnouncementAfterAcceptingOpens() {
        Quest(
            guildId = guild.id,
            title = "Bad timing",
            announcedAtMillis = 2_000,
            acceptStartsAtMillis = 1_000
        ).validateGovernancePolicy(guild, listOf(owner, adventurer), owner)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFormationWithoutSlots() {
        Quest(
            guildId = guild.id,
            title = "Empty formation",
            type = QuestType.FORMATION_QUEST
        ).validateGovernancePolicy(guild, listOf(owner, adventurer), owner)
    }
}
