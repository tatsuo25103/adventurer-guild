package com.example.adventurerguild.nearby

import android.Manifest
import android.content.Context
import android.os.Build
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class NearbyCounterPhase {
    IDLE,
    ADVERTISING,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    READY_TO_SIGN,
    SIGNED,
    ERROR
}

data class NearbyCounterState(
    val sessionId: String? = null,
    val phase: NearbyCounterPhase = NearbyCounterPhase.IDLE,
    val peerName: String? = null,
    val error: String? = null
)

class NearbyCounterCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val client = Nearby.getConnectionsClient(appContext)
    private val serviceId = "${appContext.packageName}.counter"
    private val _state = MutableStateFlow(NearbyCounterState())
    val state: StateFlow<NearbyCounterState> = _state.asStateFlow()

    private var expectedSessionId: String? = null
    private var localDisplayName: String = ""
    private var connectedEndpointId: String? = null

    fun advertise(sessionId: String, displayName: String) {
        stop()
        expectedSessionId = sessionId
        localDisplayName = displayName
        _state.value = NearbyCounterState(sessionId, NearbyCounterPhase.ADVERTISING)
        client.startAdvertising(
            endpointName(sessionId, displayName),
            serviceId,
            connectionLifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        ).addOnFailureListener { fail(it) }
    }

    fun discover(sessionId: String, displayName: String) {
        stop()
        expectedSessionId = sessionId
        localDisplayName = displayName
        _state.value = NearbyCounterState(sessionId, NearbyCounterPhase.DISCOVERING)
        client.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        ).addOnFailureListener { fail(it) }
    }

    fun sendManagerApproval(sessionId: String, managerUserId: String, managerName: String): Boolean {
        val endpointId = connectedEndpointId ?: return false
        if (_state.value.phase != NearbyCounterPhase.READY_TO_SIGN || expectedSessionId != sessionId) return false
        val payload = JSONObject()
            .put("type", "MANAGER_APPROVAL")
            .put("sessionId", sessionId)
            .put("managerUserId", managerUserId)
            .put("managerName", managerName)
            .put("approvedAt", System.currentTimeMillis())
            .toString()
            .toByteArray(Charsets.UTF_8)
        client.sendPayload(endpointId, Payload.fromBytes(payload))
            .addOnSuccessListener {
                _state.value = _state.value.copy(phase = NearbyCounterPhase.SIGNED)
            }
            .addOnFailureListener { fail(it) }
        return true
    }

    fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        expectedSessionId = null
        connectedEndpointId = null
        _state.value = NearbyCounterState()
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val sessionId = expectedSessionId ?: return
            if (!info.endpointName.startsWith("AG|$sessionId|")) return
            client.stopDiscovery()
            _state.value = _state.value.copy(
                phase = NearbyCounterPhase.CONNECTING,
                peerName = info.endpointName.substringAfterLast("|")
            )
            client.requestConnection(localDisplayName, endpointId, connectionLifecycleCallback)
                .addOnFailureListener { fail(it) }
        }

        override fun onEndpointLost(endpointId: String) = Unit
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            _state.value = _state.value.copy(
                phase = NearbyCounterPhase.CONNECTING,
                peerName = info.endpointName.substringAfterLast("|")
            )
            client.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { fail(it) }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpointId = endpointId
                client.stopAdvertising()
                client.stopDiscovery()
                _state.value = _state.value.copy(phase = NearbyCounterPhase.CONNECTED)
                sendHandshake(endpointId)
            } else {
                fail(IllegalStateException("Nearby 連線失敗：${result.status.statusCode}"))
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpointId = null
            if (_state.value.phase != NearbyCounterPhase.SIGNED) {
                fail(IllegalStateException("Nearby 連線已中斷"))
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val message = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull() ?: return
            if (message.optString("sessionId") != expectedSessionId) {
                fail(IllegalStateException("收到不屬於本次任務的 Nearby 資料"))
                return
            }
            when (message.optString("type")) {
                "HANDSHAKE" -> _state.value = _state.value.copy(phase = NearbyCounterPhase.READY_TO_SIGN)
                "MANAGER_APPROVAL" -> _state.value = _state.value.copy(
                    phase = NearbyCounterPhase.SIGNED,
                    peerName = message.optString("managerName").ifBlank { _state.value.peerName }
                )
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private fun sendHandshake(endpointId: String) {
        val sessionId = expectedSessionId ?: return
        val bytes = JSONObject()
            .put("type", "HANDSHAKE")
            .put("sessionId", sessionId)
            .toString()
            .toByteArray(Charsets.UTF_8)
        client.sendPayload(endpointId, Payload.fromBytes(bytes)).addOnFailureListener { fail(it) }
    }

    private fun endpointName(sessionId: String, displayName: String): String =
        "AG|$sessionId|${displayName.take(24)}"

    private fun fail(error: Throwable) {
        _state.value = _state.value.copy(
            phase = NearbyCounterPhase.ERROR,
            error = error.message ?: "Nearby 發生未知錯誤"
        )
    }

    companion object {
        fun requiredRuntimePermissions(): Array<String> = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
            Build.VERSION.SDK_INT >= 31 -> arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            else -> arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}
