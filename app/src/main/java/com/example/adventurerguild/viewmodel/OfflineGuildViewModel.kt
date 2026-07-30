package com.example.adventurerguild.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adventurerguild.BuildConfig
import com.example.adventurerguild.cloud.CloudflareApiClient
import com.example.adventurerguild.cloud.DeviceIdentityStore
import com.example.adventurerguild.data.enumValueOrDefault
import com.example.adventurerguild.data.levelFromExp
import com.example.adventurerguild.drive.GoogleDriveGuildSyncClient
import com.example.adventurerguild.drive.DriveGuildJoinRequest
import com.example.adventurerguild.drive.GuildDriveMemberSide
import com.example.adventurerguild.drive.GuildDriveWorkspace
import com.example.adventurerguild.drive.GuildSyncEvent
import com.example.adventurerguild.drive.GuildSyncEventType
import com.example.adventurerguild.drive.GuildSyncMetadata
import com.example.adventurerguild.drive.GuildSyncProvider
import com.example.adventurerguild.drive.GuildSyncTrigger
import com.example.adventurerguild.drive.NoOpGuildSyncProvider
import com.example.adventurerguild.model.*
import com.example.adventurerguild.nearby.NearbyCounterCoordinator
import com.example.adventurerguild.nearby.NearbyCounterPhase
import com.example.adventurerguild.widget.QuestWidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class OfflineGuildViewModel(context: Context) : ViewModel(), GuildController {
    private val appContext = context.applicationContext
    private val store = OfflineGuildStore(appContext)
    private val syncProvider: GuildSyncProvider = NoOpGuildSyncProvider
    private val driveClient = GoogleDriveGuildSyncClient()
    private val cloudIdentity = DeviceIdentityStore(appContext)
    private val cloudClient = CloudflareApiClient(
        BuildConfig.CLOUDFLARE_API_BASE_URL,
        cloudIdentity
    )
    private val nearbyCounter = NearbyCounterCoordinator(appContext)
    private val _state = MutableStateFlow(GuildUiState(loading = true))
    override val state: StateFlow<GuildUiState> = _state.asStateFlow()

    private var data = store.load().normalizedAccess()
    private var syncMetadataByGuild: Map<String, GuildSyncMetadata> = emptyMap()

    init {
        data = data.normalizedAccess().ensureSeeded()
        store.save(data)
        val currentUser = data.currentUid?.let { data.users[it] }
        _state.value = GuildUiState(user = currentUser, loading = false)
        currentUser?.let {
            refreshAll(it)
            syncOn(GuildSyncTrigger.APP_FOREGROUND, data.currentGuildId ?: it.guildId)
        }
        viewModelScope.launch {
            nearbyCounter.state.collect { nearbyState ->
                _state.value = _state.value.copy(nearbyCounter = nearbyState)
            }
        }
    }

    override fun register(email: String, password: String, displayName: String, asAdmin: Boolean) = launchBusy {
        val normalizedEmail = email.trim().lowercase()
        val existing = data.users.values.firstOrNull { it.email == normalizedEmail }
        val profile = existing ?: UserProfile(
            uid = "offline-${UUID.randomUUID()}",
            email = normalizedEmail,
            displayName = displayName.ifBlank { normalizedEmail.substringBefore("@") },
            role = if (asAdmin) UserRole.GUILD_ADMIN else UserRole.ADVENTURER
        )
        data = data.copy(
            users = data.users + (profile.uid to profile),
            currentUid = profile.uid
        ).ensureSeeded()
        store.save(data)
        refreshAll(profile)
        syncOn(GuildSyncTrigger.LOGIN, profile.guildId)
    }

    override fun login(email: String, password: String) = launchBusy {
        val normalizedEmail = email.trim().lowercase()
        val profile = data.users.values.firstOrNull { it.email == normalizedEmail }
            ?: UserProfile(
                uid = "offline-${UUID.randomUUID()}",
                email = normalizedEmail,
                displayName = normalizedEmail.substringBefore("@"),
                role = UserRole.ADVENTURER
            )
        data = data.copy(
            users = data.users + (profile.uid to profile),
            currentUid = profile.uid
        ).ensureSeeded()
        store.save(data)
        refreshAll(profile)
        syncOn(GuildSyncTrigger.LOGIN, profile.guildId)
    }

    override fun loginWithDevice(asAdmin: Boolean) = launchBusy {
        val cloudUserId = cloudIdentity.userId
        withContext(Dispatchers.IO) {
            cloudClient.registerDevice(android.os.Build.MODEL.ifBlank { "Android device" })
        }
        val existing = data.users.values.firstOrNull { it.cloudUserId == cloudUserId }
        val profile = existing?.copy(
            role = if (asAdmin) UserRole.GUILD_ADMIN else UserRole.ADVENTURER
        ) ?: UserProfile(
            uid = "device-$cloudUserId",
            cloudUserId = cloudUserId,
            displayName = android.os.Build.MODEL.ifBlank { "冒險者" },
            role = if (asAdmin) UserRole.GUILD_ADMIN else UserRole.ADVENTURER
        )
        data = data.copy(
            users = data.users + (profile.uid to profile),
            currentUid = profile.uid
        ).ensureSeeded()
        store.save(data)
        runCatching { syncCloudMemberships(profile) }
        refreshAll(data.users[profile.uid] ?: profile)
        syncOn(GuildSyncTrigger.LOGIN, profile.guildId)
    }

    override fun loginOfflineTest(asAdmin: Boolean) = launchBusy {
        require(BuildConfig.ENABLE_TEST_ACCOUNTS) { "正式版本不提供測試帳號。" }
        withContext(Dispatchers.IO) {
            cloudClient.registerDevice(android.os.Build.MODEL.ifBlank { "Android test device" })
        }
        val profile = if (asAdmin) {
            UserProfile(
                uid = "offline-test-admin",
                email = "offline.admin@test.local",
                displayName = "測試管理員",
                role = UserRole.GUILD_ADMIN,
                joinedGuildIds = emptyList(),
                managedGuildIds = emptyList(),
                guildRoles = emptyMap()
            )
        } else {
            UserProfile(
                uid = "offline-test-adventurer",
                email = "offline.adventurer@test.local",
                displayName = "測試冒險者",
                role = UserRole.ADVENTURER,
                gp = 2000,
                joinedGuildIds = emptyList(),
                managedGuildIds = emptyList(),
                guildRoles = emptyMap()
            )
        }
        data = data.copy(
            users = data.users + (profile.uid to profile),
            currentUid = profile.uid,
            currentGuildId = null
        ).normalizedAccess().ensureSeeded()
        store.save(data)
        _state.value = portalState(profile)
    }

    override fun loginWithGoogle(idToken: String, asAdmin: Boolean) {
        reportError("Google 帳號登入已停用。請使用裝置 UUID 帳號。")
    }

    override fun setDriveAccessToken(accessToken: String?) {
        driveClient.setAccessToken(accessToken)
        if (!accessToken.isNullOrBlank()) {
            data.currentUid?.let { uid ->
                data.users[uid]?.let { user ->
                    syncOn(GuildSyncTrigger.MANUAL_REFRESH, data.currentGuildId ?: user.guildId)
                }
            }
        }
    }

    override fun createAccountTransferCode() = launchBusy {
        requireUser()
        val response = withContext(Dispatchers.IO) { cloudClient.createTransferCode() }
        _state.value = _state.value.copy(
            loading = false,
            error = null,
            accountTransferUserId = response.getString("userId"),
            accountTransferCode = response.getString("transferCode"),
            accountTransferExpiresAt = response.getLong("expiresAt")
        )
    }

    override fun inheritCloudAccount(userId: String, transferCode: String) = launchBusy {
        require(data.currentUid == null) { "請先登出目前帳號再進行移機。" }
        require(userId.isNotBlank() && transferCode.isNotBlank()) { "帳號 UUID 與移機碼不可空白。" }
        withContext(Dispatchers.IO) {
            cloudClient.inheritAccount(
                accountUserId = userId.trim(),
                transferCode = transferCode.trim(),
                displayName = android.os.Build.MODEL.ifBlank { "Android device" }
            )
        }
        _state.value = GuildUiState(
            error = "移機成功。請使用裝置 UUID 登入以載入原本的公會身分。"
        )
    }

    override fun logout() {
        data = data.copy(currentUid = null)
        store.save(data)
        _state.value = GuildUiState()
    }

    override fun returnToAccountEntry() {
        data = data.copy(currentUid = null, currentGuildId = null)
        store.save(data)
        _state.value = GuildUiState(authDestination = AuthDestination.ACCOUNT_ENTRY)
    }

    override fun returnToRoleSelection() {
        data = data.copy(currentUid = null, currentGuildId = null)
        store.save(data)
        _state.value = GuildUiState(authDestination = AuthDestination.ROLE_SELECTION)
    }

    override fun reportError(message: String) {
        _state.value = _state.value.copy(loading = false, error = message)
    }

    override fun updateDisplayName(displayName: String) = launchBusy {
        val user = requireUser()
        val normalized = displayName.trim()
        require(normalized.length in 2..40) { "名稱需為 2 至 40 個字元。" }
        withContext(Dispatchers.IO) { cloudClient.updateProfile(normalized) }
        val updated = user.copy(displayName = normalized)
        data = data.copy(users = data.users + (updated.uid to updated))
        store.save(data)
        refreshAll(updated)
    }

    override fun updateCustomTitle(title: String) = launchBusy {
        val user = requireUser()
        val updated = user.copy(customTitle = title.trim())
        data = data.copy(users = data.users + (updated.uid to updated))
        store.save(data)
        refreshAll(updated)
    }

    override fun rotateGuildInvite(guildId: String) = launchBusy {
        val user = requireUser()
        val guild = data.guilds[guildId] ?: error("找不到公會。")
        require(user.hasGuildPermission(guild, GuildPermission.MANAGE_GUILD_SETTINGS)) {
            "沒有管理公會邀請的權限。"
        }
        val code = randomInviteCode()
        val expiresAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(90)
        withContext(Dispatchers.IO) {
            cloudClient.createGuildInvite(
                guildId = guild.id,
                inviteCode = code,
                oneTime = false,
                expiresAt = expiresAt,
                replaceReusable = true
            )
        }
        val updatedGuild = guild.copy(inviteCode = code)
        data = data.copy(guilds = data.guilds + (guild.id to updatedGuild))
        store.save(data)
        _state.value = _state.value.copy(
            loading = false,
            error = "舊的常用邀請碼已失效。",
            managedGuilds = _state.value.managedGuilds.map { if (it.id == guild.id) updatedGuild else it },
            activeGuild = _state.value.activeGuild?.let { if (it.id == guild.id) updatedGuild else it },
            generatedInviteGuildId = guild.id,
            generatedInviteCode = code,
            generatedInviteOneTime = false,
            generatedInviteExpiresAt = expiresAt
        )
    }

    override fun createOneTimeGuildInvite(guildId: String) = launchBusy {
        val user = requireUser()
        val guild = data.guilds[guildId] ?: error("找不到公會。")
        require(user.hasGuildPermission(guild, GuildPermission.MANAGE_GUILD_SETTINGS)) {
            "沒有管理公會邀請的權限。"
        }
        val code = randomInviteCode()
        val expiresAt = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24)
        withContext(Dispatchers.IO) {
            cloudClient.createGuildInvite(
                guildId = guild.id,
                inviteCode = code,
                oneTime = true,
                expiresAt = expiresAt,
                replaceReusable = false
            )
        }
        _state.value = _state.value.copy(
            loading = false,
            error = "一次性邀請已建立；送出一次加入申請後即失效。",
            generatedInviteGuildId = guild.id,
            generatedInviteCode = code,
            generatedInviteOneTime = true,
            generatedInviteExpiresAt = expiresAt
        )
    }

    override fun assignGuildRole(member: UserProfile, guildId: String, roleTitle: String) = launchBusy {
        val user = requireUser()
        val guild = data.guilds[guildId] ?: error("找不到公會。")
        require(user.hasGuildPermission(guild, GuildPermission.ASSIGN_ROLES)) { "沒有指派職務權限。" }
        val existing = data.users[member.uid] ?: error("找不到成員。")
        val roleHasManagementPermissions = guild.rolePermissions[roleTitle].orEmpty().isNotEmpty()
        require(!roleHasManagementPermissions || guildId !in existing.joinedGuildIds) {
            "冒險者不能同時擔任管理職。請用公會管理方身分進入員工通道。"
        }
        val updated = existing.copy(guildRoles = existing.guildRoles + (guildId to roleTitle.trim()))
        data = data.copy(users = data.users + (updated.uid to updated))
        store.save(data)
        refreshAll(data.users[user.uid] ?: user)
    }

    override fun updateGuildRankTitle(rank: AdventurerRank, title: String) = launchBusy {
        val user = requireUser()
        val guild = data.currentGuildId?.let { data.guilds[it] } ?: error("尚未選擇公會。")
        require(user.hasGuildPermission(guild, GuildPermission.MANAGE_GUILD_SETTINGS)) { "沒有管理公會基本設定權限。" }
        val updated = guild.copy(rankTitles = guild.rankTitles + (rank.name to title.trim().ifBlank { rank.displayName }))
        data = data.copy(guilds = data.guilds + (updated.id to updated))
        store.save(data)
        refreshAll(user)
    }

    override fun updateGuildRolePermission(roleTitle: String, permission: GuildPermission, enabled: Boolean) = launchBusy {
        val user = requireUser()
        val guild = data.currentGuildId?.let { data.guilds[it] } ?: error("尚未選擇公會。")
        require(user.hasGuildPermission(guild, GuildPermission.MANAGE_ROLE_PERMISSIONS)) { "沒有修改職務權限的權限。" }
        val current = guild.rolePermissions[roleTitle].orEmpty()
        val next = if (enabled) (current + permission.name).distinct() else current - permission.name
        val updated = guild.copy(rolePermissions = guild.rolePermissions + (roleTitle to next))
        data = data.copy(guilds = data.guilds + (updated.id to updated))
        store.save(data)
        refreshAll(user)
    }

    override fun approveJoinRequest(member: UserProfile, approved: Boolean) = launchBusy {
        val user = requireUser()
        val guild = data.currentGuildId?.let { data.guilds[it] } ?: error("尚未選擇公會。")
        require(user.hasGuildPermission(guild, GuildPermission.REVIEW_JOIN_REQUESTS)) { "沒有審核加入人員權限。" }
        val cloudRequestId = guild.cloudJoinRequestIds[member.uid]
        if (cloudRequestId != null) {
            withContext(Dispatchers.IO) { cloudClient.decideJoinRequest(cloudRequestId, approved) }
        }
        val requestedSide = guild.cloudJoinRequestedSides[member.uid] ?: "ADVENTURER"
        val updatedGuild = guild.copy(
            joinRequestUserIds = guild.joinRequestUserIds - member.uid,
            cloudJoinRequestIds = guild.cloudJoinRequestIds - member.uid,
            cloudJoinRequestedSides = guild.cloudJoinRequestedSides - member.uid
        )
        val users = if (approved) {
            require(
                if (requestedSide == "MANAGER") guild.id !in member.joinedGuildIds
                else guild.id !in member.managedGuildIds && member.uid != guild.ownerUid
            ) {
                "此帳號已在本公會另一方名單中，不能同時擔任管理方與冒險者。"
            }
            val updatedMember = if (requestedSide == "MANAGER") {
                member.copy(
                    guildId = guild.id,
                    role = UserRole.GUILD_ADMIN,
                    managedGuildIds = (member.managedGuildIds + guild.id).distinct(),
                    guildRoles = member.guildRoles + (guild.id to GuildRoleCatalog.defaultRoles[6])
                )
            } else {
                member.copy(
                    guildId = guild.id,
                    role = UserRole.ADVENTURER,
                    joinedGuildIds = (member.joinedGuildIds + guild.id).distinct(),
                    guildRoles = member.guildRoles + (guild.id to GuildRoleCatalog.defaultRoles.last())
                )
            }
            data.users + (updatedMember.uid to updatedMember)
        } else {
            data.users
        }
        data = data.copy(users = users, guilds = data.guilds + (updatedGuild.id to updatedGuild))
        store.save(data)
        if (approved && guild.ownerUid == user.uid && driveClient.isAuthorized()) {
            guild.driveWorkspaceOrNull()?.let { workspace ->
                val (inboxId, attachmentId) = driveClient.provisionMemberWorkspace(
                    workspace = workspace,
                    memberUid = member.uid,
                    memberEmail = member.email
                )
                val latestGuild = data.guilds[guild.id] ?: guild
                val provisionedGuild = latestGuild.copy(
                    driveMemberInboxIds = latestGuild.driveMemberInboxIds + (member.uid to inboxId),
                    driveMemberAttachmentFolderIds =
                        latestGuild.driveMemberAttachmentFolderIds + (member.uid to attachmentId)
                )
                data = data.copy(guilds = data.guilds + (guild.id to provisionedGuild))
                store.save(data)
                ensureGuildDriveSnapshot(guild.id)
            }
        }
        refreshAll(user)
    }

    override fun removeGuildMember(member: UserProfile) = launchBusy {
        val user = requireUser()
        val guild = data.currentGuildId?.let { data.guilds[it] } ?: error("尚未選擇公會。")
        require(user.hasGuildPermission(guild, GuildPermission.REMOVE_MEMBERS)) { "沒有移除會員權限。" }
        require(member.uid != guild.ownerUid) { "不能移除公會會長。" }
        require(member.cloudUserId.isNotBlank()) { "此會員缺少雲端帳號識別，請先同步會員資料。" }
        withContext(Dispatchers.IO) {
            cloudClient.revokeGuildMember(guild.id, member.cloudUserId)
        }
        if (guild.ownerUid == user.uid && driveClient.isAuthorized()) {
            guild.driveWorkspaceOrNull()?.let { workspace ->
                driveClient.revokeMemberWorkspace(
                    workspace = workspace,
                    memberEmail = member.email,
                    inboxId = guild.driveMemberInboxIds[member.uid],
                    attachmentId = guild.driveMemberAttachmentFolderIds[member.uid]
                )
            }
        }
        val updatedMember = member.copy(
            joinedGuildIds = member.joinedGuildIds - guild.id,
            managedGuildIds = member.managedGuildIds - guild.id,
            guildRoles = member.guildRoles - guild.id,
            guildId = if (member.guildId == guild.id) "default-guild" else member.guildId
        )
        val updatedGuild = guild.copy(joinRequestUserIds = guild.joinRequestUserIds - member.uid)
        data = data.copy(users = data.users + (updatedMember.uid to updatedMember), guilds = data.guilds + (updatedGuild.id to updatedGuild))
        store.save(data)
        refreshAll(user)
    }

    override fun updateGuildAnnouncement(message: String) = launchBusy {
        val user = requireUser()
        val guild = data.currentGuildId?.let { data.guilds[it] } ?: error("尚未選擇公會。")
        require(user.hasGuildPermission(guild, GuildPermission.POST_ANNOUNCEMENTS)) { "沒有發布公會公告權限。" }
        val updated = guild.copy(announcement = message.trim())
        data = data.copy(guilds = data.guilds + (updated.id to updated))
        store.save(data)
        refreshAll(user)
    }

    override fun updateGuildVacation(enabled: Boolean, note: String) = launchBusy {
        val user = requireUser()
        val guild = data.currentGuildId?.let { data.guilds[it] } ?: error("尚未選擇公會。")
        require(user.hasGuildPermission(guild, GuildPermission.SET_VACATION)) { "沒有設定公會休假權限。" }
        val updated = guild.copy(vacationEnabled = enabled, vacationNote = note.trim())
        data = data.copy(guilds = data.guilds + (updated.id to updated))
        store.save(data)
        refreshAll(user)
    }

    override fun refresh() {
        launchBusy {
            data.currentUid?.let { uid ->
                data.users[uid]?.let { user ->
                    syncCloudMemberships(user)
                    data.currentGuildId?.let { syncCloudJoinRequests(it) }
                    data.currentGuildId?.let { syncCloudCounterSessions(it) }
                    val refreshed = data.users[uid] ?: user
                syncOn(GuildSyncTrigger.MANUAL_REFRESH, data.currentGuildId ?: user.guildId)
                    refreshAll(refreshed)
                }
            }
        }
    }

    override fun refreshCounterSessions() {
        val guildId = data.currentGuildId ?: return
        viewModelScope.launch {
            runCatching {
                syncCloudCounterSessions(guildId)
                data.currentUid
                    ?.let { data.users[it] }
                    ?.let(::refreshAll)
            }
        }
    }

    override fun createGuild(name: String) = launchBusy {
        val user = requireUser()
        require(user.role == UserRole.GUILD_ADMIN) { "只有管理方帳號可以創建公會。冒險者請從公會酒吧加入公會。" }
        val guild = Guild(
            id = "guild-${UUID.randomUUID()}",
            name = name.ifBlank { "${user.displayName} 的公會" },
            ownerUid = user.uid,
            ownerEmail = user.email,
            inviteCode = randomInviteCode()
        )
        withContext(Dispatchers.IO) {
            cloudClient.createGuild(
                guild.id,
                guild.name,
                guild.inviteCode,
                System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30),
                user.uid,
                user.displayName
            )
        }
        val updatedUser = user.activateGuildProgress(guild.id).copy(
            managedGuildIds = (user.managedGuildIds + guild.id).distinct()
        )
        data = data.copy(
            users = data.users + (updatedUser.uid to updatedUser),
            guilds = data.guilds + (guild.id to guild),
            currentGuildId = guild.id
        ).seedGuildContent(guild.id)
        store.save(data)
        refreshAll(updatedUser)
        runCatching { ensureGuildDriveSnapshot(guild.id) }
            .onSuccess { syncOn(GuildSyncTrigger.ENTER_GUILD, guild.id) }
            .onFailure { reportError(it.driveSyncErrorMessage("公會已建立在本機")) }
    }

    override fun joinGuild(inviteCode: String) = launchBusy {
        val user = requireUser()
        val requestedInviteCode = inviteCode.extractGuildInviteCode()
        val resolved = withContext(Dispatchers.IO) { cloudClient.resolveInvite(requestedInviteCode) }
            .getJSONObject("guild")
        val guildId = resolved.getString("guildId")
        val guild = data.guilds[guildId] ?: Guild(
            id = guildId,
            name = resolved.getString("name"),
            ownerUid = "cloud-owner-$guildId",
            inviteCode = requestedInviteCode
        )
        require(guild.id !in user.joinedGuildIds && guild.id !in user.managedGuildIds) { "你已經是此公會成員。" }
        require(user.role == UserRole.ADVENTURER) { "管理方身分請從公會管理通道進入，不可申請成為冒險者。" }
        withContext(Dispatchers.IO) {
            cloudClient.requestGuildJoin(
                UUID.randomUUID().toString(),
                requestedInviteCode,
                user.uid,
                user.displayName,
                "ADVENTURER"
            )
        }
        data = data.copy(guilds = data.guilds + (guild.id to guild))
        store.save(data)
        reportError("已送出加入申請，等待公會審核。")
    }

    override fun joinGuildAsManager(inviteCode: String) = launchBusy {
        val user = requireUser()
        val requestedInviteCode = inviteCode.extractGuildInviteCode()
        val resolved = withContext(Dispatchers.IO) { cloudClient.resolveInvite(requestedInviteCode) }
            .getJSONObject("guild")
        val guildId = resolved.getString("guildId")
        val guild = data.guilds[guildId] ?: Guild(
            id = guildId,
            name = resolved.getString("name"),
            ownerUid = "cloud-owner-$guildId",
            inviteCode = requestedInviteCode
        )
        require(user.role == UserRole.GUILD_ADMIN) { "只有管理方帳號可以走員工通道。" }
        require(guild.id !in user.joinedGuildIds) { "冒險者成員不能同時加入管理方。" }
        require(guild.id !in user.managedGuildIds) { "你已經在此公會的管理方名單中。" }
        withContext(Dispatchers.IO) {
            cloudClient.requestGuildJoin(
                UUID.randomUUID().toString(),
                requestedInviteCode,
                user.uid,
                user.displayName,
                "MANAGER"
            )
        }
        data = data.copy(guilds = data.guilds + (guild.id to guild))
        store.save(data)
        reportError("已送出管理方加入申請，需由公會現有管理方核准。")
    }

    override fun selectGuild(guildId: String) = launchBusy {
        val user = requireUser()
        if (guildId in user.managedGuildIds) syncCloudJoinRequests(guildId)
        syncCloudCounterSessions(guildId)
        val guild = data.guilds[guildId] ?: error("找不到公會。")
        require(user.canEnterGuild(guild)) { "你尚未以正確身分加入此公會。" }
        val updatedUser = user.activateGuildProgress(guild.id)
        data = data.copy(users = data.users + (updatedUser.uid to updatedUser), currentGuildId = guild.id)
        store.save(data)
        refreshAll(updatedUser)
        syncOn(GuildSyncTrigger.ENTER_GUILD, guild.id)
    }

    override fun backToGuildPortal() {
        data = data.copy(currentGuildId = null)
        store.save(data)
        val user = data.currentUid?.let { data.users[it] }
        _state.value = portalState(user, GuildPortalDestination.APP_HOME)
    }

    override fun backToGuildSelection() {
        data = data.copy(currentGuildId = null)
        store.save(data)
        val user = data.currentUid?.let { data.users[it] }
        _state.value = portalState(user, GuildPortalDestination.GUILD_SELECTION)
    }

    override fun createQuest(
        title: String,
        description: String,
        type: QuestType,
        gp: Long,
        exp: Long,
        announcedAtMillis: Long?,
        acceptStartsAtMillis: Long?,
        hasTimeLimit: Boolean,
        startsAtMillis: Long?,
        endsAtMillis: Long?,
        penaltyGp: Long,
        penaltyExp: Long,
        activeWeekdays: List<Int>,
        difficulty: QuestDifficulty,
        tags: List<String>,
        minRank: AdventurerRank,
        assignedAdventurerIds: List<String>,
        assignedReviewerIds: List<String>,
        prerequisiteQuestIds: List<String>,
        bonusGp: Long,
        bonusExp: Long,
        gracePeriodDays: Int,
        submissionDeadlineDays: Int,
        weeklyRefreshWeekday: Int?,
        monthlyRefreshDay: Int?,
        repeatLimitType: RepeatLimitType,
        repeatLimitCount: Int,
        formationSlots: List<QuestSlot>,
        formationRequired: Boolean,
        formationMinSlotsPerUser: Int,
        formationMaxSlotsPerUser: Int,
        formationRollMode: FormationRollMode,
        formationAutoRollAtMillis: Long?,
        proofMode: QuestProofMode,
        autoReviewEnabled: Boolean,
        pinned: Boolean,
        createAsDraft: Boolean
    ) = launchBusy {
        val user = requireUser()
        val guild = requireActiveGuild()
        require(title.isNotBlank()) { "任務標題不可空白。" }
        require(!hasTimeLimit || startsAtMillis == null || endsAtMillis == null || startsAtMillis <= endsAtMillis) { "任務開始日期不可晚於結束日期。" }
        val requiredPermission = if (createAsDraft) GuildPermission.EDIT_QUESTS else GuildPermission.PUBLISH_QUESTS
        require(user.hasGuildPermission(guild, requiredPermission)) { "沒有${requiredPermission.displayName}權限。" }
        if (penaltyGp > 0 || penaltyExp > 0) {
            require(user.hasGuildPermission(guild, GuildPermission.MANAGE_QUEST_PENALTIES)) { "沒有設定任務處罰權限。" }
        }
        require(type != QuestType.GUILD_RAID) { "公會討伐戰請在討伐頁建立，不走一般任務流程。" }
        if (type == QuestType.LIMITED_EVENT_QUEST) {
            require(endsAtMillis != null) { "限時討伐令必須設定活動結束日期。" }
        }
        val quest = Quest(
            id = "quest-${UUID.randomUUID()}",
            guildId = guild.id,
            title = title,
            description = description,
            type = type,
            status = if (createAsDraft) QuestStatus.DRAFT else QuestStatus.PUBLISHED,
            gpReward = gp,
            expReward = exp,
            createdBy = user.uid,
            announcedAtMillis = announcedAtMillis,
            acceptStartsAtMillis = acceptStartsAtMillis,
            hasTimeLimit = hasTimeLimit || type == QuestType.LIMITED_EVENT_QUEST,
            startsAtMillis = startsAtMillis,
            endsAtMillis = endsAtMillis,
            penaltyGp = if (type == QuestType.LIMITED_EVENT_QUEST) 0 else penaltyGp,
            penaltyExp = if (type == QuestType.LIMITED_EVENT_QUEST) 0 else penaltyExp,
            activeWeekdays = activeWeekdays,
            difficulty = difficulty,
            tags = tags,
            minRank = minRank,
            assignedAdventurerIds = assignedAdventurerIds.distinct(),
            assignedReviewerIds = assignedReviewerIds.distinct(),
            prerequisiteQuestIds = prerequisiteQuestIds.distinct().filterNot { it.isBlank() },
            bonusGp = bonusGp,
            bonusExp = bonusExp,
            gracePeriodDays = if (type == QuestType.LIMITED_EVENT_QUEST) 0 else gracePeriodDays,
            submissionDeadlineDays = if (type == QuestType.LIMITED_EVENT_QUEST) 0 else submissionDeadlineDays,
            weeklyRefreshWeekday = weeklyRefreshWeekday,
            monthlyRefreshDay = monthlyRefreshDay,
            repeatLimitType = if (type == QuestType.REPEATABLE_QUEST) repeatLimitType else RepeatLimitType.NONE,
            repeatLimitCount = if (type == QuestType.REPEATABLE_QUEST) repeatLimitCount.coerceAtLeast(0) else 0,
            formationSlots = if (type == QuestType.FORMATION_QUEST) formationSlots.normalizedFormationSlots() else emptyList(),
            formationRequired = type == QuestType.FORMATION_QUEST && formationRequired,
            formationMinSlotsPerUser = if (type == QuestType.FORMATION_QUEST) formationMinSlotsPerUser.coerceAtLeast(0) else 0,
            formationMaxSlotsPerUser = if (type == QuestType.FORMATION_QUEST) formationMaxSlotsPerUser.coerceAtLeast(1) else 1,
            formationRollMode = if (type == QuestType.FORMATION_QUEST) formationRollMode else FormationRollMode.OPTIONAL_SELF_SELECT,
            formationAutoRollAtMillis = if (type == QuestType.FORMATION_QUEST) formationAutoRollAtMillis else null,
            proofMode = proofMode,
            autoReviewEnabled = autoReviewEnabled,
            pinned = pinned
        ).normalizedTimingPolicy()
        quest.validateGovernancePolicy(guild, data.users.values, user)
        data = data.copy(quests = data.quests + quest)
        if (quest.type == QuestType.FORMATION_QUEST && quest.formationRollMode == FormationRollMode.IMMEDIATE_ROLL) {
            data = data.rollFormationQuestAssignments(quest, guild)
        }
        store.save(data)
        refreshAll(user)
    }

    override fun editQuest(original: Quest, updated: Quest, changeSummary: String) = launchBusy {
        val user = requireUser()
        require(user.hasGuildPermission(requireActiveGuild(), GuildPermission.EDIT_QUESTS)) { "沒有編輯任務權限。" }
        if (updated.type == QuestType.LIMITED_EVENT_QUEST) {
            require(updated.endsAtMillis != null) { "限時討伐令必須設定活動結束日期。" }
        }
        val normalized = updated.normalizedTimingPolicy()
        normalized.validateGovernancePolicy(requireActiveGuild(), data.users.values, user)
        val savedQuest = original.applyEditPolicy(normalized, changeSummary, System.currentTimeMillis())
        data = data.copy(quests = data.quests.map { if (it.id == original.id) savedQuest else it })
        store.save(data)
        refreshAll(user)
    }

    override fun duplicateQuest(quest: Quest) = launchBusy {
        val user = requireUser()
        require(user.hasGuildPermission(requireActiveGuild(), GuildPermission.PUBLISH_QUESTS)) { "沒有發布任務權限。" }
        val copied = quest.copy(
            id = "quest-${UUID.randomUUID()}",
            title = "${quest.title} Copy",
            status = QuestStatus.DRAFT,
            createdBy = user.uid,
            pendingChangeSummary = null,
            pendingChangeEffectiveCycle = null,
            pendingChangeCreatedAtMillis = null,
            pendingChangeQuest = null
        )
        data = data.copy(quests = data.quests + copied)
        store.save(data)
        refreshAll(user)
    }

    override fun seedChildDailyQuests() = launchBusy {
        val user = requireUser()
        val guild = requireActiveGuild()
        require(user.hasGuildPermission(guild, GuildPermission.MANAGE_QUEST_TEMPLATES)) { "沒有管理任務模板權限。" }
        val existingTitles = data.quests.filter { it.guildId == guild.id }.map { it.title }.toSet()
        val quests = childDailyQuestTemplates(guild.id, user.uid)
            .filterNot { it.title in existingTitles }
            .map { it.copy(id = "quest-${UUID.randomUUID()}") }
        data = data.copy(quests = data.quests + quests)
        store.save(data)
        refreshAll(user)
    }

    override fun saveQuestAsTemplate(quest: Quest) = launchBusy {
        val user = requireUser()
        val guild = requireActiveGuild()
        require(user.hasGuildPermission(guild, GuildPermission.MANAGE_QUEST_TEMPLATES)) { "沒有管理任務模板權限。" }
        val template = QuestTemplate(
            id = "template-${UUID.randomUUID()}",
            guildId = guild.id,
            name = quest.title,
            quest = quest.asTemplateQuest(),
            createdBy = user.uid
        )
        val kept = data.questTemplates.filterNot { it.guildId == template.guildId && it.name == template.name }
        data = data.copy(questTemplates = kept + template)
        store.save(data)
        refreshAll(user)
    }

    override fun submitQuest(
        quest: Quest,
        proofText: String,
        proofImageUrl: String?,
        overachieved: Boolean,
        overachievementText: String
    ) = launchBusy {
        val user = requireUser()
        val guild = requireActiveGuild()
        require(user.isGuildAdventurer(guild)) { "你不是此公會的冒險者，不能提交任務回報。" }
        validateQuestSubmission(quest, user, guild, data.submissions)
        if (quest.proofMode == QuestProofMode.TEXT) {
            require(proofText.isNotBlank()) { "此任務需要填寫文字回報。" }
        }
        val now = System.currentTimeMillis()
        if (quest.proofMode != QuestProofMode.IN_PERSON) {
            val submission = quest.buildSubmissionFor(
                adventurer = user,
                proofText = proofText,
                proofImageUrl = proofImageUrl,
                overachieved = overachieved,
                overachievementText = overachievementText,
                submittedAtMillis = now
            )
            val reviewedSubmission = if (quest.autoReviewEnabled) {
                submission.copy(
                    status = SubmissionStatus.APPROVED,
                    reviewedBy = quest.createdBy.takeIf { it.isNotBlank() },
                    reviewedAtMillis = now,
                    reviewNote = if (overachieved) {
                        "自動審核核准基本獎勵；超額部分需另由管理員調整。"
                    } else {
                        "自動審核核准基本獎勵。"
                    }
                )
            } else {
                submission
            }
            val updatedUser = if (quest.autoReviewEnabled) {
                rewardUserForSubmission(user, submission, quest, 0, 0)
            } else {
                user
            }
            data = data.copy(
                users = data.users + (updatedUser.uid to updatedUser),
                submissions = data.submissions + reviewedSubmission
            )
            store.save(data)
            refreshAll(updatedUser)
            reportError(
                if (quest.autoReviewEnabled) {
                    "已提交任務回報，系統已自動核准基本獎勵。"
                } else {
                    "已提交任務回報，等待公會審核。"
                }
            )
            return@launchBusy
        }
        requireNoActiveCounterSession(GuildCounterAction.SUBMIT_QUEST, quest.id, user.uid)
        val session = GuildCounterSession(
            id = "counter-${UUID.randomUUID()}",
            guildId = guild.id,
            action = GuildCounterAction.SUBMIT_QUEST,
            questId = quest.id,
            questTitle = quest.title,
            adventurerUid = user.uid,
            adventurerName = user.displayName,
            proofMode = quest.proofMode,
            proofText = if (quest.proofMode == QuestProofMode.NONE) "" else proofText,
            overachieved = overachieved,
            overachievementText = overachievementText,
            nonce = secureNonce(),
            createdAtMillis = now,
            expiresAtMillis = now + COUNTER_SESSION_TTL_MILLIS,
            adventurerConfirmedAtMillis = now
        )
        publishCounterSession(session)
        data = data.copy(counterSessions = data.counterSessions + session)
        store.save(data)
        refreshAll(user)
        reportError(
            "已建立當面交付。請與管理員將兩支手機靠近，透過 Nearby 連線後直接出示照片、影片或其他證明；檔案不會上傳或傳送。"
        )
    }

    override fun acceptQuest(quest: Quest) = launchBusy {
        val user = requireUser()
        val guild = requireActiveGuild()
        require(user.isGuildAdventurer(guild)) { "你不是此公會的冒險者，不能接取任務。" }
        validateQuestAcceptance(quest, user, guild, data.submissions)
        requireNoActiveCounterSession(GuildCounterAction.ACCEPT_QUEST, quest.id, user.uid)
        val now = System.currentTimeMillis()
        val session = GuildCounterSession(
            id = "counter-${UUID.randomUUID()}",
            guildId = guild.id,
            action = GuildCounterAction.ACCEPT_QUEST,
            questId = quest.id,
            questTitle = quest.title,
            adventurerUid = user.uid,
            adventurerName = user.displayName,
            nonce = secureNonce(),
            createdAtMillis = now,
            expiresAtMillis = now + COUNTER_SESSION_TTL_MILLIS,
            adventurerConfirmedAtMillis = now
        )
        publishCounterSession(session)
        data = data.copy(counterSessions = data.counterSessions + session)
        store.save(data)
        refreshAll(user)
        reportError("接取申請已送到公會櫃檯，需由在線管理員當面確認後才會生效。")
    }

    override fun selectFormationSlot(quest: Quest, slot: QuestSlot) = launchBusy {
        val user = requireUser()
        val guild = requireActiveGuild()
        require(user.isGuildAdventurer(guild)) { "你不是此公會的冒險者，不能選擇位置。" }
        val liveQuest = data.quests.firstOrNull { it.id == quest.id } ?: error("找不到戰團編成令。")
        validateFormationSlotSelection(liveQuest, slot, user, guild)
        val assignment = QuestSlotAssignment(slotId = slot.id, userId = user.uid, userName = user.displayName, assignedByRoll = false)
        data = data.copy(quests = data.quests.map {
            if (it.id == liveQuest.id) it.copy(formationAssignments = it.formationAssignments + assignment) else it
        })
        store.save(data)
        refreshAll(user)
    }

    override fun rollFormationQuest(quest: Quest) = launchBusy {
        val manager = requireUser()
        val guild = requireActiveGuild()
        require(manager.hasGuildPermission(guild, GuildPermission.PUBLISH_QUESTS) || manager.hasGuildPermission(guild, GuildPermission.EDIT_QUESTS)) {
            "沒有分派戰團位置的權限。"
        }
        val liveQuest = data.quests.firstOrNull { it.id == quest.id } ?: error("找不到戰團編成令。")
        require(liveQuest.type == QuestType.FORMATION_QUEST) { "只有戰團編成令可以 Roll 位置。" }
        val adventurers = data.users.values
            .filter { it.isGuildAdventurer(guild) && liveQuest.canBeSeenBy(it) && it.rank.ordinal >= liveQuest.minRank.ordinal }
            .shuffled()
        val assignments = liveQuest.formationAssignments.toMutableList()
        adventurers.forEach { adventurer ->
            while (assignments.count { it.userId == adventurer.uid } < liveQuest.formationMinSlotsPerUser.coerceAtLeast(1)) {
                val slot = liveQuest.formationSlots
                    .filter { adventurer.rank.ordinal >= it.minRank.ordinal }
                    .filter { candidate -> assignments.count { it.slotId == candidate.id } < candidate.capacity }
                    .shuffled()
                    .firstOrNull() ?: break
                assignments += QuestSlotAssignment(slotId = slot.id, userId = adventurer.uid, userName = adventurer.displayName, assignedByRoll = true)
                if (assignments.count { it.userId == adventurer.uid } >= liveQuest.formationMaxSlotsPerUser.coerceAtLeast(1)) break
            }
        }
        data = data.copy(quests = data.quests.map {
            if (it.id == liveQuest.id) it.copy(formationAssignments = assignments.distinctBy { assignment -> assignment.slotId to assignment.userId }) else it
        })
        store.save(data)
        refreshAll(manager)
    }

    override fun setQuestStatus(quest: Quest, status: QuestStatus) = launchBusy {
        val user = requireUser()
        val guild = requireActiveGuild()
        val requiredPermission = if (status == QuestStatus.PUBLISHED || status == QuestStatus.AVAILABLE) GuildPermission.PUBLISH_QUESTS else GuildPermission.UNPUBLISH_QUESTS
        require(user.hasGuildPermission(guild, requiredPermission)) { "沒有${requiredPermission.displayName}權限。" }
        val liveQuest = data.quests.firstOrNull { it.id == quest.id } ?: error("找不到任務。")
        liveQuest.validateStatusTransition(status)
        if (status == QuestStatus.PUBLISHED || status == QuestStatus.AVAILABLE) {
            liveQuest.copy(status = status).validateGovernancePolicy(guild, data.users.values, user)
        }
        data = data.copy(quests = data.quests.map { if (it.id == quest.id) it.copy(status = status) else it })
        store.save(data)
        refreshAll(user)
    }

    override fun reviewSubmission(
        submission: QuestSubmission,
        approved: Boolean,
        note: String?,
        bonusGp: Long,
        bonusExp: Long
    ) = launchBusy {
        val admin = requireUser()
        val guild = requireActiveGuild()
        val quest = data.quests.firstOrNull { it.id == submission.questId } ?: error("找不到任務。")
        val liveSubmission = data.submissions.firstOrNull { it.id == submission.id } ?: error("找不到任務回報。")
        require(liveSubmission.status == SubmissionStatus.SUBMITTED) { "此任務回報已結算，不能重複審核。" }
        require(admin.canReviewQuestSubmission(guild, quest)) { "沒有審核此任務回報的權限。" }
        if (bonusGp > 0 || bonusExp > 0) {
            require(admin.hasGuildPermission(guild, GuildPermission.ADJUST_OVERACHIEVEMENT_REWARDS)) { "沒有調整超額提交獎勵權限。" }
        }
        requireNoActiveCounterSession(GuildCounterAction.SETTLE_SUBMISSION, submission.id, submission.userId)
        val now = System.currentTimeMillis()
        val adventurerCloudUserId = data.users[submission.userId]?.cloudUserId
            ?.takeIf { it.isNotBlank() }
            ?: error("冒險者尚未完成雲端身分同步，請先刷新公會成員資料。")
        val session = GuildCounterSession(
            id = "counter-${UUID.randomUUID()}",
            guildId = guild.id,
            action = GuildCounterAction.SETTLE_SUBMISSION,
            status = GuildCounterSessionStatus.AWAITING_FINAL_CONFIRMATION,
            questId = submission.questId,
            questTitle = submission.questTitle,
            submissionId = submission.id,
            adventurerUid = submission.userId,
            adventurerName = submission.userName,
            managerUid = admin.uid,
            managerName = admin.displayName,
            approved = approved,
            proposedBonusGp = if (approved) bonusGp.coerceAtLeast(0) else 0,
            proposedBonusExp = if (approved) bonusExp.coerceAtLeast(0) else 0,
            reviewNote = note,
            nonce = secureNonce(),
            createdAtMillis = now,
            expiresAtMillis = now + COUNTER_SESSION_TTL_MILLIS,
            managerConfirmedAtMillis = now
        )
        publishCounterSession(session, adventurerCloudUserId)
        data = data.copy(counterSessions = data.counterSessions + session)
        store.save(data)
        refreshAll(admin)
        reportError("結算內容已提出，需冒險者在線確認領取後才會更新 GP／EXP。")
    }

    override fun confirmCounterSession(session: GuildCounterSession) = launchBusy {
        val user = requireUser()
        val live = data.counterSessions.firstOrNull { it.id == session.id } ?: error("找不到這次櫃檯會面。")
        val guild = data.guilds[live.guildId] ?: error("找不到公會。")
        require(canConfirmCounterSession(live, user, guild, System.currentTimeMillis())) {
            "會面已逾時、已完成，或你沒有確認此階段的權限。"
        }
        withContext(Dispatchers.IO) { cloudClient.confirmCounterSession(live.id) }
        val existingSessionIds = data.counterSessions.mapTo(mutableSetOf()) { it.id }
        when (live.action) {
            GuildCounterAction.ACCEPT_QUEST -> completeAcceptanceAtCounter(live, user, guild)
            GuildCounterAction.SUBMIT_QUEST -> completeSubmissionAtCounter(live, user, guild)
            GuildCounterAction.SETTLE_SUBMISSION -> completeSettlementAtCounter(live, user, guild)
        }
        data.counterSessions
            .filter { it.id !in existingSessionIds && it.status in ACTIVE_COUNTER_SESSION_STATUSES }
            .forEach { generated ->
                val targetCloudUserId = data.users[generated.adventurerUid]?.cloudUserId
                    ?.takeIf { it.isNotBlank() }
                    ?: return@forEach
                publishCounterSession(generated, targetCloudUserId)
            }
        store.save(data)
        refreshAll(data.users[user.uid] ?: user)
    }

    override fun cancelCounterSession(session: GuildCounterSession) = launchBusy {
        val user = requireUser()
        val live = data.counterSessions.firstOrNull { it.id == session.id } ?: error("找不到這次櫃檯會面。")
        require(user.uid == live.adventurerUid || user.uid == live.managerUid || user.isGuildManager(requireActiveGuild())) {
            "你不是這次會面的參與者。"
        }
        withContext(Dispatchers.IO) { cloudClient.cancelCounterSession(live.id) }
        data = data.copy(counterSessions = data.counterSessions.map {
            if (it.id == live.id) it.copy(status = GuildCounterSessionStatus.CANCELLED) else it
        })
        store.save(data)
        refreshAll(user)
    }

    override fun startNearbyCounter(session: GuildCounterSession) {
        val user = data.currentUid?.let { data.users[it] } ?: return
        val live = data.counterSessions.firstOrNull { it.id == session.id } ?: return
        if (user.uid == live.adventurerUid) {
            nearbyCounter.advertise(live.id, user.displayName)
        } else {
            nearbyCounter.discover(live.id, user.displayName)
        }
    }

    override fun confirmNearbyCounter(session: GuildCounterSession) {
        val user = data.currentUid?.let { data.users[it] } ?: return
        val nearbyState = nearbyCounter.state.value
        if (nearbyState.sessionId != session.id || nearbyState.phase != NearbyCounterPhase.READY_TO_SIGN) {
            reportError("Nearby 尚未與冒險者手機完成連線。")
            return
        }
        if (!nearbyCounter.sendManagerApproval(session.id, user.uid, user.displayName)) {
            reportError("Nearby 簽核傳送失敗，請重新連線。")
            return
        }
        confirmCounterSession(session)
    }

    override fun stopNearbyCounter() {
        nearbyCounter.stop()
    }

    private fun canConfirmCounterSession(
        session: GuildCounterSession,
        user: UserProfile,
        guild: Guild,
        nowMillis: Long
    ): Boolean {
        if (!GuildCounterSecurityPolicy.isActive(session, nowMillis) || session.guildId != guild.id) return false
        return when (session.action) {
            GuildCounterAction.SUBMIT_QUEST -> {
                val quest = data.quests.firstOrNull { it.id == session.questId } ?: return false
                user.canReviewQuestSubmission(guild, quest)
            }
            else -> GuildCounterSecurityPolicy.canConfirm(session, user, guild, nowMillis)
        }
    }

    override fun onCleared() {
        nearbyCounter.stop()
        super.onCleared()
    }

    override fun createReward(name: String, description: String, cost: Long) = launchBusy {
        val user = requireUser()
        val guild = requireActiveGuild()
        require(user.hasGuildPermission(guild, GuildPermission.MANAGE_REWARDS)) { "沒有管理獎勵權限。" }
        require(name.isNotBlank()) { "獎勵名稱不可空白。" }
        require(cost >= 0) { "兌換 GP 不可為負數。" }
        data = data.copy(rewards = data.rewards + Reward(
            id = "reward-${UUID.randomUUID()}",
            guildId = guild.id,
            name = name.trim(),
            description = description.trim(),
            gpCost = cost
        ))
        store.save(data)
        refreshAll(user)
    }

    override fun redeem(reward: Reward) = launchBusy {
        val user = requireUser()
        val guild = requireActiveGuild()
        require(user.isGuildAdventurer(guild)) { "你尚未以冒險者身分加入此公會。" }
        require(reward.guildId == guild.id && reward.active) { "此獎勵不屬於目前公會或已下架。" }
        require(reward.stock == null || reward.stock > 0) { "此獎勵庫存不足。" }
        require(user.gp >= reward.gpCost) { "GP 不足，無法兌換。" }
        val progress = user.progressForGuild(guild.id)
        val updated = user.withGuildProgress(
            guild.id,
            progress.copy(gp = progress.gp - reward.gpCost)
        )
        val redemption = Redemption(
            id = "redemption-${UUID.randomUUID()}",
            rewardId = reward.id,
            rewardName = reward.name,
            userId = user.uid,
            userName = user.displayName,
            gpCost = reward.gpCost
        )
        val updatedRewards = if (reward.stock == null) {
            data.rewards
        } else {
            data.rewards.map {
                if (it.id == reward.id) it.copy(stock = ((it.stock ?: 0) - 1).coerceAtLeast(0)) else it
            }
        }
        data = data.copy(
            users = data.users + (updated.uid to updated),
            rewards = updatedRewards,
            redemptions = data.redemptions + redemption
        )
        store.save(data)
        refreshAll(updated)
    }

    override fun reviewRedemption(redemption: Redemption, approved: Boolean) = launchBusy {
        val user = requireUser()
        val guild = requireActiveGuild()
        require(user.hasGuildPermission(guild, GuildPermission.REVIEW_REDEMPTIONS)) { "沒有兌換審核權限。" }
        val storedRedemption = data.redemptions.firstOrNull { it.id == redemption.id } ?: error("找不到兌換申請。")
        val reward = data.rewards.firstOrNull { it.id == storedRedemption.rewardId } ?: error("找不到兌換獎勵。")
        require(reward.guildId == guild.id) { "此兌換申請不屬於目前公會。" }
        require(storedRedemption.status == RedemptionStatus.PENDING) { "此兌換申請已處理。" }
        val requester = data.users[redemption.userId]
        val refundedUsers = if (!approved && requester != null) {
            val progress = requester.progressForGuild(guild.id)
            data.users + (
                requester.uid to requester.withGuildProgress(
                    guild.id,
                    progress.copy(gp = progress.gp + storedRedemption.gpCost)
                )
            )
        } else {
            data.users
        }
        val restoredRewards = if (!approved && reward.stock != null) {
            data.rewards.map {
                if (it.id == reward.id) it.copy(stock = (it.stock ?: 0) + 1) else it
            }
        } else {
            data.rewards
        }
        data = data.copy(redemptions = data.redemptions.map {
            if (it.id == storedRedemption.id) {
                it.copy(
                    status = if (approved) RedemptionStatus.APPROVED else RedemptionStatus.REJECTED,
                    reviewedBy = user.uid,
                    reviewedAtMillis = System.currentTimeMillis()
                )
            } else {
                it
            }
        }, users = refundedUsers, rewards = restoredRewards)
        store.save(data)
        refreshAll(user)
    }

    override fun reviewPenalty(record: PenaltyRecord, apply: Boolean) = launchBusy {
        val reviewer = requireUser()
        val guild = requireActiveGuild()
        require(reviewer.hasGuildPermission(guild, GuildPermission.MANAGE_QUEST_PENALTIES)) { "沒有審核任務處罰權限。" }
        require(record.guildId == guild.id && record.status == PenaltyStatus.PENDING) { "此處罰紀錄已處理或不屬於目前公會。" }
        val target = data.users[record.userId]
        val users = if (apply && target != null) {
            val progress = target.progressForGuild(guild.id)
            val nextExp = (progress.exp - record.penaltyExp).coerceAtLeast(0)
            val nextRank = AdventurerRank.fromExp(nextExp)
            data.users + (
                target.uid to target.withGuildProgress(
                    guild.id,
                    progress.copy(
                        gp = (progress.gp - record.penaltyGp).coerceAtLeast(0),
                        exp = nextExp,
                        level = levelFromExp(nextExp).toInt(),
                        rank = nextRank,
                        title = nextRank.displayName
                    )
                )
            )
        } else {
            data.users
        }
        data = data.copy(
            users = users,
            penaltyRecords = data.penaltyRecords.map {
                if (it.id == record.id) {
                    it.copy(
                        status = if (apply) PenaltyStatus.APPLIED else PenaltyStatus.WAIVED,
                        reviewedBy = reviewer.uid,
                        reviewedAtMillis = System.currentTimeMillis()
                    )
                } else {
                    it
                }
            }
        )
        store.save(data)
        refreshAll(data.users[reviewer.uid] ?: reviewer)
    }

    override fun adjustMemberGpExp(member: UserProfile, gpDelta: Long, expDelta: Long, reason: String) = launchBusy {
        val reviewer = requireUser()
        val guild = requireActiveGuild()
        require(reviewer.hasGuildPermission(guild, GuildPermission.MANUAL_ADJUST_GP_EXP)) { "沒有手動補發/扣除 GP EXP 權限。" }
        require(guild.id in member.joinedGuildIds || guild.id in member.managedGuildIds) { "此成員不屬於目前公會。" }
        val current = data.users[member.uid] ?: error("找不到成員。")
        val progress = current.progressForGuild(guild.id)
        val nextExp = (progress.exp + expDelta).coerceAtLeast(0)
        val nextRank = AdventurerRank.fromExp(nextExp)
        val updated = current.withGuildProgress(
            guild.id,
            progress.copy(
                gp = (progress.gp + gpDelta).coerceAtLeast(0),
                exp = nextExp,
                level = levelFromExp(nextExp).toInt(),
                rank = nextRank,
                title = nextRank.displayName
            )
        )
        val record = PenaltyRecord(
            id = "adjustment-${UUID.randomUUID()}",
            guildId = guild.id,
            questTitle = "手動獎懲修正",
            userId = updated.uid,
            userName = updated.displayName,
            cycleKey = "manual-${System.currentTimeMillis()}",
            penaltyGp = -gpDelta,
            penaltyExp = -expDelta,
            reason = reason.ifBlank { "管理員手動修正" },
            status = PenaltyStatus.APPLIED,
            reviewedBy = reviewer.uid,
            reviewedAtMillis = System.currentTimeMillis()
        )
        data = data.copy(
            users = data.users + (updated.uid to updated),
            penaltyRecords = data.penaltyRecords + record
        )
        store.save(data)
        refreshAll(data.users[reviewer.uid] ?: reviewer)
    }

    override fun contributeToRaid(raid: GuildRaid, amount: Long) = launchBusy {
        val user = requireUser()
        val guild = requireActiveGuild()
        require(user.isGuildAdventurer(guild)) { "你尚未以冒險者身分加入此公會。" }
        require(amount > 0) { "貢獻值必須大於 0。" }
        require(raid.guildId == guild.id) { "此討伐戰不屬於目前公會。" }
        require(raid.active) { "討伐戰已結束。" }
        require(raid.endsAtMillis == null || raid.endsAtMillis >= System.currentTimeMillis()) { "討伐戰已過期。" }
        require(raid.currentProgress < raid.targetProgress) { "討伐戰已達成目標，等待結算。" }
        val acceptedAmount = amount.coerceAtMost(raid.targetProgress - raid.currentProgress)
        val updatedRaid = raid.copy(currentProgress = (raid.currentProgress + acceptedAmount).coerceAtMost(raid.targetProgress))
        val progress = user.progressForGuild(guild.id)
        val exp = progress.exp + acceptedAmount * raid.expRewardPerContribution
        val rank = AdventurerRank.fromExp(exp)
        val updatedUser = user.withGuildProgress(
            guild.id,
            progress.copy(
                gp = progress.gp + acceptedAmount * raid.gpRewardPerContribution,
                exp = exp,
                level = levelFromExp(exp).toInt(),
                rank = rank,
                title = rank.displayName
            )
        )
        data = data.copy(
            users = data.users + (updatedUser.uid to updatedUser),
            raids = data.raids.map { if (it.id == raid.id) updatedRaid else it },
            raidContributions = data.raidContributions + GuildRaidContribution(
                id = "raid-contribution-${UUID.randomUUID()}",
                raidId = raid.id,
                guildId = raid.guildId,
                userId = user.uid,
                userName = user.displayName,
                amount = acceptedAmount
            )
        )
        store.save(data)
        refreshAll(updatedUser)
    }

    private fun requireNoActiveCounterSession(action: GuildCounterAction, targetId: String, adventurerUid: String) {
        val duplicate = data.counterSessions.any {
            it.action == action &&
                (it.questId == targetId || it.submissionId == targetId) &&
                it.adventurerUid == adventurerUid &&
                it.status in ACTIVE_COUNTER_SESSION_STATUSES &&
                it.expiresAtMillis >= System.currentTimeMillis()
        }
        require(!duplicate) { "已有相同的公會櫃檯會面等待處理。" }
    }

    private fun completeAcceptanceAtCounter(
        session: GuildCounterSession,
        manager: UserProfile,
        guild: Guild
    ) {
        require(manager.hasGuildPermission(guild, GuildPermission.PUBLISH_QUESTS) ||
            manager.hasGuildPermission(guild, GuildPermission.REVIEW_QUESTS)) {
            "沒有在櫃檯確認任務接取的權限。"
        }
        val adventurer = data.users[session.adventurerUid] ?: error("找不到冒險者。")
        val quest = data.quests.firstOrNull { it.id == session.questId } ?: error("找不到任務。")
        validateQuestAcceptance(quest, adventurer, guild, data.submissions)
        val updatedAdventurer = adventurer.copy(
            acceptedQuestIds = (adventurer.acceptedQuestIds + quest.id).distinct()
        )
        val completed = session.completeWithManager(manager)
        data = data.copy(
            users = data.users + (updatedAdventurer.uid to updatedAdventurer),
            counterSessions = data.counterSessions.replaceSession(completed)
        )
    }

    private fun completeSubmissionAtCounter(
        session: GuildCounterSession,
        manager: UserProfile,
        guild: Guild
    ) {
        val adventurer = data.users[session.adventurerUid] ?: error("找不到冒險者。")
        val quest = data.quests.firstOrNull { it.id == session.questId } ?: error("找不到任務。")
        require(manager.canReviewQuestSubmission(guild, quest)) {
            "沒有審核此 Nearby 任務回報的權限。"
        }
        validateQuestSubmission(quest, adventurer, guild, data.submissions)
        val now = System.currentTimeMillis()
        val submission = quest.buildSubmissionFor(
            adventurer = adventurer,
            proofText = session.proofText,
            proofImageUrl = null,
            overachieved = session.overachieved,
            overachievementText = session.overachievementText,
            submittedAtMillis = now
        )
        val completed = session.copy(submissionId = submission.id).completeWithManager(manager)
        var sessions = data.counterSessions.replaceSession(completed)
        if (quest.autoReviewEnabled) {
            sessions = sessions + GuildCounterSession(
                id = "counter-${UUID.randomUUID()}",
                guildId = guild.id,
                action = GuildCounterAction.SETTLE_SUBMISSION,
                status = GuildCounterSessionStatus.AWAITING_FINAL_CONFIRMATION,
                questId = quest.id,
                questTitle = quest.title,
                submissionId = submission.id,
                adventurerUid = adventurer.uid,
                adventurerName = adventurer.displayName,
                managerUid = manager.uid,
                managerName = manager.displayName,
                approved = true,
                reviewNote = if (session.overachieved) {
                    "自動審核核准基本獎勵；超額部分需另由管理員調整。"
                } else {
                    "自動審核核准基本獎勵。"
                },
                nonce = secureNonce(),
                createdAtMillis = now,
                expiresAtMillis = now + COUNTER_SESSION_TTL_MILLIS,
                managerConfirmedAtMillis = now
            )
        }
        data = data.copy(
            submissions = data.submissions + submission,
            counterSessions = sessions
        )
    }

    private fun completeSettlementAtCounter(
        session: GuildCounterSession,
        adventurer: UserProfile,
        guild: Guild
    ) {
        require(adventurer.uid == session.adventurerUid && adventurer.isGuildAdventurer(guild)) {
            "只有這次回報的冒險者能確認結算。"
        }
        require(session.managerUid != null && session.managerConfirmedAtMillis != null) {
            "管理員尚未確認結算內容。"
        }
        val submissionId = session.submissionId ?: error("結算缺少回報編號。")
        val submission = data.submissions.firstOrNull { it.id == submissionId } ?: error("找不到任務回報。")
        require(submission.status == SubmissionStatus.SUBMITTED) { "此任務回報已結算，不能重複領取獎勵。" }
        val quest = data.quests.firstOrNull { it.id == submission.questId }
        val approved = session.approved == true
        val reviewed = submission.copy(
            status = if (approved) SubmissionStatus.APPROVED else SubmissionStatus.NEEDS_REVISION,
            reviewedBy = session.managerUid,
            reviewedAtMillis = System.currentTimeMillis(),
            reviewBonusGp = if (approved) session.proposedBonusGp else 0,
            reviewBonusExp = if (approved) session.proposedBonusExp else 0,
            reviewNote = session.reviewNote
        )
        val updatedAdventurer = if (approved) {
            rewardUserForSubmission(
                adventurer,
                submission,
                quest,
                session.proposedBonusGp,
                session.proposedBonusExp
            )
        } else {
            adventurer
        }
        val completed = session.completeWithAdventurer()
        data = data.copy(
            users = data.users + (updatedAdventurer.uid to updatedAdventurer),
            submissions = data.submissions.map { if (it.id == submissionId) reviewed else it },
            counterSessions = data.counterSessions.replaceSession(completed)
        )
    }

    private fun refreshAll(user: UserProfile) {
        data = data.normalizedAccess()
        var refreshedUser = data.users[user.uid] ?: user
        val now = System.currentTimeMillis()
        val normalizedCounterSessions = data.counterSessions.map {
            if (it.status in ACTIVE_COUNTER_SESSION_STATUSES && it.expiresAtMillis < now) {
                it.copy(status = GuildCounterSessionStatus.EXPIRED)
            } else {
                it
            }
        }
        if (normalizedCounterSessions != data.counterSessions) {
            data = data.copy(counterSessions = normalizedCounterSessions)
            store.save(data)
        }
        val normalizedQuests = data.quests.map { it.applyDuePendingChange(now).applyEventExpiry(now) }
        if (normalizedQuests != data.quests) {
            val changedGuildIds = data.quests.zip(normalizedQuests)
                .filter { (before, after) -> before != after }
                .map { (_, after) -> after.guildId }
                .distinct()
            data = data.copy(quests = normalizedQuests)
            store.save(data)
            changedGuildIds.forEach { guildId ->
                markLocalChange(
                    guildId = guildId,
                    type = GuildSyncEventType.LOCAL_STATE_CHANGED,
                    summary = "quest cycle state changed",
                    uploadQuestCatalog = true
                )
            }
        }
        val activeGuild = data.currentGuildId
            ?.let { data.guilds[it] }
            ?.takeIf { refreshedUser.canEnterGuild(it) }
        if (activeGuild == null) {
            if (data.currentGuildId != null) {
                data = data.copy(currentGuildId = null)
                store.save(data)
            }
            _state.value = portalState(refreshedUser)
            return
        }
        val activatedUser = refreshedUser.activateGuildProgress(activeGuild.id)
        if (activatedUser != refreshedUser) {
            refreshedUser = activatedUser
            data = data.copy(users = data.users + (refreshedUser.uid to refreshedUser))
            store.save(data)
        }
        val visibleQuests = if (refreshedUser.isGuildManager(activeGuild)) {
            data.quests.filter { it.guildId == activeGuild.id }
        } else {
            data.quests.filter {
                it.guildId == activeGuild.id &&
                    it.canBeSeenBy(refreshedUser) &&
                    it.isVisibleToAdventurer(activeGuild, now)
            }
        }
        val canReviewQuests = refreshedUser.hasGuildPermission(activeGuild, GuildPermission.REVIEW_QUESTS)
        val canReviewNearbySubmissions = refreshedUser.hasGuildPermission(activeGuild, GuildPermission.REVIEW_NEARBY_SUBMISSIONS)
        val canReviewRedemptions = refreshedUser.hasGuildPermission(activeGuild, GuildPermission.REVIEW_REDEMPTIONS)
        val canReviewPenalties = refreshedUser.hasGuildPermission(activeGuild, GuildPermission.MANAGE_QUEST_PENALTIES)
        val withPenaltyRecords = data.generatePendingPenaltyRecords(activeGuild, now)
        if (withPenaltyRecords.penaltyRecords != data.penaltyRecords) {
            data = withPenaltyRecords
            store.save(data)
        }
        val activeRewardIds = data.rewards
            .filter { it.guildId == activeGuild.id }
            .map { it.id }
            .toSet()
        _state.value = GuildUiState(
            loading = false,
            error = null,
            user = refreshedUser,
            activeGuild = activeGuild,
            joinedGuilds = refreshedUser.joinedGuildIds.mapNotNull { data.guilds[it] },
            managedGuilds = refreshedUser.managedGuildIds.mapNotNull { data.guilds[it] },
            quests = visibleQuests,
            questTemplates = (loadBundledQuestTemplates(appContext, activeGuild.id) + data.questTemplates.filter { it.guildId == activeGuild.id })
                .distinctBy { it.id },
            submissions = data.submissions.filter { it.userId == user.uid && data.quests.firstOrNull { quest -> quest.id == it.questId }?.guildId == activeGuild.id },
            pendingSubmissions = if (canReviewQuests || canReviewNearbySubmissions || refreshedUser.isGuildManager(activeGuild)) {
                data.submissions.filter { submission ->
                    val quest = data.quests.firstOrNull { it.id == submission.questId }
                    submission.status == SubmissionStatus.SUBMITTED &&
                        quest?.guildId == activeGuild.id &&
                        refreshedUser.canReviewQuestSubmission(activeGuild, quest)
                }
            } else {
                emptyList()
            },
            rewards = data.rewards.filter { it.active && it.guildId == activeGuild.id },
            redemptions = if (canReviewRedemptions) data.redemptions.filter { it.status == RedemptionStatus.PENDING && it.rewardId in activeRewardIds } else emptyList(),
            pendingPenaltyRecords = if (canReviewPenalties) data.penaltyRecords.filter { it.guildId == activeGuild.id && it.status == PenaltyStatus.PENDING } else emptyList(),
            raids = data.raids.filter { it.active && it.guildId == activeGuild.id && (it.endsAtMillis == null || it.endsAtMillis >= now) },
            raidContributions = data.raidContributions.filter { it.guildId == activeGuild.id },
            joinRequests = activeGuild.joinRequestUserIds.mapNotNull { data.users[it] },
            guildMembers = data.users.values
                .filter {
                    it.uid == activeGuild.ownerUid ||
                        activeGuild.id in it.joinedGuildIds ||
                        activeGuild.id in it.managedGuildIds
                }
                .map { it.activateGuildProgress(activeGuild.id) }
                .sortedWith(compareByDescending<UserProfile> { activeGuild.id in it.managedGuildIds }.thenBy { it.displayName }),
            counterSessions = data.counterSessions.filter {
                it.guildId == activeGuild.id &&
                    it.status in ACTIVE_COUNTER_SESSION_STATUSES &&
                    (
                        it.adventurerUid == refreshedUser.uid ||
                            refreshedUser.isGuildManager(activeGuild)
                    )
            },
            nearbyCounter = nearbyCounter.state.value
        )
    }

    private fun portalState(
        user: UserProfile?,
        destination: GuildPortalDestination = GuildPortalDestination.APP_HOME
    ): GuildUiState = GuildUiState(
        loading = false,
        error = null,
        user = user,
        activeGuild = null,
        portalDestination = destination,
        joinedGuilds = user?.joinedGuildIds?.mapNotNull { data.guilds[it] }.orEmpty(),
        managedGuilds = user?.managedGuildIds?.mapNotNull { data.guilds[it] }.orEmpty(),
        nearbyCounter = nearbyCounter.state.value
    )

    private suspend fun syncCloudMemberships(user: UserProfile) {
        val response = withContext(Dispatchers.IO) { cloudClient.listMyGuilds() }
        val remoteGuilds = response.optJSONArray("guilds") ?: JSONArray()
        var updatedUser = user.copy(cloudUserId = cloudIdentity.userId)
        var guilds = data.guilds
        for (index in 0 until remoteGuilds.length()) {
            val remote = remoteGuilds.getJSONObject(index)
            val guildId = remote.getString("guildId")
            val side = remote.getString("side")
            val existing = guilds[guildId]
            guilds = guilds + (
                guildId to (existing ?: Guild(
                    id = guildId,
                    name = remote.getString("name"),
                    ownerUid = "cloud-owner-$guildId"
                ))
            )
            updatedUser = if (side == "MANAGER") {
                require(guildId !in updatedUser.joinedGuildIds) {
                    "雲端資料拒絕同步：同一公會不能同時是管理方與冒險者。"
                }
                updatedUser.copy(
                    managedGuildIds = (updatedUser.managedGuildIds + guildId).distinct(),
                    guildRoles = updatedUser.guildRoles + (
                        guildId to if (remote.optString("roleCertificate") == "OWNER") {
                            GuildRoleCatalog.defaultRoles.first()
                        } else {
                            GuildRoleCatalog.defaultRoles[6]
                        }
                    )
                )
            } else {
                require(guildId !in updatedUser.managedGuildIds) {
                    "雲端資料拒絕同步：同一公會不能同時是管理方與冒險者。"
                }
                updatedUser.copy(
                    joinedGuildIds = (updatedUser.joinedGuildIds + guildId).distinct(),
                    guildRoles = updatedUser.guildRoles + (guildId to GuildRoleCatalog.defaultRoles.last())
                )
            }
        }
        val remoteMemberships = (0 until remoteGuilds.length())
            .map { remoteGuilds.getJSONObject(it) }
        val remoteManagedGuildIds = remoteMemberships
            .filter { it.getString("side") == "MANAGER" }
            .map { it.getString("guildId") }
            .distinct()
        val remoteJoinedGuildIds = remoteMemberships
            .filter { it.getString("side") == "ADVENTURER" }
            .map { it.getString("guildId") }
            .distinct()
        require(remoteManagedGuildIds.intersect(remoteJoinedGuildIds.toSet()).isEmpty()) {
            "雲端資料拒絕同步：同一公會不能同時是管理方與冒險者。"
        }
        val remoteGuildRoles = remoteMemberships.associate { remote ->
            val role = if (remote.getString("side") == "MANAGER") {
                if (remote.optString("roleCertificate") == "OWNER") {
                    GuildRoleCatalog.defaultRoles.first()
                } else {
                    GuildRoleCatalog.defaultRoles[6]
                }
            } else {
                GuildRoleCatalog.defaultRoles.last()
            }
            remote.getString("guildId") to role
        }
        updatedUser = updatedUser.copy(
            displayName = remoteMemberships.firstOrNull()
                ?.optString("displayName")
                ?.takeIf { it.isNotBlank() }
                ?: updatedUser.displayName,
            managedGuildIds = remoteManagedGuildIds,
            joinedGuildIds = remoteJoinedGuildIds,
            guildRoles = remoteGuildRoles
        )
        remoteMemberships.forEach { remote ->
            val guildId = remote.getString("guildId")
            val current = guilds[guildId] ?: return@forEach
            guilds = guilds + (
                guildId to current.copy(
                    ownerUid = if (remote.optString("roleCertificate") == "OWNER") {
                        user.uid
                    } else {
                        current.ownerUid
                    },
                    inviteCode = remote.optString("inviteCode").ifBlank { current.inviteCode }
                )
            )
        }
        data = data.copy(
            users = data.users + (updatedUser.uid to updatedUser),
            guilds = guilds
        )
        store.save(data)
    }

    private suspend fun syncCloudJoinRequests(guildId: String) {
        val response = runCatching {
            withContext(Dispatchers.IO) { cloudClient.listJoinRequests(guildId) }
        }.getOrNull() ?: return
        val requests = response.optJSONArray("requests") ?: JSONArray()
        val requestUsers = mutableListOf<UserProfile>()
        val requestIds = mutableMapOf<String, String>()
        val requestSides = mutableMapOf<String, String>()
        for (index in 0 until requests.length()) {
            val request = requests.getJSONObject(index)
            val profileId = request.getString("applicantProfileId")
            val requestedSide = request.getString("requestedSide")
            val existing = data.users[profileId]
            requestUsers += (existing ?: UserProfile(
                uid = profileId,
                displayName = request.getString("applicantDisplayName"),
                role = if (requestedSide == "MANAGER") UserRole.GUILD_ADMIN else UserRole.ADVENTURER
            )).copy(cloudUserId = request.getString("applicantUserId"))
            requestIds[profileId] = request.getString("requestId")
            requestSides[profileId] = requestedSide
        }
        val guild = data.guilds[guildId] ?: return
        val updatedGuild = guild.copy(
            joinRequestUserIds = requestUsers.map { it.uid },
            cloudJoinRequestIds = requestIds,
            cloudJoinRequestedSides = requestSides
        )
        data = data.copy(
            users = data.users + requestUsers.associateBy { it.uid },
            guilds = data.guilds + (guildId to updatedGuild)
        )
        store.save(data)
    }

    private suspend fun publishCounterSession(
        session: GuildCounterSession,
        adventurerCloudUserId: String? = null
    ) {
        val summary = JSONObject()
            .put("adventurerProfileId", session.adventurerUid)
            .put("adventurerName", session.adventurerName)
            .put("managerProfileId", session.managerUid ?: "")
            .put("managerName", session.managerName ?: "")
            .put("questId", session.questId)
            .put("questTitle", session.questTitle)
            .put("submissionId", session.submissionId ?: "")
            .put("proofMode", session.proofMode.name)
            .put("proofText", session.proofText)
            .put("overachieved", session.overachieved)
            .put("approved", session.approved)
            .put("proposedBonusGp", session.proposedBonusGp)
            .put("proposedBonusExp", session.proposedBonusExp)
            .put("reviewNote", session.reviewNote ?: "")
            .toString()
        withContext(Dispatchers.IO) {
            cloudClient.createCounterSession(
                sessionId = session.id,
                guildId = session.guildId,
                action = session.action.name,
                adventurerUserId = adventurerCloudUserId,
                nonceHash = session.nonce.sha256(),
                summary = summary,
                expiresAt = session.expiresAtMillis
            )
        }
    }

    private suspend fun syncCloudCounterSessions(guildId: String) {
        val response = runCatching {
            withContext(Dispatchers.IO) { cloudClient.listCounterSessions(guildId) }
        }.getOrNull() ?: return
        val remoteSessions = response.optJSONArray("sessions") ?: JSONArray()
        var sessions = data.counterSessions
        var users = data.users
        for (index in 0 until remoteSessions.length()) {
            val remote = remoteSessions.getJSONObject(index)
            val summary = JSONObject(remote.getString("encryptedSummary"))
            val sessionId = remote.getString("sessionId")
            val existing = sessions.firstOrNull { it.id == sessionId }
            val adventurerProfileId = summary.getString("adventurerProfileId")
            val remoteStatus = enumValueOrDefault(
                remote.getString("status"),
                GuildCounterSessionStatus.WAITING_FOR_COUNTERPART
            )
            val merged = (existing ?: GuildCounterSession(
                id = sessionId,
                guildId = remote.getString("guildId"),
                action = enumValueOrDefault(remote.getString("action"), GuildCounterAction.ACCEPT_QUEST),
                questId = summary.getString("questId"),
                questTitle = summary.getString("questTitle"),
                submissionId = summary.optString("submissionId").takeIf { it.isNotBlank() },
                adventurerUid = adventurerProfileId,
                adventurerName = summary.getString("adventurerName"),
                managerUid = summary.optString("managerProfileId").takeIf { it.isNotBlank() },
                managerName = summary.optString("managerName").takeIf { it.isNotBlank() },
                proofMode = enumValueOrDefault(summary.optString("proofMode"), QuestProofMode.TEXT),
                proofText = summary.optString("proofText"),
                overachieved = summary.optBoolean("overachieved"),
                approved = if (summary.isNull("approved")) null else summary.optBoolean("approved"),
                proposedBonusGp = summary.optLong("proposedBonusGp"),
                proposedBonusExp = summary.optLong("proposedBonusExp"),
                reviewNote = summary.optString("reviewNote").takeIf { it.isNotBlank() },
                nonce = "",
                createdAtMillis = remote.getLong("createdAt"),
                expiresAtMillis = remote.getLong("expiresAt")
            )).copy(
                status = remoteStatus,
                completedAtMillis = remote.optLong("completedAt").takeIf { it > 0 }
            )
            if (adventurerProfileId !in users) {
                users = users + (
                    adventurerProfileId to UserProfile(
                        uid = adventurerProfileId,
                        cloudUserId = remote.getString("adventurerUserId"),
                        displayName = summary.getString("adventurerName"),
                        role = UserRole.ADVENTURER,
                        joinedGuildIds = listOf(guildId)
                    )
                )
            }
            if (existing != null &&
                existing.status in ACTIVE_COUNTER_SESSION_STATUSES &&
                remoteStatus == GuildCounterSessionStatus.COMPLETED
            ) {
                applyRemoteCounterCompletion(existing)
                users = data.users
            }
            sessions = if (existing == null) sessions + merged else sessions.replaceSession(merged)
        }
        data = data.copy(users = users, counterSessions = sessions)
        store.save(data)
    }

    private fun applyRemoteCounterCompletion(session: GuildCounterSession) {
        val current = data.currentUid?.let { data.users[it] } ?: return
        if (current.uid != session.adventurerUid) return
        when (session.action) {
            GuildCounterAction.ACCEPT_QUEST -> {
                data = data.copy(users = data.users + (
                    current.uid to current.copy(
                        acceptedQuestIds = (current.acceptedQuestIds + session.questId).distinct()
                    )
                ))
            }
            GuildCounterAction.SUBMIT_QUEST -> {
                if (data.submissions.none { it.questId == session.questId && it.userId == current.uid }) {
                    val quest = data.quests.firstOrNull { it.id == session.questId } ?: return
                    data = data.copy(submissions = data.submissions + QuestSubmission(
                        id = session.submissionId ?: "submission-${UUID.randomUUID()}",
                        questId = quest.id,
                        questTitle = quest.title,
                        userId = current.uid,
                        userName = current.displayName,
                        proofMode = session.proofMode,
                        proofText = session.proofText,
                        overachieved = session.overachieved,
                        overachievementText = session.overachievementText,
                        gpReward = quest.gpReward + quest.bonusGp,
                        expReward = quest.expReward + quest.bonusExp
                    ))
                }
            }
            GuildCounterAction.SETTLE_SUBMISSION -> Unit
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val before = data
            runCatching { block() }.onFailure { reportError(it.message ?: "發生未知錯誤") }
            val activeGuildId = data.currentGuildId ?: data.currentUid?.let { data.users[it]?.guildId }
            if (activeGuildId != null && data.guildSyncSlice(activeGuildId) != before.guildSyncSlice(activeGuildId)) {
                val questCatalogChanged =
                    data.quests.filter { it.guildId == activeGuildId } !=
                        before.quests.filter { it.guildId == activeGuildId }
                markLocalChange(
                    guildId = activeGuildId,
                    type = GuildSyncEventType.LOCAL_STATE_CHANGED,
                    summary = "local data changed",
                    uploadQuestCatalog = questCatalogChanged
                )
            }
            _state.value = _state.value.copy(loading = false)
        }
    }

    private fun requireUser(): UserProfile = _state.value.user ?: error("尚未登入")

    private fun requireActiveGuild(): Guild =
        data.currentGuildId?.let { data.guilds[it] } ?: error("尚未選擇公會。")

    private suspend fun ensureGuildDriveSnapshot(guildId: String) {
        if (!driveClient.isAuthorized()) return
        val guild = data.guilds[guildId] ?: return
        if (guild.ownerUid != data.currentUid) return
        if (guild.driveStateFileId.isNullOrBlank()) {
            val workspace = driveClient.createGuildWorkspace(
                guildId = guild.id,
                guildName = guild.name,
                stateJson = store.toJson(data.guildScopedSnapshot(guild.id)),
                invitationJson = { stateFileId -> guildInvitationJson(guild, stateFileId) }
            )
            val updatedGuild = guild.copy(
                driveFolderId = workspace.rootFolderId,
                driveStateFileId = workspace.stateFileId,
                driveInviteFileId = workspace.inviteFileId,
                driveManagersFolderId = workspace.managersFolderId,
                driveMemberInboxesFolderId = workspace.memberInboxesFolderId,
                driveAttachmentsFolderId = workspace.attachmentsFolderId,
                driveAuditFolderId = workspace.auditFolderId,
                driveBackupsFolderId = workspace.backupsFolderId
            )
            data = data.copy(guilds = data.guilds + (guild.id to updatedGuild))
            store.save(data)
            driveClient.uploadGuildSnapshot(
                existingFileId = workspace.stateFileId,
                fileName = "guild_state.json",
                json = store.toJson(data.guildScopedSnapshot(guild.id))
            )
            return
        }
        driveClient.uploadGuildSnapshot(
            existingFileId = guild.driveStateFileId,
            fileName = "guild_state.json",
            json = store.toJson(data.guildScopedSnapshot(guild.id))
        )
    }

    private suspend fun importGuildFromDriveLinkIfNeeded(inviteText: String) {
        val driveFileId = inviteText.extractDriveFileId() ?: return
        if (data.guilds.values.any { it.driveInviteFileId == driveFileId || it.driveFolderId == driveFileId }) return
        require(driveClient.isAuthorized()) { "此邀請連結需要先使用 Google 登入並授權 Drive。" }
        val downloaded = driveClient.downloadGuildSnapshot(driveFileId)
        val invitation = runCatching { JSONObject(downloaded) }.getOrNull()
        if (invitation?.optString("kind") == "adventurer-guild-invitation") {
            val guildId = invitation.optString("guildId")
            require(guildId.isNotBlank()) { "公會邀請檔缺少 guildId。" }
            val importedGuild = Guild(
                id = guildId,
                name = invitation.optString("guildName"),
                ownerUid = invitation.optString("ownerUid"),
                ownerEmail = invitation.optString("ownerEmail"),
                inviteCode = invitation.optString("inviteCode"),
                driveStateFileId = invitation.optString("stateFileId").takeIf { it.isNotBlank() },
                driveInviteFileId = driveFileId
            )
            data = data.copy(guilds = data.guilds + (guildId to importedGuild))
        } else {
            val remoteData = store.fromJson(downloaded)
            data = data.mergeRemote(remoteData, keepCurrentSession = true)
        }
        store.save(data)
    }

    private fun syncOn(trigger: GuildSyncTrigger, guildId: String?) {
        if (guildId.isNullOrBlank() || guildId == "default-guild") return
        viewModelScope.launch {
            runCatching { syncCloudQuestCatalog(guildId) }
                .onFailure { reportError("任務同步失敗：${it.message}") }
            if (driveClient.isAuthorized()) {
                runCatching {
                    val guild = data.guilds[guildId]
                    val driveFileId = guild?.driveStateFileId ?: guild?.driveFolderId
                    if (!driveFileId.isNullOrBlank()) {
                        val remoteData = store.fromJson(driveClient.downloadGuildSnapshot(driveFileId))
                        data = data.mergeRemote(remoteData, keepCurrentSession = true)
                        store.save(data)
                    }
                }.onFailure {
                    reportError("Drive 下載失敗：${it.message}")
                }
            }
            val metadata = syncMetadataByGuild[guildId] ?: GuildSyncMetadata(guildId = guildId)
            val result = syncProvider.pullIfChanged(guildId, trigger, metadata)
            syncMetadataByGuild = syncMetadataByGuild + (guildId to result.metadata)
            data.currentUid?.let { data.users[it] }?.let(::refreshAll)
        }
    }

    private suspend fun syncCloudQuestCatalog(guildId: String) {
        val user = data.currentUid?.let { data.users[it] } ?: return
        val guild = data.guilds[guildId] ?: return
        val response = withContext(Dispatchers.IO) {
            cloudClient.getGuildQuestCatalog(guildId)
        }
        val catalog = response.optJSONObject("catalog")
        if (catalog == null) {
            if (user.isGuildManager(guild)) {
                uploadCloudQuestCatalog(guildId)
            } else {
                val withoutLegacySamples = data.quests.filterNot {
                    it.guildId == guildId && it.isLegacySeedQuest(guildId)
                }
                if (withoutLegacySamples != data.quests) {
                    data = data.copy(quests = withoutLegacySamples)
                    store.save(data)
                }
            }
            return
        }
        val remoteQuests = catalog.optJSONArray("quests")
            .toList(::jsonToQuest)
            .filter { it.guildId == guildId }
        data = data.copy(
            quests = data.quests.filterNot { it.guildId == guildId } + remoteQuests
        )
        store.save(data)
    }

    private suspend fun uploadCloudQuestCatalog(guildId: String) {
        val catalog = JSONObject()
            .put("schemaVersion", 1)
            .put("guildId", guildId)
            .put(
                "quests",
                JSONArray(data.quests.filter { it.guildId == guildId }.map(::questToJson))
            )
        withContext(Dispatchers.IO) {
            cloudClient.putGuildQuestCatalog(guildId, catalog)
        }
    }

    private fun markLocalChange(
        guildId: String,
        type: GuildSyncEventType,
        summary: String,
        targetId: String = "",
        uploadQuestCatalog: Boolean = false
    ) {
        if (guildId == "default-guild") return
        val event = GuildSyncEvent(
            id = "sync-event-${UUID.randomUUID()}",
            guildId = guildId,
            actorUid = data.currentUid.orEmpty(),
            type = type,
            targetId = targetId,
            payloadSummary = summary
        )
        viewModelScope.launch {
            val metadata = syncMetadataByGuild[guildId] ?: GuildSyncMetadata(guildId = guildId)
            val pendingMetadata = metadata.copy(pendingEventCount = metadata.pendingEventCount + 1)
            syncMetadataByGuild = syncMetadataByGuild + (guildId to pendingMetadata)
            val currentUser = data.currentUid?.let { data.users[it] }
            val guild = data.guilds[guildId]
            if (
                uploadQuestCatalog &&
                currentUser != null &&
                guild != null &&
                currentUser.isGuildManager(guild)
            ) {
                runCatching { uploadCloudQuestCatalog(guildId) }
                    .onFailure { reportError("任務上傳失敗：${it.message}") }
            }
            if (driveClient.isAuthorized()) {
                runCatching { ensureGuildDriveSnapshot(guildId) }
                    .onFailure { reportError(it.driveSyncErrorMessage("本機資料已保留")) }
            }
            val result = syncProvider.pushPendingEvents(
                guildId = guildId,
                events = listOf(event),
                trigger = GuildSyncTrigger.LOCAL_CHANGE,
                metadata = pendingMetadata
            )
            syncMetadataByGuild = syncMetadataByGuild + (guildId to result.metadata)
        }
    }
}

