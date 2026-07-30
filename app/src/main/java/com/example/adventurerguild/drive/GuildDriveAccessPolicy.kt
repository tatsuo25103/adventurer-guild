package com.example.adventurerguild.drive

enum class GuildDriveMemberSide {
    MANAGER,
    ADVENTURER
}

enum class GuildDriveResource {
    INVITATION,
    AUTHORITATIVE_STATE,
    MEMBER_INBOX,
    MEMBER_ATTACHMENTS,
    AUDIT,
    BACKUPS
}

enum class GuildDriveRole {
    NONE,
    READER,
    WRITER,
    OWNER
}

object GuildDriveAccessPolicy {
    fun roleFor(
        resource: GuildDriveResource,
        isGuildOwner: Boolean,
        memberSide: GuildDriveMemberSide?,
        isOwnMemberResource: Boolean = false
    ): GuildDriveRole {
        if (isGuildOwner) return GuildDriveRole.OWNER
        return when (resource) {
            GuildDriveResource.INVITATION -> GuildDriveRole.READER
            GuildDriveResource.AUTHORITATIVE_STATE ->
                if (memberSide != null) GuildDriveRole.READER else GuildDriveRole.NONE
            GuildDriveResource.MEMBER_INBOX,
            GuildDriveResource.MEMBER_ATTACHMENTS ->
                if (memberSide != null && isOwnMemberResource) GuildDriveRole.WRITER else GuildDriveRole.NONE
            GuildDriveResource.AUDIT ->
                if (memberSide == GuildDriveMemberSide.MANAGER) GuildDriveRole.READER else GuildDriveRole.NONE
            GuildDriveResource.BACKUPS -> GuildDriveRole.NONE
        }
    }

    fun canWriteAuthoritativeState(isGuildOwner: Boolean): Boolean = isGuildOwner

    fun publicLinkRole(resource: GuildDriveResource): GuildDriveRole =
        if (resource == GuildDriveResource.INVITATION) GuildDriveRole.READER else GuildDriveRole.NONE
}
