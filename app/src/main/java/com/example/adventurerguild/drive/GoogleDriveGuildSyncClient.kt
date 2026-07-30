package com.example.adventurerguild.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GuildDriveWorkspace(
    val rootFolderId: String,
    val stateFileId: String,
    val inviteFileId: String,
    val managersFolderId: String,
    val memberInboxesFolderId: String,
    val attachmentsFolderId: String,
    val auditFolderId: String,
    val backupsFolderId: String
)

class GoogleDriveGuildSyncClient {
    private var accessToken: String? = null

    fun setAccessToken(token: String?) {
        accessToken = token?.takeIf { it.isNotBlank() }
    }

    fun isAuthorized(): Boolean = !accessToken.isNullOrBlank()

    suspend fun createJoinRequest(
        request: DriveGuildJoinRequest,
        guildOwnerEmail: String
    ): String = withContext(Dispatchers.IO) {
        require(guildOwnerEmail.isNotBlank()) { "公會邀請缺少會長電子郵件，無法送出加入申請。" }
        val json = JSONObject()
            .put("kind", "adventurer-guild-join-request")
            .put("schemaVersion", request.schemaVersion)
            .put("requestId", request.requestId)
            .put("guildId", request.guildId)
            .put("inviteCode", request.inviteCode)
            .put("applicantUid", request.applicantUid)
            .put("applicantEmail", request.applicantEmail)
            .put("applicantName", request.applicantName)
            .put("requestedSide", request.requestedSide.name)
            .put("createdAtMillis", request.createdAtMillis)
            .toString(2)
        val fileId = createJsonFile(
            fileName = "guild_join_request_${request.guildId}_${request.requestId}.json",
            json = json,
            appProperties = mapOf(
                "kind" to "guild_join_request",
                "guildId" to request.guildId,
                "requestId" to request.requestId
            )
        )
        shareFileWithUser(fileId, guildOwnerEmail, "reader")
        fileId
    }

    suspend fun listSharedJoinRequestFileIds(guildId: String): List<String> =
        withContext(Dispatchers.IO) {
            val query = """
                sharedWithMe and trashed = false and
                appProperties has { key='kind' and value='guild_join_request' } and
                appProperties has { key='guildId' and value='$guildId' }
            """.trimIndent().replace("\n", " ")
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            val response = request(
                method = "GET",
                url = "https://www.googleapis.com/drive/v3/files?q=$encoded&fields=files(id)"
            )
            val files = JSONObject(response).optJSONArray("files") ?: return@withContext emptyList()
            (0 until files.length()).map { files.getJSONObject(it).getString("id") }
        }

    suspend fun provisionMemberWorkspace(
        workspace: GuildDriveWorkspace,
        memberUid: String,
        memberEmail: String
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        require(memberEmail.isNotBlank()) { "成員缺少 Google 電子郵件，無法配置 Drive 權限。" }
        val inboxId = createFolder(memberUid, workspace.memberInboxesFolderId)
        val attachmentId = createFolder(memberUid, workspace.attachmentsFolderId)
        shareFileWithUser(workspace.stateFileId, memberEmail, "reader")
        shareFileWithUser(inboxId, memberEmail, "writer")
        shareFileWithUser(attachmentId, memberEmail, "writer")
        inboxId to attachmentId
    }

    suspend fun revokeMemberWorkspace(
        workspace: GuildDriveWorkspace,
        memberEmail: String,
        inboxId: String?,
        attachmentId: String?
    ) = withContext(Dispatchers.IO) {
        if (memberEmail.isBlank()) return@withContext
        revokeUserPermission(workspace.stateFileId, memberEmail)
        inboxId?.let { revokeUserPermission(it, memberEmail) }
        attachmentId?.let { revokeUserPermission(it, memberEmail) }
    }

    suspend fun createGuildWorkspace(
        guildId: String,
        guildName: String,
        stateJson: String,
        invitationJson: (stateFileId: String) -> String
    ): GuildDriveWorkspace = withContext(Dispatchers.IO) {
        val rootId = createFolder("Adventurer Guild - $guildName [$guildId]")
        val managersId = createFolder("managers", rootId)
        val inboxesId = createFolder("member_inboxes", rootId)
        val attachmentsId = createFolder("attachments", rootId)
        val auditId = createFolder("audit", rootId)
        val backupsId = createFolder("backups", rootId)
        val stateId = createJsonFile("guild_state.json", stateJson, rootId)
        val inviteId = createJsonFile(
            fileName = "guild_invite_$guildId.json",
            json = invitationJson(stateId),
            parentId = rootId
        )
        shareFileReadOnlyWithLink(inviteId)
        GuildDriveWorkspace(
            rootFolderId = rootId,
            stateFileId = stateId,
            inviteFileId = inviteId,
            managersFolderId = managersId,
            memberInboxesFolderId = inboxesId,
            attachmentsFolderId = attachmentsId,
            auditFolderId = auditId,
            backupsFolderId = backupsId
        )
    }