private fun Throwable.driveSyncErrorMessage(localResult: String): String {
    val detail = message.orEmpty()
    return when {
        detail.contains("has not been used", ignoreCase = true) ||
            detail.contains("SERVICE_DISABLED", ignoreCase = true) ||
            detail.contains("accessNotConfigured", ignoreCase = true) ->
            "$localResult；Google Drive API 尚未啟用，啟用後可重新同步。"
        detail.contains("HTTP 401", ignoreCase = true) ->
            "$localResult；Google Drive 授權已失效，請重新登入授權。"
        detail.contains("HTTP 403", ignoreCase = true) ->
            "$localResult；Google Drive 拒絕存取，請確認 API 與授權設定。"
        else -> "$localResult；Drive 同步暫時失敗，稍後可重新整理再試。"
    }
}

private fun OfflineGuildData.guildSyncSlice(guildId: String): List<Any?> {
    val guildQuestIds = quests.filter { it.guildId == guildId }.map { it.id }.toSet()
    val guildRewardIds = rewards.filter { it.guildId == guildId }.map { it.id }.toSet()
    return listOf(
        guilds[guildId],
        users.values.filter {
            it.uid == guilds[guildId]?.ownerUid ||
                guildId in it.joinedGuildIds ||
                guildId in it.managedGuildIds
        },
        quests.filter { it.guildId == guildId },
        questTemplates.filter { it.guildId == guildId },
        submissions.filter { it.questId in guildQuestIds },
        raids.filter { it.guildId == guildId },
        raidContributions.filter { it.guildId == guildId },
        rewards.filter { it.guildId == guildId },
        redemptions.filter { it.rewardId in guildRewardIds },
        penaltyRecords.filter { it.guildId == guildId },
        counterSessions.filter { it.guildId == guildId }
    )
}

