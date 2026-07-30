package com.example.adventurerguild.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuildDriveAccessPolicyTest {
    @Test
    fun publicLinksAreNeverWritable() {
        GuildDriveResource.entries.forEach { resource ->
            val role = GuildDriveAccessPolicy.publicLinkRole(resource)
            assertFalse(role == GuildDriveRole.WRITER || role == GuildDriveRole.OWNER)
        }
        assertEquals(
            GuildDriveRole.READER,
            GuildDriveAccessPolicy.publicLinkRole(GuildDriveResource.INVITATION)
        )
    }

    @Test
    fun membersWriteOnlyTheirOwnInboxAndAttachments() {
        GuildDriveMemberSide.entries.forEach { side ->
            assertEquals(
                GuildDriveRole.WRITER,
                GuildDriveAccessPolicy.roleFor(
                    GuildDriveResource.MEMBER_INBOX,
                    isGuildOwner = false,
                    memberSide = side,
                    isOwnMemberResource = true
                )
            )
            assertEquals(
                GuildDriveRole.NONE,
                GuildDriveAccessPolicy.roleFor(
                    GuildDriveResource.MEMBER_INBOX,
                    isGuildOwner = false,
                    memberSide = side,
                    isOwnMemberResource = false
                )
            )
        }
    }

    @Test
    fun onlyOwnerWritesAuthoritativeState() {
        assertTrue(GuildDriveAccessPolicy.canWriteAuthoritativeState(isGuildOwner = true))
        assertFalse(GuildDriveAccessPolicy.canWriteAuthoritativeState(isGuildOwner = false))
        assertEquals(
            GuildDriveRole.READER,
            GuildDriveAccessPolicy.roleFor(
                GuildDriveResource.AUTHORITATIVE_STATE,
                isGuildOwner = false,
                memberSide = GuildDriveMemberSide.MANAGER
            )
        )
    }
}