    suspend fun uploadGuildSnapshot(
        existingFileId: String?,
        fileName: String,
        json: String
    ): String = withContext(Dispatchers.IO) {
        if (existingFileId.isNullOrBlank()) {
            val createdFileId = createJsonFile(fileName, json)
            shareFileReadOnlyWithLink(createdFileId)
            createdFileId
        } else {
            updateJsonFile(existingFileId, json)
            existingFileId
        }
    }

    suspend fun downloadGuildSnapshot(fileId: String): String = withContext(Dispatchers.IO) {
        request(
            method = "GET",
            url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        )
    }

    private fun createFolder(name: String, parentId: String? = null): String {
        val metadata = JSONObject()
            .put("name", name)
            .put("mimeType", "application/vnd.google-apps.folder")
        parentId?.takeIf { it.isNotBlank() }?.let {
            metadata.put("parents", org.json.JSONArray().put(it))
        }
        val response = request(
            method = "POST",
            url = "https://www.googleapis.com/drive/v3/files?fields=id",
            body = metadata.toString(),
            contentType = "application/json; charset=UTF-8"
        )
        return JSONObject(response).getString("id")
    }

    private fun createJsonFile(
        fileName: String,
        json: String,
        parentId: String? = null,
        appProperties: Map<String, String> = emptyMap()
    ): String {
        val boundary = "guild-${System.currentTimeMillis()}"
        val metadata = JSONObject()
            .put("name", fileName)
            .put("mimeType", "application/json")
        parentId?.takeIf { it.isNotBlank() }?.let {
            metadata.put("parents", org.json.JSONArray().put(it))
        }
        if (appProperties.isNotEmpty()) {
            metadata.put("appProperties", JSONObject(appProperties))
        }
        val body = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString())
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(json)
            append("\r\n--$boundary--\r\n")
        }
        val response = request(
            method = "POST",
            url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
            body = body,
            contentType = "multipart/related; boundary=$boundary"
        )
        return JSONObject(response).getString("id")
    }

    private fun updateJsonFile(fileId: String, json: String) {
        request(
            method = "PATCH",
            url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media",
            body = json,
            contentType = "application/json; charset=UTF-8"
        )
    }

    private fun shareFileReadOnlyWithLink(fileId: String) {
        val permission = JSONObject()
            .put("type", "anyone")
            .put("role", "reader")
            .put("allowFileDiscovery", false)
        request(
            method = "POST",
            url = "https://www.googleapis.com/drive/v3/files/$fileId/permissions?fields=id",
            body = permission.toString(),
            contentType = "application/json; charset=UTF-8"
        )
    }

    private fun shareFileWithUser(fileId: String, email: String, role: String) {
        val permission = JSONObject()
            .put("type", "user")
            .put("role", role)
            .put("emailAddress", email)
        request(
            method = "POST",
            url = "https://www.googleapis.com/drive/v3/files/$fileId/permissions?sendNotificationEmail=false&fields=id",
            body = permission.toString(),
            contentType = "application/json; charset=UTF-8"
        )
    }

    private fun revokeUserPermission(fileId: String, email: String) {
        val response = request(
            method = "GET",
            url = "https://www.googleapis.com/drive/v3/files/$fileId/permissions?fields=permissions(id,emailAddress)"
        )
        val permissions = JSONObject(response).optJSONArray("permissions") ?: return
        for (index in 0 until permissions.length()) {
            val permission = permissions.getJSONObject(index)
            if (permission.optString("emailAddress").equals(email, ignoreCase = true)) {
                request(
                    method = "DELETE",
                    url = "https://www.googleapis.com/drive/v3/files/$fileId/permissions/${permission.getString("id")}"
                )
            }
        }
    }

    private fun request(
        method: String,
        url: String,
        body: String? = null,
        contentType: String? = null
    ): String {
        val token = accessToken ?: error("尚未授權 Google Drive。")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: "application/json; charset=UTF-8")
            }
        }
        if (body != null) {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) {
            error("Google Drive API 失敗：HTTP $code $response")
        }
        return response
    }
}