private fun OfflineGuildData.guildScopedSnapshot(guildId: String): OfflineGuildData {
    val guild = guilds[guildId] ?: return OfflineGuildData()
    val memberIds = users.values
        .filter {
            it.uid == guild.ownerUid ||
                guildId in it.joinedGuildIds ||
                guildId in it.managedGuildIds
        }
        .map { it.uid }
        .toSet()
    val guildQuests = quests.filter { it.guildId == guildId }
    val questIds = guildQuests.map { it.id }.toSet()
    val guildRewards = rewards.filter { it.guildId == guildId }
    val rewardIds = guildRewards.map { it.id }.toSet()
    return OfflineGuildData(
        users = users.filterKeys { it in memberIds },
        guilds = mapOf(guildId to guild),
        quests = guildQuests,
        questTemplates = questTemplates.filter { it.guildId == guildId },
        submissions = submissions.filter { it.questId in questIds },
        raids = raids.filter { it.guildId == guildId },
        raidContributions = raidContributions.filter { it.guildId == guildId },
        rewards = guildRewards,
        redemptions = redemptions.filter { it.rewardId in rewardIds },
        penaltyRecords = penaltyRecords.filter { it.guildId == guildId },
        counterSessions = counterSessions
            .filter { it.guildId == guildId }
            .map(GuildCounterSecurityPolicy::cloudSafeCopy)
    )
}

