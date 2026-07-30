package com.example.adventurerguild.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

object DriveAuthorization {
    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    suspend fun requestDriveFileAccess(context: Context): DriveAuthorizationResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        val resolution = result.pendingIntent
        return if (result.hasResolution() && resolution != null) {
            DriveAuthorizationResult.NeedsConsent(resolution)
        } else {
            DriveAuthorizationResult.Authorized(result.accessToken)
        }
    }

    fun authorizationResultFromIntent(context: Context, intent: Intent?): DriveAuthorizationResult {
        val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(intent)
        return DriveAuthorizationResult.Authorized(result.accessToken)
    }
}

sealed interface DriveAuthorizationResult {
    data class Authorized(val accessToken: String?) : DriveAuthorizationResult
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthorizationResult
}
