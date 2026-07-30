package com.example.adventurerguild.drive

/**
 * Event-driven sync hooks for the offline-first app.
 *
 * The MVP keeps this provider as a no-op so the app remains fully local. A future
 * Google Drive implementation can plug in here without adding periodic polling:
 * pull on login/enter-guild/manual refresh, push only after local data changes.
 */
enum class GuildSyncTrigger {
    LOGIN,
    ENTER_GUILD,
    LOCAL_CHANGE,
    MANUAL_REFRESH,
    APP_FOREGROUND
}

enum class GuildSyncEventType {
    LOCAL_STATE_CHANGED,
    QUEST_CHANGED,
    QUEST_SUBMITTED,
    QUEST_REVIEWED,
    REWARD_CHANGED,
    REDEMPTION_CHANGED,
    RAID_CHANGED,
    GUILD_SETTINGS_CHANGED,
    MEMBER_CHANGED
}

data class GuildSyncMetadata(
    val guildId: String = "",
    val localVersion: Long = 0,
    val remoteVersion: Long = 0,
    val lastSyncAtMillis: Long = 0,
    val pendingEventCount: Int = 0,
    val lastError: String? = null
)

data class GuildSyncEvent(
    val id: String = "",
    val guildId: String = "",
    val actorUid: String = "",
    val type: GuildSyncEventType = GuildSyncEventType.LOCAL_STATE_CHANGED,
    val targetId: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val payloadSummary: String = ""
)

data class GuildSyncResult(
    val metadata: GuildSyncMetadata,
    val remoteChanged: Boolean = false,
    val pushedEventCount: Int = 0,
    val message: String = ""
)

interface GuildSyncProvider {
    suspend fun pullIfChanged(
        guildId: String,
        trigger: GuildSyncTrigger,
        metadata: GuildSyncMetadata
    ): GuildSyncResult

    suspend fun pushPendingEvents(
        guildId: String,
        events: List<GuildSyncEvent>,
        trigger: GuildSyncTrigger,
        metadata: GuildSyncMetadata
    ): GuildSyncResult
}

object NoOpGuildSyncProvider : GuildSyncProvider {
    override suspend fun pullIfChanged(
        guildId: String,
        trigger: GuildSyncTrigger,
        metadata: GuildSyncMetadata
    ): GuildSyncResult {
        return GuildSyncResult(
            metadata = metadata.copy(
                guildId = guildId,
                lastSyncAtMillis = System.currentTimeMillis(),
                lastError = null
            ),
            message = "Offline sync provider: pull skipped for $trigger."
        )
    }

    override suspend fun pushPendingEvents(
        guildId: String,
        events: List<GuildSyncEvent>,
        trigger: GuildSyncTrigger,
        metadata: GuildSyncMetadata
    ): GuildSyncResult {
        return GuildSyncResult(
            metadata = metadata.copy(
                guildId = guildId,
                localVersion = metadata.localVersion + events.size,
                pendingEventCount = 0,
                lastSyncAtMillis = System.currentTimeMillis(),
                lastError = null
            ),
            pushedEventCount = events.size,
            message = "Offline sync provider: push skipped for $trigger."
        )
    }
}