private fun guildInvitationJson(guild: Guild, stateFileId: String): String =
    JSONObject()
        .put("kind", "adventurer-guild-invitation")
        .put("schemaVersion", 1)
        .put("guildId", guild.id)
        .put("guildName", guild.name)
        .put("ownerUid", guild.ownerUid)
        .put("ownerEmail", guild.ownerEmail)
        .put("inviteCode", guild.inviteCode)
        .put("stateFileId", stateFileId)
        .put("createdAtMillis", guild.createdAtMillis)
        .toString(2)

private fun Guild.driveWorkspaceOrNull(): GuildDriveWorkspace? {
    val root = driveFolderId ?: return null
    val state = driveStateFileId ?: return null
    val invite = driveInviteFileId ?: return null
    val managers = driveManagersFolderId ?: return null
    val inboxes = driveMemberInboxesFolderId ?: return null
    val attachments = driveAttachmentsFolderId ?: return null
    val audit = driveAuditFolderId ?: return null
    val backups = driveBackupsFolderId ?: return null
    return GuildDriveWorkspace(
        rootFolderId = root,
        stateFileId = state,
        inviteFileId = invite,
        managersFolderId = managers,
        memberInboxesFolderId = inboxes,
        attachmentsFolderId = attachments,
        auditFolderId = audit,
        backupsFolderId = backups
    )
}

