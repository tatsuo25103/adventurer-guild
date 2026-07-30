package com.example.adventurerguild.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuildMembershipTest {
    private val managedGuild = Guild(id = "managed", ownerUid = "owner")
    private val joinedGuild = Guild(id = "joined", ownerUid = "another-owner")

    @Test
    fun accountCanHaveDifferentRolesAcrossGuilds() {
        val user = UserProfile(
            uid = "user",
            managedGuildIds = listOf(managedGuild.id),
            joinedGuildIds = listOf(joinedGuild.id)
        )

        assertTrue(user.isGuildManager(managedGuild))
        assertFalse(user.isGuildAdventurer(managedGuild))
        assertTrue(user.isGuildAdventurer(joinedGuild))
        assertFalse(user.isGuildManager(joinedGuild))
    }

    @Test
    fun managerWinsIfLegacyDataContainsBothRolesInSameGuild() {
        val user = UserProfile(
            uid = "user",
            managedGuildIds = listOf(managedGuild.id),
            joinedGuildIds = listOf(managedGuild.id)
        )

        assertTrue(user.isGuildManager(managedGuild))
        assertFalse(user.isGuildAdventurer(managedGuild))
    }

    @Test
    fun managementPermissionOnlyAppliesToManagedGuild() {
        val user = UserProfile(
            uid = "user",
            managedGuildIds = listOf(managedGuild.id),
            joinedGuildIds = listOf(joinedGuild.id),
            guildRoles = mapOf(managedGuild.id to GuildRoleCatalog.defaultRoles.first())
        )

        assertTrue(user.hasGuildPermission(managedGuild, GuildPermission.PUBLISH_QUESTS))
        assertFalse(user.hasGuildPermission(joinedGuild, GuildPermission.PUBLISH_QUESTS))
    }
}
