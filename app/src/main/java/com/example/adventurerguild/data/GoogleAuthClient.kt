package com.example.adventurerguild.data

import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.example.adventurerguild.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleAuthClient(private val activity: ComponentActivity) {
    private val credentialManager = CredentialManager.create(activity)

    suspend fun requestGoogleIdToken(): String {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        require(webClientId.isNotBlank()) {
            "尚未設定 GOOGLE_WEB_CLIENT_ID。請在 gradle.properties 填入 Firebase/Google Cloud 的 Web client ID。"
        }

        val googleIdOption = GetSignInWithGoogleOption.Builder(
            serverClientId = webClientId
        )
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = try {
            credentialManager.getCredential(activity, request)
        } catch (_: NoCredentialException) {
            error("裝置上沒有可用的 Google 帳號，請先在 Android 設定中加入帳號。")
        }
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }

        error("無法取得 Google ID token。")
    }
}