private fun OfflineGuildData.mergeRemote(remote: OfflineGuildData, keepCurrentSession: Boolean): OfflineGuildData {
    val localCurrentUid = currentUid
    val localCurrentGuildId = currentGuildId
    return copy(
        users = users + remote.users,
        guilds = guilds + remote.guilds,
        quests = mergeById(quests, remote.quests) { it.id },
        questTemplates = mergeById(questTemplates, remote.questTemplates) { it.id },
        submissions = mergeById(submissions, remote.submissions) { it.id },
        raids = mergeById(raids, remote.raids) { it.id },
        raidContributions = mergeById(raidContributions, remote.raidContributions) { it.id },
        rewards = mergeById(rewards, remote.rewards) { it.id },
        redemptions = mergeById(redemptions, remote.redemptions) { it.id },
        penaltyRecords = mergeById(penaltyRecords, remote.penaltyRecords) { it.id },
        counterSessions = mergeById(counterSessions, remote.counterSessions) { it.id },
        currentUid = if (keepCurrentSession) localCurrentUid else remote.currentUid,
        currentGuildId = if (keepCurrentSession) localCurrentGuildId else remote.currentGuildId
    ).normalizedAccess()
}

private inline fun <T> mergeById(local: List<T>, remote: List<T>, id: (T) -> String): List<T> {
    val merged = local.associateBy(id).toMutableMap()
    remote.forEach { merged[id(it)] = it }
    return merged.values.toList()
}

private const val COUNTER_SESSION_TTL_MILLIS = 10 * 60 * 1000L

private val ACTIVE_COUNTER_SESSION_STATUSES = setOf(
    GuildCounterSessionStatus.WAITING_FOR_COUNTERPART,
    GuildCounterSessionStatus.AWAITING_FINAL_CONFIRMATION
)

private fun secureNonce(): String =
    ByteArray(24)
        .also(java.security.SecureRandom()::nextBytes)
        .joinToString("") { "%02x".format(it) }

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8)).toHex()

private fun GuildCounterSession.receiptPayload(completedAt: Long): String = listOf(
    id,
    guildId,
    action.name,
    questId,
    submissionId.orEmpty(),
    adventurerUid,
    managerUid.orEmpty(),
    approved?.toString().orEmpty(),
    proposedBonusGp.toString(),
    proposedBonusExp.toString(),
    nonce,
    completedAt.toString()
).joinToString("|")

private fun GuildCounterSession.completeWithManager(manager: UserProfile): GuildCounterSession {
    val now = System.currentTimeMillis()
    val confirmed = copy(
        status = GuildCounterSessionStatus.COMPLETED,
        managerUid = manager.uid,
        managerName = manager.displayName,
        managerConfirmedAtMillis = now,
        completedAtMillis = now
    )
    return confirmed.copy(receiptSha256 = confirmed.receiptPayload(now).sha256())
}

private fun GuildCounterSession.completeWithAdventurer(): GuildCounterSession {
    val now = System.currentTimeMillis()
    val confirmed = copy(
        status = GuildCounterSessionStatus.COMPLETED,
        adventurerConfirmedAtMillis = now,
        completedAtMillis = now
    )
    return confirmed.copy(receiptSha256 = confirmed.receiptPayload(now).sha256())
}

private fun List<GuildCounterSession>.replaceSession(updated: GuildCounterSession): List<GuildCounterSession> =
    map { if (it.id == updated.id) updated else it }

private fun String.extractGuildInviteCode(): String {
    val value = trim()
    val queryCode = Regex("""(?:[?&](?:code|invite|token)=)([A-Za-z0-9-]+)""")
        .find(value)
        ?.groupValues
        ?.getOrNull(1)
    return (queryCode ?: value.substringAfterLast("/")).trim().uppercase(Locale.getDefault())
}

private fun String.extractDriveFileId(): String? {
    val value = trim()
    return Regex("""(?:[?&](?:driveFileId|fileId)=)([A-Za-z0-9_-]+)""")
        .find(value)
        ?.groupValues
        ?.getOrNull(1)
}

private fun Quest.asTemplateQuest(): Quest = copy(
    id = "",
    status = QuestStatus.DRAFT,
    announcedAtMillis = null,
    acceptStartsAtMillis = null,
    startsAtMillis = null,
    endsAtMillis = null,
    createdBy = "",
    pendingChangeSummary = null,
    pendingChangeEffectiveCycle = null,
    pendingChangeCreatedAtMillis = null,
    pendingChangeQuest = null
).normalizedTimingPolicy()

private fun Quest.canBeSeenBy(user: UserProfile): Boolean =
    assignedAdventurerIds.isEmpty() || user.uid in assignedAdventurerIds

private fun validateFormationSlotSelection(quest: Quest, slot: QuestSlot, user: UserProfile, guild: Guild) {
    val now = System.currentTimeMillis()
    require(quest.type == QuestType.FORMATION_QUEST) { "這不是戰團編成令。" }
    require(quest.guildId == guild.id && guild.id in user.joinedGuildIds) { "你尚未以冒險者身分加入此公會。" }
    require(quest.canBeSeenBy(user)) { "此戰團編成令已指名給其他冒險者。" }
    require(quest.isVisibleToAdventurer(guild, now)) { "戰團編成令目前不可選位。" }
    require(slot.id in quest.formationSlots.map { it.id }) { "找不到這個位置。" }
    require(slot.selfSelectable) { "這個位置不開放自選。" }
    require(user.rank.ordinal >= slot.minRank.ordinal && user.rank.ordinal >= quest.minRank.ordinal) { "Rank 不足，無法選擇此位置。" }
    require(quest.formationAssignments.none { it.slotId == slot.id && it.userId == user.uid }) { "你已經選過這個位置。" }
    require(quest.formationAssignments.count { it.userId == user.uid } < quest.formationMaxSlotsPerUser.coerceAtLeast(1)) { "已達每人可選位置上限。" }
    require(quest.formationAssignments.count { it.slotId == slot.id } < slot.capacity) { "這個位置名額已滿。" }
}

private fun UserProfile.canEnterGuild(guild: Guild): Boolean =
    when (role) {
        UserRole.GUILD_ADMIN -> isGuildManager(guild)
        UserRole.ADVENTURER -> isGuildAdventurer(guild)
    }

private fun validateQuestAcceptance(
    quest: Quest,
    user: UserProfile,
    guild: Guild,
    submissions: List<QuestSubmission>
) {
    val now = System.currentTimeMillis()
    require(quest.guildId == guild.id && guild.id in user.joinedGuildIds) { "你尚未以冒險者身分加入此公會。" }
    require(quest.type != QuestType.GUILD_RAID) { "公會討伐戰不需個別接取。" }
    require(!quest.type.isStrictCycleType()) { "每日、每週與每月任務是強制任務，不需要接取。" }
    if (quest.type == QuestType.MAIN_QUEST) {
        val missing = quest.missingPrerequisiteQuestIds(user.uid, submissions)
        if (missing.isNotEmpty()) {
            error("命運篇章尚未解鎖，請先完成 ${missing.size} 個前置任務。")
        }
    }
    if (quest.type == QuestType.PROMOTION_QUEST) {
        require(user.canStartPromotionTrial()) { "尚未達到下一階 EXP 門檻，不能接取晉階試煉。" }
    }
    require(quest.id !in user.acceptedQuestIds) { "你已經接取此任務。" }
    require(user.rank.ordinal >= quest.minRank.ordinal) { "Rank 不足，無法接取此任務。" }
    require(quest.canBeSeenBy(user)) { "此任務已指名給其他冒險者。" }
    require(quest.isVisibleToAdventurer(guild, now)) { "任務目前不可接取。" }
    require(quest.isOpenForAccept(now)) { "任務尚未開放接取。" }
}

