package com.example.adventurerguild.model

object GuildCounterSecurityPolicy {
    fun isActive(session: GuildCounterSession, nowMillis: Long): Boolean =
        (
            session.status == GuildCounterSessionStatus.WAITING_FOR_COUNTERPART ||
                session.status == GuildCounterSessionStatus.AWAITING_FINAL_CONFIRMATION
            ) && nowMillis <= session.expiresAtMillis

    fun canConfirm(
        session: GuildCounterSession,
        user: UserProfile,
        guild: Guild,
        nowMillis: Long
    ): Boolean {
        if (!isActive(session, nowMillis) || session.guildId != guild.id) return false
        return when (session.action) {
            GuildCounterAction.ACCEPT_QUEST ->
                user.hasGuildPermission(guild, GuildPermission.PUBLISH_QUESTS) ||
                    user.hasGuildPermission(guild, GuildPermission.REVIEW_QUESTS)
            GuildCounterAction.SUBMIT_QUEST ->
                user.hasGuildPermission(guild, GuildPermission.REVIEW_QUESTS) ||
                    user.hasGuildPermission(guild, GuildPermission.REVIEW_NEARBY_SUBMISSIONS)
            GuildCounterAction.SETTLE_SUBMISSION ->
                user.uid == session.adventurerUid && user.isGuildAdventurer(guild)
        }
    }

    fun cloudSafeCopy(session: GuildCounterSession): GuildCounterSession =
        session.copy(
            localProofImageUri = null,
            proofImageSha256 = null,
            retainProofCopyApproved = false
        )
}
