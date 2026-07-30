package com.example.adventurerguild.cloud

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

class CloudflareApiClient(
    private val baseUrl: String,
    private val identity: DeviceIdentityStore
) {
    fun health(): JSONObject = request("GET", "/health")

    fun registerDevice(
        displayName: String,
        transferCode: String? = null,
        accountUserId: String = identity.userId
    ): JSONObject {
        val body = JSONObject()
            .put("deviceId", identity.deviceId)
            .put("userId", accountUserId)
            .put("publicKey", identity.publicKeyBase64)
            .put("displayName", displayName.take(80))
            .put("transferCode", transferCode.orEmpty())
            .toString()
        return request("POST", "/v1/devices/register", body)
    }

    fun createTransferCode(): JSONObject =
        request("POST", "/v1/me/account-transfer", "{}")

    fun inheritAccount(accountUserId: String, transferCode: String, displayName: String): JSONObject {
        val response = registerDevice(
            displayName = displayName,
            transferCode = transferCode,
            accountUserId = accountUserId
        )
        identity.inheritUserId(accountUserId)
        return response
    }

    fun revokeGuildMember(guildId: String, memberCloudUserId: String): JSONObject =
        request(
            "POST",
            "/v1/guilds/${encode(guildId)}/members/${encode(memberCloudUserId)}/revoke",
            "{}"
        )

    fun listDevices(): JSONObject = request("GET", "/v1/me/devices")

    fun revokeDevice(deviceId: String): JSONObject =
        request("POST", "/v1/me/devices/${encode(deviceId)}/revoke", "{}")

    fun updateProfile(displayName: String): JSONObject =
        request(
            "PATCH",
            "/v1/me/profile",
            JSONObject().put("displayName", displayName.take(40)).toString()
        )

    fun createGuild(
        guildId: String,
        name: String,
        inviteCode: String,
        inviteExpiresAt: Long,
        ownerProfileId: String,
        ownerDisplayName: String
    ): JSONObject = request(
        "POST",
        "/v1/guilds",
        JSONObject()
            .put("guildId", guildId)
            .put("name", name)
            .put("inviteCode", inviteCode)
            .put("inviteExpiresAt", inviteExpiresAt)
            .put("ownerProfileId", ownerProfileId)
            .put("ownerDisplayName", ownerDisplayName)
            .toString()
    )

    fun resolveInvite(inviteCode: String): JSONObject =
        request("GET", "/v1/guild-invites/resolve?code=${encode(inviteCode)}")

    fun createGuildInvite(
        guildId: String,
        inviteCode: String,
        oneTime: Boolean,
        expiresAt: Long,
        replaceReusable: Boolean
    ): JSONObject = request(
        "POST",
        "/v1/guilds/${encode(guildId)}/invites",
        JSONObject()
            .put("inviteCode", inviteCode)
            .put("oneTime", oneTime)
            .put("expiresAt", expiresAt)
            .put("replaceReusable", replaceReusable)
            .toString()
    )

    fun requestGuildJoin(
        requestId: String,
        inviteCode: String,
        applicantProfileId: String,
        applicantDisplayName: String,
        requestedSide: String
    ): JSONObject = request(
        "POST",
        "/v1/guild-join-requests",
        JSONObject()
            .put("requestId", requestId)
            .put("inviteCode", inviteCode)
            .put("applicantProfileId", applicantProfileId)
            .put("applicantDisplayName", applicantDisplayName)
            .put("requestedSide", requestedSide)
            .toString()
    )

    fun listJoinRequests(guildId: String): JSONObject =
        request("GET", "/v1/guild-join-requests?guildId=${encode(guildId)}")

    fun decideJoinRequest(requestId: String, approved: Boolean): JSONObject =
        request(
            "POST",
            "/v1/guild-join-requests/${encode(requestId)}/decision",
            JSONObject().put("decision", if (approved) "APPROVED" else "REJECTED").toString()
        )

    fun listMyGuilds(): JSONObject = request("GET", "/v1/me/guilds")

    fun getGuildQuestCatalog(guildId: String): JSONObject =
        request("GET", "/v1/guilds/${encode(guildId)}/quest-catalog")

    fun putGuildQuestCatalog(guildId: String, catalog: JSONObject): JSONObject =
        request(
            "PUT",
            "/v1/guilds/${encode(guildId)}/quest-catalog",
            catalog.toString()
        )

    fun createCounterSession(
        sessionId: String,
        guildId: String,
        action: String,
        adventurerUserId: String?,
        nonceHash: String,
        summary: String,
        expiresAt: Long
    ): JSONObject = request(
        "POST",
        "/v1/counter-sessions",
        JSONObject()
            .put("sessionId", sessionId)
            .put("guildId", guildId)
            .put("action", action)
            .put("adventurerUserId", adventurerUserId)
            .put("nonceHash", nonceHash)
            .put("encryptedSummary", summary)
            .put("expiresAt", expiresAt)
            .toString()
    )

    fun listCounterSessions(guildId: String): JSONObject =
        request("GET", "/v1/counter-sessions?guildId=${encode(guildId)}")

    fun confirmCounterSession(sessionId: String): JSONObject =
        request("POST", "/v1/counter-sessions/${encode(sessionId)}/confirm", "{}")

    fun cancelCounterSession(sessionId: String): JSONObject =
        request("POST", "/v1/counter-sessions/${encode(sessionId)}/cancel", "{}")

    fun request(method: String, pathAndQuery: String, body: String = ""): JSONObject {
        require(pathAndQuery.startsWith("/"))
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        require(
            normalizedBaseUrl.startsWith("https://") ||
                normalizedBaseUrl.startsWith("http://localhost") ||
                normalizedBaseUrl.startsWith("http://10.0.2.2")
        ) {
            "尚未設定雲端服務位址，請在 private.properties 設定 CLOUDFLARE_API_BASE_URL。"
        }
        val timestamp = System.currentTimeMillis().toString()
        val nonce = ByteArray(24).also(SecureRandom()::nextBytes).let {
            Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
        val bodyHash = MessageDigest.getInstance("SHA-256")
            .digest(body.toByteArray(StandardCharsets.UTF_8))
            .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
        val canonical = listOf(method.uppercase(), pathAndQuery, timestamp, nonce, bodyHash).joinToString("\n")
        val connection = (URL(normalizedBaseUrl + pathAndQuery).openConnection() as HttpURLConnection).apply {
            requestMethod = method.uppercase()
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Device-Id", identity.deviceId)
            setRequestProperty("X-Timestamp", timestamp)
            setRequestProperty("X-Nonce", nonce)
            setRequestProperty("X-Signature", identity.sign(canonical.toByteArray(StandardCharsets.UTF_8)))
            if (body.isNotEmpty()) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        if (body.isNotEmpty()) {
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        val status = connection.responseCode
        val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        connection.disconnect()
        if (status !in 200..299) {
            val message = runCatching { JSONObject(responseBody).optString("error") }.getOrNull()
            error("Cloudflare API $status: ${message?.ifBlank { responseBody } ?: responseBody}")
        }
        return JSONObject(responseBody)
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