private fun validateQuestSubmission(
    quest: Quest,
    user: UserProfile,
    guild: Guild,
    submissions: List<QuestSubmission>
) {
    val now = System.currentTimeMillis()
    require(quest.guildId == guild.id && guild.id in user.joinedGuildIds) { "你尚未以冒險者身分加入此公會。" }
    require(quest.type != QuestType.GUILD_RAID) { "公會討伐戰不使用一般回報流程。" }
    require(quest.canBeSeenBy(user)) { "此任務已指名給其他冒險者。" }
    if (quest.type == QuestType.MAIN_QUEST) {
        val missing = quest.missingPrerequisiteQuestIds(user.uid, submissions)
        require(missing.isEmpty()) { "命運篇章尚未解鎖，請先完成 ${missing.size} 個前置任務。" }
    }
    if (quest.type == QuestType.PROMOTION_QUEST) {
        require(user.canStartPromotionTrial()) { "尚未達到下一階 EXP 門檻，不能提交晉階試煉。" }
    }
    val mandatory = quest.type.isStrictCycleType()
    val repeatable = quest.type == QuestType.REPEATABLE_QUEST
    if (quest.type == QuestType.FORMATION_QUEST) {
        require(quest.isVisibleToAdventurer(guild, now) || quest.isWithinSubmissionDeadline(now)) { "任務已過期，不能再提交。" }
        require(quest.assignedFormationSlots(user.uid).isNotEmpty()) { "請先選擇或等待分派位置。" }
        require(submissions.none { it.questId == quest.id && it.userId == user.uid && it.status == SubmissionStatus.SUBMITTED }) {
            "此戰團編成令已有待審回報，請等管理員審核後再提交。"
        }
        return
    }
    if (repeatable) {
        require(quest.isVisibleToAdventurer(guild, now) || quest.isWithinSubmissionDeadline(now)) { "任務已過期，不能再提交。" }
        require(!quest.isRepeatLimitReached(user.uid, submissions, now)) { "此常駐委託已達提交上限。" }
        require(submissions.none { it.questId == quest.id && it.userId == user.uid && it.status == SubmissionStatus.SUBMITTED }) {
            "此常駐委託已有待審回報，請等管理員審核後再提交下一次。"
        }
        return
    }
    require(mandatory || quest.id in user.acceptedQuestIds) { "非固定強制任務請先接取後再提交回報。" }
    require(quest.isVisibleToAdventurer(guild, now) || quest.isWithinSubmissionDeadline(now)) { "任務已過期，不能再提交。" }
    val currentCycleWindow = if (mandatory) quest.currentSubmissionCycleWindow(now) else null
    val latest = submissions
        .filter { it.questId == quest.id && it.userId == user.uid }
        .filter { currentCycleWindow == null || it.submittedAtMillis in currentCycleWindow }
        .maxByOrNull { it.submittedAtMillis }
    require(latest == null || latest.status == SubmissionStatus.NEEDS_REVISION || latest.status == SubmissionStatus.REJECTED) {
        "此任務已有待審或已核准回報。"
    }
}

private fun Quest.buildSubmissionFor(
    adventurer: UserProfile,
    proofText: String,
    proofImageUrl: String?,
    overachieved: Boolean,
    overachievementText: String,
    submittedAtMillis: Long
): QuestSubmission {
    val formationSlots = if (type == QuestType.FORMATION_QUEST) {
        assignedFormationSlots(adventurer.uid)
    } else {
        emptyList()
    }
    val formationGp = formationSlots.sumOf { it.gpReward }
    val formationExp = formationSlots.sumOf { it.expReward }
    return QuestSubmission(
        id = "submission-${UUID.randomUUID()}",
        questId = id,
        questTitle = title,
        userId = adventurer.uid,
        userName = adventurer.displayName,
        proofMode = proofMode,
        proofText = if (proofMode == QuestProofMode.NONE) "" else proofText,
        proofImageUrl = proofImageUrl,
        overachieved = overachieved,
        overachievementText = overachievementText,
        formationSlotIds = formationSlots.map { it.id },
        formationSlotNames = formationSlots.map { it.name },
        status = SubmissionStatus.SUBMITTED,
        gpReward = if (type == QuestType.FORMATION_QUEST) formationGp + bonusGp else gpReward + bonusGp,
        expReward = if (type == QuestType.FORMATION_QUEST) formationExp + bonusExp else expReward + bonusExp,
        submittedAtMillis = submittedAtMillis
    )
}

private fun rewardUserForSubmission(
    user: UserProfile,
    submission: QuestSubmission,
    quest: Quest?,
    bonusGp: Long,
    bonusExp: Long
): UserProfile {
    val guildId = quest?.guildId ?: user.guildId
    val activeUser = user.activateGuildProgress(guildId)
    val progress = activeUser.progressForGuild(guildId)
    val exp = progress.exp + submission.expReward + bonusExp
    val rank = if (quest?.type == QuestType.PROMOTION_QUEST && activeUser.canStartPromotionTrial()) {
        activeUser.nextPromotionRank() ?: progress.rank
    } else {
        progress.rank
    }
    return activeUser.withGuildProgress(
        guildId,
        progress.copy(
            gp = progress.gp + submission.gpReward + bonusGp,
            exp = exp,
            level = levelFromExp(exp).toInt(),
            rank = rank,
            title = rank.displayName
        )
    )
}

private fun Quest.missingPrerequisiteQuestIds(userId: String, submissions: List<QuestSubmission>): List<String> {
    if (prerequisiteQuestIds.isEmpty()) return emptyList()
    val completedQuestIds = submissions
        .filter { it.userId == userId && it.status == SubmissionStatus.APPROVED }
        .map { it.questId }
        .toSet()
    return prerequisiteQuestIds.filterNot { it in completedQuestIds }
}

private fun UserProfile.nextPromotionRank(): AdventurerRank? =
    AdventurerRank.entries.firstOrNull { it.ordinal > rank.ordinal }

private fun UserProfile.canStartPromotionTrial(): Boolean {
    val nextRank = nextPromotionRank() ?: return false
    return exp >= nextRank.minExp
}

private fun Quest.isVisibleToAdventurer(guild: Guild, now: Long): Boolean =
    (status == QuestStatus.PUBLISHED || status == QuestStatus.AVAILABLE) &&
        isAnnounced(now) &&
        isOpenForQuestWindow(now) &&
        isActiveForCycle(guild, now)

private fun Quest.isAnnounced(now: Long): Boolean =
    announcedAtMillis == null || announcedAtMillis <= now

private fun Quest.isOpenForAccept(now: Long): Boolean =
    acceptStartsAtMillis == null || acceptStartsAtMillis <= now

private fun Quest.isOpenForQuestWindow(now: Long): Boolean {
    if (!hasTimeLimit) return true
    if (startsAtMillis != null && now < startsAtMillis) return false
    if (endsAtMillis != null && now > endsAtMillis) return false
    return true
}

private fun Quest.isWithinSubmissionDeadline(now: Long): Boolean {
    if (!hasTimeLimit || endsAtMillis == null || type.isStrictCycleType()) return false
    val extraDays = (gracePeriodDays + submissionDeadlineDays).coerceAtLeast(0)
    if (extraDays <= 0) return false
    return now <= endsAtMillis + TimeUnit.DAYS.toMillis(extraDays.toLong())
}

private fun Quest.isActiveForCycle(guild: Guild, now: Long): Boolean {
    if (guild.vacationEnabled && type.isStrictCycleType()) return false
    val calendar = Calendar.getInstance(Locale.getDefault()).apply { timeInMillis = now }
    return when (type) {
        QuestType.DAILY_QUEST -> {
            val weekday = calendar.get(Calendar.DAY_OF_WEEK).toIsoWeekday()
            activeWeekdays.isEmpty() || weekday in activeWeekdays
        }
        QuestType.WEEKLY_QUEST -> true
        QuestType.MONTHLY_QUEST -> true
        else -> true
    }
}

private fun Int.toIsoWeekday(): Int =
    if (this == Calendar.SUNDAY) 7 else this - 1

private fun Quest.applyDuePendingChange(now: Long): Quest {
    val pending = pendingChangeQuest ?: return this
    val createdAt = pendingChangeCreatedAtMillis ?: return this
    if (now < nextCycleStartMillis(createdAt)) return this
    return pending.copy(
        id = id,
        guildId = guildId,
        status = status,
        createdBy = createdBy,
        pendingChangeSummary = null,
        pendingChangeEffectiveCycle = null,
        pendingChangeCreatedAtMillis = null,
        pendingChangeQuest = null
    ).normalizedTimingPolicy()
}

private fun Quest.applyEventExpiry(now: Long): Quest {
    if (type != QuestType.LIMITED_EVENT_QUEST) return this
    if (status != QuestStatus.PUBLISHED && status != QuestStatus.AVAILABLE) return this
    val eventEnd = endsAtMillis ?: return this
    return if (now > eventEnd) copy(status = QuestStatus.EXPIRED) else this
}

private fun Quest.nextCycleStartMillis(fromMillis: Long): Long {
    val calendar = Calendar.getInstance(Locale.getDefault()).apply { timeInMillis = fromMillis }
    when (type) {
        QuestType.DAILY_QUEST -> calendar.add(Calendar.DAY_OF_YEAR, 1)
        QuestType.WEEKLY_QUEST -> {
            val refreshWeekday = (weeklyRefreshWeekday ?: 1).coerceIn(1, 7)
            val currentWeekday = calendar.get(Calendar.DAY_OF_WEEK).toIsoWeekday()
            val daysUntilRefresh = ((refreshWeekday - currentWeekday + 7) % 7).let {
                if (it == 0) 7 else it
            }
            calendar.add(Calendar.DAY_OF_YEAR, daysUntilRefresh)
        }
        QuestType.MONTHLY_QUEST -> {
            val refreshDay = (monthlyRefreshDay ?: 1).coerceIn(1, 31)
            calendar.set(
                Calendar.DAY_OF_MONTH,
                minOf(refreshDay, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            )
            calendar.startOfDay()
            if (calendar.timeInMillis <= fromMillis) {
                calendar.add(Calendar.MONTH, 1)
                calendar.set(
                    Calendar.DAY_OF_MONTH,
                    minOf(refreshDay, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                )
            }
        }
        else -> calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

private data class OfflineGuildData(
    val users: Map<String, UserProfile> = emptyMap(),
    val currentUid: String? = null,
    val currentGuildId: String? = null,
    val guilds: Map<String, Guild> = emptyMap(),
    val quests: List<Quest> = emptyList(),
    val questTemplates: List<QuestTemplate> = emptyList(),
    val submissions: List<QuestSubmission> = emptyList(),
    val raids: List<GuildRaid> = emptyList(),
    val raidContributions: List<GuildRaidContribution> = emptyList(),
    val rewards: List<Reward> = emptyList(),
    val redemptions: List<Redemption> = emptyList(),
    val penaltyRecords: List<PenaltyRecord> = emptyList(),
    val counterSessions: List<GuildCounterSession> = emptyList()
) {
    fun ensureSeeded(): OfflineGuildData {
        return this
    }

    fun normalizedAccess(): OfflineGuildData {
        val normalizedUsers = users.mapValues { (_, user) ->
            val ownedGuildIds = guilds.values.filter { it.ownerUid == user.uid }.map { it.id }
            val managed = (user.managedGuildIds + ownedGuildIds)
                .filter { it in guilds }
                .distinct()
            val joined = user.joinedGuildIds
                .filter { it in guilds && it !in managed }
                .distinct()
            val accessibleForMode = when (user.role) {
                UserRole.GUILD_ADMIN -> managed
                UserRole.ADVENTURER -> joined
            }
            val normalized = user.copy(
                joinedGuildIds = joined,
                managedGuildIds = managed,
                guildId = if (user.guildId in accessibleForMode) {
                    user.guildId
                } else {
                    accessibleForMode.firstOrNull() ?: "default-guild"
                }
            )
            if (normalized.guildId == "default-guild") {
                normalized
            } else {
                normalized.activateGuildProgress(normalized.guildId)
            }
        }
        val currentUser = currentUid?.let { normalizedUsers[it] }
        val currentGuild = currentGuildId?.let { guilds[it] }
        val safeCurrentGuildId = if (currentUser != null && currentGuild != null && currentUser.canEnterGuild(currentGuild)) {
            currentGuildId
        } else {
            null
        }
        return copy(users = normalizedUsers, currentGuildId = safeCurrentGuildId)
    }

    fun seedGuildContent(guildId: String): OfflineGuildData {
        if (rewards.any { it.guildId == guildId }) return this
        val seededRewards = if (rewards.any { it.guildId == guildId }) rewards else rewards + listOf(
            Reward(id = "$guildId-reward-badge", guildId = guildId, name = "公會徽章", description = "個人頁展示用稱號徽章。", gpCost = 80),
            Reward(id = "$guildId-reward-supply", guildId = guildId, name = "補給券", description = "可向公會兌換一次補給。", gpCost = 150),
            Reward(id = "$guildId-reward-legend", guildId = guildId, name = "傳說委託入場券", description = "解鎖一次高階挑戰資格。", gpCost = 500)
        )
        return copy(rewards = seededRewards)
    }
}

private fun Quest.isLegacySeedQuest(guildId: String): Boolean =
    id in setOf(
        "$guildId-quest-daily-clean-board",
        "$guildId-quest-weekly-supply",
        "$guildId-quest-monthly-forest",
        "$guildId-quest-promotion-e"
    )

private data class PenaltyCycle(
    val key: String,
    val startMillis: Long,
    val endMillis: Long,
    val reason: String,
    val mandatoryForAllMembers: Boolean
)

private fun OfflineGuildData.generatePendingPenaltyRecords(guild: Guild, now: Long): OfflineGuildData {
    val existingKeys = penaltyRecords.map { it.questId to it.userId to it.cycleKey }.toSet()
    val guildMembers = users.values.filter { it.isGuildAdventurer(guild) }
    var nextUsers = users
    val generated = quests
        .filter { quest ->
            quest.guildId == guild.id &&
                (quest.status == QuestStatus.PUBLISHED || quest.status == QuestStatus.AVAILABLE) &&
                (quest.penaltyGp > 0 || quest.penaltyExp > 0) &&
                quest.isAnnounced(now)
        }
        .flatMap { quest ->
            val cycle = quest.penaltyCycle(guild, now) ?: return@flatMap emptyList()
            if (!quest.appliesToPenaltyCycle(cycle)) return@flatMap emptyList()
            val targets = if (cycle.mandatoryForAllMembers) {
                guildMembers
            } else {
                guildMembers.filter { quest.id in it.acceptedQuestIds }
            }
            targets
                .filterNot { member -> (quest.id to member.uid to cycle.key) in existingKeys }
                .filterNot { member -> hasValidSubmission(member.uid, quest.id, cycle.startMillis, cycle.endMillis) }
                .map { member ->
                    val status = if (quest.autoReviewEnabled) PenaltyStatus.APPLIED else PenaltyStatus.PENDING
                    if (quest.autoReviewEnabled) {
                        val current = nextUsers[member.uid] ?: member
                        val progress = current.progressForGuild(guild.id)
                        val nextExp = (progress.exp - quest.penaltyExp).coerceAtLeast(0)
                        val nextRank = AdventurerRank.fromExp(nextExp)
                        nextUsers = nextUsers + (
                            current.uid to current.withGuildProgress(
                                guild.id,
                                progress.copy(
                                    gp = (progress.gp - quest.penaltyGp).coerceAtLeast(0),
                                    exp = nextExp,
                                    level = levelFromExp(nextExp).toInt(),
                                    rank = nextRank,
                                    title = nextRank.displayName
                                )
                            )
                        )
                    }
                    PenaltyRecord(
                        id = "penalty-${UUID.randomUUID()}",
                        guildId = guild.id,
                        questId = quest.id,
                        questTitle = quest.title,
                        userId = member.uid,
                        userName = member.displayName,
                        cycleKey = cycle.key,
                        penaltyGp = quest.penaltyGp,
                        penaltyExp = quest.penaltyExp,
                        reason = if (quest.autoReviewEnabled) "${cycle.reason}（自動扣除）" else cycle.reason,
                        status = status,
                        reviewedBy = if (quest.autoReviewEnabled) quest.createdBy else null,
                        reviewedAtMillis = if (quest.autoReviewEnabled) now else null
                    )
                }
        }
    return if (generated.isEmpty()) this else copy(users = nextUsers, penaltyRecords = penaltyRecords + generated)
}

private fun Quest.appliesToPenaltyCycle(cycle: PenaltyCycle): Boolean {
    if (announcedAtMillis != null && announcedAtMillis > cycle.startMillis) return false
    if (acceptStartsAtMillis != null && acceptStartsAtMillis > cycle.startMillis) return false
    if (hasTimeLimit && startsAtMillis != null && startsAtMillis > cycle.startMillis) return false
    if (hasTimeLimit && endsAtMillis != null && endsAtMillis < cycle.startMillis) return false
    return true
}

private fun OfflineGuildData.hasValidSubmission(userId: String, questId: String, startMillis: Long, endMillis: Long): Boolean =
    submissions.any {
        it.userId == userId &&
            it.questId == questId &&
            it.submittedAtMillis in startMillis..endMillis &&
            it.status != SubmissionStatus.REJECTED
    }

private fun OfflineGuildData.rollFormationQuestAssignments(quest: Quest, guild: Guild): OfflineGuildData {
    val adventurers = users.values
        .filter { it.isGuildAdventurer(guild) && quest.canBeSeenBy(it) }
        .map { it.activateGuildProgress(guild.id) }
        .filter { it.rank.ordinal >= quest.minRank.ordinal }
        .shuffled()
    val assignments = quest.formationAssignments.toMutableList()
    adventurers.forEach { adventurer ->
        while (assignments.count { it.userId == adventurer.uid } < quest.formationMinSlotsPerUser.coerceAtLeast(1)) {
            val slot = quest.formationSlots
                .filter { adventurer.rank.ordinal >= it.minRank.ordinal }
                .filter { candidate -> assignments.count { it.slotId == candidate.id } < candidate.capacity }
                .shuffled()
                .firstOrNull() ?: break
            assignments += QuestSlotAssignment(slotId = slot.id, userId = adventurer.uid, userName = adventurer.displayName, assignedByRoll = true)
            if (assignments.count { it.userId == adventurer.uid } >= quest.formationMaxSlotsPerUser.coerceAtLeast(1)) break
        }
    }
    return copy(quests = quests.map {
        if (it.id == quest.id) it.copy(formationAssignments = assignments.distinctBy { assignment -> assignment.slotId to assignment.userId }) else it
    })
}

private fun Quest.isRepeatLimitReached(
    userId: String,
    submissions: List<QuestSubmission>,
    now: Long = System.currentTimeMillis()
): Boolean {
    if (type != QuestType.REPEATABLE_QUEST || repeatLimitType == RepeatLimitType.NONE || repeatLimitCount <= 0) return false
    val window = repeatLimitWindow(now)
    val used = submissions.count {
        it.questId == id &&
            it.userId == userId &&
            it.status != SubmissionStatus.REJECTED &&
            it.status != SubmissionStatus.NEEDS_REVISION &&
            (window == null || it.submittedAtMillis in window)
    }
    return used >= repeatLimitCount
}

private fun Quest.repeatLimitWindow(now: Long = System.currentTimeMillis()): LongRange? {
    val today = Calendar.getInstance(Locale.getDefault()).apply { timeInMillis = now }.startOfDay()
    return when (repeatLimitType) {
        RepeatLimitType.NONE, RepeatLimitType.TOTAL -> null
        RepeatLimitType.DAILY -> {
            val start = today.timeInMillis
            start..(start + TimeUnit.DAYS.toMillis(1) - 1)
        }
        RepeatLimitType.WEEKLY -> {
            val start = Calendar.getInstance(Locale.getDefault()).apply {
                timeInMillis = today.timeInMillis
                val weekday = get(Calendar.DAY_OF_WEEK).toIsoWeekday()
                add(Calendar.DAY_OF_YEAR, -((weekday - 1 + 7) % 7))
                startOfDay()
            }.timeInMillis
            start..(start + TimeUnit.DAYS.toMillis(7) - 1)
        }
        RepeatLimitType.MONTHLY -> {
            val startCalendar = Calendar.getInstance(Locale.getDefault()).apply {
                timeInMillis = today.timeInMillis
                set(Calendar.DAY_OF_MONTH, 1)
                startOfDay()
            }
            val endCalendar = Calendar.getInstance(Locale.getDefault()).apply {
                timeInMillis = startCalendar.timeInMillis
                add(Calendar.MONTH, 1)
            }
            startCalendar.timeInMillis..(endCalendar.timeInMillis - 1)
        }
    }
}

private fun Quest.penaltyCycle(guild: Guild, now: Long): PenaltyCycle? {
    if (type == QuestType.LIMITED_EVENT_QUEST) return null
    if (type.isStrictCycleType()) {
        if (guild.vacationEnabled) return null
        return strictPenaltyCycle(now)
    }
    if (!hasTimeLimit || endsAtMillis == null) return null
    val deadline = endsAtMillis + TimeUnit.DAYS.toMillis((gracePeriodDays + submissionDeadlineDays).coerceAtLeast(0).toLong())
    if (now <= deadline) return null
    return PenaltyCycle(
        key = "limited-$id-$deadline",
        startMillis = startsAtMillis ?: 0,
        endMillis = deadline,
        reason = "限期任務超過補交期限",
        mandatoryForAllMembers = false
    )
}

private fun Quest.currentSubmissionCycleWindow(now: Long): LongRange {
    val today = Calendar.getInstance(Locale.getDefault()).apply { timeInMillis = now }.startOfDay()
    return when (type) {
        QuestType.DAILY_QUEST -> {
            val start = today.timeInMillis
            start..(start + TimeUnit.DAYS.toMillis(1) - 1)
        }
        QuestType.WEEKLY_QUEST -> {
            val refreshWeekday = weeklyRefreshWeekday ?: 1
            val todayWeekday = today.get(Calendar.DAY_OF_WEEK).toIsoWeekday()
            val daysSinceRefresh = (todayWeekday - refreshWeekday + 7) % 7
            val start = today.timeInMillis - TimeUnit.DAYS.toMillis(daysSinceRefresh.toLong())
            start..(start + TimeUnit.DAYS.toMillis(7) - 1)
        }
        QuestType.MONTHLY_QUEST -> {
            val refreshDay = (monthlyRefreshDay ?: 1).coerceIn(1, 31)
            val startCalendar = Calendar.getInstance(Locale.getDefault()).apply {
                timeInMillis = today.timeInMillis
                set(Calendar.DAY_OF_MONTH, minOf(refreshDay, getActualMaximum(Calendar.DAY_OF_MONTH)))
                startOfDay()
                if (timeInMillis > today.timeInMillis) {
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, minOf(refreshDay, getActualMaximum(Calendar.DAY_OF_MONTH)))
                    startOfDay()
                }
            }
            val endCalendar = Calendar.getInstance(Locale.getDefault()).apply {
                timeInMillis = startCalendar.timeInMillis
                add(Calendar.MONTH, 1)
                set(Calendar.DAY_OF_MONTH, minOf(refreshDay, getActualMaximum(Calendar.DAY_OF_MONTH)))
                startOfDay()
            }
            startCalendar.timeInMillis..(endCalendar.timeInMillis - 1)
        }
        else -> 0L..Long.MAX_VALUE
    }
}

private fun Quest.strictPenaltyCycle(now: Long): PenaltyCycle? {
    val today = Calendar.getInstance(Locale.getDefault()).apply { timeInMillis = now }.startOfDay()
    return when (type) {
        QuestType.DAILY_QUEST -> {
            val end = today.timeInMillis - 1
            val start = Calendar.getInstance(Locale.getDefault()).apply { timeInMillis = end }.startOfDay().timeInMillis
            val weekday = Calendar.getInstance(Locale.getDefault()).apply { timeInMillis = start }.get(Calendar.DAY_OF_WEEK).toIsoWeekday()
            if (activeWeekdays.isNotEmpty() && weekday !in activeWeekdays) return null
            PenaltyCycle("daily-$id-$start", start, end, "每日任務未於當日完成", mandatoryForAllMembers = true)
        }
        QuestType.WEEKLY_QUEST -> {
            val refreshWeekday = weeklyRefreshWeekday ?: 1
            val todayWeekday = today.get(Calendar.DAY_OF_WEEK).toIsoWeekday()
            if (todayWeekday != refreshWeekday) return null
            val end = today.timeInMillis - 1
            val start = end - TimeUnit.DAYS.toMillis(7) + 1
            PenaltyCycle("weekly-$id-$start", start, end, "每週任務未於週期內完成", mandatoryForAllMembers = true)
        }
        QuestType.MONTHLY_QUEST -> {
            val refreshDay = (monthlyRefreshDay ?: 1).coerceIn(1, 31)
            val effectiveRefreshDay = minOf(refreshDay, today.getActualMaximum(Calendar.DAY_OF_MONTH))
            if (today.get(Calendar.DAY_OF_MONTH) != effectiveRefreshDay) return null
            val previousCycleStart = Calendar.getInstance(Locale.getDefault()).apply {
                timeInMillis = today.timeInMillis
                add(Calendar.MONTH, -1)
                set(Calendar.DAY_OF_MONTH, minOf(refreshDay, getActualMaximum(Calendar.DAY_OF_MONTH)))
            }.startOfDay().timeInMillis
            val end = today.timeInMillis - 1
            PenaltyCycle("monthly-$id-$previousCycleStart", previousCycleStart, end, "每月任務未於週期內完成", mandatoryForAllMembers = true)
        }
        else -> null
    }
}

private fun Calendar.startOfDay(): Calendar = apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

private class OfflineGuildStore(private val context: Context) {
    private val file = File(context.filesDir, "offline_guild_state.json")

    fun load(): OfflineGuildData {
        if (!file.exists()) return OfflineGuildData()
        return fromJson(file.readText())
    }

    fun fromJson(json: String): OfflineGuildData {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return OfflineGuildData()
        return OfflineGuildData(
            users = root.optJSONArray("users").toList(::jsonToUser).associateBy { it.uid },
            currentUid = root.optString("currentUid").takeIf { it.isNotBlank() },
            currentGuildId = root.optString("currentGuildId").takeIf { it.isNotBlank() },
            guilds = root.optJSONArray("guilds").toList(::jsonToGuild).associateBy { it.id },
            quests = root.optJSONArray("quests").toList(::jsonToQuest),
            questTemplates = root.optJSONArray("questTemplates").toList(::jsonToQuestTemplate),
            submissions = root.optJSONArray("submissions").toList(::jsonToSubmission),
            raids = root.optJSONArray("raids").toList(::jsonToRaid),
            raidContributions = root.optJSONArray("raidContributions").toList(::jsonToRaidContribution),
            rewards = root.optJSONArray("rewards").toList(::jsonToReward),
            redemptions = root.optJSONArray("redemptions").toList(::jsonToRedemption),
            penaltyRecords = root.optJSONArray("penaltyRecords").toList(::jsonToPenaltyRecord),
            counterSessions = root.optJSONArray("counterSessions").toList(::jsonToCounterSession)
        )
    }

    fun save(data: OfflineGuildData) {
        file.writeText(toJson(data))
        QuestWidgetUpdater.update(context)
    }

    fun toJson(data: OfflineGuildData): String {
        val root = JSONObject()
            .put("currentUid", data.currentUid ?: "")
            .put("currentGuildId", data.currentGuildId ?: "")
            .put("guilds", JSONArray(data.guilds.values.map(::guildToJson)))
            .put("users", JSONArray(data.users.values.map(::userToJson)))
            .put("quests", JSONArray(data.quests.map(::questToJson)))
            .put("questTemplates", JSONArray(data.questTemplates.map(::questTemplateToJson)))
            .put("submissions", JSONArray(data.submissions.map(::submissionToJson)))
            .put("raids", JSONArray(data.raids.map(::raidToJson)))
            .put("raidContributions", JSONArray(data.raidContributions.map(::raidContributionToJson)))
            .put("rewards", JSONArray(data.rewards.map(::rewardToJson)))
            .put("redemptions", JSONArray(data.redemptions.map(::redemptionToJson)))
            .put("penaltyRecords", JSONArray(data.penaltyRecords.map(::penaltyRecordToJson)))
            .put("counterSessions", JSONArray(data.counterSessions.map(::counterSessionToJson)))
        return root.toString(2)
    }
}

private fun counterSessionToJson(session: GuildCounterSession) = JSONObject()
    .put("id", session.id)
    .put("guildId", session.guildId)
    .put("action", session.action.name)
    .put("status", session.status.name)
    .put("questId", session.questId)
    .put("questTitle", session.questTitle)
    .put("submissionId", session.submissionId ?: "")
    .put("adventurerUid", session.adventurerUid)
    .put("adventurerName", session.adventurerName)
    .put("managerUid", session.managerUid ?: "")
    .put("managerName", session.managerName ?: "")
    .put("proofMode", session.proofMode.name)
    .put("proofText", session.proofText)
    .put("localProofImageUri", session.localProofImageUri ?: "")
    .put("proofImageSha256", session.proofImageSha256 ?: "")
    .put("retainProofCopyApproved", session.retainProofCopyApproved)
    .put("overachieved", session.overachieved)
    .put("overachievementText", session.overachievementText)
    .put("approved", session.approved ?: JSONObject.NULL)
    .put("proposedBonusGp", session.proposedBonusGp)
    .put("proposedBonusExp", session.proposedBonusExp)
    .put("reviewNote", session.reviewNote ?: "")
    .put("nonce", session.nonce)
    .put("createdAtMillis", session.createdAtMillis)
    .put("expiresAtMillis", session.expiresAtMillis)
    .put("adventurerConfirmedAtMillis", session.adventurerConfirmedAtMillis ?: 0)
    .put("managerConfirmedAtMillis", session.managerConfirmedAtMillis ?: 0)
    .put("completedAtMillis", session.completedAtMillis ?: 0)
    .put("receiptSha256", session.receiptSha256 ?: "")

private fun jsonToCounterSession(json: JSONObject) = GuildCounterSession(
    id = json.optString("id"),
    guildId = json.optString("guildId"),
    action = enumValueOrDefault(json.optString("action"), GuildCounterAction.ACCEPT_QUEST),
    status = enumValueOrDefault(
        json.optString("status"),
        GuildCounterSessionStatus.WAITING_FOR_COUNTERPART
    ),
    questId = json.optString("questId"),
    questTitle = json.optString("questTitle"),
    submissionId = json.optString("submissionId").takeIf { it.isNotBlank() },
    adventurerUid = json.optString("adventurerUid"),
    adventurerName = json.optString("adventurerName"),
    managerUid = json.optString("managerUid").takeIf { it.isNotBlank() },
    managerName = json.optString("managerName").takeIf { it.isNotBlank() },
    proofMode = enumValueOrDefault(json.optString("proofMode"), QuestProofMode.TEXT),
    proofText = json.optString("proofText"),
    localProofImageUri = json.optString("localProofImageUri").takeIf { it.isNotBlank() },
    proofImageSha256 = json.optString("proofImageSha256").takeIf { it.isNotBlank() },
    retainProofCopyApproved = json.optBoolean("retainProofCopyApproved", false),
    overachieved = json.optBoolean("overachieved", false),
    overachievementText = json.optString("overachievementText"),
    approved = if (json.isNull("approved")) null else json.optBoolean("approved"),
    proposedBonusGp = json.optLong("proposedBonusGp", 0),
    proposedBonusExp = json.optLong("proposedBonusExp", 0),
    reviewNote = json.optString("reviewNote").takeIf { it.isNotBlank() },
    nonce = json.optString("nonce"),
    createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis()),
    expiresAtMillis = json.optLong("expiresAtMillis", System.currentTimeMillis()),
    adventurerConfirmedAtMillis = json.optLong("adventurerConfirmedAtMillis").takeIf { it > 0 },
    managerConfirmedAtMillis = json.optLong("managerConfirmedAtMillis").takeIf { it > 0 },
    completedAtMillis = json.optLong("completedAtMillis").takeIf { it > 0 },
    receiptSha256 = json.optString("receiptSha256").takeIf { it.isNotBlank() }
)

private inline fun <T> JSONArray?.toList(transform: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).map { transform(getJSONObject(it)) }

private fun userToJson(user: UserProfile) = JSONObject()
    .put("uid", user.uid).put("cloudUserId", user.cloudUserId)
    .put("email", user.email).put("displayName", user.displayName)
    .put("role", user.role.name).put("guildId", user.guildId).put("gp", user.gp).put("exp", user.exp)
    .put("level", user.level).put("rank", user.rank.name).put("title", user.title)
    .put("customTitle", user.customTitle)
    .put("guildRoles", JSONObject(user.guildRoles))
    .put("acceptedQuestIds", JSONArray(user.acceptedQuestIds))
    .put("joinedGuildIds", JSONArray(user.joinedGuildIds))
    .put("managedGuildIds", JSONArray(user.managedGuildIds))
    .put(
        "guildProgress",
        JSONObject(user.guildProgress.mapValues { (_, progress) ->
            JSONObject()
                .put("gp", progress.gp)
                .put("exp", progress.exp)
                .put("level", progress.level)
                .put("rank", progress.rank.name)
                .put("title", progress.title)
        })
    )

private fun jsonToUser(json: JSONObject): UserProfile {
    val exp = json.optLong("exp", 0)
    return UserProfile(
        uid = json.optString("uid"),
        cloudUserId = json.optString("cloudUserId"),
        email = json.optString("email"),
        displayName = json.optString("displayName"),
        role = enumValueOrDefault(json.optString("role"), UserRole.ADVENTURER),
        guildId = json.optString("guildId", "default-guild"),
        gp = json.optLong("gp", 0),
        exp = exp,
        level = json.optInt("level", levelFromExp(exp).toInt()),
        rank = enumValueOrDefault(json.optString("rank"), AdventurerRank.fromExp(exp)),
        title = json.optString("title", "新手冒險者"),
        customTitle = json.optString("customTitle"),
        guildRoles = json.optJSONObject("guildRoles").stringMap(),
        acceptedQuestIds = json.optJSONArray("acceptedQuestIds").stringList(),
        joinedGuildIds = json.optJSONArray("joinedGuildIds").stringList(),
        managedGuildIds = json.optJSONArray("managedGuildIds").stringList(),
        guildProgress = json.optJSONObject("guildProgress").guildProgressMap()
    )
}

private fun JSONObject?.guildProgressMap(): Map<String, GuildProgress> {
    if (this == null) return emptyMap()
    return keys().asSequence().mapNotNull { guildId ->
        val value = optJSONObject(guildId) ?: return@mapNotNull null
        val guildExp = value.optLong("exp", 0)
        guildId to GuildProgress(
            gp = value.optLong("gp", 0),
            exp = guildExp,
            level = value.optInt("level", levelFromExp(guildExp).toInt()),
            rank = enumValueOrDefault(value.optString("rank"), AdventurerRank.fromExp(guildExp)),
            title = value.optString("title").ifBlank { AdventurerRank.fromExp(guildExp).displayName }
        )
    }.toMap()
}

private fun guildToJson(guild: Guild) = JSONObject()
    .put("id", guild.id).put("name", guild.name).put("ownerUid", guild.ownerUid)
    .put("ownerEmail", guild.ownerEmail)
    .put("inviteCode", guild.inviteCode).put("driveFolderId", guild.driveFolderId ?: "")
    .put("driveStateFileId", guild.driveStateFileId ?: "")
    .put("driveInviteFileId", guild.driveInviteFileId ?: "")
    .put("driveManagersFolderId", guild.driveManagersFolderId ?: "")
    .put("driveMemberInboxesFolderId", guild.driveMemberInboxesFolderId ?: "")
    .put("driveAttachmentsFolderId", guild.driveAttachmentsFolderId ?: "")
    .put("driveAuditFolderId", guild.driveAuditFolderId ?: "")
    .put("driveBackupsFolderId", guild.driveBackupsFolderId ?: "")
    .put("driveMemberInboxIds", JSONObject(guild.driveMemberInboxIds))
    .put("driveMemberAttachmentFolderIds", JSONObject(guild.driveMemberAttachmentFolderIds))
    .put("createdAtMillis", guild.createdAtMillis)
    .put("rankTitles", JSONObject(guild.rankTitles))
    .put("rolePermissions", JSONObject(guild.rolePermissions.mapValues { JSONArray(it.value) }))
    .put("joinRequestUserIds", JSONArray(guild.joinRequestUserIds))
    .put("cloudJoinRequestIds", JSONObject(guild.cloudJoinRequestIds))
    .put("cloudJoinRequestedSides", JSONObject(guild.cloudJoinRequestedSides))
    .put("announcement", guild.announcement)
    .put("vacationEnabled", guild.vacationEnabled)
    .put("vacationNote", guild.vacationNote)

private fun jsonToGuild(json: JSONObject) = Guild(
    id = json.optString("id"),
    name = json.optString("name"),
    ownerUid = json.optString("ownerUid"),
    ownerEmail = json.optString("ownerEmail"),
    inviteCode = json.optString("inviteCode"),
    driveFolderId = json.optString("driveFolderId").takeIf { it.isNotBlank() },
    driveStateFileId = json.optString("driveStateFileId").takeIf { it.isNotBlank() },
    driveInviteFileId = json.optString("driveInviteFileId").takeIf { it.isNotBlank() },
    driveManagersFolderId = json.optString("driveManagersFolderId").takeIf { it.isNotBlank() },
    driveMemberInboxesFolderId = json.optString("driveMemberInboxesFolderId").takeIf { it.isNotBlank() },
    driveAttachmentsFolderId = json.optString("driveAttachmentsFolderId").takeIf { it.isNotBlank() },
    driveAuditFolderId = json.optString("driveAuditFolderId").takeIf { it.isNotBlank() },
    driveBackupsFolderId = json.optString("driveBackupsFolderId").takeIf { it.isNotBlank() },
    driveMemberInboxIds = json.optJSONObject("driveMemberInboxIds").stringMap(),
    driveMemberAttachmentFolderIds = json.optJSONObject("driveMemberAttachmentFolderIds").stringMap(),
    createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis()),
    rankTitles = defaultRankTitles() + json.optJSONObject("rankTitles").stringMap(),
    rolePermissions = defaultGuildRolePermissions() + json.optJSONObject("rolePermissions").stringListMap(),
    joinRequestUserIds = json.optJSONArray("joinRequestUserIds").stringList(),
    cloudJoinRequestIds = json.optJSONObject("cloudJoinRequestIds").stringMap(),
    cloudJoinRequestedSides = json.optJSONObject("cloudJoinRequestedSides").stringMap(),
    announcement = json.optString("announcement"),
    vacationEnabled = json.optBoolean("vacationEnabled", false),
    vacationNote = json.optString("vacationNote")
)

private fun questToJson(quest: Quest): JSONObject = JSONObject()
    .put("id", quest.id).put("guildId", quest.guildId).put("title", quest.title).put("description", quest.description)
    .put("type", quest.type.name).put("status", quest.status.name).put("gpReward", quest.gpReward).put("expReward", quest.expReward)
    .put("targetCount", quest.targetCount).put("createdBy", quest.createdBy)
    .put("announcedAtMillis", quest.announcedAtMillis ?: 0).put("acceptStartsAtMillis", quest.acceptStartsAtMillis ?: 0)
    .put("startsAtMillis", quest.startsAtMillis ?: 0).put("endsAtMillis", quest.endsAtMillis ?: 0)
    .put("hasTimeLimit", quest.hasTimeLimit).put("penaltyGp", quest.penaltyGp).put("penaltyExp", quest.penaltyExp)
    .put("activeWeekdays", JSONArray(quest.activeWeekdays))
    .put("difficulty", quest.difficulty.name)
    .put("tags", JSONArray(quest.tags))
    .put("minRank", quest.minRank.name)
    .put("assignedAdventurerIds", JSONArray(quest.assignedAdventurerIds))
    .put("assignedReviewerIds", JSONArray(quest.assignedReviewerIds))
    .put("prerequisiteQuestIds", JSONArray(quest.prerequisiteQuestIds))
    .put("bonusGp", quest.bonusGp)
    .put("bonusExp", quest.bonusExp)
    .put("gracePeriodDays", quest.gracePeriodDays)
    .put("submissionDeadlineDays", quest.submissionDeadlineDays)
    .put("weeklyRefreshWeekday", quest.weeklyRefreshWeekday ?: 0)
    .put("monthlyRefreshDay", quest.monthlyRefreshDay ?: 0)
    .put("repeatLimitType", quest.repeatLimitType.name)
    .put("repeatLimitCount", quest.repeatLimitCount)
    .put("formationSlots", JSONArray(quest.formationSlots.map(::questSlotToJson)))
    .put("formationAssignments", JSONArray(quest.formationAssignments.map(::questSlotAssignmentToJson)))
    .put("formationRequired", quest.formationRequired)
    .put("formationMinSlotsPerUser", quest.formationMinSlotsPerUser)
    .put("formationMaxSlotsPerUser", quest.formationMaxSlotsPerUser)
    .put("formationRollMode", quest.formationRollMode.name)
    .put("formationAutoRollAtMillis", quest.formationAutoRollAtMillis ?: 0)
    .put("proofMode", quest.proofMode.name)
    .put("autoReviewEnabled", quest.autoReviewEnabled)
    .put("pinned", quest.pinned)
    .put("sortOrder", quest.sortOrder)
    .put("pendingChangeSummary", quest.pendingChangeSummary ?: "")
    .put("pendingChangeEffectiveCycle", quest.pendingChangeEffectiveCycle ?: "")
    .put("pendingChangeCreatedAtMillis", quest.pendingChangeCreatedAtMillis ?: 0)
    .put("pendingChangeQuest", quest.pendingChangeQuest?.let(::questToJson) ?: JSONObject())

private fun jsonToQuest(json: JSONObject): Quest = Quest(
    id = json.optString("id"),
    guildId = json.optString("guildId", "default-guild"),
    title = json.optString("title"),
    description = json.optString("description"),
    type = enumValueOrDefault(json.optString("type"), QuestType.DAILY_QUEST),
    status = enumValueOrDefault(json.optString("status"), QuestStatus.DRAFT),
    gpReward = json.optLong("gpReward", 10),
    expReward = json.optLong("expReward", 10),
    targetCount = json.optLong("targetCount", 1),
    createdBy = json.optString("createdBy"),
    announcedAtMillis = json.optLong("announcedAtMillis").takeIf { it > 0 },
    acceptStartsAtMillis = json.optLong("acceptStartsAtMillis").takeIf { it > 0 },
    startsAtMillis = json.optLong("startsAtMillis").takeIf { it > 0 },
    endsAtMillis = json.optLong("endsAtMillis").takeIf { it > 0 },
    hasTimeLimit = json.optBoolean("hasTimeLimit", false),
    penaltyGp = json.optLong("penaltyGp", 0),
    penaltyExp = json.optLong("penaltyExp", 0),
    activeWeekdays = json.optJSONArray("activeWeekdays").intList(),
    difficulty = enumValueOrDefault(json.optString("difficulty"), QuestDifficulty.NORMAL),
    tags = json.optJSONArray("tags").stringList(),
    minRank = enumValueOrDefault(json.optString("minRank"), AdventurerRank.F),
    assignedAdventurerIds = json.optJSONArray("assignedAdventurerIds").stringList(),
    assignedReviewerIds = json.optJSONArray("assignedReviewerIds").stringList(),
    prerequisiteQuestIds = json.optJSONArray("prerequisiteQuestIds").stringList(),
    bonusGp = json.optLong("bonusGp", 0),
    bonusExp = json.optLong("bonusExp", 0),
    gracePeriodDays = json.optInt("gracePeriodDays", 0),
    submissionDeadlineDays = json.optInt("submissionDeadlineDays", 0),
    weeklyRefreshWeekday = json.optInt("weeklyRefreshWeekday", 0).takeIf { it > 0 },
    monthlyRefreshDay = json.optInt("monthlyRefreshDay", 0).takeIf { it > 0 },
    repeatLimitType = enumValueOrDefault(json.optString("repeatLimitType"), RepeatLimitType.NONE),
    repeatLimitCount = json.optInt("repeatLimitCount", 0),
    formationSlots = json.optJSONArray("formationSlots").toList(::jsonToQuestSlot),
    formationAssignments = json.optJSONArray("formationAssignments").toList(::jsonToQuestSlotAssignment),
    formationRequired = json.optBoolean("formationRequired", false),
    formationMinSlotsPerUser = json.optInt("formationMinSlotsPerUser", 1),
    formationMaxSlotsPerUser = json.optInt("formationMaxSlotsPerUser", 1),
    formationRollMode = enumValueOrDefault(json.optString("formationRollMode"), FormationRollMode.OPTIONAL_SELF_SELECT),
    formationAutoRollAtMillis = json.optLong("formationAutoRollAtMillis", 0).takeIf { it > 0 },
    proofMode = enumValueOrDefault(json.optString("proofMode"), QuestProofMode.TEXT),
    autoReviewEnabled = json.optBoolean("autoReviewEnabled", false),
    pinned = json.optBoolean("pinned", false),
    sortOrder = json.optInt("sortOrder", 0),
    pendingChangeSummary = json.optString("pendingChangeSummary").takeIf { it.isNotBlank() },
    pendingChangeEffectiveCycle = json.optString("pendingChangeEffectiveCycle").takeIf { it.isNotBlank() },
    pendingChangeCreatedAtMillis = json.optLong("pendingChangeCreatedAtMillis").takeIf { it > 0 },
    pendingChangeQuest = json.optJSONObject("pendingChangeQuest")
        ?.takeIf { it.optString("id").isNotBlank() || it.optString("title").isNotBlank() }
        ?.let(::jsonToQuest)
)

private fun questSlotToJson(slot: QuestSlot) = JSONObject()
    .put("id", slot.id)
    .put("name", slot.name)
    .put("capacity", slot.capacity)
    .put("gpReward", slot.gpReward)
    .put("expReward", slot.expReward)
    .put("penaltyGp", slot.penaltyGp)
    .put("penaltyExp", slot.penaltyExp)
    .put("description", slot.description)
    .put("selfSelectable", slot.selfSelectable)
    .put("minRank", slot.minRank.name)

private fun jsonToQuestSlot(json: JSONObject) = QuestSlot(
    id = json.optString("id"),
    name = json.optString("name"),
    capacity = json.optInt("capacity", 1),
    gpReward = json.optLong("gpReward", 0),
    expReward = json.optLong("expReward", 0),
    penaltyGp = json.optLong("penaltyGp", 0),
    penaltyExp = json.optLong("penaltyExp", 0),
    description = json.optString("description"),
    selfSelectable = json.optBoolean("selfSelectable", true),
    minRank = enumValueOrDefault(json.optString("minRank"), AdventurerRank.F)
)

private fun questSlotAssignmentToJson(assignment: QuestSlotAssignment) = JSONObject()
    .put("slotId", assignment.slotId)
    .put("userId", assignment.userId)
    .put("userName", assignment.userName)
    .put("assignedByRoll", assignment.assignedByRoll)
    .put("assignedAtMillis", assignment.assignedAtMillis)

private fun jsonToQuestSlotAssignment(json: JSONObject) = QuestSlotAssignment(
    slotId = json.optString("slotId"),
    userId = json.optString("userId"),
    userName = json.optString("userName"),
    assignedByRoll = json.optBoolean("assignedByRoll", false),
    assignedAtMillis = json.optLong("assignedAtMillis", System.currentTimeMillis())
)

private fun questTemplateToJson(template: QuestTemplate) = JSONObject()
    .put("id", template.id)
    .put("guildId", template.guildId)
    .put("name", template.name)
    .put("quest", questToJson(template.quest))
    .put("createdBy", template.createdBy)
    .put("createdAtMillis", template.createdAtMillis)

private fun jsonToQuestTemplate(json: JSONObject) = QuestTemplate(
    id = json.optString("id"),
    guildId = json.optString("guildId", "default-guild"),
    name = json.optString("name"),
    quest = json.optJSONObject("quest")?.let(::jsonToQuest) ?: Quest(),
    createdBy = json.optString("createdBy"),
    createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis())
)

private fun submissionToJson(submission: QuestSubmission) = JSONObject()
    .put("id", submission.id).put("questId", submission.questId).put("questTitle", submission.questTitle)
    .put("userId", submission.userId).put("userName", submission.userName)
    .put("proofMode", submission.proofMode.name).put("proofText", submission.proofText)
    .put("proofImageUrl", submission.proofImageUrl ?: "")
    .put("overachieved", submission.overachieved).put("overachievementText", submission.overachievementText)
    .put("formationSlotIds", JSONArray(submission.formationSlotIds))
    .put("formationSlotNames", JSONArray(submission.formationSlotNames))
    .put("status", submission.status.name).put("gpReward", submission.gpReward).put("expReward", submission.expReward)
    .put("submittedAtMillis", submission.submittedAtMillis).put("reviewedBy", submission.reviewedBy ?: "")
    .put("reviewBonusGp", submission.reviewBonusGp).put("reviewBonusExp", submission.reviewBonusExp)
    .put("reviewedAtMillis", submission.reviewedAtMillis ?: 0).put("reviewNote", submission.reviewNote ?: "")

private fun jsonToSubmission(json: JSONObject) = QuestSubmission(
    id = json.optString("id"),
    questId = json.optString("questId"),
    questTitle = json.optString("questTitle"),
    userId = json.optString("userId"),
    userName = json.optString("userName"),
    proofMode = enumValueOrDefault(json.optString("proofMode"), QuestProofMode.TEXT),
    proofText = json.optString("proofText"),
    proofImageUrl = json.optString("proofImageUrl").takeIf { it.isNotBlank() },
    overachieved = json.optBoolean("overachieved", false),
    overachievementText = json.optString("overachievementText"),
    formationSlotIds = json.optJSONArray("formationSlotIds").stringList(),
    formationSlotNames = json.optJSONArray("formationSlotNames").stringList(),
    status = enumValueOrDefault(json.optString("status"), SubmissionStatus.SUBMITTED),
    gpReward = json.optLong("gpReward", 0),
    expReward = json.optLong("expReward", 0),
    submittedAtMillis = json.optLong("submittedAtMillis", System.currentTimeMillis()),
    reviewedBy = json.optString("reviewedBy").takeIf { it.isNotBlank() },
    reviewedAtMillis = json.optLong("reviewedAtMillis").takeIf { it > 0 },
    reviewBonusGp = json.optLong("reviewBonusGp", 0),
    reviewBonusExp = json.optLong("reviewBonusExp", 0),
    reviewNote = json.optString("reviewNote").takeIf { it.isNotBlank() }
)

private fun raidToJson(raid: GuildRaid) = JSONObject()
    .put("id", raid.id).put("guildId", raid.guildId).put("title", raid.title).put("description", raid.description)
    .put("targetProgress", raid.targetProgress).put("currentProgress", raid.currentProgress)
    .put("gpRewardPerContribution", raid.gpRewardPerContribution).put("expRewardPerContribution", raid.expRewardPerContribution)
    .put("active", raid.active)

private fun jsonToRaid(json: JSONObject) = GuildRaid(
    id = json.optString("id"),
    guildId = json.optString("guildId", "default-guild"),
    title = json.optString("title"),
    description = json.optString("description"),
    targetProgress = json.optLong("targetProgress", 100),
    currentProgress = json.optLong("currentProgress", 0),
    gpRewardPerContribution = json.optLong("gpRewardPerContribution", 1),
    expRewardPerContribution = json.optLong("expRewardPerContribution", 1),
    active = json.optBoolean("active", true)
)

private fun raidContributionToJson(contribution: GuildRaidContribution) = JSONObject()
    .put("id", contribution.id).put("raidId", contribution.raidId).put("guildId", contribution.guildId)
    .put("userId", contribution.userId).put("userName", contribution.userName)
    .put("amount", contribution.amount).put("createdAtMillis", contribution.createdAtMillis)

private fun jsonToRaidContribution(json: JSONObject) = GuildRaidContribution(
    id = json.optString("id"),
    raidId = json.optString("raidId"),
    guildId = json.optString("guildId", "default-guild"),
    userId = json.optString("userId"),
    userName = json.optString("userName"),
    amount = json.optLong("amount", 0),
    createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis())
)

private fun rewardToJson(reward: Reward) = JSONObject()
    .put("id", reward.id).put("guildId", reward.guildId).put("name", reward.name).put("description", reward.description)
    .put("gpCost", reward.gpCost).put("active", reward.active)

private fun jsonToReward(json: JSONObject) = Reward(
    id = json.optString("id"),
    guildId = json.optString("guildId", "default-guild"),
    name = json.optString("name"),
    description = json.optString("description"),
    gpCost = json.optLong("gpCost", 100),
    active = json.optBoolean("active", true)
)

private fun redemptionToJson(redemption: Redemption) = JSONObject()
    .put("id", redemption.id).put("rewardId", redemption.rewardId).put("rewardName", redemption.rewardName)
    .put("userId", redemption.userId).put("userName", redemption.userName).put("gpCost", redemption.gpCost)
    .put("status", redemption.status.name).put("requestedAtMillis", redemption.requestedAtMillis)
    .put("reviewedBy", redemption.reviewedBy ?: "").put("reviewedAtMillis", redemption.reviewedAtMillis ?: 0)

private fun jsonToRedemption(json: JSONObject) = Redemption(
    id = json.optString("id"),
    rewardId = json.optString("rewardId"),
    rewardName = json.optString("rewardName"),
    userId = json.optString("userId"),
    userName = json.optString("userName"),
    gpCost = json.optLong("gpCost", 0),
    status = enumValueOrDefault(json.optString("status"), RedemptionStatus.PENDING),
    requestedAtMillis = json.optLong("requestedAtMillis", System.currentTimeMillis()),
    reviewedBy = json.optString("reviewedBy").takeIf { it.isNotBlank() },
    reviewedAtMillis = json.optLong("reviewedAtMillis").takeIf { it > 0 }
)

private fun penaltyRecordToJson(record: PenaltyRecord) = JSONObject()
    .put("id", record.id)
    .put("guildId", record.guildId)
    .put("questId", record.questId)
    .put("questTitle", record.questTitle)
    .put("userId", record.userId)
    .put("userName", record.userName)
    .put("cycleKey", record.cycleKey)
    .put("penaltyGp", record.penaltyGp)
    .put("penaltyExp", record.penaltyExp)
    .put("reason", record.reason)
    .put("status", record.status.name)
    .put("createdAtMillis", record.createdAtMillis)
    .put("reviewedBy", record.reviewedBy ?: "")
    .put("reviewedAtMillis", record.reviewedAtMillis ?: 0)

private fun jsonToPenaltyRecord(json: JSONObject) = PenaltyRecord(
    id = json.optString("id"),
    guildId = json.optString("guildId", "default-guild"),
    questId = json.optString("questId"),
    questTitle = json.optString("questTitle"),
    userId = json.optString("userId"),
    userName = json.optString("userName"),
    cycleKey = json.optString("cycleKey"),
    penaltyGp = json.optLong("penaltyGp", 0),
    penaltyExp = json.optLong("penaltyExp", 0),
    reason = json.optString("reason"),
    status = enumValueOrDefault(json.optString("status"), PenaltyStatus.PENDING),
    createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis()),
    reviewedBy = json.optString("reviewedBy").takeIf { it.isNotBlank() },
    reviewedAtMillis = json.optLong("reviewedAtMillis").takeIf { it > 0 }
)

private fun JSONArray?.stringList(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }

private fun JSONArray?.intList(): List<Int> =
    if (this == null) emptyList() else (0 until length()).map { getInt(it) }

private fun JSONObject?.stringMap(): Map<String, String> =
    if (this == null) {
        emptyMap()
    } else {
        keys().asSequence().associateWith { optString(it) }
    }

private fun JSONObject?.stringListMap(): Map<String, List<String>> =
    if (this == null) {
        emptyMap()
    } else {
        keys().asSequence().associateWith { optJSONArray(it).stringList() }
    }

private fun randomInviteCode(): String =
    UUID.randomUUID().toString().take(8).uppercase()

private fun Quest.applyEditPolicy(updated: Quest, changeSummary: String, now: Long): Quest {
    val isUnannounced = announcedAtMillis == null || announcedAtMillis > now
    if (isUnannounced || !isFixedCycleQuest()) {
        return updated.copy(
            pendingChangeSummary = null,
            pendingChangeEffectiveCycle = null,
            pendingChangeCreatedAtMillis = null,
            pendingChangeQuest = null
        )
    }
    return copy(
        pendingChangeSummary = changeSummary.ifBlank { describeQuestChanges(this, updated) },
        pendingChangeEffectiveCycle = nextCycleLabel(),
        pendingChangeCreatedAtMillis = now,
        pendingChangeQuest = updated.copy(
            id = id,
            guildId = guildId,
            status = status,
            createdBy = createdBy,
            pendingChangeSummary = null,
            pendingChangeEffectiveCycle = null,
            pendingChangeCreatedAtMillis = null,
            pendingChangeQuest = null
        )
    )
}

private fun Quest.isFixedCycleQuest(): Boolean =
    type == QuestType.DAILY_QUEST || type == QuestType.WEEKLY_QUEST || type == QuestType.MONTHLY_QUEST

private fun Quest.nextCycleLabel(): String = when (type) {
    QuestType.DAILY_QUEST -> "下一個每日循環"
    QuestType.WEEKLY_QUEST -> "下一個每週循環"
    QuestType.MONTHLY_QUEST -> "下一個每月循環"
    else -> "下一個循環"
}

private fun describeQuestChanges(old: Quest, updated: Quest): String {
    val fields = buildList {
        if (old.title != updated.title) add("標題")
        if (old.description != updated.description) add("描述")
        if (old.type != updated.type) add("類型")
        if (old.gpReward != updated.gpReward) add("GP")
        if (old.expReward != updated.expReward) add("EXP")
        if (old.announcedAtMillis != updated.announcedAtMillis) add("公告日期")
        if (old.acceptStartsAtMillis != updated.acceptStartsAtMillis) add("開放接取日期")
        if (old.hasTimeLimit != updated.hasTimeLimit || old.startsAtMillis != updated.startsAtMillis || old.endsAtMillis != updated.endsAtMillis) add("期間")
        if (old.penaltyGp != updated.penaltyGp || old.penaltyExp != updated.penaltyExp) add("未完成處罰")
        if (old.activeWeekdays != updated.activeWeekdays) add("星期")
    }
    return if (fields.isEmpty()) "任務設定已調整" else fields.joinToString("、") + "將調整"
}
